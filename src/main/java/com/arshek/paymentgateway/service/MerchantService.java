package com.arshek.paymentgateway.service;

import com.arshek.paymentgateway.domain.Merchant;
import com.arshek.paymentgateway.dto.CreateMerchantRequest;
import com.arshek.paymentgateway.dto.MerchantCreatedResponse;
import com.arshek.paymentgateway.dto.MerchantResponse;
import com.arshek.paymentgateway.dto.PageResponse;
import com.arshek.paymentgateway.exception.ConflictException;
import com.arshek.paymentgateway.exception.ResourceNotFoundException;
import com.arshek.paymentgateway.repository.MerchantRepository;
import com.arshek.paymentgateway.security.MerchantPrincipal;
import com.arshek.paymentgateway.util.Crypto;
import java.util.Locale;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MerchantService {

    private static final String API_KEY_PREFIX = "sk_";
    private static final int PREFIX_DISPLAY_LENGTH = 12;

    private final MerchantRepository merchantRepository;
    private final WebhookUrlValidator webhookUrlValidator;
    private final PaymentMapper mapper;

    @Transactional
    public MerchantCreatedResponse create(CreateMerchantRequest request) {
        String email = request.email().trim().toLowerCase(Locale.ROOT);
        if (merchantRepository.existsByEmail(email)) {
            throw new ConflictException("merchant_exists", "A merchant with this email already exists");
        }
        String webhookUrl = normalizeUrl(request.webhookUrl());
        if (webhookUrl != null) {
            webhookUrlValidator.validate(webhookUrl);
        }
        String apiKey = API_KEY_PREFIX + Crypto.randomToken(32);
        String webhookSecret = "whsec_" + Crypto.randomToken(24);
        Merchant merchant = merchantRepository.save(new Merchant(request.name().trim(), email,
                Crypto.sha256Hex(apiKey), apiKey.substring(0, PREFIX_DISPLAY_LENGTH), webhookSecret, webhookUrl));
        return new MerchantCreatedResponse(merchant.getPublicId(), merchant.getName(), merchant.getEmail(),
                merchant.getStatus(), apiKey, webhookSecret, merchant.getWebhookUrl(), merchant.getCreatedAt());
    }

    @Transactional(readOnly = true)
    public Optional<MerchantPrincipal> authenticate(String apiKey) {
        return merchantRepository.findByApiKeyHash(Crypto.sha256Hex(apiKey))
                .filter(Merchant::isActive)
                .map(m -> new MerchantPrincipal(m.getId(), m.getPublicId(), m.getName()));
    }

    @Transactional(readOnly = true)
    public MerchantResponse get(Long merchantId) {
        return mapper.toResponse(load(merchantId));
    }

    @Transactional(readOnly = true)
    public PageResponse<MerchantResponse> list(Pageable pageable) {
        return PageResponse.from(merchantRepository.findAll(pageable).map(mapper::toResponse));
    }

    @Transactional
    public MerchantResponse updateWebhookUrl(Long merchantId, String webhookUrl) {
        String normalized = normalizeUrl(webhookUrl);
        if (normalized != null) {
            webhookUrlValidator.validate(normalized);
        }
        Merchant merchant = load(merchantId);
        merchant.updateWebhookUrl(normalized);
        return mapper.toResponse(merchant);
    }

    private Merchant load(Long merchantId) {
        return merchantRepository.findById(merchantId)
                .orElseThrow(() -> new ResourceNotFoundException("Merchant", String.valueOf(merchantId)));
    }

    private static String normalizeUrl(String url) {
        return url == null || url.isBlank() ? null : url.trim();
    }
}
