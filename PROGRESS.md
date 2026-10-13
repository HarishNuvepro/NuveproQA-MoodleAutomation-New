# Progress Tracker — Nuvepro Moodle UI Automation

Living status of what's **automated (green)**, **open (to do)**, and **deferred (with reason)**, by
module. Update as batches complete. Legend: ✅ done · 🔲 open · ⏸️ deferred/blocked.
Last updated: 2026-08-26.

Repo: https://github.com/codelogin007/NuveproQA-MoodleAutomation (branch `main`).
Run a module: `mvnw test "-Dcucumber.tags=@<tag>"`.

---

## Coverage at a glance
**Authoritative per-case status is the `Automation_Coverage` dashboard sheet in
`docs/MoodleRegression_Testing_Kiwi new.xlsx`** — the master (mind the space in the filename);
`MoodleRegression_Testing_Kiwi.xlsx` is the backup. Each case has an `Automated` + `Automation_Ref`
column. Coverage totals as QA has them: **474 Yes · 9 Partial · 8 Deviation (findings) · 281 Deferred
(gap rows) · 786 No = 1,558** → **30% of worked-sheet cases**; the sheets not listed there are 0%.
`Regression_Dashboard` tracks execution in the module order QA asked for: Moodle_users 32/47 (100%),
Upload_Users 39/48 (90.7%), All Course Page 84/110 (84.0%), Moodle_Sandbox_Labs 48/62 (82.8% — the
merged Sandbox cases raised the total from 53), then a single Assessment row covering both suites
(47 + 24 = 71 of the 350 cases the release plan counts, 20.3%) — **274 executed / 617**.
`Release_Regression_Plan` leads with the same five modules, then `Practice_Project` (row 15), then keeps
QA's original order; it also carries the `Upload_Users` row that was missing. Its TOTAL counts
**2,163** cases across 34 module sheets (was 2,132 before `Upload_Users` was added and before Guided
was merged away).
Verify both sheets against the case rows with **`python tools/check_kiwi_dashboards.py`** (read-only)
and re-cache the formulas after any openpyxl save with **`python tools/refresh_kiwi_dashboard.py`**.
As of 2026-08-27 the check is clean on `Regression_Dashboard` and flags 5 stale `Automation_Coverage`
rows for QA: Moodle_users (shows 25 Yes, data says 32), Moodle_Roles (53/102 vs 51/89),
Moodle_Assessment_Labs (124/347 vs 128/344), Moodle_Sandbox_Labs (48/53 vs 52/62, from the Sandbox
merge) and Practice_Project (still shows the old Guided counts 21/61 against the merged 16/184).
The table below is the module-level narrative; the dashboard is the source of truth per case.

### Modules with automation
| Module (Kiwi sheet) | Automated scenarios | Doc cases covered | Derived gaps | Deferred (reason) |
|---|---|---|---|---|
| Sandbox lab (Moodle_Sandbox_Labs 56; also overlaps Sandbox / Lab_Control_Panel / My_Labs) | ~24 green | 55 Yes + 1 Partial / 56 | CGAP-PG(37)+SandboxGap | template variants, config-gated, value-checks |
| Guided → now `Practice_Project` (185, merged 2026-08-27: 44 of the 61 Guided cases were unique, 17 duplicated existing practice cases) | 13 | ~12 / 44 carried | CGAP-PGG (19) | Complete btn dormant; overrides/checkpoint; cron |
| Moodle_users (47) | 32 | 32 / 47 | — | 15 marked No (not automatable) |
| Upload_Users (48) | 31 green | 39 Yes + 4 Partial / 48 | 13 extended scenarios green | 5 marked No for now: encoding, custom field, mail, templates, site config |
| Tags (Moodle_Tags 43) | 29 | ~29 / 43 | CGAP-T (11) | Orgo/report tags, config-gated |
| Roles (Moodle_Roles 90) | 10 (matrix ≈ 56 cases) | ~56 / 90 | CGAP-R (12) | enforcement gaps, broad functional |
| Groups (Groups_Testcases 48) | 11 | ~10 / 48 | CGAP-GR (11) | cohort-sync enrolment, reports, time-based |
| Assessment (Moodle_Assessment_Labs 327) | 16 (15 green + 1 defect red) | ~10 / 327 | CGAP-A (11) | PS-CRUD, per-testcase marks, cron, proctoring, hide-grades |
| Pre-Delivery (Pre_Delivery_Lab_Checklist 76) | batch 1–3 authored | ~62 Yes / 9 Partial / 5 No | CGAP-PDL (4) | green run + set LAB_READINESS_DASHBOARD_PATH if discovery skips |
| NuveBulkMail (local_bulkmail, sheet 110) | 4 green | ~5 / 110 | CGAP-BM (3) | compose/send, templates, reminders, campaign cron (need SMTP capture) |
| AWS_Marketplace (local_nuveawsmarketplace, 36) | 4 green + 1 defect red | ~6 / 36 | CGAP-AWS (3) | subscriptions flow, save valid config (mutates), multi-region |
| Content_Integration_Git_URL (24) | 1 green | ~3 / 24 | CGAP-CIG (2) | git-fetch valid/invalid, all-three-lab-types matrix |
| Difficulty_Level_Configuration (14) | 2 green | ~3 / 14 | CGAP-DLC (3) | empty-save handling, config→activity flow |
| Randomization_Activity (mod_randomactivity, 68) | 4 green | ~5 / 68 | CGAP-RAND (3) | dynamic-display/completion behavior, grades, view/index |
| Lab_Templates (cloudlabs labtemplates.php, 82) | 3 green | ~4 / 82 | CGAP-LT (4) | create/edit/delete template (mutates core config), catalog-driven fields |
| Reports (report_cloudlabsreport, 202) | 5 green + 2 defect red | ~9 / 202 | CGAP-RPT (3) | data-verification (counts/grades/dates - need fixtures), per-report-type (imocha/Capgemini) |
| My_Labs (theme_nuvetheme mylabs.php, 41) | 3 green | ~4 / 41 | CGAP-ML (3) | launch/stop/expired/empty-state (need real labs), responsive, settings toggles |
| Sandbox (cloudlabs landing, 12) | 3 green | ~6 / 12 | CGAP-SB (2) | Read More modal content, no-lab state (need data/provisioning) |
| **Subtotal (worked sheets)** | **~141 green scenarios** | **~221 / ~1262 doc cases** | 143 gap rows | — |

### Not started — 0 covered (the rest of the current master, ~1,088 cases across 25 sheets)
Practice_Project 141 · All Course Page 110 (106 + 4 CGAP; 26 combined scenarios authored, live hardening in progress) ·
Moodle settings 79 · Activities 67 ·
Moodle_Logs 50 · CloudLabs_Lab_Creation_Status 49 ·
Lab_Control_Panel 34 · Course_Sync_Multi_Tenant 33 · User_Sync_Orgo 31 · Raven360_AWS_Content 29 ·
Lab_Creation_Region_Based 23 · Moodle_cron 17 ·
Sequential_Activity_Completion 16 · Moodle-Cloudlabs Integration 13 ·
Course_Category_Lab_Provision 12 · Stop_Lab_Enhancement 7 · LoadBalacer_Moodle 7.
(Moodle settings 79 - being done by a second automator, out of this repo's scope here.)

### Grand total (current master)
**~172 of ~1,976 original doc cases automated (~9%)** · ~108 green scenarios · 116 `CGAP-*`/gap rows we
authored & merged into the Kiwi doc. The bulk of the gap is the newly-added feature sheets + expanded
Assessment/Reports/Practice_Project/NuveBulkMail — most are automatable (config/UI/admin surface), some
are real-lab/cron/proctoring (deferred). Note: some new sheets (Sandbox, Lab_Control_Panel, My_Labs,
Practice_Project) overlap Playground/Guided mechanics already automated, so effective coverage of NEW
behavior is a bit higher than the raw 9%.

Findings raised: Roles permissions defect (fixed+verified) · Assessment CGAP-A-8 (`@assessdeviation`,
open — student can open the problem-statements admin page) · guided cmid 780 env fixes (due date/timelimit).

---

## Sandbox lab (cloudlabs)  — tag `@provisioning` groups
# (Code page objects may still be named Playground*; product term in features is sandbox. Tags stay @PG-* for Kiwi.)
- ✅ **State actions** (`@stateactions`): PG-12, 15, 17, 18, 19, 33, 34, 35, 38, 49 — `sandbox.feature`
- ✅ **Student user functionality** (`@SB-user`): landing/console/Access Lab/stop/continue/role gate/notes
  (covers former `@console` PG-3/5/6/8 + PG-13/14/23/24/30/41/42/45/56)
- ✅ **Bulk** (`@bulk`): PG-21 — same `sandbox.feature`
- 🔲 Value-checks: PG-25, 26, 56, 46 (mostly template-gated on sandbox_1 — need enabling template)
- 🔲 Traceability refresh (results column is stale)
- ⏸️ Template variants (VM / Composition / AWS-CSP-GCP) — need cmids (see TODO.md)
- ⏸️ Config-gated: PG-31, 50, 54, 55 (fault-injection / expired / unlimited templates)
- ⏸️ CGAP-PG-* gap cases (Playground_Coverage_Matrix.md §E)

## Practice Project (merged 2026-09-22) — former `guided_*.feature` → `practice_project*.feature`; tags `@practice_project` + `@guidedgaps` + `@guidedlab` + `@guidedattempts`
- ✅ Landing + admin + config presence: PGG-4, 5, 6, 9, 10, 12, 2, 3
- ✅ **Practice Project conversion pass completed 2026-08-29** (`@practice_project`): every one of
  the 191 master rows is classified and every previously blank row has an `@PP-*` scenario.
  Coverage after the deferred conversion: **70 Yes, 2 Partial, 6 Deviation, 6 Deferred, 107 No**.
  Converted from the dashboard-deferred set: PP-41, PP-120, PP-125, CGAP-PGG-13/14/15/17/20 (Yes)
  and CGAP-PGG-19 (Deviation: export types configured, dropdown not rendered).
  High/Medium
  no-lab batch for form fields (lab type, stop, cooldown, attempts 1/100), landing Manage
  Overrides, User Attempts drill-down, consolidated report download, and email send.
  Override CRUD is green for completed eligible users, including unlimited/finite attempts,
  unlimited/finite duration, edit, delete, and re-override. Batch 2 (2026-08-29) added form
  progress-tracking/time-units/lab-details, landing dates/time/attempts, User Attempts
  search/pagination/download/select-to-enable, plugin report-modal cancel, and override status.
  Console and override expansion added green PP-9/10/18/34/44/93/129/141.
  Six derived gaps appended as `CGAP-PP-1`…`CGAP-PP-6`; Get Progress console cases are No (dormant UI).
  Deviations: PP-31/32 have no Complete action, PP-52 has no "No Attempts" option,
  PP-117 permits multi-participant selection, and PP-131 does not open checkpoint results.
  Config-gated scenarios are implemented for report, API, variant-CMID, timed, and provisioning
  cases. Current environment blockers: configurable report URLs/artifacts unavailable; seeding WS
  token lacks `mod_cloudlabs_get_guided_project_result`; CloudLabs login returns HTTP 401; optional
  without-progress/no-stop/parallel/timed CMIDs are unset.
- ✅ Landing + admin + config presence: PGG-4, 5, 6, 9, 10, 12, 2, 3
- ✅ **Derived gaps, no-provisioning** (`@guidedgaps`): CGAP-PGG-13/14 (student sees no admin buttons;
  admin participants page blocked for student — capability ENFORCEMENT), CGAP-PGG-15 (due-before-start
  rejected), CGAP-PGG-16/17 (progress-tracking + content-source config toggles)
- ✅ **Lab lifecycle** (`@guidedlab`, one provisioned lab): G14 control panel, G15/CGAP-PGG-8
  start→RUNNING (API), G16/G17/CGAP-PGG-9 stop→STOPPED + back to landing, CGAP-PGG-1 Continue.
  Gotchas handled: start/continue confirm modals; cp.php CP detected by CONTROLS (no
  .guided-control-panel container); lab NOT auto-provisioned — click "Start hands-on lab"; lab id
  read from admin Manage Labs (CP inputs are server-rendered/stale).
- ✅ **Attempt lifecycle** (`@guidedattempts`): CGAP-PGG-5 submit while RUNNING (firm), CGAP-PGG-3
  cooldown blocked-signal observed (1-min gap), CGAP-PGG-2 New Attempt after the gap → CP. All green.
- ⏸️ CGAP-PGG-6 Complete button: DORMANT (commented out in guided-landing.mustache, like Get-Progress)
- 🔲 Attempt lifecycle remainder: CGAP-PGG-4 (attempts exhausted), 7 (restart blocked), 10/11/12
  (checkpoint data / drill-down / override), 19 (table export)
- ⚠️ **Env fixes applied 2026-07-07** (restore notes): guided activity due date was EXPIRED
  (2026-05-09 15:52 → 2027) and time limit was 2 MINUTES (→ 1h) — either made the activity unusable
  for every student; flag to the team if intentional.
- 🔲 Admin: Checkpoint Details modal, User Attempts drill-down, report email
- ⏸️ **Blocked:** G18, G19, G21 (Get-Progress button is DORMANT/commented out — needs product confirmation)
- ⏸️ Deferred (time/cron/data): G20 (cron), G22 (auto-complete), G23/G24 (restart timing), G11/G13/G26 (checkpoint data)

## Moodle_users  — tags `@cat_users` / `@users` (feature TC series `@U-user-1` … `@U-user-32`)
- ✅ Feature maintained like assessment create/userflow: sequential **TC-1..TC-32** + Kiwi `@U*` / `@CGAP-U-*`
- ✅ Create/validate/login: U1–U6, U8 (lockout), U21, U25
- ✅ Profile: U10, U11, U12 · Session/enrol: U22, U13 (participants enrol), U15
- ✅ Admin ops: U24, U26, U27, U30 · Cohorts: U28, U29 · System role: CGAP-U-4 · Bulk suspend/delete: CGAP-U-5
- ✅ Gaps automated: CGAP-U-1,2,3,6,7,8,9,11,12
- ❌ Marked **No (not automatable)** in Kiwi: Remember Me, forgot-password/mail, paid/self-enrol
  variants, course activities overlap, time unenrol, self-registration policy, reset-link expiry,
  profile-picture upload, auth “No login”

## Upload_Users — tag `@cat_upload_users` (`@UU-S1` … `@UU-S31`)
- ✅ Derived 48 cases from the Moodle 4.5 Upload users controls, CSV state model, role/input matrices:
  30 current-scope rows + 18 deferred variants, stored on the separate `Upload_Users` Kiwi sheet.
- ✅ Implemented 31 combined Cucumber scenarios covering file/preview/results, add/update/skip/append,
  row errors, passwords, suspend/reactivate, delete, rename, course/cohort/profile fields, delimiter,
  student access enforcement, and the 13 extended Upload users options.
- ✅ **All 31 scenarios green (2026-08-26)** → Kiwi `Upload_Users`: **39 Yes + 4 Partial** of 48
  (81% Yes; 90% Yes+Partial). Partial: UU-02 (example CSV link asserted, file not downloaded/parsed),
  UU-07 (missing *email* header only), UU-24 (unknown `course1` proven not to enrol, the row still
  being created is not asserted), UU-45 (over-limit firstname is refused and creates no user; Moodle
  fatals with "Error writing to database" instead of a per-row error and aborts remaining rows).
  Extended green: UU-32…UU-41 and UU-46…UU-47 (`@UU-S19`…`@UU-S28`, `@UU-S30`, `@UU-S31`).
- ✅ `@UU-S18` needs no config: it targets the account running the suite, which Upload users refuses
  as both "your own account" and a site administrator before it ever calls `delete_user()`.
  `UPLOAD_USERS_PROTECTED_ADMIN_USERNAME` still redirects it at another administrator.
- 📌 Live behaviours the cases were corrected to: a weak CSV password is **flagged + forced reset**,
  not rejected (UU-15); Default values only apply to columns the CSV omits, so the default is
  exercised on Institution (UU-26); `uu_increment_username()` bumps a trailing number instead of
  appending one (UU-12); with **Standardise usernames = No** a username containing a space aborts the
  whole upload with a fatal "given username contains invalid characters" page and no totals — that
  row is excluded from the mixed-CSV case (UU-08) and is a candidate defect to raise.
- 📌 Account lookups go through `AdminUsers.lookupUserIdByEmail` (Bulk user actions' legacy filter,
  whose Available list carries the user id) because the Browse users report-builder table applies
  filters over AJAX and keeps them in the session, which silently turned created users into
  "missing".
- ❌ Marked **No (not automatable for now)**: UU-31 non-UTF-8 encoding fixture, UU-42 configured
  custom profile field, UU-43 SMTP/mail capture, UU-44 low-priority default-template matrix, and
  UU-48 site-wide duplicate-email configuration mutation.

## Tags  — tags `@tags` (creation) + `@tagmgmt` (management)
- ✅ **Creation** (activity form): T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T27, T38
- ✅ **Management** (/tag/manage.php): T16 rename, T18 delete, T28 multi-delete, T24/T25/T29 combine,
  T19/T20 standard→suggested, T21/T22/T26 not-suggested, T23 renamed-suggested
- 🔲 T17 (rename updates modified date), T41 (UI messages on add/delete/combine)
- ⏸️ T30-T39 (tags on Orgo lab-details page + resource-duration report — EXTERNAL Orgo/cloudlabs reports)
- ⏸️ T40 (admin blocks tag editing — config), CGAP-T gaps (T-2 dedup, T-5 browse, T-7 reuse+count, T-10 length cap are automatable)

## Roles  — tag `@rolematrix`
- ✅ **Cloudlabs capability matrix (R24-R79)**: 5 data-driven scenarios verify all 12 cloudlabs caps
  per role vs db/access.php defaults (Manager/CourseCreator/EditingTeacher=allow, non-editing=notset,
  student=prevent). Admins bypass checks (not verified). All green.
- ✅ **Finding caught + fixed**: on 2026-07-06 flagged Editing-teacher (missing 4 lab caps) and
  Non-editing-teacher (had 2 assessment caps vs R66); site config corrected; re-run confirms fixed.
  See ../Docs/Roles_Findings.md.
- ✅ **Custom roles R86-R87** (`@customrole`): create a custom role from an archetype preset (gets
  cloudlabs caps) → listed → capabilities enabled. Role deleted at teardown.
- 🔲 **R88** deferred: custom role in the "Enrol users" dropdown — a fresh role likely needs
  "allow role assignment" config first; step SKIPS with a reason (AJAX enrol-modal + allow-assign work)
- ✅ **Functional subset** (`@rolefunc`): R2 (admin Site Administration access), R17 (assign Manager →
  system manager), R3 (assign then remove a role) — all green (system-context dual-listbox assign)
- 🔲 Enforcement gaps CGAP-R-1/2/7 (control visible/hidden by capability at runtime), R80-R85 UI integration
- ⏸️ R88 custom role in enrol dropdown (deferred — allow-assign / AJAX modal)
- ⏸️ R1/R5-R16 broad functional (course create/grade/submit/forum — overlaps other sheets)
- ✅ Gap rows CGAP-R-1..12 written into the Moodle_Roles sheet

## Groups  — tag `@groups`
- ✅ Create group (G12), duplicate-name rejected (G14 - Moodle enforces unique names; manual expected
  duplicates allowed → corrected), groups dropdown present (G1), default All Groups (G5), select single
  group (G2), search in dropdown (G4), groups column on Manage User Labs (G42)
- ⏸️ G3 select-multiple: SKIPS - the dropdown is single-select (All Groups or one), so multi-select is
  not supported by the UI (documented)
- ✅ Cohorts + members: create cohort (G11), duplicate cohort names allowed (G13 - opposite of groups),
  same user in multiple groups (G24, via the group-members dual-listbox)
- 🔲 Report export by group (G6-G10, G27-G37), groups column on other reports (G34)
- ⏸️ Cohort-sync enrolment method (G15-G19, G23, G25-G33, G45-G46) - complex enrolment-method UI + sync
- ⏸️ Cohort membership add/remove (G20-G22) - needs the cohort-assign page id (JS `:id` on the index)
- ⏸️ Duration/time-based (G26/G30-G33/G43)
- ✅ Gap rows CGAP-GR-1..11 in the Groups_Testcases sheet

## Assessment (challenge)  — tag `@assessgaps` (Kiwi sheet: 327 cases, 107 High)
- ✅ **Batch 1 (no provisioning)**: landing presence (attempt controls/details on ASSESSMENT_CMID=781,
  course 50); CGAP-A-1..4 config toggles (evaluationtype AUTO/ANSWER/MANUAL fields, randomization
  hides editor, content-source git URL, cooldown→attempts-after with polling); CGAP-A-5/6/7 edit-form
  validations (due-before-start, missing gradepass, invalid testscriptparams JSON — validation failure
  saves nothing); CGAP-A-8 student blocked from managechallenges.php + no admin controls on landing.
- 🔴 **DEFECT found (CGAP-A-8 / `@assessdeviation`, fails by design until fixed)**: a STUDENT can open
  `assessment-problemstatements.php` by direct URL — the admin table renders (the landing button is
  hidden per A249, but the page lacks a capability check; managechallenges.php IS protected).
- ✅ **Batch 2 (provisioned + REAL evaluation)** (`@assesslab` + `@assesscomplete`): start → control
  panel → lab RUNNING (API) → submit → the engine runs the configured S3-Life-Cycle scripts → attempt
  Evaluated (fresh student = grade 0 / Fail, EXPECTED). Then the activity AUTO-COMPLETES
  (isChallengeEnded=1, finalResult=Fail — also covers A127); CGAP-A-9 consent gate verified wired
  (final submit disabled → enables on ticking #confirmFinalSubmission). All green.
  Gotchas: challenge-landing.mustache is the ACTIVE template (not assessment-landing); complete button
  is `.np-ap-cl-challenge-end` (auto-hides once ended); "Start hands-on lab" launch needed; lab id from
  admin Manage Labs. ASSESSMENT_SCRIPT_URL/LAB_TEMPLATE documented in .env.example (plan 777/team 90).
- ✅ **Batch 3 (admin surface, no provisioning)** (`@assessadmin`): manage-challenges table lists
  enrolled users with grade/result columns (A11/A16); override + report + user-attempt controls present
  (A45-54/A103 surface); user-attempts drill-down opens userattempts.php (#np-ap-user-attempts-table,
  via row-radio select + #np-ap-user-attempt); admin CAN open the problem-statements page (A249-admin,
  counterpart to the @assessdeviation student defect). All green.
- 🔲 Problem-statements CRUD (A250/255/257/259): 781 has NO problem statements (randomization off) -
  needs a randomized activity with seeded PS; deferred until such an activity/cmid is available
- 🔲 Overrides deep flow (add/decrease/unlimited A45-A54), retake-after-completion, manual grade update
  (A115), console-open (A125) - need attempt data / provisioning
- ⏸️ Per-testcase marks (A65-96 — needs real lab work), cron clusters (A99/185-188/206-214), proctoring
- ✅ Matrix: ../Docs/Assessment_Coverage_Matrix.md · CGAP-A-1..12 in the Kiwi sheet (A-10 deduped)

## Pre-Delivery Lab Readiness Checklist  — tag `@predelivery` (Kiwi: Pre_Delivery_Lab_Checklist, 72)
- ✅ **Batch 1 (no provisioning)**: Lab Readiness page (`lab_readiness.php?id=cmid`) admin-only (cap
  `mod/cloudlabs:viewlabreadiness`; student blocked); checklist `.checklist-item` completion drives
  `#labProgressBar` to 100% + green `bg-success`; env-type VM/Account toggles `.vm-field`/`.account-field`;
  mandatory Support-Engineer name (empty → `alert()` → save blocked, nothing persists). All green.
- ✅ **Batch 2–3**: `predelivery_dashboard.feature` (filters/cards/table/Open/Apply), `predelivery_mutate.feature`
  (throwaway sandbox save, audit, visibility, screenshot re-upload, CGAP-PDL-4). Optional `.env`:
  `LAB_READINESS_DASHBOARD_PATH`. 🔲 Green runs + Partial→Yes where env matches.
- 🔲 Legacy note: successful save +
  audit-log row, screenshot upload + re-upload override, backend-warning banner, force-enable flagging
- ✅ Matrix ../Docs/PreDelivery_LabReadiness_Coverage_Matrix.md · CGAP-PDL-1..4 (pending Kiwi write — file was open)

## NuveBulkMail  — tag `@bulkmail` (plugin `local_bulkmail`; Kiwi sheet NuveBulkMail, 110)
- ✅ **Batch 1 (settings form, no mail)**: `/local/bulkmail/settingspage.php` — passwordpattern (5 opts,
  incl "Username + custom text") + passwordbehavior ("Use pattern from settings" / "Send reset link") +
  from/domains fields present; customtext accepts alphanumeric+special; settings blocked for a student
  (cap `local/bulkmail:managesettings`); save a pattern+behavior → persists, then originals RESTORED. Green.
- 🔲 Batch 2+: compose flow (compose.php step1/step3), templates CRUD (templates.php/template_form.php),
  reminders (reminders.php), schedule, logs — automatable UI; ⏸️ actual mail send + campaign cron
  (process_campaigns) need SMTP capture.

## AWS Marketplace  — tag `@awsmarket` (plugin `local_nuveawsmarketplace`; Kiwi sheet 36)
- ✅ **Batch 1 (admin settings)**: `/admin/settings.php?section=local_nuveawsmarketplace_settings` shows
  region dropdown + access_key + secret_key + support_email; invalid support email rejected (validation,
  not persisted); student blocked (Moodle "sectionerror"). Green.
- 🔴 **FINDING (`@awsdeviation`, red by design)**: the **AWS Secret Key is a plain-text input, not masked**
  (should use admin_setting_configpasswordunmask) — sensitive credential shown in clear.
- 🔲 Batch 2: subscriptions flow (awsmpsubscriptions.php), multi-region, save-valid-config (mutates → save+restore)

## Cloudlabs config sheets  — tag `@cloudlabsconfig`
- ✅ **Content_Integration_Git_URL** (`@contentsource`): `descriptionsource` manual/git toggles the
  `descriptiongiturl` field (the general/sandbox variant; guided `guidecontentsource` + challenge
  `challengecontentsource` already covered). 🔲 git-fetch valid/invalid + full 3-lab-type matrix.
- ✅ **Difficulty_Level_Configuration** (`@difficulty`): activity `difficulty_level` select (configured
  levels + Beginner default); admin "Difficulty Level Settings" (`cloudlabs/difficulty_levels`) on
  Cloud Server Settings. ⚠️ Spec discrepancy: documented levels Beginner/Practitioner/Proficient/Expert
  vs actual Beginner/Intermediate/Advanced/Expert (admin-configurable). 🔲 empty-save handling.

## Randomization Activity  — tag `@randomactivity` (plugin `mod_randomactivity`; Kiwi sheet 68)
- ✅ **Batch 1**: add form shows name/dynamicdisplay/duedate/completiontrack; name accepts
  alphanumeric+special; name required (empty blocked); create a randomization activity → appears in
  course → deleted via UI in `@After` (WS token lacks core_course_delete_modules). All green.
- 🔲 Batch 2: dynamic-display behavior, completion tracking, grades (grade.php/gradeslist.php),
  view/index rendering, activity randomization logic

## Phase 0 hygiene (2026-09-22) — **closed**
- ✅ **`tools/phase0_hygiene.py`**: Lab Templates verified refs → **`reconcile_lab_templates_refs.py`**
  (31 Kiwi **Yes** rows without a matching `@LT-*` scenario → **Partial** + honest `Automation_Ref`) →
  Tags ref check → **`sync_kiwi_and_traceability.py`**.
- ✅ **Moodle_Lab_Templates** dashboard: **35 Yes / 31 Partial / 18 No** (84 total); Regression_Dashboard
  **35 executed** (53% completion on that row). Do **not** run `update_lab_templates_coverage.py` after
  reconcile — it would re-mark missing tags as Yes.
- ⚠️ Pre-existing **Automation_Coverage DRIFT** on Users / Roles / Assessment (QA-owned rows) — unchanged.

## Lab Templates  — tag `@labtemplates` (cloudlabs labtemplates.php; Kiwi sheet Moodle_Lab_Templates 86)
- ✅ **Batch 1 green (17 scenarios / 21 exact cases)**: list columns incl. showing the hidden
  Access Keys / Resource Type / Created On columns; Add option/form; required-field and interval
  validation; Create/Start/Unpark presence, defaults and editing; view details; column chooser;
  refresh; name/single-text/long/XSS search; site-admin gate.
  Live-DOM facts this batch pinned down: the action intervals sit on the **Lab Features** tab (not the
  default General one) and Unpark appears only for a parkable resource type (VM); bootstrap-table data
  rows carry `data-index` and arrive behind a `.fixed-table-loading` overlay, and columns hidden by the
  chooser are absent from `thead` entirely; Moodle 4.5 runs Bootstrap 5, so the plugin's Bootstrap 4
  `data-toggle` tabs/dropdown need a JS fallback to activate.
- ✅ **LT-16** (`@LT-16`): View details allows **Edit**; **Delete** is not offered on the view page.
- ⚠️ Interval-specific validation (LT-50/54) is asserted through the field's own required constraint,
  not the plugin's `#createDurationError` text: the form reports required fields one at a time starting
  with Name, so reaching the interval message would mean actually persisting a template.
- ✅ **Batch 2 complete** with stamped throwaway templates (Plan 1747 / Team 90): create/edit/view/
  delete, activate/deactivate, in-use protection, duplicate-name rejection, XSS-name sanitization,
  activity dropdown visibility, Plan/Team/Catalog resolution (including invalid Plan/Team cases),
  browser-error check, existing/duplicated/copied activity retention, and edit propagation. Template,
  activity, and copied-course cleanup run after each scenario.
- ✅ **Provisioning reuse**: exact Lab Template tags now point to the already exercised Sandbox flows
  for iframe, provisioning input, duration/cost/budget, notes/speed test, Lab Info, Stop/Delete, and
  recreate-after-delete. LT-60 additionally observes the transient four-step Start tracker and is green.
- ⚠️ **LT-37 deviation**: randomized assessment problem-statement template selection has no
  Plan/Team/Catalog information panel in the deployed/plugin form.
- ⚠️ **LT-59 deviation**: the Create tracker completes before the ordinary console wait can inspect it;
  a dedicated pre-wait probe is retained to expose the missing/transient behavior.
- ✅ **Coverage reconciliation complete (2026-08-29):** 63 exact cases Yes, 3 Partial, 9
  fail-by-design Deviations, and 11 No because their required external fixtures are unavailable.
  LT-46 is partial (browser errors covered; server logs unavailable), LT-69 is partial (Sandbox
  Create/Start only), and LT-77 is partial (Create failure only).
- ⚠️ **New verified deviations:** LT-6 enables submission inputs but Plan 1747 returns no
  `SubmitChallenge` schema; LT-9 persists notification threshold/message but the activity reports
  `Lab duration not Configured`; LT-39's real Moodle backup/restore omits the CloudLabs activity;
  LT-81 has no bulk import control/service; LT-82 accepts interval `999999999`.
- 🔒 **External blockers (11 No):** Park/Unpark or parked VM (LT-61/65/68), undefined real timing
  behavior and slow-plan fixtures (LT-63/64), proctoring launch (LT-71), Orgo suspend/resume
  (LT-72), SSO/LTI launch harnesses (LT-74/75), webhook inspection (LT-78), and the Low-priority
  site-theme mutation/transient-progress combination (LT-79).
- ⚠️ Nit (CGAP-LT-3): non-site-admin gets a BLANK page (bare `die`), not a proper Moodle access-denied

## Reports  — tag `@reports` (plugin `report_cloudlabsreport`; Kiwi sheet Moodle_Reports 202)
- ✅ **Batch 1 (course report landing)**: `courses.php` lists courses (`#np-rp-courses-npcourses`:
  Course Name/Total Assessments/Participants/Start/End Date); search filters + clears (placeholder
  "Search by name"); course drills into `courseparticipants.php`. Rows load via AJAX (wait for them). Green.
- ✅ **Batch 2 (participant drill-down)**: `courseparticipants.php` lists participants (Name/Email/
  Result/Group); drilling a participant opens `userchallenges.php`. Green.
- 🔴 **FINDING (`@rptdeviation`, red by design) — WHOLE CHAIN LEAKS**: every `report_cloudlabsreport`
  page has ONLY `require_login()` (no capability/context). A STUDENT can open `courses.php`,
  `courseparticipants.php` (**other users' Name/Email/Result**), and `userchallenges.php`/
  `userattempts.php` (**another user's challenge/attempt data**). Cross-user PII + result exposure —
  ESCALATE. (CGAP-RPT-1/2)
- 🔲 Batch 3+: per-report-type (imocha 52, Highest-duration 33, Capgemini 47, assessment-attempts 24),
  export. ⏸️ data-verification (counts/grades/dates = need fixtures)

## My Labs  — tag `@mylabs` (theme_nuvetheme `/theme/nuvetheme/mylabs.php`; Kiwi sheet My_Labs 44)
- ✅ **Batch 1–2 (no provisioning, no Reports)**: cards + search, XSS-safe search, partial-name filter,
  mobile viewport, NuveTheme My Labs controls (@ML-15), nav hide/show (@ML-16/@ML-18), status text
  off on cards (@ML-19). @ML-17 **Partial** — direct URL redirects to `/my/` when Show My Labs is off.
  Kiwi: **10 Yes, 1 Partial**, 9 Deferred, 24 No. Dashboard row **My_Labs** 50% completion.
- 🔲 Batch 3: launch/stop/expired/empty-state/isolation — need `@provisioning` or fixtures.

## Difficulty + Git URL config — tag `@cloudlabsconfig` (`cloudlabs_config.feature`) — **complete (automatable slice)**
- ✅ **Difficulty_Level_Configuration**: `@DLC-1`…`@DLC-9` — activity dropdown + admin settings (mutating
  cases restore via `@difficultyrestore`); `@DLC-3` **Deviation** (Practitioner doc vs live levels).
  Kiwi: **9 Yes**, **2 Deviation**, **4 Deferred**, **2 No**.
- ✅ **Content_Integration_Git_URL**: `@CIG-1`…`@CIG-11` — description/content source toggles per lab type;
  `@CIG-7` **Deviation** (playground still shows description source). Remaining git-fetch/PDF/backup rows **No/Deferred**.
  Kiwi: **5 Yes**, **3 Partial**, **1 Deviation**, **15 No**, **2 Deferred**.

## Sequential Activity Completion — tag `@sac` (`sequential_completion.feature`)
- ✅ **16/16 Yes** in Kiwi: mark-as-done chain (`@SAC-1`, `@SAC-9`…`@SAC-13`, `@SAC-16`), grade gates
  (`@SAC-2`…`@SAC-8`, `@SAC-14`), 3-activity chain `@SAC-15`. Needs `MOODLE_WS_TOKEN`; `@AfterAll` deletes fixture courses.

## Moodle-Cloudlabs Integration — tag `@mci` (Kiwi sheet 13 cases, `@MCI-1`…`@MCI-13`)
- ✅ **Integration smoke (no provisioning)**: Cloud Server Settings fields (URL/company/credentials,
  developer logs, support email), Lab Templates admin + add-form plan/team/access, lab activity add
  form (template select + playground/guided/assessment|challenge types), activity chooser / add-module
  listing, cloudlabs reports on `/report/index.php` or `report_cloudlabsreport`. Kiwi: **11 Yes**,
  **1 Partial** (@MCI-12 — no Test connection control; scenario `@skip` in default runs),
  **1 No** (@MCI-13 uninstall — `@skip`). Feature: `moodle_cloudlabs_integration.feature`.
- Run: `mvnw test -Dcucumber.tags="@mci and not @skip"`

## Sandbox  — tag `@sandbox` (cloudlabs sandbox/playground landing; Kiwi sheet Sandbox 12)
- ✅ **Batch 1 (no provisioning)**: sandbox landing (view.php?id=PLAYGROUND_CMID) shows Lab Details
  (Lab Type=Sandbox / Lab Status / Lab Created date / Sandbox ID), Duration (Alloted/Consumed/
  Remaining), and Description + Read More. Green.
- 🔲 Batch 2: Read More modal content (large text/image), no-lab/not-created state (need data)

## Not started (next sheets — apply the CLAUDE.md coverage-matrix + gap-rows gate first)
- 🔲 Reports (Kiwi: 203 — cloudlabs report), Activities (68)
- 🔲 All Course Page (review + 26 scenario conversion authored 2026-08-25; 4 exact rows green;
  live failures found for hidden-course visibility and commented-out admin controls; hardening open;
  see AllCoursePage_Coverage_Matrix.md)
- 🔲 Settings, cron, Logs, LTI & SSO, LoadBalancer
- 🔲 New Kiwi feature sheets: AWS_Marketplace, Randomization_Activity, Practice_Project, Sandbox,
  Lab_Control_Panel, My_Labs, etc. (many overlap Playground/Guided/Assessment already covered)
- (Assessment: batches 1-3 done — see its section; Roles + Groups: first batches done — remainder open there)

## Gap rows written into the xlsx (derived, `CGAP-*`)
- ✅ Old master `MoodleRegression_Testing.xlsx`: `Moodle_users` CGAP-U-1..12 · `Moodle_Tags` CGAP-T-1..11 ·
  `Moodle_Roles` CGAP-R-1..12 · `Groups_Testcases` CGAP-GR-1..11
- ✅ **Kiwi master (now `MoodleRegression_Testing_Kiwi new.xlsx`)**: all of the above PLUS
  37 Playground gap rows, `Moodle_Guided_Labs` CGAP-PGG (19), `Moodle_Assessment_Labs` CGAP-A-1..12 —
  102 rows merged, Case_Status=PROPOSED, deduped, backed up. Matrices in ../Docs/*_Coverage_Matrix.md.

## Infra / cross-cutting
- ✅ Fresh-user lab + CloudLabs API polling (extend/expire) — the standard lab method
- ✅ WS ApiClient (seed/enrol/delete users); id-based profile checks for the report-builder list
- 🔲 Traceability results column refresh (needs one comprehensive run per suite)
- 🔲 Report-builder deep handling (would unlock CGAP-U-9/11/12 + faster tag ops)
