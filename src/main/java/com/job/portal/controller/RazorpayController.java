package com.job.portal.controller;

import com.job.portal.entity.UserPayment;
import com.job.portal.repository.UserPaymentRepository;
import com.job.portal.service.EmailAsyncService;
import com.razorpay.Order;
import com.razorpay.RazorpayClient;
import com.razorpay.RazorpayException;
import com.razorpay.Utils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/payments")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
@Slf4j
public class RazorpayController {

    private final UserPaymentRepository paymentRepository;
    private final EmailAsyncService emailService;

    @Value("${razorpay.key.id}")
    private String keyId;

    @Value("${razorpay.key.secret}")
    private String keySecret;


    @PostMapping("/create-order")
    public ResponseEntity<?> createOrder(@RequestBody Map<String, Object> data) {
        try {
            int amount = Integer.parseInt(data.get("amount").toString());
            
            if (amount < 100 || amount > 50000000) { // Max 5 Lakhs INR per txn
                return ResponseEntity.badRequest().body(Map.of("error", "Amount must be between ₹1 and ₹500,000"));
            }

            RazorpayClient razorpay = new RazorpayClient(keyId.trim(), keySecret.trim());

            JSONObject orderRequest = new JSONObject();
            orderRequest.put("amount", amount); // amount in the smallest currency unit
            orderRequest.put("currency", data.getOrDefault("currency", "INR"));
            orderRequest.put("receipt", "txn_" + UUID.randomUUID().toString().substring(0, 8));

            Order order = razorpay.orders.create(orderRequest);
            
            Map<String, Object> response = new HashMap<>();
            response.put("order_id", order.get("id"));
            response.put("amount", order.get("amount"));
            response.put("currency", order.get("currency"));
            
            return ResponseEntity.ok(response);
        } catch (RazorpayException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Razorpay Error: " + e.getMessage() + ". Please check your API keys."));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Error creating order: " + e.getMessage() + " | keyId=" + keyId));
        }
    }

    @PostMapping("/verify-payment")
    public ResponseEntity<?> verifyPayment(@RequestBody Map<String, String> data) {
        try {
            String razorpayOrderId = data.get("razorpay_order_id");
            String razorpayPaymentId = data.get("razorpay_payment_id");
            String razorpaySignature = data.get("razorpay_signature");

            // Custom fields for storage
            String donorName = data.getOrDefault("name", "Guest Supporter");
            String donorEmail = data.getOrDefault("email", "guest@chaitanyatechworld.com").trim().toLowerCase();
            String amount = data.getOrDefault("amount", "0");
            String itemName = data.getOrDefault("itemName", "Support Contribution");
            String itemId = data.getOrDefault("itemId", null);
            String itemType = data.getOrDefault("itemType", "SUPPORT");

            if (razorpayOrderId == null || razorpayPaymentId == null || razorpaySignature == null) {
                return ResponseEntity.badRequest().body(Map.of("error", "Missing required fields"));
            }

            String payload = razorpayOrderId + "|" + razorpayPaymentId;
            boolean isValidSignature = Utils.verifySignature(payload, razorpaySignature, keySecret.trim());

            if (isValidSignature) {
                // Prevent Replay Attacks: Check if payment was already processed
                if (paymentRepository.existsByTransactionId(razorpayPaymentId)) {
                    log.warn("Replay attack detected or duplicate payment submission for txn: {}", razorpayPaymentId);
                    return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", "Payment already processed"));
                }

                // 1. Save to Database
                UserPayment payment = new UserPayment();
                payment.setUserEmail(donorEmail);
                payment.setUserName(donorName);
                payment.setTransactionId(razorpayPaymentId);
                payment.setAmount(amount);
                payment.setItemName(itemName);
                payment.setItemId(itemId);
                payment.setItemType(itemType);
                payment.setPaymentMethod("Razorpay");
                payment.setStatus("Completed");
                payment.setCreatedAt(LocalDateTime.now());
                
                UserPayment saved = paymentRepository.save(payment);
                log.info("Payment recorded: txnId={}, amount={}, user={}", saved.getTransactionId(), saved.getAmount(), saved.getUserEmail());

                // 2. Trigger Emails
                String adminHtml = "<html><body style='font-family: Arial, sans-serif; color: #333;'>" +
                    "<div style='max-width: 600px; margin: 0 auto; border: 1px solid #eee; border-radius: 10px; overflow: hidden;'>" +
                    "<div style='background: #6366f1; padding: 20px; text-align: center; color: white;'>" +
                    "<h2>New Support Received!</h2>" +
                    "</div>" +
                    "<div style='padding: 30px;'>" +
                    "<p>Hello Admin,</p>" +
                    "<p>You have received a new support contribution from the community.</p>" +
                    "<table style='width: 100%; border-collapse: collapse; margin-top: 20px;'>" +
                    "<tr><td style='padding: 10px; border-bottom: 1px solid #eee;'><b>Supporter</b></td><td style='padding: 10px; border-bottom: 1px solid #eee;'>" + donorName + "</td></tr>" +
                    "<tr><td style='padding: 10px; border-bottom: 1px solid #eee;'><b>Email</b></td><td style='padding: 10px; border-bottom: 1px solid #eee;'>" + donorEmail + "</td></tr>" +
                    "<tr><td style='padding: 10px; border-bottom: 1px solid #eee;'><b>Amount</b></td><td style='padding: 10px; border-bottom: 1px solid #eee; color: #10b981; font-weight: bold;'>₹" + amount + "</td></tr>" +
                    "<tr><td style='padding: 10px; border-bottom: 1px solid #eee;'><b>Transaction ID</b></td><td style='padding: 10px; border-bottom: 1px solid #eee; font-family: monospace;'>" + razorpayPaymentId + "</td></tr>" +
                    "<tr><td style='padding: 10px; border-bottom: 1px solid #eee;'><b>Date</b></td><td style='padding: 10px; border-bottom: 1px solid #eee;'>" + LocalDateTime.now() + "</td></tr>" +
                    "</table>" +
                    "</div>" +
                    "<div style='background: #f9fafb; padding: 15px; text-align: center; font-size: 12px; color: #9ca3af;'>" +
                    "Chaitanya Tech World Admin Notification" +
                    "</div>" +
                    "</div></body></html>";

                String userHtml = "<html><body style='font-family: Arial, sans-serif; color: #333;'>" +
                    "<div style='max-width: 600px; margin: 0 auto; border: 1px solid #eee; border-radius: 10px; overflow: hidden;'>" +
                    "<div style='background: #6366f1; padding: 20px; text-align: center; color: white;'>" +
                    "<h2>Thank You for Your Support!</h2>" +
                    "</div>" +
                    "<div style='padding: 30px;'>" +
                    "<p>Hi <b>" + donorName + "</b>,</p>" +
                    "<p>We have successfully received your contribution of <span style='color: #6366f1; font-weight: bold;'>₹" + amount + "</span>.</p>" +
                    "<p>Your kindness and generosity help us maintain Chaitanya Tech World as an independent platform for professional digital tools.</p>" +
                    "<div style='background: #f3f4f6; padding: 20px; border-radius: 8px; margin-top: 20px;'>" +
                    "<h4 style='margin-top: 0;'>Transaction Receipt</h4>" +
                    "<p style='margin-bottom: 5px; font-size: 14px;'><b>Amount Paid:</b> ₹" + amount + "</p>" +
                    "<p style='margin-bottom: 5px; font-size: 14px;'><b>Transaction ID:</b> " + razorpayPaymentId + "</p>" +
                    "<p style='margin-bottom: 0; font-size: 14px;'><b>Status:</b> Completed</p>" +
                    "</div>" +
                    "<p style='margin-top: 25px;'>Best Regards,<br/><b>Chaitanya Gidijala</b><br/>Founder, Chaitanya Tech World</p>" +
                    "</div>" +
                    "<div style='background: #f9fafb; padding: 15px; text-align: center; font-size: 12px; color: #9ca3af;'>" +
                    "You are receiving this because you made a contribution to Chaitanya Tech World." +
                    "</div>" +
                    "</div></body></html>";

                emailService.sendSupportEmails(donorName, donorEmail, amount, razorpayPaymentId, adminHtml, userHtml);

                return ResponseEntity.ok(Map.of("status", "success", "message", "Payment verified and recorded"));
            } else {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "Invalid signature"));
            }
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Error verifying payment: " + e.getMessage()));
        }
    }
}
