package com.arshek.paymentgateway.repository;

import com.arshek.paymentgateway.domain.WebhookEvent;
import com.arshek.paymentgateway.domain.WebhookStatus;
import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;

public interface WebhookEventRepository extends JpaRepository<WebhookEvent, Long> {

    /** {@code lock.timeout = -2} requests SKIP LOCKED so multiple instances never claim the same row. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "-2"))
    @Query("select e from WebhookEvent e where e.status = :status and e.nextAttemptAt <= :now "
            + "order by e.nextAttemptAt")
    List<WebhookEvent> findDue(@Param("status") WebhookStatus status, @Param("now") Instant now,
                               Pageable pageable);

    long countByMerchantId(Long merchantId);
}
