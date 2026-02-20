package com.job.portal.dto;

import com.job.portal.entity.QuizQuestion;
import lombok.Data;

import java.util.List;

@Data
public class QuizQuestionDto {
    private Long id;
    private String question;
    private List<String> options;
    private String correctAnswer;
    private QuizQuestion.Difficulty difficulty;
}
