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

    // The email lookup runs inside Hibernate's flush (auditing); it must not share the caller's persistence context.
    @Test
    void emailLookupRunsInSeparateTransaction() throws NoSuchMethodException {
        Transactional tx = implClass().getMethod("getEmail", UUID.class).getAnnotation(Transactional.class);

        assertThat(tx).isNotNull();
        assertThat(tx.propagation()).isEqualTo(Propagation.REQUIRES_NEW);
    }

    private static Class<?> implClass() {
        return com.pranicdoc.cph.auth.service.impl.HealerCacheServiceImpl.class;
    }
}
