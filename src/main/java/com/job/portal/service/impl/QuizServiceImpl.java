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
        return convertToDto(saved);
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
                .map(this::convertToDto)
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
        return convertToDto(updated);
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
        return convertToDto(entity);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<QuizDto> getAll(Pageable pageable) {
        return repository.findAll(pageable)
                .map(this::convertToDto);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<QuizDto> getByJobId(Long jobId, Pageable pageable) {
        return repository.findByJobId(jobId, pageable)
                .map(this::convertToDto);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<QuizDto> searchByTag(String tag, Pageable pageable) {
        return repository.findByTagsContaining(tag, pageable)
                .map(this::convertToDto);
    }

    private QuizDto convertToDto(Quiz entity) {
        QuizDto dto = modelMapper.map(entity, QuizDto.class);
        if (entity.getTags() != null) {
            dto.setTags(entity.getTags().stream().collect(Collectors.toList()));
        }
        if (entity.getQuestions() != null) {
            // If the DTO has questions, we should map them too
            // Note: This assumes QuizDto has a List<QuizQuestionDto>
            // We just let ModelMapper handle the list element mapping but the outer collection is now initialized
        }
        return dto;
    }
}
