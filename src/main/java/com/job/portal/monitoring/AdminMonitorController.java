package com.job.portal.monitoring;

import com.job.portal.dto.ApiResponse;
import com.job.portal.entity.ContactMessage;
import com.job.portal.entity.DailyAnalytics;
import com.job.portal.repository.ContactRepository;
import com.job.portal.repository.DailyAnalyticsRepository;
import com.job.portal.service.DailyChallengeService;
import jakarta.annotation.PostConstruct;
import jakarta.persistence.criteria.Predicate;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;
import java.util.function.Supplier;

/**
 * Admin monitoring API (ROLE_ADMIN, enforced in SecurityConfig). The live stream
 * is authenticated with a one-time ticket obtained from {@code POST /stream-ticket}.
 */
@lombok.extern.slf4j.Slf4j
@RestController
@RequestMapping("/api/admin/monitor")
public class AdminMonitorController {

    private final MonitoringHub hub;
    private final PresenceService presence;
    private final SystemHealthService health;
    private final MonitorLogRepository logs;
    private final DailyAnalyticsRepository analytics;
    private final ContactRepository contacts;
    private final DailyChallengeService daily;
    private final MonitorLogWriter writer;
    private final TrafficStatsService traffic;
    private final GeoIpService geo;
    private final org.springframework.jdbc.core.JdbcTemplate jdbc;

    public AdminMonitorController(MonitoringHub hub, PresenceService presence, SystemHealthService health,
                                  MonitorLogRepository logs, DailyAnalyticsRepository analytics,
                                  ContactRepository contacts, DailyChallengeService daily, MonitorLogWriter writer,
                                  TrafficStatsService traffic, GeoIpService geo, org.springframework.jdbc.core.JdbcTemplate jdbc) {
        this.hub = hub;
        this.presence = presence;
        this.health = health;
        this.logs = logs;
        this.analytics = analytics;
        this.contacts = contacts;
        this.daily = daily;
        this.writer = writer;
        this.traffic = traffic;
        this.geo = geo;
        this.jdbc = jdbc;
    }

    @PostConstruct
    void wire() {
        hub.setMetricsSupplier(this::liveMetrics);
    }

    /** Small, frequent payload pushed on the stream every 5s. */
    Map<String, Object> liveMetrics() {
        Map<String, Object> m = new LinkedHashMap<>(hub.metrics(30));
        Map<String, Object> jvm = health.jvm();
        m.put("heapUsed", jvm.get("heapUsed"));
        m.put("heapMax", jvm.get("heapMax"));
        m.put("processCpu", jvm.get("processCpu"));
        m.put("threads", jvm.get("threads"));
        m.put("uptimeMs", jvm.get("uptimeMs"));
        m.put("live", presence.liveCount());
        m.put("at", System.currentTimeMillis());
        return m;
    }

    @GetMapping("/overview")
    public ApiResponse<Map<String, Object>> overview() {
        // Each section is independent: one failing query must not blank the whole dashboard.
        Map<String, Object> out = new LinkedHashMap<>();
        Map<String, String> errors = new LinkedHashMap<>();
        LocalDate today = LocalDate.now();

        section(out, errors, "live", presence::breakdown);
        section(out, errors, "metrics", () -> hub.metrics(60));
        section(out, errors, "daily30", () -> {
            List<DailyAnalytics> rows = new ArrayList<>(analytics.findTop30ByOrderByDateIdDesc());
            rows.sort(Comparator.comparing(DailyAnalytics::getDateId));
            DailyAnalytics todayRow = rows.stream().filter(d -> today.equals(d.getDateId())).findFirst().orElse(null);
            out.put("today", Map.of(
                    "views", todayRow == null ? 0L : todayRow.getTotalViews(),
                    "uniqueVisitors", todayRow == null ? 0L : todayRow.getUniqueVisitors(),
                    "peakConcurrent", presence.peakToday()));
            return rows;
        });
        section(out, errors, "allTimeViews", analytics::sumTotalViews);
        section(out, errors, "traffic24h", traffic::last24h);
        section(out, errors, "endpoints", () -> hub.endpointPerformance(12));
        section(out, errors, "uptime", () -> {
            var rt = java.lang.management.ManagementFactory.getRuntimeMXBean();
            return Map.of("startedAt", rt.getStartTime(), "uptimeMs", rt.getUptime());
        });
        section(out, errors, "dailyChallenge", () -> daily.stats(daily.currentDay()));
        section(out, errors, "inquiries", () -> Map.of("total", contacts.count(), "unread", contacts.countUnread()));
        if (!out.containsKey("today")) {
            out.put("today", Map.of("views", 0L, "uniqueVisitors", 0L, "peakConcurrent", presence.peakToday()));
        }
        out.put("errors", errors);
        return ApiResponse.success(errors.isEmpty() ? "Overview" : "Overview (partial)", out);
    }

    private void section(Map<String, Object> out, Map<String, String> errors, String key, Supplier<Object> supplier) {
        try {
            out.put(key, supplier.get());
        } catch (RuntimeException e) {
            log.error("Admin overview section '{}' failed", key, e);
            errors.put(key, e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    @GetMapping("/visitors")
    public ApiResponse<Map<String, Object>> visitors() {
        return ApiResponse.success("Live visitors", Map.of(
                "sessions", presence.snapshot(),
                "breakdown", presence.breakdown(),
                "at", System.currentTimeMillis()));
    }

    @GetMapping("/visitors/{visitorId}/timeline")
    public ApiResponse<List<MonitorLog>> timeline(@PathVariable String visitorId,
                                                  @RequestParam(defaultValue = "200") int limit) {
        return ApiResponse.success("Visitor timeline",
                logs.findByVisitorIdOrderByCreatedAtDesc(visitorId, PageRequest.of(0, Math.min(limit, 500))));
    }

    /** Latest in-memory lines (includes rows not yet flushed to the DB). */
    @GetMapping("/logs/recent")
    public ApiResponse<List<LogEntry>> recent(@RequestParam(defaultValue = "300") int limit,
                                              @RequestParam(defaultValue = "false") boolean heartbeats) {
        return ApiResponse.success("Recent logs", hub.recent(Math.min(limit, MonitoringHub.RING_SIZE), heartbeats));
    }

    /** Searchable history from the database. */
    @GetMapping("/logs")
    public ApiResponse<Map<String, Object>> search(@RequestParam(required = false) List<String> kind,
                                                   @RequestParam(required = false) String q,
                                                   @RequestParam(required = false) String status,
                                                   @RequestParam(required = false) String method,
                                                   @RequestParam(required = false) String visitorId,
                                                   @RequestParam(required = false) Long from,
                                                   @RequestParam(required = false) Long to,
                                                   @RequestParam(defaultValue = "0") int page,
                                                   @RequestParam(defaultValue = "50") int size) {
        Specification<MonitorLog> spec = (root, query, cb) -> {
            List<Predicate> p = new ArrayList<>();
            if (kind != null && !kind.isEmpty()) p.add(root.get("kind").in(kind));
            if (method != null && !method.isBlank()) p.add(cb.equal(root.get("method"), method.toUpperCase()));
            if (visitorId != null && !visitorId.isBlank()) p.add(cb.equal(root.get("visitorId"), visitorId));
            if (from != null) p.add(cb.greaterThanOrEqualTo(root.get("createdAt"), Instant.ofEpochMilli(from)));
            if (to != null) p.add(cb.lessThanOrEqualTo(root.get("createdAt"), Instant.ofEpochMilli(to)));
            if (status != null && status.matches("[1-5]xx")) {
                int lo = (status.charAt(0) - '0') * 100;
                p.add(cb.between(root.get("status"), lo, lo + 99));
            }
            if (q != null && !q.isBlank()) {
                String like = "%" + q.toLowerCase().trim() + "%";
                p.add(cb.or(
                        cb.like(cb.lower(root.get("path")), like),
                        cb.like(cb.lower(cb.coalesce(root.get("label"), "")), like),
                        cb.like(cb.lower(cb.coalesce(root.get("username"), "")), like),
                        cb.like(cb.lower(cb.coalesce(root.get("userEmail"), "")), like),
                        cb.like(cb.lower(cb.coalesce(root.get("ip"), "")), like),
                        cb.like(cb.lower(cb.coalesce(root.get("country"), "")), like),
                        cb.like(cb.lower(cb.coalesce(root.get("city"), "")), like),
                        cb.like(cb.lower(cb.coalesce(root.get("visitorId"), "")), like)));
            }
            return cb.and(p.toArray(new Predicate[0]));
        };
        Page<MonitorLog> result = logs.findAll(spec,
                PageRequest.of(Math.max(0, page), Math.min(Math.max(1, size), 200), Sort.by(Sort.Direction.DESC, "createdAt")));
        return ApiResponse.success("Logs", Map.of(
                "items", result.getContent(),
                "total", result.getTotalElements(),
                "page", result.getNumber(),
                "pages", result.getTotalPages()));
    }

    @GetMapping("/health")
    public ApiResponse<Map<String, Object>> health() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("api", Map.of("status", "UP", "jvm", health.jvm(), "actuator", health.actuator()));
        out.put("database", health.database());
        out.put("services", health.services());
        out.put("client", hub.clientHealth());
        out.put("metrics", hub.metrics(60));
        out.put("recentErrors", hub.recentErrors());
        out.put("monitor", Map.of(
                "liveStreams", hub.streamCount(),
                "pendingWrites", writer.pending(),
                "liveSessions", presence.liveCount()));
        out.put("pipeline", writer.status());
        out.put("geo", geo.status());
        out.put("tables", tables());
        return ApiResponse.success("Health", out);
    }

    @PostMapping("/stream-ticket")
    public ApiResponse<Map<String, Object>> ticket(Authentication auth) {
        String user = auth == null ? "admin" : auth.getName();
        return ApiResponse.success("Ticket", Map.of("ticket", hub.issueTicket(user), "expiresInMs", MonitoringHub.TICKET_TTL_MS));
    }

    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@RequestParam String ticket, HttpServletResponse response) {
        SseEmitter emitter = hub.openStream(ticket);
        if (emitter == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid or expired stream ticket");
        }
        response.setHeader("X-Accel-Buffering", "no");
        response.setHeader("Cache-Control", "no-cache, no-transform");
        return emitter;
    }

    /** Mark an inquiry as read (the dashboard's inbox). */
    @PatchMapping("/inquiries/{id}/read")
    public ResponseEntity<ApiResponse<ContactMessage>> markRead(@PathVariable Long id,
                                                                @RequestParam(defaultValue = "true") boolean read) {
        return contacts.findById(id)
                .map(c -> {
                    c.setRead(read);
                    return ResponseEntity.ok(ApiResponse.success("Updated", contacts.save(c)));
                })
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error("Not found", null)));
    }

    static final List<String> TABLES = List.of("monitor_logs", "monitor_visitor_days", "daily_challenge_results",
            "daily_analytics", "contact_messages");

    /** Existence + row count of the tables the dashboard depends on (catches missing schema at a glance). */
    private List<Map<String, Object>> tables() {
        List<Map<String, Object>> out = new ArrayList<>();
        for (String t : TABLES) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("name", t);
            try {
                m.put("rows", jdbc.queryForObject("select count(*) from " + t, Long.class));
                m.put("ok", true);
            } catch (RuntimeException e) {
                m.put("ok", false);
                m.put("error", MonitorLogWriter.rootMessage(e));
            }
            out.add(m);
        }
        return out;
    }
}
