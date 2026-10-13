# Medium automation — batch plan (start with fast 100% slices)

Medium tier backlog ≈ **397** cases (`python tools/automation_feasibility_plan.py`). Do **not** start with the full Assessment sheet (194) if the goal is a quick, green module win.

## Batch 0 — Moodle-Cloudlabs Integration (**done first — fastest 100%**)

| Why | 11 admin smoke scenarios, **no** `@provisioning`, **no** real labs, ~4 min |
|-----|----------------------------------------------------------------------------|
| Feature | `moodle_cloudlabs_integration.feature` |
| Tags | `@MCI-1` … `@MCI-11` (`@mci` only; MCI-12 / MCI-13 not in feature) |

```powershell
.\mvnw.cmd test "-Dcucumber.features=src/test/resources/features/moodle_cloudlabs_integration.feature" "-Dcucumber.tags=@mci and not @skip"
```

After green: `python tools/merge_cuke_for_traceability.py` then `python tools/sync_kiwi_and_traceability.py`.

## Batch 0b — Pre-Delivery **mutate only** (next fast slice, no dashboard)

Dashboard `@easybatch2` scenarios can fail on **live data** (status labels / search rows). Mutate scenarios use **throwaway** activities and are more stable:

```powershell
.\mvnw.cmd test "-Dcucumber.tags=@predelivery-mutate and @easybatch2 and not @skip"
```

(6 scenarios in `predelivery_mutate.feature`.)

## Batch 1 — Medium proper (in progress)

Export backlog slice:

```powershell
python tools/export_medium_batches.py
```

Output: `reports/medium_automation_batch1.csv` (51 rows, **High → Medium**, medium tier only). Most rows are **Assessment deferred**; the first **implemented slice** is **Randomization Activity** (form / required name / duplicate names / create).

### Batch 1a — Randomization Activity (fast, no provisioning)

| Tags | Kiwi |
|------|------|
| `@mediumbatch1` `@randomactivity` | `CGAP-RAND-1` … `3`, `@RA-*` |

```powershell
.\mvnw.cmd test -Pwatch "-Dcucumber.tags=@mediumbatch1 and not @skip"
```

Kiwi: `python tools/update_randomization_activity_coverage.py` then sync traceability.

### Batch 1b — Assessment config / reopening (no provisioning)

Promote existing Cucumber (override form, hide columns, CGAP-A-8) in Kiwi:

```powershell
python tools/update_assessment_medium_batch1_coverage.py
python tools/sync_kiwi_and_traceability.py
```

Run (needs `ASSESSMENT_CMID`, `COURSE_ID`, `MOODLE_WS_TOKEN` for role-matrix cases):

```powershell
.\mvnw.cmd test -Pwatch "-Dcucumber.tags=@mediumbatch1 and not @provisioning and not @skip"
```

Features: `assessment_reopening.feature`, `assessment_show_hide_grades.feature`, `assessment_hide_final_grade.feature`, `assessment_user_functionality.feature` (`@CGAP-A-8`).

### Batch 1c — Lab actions, attempts UI, cooldown form, randomization admin (no lab)

```powershell
python tools/update_assessment_medium_batch1c_coverage.py
python tools/sync_kiwi_and_traceability.py
```

Covers implemented tags in `assessment_lab_actions`, `assessment_user_attempts_page`, `assessment_retake_cooldown`, `assessment_randomization_problem_statements` (form/admin — not `@provisioning` lifecycle stubs).

### Batch 1d — Assessment High deferred + provisioning (next)

Default export is mostly **Moodle_Assessment_Labs** deferred CGAP — automate **High** rows with coverage-matrix gate per `CLAUDE.md` (not bulk Partial linking).

## What to avoid for “100% first”

| Slice | Risk |
|-------|------|
| Full `@easybatch2` | Includes `@provisioning` LT progress + PP deviation + dashboard data sensitivity |
| `@predelivery and @easybatch2` | 2/16 dashboard failures observed on current env (mutate 6/6 passed) |
| Assessment medium (194) | Large; many blank Kiwi + CGAP + `@provisioning` |
