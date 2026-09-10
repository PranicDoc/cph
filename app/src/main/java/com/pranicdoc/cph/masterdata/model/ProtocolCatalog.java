package com.pranicdoc.cph.masterdata.model;

import com.pranicdoc.cph.common.enums.ProtocolType;
import com.pranicdoc.cph.common.model.BaseEntity;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.envers.Audited;

import java.util.UUID;

/**
 * ProtocolCatalog — master list of named pranic-healing protocols/techniques,
 * sourced from the reference books (bulk-imported via xlsx/csv).
 * healer_id NULL = system-level (all imported rows); non-null = healer-custom entry.
 */
@Entity
@Table(name = "protocol_catalog",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_protocol_catalog_name_book_page",
                columnNames = {"name", "source_book", "page_number"}))
@SQLRestriction("is_deleted = false")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Audited
public class ProtocolCatalog extends BaseEntity {

    @Column(name = "name", nullable = false)
    private String name; // "Protocol/Technique Name"

    @Column(name = "source_book", nullable = false)
    private String sourceBook; // "Book"

    @Column(name = "page_number", nullable = false)
    private Integer pageNumber; // "Page"

    @Column(name = "category")
    private String category; // "Category" — kept verbatim, not used for PROTOCOL/SUPPLEMENTARY

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "chapter")
    private String chapter;

    @Enumerated(EnumType.STRING)
    @Column(name = "protocol_type", nullable = false, length = 20)
    private ProtocolType protocolType;

    @Column(name = "healer_id", columnDefinition = "uuid")
    private UUID healerId; // NULL = system-level, non-null = healer-custom
}
