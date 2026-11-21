package com.pranicdoc.cph.auth.service;

import com.pranicdoc.cph.auth.repository.HealerInviteLinkRepository;
import com.pranicdoc.cph.auth.repository.HealerRepository;
import com.pranicdoc.cph.auth.repository.PasswordResetTokenRepository;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.cache.CacheManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class HealerEraseServiceTest {

    @Mock private HealerRepository healers;
    @Mock private PatientRepository patients;
    @Mock private RecordPurger purger;
    @Mock private PasswordResetTokenRepository passwordResetTokens;
    @Mock private ProtocolImportJobRepository importJobs;
    @Mock private FeedbackLinkRepository feedbackLinks;
    @Mock private MasterDataElevationRepository elevations;
    @Mock private HealerProtocolUnderstandingRepository understandings;
    @Mock private HealerInviteLinkRepository inviteLinks;
    @Mock private ReviewSubmissionRepository submissions;
    @Mock private ReviewCommentRepository comments;
    @Mock private ReviewerAssignmentRepository assignments;
    @Mock private ChakraCatalogRepository chakras;
    @Mock private PranaColorCatalogRepository colors;
    @Mock private ProtocolCatalogRepository protocols;
    @Mock private ProtocolChakraRepository protocolChakras;
    @Mock private ProtocolColorMappingRepository protocolColorMappings;
    @Mock private SecurityContextUtil security;
    @Mock private RestClient credit;
    @Mock private TransactionTemplate tx;
    @Mock private CacheManager cacheManager;
    @InjectMocks private HealerEraseService service;

    private final UUID healerId = UUID.randomUUID();

    @BeforeEach
    void runTransactionBodies() {
        org.mockito.Mockito.doAnswer(inv -> {
            java.util.function.Consumer<Object> body = inv.getArgument(0);
            body.accept(null);
            return null;
        }).when(tx).executeWithoutResult(any());
    }

    private void healerRow(String role) {
        HealerRepository.HealerIdentity identity = new HealerRepository.HealerIdentity() {
            @Override
            public String getEmail() {
                return "asha@example.com";
            }

            @Override
            public String getRole() {
                return role;
            }
        };
        when(healers.findIdentity(healerId)).thenReturn(List.of(identity));
    }

    @Test
    void aWrongConfirmationErasesNothing() {
        healerRow("PRACTITIONER");
        when(security.getCurrentHealerId()).thenReturn(healerId);

        assertThrows(ResponseStatusException.class, () -> service.eraseOwn("someone@else.com"));

        verify(healers).findIdentity(healerId);
        org.mockito.Mockito.verifyNoMoreInteractions(healers);
        org.mockito.Mockito.verifyNoInteractions(purger, patients, passwordResetTokens, importJobs, feedbackLinks,
                elevations, understandings, inviteLinks, submissions, comments, assignments, chakras, colors,
                protocols, protocolChakras, protocolColorMappings);
    }

    @Test
    void theLastSuperAdminCannotBeErased() {
        healerRow("SUPADM");
        when(security.getCurrentHealerId()).thenReturn(healerId);
        when(healers.findOtherSuperAdminIds(healerId)).thenReturn(List.of());

        assertThrows(ResponseStatusException.class, () -> service.eraseOwn("asha@example.com"));

        verify(purger, never()).purgePatient(any());
        verify(healers, never()).eraseById(any());
    }

    @Test
    void onlyASuperAdminCanEraseSomeoneElse() {
        when(security.getCurrentRole()).thenReturn("PRACTITIONER");

        assertThrows(ResponseStatusException.class, () -> service.eraseAsAdmin(healerId, "asha@example.com"));

        verify(healers, never()).findIdentity(any());
    }
}
