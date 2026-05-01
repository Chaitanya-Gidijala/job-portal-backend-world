package com.job.portal.service.impl;

import com.job.portal.dto.QuizDto;
import com.job.portal.entity.Quiz;
import com.job.portal.entity.QuizQuestion;
import com.job.portal.exception.ResourceNotFoundException;
import com.job.portal.repository.QuizRepository;
import com.job.portal.service.QuizService;
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
public class QuizServiceImpl implements QuizService {

    private final QuizRepository repository;
    private final ModelMapper modelMapper;

    @Override
    @Transactional
    public QuizDto create(QuizDto dto) {
        log.info("Creating quiz: {}", dto.getTitle());
        Quiz entity = modelMapper.map(dto, Quiz.class);
        if (entity.getQuestions() != null) {
            entity.getQuestions().forEach(q -> {
                q.setQuiz(entity);
                if (q.getDifficulty() == null) {
                    q.setDifficulty(QuizQuestion.Difficulty.INTERMEDIATE);
                }
            });
        }
        Quiz saved = repository.save(entity);
        return modelMapper.map(saved, QuizDto.class);
    }

    @Override
    @Transactional
    public List<QuizDto> createBatch(List<QuizDto> dtos) {
        log.info("Batch creating {} quizzes", dtos.size());
        List<Quiz> entities = dtos.stream()
                .map(dto -> {
                    Quiz entity = modelMapper.map(dto, Quiz.class);
                    if (entity.getQuestions() != null) {
                        entity.getQuestions().forEach(q -> {
                            q.setQuiz(entity);
                            if (q.getDifficulty() == null) {
                                q.setDifficulty(QuizQuestion.Difficulty.INTERMEDIATE);
                            }
                        });
                    }
                    return entity;
                })
                .collect(Collectors.toList());
        List<Quiz> saved = repository.saveAll(entities);
        return saved.stream()
                .map(entity -> modelMapper.map(entity, QuizDto.class))
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    @CacheEvict(value = "quizzes", key = "#id")
    public QuizDto update(Long id, QuizDto dto) {
        log.info("Updating quiz with id: {}", id);
        Quiz existing = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Quiz", "id", id));

        existing.setTitle(dto.getTitle());
        existing.setDuration(dto.getDuration());
        existing.setTotalQuestions(dto.getTotalQuestions());
        existing.setTags(dto.getTags());
        existing.setJobId(dto.getJobId());

        Quiz updated = repository.save(existing);
        return modelMapper.map(updated, QuizDto.class);
    }

    @Override
    @Transactional
    @CacheEvict(value = "quizzes", key = "#id")
    public void delete(Long id) {
        if (!repository.existsById(id)) {
            throw new ResourceNotFoundException("Quiz", "id", id);
        }
        repository.deleteById(id);
    }

    @Override
    @Transactional(readOnly = true)
    @Cacheable(value = "quizzes", key = "#id")
    public QuizDto getById(Long id) {
        Quiz entity = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Quiz", "id", id));
        return modelMapper.map(entity, QuizDto.class);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<QuizDto> getAll(Pageable pageable) {
        return repository.findAll(pageable)
                .map(entity -> modelMapper.map(entity, QuizDto.class));
    }

    @Override
    @Transactional(readOnly = true)
    public Page<QuizDto> getByJobId(Long jobId, Pageable pageable) {
        return repository.findByJobId(jobId, pageable)
                .map(entity -> modelMapper.map(entity, QuizDto.class));
    }

    @Override
    @Transactional(readOnly = true)
    public Page<QuizDto> searchByTag(String tag, Pageable pageable) {
        return repository.findByTagsContaining(tag, pageable)
                .map(entity -> modelMapper.map(entity, QuizDto.class));
    }
}
