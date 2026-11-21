package com.job.portal.growth;

import com.job.portal.monitoring.VisitorProfileService;
import com.job.portal.service.DailyChallengeService;
import com.job.portal.service.EmailAsyncService;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Newsletter, identity, progress sync, certificates, leaderboards, push and the admin Growth API. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "app.jwt-secret=" + GrowthFeaturesTest.SECRET,
        "app.site-url=https://example.test",
})
class GrowthFeaturesTest {

    static final String SECRET = "Q1RXLW1vbml0b3JpbmctdGVzdC1zZWNyZXQtdGhhdC1pcy1sb25nLWVub3VnaC1mb3ItaHM1MTI=";

    @Autowired
    TestRestTemplate rest;
    @Autowired
    VisitorProfileService profiles;
    @Autowired
    DailyChallengeService daily;
    @MockitoBean
    EmailAsyncService email;

    @BeforeEach
    void mail() {
        when(email.sendNow(anyString(), anyString(), anyString())).thenReturn(true);
        when(email.getAdminEmail()).thenReturn("owner@example.test");
    }

    private static String token(String user, String mail, String role) {
        return Jwts.builder().subject(user).claim("roles", List.of(role)).claim("email", mail)
                .expiration(new Date(System.currentTimeMillis() + 600_000))
                .signWith(Keys.hmacShaKeyFor(Decoders.BASE64.decode(SECRET))).compact();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> call(HttpMethod method, String path, Object body, String bearer, HttpStatus expect) {
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        if (bearer != null) h.setBearerAuth(bearer);
        ResponseEntity<Map> res = rest.exchange(path, method, new HttpEntity<>(body, h), Map.class);
        assertEquals(expect, res.getStatusCode(), path + " -> " + res.getBody());
        Object data = res.getBody() == null ? null : res.getBody().get("data");
        return data instanceof Map ? (Map<String, Object>) data : Map.of("value", String.valueOf(data));
    }

    private Map<String, Object> profile(String visitorId) {
        profiles.flush();
        return call(HttpMethod.GET, "/api/admin/monitor/visitors/" + visitorId + "/profile", null, token("chief", "chief@example.test", "ROLE_ADMIN"), HttpStatus.OK);
    }

    @Test
    @SuppressWarnings("unchecked")
    void newsletterIdentityAndPreferences() {
        String visitor = UUID.randomUUID().toString();
        call(HttpMethod.POST, "/api/newsletter/subscribe", Map.of("email", "Asha.Rao@Example.com", "name", "Asha Rao",
                "visitorId", visitor, "daily", true, "weekly", false, "source", "daily-result"), null, HttpStatus.OK);
        call(HttpMethod.POST, "/api/newsletter/subscribe", Map.of("email", "not-an-email"), null, HttpStatus.UNPROCESSABLE_ENTITY);

        // The visitor profile now has a real name and email
        Map<String, Object> p = (Map<String, Object>) profile(visitor).get("profile");
        assertEquals("Asha Rao", p.get("name"));
        assertEquals("asha.rao@example.com", p.get("email"));
        assertEquals("newsletter", p.get("identitySource"));

        // Welcome email + admin identity alert
        verify(email, timeout(3000)).sendNow(eq("asha.rao@example.com"), contains("You're in"), anyString());
        verify(email, timeout(3000)).sendNow(eq("owner@example.test"), contains("Asha Rao"), anyString());

        // A Google sign-in is stronger evidence and updates the identity
        call(HttpMethod.POST, "/api/me/identify", Map.of("visitorId", visitor, "name", "Asha R. Kumar", "provider", "google"),
                token("asha", "asha@gmail.com", "ROLE_USER"), HttpStatus.OK);
        p = (Map<String, Object>) profile(visitor).get("profile");
        assertEquals("Asha R. Kumar", p.get("name"));
        assertEquals("asha@gmail.com", p.get("email"));
        assertEquals("google", p.get("identitySource"));
        assertEquals("asha", p.get("username"));
        // …and a later form submission (weaker) does not overwrite it
        call(HttpMethod.POST, "/api/newsletter/subscribe", Map.of("email", "other@example.com", "name", "Someone Else",
                "visitorId", visitor, "daily", true, "weekly", true), null, HttpStatus.OK);
        p = (Map<String, Object>) profile(visitor).get("profile");
        assertEquals("Asha R. Kumar", p.get("name"));

        // Preferences & unsubscribe through the emailed token
        Map<String, Object> admin = call(HttpMethod.GET, "/api/admin/growth/overview", null, token("chief", "c@e.t", "ROLE_ADMIN"), HttpStatus.OK);
        Map<String, Object> news = (Map<String, Object>) admin.get("newsletter");
        assertTrue(((Number) news.get("active")).intValue() >= 2);
        String tokenValue = tokenFor("asha.rao@example.com");
        Map<String, Object> prefs = call(HttpMethod.GET, "/api/newsletter/preferences?token=" + tokenValue, null, null, HttpStatus.OK);
        assertEquals(Boolean.TRUE, prefs.get("daily"));
        assertFalse(String.valueOf(prefs.get("email")).contains("asha.rao"), "email is masked");
        call(HttpMethod.POST, "/api/newsletter/preferences", Map.of("token", tokenValue, "daily", false, "weekly", false), null, HttpStatus.OK);
        prefs = call(HttpMethod.GET, "/api/newsletter/preferences?token=" + tokenValue, null, null, HttpStatus.OK);
        assertEquals(Boolean.FALSE, prefs.get("active"));
        call(HttpMethod.GET, "/api/newsletter/preferences?token=bogus", null, null, HttpStatus.NOT_FOUND);
    }

    @Autowired
    com.job.portal.repository.NewsletterSubscriberRepository subscribers;

    private String tokenFor(String address) {
        return subscribers.findFirstByEmailIgnoreCase(address).orElseThrow().getToken();
    }

    @Test
    @SuppressWarnings("unchecked")
    void progressSyncMergesDevicesAndUnlocksCertificates() {
        String jwt = token("ravi", "ravi@example.com", "ROLE_USER");
        call(HttpMethod.GET, "/api/me/progress", null, null, HttpStatus.UNAUTHORIZED);

        Map<String, Object> phone = Map.of("v", 1, "learnerId", "device-a", "createdAt", "2026-11-01T00:00:00Z",
                "days", Map.of("2026-11-18", Map.of("xp", 20), "2026-11-19", Map.of("xp", 10)),
                "roadmaps", Map.of("java", Map.of("sec-1-1::Install JDK 21", "2026-11-18")),
                "challenges", challenges(1, 6));
        Map<String, Object> laptop = Map.of("v", 1, "learnerId", "device-b",
                "days", Map.of("2026-11-19", Map.of("xp", 30), "2026-11-20", Map.of("xp", 5)),
                "roadmaps", Map.of("java", Map.of("sec-1-1::Maven & Gradle", "2026-11-20")),
                "challenges", challenges(6, 10));

        call(HttpMethod.PUT, "/api/me/progress", phone, jwt, HttpStatus.OK);
        Map<String, Object> merged = call(HttpMethod.PUT, "/api/me/progress", laptop, jwt, HttpStatus.OK);
        Map<String, Object> days = (Map<String, Object>) merged.get("days");
        assertEquals(3, days.size());
        assertEquals(30, ((Map<String, Object>) days.get("2026-11-19")).get("xp"));
        assertEquals(2, ((Map<String, Object>) ((Map<String, Object>) merged.get("roadmaps")).get("java")).size());
        assertEquals(10, ((Map<String, Object>) merged.get("challenges")).size());
        assertFalse(merged.containsKey("learnerId"), "device ids are not stored");

        // Certificates: earned ones can be claimed, others are refused
        Map<String, Object> certs = call(HttpMethod.GET, "/api/me/certificates", null, jwt, HttpStatus.OK);
        Map<String, Object> ten = ((List<Map<String, Object>>) certs.get("available")).stream()
                .filter(c -> "PUZZLES_10".equals(c.get("kind"))).findFirst().orElseThrow();
        assertEquals(Boolean.TRUE, ten.get("eligible"));
        call(HttpMethod.POST, "/api/me/certificates", Map.of("kind", "ROADMAP_JAVA", "name", "Ravi Teja"), jwt, HttpStatus.UNPROCESSABLE_ENTITY);
        Map<String, Object> issued = call(HttpMethod.POST, "/api/me/certificates", Map.of("kind", "PUZZLES_10", "name", "Ravi Teja"), jwt, HttpStatus.OK);
        String code = (String) issued.get("code");
        assertTrue(code.startsWith("CTW-"));
        Map<String, Object> again = call(HttpMethod.POST, "/api/me/certificates", Map.of("kind", "PUZZLES_10", "name", "Ravi Teja"), jwt, HttpStatus.OK);
        assertEquals(code, again.get("code"), "claiming twice returns the same certificate");

        Map<String, Object> pub = call(HttpMethod.GET, "/api/certificates/" + code, null, null, HttpStatus.OK);
        assertEquals("Ravi Teja", pub.get("recipientName"));
        assertFalse(pub.containsKey("email"), "public view hides the email");
        call(HttpMethod.GET, "/api/certificates/CTW-NOPE0000", null, null, HttpStatus.NOT_FOUND);
    }

    private static Map<String, Object> challenges(int from, int to) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = from; i <= to; i++) m.put(String.valueOf(i), Map.of("score", 2));
        return m;
    }

    @Test
    @SuppressWarnings("unchecked")
    void leaderboardsUseFirstNameAndInitial() {
        int day = daily.currentDay();
        String named = UUID.randomUUID().toString();
        String anon = UUID.randomUUID().toString();
        call(HttpMethod.POST, "/api/newsletter/subscribe", Map.of("email", "meera@example.com", "name", "Meera Nair", "visitorId", named), null, HttpStatus.OK);
        profiles.flush();
        call(HttpMethod.POST, "/api/engagement/daily/" + day + "/results", Map.of("learnerId", named, "answers", "111", "score", 3, "durationMs", 40_000), null, HttpStatus.CREATED);
        call(HttpMethod.POST, "/api/engagement/daily/" + day + "/results", Map.of("learnerId", anon, "answers", "110", "score", 2, "durationMs", 20_000), null, HttpStatus.CREATED);

        Map<String, Object> today = call(HttpMethod.GET, "/api/engagement/leaderboard?board=today&learnerId=" + anon, null, null, HttpStatus.OK);
        List<Map<String, Object>> entries = (List<Map<String, Object>>) today.get("entries");
        Map<String, Object> first = entries.stream().filter(e -> "Meera N.".equals(e.get("name"))).findFirst().orElseThrow();
        Map<String, Object> second = entries.stream().filter(e -> String.valueOf(e.get("name")).startsWith("Learner " + anon.substring(0, 4).toUpperCase())).findFirst().orElseThrow();
        assertTrue(((Number) first.get("rank")).intValue() < ((Number) second.get("rank")).intValue());
        assertEquals(Boolean.TRUE, ((Map<String, Object>) today.get("you")).get("you"));
        call(HttpMethod.GET, "/api/engagement/leaderboard?board=week", null, null, HttpStatus.OK);
        Map<String, Object> streaks = call(HttpMethod.GET, "/api/engagement/leaderboard?board=streaks", null, null, HttpStatus.OK);
        assertTrue(((Number) streaks.get("players")).intValue() >= 2);
    }

    @Test
    void pushAndAdminGrowth() {
        Map<String, Object> key = call(HttpMethod.GET, "/api/push/public-key", null, null, HttpStatus.OK);
        assertEquals(87, String.valueOf(key.get("publicKey")).length(), "65-byte P-256 point, base64url");
        call(HttpMethod.POST, "/api/push/subscribe", Map.of("endpoint", "https://evil.example/x", "keys", Map.of("p256dh", "a", "auth", "b")), null, HttpStatus.UNPROCESSABLE_ENTITY);
        call(HttpMethod.POST, "/api/push/subscribe", Map.of("endpoint", "https://fcm.googleapis.com/fcm/send/test-" + UUID.randomUUID(),
                "keys", Map.of("p256dh", "BPub", "auth", "auth"), "visitorId", "v1"), null, HttpStatus.OK);

        String admin = token("chief", "c@e.t", "ROLE_ADMIN");
        call(HttpMethod.GET, "/api/admin/growth/overview", null, token("bob", "b@e.t", "ROLE_USER"), HttpStatus.FORBIDDEN);
        Map<String, Object> overview = call(HttpMethod.GET, "/api/admin/growth/overview", null, admin, HttpStatus.OK);
        assertTrue(((Number) overview.get("pushSubscribers")).intValue() >= 1);

        HttpHeaders h = new HttpHeaders();
        h.setBearerAuth(admin);
        ResponseEntity<String> preview = rest.exchange("/api/admin/growth/digest/preview?kind=daily", HttpMethod.GET, new HttpEntity<>(h), String.class);
        assertTrue(preview.getBody().contains("Traceflow Daily #"));
        assertTrue(preview.getBody().contains("Unsubscribe"));

        call(HttpMethod.POST, "/api/newsletter/subscribe", Map.of("email", "daily@example.com", "daily", true, "weekly", false), null, HttpStatus.OK);
        call(HttpMethod.POST, "/api/admin/growth/digest/send?kind=daily", null, admin, HttpStatus.OK);
        verify(email, timeout(3000)).sendNow(eq("daily@example.com"), contains("is live"), contains("utm_source=email"));
    }
}
