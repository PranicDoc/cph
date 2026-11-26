package com.pranicdoc.cph.auth.service;

import com.pranicdoc.cph.auth.model.Healer;
import com.pranicdoc.cph.auth.repository.HealerRepository;
import com.pranicdoc.cph.auth.service.impl.HealerCacheServiceImpl;
import com.pranicdoc.cph.common.cache.TwoLevelCacheManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Real cache proxy around HealerCacheServiceImpl: what a profile change leaves behind in the cache. */
class HealerCacheProfileChangeTest {

    @EnableCaching
    static class Caching {
    }

    private final HealerRepository repository = mock(HealerRepository.class);
    private AnnotationConfigApplicationContext context;
    private HealerCacheService cache;

    @BeforeEach
    void start() {
        TwoLevelCacheManager manager = new TwoLevelCacheManager(new ConcurrentMapCacheManager(), 60, 100);
        manager.registerCache("healerName");
        manager.registerCache("healerEmail");
        context = new AnnotationConfigApplicationContext();
        context.register(Caching.class);
        context.registerBean(CacheManager.class, () -> manager);
        context.registerBean(HealerCacheServiceImpl.class, () -> new HealerCacheServiceImpl(repository));
        context.refresh();
        cache = context.getBean(HealerCacheService.class);
    }

    @AfterEach
    void stop() {
        context.close();
    }

    private Healer healer(UUID id, String name, String email) {
        Healer healer = Healer.builder().fullName(name).email(email).build();
        when(repository.findById(id)).thenReturn(Optional.of(healer));
        when(repository.findEmailById(id)).thenReturn(email);
        return healer;
    }

    @Test
    void profileChangeServesNewNameAndEmail() {
        UUID id = UUID.randomUUID();
        Healer healer = healer(id, "Old Name", "old@x.com");
        assertThat(cache.getName(id)).isEqualTo("Old Name");
        assertThat(cache.getEmail(id)).isEqualTo("old@x.com");
        healer.setFullName("New Name");

        cache.onProfileChanged(id, "new@x.com");

        assertThat(cache.getName(id)).isEqualTo("New Name");
        assertThat(cache.getEmail(id)).isEqualTo("new@x.com");
        // The new email came from the put: no cold load that could run mid-flush.
        verify(repository, times(1)).findEmailById(id);
    }

    @Test
    void otherHealersStayCached() {
        UUID changed = UUID.randomUUID();
        UUID other = UUID.randomUUID();
        Healer otherHealer = healer(other, "Other Name", "other@x.com");
        cache.getName(other);
        cache.getEmail(other);
        otherHealer.setFullName("Not Reloaded");

        cache.onProfileChanged(changed, "new@x.com");

        assertThat(cache.getName(other)).isEqualTo("Other Name");
        assertThat(cache.getEmail(other)).isEqualTo("other@x.com");
        verify(repository, times(1)).findEmailById(other);
    }
}
