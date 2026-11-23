package com.pranicdoc.cph.common.feature;

import io.split.client.SplitClient;
import io.split.client.SplitClientConfig;
import io.split.client.SplitFactoryBuilder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.context.annotation.Conditional;
import org.springframework.core.type.AnnotatedTypeMetadata;
import org.springframework.util.StringUtils;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeoutException;
import java.util.function.IntFunction;

/**
 * Harness FME (Split-derived) server-side SDK. Active only when {@code features.fme.sdk-key} is set; the SDK
 * keeps flag definitions in memory and refreshes them in the background, so evaluation makes no network call.
 * Startup waits at most {@code features.fme.ready-timeout-ms} for the first sync, then carries on; until the
 * SDK is ready it answers {@code control}, which {@link FeatureFlags} treats as off. The key is never logged.
 */
@Slf4j
@Component
@Conditional(FmeTreatmentSource.SdkKeyPresent.class)
public class FmeTreatmentSource implements TreatmentSource, DisposableBean {

    /** Matches only when a non-blank SDK key is configured; the key is read, never spliced into an expression. */
    static class SdkKeyPresent implements Condition {
        @Override
        public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
            return StringUtils.hasText(context.getEnvironment().getProperty("features.fme.sdk-key"));
        }
    }

    private static final long DEFAULT_READY_TIMEOUT_MS = 3000;

    /** Null when the SDK could not be built; evaluation then answers {@code control}. */
    private final SplitClient client;

    // Hand-written: @Value constructor parameters. The timeout arrives as text so a bad env value cannot
    // fail startup.
    @Autowired
    public FmeTreatmentSource(@Value("${features.fme.sdk-key}") String sdkKey,
                              @Value("${features.fme.ready-timeout-ms:3000}") String readyTimeoutMs) {
        this(sdkKey, parseTimeout(readyTimeoutMs), ms -> SplitClientConfig.builder()
                .setBlockUntilReadyTimeout(ms).build());
    }

    /** For tests: localhost mode, unreachable endpoints, or a failing config. */
    FmeTreatmentSource(String sdkKey, long readyTimeoutMs, IntFunction<SplitClientConfig> configFactory) {
        int timeout = (int) Math.max(1, Math.min(readyTimeoutMs, Integer.MAX_VALUE));
        SplitClient built = null;
        try {
            built = SplitFactoryBuilder.build(sdkKey, configFactory.apply(timeout)).client();
            built.blockUntilReady();
            log.info("Harness FME SDK ready");
        } catch (TimeoutException e) {
            log.warn("Harness FME SDK not ready after {} ms; flags serve off until it is", timeout);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Interrupted waiting for Harness FME SDK; flags serve off until it is ready");
        } catch (Exception e) {
            // Class name only: an SDK exception message could echo the key or endpoint.
            log.warn("Harness FME client unavailable, serving control ({})", e.getClass().getSimpleName());
        }
        this.client = built;
    }

    static long parseTimeout(String raw) {
        try {
            return Long.parseLong(raw.trim());
        } catch (RuntimeException e) {
            log.warn("Invalid features.fme.ready-timeout-ms, using {}", DEFAULT_READY_TIMEOUT_MS);
            return DEFAULT_READY_TIMEOUT_MS;
        }
    }

    @Override
    public String treatment(String trafficKey, String flagName) {
        return client == null ? "control" : client.getTreatment(trafficKey, flagName);
    }

    @Override
    public void destroy() {
        if (client != null) {
            client.destroy();
        }
    }
}
