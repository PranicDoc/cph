package com.pranicdoc.cph.masterdata.service;

import com.pranicdoc.cph.auth.model.Healer;
import com.pranicdoc.cph.auth.repository.HealerRepository;
import com.pranicdoc.cph.common.security.SecurityContextUtil;
import com.pranicdoc.cph.masterdata.model.MasterDataElevation;
import com.pranicdoc.cph.masterdata.repository.MasterDataElevationRepository;
import com.pranicdoc.cph.rbac.model.Resource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MasterDataElevationServiceTest {

    @Mock private MasterDataElevationRepository elevationRepository;
    @Mock private HealerRepository healerRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private SecurityContextUtil securityContextUtil;
    @InjectMocks private MasterDataElevationService service;

    private final UUID healerId = UUID.randomUUID();
    private final Healer healer = new Healer();

    @BeforeEach
    void setUp() {
        healer.setPasswordHash("hash");
        ReflectionTestUtils.setField(service, "elevationMinutes", 5);
        lenient().when(securityContextUtil.getCurrentHealerId()).thenReturn(healerId);
        lenient().when(healerRepository.findById(healerId)).thenReturn(Optional.of(healer));
    }

    @Test
    void theRightPasswordUnlocksTheTypeForTheConfiguredMinutes() {
        when(passwordEncoder.matches("secret", "hash")).thenReturn(true);

        Instant before = Instant.now();
        Instant expiresAt = service.elevate(Resource.MASTER_DATA_CHAKRAS, "secret");

        ArgumentCaptor<MasterDataElevation> saved = ArgumentCaptor.forClass(MasterDataElevation.class);
        verify(elevationRepository).save(saved.capture());
        assertThat(saved.getValue().getHealerId()).isEqualTo(healerId);
        assertThat(saved.getValue().getResource()).isEqualTo(Resource.MASTER_DATA_CHAKRAS);
        assertThat(saved.getValue().getExpiresAt()).isEqualTo(expiresAt);
        assertThat(expiresAt).isBetween(before.plus(Duration.ofMinutes(5)), Instant.now().plus(Duration.ofMinutes(5)));
    }

    @Test
    void aWrongPasswordUnlocksNothing() {
        when(passwordEncoder.matches("wrong", "hash")).thenReturn(false);

        assertThatThrownBy(() -> service.elevate(Resource.MASTER_DATA_CHAKRAS, "wrong"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Password is incorrect");
        verify(elevationRepository, never()).save(any());
    }

    @Test
    void anAccountWithoutAPasswordIsAskedToSetOneFirst() {
        healer.setPasswordHash(null);

        assertThatThrownBy(() -> service.elevate(Resource.MASTER_DATA_COLORS, "anything"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Set a password in your profile first");
        verify(elevationRepository, never()).save(any());
    }

    @Test
    void onlyUnexpiredUnlocksCount() {
        Instant later = Instant.now().plus(Duration.ofMinutes(3));
        when(elevationRepository.findByHealerIdAndExpiresAtAfter(any(), any())).thenReturn(List.of(
                MasterDataElevation.builder().healerId(healerId).resource(Resource.MASTER_DATA_COLORS).expiresAt(later).build()));

        assertThat(service.activeFor(healerId)).containsOnlyKeys("MASTER_DATA_COLORS");
        assertThat(service.isActive(null, Resource.MASTER_DATA_COLORS)).isFalse();
    }
}
