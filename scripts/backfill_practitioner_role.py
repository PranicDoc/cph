#!/usr/bin/env python3
"""Provision the baseline "Practitioner" role and assign it to existing healers.

Replaces the old scripts/backfill-practitioner-role.sql. Run once per
environment as part of a deploy (see .github/workflows/backfill-practitioner-role.yml),
or ad-hoc from the Actions tab whenever the grant matrix in
``app/src/main/resources/rbac/baseline-role.json`` changes.

What it does (all idempotent, one transaction):

  1. Upsert the ``Practitioner`` row in ``rbac_role`` (un-soft-deletes it if
     it was removed; refreshes the description).
  2. Reconcile ``role_permission`` for that role to exactly the spec in
     baseline-role.json — re-activating soft-deleted rows, inserting missing
     ones, and soft-deleting any grant no longer in the spec.
  3. Insert a ``user_role`` row assigning ``Practitioner`` to every active,
     non-deleted ``HEALER`` account that currently holds no (live) role.

Connection is taken from the environment (same var names as the Flyway block
in application-prod.yml):

  DB_HOST       required   Postgres host (Aiven direct host, not PgBouncer)
  DB_PORT       optional   default 5432
  DB_NAME       optional   default "pranic_doc"
  DB_USERNAME   required
  DB_PASSWORD   required
  DB_SSLMODE    optional   default "require"

Exit code is non-zero on any failure so a CI job blocks on it.
"""

from __future__ import annotations

import argparse
import json
import os
import sys
from pathlib import Path

import psycopg

DEFINITION_PATH = (
    Path(__file__).resolve().parent.parent
    / "app"
    / "src"
    / "main"
    / "resources"
    / "rbac"
    / "baseline-role.json"
)

# Mirror of com.pranicdoc.cph.rbac.model.Operation — used only to give a clear
# error before touching the DB if the JSON drifts from the Java enum.
KNOWN_OPERATIONS = {
    "READ", "CREATE", "UPDATE", "DELETE", "EXPORT", "SHARE", "WRITE", "ADMIN",
}


def load_definition() -> tuple[str, str, dict[str, list[str]]]:
    if not DEFINITION_PATH.exists():
        sys.exit(f"ERROR: baseline role definition not found at {DEFINITION_PATH}")
    data = json.loads(DEFINITION_PATH.read_text(encoding="utf-8"))
    name = data.get("name")
    description = data.get("description")
    grants = data.get("grants") or {}
    if not name or not description or not grants:
        sys.exit("ERROR: baseline-role.json is missing name, description, or grants")
    for resource, ops in grants.items():
        bad = sorted(set(ops) - KNOWN_OPERATIONS)
        if bad:
            sys.exit(f"ERROR: {resource} has unknown operation(s): {', '.join(bad)}")
    return name, description, grants


def connect() -> psycopg.Connection:
    missing = [v for v in ("DB_HOST", "DB_USERNAME", "DB_PASSWORD") if not os.environ.get(v)]
    if missing:
        sys.exit(f"ERROR: missing required env var(s): {', '.join(missing)}")
    return psycopg.connect(
        host=os.environ["DB_HOST"],
        port=int(os.environ.get("DB_PORT") or 5432),
        dbname=os.environ.get("DB_NAME") or "pranic_doc",
        user=os.environ["DB_USERNAME"],
        password=os.environ["DB_PASSWORD"],
        sslmode=os.environ.get("DB_SSLMODE") or "require",
        connect_timeout=15,
        application_name="backfill_practitioner_role",
    )


def upsert_role(cur: psycopg.Cursor, name: str, description: str, dry_run: bool) -> str:
    cur.execute("SELECT id, is_deleted, description FROM rbac_role WHERE name = %s", (name,))
    row = cur.fetchone()
    if row is None:
        print(f"  role '{name}': absent -> INSERT")
        if dry_run:
            return "00000000-0000-0000-0000-000000000000"
        cur.execute(
            """
            INSERT INTO rbac_role (id, created_at, updated_at, is_deleted, version, name, description)
            VALUES (gen_random_uuid(), now(), now(), false, 0, %s, %s)
            RETURNING id
            """,
            (name, description),
        )
        return str(cur.fetchone()[0])

    role_id, is_deleted, current_desc = row
    changes = []
    if is_deleted:
        changes.append("un-delete")
    if current_desc != description:
        changes.append("refresh description")
    if changes:
        print(f"  role '{name}': present ({role_id}) -> {', '.join(changes)}")
        if not dry_run:
            cur.execute(
                "UPDATE rbac_role SET is_deleted = false, description = %s, updated_at = now() WHERE id = %s",
                (description, role_id),
            )
    else:
        print(f"  role '{name}': present ({role_id}) -> no change")
    return str(role_id)


def reconcile_grants(
    cur: psycopg.Cursor, role_id: str, grants: dict[str, list[str]], dry_run: bool
) -> None:
    desired: set[tuple[str, str]] = {
        (resource, op) for resource, ops in grants.items() for op in ops
    }

    cur.execute(
        "SELECT resource, operation, is_deleted FROM role_permission WHERE role_id = %s",
        (role_id,),
    )
    existing = {(r, o): d for r, o, d in cur.fetchall()}

    to_insert = sorted(k for k in desired if k not in existing)
    to_reactivate = sorted(k for k in desired if existing.get(k) is True)
    to_soft_delete = sorted(
        k for k, is_deleted in existing.items() if is_deleted is False and k not in desired
    )

    print(
        f"  grants: {len(desired)} in spec | "
        f"+{len(to_insert)} new, ^{len(to_reactivate)} re-activated, "
        f"-{len(to_soft_delete)} removed"
    )
    for r, o in to_insert:
        print(f"    + {r}:{o}")
    for r, o in to_reactivate:
        print(f"    ^ {r}:{o}")
    for r, o in to_soft_delete:
        print(f"    - {r}:{o}")

    if dry_run:
        return

    for r, o in to_insert:
        cur.execute(
            """
            INSERT INTO role_permission
                (id, created_at, updated_at, is_deleted, version, role_id, resource, operation)
            VALUES (gen_random_uuid(), now(), now(), false, 0, %s, %s, %s)
            """,
            (role_id, r, o),
        )
    for r, o in to_reactivate:
        cur.execute(
            """
            UPDATE role_permission SET is_deleted = false, updated_at = now()
            WHERE role_id = %s AND resource = %s AND operation = %s
            """,
            (role_id, r, o),
        )
    for r, o in to_soft_delete:
        cur.execute(
            """
            UPDATE role_permission SET is_deleted = true, updated_at = now()
            WHERE role_id = %s AND resource = %s AND operation = %s AND is_deleted = false
            """,
            (role_id, r, o),
        )


def assign_to_healers(cur: psycopg.Cursor, role_id: str, dry_run: bool) -> None:
    select_targets = """
        SELECT h.id, h.email
        FROM healer h
        WHERE h.active = true
          AND h.is_deleted = false
          AND h.role = 'HEALER'
          AND NOT EXISTS (
                SELECT 1 FROM user_role ur
                WHERE ur.principal_type = 'HEALER'
                  AND ur.principal_id = h.id
                  AND ur.is_deleted = false
          )
    """
    cur.execute(select_targets, ())
    targets = cur.fetchall()
    print(f"  assignment: {len(targets)} healer(s) with no live role -> grant '{role_id}'")
    for _, email in targets:
        print(f"    * {email}")

    if dry_run or not targets:
        return

    cur.execute(
        f"""
        INSERT INTO user_role
            (id, created_at, updated_at, is_deleted, version,
             principal_type, principal_id, role_id, assigned_at, assigned_by)
        SELECT gen_random_uuid(), now(), now(), false, 0,
               'HEALER', t.id, %s, now(), NULL
        FROM ( {select_targets} ) AS t
        """,
        (role_id,),
    )
    print(f"  assignment: inserted {cur.rowcount} user_role row(s)")


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--dry-run",
        action="store_true",
        help="print the plan, roll back instead of committing",
    )
    args = parser.parse_args()

    name, description, grants = load_definition()
    print(f"Baseline role: '{name}' with {sum(len(v) for v in grants.values())} grant(s) across "
          f"{len(grants)} resource(s)")
    if args.dry_run:
        print("--- DRY RUN: no changes will be committed ---")

    conn = connect()
    try:
        conn.autocommit = False
        with conn.cursor() as cur:
            print("[1/3] rbac_role")
            role_id = upsert_role(cur, name, description, args.dry_run)
            print("[2/3] role_permission")
            reconcile_grants(cur, role_id, grants, args.dry_run)
            print("[3/3] user_role")
            assign_to_healers(cur, role_id, args.dry_run)
        if args.dry_run:
            conn.rollback()
            print("Dry run complete - rolled back.")
        else:
            conn.commit()
            print("Committed.")
    except Exception:
        conn.rollback()
        raise
    finally:
        conn.close()
    return 0


if __name__ == "__main__":
    sys.exit(main())
