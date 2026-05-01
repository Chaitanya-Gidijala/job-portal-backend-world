package com.job.portal.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Entity
@Table(name = "user_resumes")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserResume {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_email", nullable = false)
    private String userEmail;

    @Column(name = "resume_id", nullable = false)
    private String resumeId;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "template", nullable = false)
    private String template;

    @Column(name = "type", nullable = false)
    private String type;

    @Column(name = "content", columnDefinition = "TEXT")
    private String content;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
}
