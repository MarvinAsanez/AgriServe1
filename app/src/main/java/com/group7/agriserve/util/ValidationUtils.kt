package com.group7.agriserve.util

/**
 * Common validation utilities for Philippine government and contact credentials.
 */
object ValidationUtils {

    /**
     * Normalizes and validates a Philippine mobile phone number into the standard 11-digit format (09XXXXXXXXX or 08XXXXXXXXX).
     * 
     * Handles:
     * - Standard 11-digit: "09171234567", "08171234567", "08961234567"
     * - 10-digit without leading 0: "9171234567", "8961234567" -> "09171234567"
     * - International "+63": "+63 917 123 4567", "+639171234567" -> "09171234567"
     * - Country code without plus: "639171234567" -> "09171234567"
     * - Accidental duplicate prefix from pre-filled "09": "0909171234567" -> "09171234567"
     * - Redundant "+6309": "+6309171234567", "6309171234567" -> "09171234567"
     * - Formatted numbers with spaces, dashes, parentheses: "(0917) 123-4567", "0917-123-4567" -> "09171234567"
     * 
     * Returns the clean 11-digit "09XXXXXXXXX" or "08XXXXXXXXX" string if valid, or null if invalid.
     */
    fun normalizePhilippinePhone(input: String?): String? {
        if (input.isNullOrBlank()) return null

        // 1. Strip spaces, dashes, parentheses, dots, slashes, plus signs
        val cleaned = input.trim()
            .replace(" ", "")
            .replace("-", "")
            .replace("(", "")
            .replace(")", "")
            .replace(".", "")
            .replace("/", "")
            .replace("+", "")

        // 2. Extract digits only
        val digits = cleaned.filter { it.isDigit() }
        if (digits.length < 10) return null

        // 3. If user provided at least 10 digits and the last 10 digits start with 9 or 8
        // (Standard Philippine national significant numbers: 9XXXXXXXXX or 8XXXXXXXXX)
        val last10 = digits.takeLast(10)
        if ((last10.startsWith("9") || last10.startsWith("8")) && last10.length == 10) {
            val candidate = "0$last10"
            if (candidate.length == 11) {
                return candidate
            }
        }

        // 4. If exactly 11 digits starting with 0
        if (digits.length == 11 && digits.startsWith("0")) {
            return digits
        }

        // 5. If 12 digits starting with 63 (e.g. 63XXXXXXXXXX)
        if (digits.length == 12 && digits.startsWith("63")) {
            return "0" + digits.substring(2)
        }

        // 6. If 13 digits starting with 630 (e.g. 630XXXXXXXXXX)
        if (digits.length == 13 && digits.startsWith("630")) {
            return "0" + digits.substring(3)
        }

        // 7. If 13 digits starting with 0909 or 0808 (accidental duplicate prefix)
        if (digits.length == 13 && (digits.startsWith("0909") || digits.startsWith("0808"))) {
            return digits.substring(2)
        }

        return null
    }

    /**
     * Checks if a phone string matches the standard 11-digit Philippine mobile format.
     */
    fun isValidPhilippineMobile(phone: String?): Boolean {
        return normalizePhilippinePhone(phone) != null
    }
}
