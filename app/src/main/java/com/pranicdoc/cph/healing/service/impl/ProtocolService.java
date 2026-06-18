package com.pranicdoc.cph.healing.service.impl;

import com.pranicdoc.cph.common.audit.Auditable;
import com.pranicdoc.cph.healing.dto.ProtocolRequest;
import com.pranicdoc.cph.healing.model.HealingSession;
import com.pranicdoc.cph.healing.model.ProtocolColor;
import com.pranicdoc.cph.healing.model.ProtocolEntry;
import com.pranicdoc.cph.healing.repository.ProtocolEntryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class ProtocolService {

    private final ProtocolEntryRepository protocolEntryRepository;
    private final SessionService sessionService;

    /**
     * Save protocol entries with their associated colors for a session.
     */
    @Auditable(action = "SAVE_PROTOCOL")
    public void saveProtocol(UUID sessionId, List<ProtocolRequest> requests) {
        HealingSession session = sessionService.findOrThrow(sessionId);

        for (ProtocolRequest req : requests) {
            ProtocolEntry entry = ProtocolEntry.builder()
                    .healingSession(session)
                    .chakraId(req.chakraId())
                    .targetArea(req.targetArea())
                    .technique(req.technique())
                    .notes(req.notes())
                    .build();

            for (ProtocolRequest.ProtocolColorRequest colorReq : req.colors()) {
                ProtocolColor color = ProtocolColor.builder()
                        .protocolEntry(entry)
                        .colorId(colorReq.colorId())
                        .usageType(colorReq.usageType())
                        .build();
                entry.getColors().add(color);
            }

            protocolEntryRepository.save(entry);
        }
    }
}
