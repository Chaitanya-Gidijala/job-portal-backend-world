package com.job.portal.service;

import com.job.portal.dto.PromptDTO;
import java.util.List;

public interface PromptService {
    List<PromptDTO> getAllPrompts();
    PromptDTO getPromptById(Long id);
    PromptDTO savePrompt(PromptDTO promptDTO);
    PromptDTO updatePrompt(Long id, PromptDTO promptDTO);
    void deletePrompt(Long id);
}
