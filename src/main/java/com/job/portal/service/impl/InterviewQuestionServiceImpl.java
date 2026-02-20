package com.job.portal.service.impl;

import com.job.portal.dto.InterviewQuestionDto;
import com.job.portal.entity.InterviewQuestion;
import com.job.portal.exception.ResourceNotFoundException;
import com.job.portal.repository.InterviewQuestionRepository;
import com.job.portal.service.InterviewQuestionService;
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
public class InterviewQuestionServiceImpl implements InterviewQuestionService {

    private final InterviewQuestionRepository repository;
    private final ModelMapper modelMapper;

    @Override
    @Transactional
    public InterviewQuestionDto create(InterviewQuestionDto dto) {
        log.info("Creating interview question: {}", dto.getQuestion());
        InterviewQuestion entity = modelMapper.map(dto, InterviewQuestion.class);
        InterviewQuestion saved = repository.save(entity);
        return modelMapper.map(saved, InterviewQuestionDto.class);
    }

    @Override
    @Transactional
    public List<InterviewQuestionDto> createBatch(List<InterviewQuestionDto> dtos) {
        log.info("Batch creating {} interview questions", dtos.size());
        List<InterviewQuestion> entities = dtos.stream()
                .map(dto -> modelMapper.map(dto, InterviewQuestion.class))
                .collect(Collectors.toList());
        List<InterviewQuestion> saved = repository.saveAll(entities);
        return saved.stream()
                .map(entity -> modelMapper.map(entity, InterviewQuestionDto.class))
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    @CacheEvict(value = "interview_questions", key = "#id")
    public InterviewQuestionDto update(Long id, InterviewQuestionDto dto) {
        log.info("Updating interview question with id: {}", id);
        InterviewQuestion existing = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("InterviewQuestion", "id", id));

        existing.setQuestion(dto.getQuestion());
        existing.setAnswer(dto.getAnswer());
        existing.setDifficulty(dto.getDifficulty());
        existing.setTags(dto.getTags());
        existing.setJobId(dto.getJobId());

        InterviewQuestion updated = repository.save(existing);
        return modelMapper.map(updated, InterviewQuestionDto.class);
    }

    @Override
    @Transactional
    @CacheEvict(value = "interview_questions", key = "#id")
    public void delete(Long id) {
        log.info("Deleting interview question with id: {}", id);
        if (!repository.existsById(id)) {
            throw new ResourceNotFoundException("InterviewQuestion", "id", id);
        }
        repository.deleteById(id);
    }

    @Override
    @Cacheable(value = "interview_questions", key = "#id")
    public InterviewQuestionDto getById(Long id) {
        log.info("Fetching interview question with id: {}", id);
        InterviewQuestion entity = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("InterviewQuestion", "id", id));
        return modelMapper.map(entity, InterviewQuestionDto.class);
    }

    @Override
    public Page<InterviewQuestionDto> getAll(Pageable pageable) {
        log.debug("Fetching all interview questions");
        return repository.findAll(pageable)
                .map(entity -> modelMapper.map(entity, InterviewQuestionDto.class));
    }

    @Override
    public Page<InterviewQuestionDto> getByDifficulty(InterviewQuestion.Difficulty difficulty, Pageable pageable) {
        return repository.findByDifficulty(difficulty, pageable)
                .map(entity -> modelMapper.map(entity, InterviewQuestionDto.class));
    }

    @Override
    public Page<InterviewQuestionDto> getByJobId(Long jobId, Pageable pageable) {
        return repository.findByJobId(jobId, pageable)
                .map(entity -> modelMapper.map(entity, InterviewQuestionDto.class));
    }

    @Override
    public Page<InterviewQuestionDto> searchByTag(String tag, Pageable pageable) {
        return repository.findByTagsContaining(tag, pageable)
                .map(entity -> modelMapper.map(entity, InterviewQuestionDto.class));
    }

    @Override
    public Page<InterviewQuestionDto> search(String tag, InterviewQuestion.Difficulty difficulty, Pageable pageable) {
        log.info("Searching interview questions with tag: {} and difficulty: {}", tag, difficulty);
        Page<InterviewQuestion> result;

        if (tag != null && difficulty != null) {
            result = repository.findByTagsContainingAndDifficulty(tag, difficulty, pageable);
        } else if (tag != null) {
            result = repository.findByTagsContaining(tag, pageable);
        } else if (difficulty != null) {
            result = repository.findByDifficulty(difficulty, pageable);
        } else {
            result = repository.findAll(pageable);
        }

        return result.map(entity -> modelMapper.map(entity, InterviewQuestionDto.class));
    }
}
