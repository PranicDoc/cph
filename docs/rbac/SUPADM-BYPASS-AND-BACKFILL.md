# SUPADM Bypass Design and ADMIN-Tier Backfill Migration Plan

This document satisfies ROADMAP Phase 35 Success Criterion 3 ("A documented
SUPADM-bypass design and ADMIN-tier backfill migration plan exists, ready for
Phase 36's enforcement wiring"). It is referenced by
`V30__create_rbac_schema.sql`'s banner comment (line 42-43) and by threat
`T-35-06` in both `35-01-PLAN.md` and `35-03-PLAN.md`'s threat registers,
both of which point here for the resolution of the accepted
`user_role.principal_id` spoofing risk. This document is design/reference
material only — no SQL migration, no Java code, no schema change ships in
this plan. Its two audiences are **Phase 36** (Role CRUD Backend +
Enforcement, which builds the `RbacAuthz` bean this document specifies) and
**Phase 40** (Legacy Endpoint Cutover & Admin Backfill, which authors the
migration this document's grant table and INSERT description are the
starting point for).

## SUPADM Bypass Design

### Where it lives

Per D-04's combined `@PreAuthorize` expression
(`hasAnyRole('ADMIN','SUPADM') and @rbacAuthz.hasPermission('RESOURCE_KEY','VERB')`),
the SUPADM bypass belongs inside the `@rbacAuthz.hasPermission(resourceKey,
verb)` method Phase 36 creates — in a new `RbacAuthz` bean under
`com.pranicdoc.cph.rbac` — not in the outer `hasAnyRole('ADMIN','SUPADM')`
half of the expression. The outer gate already passes SUPADM through Spring
Security's own role check (Spring Security evaluates `hasAnyRole` against
the caller's granted authorities before the `and` clause is even reached).
The inner half is where a false denial could otherwise occur: if
`hasPermission` fell through to the normal `RolePermissionLookupService`
path for a SUPADM caller, it would return `false` on every request, because
SUPADM has no `user_role` grant (per D-02) and never will. Without an
explicit short-circuit, the outer gate's "yes" would be silently overridden
by the inner gate's "no," locking every SUPADM account out of every
converted endpoint. This is precisely the failure mode this design exists
to prevent.

### The check itself

The concrete first line of `RbacAuthz.hasPermission(String resourceKey,
String verb)`: read the caller's role via the existing
`SecurityContextUtil.getCurrentRole()` (already injected into `RbacAuthz` as
a constructor dependency, same as `ConsentTypeController` already does at
lines 42/68/76 of
`cph/app/src/main/java/com/pranicdoc/cph/consent/controller/ConsentTypeController.java`)
and short-circuit `return true` immediately if it equals the literal
`"SUPADM"`, before any call to `RolePermissionLookupService` or its cache.

```java
public boolean hasPermission(String resourceKey, String verb) {
    if ("SUPADM".equals(securityContextUtil.getCurrentRole())) {
        return true;
    }
    // ... normal RolePermissionLookupService path for everyone else
}
```

This reuses the existing JWT/`SecurityContextUtil` mechanism verbatim — D-02
requires SUPADM stay "untouched everywhere else (JWT, SecurityContextUtil,
existing checks)," so this design introduces no new claim, no new token
field, and no new authentication path. `getCurrentRole()` already throws
`IllegalStateException` if the caller is unauthenticated (see
`SecurityContextUtil.java` lines 31-39), which is fine here: `hasPermission`
only ever runs after Spring Security's `.anyRequest().authenticated()` gate
and the outer `@PreAuthorize` role check have both already succeeded, so the
security context is guaranteed populated by the time this method executes.

### Confirms D-02's data-level guarantee

Because the bypass is a code-level short-circuit evaluated before any
database or cache read, SUPADM structurally never needs an `rbac_role` row
or a `user_role` grant. In plain terms: SUPADM never receives a rbac_role
row, and SUPADM never receives a user_role grant. There is no seeded
"SUPADM role" for Phase 40 to backfill and no `user_role` row for Phase 36
to create for a SUPADM account; this is a design invariant, not an accident
of what Phase 35/36 happen to seed. Phase 35's `V30` migration
already seeds zero rows into `rbac_role` (`T-35-03`), and this document
confirms that Phase 36 must never change that for SUPADM specifically —
the zero-`rbac_role`-rows state is permanent for SUPADM, not just true on
day one.

### Cache interaction

Because the bypass returns before touching `RolePermissionLookupService`
(D-07's Redis/Caffeine two-level cache, keyed by healerId), a SUPADM caller
never populates or consumes a cache entry — there is nothing to invalidate
for SUPADM on a permission change, and the cache-key strategy Phase 36
designs only ever needs to cover non-SUPADM principals. A cache warm-up or
invalidation job that iterates `user_role` rows will structurally never
encounter a SUPADM entry to warm or invalidate, because none exists.

### Resolves T-35-06 for the enforcement path

Restating the accepted threat from 35-01/35-03: `user_role.principal_id`
carries no FK, mirroring `consent.subject_id`'s precedent (D-08), with
existence validated at the service layer rather than by the database. The
bypass path never touches `user_role` at all for SUPADM — there is no
`principal_id` to validate, spoof, or reason about on the SUPADM path,
because the bypass returns `true` before any `user_role` lookup is ever
attempted. T-35-06 is therefore entirely orthogonal to SUPADM; it only ever
concerns non-SUPADM principals assigned through Phase 37's
`RoleAssignmentService`, which is where the real existence-validation
obligation lives.

### What Phase 36 must NOT do

1. **Do not seed a `rbac_role` row named `SUPADM` or similar "for
   consistency."** This reintroduces exactly the "declared but unenforced"
   risk `PITFALLS.md` documents for the old `SENIOR_HEALER`/`RECEPTIONIST`
   tiers, this time in reverse: a role row nobody ever grants via
   `user_role` (because the bypass never checks it) but somebody might
   later query, assume is meaningful, and build broken tooling around (e.g.
   a "roles list" screen showing a phantom `SUPADM` row with zero assigned
   users and zero real effect).

2. **Do not special-case SUPADM inside `RolePermissionLookupService`
   itself.** Keep the short-circuit in `RbacAuthz`, one call site, so it is
   greppable and unit-testable in isolation — matching D-04's stated
   rationale for choosing bean-reference SpEL over Spring's
   `PermissionEvaluator`/ACL module in the first place. Scattering the
   SUPADM check across the cache-lookup service as well would create two
   sources of truth for "is this caller exempt," which is exactly the kind
   of enforcement-path ambiguity D-04 was written to avoid.

3. **Do not read the SUPADM check from a database flag or config value.**
   It must stay the hardcoded string comparison D-02 specifies, because a
   configurable "who is SUPADM" surface is itself a privilege-escalation
   target — an attacker (or a careless admin-tooling bug) who can flip a
   config value or database row to name themselves "SUPADM-equivalent"
   bypasses every permission check in the system. The hardcoded literal is
   the security boundary, not an implementation detail to be made flexible
   later.

## ADMIN-Tier Backfill Migration Plan

> ## ⚠️ STATUS: NOT EXECUTED — historical rationale only
>
> **Phase 40 deliberately did not run this plan, and it is permanently mooted.**
> Everything below Steps 1–5, the `Legacy Administrator` role, and the grant
> table is retained as design history, not as an executable specification.
>
> **Why (40-CONTEXT D-05 / D-06):** production holds **zero**
> `healer.role='ADMIN'` accounts, so there is nothing to backfill. D-06
> discharges `MIGRATE-03` by an operator-run count audit
> (`SELECT count(*) FROM healer WHERE role='ADMIN';` → expected `0`) recorded in
> the Phase 40 verification doc instead, following the `MIGRATE-05` precedent
> (which confirmed the same for `SENIOR_HEALER`/`RECEPTIONIST`).
>
> **What this means concretely:** Phase 40 shipped **no Flyway migration, no
> `Legacy Administrator` role, no `rbac_role` seed, and no `user_role` INSERT.**
> The next Flyway version remains unconsumed by this phase. Phase 40 is a
> code-only change on `feature/rbac-schema`; rollback = code revert (D-11).
>
> **Accuracy warning — the grant table below is stale on both resource keys and
> verbs.** Do not copy any `(resource, verb)` pair from it:
> - `TRANSACTIONS` is not a `Resource` enum constant — the real constant is
>   `CREDIT_ACCOUNT_TRANSACTIONS`.
> - `MASTER_DATA` is not a `Resource` enum constant — the real constants are
>   `MASTER_DATA_CHAKRAS`, `MASTER_DATA_COLORS` and `MASTER_DATA_PROTOCOLS`.
> - `HEALER_REGISTRATION` is not a `Resource` enum constant — the real constant
>   is `HEALER_REG`.
> - The `revoke → DELETE` mapping (row `HEALER_REGISTRATION`) is invalid:
>   `HEALER_REG` allow-lists only `READ, CREATE, UPDATE` — it has **no `DELETE`
>   verb**. Phase 40 mapped invite `revoke` to `HEALER_REG:UPDATE` (PD-40-07).
>
> Anyone reviving this plan must re-derive **every** pair against
> `app/src/main/java/com/pranicdoc/cph/rbac/model/Resource.java`, which is the
> sole allow-list (`Permission`'s constructor enforces `Resource.supports(...)`;
> there is no `resource_permission` table).
>
> **Authoritative record of what Phase 40 actually did:**
> `.planning/phases/40-legacy-endpoint-cutover-admin-backfill/40-VERIFICATION-EVIDENCE.md`.

### Goal restated

Every account with `healer.role = 'ADMIN'` at the moment of Phase 40's
cutover deploy must receive a `user_role` grant equivalent to its
pre-cutover access, in the same deploy that removes the old hardcoded
checks — satisfying ROADMAP Phase 40 Success Criteria 3 and 4 and
requirements `MIGRATE-02`/`MIGRATE-03`.

### Step 1 — one new role, not one row per healer (PD-05)

Phase 40's migration file (its own next sequential `V` number, authored in
Phase 40, not this phase) inserts exactly one `rbac_role` row — recommended
`name = 'Legacy Administrator'`, `description` citing this document and
`MIGRATE-03` — using the same fixed-literal-UUID seeding idiom
`V27__create_consent_type_table.sql` and this migration's own Plan 35-02
task already established (no `gen_random_uuid()`, explicit UUID literal,
`now(), now(), false, 0` for the `BaseEntity` prefix). Reusing an existing
role is impossible: Phase 35 seeds zero roles into `rbac_role` (`T-35-03`),
so Phase 40 is necessarily the first writer.

### Step 2 — grant table, derived from current access, not from D-16's full catalog (PD-06)

The following table is the literal grant set Phase 40's migration inserts
into `role_permission` for the `Legacy Administrator` role, joining against
Plan 35-02's seeded `resource_permission` rows by `(resource key, verb)`.
This table is a **subset** of D-16's 23-row allow-list, not a copy of it.

| Resource | Verb(s) granted to `Legacy Administrator` | Verb(s) deliberately withheld | Grounded in |
|---|---|---|---|
| ROLES | none | READ, WRITE, DELETE | D-06 — Roles & Permissions is SUPADM-only; ADMIN never had role-management capability to preserve |
| PACKAGES | none | READ, WRITE, DELETE | `AdminCreditController` `/packages/*` is `hasRole('SUPADM')` only today — ADMIN never had this access |
| TRANSACTIONS | READ, WRITE | — | `AdminCreditController` GET transactions / POST adjust / PUT payg are all `hasAnyRole('ADMIN','SUPADM')` |
| MASTER_DATA | WRITE, DELETE | READ (flagged, not withheld — see note below) | `MasterDataController` mutating verbs are `hasAnyRole('ADMIN','SUPADM')`; GET endpoints carry no role gate today |
| ACCOUNTS | READ, WRITE | DELETE | `AdminCreditController` GET /accounts is `hasAnyRole('ADMIN','SUPADM')` (READ); `HealerController` PATCH /active is `hasAnyRole('ADMIN','SUPADM')` (WRITE); `HealerController` GET list and DELETE are `hasRole('SUPADM')` only |
| HEALER_REGISTRATION | READ, WRITE, DELETE | — | `AdminInviteController` create/list/resend/revoke are all `hasAnyRole('ADMIN','SUPADM')`; D-16 maps revoke to the DELETE verb |
| LEGAL_DOCUMENTS | READ, WRITE, DELETE | ADMIN | `ConsentTypeController`/`ConsentController.publish` endpoint gates are `hasAnyRole('ADMIN','SUPADM')`; the `ADMIN` verb (mandatory-toggle/deactivate-system-type effect) is already SUPADM-override-guarded inside the service via `isSuperAdmin`, per D-16's own note |
| GLOBAL_CONFIG | none | READ, WRITE | `AdminCreditController` `/config/*` is `hasRole('SUPADM')` only today — ADMIN never had this access |

**Note on MASTER_DATA's READ:** it is deliberately flagged rather than
definitively included or excluded. Today's `GET` endpoints on
`MasterDataController` carry no role gate at all (open to any authenticated
caller), so there is no existing ADMIN-specific "read" permission to
preserve — but Phase 40's endpoint-by-endpoint sweep (ROADMAP Phase 40
Success Criterion 1) may choose to gate `GET` behind `MASTER_DATA:READ` as
part of the cutover. Recommend Phase 40 grant `MASTER_DATA:READ` to
`Legacy Administrator` regardless, since granting a permission ADMIN's
ungated `GET` access already exceeded is strictly safe (it cannot reduce
access), whereas withholding it could regress an ADMIN account if Phase 40
does add the gate.

**Summary of what is withheld and why:** `PACKAGES` and `GLOBAL_CONFIG` are
withheld from the backfill grant in full — ADMIN never had access to either,
both are `hasRole('SUPADM')`-only today (`AdminCreditController` `/packages/*`
and `/config/*`). `ROLES` is likewise withheld in full per D-06. The single
`LEGAL_DOCUMENTS:ADMIN` verb is withheld while the other three
`LEGAL_DOCUMENTS` verbs are granted. Granting any of these four withheld
combinations would silently hand every legacy ADMIN account a privilege it
never had before — a regression disguised as a backfill, not a faithful
migration.

### Step 3 — the backfill INSERT

The concrete `user_role` backfill statement, in prose (Phase 40 authors the
real runnable SQL, this is not it):

`INSERT INTO user_role (id, created_at, updated_at, is_deleted, version, principal_type, principal_id, role_id, assigned_at, assigned_by) SELECT <generated-or-fixed-uuid>, now(), now(), false, 0, 'HEALER', healer.id, '<legacy-administrator-role-id>', now(), NULL FROM healer WHERE role = 'ADMIN'`

`assigned_by` is `NULL` rather than a healer id, since this grant is
system-issued by the migration itself, not by another admin's action
through Phase 37's `RoleAssignmentService`. Recommend Phase 40 additionally
set `created_by` to a fixed marker string (e.g. `'MIGRATE-03-BACKFILL'`) on
the `BaseEntity` prefix so the row is traceable in an audit query without
needing a separate migration-log table.

### Step 4 — atomicity, the "no window" guarantee

The mechanism, not just an assertion: Spring Boot runs Flyway `migrate()`
synchronously during application context startup, before the embedded
server begins accepting HTTP traffic. If Phase 40's single migration file
performs Step 1 (role), Step 2 (grants), and Step 3 (backfill) together, and
the same deployed artifact also contains the code change that replaces the
old hardcoded `@PreAuthorize("hasAnyRole('ADMIN','SUPADM')")` checks with the
new dynamic `@rbacAuthz.hasPermission(...)` checks, then by construction:
the grant rows exist in the database before any request can ever reach code
that evaluates the new dynamic check — the JVM cannot finish starting and
route traffic until Flyway completes. There is no separate "migrate first,
deploy code later" step and no separate "deploy code first, backfill later"
step — one deploy, one migration, one artifact. This is why Step 1-3 must be
ONE migration file, not three, and must ship in the same release as the
`@PreAuthorize` conversion, never a preceding or following release.

### Step 5 — verification path for Phase 40

ROADMAP Phase 40 Success Criterion 4 ("a pre-existing ADMIN account, after
the cutover deploy, can still successfully call every endpoint it could call
before") is the acceptance test for this entire section. The
compliance-sensitive `ConsentTypeController`/`ConsentController` endpoints
specifically must be included in that human verification given their
legal/compliance sensitivity (already called out in ROADMAP Phase 40 SC4).

### Non-goals, explicit

This document does not migrate `SENIOR_HEALER`/`RECEPTIONIST` (`MIGRATE-05`,
zero accounts hold either value, Phase 41's direct removal) and does not
alter `healer.role` in any way — the backfill is additive only, exactly
like `V30` (Plan 35-01 `T-35-01`); `healer.role = 'ADMIN'` remains readable
and unchanged until Phase 41 shrinks the `Role` enum.

## Cross-References

- `.planning/ROADMAP.md` — Phase 36 (Role CRUD Backend + Enforcement) and
  Phase 40 (Legacy Endpoint Cutover & Admin Backfill) sections
- `.planning/REQUIREMENTS.md` — `RBAC-02`/`RBAC-03`/`RBAC-04` (Phase 36) and
  `MIGRATE-02`/`MIGRATE-03` (Phase 40)
- `cph/app/src/main/resources/db/migration/V30__create_rbac_schema.sql` —
  the schema this plan targets
- `.planning/phases/35-rbac-schema-resource-permission-catalog/35-01-PLAN.md`
  and `35-03-PLAN.md` — `T-35-06`, the threat this document was written to
  close out
