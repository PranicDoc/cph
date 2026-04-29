package com.pranicdoc.cph.clinic.model;

import com.pranicdoc.cph.common.model.BaseEntity;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.envers.Audited;

/**
 * Clinic entity — represents a pranic healing center (tenant).
 * NOT tenant-scoped itself (it IS the tenant). Extends BaseEntity directly.
 */
@Entity
@Table(name = "clinic")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Audited
public class Clinic extends BaseEntity {

    @Column(name = "code", nullable = false, unique = true, length = 20)
    private String code;   // e.g. "CHNY01" — used in JWT/display

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "phone")
    private String phone;

    @Column(name = "email")
    private String email;

    @Column(name = "address_line1")
    private String addressLine1;

    @Column(name = "address_line2")
    private String addressLine2;

    @Column(name = "city")
    private String city;

    @Column(name = "state")
    private String state;

    @Column(name = "zip", length = 10)
    private String zip;

    @Column(name = "active", nullable = false)
    @Builder.Default
    private boolean active = true;
}
