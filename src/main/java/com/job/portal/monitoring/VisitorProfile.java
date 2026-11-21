package com.job.portal.monitoring;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Permanent record of every visitor (one row per anonymous browser id).
 * Filterable fields are columns; the rest of the client details live in `details` (JSON).
 */
@Data
@Entity
@NoArgsConstructor
@Table(name = "visitor_profiles",
        uniqueConstraints = @UniqueConstraint(name = "uk_visitor_profile", columnNames = "visitor_id"),
        indexes = {
                @Index(name = "idx_visitor_profiles_last_seen", columnList = "last_seen"),
                @Index(name = "idx_visitor_profiles_first_seen", columnList = "first_seen")
        })
public class VisitorProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "visitor_id", nullable = false, length = 64)
    private String visitorId;

    @Column(name = "first_seen")
    private Instant firstSeen;

    @Column(name = "last_seen")
    private Instant lastSeen;

    /** Sessions (separate visits). */
    private Integer visits;

    @Column(name = "page_views")
    private Integer pageViews;

    @Column(name = "landing_page", length = 300)
    private String landingPage;

    @Column(name = "last_page", length = 300)
    private String lastPage;

    /** utm_source, else referrer host, else "Direct". */
    @Column(length = 200)
    private String source;

    @Column(name = "first_referrer", length = 500)
    private String firstReferrer;

    @Column(name = "utm_medium", length = 120)
    private String utmMedium;

    @Column(name = "utm_campaign", length = 120)
    private String utmCampaign;

    @Column(length = 80)
    private String country;

    @Column(name = "country_code", length = 8)
    private String countryCode;

    @Column(length = 120)
    private String region;

    @Column(length = 120)
    private String city;

    @Column(length = 60)
    private String timezone;

    @Column(length = 160)
    private String isp;

    @Column(length = 64)
    private String ip;

    @Column(length = 60)
    private String browser;

    @Column(length = 60)
    private String os;

    @Column(length = 20)
    private String device;

    @Column(length = 20)
    private String language;

    @Column(length = 20)
    private String screen;

    @Column(length = 120)
    private String username;

    @Column(length = 160)
    private String email;

    /** Real name, once the visitor identifies themselves (sign-in, contact form, newsletter). */
    @Column(length = 160)
    private String name;

    /** How we know who they are: google, github, account, contact, newsletter. */
    @Column(name = "identity_source", length = 20)
    private String identitySource;

    @Column(name = "identified_at")
    private Instant identifiedAt;

    private Boolean bot;

    @Column(name = "last_notified_at")
    private Instant lastNotifiedAt;

    /** Remaining client details as JSON (viewport, platform, cores, memory, connection …). */
    @Column(length = 4000)
    private String details;
}
