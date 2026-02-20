package com.job.portal.dto;

import com.job.portal.entity.InterviewQuestion;
import lombok.Data;

import java.util.List;

@Data
public class InterviewQuestionDto {
    private Long id;
    private String question;
    private String answer;
    private InterviewQuestion.Difficulty difficulty;
    private List<String> tags;
    private Long jobId;
}
