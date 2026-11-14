package com.job.portal.monitoring;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.security.SecureRandom;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;
import java.util.regex.Pattern;

/**
 * Central real-time monitoring state.
 *
 *   • ring buffer of the latest log lines (requests + client events)
 *   • per-minute request metrics for the last hour, latency percentiles for 5 minutes
 *   • client-side health (JS errors, Web Vitals, app versions)
 *   • the admin live stream: one-time tickets → SSE, with batched `logs`,
 *     `presence` and `metrics` frames
 */
@Slf4j
@Component
public class MonitoringHub {

    static final int RING_SIZE = 3_000;
    static final long TICKET_TTL_MS = 60_000;
    static final long STREAM_TIMEOUT_MS = 30 * 60 * 1000L;
    private static final Pattern ID_SEGMENT = Pattern.compile("/(\\d+|[0-9a-fA-F-]{16,})(?=/|$)");

    private final PresenceService presence;
    private final MonitorLogWriter writer;
    private final AtomicLong seq = new AtomicLong();

    private final Deque<LogEntry> ring = new ArrayDeque<>(RING_SIZE);
    private final ConcurrentLinkedQueue<LogEntry> outbox = new ConcurrentLinkedQueue<>();

    /* metrics */
    private static final class Bucket {
        final AtomicLong requests = new AtomicLong();
        final AtomicLong errors = new AtomicLong();
        final AtomicLong clientErrors = new AtomicLong();
        final AtomicLong totalMs = new AtomicLong();
        final AtomicLong pageViews = new AtomicLong();
        final AtomicLong clicks = new AtomicLong();
        final AtomicLong jsErrors = new AtomicLong();
    }

    private final Map<Long, Bucket> buckets = new ConcurrentHashMap<>();
    private final ConcurrentLinkedDeque<long[]> latencies = new ConcurrentLinkedDeque<>();
    private final Map<String, AtomicLong> endpointHits = new ConcurrentHashMap<>();
    private final Map<String, ConcurrentLinkedDeque<Double>> vitals = new ConcurrentHashMap<>();
    private final Map<String, AtomicLong> appVersions = new ConcurrentHashMap<>();
    private final Deque<LogEntry> recentErrors = new ArrayDeque<>();
    private final AtomicLong totalRequests = new AtomicLong();
    private final long startedAt = System.currentTimeMillis();

    /* admin stream */
    private final Map<String, Long> tickets = new ConcurrentHashMap<>();
    private final Map<SseEmitter, String> streams = new ConcurrentHashMap<>();
    private final SecureRandom random = new SecureRandom();
    private volatile long lastPresenceVersion = -1;
    private volatile Supplier<Object> metricsSupplier = () -> null;

    public MonitoringHub(PresenceService presence, MonitorLogWriter writer) {
        this.presence = presence;
        this.writer = writer;
    }

    /* ── Recording ─────────────────────────────────────────────────────── */

    public void record(LogEntry e) {
        e.setSeq(seq.incrementAndGet());
        if (e.getTs() == 0) e.setTs(System.currentTimeMillis());
        synchronized (ring) {
            if (ring.size() >= RING_SIZE) ring.removeFirst();
            ring.addLast(e);
        }
        outbox.add(e);
        count(e);
        if (!LogEntry.HEARTBEAT.equals(e.getKind())) {
            writer.enqueue(e);
        }
    }

    private void count(LogEntry e) {
        Bucket b = buckets.computeIfAbsent(e.getTs() / 60_000, k -> new Bucket());
        switch (e.getKind()) {
            case LogEntry.REQUEST -> {
                totalRequests.incrementAndGet();
                b.requests.incrementAndGet();
                long ms = e.getDurationMs() == null ? 0 : e.getDurationMs();
                b.totalMs.addAndGet(ms);
                int status = e.getStatus() == null ? 0 : e.getStatus();
                if (status >= 500) b.errors.incrementAndGet();
                else if (status >= 400) b.clientErrors.incrementAndGet();
                latencies.addLast(new long[]{e.getTs(), ms});
                if (latencies.size() > 50_000) latencies.pollFirst();
                endpointHits.computeIfAbsent(e.getMethod() + " " + normalize(e.getPath()), k -> new AtomicLong()).incrementAndGet();
                if (status >= 500) remember(e);
            }
            case LogEntry.PAGEVIEW -> b.pageViews.incrementAndGet();
            case LogEntry.CLICK -> b.clicks.incrementAndGet();
            case LogEntry.ERROR -> {
                b.jsErrors.incrementAndGet();
                remember(e);
            }
            case LogEntry.VITAL -> {
                try {
                    double v = Double.parseDouble(e.getDetail());
                    var series = vitals.computeIfAbsent(e.getLabel(), k -> new ConcurrentLinkedDeque<>());
                    series.addLast(v);
                    if (series.size() > 500) series.pollFirst();
                } catch (RuntimeException ignored) {
                    // malformed vital
                }
            }
            default -> { }
        }
        if (e.getAppVersion() != null && LogEntry.PAGEVIEW.equals(e.getKind())) {
            appVersions.computeIfAbsent(e.getAppVersion(), k -> new AtomicLong()).incrementAndGet();
        }
    }

    private void remember(LogEntry e) {
        synchronized (recentErrors) {
            recentErrors.addFirst(e);
            while (recentErrors.size() > 50) recentErrors.removeLast();
        }
    }

    /** "/api/jobs/42" → "/api/jobs/{id}" so endpoint stats group sensibly. */
    static String normalize(String path) {
        return path == null ? "" : ID_SEGMENT.matcher(path).replaceAll("/{id}");
    }

    /* ── Reads ─────────────────────────────────────────────────────────── */

    public List<LogEntry> recent(int limit, boolean includeHeartbeats) {
        List<LogEntry> out = new ArrayList<>();
        synchronized (ring) {
            Iterator<LogEntry> it = ring.descendingIterator();
            while (it.hasNext() && out.size() < limit) {
                LogEntry e = it.next();
                if (includeHeartbeats || !LogEntry.HEARTBEAT.equals(e.getKind())) out.add(e);
            }
        }
        return out;
    }

    /** Request-rate series for the last `minutes` minutes plus latency percentiles. */
    public Map<String, Object> metrics(int minutes) {
        long nowMinute = System.currentTimeMillis() / 60_000;
        List<Map<String, Object>> series = new ArrayList<>(minutes);
        long req = 0, err = 0, cerr = 0, ms = 0, views = 0, clicks = 0, jsErr = 0;
        for (long m = nowMinute - minutes + 1; m <= nowMinute; m++) {
            Bucket b = buckets.get(m);
            long r = b == null ? 0 : b.requests.get();
            long e = b == null ? 0 : b.errors.get();
            long c = b == null ? 0 : b.clientErrors.get();
            long t = b == null ? 0 : b.totalMs.get();
            long v = b == null ? 0 : b.pageViews.get();
            long k = b == null ? 0 : b.clicks.get();
            long j = b == null ? 0 : b.jsErrors.get();
            req += r; err += e; cerr += c; ms += t; views += v; clicks += k; jsErr += j;
            series.add(Map.of("t", m * 60_000, "requests", r, "errors", e, "clientErrors", c,
                    "avgMs", r == 0 ? 0 : t / r, "pageViews", v, "clicks", k, "jsErrors", j));
        }

        long cutoff = System.currentTimeMillis() - 5 * 60_000;
        long[] recent = latencies.stream().filter(x -> x[0] >= cutoff).mapToLong(x -> x[1]).sorted().toArray();

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("series", series);
        out.put("requests", req);
        out.put("serverErrors", err);
        out.put("clientErrors", cerr);
        out.put("errorRate", req == 0 ? 0 : Math.round(err * 10000.0 / req) / 100.0);
        out.put("avgMs", req == 0 ? 0 : ms / req);
        out.put("p50Ms", percentile(recent, 0.50));
        out.put("p95Ms", percentile(recent, 0.95));
        out.put("p99Ms", percentile(recent, 0.99));
        out.put("rpm", series.isEmpty() ? 0 : ((Map<?, ?>) series.get(series.size() - 1)).get("requests"));
        out.put("pageViews", views);
        out.put("clicks", clicks);
        out.put("jsErrors", jsErr);
        out.put("totalRequests", totalRequests.get());
        out.put("since", startedAt);
        return out;
    }

    public List<Map<String, Object>> topEndpoints(int limit) {
        return endpointHits.entrySet().stream()
                .sorted((a, b) -> Long.compare(b.getValue().get(), a.getValue().get()))
                .limit(limit)
                .map(e -> Map.<String, Object>of("endpoint", e.getKey(), "count", e.getValue().get()))
                .toList();
    }

    /** React-side health: JS errors, Web Vitals p75, versions in the wild. */
    public Map<String, Object> clientHealth() {
        Map<String, Object> vit = new LinkedHashMap<>();
        vitals.forEach((name, values) -> {
            double[] sorted = values.stream().mapToDouble(Double::doubleValue).sorted().toArray();
            if (sorted.length > 0) {
                vit.put(name, Map.of("p75", sorted[(int) Math.floor((sorted.length - 1) * 0.75)], "samples", sorted.length));
            }
        });
        Map<String, Long> versions = new TreeMap<>(Comparator.reverseOrder());
        appVersions.forEach((k, v) -> versions.put(k, v.get()));
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("vitals", vit);
        out.put("versions", versions);
        Map<String, Object> m = metrics(60);
        out.put("jsErrorsLastHour", m.get("jsErrors"));
        out.put("pageViewsLastHour", m.get("pageViews"));
        return out;
    }

    public List<LogEntry> recentErrors() {
        synchronized (recentErrors) {
            return new ArrayList<>(recentErrors);
        }
    }

    private static long percentile(long[] sorted, double p) {
        if (sorted.length == 0) return 0;
        return sorted[(int) Math.min(sorted.length - 1, Math.floor(p * (sorted.length - 1)))];
    }

    /* ── Admin live stream ─────────────────────────────────────────────── */

    public void setMetricsSupplier(Supplier<Object> supplier) {
        this.metricsSupplier = supplier;
    }

    /** One-time, short-lived ticket — EventSource cannot send an Authorization header. */
    public String issueTicket(String username) {
        byte[] bytes = new byte[24];
        random.nextBytes(bytes);
        String ticket = HexFormat.of().formatHex(bytes);
        tickets.put(ticket, System.currentTimeMillis() + TICKET_TTL_MS);
        log.info("Admin live-stream ticket issued to {}", username);
        return ticket;
    }

    public SseEmitter openStream(String ticket) {
        Long expires = ticket == null ? null : tickets.remove(ticket);
        if (expires == null || expires < System.currentTimeMillis()) {
            return null;
        }
        SseEmitter emitter = new SseEmitter(STREAM_TIMEOUT_MS);
        streams.put(emitter, ticket);
        emitter.onCompletion(() -> streams.remove(emitter));
        emitter.onTimeout(() -> {
            streams.remove(emitter);
            emitter.complete();
        });
        emitter.onError(e -> streams.remove(emitter));

        send(emitter, "presence", presencePayload());
        List<LogEntry> backlog = recent(200, false);
        Collections.reverse(backlog);
        send(emitter, "logs", backlog);
        Object metrics = metricsSupplier.get();
        if (metrics != null) send(emitter, "metrics", metrics);
        return emitter;
    }

    public int streamCount() {
        return streams.size();
    }

    private Map<String, Object> presencePayload() {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("sessions", presence.snapshot());
        p.put("breakdown", presence.breakdown());
        p.put("at", System.currentTimeMillis());
        return p;
    }

    /** Push new log lines and presence changes once per second. */
    @Scheduled(fixedDelay = 1_000)
    public void flushStream() {
        List<LogEntry> batch = new ArrayList<>();
        LogEntry e;
        while ((e = outbox.poll()) != null && batch.size() < 1_000) batch.add(e);
        if (streams.isEmpty()) {
            outbox.clear();
            return;
        }
        if (!batch.isEmpty()) broadcast("logs", batch);
        long v = presence.version();
        if (v != lastPresenceVersion) {
            lastPresenceVersion = v;
            broadcast("presence", presencePayload());
        }
    }

    @Scheduled(fixedRate = 5_000)
    public void pushMetrics() {
        if (streams.isEmpty()) return;
        Object metrics = metricsSupplier.get();
        if (metrics != null) broadcast("metrics", metrics);
    }

    @Scheduled(fixedRate = 15_000)
    public void housekeeping() {
        long now = System.currentTimeMillis();
        tickets.values().removeIf(expiry -> expiry < now);
        long oldestMinute = now / 60_000 - 61;
        buckets.keySet().removeIf(m -> m < oldestMinute);
        long cutoff = now - 5 * 60_000;
        while (!latencies.isEmpty() && latencies.peekFirst()[0] < cutoff) latencies.pollFirst();
        streams.keySet().forEach(em -> {
            try {
                em.send(SseEmitter.event().comment("ping"));
            } catch (IOException | IllegalStateException ex) {
                streams.remove(em);
            }
        });
    }

    private void broadcast(String name, Object payload) {
        streams.keySet().forEach(em -> send(em, name, payload));
    }

    private void send(SseEmitter emitter, String name, Object payload) {
        try {
            emitter.send(SseEmitter.event().name(name).data(payload, MediaType.APPLICATION_JSON));
        } catch (IOException | IllegalStateException ex) {
            streams.remove(emitter);
            emitter.completeWithError(ex);
        }
    }
}
