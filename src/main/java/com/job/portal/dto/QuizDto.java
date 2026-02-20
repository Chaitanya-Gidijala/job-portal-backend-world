package com.job.portal.dto;

import lombok.Data;

import java.util.List;

@Data
public class QuizDto {
    private Long id;
    private String title;
    private Integer duration;
    private Integer totalQuestions;
    private List<String> tags;
    private Long jobId;
    private List<QuizQuestionDto> questions;
}
