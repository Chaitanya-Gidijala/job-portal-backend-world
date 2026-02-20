package com.job.portal.controller;

import com.job.portal.dto.InterviewQuestionDto;
import com.job.portal.entity.InterviewQuestion;
import com.job.portal.service.InterviewQuestionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/interview-questions")
@RequiredArgsConstructor
@Slf4j
public class InterviewQuestionController {

    private final InterviewQuestionService service;

    @PostMapping
    @org.springframework.security.access.prepost.PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<InterviewQuestionDto> create(@Valid @RequestBody InterviewQuestionDto dto) {
        log.info("REST request to create InterviewQuestion");
        return new ResponseEntity<>(service.create(dto), HttpStatus.CREATED);
    }

    @PostMapping("/batch")
    @org.springframework.security.access.prepost.PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<InterviewQuestionDto>> createBatch(@Valid @RequestBody List<InterviewQuestionDto> dtos) {
        log.info("REST request to create batch of InterviewQuestions");
        return new ResponseEntity<>(service.createBatch(dtos), HttpStatus.CREATED);
    }

    @PutMapping("/{id}")
    @org.springframework.security.access.prepost.PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<InterviewQuestionDto> update(@PathVariable Long id,
            @Valid @RequestBody InterviewQuestionDto dto) {
        log.info("REST request to update InterviewQuestion: {}", id);
        return ResponseEntity.ok(service.update(id, dto));
    }

    @DeleteMapping("/{id}")
    @org.springframework.security.access.prepost.PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        log.info("REST request to delete InterviewQuestion: {}", id);
        service.delete(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}")
    public ResponseEntity<InterviewQuestionDto> getById(@PathVariable Long id) {
        log.info("REST request to get InterviewQuestion: {}", id);
        return ResponseEntity.ok(service.getById(id));
    }

    @GetMapping
    public ResponseEntity<Page<InterviewQuestionDto>> getAll(
            @PageableDefault(sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        log.info("REST request to get all InterviewQuestions");
        return ResponseEntity.ok(service.getAll(pageable));
    }

    @GetMapping("/job/{jobId}")
    public ResponseEntity<Page<InterviewQuestionDto>> getByJobId(
            @PathVariable Long jobId,
            @PageableDefault(sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        log.info("REST request to get InterviewQuestions for Job: {}", jobId);
        return ResponseEntity.ok(service.getByJobId(jobId, pageable));
    }

    @GetMapping("/difficulty/{difficulty}")
    public ResponseEntity<Page<InterviewQuestionDto>> getByDifficulty(
            @PathVariable InterviewQuestion.Difficulty difficulty,
            @PageableDefault(sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        log.info("REST request to get InterviewQuestions by difficulty: {}", difficulty);
        return ResponseEntity.ok(service.getByDifficulty(difficulty, pageable));
    }

    @GetMapping("/search")
    public ResponseEntity<Page<InterviewQuestionDto>> search(
            @RequestParam(required = false) String tag,
            @RequestParam(required = false) InterviewQuestion.Difficulty difficulty,
            @PageableDefault(sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        log.info("REST request to search InterviewQuestions by tag: {} and difficulty: {}", tag, difficulty);
        return ResponseEntity.ok(service.search(tag, difficulty, pageable));
    }
}
