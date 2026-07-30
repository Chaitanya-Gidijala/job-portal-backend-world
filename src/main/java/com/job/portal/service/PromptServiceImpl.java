package com.job.portal.service;

import com.job.portal.dto.PromptDTO;
import com.job.portal.entity.Prompt;
import com.job.portal.repository.PromptRepository;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;
import org.springframework.data.domain.Sort;

@Service
public class PromptServiceImpl implements PromptService {

    @Autowired
    private PromptRepository promptRepository;

    @Autowired
    private ModelMapper modelMapper;

    @Override
    public List<PromptDTO> getAllPrompts() {
        return promptRepository.findAll(Sort.by(Sort.Direction.DESC, "id")).stream()
                .map(prompt -> modelMapper.map(prompt, PromptDTO.class))
                .collect(Collectors.toList());
    }

    @Override
    public PromptDTO getPromptById(Long id) {
        Prompt prompt = promptRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Prompt not found with id: " + id));
        return modelMapper.map(prompt, PromptDTO.class);
    }

    @Override
    public PromptDTO savePrompt(PromptDTO promptDTO) {
        Prompt prompt = modelMapper.map(promptDTO, Prompt.class);
        Prompt savedPrompt = promptRepository.save(prompt);
        return modelMapper.map(savedPrompt, PromptDTO.class);
    }

    @Override
    public PromptDTO updatePrompt(Long id, PromptDTO promptDTO) {
        Prompt existingPrompt = promptRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Prompt not found with id: " + id));
        
        existingPrompt.setTitle(promptDTO.getTitle());
        existingPrompt.setDescription(promptDTO.getDescription());
        existingPrompt.setMediaUrl(promptDTO.getMediaUrl());
        existingPrompt.setMediaType(promptDTO.getMediaType());
        existingPrompt.setPromptText(promptDTO.getPromptText());
        existingPrompt.setAiModel(promptDTO.getAiModel());
        existingPrompt.setCategory(promptDTO.getCategory());

        Prompt updatedPrompt = promptRepository.save(existingPrompt);
        return modelMapper.map(updatedPrompt, PromptDTO.class);
    }

    @Override
    public void deletePrompt(Long id) {
        if (!promptRepository.existsById(id)) {
            throw new RuntimeException("Prompt not found with id: " + id);
        }
        promptRepository.deleteById(id);
    }
}
