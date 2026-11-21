package com.pranicdoc.cph.review.repository;

import java.util.Collection;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Modifying;
import com.pranicdoc.cph.review.model.ReviewComment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ReviewCommentRepository extends JpaRepository<ReviewComment, UUID> {

    List<ReviewComment> findBySubmissionIdOrderByCreatedAtAsc(UUID submissionId);

    // --- erase / purge (native: must also see soft-deleted rows and the _aud shadow tables) ---

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = "UPDATE review_comment SET parent_id = NULL WHERE submission_id IN (:ids)", nativeQuery = true)
    int unlinkParentsBySubmissionIds(@Param("ids") Collection<UUID> ids);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = "DELETE FROM review_comment_aud WHERE id IN (SELECT id FROM review_comment WHERE submission_id IN (:ids))", nativeQuery = true)
    int eraseAuditBySubmissionIds(@Param("ids") Collection<UUID> ids);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = "DELETE FROM review_comment WHERE submission_id IN (:ids)", nativeQuery = true)
    int eraseBySubmissionIds(@Param("ids") Collection<UUID> ids);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = "UPDATE review_comment SET parent_id = NULL WHERE revision_id IN (:ids)", nativeQuery = true)
    int unlinkParentsByRevisionIds(@Param("ids") Collection<UUID> ids);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = "DELETE FROM review_comment_aud WHERE id IN (SELECT id FROM review_comment WHERE revision_id IN (:ids))", nativeQuery = true)
    int eraseAuditByRevisionIds(@Param("ids") Collection<UUID> ids);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = "DELETE FROM review_comment WHERE revision_id IN (:ids)", nativeQuery = true)
    int eraseByRevisionIds(@Param("ids") Collection<UUID> ids);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = "UPDATE review_comment SET author_id = NULL WHERE author_id = :healerId", nativeQuery = true)
    int clearAuthor(@Param("healerId") UUID healerId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = "UPDATE review_comment SET created_by = NULL WHERE lower(created_by) = lower(:email)", nativeQuery = true)
    int clearCreatedBy(@Param("email") String email);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = "UPDATE review_comment SET updated_by = NULL WHERE lower(updated_by) = lower(:email)", nativeQuery = true)
    int clearUpdatedBy(@Param("email") String email);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = "UPDATE review_comment_aud SET created_by = NULL WHERE lower(created_by) = lower(:email)", nativeQuery = true)
    int clearCreatedByInHistory(@Param("email") String email);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = "UPDATE review_comment_aud SET updated_by = NULL WHERE lower(updated_by) = lower(:email)", nativeQuery = true)
    int clearUpdatedByInHistory(@Param("email") String email);
}
