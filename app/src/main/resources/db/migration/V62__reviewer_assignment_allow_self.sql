-- =============================================================================
-- V62__reviewer_assignment_allow_self.sql
-- A super admin may be their own reviewer. "Only a super admin may self-assign"
-- depends on the account's role, so ReviewerAssignmentService is now the guard.
-- =============================================================================

ALTER TABLE reviewer_assignment DROP CONSTRAINT ck_reviewer_assignment_not_self;
