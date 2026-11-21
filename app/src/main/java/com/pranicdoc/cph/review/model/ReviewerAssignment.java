package com.pranicdoc.cph.review.model;

import com.pranicdoc.cph.common.model.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.envers.Audited;

import java.util.UUID;

/**
 * Links a healer to the instructor who reviews their cases. At most one row per healer;
 * an unlink deletes the row (a soft delete would keep the unique healer_id slot occupied).
 */
@Entity
@Table(name = "reviewer_assignment")
@SQLRestriction("is_deleted = false")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Audited
public class ReviewerAssignment extends BaseEntity {

    @Column(name = "healer_id", nullable = false, updatable = false, unique = true)
    private UUID healerId;

    @Column(name = "reviewer_id", nullable = false)
    private UUID reviewerId;
}
