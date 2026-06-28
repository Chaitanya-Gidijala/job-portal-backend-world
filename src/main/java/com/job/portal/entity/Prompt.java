package com.job.portal.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "prompts")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Prompt {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(nullable = false)
    private String mediaUrl;

    @Column(nullable = false)
    private String mediaType; // "PHOTO" or "VIDEO"

    @Column(nullable = true)
    private String aiModel; // e.g. "ChatGPT", "Gemini", "Midjourney", "DALL·E", etc.

    @Column(columnDefinition = "TEXT", nullable = false)
    private String promptText;

    @Column(nullable = true)
    private String category; // e.g. "Men", "Women", "Nature", "Birthday"

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;
}
