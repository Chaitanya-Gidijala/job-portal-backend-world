package com.job.portal.controller;

import com.job.portal.entity.NewsletterSubscriber;
import com.job.portal.repository.NewsletterSubscriberRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/newsletter")
public class NewsletterController {

    @Autowired
    private NewsletterSubscriberRepository newsletterSubscriberRepository;

    @PostMapping("/subscribe")
    public ResponseEntity<?> subscribe(@RequestBody Map<String, String> payload) {
        String email = payload.get("email");
        
        if (email == null || email.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Email is required"));
        }
        
        if (newsletterSubscriberRepository.existsByEmail(email)) {
            return ResponseEntity.ok(Map.of("message", "Already subscribed"));
        }
        
        NewsletterSubscriber subscriber = new NewsletterSubscriber();
        subscriber.setEmail(email.trim());
        newsletterSubscriberRepository.save(subscriber);
        
        return ResponseEntity.ok(Map.of("message", "Successfully subscribed"));
    }
}
