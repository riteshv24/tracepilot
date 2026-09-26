# TracePilot walkthrough

## A ten-minute working demonstration

Start `./run.sh`, open port 8311, and wait for the two seeded experiments to finish. The Atlas support material is fictional and contains intentionally wrong recorded answers.

1. Open **Experiments** and choose the recorded candidate. Inspect the evidence for a failed case: response text, reference, support context, applicable checks, and gate failures should explain the result.
2. Compare the recorded run with the extractive challenger. Both use the same original dataset revision, so case-level pairing is valid. Point out that a source-copying baseline can pass lexical checks yet fail the JSON or expected-refusal case.
3. Open **Datasets**, edit a case, and save with its current version. Inspect the original run again: its immutable case snapshot should not change. Queue a new run to evaluate the new version.
4. Add a human annotation in the case inspector. Explain why an annotation adds judgment instead of overwriting the original response.
5. Export a run to CSV/JSON, then use `python scripts/demo.py` to create another actual two-provider comparison through the public HTTP API.

## Follow the code

Read `Product` for routing, `DatasetService` for versions, `RunService` for immutable submissions and case persistence, `EvaluationMetrics` for transparent checks, and `RunComparison` for pairing. Then examine `foundation/Jobs` and the restart/cancellation contracts in `tests/`.

## Questions to answer in a technical discussion

**Why not let a model judge everything?** Deterministic checks are cheap and inspectable, but incomplete. A model judge would need its own versioned prompts, calibrated labels, reproducibility controls, and error accounting.

**Why is missing reference data not a score of one?** It is not evidence of correctness. Applicability is tracked separately so averages do not improve just because reference fields are missing.

**What happens when the process dies mid-run?** Completed case evidence persists. Interrupted running jobs become explicit failures on restart instead of silently returning a completed result.

**What does the cost estimate mean?** User-entered rates multiplied by usage accounting, which can be a word-based estimate. It is not a verified provider invoice.

## Suggested first personal contribution

Add JSON Schema validation as a new, explicitly applicable evaluator. Include malformed-schema tests, distinguish schema configuration errors from failed model outputs, expose the schema revision in snapshots, and compare results on a dataset you personally curate. Capture the change as a genuine Git commit and describe only the measured outcomes.
