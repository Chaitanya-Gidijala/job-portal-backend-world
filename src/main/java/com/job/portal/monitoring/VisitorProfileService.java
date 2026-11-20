package com.job.portal.monitoring;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Keeps a permanent profile per visitor (visitor_profiles).
 *
 * Heartbeats only update an in-memory "pending" entry; a single scheduled flush
 * upserts them in batches every few seconds, so requests never wait on the DB and
 * two tabs of the same visitor can never race on the insert.
 */
@Slf4j
@Service
public class VisitorProfileService {

    private static final class Pending {
        PresenceService.LiveSession latest;
        int newSessions;
        int newPages;
        String landing;
    }

    private final VisitorProfileRepository repository;
    private final ObjectMapper mapper;
    private final Map<String, Pending> pending = new ConcurrentHashMap<>();
    private volatile long lastFlushAt;
    private volatile String lastError;

    public VisitorProfileService(VisitorProfileRepository repository, ObjectMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    @EventListener
    public void onVisit(VisitEvent e) {
        PresenceService.LiveSession s = e.session();
        if (s.getVisitorId() == null) return;
        pending.compute(s.getVisitorId(), (id, p) -> {
            if (p == null) p = new Pending();
            p.latest = s;
            if (e.newSession()) {
                p.newSessions++;
                if (p.landing == null) p.landing = s.getDetails().getOrDefault("landing", s.getPath());
            }
            if (e.newPage()) p.newPages++;
            return p;
        });
    }

    @Scheduled(fixedDelay = 5_000)
    public void flush() {
        if (pending.isEmpty()) return;
        Map<String, Pending> batch = new HashMap<>();
        for (String id : List.copyOf(pending.keySet())) {
            Pending p = pending.remove(id);
            if (p != null) batch.put(id, p);
        }
        try {
            Map<String, VisitorProfile> existing = new HashMap<>();
            repository.findByVisitorIdIn(batch.keySet()).forEach(v -> existing.put(v.getVisitorId(), v));
            List<VisitorProfile> toSave = new ArrayList<>();
            batch.forEach((id, p) -> toSave.add(apply(existing.get(id), id, p)));
            repository.saveAll(toSave);
            lastFlushAt = System.currentTimeMillis();
            lastError = null;
        } catch (RuntimeException ex) {
            lastError = MonitorLogWriter.rootMessage(ex);
            log.warn("Could not save visitor profiles: {}", lastError);
            // Put the work back (merged with anything that arrived meanwhile) and retry next time.
            batch.forEach((id, p) -> pending.merge(id, p, (newer, older) -> {
                newer.newSessions += older.newSessions;
                newer.newPages += older.newPages;
                if (newer.landing == null) newer.landing = older.landing;
                return newer;
            }));
        }
    }

    private VisitorProfile apply(VisitorProfile v, String visitorId, Pending p) {
        PresenceService.LiveSession s = p.latest;
        Map<String, String> d = s.getDetails();
        Instant now = Instant.now();
        if (v == null) {
            v = new VisitorProfile();
            v.setVisitorId(visitorId);
            v.setFirstSeen(Instant.ofEpochMilli(s.getStartedAt() > 0 ? s.getStartedAt() : now.toEpochMilli()));
            v.setVisits(0);
            v.setPageViews(0);
        }
        v.setVisits(nz(v.getVisits()) + Math.max(p.newSessions, v.getVisits() == null || v.getVisits() == 0 ? 1 : 0));
        v.setPageViews(nz(v.getPageViews()) + p.newPages);
        v.setLastSeen(now);
        v.setLastPage(ClientInfo.truncate(s.getPath(), 300));

        // First-touch attribution: only set once.
        if (v.getLandingPage() == null) v.setLandingPage(ClientInfo.truncate(p.landing != null ? p.landing : s.getPath(), 300));
        if (v.getSource() == null) {
            String utm = d.get("utmSource");
            v.setSource(ClientInfo.truncate(utm != null ? utm : PresenceService.referrerHost(s.getReferrer()), 200));
            v.setFirstReferrer(ClientInfo.truncate(s.getReferrer(), 500));
            v.setUtmMedium(ClientInfo.truncate(d.get("utmMedium"), 120));
            v.setUtmCampaign(ClientInfo.truncate(d.get("utmCampaign"), 120));
        }

        // Latest known environment.
        if (s.getCountry() != null) {
            v.setCountry(ClientInfo.truncate(s.getCountry(), 80));
            v.setCountryCode(ClientInfo.truncate(s.getCountryCode(), 8));
            v.setRegion(ClientInfo.truncate(s.getRegion(), 120));
            v.setCity(ClientInfo.truncate(s.getCity(), 120));
            v.setIsp(ClientInfo.truncate(s.getIsp(), 160));
        }
        v.setIp(s.getIp());
        v.setTimezone(ClientInfo.truncate(s.getTimezone(), 60));
        v.setBrowser(ClientInfo.truncate(d.getOrDefault("browserVersion", s.getBrowser()), 60));
        v.setOs(ClientInfo.truncate(d.getOrDefault("osVersion", s.getOs()), 60));
        v.setDevice(s.getDevice());
        v.setLanguage(ClientInfo.truncate(s.getLanguage(), 20));
        v.setScreen(ClientInfo.truncate(s.getScreen(), 20));
        v.setBot("Bot".equals(s.getDevice()));
        if (s.getUsername() != null) v.setUsername(ClientInfo.truncate(s.getUsername(), 120));
        if (s.getEmail() != null) v.setEmail(ClientInfo.truncate(s.getEmail(), 160));

        Map<String, String> merged = new TreeMap<>(readDetails(v.getDetails()));
        merged.putAll(d);
        v.setDetails(writeDetails(merged));
        return v;
    }

    private static int nz(Integer i) {
        return i == null ? 0 : i;
    }

    public Map<String, String> readDetails(String json) {
        if (json == null || json.isBlank()) return new TreeMap<>();
        try {
            return mapper.readValue(json, new TypeReference<TreeMap<String, String>>() { });
        } catch (Exception e) {
            return new TreeMap<>();
        }
    }

    private String writeDetails(Map<String, String> details) {
        try {
            String json = mapper.writeValueAsString(details);
            return json.length() <= 4000 ? json : json.substring(0, 3999);
        } catch (Exception e) {
            return null;
        }
    }

    public Map<String, Object> status() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("pending", pending.size());
        m.put("lastFlushAt", lastFlushAt == 0 ? null : lastFlushAt);
        m.put("lastError", lastError);
        return m;
    }
}
