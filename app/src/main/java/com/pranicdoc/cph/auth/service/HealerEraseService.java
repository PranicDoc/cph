package com.pranicdoc.cph.auth.service;

import com.pranicdoc.cph.auth.dto.EraseAccountResponse;
import com.pranicdoc.cph.common.audit.Auditable;
import com.pranicdoc.cph.common.exception.ResourceNotFoundException;
import com.pranicdoc.cph.common.security.SecurityContextUtil;
import com.pranicdoc.cph.patient.service.RecordPurger;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.cache.CacheManager;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Erases a healer account for good: their patients and everything of theirs (via {@link RecordPurger}),
 * their personal rows, the account itself and its credit-service account. Master data (chakras,
 * colours, protocols) they added is shared with other healers, so it is handed to another super admin
 * rather than deleted. Consent records and the audit log are kept (legal evidence).
 *
 * <p>Deactivating a healer (reversible) stays a separate action; this one cannot be undone.
 */
@Service
@Slf4j
public class HealerEraseService {

    /** Shared master data tables a healer may own; their rows move to another super admin. */
    private static final List<String> HANDED_OVER = List.of(
            "chakra_catalog", "prana_color_catalog", "protocol_catalog", "protocol_chakra",
            "protocol_color_mapping");

    /** Tables whose rows are only meaningful to the healer: erased with them. */
    private static final List<String> PERSONAL = List.of(
            "password_reset_token", "protocol_import_job", "feedback_link", "master_data_elevation",
            "healer_protocol_understanding");

    private final JdbcTemplate jdbc;
    private final RecordPurger purger;
    private final SecurityContextUtil securityContextUtil;
    private final RestClient creditServiceRestClient;
    private final TransactionTemplate tx;
    private final CacheManager cacheManager;

    public HealerEraseService(JdbcTemplate jdbc, RecordPurger purger, SecurityContextUtil securityContextUtil,
                              @Qualifier("creditServiceRestClient") RestClient creditServiceRestClient,
                              TransactionTemplate tx, CacheManager cacheManager) {
        this.jdbc = jdbc;
        this.purger = purger;
        this.securityContextUtil = securityContextUtil;
        this.creditServiceRestClient = creditServiceRestClient;
        this.tx = tx;
        this.cacheManager = cacheManager;
    }

    /** A healer erases their own account. */
    @Auditable(action = "ERASE_OWN_ACCOUNT")
    public EraseAccountResponse eraseOwn(String confirmation) {
        return erase(securityContextUtil.getCurrentHealerId(), confirmation);
    }

    /** A super admin erases any healer's account. */
    @Auditable(action = "ERASE_HEALER_ACCOUNT")
    public EraseAccountResponse eraseAsAdmin(UUID healerId, String confirmation) {
        if (!"SUPADM".equals(securityContextUtil.getCurrentRole())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only a super admin can do this");
        }
        return erase(healerId, confirmation);
    }

    EraseAccountResponse erase(UUID healerId, String confirmation) {
        tx.executeWithoutResult(status -> eraseRecords(healerId, confirmation));
        var cache = cacheManager.getCache("healerActiveStatus");
        if (cache != null) {
            cache.evict(healerId);
        }
        return new EraseAccountResponse(eraseCreditAccount(healerId));
    }

    private void eraseRecords(UUID healerId, String confirmation) {
        Map<String, Object> healer = jdbc.queryForList("SELECT email, role FROM healer WHERE id = ?", healerId)
                .stream().findFirst().orElseThrow(() -> new ResourceNotFoundException("Healer", healerId));
        String email = String.valueOf(healer.get("email"));
        if (confirmation == null || !confirmation.trim().equalsIgnoreCase(email)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Type the account's e-mail address to confirm");
        }
        UUID heir = otherSuperAdmin(healerId);
        if ("SUPADM".equals(healer.get("role")) && heir == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Cannot erase the last super admin account");
        }

        for (UUID patientId : jdbc.queryForList("SELECT id FROM patient WHERE healer_id = ?", UUID.class, healerId)) {
            purger.purgePatient(patientId);
        }
        // Defensive: review threads the healer opened on a case that is not one of their patients'.
        purger.purgeReviewSubmissionsOfHealer(healerId);
        detachFromReviews(healerId, email);
        for (String table : PERSONAL) {
            jdbc.update("DELETE FROM " + table + " WHERE healer_id = ?", healerId);
        }
        jdbc.update("DELETE FROM user_role WHERE principal_id = ?", healerId);
        jdbc.update("UPDATE healer_invite_links SET invited_by_healer_id = NULL WHERE invited_by_healer_id = ?", healerId);
        if (heir != null) {
            for (String table : HANDED_OVER) {
                jdbc.update("UPDATE " + table + " SET healer_id = ? WHERE healer_id = ?", heir, healerId);
            }
        }
        jdbc.update("DELETE FROM healer_aud WHERE id = ?", healerId);
        jdbc.update("DELETE FROM healer WHERE id = ?", healerId);
    }

    /**
     * The healer as an instructor: their healers' submissions stay, so the reviewer and comment-author
     * links to them are cleared (the submission then waits for a newly linked instructor, and their
     * comments read "Deleted user"), their e-mail is wiped from those rows' audit columns, and every assignment they are part of is removed.
     */
    private void detachFromReviews(UUID healerId, String email) {
        jdbc.update("UPDATE review_submission SET reviewer_id = NULL WHERE reviewer_id = ?", healerId);
        jdbc.update("UPDATE review_comment SET author_id = NULL WHERE author_id = ?", healerId);
        // The kept rows also carry the healer's e-mail as created_by / updated_by (and in their history copies).
        for (String table : List.of("review_submission", "review_comment", "review_submission_aud", "review_comment_aud")) {
            jdbc.update("UPDATE " + table + " SET created_by = NULL WHERE lower(created_by) = lower(?)", email);
            jdbc.update("UPDATE " + table + " SET updated_by = NULL WHERE lower(updated_by) = lower(?)", email);
        }
        jdbc.update("DELETE FROM reviewer_assignment_aud WHERE healer_id = ? OR reviewer_id = ? OR id IN ("
                + "SELECT id FROM reviewer_assignment WHERE healer_id = ? OR reviewer_id = ?)",
                healerId, healerId, healerId, healerId);
        jdbc.update("DELETE FROM reviewer_assignment WHERE healer_id = ? OR reviewer_id = ?", healerId, healerId);
    }

    private UUID otherSuperAdmin(UUID except) {
        return jdbc.queryForList(
                        "SELECT id FROM healer WHERE role = 'SUPADM' AND is_deleted = false AND id <> ? ORDER BY created_at LIMIT 1",
                        UUID.class, except)
                .stream().findFirst().orElse(null);
    }

    /** Best effort after the records are gone; the credit service call is safe to repeat. */
    private boolean eraseCreditAccount(UUID healerId) {
        try {
            creditServiceRestClient.delete().uri("/credits/admin/accounts/HEALER/{id}", healerId)
                    .retrieve().toBodilessEntity();
            return true;
        } catch (RuntimeException e) {
            log.error("Healer {} was erased but their credit account could not be removed - remove it by hand", healerId, e);
            return false;
        }
    }
}
