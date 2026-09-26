package dev.portfolio.tracepilot;

import dev.portfolio.foundation.*;
import java.util.*;

/** Aggregates measured observations. No randomly generated latency, price, score or usage data. */
public final class RunSummary {
    private RunSummary() {
    }
    public static Map<String, Object> calculate(List<Map<String, Object>> results, Map<String, Object> settings, int expected) {
        long passed=results.stream().filter(r->Json.bool(r, "passed", false)).count();
        long errors=results.stream().filter(r->!Json.string(r, "error", "").isBlank()).count();
        List<Long> latency=results.stream().filter(r->Json.string(r, "error", "").isBlank()).map(r->Json.integer(r, "latencyMs", 0)).toList();
        Map<String, List<Double>> values=new LinkedHashMap<>();
        for(Map<String, Object> result:results) {
            if(result.get("scores") instanceof Map<?, ?>) {
                Json.object(result.get("scores")).forEach((k, v)-> {
                    if(v instanceof Number n)values.computeIfAbsent(k, x->new ArrayList<>()).add(n.doubleValue());
                });
            }
        }
        Map<String, Object> averages=new LinkedHashMap<>();
        values.forEach((k, v)->averages.put(k, Text.round(v.stream().mapToDouble(Double::doubleValue).average().orElse(0))));
        double rate=results.isEmpty()?0:(double)passed/results.size();
        double p95=Text.percentile(latency, .95);
        long input=results.stream().mapToLong(r->Json.integer(r, "inputTokens", 0)).sum(), output=results.stream().mapToLong(r->Json.integer(r, "outputTokens", 0)).sum();
        List<String> gates=new ArrayList<>();
        if(results.size()!=expected)gates.add("incomplete");
        if(rate<Json.number(settings, "minPassRate", .8))gates.add("pass_rate");
        if(p95>Json.number(settings, "maxLatencyMs", 3000))gates.add("p95_latency");
        if(errors>0)gates.add("provider_errors");
        return Json.map("completed", results.size(), "expected", expected, "passed", passed, "failed", results.size()-passed, "errors", errors, "passRate", Text.round(rate), "p50Ms", Text.percentile(latency, .5), "p95Ms", p95, "inputTokens", input, "outputTokens", output, "averages", averages, "gatePassed", gates.isEmpty(), "gateFailures", gates, "estimatedCost", Text.round(input/1000.0*Json.number(settings, "inputPricePer1k", 0)+output/1000.0*Json.number(settings, "outputPricePer1k", 0)), "pricingNote", "User-entered unit prices; token accounting can be an estimate");
    }
}
