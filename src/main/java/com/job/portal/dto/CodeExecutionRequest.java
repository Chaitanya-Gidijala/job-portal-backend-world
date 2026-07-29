package com.job.portal.dto;

import lombok.Data;
import java.util.List;

@Data
public class CodeExecutionRequest {
    private String language;
    private String code;
    private String fullCode;
    private List<TestCase> testCases;
    
    @Data
    public static class TestCase {
        private String input;
        private String expected;
    }
}
