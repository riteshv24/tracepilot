#!/usr/bin/env python3
"""Create private local Compose secrets without overwriting an existing .env."""
import os
import secrets
from pathlib import Path
path = Path(__file__).resolve().parent.parent / ".env"
text = "APP_TOKEN=" + secrets.token_urlsafe(36) + "\nDB_PASSWORD=" + secrets.token_urlsafe(36) + "\nOLLAMA_BASE_URL=http://host.docker.internal:11434\nOLLAMA_MODEL=\nOLLAMA_EMBED_MODEL=\nGITHUB_TOKEN=\nGITHUB_WEBHOOK_SECRET=\n"
try:
    fd = os.open(path, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
    with os.fdopen(fd, "w") as output:
        output.write(text)
except FileExistsError:
    raise SystemExit(".env already exists; it was not changed. Review it manually.")
print("Created .env with independent random workspace/database secrets. It is excluded from Git.")
print("Review .env locally and enter APP_TOKEN in the app's Connection dialog when using Compose.")
