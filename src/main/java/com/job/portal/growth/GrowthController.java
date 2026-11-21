package com.job.portal.growth;

import com.fasterxml.jackson.databind.JsonNode;
import com.job.portal.dto.ApiResponse;
import com.job.portal.entity.NewsletterSubscriber;
import com.job.portal.monitoring.ClientInfo;
import com.job.portal.monitoring.VisitorIdentifiedEvent;
import com.job.portal.security.JwtTokenProvider;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.time.Instant;
import java.util.*;

/**
 * Public growth endpoints (newsletter, push, leaderboards, certificate verification)
 * and the signed-in learner's own endpoints under /api/me (JWT required).
 */
@RestController
@RequestMapping("/api")
public class GrowthController {

    /** Only real browser push services — never let a client make us POST to an arbitrary URL. */
    static final List<String> PUSH_HOSTS = List.of("fcm.googleapis.com", "updates.push.services.mozilla.com",
            ".notify.windows.com", ".push.apple.com");

    private final NewsletterService newsletter;
    private final WebPushService push;
    private final PushSubscriptionRepository pushSubscriptions;
    private final LeaderboardService leaderboards;
    private final ProgressService progress;
    private final JwtTokenProvider jwt;
    private final ApplicationEventPublisher events;

    public GrowthController(NewsletterService newsletter, WebPushService push, PushSubscriptionRepository pushSubscriptions,
                            LeaderboardService leaderboards, ProgressService progress, JwtTokenProvider jwt,
                            ApplicationEventPublisher events) {
        this.newsletter = newsletter;
        this.push = push;
        this.pushSubscriptions = pushSubscriptions;
        this.leaderboards = leaderboards;
        this.progress = progress;
        this.jwt = jwt;
        this.events = events;
    }

    private static <T> ResponseEntity<ApiResponse<T>> bad(String message) {
        return ResponseEntity.unprocessableEntity().body(ApiResponse.error(message, null));
    }

    private static boolean bool(Object v, boolean fallback) {
        return v == null ? fallback : Boolean.parseBoolean(String.valueOf(v));
    }

    private static String str(Map<String, Object> body, String key) {
        Object v = body.get(key);
        return v == null ? null : String.valueOf(v);
    }

    /* ── Newsletter ────────────────────────────────────────────────────── */

    @PostMapping("/newsletter/subscribe")
    public ResponseEntity<ApiResponse<Map<String, Object>>> subscribe(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        if (!newsletter.allow(ClientInfo.ip(request))) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(ApiResponse.error("Too many sign-ups from this network. Try again later.", null));
        }
        try {
            NewsletterSubscriber s = newsletter.subscribe(str(body, "email"), str(body, "name"), str(body, "visitorId"),
                    bool(body.get("daily"), true), bool(body.get("weekly"), true), str(body, "source"));
            return ResponseEntity.ok(ApiResponse.success("Subscribed", Map.of("email", s.getEmail(),
                    "daily", Boolean.TRUE.equals(s.getWantsDaily()), "weekly", Boolean.TRUE.equals(s.getWantsWeekly()))));
        } catch (IllegalArgumentException e) {
            return bad(e.getMessage());
        }
    }

    @GetMapping("/newsletter/preferences")
    public ResponseEntity<ApiResponse<Map<String, Object>>> preferences(@RequestParam String token) {
        return newsletter.byToken(token)
                .map(s -> ResponseEntity.ok(ApiResponse.success("Preferences", prefs(s))))
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error("This link is invalid or has expired", null)));
    }

    @PostMapping("/newsletter/preferences")
    public ResponseEntity<ApiResponse<Map<String, Object>>> updatePreferences(@RequestBody Map<String, Object> body) {
        try {
            NewsletterSubscriber s = newsletter.updatePreferences(str(body, "token"), bool(body.get("daily"), false), bool(body.get("weekly"), false));
            return ResponseEntity.ok(ApiResponse.success("Saved", prefs(s)));
        } catch (IllegalArgumentException e) {
            return bad(e.getMessage());
        }
    }

    private static Map<String, Object> prefs(NewsletterSubscriber s) {
        String email = s.getEmail();
        int at = email.indexOf('@');
        String masked = at > 2 ? email.charAt(0) + "•••" + email.substring(at - 1) : email;
        return Map.of("email", masked, "daily", Boolean.TRUE.equals(s.getWantsDaily()),
                "weekly", !Boolean.FALSE.equals(s.getWantsWeekly()) && s.isActiveSubscriber(), "active", s.isActiveSubscriber());
    }

    /* ── Push notifications ────────────────────────────────────────────── */

    @GetMapping("/push/public-key")
    public ApiResponse<Map<String, String>> pushKey() {
        return ApiResponse.success("VAPID key", Map.of("publicKey", push.publicKey()));
    }

    @PostMapping("/push/subscribe")
    public ResponseEntity<ApiResponse<Map<String, Object>>> pushSubscribe(@RequestBody Map<String, Object> body) {
        String endpoint = str(body, "endpoint");
        Object keys = body.get("keys");
        if (endpoint == null || !(keys instanceof Map<?, ?> k) || k.get("p256dh") == null || k.get("auth") == null) {
            return bad("Invalid subscription");
        }
        if (!allowedEndpoint(endpoint)) return bad("Unsupported push service");
        PushSubscription sub = pushSubscriptions.findByEndpoint(endpoint).orElseGet(PushSubscription::new);
        sub.setEndpoint(endpoint);
        sub.setP256dh(ClientInfo.truncate(String.valueOf(k.get("p256dh")), 200));
        sub.setAuth(ClientInfo.truncate(String.valueOf(k.get("auth")), 100));
        sub.setVisitorId(ClientInfo.truncate(str(body, "visitorId"), 64));
        if (sub.getCreatedAt() == null) sub.setCreatedAt(Instant.now());
        sub.setFailures(0);
        pushSubscriptions.save(sub);
        return ResponseEntity.ok(ApiResponse.success("Subscribed", Map.of("subscribed", true)));
    }

    @PostMapping("/push/unsubscribe")
    public ApiResponse<Map<String, Object>> pushUnsubscribe(@RequestBody Map<String, Object> body) {
        String endpoint = str(body, "endpoint");
        if (endpoint != null) pushSubscriptions.deleteByEndpoint(endpoint);
        return ApiResponse.success("Unsubscribed", Map.of("subscribed", false));
    }

    static boolean allowedEndpoint(String endpoint) {
        try {
            URI uri = URI.create(endpoint);
            if (!"https".equals(uri.getScheme()) || uri.getHost() == null || endpoint.length() > 600) return false;
            String host = uri.getHost().toLowerCase(Locale.ROOT);
            return PUSH_HOSTS.stream().anyMatch(h -> h.startsWith(".") ? host.endsWith(h) : host.equals(h));
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    /* ── Leaderboards ──────────────────────────────────────────────────── */

    @GetMapping("/engagement/leaderboard")
    public ApiResponse<Map<String, Object>> leaderboard(@RequestParam(defaultValue = "today") String board,
                                                        @RequestParam(required = false) String learnerId,
                                                        @RequestParam(defaultValue = "20") int limit) {
        int n = Math.min(Math.max(limit, 1), 100);
        Map<String, Object> data = switch (board) {
            case "week" -> leaderboards.week(learnerId, n);
            case "streaks" -> leaderboards.streaks(learnerId, n);
            default -> leaderboards.today(learnerId, n);
        };
        return ApiResponse.success("Leaderboard", data);
    }

    /* ── Certificate verification (public) ─────────────────────────────── */

    @GetMapping("/certificates/{code}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> certificate(@PathVariable String code) {
        return progress.find(code)
                .map(c -> ResponseEntity.ok(ApiResponse.success("Certificate", certificateView(c))))
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error("Certificate not found", null)));
    }

    static Map<String, Object> certificateView(Certificate c) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("code", c.getCode());
        m.put("kind", c.getKind());
        m.put("title", c.getTitle());
        m.put("description", c.getDescription());
        m.put("recipientName", c.getRecipientName());
        m.put("issuedAt", c.getIssuedAt());
        return m;
    }

    /* ── Signed-in learner (/api/me) ───────────────────────────────────── */

    private String email(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) return null;
        try {
            return jwt.getEmail(header.substring(7).trim());
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** Link this browser's visitor id to the signed-in account (name, email). */
    @PostMapping("/me/identify")
    public ApiResponse<Map<String, Object>> identify(@RequestBody Map<String, Object> body, Authentication auth, HttpServletRequest request) {
        String provider = str(body, "provider");
        String source = Set.of("google", "github").contains(provider) ? provider : "account";
        String email = email(request);
        events.publishEvent(new VisitorIdentifiedEvent(str(body, "visitorId"), str(body, "name"), email, auth.getName(), source));
        return ApiResponse.success("Linked", Map.of("username", auth.getName(), "email", email == null ? "" : email));
    }

    @GetMapping("/me/progress")
    public ApiResponse<JsonNode> getProgress(Authentication auth) {
        return ApiResponse.success("Progress", progress.get(auth.getName()));
    }

    @PutMapping("/me/progress")
    public ResponseEntity<ApiResponse<JsonNode>> syncProgress(@RequestBody JsonNode body, Authentication auth, HttpServletRequest request) {
        try {
            return ResponseEntity.ok(ApiResponse.success("Synced", progress.sync(auth.getName(), email(request), body)));
        } catch (IllegalArgumentException e) {
            return bad(e.getMessage());
        }
    }

    @GetMapping("/me/certificates")
    public ApiResponse<Map<String, Object>> myCertificates(Authentication auth) {
        return ApiResponse.success("Certificates", Map.of(
                "available", progress.eligibility(auth.getName()),
                "issued", progress.mine(auth.getName()).stream().map(GrowthController::certificateView).toList()));
    }

    @PostMapping("/me/certificates")
    public ResponseEntity<ApiResponse<Map<String, Object>>> claim(@RequestBody Map<String, Object> body, Authentication auth, HttpServletRequest request) {
        try {
            Certificate c = progress.issue(auth.getName(), email(request), str(body, "name"), str(body, "kind"));
            return ResponseEntity.ok(ApiResponse.success("Issued", certificateView(c)));
        } catch (IllegalArgumentException | IllegalStateException e) {
            return bad(e.getMessage());
        }
    }
}
