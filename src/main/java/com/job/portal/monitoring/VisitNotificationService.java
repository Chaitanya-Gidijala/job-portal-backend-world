package com.job.portal.monitoring;

import com.job.portal.service.EmailAsyncService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.util.HtmlUtils;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Emails the admin when someone visits the site (via the existing Brevo setup).
 *
 * Rules (admin-editable, see MonitorSettingsService):
 *   mode       OFF | NEW_VISITORS (first visit ever) | EVERY_VISIT (each new session)
 *   cooldown   per-visitor quiet period between emails (EVERY_VISIT)
 *   dailyCap   hard limit per day — protects the Brevo free tier (300/day)
 *   ignore     visitor ids never notified (e.g. the admin's own browsers); bots skipped
 *
 * Each email is sent ~25s after the visit starts so it includes location and
 * the first pages viewed.
 */
@Slf4j
@Service
public class VisitNotificationService {

    static final int HISTORY = 60;

    public record Outcome(long at, String visitorId, String place, String status, String reason) {
    }

    private record PendingVisit(String visitorId, String sessionId, long dueAt) {
    }

    private final MonitorSettingsService settings;
    private final VisitorProfileRepository profiles;
    private final PresenceService presence;
    private final EmailAsyncService email;
    private final String siteUrl;
    private final long delayMs;
    private final Map<String, PendingVisit> pending = new ConcurrentHashMap<>();
    private final Deque<Outcome> history = new ArrayDeque<>();
    private final AtomicInteger sentToday = new AtomicInteger();
    private volatile LocalDate day = LocalDate.now();

    public VisitNotificationService(MonitorSettingsService settings, VisitorProfileRepository profiles, PresenceService presence,
                                    EmailAsyncService email, @Value("${app.site-url:https://www.chaitanyatechworld.com}") String siteUrl,
                                    @Value("${monitor.notify.delay-ms:25000}") long delayMs) {
        this.delayMs = delayMs;
        this.settings = settings;
        this.profiles = profiles;
        this.presence = presence;
        this.email = email;
        this.siteUrl = siteUrl.replaceAll("/+$", "");
    }

    @EventListener
    public void onVisit(VisitEvent e) {
        if (!e.newSession() || e.session().getVisitorId() == null) return;
        if ("OFF".equals(settings.get(MonitorSettingsService.NOTIFY_MODE))) return;
        pending.putIfAbsent(e.session().getVisitorId(),
                new PendingVisit(e.session().getVisitorId(), e.session().getSessionId(), System.currentTimeMillis() + delayMs));
    }

    private final Map<String, Long> identifyAlerts = new ConcurrentHashMap<>();

    /** Email the admin when a visitor identifies themselves (sign-in, newsletter, contact form). */
    @EventListener
    public void onIdentified(VisitorIdentifiedEvent e) {
        if (!settings.getBool(MonitorSettingsService.NOTIFY_ON_IDENTIFY) || e.email() == null) return;
        String key = e.email().toLowerCase() + "|" + e.source();
        long now = System.currentTimeMillis();
        Long last = identifyAlerts.put(key, now);
        if (last != null && now - last < Duration.ofHours(24).toMillis()) return; // once a day per person & channel
        identifyAlerts.values().removeIf(t -> now - t > Duration.ofDays(2).toMillis());
        String what = switch (e.source()) {
            case "google" -> "signed in with Google";
            case "github" -> "signed in with GitHub";
            case "account" -> "signed in";
            case "newsletter" -> "subscribed to the newsletter";
            case "contact" -> "sent the contact form";
            default -> "identified themselves";
        };
        String who = e.name() == null || e.name().isBlank() ? e.email() : e.name() + " (" + e.email() + ")";
        String link = e.visitorId() == null ? siteUrl + "/admin/growth" : siteUrl + "/admin/visitors?visitor=" + e.visitorId();
        String html = "<div style=\"font-family:system-ui,sans-serif\"><h2 style=\"margin:0 0 8px\">" + HtmlUtils.htmlEscape(who) + "</h2>"
                + "<p style=\"color:#374151\">" + HtmlUtils.htmlEscape(who) + " just " + what + ".</p>"
                + "<p><a href=\"" + HtmlUtils.htmlEscape(link) + "\" style=\"background:#6d4cff;color:#fff;padding:10px 16px;border-radius:8px;text-decoration:none\">View in dashboard</a></p></div>";
        java.util.concurrent.CompletableFuture.runAsync(() -> {
            boolean ok = email.sendNow(recipient(), "👋 " + who + " " + what, html);
            record(e.visitorId() == null ? "—" : e.visitorId(), who, ok ? "SENT" : "FAILED", ok ? "Identity alert: " + what : "Brevo rejected the identity alert");
        });
    }

    @Scheduled(fixedDelay = 10_000)
    public void processDue() {
        long now = System.currentTimeMillis();
        for (PendingVisit p : List.copyOf(pending.values())) {
            if (p.dueAt() > now) continue;
            pending.remove(p.visitorId());
            try {
                handle(p);
            } catch (RuntimeException ex) {
                record(p.visitorId(), null, "FAILED", MonitorLogWriter.rootMessage(ex));
                log.warn("Visit notification failed for {}: {}", p.visitorId(), ex.getMessage());
            }
        }
    }

    private void handle(PendingVisit p) {
        rollDay();
        String mode = settings.get(MonitorSettingsService.NOTIFY_MODE);
        PresenceService.LiveSession live = presence.get(p.sessionId());
        VisitorProfile profile = profiles.findByVisitorId(p.visitorId()).orElse(null);
        String place = place(live, profile);

        if ("OFF".equals(mode)) return;
        if (settings.getSet(MonitorSettingsService.NOTIFY_IGNORE).contains(p.visitorId())) {
            record(p.visitorId(), place, "SKIPPED", "Visitor is on the ignore list");
            return;
        }
        boolean bot = (live != null && "Bot".equals(live.getDevice())) || (profile != null && Boolean.TRUE.equals(profile.getBot()));
        if (bot && settings.getBool(MonitorSettingsService.NOTIFY_SKIP_BOTS)) {
            record(p.visitorId(), place, "SKIPPED", "Bot / automated browser");
            return;
        }
        int visits = profile == null || profile.getVisits() == null ? 1 : profile.getVisits();
        if ("NEW_VISITORS".equals(mode) && (visits > 1 || (profile != null && profile.getLastNotifiedAt() != null))) {
            record(p.visitorId(), place, "SKIPPED", "Returning visitor (mode: new visitors only)");
            return;
        }
        int cooldown = settings.getInt(MonitorSettingsService.NOTIFY_COOLDOWN_HOURS);
        if (profile != null && profile.getLastNotifiedAt() != null
                && profile.getLastNotifiedAt().isAfter(Instant.now().minus(Duration.ofHours(cooldown)))) {
            record(p.visitorId(), place, "SKIPPED", "Notified in the last " + cooldown + "h (cooldown)");
            return;
        }
        int cap = settings.getInt(MonitorSettingsService.NOTIFY_DAILY_CAP);
        if (sentToday.get() >= cap) {
            record(p.visitorId(), place, "SKIPPED", "Daily cap of " + cap + " emails reached");
            return;
        }

        String to = recipient();
        String subject = (visits <= 1 ? "New visitor" : "Returning visitor (visit #" + visits + ")") + " from " + place;
        boolean ok = email.sendNow(to, subject, html(p.visitorId(), live, profile, visits, place));
        if (ok) {
            sentToday.incrementAndGet();
            profiles.markNotified(p.visitorId(), Instant.now());
            record(p.visitorId(), place, "SENT", "to " + to);
        } else {
            record(p.visitorId(), place, "FAILED", "Brevo rejected the email — check BREVO_API_KEY and the sender address");
        }
    }

    /** Send a sample notification to verify the mail setup. */
    public Outcome sendTest() {
        String to = recipient();
        boolean ok = email.sendNow(to, "Test: visit notifications are working",
                "<p style=\"font-family:system-ui,sans-serif\">This is a test from your CTW Control dashboard. "
                        + "Visit notifications will arrive at this address.</p>");
        return record("test", "—", ok ? "SENT" : "FAILED", ok ? "Test email to " + to : "Brevo rejected the test email to " + to);
    }

    public String recipient() {
        String configured = settings.get(MonitorSettingsService.NOTIFY_RECIPIENT);
        return configured == null || configured.isBlank() ? email.getAdminEmail() : configured;
    }

    private Outcome record(String visitorId, String place, String status, String reason) {
        Outcome o = new Outcome(System.currentTimeMillis(), visitorId, place, status, reason);
        synchronized (history) {
            history.addFirst(o);
            while (history.size() > HISTORY) history.removeLast();
        }
        return o;
    }

    private void rollDay() {
        LocalDate today = LocalDate.now();
        if (!today.equals(day)) {
            day = today;
            sentToday.set(0);
        }
    }

    public Map<String, Object> status() {
        rollDay();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("recipient", recipient());
        m.put("sentToday", sentToday.get());
        m.put("pending", pending.size());
        synchronized (history) {
            m.put("recent", List.copyOf(history));
        }
        return m;
    }

    /* ── Email content ─────────────────────────────────────────────────── */

    static String place(PresenceService.LiveSession s, VisitorProfile v) {
        String city = s != null && s.getCity() != null ? s.getCity() : v == null ? null : v.getCity();
        String country = s != null && s.getCountry() != null ? s.getCountry() : v == null ? null : v.getCountry();
        List<String> parts = new ArrayList<>();
        if (city != null && !city.isBlank()) parts.add(city);
        if (country != null && !country.isBlank() && !country.equals(city)) parts.add(country);
        return parts.isEmpty() ? "an unknown location" : String.join(", ", parts);
    }

    private String html(String visitorId, PresenceService.LiveSession s, VisitorProfile v, int visits, String place) {
        Map<String, String> d = s != null ? s.getDetails() : Map.of();
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm").withZone(ZoneId.of("Asia/Kolkata"));
        List<String[]> rows = new ArrayList<>();
        rows.add(new String[]{"Visitor", "Visitor " + visitorId.substring(0, Math.min(4, visitorId.length())).toUpperCase() + (visits <= 1 ? " (first visit)" : " (visit #" + visits + ")")});
        if (v != null && v.getFirstSeen() != null && visits > 1) rows.add(new String[]{"First seen", fmt.format(v.getFirstSeen()) + " IST"});
        if (v != null && v.getSource() != null) rows.add(new String[]{"First came from", v.getSource() + (v.getLandingPage() != null ? " → " + v.getLandingPage() : "")});
        rows.add(new String[]{"Approx. location", place + (s != null && s.getIsp() != null ? " · " + s.getIsp() : "")});
        if (s != null) {
            rows.add(new String[]{"Now viewing", s.getPath() + (s.getTitle() != null ? " — " + s.getTitle() : "")});
            if (s.getTrail() != null && s.getTrail().size() > 1) rows.add(new String[]{"Pages so far", String.join(" → ", s.getTrail())});
            rows.add(new String[]{"Came from", s.getReferrer() == null || s.getReferrer().isBlank() ? "Direct" : s.getReferrer()});
            rows.add(new String[]{"Device", s.getDevice() + " · " + d.getOrDefault("osVersion", s.getOs()) + " · " + d.getOrDefault("browserVersion", s.getBrowser())});
            rows.add(new String[]{"Screen", Objects.toString(s.getScreen(), "—") + (d.containsKey("viewport") ? " (window " + d.get("viewport") + ")" : "")});
            rows.add(new String[]{"Language · timezone", Objects.toString(s.getLanguage(), "—") + " · " + Objects.toString(s.getTimezone(), "—")});
            if (d.containsKey("utmSource")) rows.add(new String[]{"Campaign", d.get("utmSource") + " / " + d.getOrDefault("utmMedium", "—") + " / " + d.getOrDefault("utmCampaign", "—")});
            else if (v != null && v.getUtmCampaign() != null) rows.add(new String[]{"Campaign", v.getSource() + " / " + Objects.toString(v.getUtmMedium(), "—") + " / " + v.getUtmCampaign()});
            rows.add(new String[]{"IP address", s.getIp()});
        }
        StringBuilder table = new StringBuilder();
        for (String[] r : rows) {
            table.append("<tr><td style=\"padding:6px 12px;color:#6b7280;white-space:nowrap;vertical-align:top\">")
                    .append(HtmlUtils.htmlEscape(r[0])).append("</td><td style=\"padding:6px 12px;color:#111827\">")
                    .append(HtmlUtils.htmlEscape(Objects.toString(r[1], "—"))).append("</td></tr>");
        }
        String link = siteUrl + "/admin/visitors?visitor=" + visitorId;
        return "<div style=\"font-family:system-ui,-apple-system,Segoe UI,sans-serif;max-width:560px\">"
                + "<h2 style=\"margin:0 0 4px;font-size:18px;color:#111827\">Someone is on your site</h2>"
                + "<p style=\"margin:0 0 16px;color:#6b7280\">" + HtmlUtils.htmlEscape(place) + "</p>"
                + "<table style=\"border-collapse:collapse;font-size:14px;border:1px solid #e5e7eb;border-radius:8px\">" + table + "</table>"
                + "<p style=\"margin:20px 0\"><a href=\"" + HtmlUtils.htmlEscape(link) + "\" style=\"background:#6d4cff;color:#fff;padding:10px 16px;border-radius:8px;text-decoration:none\">Open in dashboard</a></p>"
                + "<p style=\"color:#9ca3af;font-size:12px\">Location is approximate (IP-based). Change these alerts in CTW Control → Notifications.</p>"
                + "</div>";
    }
}
