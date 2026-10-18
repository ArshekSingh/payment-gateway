package com.arshek.paymentgateway.service;

import com.arshek.paymentgateway.domain.Merchant;
import com.arshek.paymentgateway.domain.Payment;
import com.arshek.paymentgateway.domain.Refund;
import com.arshek.paymentgateway.dto.MerchantResponse;
import com.arshek.paymentgateway.dto.PaymentResponse;
import com.arshek.paymentgateway.dto.RefundResponse;
import org.springframework.stereotype.Component;

@Component
public class PaymentMapper {

    public PaymentResponse toResponse(Payment p) {
        return new PaymentResponse(p.getPublicId(), p.getAmount(), p.getCurrency(), p.getStatus(),
                p.getDescription(), p.getMerchantReference(), p.isCaptureAutomatically(),
                p.getCardBrand(), p.getCardLast4(), p.getAmountRefunded(),
                p.getFailureCode(), p.getFailureMessage(),
                p.getCreatedAt(), p.getAuthorizedAt(), p.getCapturedAt());
    }

    /** Takes the payment's public id explicitly so mapping never touches the lazy payment association. */
    public RefundResponse toResponse(Refund r, String paymentPublicId) {
        return new RefundResponse(r.getPublicId(), paymentPublicId, r.getAmount(), r.getStatus(),
                r.getReason(), r.getFailureCode(), r.getCreatedAt());
    }

    public MerchantResponse toResponse(Merchant m) {
        return new MerchantResponse(m.getPublicId(), m.getName(), m.getEmail(), m.getStatus(),
                m.getApiKeyPrefix(), m.getWebhookUrl(), m.getCreatedAt());
    }
}
