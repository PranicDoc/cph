package com.pranicdoc.cph.auth.service;

import org.junit.jupiter.api.Test;
import org.springframework.cache.annotation.Cacheable;

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

    private static Class<?> implClass() {
        return com.pranicdoc.cph.auth.service.impl.HealerCacheServiceImpl.class;
    }
}
