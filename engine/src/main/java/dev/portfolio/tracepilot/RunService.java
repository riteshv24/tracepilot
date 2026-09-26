package dev.portfolio.tracepilot;

import dev.portfolio.foundation.*;
import java.util.*;

/** Orchestrates immutable run snapshots, per-case persistence and human review annotations. */
public final class RunService {
    private final Store store;
    private final Jobs jobs;
    private final ResponseProviders providers;
    private final EvaluationMetrics metrics=new EvaluationMetrics();
    public RunService(Store store, Jobs jobs, ModelClient model) {
        this.store=store;
        this.jobs=jobs;
        this.providers=new ResponseProviders(model);
        jobs.register("evaluate", this::evaluate);
    }
    public Map<String, Object> queue(Map<String, Object> input, String key) {
        String datasetId=Checks.id(Json.string(input, "datasetId", "")), promptId=Checks.id(Json.string(input, "promptId", ""));
        String provider=Checks.oneOf(Json.string(input, "provider", "recorded"), "provider", "recorded", "extractive", "ollama");
        String name=Checks.text(Json.string(input, "name", "Evaluation run"), "name", 120);
        Map<String, Object> settings=Json.map("minF1", Checks.range(Json.number(input, "minF1", .45), "minF1", 0, 1), "minSupport", Checks.range(Json.number(input, "minSupport", .55), "minSupport", 0, 1), "minPassRate", Checks.range(Json.number(input, "minPassRate", .8), "minPassRate", 0, 1), "maxLatencyMs", Checks.range(Json.integer(input, "maxLatencyMs", 3000), "maxLatencyMs", 1, 120000), "inputPricePer1k", Checks.range(Json.number(input, "inputPricePer1k", 0), "inputPricePer1k", 0, 100), "outputPricePer1k", Checks.range(Json.number(input, "outputPricePer1k", 0), "outputPricePer1k", 0, 100));
        return store.write(tx-> {
            Map<String, Object> dataset=tx.require("datasets", datasetId), prompt=tx.require("prompts", promptId);
            Map<String, Object> payload=Json.map("name", name, "dataset", dataset, "prompt", prompt, "provider", provider, "settings", settings);
            Map<String, Object> job=jobs.enqueue(tx, "evaluate", payload, key);
            String id=Json.string(job, "id", "");
            if(tx.get("runs", id).isEmpty()) {
                if(tx.scan("runs").size()>=150)throw Problem.invalid("Workspace run limit reached; use a fresh workspace or export old runs");
                tx.put("runs", id, Json.map("id", id, "name", name, "datasetId", datasetId, "datasetName", Json.string(dataset, "name", ""), "datasetVersion", Json.integer(dataset, "version", 1), "promptName", Json.string(prompt, "name", ""), "promptVersion", Json.integer(prompt, "version", 1), "provider", provider, "settings", settings, "snapshot", payload, "createdAt", Checks.now()));
            }
            job.put("runId", id);
            return job;
        });
    }
    private Map<String, Object> evaluate(Jobs.Context context, Map<String, Object> payload) throws Exception {
        List<Object> cases=Json.list(Json.object(payload.get("dataset")), "cases");
        Map<String, Object> prompt=Json.object(payload.get("prompt")), settings=Json.object(payload.get("settings"));
        int completed=0, errors=0;
        for(Object raw:cases) {
            context.checkCancelled();
            CaseSpec item=CaseSpec.from(Json.object(raw));
            Map<String, Object> result;
            try {
                ResponseProviders.Output output=providers.response(Json.string(payload, "provider", "recorded"), item, prompt);
                result=metrics.evaluate(item, output.text(), output.latencyMillis(), Json.number(settings, "minF1", .45), Json.number(settings, "minSupport", .55), Json.integer(settings, "maxLatencyMs", 3000));
                result.putAll(Json.map("response", output.text(), "latencyMs", output.latencyMillis(), "inputTokens", output.inputTokens(), "outputTokens", output.outputTokens(), "tokenAccounting", output.tokenAccounting(), "provider", output.provider(), "error", ""));
                errors=0;
            } catch(InterruptedException|java.util.concurrent.CancellationException e) {
                throw e;
            } catch(Exception e) {
                errors++;
                result=Json.map("response", "", "passed", false, "scores", Map.of(), "metrics", List.of(), "failures", List.of("provider_error"), "error", e instanceof Problem?e.getMessage():"Provider request failed", "latencyMs", 0, "inputTokens", 0, "outputTokens", 0, "tokenAccounting", "unavailable");
            }
            result.putAll(Json.map("id", context.id()+"__"+item.id(), "runId", context.id(), "caseId", item.id(), "question", item.question(), "context", item.context(), "reference", item.reference(), "revision", 1, "annotation", Json.map("verdict", "unreviewed", "note", "")));
            Map<String, Object> saved=result;
            store.write(tx-> {
                tx.put("results", Json.string(saved, "id", ""), saved);
                return null;
            });
            context.progress(++completed, cases.size(), "Evaluated "+item.id());
            if(errors>=3)throw new Problem(502, "provider_unavailable", "Stopped after three consecutive provider failures; completed evidence is preserved");
        }
        return Json.map("runId", context.id(), "caseCount", completed);
    }
    public List<Map<String, Object>> list() {
        return store.read(tx->tx.scan("runs").stream().sorted(Comparator.comparing((Map<String, Object> x)->Json.string(x, "createdAt", "")).reversed()).map(run->view(tx, run, false)).toList());
    }
    public Map<String, Object> get(String id) {
        return store.read(tx->view(tx, tx.require("runs", Checks.id(id)), true));
    }
    private Map<String, Object> view(Store.Tx tx, Map<String, Object> run, boolean detail) {
        List<Map<String, Object>> results=tx.scan("results").stream().filter(r->Json.string(r, "runId", "").equals(Json.string(run, "id", ""))).sorted(Comparator.comparing(r->Json.string(r, "caseId", ""))).toList();
        Map<String, Object> job=tx.require("jobs", Json.string(run, "id", ""));
        Map<String, Object> snapshot=Json.object(run.get("snapshot"));
        int expected=Json.list(Json.object(snapshot.get("dataset")), "cases").size();
        run.put("status", Json.string(job, "status", ""));
        run.put("jobMessage", Json.string(job, "message", ""));
        run.put("jobError", Json.string(job, "error", ""));
        run.put("summary", RunSummary.calculate(results, Json.object(run.get("settings")), expected));
        if(detail)run.put("results", results);
        else run.remove("snapshot");
        return run;
    }
    public Map<String, Object> annotate(String runId, String caseId, Map<String, Object> input) {
        Checks.id(runId);
        Checks.id(caseId);
        String verdict=Checks.oneOf(Json.string(input, "verdict", "unreviewed"), "verdict", "unreviewed", "pass", "fail");
        String note=Checks.optional(Json.string(input, "note", ""), "note", 2000);
        return store.write(tx-> {
            String key=runId+"__"+caseId;
            Map<String, Object> row=tx.require("results", key);
            long version=Json.integer(row, "revision", 1);
            if(version!=Json.integer(input, "expectedRevision", -1))throw Problem.conflict("Annotation changed; reload this result");
            row.put("annotation", Json.map("verdict", verdict, "note", note, "updatedAt", Checks.now()));
            row.put("revision", version+1);
            tx.put("results", key, row);
            Audit.record(tx, "result.annotated", key);
            return row;
        });
    }
    public String csv(String id) {
        Map<String, Object> run=get(id);
        StringBuilder out=new StringBuilder("case_id,question,passed,latency_ms,token_f1,context_support,response,reference\r\n");
        for(Object value:Json.list(run, "results")) {
            Map<String, Object> row=Json.object(value), scores=Json.object(row.get("scores"));
            List<Object> cells=Arrays.asList(row.get("caseId"), row.get("question"), row.get("passed"), row.get("latencyMs"), scores.get("tokenF1"), scores.get("contextSupport"), row.get("response"), row.get("reference"));
            out.append(String.join(",", cells.stream().map(Text::csv).toList())).append("\r\n");
        }
        return out.toString();
    }
}
