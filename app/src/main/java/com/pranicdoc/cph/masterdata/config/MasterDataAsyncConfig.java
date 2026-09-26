package com.pranicdoc.cph.masterdata.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * Executor for async protocol imports ({@code ProtocolImportAsyncRunner}). No
 * {@code @EnableAsync} here — it's already active globally via {@code
 * NotificationInfraConfig} (Spring only needs one). Bounded pool: several
 * healers submitting imports at once queue rather than spawning unbounded
 * threads — correctness under concurrent submissions matters more than
 * parallelism for a background bulk-upsert job (extendable later if imports
 * become frequent enough to need more throughput).
 */
@Configuration
public class MasterDataAsyncConfig {

    @Bean(name = "protocolImportExecutor")
    public Executor protocolImportExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(50);
        executor.setThreadNamePrefix("protocol-import-");
        executor.initialize();
        return executor;
    }
}
