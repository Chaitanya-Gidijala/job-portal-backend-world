package com.job.portal.monitoring;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;

public interface VisitorDayRepository extends JpaRepository<VisitorDay, Long> {

    boolean existsByDayAndVisitorId(LocalDate day, String visitorId);

    long countByDay(LocalDate day);
}
