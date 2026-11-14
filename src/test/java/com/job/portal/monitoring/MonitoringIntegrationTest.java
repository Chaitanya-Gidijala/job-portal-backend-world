package com.job.portal.monitoring;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.*;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** Admin monitoring API end-to-end: role enforcement, ingestion → live views, stream tickets. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "APP_CORS_ALLOWED_ORIGINS=http://localhost:5173",
        "app.admin-email=test@example.com",
        "BREVO_API_KEY=test",
        "RAZORPAY_KEY_ID=test",
        "RAZORPAY_KEY_SECRET=test",
        "app.jwt-secret=" + MonitoringIntegrationTest.SECRET,
        "monitor.geoip.enabled=false",
        "monitor.targets.security-url=",
        "monitor.targets.frontend-url=",
})
class MonitoringIntegrationTest {

    static final String SECRET = "Q1RXLW1vbml0b3JpbmctdGVzdC1zZWNyZXQtdGhhdC1pcy1sb25nLWVub3VnaC1mb3ItaHM1MTI=";

    @LocalServerPort
    int port;

    @Autowired
    TestRestTemplate rest;

    @Autowired
    PresenceService presence;

    private String url(String path) {
        return "http://localhost:" + port + path;
    }

    private static String token(String user, List<String> roles) {
        return Jwts.builder()
                .subject(user)
                .claim("roles", roles)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 3_600_000))
                .signWith(Keys.hmacShaKeyFor(Decoders.BASE64.decode(SECRET)))
                .compact();
    }

    private HttpEntity<Void> as(String token) {
        HttpHeaders h = new HttpHeaders();
        h.setBearerAuth(token);
        return new HttpEntity<>(h);
    }

    private ResponseEntity<Map> adminGet(String path) {
        return rest.exchange(url(path), HttpMethod.GET, as(token("chief", List.of("ROLE_ADMIN"))), Map.class);
    }

    @Test
    void adminApiRequiresAdminRole() {
        assertEquals(HttpStatus.UNAUTHORIZED, rest.getForEntity(url("/api/admin/monitor/overview"), String.class).getStatusCode());
        assertEquals(HttpStatus.FORBIDDEN, rest.exchange(url("/api/admin/monitor/overview"), HttpMethod.GET,
                as(token("bob", List.of("ROLE_USER"))), String.class).getStatusCode());
        assertEquals(HttpStatus.OK, adminGet("/api/admin/monitor/overview").getStatusCode());
        assertEquals(HttpStatus.OK, adminGet("/api/admin/monitor/health").getStatusCode());
    }

    @Test
    void contactInboxIsNoLongerPublic() {
        assertEquals(HttpStatus.UNAUTHORIZED, rest.getForEntity(url("/api/contact/all"), String.class).getStatusCode());
        assertEquals(HttpStatus.OK, adminGet("/api/contact/all").getStatusCode());
    }

    @Test
    @SuppressWarnings("unchecked")
    void presenceAndEventsReachTheDashboard() {
        String session = UUID.randomUUID().toString();
        String visitor = UUID.randomUUID().toString();
        HttpHeaders text = new HttpHeaders();
        text.setContentType(MediaType.TEXT_PLAIN);

        String ping = "{\"sessionId\":\"" + session + "\",\"visitorId\":\"" + visitor + "\",\"path\":\"/daily\",\"title\":\"Daily\",\"timezone\":\"Asia/Kolkata\"}";
        assertEquals(HttpStatus.NO_CONTENT, rest.postForEntity(url("/api/monitor/presence"), new HttpEntity<>(ping, text), Void.class).getStatusCode());

        String events = "{\"sessionId\":\"" + session + "\",\"visitorId\":\"" + visitor + "\",\"events\":["
                + "{\"type\":\"click\",\"path\":\"/daily\",\"label\":\"Start Daily #1\"},"
                + "{\"type\":\"bogus\"}]}";
        assertEquals(HttpStatus.NO_CONTENT, rest.postForEntity(url("/api/monitor/events"), new HttpEntity<>(events, text), Void.class).getStatusCode());

        // Unique visitors are counted exactly once per day (regression: reserved "day" column broke this).
        presence.flushAnalytics();
        Map<String, Object> today = (Map<String, Object>) ((Map<String, Object>) adminGet("/api/admin/monitor/overview").getBody().get("data")).get("today");
        assertTrue(((Number) today.get("uniqueVisitors")).longValue() >= 1, "unique visitors counted");
        assertTrue(((Number) today.get("views")).longValue() >= 1, "page views counted");

        Map<String, Object> visitors = (Map<String, Object>) adminGet("/api/admin/monitor/visitors").getBody().get("data");
        List<Map<String, Object>> sessions = (List<Map<String, Object>>) visitors.get("sessions");
        assertTrue(sessions.stream().anyMatch(s -> session.equals(s.get("sessionId")) && "/daily".equals(s.get("path"))));

        List<Map<String, Object>> recent = (List<Map<String, Object>>) adminGet("/api/admin/monitor/logs/recent").getBody().get("data");
        assertTrue(recent.stream().anyMatch(l -> "CLICK".equals(l.get("kind")) && "Start Daily #1".equals(l.get("label"))));
        // The admin's own API calls are logged as REQUESTs with the verified username.
        assertTrue(recent.stream().anyMatch(l -> "REQUEST".equals(l.get("kind")) && "chief".equals(l.get("username"))));

        String leave = "{\"sessionId\":\"" + session + "\"}";
        rest.postForEntity(url("/api/monitor/leave"), new HttpEntity<>(leave, text), Void.class);
        visitors = (Map<String, Object>) adminGet("/api/admin/monitor/visitors").getBody().get("data");
        sessions = (List<Map<String, Object>>) visitors.get("sessions");
        assertTrue(sessions.stream().noneMatch(s -> session.equals(s.get("sessionId"))));
    }

    @Test
    @SuppressWarnings("unchecked")
    void streamTicketsAreSingleUse() throws Exception {
        ResponseEntity<Map> ticketRes = rest.exchange(url("/api/admin/monitor/stream-ticket"), HttpMethod.POST,
                as(token("chief", List.of("ROLE_ADMIN"))), Map.class);
        String ticket = (String) ((Map<String, Object>) ticketRes.getBody().get("data")).get("ticket");

        HttpURLConnection conn = (HttpURLConnection) URI.create(url("/api/admin/monitor/stream?ticket=" + ticket)).toURL().openConnection();
        conn.setRequestProperty("Accept", "text/event-stream");
        conn.setReadTimeout(5_000);
        assertEquals(200, conn.getResponseCode());
        try (BufferedReader r = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
            assertEquals("event:presence", r.readLine());
        } finally {
            conn.disconnect();
        }

        assertEquals(HttpStatus.UNAUTHORIZED, rest.getForEntity(url("/api/admin/monitor/stream?ticket=" + ticket), String.class).getStatusCode());
        assertEquals(HttpStatus.UNAUTHORIZED, rest.getForEntity(url("/api/admin/monitor/stream?ticket=forged"), String.class).getStatusCode());
    }

    @Test
    void secretsInQueryStringsAreMasked() {
        assertEquals("ticket=***&page=2", RequestLoggingFilter.maskQuery("ticket=abc123&page=2"));
        assertEquals("q=x&token=***", RequestLoggingFilter.maskQuery("q=x&token=eyJ"));
        assertEquals("/api/jobs/{id}/apply", MonitoringHub.normalize("/api/jobs/42/apply"));
    }
}
