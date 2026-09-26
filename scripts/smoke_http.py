#!/usr/bin/env python3
"""Real HTTP integration smoke in an isolated temporary workspace. Requires scripts/check.sh first."""
from __future__ import annotations
import json
import os
import socket
import subprocess
import tempfile
import time
from pathlib import Path
from api_client import ApiClient

ROOT = Path(__file__).resolve().parent.parent
SLUG = 'tracepilot'

def main() -> None:
    classes = ROOT / ".build/tests"
    if not classes.exists():
        raise SystemExit("Run scripts/check.sh (or scripts/check.ps1) first.")
    with socket.socket() as sock:
        sock.bind(("127.0.0.1", 0))
        port = sock.getsockname()[1]
    with tempfile.TemporaryDirectory(prefix=SLUG + "-http-") as data:
        env = dict(os.environ, PORT=str(port), APP_DATA=data, APP_BIND="127.0.0.1",
                   APP_TOKEN="integration-test-local-key-only", JDBC_URL="", OLLAMA_MODEL="", OLLAMA_EMBED_MODEL="",
                   GITHUB_TOKEN="", GITHUB_WEBHOOK_SECRET="")
        env.pop("APP_PUBLIC_HOST", None)
        env["APP_WEB"] = str(ROOT / "web")
        api = ApiClient(f"http://127.0.0.1:{port}", env["APP_TOKEN"])
        with open(Path(data) / "server.log", "w+") as log:
            process = subprocess.Popen(["java", "--add-modules", "jdk.httpserver,jdk.compiler", "-cp", str(classes),
                "dev.portfolio.launcher.Main"], cwd=ROOT, env=env, stdout=log, stderr=subprocess.STDOUT)
            checks = 0
            try:
                for _ in range(100):
                    try:
                        api.request("GET", "/health", expected=200)
                        break
                    except OSError:
                        if process.poll() is not None:
                            log.seek(0)
                            raise RuntimeError(log.read())
                        time.sleep(.1)
                else:
                    raise TimeoutError("Server did not become healthy")
                for job in api.request("GET", "/api/jobs"):
                    api.wait(job["id"], 30)
                for path in ("/health", "/api/meta", "/api/jobs", "/api/routes", "/api/audit"):
                    api.request("GET", path, expected=200)
                    checks += 1
                ApiClient(api.base_url, "wrong").request("GET", "/api/meta", expected=401)
                api.request("GET", "/api/meta", expected=403, headers={"Host": "untrusted.example"})
                api.request("POST", "/api/missing", {}, expected=403, headers={"Origin": "https://untrusted.example"})
                api.request("GET", "/api/missing", expected=404)
                checks += 4

                datasets = api.request("GET", "/api/datasets", expected=200)
                prompts = api.request("GET", "/api/prompts", expected=200)
                jobs = []
                for provider in ("recorded", "extractive"):
                    body = {"name": "HTTP contract " + provider, "datasetId": datasets[0]["id"], "promptId": prompts[0]["id"], "provider": provider}
                    first = api.request("POST", "/api/runs", body, expected=202, headers={"Idempotency-Key": provider})
                    same = api.request("POST", "/api/runs", body, expected=202, headers={"Idempotency-Key": provider})
                    assert first["id"] == same["id"]
                    api.wait(first["id"])
                    jobs.append(first)
                comparison = api.request("POST", "/api/compare", {"baselineId": jobs[0]["id"], "candidateId": jobs[1]["id"]}, expected=200)
                assert comparison["pairedCases"] == 12
                run = api.request("GET", "/api/runs/" + jobs[0]["id"], expected=200)
                result = run["results"][0]
                path = "/api/runs/" + run["id"] + "/results/" + result["caseId"] + "/annotation"
                api.request("PUT", path, {"expectedRevision": 1, "verdict": "pass", "note": "HTTP test"}, expected=200)
                api.request("PUT", path, {"expectedRevision": 1, "verdict": "pass"}, expected=409)
                csv = api.request("GET", "/api/runs/" + run["id"] + "/export.csv", expected=200)
                assert csv.startswith("case_id,")
                checks += 13

                print(f"HTTP RESULT: {checks} contract checks passed ({SLUG}, isolated workspace)")
            finally:
                process.terminate()
                try:
                    process.wait(timeout=20)
                except subprocess.TimeoutExpired:
                    process.kill()
                    process.wait()

if __name__ == "__main__":
    main()
