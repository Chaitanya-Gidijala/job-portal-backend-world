package com.job.portal.service.impl;

import com.job.portal.dto.TopicDto;
import com.job.portal.entity.Topic;
import com.job.portal.exception.ResourceNotFoundException;
import com.job.portal.repository.TopicRepository;
import com.job.portal.service.TopicService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.modelmapper.ModelMapper;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class TopicServiceImpl implements TopicService {

    private final TopicRepository repository;
    private final ModelMapper modelMapper;

    @Override
    @Transactional
    @CacheEvict(value = "topics", allEntries = true)
    public TopicDto create(TopicDto dto) {
        Topic entity = modelMapper.map(dto, Topic.class);
        Topic saved = repository.save(entity);
        return modelMapper.map(saved, TopicDto.class);
    }

    @Override
    @Transactional
    @CacheEvict(value = "topics", allEntries = true)
    public List<TopicDto> createBatch(List<TopicDto> dtos) {
        List<Topic> entities = dtos.stream()
                .map(dto -> modelMapper.map(dto, Topic.class))
                .collect(Collectors.toList());
        List<Topic> saved = repository.saveAll(entities);
        return saved.stream()
                .map(entity -> modelMapper.map(entity, TopicDto.class))
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    @CacheEvict(value = "topics", allEntries = true)
    public TopicDto update(String id, TopicDto dto) {
        Topic existing = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Topic", "id", id));
        existing.setName(dto.getName());
        existing.setIcon(dto.getIcon());
        Topic updated = repository.save(existing);
        return modelMapper.map(updated, TopicDto.class);
    }

    @Override
    @Transactional
    @CacheEvict(value = "topics", allEntries = true)
    public void delete(String id) {
        if (!repository.existsById(id)) {
            throw new ResourceNotFoundException("Topic", "id", id);
        }
        repository.deleteById(id);
    }

    @Override
    @Cacheable(value = "topics", key = "#id")
    public TopicDto getById(String id) {
        Topic entity = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Topic", "id", id));
        return modelMapper.map(entity, TopicDto.class);
    }

    @Override
    @Cacheable(value = "topics", key = "'all'")
    public List<TopicDto> getAll() {
        return repository.findAll().stream()
                .map(entity -> modelMapper.map(entity, TopicDto.class))
                .collect(Collectors.toList());
    }
}
