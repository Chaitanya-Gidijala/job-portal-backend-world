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

    @Value("${brevo.sender.email:support@chaitanyatechworld.com}")
    private String senderEmail;

    @Value("${brevo.sender.name:ChaitanyaTechWorld}")
    private String senderName;

    @Async("taskExecutor")
    public void sendInquiryEmails(ContactRequest request, String adminHtmlContent, String userHtmlContent) {
        log.info("Starting asynchronous Brevo HTTP email process for inquiry: {}", request.getServiceType());
        
        // 1. Send notification to Admin
        sendEmail(adminEmail, "New Inquiry: " + request.getServiceType(), adminHtmlContent);
        
        // 2. Send confirmation to User
        sendEmail(request.getEmail(), "We received your inquiry - ChaitanyaTechWorld", userHtmlContent);
    }

    @Async("taskExecutor")
    public void sendSupportEmails(String donorName, String donorEmail, String amount, String txnId, String adminHtml, String userHtml) {
        log.info("Starting asynchronous Brevo HTTP email process for Support contribution from: {}", donorEmail);
        
        // 1. Notify Admin
        sendEmail(adminEmail, "New Support Received: ₹" + amount + " from " + donorName, adminHtml);
        
        // 2. Notify Donor
        sendEmail(donorEmail, "Thank you for your support - ChaitanyaTechWorld", userHtml);
    }

    private void sendEmail(String to, String subject, String htmlContent) {
        try {
            log.info("Sending Brevo HTTP email to {}...", to);
            
            org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
            headers.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
            headers.set("api-key", brevoApiKey);

            java.util.Map<String, Object> body = new java.util.HashMap<>();
            body.put("sender", java.util.Map.of("name", senderName, "email", senderEmail));
            body.put("to", java.util.List.of(java.util.Map.of("email", to)));
            body.put("subject", subject);
            body.put("htmlContent", htmlContent);

            org.springframework.http.HttpEntity<java.util.Map<String, Object>> entity = new org.springframework.http.HttpEntity<>(body, headers);
            
            org.springframework.http.ResponseEntity<String> response = restTemplate.postForEntity(brevoApiUrl, entity, String.class);
            log.info("Brevo Response (Status {}): {}", response.getStatusCode(), response.getBody());
            
            if (response.getStatusCode().is2xxSuccessful()) {
                log.info("Brevo HTTP email successfully accepted for {}", to);
            } else {
                log.warn("Brevo HTTP email might have failed for {}. Response: {}", to, response.getBody());
            }
        } catch (org.springframework.web.client.HttpClientErrorException e) {
            log.error("Brevo API Error ({}): {}", e.getStatusCode(), e.getResponseBodyAsString());
        } catch (Exception e) {
            log.error("Unexpected error while sending Brevo HTTP email to {}: {}", to, e.getMessage());
        }
    }
}
