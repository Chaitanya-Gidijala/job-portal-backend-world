package com.job.portal.controller;

import com.job.portal.service.DailyChallengeService;
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
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** End-to-end over HTTP with an in-memory H2 database (anonymous access, no JWT). */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
// The app needs these secrets at startup; dummy values are enough for this slice.
@TestPropertySource(properties = {
        "APP_CORS_ALLOWED_ORIGINS=http://localhost:5173",
        "app.admin-email=test@example.com",
        "BREVO_API_KEY=test",
        "RAZORPAY_KEY_ID=test",
        "RAZORPAY_KEY_SECRET=test",
})
class EngagementControllerTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate rest;

    @Autowired
    private DailyChallengeService service;

    private String url(String path) {
        return "http://localhost:" + port + "/api/engagement/daily" + path;
    }

    private ResponseEntity<Map> submit(int day, String learner, String answers, int score) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        Map<String, Object> body = Map.of("learnerId", learner, "answers", answers, "score", score, "durationMs", 42_000);
        return rest.postForEntity(url("/" + day + "/results"), new HttpEntity<>(body, headers), Map.class);
    }

    @Test
    void submitThenReadStats() {
        int day = service.currentDay();
        String learner = UUID.randomUUID().toString();

        ResponseEntity<Map> created = submit(day, learner, "110", 2);
        assertEquals(HttpStatus.CREATED, created.getStatusCode());

        ResponseEntity<Map> duplicate = submit(day, learner, "111", 3);
        assertEquals(HttpStatus.OK, duplicate.getStatusCode());
        Map<?, ?> dupData = (Map<?, ?>) duplicate.getBody().get("data");
        assertEquals(Boolean.TRUE, dupData.get("alreadySubmitted"));

        ResponseEntity<Map> stats = rest.getForEntity(url("/" + day + "/stats"), Map.class);
        assertEquals(HttpStatus.OK, stats.getStatusCode());
        Map<?, ?> data = (Map<?, ?>) stats.getBody().get("data");
        assertTrue(((Number) data.get("completions")).longValue() >= 1);
    }

    @Test
    void rejectsInvalidPayloadsAndUnknownPuzzles() {
        int day = service.currentDay();
        assertEquals(HttpStatus.BAD_REQUEST, submit(day, "not-a-uuid", "110", 2).getStatusCode());
        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, submit(day, UUID.randomUUID().toString(), "110", 3).getStatusCode());
        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, submit(day - 5, UUID.randomUUID().toString(), "110", 2).getStatusCode());
        assertEquals(HttpStatus.NOT_FOUND, rest.getForEntity(url("/" + (day + 10) + "/stats"), String.class).getStatusCode());
    }

    @Test
    void liveStreamSendsSnapshotFirst() throws Exception {
        int day = service.currentDay();
        HttpURLConnection conn = (HttpURLConnection) URI.create(url("/" + day + "/live?learnerId=tester")).toURL().openConnection();
        conn.setRequestProperty("Accept", "text/event-stream");
        conn.setReadTimeout(5_000);
        assertEquals(200, conn.getResponseCode());
        assertTrue(conn.getContentType().startsWith("text/event-stream"));

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
            assertEquals("event:stats", reader.readLine());
            String data = reader.readLine();
            assertTrue(data.startsWith("data:"), data);
            assertTrue(data.contains("\"liveNow\":1"), data);
        } finally {
            conn.disconnect();
        }
    }
}
