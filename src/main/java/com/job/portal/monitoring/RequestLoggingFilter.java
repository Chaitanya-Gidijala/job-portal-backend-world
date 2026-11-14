package com.job.portal.monitoring;

import com.job.portal.security.JwtTokenProvider;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Logs every HTTP request (method, path, status, latency, IP, user, visitor, location).
 *
 * Runs outermost — before Spring Security — so rejected requests (401/403) are
 * captured too. The user comes from the Bearer token; the visitor is matched to a
 * live React session by IP + user agent. Presence heartbeats are tagged HEARTBEAT
 * (memory only) and the admin's own live stream is not logged.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class RequestLoggingFilter extends OncePerRequestFilter {

    private static final int TOKEN_CACHE_MAX = 2_000;

    private final MonitoringHub hub;
    private final PresenceService presence;
    private final GeoIpService geo;
    private final JwtTokenProvider jwt;
    /** Verified identity from a JWT; empty username = invalid token. */
    record Identity(String username, String email) {
        static final Identity NONE = new Identity("", null);
    }

    private final Map<String, Identity> tokenUsers = new ConcurrentHashMap<>();

    public RequestLoggingFilter(MonitoringHub hub, PresenceService presence, GeoIpService geo, JwtTokenProvider jwt) {
        this.hub = hub;
        this.presence = presence;
        this.geo = geo;
        this.jwt = jwt;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return "OPTIONS".equals(request.getMethod())
                || path.startsWith("/api/admin/monitor/stream")
                || path.startsWith("/actuator")
                || path.equals("/favicon.ico");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        long start = System.nanoTime();
        try {
            chain.doFilter(request, response);
        } finally {
            try {
                record(request, response.getStatus(), (System.nanoTime() - start) / 1_000_000);
            } catch (RuntimeException ignored) {
                // monitoring must never break a request
            }
        }
    }

    private void record(HttpServletRequest request, int status, long durationMs) {
        String path = request.getRequestURI();
        String ip = ClientInfo.ip(request);
        String ua = request.getHeader("User-Agent");
        boolean heartbeat = path.startsWith("/api/monitor/");

        PresenceService.LiveSession session = presence.findByClient(ip, ua);
        Identity id = identity(request.getHeader("Authorization"));
        String username = id == null ? null : id.username();
        String email = id == null ? null : id.email();
        if (session != null) {
            // Verified identity (from a valid JWT) sticks to the visitor's live session.
            if (username != null) {
                session.setUsername(username);
                session.setEmail(email);
            } else {
                username = session.getUsername();
                email = session.getEmail();
            }
        }

        GeoIpService.Geo g = geo.cached(ip);
        if (g == null && !heartbeat) geo.resolve(ip, x -> { });

        hub.record(LogEntry.builder()
                .ts(System.currentTimeMillis())
                .kind(heartbeat ? LogEntry.HEARTBEAT : LogEntry.REQUEST)
                .method(request.getMethod())
                .path(path)
                .query(maskQuery(request.getQueryString()))
                .status(status)
                .durationMs(durationMs)
                .ip(ip)
                .username(username)
                .userEmail(email)
                .visitorId(session == null ? null : session.getVisitorId())
                .sessionId(session == null ? null : session.getSessionId())
                .userAgent(ua)
                .browser(ClientInfo.browser(ua))
                .os(ClientInfo.os(ua))
                .device(ClientInfo.device(ua))
                .country(g == null ? null : g.country())
                .city(g == null ? null : g.city())
                .label(session == null ? null : session.getPath())
                .detail(request.getHeader("Referer"))
                .build());
    }

    /** Identity from a Bearer token (cached per token; invalid tokens are remembered as anonymous). */
    private Identity identity(String header) {
        if (header == null || !header.startsWith("Bearer ")) return null;
        String token = header.substring(7).trim();
        if (token.isEmpty() || "null".equals(token)) return null;
        Identity cached = tokenUsers.get(token);
        if (cached != null) return cached == Identity.NONE ? null : cached;
        Identity found = Identity.NONE;
        try {
            if (jwt.validateToken(token)) {
                String email = null;
                try {
                    email = jwt.getEmail(token);
                } catch (RuntimeException ignored) {
                    // token without an email claim
                }
                found = new Identity(jwt.getUsername(token), email);
            }
        } catch (RuntimeException ignored) {
            // invalid / expired token
        }
        if (tokenUsers.size() > TOKEN_CACHE_MAX) tokenUsers.clear();
        tokenUsers.put(token, found);
        return found == Identity.NONE ? null : found;
    }

    /** Never store secrets that arrive in query strings (stream tickets, tokens, passwords). */
    static String maskQuery(String query) {
        if (query == null) return null;
        return query.replaceAll("(?i)(ticket|token|access_token|password|key)=[^&]*", "$1=***");
    }
}
