# TracePilot HTTP contract

Default origin: `http://localhost:8311`. Native and Spring adapters expose the same routes below. `GET /health` is also available as a liveness response.

JSON request bodies use `Content-Type: application/json`. Set `X-Workspace-Key` when `APP_TOKEN` is configured. Creating work accepts `Idempotency-Key` where the route queues a job. Job responses include an `id`; poll `/api/jobs/{id}` until `SUCCEEDED`, `FAILED`, or `CANCELLED`.

| Method | Route |
|---|---|
| `GET` | `/api/meta` |
| `GET` | `/api/jobs` |
| `GET` | `/api/jobs/{id}` |
| `POST` | `/api/jobs/{id}/cancel` |
| `GET` | `/api/audit` |
| `GET` | `/api/routes` |
| `GET` | `/api/datasets` |
| `POST` | `/api/datasets` |
| `GET` | `/api/datasets/{id}` |
| `PUT` | `/api/datasets/{id}` |
| `GET` | `/api/datasets/{id}/versions` |
| `GET` | `/api/prompts` |
| `POST` | `/api/prompts` |
| `PUT` | `/api/prompts/{id}` |
| `POST` | `/api/prompts/preview` |
| `GET` | `/api/runs` |
| `POST` | `/api/runs` |
| `GET` | `/api/runs/{id}` |
| `PUT` | `/api/runs/{id}/results/{caseId}/annotation` |
| `GET` | `/api/runs/{id}/export.csv` |
| `GET` | `/api/runs/{id}/export.json` |
| `POST` | `/api/compare` |

## Errors and concurrency

Errors use `application/problem+json` with `type`, `title`, `status`, `detail`, and `requestId`. Input problems produce 400; missing records 404; stale optimistic revisions or idempotency conflicts 409; oversized input 413; queue exhaustion 429; configuration/provider failures produce an explicit 5xx response. Unsupported content types produce 415.

Use the current `expectedVersion` when updating a dataset, prompt, repository, or policy. Use `expectedRevision` for case annotations, finding dispositions, or query feedback. Fetch the new state before retrying a conflict; do not blindly overwrite another edit.

Metadata intentionally omits provider credentials and workspace keys. Audit events store action/target/timestamp metadata, but domain records and jobs can contain original private text.

## Executable examples

`python scripts/demo.py` performs a real product workflow with valid IDs and dependencies. `scripts/smoke_http.py` demonstrates isolated HTTP assertions. Example request shapes are in `examples/` and `requests.http`. The browser's Network tab also exposes the same plain JSON contract; no private frontend protocol exists.

## Protective limits

All HTTP bodies: 1 MiB. Local workspace: 32 MiB. Pending jobs: 64; workers: 2. TracePilot: 30 datasets, 200 cases/dataset, 150 runs. MergeMind: 20 snapshots, 60 files/snapshot, 600,000 source characters, 60 changed files/6,000 diff lines, 100 reviews; GitHub imports at most 30 changed files. GraphRAG Studio: 12 collections, 80 documents/collection, 120,000 characters/document, 180 chunks/document, 2,500 retrieved-corpus chunks, top K 1–12, retrieval expansion at most 2 hops, benchmark at most 20 labelled questions.

Protective maxima are not load-test results. Actual practical capacity also depends on source length, retained snapshots, model configuration, hardware, and storage mode.
