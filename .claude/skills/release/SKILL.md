---
name: release
description: Cut a cui-open-rewrite release — bump .github/project.yml version, open (with the skip-bot-review label) and merge the release PR on a release/ branch, wait for the automated Release workflow, verify the release landed, then reformat the generated GitHub release notes
user-invocable: true
allowed-tools: Bash, Read, Edit, AskUserQuestion
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

`.github/project.yml` is the single source of truth for both versions — read it, never
assume:

```bash
grep -E 'current-version|next-version' .github/project.yml
```

- `release.current-version` — the **last released** version.
- `release.next-version` — what `pom.xml` carries between releases.

**Scheme:** cui-open-rewrite uses a three-segment `X.Y.Z` version line. Patch releases
increment `Z`; minor releases increment `Y` and reset `Z`.

**Default rule:** the release version is `next-version` with `-SNAPSHOT` stripped, expressed
in the `X.Y.Z` scheme. Note that `next-version` is sometimes recorded as a two-segment
snapshot (`X.Y-SNAPSHOT`); normalize it to three segments (`X.Y-SNAPSHOT` → `X.Y.0`). The
new `next-version` is the next patch bump plus `-SNAPSHOT`, or the next minor if that is
what is being released.

**Ask the user** (AskUserQuestion) whenever there is doubt — the numbers here are frequently
inconsistent (a three-segment `current-version` alongside a two-segment `next-version`
snapshot), and the choice between a patch and a minor release is a judgment call. State the
determined version and confirm before proceeding rather than guessing.

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
git checkout -b release/release_<version>
```

### Step 5 — Update `.github/project.yml`

Edit the `release` block:
- `current-version:` → the version determined in Step 1
- `next-version:` → next patch (or minor) + `-SNAPSHOT`

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
gh pr merge <pr#> --repo cuioss/cui-open-rewrite --squash
```

`main` is governed by the org-managed merge queue (`main-merge-queue`), so this **enqueues**
the PR rather than merging it immediately, and `--delete-branch` is rejected (the queue
auto-deletes the head branch on merge). After `gh pr merge ... --squash`, poll until the PR
reports `MERGED` before expecting the Release workflow:

```bash
gh pr view <pr#> --repo cuioss/cui-open-rewrite --json state --jq .state
```

The release workflow itself is unaffected by the queue: `cuioss-release-bot` is a bypass
actor on `main-merge-queue`, so its direct push of the release commit + tag succeeds.

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
4. **Collapse by library identity — one line per library, spanning the full range.**
   The unit of collapsing is the *library*, not the PR title. Merge into a single line
   whenever the PRs concern the same library, in all three shapes that occur:
   - **Version chains** — several bumps of one artifact (`A → B → C`) collapse to one line
     spanning `A → C`, carrying the latest PR's author.
   - **The same library in several places** — one library bumped in more than one module or
     directory is **one** line naming them all, not one line each. Those titles differ only
     by that suffix, so do not wait for identical titles before merging.
   - **One upstream release landing as several coordinates** — when a single upstream bump
     arrives as separate PRs against different coordinates (e.g. a version property *and*
     a BOM or parent), that is **one** bump: one line naming the coordinates in parentheses.

   Carry every merged PR's URL onto the surviving line, comma-separated.
5. **Recover versions the title omits.** Dependabot truncates a title to
   `bump <lib> in /<dir>`, with no versions, when several dependencies must move together.
   Never publish a dependency line without a version range: read the PR body, which states
   ``Updates `<lib>` from X to Y``, and use those versions when computing the range:

   ```bash
   gh pr view <n> --repo cuioss/cui-open-rewrite --json body --jq .body | head -6
   ```
6. **Keep OpenRewrite core bumps** — unlike downstream consumer projects, OpenRewrite itself
   (`rewrite-maven-plugin`, `rewrite-core`, `rewrite-java`, `openrewrite.version`) is a
   **primary dependency** of this project and belongs under `### Infra`. Do **not** drop it.
7. **Remove internal tooling churn** — drop PRs that only touch dev/build orchestration with
   no user-facing effect: `marshal.json`/plan-marshall config migrations, plan-marshall build
   wiring, internal dev-skill changes, and the mechanical version-bump PR itself.
8. **Preserve each kept PR line** in its original
   `* <title> by @author in <url>` shape. Rules 4 and 5 **override** verbatimness where
   they conflict: rewrite the title's version range to span the collapsed chain, and name
   the several modules or coordinates on the surviving line.
9. Keep the trailing `**Full Changelog**: ...compare/<prev>...<version>` line.

#### Verify before publishing (mandatory)

These rules are easy to under-apply: a duplicate survives whenever two PRs touch the same
library under differing titles. After building the notes file and **before**
`gh release edit`, assert that every library appears exactly once:

```bash
grep -oE '(bump|update) [^ ]+ (from|in)' .plan/temp/release-<version>.md \
  | sort | uniq -c | sort -rn | head
```

Every count must be `1`. Any count `>1` is an unmerged duplicate — collapse it per rule
4 and re-run. Also confirm that no dependency line is missing a version range
(rule 5).


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
