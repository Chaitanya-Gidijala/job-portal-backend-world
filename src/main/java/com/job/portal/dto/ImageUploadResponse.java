package com.job.portal.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response DTO returned after a successful prompt image upload to GitHub.
 * Contains both a CDN-cached URL (preferred) and a raw GitHub URL (fallback).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ImageUploadResponse {

    /** jsDelivr CDN URL — cached, fast, recommended as primary URL to store. */
    private String imageUrl;

    /** Direct raw.githubusercontent.com URL — no CDN, used as fallback. */
    private String rawUrl;

    /** The path inside the GitHub repo, e.g. images/2025/07/uuid-filename.jpg */
    private String path;

    /** Original filename provided by the uploader. */
    private String originalFilename;

    /** SHA returned by GitHub after the commit — needed to update/delete the file later. */
    private String sha;
}
