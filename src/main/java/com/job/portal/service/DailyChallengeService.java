package com.job.portal.service;

import com.job.portal.dto.DailyChallengeDtos.Stats;
import com.job.portal.dto.DailyChallengeDtos.Submission;
import com.job.portal.dto.DailyChallengeDtos.SubmissionResult;
import com.job.portal.entity.DailyChallengeResult;
import com.job.portal.repository.DailyChallengeResultRepository;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Traceflow Daily — community results and live stats.
 *
 * Per-puzzle aggregates are kept in memory (seeded from the database on first use)
 * so stats reads and live broadcasts never hit the DB. Writes are coalesced:
 * a submission or presence change marks the puzzle dirty and a 1s scheduler
 * pushes one `stats` frame to every connected client.
 */
@Slf4j
@Service
public class DailyChallengeService {

    /** Puzzle #1. Must match CHALLENGE_EPOCH in the frontend (src/features/engagement/lib/dates.js). */
    static final LocalDate EPOCH = LocalDate.of(2026, 11, 14);
    static final String STATS_EVENT = "stats";

    private final DailyChallengeResultRepository repository;
    private final DailyChallengeLiveRegistry live;
    private final Clock clock;

    private final Map<Integer, Aggregate> aggregates = new ConcurrentHashMap<>();
    private final Set<Integer> dirty = ConcurrentHashMap.newKeySet();

    @Autowired
    public DailyChallengeService(DailyChallengeResultRepository repository, DailyChallengeLiveRegistry live) {
        this(repository, live, Clock.systemUTC());
    }

    DailyChallengeService(DailyChallengeResultRepository repository, DailyChallengeLiveRegistry live, Clock clock) {
        this.repository = repository;
        this.live = live;
        this.clock = clock;
    }

    @PostConstruct
    void wirePresence() {
        live.onPresenceChange(dirty::add);
    }

    /* ── Puzzle calendar ──────────────────────────────────────────────── */

    /** Puzzle number for today's UTC date. */
    public int currentDay() {
        return (int) ChronoUnit.DAYS.between(EPOCH, LocalDate.now(clock.withZone(ZoneOffset.UTC))) + 1;
    }

    /**
     * Learners play on their local date, which can be one day either side of UTC
     * (UTC−12 … UTC+14), so yesterday / today / tomorrow are all valid.
     */
    public boolean isPlayable(int day) {
        int today = currentDay();
        return day >= 1 && Math.abs(day - today) <= 1;
    }

    /* ── Reads ────────────────────────────────────────────────────────── */

    public Stats stats(int day) {
        return aggregate(day).snapshot(day, live.liveCount(day), clock.millis());
    }

    /* ── Writes ───────────────────────────────────────────────────────── */

    /** Record a result (idempotent per learner and puzzle). */
    public SubmissionResult submit(int day, Submission submission) {
        if (!isPlayable(day)) {
            throw new IllegalArgumentException("Puzzle #" + day + " is not open for submissions");
        }
        int flags = (int) submission.answers().chars().filter(c -> c == '1').count();
        if (flags != submission.score()) {
            throw new IllegalArgumentException("score does not match answers");
        }

        String learnerId = submission.learnerId().toLowerCase();
        Optional<DailyChallengeResult> existing = repository.findByChallengeDayAndLearnerId(day, learnerId);
        if (existing.isPresent()) {
            return resultFor(day, existing.get(), true);
        }

        DailyChallengeResult saved;
        try {
            saved = repository.save(DailyChallengeResult.builder()
                    .challengeDay(day)
                    .learnerId(learnerId)
                    .score(submission.score())
                    .answers(submission.answers())
                    .durationMs(submission.durationMs())
                    .createdAt(Instant.now(clock))
                    .build());
        } catch (DataIntegrityViolationException race) {
            // Same learner submitted concurrently (two tabs) — treat as a duplicate.
            return repository.findByChallengeDayAndLearnerId(day, learnerId)
                    .map(r -> resultFor(day, r, true))
                    .orElseThrow(() -> race);
        }

        aggregate(day).add(saved.getScore(), saved.getDurationMs());
        dirty.add(day);
        return resultFor(day, saved, false);
    }

    private SubmissionResult resultFor(int day, DailyChallengeResult r, boolean duplicate) {
        Stats stats = stats(day);
        long beaten = repository.countBeaten(day, r.getScore(), r.getDurationMs());
        long others = Math.max(0, stats.completions() - 1);
        double percentile = others == 0 ? 100.0 : Math.min(100.0, beaten * 100.0 / others);
        long rank = Math.max(1, stats.completions() - beaten);
        return new SubmissionResult(stats, Math.round(percentile * 10) / 10.0, rank, duplicate);
    }

    /* ── Live stream ──────────────────────────────────────────────────── */

    /** Open a live stream; the first frame is the current snapshot. Null when at capacity. */
    public SseEmitter subscribe(int day, String learnerId) {
        SseEmitter emitter = live.subscribe(day, learnerId);
        if (emitter != null) {
            live.send(emitter, STATS_EVENT, stats(day));
        }
        return emitter;
    }

    /** Coalesced broadcast: at most one frame per puzzle per second. */
    @Scheduled(fixedDelay = 1_000)
    public void flush() {
        if (dirty.isEmpty()) {
            return;
        }
        for (Integer day : List.copyOf(dirty)) {
            dirty.remove(day);
            live.broadcast(day, STATS_EVENT, stats(day));
        }
    }

    @Scheduled(fixedRate = 10_000)
    public void heartbeat() {
        live.heartbeat();
        // Only the current window of puzzles is ever read; drop older aggregates.
        int oldest = currentDay() - 2;
        aggregates.keySet().removeIf(d -> d < oldest);
    }

    /* ── Aggregates ───────────────────────────────────────────────────── */

    private Aggregate aggregate(int day) {
        return aggregates.computeIfAbsent(day, d -> {
            Aggregate agg = new Aggregate();
            for (Object[] row : repository.aggregateByScore(d)) {
                int score = ((Number) row[0]).intValue();
                long count = ((Number) row[1]).longValue();
                long totalMs = row[2] == null ? 0 : ((Number) row[2]).longValue();
                agg.seed(score, count, totalMs);
            }
            return agg;
        });
    }

    /** Thread-safe running totals for one puzzle. */
    static final class Aggregate {
        private final long[] distribution = new long[4];
        private long count;
        private long totalScore;
        private long totalDurationMs;

        synchronized void seed(int score, long n, long totalMs) {
            if (score < 0 || score > 3) {
                return;
            }
            distribution[score] += n;
            count += n;
            totalScore += (long) score * n;
            totalDurationMs += totalMs;
        }

        synchronized void add(int score, long durationMs) {
            seed(score, 1, durationMs);
        }

        synchronized Stats snapshot(int day, int liveNow, long now) {
            double avgScore = count == 0 ? 0 : Math.round(totalScore * 100.0 / count) / 100.0;
            long avgMs = count == 0 ? 0 : totalDurationMs / count;
            return new Stats(day, count,
                    List.of(distribution[0], distribution[1], distribution[2], distribution[3]),
                    avgScore, avgMs, liveNow, now);
        }
    }
}
