package com.job.portal.monitoring;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

public interface MonitorLogRepository extends JpaRepository<MonitorLog, Long>, JpaSpecificationExecutor<MonitorLog> {

    List<MonitorLog> findByVisitorIdOrderByCreatedAtDesc(String visitorId, Pageable pageable);

    @Modifying
    @Transactional
    @Query("delete from MonitorLog l where l.createdAt < :cutoff")
    int deleteOlderThan(@Param("cutoff") Instant cutoff);

    /** Lightweight tuples for the 24h analytics (grouped in Java, dialect-independent). */
    @Query("select l.kind, l.status, l.createdAt, l.visitorId, l.sessionId, l.device, l.browser, l.os, l.country, l.path "
            + "from MonitorLog l where l.createdAt >= :since and l.kind in ('PAGEVIEW', 'REQUEST', 'ERROR')")
    List<Object[]> statsRows(@Param("since") Instant since, Pageable pageable);
}
