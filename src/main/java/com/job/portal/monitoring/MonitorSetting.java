package com.job.portal.monitoring;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Admin-editable key/value setting (e.g. visit-notification preferences). */
@Data
@Entity
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "monitor_settings")
public class MonitorSetting {

    @Id
    @Column(name = "setting_key", length = 80)
    private String key;

    @Column(name = "setting_value", length = 2000)
    private String value;
}
