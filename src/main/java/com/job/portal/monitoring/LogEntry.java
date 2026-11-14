package com.job.portal.monitoring;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** In-memory log line streamed to the admin dashboard (persisted copy: {@link MonitorLog}). */
@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class LogEntry {

    public static final String REQUEST = "REQUEST";
    public static final String HEARTBEAT = "HEARTBEAT";
    public static final String PAGEVIEW = "PAGEVIEW";
    public static final String CLICK = "CLICK";
    public static final String ERROR = "ERROR";
    public static final String VITAL = "VITAL";

    /** Monotonic id for the live stream (not the DB id). */
    private long seq;
    private long ts;
    private String kind;
    private String method;
    private String path;
    private String query;
    private Integer status;
    private Long durationMs;
    private String ip;
    private String username;
    private String userEmail;
    private String visitorId;
    private String sessionId;
    private String userAgent;
    private String browser;
    private String os;
    private String device;
    private String country;
    private String city;
    private String label;
    private String detail;
    private String appVersion;

    MonitorLog toEntity() {
        return MonitorLog.builder()
                .createdAt(java.time.Instant.ofEpochMilli(ts))
                .kind(kind)
                .method(method)
                .path(ClientInfo.truncate(path, 500))
                .query(ClientInfo.truncate(query, 500))
                .status(status)
                .durationMs(durationMs)
                .ip(ip)
                .username(ClientInfo.truncate(username, 120))
                .userEmail(ClientInfo.truncate(userEmail, 160))
                .visitorId(ClientInfo.truncate(visitorId, 64))
                .sessionId(ClientInfo.truncate(sessionId, 64))
                .userAgent(ClientInfo.truncate(userAgent, 400))
                .browser(browser)
                .os(os)
                .device(device)
                .country(ClientInfo.truncate(country, 80))
                .city(ClientInfo.truncate(city, 120))
                .label(ClientInfo.truncate(label, 500))
                .detail(ClientInfo.truncate(detail, 1000))
                .appVersion(ClientInfo.truncate(appVersion, 40))
                .build();
    }
}
