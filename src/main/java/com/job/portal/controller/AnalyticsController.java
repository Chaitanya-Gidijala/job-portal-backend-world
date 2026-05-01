package com.job.portal.controller;

import com.job.portal.dto.ApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.HashMap;

@RestController
@RequestMapping("/api/analytics")
@CrossOrigin(origins = "*")
public class AnalyticsController {

    private final AtomicLong totalViews = new AtomicLong(0);
    private final AtomicLong uniqueVisitors = new AtomicLong(0);
    private final Map<String, AtomicLong> browserStats = new ConcurrentHashMap<>();

    @PostMapping("/visit")
    public ResponseEntity<ApiResponse<Map<String, Object>>> visit(@RequestBody(required = false) Map<String, String> metadata) {
        long count = totalViews.incrementAndGet();
        
        // Very basic unique visitor simulation
        if (Math.random() > 0.3) {
            uniqueVisitors.incrementAndGet();
        }

        if (metadata != null) {
            String browser = metadata.get("browser");
            if (browser != null) {
                browserStats.computeIfAbsent(browser, k -> new AtomicLong(0)).incrementAndGet();
            }
        }

        Map<String, Object> data = new HashMap<>();
        data.put("count", count);
        
        return ResponseEntity.ok(ApiResponse.success("Visit recorded successfully", data));
    }

    @GetMapping("/stats")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getStats() {
        Map<String, Object> data = new HashMap<>();
        data.put("totalViews", totalViews.get());
        data.put("uniqueVisitors", uniqueVisitors.get());
        
        Map<String, Long> bStats = new HashMap<>();
        browserStats.forEach((k, v) -> bStats.put(k, v.get()));
        data.put("browserStats", bStats);

        return ResponseEntity.ok(ApiResponse.success("Analytics fetched successfully", data));
    }
}
