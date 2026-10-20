package com.pranicdoc.cph.notification.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

// HTTP client for WhatsApp lives in common.http (RestTemplateService / RestTemplateConfig).
@Configuration
@EnableScheduling
@EnableAsync
@EnableConfigurationProperties(NotificationProperties.class)
public class NotificationInfraConfig {

    @Bean(name = "whatsappWorkerExecutor")
    public Executor whatsappWorkerExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(10);
        executor.setQueueCapacity(1000);
        executor.setThreadNamePrefix("WA-Worker-");
        executor.initialize();
        return executor;
    }
}
