# Publish this repository

This folder is a standalone repository. Do not push the parent folder containing all three projects unless you deliberately want a monorepo.

## Before the first commit

Run the native tests and HTTP smoke. Check `.gitignore`, inspect the screenshots and sample fixtures, and confirm there are no real tokens or private documents. The screenshot timestamps are actual local execution timestamps; they are not development history.

Create an **empty** GitHub repository named `tracepilot` in the desired account. Choose its visibility deliberately. Do not initialize the remote with another README if you plan to push this folder as its first commit.

From this folder:

```bash
git init
git branch -M main
git add .
git status --short
git commit -m "feat: add Java TracePilot workbench with tests and documentation"
git remote add origin https://github.com/riteshvd/tracepilot.git
git push -u origin main
```

Replace the account/repository path when publishing elsewhere. Authenticate through your normal Git credential manager; do not paste a personal access token into the remote URL or source tree. If a remote already has content, inspect it and reconcile normally—do not use a force push as a shortcut.

## After pushing

Wait for the actual GitHub Actions results. The Maven job is the first target-environment verification of the Spring adapter and H2 tests; fix and rerun it before describing that path as tested. Add repository topics relevant to the implemented features, enable private vulnerability reporting, and pin the repository only after you can run and explain its main workflow.

A useful first issue is a reproducible improvement from ROADMAP.md. Record genuine follow-up commits. Do not backdate commits or describe a generated initial implementation as months of historical work.
