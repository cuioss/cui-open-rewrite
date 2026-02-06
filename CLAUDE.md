# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

cui-open-rewrite is a Java library providing OpenRewrite recipes for cuioss projects. It helps automate Java modernization and code migration tasks.

**Maven Coordinates**: `de.cuioss:cui-open-rewrite`

## Build Commands

```bash
# Build and run all tests
./mvnw clean verify

# Run tests only
./mvnw test

# Run a specific test class
./mvnw test -Dtest=ClassName

# Run a specific test method
./mvnw test -Dtest=ClassName#methodName

# Skip tests
./mvnw clean install -DskipTests
```

## Git Workflow

This repository has branch protection on `main`. Direct pushes to `main` are never allowed. Always use this workflow:

1. Create a feature branch: `git checkout -b <branch-name>`
2. Commit changes: `git add <files> && git commit -m "<message>"`
3. Push the branch: `git push -u origin <branch-name>`
4. Create a PR: `gh pr create --repo cuioss/cui-open-rewrite --head <branch-name> --base main --title "<title>" --body "<body>"`
5. Wait for CI + Gemini review (waits until checks complete): `gh pr checks --repo cuioss/cui-open-rewrite <pr-number> --watch`
6. **Handle Gemini review comments** — fetch with `gh api repos/cuioss/cui-open-rewrite/pulls/<pr-number>/comments` and for each:
    - If clearly valid and fixable: fix it, commit, push, then reply explaining the fix and resolve the comment
    - If disagree or out of scope: reply explaining why, then resolve the comment
    - If uncertain (not 100% confident): **ask the user** before acting
    - Every comment MUST get a reply (reason for fix or reason for not fixing) and MUST be resolved
7. Do **NOT** enable auto-merge unless explicitly instructed. Wait for user approval.
8. Return to main: `git checkout main && git pull`
