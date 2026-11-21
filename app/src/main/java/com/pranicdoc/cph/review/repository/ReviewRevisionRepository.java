package com.pranicdoc.cph.review.repository;

import com.pranicdoc.cph.review.model.ReviewRevision;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ReviewRevisionRepository extends JpaRepository<ReviewRevision, UUID> {

    List<ReviewRevision> findBySubmissionIdOrderByRevisionNoAsc(UUID submissionId);

    Optional<ReviewRevision> findTopBySubmissionIdOrderByRevisionNoDesc(UUID submissionId);

    List<ReviewRevision> findBySubmissionIdIn(Collection<UUID> submissionIds);

    Optional<ReviewRevision> findBySubmissionIdAndRevisionNo(UUID submissionId, int revisionNo);
}
