package com.pranicdoc.cph.auth.service.impl;

import com.pranicdoc.cph.auth.model.Healer;
import com.pranicdoc.cph.auth.repository.HealerRepository;
import com.pranicdoc.cph.common.enums.Role;
import com.pranicdoc.cph.common.exception.ResourceNotFoundException;
import com.pranicdoc.cph.common.security.SecurityContextUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit coverage for the two CR-01 guards added to {@link HealerServiceImpl#removeHealer(UUID)}:
 * self-delete refusal (any role) and last-SUPADM refusal, plus the happy path and the
 * pre-existing 404. Plain Mockito — no Spring context, so {@code @Transactional}/{@code @CacheEvict}
 * on the method are inert here, which is exactly what we want to isolate the guard logic.
 */
@ExtendWith(MockitoExtension.class)
class HealerServiceImplRemoveHealerTest {

    private static final UUID SELF_ID = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
    private static final UUID ADMIN_ID = UUID.fromString("00000000-0000-0000-0000-0000000000b2");
    private static final UUID SUP_ID = UUID.fromString("00000000-0000-0000-0000-0000000000c3");
    private static final UUID OTHER_ID = UUID.fromString("00000000-0000-0000-0000-0000000000d4");
    private static final UUID GHOST_ID = UUID.fromString("00000000-0000-0000-0000-0000000000e5");

    @Mock
    private HealerRepository healerRepository;

    @Mock
    private SecurityContextUtil securityContextUtil;

    @InjectMocks
    private HealerServiceImpl service;

    private static Healer healer(UUID id, Role role) {
        Healer h = Healer.builder().role(role).build();
        h.setId(id);
        return h;
    }

    @Test
    void removeHealer_ownAccount_throwsAccessDeniedAndDoesNotDelete() {
        Healer self = healer(SELF_ID, Role.HEALER);
        when(healerRepository.findById(SELF_ID)).thenReturn(Optional.of(self));
        when(securityContextUtil.getCurrentHealerId()).thenReturn(SELF_ID);

        assertThrows(AccessDeniedException.class, () -> service.removeHealer(SELF_ID));

        verify(healerRepository, never()).save(any());
        assertFalse(self.isDeleted(), "self healer must not be soft-deleted");
    }

    @Test
    void removeHealer_targetIsLastSupadm_throwsAccessDeniedAndDoesNotDelete() {
        Healer sup = healer(SUP_ID, Role.SUPADM);
        when(healerRepository.findById(SUP_ID)).thenReturn(Optional.of(sup));
        when(securityContextUtil.getCurrentHealerId()).thenReturn(ADMIN_ID);
        when(healerRepository.countByRole(Role.SUPADM)).thenReturn(1L);

        assertThrows(AccessDeniedException.class, () -> service.removeHealer(SUP_ID));

        verify(healerRepository, never()).save(any());
        assertFalse(sup.isDeleted(), "the last SUPADM must not be soft-deleted");
    }

    @Test
    void removeHealer_targetIsSupadmButNotLast_softDeletesAndSaves() {
        Healer sup = healer(SUP_ID, Role.SUPADM);
        when(healerRepository.findById(SUP_ID)).thenReturn(Optional.of(sup));
        when(securityContextUtil.getCurrentHealerId()).thenReturn(ADMIN_ID);
        when(healerRepository.countByRole(Role.SUPADM)).thenReturn(2L);

        service.removeHealer(SUP_ID);

        verify(healerRepository).save(sup);
        assertTrue(sup.isDeleted(), "a non-last SUPADM is still removable");
    }

    @Test
    void removeHealer_ordinaryHealer_softDeletesAndSaves() {
        Healer other = healer(OTHER_ID, Role.HEALER);
        when(healerRepository.findById(OTHER_ID)).thenReturn(Optional.of(other));
        when(securityContextUtil.getCurrentHealerId()).thenReturn(ADMIN_ID);

        service.removeHealer(OTHER_ID);

        verify(healerRepository).save(other);
        assertTrue(other.isDeleted(), "an ordinary healer is soft-deleted");
        verify(healerRepository, never()).countByRole(any());
    }

    @Test
    void removeHealer_unknownId_throwsResourceNotFound() {
        when(healerRepository.findById(GHOST_ID)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> service.removeHealer(GHOST_ID));

        verifyNoInteractions(securityContextUtil);
        verify(healerRepository, never()).save(any());
    }
}
