package com.pranicdoc.cph.review.repository;

import com.pranicdoc.cph.review.model.ReviewComment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ReviewCommentRepository extends JpaRepository<ReviewComment, UUID> {

    List<ReviewComment> findBySubmissionIdOrderByCreatedAtAsc(UUID submissionId);
}
