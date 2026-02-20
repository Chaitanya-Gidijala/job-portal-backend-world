package com.job.portal.service;

import com.job.portal.dto.LearningResourceDto;
import com.job.portal.entity.LearningResource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface LearningResourceService {
    LearningResourceDto create(LearningResourceDto dto);

    List<LearningResourceDto> createBatch(List<LearningResourceDto> dtos);

    LearningResourceDto update(Long id, LearningResourceDto dto);

    void delete(Long id);

    LearningResourceDto getById(Long id);

    Page<LearningResourceDto> getAll(Pageable pageable);

    Page<LearningResourceDto> getByType(LearningResource.ResourceType type, Pageable pageable);

    Page<LearningResourceDto> getByJobId(Long jobId, Pageable pageable);

    Page<LearningResourceDto> searchByTag(String tag, Pageable pageable);
}
