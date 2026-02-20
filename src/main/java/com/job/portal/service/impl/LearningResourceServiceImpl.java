package com.job.portal.service.impl;

import com.job.portal.dto.LearningResourceDto;
import com.job.portal.entity.LearningResource;
import com.job.portal.exception.ResourceNotFoundException;
import com.job.portal.repository.LearningResourceRepository;
import com.job.portal.service.LearningResourceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.modelmapper.ModelMapper;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class LearningResourceServiceImpl implements LearningResourceService {

    private final LearningResourceRepository repository;
    private final ModelMapper modelMapper;

    @Override
    @Transactional
    public LearningResourceDto create(LearningResourceDto dto) {
        log.info("Creating learning resource: {}", dto.getTitle());
        LearningResource entity = modelMapper.map(dto, LearningResource.class);
        LearningResource saved = repository.save(entity);
        return modelMapper.map(saved, LearningResourceDto.class);
    }

    @Override
    @Transactional
    public List<LearningResourceDto> createBatch(List<LearningResourceDto> dtos) {
        log.info("Batch creating {} learning resources", dtos.size());
        List<LearningResource> entities = dtos.stream()
                .map(dto -> modelMapper.map(dto, LearningResource.class))
                .collect(Collectors.toList());
        List<LearningResource> saved = repository.saveAll(entities);
        return saved.stream()
                .map(entity -> modelMapper.map(entity, LearningResourceDto.class))
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    @CacheEvict(value = "learning_resources", key = "#id")
    public LearningResourceDto update(Long id, LearningResourceDto dto) {
        log.info("Updating learning resource with id: {}", id);
        LearningResource existing = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("LearningResource", "id", id));

        log.debug("Found existing resource: {}", existing.getTitle());

        existing.setTitle(dto.getTitle());
        existing.setUrl(dto.getUrl());
        existing.setType(dto.getType());
        existing.setDescription(dto.getDescription());
        existing.setJobId(dto.getJobId());

        // Handle tags collection safely
        if (existing.getTags() == null) {
            existing.setTags(new java.util.ArrayList<>());
        }
        existing.getTags().clear();
        if (dto.getTags() != null) {
            existing.getTags().addAll(dto.getTags());
        }

        try {
            LearningResource updated = repository.save(existing);
            log.info("Successfully saved learning resource with id: {}", id);
            return modelMapper.map(updated, LearningResourceDto.class);
        } catch (Exception e) {
            log.error("Error saving learning resource: {}", e.getMessage(), e);
            throw e;
        }
    }

    @Override
    @Transactional
    @CacheEvict(value = "learning_resources", key = "#id")
    public void delete(Long id) {
        log.info("Deleting learning resource with id: {}", id);
        if (!repository.existsById(id)) {
            throw new ResourceNotFoundException("LearningResource", "id", id);
        }
        repository.deleteById(id);
    }

    @Override
    @Cacheable(value = "learning_resources", key = "#id")
    public LearningResourceDto getById(Long id) {
        log.info("Fetching learning resource with id: {}", id);
        LearningResource entity = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("LearningResource", "id", id));
        return modelMapper.map(entity, LearningResourceDto.class);
    }

    @Override
    public Page<LearningResourceDto> getAll(Pageable pageable) {
        return repository.findAll(pageable)
                .map(entity -> modelMapper.map(entity, LearningResourceDto.class));
    }

    @Override
    public Page<LearningResourceDto> getByType(LearningResource.ResourceType type, Pageable pageable) {
        return repository.findByType(type, pageable)
                .map(entity -> modelMapper.map(entity, LearningResourceDto.class));
    }

    @Override
    public Page<LearningResourceDto> getByJobId(Long jobId, Pageable pageable) {
        return repository.findByJobId(jobId, pageable)
                .map(entity -> modelMapper.map(entity, LearningResourceDto.class));
    }

    @Override
    public Page<LearningResourceDto> searchByTag(String tag, Pageable pageable) {
        return repository.findByTagsContaining(tag, pageable)
                .map(entity -> modelMapper.map(entity, LearningResourceDto.class));
    }
}
