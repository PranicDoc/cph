package com.pranicdoc.cph.patient.model;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@NoArgsConstructor
@Data
@Entity
public class RegistrationLink {
    @Id
    private String uuid;
    private Instant expiresAt;
    @Enumerated(EnumType.STRING)
    private Status status;
    public enum Status {
        ACTIVE, IN_PROGRESS, USED
    }
}
