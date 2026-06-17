package com.pranicdoc.cph.common.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

@Configuration
@EnableCaching
public class CacheConfig {

    @Value("${dashboard.cache.ttl-minutes:5}")
    private int dashboardTtlMinutes;

    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager manager = new CaffeineCacheManager("healerEmail");

        // Global spec for healerEmail -- DO NOT change (12h TTL, 10_000 max entries)
        manager.setCaffeine(
                Caffeine.newBuilder()
                        .maximumSize(10_000)
                        .expireAfterWrite(12, TimeUnit.HOURS)
        );

        // Per-name dashboard caches with configurable TTL (default 5 min)
        com.github.benmanes.caffeine.cache.Cache<Object, Object> summaryCache =
                Caffeine.newBuilder()
                        .maximumSize(1_000)
                        .expireAfterWrite(dashboardTtlMinutes, TimeUnit.MINUTES)
                        .build();
        manager.registerCustomCache("dashboard.summary", summaryCache);

        com.github.benmanes.caffeine.cache.Cache<Object, Object> trendsCache =
                Caffeine.newBuilder()
                        .maximumSize(1_000)
                        .expireAfterWrite(dashboardTtlMinutes, TimeUnit.MINUTES)
                        .build();
        manager.registerCustomCache("dashboard.session-trends", trendsCache);

        com.github.benmanes.caffeine.cache.Cache<Object, Object> growthCache =
                Caffeine.newBuilder()
                        .maximumSize(1_000)
                        .expireAfterWrite(dashboardTtlMinutes, TimeUnit.MINUTES)
                        .build();
        manager.registerCustomCache("dashboard.patient-growth", growthCache);
        com.github.benmanes.caffeine.cache.Cache<Object, Object> topSymptomsCache =
                Caffeine.newBuilder()
                        .maximumSize(1_000)
                        .expireAfterWrite(dashboardTtlMinutes, TimeUnit.MINUTES)
                        .build();
        manager.registerCustomCache("dashboard.top-symptoms", topSymptomsCache);

        com.github.benmanes.caffeine.cache.Cache<Object, Object> topProtocolsCache =
                Caffeine.newBuilder()
                        .maximumSize(1_000)
                        .expireAfterWrite(dashboardTtlMinutes, TimeUnit.MINUTES)
                        .build();
        manager.registerCustomCache("dashboard.top-protocols", topProtocolsCache);

        return manager;
    }
}