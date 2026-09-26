# Contributing to TracePilot

Start with `docs/ARCHITECTURE.md`, run the application, and reproduce the native test suite. Keep changes focused on one user-visible behavior or invariant.

## Local checks

```bash
./scripts/check.sh
python scripts/smoke_http.py
mvn -B -ntp verify
```

The last command needs Maven and dependency access. Record what you actually ran in a pull request, and explicitly distinguish unavailable checks from passing checks.

## Change guidelines

Keep product rules inside the Java domain layer; HTTP controllers should adapt transport, not duplicate business behavior. Add regression cases for bug fixes, preserve stable case/document/finding identifiers where appropriate, and test optimistic-revision conflicts. Avoid introducing an external service when a local implementation meets the documented workload.

The UI must render untrusted text through escaping or text nodes. New actions need loading, error, empty, and success states—not a button that only displays a toast. Maintain keyboard-accessible forms and sensible small-screen behavior.

Never place real secrets in examples. Seed data is fictional and should remain clearly identified. Do not edit test fixtures only to force a preferred benchmark result; explain changed relevance labels or policy thresholds.

## Pull-request evidence

Include the problem, implementation boundary, tests actually executed, screenshots for UI changes, migration/compatibility implications, and remaining limitations. Report computed results with dataset/model/configuration context. Do not claim general quality improvements from one tiny fixture.
