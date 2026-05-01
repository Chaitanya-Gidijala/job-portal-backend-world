package com.job.portal.controller;

import com.job.portal.entity.UserPayment;
import com.job.portal.entity.UserResume;
import com.job.portal.repository.UserPaymentRepository;
import com.job.portal.repository.UserResumeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/user/profile")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class UserProfileController {

    private static final Logger log = LoggerFactory.getLogger(UserProfileController.class);

    private final UserResumeRepository resumeRepository;
    private final UserPaymentRepository paymentRepository;
    private final com.job.portal.security.JwtTokenProvider jwtTokenProvider;

    @Value("${app.admin-email:support@chaitanyatechworld.com}")
    private String adminEmail;

    private String getAuthenticatedEmail(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (bearerToken != null && bearerToken.startsWith("Bearer ")) {
            String token = bearerToken.substring(7);
            String email = jwtTokenProvider.getEmail(token);
            if (email != null) return email.trim().toLowerCase();
        }
        
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null ? auth.getName().trim().toLowerCase() : null;
    }

    @GetMapping("/resumes")
    public ResponseEntity<List<UserResume>> getUserResumes(HttpServletRequest request) {
        String email = getAuthenticatedEmail(request);
        if (email == null) {
            log.warn("Unauthorized access attempt to /resumes");
            return ResponseEntity.status(401).build();
        }
        log.info("Fetching resumes for user: {}", email);
        
        List<UserResume> resumes = resumeRepository.findByUserEmailOrderByCreatedAtDesc(email);
        return ResponseEntity.ok(resumes);
    }

    @PostMapping("/resumes")
    public ResponseEntity<UserResume> saveUserResume(@RequestBody UserResume resume, HttpServletRequest request) {
        String email = getAuthenticatedEmail(request);
        
        resume.setUserEmail(email);
        resume.setCreatedAt(LocalDateTime.now());
        if(resume.getResumeId() == null) {
            resume.setResumeId("R-" + UUID.randomUUID().toString().substring(0, 6).toUpperCase());
        }
        
        UserResume saved = resumeRepository.save(resume);
        return ResponseEntity.ok(saved);
    }

    @GetMapping("/payments")
    public ResponseEntity<List<UserPayment>> getUserPayments(HttpServletRequest request) {
        String email = getAuthenticatedEmail(request);
        if (email == null) {
            log.warn("Unauthorized access attempt to /payments");
            return ResponseEntity.status(401).build();
        }
        log.info("Fetching payments for user: {}", email);
        try {
            List<UserPayment> payments = paymentRepository.findByUserEmailOrderByCreatedAtDesc(email);
            log.info("Found {} payment(s) for user: {}", payments.size(), email);
            return ResponseEntity.ok(payments);
        } catch (Exception e) {
            log.error("Error fetching payments for {}: {}", email, e.getMessage());
            return ResponseEntity.internalServerError().build();
        }
    }

    @PostMapping("/payments")
    public ResponseEntity<UserPayment> saveUserPayment(@RequestBody UserPayment payment, HttpServletRequest request) {
        String email = getAuthenticatedEmail(request);
        
        payment.setUserEmail(email);
        payment.setCreatedAt(LocalDateTime.now());
        if(payment.getTransactionId() == null) {
            payment.setTransactionId("TXN-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        }
        if(payment.getStatus() == null) {
            payment.setStatus("Completed");
        }
        
        UserPayment saved = paymentRepository.save(payment);
        return ResponseEntity.ok(saved);
    }

    @GetMapping("/payments/all")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<UserPayment>> getAllPayments() {
        try {
            List<UserPayment> payments = paymentRepository.findAll();
            return ResponseEntity.ok(payments);
        } catch (Exception e) {
            log.error("Error fetching all payments: ", e);
            throw e;
        }
    }
}
