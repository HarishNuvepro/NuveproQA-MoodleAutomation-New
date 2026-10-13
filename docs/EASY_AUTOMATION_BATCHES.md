# Easy automation backlog — two batches (102 cases)

Split from the **easy** tier (`tools/automation_feasibility_plan.py`): **51 + 51** cases, sorted **High → Medium** then module. Full list: `reports/easy_automation_batches.csv`.

## Batch 1 (51) — implemented in repo

| Area | Cases | Where |
|------|------:|--------|
| Groups CGAP-GR | 11 | `easy_automation_batch1.feature` (+ some deferred skips) |
| Roles CGAP-R | 7 | `easy_automation_batch1.feature`, `RoleEnforcementSteps` |
| Tags CGAP-T | 6 | `easy_automation_batch1.feature` |
| Lab Templates @LT-* | 21 | `labtemplates.feature`, `sandbox_user_functionality.feature` (tagged `@easybatch1`) |
| Git URL / difficulty | 6 | `easy_automation_batch1.feature` + `cloudlabs_config.feature` + `difficulty_level_configuration.feature` |
| My Labs CGAP-ML-2 | 1 | `easy_automation_batch1.feature` (skip until `-Pslow` lab seed) |
| Moodle settings (row 2) | 1 | Still **blank** in Kiwi — judgement pending (not in Cucumber yet) |

After a green run, update Kiwi and traceability (see batch 2 section for the full command chain including `merge_cuke_for_traceability.py`).

### Run batch 1

From repo root (PowerShell):

```powershell
# Fast subset (no real cloud lab / no @provisioning):
.\mvnw.cmd test -Pwatch "-Dcucumber.tags=@easybatch1 and not @provisioning and not @skip"

# Include lab progress (@LT-70 / @LT-48) and future CGAP-ML-2 provisioning:
.\mvnw.cmd test -Pwatch -Pslow "-Dcucumber.tags=@easybatch1 and not @skip"
```

Headed debug (single tag):

```powershell
.\mvnw.cmd test -Pwatch -Dheadless=false -Dslowmo=600 "-Dcucumber.tags=@CGAP-GR-1"
```

Requires `.env`: `COURSE_ID`, `PLAYGROUND_CMID`, `MOODLE_WS_TOKEN`, lab template Plan/Team for mutating `@LT-*` disposable templates.

## Batch 2 (51) — implemented in repo

| Area | Cases | Where |
|------|------:|--------|
| Roles CGAP-R | 5 | `easy_automation_batch2.feature` |
| Tags CGAP-T | 6 | `easy_automation_batch2.feature` |
| Lab Templates @LT-* | 11 | `sandbox_user_functionality.feature`, `sandbox.feature` (`@provisioning` for progress aliases) |
| My Labs | 9 | `mylabs.feature` (`@ML-23` / `@ML-26` assessment & practice filters) |
| Practice Project | 10 | `practice_project.feature`, `practice_project_user_functionality.feature`; CGAP-PP deferred `@skip` stubs |
| Pre-Delivery | 11 | `predelivery_dashboard.feature`, `predelivery_mutate.feature` |
| Difficulty `@DLC-10` | 1 | `difficulty_level_configuration.feature` |

Rows **52–102** in `reports/easy_automation_batches.csv`.

Run after batch 1 is green (headless fast slice):

```powershell
.\mvnw.cmd test "-Dcucumber.tags=@easybatch2 and not @provisioning and not @skip"
```

After green batch runs, refresh Kiwi + dashboards + traceability (workbook closed):

```powershell
copy target\cucumber.json target\cucumber_fast.json
python tools\merge_cuke_for_traceability.py
python tools\update_easy_batch1_kiwi.py
python tools\update_easy_batch2_kiwi.py
python tools\easy_batch_apply_execution.py
python tools\sync_kiwi_and_traceability.py
```

If `target\cucumber.json` is missing, re-run the batch Maven command first (Cucumber writes JSON on test completion).

## Regenerate batch CSV

```powershell
python tools\export_easy_batches.py
```
