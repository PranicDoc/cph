package com.pranicdoc.cph.common.feature;

import io.split.client.SplitClient;
import io.split.client.SplitClientConfig;
import io.split.client.SplitFactory;
import io.split.client.SplitFactoryBuilder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeoutException;

/**
 * Harness FME (Split-derived) server-side SDK. Active only when {@code features.fme.sdk-key} is set; the SDK
 * keeps flag definitions in memory and refreshes them in the background, so evaluation makes no network call.
 * Startup waits at most {@code features.fme.ready-timeout-ms} for the first sync, then carries on; until the
 * SDK is ready it answers {@code control}, which {@link FeatureFlags} treats as off. The key is never logged.
 */
@Slf4j
@Component
@ConditionalOnExpression("!'${features.fme.sdk-key:}'.isBlank()")
public class FmeTreatmentSource implements TreatmentSource, DisposableBean {

    private final SplitFactory factory;
    private final SplitClient client;

    // Hand-written: @Value constructor parameters, and the SDK config is derived from them.
    @Autowired
    public FmeTreatmentSource(@Value("${features.fme.sdk-key}") String sdkKey,
                              @Value("${features.fme.ready-timeout-ms:3000}") long readyTimeoutMs) {
        this(sdkKey, SplitClientConfig.builder().setBlockUntilReadyTimeout((int) readyTimeoutMs).build(),
                readyTimeoutMs);
    }

    /** For tests: localhost mode or unreachable endpoints. */
    FmeTreatmentSource(String sdkKey, SplitClientConfig config, long readyTimeoutMs) {
        SplitFactory built;
        SplitClient builtClient;
        try {
            built = SplitFactoryBuilder.build(sdkKey, config);
            builtClient = built.client();
        } catch (Exception e) {
            throw new IllegalStateException("Harness FME SDK could not be built", e);
        }
        this.factory = built;
        this.client = builtClient;
        try {
            client.blockUntilReady();
            log.info("Harness FME SDK ready");
        } catch (TimeoutException e) {
            log.warn("Harness FME SDK not ready after {} ms; flags serve off until it is", readyTimeoutMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Interrupted waiting for Harness FME SDK; flags serve off until it is ready");
        }
    }

    @Override
    public String treatment(String trafficKey, String flagName) {
        return client.getTreatment(trafficKey, flagName);
    }

    @Override
    public void destroy() {
        client.destroy();
    }
}
