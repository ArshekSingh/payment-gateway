package com.arshek.paymentgateway.web;

import com.arshek.paymentgateway.dto.MerchantResponse;
import com.arshek.paymentgateway.dto.UpdateWebhookRequest;
import com.arshek.paymentgateway.security.MerchantPrincipal;
import com.arshek.paymentgateway.service.MerchantService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/merchant")
@RequiredArgsConstructor
public class MerchantController {

    private final MerchantService merchantService;

    @GetMapping
    public MerchantResponse me(@AuthenticationPrincipal MerchantPrincipal merchant) {
        return merchantService.get(merchant.id());
    }

    @PutMapping("/webhook")
    public MerchantResponse updateWebhook(@AuthenticationPrincipal MerchantPrincipal merchant,
                                          @Valid @RequestBody UpdateWebhookRequest request) {
        return merchantService.updateWebhookUrl(merchant.id(), request.webhookUrl());
    }
}
