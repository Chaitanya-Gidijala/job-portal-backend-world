package com.job.portal.monitoring;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface VisitorProfileRepository extends JpaRepository<VisitorProfile, Long>, JpaSpecificationExecutor<VisitorProfile> {

    Optional<VisitorProfile> findByVisitorId(String visitorId);

    List<VisitorProfile> findByVisitorIdIn(Collection<String> visitorIds);

    long countByFirstSeenGreaterThanEqual(Instant since);

    long countByLastSeenGreaterThanEqual(Instant since);

    @Query("select count(v) from VisitorProfile v where v.lastSeen >= :since and v.firstSeen < :since")
    long countReturningSince(@Param("since") Instant since);

    /** Lightweight tuples for source / location rankings (grouped in Java). */
    @Query("select v.source, v.country, v.device, v.browser, v.os from VisitorProfile v where v.lastSeen >= :since")
    List<Object[]> breakdownRows(@Param("since") Instant since);
}
