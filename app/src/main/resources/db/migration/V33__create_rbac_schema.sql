-- =============================================================================
-- V33__create_rbac_schema.sql
-- Phase 35 (RBAC Schema) — RBAC-01.
--
-- Strictly additive: creates three new tables (rbac_role, role_permission,
-- user_role) plus their three Envers audit shadow tables. Touches NO
-- pre-existing table, column, or row.
--
-- Superseded design note: resource/permission/resource_permission were
-- originally three DB-seeded catalog tables with an allow-list FK
-- (resource_permission) enforcing valid (resource, verb) combos, shipped as
-- V30__create_rbac_schema.sql. That design is replaced here — Resource and
-- Operation are now static Java enums (com.pranicdoc.cph.rbac.model), and
-- the allow-list check moves into Java: Permission's constructor calls
-- Resource.supports(Operation) at write time, before a grant is ever
-- persisted. Neither V30 nor this migration ran in any environment before
-- the switch, so this shipped as a renumbered V31 rather than an edit to V30
-- in place.
--
-- Renumbered a second time, V31 -> V33 (36-05 live-verification setup):
-- master independently added its own V30__add_audit_log_status_and_message.sql
-- and V31__add_new_case_status_continued.sql after this branch forked, and
-- both landed on the shared local dev DB. Neither this migration nor V32
-- (now V34) had run in any environment before this renumber either.
--
-- Planner decisions (locked, see 35-01-PLAN.md <planner_decisions>):
--
-- PD-01 — Java entity naming: table names are exactly rbac_role,
--   role_permission, user_role. Java entity class names are RbacRole,
--   RolePermission, UserRole. The `Rbac` prefix on rbac_role avoids
--   collision with com.pranicdoc.cph.common.enums.Role.
--
-- PD-03 — no is_system column on rbac_role: SUPADM is never a user_role
--   target and never needs a permission grant (D-02), and this migration
--   seeds zero roles, so there is no protected seeded role for such a flag
--   to guard.
--
-- PD-04 (superseded) — role_permission grants now carry a resource + verb
--   pair directly (as VARCHAR enum names), not an FK to an allow-list table.
--   Resource.supports(Operation) is the allow-list check, enforced in Java.
--
-- The SUPADM-bypass design and the Phase 40 ADMIN-tier backfill plan live in
-- cph/docs/rbac/SUPADM-BYPASS-AND-BACKFILL.md (authored in Plan 35-05).
-- =============================================================================

-- D-02/PD-03: rbac_role deliberately has no is_system/system_defined column.
-- SUPADM is the sole hardcoded bypass tier (see AdminAuthz.java), is never a
-- user_role target, and this migration seeds zero rows into rbac_role — so
-- there is no protected seeded role for such a flag to guard.
CREATE TABLE rbac_role (
    id            UUID         NOT NULL,
    created_at    TIMESTAMPTZ  NOT NULL,
    updated_at    TIMESTAMPTZ  NOT NULL,
    created_by    VARCHAR(255),
    updated_by    VARCHAR(255),
    is_deleted    BOOLEAN      NOT NULL DEFAULT FALSE,
    version       BIGINT       NOT NULL DEFAULT 0,
    name          VARCHAR(100) NOT NULL,
    description   VARCHAR(500),
    CONSTRAINT pk_rbac_role PRIMARY KEY (id),
    CONSTRAINT uq_rbac_role_name UNIQUE (name)
);

-- resource/operation are static Java enums (com.pranicdoc.cph.rbac.model),
-- not DB-seeded tables — a grant stores the enum name directly. Validity of
-- a (resource, verb) combo is enforced in Java by Permission's constructor
-- (Resource.supports(Operation)), not by a DB FK/CHECK constraint.
CREATE TABLE role_permission (
    id            UUID         NOT NULL,
    created_at    TIMESTAMPTZ  NOT NULL,
    updated_at    TIMESTAMPTZ  NOT NULL,
    created_by    VARCHAR(255),
    updated_by    VARCHAR(255),
    is_deleted    BOOLEAN      NOT NULL DEFAULT FALSE,
    version       BIGINT       NOT NULL DEFAULT 0,
    role_id       UUID         NOT NULL,
    resource      VARCHAR(40)  NOT NULL,
    operation     VARCHAR(20)  NOT NULL,
    CONSTRAINT pk_role_permission PRIMARY KEY (id),
    -- NOTE: BaseEntity's @SQLRestriction("is_deleted = false") means a
    -- soft-deleted row still occupies its unique slot — the same accepted
    -- trade-off protocol_color_mapping already carries.
    CONSTRAINT uq_role_permission UNIQUE (role_id, resource, operation),
    CONSTRAINT fk_role_permission_role FOREIGN KEY (role_id) REFERENCES rbac_role(id)
);

CREATE INDEX idx_role_permission_role ON role_permission (role_id);

-- D-08: polymorphic principal_type/principal_id, no FK on principal_id,
-- mirroring consent.subject_type/subject_id from V25 — existence of the
-- principal is a service-layer concern (Phase 37's RoleAssignmentService),
-- not a DB constraint. principal_type is VARCHAR(30) to mirror
-- consent.subject_type exactly (ARCHITECTURE.md's sketch says 20; the
-- in-repo precedent wins). UserRole still extends BaseEntity (per
-- PATTERNS.md's recommendation) with assigned_at/assigned_by as explicit
-- domain columns distinct from the inherited created_at/created_by, exactly
-- as Consent.accepted_by is distinct from created_by. Deliberately NO
-- healer_id column — the polymorphic pair replaces it.
CREATE TABLE user_role (
    id             UUID         NOT NULL,
    created_at     TIMESTAMPTZ  NOT NULL,
    updated_at     TIMESTAMPTZ  NOT NULL,
    created_by     VARCHAR(255),
    updated_by     VARCHAR(255),
    is_deleted     BOOLEAN      NOT NULL DEFAULT FALSE,
    version        BIGINT       NOT NULL DEFAULT 0,
    principal_type VARCHAR(30)  NOT NULL,
    principal_id   UUID         NOT NULL,
    role_id        UUID         NOT NULL,
    assigned_at    TIMESTAMPTZ  NOT NULL,
    assigned_by    UUID,
    CONSTRAINT pk_user_role PRIMARY KEY (id),
    -- NOTE: BaseEntity's @SQLRestriction("is_deleted = false") means a
    -- soft-deleted row still occupies its unique slot — the same accepted
    -- trade-off protocol_color_mapping already carries.
    CONSTRAINT uq_user_role UNIQUE (principal_type, principal_id, role_id),
    CONSTRAINT fk_user_role_role FOREIGN KEY (role_id) REFERENCES rbac_role(id)
);

CREATE INDEX idx_user_role_principal ON user_role (principal_type, principal_id);

-- =============================================================================
-- Envers audit-history shadow tables — required because BaseEntity is
-- @Audited at the mapped-superclass level, so every entity extending it is
-- audited whether or not this table exists. Omitting a shadow table makes
-- the very first insert on the base table fail with SQLGrammarException
-- (the exact failure mode V27's own comment documents).
-- =============================================================================

CREATE TABLE rbac_role_aud (
    id            UUID,
    rev           BIGINT NOT NULL,
    revtype       SMALLINT,
    created_at    TIMESTAMPTZ,
    updated_at    TIMESTAMPTZ,
    created_by    VARCHAR(255),
    updated_by    VARCHAR(255),
    is_deleted    BOOLEAN,
    version       BIGINT,
    name          VARCHAR(100),
    description   VARCHAR(500),
    CONSTRAINT pk_rbac_role_aud     PRIMARY KEY (id, rev),
    CONSTRAINT fk_rbac_role_aud_rev FOREIGN KEY (rev) REFERENCES revinfo(rev)
);

-- role_permission_aud must NOT reference rbac_role(id) — shadow tables carry
-- no other foreign keys and no indexes, matching how consent_aud carries no
-- FK on subject_id.
CREATE TABLE role_permission_aud (
    id            UUID,
    rev           BIGINT NOT NULL,
    revtype       SMALLINT,
    created_at    TIMESTAMPTZ,
    updated_at    TIMESTAMPTZ,
    created_by    VARCHAR(255),
    updated_by    VARCHAR(255),
    is_deleted    BOOLEAN,
    version       BIGINT,
    role_id       UUID,
    resource      VARCHAR(40),
    operation     VARCHAR(20),
    CONSTRAINT pk_role_permission_aud     PRIMARY KEY (id, rev),
    CONSTRAINT fk_role_permission_aud_rev FOREIGN KEY (rev) REFERENCES revinfo(rev)
);

CREATE TABLE user_role_aud (
    id             UUID,
    rev            BIGINT NOT NULL,
    revtype        SMALLINT,
    created_at     TIMESTAMPTZ,
    updated_at     TIMESTAMPTZ,
    created_by     VARCHAR(255),
    updated_by     VARCHAR(255),
    is_deleted     BOOLEAN,
    version        BIGINT,
    principal_type VARCHAR(30),
    principal_id   UUID,
    role_id        UUID,
    assigned_at    TIMESTAMPTZ,
    assigned_by    UUID,
    CONSTRAINT pk_user_role_aud     PRIMARY KEY (id, rev),
    CONSTRAINT fk_user_role_aud_rev FOREIGN KEY (rev) REFERENCES revinfo(rev)
);
