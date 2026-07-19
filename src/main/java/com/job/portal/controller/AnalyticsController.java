package com.job.portal.controller;

import com.job.portal.dto.ApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import java.util.HashMap;
import java.time.LocalDateTime;

import com.job.portal.entity.DailyAnalytics;
import com.job.portal.entity.VisitorSession;
import com.job.portal.repository.DailyAnalyticsRepository;
import com.job.portal.repository.VisitorSessionRepository;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/analytics")
@CrossOrigin(origins = "*")
public class AnalyticsController {

    @Autowired
    private DailyAnalyticsRepository analyticsRepository;

    @Autowired
    private VisitorSessionRepository visitorSessionRepository;

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
    public ResponseEntity<ApiResponse<Map<String, Object>>> visit(@RequestBody(required = false) Map<String, String> metadata, HttpServletRequest request) {
        DailyAnalytics today = getOrCreateToday();
        today.setTotalViews(today.getTotalViews() + 1);

        // Simple unique visitor logic based on probability for demo (ideally IP/cookie based)
        if (Math.random() > 0.3) {
            today.setUniqueVisitors(today.getUniqueVisitors() + 1);
        }

        analyticsRepository.save(today);

        // Save detailed visitor session
        if (metadata != null) {
            String ip = request.getHeader("X-Forwarded-For");
            if (ip == null) ip = request.getRemoteAddr();

            VisitorSession session = VisitorSession.builder()
                    .page(metadata.get("page"))
                    .browser(metadata.get("browser"))
                    .os(metadata.get("os"))
                    .device(metadata.get("device"))
                    .language(metadata.get("language"))
                    .referrer(metadata.get("referrer"))
                    .userEmail(metadata.get("userEmail"))
                    .systemInfo(metadata.get("systemInfo"))
                    .ipAddress(ip)
                    .timestamp(LocalDateTime.now())
                    .build();
            visitorSessionRepository.save(session);
        }

        Map<String, Object> data = new HashMap<>();
        data.put("count", today.getTotalViews());
        
        return ResponseEntity.ok(ApiResponse.success("Visit recorded successfully", data));
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<Map<String, Object>>> recordLogin() {
        DailyAnalytics today = getOrCreateToday();
        today.setUserLogins(today.getUserLogins() + 1);
        analyticsRepository.save(today);

        Map<String, Object> data = new HashMap<>();
        data.put("logins", today.getUserLogins());
        
        return ResponseEntity.ok(ApiResponse.success("Login recorded successfully", data));
    }

    @GetMapping("/stats")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getStats() {
        Map<String, Object> data = new HashMap<>();
        
        List<DailyAnalytics> allData = analyticsRepository.findAll();
        
        // Return historical daily data for the graph (last 30 days)
        List<DailyAnalytics> historicalData = allData.stream()
            .sorted((a, b) -> b.getDateId().compareTo(a.getDateId()))
            .limit(30)
            .collect(Collectors.toList());
        historicalData.sort((a, b) -> a.getDateId().compareTo(b.getDateId()));
        data.put("dailyStats", historicalData);
        
        // Total aggregations purely from DB
        long sumViews = allData.stream().mapToLong(DailyAnalytics::getTotalViews).sum();
        long sumUnique = allData.stream().mapToLong(DailyAnalytics::getUniqueVisitors).sum();
        long sumLogins = allData.stream().mapToLong(DailyAnalytics::getUserLogins).sum();

        data.put("totalViews", sumViews);
        data.put("uniqueVisitors", sumUnique);
        data.put("totalLogins", sumLogins);
        
        // Calculate browser distribution dynamically from last 1000 sessions
        List<VisitorSession> recentSessions = visitorSessionRepository.findTop50ByOrderByTimestampDesc();
        Map<String, Long> bStats = recentSessions.stream()
            .filter(s -> s.getBrowser() != null)
            .collect(Collectors.groupingBy(VisitorSession::getBrowser, Collectors.counting()));
        data.put("browserStats", bStats);

        return ResponseEntity.ok(ApiResponse.success("Analytics fetched successfully", data));
    }

    @GetMapping("/sessions")
    public ResponseEntity<ApiResponse<List<VisitorSession>>> getSessions(@RequestParam(defaultValue = "50") int limit) {
        List<VisitorSession> sessions = visitorSessionRepository.findTop50ByOrderByTimestampDesc();
        return ResponseEntity.ok(ApiResponse.success("Sessions fetched successfully", sessions));
    }
}

