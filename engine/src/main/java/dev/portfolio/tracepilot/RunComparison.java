package dev.portfolio.tracepilot;

import dev.portfolio.foundation.*;
import java.util.*;

public final class RunComparison {
    private RunComparison() {
    }
    public static Map<String, Object> compare(Map<String, Object> baseline, Map<String, Object> candidate) {
        if(!Json.string(baseline, "datasetId", "").equals(Json.string(candidate, "datasetId", ""))||Json.integer(baseline, "datasetVersion", 0)!=Json.integer(candidate, "datasetVersion", 0)) throw Problem.conflict("Paired comparisons require the same dataset version");
        Map<String, Map<String, Object>> left=new LinkedHashMap<>();
        Json.list(baseline, "results").forEach(v-> {
            Map<String, Object> row=Json.object(v);
            left.put(Json.string(row, "caseId", ""), row);
        });
        List<Map<String, Object>> changes=new ArrayList<>();
        int regressions=0, improvements=0;
        for(Object value:Json.list(candidate, "results")) {
            Map<String, Object> right=Json.object(value), old=left.get(Json.string(right, "caseId", ""));
            if(old==null)continue;
            boolean before=Json.bool(old, "passed", false), after=Json.bool(right, "passed", false);
            if(before&&!after)regressions++;
            if(!before&&after)improvements++;
            Map<String, Object> oldScores=Json.object(old.get("scores")), newScores=Json.object(right.get("scores"));
            Double f1Delta=oldScores.containsKey("tokenF1")&&newScores.containsKey("tokenF1")?Text.round(Json.number(newScores, "tokenF1", 0)-Json.number(oldScores, "tokenF1", 0)):null;
            changes.add(Json.map("caseId", right.get("caseId"), "question", right.get("question"), "baselinePassed", before, "candidatePassed", after, "f1Delta", f1Delta, "latencyDeltaMs", Json.integer(right, "latencyMs", 0)-Json.integer(old, "latencyMs", 0), "baselineResponse", old.get("response"), "candidateResponse", right.get("response")));
        }
        Map<String, Object> leftSummary=Json.object(baseline.get("summary")), rightSummary=Json.object(candidate.get("summary"));
        return Json.map("baselineId", baseline.get("id"), "candidateId", candidate.get("id"), "pairedCases", changes.size(), "regressions", regressions, "improvements", improvements, "passRateDelta", Text.round(Json.number(rightSummary, "passRate", 0)-Json.number(leftSummary, "passRate", 0)), "p95DeltaMs", Json.number(rightSummary, "p95Ms", 0)-Json.number(leftSummary, "p95Ms", 0), "changes", changes);
    }
}
