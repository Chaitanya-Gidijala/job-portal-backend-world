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

    /** [path, count] — most requested pages via client page views since a point in time. */
    @Query("select l.path, count(l) from MonitorLog l where l.kind = 'PAGEVIEW' and l.createdAt >= :since "
            + "group by l.path order by count(l) desc")
    List<Object[]> topPages(@Param("since") Instant since, Pageable pageable);

    /** [country, count] — page views by country since a point in time. */
    @Query("select coalesce(l.country, 'Unknown'), count(l) from MonitorLog l where l.kind = 'PAGEVIEW' "
            + "and l.createdAt >= :since group by l.country order by count(l) desc")
    List<Object[]> topCountries(@Param("since") Instant since, Pageable pageable);
}
