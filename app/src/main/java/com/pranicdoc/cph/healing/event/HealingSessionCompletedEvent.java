package com.pranicdoc.cph.healing.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

import java.util.UUID;

@Getter
public class HealingSessionCompletedEvent extends ApplicationEvent {

    private final UUID healingCaseId;
    private final UUID clinicId;

    public HealingSessionCompletedEvent(Object source, UUID healingCaseId, UUID clinicId) {
        super(source);
        this.healingCaseId = healingCaseId;
        this.clinicId = clinicId;
    }

}
