package com.arshek.paymentgateway.dto;

import com.arshek.paymentgateway.util.CardUtils;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class CardNumberValidator implements ConstraintValidator<ValidCardNumber, String> {

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        // null/blank is reported by @NotBlank; this only checks format
        return value == null || value.isBlank() || CardUtils.isValidLuhn(value);
    }
}
