package com.arshek.paymentgateway.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class UtilTest {

    @Test
    void luhnAcceptsKnownTestCardsAndRejectsGarbage() {
        assertThat(CardUtils.isValidLuhn("4242424242424242")).isTrue();
        assertThat(CardUtils.isValidLuhn("5555555555554444")).isTrue();
        assertThat(CardUtils.isValidLuhn("4242424242424241")).isFalse();
        assertThat(CardUtils.isValidLuhn("4242-4242-4242-4242")).isFalse();
        assertThat(CardUtils.isValidLuhn("123")).isFalse();
        assertThat(CardUtils.isValidLuhn(null)).isFalse();
    }

    @Test
    void detectsBrands() {
        assertThat(CardUtils.brandOf("4242424242424242")).isEqualTo("visa");
        assertThat(CardUtils.brandOf("5555555555554444")).isEqualTo("mastercard");
        assertThat(CardUtils.brandOf("378282246310005")).isEqualTo("amex");
        assertThat(CardUtils.brandOf("6011111111111117")).isEqualTo("discover");
    }

    @Test
    void hmacMatchesKnownVector() {
        // RFC 4231 test case 2
        assertThat(Crypto.hmacSha256Hex("Jefe", "what do ya want for nothing?"))
                .isEqualTo("5bdcc146bf60754e6a042426089575c75a003f089d2739839dec58b964ec3843");
    }

    @Test
    void constantTimeEqualsHandlesNullsAndMismatch() {
        assertThat(Crypto.constantTimeEquals("a", "a")).isTrue();
        assertThat(Crypto.constantTimeEquals("a", "b")).isFalse();
        assertThat(Crypto.constantTimeEquals("a", null)).isFalse();
    }

    @Test
    void publicIdsAreUniqueAndPrefixed() {
        assertThat(Crypto.publicId("pay")).startsWith("pay_").isNotEqualTo(Crypto.publicId("pay"));
    }
}
