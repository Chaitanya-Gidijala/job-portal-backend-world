package com.job.portal.service;

import com.job.portal.dto.DailyChallengeDtos.Stats;
import com.job.portal.dto.DailyChallengeDtos.Submission;
import com.job.portal.dto.DailyChallengeDtos.SubmissionResult;
import com.job.portal.entity.DailyChallengeResult;
import com.job.portal.repository.DailyChallengeResultRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DailyChallengeServiceTest {

    private static final String LEARNER = "3f2b8c1e-9a4d-4b7e-8c21-5d6e7f8a9b0c";

    @Mock
    private DailyChallengeResultRepository repository;

    private DailyChallengeLiveRegistry live;
    private DailyChallengeService service;

    @BeforeEach
    void setUp() {
        live = new DailyChallengeLiveRegistry();
        // 2026-11-20T10:00Z → puzzle #7
        Clock clock = Clock.fixed(Instant.parse("2026-11-20T10:00:00Z"), ZoneOffset.UTC);
        service = new DailyChallengeService(repository, live, clock);
        service.wirePresence();
    }

    @Test
    void currentDay_countsFromEpoch() {
        assertEquals(7, service.currentDay());
    }

    @Test
    void isPlayable_acceptsOneDayEitherSideOfUtc() {
        assertTrue(service.isPlayable(6));
        assertTrue(service.isPlayable(7));
        assertTrue(service.isPlayable(8));
        assertFalse(service.isPlayable(5));
        assertFalse(service.isPlayable(9));
        assertFalse(service.isPlayable(0));
    }

    @Test
    void submit_rejectsClosedPuzzle() {
        assertThrows(IllegalArgumentException.class,
                () -> service.submit(3, new Submission(LEARNER, "111", 3, 60_000)));
        verify(repository, never()).save(any());
    }

    @Test
    void submit_rejectsScoreThatDoesNotMatchAnswers() {
        assertThrows(IllegalArgumentException.class,
                () -> service.submit(7, new Submission(LEARNER, "101", 3, 60_000)));
    }

    @Test
    void submit_savesAndUpdatesAggregate() {
        when(repository.aggregateByScore(7)).thenReturn(List.<Object[]>of(new Object[]{2, 3L, 300_000L}));
        when(repository.findByChallengeDayAndLearnerId(7, LEARNER)).thenReturn(Optional.empty());
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(repository.countBeaten(7, 3, 45_000)).thenReturn(3L);

        SubmissionResult result = service.submit(7, new Submission(LEARNER.toUpperCase(), "111", 3, 45_000));

        Stats stats = result.stats();
        assertFalse(result.alreadySubmitted());
        assertEquals(4, stats.completions());
        assertEquals(List.of(0L, 0L, 3L, 1L), stats.distribution());
        assertEquals(2.25, stats.averageScore());
        assertEquals((300_000 + 45_000) / 4, stats.averageDurationMs());
        assertEquals(100.0, result.percentile());
        assertEquals(1, result.rank());
        verify(repository).save(argThat(r -> r.getLearnerId().equals(LEARNER) && r.getScore() == 3));
    }

    @Test
    void submit_isIdempotentPerLearner() {
        DailyChallengeResult existing = DailyChallengeResult.builder()
                .challengeDay(7).learnerId(LEARNER).score(1).answers("100").durationMs(90_000).build();
        when(repository.aggregateByScore(7)).thenReturn(List.<Object[]>of(new Object[]{1, 2L, 180_000L}));
        when(repository.findByChallengeDayAndLearnerId(7, LEARNER)).thenReturn(Optional.of(existing));
        when(repository.countBeaten(7, 1, 90_000)).thenReturn(0L);

        SubmissionResult result = service.submit(7, new Submission(LEARNER, "111", 3, 10_000));

        assertTrue(result.alreadySubmitted());
        assertEquals(2, result.stats().completions());
        assertEquals(0.0, result.percentile());
        verify(repository, never()).save(any());
    }

    @Test
    void stats_reportsDistinctLiveLearners() {
        when(repository.aggregateByScore(7)).thenReturn(List.of());
        live.subscribe(7, "a");
        live.subscribe(7, "a"); // same learner, second tab
        live.subscribe(7, "b");
        live.subscribe(6, "c"); // different puzzle

        assertEquals(2, service.stats(7).liveNow());
        assertEquals(4, live.connectionCount());
    }
}
