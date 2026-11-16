package com.pranicdoc.cph.auth.service;

import com.pranicdoc.cph.auth.dto.EraseAccountResponse;
import com.pranicdoc.cph.auth.repository.HealerInviteLinkRepository;
import com.pranicdoc.cph.auth.repository.HealerRepository;
import com.pranicdoc.cph.auth.repository.PasswordResetTokenRepository;
import com.pranicdoc.cph.common.audit.Auditable;
import com.pranicdoc.cph.common.exception.ResourceNotFoundException;
import com.pranicdoc.cph.common.security.SecurityContextUtil;
import com.pranicdoc.cph.healing.repository.HealerProtocolUnderstandingRepository;
import com.pranicdoc.cph.masterdata.repository.ChakraCatalogRepository;
import com.pranicdoc.cph.masterdata.repository.MasterDataElevationRepository;
import com.pranicdoc.cph.masterdata.repository.PranaColorCatalogRepository;
import com.pranicdoc.cph.masterdata.repository.ProtocolCatalogRepository;
import com.pranicdoc.cph.masterdata.repository.ProtocolChakraRepository;
import com.pranicdoc.cph.masterdata.repository.ProtocolColorMappingRepository;
import com.pranicdoc.cph.masterdata.repository.ProtocolImportJobRepository;
import com.pranicdoc.cph.notification.repository.FeedbackLinkRepository;
import com.pranicdoc.cph.patient.repository.PatientRepository;
import com.pranicdoc.cph.patient.service.RecordPurger;
import com.pranicdoc.cph.review.repository.ReviewCommentRepository;
import com.pranicdoc.cph.review.repository.ReviewSubmissionRepository;
import com.pranicdoc.cph.review.repository.ReviewerAssignmentRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.cache.CacheManager;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

/**
 * Erases a healer account for good: their patients and everything of theirs (via {@link RecordPurger}),
 * their personal rows, the account itself and its credit-service account. Master data (chakras,
 * colours, protocols) they added is shared with other healers, so it is handed to another super admin
 * rather than deleted. Consent records and the audit log are kept (legal evidence).
 *
 * <p>The tables this touches are declared in {@link EraseCoverage}; add a new healer-owned table there
 * and here ({@code EraseCoverageGuardTest} fails the build until you do).
 *
 * <p>Deactivating a healer (reversible) stays a separate action; this one cannot be undone.
 */
@Service
@Slf4j
public class HealerEraseService {

    /** Tables this class erases from, re-points or hands over (the patient tables are in {@link RecordPurger}). */
    public static final List<String> HEALER_TABLES = List.of(
            "healer", "password_reset_token", "protocol_import_job", "master_data_elevation", "user_role",
            "reviewer_assignment", "healer_invite_links", "chakra_catalog", "prana_color_catalog",
            "protocol_catalog", "protocol_chakra", "protocol_color_mapping");

    private final HealerRepository healers;
    private final PatientRepository patients;
    private final RecordPurger purger;
    private final PasswordResetTokenRepository passwordResetTokens;
    private final ProtocolImportJobRepository importJobs;
    private final FeedbackLinkRepository feedbackLinks;
    private final MasterDataElevationRepository elevations;
    private final HealerProtocolUnderstandingRepository understandings;
    private final HealerInviteLinkRepository inviteLinks;
    private final ReviewSubmissionRepository submissions;
    private final ReviewCommentRepository comments;
    private final ReviewerAssignmentRepository assignments;
    private final ChakraCatalogRepository chakras;
    private final PranaColorCatalogRepository colors;
    private final ProtocolCatalogRepository protocols;
    private final ProtocolChakraRepository protocolChakras;
    private final ProtocolColorMappingRepository protocolColorMappings;
    private final SecurityContextUtil securityContextUtil;
    private final RestClient creditServiceRestClient;
    private final TransactionTemplate tx;
    private final CacheManager cacheManager;

    // One argument per repository: the erase plan below is a plain list of what it touches.
    @SuppressWarnings("java:S107")
    public HealerEraseService(HealerRepository healers, PatientRepository patients, RecordPurger purger,
                              PasswordResetTokenRepository passwordResetTokens, ProtocolImportJobRepository importJobs,
                              FeedbackLinkRepository feedbackLinks, MasterDataElevationRepository elevations,
                              HealerProtocolUnderstandingRepository understandings,
                              HealerInviteLinkRepository inviteLinks, ReviewSubmissionRepository submissions,
                              ReviewCommentRepository comments, ReviewerAssignmentRepository assignments,
                              ChakraCatalogRepository chakras, PranaColorCatalogRepository colors,
                              ProtocolCatalogRepository protocols, ProtocolChakraRepository protocolChakras,
                              ProtocolColorMappingRepository protocolColorMappings,
                              SecurityContextUtil securityContextUtil,
                              @Qualifier("creditServiceRestClient") RestClient creditServiceRestClient,
                              TransactionTemplate tx, CacheManager cacheManager) {
        this.healers = healers;
        this.patients = patients;
        this.purger = purger;
        this.passwordResetTokens = passwordResetTokens;
        this.importJobs = importJobs;
        this.feedbackLinks = feedbackLinks;
        this.elevations = elevations;
        this.understandings = understandings;
        this.inviteLinks = inviteLinks;
        this.submissions = submissions;
        this.comments = comments;
        this.assignments = assignments;
        this.chakras = chakras;
        this.colors = colors;
        this.protocols = protocols;
        this.protocolChakras = protocolChakras;
        this.protocolColorMappings = protocolColorMappings;
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
        HealerRepository.HealerIdentity healer = healers.findIdentity(healerId)
                .stream().findFirst().orElseThrow(() -> new ResourceNotFoundException("Healer", healerId));
        String email = String.valueOf(healer.getEmail());
        if (confirmation == null || !confirmation.trim().equalsIgnoreCase(email)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Type the account's e-mail address to confirm");
        }
        UUID heir = otherSuperAdmin(healerId);
        if ("SUPADM".equals(healer.getRole()) && heir == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Cannot erase the last super admin account");
        }

        for (UUID patientId : patients.findIdsByHealerId(healerId)) {
            purger.purgePatient(patientId);
        }
        // Defensive: review threads the healer opened on a case that is not one of their patients'.
        purger.purgeReviewSubmissionsOfHealer(healerId);
        detachFromReviews(healerId, email);
        // Rows that are only meaningful to the healer: erased with them.
        passwordResetTokens.eraseByHealerId(healerId);
        importJobs.eraseByHealerId(healerId);
        feedbackLinks.eraseByHealerId(healerId);
        elevations.eraseByHealerId(healerId);
        understandings.eraseByHealerId(healerId);
        healers.eraseUserRolesById(healerId);
        inviteLinks.clearInviter(healerId);
        if (heir != null) {
            // Shared master data they added moves to another super admin.
            chakras.handOver(healerId, heir);
            colors.handOver(healerId, heir);
            protocols.handOver(healerId, heir);
            protocolChakras.handOver(healerId, heir);
            protocolColorMappings.handOver(healerId, heir);
        }
        healers.eraseAuditById(healerId);
        healers.eraseById(healerId);
    }

    /**
     * The healer as an instructor: their healers' submissions stay, so the reviewer and comment-author
     * links to them are cleared (the submission then waits for a newly linked instructor, and their
     * comments read "Deleted user"), their e-mail is wiped from those rows' audit columns, and every
     * assignment they are part of is removed.
     */
    private void detachFromReviews(UUID healerId, String email) {
        submissions.clearReviewer(healerId);
        comments.clearAuthor(healerId);
        // The kept rows also carry the healer's e-mail as created_by / updated_by (and in their history copies).
        submissions.clearCreatedBy(email);
        submissions.clearUpdatedBy(email);
        submissions.clearCreatedByInHistory(email);
        submissions.clearUpdatedByInHistory(email);
        comments.clearCreatedBy(email);
        comments.clearUpdatedBy(email);
        comments.clearCreatedByInHistory(email);
        comments.clearUpdatedByInHistory(email);
        assignments.eraseAuditOfHealer(healerId);
        assignments.eraseOfHealer(healerId);
    }

    private UUID otherSuperAdmin(UUID except) {
        return healers.findOtherSuperAdminIds(except).stream().findFirst().orElse(null);
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
