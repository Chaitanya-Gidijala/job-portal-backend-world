package com.job.portal.service.impl;

import com.job.portal.dto.ContactRequest;
import com.job.portal.dto.ContactResponse;
import com.job.portal.entity.ContactMessage;
import com.job.portal.repository.ContactRepository;
import com.job.portal.service.ContactService;
import java.util.List;
import java.util.stream.Collectors;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class ContactServiceImpl implements ContactService {

    private final ContactRepository contactRepository;
    private final JavaMailSender mailSender;

    @Value("${app.admin-email}")
    private String adminEmail;

    @Override
    @Transactional
    public void submitContactForm(ContactRequest request) {
        log.info("Received new contact form submission from: {}", request.getEmail());

        ContactMessage message = new ContactMessage();
        message.setName(request.getName());
        message.setEmail(request.getEmail());
        message.setPhone(request.getPhone());
        message.setServiceType(request.getServiceType());
        message.setBudget(request.getBudget());
        message.setMessage(request.getMessage());

        contactRepository.save(message);
        log.info("Saved contact message to database with ID: {}", message.getId());

        sendEmailNotification(request);
    }

    private void sendEmailNotification(ContactRequest request) {
        log.info("Sending professional HTML email notification to admin...");
        try {
            MimeMessage mimeMessage = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true, "UTF-8");

            helper.setTo(adminEmail);
            helper.setSubject("New Inquiry: " + request.getServiceType());
            
            String htmlContent = generateHtmlContent(request);
            helper.setText(htmlContent, true);

            mailSender.send(mimeMessage);
            log.info("Professional email notification sent successfully to admin.");
        } catch (Exception e) {
            log.error("Failed to send email notification: {}", e.getMessage());
        }
    }

    @Override
    public List<ContactResponse> getAllInquiries() {
        log.info("Fetching all contact inquiries from database");
        return contactRepository.findAll().stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Override
    public ContactResponse getInquiryById(Long id) {
        log.info("Fetching inquiry details for ID: {}", id);
        ContactMessage message = contactRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Inquiry not found with id: " + id));
        
        // Mark as read when viewed
        if (!message.isRead()) {
            message.setRead(true);
            contactRepository.save(message);
        }
        
        return mapToResponse(message);
    }

    private ContactResponse mapToResponse(ContactMessage message) {
        return new ContactResponse(
                message.getId(),
                message.getName(),
                message.getEmail(),
                message.getPhone(),
                message.getServiceType(),
                message.getBudget(),
                message.getMessage(),
                message.getCreatedAt(),
                message.isRead()
        );
    }

    private String generateHtmlContent(ContactRequest request) {
        return String.format(
            "<!DOCTYPE html>" +
            "<html>" +
            "<head>" +
            "    <style>" +
            "        body { font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif; line-height: 1.6; color: #333; margin: 0; padding: 0; background-color: #f4f7f6; }" +
            "        .container { max-width: 600px; margin: 20px auto; background: #ffffff; border-radius: 8px; overflow: hidden; box-shadow: 0 4px 10px rgba(0,0,0,0.05); border: 1px solid #e1e8ed; }" +
            "        .header { background: linear-gradient(135deg, #2c3e50 0%%, #34495e 100%%); color: #ffffff; padding: 30px 20px; text-align: center; }" +
            "        .header h1 { margin: 0; font-size: 24px; font-weight: 600; letter-spacing: 1px; }" +
            "        .content { padding: 30px 40px; }" +
            "        .section-title { font-size: 14px; font-weight: bold; color: #7f8c8d; text-transform: uppercase; margin-bottom: 15px; border-bottom: 1px solid #eee; padding-bottom: 5px; }" +
            "        .info-grid { display: block; margin-bottom: 25px; }" +
            "        .info-item { margin-bottom: 12px; }" +
            "        .label { font-weight: 600; color: #2c3e50; width: 100px; display: inline-block; }" +
            "        .value { color: #555; }" +
            "        .message-box { background: #f9f9f9; border-left: 4px solid #3498db; padding: 20px; border-radius: 4px; font-style: italic; color: #444; }" +
            "        .footer { background: #f4f7f6; color: #95a5a6; padding: 20px; text-align: center; font-size: 12px; }" +
            "        .badge { display: inline-block; padding: 4px 12px; border-radius: 20px; background: #3498db; color: white; font-size: 11px; font-weight: bold; text-transform: uppercase; }" +
            "    </style>" +
            "</head>" +
            "<body>" +
            "    <div class='container'>" +
            "        <div class='header'>" +
            "            <h1>New Project Inquiry</h1>" +
            "            <div style='margin-top: 10px;'><span class='badge'>%s</span></div>" +
            "        </div>" +
            "        <div class='content'>" +
            "            <div class='section-title'>Client Information</div>" +
            "            <div class='info-grid'>" +
            "                <div class='info-item'><span class='label'>Name:</span> <span class='value'>%s</span></div>" +
            "                <div class='info-item'><span class='label'>Email:</span> <span class='value'><a href='mailto:%s' style='color: #3498db; text-decoration: none;'>%s</a></span></div>" +
            "                <div class='info-item'><span class='label'>Phone:</span> <span class='value'>%s</span></div>" +
            "                <div class='info-item'><span class='label'>Budget:</span> <span class='value'>$%d</span></div>" +
            "            </div>" +
            "            " +
            "            <div class='section-title'>Message Preview</div>" +
            "            <div class='message-box'>\"%s\"</div>" +
            "        </div>" +
            "        <div class='footer'>" +
            "            This is an automated notification from your ChaitanyaTechWorld Service.<br>" +
            "            &copy; 2026 ChaitanyaTechWorld Portal. All rights reserved." +
            "        </div>" +
            "    </div>" +
            "</body>" +
            "</html>",
            request.getServiceType(),
            request.getName(),
            request.getEmail(), request.getEmail(),
            request.getPhone() != null ? request.getPhone() : "Not provided",
            request.getBudget() != null ? request.getBudget() : 0,
            request.getMessage()
        );
    }
}
