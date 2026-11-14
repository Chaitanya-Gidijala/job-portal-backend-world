package com.job.portal.monitoring;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/** One row per (day, visitor) — makes "unique visitors today" exact and restart-safe. */
@Data
@Entity
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "monitor_visitor_days",
        uniqueConstraints = @UniqueConstraint(name = "uk_visitor_day", columnNames = {"visit_day", "visitor_id"}))
public class VisitorDay {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Column is not called "day": that is a reserved word in H2 / SQL:2016. */
    @Column(name = "visit_day", nullable = false)
    private LocalDate day;

    @Column(name = "visitor_id", nullable = false, length = 64)
    private String visitorId;

    public VisitorDay(LocalDate day, String visitorId) {
        this.day = day;
        this.visitorId = visitorId;
    }
}
