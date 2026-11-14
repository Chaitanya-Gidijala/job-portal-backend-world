package com.job.portal.monitoring;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/**
 * IP → approximate location (country / region / city).
 *
 * Lookups are asynchronous, cached for 24h and rate-limited, so request handling
 * never waits on the network. Private / loopback addresses resolve locally.
 * Configure with:
 *   monitor.geoip.enabled  (default true)
 *   monitor.geoip.url      template with {ip}; must return ip-api.com style JSON
 *   monitor.geoip.per-minute  provider rate limit (ip-api.com free tier: 45/min)
 */
@Slf4j
@Service
public class GeoIpService {

    public record Geo(String country, String countryCode, String region, String city,
                      Double lat, Double lon, String isp) {
        static final Geo LOCAL = new Geo("Local network", "LAN", null, null, null, null, null);
        static final Geo UNKNOWN = new Geo(null, null, null, null, null, null, null);
    }

    private final RestTemplate restTemplate;
    private final boolean enabled;
    private final String urlTemplate;
    private final int perMinute;

    private final Cache<String, Geo> cache = Caffeine.newBuilder()
            .expireAfterWrite(Duration.ofHours(24))
            .maximumSize(50_000)
            .build();
    private final Set<String> inFlight = ConcurrentHashMap.newKeySet();
    private final Map<String, java.util.List<Consumer<Geo>>> waiters = new ConcurrentHashMap<>();
    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "geoip-lookup");
        t.setDaemon(true);
        return t;
    });
    private final AtomicLong windowStart = new AtomicLong(System.currentTimeMillis());
    private final AtomicInteger windowCount = new AtomicInteger();
    private final AtomicLong lookups = new AtomicLong();
    private final AtomicLong failures = new AtomicLong();
    private final AtomicLong rateLimited = new AtomicLong();

    public GeoIpService(RestTemplate restTemplate,
                        @Value("${monitor.geoip.enabled:true}") boolean enabled,
                        @Value("${monitor.geoip.url:http://ip-api.com/json/{ip}?fields=status,country,countryCode,regionName,city,lat,lon,isp}") String urlTemplate,
                        @Value("${monitor.geoip.per-minute:40}") int perMinute) {
        this.restTemplate = restTemplate;
        this.enabled = enabled;
        this.urlTemplate = urlTemplate;
        this.perMinute = perMinute;
    }

    /** Cached result only (null when not yet resolved). */
    public Geo cached(String ip) {
        if (ip == null) return null;
        if (isPrivate(ip)) return Geo.LOCAL;
        return cache.getIfPresent(ip);
    }

    /** Resolve in the background; `callback` runs once a result is known (immediately if cached). */
    public void resolve(String ip, Consumer<Geo> callback) {
        Geo known = cached(ip);
        if (known != null) {
            callback.accept(known);
            return;
        }
        if (!enabled || ip == null) {
            return;
        }
        waiters.computeIfAbsent(ip, k -> new java.util.concurrent.CopyOnWriteArrayList<>()).add(callback);
        if (!inFlight.add(ip)) {
            return;
        }
        executor.submit(() -> {
            Geo geo = fetch(ip);
            if (geo != null) {
                cache.put(ip, geo);
            }
            inFlight.remove(ip);
            var list = waiters.remove(ip);
            if (geo != null && list != null) {
                list.forEach(cb -> {
                    try {
                        cb.accept(geo);
                    } catch (RuntimeException e) {
                        log.debug("geo callback failed", e);
                    }
                });
            }
        });
    }

    private boolean allowRequest() {
        long now = System.currentTimeMillis();
        if (now - windowStart.get() > 60_000) {
            windowStart.set(now);
            windowCount.set(0);
        }
        return windowCount.incrementAndGet() <= perMinute;
    }

    @SuppressWarnings("unchecked")
    private Geo fetch(String ip) {
        if (!allowRequest()) {
            rateLimited.incrementAndGet();
            return null; // retried on the next request from this IP
        }
        lookups.incrementAndGet();
        try {
            Map<String, Object> body = restTemplate.getForObject(urlTemplate.replace("{ip}", ip), Map.class);
            if (body == null || !"success".equals(body.get("status"))) {
                failures.incrementAndGet();
                return Geo.UNKNOWN;
            }
            return new Geo(
                    (String) body.get("country"),
                    (String) body.get("countryCode"),
                    (String) body.get("regionName"),
                    (String) body.get("city"),
                    body.get("lat") instanceof Number n ? n.doubleValue() : null,
                    body.get("lon") instanceof Number n ? n.doubleValue() : null,
                    (String) body.get("isp"));
        } catch (RuntimeException e) {
            failures.incrementAndGet();
            log.debug("GeoIP lookup failed for {}: {}", ip, e.getMessage());
            return null;
        }
    }

    public Map<String, Object> status() {
        return Map.of(
                "enabled", enabled,
                "cachedIps", cache.estimatedSize(),
                "lookups", lookups.get(),
                "failures", failures.get(),
                "rateLimited", rateLimited.get(),
                "perMinuteLimit", perMinute);
    }

    static boolean isPrivate(String ip) {
        return ip.startsWith("10.") || ip.startsWith("192.168.") || ip.startsWith("127.")
                || ip.equals("0:0:0:0:0:0:0:1") || ip.equals("::1") || ip.startsWith("fc") || ip.startsWith("fd")
                || ip.matches("^172\\.(1[6-9]|2\\d|3[01])\\..*");
    }
}
