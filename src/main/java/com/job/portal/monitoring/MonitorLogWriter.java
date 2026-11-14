package com.job.portal.monitoring;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Batched, asynchronous persistence for monitor logs — requests never wait on the DB.
 * Rows older than `monitor.retention-days` (default 14) are purged nightly.
 */
@Slf4j
@Component
public class MonitorLogWriter {

    static final int MAX_QUEUE = 20_000;
    static final int BATCH = 500;

    private final MonitorLogRepository repository;
    private final int retentionDays;
    private final ConcurrentLinkedQueue<LogEntry> queue = new ConcurrentLinkedQueue<>();
    private final AtomicInteger size = new AtomicInteger();
    private final AtomicInteger dropped = new AtomicInteger();

    public MonitorLogWriter(MonitorLogRepository repository,
                            @Value("${monitor.retention-days:14}") int retentionDays) {
        this.repository = repository;
        this.retentionDays = retentionDays;
    }

    public void enqueue(LogEntry entry) {
        if (size.get() >= MAX_QUEUE) {
            dropped.incrementAndGet(); // DB is down or slow — keep memory bounded
            return;
        }
        queue.add(entry);
        size.incrementAndGet();
    }

    @Scheduled(fixedDelay = 2_000)
    public void flush() {
        while (!queue.isEmpty()) {
            List<MonitorLog> batch = new ArrayList<>(BATCH);
            LogEntry e;
            while (batch.size() < BATCH && (e = queue.poll()) != null) {
                size.decrementAndGet();
                batch.add(e.toEntity());
            }
            if (batch.isEmpty()) {
                return;
            }
            try {
                repository.saveAll(batch);
            } catch (RuntimeException ex) {
                log.warn("Could not persist {} monitor logs: {}", batch.size(), ex.getMessage());
                return;
            }
        }
        int lost = dropped.getAndSet(0);
        if (lost > 0) {
            log.warn("Dropped {} monitor logs because the write queue was full", lost);
        }
    }

    @Scheduled(cron = "0 15 3 * * *")
    public void purge() {
        int removed = repository.deleteOlderThan(Instant.now().minus(Duration.ofDays(retentionDays)));
        log.info("Purged {} monitor logs older than {} days", removed, retentionDays);
    }

    public int pending() {
        return size.get();
    }
}
