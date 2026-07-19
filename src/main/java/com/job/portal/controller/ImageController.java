package com.job.portal.controller;

import com.job.portal.dto.ApiResponse;
import com.job.portal.dto.ImageDTO;
import com.job.portal.service.ImageService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/admin/images")
public class ImageController {

    @Autowired
    private ImageService imageService;

    @PostMapping("/upload")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<ImageDTO>> uploadImage(@RequestParam("file") MultipartFile file) {
        try {
            ImageDTO imageDTO = imageService.uploadImage(file);
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(ApiResponse.success("Image uploaded successfully", imageDTO));
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.error(e.getMessage(), null));
        }
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<List<ImageDTO>>> getAllImages() {
        List<ImageDTO> images = imageService.getAllImages();
        return ResponseEntity.ok(ApiResponse.success("Images fetched successfully", images));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> deleteImage(@PathVariable Long id) {
        try {
            imageService.deleteImage(id);
            return ResponseEntity.ok(ApiResponse.success("Image deleted successfully", null));
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error(e.getMessage(), null));
        }
    }
}
