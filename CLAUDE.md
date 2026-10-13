# CLAUDE.md — Nuvepro Moodle UI Automation (Java)

Project context and conventions for this automation project. Read this before changing tests.

## What this is
All-BDD UI regression automation for the Nuvepro Moodle LMS (Moodle 4.5.4).
**Stack:** Playwright-Java 1.48 + TestNG 7.10 + Cucumber-JVM 7.18 + Maven (via `mvnw`), Java 17.
Tests run against a **deployed** Moodle URL (see `.env`), driving the UI; test data preconditions
use Moodle/plugin APIs where possible. The Python reference suite lives at `../automation`.

Manual test cases come from `../Docs/Testcases/MoodleRegression_Testing.xlsx`. Each scenario is
tagged with its manual-case id (`@PG-1` … `@PG-16`, area prefix + row number) — that tag is the
traceability link.

## Test-case coverage methodology (MANDATORY — derive cases from artifacts, not intuition)
The manual sheets are checklists, not exhaustive specs, and generating cases from a mental checklist
MISSES cases (we missed "Create from Manage Labs" and "create-after-delete" by anchoring on one
example). For ANY module, derive cases mechanically from these FOUR coverage models, then
de-duplicate:
1. **Control/UI inventory** — enumerate EVERY interactive element from the plugin `.mustache`
   templates (buttons, links, dropdowns, inputs, tabs, checkboxes, modals). One case per control:
   does it work, what is the outcome, and its enabled/disabled conditions. Do NOT extract selectively.
2. **State-transition model** — model the entity as a state machine (lab: NotCreated → Creating →
   Running → Stopped → Deleted → Failed; actions Create/Start/Stop/Delete/Sync). Enumerate EVERY
   valid transition (happy) AND invalid transition (negative), including re-entry: create-after-delete,
   restart-after-stop, recreate-after-fail.
3. **Requirement/acceptance coverage** — one case per FDD / feature-doc acceptance criterion
   (`../Docs/FDDs`, `../Docs/Featuredocs` — the authoritative behaviour + edge cases).
4. **Cross-cutting matrices** — roles × actions; template options (cost/duration/notes/speedtest/
   catalog/iframe/composition) × on-off; input fields × valid/invalid/boundary; platforms
   (AWS/CSP/GCP) × features.

Then **de-duplicate against ALL 19 sheets** of the xlsx (a mechanic may already live elsewhere — e.g.
Manage Labs create/export is partly in the 'Activities' / Pre-create labs sheet). Present a **coverage
matrix** (rows = control/transition/criterion; cols = positive/negative/role/boundary); empty cells = gaps.

**Failure modes to avoid (why cases get missed):** anchoring on the example the user mentioned instead
of inventorying the whole page; extracting controls selectively; testing atomic actions but not
transitions/sequences/re-entry; generating from intuition instead of reading the artifacts; not
reconciling against the other sheets (causes both misses AND duplicates).

**Success criteria:** a case passes when the ACTION COMPLETES with the expected end state (Start →
Running, Delete → removed), NOT merely that a control exists. Always exercise the interaction and
assert the outcome (see "Exercise the action, don't just assert presence").

**Augmented (gap-fill) cases:** add to the xlsx as new rows — id `CGAP-<AREA>-<n>` in the S.No column,
Comment `Automation gap (derived ...) <date>`, other rows continue after the originals. **Never write
backup copies of the workbook** (no `.backup-<date>.xlsx` — QA's standing instruction): edit
`docs/MoodleRegression_Testing_Kiwi new.xlsx` in place (note the space in the filename), use a
duplicate-guard, and touch ONLY that sheet. `docs/MoodleRegression_Testing_Kiwi.xlsx` is the BACKUP
of the previous state — never edit it.
Automate them tagged (`@CGAP-<AREA>-<n>`) so traceability distinguishes original vs augmented.

**Per-sheet workflow — DO IN THIS ORDER. Gap analysis + writing CGAP rows into the sheet is a GATE
that comes BEFORE any automation scripting:**
1. Read ALL manual cases in the sheet.
2. Inventory the control surface (templates/pages) + state machine + roles/inputs/boundary matrices.
3. Derive gaps and **append `CGAP-<AREA>-<n>` rows into that sheet** (back up; duplicate-guard;
   confirm before writing to the shared workbook — it is the team's source of truth).
4. ONLY THEN write feature files / step definitions and automate.
Scripting before the derived gaps are captured in the sheet is out of order — the sheet must reflect
the full derived coverage first.

## Scope & sequencing (STANDING — apply by default; see TODO.md for deferred work)
- **Priority:** respect the case `Importance` column. Automate **High then Medium** first;
  **Low-priority cases are deferred** (not taken up now). QA owns the priority; assign realistic
  priorities to gap cases (do NOT blanket-mark everything High).
- **Roles under test = admin and student ONLY** (by default). Do NOT author/automate manager,
  teacher, or non-editing-teacher role cases unless explicitly requested. Role cases assert:
  admin can manage/act; student can launch an assigned lab but cannot manage/act.
- **Lab-template type sequencing:** complete the FULL case set against **Account** lab templates
  FIRST. Only AFTER that, add — at the end — the variants for the lab-action-relevant cases:
  1. cloud **platforms** (AWS / CSP / GCP), 2. **Composition** labs, 3. **VM** labs.
  Do not interleave these early; they are deferred (see TODO.md).
- These variants require selecting **activities configured with those template types**. Maintain the
  cmids in `.env`: `PLAYGROUND_CMID` = an Account sandbox (current); add `PLAYGROUND_VM_CMID`,
  `PLAYGROUND_COMPOSITION_CMID`, and per-platform cmids when those phases start. Cases needing a
  template type not yet available should config-gate skip (as PG-4/PG-9 do).

## How to run (from this folder)
- Fast (hidden): `mvnw test`
- Provisioning (real cloud lab): `mvnw test -Pslow`
- **Watch one scenario:** `mvnw test -Pwatch -Dheadless=false -Dslowmo=500 -Dcucumber.tags="@PG-1"`
- `-Pwatch` runs ONLY the Cucumber runner (no ConnectivityTest). `-Dcucumber.tags` filters ONLY
  Cucumber scenarios, not plain TestNG classes.
- Headed/slowmo/tags are resolved as `-D` system properties overriding `.env` (see `Settings`).

## Architecture / conventions
- **All-BDD.** Every case is a tagged Cucumber scenario in `src/test/resources/features/*.feature`.
  No plain-TestNG test classes for cases (only `ConnectivityTest` = login smoke). Don't duplicate a
  case as both a scenario and a `@Test`.
- **Login once.** `GlobalHooks.@BeforeAll` logs in and saves Playwright `storageState`
  (`target/state.json`); every scenario's context loads it → already authenticated, NO per-scenario
  login. This also fixed login-navigation timeouts under load. Don't add per-scenario logins.
- **No feature-wide `Background`.** Each scenario opens the lab landing ONLY when it needs it
  (`Given I open the playground activity`). Create/report scenarios (PG-1, PG-10) must NOT open the lab.
- **Provisioning = real cloud cost.** `@provisioning` scenarios spin a REAL lab. TWO patterns —
  pick by what the case tests (see "Lab provisioning patterns" below):
  1. **Lab-ACTION cases** (create/start/stop/delete lifecycle) → **fresh per-user lab** (`@freshlab`):
     seed a throwaway user + enrol (Web Services), create their lab via Manage Labs, act, tear down.
     Immune to lab expiry, cp.php-free. This is the RELIABLE pattern for action/state tests.
  2. **EXISTING-lab / read-only cases** (popups, notes, usage on a running lab) → reuse an existing
     lab; do NOT create fresh. If it risks expiring mid-test, EXTEND its duration via the CloudLabs
     API (see below) rather than reprovisioning.
- **Page Objects** hold all selectors, read from the plugin Mustache templates in
  `../Main/Dev/Activity/cloudlabs/templates/`. Prefer stable author ids/classes → role → label.
- **Exercise the action, don't just assert presence.** A scenario should DO the user interaction the
  manual case describes (open the dropdown, click the control) and then assert the outcome — not just
  check that a control exists. Presence-only checks look like "nothing happened" in headed mode and
  under-test the case. (Verify each case in headed mode: `-Pwatch -Dheadless=false -Dslowmo=800`.)
  Exception: don't execute genuinely destructive/costly actions (e.g. actually Delete a lab, or
  spin a cloud lab) unless the case is specifically about that and is tagged `@provisioning`.

## Manage Labs selectors (managelabs.php)
- Actions dropdown toggle: `#np-ap-cl-assessment-lab-actions` (text "Actions"); items Start
  `#np-ap-manage-start-btn`, Stop `#np-ap-manage-stop-btn`, Delete `#np-ap-manage-delete-btn`
  (each `disabled` until a row is selected). Create `#np-ap-manage-create-btn`, Lab Details
  `#np-ap-manage-view-btn`, Sync `#np-ap-manage-sync-btn`. Row select checkboxes:
  `#np-ap-manage-lab-table input[name='btSelectItem']` (bootstrap-table). The `#np-ap-cl-manage-active-labs-actions`
  id is the REPORT dropdown, NOT actions — don't confuse them.

## Lab operations — MANDATORY method (API-driven, fresh lab). Use this for EVERY lab-action test.
A single long-lived shared lab EXPIRES mid-run and UI status is STALE/fragile. The proven, reliable
method (validated `@freshlab` + `@labactions`) — follow it across the whole suite:

1. **Provision a FRESH per-user lab** (never a persistent shared lab). `helpers/ApiClient` (Moodle WS,
   "Automation Seeding" token): `createUser(stamp)` + `enrolUser(id, courseId, 5)`; then
   `ManageLabs.createLabForUserEmail(email, sectionId)` (not-created-users tab, admin, **no cp.php**).
2. **Capture the lab id (== provider `subscriptionId`) AT CREATION** via `ManageLabs.labIdForEmail(email)`
   (retry a few times; the row shows the email while the user still exists). Operate by **lab id** after.
3. **Poll STATUS via the CloudLabs API — NOT the Manage Labs UI.** `CloudLabsClient.waitForState(labId,
   targets, maxSeconds, intervalSeconds)` calls `getSubscriptionsStatus` and LOGS every poll
   (`[CloudLabs] poll N lab=.. -> Create/Complete = RUNNING`). Maps `nl_action`(Create/Start/Stop/Delete)
   + `nl_status`(Complete/InProgress/Failed) via `ManageLabs.classifyActionStatus(a,s)` → `LabState`.
4. **Actions (Start/Stop/Delete)** via `ManageLabs.performActionForEmail(email, SELECTOR, sectionId)`
   (UI click; works while the user exists), then **verify via the API poller** (step 3).
5. **Extend / expire duration** via CloudLabs: `extendHours(labId,n)` / `reduceHours` / `expireLab`.
   Detect expiry on the seeded user's LANDING: `#lab-remaining-pl == "00:00"` (console-free).
6. **Delete ONCE at teardown** (`@AfterAll` / `@After`) + `ApiClient.deleteUser(id)`. Not per action.

**Reference impls:** `steps/FreshLabSteps.java` (per-scenario lab), `steps/SharedLabSteps.java` (one lab
shared across scenarios, deleted once), `features/fresh_lab.feature`, `features/lab_actions.feature`.

**CloudLabs API (`helpers/CloudLabsClient`) — SEPARATE system from Moodle.** `.env`:
`CLOUDLABS_GATEWAY_URL` (=qalb.cloudloka.com), `CLOUDLABS_ADMIN_USER`, `CLOUDLABS_ADMIN_PASS`.
- `POST {gw}/v1/users/login` (form user/pass) → JSON `{"token":..}` AND a session cookie `SSESS…`.
- All other calls send header `X-CSRF-Token: {token}` **AND the session cookie** (CookieManager) —
  token alone → 403 "anonymous". Endpoints: `/v1/subscriptions/getSubscriptionsStatus` (body
  `subscriptionIds`), `/extendDuration` (body `subscriptionId,duration,durationType=Hours`; negative
  = reduce/expire). Trust-all SSL (strictSSL false).

**MEASURED provider timings (don't assume — these were measured):** create ≈ instant (RUNNING on
poll 1), start ≈ instant, stop ≈ 15s, **delete ≈ 5-8 min (slow AND variable)** — the only slow op.
Ceilings: 180s create/start/stop, **600s delete** (measured a delete exceed 7 min); interval 5s
(15s for delete to cut log noise).

**ANTI-PATTERNS — do NOT (each cost us real time this project):**
- ❌ Scrape lab status from the Manage Labs UI — it is STALE (UI showed `Create/Complete` for labs the
  API reported `Deleted`). Use the API (step 3).
- ❌ Match a lab by email after the user is deleted — the email column becomes a HASH. Capture the
  lab id at creation and use it.
- ❌ Set 20-min timeout ceilings "to be safe" — create is instant; measure real timings first.
- ❌ Poll silently — LOG every poll so a failure is diagnosable from ONE run (a silent poller cost a
  22-min hang that a log would have shown in 2 min).

**Read-only views** (Usage/Info/Instructions/Credentials/Notes) live on the console → need the seeded
user's login + cp.php; keep each tolerant (skip if the console won't load). The old UI-scraping
`waitForEmailState`/`ensureState`/`firstRowState` are DEPRECATED for lab state — keep them only for
non-lab Manage Labs listing checks (PG-2/11/20). bootstrap-table loads rows via async AJAX, so any
residual UI read must wait for a real row (`open()` already does).

## Corrections & gotchas we hit (do not regress these)
- **PG-1 course id via config, not a page load.** PG-1 (create activity) reads `COURSE_ID` from
  `.env` (e.g. course URL `?id=72`) and goes straight to `course/modedit.php?add=cloudlabs`. It must
  NOT open the sandbox landing to scrape the course id (that showed a confusing lab "Continue" page).
- **@AfterAll lab safety-net is guarded** by `GlobalHooks.labProvisioned` (set true only by
  start-lab / running-lab / VM steps). Non-provisioning runs must NOT open the sandbox lab page at
  teardown. If you add a step that provisions, set the flag.
- **dotenv-java `.env` rules:** it treats `#` as an inline comment → QUOTE any value containing `#`
  (e.g. passwords: `MOODLE_ADMIN_PASS="your#password"`). And the file must be **UTF-8 without BOM**
  (PowerShell `Set-Content -Encoding UTF8` adds a BOM → "Malformed entry"; write BOM-less).
- **Confirm modals (Moodle 4.5):** don't target `.modal.show`/`[data-action=save]` directly (hidden
  duplicate modals exist). `BasePage.confirmModal()` POLLS `page.evaluate` to click the VISIBLE
  `[data-region=modal-container]` save button (handles "Yes" and "Stop"). Don't revert to a single click.
- **Slow form submits (Moodle POSTs > 15s):** never `submit.click()` + `waitForLoadState()` — the
 click times out "waiting for scheduled navigations" and `waitForLoadState` returns on the OLD page.
 Use `BasePage.submitAndAwaitNewPage(locator)` / `helpers.FormSubmit.andAwaitNewPage(page, locator[, ms])`
 (marks the document, no-wait click, polls for the new one; returns early on a visible validation
 error). Pass ~30_000 for negative-validation submits. Long jobs with their own poll loop (upload
 users, course copy, restore) use `clickSlowSubmit` and wrap DOM probes in try/catch.
- **JDK:** `mvnw.cmd` auto-selects an installed JDK ≥17 when `JAVA_HOME` is missing/older (Java 8 is
 first on PATH here); the enforcer rule fails fast otherwise.
- **Lab-state dependency:** after a lab exists the landing shows "Continue" not "Start Sandbox".
  Use `PlaygroundLanding.openLabConsole()` (handles both). The lab is SERVER-SIDE, so a fresh browser
  context reopening the activity still sees the running lab.
- **Cloud-UI timing:** lab status flips to RUNNING before the console UI (Jump to Console, usage FAB)
  renders. Wait for a console-ready signal / use visibility waits before asserting; don't skip
  immediately. Poll ~80s for stop to settle.
- **Config-gated skips** use TestNG `SkipException` inside a step — cucumber-testng treats it as
  SKIPPED (verified). Used for PG-4 (needs `PLAYGROUND_VM_CMID`), PG-7 (speed test off), PG-9 (needs
  `PLAYGROUND_CATALOG_CMID`).

## Reports & traceability
- Cucumber HTML: `target/cucumber-report.html`; TestNG: `target/surefire-reports/emailable-report.html`.
- **Traceability + execution** (manual case → scenario → last result): `reports/traceability_execution.{html,csv}`,
  from `tools/traceability.py` (Python; merges `target/cucumber_fast.json` + `cucumber_slow.json` — save
  each run's `target/cucumber.json` to those names before regenerating). "Automated" = a scenario exists.
- **After ANY Kiwi / coverage / @tag change (same PR or agent turn):** run
  `python tools/sync_kiwi_and_traceability.py` — refreshes dashboard formula caches and regenerates the
  traceability matrix. Do this without waiting for the user to ask. Optional: `--strict` fails on dashboard DRIFT.
- **On-demand cleanup (never default `mvn test`):** after the user confirms a green module run, they run
  `mvnw test -Pcleanup` or `cleanup.cmd` (see `docs/CLEANUP.md`, `RUNNERS.md`). Track artefacts in
  `target/automation-manifest.properties` and suite-course property files; do not delete `.env` fixture
  courses/cmid-only resources. Module runs: `-Dautomation.deferCleanup=true`; preview: `-DcleanupDryRun=true`.
- **Failure diagnostics (automatic).** On any scenario FAILURE, `steps/FailureCapture` (wired into the
  `Hooks.@After`) dumps `target/failures/<scenario>/`: `screenshot.png`, `page.html` (DOM at failure),
  `console.log` (browser console + page errors, collected via `TestContext` listeners), `trace.zip`
  (Playwright trace — open with `npx playwright show-trace <zip>`), and `summary.txt`. The screenshot +
  console are also attached to the Cucumber HTML report. A trace records for EVERY scenario but is kept
  only on failure (discarded on pass), so green runs stay cheap. Capture is best-effort — it never masks
  the real failure.
- **Failed + skipped digest:** `python tools/failure_report.py` parses `target/cucumber.json` into
  `reports/failures.{md,csv}` — one line per non-passing scenario with its @tag, status, failing/skipped
  step, the reason (SkipException/assertion message), and a link to its `target/failures/` artifacts.
  This is the fastest way to triage a run without re-executing it.

## When extending (Guided / Assessment / other sheets)
- Mirror this pattern: tagged scenarios (`@GP-n`, `@ASMT-n`), Page Objects from the templates,
  `@provisioning` + shared lab for real-lab flows, config-gated skips for special preconditions.
- Selectors: guided-landing.mustache / assessment-landing.mustache (already inventoried in the Python
  Page Objects `../automation/pages/*`). Reuse those.

## Coverage tracking — PER CASE, not per sheet (IMPORTANT)
- Status lives in **`docs/MoodleRegression_Testing_Kiwi new.xlsx`** (the master), per-case columns
  `Automated` + `Automation_Ref`, summarised on the **`Automation_Coverage`** dashboard sheet. NOT in
  CLAUDE.md (this file is method/instructions), and never claim a whole "sheet automated".
- **The two dashboard sheets are QA-OWNED — do NOT rewrite them.** `Regression_Dashboard` lists only
 the modules worked so far (`Executed` = `Automated`=Yes AND `Case_Status` in Pass/Fail/Blocked;
 `Not Automatable` = `Automated`=No; `Completion %` = Executed / (Total − Not Automatable)) and
 `Automation_Coverage` summarises the `Automated` column per sheet. QA maintains their formulas and
 calculates them outside this machine (no Excel licence here, so anything we compute must be a typed
 value — and rewriting their layout has burned us twice).
- **After ANY case change run `python tools/check_kiwi_dashboards.py`** — read-only: it recomputes
 every dashboard number from the case rows and prints `ok` or `DRIFT` per row, exiting 1 on drift.
 Report drift to QA (or ask before typing corrected numbers in).
- **After ANY workbook save from openpyxl run `python tools/refresh_kiwi_dashboard.py`.** openpyxl
 drops the cached result of EVERY formula it saves, which makes those cells render blank without an
 Excel licence to recalculate — this script recomputes and re-injects the caches for
  `Regression_Dashboard` + `Release_Regression_Plan`. It also owns the row order QA asked for on BOTH
  sheets — Users, Upload users, All course page, Sandbox, Assessment, Lab Templates
  (`TRAILING_MODULES`), then the plan's remaining modules
 unchanged — where the dashboard's single merged Assessment row takes its Total from the plan sheet and
 its Executed from the two assessment suites. The plan's formulas are regenerated per row (`I`/`J`/`L`
 reference their own row), so a module can move without dragging stale references, and any module
 sheet missing from the plan is added (`PLAN_ADDITIONS`) so TOTAL counts every sheet.
- Vocabulary: **`Yes`** = a green test verifies THAT case (put the `feature @tag` in `Automation_Ref`);
  **`Partial`** = partly covered (say what's missing in the ref); **`Deviation`** = a fail-by-design
  test flagging a real defect; **`Deferred`** = derived `CGAP-*` gap not yet automated; **`No`** = not
  automated (put the reason in the ref); **blank** = not judged yet.
- **Mark `Yes` ONLY where the test verifies that exact case — presence != behaviour.** Do not keyword-
  guess: it over-claims broad words ("add"/"assign"/"start") and under-claims when the case wording
  differs from the scenario. When you automate a new case, set its `Automated` row in the same change.
- **The `Steps` column is UNRELIABLE — do not depend on it.** It has copy-paste/templating errors (e.g.
  an Assessment "create activity for all platforms" case whose Steps describe creating a *playground*
  lab). Always derive the real steps from the **Summary + Title** + the actual plugin behaviour
  (mustache templates / mod_form / services / live selectors). Treat `Steps` as a weak hint at most; if
  it contradicts the Summary, trust the Summary and the system.
- `PROGRESS.md` holds the living per-module narrative; the xlsx dashboard is the authoritative per-case
  source. Keep both in sync.
