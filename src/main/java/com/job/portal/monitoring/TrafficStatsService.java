package com.job.portal.monitoring;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Last-24-hours traffic analytics from persisted monitor logs.
 *
 * One simple tuple query; all grouping happens in Java so the results are
 * identical on PostgreSQL, MySQL and H2 (no dialect-specific date functions).
 * Survives API restarts, unlike the in-memory live metrics.
 */
@Service
public class TrafficStatsService {

    static final int MAX_ROWS = 100_000;
    private static final long HOUR = 3_600_000L;

    private final MonitorLogRepository logs;

    public TrafficStatsService(MonitorLogRepository logs) {
        this.logs = logs;
    }

    private record Row(String kind, Integer status, long ts, String visitorId, String sessionId, String device,
                       String browser, String os, String country, String path) {
    }

    public Map<String, Object> last24h() {
        long now = System.currentTimeMillis();
        long firstHour = (now / HOUR - 23) * HOUR;
        List<Row> rows = logs.statsRows(Instant.ofEpochMilli(firstHour), PageRequest.of(0, MAX_ROWS)).stream()
                .map(r -> new Row((String) r[0], (Integer) r[1], ((Instant) r[2]).toEpochMilli(), (String) r[3],
                        (String) r[4], (String) r[5], (String) r[6], (String) r[7], (String) r[8], (String) r[9]))
                .toList();

        // Hourly buckets (24, oldest first)
        long[][] buckets = new long[24][4]; // pageViews, requests, serverErrors, jsErrors
        List<Set<String>> hourVisitors = new ArrayList<>();
        for (int i = 0; i < 24; i++) hourVisitors.add(new HashSet<>());
        for (Row r : rows) {
            int i = (int) ((r.ts() - firstHour) / HOUR);
            if (i < 0 || i > 23) continue;
            switch (r.kind()) {
                case LogEntry.PAGEVIEW -> {
                    buckets[i][0]++;
                    if (r.visitorId() != null) hourVisitors.get(i).add(r.visitorId());
                }
                case LogEntry.REQUEST -> {
                    buckets[i][1]++;
                    if (r.status() != null && r.status() >= 500) buckets[i][2]++;
                }
                case LogEntry.ERROR -> buckets[i][3]++;
                default -> { }
            }
        }
        List<Map<String, Object>> hourly = new ArrayList<>(24);
        for (int i = 0; i < 24; i++) {
            hourly.add(Map.of("t", firstHour + i * HOUR, "pageViews", buckets[i][0], "requests", buckets[i][1],
                    "serverErrors", buckets[i][2], "jsErrors", buckets[i][3], "visitors", (long) hourVisitors.get(i).size()));
        }

        List<Row> views = rows.stream().filter(r -> LogEntry.PAGEVIEW.equals(r.kind())).toList();
        List<Row> requests = rows.stream().filter(r -> LogEntry.REQUEST.equals(r.kind())).toList();
        Map<String, Long> perSession = views.stream().filter(r -> r.sessionId() != null)
                .collect(Collectors.groupingBy(Row::sessionId, Collectors.counting()));
        long sessions = perSession.size();
        long bounced = perSession.values().stream().filter(c -> c == 1).count();

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("since", firstHour);
        out.put("hourly", hourly);
        out.put("pageViews", views.size());
        out.put("visitors", views.stream().map(Row::visitorId).filter(Objects::nonNull).distinct().count());
        out.put("sessions", sessions);
        out.put("pagesPerSession", sessions == 0 ? 0 : Math.round(perSession.values().stream().mapToLong(Long::longValue).sum() * 10.0 / sessions) / 10.0);
        out.put("bounceRate", sessions == 0 ? 0 : Math.round(bounced * 1000.0 / sessions) / 10.0);
        out.put("requests", requests.size());
        out.put("serverErrors", requests.stream().filter(r -> r.status() != null && r.status() >= 500).count());
        out.put("clientErrors", requests.stream().filter(r -> r.status() != null && r.status() >= 400 && r.status() < 500).count());
        out.put("jsErrors", rows.stream().filter(r -> LogEntry.ERROR.equals(r.kind())).count());
        out.put("topPages", top(views, Row::path, 10));
        out.put("countries", top(views, r -> r.country() == null ? "Unknown" : r.country(), 10));
        out.put("devices", top(views, Row::device, 5));
        out.put("browsers", top(views, Row::browser, 6));
        out.put("os", top(views, Row::os, 6));
        out.put("statusClasses", top(requests, r -> r.status() == null ? "—" : (r.status() / 100) + "xx", 5));
        out.put("failingEndpoints", top(requests.stream().filter(r -> r.status() != null && r.status() >= 500).toList(),
                r -> MonitoringHub.normalize(r.path()), 6));
        out.put("truncated", rows.size() >= MAX_ROWS);
        out.put("since24h", Duration.ofHours(24).toMillis());
        return out;
    }

    private static List<Map<String, Object>> top(List<Row> rows, Function<Row, String> key, int limit) {
        return rows.stream()
                .map(key)
                .filter(Objects::nonNull)
                .collect(Collectors.groupingBy(k -> k, Collectors.counting()))
                .entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(limit)
                .map(e -> Map.<String, Object>of("name", e.getKey(), "count", e.getValue()))
                .toList();
    }
}
