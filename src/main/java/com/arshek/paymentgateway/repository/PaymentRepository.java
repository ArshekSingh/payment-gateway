package com.arshek.paymentgateway.repository;

import com.arshek.paymentgateway.domain.Payment;
import com.arshek.paymentgateway.domain.PaymentStatus;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    @Query("select p from Payment p where p.publicId = :publicId and p.merchant.id = :merchantId")
    Optional<Payment> findOwned(@Param("publicId") String publicId, @Param("merchantId") Long merchantId);

    /** Row-locks the payment so concurrent confirm/capture/refund calls are serialised. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Payment p where p.publicId = :publicId and p.merchant.id = :merchantId")
    Optional<Payment> findOwnedForUpdate(@Param("publicId") String publicId, @Param("merchantId") Long merchantId);

    @Query("select p from Payment p where p.merchant.id = :merchantId and p.idempotencyKey = :key")
    Optional<Payment> findByIdempotencyKey(@Param("merchantId") Long merchantId, @Param("key") String key);

    @Query("select p from Payment p where p.merchant.id = :merchantId")
    Page<Payment> findByMerchant(@Param("merchantId") Long merchantId, Pageable pageable);

    @Query("select p from Payment p where p.merchant.id = :merchantId and p.status = :status")
    Page<Payment> findByMerchantAndStatus(@Param("merchantId") Long merchantId,
                                          @Param("status") PaymentStatus status, Pageable pageable);
}
