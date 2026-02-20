package com.job.portal.controller;

import com.job.portal.dto.TopicDto;
import com.job.portal.service.TopicService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/topics")
@RequiredArgsConstructor
@Slf4j
public class TopicController {

    private final TopicService service;

    @PostMapping
    @org.springframework.security.access.prepost.PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<TopicDto> create(@Valid @RequestBody TopicDto dto) {
        log.info("REST request to create Topic");
        return new ResponseEntity<>(service.create(dto), HttpStatus.CREATED);
    }

    @PostMapping("/batch")
    @org.springframework.security.access.prepost.PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<TopicDto>> createBatch(@Valid @RequestBody List<TopicDto> dtos) {
        log.info("REST request to create batch of Topics");
        return new ResponseEntity<>(service.createBatch(dtos), HttpStatus.CREATED);
    }

    @PutMapping("/{id}")
    @org.springframework.security.access.prepost.PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<TopicDto> update(@PathVariable String id, @Valid @RequestBody TopicDto dto) {
        log.info("REST request to update Topic: {}", id);
        return ResponseEntity.ok(service.update(id, dto));
    }

    @DeleteMapping("/{id}")
    @org.springframework.security.access.prepost.PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        log.info("REST request to delete Topic: {}", id);
        service.delete(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}")
    public ResponseEntity<TopicDto> getById(@PathVariable String id) {
        log.info("REST request to get Topic: {}", id);
        return ResponseEntity.ok(service.getById(id));
    }

    @GetMapping
    public ResponseEntity<List<TopicDto>> getAll() {
        log.info("REST request to get all Topics");
        return ResponseEntity.ok(service.getAll());
    }
}
