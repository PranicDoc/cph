package com.pranicdoc.cph.rbac.baseline;

import com.pranicdoc.cph.rbac.model.Operation;
import com.pranicdoc.cph.rbac.model.Resource;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;

class BaselineRoleTest {

    @Test
    void practitionerBaseline_doesNotGrantCaseReview() {
        Set<Operation> caseOps = BaselineRole.GRANTS.getOrDefault(Resource.CASE, Set.of());
        assertFalse(caseOps.contains(Operation.REVIEW),
                "CASE:REVIEW is an instructor permission and must not be in the Practitioner baseline");
    }
}
