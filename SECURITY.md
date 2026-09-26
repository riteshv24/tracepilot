# Security scope and reporting

This release is a **single-workspace local engineering tool**, not a hardened public service. Use fictional or appropriately sanitized inputs. Do not commit `.env`, runtime state, access tokens, private repository snapshots, or real customer documents.

## Controls that exist

The default bind is loopback. A non-loopback bind requires a workspace key of at least 24 characters. The API validates Host values, rejects cross-origin writes, bounds request size, compares secret values without early string-exit comparison, and emits safe problem responses plus request IDs. Browser code escapes untrusted text before HTML interpolation; code samples are displayed as text.

The native adapter bounds HTTP concurrency. Persisted background work has explicit queue limits, idempotency, cooperative cancellation, and interrupted-job recovery. Provider requests use an operator-configured origin, no redirects, byte limits, and body-read deadlines. There is no API for arbitrary remote URL fetching.

Repository paths are logical POSIX paths and reject traversal/absolute paths. MergeMind stores supplied source as text and uses `JavacTask.parse` with annotation processing disabled. It never loads, runs, installs, or builds the uploaded project. GitHub integration is read-only; no review comments, checks, commits, or patches are posted.

## Controls that do not exist

There are no individual accounts, tenant isolation, OIDC, role-based access, per-user quotas, encrypted-at-rest workspace records, secret vault, dependency vulnerability certification, or production penetration-test attestation. A workspace key is shared access, not an identity system. API keys are kept in tab session storage, not a hardened enterprise credential store.

Original input text may contain secrets. Redacted finding excerpts and metadata-only audit events do **not** mean the underlying repository, document, or job payload has been redacted. Persistence and exports can contain the original content. Review it before publication.

Prompt injection is not solved by an instruction string. Model output is advisory, untrusted text. Graph citations validate source identifiers, not factual entailment. Code-review findings are candidates for a human reviewer. TracePilot scores are heuristics, not security or truth guarantees.

## Safe deployment boundary

Keep the service on loopback for demonstrations. A public deployment requires an authentication/authorization design, TLS, a trusted reverse proxy, traffic limits, dependency review, log retention policy, provider-egress restrictions, backup/restore drills, and product-specific abuse tests. Single-instance job ownership remains required even with a database.

## Reporting a problem

Use a private security-reporting channel on the repository once the maintainer enables one. Do not open a public issue with working credentials, customer data, or exploit details against a deployed third-party system. Include the affected version, sanitized reproduction, impact, and suggested invariant/test when available.
