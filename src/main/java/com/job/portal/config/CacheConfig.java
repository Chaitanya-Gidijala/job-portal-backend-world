package com.job.portal.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
public class CacheConfig {

    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager cacheManager = new CaffeineCacheManager(
                "jobs",
                "job",
                "jobs_by_experience",
                "jobs_latest",
                "jobs_search",
                "job_counts",
                "jobs_by_company",
                "interview_questions",
                "learning_resources",
                "quizzes",
                "topics");
        cacheManager.setCaffeine(Caffeine.newBuilder()
                .expireAfterWrite(Duration.ofMinutes(5))
                .maximumSize(10_000)
                .recordStats());
        return cacheManager;
    }
}
