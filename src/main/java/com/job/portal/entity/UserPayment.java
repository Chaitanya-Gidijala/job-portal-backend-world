package com.job.portal.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Entity
@Table(name = "user_payments")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserPayment {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_email", nullable = false)
    private String userEmail;

    @Column(name = "user_name")
    private String userName;

    @Column(name = "transaction_id", nullable = false, unique = true)
    private String transactionId;

    @Column(name = "item_name", nullable = false)
    private String itemName;

    @Column(name = "item_id")
    private String itemId;

    @Column(name = "item_type")
    private String itemType; // e.g., "SUPPORT", "RESUME"

    @Column(name = "amount", nullable = false)
    private String amount;

    @Column(name = "payment_method", nullable = false)
    private String paymentMethod;

    @Column(name = "status", nullable = false)
    private String status;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
}
