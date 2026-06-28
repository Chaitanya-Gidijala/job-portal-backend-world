package com.job.portal.controller;

import com.job.portal.dto.ApiResponse;
import com.job.portal.dto.PromptDTO;
import com.job.portal.service.PromptService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
import java.util.List;

@RestController
@RequestMapping("/api/prompts")
public class PromptController {

    @Autowired
    private PromptService promptService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<PromptDTO>>> getAllPrompts() {
        List<PromptDTO> prompts = promptService.getAllPrompts();
        if (prompts.isEmpty()) {
            return ResponseEntity.ok(ApiResponse.success("No prompts found", Collections.emptyList()));
        }
        return ResponseEntity.ok(ApiResponse.success("Prompts fetched successfully", prompts));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<PromptDTO>> getPrompt(@PathVariable Long id) {
        try {
            PromptDTO promptDTO = promptService.getPromptById(id);
            return ResponseEntity.ok(ApiResponse.success("Prompt fetched successfully", promptDTO));
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error("Prompt not found with id: " + id, null));
        }
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<PromptDTO>> createPrompt(@Valid @RequestBody PromptDTO promptDTO) {
        PromptDTO savedPrompt = promptService.savePrompt(promptDTO);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Prompt created successfully", savedPrompt));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<PromptDTO>> updatePrompt(@PathVariable Long id, @Valid @RequestBody PromptDTO promptDTO) {
        try {
            PromptDTO updatedPrompt = promptService.updatePrompt(id, promptDTO);
            return ResponseEntity.ok(ApiResponse.success("Prompt updated successfully", updatedPrompt));
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error("Prompt not found with id: " + id, null));
        }
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> deletePrompt(@PathVariable Long id) {
        try {
            promptService.deletePrompt(id);
            return ResponseEntity.ok(ApiResponse.success("Prompt deleted successfully", null));
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error("Prompt not found with id: " + id, null));
        }
    }
}
