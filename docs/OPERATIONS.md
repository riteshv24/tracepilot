# Running and operating TracePilot

## Requirements and modes

The tested zero-download path requires a full JDK 21. `javac` and `java` must both be on PATH. A JRE alone cannot compile the application; MergeMind additionally uses the JDK compiler's parsing API during reviews. Node 22+ runs the pure JavaScript tests; Python 3.10+ runs optional standard-library demo/smoke tools. Neither is needed to serve the browser app.

Native mode:

```bash
./run.sh
# http://localhost:8311
```

Spring mode requires Maven and dependency downloads:

```bash
mvn -B -ntp verify
java -jar spring/target/tracepilot-spring-0.1.0.jar
```

Both listen on the same default port and use the same API/domain model. Do not start both against the same workspace. The local file lock rejects conflicting processes, and the HTTP port also must be unique.

## Configuration

| Variable | Default / purpose |
|---|---|
| `PORT` | `8311`; application HTTP port |
| `APP_BIND` | `127.0.0.1`; explicit non-loopback bind requires a key of at least 24 characters |
| `APP_TOKEN` | Empty for loopback-only development; otherwise the workspace API key |
| `APP_PUBLIC_HOST` | Optional exact external `Host` value, including port when present |
| `APP_DATA` | `.local`; directory for the atomic local workspace |
| `APP_WEB` | `web`; native launcher's static asset directory |
| `JDBC_URL` | Empty selects local storage; PostgreSQL JDBC URL selects the JDBC store |
| `DB_USER`, `DB_PASSWORD` | Database credentials; used by the Spring/JDBC deployment |
| `OLLAMA_BASE_URL` | Native default `http://localhost:11434`; operator-controlled provider origin |
| `OLLAMA_MODEL` | Empty disables live text generation |
| `OLLAMA_EMBED_MODEL` | Empty disables live semantic embeddings |
| `GITHUB_TOKEN` | MergeMind only; server-side read credentials |
| `GITHUB_WEBHOOK_SECRET` | MergeMind only; signed delivery verification, disabled when absent/short |

Native scripts do not load `.env`. Set environment variables in your shell. For example:

```bash
APP_DATA=.local-alt PORT=8321 ./run.sh
```

PowerShell:

```powershell
$env:APP_DATA = '.local-alt'
$env:PORT = '8321'
.\run.ps1
```

`JDBC_URL` in native mode also requires a separately supplied JDBC driver on the JVM classpath. The provided native launcher deliberately has no dependency jars. **Use the Spring jar/Compose path for the packaged PostgreSQL deployment.**

## Docker Compose

```bash
python scripts/init_env.py
# Review .env. Keep both generated secrets private.
docker compose up --build
```

The app container uses the Spring Dockerfile, and PostgreSQL 17 stores its data in a named volume. The host HTTP port is bound to `127.0.0.1`; the database has no published host port. Enter the workspace key from `.env` in the browser connection dialog. A compose rebuild requires network access to image/dependency registries.

The native Dockerfile is a separate JDK-only build option:

```bash
docker build -t tracepilot:local .
# APP_TOKEN must be a nonempty, adequately long secret in the invoking shell.
docker run --rm -p 127.0.0.1:8311:8311 -e APP_TOKEN tracepilot:local
```

That native example is ephemeral. Add an appropriately owned persistent volume at `/app/data` before relying on it for retained work. The container process runs as a non-root account.

## Optional real model integrations

Set `OLLAMA_MODEL` to the exact identifier of a model already installed on the configured Ollama server. Restart the application so metadata reflects the new configuration. Enabling a configured URL may send selected prompts, code observations, or document passages to that service. Inspect the data-sharing boundary first.

```bash
export OLLAMA_BASE_URL=http://localhost:11434
export OLLAMA_MODEL='your-installed-model'
./run.sh
```

GraphRAG Studio also needs `OLLAMA_EMBED_MODEL` for a collection created with the `ollama` provider. Hash and semantic spaces are not interchangeable. Model name and dimension signatures are recorded, and mismatches require reindexing. Replacing model weights under the same model name is not automatically detected: record model digests externally and reindex deliberately.

Provider errors remain visible. The server does not silently substitute the offline baseline for a requested real model. A local baseline's latency and quality numbers must not be presented as a paid-provider benchmark.

## Persistence, backup, and reset

Local data includes source text, prompt/run snapshots, annotations, embeddings, and job payloads in plaintext. It is excluded from Git but is not encrypted at rest. Use non-sensitive fixtures for public demonstrations.

For a consistent local backup, stop the process, copy `.local/workspace.json` to a protected location, then restart. Restore only into a stopped workspace after checking the schema version and JSON integrity. A live OS lock file is not a portable backup artifact.

For PostgreSQL, use a database-native consistent backup, retain the matching application version, and test restoration. A successful `docker compose down` does not delete the named volume; `down -v` is destructive. Never use it as routine troubleshooting for a workspace you need to keep.

For an empty local experiment, choose a new `APP_DATA` directory rather than deleting evidence. There is no automatic schema migration engine yet. Schema version 1 is validated, and incompatible/corrupt snapshots fail closed.

## Health, concurrency, and bounds

`GET /health` is a basic application liveness response. It is **not** a deep database/provider readiness test. Optional Actuator health/info is on a separate loopback management port in Spring mode. Jobs, metadata-only audit events, and safe errors are visible through the API/UI.

The native HTTP adapter bounds concurrent handling to 32 requests. Job concurrency is two workers, with 64 queued/running items. HTTP JSON payloads are bounded to 1 MiB; file workspaces to 32 MiB. Product-specific limits are documented in API.md. These are protective budgets, not load-tested capacity claims.

Common recovery paths:

| Symptom | Action |
|---|---|
| Port already in use | Stop the previous app or set a different `PORT` |
| Workspace already open | Use one JVM per directory; do not delete a live lock to bypass it |
| 401 in the UI | Enter the correct `APP_TOKEN` in Connection |
| Host rejected behind a proxy | Configure the exact `APP_PUBLIC_HOST`, a key, TLS, and a trusted proxy |
| Invalid model / provider failure | Verify the installed model name and provider endpoint; inspect job error |
| Failed job after restart | Inspect retained evidence, then explicitly submit a new job |
| Corrupt snapshot | Preserve it and restore a verified backup; do not replace it with empty JSON |
| Workspace size limit | Export evidence and use a new workspace, or move to the JDBC deployment after verification |

Public internet deployment is outside this release's security scope. Do not expose it by changing the bind address alone.
