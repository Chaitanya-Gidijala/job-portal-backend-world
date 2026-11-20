package com.job.portal.monitoring;

import com.job.portal.service.EmailAsyncService;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
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

/** Visitor directory, client details and visit email notifications, end to end over HTTP. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "app.jwt-secret=" + VisitorFeaturesTest.SECRET,
        "monitor.notify.delay-ms=0",
        "app.site-url=https://example.test",
})
class VisitorFeaturesTest {

    static final String SECRET = "Q1RXLW1vbml0b3JpbmctdGVzdC1zZWNyZXQtdGhhdC1pcy1sb25nLWVub3VnaC1mb3ItaHM1MTI=";
    static final String CHROME_ANDROID = "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36";

    @Autowired
    TestRestTemplate rest;
    @Autowired
    VisitorProfileService profiles;
    @Autowired
    VisitNotificationService notifications;
    @Autowired
    MonitoringSchemaGuard schemaGuard;
    @MockitoBean
    EmailAsyncService email;

    private HttpHeaders admin() {
        String t = Jwts.builder().subject("chief").claim("roles", List.of("ROLE_ADMIN"))
                .expiration(new Date(System.currentTimeMillis() + 600_000))
                .signWith(Keys.hmacShaKeyFor(Decoders.BASE64.decode(SECRET))).compact();
        HttpHeaders h = new HttpHeaders();
        h.setBearerAuth(t);
        return h;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> adminCall(HttpMethod method, String path, Object body) {
        HttpHeaders h = admin();
        h.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<Map> res = rest.exchange(path, method, new HttpEntity<>(body, h), Map.class);
        assertEquals(HttpStatus.OK, res.getStatusCode(), path + " -> " + res.getBody());
        return (Map<String, Object>) res.getBody().get("data");
    }

    private void ping(String session, String visitor, String path, String client) {
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.TEXT_PLAIN);
        h.set("User-Agent", CHROME_ANDROID);
        String body = "{\"sessionId\":\"" + session + "\",\"visitorId\":\"" + visitor + "\",\"path\":\"" + path + "\""
                + ",\"referrer\":\"https://www.google.com/\",\"language\":\"en-IN\",\"timezone\":\"Asia/Kolkata\",\"screen\":\"412x915\""
                + (client == null ? "" : ",\"client\":" + client) + "}";
        assertEquals(HttpStatus.NO_CONTENT, rest.postForEntity("/api/monitor/presence", new HttpEntity<>(body, h), Void.class).getStatusCode());
    }

    @Test
    @SuppressWarnings("unchecked")
    void visitorIsStoredDetailedAndNotified() {
        when(email.sendNow(anyString(), anyString(), anyString())).thenReturn(true);
        when(email.getAdminEmail()).thenReturn("owner@example.test");
        adminCall(HttpMethod.PUT, "/api/admin/monitor/notifications/settings",
                Map.of("notify.mode", "EVERY_VISIT", "notify.cooldownHours", "6", "notify.dailyCap", "50"));

        String visitor = UUID.randomUUID().toString();
        String session = UUID.randomUUID().toString();
        ping(session, visitor, "/daily", "{\"viewport\":\"412x780\",\"cores\":\"8\",\"memory\":\"4\",\"connection\":\"4g\","
                + "\"utmSource\":\"linkedin\",\"utmCampaign\":\"launch\",\"landing\":\"/daily?utm_source=linkedin\",\"evil\":\"<script>\"}");
        ping(session, visitor, "/roadmap/java", null);
        profiles.flush();

        // Stored profile with first-touch attribution and parsed versions
        Map<String, Object> page = adminCall(HttpMethod.GET, "/api/admin/monitor/visitors/directory?q=" + visitor.substring(0, 8), null);
        List<Map<String, Object>> items = (List<Map<String, Object>>) page.get("items");
        assertEquals(1, items.size());
        Map<String, Object> p = items.get(0);
        assertEquals("linkedin", p.get("source"));
        assertEquals("launch", p.get("utmCampaign"));
        assertEquals("Chrome 131", p.get("browser"));
        assertEquals("Android 14", p.get("os"));
        assertEquals("Mobile", p.get("device"));
        assertEquals(1, ((Number) p.get("visits")).intValue());
        assertEquals(2, ((Number) p.get("pageViews")).intValue());
        assertEquals("/roadmap/java", p.get("lastPage"));

        Map<String, Object> profile = adminCall(HttpMethod.GET, "/api/admin/monitor/visitors/" + visitor + "/profile", null);
        Map<String, String> details = (Map<String, String>) profile.get("details");
        assertEquals("8", details.get("cores"));
        assertEquals("4g", details.get("connection"));
        assertFalse(details.containsKey("evil"), "unknown client keys are dropped");
        assertEquals(Boolean.TRUE, profile.get("online"));

        // First visit → email sent to the admin address with the visit details
        notifications.processDue();
        verify(email).sendNow(eq("owner@example.test"), contains("New visitor"), argThat(html ->
                html.contains("/roadmap/java") && html.contains("Android 14") && html.contains("linkedin") && html.contains("launch")
                        && html.contains("https://example.test/admin/visitors?visitor=" + visitor)));

        // Same visitor, new session → skipped by the cooldown
        ping(UUID.randomUUID().toString(), visitor, "/", null);
        profiles.flush();
        notifications.processDue();
        verify(email, times(1)).sendNow(anyString(), anyString(), anyString());
        Map<String, Object> status = adminCall(HttpMethod.GET, "/api/admin/monitor/notifications", null);
        List<Map<String, Object>> recent = (List<Map<String, Object>>) status.get("recent");
        assertEquals("SKIPPED", recent.get(0).get("status"));
        assertTrue(String.valueOf(recent.get(0).get("reason")).contains("cooldown"));
        assertEquals("SENT", recent.get(1).get("status"));

        // Ignore list: a brand-new visitor on the list is never emailed
        String ignored = UUID.randomUUID().toString();
        adminCall(HttpMethod.POST, "/api/admin/monitor/notifications/ignore", Map.of("visitorId", ignored));
        ping(UUID.randomUUID().toString(), ignored, "/", null);
        profiles.flush();
        notifications.processDue();
        verify(email, times(1)).sendNow(anyString(), anyString(), anyString());

        // Summary
        Map<String, Object> summary = adminCall(HttpMethod.GET, "/api/admin/monitor/visitors/summary", null);
        assertTrue(((Number) summary.get("total")).intValue() >= 2);
        assertTrue(((List<?>) summary.get("sources30d")).stream().anyMatch(s -> "linkedin".equals(((Map<?, ?>) s).get("name"))));

        assertTrue(Boolean.TRUE.equals(schemaGuard.report().get("ok")), "schema " + schemaGuard.report());
    }

    /** Regression: a reload sends "leave" then pings again with the same tab session — that is the same visit. */
    @Test
    @SuppressWarnings("unchecked")
    void reloadContinuesTheSameVisit() {
        String visitor = UUID.randomUUID().toString();
        String session = UUID.randomUUID().toString();
        ping(session, visitor, "/daily", null);
        HttpHeaders text = new HttpHeaders();
        text.setContentType(MediaType.TEXT_PLAIN);
        rest.postForEntity("/api/monitor/leave", new HttpEntity<>("{\"sessionId\":\"" + session + "\"}", text), Void.class);
        ping(session, visitor, "/roadmap", null);
        profiles.flush();
        Map<String, Object> page = adminCall(HttpMethod.GET, "/api/admin/monitor/visitors/directory?q=" + visitor.substring(0, 8), null);
        Map<String, Object> p = ((List<Map<String, Object>>) page.get("items")).get(0);
        assertEquals(1, ((Number) p.get("visits")).intValue(), "a reload is not a new visit");
        Map<String, Object> live = adminCall(HttpMethod.GET, "/api/admin/monitor/visitors", null);
        Map<String, Object> s = ((List<Map<String, Object>>) live.get("sessions")).stream()
                .filter(x -> session.equals(x.get("sessionId"))).findFirst().orElseThrow();
        assertEquals(List.of("/daily", "/roadmap"), s.get("trail"), "the resumed visit keeps its page trail");
        assertEquals(2, ((Number) p.get("pageViews")).intValue());
    }

    @Test
    void settingsAreValidated() {
        HttpHeaders h = admin();
        h.setContentType(MediaType.APPLICATION_JSON);
        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, rest.exchange("/api/admin/monitor/notifications/settings", HttpMethod.PUT,
                new HttpEntity<>(Map.of("notify.mode", "SOMETIMES"), h), String.class).getStatusCode());
        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, rest.exchange("/api/admin/monitor/notifications/settings", HttpMethod.PUT,
                new HttpEntity<>(Map.of("notify.recipient", "not-an-email"), h), String.class).getStatusCode());
        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, rest.exchange("/api/admin/monitor/notifications/settings", HttpMethod.PUT,
                new HttpEntity<>(Map.of("unknown.key", "1"), h), String.class).getStatusCode());
        assertEquals(HttpStatus.OK, rest.exchange("/api/admin/monitor/notifications/settings", HttpMethod.PUT,
                new HttpEntity<>(Map.of("notify.mode", "NEW_VISITORS", "notify.dailyCap", "20"), h), String.class).getStatusCode());
        assertEquals(HttpStatus.UNAUTHORIZED, rest.exchange("/api/admin/monitor/notifications", HttpMethod.GET,
                HttpEntity.EMPTY, String.class).getStatusCode());
    }
}
