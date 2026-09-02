package com.aztu.support_erp.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Enables the auth-sync outbox worker. */
@Configuration
@EnableAsync
@EnableScheduling
public class AsyncSchedulingConfig {
}
