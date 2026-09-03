# Docs — test cases & coverage

Version-controlled copies of the QA test-case master and the automation coverage docs.

## Files
- **`MoodleRegression_Testing_Kiwi new.xlsx`** — the master test-case workbook (Kiwi TCMS export).
  Note the space in the filename.
  - Per-case automation status lives in each sheet's **`Automated`** column (`Yes` / `No` / `Deferred` /
    `Deviation` / blank = not judged yet) with the exact test or reason in **`Automation_Ref`**.
  - The **`Automation_Coverage`** sheet is the automation dashboard (per-sheet Yes/Partial/Deviation/
    Deferred/No counts + % automated).
  - The **`Regression_Dashboard`** sheet is the execution dashboard for the modules worked so far
    (Total / Executed / Not Automatable / Deferred / Completion %, where Executed = `Automated`=Yes
    and `Case_Status` in Pass/Fail/Blocked).
  - Derived gap cases are the `CGAP-*` rows (Case_Status = PROPOSED).
- **`MoodleRegression_Testing_Kiwi.xlsx`** — backup of the previous master. Do not edit.
- **`*_Coverage_Matrix.md`** — per-module control inventory + coverage plan (Playground, Guided,
  Assessment, Pre-Delivery Lab Readiness).
- **`Roles_Findings.md`** — the roles/permissions findings write-up.

## Conventions
- This repo copy is the versioned source; update it here (via PR) so changes flow through git.
- The two dashboard sheets are **QA-owned** — don't rewrite their layout or formulas. After changing
  cases, run `python tools/check_kiwi_dashboards.py` (read-only) to see whether any dashboard row has
  drifted from the case data, and report drift rather than silently fixing it.
- Any script that saves the workbook through openpyxl **erases every formula's cached result**, so the
  cells look blank until Excel recalculates. Run `python tools/refresh_kiwi_dashboard.py` afterwards:
  it recomputes and re-injects those caches for `Regression_Dashboard` and `Release_Regression_Plan`,
  and keeps the dashboard's module order (Users, Upload users, All course page, Sandbox, Assessment).
- Mark a case `Automated = Yes` (with its `feature @tag`) only when a green test verifies THAT case
  (presence ≠ behaviour). Deviations are fail-by-design tests flagging a real defect.
- Backups (`*.backup*.xlsx`) and Excel lock files (`~$*`) are git-ignored.

See `../PROGRESS.md` for the live per-module status and the coverage-at-a-glance table.
