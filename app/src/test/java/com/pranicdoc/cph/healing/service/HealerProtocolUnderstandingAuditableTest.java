package com.pranicdoc.cph.healing.service;

import com.pranicdoc.cph.common.audit.Auditable;
import com.pranicdoc.cph.healing.dto.HealerProtocolUnderstandingRequest;
import com.pranicdoc.cph.healing.service.impl.HealerProtocolUnderstandingService;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class HealerProtocolUnderstandingAuditableTest {

    @Test
    void saveFromHealerSelection_isAuditedWithDedicatedAction() throws NoSuchMethodException {
        Auditable auditable = HealerProtocolUnderstandingService.class
                .getMethod("saveFromHealerSelection", UUID.class, HealerProtocolUnderstandingRequest.class)
                .getAnnotation(Auditable.class);

        assertNotNull(auditable, "saving the healer's protocol understanding must be @Auditable");
        assertEquals("SAVE_HEALER_PROTOCOL_UNDERSTANDING", auditable.action());
    }
}
