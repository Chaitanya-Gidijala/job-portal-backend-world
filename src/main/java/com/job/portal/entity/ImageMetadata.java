package com.job.portal.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "image_metadata")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ImageMetadata {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 255)
    private String filename;

    @Column(nullable = false, length = 1000)
    private String githubUrl;

    @Column(nullable = false, length = 1000)
    private String githubPath;
    
    @Column(length = 255)
    private String githubSha; // Store SHA for deletion

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime uploadDate;
}
