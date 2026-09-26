package dev.portfolio.foundation;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;
import java.util.regex.Pattern;

public final class Checks {
    private static final Pattern ID = Pattern.compile("[a-zA-Z0-9][a-zA-Z0-9_.-]{0,95}");
    private Checks() {
    }
    public static String id(String value) {
        if (value == null || !ID.matcher(value).matches()) throw Problem.invalid("Invalid identifier");
        return value;
    }
    public static String text(String value, String field, int max) {
        if (value == null || value.isBlank()) throw Problem.invalid(field + " is required");
        if (value.length() > max) throw Problem.invalid(field + " exceeds " + max + " characters");
        if (value.indexOf('\0') >= 0) throw Problem.invalid(field + " contains a null character");
        return value.strip();
    }
    public static String optional(String value, String field, int max) {
        if (value == null) return "";
        if (value.length() > max || value.indexOf('\0') >= 0) throw Problem.invalid(field + " is too long or contains null characters");
        return value;
    }
    public static int range(long value, String field, int min, int max) {
        if (value < min || value > max) throw Problem.invalid(field + " must be between " + min + " and " + max);
        return (int)value;
    }
    public static double range(double value, String field, double min, double max) {
        if (!Double.isFinite(value) || value < min || value > max) throw Problem.invalid(field + " is outside its allowed range");
        return value;
    }
    public static String oneOf(String value, String field, String... allowed) {
        if (!Arrays.asList(allowed).contains(value)) throw Problem.invalid(field + " must be one of " + String.join(", ", allowed));
        return value;
    }
    public static String uuid() {
        return UUID.randomUUID().toString();
    }
    public static String now() {
        return Instant.now().toString();
    }
    public static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
    public static boolean sameSecret(String expected, String actual) {
        return MessageDigest.isEqual(sha256(expected).getBytes(StandardCharsets.US_ASCII), sha256(actual).getBytes(StandardCharsets.US_ASCII));
    }
    /** Logical repository paths only. These are not resolved against the machine's filesystem. */
    public static String logicalPath(String value) {
        text(value, "path", 240);
        if (value.startsWith("/") || value.contains("\\") || value.contains(":")) throw Problem.invalid("Use a relative POSIX repository path");
        for (String part : value.split("/", -1)) if (part.isBlank() || part.equals(".") || part.equals("..")) throw Problem.invalid("Unsafe repository path");
        return value;
    }
}
