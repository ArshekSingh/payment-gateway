package com.arshek.paymentgateway.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.arshek.paymentgateway.exception.ConflictException;
import com.arshek.paymentgateway.exception.InvalidRequestException;
import org.junit.jupiter.api.Test;

class PaymentTest {

    private Payment newPayment(long amount) {
        Merchant merchant = new Merchant("Acme", "acme@example.com", "hash", "sk_prefix", "whsec", null);
        return new Payment(merchant, amount, "USD", true, null, null, null, "fp");
    }

    @Test
    void happyPathCreatedToRefunded() {
        Payment payment = newPayment(1000);
        payment.authorize("auth_1", "visa", "4242");
        payment.capture();
        payment.registerRefund(400);
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PARTIALLY_REFUNDED);
        assertThat(payment.refundableAmount()).isEqualTo(600);

        payment.registerRefund(600);
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.REFUNDED);
        assertThat(payment.refundableAmount()).isZero();
    }

    @Test
    void cannotCaptureUnauthorizedPayment() {
        Payment payment = newPayment(1000);
        assertThatThrownBy(payment::capture).isInstanceOf(ConflictException.class);
    }

    @Test
    void failedPaymentCanBeRetriedAndClearsFailure() {
        Payment payment = newPayment(1000);
        payment.fail("card_declined", "declined");
        assertThat(payment.canConfirm()).isTrue();

        payment.authorize("auth_2", "visa", "4242");
        assertThat(payment.getFailureCode()).isNull();
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.AUTHORIZED);
    }

    @Test
    void refundCannotExceedBalance() {
        Payment payment = newPayment(1000);
        payment.authorize("auth_1", "visa", "4242");
        payment.capture();
        assertThatThrownBy(() -> payment.registerRefund(1001)).isInstanceOf(InvalidRequestException.class);
    }

    @Test
    void cannotRefundBeforeCapture() {
        Payment payment = newPayment(1000);
        payment.authorize("auth_1", "visa", "4242");
        assertThatThrownBy(() -> payment.registerRefund(100)).isInstanceOf(ConflictException.class);
    }

    @Test
    void capturedPaymentCannotBeCancelled() {
        Payment payment = newPayment(1000);
        payment.authorize("auth_1", "visa", "4242");
        payment.capture();
        assertThatThrownBy(payment::cancel).isInstanceOf(ConflictException.class);
    }
}
