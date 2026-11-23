package com.pranicdoc.cph.common.feature;

import com.pranicdoc.cph.common.exception.ResourceNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CaseReviewGateAspectTest {

    @RequiresCaseReview
    static class ClassGated {
        public String hello() { return "hi"; }
    }

    static class MethodGated {
        @RequiresCaseReview
        public String gated() { return "gated"; }

        public String open() { return "open"; }
    }

    private FeatureFlags flags;
    private CaseReviewGateAspect aspect;

    @BeforeEach
    void setUp() {
        flags = mock(FeatureFlags.class);
        aspect = new CaseReviewGateAspect(flags);
    }

    private <T> T proxy(T target) {
        AspectJProxyFactory factory = new AspectJProxyFactory(target);
        factory.setProxyTargetClass(true);
        factory.addAspect(aspect);
        return factory.getProxy();
    }

    @Test
    void classLevelAnnotatedIsGatedWhenOff() {
        when(flags.caseReviewEnabled()).thenReturn(false);
        ClassGated gated = proxy(new ClassGated());
        assertThatThrownBy(gated::hello).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void classLevelAnnotatedPassesWhenOn() {
        when(flags.caseReviewEnabled()).thenReturn(true);
        ClassGated gated = proxy(new ClassGated());
        assertThat(gated.hello()).isEqualTo("hi");
    }

    @Test
    void methodLevelOnlyGatesThatMethod() {
        when(flags.caseReviewEnabled()).thenReturn(false);
        MethodGated target = proxy(new MethodGated());
        assertThatThrownBy(target::gated).isInstanceOf(ResourceNotFoundException.class);
        assertThat(target.open()).isEqualTo("open");
    }
}
