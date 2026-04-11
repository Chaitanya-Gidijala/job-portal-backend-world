package com.job.portal.service.impl;

import com.job.portal.dto.ContactRequest;
import com.job.portal.dto.ContactResponse;
import com.job.portal.entity.ContactMessage;
import com.job.portal.repository.ContactRepository;
import com.job.portal.service.ContactService;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class ContactServiceImpl implements ContactService {

    private final ContactRepository contactRepository;
    private final com.job.portal.service.EmailAsyncService emailAsyncService;

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

        // Truly decouple email sending to ensure immediate API response
        java.util.concurrent.CompletableFuture.runAsync(() -> {
            try {
                sendEmailNotification(request);
            } catch (Exception e) {
                log.error("Failed to send async email notification", e);
            }
        });
    }

    private void sendEmailNotification(ContactRequest request) {
        log.info("Handing over email tasks to EmailAsyncService...");
        
        String adminHtmlContent = generateHtmlContent(request);
        String userHtmlContent = generateUserConfirmationHtml(request);
        
        emailAsyncService.sendInquiryEmails(request, adminHtmlContent, userHtmlContent);
        
        log.info("Email tasks handed over successfully. API request unblocked.");
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
            "                <div class='info-item'><span class='label'>Budget:</span> <span class='value'>%d</span></div>" +
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

    private String generateUserConfirmationHtml(ContactRequest request) {
        return String.format(
            "<!DOCTYPE html>" +
            "<html>" +
            "<head>" +
            "    <meta charset='UTF-8'>" +
            "    <meta name='viewport' content='width=device-width, initial-scale=1.0'>" +
            "    <style>" +
            "        body { font-family: 'Helvetica Neue', Helvetica, Arial, sans-serif; line-height: 1.6; color: #334155; margin: 0; padding: 0; background-color: #f8fafc; }" +
            "        .wrapper { width: 100%%; table-layout: fixed; background-color: #f8fafc; padding-bottom: 40px; }" +
            "        .main { background-color: #ffffff; margin: 0 auto; width: 100%%; max-width: 600px; border-spacing: 0; font-family: sans-serif; color: #334155; border-radius: 12px; overflow: hidden; margin-top: 40px; box-shadow: 0 4px 6px -1px rgba(0, 0, 0, 0.1), 0 2px 4px -1px rgba(0, 0, 0, 0.06); }" +
            "        .header { background: linear-gradient(135deg, #0f172a 0%%, #1e293b 100%%); padding: 40px 20px; text-align: center; color: #ffffff; }" +
            "        .header h1 { margin: 0; font-size: 24px; font-weight: 700; letter-spacing: -0.025em; }" +
            "        .content { padding: 40px; }" +
            "        .greeting { font-size: 20px; font-weight: 700; color: #1e293b; margin-bottom: 16px; }" +
            "        .message { font-size: 16px; color: #475569; margin-bottom: 32px; }" +
            "        .service-badge { display: inline-block; padding: 6px 16px; background-color: #e2e8f0; color: #475569; border-radius: 9999px; font-size: 14px; font-weight: 600; margin-bottom: 24px; }" +
            "        .steps-container { background-color: #f1f5f9; border-radius: 12px; padding: 24px; margin-bottom: 32px; }" +
            "        .steps-title { font-size: 16px; font-weight: 700; color: #1e293b; margin-bottom: 16px; display: flex; align-items: center; }" +
            "        .step-item { margin-bottom: 12px; font-size: 14px; display: flex; }" +
            "        .step-number { background-color: #3b82f6; color: white; width: 22px; height: 22px; border-radius: 50%%; display: inline-block; text-align: center; line-height: 22px; font-size: 12px; font-weight: bold; margin-right: 12px; flex-shrink: 0; }" +
            "        .footer { padding: 32px; text-align: center; color: #94a3b8; font-size: 13px; }" +
            "        .social-links { margin-bottom: 16px; }" +
            "        .social-links a { color: #64748b; text-decoration: none; margin: 0 8px; font-weight: 600; }" +
            "        .divider { border-top: 1px solid #e2e8f0; margin: 32px 0; }" +
            "        @media screen and (max-width: 600px) { .content { padding: 24px; } .main { margin-top: 20px; border-radius: 0; } }" +
            "    </style>" +
            "</head>" +
            "<body>" +
            "    <div class='wrapper'>" +
            "        <table class='main' align='center'>" +
            "            <tr>" +
            "                <td class='header'>" +
            "                    <h1>ChaitanyaTechWorld</h1>" +
            "                </td>" +
            "            </tr>" +
            "            <tr>" +
            "                <td class='content'>" +
            "                    <div class='greeting'>Hello %s,</div>" +
            "                    <div class='message'>" +
            "                        Thank you for reaching out! We've successfully received your inquiry regarding " +
            "                        <span class='service-badge'>%s</span>.<br><br>" +
            "                        Our team of experts is already reviewing your requirements. We aim to provide a personalized response that specifically addresses your project goals." +
            "                    </div>" +
            "                    " +
            "                    <div class='steps-container'>" +
            "                        <div class='steps-title'>Next Steps</div>" +
            "                        <div class='step-item'><span class='step-number'>1</span> <span>Initial review of your project details and message.</span></div>" +
            "                        <div class='step-item'><span class='step-number'>2</span> <span>Internal consultation to prep preliminary ideas.</span></div>" +
            "                        <div class='step-item'><span class='step-number'>3</span> <span>A direct follow-up from one of our leads within 24-48 business hours.</span></div>" +
            "                    </div>" +
            "                    " +
            "                    <div class='message' style='margin-bottom: 0;'>" +
            "                        In the meantime, feel free to check out our latest projects and updates on our social platforms." +
            "                    </div>" +
            "                </td>" +
            "            </tr>" +
            "            <tr>" +
            "                <td class='footer'>" +
            "                    <div class='social-links'>" +
            "                        <a href='https://www.chaitanyatechworld.com'>Chaitanya Tech World</a>" +
            "                    </div>" +
            "                    <div>&copy; 2026 ChaitanyaTechWorld. All rights reserved.</div>" +
            "                </td>" +
            "            </tr>" +
            "        </table>" +
            "    </div>" +
            "</body>" +
            "</html>",
            request.getName(),
            request.getServiceType()
        );
    }
}
