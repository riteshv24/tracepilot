# ADR 002: serialize bounded workspace writes

Status: accepted for single-instance operation.

## Context

A dataset/run, source/index, review/finding, or job/idempotency record can involve several aggregates. A partially written change would be harder to reason about than a bounded throughput limit. This release targets local engineering workflows, not thousands of simultaneous users.

## Decision

Use one transactional `Store` interface. Local writes copy state and atomically commit the snapshot. PostgreSQL writes acquire a workspace mutex row and use prepared statements inside one transaction. Domain updates also use explicit optimistic revisions to reject stale user edits.

## Consequences

Rollback and concurrency behavior are easy to test. Snapshotting a run or scheduling a job can be atomic with associated product state. Small workloads pay a predictable serialization cost.

Whole-state copy-on-write is not appropriate for unbounded data. The JDBC mutex constrains throughput. Neither design provides distributed job ownership. Scaling requires per-aggregate invariants, lease-based job claims, idempotent external effects, migrations, and a load/recovery test plan—not simply adding replicas.
