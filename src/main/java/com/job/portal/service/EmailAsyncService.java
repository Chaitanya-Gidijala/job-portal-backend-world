package com.job.portal.service;

import com.job.portal.dto.ContactRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailAsyncService {

    private final org.springframework.web.client.RestTemplate restTemplate;

    @Value("${brevo.api.key}")
    private String brevoApiKey;

    @Value("${brevo.api.url}")
    private String brevoApiUrl;

    @Value("${app.admin-email}")
    private String adminEmail;

    @Async("taskExecutor")
    public void sendInquiryEmails(ContactRequest request, String adminHtmlContent, String userHtmlContent) {
        log.info("Starting asynchronous Brevo HTTP email process for inquiry: {}", request.getServiceType());
        
        // 1. Send notification to Admin
        sendEmail(adminEmail, "New Inquiry: " + request.getServiceType(), adminHtmlContent);
        
        // 2. Send confirmation to User
        sendEmail(request.getEmail(), "We received your inquiry - ChaitanyaTechWorld", userHtmlContent);
    }

    private void sendEmail(String to, String subject, String htmlContent) {
        try {
            log.info("Sending Brevo HTTP email to {}...", to);
            
            org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
            headers.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
            headers.set("api-key", brevoApiKey);

            java.util.Map<String, Object> body = new java.util.HashMap<>();
            body.put("sender", java.util.Map.of("name", "ChaitanyaTechWorld", "email", "support@chaitanyatechworld.com"));
            body.put("to", java.util.List.of(java.util.Map.of("email", to)));
            body.put("subject", subject);
            body.put("htmlContent", htmlContent);

            org.springframework.http.HttpEntity<java.util.Map<String, Object>> entity = new org.springframework.http.HttpEntity<>(body, headers);
            
            restTemplate.postForEntity(brevoApiUrl, entity, String.class);
            log.info("Brevo HTTP email sent successfully to {}", to);
        } catch (Exception e) {
            log.error("Failed to send Brevo HTTP email to {}: {}", to, e.getMessage());
        }
    }
}
