package dev.portfolio.tests;

import dev.portfolio.foundation.*;
import dev.portfolio.tracepilot.*;
import java.util.*;
import static dev.portfolio.tests.TestSupport.*;

public final class DomainTests {
    private static CaseSpec sample(String reference, String response) {
        return CaseSpec.from(Json.map("id", "case-a", "question", "What is the refund window?", "context", "Refunds are available within 30 days.", "reference", reference, "recordedResponse", response));
    }
    public static void run() throws Exception {
        EvaluationMetrics metrics=new EvaluationMetrics();
        test("Token F1 handles repeated tokens as a multiset", ()->near(2.0/3, metrics.tokenF1("alpha alpha beta", "alpha beta beta"), 1e-9));
        test("Token F1 handles empty predictions without division by zero", ()-> {
            equal(0, metrics.tokenF1("alpha", ""));
            equal(1, metrics.tokenF1("", ""));
        });
        test("Reference metrics are absent when reference is absent", ()-> {
            var out=metrics.evaluate(sample("", "30 days"), "30 days", 0, .5, .5, 10);
            truth(!object(out.get("scores")).containsKey("tokenF1"));
        });
        test("Lexical support is not reported as perfect without context", ()-> {
            var item=CaseSpec.from(Json.map("id", "a", "question", "Question?", "context", "", "reference", ""));
            var out=metrics.evaluate(item, "Answer", 0, 0, 0, 10);
            truth(!object(out.get("scores")).containsKey("contextSupport"));
        });
        test("Required phrases match whole token sequences", ()-> {
            var item=CaseSpec.from(Json.map("id", "a", "question", "q", "requiredPhrases", List.of("cat")));
            var out=metrics.evaluate(item, "concatenate", 0, 0, 0, 10);
            truth(Json.strings(out, "failures").contains("required_phrases"));
        });
        test("JSON formatting gate rejects trailing junk", ()-> {
            var item=CaseSpec.from(Json.map("id", "a", "question", "q", "format", "json"));
            var out=metrics.evaluate(item, "{} junk", 0, 0, 0, 10);
            truth(Json.strings(out, "failures").contains("json_format"));
        });
        test("Latency gate is independent of text quality", ()-> {
            var out=metrics.evaluate(sample("30 days", ""), "30 days", 101, 0, 0, 100);
            truth(Json.strings(out, "failures").contains("latency_limit"));
        });
        test("Refusal expectations use explicit supported phrases", ()-> {
            var item=CaseSpec.from(Json.map("id", "a", "question", "q", "expectRefusal", true));
            var out=metrics.evaluate(item, "I cannot determine that from the information provided.", 0, 0, 0, 10);
            truth(Json.bool(out, "passed", false));
        });
        test("Prompt substitutions cannot evaluate code or dollar expressions", ()-> {
            var item=CaseSpec.from(Json.map("id", "a", "question", "$1 \\ ${exec()}", "context", "context"));
            equal("$1 \\ ${exec()} | context", PromptTemplate.render("{{question}} | {{context}}", item));
        });
        test("Prompt rejects unknown variables and missing question", ()-> {
            problem(400, ()->PromptTemplate.validate("{{secret}} {{question}}"));
            problem(400, ()->PromptTemplate.validate("{{context}}"));
        });
        test("Duplicate case IDs are rejected", ()-> {
            var service=new DatasetService(new MemoryStore());
            problem(400, ()->service.create(Json.map("name", "duplicate", "cases", List.of(sample("x", "x").json(), sample("x", "x").json()))));
        });
        test("Versioned datasets preserve history and reject stale writers", ()-> {
            var service=new DatasetService(new MemoryStore());
            var old=service.create(Json.map("name", "v1", "cases", List.of(sample("x", "x").json())));
            var input=Json.map("name", "v2", "expectedVersion", 1, "cases", List.of(sample("y", "y").json()));
            var next=service.update(id(old), input);
            equal(2, next.get("version"));
            equal(2, service.versions(id(old)).size());
            equal("v1", service.versions(id(old)).get(0).get("name"));
            problem(409, ()->service.update(id(old), input));
        });
        try(Product product=new Product(new MemoryStore(), offlineModel())) {
            product.start();
            allJobs(product);
            var runs=list(product, "/api/runs");
            String first=id(runs.get(0));
            var detail=call(product, "GET", "/api/runs/"+first, null);
            test("Sample evaluations compute 12 independent cases", ()-> {
                equal(2, runs.size());
                equal(12, Json.list(detail, "results").size());
                truth(object(detail.get("summary")).containsKey("passRate"));
            });
            test("Same-version comparison pairs all stable case IDs", ()-> {
                var other=call(product, "GET", "/api/runs/"+id(runs.get(1)), null);
                equal(12, RunComparison.compare(detail, other).get("pairedCases"));
            });
            test("Comparison rejects different dataset versions", ()-> {
                var changed=Json.copyMap(detail);
                changed.put("datasetVersion", 99);
                problem(409, ()->RunComparison.compare(detail, changed));
            });
            test("Annotations have independent optimistic revision", ()-> {
                var row=object(Json.list(detail, "results").get(0));
                String url="/api/runs/"+first+"/results/"+row.get("caseId")+"/annotation";
                var body=Json.map("verdict", "fail", "note", "Human review", "expectedRevision", 1);
                var result=call(product, "PUT", url, body);
                equal(2, result.get("revision"));
                equal(row.get("passed"), result.get("passed"));
                problem(409, ()->call(product, "PUT", url, body));
            });
            test("CSV report has real rows and export headers", ()-> {
                var response=product.router().handle(new Router.Request("GET", "/api/runs/"+first+"/export.csv", Map.of(), "", Map.of()));
                truth(response.headers().get("Content-Disposition").contains(".csv"));
                truth(response.body().startsWith("case_id,"));
                truth(response.body().contains("\r\n"));
            });
            test("Editing a dataset does not mutate an existing run snapshot", ()-> {
                String ds=Json.string(detail, "datasetId", "");
                var original=call(product, "GET", "/api/datasets/"+ds, null);
                var update=Json.copyMap(original);
                update.put("name", "new version");
                update.put("expectedVersion", original.get("version"));
                call(product, "PUT", "/api/datasets/"+ds, update);
                equal(1, call(product, "GET", "/api/runs/"+first, null).get("datasetVersion"));
            });
            test("Missing dataset produces a domain 404", ()->problem(404, ()->call(product, "GET", "/api/datasets/not-present", null)));
            test("Dataset case count cannot be zero", ()->problem(400, ()->call(product, "POST", "/api/datasets", Json.map("name", "empty", "cases", List.of()))));
            test("Unknown response provider is rejected", ()->problem(400, ()->call(product, "POST", "/api/runs", Json.map("name", "invalid", "datasetId", detail.get("datasetId"), "promptId", id(list(product, "/api/prompts").get(0)), "provider", "made-up"))));
        }
    }
}
