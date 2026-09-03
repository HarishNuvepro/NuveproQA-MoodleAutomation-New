# Runners — headed commands for every feature file

PowerShell, from this repo root. Every command below **shows the browser** (`-Pwatch` + `-Dheadless=false`).

`-Pwatch` = Cucumber only (no ConnectivityTest), headed, 800ms slowmo.

Default Maven tags are `not @provisioning and not @sandbox-cleanup`. That **drops real-lab cases**. All whole-file commands below override tags to `not @sandbox-cleanup` so provisioning cases in that file still run.

Reports: `target/cucumber-report.html` · `target/cucumber.json` · `target/surefire-reports/emailable-report.html`

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

## Sandbox (create first, then user, then actions)

```powershell
.\mvnw.cmd test -Pwatch -Dheadless=false "-Dcucumber.tags=not @sandbox-cleanup" "-Dcucumber.features=src/test/resources/features/sandbox_create.feature"
.\mvnw.cmd test -Pwatch -Dheadless=false "-Dcucumber.tags=not @sandbox-cleanup" "-Dcucumber.features=src/test/resources/features/sandbox_user_functionality.feature"
.\mvnw.cmd test -Pwatch -Dheadless=false "-Dcucumber.tags=not @sandbox-cleanup" "-Dcucumber.features=src/test/resources/features/sandbox_actions.feature"
```

On-demand course wipe only (do **not** run with the suite):

```powershell
.\mvnw.cmd test -Pwatch -Dheadless=false "-Dcucumber.tags=@sandbox-cleanup"
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
.\mvnw.cmd test -Pwatch -Dheadless=false "-Dcucumber.tags=@assessment-cleanup"
```

---

## Practice Project

```powershell
.\mvnw.cmd test -Pwatch -Dheadless=false "-Dcucumber.tags=not @sandbox-cleanup" "-Dcucumber.features=src/test/resources/features/practice_project.feature"
.\mvnw.cmd test -Pwatch -Dheadless=false "-Dcucumber.tags=not @sandbox-cleanup" "-Dcucumber.features=src/test/resources/features/practice_project_user_functionality.feature"
.\mvnw.cmd test -Pwatch -Dheadless=false "-Dcucumber.tags=not @sandbox-cleanup" "-Dcucumber.features=src/test/resources/features/practice_project_actions.feature"
.\mvnw.cmd test -Pwatch -Dheadless=false "-Dcucumber.tags=not @sandbox-cleanup" "-Dcucumber.features=src/test/resources/features/practice_project_reports.feature"
```

---

## Guided / lab lifecycle

```powershell
.\mvnw.cmd test -Pwatch -Dheadless=false "-Dcucumber.tags=not @sandbox-cleanup" "-Dcucumber.features=src/test/resources/features/guided_labs.feature"
.\mvnw.cmd test -Pwatch -Dheadless=false "-Dcucumber.tags=not @sandbox-cleanup" "-Dcucumber.features=src/test/resources/features/guided_gaps.feature"
.\mvnw.cmd test -Pwatch -Dheadless=false "-Dcucumber.tags=not @sandbox-cleanup" "-Dcucumber.features=src/test/resources/features/guided_lab.feature"
.\mvnw.cmd test -Pwatch -Dheadless=false "-Dcucumber.tags=not @sandbox-cleanup" "-Dcucumber.features=src/test/resources/features/guided_attempts.feature"
.\mvnw.cmd test -Pwatch -Dheadless=false "-Dcucumber.tags=not @sandbox-cleanup" "-Dcucumber.features=src/test/resources/features/fresh_lab.feature"
.\mvnw.cmd test -Pwatch -Dheadless=false "-Dcucumber.tags=not @sandbox-cleanup" "-Dcucumber.features=src/test/resources/features/lab_actions.feature"
.\mvnw.cmd test -Pwatch -Dheadless=false "-Dcucumber.tags=not @sandbox-cleanup" "-Dcucumber.features=src/test/resources/features/labtemplates.feature"
.\mvnw.cmd test -Pwatch -Dheadless=false "-Dcucumber.tags=not @sandbox-cleanup" "-Dcucumber.features=src/test/resources/features/mylabs.feature"
```

---

## Other modules

```powershell
.\mvnw.cmd test -Pwatch -Dheadless=false "-Dcucumber.tags=not @sandbox-cleanup" "-Dcucumber.features=src/test/resources/features/sequential_completion.feature"
.\mvnw.cmd test -Pwatch -Dheadless=false "-Dcucumber.tags=not @sandbox-cleanup" "-Dcucumber.features=src/test/resources/features/cloudlabs_config.feature"
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
