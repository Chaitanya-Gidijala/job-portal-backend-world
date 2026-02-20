package com.job.portal.dto;

import com.job.portal.entity.LearningResource;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

@Data
public class LearningResourceDto {
    private Long id;

    @NotBlank(message = "Title is required")
    private String title;

    @NotBlank(message = "URL is required")
    private String url;

    @NotNull(message = "Resource type is required")
    private LearningResource.ResourceType type;

    private String description;
    private List<String> tags;
    private Long jobId;
}
