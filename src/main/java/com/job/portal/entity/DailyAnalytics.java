package com.job.portal.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.AccessLevel;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;
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

    /*
     * Counters are nullable wrappers because older production rows contain NULLs
     * (columns were added after rows existed). Loading NULL into a primitive made
     * Hibernate throw; the accessors below read NULL as 0.
     */
    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    @Column(name = "total_views")
    private Long totalViews;

    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    @Column(name = "unique_visitors")
    private Long uniqueVisitors;

    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    @Column(name = "user_logins")
    private Long userLogins;

    public long getTotalViews() {
        return totalViews == null ? 0 : totalViews;
    }

    public void setTotalViews(long value) {
        this.totalViews = value;
    }

    public long getUniqueVisitors() {
        return uniqueVisitors == null ? 0 : uniqueVisitors;
    }

    public void setUniqueVisitors(long value) {
        this.uniqueVisitors = value;
    }

    public long getUserLogins() {
        return userLogins == null ? 0 : userLogins;
    }

    public void setUserLogins(long value) {
        this.userLogins = value;
    }
}
