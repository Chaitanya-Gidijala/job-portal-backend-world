package com.job.portal.growth;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/** A signed-in learner's progress (streak days, XP, roadmap ticks, puzzles), synced across devices. */
@Data
@Entity
@NoArgsConstructor
@Table(name = "user_progress")
public class UserProgress {

    @Id
    @Column(length = 120)
    private String username;

    @Column(length = 160)
    private String email;

    /** Progress JSON (same shape as the browser's ctw-progress-v1, minus the device learner id). */
    @Column(length = 100000)
    private String data;

    @Column(name = "updated_at")
    private Instant updatedAt;
}
