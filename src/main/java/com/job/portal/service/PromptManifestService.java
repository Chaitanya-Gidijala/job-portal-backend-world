package com.job.portal.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.job.portal.dto.PromptDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service that stores prompts in a `prompts.json` manifest file on GitHub
 * instead of a relational database.
 */
@Service
@Primary
@Slf4j
public class PromptManifestService implements PromptService {

    @Value("${prompt.github.token}")
    private String githubToken;

    @Value("${prompt.github.owner}")
    private String owner;

    @Value("${prompt.github.repo}")
    private String repo;

    @Value("${prompt.github.branch:main}")
    private String branch;

    private static final String MANIFEST_PATH = "prompts.json";
    private static final String GITHUB_API_BASE = "https://api.github.com";
    private static final String CONTENTS_URL = GITHUB_API_BASE + "/repos/{owner}/{repo}/contents/{path}";

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    // ─────────────────────────────────────────────────────────────────────────

    @Override
    public List<PromptDTO> getAllPrompts() {
        return readManifest().getPrompts();
    }

    @Override
    public PromptDTO getPromptById(Long id) {
        return getAllPrompts().stream()
                .filter(p -> p.getId().equals(id))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("Prompt not found with id: " + id));
    }

    @Override
    public PromptDTO savePrompt(PromptDTO promptDTO) {
        ManifestData manifest = readManifest();
        
        // Generate new ID
        Long nextId = manifest.getPrompts().stream()
                .map(PromptDTO::getId)
                .filter(Objects::nonNull)
                .max(Long::compareTo)
                .orElse(0L) + 1;
                
        promptDTO.setId(nextId);
        promptDTO.setCreatedAt(LocalDateTime.now());
        promptDTO.setUpdatedAt(LocalDateTime.now());
        
        manifest.getPrompts().add(0, promptDTO); // Add to beginning
        writeManifest(manifest);
        
        return promptDTO;
    }

    @Override
    public PromptDTO updatePrompt(Long id, PromptDTO promptDTO) {
        ManifestData manifest = readManifest();
        
        List<PromptDTO> prompts = manifest.getPrompts();
        boolean found = false;
        
        for (int i = 0; i < prompts.size(); i++) {
            PromptDTO existing = prompts.get(i);
            if (existing.getId().equals(id)) {
                promptDTO.setId(id);
                promptDTO.setCreatedAt(existing.getCreatedAt());
                promptDTO.setUpdatedAt(LocalDateTime.now());
                prompts.set(i, promptDTO);
                found = true;
                break;
            }
        }
        
        if (!found) {
            throw new RuntimeException("Prompt not found with id: " + id);
        }
        
        writeManifest(manifest);
        return promptDTO;
    }

    @Override
    public void deletePrompt(Long id) {
        ManifestData manifest = readManifest();
        
        boolean removed = manifest.getPrompts().removeIf(p -> p.getId().equals(id));
        if (!removed) {
            throw new RuntimeException("Prompt not found with id: " + id);
        }
        
        writeManifest(manifest);
    }

    // ── Internal GitHub operations ───────────────────────────────────────────

    private ManifestData readManifest() {
        try {
            HttpEntity<Void> request = new HttpEntity<>(buildHeaders());
            ResponseEntity<Map> response = restTemplate.exchange(
                    CONTENTS_URL, HttpMethod.GET, request, Map.class,
                    owner, repo, MANIFEST_PATH);

            Map<?, ?> body = response.getBody();
            if (body != null) {
                String sha = (String) body.get("sha");
                String contentBase64 = (String) body.get("content");
                // GitHub sends content with newlines
                contentBase64 = contentBase64.replaceAll("\\n", "").replaceAll("\\r", "");
                
                byte[] decoded = Base64.getDecoder().decode(contentBase64);
                List<PromptDTO> prompts = objectMapper.readValue(decoded, new TypeReference<List<PromptDTO>>() {});
                return new ManifestData(prompts, sha);
            }
        } catch (HttpClientErrorException.NotFound e) {
            log.info("prompts.json not found, starting fresh.");
            return new ManifestData(new ArrayList<>(), null); // File doesn't exist yet
        } catch (Exception e) {
            log.error("Failed to read manifest from GitHub", e);
            throw new RuntimeException("Could not fetch prompts data.");
        }
        return new ManifestData(new ArrayList<>(), null);
    }

    private void writeManifest(ManifestData manifest) {
        try {
            // objectMapper needs to handle Java 8 dates properly if configured, 
            // but for simplicity let's configure objectMapper just in case.
            objectMapper.findAndRegisterModules();
            
            String json = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(manifest.getPrompts());
            String base64Content = Base64.getEncoder().encodeToString(json.getBytes());

            Map<String, String> body = new LinkedHashMap<>();
            body.put("message", "Update prompts.json manifest");
            body.put("content", base64Content);
            body.put("branch", branch);
            if (manifest.getSha() != null) {
                body.put("sha", manifest.getSha());
            }

            HttpEntity<Map<String, String>> request = new HttpEntity<>(body, buildHeaders());
            
            restTemplate.exchange(
                    CONTENTS_URL, HttpMethod.PUT, request, Map.class,
                    owner, repo, MANIFEST_PATH);
                    
        } catch (HttpClientErrorException.Conflict e) {
            log.warn("Conflict updating manifest (409). Retrying...");
            // Retry logic for concurrent edits: read fresh, merge (simple overwrite here or throw)
            throw new RuntimeException("Concurrent edit conflict. Please try again.");
        } catch (Exception e) {
            log.error("Failed to save manifest to GitHub", e);
            throw new RuntimeException("Could not save prompt data.");
        }
    }

    private HttpHeaders buildHeaders() {
        HttpHeaders h = new HttpHeaders();
        h.set("Authorization", "Bearer " + githubToken);
        h.set("Accept", "application/vnd.github+json");
        h.set("X-GitHub-Api-Version", "2022-11-28");
        h.setContentType(MediaType.APPLICATION_JSON);
        return h;
    }

    @lombok.Data
    @lombok.AllArgsConstructor
    private static class ManifestData {
        private List<PromptDTO> prompts;
        private String sha;
    }
}
