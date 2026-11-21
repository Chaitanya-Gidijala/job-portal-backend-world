package com.job.portal.growth;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/** A verifiable achievement certificate (public page /certificate/{code}). */
@Data
@Entity
@NoArgsConstructor
@Table(name = "certificates", indexes = @Index(name = "idx_certificates_username", columnList = "username"))
public class Certificate {

    @Id
    @Column(length = 16)
    private String code;

    /** ROADMAP_JAVA, ROADMAP_FRONTEND, PUZZLE_STREAK_7, PUZZLES_SOLVED_10, … */
    @Column(nullable = false, length = 40)
    private String kind;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(length = 500)
    private String description;

    @Column(name = "recipient_name", nullable = false, length = 120)
    private String recipientName;

    @Column(length = 120)
    private String username;

    @Column(length = 160)
    private String email;

    @Column(name = "issued_at", nullable = false)
    private Instant issuedAt;
}
