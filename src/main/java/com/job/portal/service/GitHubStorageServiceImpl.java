package com.job.portal.service;

import com.job.portal.dto.ImageUploadResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.util.*;
import java.util.regex.Pattern;

/**
 * Implementation of {@link GitHubStorageService} that uploads images to a dedicated
 * GitHub repository via the GitHub Contents API and returns both a CDN-cached
 * jsDelivr URL (primary) and a raw GitHub URL (fallback).
 *
 * <p>Key features:
 * <ul>
 *   <li>File type &amp; size validation (JPG, JPEG, PNG, WEBP — max 5 MB)</li>
 *   <li>UUID-prefixed, date-partitioned file paths to avoid collisions</li>
 *   <li>409 conflict handling: re-fetches file SHA and retries once</li>
 *   <li>Retry-once on transient 5xx / network failures</li>
 *   <li>Logs GitHub rate-limit headers for visibility</li>
 *   <li>Clean error messages — no raw GitHub exceptions leak to the frontend</li>
 * </ul>
 */
@Service
@Slf4j
public class GitHubStorageServiceImpl implements GitHubStorageService {

    // ── Config ────────────────────────────────────────────────────────────────
    @Value("${prompt.github.token}")
    private String githubToken;

    @Value("${prompt.github.owner}")
    private String owner;

    @Value("${prompt.github.repo}")
    private String repo;

    @Value("${prompt.github.branch:main}")
    private String branch;

    // ── Constants ─────────────────────────────────────────────────────────────
    private static final String GITHUB_API_BASE = "https://api.github.com";
    private static final String CONTENTS_URL = GITHUB_API_BASE + "/repos/{owner}/{repo}/contents/{path}";
    private static final long MAX_FILE_SIZE = 5 * 1024 * 1024; // 5 MB
    private static final Set<String> ALLOWED_TYPES = Set.of(
            "image/jpeg", "image/png", "image/webp"
    );
    private static final Pattern UNSAFE_CHARS = Pattern.compile("[^a-zA-Z0-9._-]");

    private final RestTemplate restTemplate = new RestTemplate();

    // ─────────────────────────────────────────────────────────────────────────
    @Override
    public ImageUploadResponse uploadPromptImage(MultipartFile file) {
        validateFile(file);

        String originalFilename = Objects.requireNonNull(file.getOriginalFilename());
        String sanitized       = sanitizeFilename(StringUtils.getFilenameExtension(originalFilename));
        String uniqueName      = UUID.randomUUID() + "-" + sanitized;
        LocalDate today        = LocalDate.now();
        String path = String.format("images/%d/%02d/%s", today.getYear(), today.getMonthValue(), uniqueName);

        String base64Content;
        try {
            base64Content = Base64.getEncoder().encodeToString(file.getBytes());
        } catch (Exception e) {
            throw new RuntimeException("Failed to read uploaded file bytes.", e);
        }

        return uploadWithRetry(path, base64Content, originalFilename, null, 0);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Internal helpers
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Attempts the GitHub PUT; retries once on 5xx or on 409 (conflict).
     * On 409 it re-fetches the current SHA and passes it to the next call.
     */
    private ImageUploadResponse uploadWithRetry(String path, String base64Content,
                                                 String originalFilename, String existingSha,
                                                 int attempt) {
        if (attempt > 1) {
            throw new RuntimeException("Upload failed after retry. Please try again later.");
        }

        HttpHeaders headers = buildHeaders();
        Map<String, String> body = new LinkedHashMap<>();
        body.put("message", "Upload prompt image: " + originalFilename);
        body.put("content", base64Content);
        body.put("branch", branch);
        if (existingSha != null) {
            body.put("sha", existingSha); // required to overwrite an existing file
        }

        HttpEntity<Map<String, String>> request = new HttpEntity<>(body, headers);

        try {
            ResponseEntity<Map> response = restTemplate.exchange(
                    CONTENTS_URL, HttpMethod.PUT, request, Map.class,
                    owner, repo, path);

            logRateLimit(response.getHeaders());

            Map<?, ?> content = (Map<?, ?>) response.getBody().get("content");
            String sha = (String) content.get("sha");

            return buildResponse(path, originalFilename, sha);

        } catch (HttpClientErrorException.Unauthorized e) {
            log.error("GitHub API returned 401 — check GITHUB_PROMPT_TOKEN env var.");
            throw new RuntimeException("Image upload failed: invalid or missing GitHub token.");

        } catch (HttpClientErrorException e) {
            if (e.getStatusCode() == HttpStatus.CONFLICT) {
                // 409 — file already exists (rare with UUID but possible). Re-fetch SHA and retry.
                log.warn("GitHub 409 conflict for path '{}'. Re-fetching SHA and retrying…", path);
                String sha = fetchExistingSha(path);
                return uploadWithRetry(path, base64Content, originalFilename, sha, attempt + 1);
            }
            log.error("GitHub API client error {}: {}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new RuntimeException("Image upload failed: " + friendlyClientError(e));

        } catch (HttpServerErrorException e) {
            // Transient GitHub 5xx — retry once
            if (attempt == 0) {
                log.warn("GitHub 5xx error ({}), retrying once…", e.getStatusCode());
                return uploadWithRetry(path, base64Content, originalFilename, existingSha, attempt + 1);
            }
            log.error("GitHub 5xx after retry: {}", e.getStatusCode());
            throw new RuntimeException("GitHub is temporarily unavailable. Please try again later.");

        } catch (Exception e) {
            if (attempt == 0) {
                log.warn("Network error uploading to GitHub, retrying once: {}", e.getMessage());
                return uploadWithRetry(path, base64Content, originalFilename, existingSha, attempt + 1);
            }
            log.error("Upload failed after retry: ", e);
            throw new RuntimeException("Image upload failed due to a network error.");
        }
    }

    /** GETs the file metadata from GitHub to retrieve the current SHA. */
    @SuppressWarnings("unchecked")
    private String fetchExistingSha(String path) {
        try {
            HttpEntity<Void> request = new HttpEntity<>(buildHeaders());
            ResponseEntity<Map> response = restTemplate.exchange(
                    CONTENTS_URL, HttpMethod.GET, request, Map.class,
                    owner, repo, path);
            Map<?, ?> body = response.getBody();
            return body != null ? (String) body.get("sha") : null;
        } catch (Exception e) {
            log.error("Failed to fetch existing SHA for path '{}': {}", path, e.getMessage());
            return null;
        }
    }

    private ImageUploadResponse buildResponse(String path, String originalFilename, String sha) {
        // jsDelivr CDN URL — globally cached, preferred
        String cdnUrl = String.format("https://cdn.jsdelivr.net/gh/%s/%s@%s/%s",
                owner, repo, branch, path);
        // raw GitHub URL — no CDN, used as fallback
        String rawUrl = String.format("https://raw.githubusercontent.com/%s/%s/%s/%s",
                owner, repo, branch, path);

        log.info("Image uploaded successfully → CDN: {}", cdnUrl);
        return ImageUploadResponse.builder()
                .imageUrl(cdnUrl)
                .rawUrl(rawUrl)
                .path(path)
                .originalFilename(originalFilename)
                .sha(sha)
                .build();
    }

    private HttpHeaders buildHeaders() {
        HttpHeaders h = new HttpHeaders();
        h.set("Authorization", "Bearer " + githubToken);
        h.set("Accept", "application/vnd.github+json");
        h.set("X-GitHub-Api-Version", "2022-11-28");
        h.setContentType(MediaType.APPLICATION_JSON);
        return h;
    }

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new RuntimeException("No file was provided.");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new RuntimeException(
                    String.format("File size %.1f MB exceeds the 5 MB limit. Please compress the image before uploading.",
                            file.getSize() / (1024.0 * 1024.0)));
        }
        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_TYPES.contains(contentType)) {
            throw new RuntimeException("Only JPG, PNG, and WEBP images are allowed.");
        }
    }

    /** Turns extension into a safe filename: e.g. "jpg" → "image.jpg" */
    private String sanitizeFilename(String extension) {
        if (extension == null || extension.isBlank()) return "image.jpg";
        String safe = UNSAFE_CHARS.matcher(extension.toLowerCase()).replaceAll("");
        return "image." + safe;
    }

    private void logRateLimit(HttpHeaders headers) {
        String remaining = headers.getFirst("X-RateLimit-Remaining");
        String reset = headers.getFirst("X-RateLimit-Reset");
        if (remaining != null) {
            log.debug("GitHub rate limit — remaining: {}, resets at epoch: {}", remaining, reset);
        }
    }

    private String friendlyClientError(HttpClientErrorException e) {
        return switch (e.getStatusCode().value()) {
            case 401 -> "Authentication failed. Check your GitHub token.";
            case 403 -> "Rate limit exceeded or insufficient token permissions.";
            case 404 -> "Repository not found. Check owner/repo configuration.";
            case 422 -> "Validation error from GitHub: " + e.getResponseBodyAsString();
            default  -> "GitHub API error (" + e.getStatusCode() + ").";
        };
    }
}
