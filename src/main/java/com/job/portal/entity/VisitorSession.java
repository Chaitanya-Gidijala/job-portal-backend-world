package com.job.portal.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Data
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "visitor_sessions")
public class VisitorSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "page")
    private String page;

    @Column(name = "browser")
    private String browser;

    @Column(name = "os")
    private String os;

    @Column(name = "device")
    private String device;

    @Column(name = "language")
    private String language;

    @Column(name = "referrer")
    private String referrer;

    @Column(name = "ip_address")
    private String ipAddress;

    @Column(name = "user_email")
    private String userEmail;

    @Column(name = "system_info", columnDefinition = "TEXT")
    private String systemInfo;

    @Column(name = "timestamp")
    private LocalDateTime timestamp;
}
