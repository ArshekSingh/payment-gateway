package com.arshek.paymentgateway.repository;

import com.arshek.paymentgateway.domain.Refund;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RefundRepository extends JpaRepository<Refund, Long> {

    @Query("select r from Refund r where r.payment.id = :paymentId and r.idempotencyKey = :key")
    Optional<Refund> findByIdempotencyKey(@Param("paymentId") Long paymentId, @Param("key") String key);

    @Query("select r from Refund r where r.payment.id = :paymentId")
    Page<Refund> findByPayment(@Param("paymentId") Long paymentId, Pageable pageable);
}
