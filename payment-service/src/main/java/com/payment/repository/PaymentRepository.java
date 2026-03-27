package com.payment.repository;

import com.payment.domain.Payment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    Optional<Payment> findByReceiptId(String receiptId);

    List<Payment> findByMemberId(Long memberId);
}
