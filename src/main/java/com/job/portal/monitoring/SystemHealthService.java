package com.job.portal.monitoring;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.health.HealthComponent;
import org.springframework.boot.actuate.health.HealthEndpoint;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.io.File;
import java.lang.management.*;
import java.net.HttpURLConnection;
import java.net.URI;
import java.sql.Connection;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;

/**
 * Application health for the admin Monitoring page.
 *
 *   • this Spring Boot API: JVM memory, CPU, threads, GC, uptime, Actuator health
 *   • the database: connection check + latency
 *   • dependent services (security-service, the React site): HTTP probes every
 *     minute with a rolling history for uptime bars
 *
 * Probe targets: monitor.targets.security-url / monitor.targets.frontend-url.
 */
@Slf4j
@Service
public class SystemHealthService {

    static final int HISTORY = 60;

    public record Probe(long at, boolean up, int status, long latencyMs, String error) {
    }

    private final DataSource dataSource;
    private final ObjectProvider<HealthEndpoint> healthEndpoint;
    private final Map<String, String> targets = new LinkedHashMap<>();
    private final Map<String, ConcurrentLinkedDeque<Probe>> history = new ConcurrentHashMap<>();

    public SystemHealthService(DataSource dataSource,
                               ObjectProvider<HealthEndpoint> healthEndpoint,
                               @Value("${monitor.targets.security-url:https://security-service-chaitanyatechworld.onrender.com/api/health}") String securityUrl,
                               @Value("${monitor.targets.frontend-url:https://chaitanyatechworld.com/}") String frontendUrl) {
        this.dataSource = dataSource;
        this.healthEndpoint = healthEndpoint;
        if (!securityUrl.isBlank()) targets.put("security-service", securityUrl);
        if (!frontendUrl.isBlank()) targets.put("react-frontend", frontendUrl);
    }

    /* ── This JVM ─────────────────────────────────────────────────────── */

    public Map<String, Object> jvm() {
        MemoryMXBean memory = ManagementFactory.getMemoryMXBean();
        MemoryUsage heap = memory.getHeapMemoryUsage();
        MemoryUsage nonHeap = memory.getNonHeapMemoryUsage();
        ThreadMXBean threads = ManagementFactory.getThreadMXBean();
        RuntimeMXBean runtime = ManagementFactory.getRuntimeMXBean();
        OperatingSystemMXBean os = ManagementFactory.getOperatingSystemMXBean();

        Double processCpu = null;
        Double systemCpu = null;
        Long totalMemory = null;
        Long freeMemory = null;
        if (os instanceof com.sun.management.OperatingSystemMXBean sun) {
            processCpu = round(sun.getProcessCpuLoad() * 100);
            systemCpu = round(sun.getCpuLoad() * 100);
            totalMemory = sun.getTotalMemorySize();
            freeMemory = sun.getFreeMemorySize();
        }

        long gcCount = 0;
        long gcTime = 0;
        for (GarbageCollectorMXBean gc : ManagementFactory.getGarbageCollectorMXBeans()) {
            gcCount += Math.max(0, gc.getCollectionCount());
            gcTime += Math.max(0, gc.getCollectionTime());
        }

        File disk = new File(".");
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("uptimeMs", runtime.getUptime());
        out.put("startedAt", runtime.getStartTime());
        out.put("javaVersion", System.getProperty("java.version"));
        out.put("processors", os.getAvailableProcessors());
        out.put("systemLoad", round(os.getSystemLoadAverage()));
        out.put("processCpu", processCpu);
        out.put("systemCpu", systemCpu);
        out.put("heapUsed", heap.getUsed());
        out.put("heapCommitted", heap.getCommitted());
        out.put("heapMax", heap.getMax());
        out.put("nonHeapUsed", nonHeap.getUsed());
        out.put("hostMemoryTotal", totalMemory);
        out.put("hostMemoryFree", freeMemory);
        out.put("threads", threads.getThreadCount());
        out.put("peakThreads", threads.getPeakThreadCount());
        out.put("daemonThreads", threads.getDaemonThreadCount());
        out.put("gcCount", gcCount);
        out.put("gcTimeMs", gcTime);
        out.put("diskFree", disk.getUsableSpace());
        out.put("diskTotal", disk.getTotalSpace());
        return out;
    }

    /** Database round trip. */
    public Map<String, Object> database() {
        long start = System.nanoTime();
        Map<String, Object> out = new LinkedHashMap<>();
        try (Connection c = dataSource.getConnection()) {
            boolean valid = c.isValid(3);
            out.put("up", valid);
            out.put("product", c.getMetaData().getDatabaseProductName());
            out.put("version", c.getMetaData().getDatabaseProductVersion());
        } catch (Exception e) {
            out.put("up", false);
            out.put("error", e.getMessage());
        }
        out.put("latencyMs", (System.nanoTime() - start) / 1_000_000);
        return out;
    }

    /** Spring Boot Actuator's aggregated status (db, diskSpace, ping …). */
    public Object actuator() {
        HealthEndpoint endpoint = healthEndpoint.getIfAvailable();
        if (endpoint == null) return Map.of("status", "UNKNOWN");
        HealthComponent health = endpoint.health();
        return health;
    }

    /* ── Dependent services ───────────────────────────────────────────── */

    @Scheduled(initialDelay = 5_000, fixedDelayString = "${monitor.probe-interval-ms:60000}")
    public void probeAll() {
        targets.forEach((name, url) -> {
            Probe p = probe(url);
            var h = history.computeIfAbsent(name, k -> new ConcurrentLinkedDeque<>());
            h.addLast(p);
            while (h.size() > HISTORY) h.pollFirst();
        });
    }

    static Probe probe(String url) {
        long start = System.nanoTime();
        try {
            HttpURLConnection conn = (HttpURLConnection) URI.create(url).toURL().openConnection();
            conn.setConnectTimeout(5_000);
            conn.setReadTimeout(20_000); // free-tier services can take a while to wake up
            conn.setRequestProperty("User-Agent", "ctw-monitor/1.0");
            int status = conn.getResponseCode();
            conn.disconnect();
            return new Probe(System.currentTimeMillis(), status < 500, status, (System.nanoTime() - start) / 1_000_000, null);
        } catch (Exception e) {
            return new Probe(System.currentTimeMillis(), false, 0, (System.nanoTime() - start) / 1_000_000, e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    public List<Map<String, Object>> services() {
        List<Map<String, Object>> out = new ArrayList<>();
        targets.forEach((name, url) -> {
            List<Probe> h = new ArrayList<>(history.getOrDefault(name, new ConcurrentLinkedDeque<>()));
            Probe last = h.isEmpty() ? null : h.get(h.size() - 1);
            long up = h.stream().filter(Probe::up).count();
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("name", name);
            m.put("url", url);
            m.put("status", last == null ? "PENDING" : last.up() ? "UP" : "DOWN");
            m.put("last", last);
            m.put("uptimePct", h.isEmpty() ? null : round(up * 100.0 / h.size()));
            m.put("history", h);
            out.add(m);
        });
        return out;
    }

    private static Double round(double v) {
        return v < 0 || Double.isNaN(v) ? null : Math.round(v * 10) / 10.0;
    }
}
