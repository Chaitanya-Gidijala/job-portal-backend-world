package com.job.portal.monitoring;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Public ingestion endpoints for the React tracker.
 *
 * Bodies are JSON but accepted as text/plain too, so the browser can use
 * navigator.sendBeacon / simple CORS requests (no preflight). Everything is
 * length-capped and rate-limited per session.
 */
@RestController
@RequestMapping("/api/monitor")
public class MonitorIngestController {

    static final int MAX_EVENTS_PER_BATCH = 50;
    static final int MAX_EVENTS_PER_MINUTE = 300;

    private final PresenceService presence;
    private final MonitoringHub hub;
    private final GeoIpService geo;
    private final ObjectMapper mapper;
    private final Map<String, AtomicInteger> budget = new ConcurrentHashMap<>();
    private volatile long budgetMinute = System.currentTimeMillis() / 60_000;

    public MonitorIngestController(PresenceService presence, MonitoringHub hub, GeoIpService geo, ObjectMapper mapper) {
        this.presence = presence;
        this.hub = hub;
        this.geo = geo;
        this.mapper = mapper;
    }

    @PostMapping(value = "/presence", consumes = {MediaType.TEXT_PLAIN_VALUE, MediaType.APPLICATION_JSON_VALUE, MediaType.ALL_VALUE})
    public ResponseEntity<Void> presence(@RequestBody String body, HttpServletRequest request) {
        JsonNode n = parse(body);
        if (n == null) return ResponseEntity.badRequest().build();
        presence.ping(new PresenceService.Ping(
                text(n, "sessionId"), text(n, "visitorId"), text(n, "path"), text(n, "title"), text(n, "referrer"),
                text(n, "screen"), text(n, "language"), text(n, "timezone"), text(n, "appVersion"),
                n.has("visible") ? n.get("visible").asBoolean(true) : null, null),
                ClientInfo.ip(request), request.getHeader("User-Agent"));
        return ResponseEntity.noContent().build();
    }

    @PostMapping(value = "/leave", consumes = {MediaType.TEXT_PLAIN_VALUE, MediaType.APPLICATION_JSON_VALUE, MediaType.ALL_VALUE})
    public ResponseEntity<Void> leave(@RequestBody(required = false) String body) {
        JsonNode n = parse(body);
        if (n != null) presence.leave(text(n, "sessionId"));
        return ResponseEntity.noContent().build();
    }

    @PostMapping(value = "/events", consumes = {MediaType.TEXT_PLAIN_VALUE, MediaType.APPLICATION_JSON_VALUE, MediaType.ALL_VALUE})
    public ResponseEntity<Void> events(@RequestBody String body, HttpServletRequest request) {
        JsonNode n = parse(body);
        if (n == null || !n.has("events") || !n.get("events").isArray()) return ResponseEntity.badRequest().build();

        String sessionId = text(n, "sessionId");
        String visitorId = text(n, "visitorId");
        String appVersion = text(n, "appVersion");
        String ip = ClientInfo.ip(request);
        String ua = request.getHeader("User-Agent");
        GeoIpService.Geo g = geo.cached(ip);
        PresenceService.LiveSession session = presence.get(sessionId);
        String username = session == null ? null : session.getUsername();

        int accepted = 0;
        for (JsonNode ev : n.get("events")) {
            if (accepted >= MAX_EVENTS_PER_BATCH || !spend(sessionId == null ? ip : sessionId)) break;
            String kind = switch (text(ev, "type") == null ? "" : text(ev, "type")) {
                case "pageview" -> LogEntry.PAGEVIEW;
                case "click" -> LogEntry.CLICK;
                case "error" -> LogEntry.ERROR;
                case "vital" -> LogEntry.VITAL;
                default -> null;
            };
            if (kind == null) continue;
            long ts = ev.has("ts") ? ev.get("ts").asLong(System.currentTimeMillis()) : System.currentTimeMillis();
            // Clamp client clocks to a sane window around server time.
            long now = System.currentTimeMillis();
            if (Math.abs(now - ts) > 10 * 60_000) ts = now;

            hub.record(LogEntry.builder()
                    .ts(ts)
                    .kind(kind)
                    .path(ClientInfo.truncate(text(ev, "path"), 500))
                    .label(ClientInfo.truncate(text(ev, "label"), 500))
                    .detail(ClientInfo.truncate(text(ev, "detail"), 1000))
                    .ip(ip)
                    .username(username)
                    .visitorId(ClientInfo.truncate(visitorId, 64))
                    .sessionId(ClientInfo.truncate(sessionId, 64))
                    .userAgent(ClientInfo.truncate(ua, 400))
                    .browser(ClientInfo.browser(ua))
                    .os(ClientInfo.os(ua))
                    .device(ClientInfo.device(ua))
                    .country(g == null ? null : g.country())
                    .city(g == null ? null : g.city())
                    .appVersion(ClientInfo.truncate(appVersion, 40))
                    .build());
            accepted++;
        }
        return ResponseEntity.noContent().build();
    }

    private boolean spend(String key) {
        long minute = System.currentTimeMillis() / 60_000;
        if (minute != budgetMinute) {
            budgetMinute = minute;
            budget.clear();
        }
        return budget.computeIfAbsent(key, k -> new AtomicInteger()).incrementAndGet() <= MAX_EVENTS_PER_MINUTE;
    }

    private JsonNode parse(String body) {
        if (body == null || body.isBlank() || body.length() > 64_000) return null;
        try {
            JsonNode n = mapper.readTree(body);
            return n != null && n.isObject() ? n : null;
        } catch (Exception e) {
            return null;
        }
    }

    private static String text(JsonNode n, String field) {
        JsonNode v = n.get(field);
        return v == null || v.isNull() ? null : v.asText();
    }
}
