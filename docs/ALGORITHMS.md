# Evaluation algorithms and evidence contracts

## Immutable experiments, not a mutable dashboard

`DatasetService` owns versioned dataset and prompt records. `RunService` validates the selected records, resolves configuration, and queues a snapshot of the inputs. Editing the original dataset does not change the evidence already attached to a run. The worker stores each case result before moving to the next case. A cancelled or failed experiment can therefore retain partial evidence without being labelled complete.

A queued run has its own identity. Its idempotency key is tied to a hash of the submitted payload; replaying the same key with different inputs is a conflict, not a silent duplicate suppression. An interrupted RUNNING job becomes FAILED after restart. The system does not pretend that an in-process queue provides distributed exactly-once execution.

## Case metrics

Implementation: `engine/src/main/java/dev/portfolio/tracepilot/EvaluationMetrics.java`.

| Metric | Actual computation | Important limitation |
|---|---|---|
| Exact match | Compare normalized, case-insensitive token sequences. | Formatting and punctuation differences can disappear during normalization. |
| Token F1 | Multiset intersection of answer/reference tokens; harmonic mean of precision and recall. | Synonyms, reasoning correctness, and contradictions are not understood. |
| Context support | Count response content tokens present in the source-context vocabulary, divided by response content-token count. | Copying the source can score well while answering the wrong question. |
| Required phrases | Fraction of configured normalized phrases present at whole-token boundaries. | A phrase appearing in a negation still counts as present. |
| JSON validity | Parse the complete response with the strict JSON parser. | This is syntax validation, not JSON Schema conformance. |
| Expected refusal | Match a small documented set of refusal phrases when refusal is expected. | This is neither a general safety classifier nor a reliable refusal detector. |

For reference multiset R, answer multiset A, and overlap O:

```text
O = sum(min(count_R(token), count_A(token)))
precision = O / |A|
recall    = O / |R|
F1        = 2 * precision * recall / (precision + recall)
```

Zero-overlap answers score zero. The pure F1 helper returns one for two empty token sequences, but a missing reference makes the public reference metric **not applicable**, and a blank response still fails its case. Missing context also produces a non-applicable support metric rather than an invented perfect score.

## Gates and summaries

A case fails when an applicable configured F1/support/phrase/JSON/refusal condition fails, when its response is empty, or when latency exceeds the configured case limit. Context-support gating is deliberately not applied to expected-refusal cases. Exact match is reported but is not itself a required gate.

`RunSummary` aggregates observed results: passed/failed cases, provider errors, applicable metric averages, latency p50/p95, usage accounting, and gate failures. Incomplete case counts, low pass rate, excessive p95, or provider errors prevent a run-level pass. Progress is not the same as quality.

Recorded and extractive providers are local baselines. Their timing includes local response processing, **not remote inference**. The optional Ollama provider records an actual request duration and consumes provider-reported token counts when present. Fallback word-based usage estimates are explicitly labelled. Cost fields multiply operator-entered unit prices by this accounting; they are not a vendor billing statement.

## Comparisons and annotations

`RunComparison` requires the same dataset identity and version, then pairs cases by stable case ID. It reports score deltas, improvements, regressions, and unchanged outcomes without claiming a statistically significant model ranking. Changing the dataset requires a new experiment design rather than quietly comparing different populations.

Human annotations use `expectedRevision` and preserve an optimistic-concurrency boundary. They add reviewer judgment to the measured evidence; they do not rewrite the original model response or automatically change a historical metric.

## Complexity and extension points

Tokenization and each basic evaluator are linear in bounded case text. A run is sequential within one worker; two independent worker slots are available. The queue is capped rather than allowing unbounded inference fan-out. Snapshots consume storage proportional to retained input/evidence size.

Extend `EvaluationMetrics` for schema checks or semantic evaluators only with explicit applicability rules, failure modes, labelled evaluation data, and tests. An LLM judge should be a separately identified provider with its own model/version/configuration—not a replacement for deterministic evidence.
