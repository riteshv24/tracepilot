"""Small standard-library API client. No third-party Python dependencies."""
from __future__ import annotations
import json
import os
import time
import urllib.error
import urllib.request
from typing import Any

class ApiClient:
    def __init__(self, base_url: str, token: str | None = None) -> None:
        self.base_url = base_url.rstrip("/")
        self.token = os.environ.get("APP_TOKEN", "") if token is None else token

    def request(self, method: str, path: str, body: Any = None,
                *, expected: int | None = None, headers: dict[str, str] | None = None) -> Any:
        payload = None if body is None else json.dumps(body).encode("utf-8")
        request_headers = {"Content-Type": "application/json", "X-Workspace-Key": self.token}
        request_headers.update(headers or {})
        request = urllib.request.Request(self.base_url + path, data=payload,
                                         headers=request_headers, method=method)
        try:
            response = urllib.request.urlopen(request, timeout=120)
        except urllib.error.HTTPError as error:
            response = error
        with response:
            data = response.read()
            content_type = response.headers.get("Content-Type", "")
            value = json.loads(data) if "json" in content_type else data.decode("utf-8")
            if expected is not None and response.status != expected:
                raise AssertionError(f"{method} {path}: expected {expected}, received {response.status}: {value}")
            if expected is None and not 200 <= response.status < 300:
                detail = value.get("detail", value) if isinstance(value, dict) else value
                raise RuntimeError(f"HTTP {response.status}: {detail}")
            return value

    def wait(self, job_id: str, seconds: float = 240) -> dict[str, Any]:
        deadline = time.monotonic() + seconds
        while time.monotonic() < deadline:
            job = self.request("GET", f"/api/jobs/{job_id}")
            if job["status"] == "SUCCEEDED":
                return job
            if job["status"] in {"FAILED", "CANCELLED"}:
                raise RuntimeError(f"Job {job['status']}: {job.get('error') or job.get('message')}")
            time.sleep(0.15)
        raise TimeoutError(f"Job {job_id} is still running; inspect Activity before retrying")
