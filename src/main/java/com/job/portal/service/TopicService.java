package com.job.portal.service;

import com.job.portal.dto.TopicDto;

import java.util.List;

public interface TopicService {
    TopicDto create(TopicDto dto);

    List<TopicDto> createBatch(List<TopicDto> dtos);

    TopicDto update(String id, TopicDto dto);

    void delete(String id);

    TopicDto getById(String id);

    List<TopicDto> getAll();
}
