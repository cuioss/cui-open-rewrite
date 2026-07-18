---
name: release
description: Cut a cui-open-rewrite release — bump .github/project.yml version, open (with the skip-bot-review label) and merge the release PR on a release/ branch, wait for the automated Release workflow, verify the release landed, then reformat the generated GitHub release notes
user-invocable: true
allowed-tools: Bash, Read, Edit
---

# Release Skill

Cuts a new cui-open-rewrite release end-to-end: determine the version, open the
version-bump PR that triggers the release, merge it, wait for the automated Release
workflow, verify the release landed, and reformat the auto-generated GitHub release notes.

## How the release is wired (read first)

The release is **fully automated by GitHub Actions**. `.github/workflows/release.yml`
triggers on a **merged pull request that changes `.github/project.yml`**:

```yaml
on:
  pull_request:
    types: [closed]
    paths:
      - '.github/project.yml'
  workflow_dispatch:
```

So this skill never runs Maven release goals by hand. Its job is to produce and merge the
correct `project.yml` change; the reusable `cuioss-organization` release workflow
(`reusable-maven-release.yml`) does the tagging, Maven Central deploy, GitHub release
creation, and — because `pages.deploy-at-release: true` — the documentation pages deploy.

Observed timings (use these as the basis for the waits below):
- PR gating check: **Maven Build ~4–7 min** (matrix over Java 21 + 25). This is a Java
  library with no integration/e2e suites, so a full green PR is typically **~5–8 min**.
- Release workflow itself: **~6 min**, but Maven Central propagation, the GitHub release
  publish, and the pages deploy can lag → allow **up to ~30 min** before treating it as
  stuck.

## Workflow

### Step 1 — Determine the version number

Read the current release block in `.github/project.yml`:
- `release.current-version` (e.g. `1.3.0`)
- `release.next-version` (e.g. `1.3-SNAPSHOT`)

**Scheme:** cui-open-rewrite uses a three-segment `X.Y.Z` version line — the release tags
are `1.0.4`, `1.1.2`, `1.2.0`, `1.3.0`, etc. Patch releases increment `Z` (`1.3.0` →
`1.3.1`); minor releases increment `Y` and reset `Z` (`1.3.0` → `1.4.0`).

**Default rule:** the release version is `next-version` with `-SNAPSHOT` stripped, expressed
in the `X.Y.Z` scheme. Note that `next-version` is sometimes recorded as a two-segment
snapshot (e.g. `1.3-SNAPSHOT`); normalize it to three segments (`1.3-SNAPSHOT` → `1.3.0`).
The new `next-version` is the next patch bump plus `-SNAPSHOT` (e.g. `1.3.1-SNAPSHOT`), or
the next minor if that is what is being released.

**Ask the user** (AskUserQuestion) whenever there is doubt — the numbers here are frequently
inconsistent (e.g. `current-version: 1.3.0` alongside `next-version: 1.3-SNAPSHOT`), and the
choice between a patch and a minor release is a judgment call. State the determined version
and confirm before proceeding rather than guessing.

### Step 2 — Determine current status (clean to release?)

```bash
gh pr list --repo cuioss/cui-open-rewrite --state open --json number,title,isDraft
```
- **No open PRs** → good, proceed.
- **Open PRs exist** → these would normally be merged before a release. Surface the list
  and **ask the user** whether to proceed anyway or wait. Do not silently ignore them.

Also confirm the working tree is clean (`git status --porcelain`) before branching.

### Step 3 — Pull current main

```bash
git checkout main && git pull --ff-only origin main
```

### Step 4 — Create the release branch

Branch name uses the `release/` prefix (required — the Maven CI workflow only triggers on
`main`, `feature/*`, `fix/*`, `chore/*`, `release/*`, `dependabot/**`; other prefixes skip
the `build` check and block auto-merge):

```bash
git checkout -b release/release_<version>   # e.g. release/release_1.3.1
```

### Step 5 — Update `.github/project.yml`

Edit the `release` block:
- `current-version:` → the version determined in Step 1 (e.g. `1.3.1`)
- `next-version:` → next patch (or minor) + `-SNAPSHOT` (e.g. `1.3.2-SNAPSHOT`)

Leave everything else untouched. The README's Maven Central badge is a dynamic
shields.io endpoint and the install snippet uses a `${cui-open-rewrite.version}` placeholder
— there is **no** per-release version to hand-edit in the README.

### Step 6 — Commit, push, open PR (with the skip-bot-review label)

```bash
git add .github/project.yml
git commit -m "chore(release): prepare release <version>"
git push -u origin release/release_<version>

# The skip-bot-review label suppresses the automated bot review on this mechanical
# version-bump PR. If the label does not yet exist in the repo, create it first:
gh label list --repo cuioss/cui-open-rewrite --search skip-bot-review \
  | grep -q skip-bot-review \
  || gh label create skip-bot-review --repo cuioss/cui-open-rewrite \
       --color BFD4F2 --description "Skip the automated bot review for this PR"

gh pr create --repo cuioss/cui-open-rewrite --base main \
  --label skip-bot-review \
  --title "chore(release): prepare release <version>" \
  --body "Bump current-version to <version>, next-version to <next>-SNAPSHOT. Triggers the automated Release workflow on merge."
```

The **`skip-bot-review` label is required** on the release PR — apply it at creation time
as shown. Use the project commit convention: `Co-Authored-By: Claude <noreply@anthropic.com>`
(no model name, no "Generated with Claude Code" footer).

### Step 7 — Wait for PR checks (~5–8 min)

Watch the checks rather than blindly sleeping:

```bash
gh pr checks <pr#> --repo cuioss/cui-open-rewrite --watch
```
If using a scheduled/loop wait, poll roughly every couple of minutes up to ~8 min.

### Step 8 — Handle PR comments / failures (if any)

- If a check fails, read the failing run's log (`gh run view <id> --log-failed`), fix the
  cause on the branch, push, and re-wait. **Never** merge a red PR.
- The `skip-bot-review` label suppresses the automated bot review, so no Gemini comments
  are expected. If a human nonetheless leaves comments (`gh pr view <pr#> --comments`),
  address them on the branch per the repo's PR-comment protocol in `CLAUDE.md`: reply to and
  resolve every comment; ask the user when uncertain.
- Re-run Step 7 after any push.

### Step 9 — Merge → release starts automatically

Once checks are green and any comments resolved:

```bash
gh pr merge <pr#> --repo cuioss/cui-open-rewrite --squash --delete-branch
```
Merging this PR (it touches `.github/project.yml`) fires `release.yml` automatically — do
**not** dispatch the release manually unless the auto-trigger demonstrably did not fire.

### Step 10 — Wait for the Release workflow (~30 min)

```bash
gh run list --repo cuioss/cui-open-rewrite --workflow "Release" --limit 3 \
  --json status,conclusion,displayTitle,databaseId
# then watch the in-progress run
gh run watch <databaseId> --repo cuioss/cui-open-rewrite
```
The workflow itself runs ~6 min; allow up to ~30 min for tag + GitHub release publish +
Maven Central propagation + pages deploy before treating it as stuck.

### Step 11 — Verify the release landed

```bash
gh release view <version> --repo cuioss/cui-open-rewrite \
  --json tagName,name,createdAt,body
git fetch --tags && git tag --list <version>
```
Confirm the tag exists and a GitHub release for `<version>` was created. If it did not
appear, inspect the Release workflow run log before proceeding.

### Step 12 — Reformat the generated release notes

The Release workflow creates the GitHub release with **auto-generated** notes (a flat
`## What's Changed` list). Rewrite them in place using the **house format below**, then
push the update:

```bash
mkdir -p .plan/temp
gh release view <version> --repo cuioss/cui-open-rewrite --json body --jq .body > .plan/temp/release-<version>-orig.md
# ...build the reformatted body in .plan/temp/release-<version>.md...
gh release edit <version> --repo cuioss/cui-open-rewrite --notes-file .plan/temp/release-<version>.md
```

#### House format rules (apply exactly)

1. **Two top-level groups:** `## Features & Enhancements` and `## Dependency Updates`.
2. **Features & Enhancements** — group functional PRs by theme with `###` subheadings,
   adapted to cui-open-rewrite's domain (an OpenRewrite recipe library), e.g.:
   - `### Recipes` — new or changed OpenRewrite recipes (e.g. `CuiLoggerStandardsRecipe`,
     `InvalidExceptionUsageRecipe`), recipe-matching/visitor logic, suppression handling
   - `### API & Code Quality` — public-API changes, refactors, cleanup, and standards work
   - `### Testing & Standards` — recipe test coverage, `RewriteTest` fixtures, CI matrix
     (e.g. rewrite-java-21/25 parser adoption)
   - `### Documentation`
   Adapt theme headings to the actual PRs; omit empty sections.
3. **Dependency Updates** — group by type with `###` subheadings (cui-open-rewrite is
   Java-only — there is no JavaScript group):
   - `### Java` — Java libraries (e.g. lombok, junit, cui-java-tools, cui-test-generator)
   - `### Infra` — platform/build/CI: build plugins, `cuioss-organization` workflow bumps,
     parent-POM / `cui-java-parent` updates
4. **Collapse version chains** — when the same artifact is bumped multiple times
   (`A → B → C`), keep only the **latest** entry spanning the full range
   (e.g. `openrewrite.version 8.86.1 → 8.87.0 → 8.88.0` becomes a single `8.86.1 → 8.88.0`).
5. **Keep OpenRewrite core bumps** — unlike downstream consumer projects, OpenRewrite itself
   (`rewrite-maven-plugin`, `rewrite-core`, `rewrite-java`, `openrewrite.version`) is a
   **primary dependency** of this project and belongs under `### Infra`. Do **not** drop it.
6. **Remove internal tooling churn** — drop PRs that only touch dev/build orchestration with
   no user-facing effect: `marshal.json`/plan-marshall config migrations, plan-marshall build
   wiring, internal dev-skill changes, and the mechanical version-bump PR itself.
7. Preserve each kept PR line verbatim (`* <title> by @author in <url>`); when two PRs share
   an identical title, merge them onto one line with both URLs.
8. Keep the trailing `**Full Changelog**: ...compare/<prev>...<version>` line.

### Step 13 — Done

Report: released version, release URL, the PR number, and a short summary of how many
dependency PRs were collapsed/removed during note reformatting.

## Critical rules

- The release is triggered by **merging a `.github/project.yml` change** — never hand-run
  Maven release goals.
- The release PR **must** use the `release/` branch prefix (or another CI-accepted prefix)
  or the build check skips and auto-merge is blocked. This skill and the releases it cuts
  share the `release/` branch prefix.
- The release PR **must** carry the `skip-bot-review` label — create it if it does not exist,
  then apply it at PR creation.
- Never merge a red PR; fix and re-wait.
- Temporary files go under `.plan/temp/`.
- Commit trailer: `Co-Authored-By: Claude <noreply@anthropic.com>`; no PR footer line.
