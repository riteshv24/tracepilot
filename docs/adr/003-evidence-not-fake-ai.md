# ADR 003: separate deterministic evidence from optional inference

Status: accepted.

## Context

A useful portfolio app must run without a paid key, but replacing inference with hard-coded output while displaying it as a model result is misleading. Real model calls also introduce availability, privacy, cost, and reproducibility concerns.

## Decision

Implement deterministic baselines as real algorithms and label their limits. Keep optional model generation/embeddings behind an explicit operator-configured client. Store the selected mode/configuration in evidence. Requested live-provider failures remain visible rather than silently changing providers.

## Consequences

The app remains reproducible offline and can demonstrate domain workflows honestly. Integration tests can use controlled local HTTP stubs without claiming a live model-quality evaluation.

A lexical score is not factuality. A syntax finding is not exploitability. A citation identifier is not entailment. Human review and appropriately labelled experiments remain necessary even after adding real inference.
