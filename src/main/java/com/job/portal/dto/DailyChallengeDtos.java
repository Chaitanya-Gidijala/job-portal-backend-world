package com.job.portal.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

import java.util.List;

/** Request / response payloads for the Traceflow Daily endpoints. */
public final class DailyChallengeDtos {

    private DailyChallengeDtos() {
    }

    public record Submission(
            @NotBlank
            @Pattern(regexp = "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$",
                    message = "learnerId must be a UUID")
            String learnerId,

            @NotBlank
            @Pattern(regexp = "^[01]{3}$", message = "answers must be three 0/1 flags")
            String answers,

            @Min(0) @Max(3)
            int score,

            @Min(1_000) @Max(3_600_000)
            long durationMs) {
    }

    /**
     * Live community stats for one puzzle.
     *
     * @param distribution players per score, index 0–3
     * @param liveNow      distinct learners connected to the live stream right now
     */
    public record Stats(
            int day,
            long completions,
            List<Long> distribution,
            double averageScore,
            long averageDurationMs,
            int liveNow,
            long updatedAt) {
    }

    /**
     * @param percentile share of other players this result beat (0–100)
     * @param alreadySubmitted true when the learner had already submitted this puzzle
     */
    public record SubmissionResult(Stats stats, double percentile, long rank, boolean alreadySubmitted) {
    }
}
