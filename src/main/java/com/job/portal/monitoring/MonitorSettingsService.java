package com.job.portal.monitoring;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Admin-editable settings (stored in monitor_settings, cached in memory).
 * Unknown keys are rejected; values are validated per key.
 */
@Slf4j
@Service
public class MonitorSettingsService {

    public static final String NOTIFY_MODE = "notify.mode";              // OFF | NEW_VISITORS | EVERY_VISIT
    public static final String NOTIFY_COOLDOWN_HOURS = "notify.cooldownHours";
    public static final String NOTIFY_DAILY_CAP = "notify.dailyCap";
    public static final String NOTIFY_RECIPIENT = "notify.recipient";
    public static final String NOTIFY_IGNORE = "notify.ignoreVisitors"; // comma-separated visitor ids
    public static final String NOTIFY_SKIP_BOTS = "notify.skipBots";
    public static final String NOTIFY_ON_IDENTIFY = "notify.onIdentify";   // email when someone signs in / subscribes / writes
    public static final String DIGEST_DAILY = "digest.daily";              // daily puzzle email to subscribers
    public static final String DIGEST_WEEKLY = "digest.weekly";            // Monday summary email
    public static final String DIGEST_CAP = "digest.cap";                  // max subscriber emails per run
    public static final String PUSH_DAILY = "push.daily";                  // daily "puzzle is live" push
    public static final String INDEXNOW_KEY = "seo.indexnowKey";
    /** Internal (never exposed or editable through the API). */
    public static final String PUSH_VAPID_PUBLIC = "push.vapidPublic";
    public static final String PUSH_VAPID_PRIVATE = "push.vapidPrivate";

    static final Set<String> INTERNAL = Set.of(PUSH_VAPID_PUBLIC, PUSH_VAPID_PRIVATE);
    static final Set<String> BOOLEANS = Set.of(NOTIFY_SKIP_BOTS, NOTIFY_ON_IDENTIFY, DIGEST_DAILY, DIGEST_WEEKLY, PUSH_DAILY);

    static final Map<String, String> DEFAULTS = Map.ofEntries(
            Map.entry(NOTIFY_MODE, "EVERY_VISIT"),
            Map.entry(NOTIFY_COOLDOWN_HOURS, "6"),
            Map.entry(NOTIFY_DAILY_CAP, "50"),
            Map.entry(NOTIFY_RECIPIENT, ""),
            Map.entry(NOTIFY_IGNORE, ""),
            Map.entry(NOTIFY_SKIP_BOTS, "true"),
            Map.entry(NOTIFY_ON_IDENTIFY, "true"),
            Map.entry(DIGEST_DAILY, "true"),
            Map.entry(DIGEST_WEEKLY, "true"),
            Map.entry(DIGEST_CAP, "200"),
            Map.entry(PUSH_DAILY, "true"),
            // Must match the key file served at the site root (frontend public/<key>.txt).
            Map.entry(INDEXNOW_KEY, "c7f3a91e5b2d48e0a6f1c3d9b8e27a54"));

    private final MonitorSettingRepository repository;
    private final Map<String, String> cache = new ConcurrentHashMap<>();
    private volatile boolean loaded;

    public MonitorSettingsService(MonitorSettingRepository repository) {
        this.repository = repository;
    }

    private void load() {
        if (loaded) return;
        try {
            repository.findAll().forEach(s -> {
                if (s.getValue() != null) cache.put(s.getKey(), s.getValue());
            });
            loaded = true;
        } catch (RuntimeException e) {
            log.warn("Could not load monitor settings, using defaults: {}", e.getMessage());
        }
    }

    public String get(String key) {
        load();
        return cache.getOrDefault(key, DEFAULTS.get(key));
    }

    public int getInt(String key) {
        try {
            return Integer.parseInt(get(key).trim());
        } catch (RuntimeException e) {
            return Integer.parseInt(DEFAULTS.get(key));
        }
    }

    public boolean getBool(String key) {
        return Boolean.parseBoolean(get(key));
    }

    public Set<String> getSet(String key) {
        Set<String> out = new LinkedHashSet<>();
        for (String s : get(key).split(",")) {
            if (!s.isBlank()) out.add(s.trim());
        }
        return out;
    }

    public Map<String, String> all() {
        load();
        Map<String, String> out = new TreeMap<>(DEFAULTS);
        out.putAll(cache);
        INTERNAL.forEach(out::remove);
        return out;
    }

    /** Internal values (e.g. generated VAPID keys) — not part of the editable settings. */
    public String internal(String key) {
        load();
        return cache.get(key);
    }

    public void setInternal(String key, String value) {
        if (!INTERNAL.contains(key)) throw new IllegalArgumentException("Not an internal key: " + key);
        repository.save(new MonitorSetting(key, value));
        cache.put(key, value);
    }

    /** Validate and persist a batch of settings; returns the full settings map. */
    public Map<String, String> update(Map<String, String> changes) {
        load();
        for (var e : changes.entrySet()) {
            String key = e.getKey();
            if (!DEFAULTS.containsKey(key)) throw new IllegalArgumentException("Unknown setting: " + key);
            String value = e.getValue() == null ? "" : e.getValue().trim();
            switch (key) {
                case NOTIFY_MODE -> {
                    if (!Set.of("OFF", "NEW_VISITORS", "EVERY_VISIT").contains(value)) throw new IllegalArgumentException("Invalid mode");
                }
                case NOTIFY_COOLDOWN_HOURS -> requireInt(value, 0, 720);
                case NOTIFY_DAILY_CAP -> requireInt(value, 0, 1000);
                case DIGEST_CAP -> requireInt(value, 0, 5000);
                case INDEXNOW_KEY -> {
                    if (!value.matches("^[a-zA-Z0-9-]{8,128}$")) throw new IllegalArgumentException("Invalid IndexNow key");
                }
                case NOTIFY_RECIPIENT -> {
                    if (!value.isEmpty() && !value.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) throw new IllegalArgumentException("Invalid email");
                }
                case NOTIFY_IGNORE -> {
                    if (value.length() > 1900) throw new IllegalArgumentException("Ignore list too long");
                }
                default -> { }
            }
            if (BOOLEANS.contains(key)) value = String.valueOf(Boolean.parseBoolean(value));
            repository.save(new MonitorSetting(key, value));
            cache.put(key, value);
        }
        return all();
    }

    /** Add a visitor id to the notification ignore list (e.g. the admin's own browser). */
    public Map<String, String> ignoreVisitor(String visitorId) {
        Set<String> ids = getSet(NOTIFY_IGNORE);
        ids.add(visitorId);
        return update(Map.of(NOTIFY_IGNORE, String.join(",", ids)));
    }

    private static void requireInt(String v, int min, int max) {
        try {
            int n = Integer.parseInt(v);
            if (n < min || n > max) throw new IllegalArgumentException("Value out of range " + min + "–" + max);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Not a number: " + v);
        }
    }
}
