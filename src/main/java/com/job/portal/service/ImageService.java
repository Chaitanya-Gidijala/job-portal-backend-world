package com.job.portal.service;

import com.job.portal.dto.ImageDTO;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface ImageService {
    ImageDTO uploadImage(MultipartFile file);
    List<ImageDTO> getAllImages();
    void deleteImage(Long id);
}
