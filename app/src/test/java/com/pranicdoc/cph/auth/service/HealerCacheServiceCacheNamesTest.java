package com.pranicdoc.cph.auth.service;

import org.junit.jupiter.api.Test;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class HealerCacheServiceCacheNamesTest {

    // Both lookups are keyed by healerId; sharing one cache made getName return the cached email,
    // which then got printed as the healer's signature on the declaration and closure.
    @Test
    void nameAndEmailLookupsUseSeparateCaches() throws NoSuchMethodException {
        String[] email = implClass().getMethod("getEmail", UUID.class).getAnnotation(Cacheable.class).value();
        String[] name = implClass().getMethod("getName", UUID.class).getAnnotation(Cacheable.class).value();

        assertThat(name).isNotEmpty().doesNotContainAnyElementsOf(java.util.List.of(email));
    }

    // REQUIRES_NEW needs a second pooled connection while the request holds one; under load that starves
    // the pool (AuditPoolDeadlockTest). The cold-cache case is handled by warming in JwtAuthFilter instead.
    @Test
    void emailLookupDoesNotOpenASecondConnection() throws NoSuchMethodException {
        Transactional tx = implClass().getMethod("getEmail", UUID.class).getAnnotation(Transactional.class);

        assertThat(tx == null || tx.propagation() != Propagation.REQUIRES_NEW).isTrue();
    }

    private static Class<?> implClass() {
        return com.pranicdoc.cph.auth.service.impl.HealerCacheServiceImpl.class;
    }
}
