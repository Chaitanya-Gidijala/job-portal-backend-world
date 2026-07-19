package com.job.portal.service;

import com.job.portal.dto.ImageDTO;
import com.job.portal.entity.ImageMetadata;
import com.job.portal.repository.ImageRepository;
import lombok.extern.slf4j.Slf4j;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.util.*;

@Service
@Slf4j
public class ImageServiceImpl implements ImageService {

    @Autowired
    private ImageRepository imageRepository;

    @Autowired
    private ModelMapper modelMapper;

    @Value("${github.api.token}")
    private String githubToken;

    @Value("${github.api.owner}")
    private String githubOwner;

    @Value("${github.api.repo}")
    private String githubRepo;

    private static final String GITHUB_API_URL = "https://api.github.com/repos/{owner}/{repo}/contents/{path}";
    private static final long MAX_FILE_SIZE = 10 * 1024 * 1024; // 10MB

    @Override
    @Transactional
    public ImageDTO uploadImage(MultipartFile file) {
        validateFile(file);

        String originalFilename = StringUtils.cleanPath(Objects.requireNonNull(file.getOriginalFilename()));
        String extension = StringUtils.getFilenameExtension(originalFilename);
        String uniqueFilename = UUID.randomUUID().toString() + "." + extension;
        String githubPath = "images/" + uniqueFilename;

        try {
            String base64Content = Base64.getEncoder().encodeToString(file.getBytes());

            RestTemplate restTemplate = new RestTemplate();
            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", "Bearer " + githubToken);
            headers.set("Accept", "application/vnd.github.v3+json");

            Map<String, String> body = new HashMap<>();
            body.put("message", "Upload image " + originalFilename + " from admin dashboard");
            body.put("content", base64Content);

            HttpEntity<Map<String, String>> requestEntity = new HttpEntity<>(body, headers);

            ResponseEntity<Map> response = restTemplate.exchange(
                    GITHUB_API_URL,
                    HttpMethod.PUT,
                    requestEntity,
                    Map.class,
                    githubOwner,
                    githubRepo,
                    githubPath
            );

            Map<String, Object> responseBody = response.getBody();
            if (responseBody == null || !responseBody.containsKey("content")) {
                throw new RuntimeException("Failed to upload image to GitHub.");
            }

            Map<String, Object> contentMap = (Map<String, Object>) responseBody.get("content");
            String sha = (String) contentMap.get("sha");
            String downloadUrl = (String) contentMap.get("download_url");

            ImageMetadata imageMetadata = ImageMetadata.builder()
                    .filename(originalFilename)
                    .githubUrl(downloadUrl)
                    .githubPath(githubPath)
                    .githubSha(sha)
                    .build();

            ImageMetadata savedMetadata = imageRepository.save(imageMetadata);
            return modelMapper.map(savedMetadata, ImageDTO.class);

        } catch (Exception e) {
            log.error("Error uploading image to GitHub: ", e);
            throw new RuntimeException("Error uploading image: " + e.getMessage());
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<ImageDTO> getAllImages() {
        return imageRepository.findAllByOrderByUploadDateDesc().stream()
                .map(image -> modelMapper.map(image, ImageDTO.class))
                .toList();
    }

    @Override
    @Transactional
    public void deleteImage(Long id) {
        ImageMetadata image = imageRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Image not found with id: " + id));

        try {
            RestTemplate restTemplate = new RestTemplate();
            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", "Bearer " + githubToken);
            headers.set("Accept", "application/vnd.github.v3+json");

            Map<String, String> body = new HashMap<>();
            body.put("message", "Delete image " + image.getFilename() + " from admin dashboard");
            body.put("sha", image.getGithubSha());

            HttpEntity<Map<String, String>> requestEntity = new HttpEntity<>(body, headers);

            restTemplate.exchange(
                    GITHUB_API_URL,
                    HttpMethod.DELETE,
                    requestEntity,
                    Void.class,
                    githubOwner,
                    githubRepo,
                    image.getGithubPath()
            );

            imageRepository.delete(image);

        } catch (Exception e) {
            log.error("Error deleting image from GitHub: ", e);
            throw new RuntimeException("Error deleting image: " + e.getMessage());
        }
    }

    private void validateFile(MultipartFile file) {
        if (file.isEmpty()) {
            throw new RuntimeException("Cannot upload empty file.");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new RuntimeException("File size exceeds 10MB limit.");
        }
        String contentType = file.getContentType();
        if (contentType == null || !(contentType.equals("image/jpeg") || contentType.equals("image/png") ||
                contentType.equals("image/webp") || contentType.equals("image/gif"))) {
            throw new RuntimeException("Only JPG, JPEG, PNG, WEBP, and GIF images are allowed.");
        }
    }
}
