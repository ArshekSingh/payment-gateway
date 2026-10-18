package com.arshek.paymentgateway.util;

public final class CardUtils {

    private CardUtils() {
    }

    /** Luhn checksum over a digits-only card number. */
    public static boolean isValidLuhn(String number) {
        if (number == null || number.length() < 12 || number.length() > 19) {
            return false;
        }
        int sum = 0;
        boolean doubleIt = false;
        for (int i = number.length() - 1; i >= 0; i--) {
            char c = number.charAt(i);
            if (c < '0' || c > '9') {
                return false;
            }
            int digit = c - '0';
            if (doubleIt) {
                digit *= 2;
                if (digit > 9) {
                    digit -= 9;
                }
            }
            sum += digit;
            doubleIt = !doubleIt;
        }
        return sum % 10 == 0;
    }

    public static String brandOf(String number) {
        if (number.startsWith("4")) {
            return "visa";
        }
        if (number.startsWith("34") || number.startsWith("37")) {
            return "amex";
        }
        if (number.length() >= 4) {
            int firstTwo = Integer.parseInt(number.substring(0, 2));
            int firstFour = Integer.parseInt(number.substring(0, 4));
            if ((firstTwo >= 51 && firstTwo <= 55) || (firstFour >= 2221 && firstFour <= 2720)) {
                return "mastercard";
            }
            if (number.startsWith("6011") || number.startsWith("65")) {
                return "discover";
            }
        }
        return "unknown";
    }

    public static String last4(String number) {
        return number.substring(number.length() - 4);
    }
}
