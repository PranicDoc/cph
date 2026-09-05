-- Backfill: assign the baseline "Practitioner" role to every existing active
-- HEALER account that currently has no role assignment at all.
--
-- Safe to re-run (idempotent): the NOT EXISTS guards skip healers who already
-- hold any role, and the WHERE on the insert skips the exact pair if present.
--
-- Prereq: the app has started at least once so BaselineRoleSeeder has created
-- the "Practitioner" rbac_role row. Run this per environment, once, after
-- deploying the v5.0 RBAC branch. New healers created after that get the role
-- automatically via InviteService.acceptInvite.
--
-- Usage (psql):  psql "<conn-string>" -f scripts/backfill-practitioner-role.sql

BEGIN;

INSERT INTO user_role (id, principal_type, principal_id, role_id, assigned_at, assigned_by, is_deleted)
SELECT gen_random_uuid(),
       'HEALER',
       h.id,
       r.id,
       now(),
       NULL,
       false
FROM healer h
CROSS JOIN (SELECT id FROM rbac_role WHERE name = 'Practitioner' AND is_deleted = false) r
WHERE h.active = true
  AND h.is_deleted = false
  AND h.role = 'HEALER'
  AND NOT EXISTS (
        SELECT 1 FROM user_role ur
        WHERE ur.principal_type = 'HEALER'
          AND ur.principal_id = h.id
          AND ur.is_deleted = false
  );

-- Report what was assigned.
SELECT h.email, 'Practitioner' AS assigned_role
FROM user_role ur
JOIN healer h ON h.id = ur.principal_id
JOIN rbac_role r ON r.id = ur.role_id
WHERE r.name = 'Practitioner'
  AND ur.principal_type = 'HEALER'
  AND ur.is_deleted = false
ORDER BY h.email;

COMMIT;
