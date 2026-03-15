package com.job.portal.controller;

import com.job.portal.dto.ApiResponse;
import com.job.portal.dto.ContactRequest;
import com.job.portal.dto.ContactResponse;
import com.job.portal.service.ContactService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/contact")
@RequiredArgsConstructor
@Slf4j
public class ContactController {

    private final ContactService contactService;

    @PostMapping("/submit")
    public ResponseEntity<ApiResponse<String>> submitContact(@Valid @RequestBody ContactRequest request) {
        log.info("Received contact form submission request for: {}", request.getEmail());
        contactService.submitContactForm(request);
        return new ResponseEntity<>(
            ApiResponse.success("Contact message submitted successfully", null), 
            HttpStatus.CREATED
        );
    }

    @GetMapping("/all")
    public ResponseEntity<ApiResponse<List<ContactResponse>>> getAllInquiries() {
        log.info("Request to fetch all contact inquiries");
        List<ContactResponse> inquiries = contactService.getAllInquiries();
        return ResponseEntity.ok(ApiResponse.success("Fetched all inquiries successfully", inquiries));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<ContactResponse>> getInquiryById(@PathVariable Long id) {
        log.info("Request to fetch contact inquiry with ID: {}", id);
        ContactResponse inquiry = contactService.getInquiryById(id);
        return ResponseEntity.ok(ApiResponse.success("Fetched inquiry details successfully", inquiry));
    }
}
