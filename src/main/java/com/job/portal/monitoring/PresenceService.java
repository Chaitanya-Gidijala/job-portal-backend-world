package com.job.portal.monitoring;

import com.job.portal.entity.DailyAnalytics;
import com.job.portal.repository.DailyAnalyticsRepository;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * Who is on the site right now.
 *
 * The React app sends a heartbeat every ~15s (and on every route change) while a
 * tab is open; sessions that stop beating for {@link #TTL_MS} drop off. Page views
 * and unique visitors are also counted here and flushed into {@link DailyAnalytics}.
 */
@Slf4j
@Service
public class PresenceService {

    static final long TTL_MS = 45_000;
    static final int TRAIL = 12;

    /** A visitor tab that is currently open. */
    @Data
    public static class LiveSession {
        private String sessionId;
        private String visitorId;
        private String username;
        private String email;
        private String ip;
        private String userAgent;
        private String browser;
        private String os;
        private String device;
        private String country;
        private String countryCode;
        private String region;
        private String city;
        private Double lat;
        private Double lon;
        private String isp;
        private String timezone;
        private String language;
        private String screen;
        private String path;
        private String title;
        private String referrer;
        private String appVersion;
        private long startedAt;
        private long lastSeen;
        private long pageSince;
        private int pageViews;
        private boolean visible = true;
        private boolean returning;
        private List<String> trail = new ArrayList<>();

        /** Consistent copy for serialisation while heartbeats keep mutating the original. */
        LiveSession copy() {
            synchronized (this) {
                LiveSession c = new LiveSession();
                org.springframework.beans.BeanUtils.copyProperties(this, c, "trail");
                c.setTrail(new ArrayList<>(trail));
                return c;
            }
        }
    }

    /** Heartbeat payload sent by the React tracker. */
    public record Ping(String sessionId, String visitorId, String path, String title, String referrer,
                       String screen, String language, String timezone, String appVersion, Boolean visible,
                       String username) {
    }

    private final GeoIpService geo;
    private final DailyAnalyticsRepository analyticsRepository;
    private final VisitorDayRepository visitorDayRepository;

    private final Map<String, LiveSession> sessions = new ConcurrentHashMap<>();
    /** ip|ua → sessionId, to attribute API requests to a visitor. */
    private final Map<String, String> clientIndex = new ConcurrentHashMap<>();
    private final AtomicLong version = new AtomicLong();

    private volatile LocalDate today = LocalDate.now();
    private final Set<String> seenToday = ConcurrentHashMap.newKeySet();
    private final AtomicLong pendingViews = new AtomicLong();
    private final AtomicLong pendingUniques = new AtomicLong();
    private final AtomicInteger peakToday = new AtomicInteger();
    private volatile long peakAt;

    public PresenceService(GeoIpService geo, DailyAnalyticsRepository analyticsRepository,
                           VisitorDayRepository visitorDayRepository) {
        this.geo = geo;
        this.analyticsRepository = analyticsRepository;
        this.visitorDayRepository = visitorDayRepository;
    }

    /** Record a heartbeat. Returns true when the visitor moved to a new page (a page view). */
    public boolean ping(Ping p, String ip, String userAgent) {
        if (p.sessionId() == null || p.sessionId().isBlank()) {
            return false;
        }
        rollDay();
        long now = System.currentTimeMillis();
        String sessionId = ClientInfo.truncate(p.sessionId(), 64);
        boolean[] newPage = {false};

        LiveSession s = sessions.computeIfAbsent(sessionId, id -> {
            LiveSession n = new LiveSession();
            n.setSessionId(id);
            n.setStartedAt(now);
            n.setIp(ip);
            n.setUserAgent(ClientInfo.truncate(userAgent, 400));
            n.setBrowser(ClientInfo.browser(userAgent));
            n.setOs(ClientInfo.os(userAgent));
            n.setDevice(ClientInfo.device(userAgent));
            applyGeo(n, geo.cached(ip));
            geo.resolve(ip, g -> {
                applyGeo(n, g);
                version.incrementAndGet();
            });
            return n;
        });

        synchronized (s) {
            s.setLastSeen(now);
            s.setVisitorId(ClientInfo.truncate(p.visitorId(), 64));
            if (p.username() != null) s.setUsername(ClientInfo.truncate(p.username(), 120));
            if (p.screen() != null) s.setScreen(ClientInfo.truncate(p.screen(), 20));
            if (p.language() != null) s.setLanguage(ClientInfo.truncate(p.language(), 20));
            if (p.timezone() != null) s.setTimezone(ClientInfo.truncate(p.timezone(), 60));
            if (p.appVersion() != null) s.setAppVersion(ClientInfo.truncate(p.appVersion(), 40));
            if (p.visible() != null) s.setVisible(p.visible());
            String path = ClientInfo.truncate(p.path(), 300);
            if (path != null && !path.equals(s.getPath())) {
                newPage[0] = true;
                s.setPath(path);
                s.setPageSince(now);
                s.setPageViews(s.getPageViews() + 1);
                s.getTrail().add(path);
                if (s.getTrail().size() > TRAIL) s.getTrail().remove(0);
                if (s.getReferrer() == null && p.referrer() != null && !p.referrer().isBlank()) {
                    s.setReferrer(ClientInfo.truncate(p.referrer(), 300));
                }
            }
            if (p.title() != null) s.setTitle(ClientInfo.truncate(p.title(), 200));
        }
        clientIndex.put(clientKey(ip, userAgent), sessionId);

        if (newPage[0]) {
            pendingViews.incrementAndGet();
        }
        if (s.getVisitorId() != null && seenToday.add(s.getVisitorId())) {
            markUnique(s);
        }
        int live = sessions.size();
        if (live > peakToday.get()) {
            peakToday.set(live);
            peakAt = now;
        }
        version.incrementAndGet();
        return newPage[0];
    }

    private void markUnique(LiveSession s) {
        try {
            if (visitorDayRepository.existsByDayAndVisitorId(today, s.getVisitorId())) {
                s.setReturning(true); // seen earlier today (before a restart)
                return;
            }
            visitorDayRepository.save(new VisitorDay(today, s.getVisitorId()));
            pendingUniques.incrementAndGet();
            s.setReturning(false);
        } catch (DataIntegrityViolationException race) {
            // another tab registered this visitor at the same moment
        } catch (RuntimeException e) {
            log.debug("visitor-day write failed: {}", e.getMessage());
        }
    }

    public void leave(String sessionId) {
        if (sessionId != null && sessions.remove(sessionId) != null) {
            version.incrementAndGet();
        }
    }

    /** Session for an API request (matched by IP + user agent), or null. */
    public LiveSession findByClient(String ip, String userAgent) {
        String id = clientIndex.get(clientKey(ip, userAgent));
        return id == null ? null : sessions.get(id);
    }

    public LiveSession get(String sessionId) {
        return sessionId == null ? null : sessions.get(sessionId);
    }

    private static String clientKey(String ip, String ua) {
        return ip + '|' + (ua == null ? "" : ua.hashCode());
    }

    private static void applyGeo(LiveSession s, GeoIpService.Geo g) {
        if (g == null) return;
        s.setCountry(g.country());
        s.setCountryCode(g.countryCode());
        s.setRegion(g.region());
        s.setCity(g.city());
        s.setLat(g.lat());
        s.setLon(g.lon());
        s.setIsp(g.isp());
    }

    /* ── Housekeeping ──────────────────────────────────────────────────── */

    @Scheduled(fixedDelay = 5_000)
    public void sweep() {
        long cutoff = System.currentTimeMillis() - TTL_MS;
        boolean removed = sessions.values().removeIf(s -> s.getLastSeen() < cutoff);
        clientIndex.values().removeIf(id -> !sessions.containsKey(id));
        if (removed) {
            version.incrementAndGet();
        }
    }

    /** Flush counters into the daily analytics row. */
    @Scheduled(fixedDelay = 10_000)
    public void flushAnalytics() {
        long views = pendingViews.getAndSet(0);
        long uniques = pendingUniques.getAndSet(0);
        if (views == 0 && uniques == 0) {
            return;
        }
        try {
            DailyAnalytics row = analyticsRepository.findById(today).orElseGet(() -> DailyAnalytics.builder()
                    .dateId(today).totalViews(0L).uniqueVisitors(0L).userLogins(0L).build());
            row.setTotalViews(row.getTotalViews() + views);
            row.setUniqueVisitors(row.getUniqueVisitors() + uniques);
            analyticsRepository.save(row);
        } catch (RuntimeException e) {
            pendingViews.addAndGet(views);
            pendingUniques.addAndGet(uniques);
            log.debug("analytics flush failed: {}", e.getMessage());
        }
    }

    private void rollDay() {
        LocalDate now = LocalDate.now();
        if (!now.equals(today)) {
            flushAnalytics();
            today = now;
            seenToday.clear();
            peakToday.set(sessions.size());
            peakAt = System.currentTimeMillis();
        }
    }

    /* ── Reads ─────────────────────────────────────────────────────────── */

    public long version() {
        return version.get();
    }

    public List<LiveSession> snapshot() {
        return sessions.values().stream()
                .map(LiveSession::copy)
                .sorted(Comparator.comparingLong(LiveSession::getStartedAt).reversed())
                .toList();
    }

    public int liveCount() {
        return sessions.size();
    }

    public int peakToday() {
        return peakToday.get();
    }

    public long peakAt() {
        return peakAt;
    }

    /** Aggregates of the current sessions for charts. */
    public Map<String, Object> breakdown() {
        List<LiveSession> list = snapshot();
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("live", list.size());
        out.put("visitors", list.stream().map(LiveSession::getVisitorId).filter(Objects::nonNull).distinct().count());
        out.put("pages", top(list, LiveSession::getPath, 10));
        out.put("countries", top(list, s -> s.getCountry() == null ? "Unknown" : s.getCountry(), 10));
        out.put("devices", top(list, LiveSession::getDevice, 5));
        out.put("browsers", top(list, LiveSession::getBrowser, 6));
        out.put("referrers", top(list, s -> referrerHost(s.getReferrer()), 8));
        out.put("peakToday", peakToday.get());
        out.put("peakAt", peakAt);
        return out;
    }

    static String referrerHost(String ref) {
        if (ref == null || ref.isBlank()) return "Direct";
        try {
            String host = java.net.URI.create(ref).getHost();
            return host == null ? "Direct" : host.replaceFirst("^www\\.", "");
        } catch (IllegalArgumentException e) {
            return "Other";
        }
    }

    private static List<Map<String, Object>> top(List<LiveSession> list,
                                                 java.util.function.Function<LiveSession, String> key, int limit) {
        return list.stream()
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
