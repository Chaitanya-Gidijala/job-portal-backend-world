package com.job.portal.service;

import com.job.portal.dto.ContactRequest;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailAsyncService {

    private final JavaMailSender mailSender;

    @Value("${app.admin-email}")
    private String adminEmail;

    @Async("taskExecutor")
    public void sendInquiryEmails(ContactRequest request, String adminHtmlContent, String userHtmlContent) {
        log.info("Starting asynchronous email process for inquiry: {}", request.getServiceType());
        
        // 1. Send notification to Admin
        sendEmail(adminEmail, "New Inquiry: " + request.getServiceType(), adminHtmlContent);
        
        // 2. Send confirmation to User
        sendEmail(request.getEmail(), "We received your inquiry - ChaitanyaTechWorld", userHtmlContent);
    }

    private void sendEmail(String to, String subject, String htmlContent) {
        try {
            log.info("Sending email to {}...", to);
            MimeMessage mimeMessage = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true, "UTF-8");

            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(htmlContent, true);

            mailSender.send(mimeMessage);
            log.info("Email sent successfully to {}", to);
        } catch (Exception e) {
            log.error("Failed to send email to {}: {}", to, e.getMessage());
        }
    }
}
