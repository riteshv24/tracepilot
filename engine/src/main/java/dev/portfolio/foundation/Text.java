package dev.portfolio.foundation;

import java.text.Normalizer;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public final class Text {
    private static final Pattern WORD = Pattern.compile("[\\p{L}\\p{N}]+(?:[-_][\\p{L}\\p{N}]+)*");
    private static final Set<String> STOP = Set.of("the", "a", "an", "and", "or", "to", "of", "in", "is", "are", "was", "were", "be", "for", "on", "at", "with", "by", "it", "its", "this", "that", "as", "from", "how", "what", "which", "who", "does", "do", "can", "i", "we", "you", "your", "our");
    private Text() {
    }
    public static List<String> tokens(String value) {
        var matcher = WORD.matcher(Normalizer.normalize(value.toLowerCase(Locale.ROOT), Normalizer.Form.NFKC));
        List<String> out = new ArrayList<>();
        while (matcher.find()) out.add(matcher.group());
        return out;
    }
    public static List<String> terms(String value) {
        return tokens(value).stream().filter(t -> !STOP.contains(t)).toList();
    }
    public static String normalize(String value) {
        return String.join(" ", tokens(value));
    }
    public static Map<String, Integer> frequencies(List<String> tokens) {
        Map<String, Integer> counts = new HashMap<>();
        tokens.forEach(t -> counts.merge(t, 1, Integer::sum));
        return counts;
    }
    public static String excerpt(String value, int max) {
        return value.length() <= max ? value : value.substring(0, Math.max(0, max-1)) + "…";
    }
    public static String csv(Object value) {
        String text = String.valueOf(value == null ? "" : value);
        // Prevent spreadsheet formula execution when reviewers open exported evidence.
        if (text.matches("(?s)^[\\s]*[=+@-].*")) text = "'" + text;
        return "\"" + text.replace("\"", "\"\"") + "\"";
    }
    public static String xml(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&apos;");
    }
    public static List<String> sentences(String value) {
        return Arrays.stream(value.split("(?<=[.!?])\\s+|\\n+")).map(String::strip).filter(s -> !s.isBlank()).toList();
    }
    public static double percentile(List<? extends Number> values, double quantile) {
        if (values.isEmpty()) return 0;
        List<Double> sorted = values.stream().map(Number::doubleValue).sorted().toList();
        return sorted.get(Math.max(0, Math.min(sorted.size()-1, (int)Math.ceil(quantile*sorted.size())-1)));
    }
    public static double round(double value) {
        return Math.round(value*10000.0)/10000.0;
    }
    public static Set<String> uniqueTerms(String value) {
        return new LinkedHashSet<>(terms(value));
    }
    public static String redacted(String value) {
        return value.replaceAll("(?i)(password|secret|api[_-]?key|token)(\\s*[:=]\\s*[\"'])([^\"'\\n]+)", "$1$2[REDACTED]");
    }
}
