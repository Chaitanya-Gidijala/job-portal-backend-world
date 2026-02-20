package com.job.portal.service;

import com.job.portal.dto.InterviewQuestionDto;
import com.job.portal.entity.InterviewQuestion;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface InterviewQuestionService {
    InterviewQuestionDto create(InterviewQuestionDto dto);

    List<InterviewQuestionDto> createBatch(List<InterviewQuestionDto> dtos);

    InterviewQuestionDto update(Long id, InterviewQuestionDto dto);

    void delete(Long id);

    InterviewQuestionDto getById(Long id);

    Page<InterviewQuestionDto> getAll(Pageable pageable);

    Page<InterviewQuestionDto> getByDifficulty(InterviewQuestion.Difficulty difficulty, Pageable pageable);

    Page<InterviewQuestionDto> getByJobId(Long jobId, Pageable pageable);

    Page<InterviewQuestionDto> searchByTag(String tag, Pageable pageable);

    Page<InterviewQuestionDto> search(String tag, InterviewQuestion.Difficulty difficulty, Pageable pageable);
}
