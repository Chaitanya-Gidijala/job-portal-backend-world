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

    static final Map<String, String> DEFAULTS = Map.of(
            NOTIFY_MODE, "EVERY_VISIT",
            NOTIFY_COOLDOWN_HOURS, "6",
            NOTIFY_DAILY_CAP, "50",
            NOTIFY_RECIPIENT, "",
            NOTIFY_IGNORE, "",
            NOTIFY_SKIP_BOTS, "true");

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
        return out;
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
                case NOTIFY_SKIP_BOTS -> value = String.valueOf(Boolean.parseBoolean(value));
                case NOTIFY_RECIPIENT -> {
                    if (!value.isEmpty() && !value.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) throw new IllegalArgumentException("Invalid email");
                }
                case NOTIFY_IGNORE -> {
                    if (value.length() > 1900) throw new IllegalArgumentException("Ignore list too long");
                }
                default -> { }
            }
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
