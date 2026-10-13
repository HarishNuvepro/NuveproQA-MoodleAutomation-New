# Runners — headed commands for every feature file

PowerShell, from this repo root. Every command below **shows the browser** (`-Pwatch` + `-Dheadless=false`).

`-Pwatch` = Cucumber only (no ConnectivityTest), headed, 800ms slowmo.

When you pass `-Dcucumber.features=...`, Maven auto-activates the **`single-feature`** profile (same TestNG suite as `-Pwatch`: only `RunCucumberTest`). Without that, the default fast suite also runs parallel sandbox runners against the filtered file and you get Playwright `pipe closed` failures.

Default Maven tags exclude `@provisioning`, `@sandbox-cleanup`, `@module-cleanup`, and `@assessment-cleanup`. That **drops real-lab cases** on plain `mvn test`. Whole-file commands below use `not @sandbox-cleanup and not @module-cleanup` so provisioning in that file still runs.

Reports: `target/cucumber-report.html` · `target/cucumber.json` · `target/surefire-reports/emailable-report.html`

Details: [docs/CLEANUP.md](docs/CLEANUP.md) · Windows shortcuts: `cleanup.cmd` · traceability: `report.cmd` · **re-run flakes:** `rerun.cmd`

---

## Re-run failed / skipped after a module execution

Parallel runs sometimes leave **failed** or **skipped** scenarios (browser, network, timing). After **`target/cucumber.json`** is written from that run:

```powershell
rerun list
rerun generate sandbox
rerun run sandbox
```

- **`rerun generate <module>`** — reads **`target/cucumber.json`** only (latest run), writes `reports/rerun/<module>_rerun.{json,tags,cmd}`.
- **`rerun run <module>`** — regenerates then runs **`*_rerun.cmd`** (serial `-Pwatch`, stable).
- **`rerun run-parallel <module>`** — same tags via **`*_rerun_parallel.cmd`**.

Registered modules (extend in `tools/module_rerun_registry.py` when you add a new dashboard module):

| `rerun` id | Feature file(s) |
|------------|-----------------|
| `users` | `moodle_users.feature` |
| `upload_users` | `upload_users.feature` |
| `all_courses` | `all_courses.feature` |
| `sandbox` | `sandbox.feature`, `sandbox_user_functionality.feature` |
| `cloudlabs_integration` | `moodle_cloudlabs_integration.feature` |
| `difficulty_level_configuration` | `difficulty_level_configuration.feature` |

```powershell
rerun generate all
python tools/failure_report.py
```

Aliases: `mci`, `cloudlabs-integration`, `upload-users`, `all-courses`, …

---

## Module run + on-demand cleanup (recommended workflow)

Run a module with **deferred** per-scenario deletes (artefacts stay on Moodle until you cleanup):

```powershell
.\mvnw.cmd test -Pwatch -Dheadless=false "-Dautomation.deferCleanup=true" "-Dcucumber.tags=not @sandbox-cleanup and not @module-cleanup" "-Dcucumber.features=src/test/resources/features/labtemplates.feature"
```

After a **green** run — preview cleanup (no changes):

```powershell
.\mvnw.cmd test -Pcleanup -Pwatch -Dheadless=false "-DcleanupDryRun=true" "-Dcucumber.tags=@cleanup-all"
```

Execute cleanup:

```powershell
.\mvnw.cmd test -Pcleanup -Pwatch -Dheadless=false "-Dcucumber.tags=@cleanup-all"
```

Or use `cleanup.cmd` from repo root (same commands, shorter):

```powershell
cleanup dry
cleanup all
cleanup core
cleanup users
cleanup sandbox
cleanup assessment
cleanup practice
cleanup all-courses
cleanup lab-templates
cleanup settings
```

Per-tag cleanup (headed):

```powershell
.\mvnw.cmd test -Pcleanup -Pwatch -Dheadless=false "-Dcucumber.tags=@cleanup-users-all-courses-sandbox"
.\mvnw.cmd test -Pcleanup -Pwatch -Dheadless=false "-Dcucumber.tags=@cleanup-users"
.\mvnw.cmd test -Pcleanup -Pwatch -Dheadless=false "-Dcucumber.tags=@sandbox-cleanup"
.\mvnw.cmd test -Pcleanup -Pwatch -Dheadless=false "-Dcucumber.tags=@cleanup-assessment"
.\mvnw.cmd test -Pcleanup -Pwatch -Dheadless=false "-Dcucumber.tags=@cleanup-practice"
.\mvnw.cmd test -Pcleanup -Pwatch -Dheadless=false "-Dcucumber.tags=@cleanup-all-courses"
.\mvnw.cmd test -Pcleanup -Pwatch -Dheadless=false "-Dcucumber.tags=@cleanup-lab-templates"
.\mvnw.cmd test -Pcleanup -Pwatch -Dheadless=false "-Dcucumber.tags=@settings-cleanup"
```

Headless cleanup (CI-style):

```powershell
.\mvnw.cmd test -Pcleanup "-Dcucumber.tags=@cleanup-all"
```

Legacy assessment activity-only wipe (if no suite course):

```powershell
.\mvnw.cmd test -Pwatch -Dheadless=false "-Dcucumber.tags=@assessment-cleanup"
```

---

## One case (replace the tag)

```powershell
.\mvnw.cmd test -Pwatch -Dheadless=false "-Dcucumber.tags=@SB-user-1"
.\mvnw.cmd test -Pwatch -Dheadless=false "-Dcucumber.tags=@U-user-1"
.\mvnw.cmd test -Pwatch -Dheadless=false "-Dcucumber.tags=@ACP-S1"
```

---

## Moodle core

```powershell
.\mvnw.cmd test -Pwatch -Dheadless=false "-Dcucumber.tags=not @sandbox-cleanup" "-Dcucumber.features=src/test/resources/features/moodle_users.feature"
.\mvnw.cmd test -Pwatch -Dheadless=false "-Dcucumber.tags=not @sandbox-cleanup" "-Dcucumber.features=src/test/resources/features/upload_users.feature"
.\mvnw.cmd test -Pwatch -Dheadless=false "-Dcucumber.tags=not @sandbox-cleanup" "-Dcucumber.features=src/test/resources/features/moodle_settings.feature"
.\mvnw.cmd test -Pwatch -Dheadless=false "-Dcucumber.tags=not @sandbox-cleanup" "-Dcucumber.features=src/test/resources/features/moodle_tags.feature"
.\mvnw.cmd test -Pwatch -Dheadless=false "-Dcucumber.tags=not @sandbox-cleanup" "-Dcucumber.features=src/test/resources/features/moodle_tags_manage.feature"
.\mvnw.cmd test -Pwatch -Dheadless=false "-Dcucumber.tags=not @sandbox-cleanup" "-Dcucumber.features=src/test/resources/features/moodle_roles.feature"
.\mvnw.cmd test -Pwatch -Dheadless=false "-Dcucumber.tags=not @sandbox-cleanup" "-Dcucumber.features=src/test/resources/features/moodle_roles_functional.feature"
.\mvnw.cmd test -Pwatch -Dheadless=false "-Dcucumber.tags=not @sandbox-cleanup" "-Dcucumber.features=src/test/resources/features/moodle_roles_custom.feature"
.\mvnw.cmd test -Pwatch -Dheadless=false "-Dcucumber.tags=not @sandbox-cleanup" "-Dcucumber.features=src/test/resources/features/moodle_groups.feature"
.\mvnw.cmd test -Pwatch -Dheadless=false "-Dcucumber.tags=not @sandbox-cleanup" "-Dcucumber.features=src/test/resources/features/all_courses.feature"
```

---

## Sandbox (create + Manage Labs, then user console)

```powershell
.\mvnw.cmd test -Pwatch -Dheadless=false "-Dcucumber.tags=not @sandbox-cleanup" "-Dcucumber.features=src/test/resources/features/sandbox.feature"
.\mvnw.cmd test -Pwatch -Dheadless=false "-Dcucumber.tags=not @sandbox-cleanup" "-Dcucumber.features=src/test/resources/features/sandbox_user_functionality.feature"
```

On-demand sandbox course wipe (do **not** run with the suite; prefer `cleanup sandbox` or section above):

```powershell
.\mvnw.cmd test -Pcleanup -Pwatch -Dheadless=false "-Dcucumber.tags=@sandbox-cleanup"
```

### Sandbox parallel (`sandbox.feature` only)

Five concurrent scenarios (`testng-sandbox-parallel.xml`), each with its own browser (`sandbox.parallel=true`, cap `parallel.browsers=5`). Includes `@provisioning` (default `-Psandbox-parallel` tags exclude only cleanup).

**Recommended — full `sandbox.feature`:**

```powershell
.\mvnw.cmd test -Psandbox-parallel "-Dautomation.deferCleanup=true"
```

**Equivalent (generic parallel runner, 5 threads):**

```powershell
.\mvnw.cmd test -Pparallel "-DsuiteXmlFile=testng-parallel5.xml" "-Dparallel.browsers=5" "-Dcucumber.features=src/test/resources/features/sandbox.feature" "-Dcucumber.tags=not @sandbox-cleanup and not @module-cleanup"
```

**UI / registry only (no CloudLabs provisioning):**

```powershell
.\mvnw.cmd test -Psandbox-parallel "-Dcucumber.tags=not @skip and not @sandbox-cleanup and not @module-cleanup and not @provisioning"
```

**Phased (seed activity registry, then parallel Manage Labs):** run create scenarios once, then parallel manage (manage steps wait on registry if you skip this, up to 240s).

```powershell
.\mvnw.cmd test -Pwatch "-Dcucumber.features=src/test/resources/features/sandbox.feature" "-Dcucumber.tags=(@SB-create-1 or @SB-create-2 or @SB-create-3 or @SB-create-6 or @SB-create-7 or @SB-create-8 or @SB-create-9 or @SB-create-11) and not @sandbox-cleanup"
.\mvnw.cmd test -Psandbox-parallel "-Dcucumber.tags=(@SB-manage-1 or @SB-manage-2 or @SB-manage-3 or @SB-manage-4 or @SB-manage-5 or @SB-manage-6 or @SB-manage-7 or @SB-manage-8 or @SB-manage-9 or @SB-manage-10 or @SB-manage-11 or @SB-manage-12 or @SB-manage-13 or @SB-manage-15 or @SB-manage-16 or @SB-manage-17 or @SB-manage-18 or @SB-manage-20 or @SB-manage-21 or @SB-manage-22 or @SB-manage-23 or @SB-manage-24 or @SB-manage-25) and not @sandbox-cleanup"
```

Reports: `target/cucumber-sandbox-parallel.html`, `target/cucumber-sandbox-parallel.json`.

**Student console parallel** (`sandbox_user_functionality.feature`, `sbuser:*` / `sbmanage:*` lanes):

```powershell
.\mvnw.cmd test -Pparallel "-DsuiteXmlFile=testng-parallel5.xml" "-Dparallel.browsers=5" "-Dsandbox.parallel=true" "-Dautomation.deferCleanup=true" "-Dcucumber.features=src/test/resources/features/sandbox_user_functionality.feature" "-Dcucumber.tags=not @skip and not @sandbox-cleanup and not @module-cleanup"
```

**Dependencies (same JVM or run these tag pairs first — hooks wait up to 20 min in parallel):**

| Chain | Tags (one command) | Lane |
|-------|-------------------|------|
| VM catalog | `@SB-user-14 or @SB-user-19` | `sbuser:vm_vpat_chain` |
| Notes → PDF No | `@SB-user-6 or @SB-user-9` | `sbuser:notes_pdf_chain` |
| Account lab reuse | `@SB-user-1`, `@SB-user-4`, `@SB-user-5`, `@SB-user-10` | `sbuser:account_lab` |
| Account lab 2 | `@SB-user-12`, `@SB-user-18` | `sbuser:account_lab2` |
| Git description | `@SB-user-2` | `sbuser:git_lab` |
| PDF Yes | `@SB-user-8` | `sbuser:pdf_yes` |
| Manage console (fresh user each) | `@SB-manage-26`, `@SB-manage-27`, `@SB-manage-28`, `@SB-manage-29`, `@SB-manage-31` | isolated (no shared Active lab) |
| Fresh / isolated | `@SB-user-3`, `@SB-user-7`, `@SB-user-11`, `@SB-user-13`, `@SB-manage-26`, `@SB-manage-29`, `@SB-manage-34`, `@LT-*` progress | own student each |

**Phased parallel (recommended for CI):**

```powershell
# 1) Chains + named-lab seeds (serial, ~15–25 min provisioning)
.\mvnw.cmd test -Pwatch "-Dautomation.deferCleanup=true" "-Dcucumber.features=src/test/resources/features/sandbox_user_functionality.feature" "-Dcucumber.tags=(@SB-user-6 or @SB-user-14) and not @skip"

# 2) Full file parallel (includes @SB-user-9 / @SB-user-19 — they wait on chain registry)
.\mvnw.cmd test -Pparallel "-DsuiteXmlFile=testng-parallel5.xml" "-Dparallel.browsers=5" "-Dsandbox.parallel=true" "-Dautomation.deferCleanup=true" "-Dcucumber.features=src/test/resources/features/sandbox_user_functionality.feature" "-Dcucumber.tags=not @skip and not @sandbox-cleanup and not @module-cleanup"
```

**Serial whole file (no parallel):**

```powershell
.\mvnw.cmd test -Pwatch "-Dautomation.deferCleanup=true" "-Dcucumber.features=src/test/resources/features/sandbox_user_functionality.feature" "-Dcucumber.tags=not @skip and not @sandbox-cleanup and not @module-cleanup"
```

---

## Assessment

```powershell
.\mvnw.cmd test -Pwatch -Dheadless=false "-Dcucumber.tags=not @sandbox-cleanup" "-Dcucumber.features=src/test/resources/features/assessment_create.feature"
.\mvnw.cmd test -Pwatch -Dheadless=false "-Dcucumber.tags=not @sandbox-cleanup" "-Dcucumber.features=src/test/resources/features/assessment_user_functionality.feature"
.\mvnw.cmd test -Pwatch -Dheadless=false "-Dcucumber.tags=not @sandbox-cleanup" "-Dcucumber.features=src/test/resources/features/assessment_lab_actions.feature"
.\mvnw.cmd test -Pwatch -Dheadless=false "-Dcucumber.tags=not @sandbox-cleanup" "-Dcucumber.features=src/test/resources/features/assessment_test_case_marks.feature"
.\mvnw.cmd test -Pwatch -Dheadless=false "-Dcucumber.tags=not @sandbox-cleanup" "-Dcucumber.features=src/test/resources/features/assessment_user_attempts_page.feature"
.\mvnw.cmd test -Pwatch -Dheadless=false "-Dcucumber.tags=not @sandbox-cleanup" "-Dcucumber.features=src/test/resources/features/assessment_show_hide_grades.feature"
.\mvnw.cmd test -Pwatch -Dheadless=false "-Dcucumber.tags=not @sandbox-cleanup" "-Dcucumber.features=src/test/resources/features/assessment_hide_final_grade.feature"
.\mvnw.cmd test -Pwatch -Dheadless=false "-Dcucumber.tags=not @sandbox-cleanup" "-Dcucumber.features=src/test/resources/features/assessment_reopening.feature"
.\mvnw.cmd test -Pwatch -Dheadless=false "-Dcucumber.tags=not @sandbox-cleanup" "-Dcucumber.features=src/test/resources/features/assessment_retake_cooldown.feature"
.\mvnw.cmd test -Pwatch -Dheadless=false "-Dcucumber.tags=not @sandbox-cleanup" "-Dcucumber.features=src/test/resources/features/assessment_problem_statement_git_url.feature"
.\mvnw.cmd test -Pwatch -Dheadless=false "-Dcucumber.tags=not @sandbox-cleanup" "-Dcucumber.features=src/test/resources/features/assessment_randomization_problem_statements.feature"
.\mvnw.cmd test -Pwatch -Dheadless=false "-Dcucumber.tags=not @sandbox-cleanup" "-Dcucumber.features=src/test/resources/features/assessment_disabling_complete.feature"
```

On-demand assessment suite course wipe: `cleanup assessment` or `@cleanup-assessment` (see top section). Legacy per-activity wipe: `@assessment-cleanup`.

---

## Practice Project

```powershell
.\mvnw.cmd test -Pwatch -Dheadless=false "-Dcucumber.tags=not @sandbox-cleanup" "-Dcucumber.features=src/test/resources/features/practice_project.feature"
.\mvnw.cmd test -Pwatch -Dheadless=false "-Dcucumber.tags=not @sandbox-cleanup" "-Dcucumber.features=src/test/resources/features/practice_project_user_functionality.feature"
.\mvnw.cmd test -Pwatch -Dheadless=false "-Dcucumber.tags=not @sandbox-cleanup" "-Dcucumber.features=src/test/resources/features/practice_project_actions.feature"
# Deferred — not automatable (Moodle Report Builder); entire feature @skip
# .\mvnw.cmd test -Pwatch ... practice_project_reports.feature
```

On-demand practice suite course wipe: `cleanup practice` or `@cleanup-practice` (create/form scenarios only; `GUIDED_CMID` fixture unchanged).

Practice Project 121→76 conversion plan: [docs/PRACTICE_PROJECT_CONVERSION_PLAN.md](docs/PRACTICE_PROJECT_CONVERSION_PLAN.md)

CloudLabs probe (before lab wave):

```powershell
.\mvnw.cmd test -Pslow -Pwatch -Dheadless=false "-Dcucumber.tags=@guidedlab"
```

---

## Practice Project (merged; former guided_*.feature)

```powershell
.\mvnw.cmd test -Pwatch -Dheadless=false "-Dcucumber.tags=not @sandbox-cleanup" "-Dcucumber.features=src/test/resources/features/practice_project.feature"
.\mvnw.cmd test -Pwatch -Dheadless=false "-Dcucumber.tags=not @sandbox-cleanup" "-Dcucumber.features=src/test/resources/features/practice_project_user_functionality.feature"
.\mvnw.cmd test -Pwatch -Dheadless=false "-Dcucumber.tags=not @sandbox-cleanup" "-Dcucumber.features=src/test/resources/features/practice_project_actions.feature"
.\mvnw.cmd test -Pslow -Pwatch -Dheadless=false "-Dcucumber.tags=@guidedlab"
.\mvnw.cmd test -Pslow -Pwatch -Dheadless=false "-Dcucumber.tags=@guidedattempts"
.\mvnw.cmd test -Pslow -Pwatch "-Dcucumber.features=src/test/resources/features/practice_project_sandbox_parity.feature"
.\mvnw.cmd test -Pwatch "-Dcucumber.features=src/test/resources/features/practice_project_sandbox_parity.feature" "-Dcucumber.tags=@CGAP-PP-21"
.\mvnw.cmd test -Pwatch -Dheadless=false "-Dcucumber.tags=not @sandbox-cleanup" "-Dcucumber.features=src/test/resources/features/fresh_lab.feature"
.\mvnw.cmd test -Pwatch -Dheadless=false "-Dcucumber.tags=not @sandbox-cleanup" "-Dcucumber.features=src/test/resources/features/lab_actions.feature"
.\mvnw.cmd test -Pwatch -Dheadless=false "-Dcucumber.tags=not @sandbox-cleanup" "-Dcucumber.features=src/test/resources/features/labtemplates.feature"
.\mvnw.cmd test -Pwatch -Dheadless=false "-Dcucumber.tags=not @sandbox-cleanup" "-Dcucumber.features=src/test/resources/features/module_cleanup.feature"
.\mvnw.cmd test -Pwatch -Dheadless=false "-Dcucumber.tags=not @sandbox-cleanup" "-Dcucumber.features=src/test/resources/features/mylabs.feature"
```

---

## Other modules

```powershell
# Sequential activity completion (@sac / @sacgrade / @sacpp). Serial reuses a shared ordered fixture;
# parallel builds an isolated fixture per scenario (safe at 5 threads):
.\mvnw.cmd test -Pwatch -Dheadless=false "-Dcucumber.tags=not @sandbox-cleanup" "-Dcucumber.features=src/test/resources/features/sequential_completion.feature"
.\mvnw.cmd test -Pparallel "-DsuiteXmlFile=testng-parallel5.xml" "-Dparallel.browsers=5" "-Dautomation.deferCleanup=true" "-Dcucumber.features=src/test/resources/features/sequential_completion.feature"
# Provisioning PP cases only: add "-Dcucumber.tags=@provisioning" and -Pslow if needed.
.\mvnw.cmd test -Pwatch -Dheadless=false "-Dcucumber.tags=not @sandbox-cleanup" "-Dcucumber.features=src/test/resources/features/cloudlabs_config.feature"
.\mvnw.cmd test -Pwatch -Dheadless=false "-Dcucumber.tags=not @sandbox-cleanup" "-Dcucumber.features=src/test/resources/features/difficulty_level_configuration.feature"
# Difficulty (@dlc): 13 scenarios / 16 Kiwi rows (3 CGAP rows share @DLC-3/@DLC-4/@DLC-5). NOT testng-parallel5 (shared admin + /my/ login). Ensure target/state.json exists (one normal mvn test or ConnectivityTest):
.\mvnw.cmd test -Pdlc-serial
# Or serial single-browser (simplest): .\mvnw.cmd test -Pwatch "-Dcucumber.features=src/test/resources/features/difficulty_level_configuration.feature" "-Dcucumber.tags=@dlc"
.\mvnw.cmd test -Pwatch -Dheadless=false "-Dcucumber.features=src/test/resources/features/sequential_completion.feature"
.\mvnw.cmd test -Pwatch -Dheadless=false "-Dcucumber.features=src/test/resources/features/moodle_cloudlabs_integration.feature"
.\mvnw.cmd test -Pwatch -Dheadless=false "-Dcucumber.tags=@MCI-1" "-Dcucumber.features=src/test/resources/features/moodle_cloudlabs_integration.feature"
# Parallel (@MCI-1 … @MCI-11, admin smoke — needs COURSE_ID for @MCI-7/@MCI-9/@MCI-10):
.\mvnw.cmd test -Pparallel "-DsuiteXmlFile=testng-parallel5.xml" "-Dparallel.browsers=5" "-Dcucumber.features=src/test/resources/features/moodle_cloudlabs_integration.feature" "-Dcucumber.tags=@mci"
.\mvnw.cmd test -Pwatch -Dheadless=false "-Dcucumber.tags=not @sandbox-cleanup" "-Dcucumber.features=src/test/resources/features/randomactivity.feature"
.\mvnw.cmd test -Pwatch -Dheadless=false "-Dcucumber.tags=not @sandbox-cleanup" "-Dcucumber.features=src/test/resources/features/reports.feature"
.\mvnw.cmd test -Pwatch -Dheadless=false "-Dcucumber.tags=not @sandbox-cleanup" "-Dcucumber.features=src/test/resources/features/bulkmail.feature"
.\mvnw.cmd test -Pwatch -Dheadless=false "-Dcucumber.tags=not @sandbox-cleanup" "-Dcucumber.features=src/test/resources/features/aws_marketplace.feature"
.\mvnw.cmd test -Pwatch -Dheadless=false "-Dcucumber.tags=not @sandbox-cleanup" "-Dcucumber.features=src/test/resources/features/predelivery.feature"
```

---

## Suite-wide (use only when you mean it)

Headless fast suite (skips `@provisioning` and `@sandbox-cleanup`):

```powershell
.\mvnw.cmd test
```

All `@provisioning` cases, still hidden:

```powershell
.\mvnw.cmd test -Pslow
```

All `@provisioning` cases, **browser visible**:

```powershell
.\mvnw.cmd test -Pslow -Dheadless=false
```
