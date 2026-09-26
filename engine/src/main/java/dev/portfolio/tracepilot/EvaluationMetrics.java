package dev.portfolio.tracepilot;

import dev.portfolio.foundation.*;
import java.util.*;

/** Reproducible heuristic evaluators, not a ground-truth hallucination detector.
 * Reference metrics are omitted when no reference exists; absence is never reported as perfect quality.
 */
public final class EvaluationMetrics {
    public record Metric(String name, Double value, boolean applicable, String explanation) {
        Map<String, Object> json() {
            return Json.map("name", name, "value", value==null?null:Text.round(value), "applicable", applicable, "explanation", explanation);
        }
    }
    @FunctionalInterface public interface Evaluator {
        Metric evaluate(CaseSpec item, String response);
    }
    private final List<Evaluator> evaluators;
    public EvaluationMetrics() {
        evaluators=List.of(this::exact, this::f1, this::support, this::phrases, this::format, this::refusal);
    }
    public Map<String, Object> evaluate(CaseSpec item, String response, long latency, double minF1, double minSupport, long maxLatency) {
        List<Metric> metrics=evaluators.stream().map(e->e.evaluate(item, response)).toList();
        Map<String, Double> scores=new LinkedHashMap<>();
        metrics.stream().filter(Metric::applicable).forEach(m->scores.put(m.name(), m.value()));
        List<String> failures=new ArrayList<>();
        if(response.isBlank())failures.add("empty_response");
        if(scores.containsKey("tokenF1")&&scores.get("tokenF1")<minF1)failures.add("reference_f1");
        if(!item.expectRefusal()&&scores.containsKey("contextSupport")&&scores.get("contextSupport")<minSupport)failures.add("context_support");
        if(scores.containsKey("requiredPhrases")&&scores.get("requiredPhrases")<1)failures.add("required_phrases");
        if(scores.containsKey("jsonValid")&&scores.get("jsonValid")<1)failures.add("json_format");
        if(scores.containsKey("expectedRefusal")&&scores.get("expectedRefusal")<1)failures.add("expected_refusal");
        if(latency>maxLatency)failures.add("latency_limit");
        return Json.map("passed", failures.isEmpty(), "failures", failures, "scores", scores, "metrics", metrics.stream().map(Metric::json).toList());
    }
    public double tokenF1(String reference, String response) {
        Map<String, Integer> expected=Text.frequencies(Text.tokens(reference));
        Map<String, Integer> actual=Text.frequencies(Text.tokens(response));
        int expectedCount=expected.values().stream().mapToInt(Integer::intValue).sum(), actualCount=actual.values().stream().mapToInt(Integer::intValue).sum();
        if(expectedCount==0||actualCount==0)return expectedCount==actualCount?1:0;
        int overlap=expected.entrySet().stream().mapToInt(e->Math.min(e.getValue(), actual.getOrDefault(e.getKey(), 0))).sum();
        double precision=(double)overlap/actualCount, recall=(double)overlap/expectedCount;
        return precision+recall==0?0:2*precision*recall/(precision+recall);
    }
    private Metric exact(CaseSpec item, String response) {
        return item.reference().isBlank()?na("exactMatch", "No reference supplied"):new Metric("exactMatch", Text.normalize(item.reference()).equals(Text.normalize(response))?1d:0d, true, "Case-insensitive token normalization");
    }
    private Metric f1(CaseSpec item, String response) {
        return item.reference().isBlank()?na("tokenF1", "No reference supplied"):new Metric("tokenF1", tokenF1(item.reference(), response), true, "Multiset token overlap with the reference; not semantic correctness");
    }
    private Metric support(CaseSpec item, String response) {
        Set<String> context=Text.uniqueTerms(item.context());
        List<String> output=Text.terms(response);
        if(context.isEmpty())return na("contextSupport", "No non-stopword context terms available");
        double score=output.isEmpty()?0:(double)output.stream().filter(context::contains).count()/output.size();
        return new Metric("contextSupport", score, true, "Output content-token coverage in source context; a lexical proxy, not factual verification");
    }
    private Metric phrases(CaseSpec item, String response) {
        if(item.requiredPhrases().isEmpty())return na("requiredPhrases", "No phrase requirements configured");
        String normalized=" "+Text.normalize(response)+" ";
        long matched=item.requiredPhrases().stream().filter(p->normalized.contains(" "+Text.normalize(p)+" ")).count();
        return new Metric("requiredPhrases", (double)matched/item.requiredPhrases().size(), true, "Fraction of required normalized phrases present as complete token sequences");
    }
    private Metric format(CaseSpec item, String response) {
        if(!item.format().equals("json"))return na("jsonValid", "Plain-text case");
        boolean valid;
        try {
            Json.parse(response);
            valid=true;
        } catch(IllegalArgumentException e) {
            valid=false;
        }
        return new Metric("jsonValid", valid?1d:0d, true, "Strict JSON syntax only; does not validate a JSON Schema");
    }
    private Metric refusal(CaseSpec item, String response) {
        if(!item.expectRefusal())return na("expectedRefusal", "Not a refusal case");
        String normalized=Text.normalize(response);
        boolean refusal=List.of("not enough information", "insufficient information", "cannot determine", "not provided", "not available", "do not know", "cannot answer").stream().anyMatch(normalized::contains);
        return new Metric("expectedRefusal", refusal?1d:0d, true, "Presence of a small explicit refusal phrase set; not a safety classifier");
    }
    private static Metric na(String name, String explanation) {
        return new Metric(name, null, false, explanation);
    }
}
