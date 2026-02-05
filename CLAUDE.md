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
4. Create a PR: `gh pr create --head <branch-name> --base main --title "<title>" --body "<body>"`
5. Enable auto-merge: `gh pr merge --auto --squash --delete-branch`
6. Wait for merge (check every ~60s): `while gh pr view --json state -q '.state' | grep -q OPEN; do sleep 60; done`
7. Return to main: `git checkout main && git pull`
