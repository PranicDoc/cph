package com.pranicdoc.cph.common.feature;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.ObjectProvider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FeatureFlagsTest {

    private final TreatmentSource source = mock(TreatmentSource.class);

    @SuppressWarnings("unchecked")
    private FeatureFlags flags(TreatmentSource src, boolean dflt) {
        ObjectProvider<TreatmentSource> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(src);
        return new FeatureFlags(provider, dflt);
    }

    @Test
    void noSourceUsesTheDefault() {
        assertThat(flags(null, false).caseReviewEnabled()).isFalse();
        assertThat(flags(null, true).caseReviewEnabled()).isTrue();
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
    void usesTheFlagNameAndConstantKey() {
        when(source.treatment(anyString(), anyString())).thenReturn("on");
        flags(source, false).caseReviewEnabled();
        verify(source).treatment("cph", "case_review_enabled");
    }

    @Test
    void reReadsTheSourceOnEveryCall() {
        when(source.treatment("cph", "case_review_enabled")).thenReturn("off", "on", "off");
        FeatureFlags f = flags(source, false);
        assertThat(f.caseReviewEnabled()).isFalse();
        assertThat(f.caseReviewEnabled()).isTrue();
        assertThat(f.caseReviewEnabled()).isFalse();
    }
}
