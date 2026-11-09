package lab;

final class Validation {
    private Validation() {}

    static String account(String value) {
        if (value == null || !value.matches("[a-zA-Z][a-zA-Z0-9._-]{0,19}"))
            throw new IllegalArgumentException("Use 1–20 letters, numbers, dots, underscores or hyphens; start with a letter.");
        return value;
    }

    static String label(String value) {
        if (value == null || value.isBlank() || value.length() > 64 ||
            value.chars().anyMatch(c -> Character.isISOControl(c)))
            throw new IllegalArgumentException("Names must contain 1–64 printable characters.");
        return value.strip();
    }

    static void password(char[] value) {
        if (value == null || value.length < 12 || value.length > 128)
            throw new IllegalArgumentException("Use a unique password between 12 and 128 characters.");
        boolean upper = false, lower = false, digit = false, other = false;
        for (char c : value) {
            upper |= Character.isUpperCase(c);
            lower |= Character.isLowerCase(c);
            digit |= Character.isDigit(c);
            other |= !Character.isLetterOrDigit(c);
        }
        if (!(upper && lower && digit && other))
            throw new IllegalArgumentException("Include uppercase, lowercase, a number, and a symbol.");
    }
}
