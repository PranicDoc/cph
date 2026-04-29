package com.pranicdoc.cph.common.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * JPA configuration — enables auditing and Hibernate Envers integration.
 * Hibernate @Filter definitions are on TenantEntity (activated by TenantInterceptor).
 */
@Configuration
@EnableJpaAuditing
public class JpaConfig {
    // Hibernate Envers is auto-configured by spring-boot-starter + hibernate-envers on classpath.
    // @FilterDef / @Filter are declared on TenantEntity.
    // TenantInterceptor enables the filter per request.
}
