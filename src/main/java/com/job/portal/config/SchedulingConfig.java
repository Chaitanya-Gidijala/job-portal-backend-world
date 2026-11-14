package com.job.portal.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Enables @Scheduled jobs (Traceflow Daily live broadcasts and SSE heartbeats). */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
