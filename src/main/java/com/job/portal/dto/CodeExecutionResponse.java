package com.job.portal.dto;

import lombok.Builder;
import lombok.Data;
import java.util.List;

@Data
@Builder
public class CodeExecutionResponse {
    private String status;
    private String message;
    private String runtime;
    private String memory;
    private int passed;
    private int total;
    private List<CaseResult> caseResults;

    @Data
    @Builder
    public static class CaseResult {
        private String input;
        private String expected;
        private String actualOutput;
        private boolean passed;
    }
}
