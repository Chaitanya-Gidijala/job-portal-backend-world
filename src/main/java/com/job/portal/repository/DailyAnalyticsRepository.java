package com.job.portal.repository;

import com.job.portal.entity.DailyAnalytics;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface DailyAnalyticsRepository extends JpaRepository<DailyAnalytics, LocalDate> {
    List<DailyAnalytics> findTop30ByOrderByDateIdDesc();
}
