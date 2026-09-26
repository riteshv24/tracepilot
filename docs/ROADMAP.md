# A 12-week extension plan

This is **future work**, not a statement that the initial repository took twelve weeks to develop. The current implementation and delivery test results are described in PROJECT_STATUS.md and TESTING.md. The schedule is a planning estimate; narrow or expand it based on measured progress.

| Weeks | Focus | Deliverable and acceptance evidence |
|---|---|---|
| 1–2 | Dataset contracts and migration | Add JSON Schema-backed case imports, migration fixtures, and validation previews. Reject invalid cases with field-level errors; prove an old dataset remains readable after migration. |
| 3–4 | Evaluator extensions | Add configurable schema and numeric-tolerance evaluators. Version evaluator settings inside run snapshots and test applicability, missing labels, and evaluator failures. |
| 5–6 | Provider reproducibility | Add another real provider behind ResponseProviders, with explicit timeouts and usage fields. Add contract stubs and opt-in live tests. Record model identifiers and never silently switch providers. |
| 7–8 | Experiment statistics | Curate an independent labelled dataset. Implement paired bootstrap intervals and judge-agreement reporting with documented assumptions. Separate practical improvements from statistical uncertainty. |
| 9–10 | Deployment and identity | Run the Spring/PostgreSQL suite in a real environment, add schema migration tooling, authenticated users, and role-tested project isolation. Verify backup/restore and rotation of secrets. |
| 11–12 | Operational proof | Load-test documented bounded workloads, measure memory/queue behavior, and add regression checks for slow or failing providers. Publish a runbook and evidence from a real deployment. |

## How to make the project your own

Open real issues, select one independently useful feature, write failing tests, implement the change, and commit it with a clear explanation. Record the actual date and result. A smaller change that you can explain and defend is more valuable than extra services that have no tested behavior.

Keep a decision log describing alternatives, constraints, and evidence. Save reproducible commands, configuration, and benchmark input alongside any measurement. Review the generated code before claiming ownership or expertise; disclose assistance where an employer, course, or application requires it.

A milestone is complete only when its acceptance evidence exists. Do not retroactively mark this plan as completed or manufacture a commit calendar.
