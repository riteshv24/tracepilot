# Validation and test strategy

## Executed in the delivery environment

| Layer | Result |
|---|---|
| JDK 21 compilation of engine + native adapter | Passed |
| Native Java suite | 62 passed, 0 failed |
| Pure browser-module suite under Node 22 | 8 passed, 0 failed |
| Real native HTTP smoke in a temporary workspace | Passed |
| Offline Chromium DOM smoke using exported real engine responses | Passed; no page errors in visited views |
| Desktop and 390-pixel narrow-viewport rendering | Inspected; overview has no horizontal page overflow |

The shared foundation contracts run independently in each self-contained repository. Per-repository execution counts should not be added together and described as that many unrelated product algorithms.

## Reproduce

```bash
./scripts/check.sh
python scripts/smoke_http.py
```

`check.sh` compiles Java source into `.build/tests` and executes `AllTests`; it then runs Node's built-in test runner when Node is available. Missing Node is explicitly reported as a skip, not a passing browser suite. `check.ps1` provides the corresponding Windows workflow.

The HTTP smoke launches a separate JVM with a temporary directory, random loopback port, and test-only key. It resets external provider configuration, waits for seeded jobs, exercises HTTP routes and domain changes, verifies safe auth/error behavior, and terminates the process. It does not mutate your normal `.local` workspace.

Foundation coverage includes JSON ambiguity/depth, copy-on-write rollback, concurrent updates, file durability/reopen, corrupt-state failure, exclusive locking, job idempotency/cancellation/restart, queue capacity, route binding, Host/origin/key checks, provider response budgets, and controlled local HTTP provider stubs.

Product contracts cover the algorithms and state transitions in `tests/dev/portfolio/tests/DomainTests.java`. Frontend module tests cover escaping, formatting, stable ordering, and—in GraphRAG Studio—layout determinism, bounded positions, adjacency, and weak-edge filtering.

## Spring and database tests: included, not executed here

```bash
mvn -B -ntp verify
```

`ApiControllerTest` contains six Spring MVC transport contracts. `JdbcStoreTest` contains five H2-backed transaction contracts. Maven and dependency downloads were unavailable in the delivery environment, so these are **unverified until your Maven/CI run completes**. H2 passing would still not establish PostgreSQL dialect, isolation, operational, or performance correctness. Run and review the actual Compose/PostgreSQL integration before claiming that deployment was verified.

The GitHub Actions workflow has separate native and Maven jobs. Workflow existence is not evidence of a successful GitHub run. Check the real Actions result after pushing.

## Browser test limitation

The available browser blocked URL navigation under its managed environment policy. No policy was changed. Screenshots and DOM interactions were therefore rendered offline with the real frontend and exported responses from the live Java engine. Browser-to-server networking was tested separately with the native HTTP smoke, not represented as a completed real-browser end-to-end run.

The screenshots show actual application UI/state, not image-generated mockups. Latency values and timestamps are from one local execution and can differ on another machine.

## Not established by these tests

No production traffic load test, broad accessibility audit, cross-browser compatibility certification, live GitHub import, live model-quality benchmark, PostgreSQL recovery exercise, Docker execution, Windows execution, or security assessment was completed in this environment. The roadmap identifies concrete follow-up tests rather than converting these gaps into invented claims.

## CLI workflow check

`python scripts/demo.py` was also executed successfully against each running native application. It creates real records and waits for real jobs; no paid provider is required. The captured output is in `validation/cli-demo.txt`. IDs and timings in this log describe that local run, not production performance.
