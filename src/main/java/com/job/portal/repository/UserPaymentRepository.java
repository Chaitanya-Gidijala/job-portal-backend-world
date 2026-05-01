package com.job.portal.repository;

import com.job.portal.entity.UserPayment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface UserPaymentRepository extends JpaRepository<UserPayment, Long> {
    List<UserPayment> findByUserEmailOrderByCreatedAtDesc(String userEmail);
    boolean existsByTransactionId(String transactionId);
}
