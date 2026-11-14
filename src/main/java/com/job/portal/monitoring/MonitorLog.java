package com.job.portal.monitoring;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * One monitoring log row: either a server request (kind = REQUEST) or a client
 * event reported by the React app (PAGEVIEW, CLICK, ERROR, VITAL). Heartbeats are
 * kept in memory only and never persisted.
 */
@Data
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "monitor_logs", indexes = {
        @Index(name = "idx_monitor_logs_created", columnList = "created_at"),
        @Index(name = "idx_monitor_logs_kind", columnList = "kind"),
        @Index(name = "idx_monitor_logs_visitor", columnList = "visitor_id")
})
public class MonitorLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(nullable = false, length = 16)
    private String kind;

    @Column(length = 10)
    private String method;

    @Column(length = 500)
    private String path;

    @Column(length = 500)
    private String query;

    private Integer status;

    @Column(name = "duration_ms")
    private Long durationMs;

    @Column(length = 64)
    private String ip;

    @Column(length = 120)
    private String username;

    @Column(name = "visitor_id", length = 64)
    private String visitorId;

    @Column(name = "session_id", length = 64)
    private String sessionId;

    @Column(name = "user_agent", length = 400)
    private String userAgent;

    @Column(length = 40)
    private String browser;

    @Column(length = 40)
    private String os;

    @Column(length = 20)
    private String device;

    @Column(length = 80)
    private String country;

    @Column(length = 120)
    private String city;

    /** Click text, page title, vital name, error message … */
    @Column(length = 500)
    private String label;

    /** Referrer, click target, error stack excerpt, vital value … */
    @Column(length = 1000)
    private String detail;

    @Column(name = "app_version", length = 40)
    private String appVersion;
}
