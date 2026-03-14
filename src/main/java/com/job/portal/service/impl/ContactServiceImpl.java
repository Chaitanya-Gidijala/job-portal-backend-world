package com.job.portal.service.impl;

import com.job.portal.dto.ContactRequest;
import com.job.portal.entity.ContactMessage;
import com.job.portal.repository.ContactRepository;
import com.job.portal.service.ContactService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class ContactServiceImpl implements ContactService {

    private final ContactRepository contactRepository;
    private final JavaMailSender mailSender;

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
        log.info("Sending email notification to admin...");
        try {
            SimpleMailMessage mailMessage = new SimpleMailMessage();
            mailMessage.setTo("support@chaitanyatechworld.com");
            mailMessage.setSubject("New Inquiry: " + request.getServiceType());
            mailMessage.setText(String.format(
                    "New contact form submission received:\n\n" +
                    "Name: %s\n" +
                    "Email: %s\n" +
                    "Phone: %s\n" +
                    "Service Type: %s\n" +
                    "Budget: %d\n" +
                    "Message: %s",
                    request.getName(), request.getEmail(), request.getPhone(),
                    request.getServiceType(), request.getBudget(), request.getMessage()
            ));

            mailSender.send(mailMessage);
            log.info("Email notification sent successfully to admin.");
        } catch (Exception e) {
            log.error("Failed to send email notification: {}", e.getMessage());
            // We don't throw exception here to avoid failing the whole request if email fails
        }
    }
}
