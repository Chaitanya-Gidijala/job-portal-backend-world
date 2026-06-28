package com.job.portal.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PromptDTO {

    private Long id;

    @NotBlank(message = "Title is required")
    private String title;

    private String description;

    @NotBlank(message = "Media URL is required")
    private String mediaUrl;

    @NotBlank(message = "Media type is required (PHOTO or VIDEO)")
    private String mediaType;

    private String aiModel; // e.g. "ChatGPT", "Gemini", "Midjourney", "DALL·E"

    @NotBlank(message = "Prompt text is required")
    private String promptText;

    private String category;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
