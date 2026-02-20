package com.job.portal.controller;

import com.job.portal.dto.QuizDto;
import com.job.portal.service.QuizService;
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

@RestController
@RequestMapping("/api/quizzes")
@RequiredArgsConstructor
@Slf4j
public class QuizController {

    private final QuizService service;

    @PostMapping
    @org.springframework.security.access.prepost.PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<QuizDto> create(@Valid @RequestBody QuizDto dto) {
        log.info("REST request to create Quiz");
        return new ResponseEntity<>(service.create(dto), HttpStatus.CREATED);
    }

    @PutMapping("/{id}")
    @org.springframework.security.access.prepost.PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<QuizDto> update(@PathVariable Long id, @Valid @RequestBody QuizDto dto) {
        log.info("REST request to update Quiz: {}", id);
        return ResponseEntity.ok(service.update(id, dto));
    }

    @DeleteMapping("/{id}")
    @org.springframework.security.access.prepost.PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        log.info("REST request to delete Quiz: {}", id);
        service.delete(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}")
    public ResponseEntity<QuizDto> getById(@PathVariable Long id) {
        log.info("REST request to get Quiz: {}", id);
        return ResponseEntity.ok(service.getById(id));
    }

    @GetMapping
    public ResponseEntity<Page<QuizDto>> getAll(
            @PageableDefault(sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        log.info("REST request to get all Quizzes");
        return ResponseEntity.ok(service.getAll(pageable));
    }

    @GetMapping("/job/{jobId}")
    public ResponseEntity<Page<QuizDto>> getByJobId(
            @PathVariable Long jobId,
            @PageableDefault(sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        log.info("REST request to get Quizzes for Job: {}", jobId);
        return ResponseEntity.ok(service.getByJobId(jobId, pageable));
    }

    @GetMapping("/search")
    public ResponseEntity<Page<QuizDto>> searchByTag(
            @RequestParam String tag,
            @PageableDefault(sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        log.info("REST request to search Quizzes by tag: {}", tag);
        return ResponseEntity.ok(service.searchByTag(tag, pageable));
    }
}
