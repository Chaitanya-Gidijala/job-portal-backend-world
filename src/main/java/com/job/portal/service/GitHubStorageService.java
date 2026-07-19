package com.job.portal.service;

import com.job.portal.dto.ImageUploadResponse;
import org.springframework.web.multipart.MultipartFile;

/**
 * Service for uploading images to a dedicated GitHub repository used as
 * asset storage for the Prompt Gallery feature.
 */
public interface GitHubStorageService {

    /**
     * Validates, compresses (server-side), uploads the given file to GitHub,
     * and returns both a CDN URL (jsDelivr) and a raw GitHub URL.
     *
     * @param file the multipart image file to upload
     * @return {@link ImageUploadResponse} with imageUrl (CDN), rawUrl, path and sha
     */
    ImageUploadResponse uploadPromptImage(MultipartFile file);
}
