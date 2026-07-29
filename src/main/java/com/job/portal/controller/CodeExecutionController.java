package com.job.portal.controller;

import com.job.portal.dto.CodeExecutionRequest;
import com.job.portal.dto.CodeExecutionResponse;
import com.job.portal.service.CodeExecutionService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/execute-code")
@CrossOrigin(origins = "*") // Allow frontend to call this endpoint
public class CodeExecutionController {

    private final CodeExecutionService codeExecutionService;

    public CodeExecutionController(CodeExecutionService codeExecutionService) {
        this.codeExecutionService = codeExecutionService;
    }

    @PostMapping
    public ResponseEntity<CodeExecutionResponse> executeCode(@RequestBody CodeExecutionRequest request) {
        CodeExecutionResponse response = codeExecutionService.execute(request);
        return ResponseEntity.ok(response);
    }
}
