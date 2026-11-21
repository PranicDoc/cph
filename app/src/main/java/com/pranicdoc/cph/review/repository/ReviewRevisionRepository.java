package com.pranicdoc.cph.review.repository;

import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Modifying;
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

    // --- erase / purge (native: must also see soft-deleted rows and the _aud shadow tables) ---
    // Ids are selected as text and converted to UUID: a native UUID column comes back as byte[] on H2.

    @Query(value = "SELECT CAST(id AS VARCHAR(36)) FROM review_revision WHERE submission_id IN (:ids)", nativeQuery = true)
    List<UUID> findIdsBySubmissionIds(@Param("ids") Collection<UUID> ids);

    @Query(value = "SELECT file_url FROM review_revision WHERE file_url IS NOT NULL AND submission_id IN (:ids)", nativeQuery = true)
    List<String> findFileUrlsBySubmissionIds(@Param("ids") Collection<UUID> ids);

    @Query(value = "SELECT review_file_url FROM review_revision WHERE review_file_url IS NOT NULL AND submission_id IN (:ids)", nativeQuery = true)
    List<String> findReviewFileUrlsBySubmissionIds(@Param("ids") Collection<UUID> ids);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = "DELETE FROM review_revision_aud WHERE id IN (:ids)", nativeQuery = true)
    int eraseAuditByIds(@Param("ids") Collection<UUID> ids);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = "DELETE FROM review_revision WHERE id IN (:ids)", nativeQuery = true)
    int eraseByIds(@Param("ids") Collection<UUID> ids);
}
