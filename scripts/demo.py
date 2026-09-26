#!/usr/bin/env python3
"""Exercise a real local tracepilot workflow; stores new evidence in the selected workspace."""
from __future__ import annotations
import argparse
import json
import sys
import uuid
from pathlib import Path
from api_client import ApiClient

def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--url", default="http://localhost:8311")
    args = parser.parse_args()
    api = ApiClient(args.url)
    run_id = uuid.uuid4().hex[:8]

    dataset = json.loads((Path(__file__).parent.parent / "examples/atlas-support.json").read_text())
    dataset["name"] = "CLI evaluation " + run_id
    dataset = api.request("POST", "/api/datasets", dataset)
    prompt = api.request("POST", "/api/prompts", {"name": "CLI prompt " + run_id,
        "template": "Context: {{context}}\nQuestion: {{question}}\nAnswer:"})
    jobs = []
    for provider in ("recorded", "extractive"):
        job = api.request("POST", "/api/runs", {"name": provider + " / " + run_id,
            "datasetId": dataset["id"], "promptId": prompt["id"], "provider": provider},
            headers={"Idempotency-Key": f"cli-{run_id}-{provider}"})
        api.wait(job["id"])
        jobs.append(job)
    comparison = api.request("POST", "/api/compare", {
        "baselineId": jobs[0]["id"], "candidateId": jobs[1]["id"]})
    print(json.dumps(comparison, indent=2))

if __name__ == "__main__":
    try:
        main()
    except (RuntimeError, TimeoutError, OSError, ValueError) as error:
        print(f"Demo failed: {error}", file=sys.stderr)
        raise SystemExit(1)
