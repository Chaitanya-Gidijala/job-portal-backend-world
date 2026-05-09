package com.job.portal.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@lombok.extern.slf4j.Slf4j
@RestController
@RequestMapping("/api/health")
public class HealthController {

    @GetMapping
    public ResponseEntity<String> health() {
        log.info("Health check endpoint called - Status: OK");
        return ResponseEntity.ok("OK");
    }
}
