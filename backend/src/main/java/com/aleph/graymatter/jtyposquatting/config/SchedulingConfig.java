package com.aleph.graymatter.jtyposquatting.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Enables the polling of the scan queue on instances that run the worker.
 */
@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "jtypo.worker.enabled", havingValue = "true", matchIfMissing = true)
public class SchedulingConfig {
}
