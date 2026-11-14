package com.job.portal.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * One finished Traceflow Daily run. A learner (anonymous browser id) can submit
 * at most one result per puzzle — enforced by the unique constraint.
 */
@Data
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "daily_challenge_results",
        uniqueConstraints = @UniqueConstraint(name = "uk_daily_result_learner", columnNames = {"challenge_day", "learner_id"}),
        indexes = @Index(name = "idx_daily_result_day", columnList = "challenge_day"))
public class DailyChallengeResult {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Puzzle number (#1 = 2026-11-14). */
    @Column(name = "challenge_day", nullable = false)
    private int challengeDay;

    @Column(name = "learner_id", nullable = false, length = 36)
    private String learnerId;

    /** 0–3 correct rounds. */
    @Column(nullable = false)
    private int score;

    /** Per-round correctness, e.g. "101". */
    @Column(nullable = false, length = 8)
    private String answers;

    @Column(name = "duration_ms", nullable = false)
    private long durationMs;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}
