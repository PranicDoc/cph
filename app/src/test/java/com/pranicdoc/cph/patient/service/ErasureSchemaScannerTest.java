package com.pranicdoc.cph.patient.service;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ErasureSchemaScannerTest {

    @Test
    void aGrandchildTableIsCaughtThroughItsParent() {
        var schema = ErasureSchemaScanner.scan(List.of(
                "CREATE TABLE healing_case (id UUID PRIMARY KEY, patient_id UUID REFERENCES patient(id));"
                        + "CREATE TABLE review_submission (id UUID, case_id UUID, CONSTRAINT fk FOREIGN KEY (case_id) REFERENCES healing_case(id));",
                "CREATE TABLE review_revision (id UUID, submission_id UUID REFERENCES review_submission(id));"
                        + "CREATE TABLE review_attachment (id UUID, revision_id UUID REFERENCES review_revision(id));"
                        + "CREATE TABLE unrelated (id UUID, x UUID REFERENCES other(id));"
                        + "ALTER TABLE late_child ADD CONSTRAINT fk2 FOREIGN KEY (a) REFERENCES review_attachment(id);",
                "CREATE TABLE late_child (id UUID);"));

        assertThat(schema.ownedTables()).contains("review_submission", "review_revision", "review_attachment",
                "late_child");
        assertThat(schema.ownedTables()).doesNotContain("unrelated");
    }

    @Test
    void auditShadowTablesFollowTheirParentAndDroppedTablesDisappear() {
        var schema = ErasureSchemaScanner.scan(List.of(
                "CREATE TABLE a_aud (id UUID, p UUID REFERENCES patient(id));"
                        + "CREATE TABLE gone (id UUID, p UUID REFERENCES patient(id));"
                        + "-- CREATE TABLE commented (p UUID REFERENCES patient(id));",
                "DROP TABLE gone;"));

        assertThat(schema.ownedTables()).doesNotContain("a_aud", "gone", "commented");
        assertThat(schema.existingTables()).doesNotContain("gone");
    }
}
