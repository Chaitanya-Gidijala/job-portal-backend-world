package com.job.portal.growth;

import com.job.portal.dto.ApiResponse;
import com.job.portal.monitoring.MonitorLogRepository;
import com.job.portal.monitoring.MonitorSettingsService;
import com.job.portal.monitoring.VisitorProfile;
import com.job.portal.monitoring.VisitorProfileRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Admin Growth page: audience, email list, push, shares, conversion, leaderboards and search engines. */
@Slf4j
@RestController
@RequestMapping("/api/admin/growth")
public class GrowthAdminController {

    private static final Pattern LOC = Pattern.compile("<loc>\\s*([^<\\s]+)\\s*</loc>");

    private final NewsletterService newsletter;
    private final WebPushService push;
    private final PushSubscriptionRepository pushSubscriptions;
    private final CertificateRepository certificates;
    private final UserProgressRepository progress;
    private final VisitorProfileRepository profiles;
    private final MonitorLogRepository logs;
    private final LeaderboardService leaderboards;
    private final MonitorSettingsService settings;
    private final RestTemplate restTemplate;
    private final String siteUrl;
    private volatile Map<String, Object> lastIndexNow;

    public GrowthAdminController(NewsletterService newsletter, WebPushService push, PushSubscriptionRepository pushSubscriptions,
                                 CertificateRepository certificates, UserProgressRepository progress, VisitorProfileRepository profiles,
                                 MonitorLogRepository logs, LeaderboardService leaderboards, MonitorSettingsService settings,
                                 RestTemplate restTemplate, @Value("${app.site-url:https://www.chaitanyatechworld.com}") String siteUrl) {
        this.newsletter = newsletter;
        this.push = push;
        this.pushSubscriptions = pushSubscriptions;
        this.certificates = certificates;
        this.progress = progress;
        this.profiles = profiles;
        this.logs = logs;
        this.leaderboards = leaderboards;
        this.settings = settings;
        this.restTemplate = restTemplate;
        this.siteUrl = siteUrl.replaceAll("/+$", "");
    }

    @GetMapping("/overview")
    public ApiResponse<Map<String, Object>> overview() {
        Instant monthAgo = Instant.now().minus(Duration.ofDays(30));
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("newsletter", newsletter.stats());
        out.put("pushSubscribers", pushSubscriptions.count());
        out.put("accountsSynced", progress.count());
        out.put("identified", identified());
        out.put("certificates", Map.of(
                "total", certificates.count(),
                "recent", certificates.findTop50ByOrderByIssuedAtDesc().stream().limit(20).map(GrowthController::certificateView).toList()));
        out.put("shares30d", pairs(logs.shareClicks(monthAgo)));
        out.put("conversion30d", conversion(profiles.conversionRows(monthAgo)));
        out.put("leaderboardToday", leaderboards.topToday(5));
        out.put("settings", Map.of(
                MonitorSettingsService.DIGEST_DAILY, settings.get(MonitorSettingsService.DIGEST_DAILY),
                MonitorSettingsService.DIGEST_WEEKLY, settings.get(MonitorSettingsService.DIGEST_WEEKLY),
                MonitorSettingsService.DIGEST_CAP, settings.get(MonitorSettingsService.DIGEST_CAP),
                MonitorSettingsService.PUSH_DAILY, settings.get(MonitorSettingsService.PUSH_DAILY),
                MonitorSettingsService.NOTIFY_ON_IDENTIFY, settings.get(MonitorSettingsService.NOTIFY_ON_IDENTIFY)));
        out.put("indexNow", lastIndexNow == null ? Map.of() : lastIndexNow);
        out.put("indexNowKey", settings.get(MonitorSettingsService.INDEXNOW_KEY));
        out.put("siteUrl", siteUrl);
        return ApiResponse.success("Growth", out);
    }

    private Map<String, Object> identified() {
        List<VisitorProfile> recent = profiles.findTop30ByIdentifiedAtIsNotNullOrderByIdentifiedAtDesc();
        Map<String, Long> bySource = new TreeMap<>();
        profiles.conversionRows(Instant.EPOCH).forEach(r -> {
            if (r[1] != null) bySource.merge(String.valueOf(r[1]), 1L, Long::sum);
        });
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("total", profiles.countByIdentifiedAtIsNotNull());
        m.put("bySource", bySource);
        m.put("recent", recent.stream().map(v -> {
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("visitorId", v.getVisitorId());
            r.put("name", v.getName());
            r.put("email", v.getEmail());
            r.put("username", v.getUsername());
            r.put("identitySource", v.getIdentitySource());
            r.put("identifiedAt", v.getIdentifiedAt());
            r.put("source", v.getSource());
            r.put("visits", v.getVisits());
            r.put("city", v.getCity());
            r.put("country", v.getCountry());
            return r;
        }).toList());
        return m;
    }

    /** Per traffic source: visitors, identified, conversion rate. */
    static List<Map<String, Object>> conversion(List<Object[]> rows) {
        Map<String, long[]> by = new HashMap<>();
        for (Object[] r : rows) {
            long[] c = by.computeIfAbsent(r[0] == null ? "Direct" : String.valueOf(r[0]), k -> new long[2]);
            c[0]++;
            if (r[1] != null) c[1]++;
        }
        return by.entrySet().stream()
                .sorted((a, b) -> Long.compare(b.getValue()[0], a.getValue()[0]))
                .limit(12)
                .map(e -> Map.<String, Object>of("source", e.getKey(), "visitors", e.getValue()[0], "identified", e.getValue()[1],
                        "rate", e.getValue()[0] == 0 ? 0 : Math.round(e.getValue()[1] * 1000.0 / e.getValue()[0]) / 10.0))
                .toList();
    }

    private static List<Map<String, Object>> pairs(List<Object[]> rows) {
        return rows.stream().map(r -> Map.<String, Object>of("name", String.valueOf(r[0]), "count", r[1])).toList();
    }

    /* ── Email digests ─────────────────────────────────────────────────── */

    @GetMapping(value = "/digest/preview", produces = MediaType.TEXT_HTML_VALUE)
    public String preview(@RequestParam(defaultValue = "daily") String kind) {
        return newsletter.preview(kind);
    }

    @PostMapping("/digest/test")
    public ApiResponse<Map<String, Object>> testDigest(@RequestParam(defaultValue = "daily") String kind) {
        boolean ok = newsletter.sendTest(kind);
        return ApiResponse.success(ok ? "Sent" : "Failed", Map.of("sent", ok));
    }

    @PostMapping("/digest/send")
    public ApiResponse<NewsletterService.RunResult> sendDigest(@RequestParam(defaultValue = "daily") String kind) {
        return ApiResponse.success("Done", "weekly".equals(kind) ? newsletter.sendWeekly(false) : newsletter.sendDaily(false));
    }

    /* ── Push ──────────────────────────────────────────────────────────── */

    @PostMapping("/push/broadcast")
    public ResponseEntity<ApiResponse<WebPushService.Result>> broadcast(@RequestBody Map<String, String> body) {
        String title = body.getOrDefault("title", "").trim();
        String text = body.getOrDefault("body", "").trim();
        String url = body.getOrDefault("url", siteUrl + "/daily").trim();
        if (title.isEmpty() || title.length() > 80 || text.length() > 200) {
            return ResponseEntity.unprocessableEntity().body(ApiResponse.error("Title (max 80) and message (max 200) required", null));
        }
        if (!url.startsWith(siteUrl) && !url.startsWith("/")) {
            return ResponseEntity.unprocessableEntity().body(ApiResponse.error("Links must point to your own site", null));
        }
        return ResponseEntity.ok(ApiResponse.success("Sent", push.broadcast(title, text, url.startsWith("/") ? siteUrl + url : url)));
    }

    /* ── Search engines (IndexNow: Bing, Yandex, Seznam, Naver …) ──────── */

    @PostMapping("/indexnow")
    public ApiResponse<Map<String, Object>> indexNow() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("at", System.currentTimeMillis());
        try {
            String xml = restTemplate.getForObject(siteUrl + "/sitemap.xml", String.class);
            List<String> urls = new ArrayList<>();
            Matcher m = LOC.matcher(xml == null ? "" : xml);
            while (m.find() && urls.size() < 10_000) urls.add(m.group(1));
            if (urls.isEmpty()) throw new IllegalStateException("No URLs found in " + siteUrl + "/sitemap.xml");
            String key = settings.get(MonitorSettingsService.INDEXNOW_KEY);
            Map<String, Object> payload = Map.of("host", URI.create(siteUrl).getHost(), "key", key,
                    "keyLocation", siteUrl + "/" + key + ".txt", "urlList", urls);
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            ResponseEntity<String> res = restTemplate.postForEntity("https://api.indexnow.org/indexnow", new HttpEntity<>(payload, headers), String.class);
            result.put("ok", res.getStatusCode().is2xxSuccessful());
            result.put("status", res.getStatusCode().value());
            result.put("urls", urls.size());
        } catch (org.springframework.web.client.HttpStatusCodeException e) {
            result.put("ok", false);
            result.put("status", e.getStatusCode().value());
            result.put("error", e.getStatusCode().value() == 403
                    ? "Key not accepted — make sure " + siteUrl + "/" + settings.get(MonitorSettingsService.INDEXNOW_KEY) + ".txt is deployed"
                    : e.getResponseBodyAsString());
        } catch (Exception e) {
            result.put("ok", false);
            result.put("error", e.getMessage());
        }
        lastIndexNow = result;
        return ApiResponse.success("IndexNow", result);
    }
}
