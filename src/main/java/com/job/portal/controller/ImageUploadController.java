package com.job.portal.controller;

import com.job.portal.dto.ApiResponse;
import com.job.portal.dto.ImageUploadResponse;
import com.job.portal.service.GitHubStorageService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/**
 * REST controller that exposes the prompt-gallery image upload endpoint.
 *
 * POST /api/admin/prompts/upload-image
 *   - Secured behind ROLE_ADMIN (reuses existing JWT auth)
 *   - Accepts multipart/form-data with a single "file" field
 *   - Returns { imageUrl (CDN), rawUrl, path, originalFilename, sha }
 */
@RestController
@RequestMapping("/api/admin/prompts")
@Slf4j
public class ImageUploadController {

    @Autowired
    private GitHubStorageService gitHubStorageService;

    /**
     * Uploads an image file to the prompt-gallery GitHub repository.
     *
     * @param file the image file (JPG / PNG / WEBP, max 5 MB)
     * @return {@link ImageUploadResponse} wrapped in {@link ApiResponse}
     */
    @PostMapping("/upload-image")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<ImageUploadResponse>> uploadImage(
            @RequestParam("file") MultipartFile file) {

        log.info("Prompt image upload request received: name={}, size={} bytes, type={}",
                file.getOriginalFilename(), file.getSize(), file.getContentType());

        try {
            ImageUploadResponse result = gitHubStorageService.uploadPromptImage(file);
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(ApiResponse.success("Image uploaded successfully", result));

        } catch (RuntimeException ex) {
            log.warn("Image upload rejected: {}", ex.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.error(ex.getMessage(), null));
        }
    }
}
