package com.job.portal.growth;

import com.job.portal.dto.DailyChallengeDtos.Stats;
import com.job.portal.entity.DailyChallengeResult;
import com.job.portal.entity.NewsletterSubscriber;
import com.job.portal.monitoring.MonitorSettingsService;
import com.job.portal.monitoring.VisitorIdentifiedEvent;
import com.job.portal.monitoring.VisitorProfileRepository;
import com.job.portal.repository.DailyChallengeResultRepository;
import com.job.portal.repository.NewsletterSubscriberRepository;
import com.job.portal.service.DailyChallengeService;
import com.job.portal.service.EmailAsyncService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.util.HtmlUtils;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Email list: subscribe / preferences / one-click unsubscribe, plus the scheduled
 * daily-puzzle email (08:00 IST) and Monday summary (09:00 IST), and the daily
 * push reminder (08:05 IST). Sent through the existing Brevo integration.
 */
@Slf4j
@Service
public class NewsletterService {

    static final ZoneId IST = ZoneId.of("Asia/Kolkata");
    private static final String TOKEN_ALPHABET = "abcdefghijkmnopqrstuvwxyz23456789";
    private static final SecureRandom RANDOM = new SecureRandom();

    public record RunResult(String kind, long at, int sent, int failed, int skipped, String note) {
    }

    private final NewsletterSubscriberRepository subscribers;
    private final DailyChallengeResultRepository results;
    private final VisitorProfileRepository profiles;
    private final DailyChallengeService daily;
    private final LeaderboardService leaderboard;
    private final EmailAsyncService email;
    private final MonitorSettingsService settings;
    private final WebPushService push;
    private final ApplicationEventPublisher events;
    private final String siteUrl;
    private final Map<String, Deque<Long>> rate = new ConcurrentHashMap<>();
    private final Deque<RunResult> runs = new ArrayDeque<>();

    public NewsletterService(NewsletterSubscriberRepository subscribers, DailyChallengeResultRepository results,
                             VisitorProfileRepository profiles, DailyChallengeService daily, LeaderboardService leaderboard,
                             EmailAsyncService email, MonitorSettingsService settings, WebPushService push,
                             ApplicationEventPublisher events,
                             @Value("${app.site-url:https://www.chaitanyatechworld.com}") String siteUrl) {
        this.subscribers = subscribers;
        this.results = results;
        this.profiles = profiles;
        this.daily = daily;
        this.leaderboard = leaderboard;
        this.email = email;
        this.settings = settings;
        this.push = push;
        this.events = events;
        this.siteUrl = siteUrl.replaceAll("/+$", "");
    }

    /* ── Subscriptions ─────────────────────────────────────────────────── */

    /** Simple abuse guard: max 5 sign-ups per IP per hour. */
    public boolean allow(String ip) {
        long now = System.currentTimeMillis();
        Deque<Long> q = rate.computeIfAbsent(ip == null ? "?" : ip, k -> new ArrayDeque<>());
        synchronized (q) {
            while (!q.isEmpty() && now - q.peekFirst() > 3_600_000) q.pollFirst();
            if (q.size() >= 5) return false;
            q.addLast(now);
            return true;
        }
    }

    public NewsletterSubscriber subscribe(String rawEmail, String name, String visitorId, boolean wantsDaily, boolean wantsWeekly, String source) {
        String address = rawEmail == null ? "" : rawEmail.trim().toLowerCase(Locale.ROOT);
        if (!address.matches("^[^@\\s]{1,64}@[^@\\s]{1,190}\\.[^@\\s]{2,}$")) throw new IllegalArgumentException("Please enter a valid email address");
        if (!wantsDaily && !wantsWeekly) throw new IllegalArgumentException("Choose at least one email");
        NewsletterSubscriber s = subscribers.findFirstByEmailIgnoreCase(address).orElseGet(NewsletterSubscriber::new);
        boolean isNew = s.getId() == null || Boolean.FALSE.equals(s.getActive());
        s.setEmail(address);
        if (name != null && !name.isBlank()) s.setName(trim(name, 120));
        if (visitorId != null && !visitorId.isBlank()) s.setVisitorId(trim(visitorId, 64));
        if (s.getSource() == null) s.setSource(trim(source == null ? "site" : source, 60));
        s.setWantsDaily(wantsDaily);
        s.setWantsWeekly(wantsWeekly);
        s.setActive(true);
        s.setUnsubscribedAt(null);
        if (s.getToken() == null) s.setToken(token());
        NewsletterSubscriber saved = subscribers.save(s);
        events.publishEvent(new VisitorIdentifiedEvent(saved.getVisitorId(), saved.getName(), saved.getEmail(), null, "newsletter"));
        if (isNew) {
            sendSafely(saved.getEmail(), "You're in — Traceflow Daily", welcomeHtml(saved));
        }
        return saved;
    }

    public Optional<NewsletterSubscriber> byToken(String token) {
        return token == null || token.isBlank() ? Optional.empty() : subscribers.findByToken(token);
    }

    public NewsletterSubscriber updatePreferences(String token, boolean wantsDaily, boolean wantsWeekly) {
        NewsletterSubscriber s = byToken(token).orElseThrow(() -> new IllegalArgumentException("Link expired or invalid"));
        s.setWantsDaily(wantsDaily);
        s.setWantsWeekly(wantsWeekly);
        s.setActive(wantsDaily || wantsWeekly);
        s.setUnsubscribedAt(wantsDaily || wantsWeekly ? null : Instant.now());
        return subscribers.save(s);
    }

    public NewsletterSubscriber unsubscribe(String token) {
        return updatePreferences(token, false, false);
    }

    private static String token() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 32; i++) sb.append(TOKEN_ALPHABET.charAt(RANDOM.nextInt(TOKEN_ALPHABET.length())));
        return sb.toString();
    }

    private static String trim(String s, int max) {
        String t = s.trim();
        return t.length() > max ? t.substring(0, max) : t;
    }

    /* ── Scheduled sends ───────────────────────────────────────────────── */

    @Scheduled(cron = "0 0 8 * * *", zone = "Asia/Kolkata")
    public void dailyJob() {
        if (settings.getBool(MonitorSettingsService.DIGEST_DAILY)) sendDaily(false);
    }

    @Scheduled(cron = "0 5 8 * * *", zone = "Asia/Kolkata")
    public void dailyPushJob() {
        if (!settings.getBool(MonitorSettingsService.PUSH_DAILY)) return;
        WebPushService.Result r = push.broadcast("Traceflow Daily #" + daily.currentDay() + " is live",
                "Three quick algorithm rounds. Keep your streak going!", siteUrl + "/daily?utm_source=push&utm_medium=notification");
        record(new RunResult("push", System.currentTimeMillis(), r.sent(), r.failed(), r.removed(), "Daily reminder"));
    }

    @Scheduled(cron = "0 0 9 * * MON", zone = "Asia/Kolkata")
    public void weeklyJob() {
        if (settings.getBool(MonitorSettingsService.DIGEST_WEEKLY)) sendWeekly(false);
    }

    /** Daily puzzle email to every daily subscriber (once per day each). */
    public RunResult sendDaily(boolean force) {
        int cap = settings.getInt(MonitorSettingsService.DIGEST_CAP);
        Instant todayStart = LocalDate.now(IST).atStartOfDay(IST).toInstant();
        int day = daily.currentDay();
        Stats yesterday = day > 1 ? daily.stats(day - 1) : null;
        int sent = 0, failed = 0, skipped = 0;
        for (NewsletterSubscriber s : subscribers.findDailyRecipients()) {
            if (!force && s.getLastSentAt() != null && s.getLastSentAt().isAfter(todayStart)) {
                skipped++;
                continue;
            }
            if (sent >= cap) {
                skipped++;
                continue;
            }
            if (email.sendNow(s.getEmail(), "Traceflow Daily #" + day + " is live 🧩", dailyHtml(s, day, yesterday))) {
                sent++;
                s.setLastSentAt(Instant.now());
                subscribers.save(s);
            } else {
                failed++;
            }
        }
        return record(new RunResult("daily", System.currentTimeMillis(), sent, failed, skipped, "Puzzle #" + day));
    }

    /** Monday summary: personal stats when the subscriber is linked to a visitor. */
    public RunResult sendWeekly(boolean force) {
        int cap = settings.getInt(MonitorSettingsService.DIGEST_CAP);
        int day = daily.currentDay();
        int sent = 0, failed = 0, skipped = 0;
        for (NewsletterSubscriber s : subscribers.findWeeklyRecipients()) {
            if (sent >= cap) {
                skipped++;
                continue;
            }
            if (email.sendNow(s.getEmail(), "Your week on Chaitanya Tech World", weeklyHtml(s, day))) {
                sent++;
            } else {
                failed++;
            }
        }
        return record(new RunResult("weekly", System.currentTimeMillis(), sent, failed, skipped, "Week ending #" + day));
    }

    /** Send one sample to the admin address. */
    public boolean sendTest(String kind) {
        NewsletterSubscriber sample = new NewsletterSubscriber();
        sample.setEmail(email.getAdminEmail());
        sample.setName("Admin");
        sample.setToken("preview");
        int day = daily.currentDay();
        String html = "weekly".equals(kind) ? weeklyHtml(sample, day) : dailyHtml(sample, day, day > 1 ? daily.stats(day - 1) : null);
        return email.sendNow(email.getAdminEmail(), "[Test] " + ("weekly".equals(kind) ? "Weekly summary" : "Daily puzzle email"), html);
    }

    public String preview(String kind) {
        NewsletterSubscriber sample = new NewsletterSubscriber();
        sample.setName("Asha");
        sample.setToken("preview");
        int day = daily.currentDay();
        return "weekly".equals(kind) ? weeklyHtml(sample, day) : dailyHtml(sample, day, day > 1 ? daily.stats(day - 1) : null);
    }

    private RunResult record(RunResult r) {
        synchronized (runs) {
            runs.addFirst(r);
            while (runs.size() > 20) runs.removeLast();
        }
        log.info("Email/push run {}: sent={} failed={} skipped={}", r.kind(), r.sent(), r.failed(), r.skipped());
        return r;
    }

    public List<RunResult> recentRuns() {
        synchronized (runs) {
            return List.copyOf(runs);
        }
    }

    private void sendSafely(String to, String subject, String html) {
        java.util.concurrent.CompletableFuture.runAsync(() -> {
            try {
                email.sendNow(to, subject, html);
            } catch (RuntimeException e) {
                log.warn("Welcome email failed: {}", e.getMessage());
            }
        });
    }

    /* ── Templates ─────────────────────────────────────────────────────── */

    private String link(String path, String campaign) {
        return siteUrl + path + (path.contains("?") ? "&" : "?") + "utm_source=email&utm_medium=newsletter&utm_campaign=" + campaign;
    }

    private String layout(String preheader, String body, NewsletterSubscriber s) {
        String manage = siteUrl + "/newsletter?token=" + s.getToken();
        return "<div style=\"background:#f5f6fb;padding:24px 12px;font-family:system-ui,-apple-system,Segoe UI,sans-serif\">"
                + "<span style=\"display:none\">" + HtmlUtils.htmlEscape(preheader) + "</span>"
                + "<div style=\"max-width:560px;margin:0 auto;background:#fff;border-radius:14px;padding:28px;border:1px solid #e6e8f0\">"
                + "<p style=\"margin:0 0 18px;font-weight:700;color:#5b3df5\">Chaitanya Tech World</p>"
                + body
                + "</div>"
                + "<p style=\"max-width:560px;margin:16px auto 0;color:#8a8fa3;font-size:12px;text-align:center\">"
                + "You subscribed at chaitanyatechworld.com. <a href=\"" + HtmlUtils.htmlEscape(manage) + "\" style=\"color:#8a8fa3\">Email preferences</a> · "
                + "<a href=\"" + HtmlUtils.htmlEscape(manage + "&action=unsubscribe") + "\" style=\"color:#8a8fa3\">Unsubscribe</a></p></div>";
    }

    private static String button(String href, String label) {
        return "<p style=\"margin:22px 0\"><a href=\"" + HtmlUtils.htmlEscape(href) + "\" style=\"background:#6d4cff;color:#fff;padding:12px 20px;border-radius:10px;text-decoration:none;font-weight:600\">"
                + HtmlUtils.htmlEscape(label) + "</a></p>";
    }

    private static String hello(NewsletterSubscriber s) {
        return s.getName() == null || s.getName().isBlank() ? "Hi there," : "Hi " + HtmlUtils.htmlEscape(s.getName().split("\\s+")[0]) + ",";
    }

    private String welcomeHtml(NewsletterSubscriber s) {
        String body = "<h1 style=\"margin:0 0 8px;font-size:22px;color:#111827\">You're subscribed 🎉</h1>"
                + "<p style=\"color:#374151;line-height:1.6\">" + hello(s) + " thanks for joining. "
                + (Boolean.TRUE.equals(s.getWantsDaily()) ? "Each morning you'll get the new Traceflow Daily puzzle. " : "")
                + (Boolean.TRUE.equals(s.getWantsWeekly()) ? "Every Monday you'll get a short summary of your week. " : "")
                + "</p>" + button(link("/daily", "welcome"), "Play today's puzzle");
        return layout("Welcome to Traceflow Daily", body, s);
    }

    private String dailyHtml(NewsletterSubscriber s, int day, Stats yesterday) {
        StringBuilder top = new StringBuilder();
        List<LeaderboardService.Entry> leaders = leaderboard.topToday(3);
        String ystats = yesterday == null || yesterday.completions() == 0 ? ""
                : "<p style=\"color:#6b7280;font-size:14px\">Yesterday " + yesterday.completions() + " people played · average score "
                + yesterday.averageScore() + "/3.</p>";
        if (!leaders.isEmpty()) {
            top.append("<p style=\"margin:18px 0 6px;font-weight:600;color:#111827\">Early leaders today</p><ol style=\"margin:0;padding-left:20px;color:#374151\">");
            leaders.forEach(e -> top.append("<li>").append(HtmlUtils.htmlEscape(e.name())).append(" — ").append(e.score()).append("/3</li>"));
            top.append("</ol>");
        }
        String body = "<h1 style=\"margin:0 0 8px;font-size:22px;color:#111827\">Traceflow Daily #" + day + " is live</h1>"
                + "<p style=\"color:#374151;line-height:1.6\">" + hello(s) + " three quick rounds: predict a swap, fast-forward a sort, count binary-search steps. About two minutes.</p>"
                + ystats + button(link("/daily", "daily-" + day), "Play puzzle #" + day) + top
                + "<p style=\"color:#6b7280;font-size:13px;margin-top:20px\">Missed one? <a href=\"" + HtmlUtils.htmlEscape(link("/daily/archive", "daily-" + day)) + "\">Practise past puzzles</a> · <a href=\"" + HtmlUtils.htmlEscape(link("/leaderboard", "daily-" + day)) + "\">Leaderboard</a></p>";
        return layout("Puzzle #" + day + " is ready", body, s);
    }

    private String weeklyHtml(NewsletterSubscriber s, int day) {
        String personal = "";
        if (s.getVisitorId() != null) {
            List<DailyChallengeResult> mine = results.findByLearnerIdAndChallengeDayBetween(s.getVisitorId(), Math.max(1, day - 6), day);
            var profile = profiles.findByVisitorId(s.getVisitorId()).orElse(null);
            int score = mine.stream().mapToInt(DailyChallengeResult::getScore).sum();
            personal = "<table style=\"width:100%;border-collapse:collapse;margin:14px 0\"><tr>"
                    + cell(String.valueOf(mine.size()), "puzzles played")
                    + cell(mine.isEmpty() ? "—" : score + "/" + (mine.size() * 3), "correct rounds")
                    + cell(profile == null || profile.getPageViews() == null ? "—" : String.valueOf(profile.getPageViews()), "pages explored")
                    + "</tr></table>";
        }
        Map<String, Object> week = leaderboard.week(null, 3);
        StringBuilder top = new StringBuilder();
        @SuppressWarnings("unchecked")
        List<LeaderboardService.Entry> leaders = (List<LeaderboardService.Entry>) week.get("entries");
        if (!leaders.isEmpty()) {
            top.append("<p style=\"margin:18px 0 6px;font-weight:600;color:#111827\">This week's top players</p><ol style=\"margin:0;padding-left:20px;color:#374151\">");
            leaders.forEach(e -> top.append("<li>").append(HtmlUtils.htmlEscape(e.name())).append(" — ").append(e.score()).append(" points</li>"));
            top.append("</ol>");
        }
        String body = "<h1 style=\"margin:0 0 8px;font-size:22px;color:#111827\">Your week</h1>"
                + "<p style=\"color:#374151;line-height:1.6\">" + hello(s) + " here's your learning recap.</p>"
                + personal + top
                + button(link("/progress", "weekly"), "See your progress")
                + "<p style=\"color:#6b7280;font-size:13px\">Keep going: <a href=\"" + HtmlUtils.htmlEscape(link("/roadmap", "weekly")) + "\">roadmaps</a> · <a href=\"" + HtmlUtils.htmlEscape(link("/traceflow/catalogue", "weekly")) + "\">algorithm visualiser</a> · <a href=\"" + HtmlUtils.htmlEscape(link("/traceflow/interview-questions", "weekly")) + "\">interview questions</a></p>";
        return layout("Your weekly learning recap", body, s);
    }

    private static String cell(String value, String label) {
        return "<td style=\"padding:12px;border:1px solid #e6e8f0;text-align:center\"><div style=\"font-size:22px;font-weight:700;color:#111827\">"
                + HtmlUtils.htmlEscape(value) + "</div><div style=\"font-size:12px;color:#6b7280\">" + HtmlUtils.htmlEscape(label) + "</div></td>";
    }

    /* ── Stats ─────────────────────────────────────────────────────────── */

    public Map<String, Object> stats() {
        List<NewsletterSubscriber> all = subscribers.findAll();
        long active = all.stream().filter(NewsletterSubscriber::isActiveSubscriber).count();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("total", all.size());
        m.put("active", active);
        m.put("daily", all.stream().filter(s -> s.isActiveSubscriber() && Boolean.TRUE.equals(s.getWantsDaily())).count());
        m.put("weekly", all.stream().filter(s -> s.isActiveSubscriber() && !Boolean.FALSE.equals(s.getWantsWeekly())).count());
        m.put("new7d", all.stream().filter(s -> s.getSubscribedAt() != null && s.getSubscribedAt().isAfter(java.time.LocalDateTime.now().minusDays(7))).count());
        m.put("unsubscribed", all.size() - active);
        m.put("recent", all.stream()
                .sorted(Comparator.comparing(NewsletterSubscriber::getSubscribedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(50)
                .map(s -> {
                    Map<String, Object> r = new LinkedHashMap<>();
                    r.put("email", s.getEmail());
                    r.put("name", s.getName());
                    r.put("source", s.getSource());
                    r.put("daily", Boolean.TRUE.equals(s.getWantsDaily()));
                    r.put("weekly", !Boolean.FALSE.equals(s.getWantsWeekly()));
                    r.put("active", s.isActiveSubscriber());
                    r.put("visitorId", s.getVisitorId());
                    r.put("subscribedAt", s.getSubscribedAt());
                    r.put("lastSentAt", s.getLastSentAt());
                    return r;
                })
                .toList());
        m.put("runs", recentRuns());
        return m;
    }
}
