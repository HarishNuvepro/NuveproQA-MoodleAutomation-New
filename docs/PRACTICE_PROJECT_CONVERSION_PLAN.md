# Practice Project — conversion plan (121 “No” → automate)

**Goal:** Turn the **~86–90 environment-blocked** rows into **Yes** with green runs, without touching the **~31 dormant/cron** rows (stay **No**).

**Audit tool (read-only):** `python tools/audit_pp_no_cases.py`

---

## Current truth (Kiwi + repo)

| Metric | Count |
|--------|------:|
| Practice_Project rows | 191 |
| **Automated = Yes** | 70 (many “Covered by @PP-n”) |
| **Automated = No** | 121 |
| Distinct `@PP-*` scenarios in repo | ~37 |
| **No but scenario already authored** | **25** (Kiwi not updated — fast win) |

---

## What stays **No** (~31) — do not schedule

**Get Progress / cron / timeline** (commented UI or needs cron control): rows called out in `tools/add_practice_project_gaps.py` → `GET_PROGRESS_NO` (e.g. PP-26, 132–135, 155–158, 161, 174, …).

**Policy:** No automation spend unless product re-enables Get Progress or QA accepts **Deviation** tests that only assert “control absent”.

---

## Conversion waves (order matters)

### Wave 0 — Prerequisites (you + QA; **gate before Wave 1**)

| # | Input | Used for | ~Rows |
|---|--------|----------|------:|
| 0.1 | **`GUIDED_CMID`** — main practice activity (Account template, stable) | All landing/admin/console | (baseline) |
| 0.2 | **Variant cmids** in `.env`: `GUIDED_WITHOUT_PROGRESS_CMID`, `GUIDED_NO_STOP_CMID`, `GUIDED_PARALLEL_CMID`, `GUIDED_TIMED_CMID`, plus any for PP-23/24/25/28/30/38/136/140 | Wave 3 | **12** |
| 0.3 | **`PRACTICE_DETAILED_REPORT_URL`** + **`PRACTICE_SUMMARY_REPORT_URL`** (Moodle Report Builder) | Wave 4 | **46** |
| 0.4 | **CloudLabs admin login** works (no HTTP 401 on gateway) | Wave 2 | **11** |
| 0.5 | **WS token** includes `mod_cloudlabs_get_guided_project_result` (and related read functions) | Wave 5 | **10** |
| 0.6 | **Second student / report user** enrolment OK on PP course | Reports scenarios | (subset of 46) |
| 0.7 | Confirm **`-Pslow` / `@provisioning`** runs allowed on QA Moodle (cost + slot) | Lab waves | **18+** |

**Run discipline:** module runs with `-Dautomation.deferCleanup=true`; after green module → `cleanup dry` → `cleanup all` (see `RUNNERS.md`).

---

### Wave 1 — **Kiwi sync only** (0 new code; ~1 day)

**25 rows** already have `@PP-n` in features but Kiwi still says **No** (blocked reason in `Automation_Ref`).

| Bucket | PP examples | Feature file | Action |
|--------|-------------|--------------|--------|
| CloudLabs 401 | 11, 12, 20, 21, 27, 29, 33, 37, 42, 48, 139 | `guided_lab.feature`, `guided_attempts.feature` | Fix 0.4 → run `@guidedlab` / tags → mark **Yes** |
| Reports (partial) | 69, 71, 73, 79, 80, 89, 94, 137 | `practice_project_reports.feature` | Fix 0.3 → run file → mark **Yes** |
| Variants (partial) | 14, 19, 22, 45 | `practice_project_user_functionality.feature` | Fix 0.2 → run with `-Pslow` where tagged → **Yes** |
| WS API (partial) | 59, 63 | `practice_project_actions.feature` | Fix 0.5 → run → **Yes** |

**Exit criteria:** All 25 green once prerequisites met; run `python tools/sync_kiwi_and_traceability.py`.

---

### Wave 2 — **CloudLabs lab lifecycle** (~11 PP ids)

**Depends on:** 0.1, 0.4, 0.7.

- Run headed: `@guidedlab`, `@guidedattempts`, multi-tag scenarios in `guided_lab.feature` / `guided_attempts.feature`.
- Uses existing **fresh student + API poll** pattern (same as Sandbox).
- **No new scenarios** unless a PP id is not covered by multi-tag scenario — then extend tags, not duplicate flows.

---

### Wave 3 — **Variant activities** (~12 PP ids)

**Depends on:** 0.2, 0.7.

- One **pre-built activity per variant** on QA (do not mutate `GUIDED_CMID` mid-suite).
- Scenarios already config-gate with `SkipException` — flipping **No → Yes** is config + green run.
- Optional later: create variants in **Practice suite course** once and store cmids in `practice.env` (mirrors `assessment.env`).

---

### Wave 4 — **Moodle configurable reports** (~46 PP ids)

**Depends on:** 0.3, 0.6, reports seeded user flow.

- Scenarios exist in `practice_project_reports.feature` (8 scenarios cover many PP rows via shared behaviour).
- Work is mostly **QA providing stable report URLs** + one-time check that filters/columns match live Report Builder.
- May need **small PO updates** if column ids differ on your site.
- Run: `practice_project_reports.feature` with deferCleanup; may create enrolments → `@cleanup-users` / course manifest as documented.

---

### Wave 5 — **Guided Project Result API** (PP **59–68**, 10 rows)

**Depends on:** 0.5.

- Scenarios in `practice_project_actions.feature` (`@PP-59`, `@PP-63`, …).
- **DevOps/Admin:** add web service function to Automation Seeding token (same as other WS seeding).
- Then run API scenarios and mark **Yes**.

---

### Wave 6 — **New or extended scenarios** (~7–15 rows)

Not fully authored yet (audit bucket **F** + **G**):

| Topic | Examples | Approach |
|--------|----------|----------|
| Console with running lab (credentials/PDF/help) | PP-13 | Extend `@guidedlab` assertions after Running |
| Override status **Not Attempted / Started** | PP-56 | Seed in-progress attempt fixture or lab state |
| Last attempt Stop/Delete, exhaust attempts, restart-in-gap | 177–179, 191–192 (sheet rows) | `@provisioning` + fresh user; align with CGAP-PP-5/6 |
| Outside date window | CGAP-PP-6 | Throwaway activity in **Practice suite course** (dated fields) |
| Product gaps (multi-select table, checkpoint modal, export dropdown) | 117, 131, 185, override “no attempts” | **Deviation** or product fix — confirm with QA before coding |

**Rule:** Append any new manual gaps to Kiwi as **CGAP-PP-n** before automation (`CLAUDE.md` workflow).

---

## Target outcome

| After waves | Yes (approx.) | No (approx.) |
|-------------|---------------|--------------|
| Today | 70 | 121 |
| Wave 1–5 complete | **~156–160** | **~31–35** (dormant + confirmed deviations) |

---

## Execution checklist (each wave)

1. Prerequisites ticked for that wave.
2. Run module with `-Dautomation.deferCleanup=true` (and `-Pslow` if `@provisioning`).
3. Triage failures → `python tools/failure_report.py`.
4. Update Kiwi **Automated** / **Case_Status** only for cases the scenario actually verifies.
5. `python tools/sync_kiwi_and_traceability.py`.
6. User-approved → `cleanup.cmd dry` → `cleanup.cmd all`.

---

## Risks and mitigations

| Risk | Mitigation |
|------|------------|
| Mutating shared `GUIDED_CMID` breaks parallel runs | Variants only via separate cmids; dated tests use suite course |
| Report URLs differ per environment | Document URLs per env; skip with clear message if unset |
| CloudLabs cost/time | Batch `@provisioning` in `-Pslow` windows; reuse fresh-lab pattern |
| Over-claiming “Yes” for multi-row coverage | One scenario → update primary row **Yes**, others “Covered by @PP-n” |

---

## In progress (no report URLs required)

- **`PracticeProjectFixtures`** — auto-creates primary + variant guided activities in **AUTO Practice Suite**; writes `practice.env` + `target/practice-fixtures.properties`.
- **`Settings.practiceKey`** — reads `practice.env` then `.env` for `GUIDED_*` cmids.
- **`GuidedSteps` / `PracticeProjectSteps`** — use fixtures (no mandatory `GUIDED_CMID` in `.env`).
- **`@PP-59` / `@PP-63`** — tagged `@skip` (WS token not permitted).

**Run now (defer cleanup recommended):**

```powershell
.\mvnw.cmd test -Pwatch -Dheadless=false "-Dautomation.deferCleanup=true" "-Dcucumber.features=src/test/resources/features/practice_project_user_functionality.feature"
.\mvnw.cmd test -Pwatch -Dheadless=false "-Dautomation.deferCleanup=true" "-Dcucumber.features=src/test/resources/features/practice_project_actions.feature"
.\mvnw.cmd test -Pwatch -Dheadless=false "-Dautomation.deferCleanup=true" "-Dcucumber.features=src/test/resources/features/practice_project.feature"
```

First run creates the suite course + activities (admin UI). Report feature still skips until URLs are set.

---

## Decisions (2026-09-21)

| Topic | Decision |
|--------|----------|
| **CloudLabs** | Unknown — run one headed `@guidedlab` probe before Wave 2 |
| **Reports** | **Deferred — not automatable** in Kiwi (~46 rows); `practice_project_reports.feature` is `@skip` until QA provides URLs later |
| **Variants** | **Automation creates** variant activities in Practice suite course; persist cmids (new `practice.env` or properties file) |
| **WS API (PP-59–68)** | **Token extension not allowed** — keep **10 rows No** (not in the 86 target) |
| **Priority** | Engineering-led order in next section |

**Revised automatable target:** ~**76** from the old “86” (exclude **10 WS API** + **~31 dormant** stays No). Plus **~25** scripted flip = **~101 Yes** possible when env/lab/reports are green.

---

## Which “reports” (Wave 4 — not the automation framework)

These are **custom reports inside Moodle QA** (Report builder / configurable reports), the same pages a admin opens in the browser to see Practice Project participation, attempts, enrolment, etc.

Automation does **not** generate these — QA creates (or copies) two reports on the Moodle site, then we store their URLs in `.env`:

- `PRACTICE_DETAILED_REPORT_URL` — detailed participant/attempt report  
- `PRACTICE_SUMMARY_REPORT_URL` — summary/counts report  

Scenarios in `practice_project_reports.feature` open those **Moodle URLs** and assert filters/columns.  
**Framework reports** (`reports/traceability_execution.html`, Cucumber HTML) are unrelated.

**What we need from you when ready:** either paste both URLs, or confirm report names so we can discover them once from `/reportbuilder/index.php`.

---

## Recommended execution order (after your answers)

1. **Engineering — variant fixture factory** (your choice: auto-create): one-time seed in `PracticeSuiteCourse` → write `practice.env` with all `GUIDED_*_CMID` values → re-run variant scenarios (Wave 3).
2. **Probe CloudLabs** — `@PP-11` / `@guidedlab` headed; if 401, Wave 2 blocked until infra fixed.
3. **Wave 1** — green run + Kiwi **Yes** for scripted rows that don’t need WS or reports (variants after step 1).
4. **Wave 4** — when you provide Moodle report URLs (46 rows).
5. **Wave 2 + 6** — provisioning scenarios (lab console, overrides, last-attempt) with `-Pslow` + deferCleanup.
6. **Skip Wave 5** — PP-59–68 remain **No** with ref “WS not permitted on automation token”.

---

## Inputs still needed from you

1. **Moodle Report Builder** — two report URLs (or go-ahead for QA to create standard PP detailed/summary reports on QA).
2. **CloudLabs probe result** — after you run `@guidedlab` once, tell us pass vs 401.
3. **Product calls on G bucket** — PP-117 / PP-131 / export dropdown: **Deviation** vs fix plugin (QA call).
