package com.job.portal.service;

import com.job.portal.dto.QuizDto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface QuizService {
    QuizDto create(QuizDto dto);

    QuizDto update(Long id, QuizDto dto);

    void delete(Long id);

    QuizDto getById(Long id);

    Page<QuizDto> getAll(Pageable pageable);

    Page<QuizDto> getByJobId(Long jobId, Pageable pageable);

    Page<QuizDto> searchByTag(String tag, Pageable pageable);
}
