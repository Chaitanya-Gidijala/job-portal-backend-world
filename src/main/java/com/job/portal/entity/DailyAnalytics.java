package com.job.portal.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "daily_analytics")
public class DailyAnalytics {

    @Id
    @Column(name = "date_id")
    private LocalDate dateId;

    @Column(name = "total_views")
    private long totalViews;

    @Column(name = "unique_visitors")
    private long uniqueVisitors;

    @Column(name = "user_logins")
    private long userLogins;
}
