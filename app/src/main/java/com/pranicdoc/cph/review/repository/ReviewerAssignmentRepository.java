package com.pranicdoc.cph.review.repository;

import com.pranicdoc.cph.review.model.ReviewerAssignment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface ReviewerAssignmentRepository extends JpaRepository<ReviewerAssignment, UUID> {

    Optional<ReviewerAssignment> findByHealerId(UUID healerId);
}
