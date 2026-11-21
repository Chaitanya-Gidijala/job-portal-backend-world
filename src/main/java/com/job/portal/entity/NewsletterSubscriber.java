package com.job.portal.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.time.LocalDateTime;

/**
 * Email subscriber: daily puzzle email and/or weekly summary.
 * Every email carries a one-click unsubscribe link built from {@link #token}.
 */
@Entity
@Table(name = "newsletter_subscribers")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class NewsletterSubscriber {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String email;

    @CreationTimestamp
    private LocalDateTime subscribedAt;

    @Column(length = 120)
    private String name;

    /** Anonymous visitor id at sign-up — links the subscriber to their visitor profile. */
    @Column(name = "visitor_id", length = 64)
    private String visitorId;

    /** Where they subscribed (daily-result, footer, progress …). */
    @Column(length = 60)
    private String source;

    @Column(name = "wants_daily")
    private Boolean wantsDaily;

    @Column(name = "wants_weekly")
    private Boolean wantsWeekly;

    private Boolean active;

    /** Secret for one-click unsubscribe / preference links. */
    @Column(length = 48)
    private String token;

    @Column(name = "last_sent_at")
    private Instant lastSentAt;

    @Column(name = "unsubscribed_at")
    private Instant unsubscribedAt;

    public boolean isActiveSubscriber() {
        return !Boolean.FALSE.equals(active);
    }
}
