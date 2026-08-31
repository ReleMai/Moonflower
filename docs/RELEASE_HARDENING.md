# MoonFlower Release Hardening

## Purpose

Keep MoonFlower production updates reviewable, privacy-safe, recoverable, and
cheap for the launcher to consume. GitHub `main`, required validation, and the
CI-produced release package are the production boundary. Generated local
clients, Steam copies, and launcher caches are never production patch targets.

## Ordered Work

1. **Protected `main` with pre-main validation** — active. Validate the exact
   feature-branch commit before it can advance `main`; require successful
   privacy, build, and deterministic checks; block force pushes and deletion.
2. **Separate validation from publication** — active. Run policy checks for
   workflow and documentation changes without producing another client ZIP;
   publish only when package-affecting inputs change.
3. **Make Gitleaks a required release dependency** — active. A commit cannot
   advance production until both the MoonFlower privacy policy and the full
   Gitleaks history scan pass for that exact SHA.
4. **Immutable rollback builds** — queued next. Publish commit-addressed builds,
   retain a bounded verified history, include the previous stable package in
   the feed, and support an explicit launcher rollback without patching a JAR.
5. **Post-publication end-to-end package verification** — planned. Download the
   actual remote asset into a clean temporary root, verify hash and safe
   extraction, and prove the packaged updater can consume the published feed.
6. **Build provenance and SBOM** — planned. Publish a package inventory,
   dependency inventory, and GitHub artifact attestation; evaluate a signed
   update manifest without embedding private signing material in the repo.
7. **Dependency and static-security scanning** — planned. Enable reviewed
   Dependabot updates for supported ecosystems and add appropriate CodeQL/static
   analysis without granting write access to untrusted pull-request code.
8. **Visible release notes** — planned. Generate player-readable release notes
   from the required commit sections and make them available to the launcher.

## Phase 1 Acceptance: Items 1-3

- A feature-branch workflow validates the exact unpublished range and cleanly
  builds/tests package-affecting changes.
- The validation workflow always reports one stable required-check name, even
  when a documentation-only commit safely skips the expensive client build.
- Gitleaks and the MoonFlower policy are required for the exact commit before
  `main` advances.
- A GitHub ruleset protects `main` from deletion, non-fast-forward updates, and
  commits lacking the required checks.
- The rolling release workflow runs only for package-affecting paths and does
  not republish the 180+ MB client for policy/documentation-only changes.
- A controlled feature-to-main publish succeeds through the new protection,
  and a read-only audit confirms the ruleset and check results.

## Phase 2 Acceptance: Item 4

- Each package is uploaded under an immutable commit-addressed release tag.
- The stable feed points to that immutable package and includes the immediately
  previous verified build when one exists.
- A bounded retention policy keeps the newest five immutable builds.
- `Play.bat -Rollback` selects the previous verified build, downloading and
  verifying it when it is not already cached; ordinary startup behavior remains
  unchanged.
- Deterministic updater checks cover rollback, corruption fallback, and both
  schema compatibility paths.
- The protected feature-to-main workflow, rolling publication, remote manifest,
  immutable asset, and launcher checks all pass in a final audit.

## Safety Boundaries

- Do not stop the user's running client merely to publish; use clean GitHub
  runners for package builds.
- Do not modify `client/bin`, a downloaded version, Steam content, or launcher
  caches as part of these tasks.
- Preserve unrelated dirty work and stage only release-hardening files.
- Keep Steam Workshop publication separate and owner-only private.
