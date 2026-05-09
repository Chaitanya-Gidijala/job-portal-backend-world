package com.job.portal.controller;

import com.job.portal.dto.ApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.HashMap;

import com.job.portal.entity.DailyAnalytics;
import com.job.portal.repository.DailyAnalyticsRepository;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/analytics")
@CrossOrigin(origins = "*")
public class AnalyticsController {

    @Autowired
    private DailyAnalyticsRepository analyticsRepository;

    private final AtomicLong totalViews = new AtomicLong(0);
    private final AtomicLong uniqueVisitors = new AtomicLong(0);
    private final AtomicLong totalLogins = new AtomicLong(0);
    private final Map<String, AtomicLong> browserStats = new ConcurrentHashMap<>();

    private DailyAnalytics getOrCreateToday() {
        LocalDate today = LocalDate.now();
        return analyticsRepository.findById(today)
                .orElseGet(() -> DailyAnalytics.builder()
                        .dateId(today)
                        .totalViews(0)
                        .uniqueVisitors(0)
                        .userLogins(0)
                        .build());
    }

    @PostMapping("/visit")
    public ResponseEntity<ApiResponse<Map<String, Object>>> visit(@RequestBody(required = false) Map<String, String> metadata) {
        long count = totalViews.incrementAndGet();
        
        DailyAnalytics today = getOrCreateToday();
        today.setTotalViews(today.getTotalViews() + 1);

        if (Math.random() > 0.3) {
            uniqueVisitors.incrementAndGet();
            today.setUniqueVisitors(today.getUniqueVisitors() + 1);
        }

        if (metadata != null) {
            String browser = metadata.get("browser");
            if (browser != null) {
                browserStats.computeIfAbsent(browser, k -> new AtomicLong(0)).incrementAndGet();
            }
        }
        
        analyticsRepository.save(today);

        Map<String, Object> data = new HashMap<>();
        data.put("count", count);
        
        return ResponseEntity.ok(ApiResponse.success("Visit recorded successfully", data));
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<Map<String, Object>>> recordLogin() {
        totalLogins.incrementAndGet();
        
        DailyAnalytics today = getOrCreateToday();
        today.setUserLogins(today.getUserLogins() + 1);
        analyticsRepository.save(today);

        Map<String, Object> data = new HashMap<>();
        data.put("logins", totalLogins.get());
        
        return ResponseEntity.ok(ApiResponse.success("Login recorded successfully", data));
    }

    @GetMapping("/stats")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getStats() {
        Map<String, Object> data = new HashMap<>();
        
        // Return historical daily data for the graph
        List<DailyAnalytics> historicalData = analyticsRepository.findTop30ByOrderByDateIdDesc();
        historicalData.sort((a, b) -> a.getDateId().compareTo(b.getDateId()));
        data.put("dailyStats", historicalData);
        
        long sumViews = historicalData.stream().mapToLong(DailyAnalytics::getTotalViews).sum() + totalViews.get();
        long sumUnique = historicalData.stream().mapToLong(DailyAnalytics::getUniqueVisitors).sum() + uniqueVisitors.get();
        long sumLogins = historicalData.stream().mapToLong(DailyAnalytics::getUserLogins).sum() + totalLogins.get();

        data.put("totalViews", sumViews);
        data.put("uniqueVisitors", sumUnique);
        data.put("totalLogins", sumLogins);
        
        Map<String, Long> bStats = new HashMap<>();
        browserStats.forEach((k, v) -> bStats.put(k, v.get()));
        data.put("browserStats", bStats);

        return ResponseEntity.ok(ApiResponse.success("Analytics fetched successfully", data));
    }
}
