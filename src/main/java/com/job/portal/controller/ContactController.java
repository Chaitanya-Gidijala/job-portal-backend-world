package com.job.portal.controller;

import com.job.portal.dto.ContactRequest;
import com.job.portal.service.ContactService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/contact")
@RequiredArgsConstructor
@Slf4j
public class ContactController {

    private final ContactService contactService;

    @PostMapping("/submit")
    public ResponseEntity<String> submitContact(@Valid @RequestBody ContactRequest request) {
        log.info("Received contact form submission request for: {}", request.getEmail());
        contactService.submitContactForm(request);
        return new ResponseEntity<>("Contact message submitted successfully", HttpStatus.CREATED);
    }
}
