package com.job.portal.controller;

import com.job.portal.dto.LearningResourceDto;
import com.job.portal.entity.LearningResource;
import com.job.portal.service.LearningResourceService;
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
@RequestMapping("/api/learning-resources")
@RequiredArgsConstructor
@Slf4j
public class LearningResourceController {

    private final LearningResourceService service;

    @PostMapping
    @org.springframework.security.access.prepost.PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<LearningResourceDto> create(@Valid @RequestBody LearningResourceDto dto) {
        log.info("REST request to create LearningResource");
        return new ResponseEntity<>(service.create(dto), HttpStatus.CREATED);
    }

    @PostMapping("/batch")
    @org.springframework.security.access.prepost.PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<LearningResourceDto>> createBatch(@Valid @RequestBody List<LearningResourceDto> dtos) {
        log.info("REST request to create batch of LearningResources");
        return new ResponseEntity<>(service.createBatch(dtos), HttpStatus.CREATED);
    }

    @PutMapping("/{id}")
    @org.springframework.security.access.prepost.PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<LearningResourceDto> update(@PathVariable Long id,
            @Valid @RequestBody LearningResourceDto dto) {
        log.info("REST request to update LearningResource: {}", id);
        return ResponseEntity.ok(service.update(id, dto));
    }

    @DeleteMapping("/{id}")
    @org.springframework.security.access.prepost.PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        log.info("REST request to delete LearningResource: {}", id);
        service.delete(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}")
    public ResponseEntity<LearningResourceDto> getById(@PathVariable Long id) {
        log.info("REST request to get LearningResource: {}", id);
        return ResponseEntity.ok(service.getById(id));
    }

    @GetMapping
    public ResponseEntity<Page<LearningResourceDto>> getAll(
            @PageableDefault(sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        log.info("REST request to get all LearningResources");
        return ResponseEntity.ok(service.getAll(pageable));
    }

    @GetMapping("/job/{jobId}")
    public ResponseEntity<Page<LearningResourceDto>> getByJobId(
            @PathVariable Long jobId,
            @PageableDefault(sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        log.info("REST request to get LearningResources for Job: {}", jobId);
        return ResponseEntity.ok(service.getByJobId(jobId, pageable));
    }

    @GetMapping("/type/{type}")
    public ResponseEntity<Page<LearningResourceDto>> getByType(
            @PathVariable LearningResource.ResourceType type,
            @PageableDefault(sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        log.info("REST request to get LearningResources by type: {}", type);
        return ResponseEntity.ok(service.getByType(type, pageable));
    }

    @GetMapping("/search")
    public ResponseEntity<Page<LearningResourceDto>> searchByTag(
            @RequestParam String tag,
            @PageableDefault(sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        log.info("REST request to search LearningResources by tag: {}", tag);
        return ResponseEntity.ok(service.searchByTag(tag, pageable));
    }
}
