package com.job.portal.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.IntConsumer;
import java.util.stream.Collectors;

/**
 * Server-Sent Events channels, one per daily puzzle.
 *
 * Tracks who is connected (presence = distinct learners on a puzzle's channel),
 * pushes `stats` frames, and sends heartbeats so idle proxies keep the stream open.
 * It knows nothing about stats itself — presence changes are reported through
 * {@link #onPresenceChange(IntConsumer)} and the service decides what to broadcast.
 */
@Slf4j
@Component
public class DailyChallengeLiveRegistry {

    /** Browsers reconnect automatically, so a bounded lifetime keeps the pool fresh. */
    static final long EMITTER_TIMEOUT_MS = 15 * 60 * 1000L;
    static final int MAX_CONNECTIONS = 5_000;

    private record Subscriber(int day, String learnerId) {
    }

    private final Map<SseEmitter, Subscriber> subscribers = new ConcurrentHashMap<>();
    private volatile IntConsumer presenceListener = day -> { };

    public void onPresenceChange(IntConsumer listener) {
        this.presenceListener = listener;
    }

    /** Open a stream, or return null when the server is at capacity. */
    public SseEmitter subscribe(int day, String learnerId) {
        if (subscribers.size() >= MAX_CONNECTIONS) {
            return null;
        }
        SseEmitter emitter = new SseEmitter(EMITTER_TIMEOUT_MS);
        String learner = (learnerId == null || learnerId.isBlank()) ? "anon-" + System.identityHashCode(emitter) : learnerId;
        subscribers.put(emitter, new Subscriber(day, learner));

        Runnable cleanup = () -> remove(emitter);
        emitter.onCompletion(cleanup);
        emitter.onTimeout(() -> {
            cleanup.run();
            emitter.complete();
        });
        emitter.onError(e -> cleanup.run());

        presenceListener.accept(day);
        return emitter;
    }

    private void remove(SseEmitter emitter) {
        Subscriber s = subscribers.remove(emitter);
        if (s != null) {
            presenceListener.accept(s.day());
        }
    }

    /** Distinct learners currently watching a puzzle. */
    public int liveCount(int day) {
        Set<String> learners = subscribers.values().stream()
                .filter(s -> s.day() == day)
                .map(Subscriber::learnerId)
                .collect(Collectors.toSet());
        return learners.size();
    }

    public int connectionCount() {
        return subscribers.size();
    }

    /** Send one event to every subscriber of a puzzle. */
    public void broadcast(int day, String eventName, Object payload) {
        subscribers.forEach((emitter, s) -> {
            if (s.day() == day) {
                send(emitter, eventName, payload);
            }
        });
    }

    /** Send one event to a single emitter (e.g. the initial snapshot). */
    public void send(SseEmitter emitter, String eventName, Object payload) {
        try {
            emitter.send(SseEmitter.event().name(eventName).data(payload, MediaType.APPLICATION_JSON));
        } catch (IOException | IllegalStateException e) {
            // Client went away — drop it; presence is recomputed by remove().
            emitter.completeWithError(e);
            remove(emitter);
        }
    }

    /** Comment frame — keeps proxies from closing idle streams and detects dead clients. */
    public void heartbeat() {
        subscribers.keySet().forEach(emitter -> {
            try {
                emitter.send(SseEmitter.event().comment("ping"));
            } catch (IOException | IllegalStateException e) {
                emitter.completeWithError(e);
                remove(emitter);
            }
        });
    }
}
