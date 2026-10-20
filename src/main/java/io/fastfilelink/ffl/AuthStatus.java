package io.fastfilelink.ffl;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The account state the core reports with {@code ffl status}. {@code name}, {@code email} and
 * {@code serialNumber} are {@code null} when the core prints a placeholder for them. {@code points} is
 * whatever the server reports, which can be fractional (for example {@code 233.9}).
 */
public record AuthStatus(
    String name,
    String email,
    Level level,
    String serialNumber,
    double points,
    boolean registered
) {
    public enum Level { FREE, STANDARD, PLUS, BASIC }

    private static final Pattern FIELD = Pattern.compile("^\\s*(User|Email|Level|Serial|Points|Registered):\\s*(.*?)\\s*$");

    public boolean loggedIn() {
        return registered && email != null;
    }

    /**
     * Parses the English {@code ffl status} layout (the core prints text only, so it is run with
     * {@code FFL_LANGUAGE=en}); any other shape is reported with the raw text.
     */
    static AuthStatus parse(String text) {
        Objects.requireNonNull(text, "text");
        if (!text.contains("Authentication Status:")) {
            throw new IllegalStateException("Unrecognized ffl status output: " + text.strip());
        }
        Map<String, String> fields = new HashMap<>();
        for (String line : text.split("\\R")) {
            Matcher matcher = FIELD.matcher(line);
            if (matcher.matches()) {
                fields.put(matcher.group(1), matcher.group(2));
            }
        }
        return new AuthStatus(
            unlessPlaceholder(fields.get("User"), "Not logged in"),
            unlessPlaceholder(fields.get("Email"), "Not set"),
            level(fields.get("Level")),
            unlessPlaceholder(fields.get("Serial"), "None"),
            points(fields.get("Points")),
            "yes".equalsIgnoreCase(fields.get("Registered"))
        );
    }

    private static String unlessPlaceholder(String value, String placeholder) {
        return value == null || value.isBlank() || value.equalsIgnoreCase(placeholder) ? null : value;
    }

    private static Level level(String value) {
        if (value == null) {
            throw new IllegalStateException("ffl status output has no Level line");
        }
        try {
            return Level.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException caught) {
            throw new IllegalStateException("Unknown ffl account level: " + value, caught);
        }
    }

    private static double points(String value) {
        if (value == null || value.isBlank()) {
            return 0.0;
        }
        try {
            return Double.parseDouble(value.trim());
        } catch (NumberFormatException caught) {
            throw new IllegalStateException("Unparsable ffl points: " + value, caught);
        }
    }
}
