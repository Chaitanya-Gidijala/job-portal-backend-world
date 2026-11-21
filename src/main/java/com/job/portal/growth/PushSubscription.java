package com.job.portal.growth;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/** A browser Web Push subscription ("remind me about the daily puzzle"). */
@Data
@Entity
@NoArgsConstructor
@Table(name = "push_subscriptions", uniqueConstraints = @UniqueConstraint(name = "uk_push_endpoint", columnNames = "endpoint"))
public class PushSubscription {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 600)
    private String endpoint;

    @Column(nullable = false, length = 200)
    private String p256dh;

    @Column(nullable = false, length = 100)
    private String auth;

    @Column(name = "visitor_id", length = 64)
    private String visitorId;

    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "last_success_at")
    private Instant lastSuccessAt;

    private Integer failures;
}
