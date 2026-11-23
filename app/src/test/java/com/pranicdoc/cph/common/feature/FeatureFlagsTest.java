package com.pranicdoc.cph.common.feature;

import com.pranicdoc.cph.common.security.SecurityContextUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.ObjectProvider;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class FeatureFlagsTest {

    private static final UUID A = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
    private static final UUID B = UUID.fromString("00000000-0000-0000-0000-0000000000b2");

    private final TreatmentSource source = mock(TreatmentSource.class);
    private final SecurityContextUtil securityContext = mock(SecurityContextUtil.class);

    {
        when(securityContext.getCurrentHealerIdOrNull()).thenReturn(A);
    }

    @SuppressWarnings("unchecked")
    private FeatureFlags flags(TreatmentSource src, boolean dflt) {
        ObjectProvider<TreatmentSource> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(src);
        return new FeatureFlags(provider, securityContext, dflt);
    }

    @Test
    void noSourceUsesTheDefaultForEveryone() {
        assertThat(flags(null, false).caseReviewEnabled()).isFalse();
        assertThat(flags(null, true).caseReviewEnabled()).isTrue();

        when(securityContext.getCurrentHealerIdOrNull()).thenReturn(null);
        assertThat(flags(null, true).caseReviewEnabled()).isTrue();
        assertThat(flags(null, true).caseReviewEnabledFor(null)).isTrue();
        assertThat(flags(null, true).caseReviewEnabledFor(B)).isTrue();
        assertThat(flags(null, false).caseReviewEnabled()).isFalse();
        assertThat(flags(null, false).caseReviewEnabledFor(null)).isFalse();
    }

    @Test
    void onMeansEnabled() {
        when(source.treatment(anyString(), anyString())).thenReturn("on");
        assertThat(flags(source, false).caseReviewEnabled()).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"off", "control", "On", "true", ""})
    @NullSource
    void anythingElseMeansDisabled(String treatment) {
        when(source.treatment(anyString(), anyString())).thenReturn(treatment);
        assertThat(flags(source, true).caseReviewEnabled()).isFalse();
    }

    @Test
    void sourceExceptionMeansDisabled() {
        when(source.treatment(anyString(), anyString())).thenThrow(new RuntimeException("boom"));
        assertThat(flags(source, true).caseReviewEnabled()).isFalse();
    }

    @Test
    void signedInHealerIdIsTheTrafficKey() {
        when(source.treatment(anyString(), anyString())).thenReturn("on");

        assertThat(flags(source, false).caseReviewEnabled()).isTrue();

        verify(source).treatment(A.toString(), "case_review_enabled");
    }

    @Test
    void noSignedInHealerMeansOffAndNoSdkCall() {
        when(securityContext.getCurrentHealerIdOrNull()).thenReturn(null);
        when(source.treatment(anyString(), anyString())).thenReturn("on");

        assertThat(flags(source, true).caseReviewEnabled()).isFalse();

        verifyNoInteractions(source);
    }

    @Test
    void explicitHealerIsEvaluatedByItsOwnId() {
        when(source.treatment(A.toString(), "case_review_enabled")).thenReturn("on");
        FeatureFlags f = flags(source, false);

        assertThat(f.caseReviewEnabledFor(A)).isTrue();
        assertThat(f.caseReviewEnabledFor(B)).isFalse();

        when(securityContext.getCurrentHealerIdOrNull()).thenReturn(B);
        assertThat(f.caseReviewEnabled()).isFalse();
    }

    @Test
    void nullHealerForExplicitVariantIsOff() {
        when(source.treatment(anyString(), anyString())).thenReturn("on");

        assertThat(flags(source, true).caseReviewEnabledFor(null)).isFalse();

        verifyNoInteractions(source);
    }

    @Test
    void reReadsTheSourceOnEveryCall() {
        when(source.treatment(A.toString(), "case_review_enabled")).thenReturn("off", "on", "off");
        FeatureFlags f = flags(source, false);
        assertThat(f.caseReviewEnabled()).isFalse();
        assertThat(f.caseReviewEnabled()).isTrue();
        assertThat(f.caseReviewEnabled()).isFalse();
    }
}
