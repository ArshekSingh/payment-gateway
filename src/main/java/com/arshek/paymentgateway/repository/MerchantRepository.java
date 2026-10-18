package com.arshek.paymentgateway.repository;

import com.arshek.paymentgateway.domain.Merchant;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MerchantRepository extends JpaRepository<Merchant, Long> {

    Optional<Merchant> findByApiKeyHash(String apiKeyHash);

    boolean existsByEmail(String email);
}
