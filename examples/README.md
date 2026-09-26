# TracePilot fixtures

`atlas-support.json` is the full twelve-case fictional seed dataset. Import it through the interface or POST it to `/api/datasets`. Deliberately incorrect `response` fields are evaluation inputs, not recommended answers. `grounded-prompt.json` can be POSTed to `/api/prompts`.

Run `python scripts/demo.py` to import both dependencies, evaluate recorded and extractive providers, and compare the real results. The CLI uses newly assigned IDs and a fresh idempotency prefix; fixtures do not depend on a specific server's runtime database.
