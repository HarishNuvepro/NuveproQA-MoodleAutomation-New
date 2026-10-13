# Nuvepro Moodle — UI Automation (Playwright-Java + TestNG + Cucumber)

BDD UI regression automation for the Nuvepro Moodle LMS. Tests run against a **deployed
Moodle instance** (a URL); test-data preconditions use Moodle/plugin **APIs** where possible,
and behaviour is driven through the **UI** with Playwright.

## Prerequisites
- **JDK 17** (already installed; `JAVA_HOME` set).
- **Maven is NOT required** — use the bundled Maven Wrapper (`mvnw`). First run downloads Maven once.
- On first setup: `mvnw exec` will also fetch the Playwright browser (Chromium) if not present.

## Configure
Copy `.env.example` to `.env` and fill in (the `.env` is git-ignored):
- `MOODLE_BASE_URL`, admin/student credentials, `MOODLE_WS_TOKEN` (Web Services token — seeds
  test users via `core_user_create_users` / `enrol_manual_enrol_users` / `core_user_delete_users`)
- `COURSE_ID` (course used by activity-creation tests, e.g. from the course URL `?id=72`)
- `PLAYGROUND_CMID` (an existing sandbox activity), optional `GUIDED_CMID` / `ASSESSMENT_CMID`
- **CloudLabs (provider) API** — needed only for the lab-provisioning tags below:
  `CLOUDLABS_GATEWAY_URL`, `CLOUDLABS_ADMIN_USER`, `CLOUDLABS_ADMIN_PASS`. Used to poll a lab's
  real state and to extend/expire its duration so long-running lab tests don't expire mid-run.

> Note: quote values that contain `#` (e.g. passwords) in `.env`, and keep the file BOM-free.
> Tags that DON'T need CloudLabs or a running lab: `@users`, `@practice_project` (form/landing). Everything under
> `@provisioning` spins a real cloud lab (slow; ~5-min deletes) and needs the CloudLabs vars.

## Run (from this folder: `automation-java`)

| Goal | Command (Command Prompt) |
|---|---|
| Fast suite (hidden) — connectivity + non-provisioning scenarios | `mvnw test` |
| Provisioning suite — spins a REAL cloud lab | `mvnw test -Pslow` |
| **Watch** one scenario, slowed, browser visible | `mvnw test -Pwatch -Dheadless=false -Dslowmo=500 -Dcucumber.tags="@PG-1"` |
| Run only one scenario (hidden) | `mvnw test -Pwatch -Dcucumber.tags="@PG-8"` |

PowerShell: prefix with `.\mvnw` and quote the arg: `"-Dcucumber.tags=@PG-1"`.

### Run by module / group
Each module is a tag. Fast/no-cloud modules first; provisioning modules spin a real lab.

| Module | Tag | Command | Needs |
|---|---|---|---|
| **Moodle users** (create/login/suspend/email + derived gaps) | `@cat_users` / `@users` | `mvnw test -Pwatch "-Dcucumber.tags=@cat_users"` | WS token |
| One user TC | `@U-user-1` … `@U-user-25` | `mvnw test -Pwatch "-Dcucumber.tags=@U-user-1"` | WS token |
| Derived user gaps only (Kiwi) | `@CGAP-U-1 or @CGAP-U-2 or ...` | `mvnw test -Pwatch "-Dcucumber.tags=@CGAP-U-1 or @CGAP-U-8"` | WS token |
| **Practice Project** (form, landing, admin smoke) | `@practice_project` | `mvnw test "-Dcucumber.tags=@practice_project and not @provisioning"` | fixture cmid |
| **Practice Project** lab lifecycle | `@guidedlab` | `mvnw test -Pslow "-Dcucumber.tags=@guidedlab"` | CloudLabs |
| Playground **state actions** (start/stop/delete/sync) | `@stateactions` | `mvnw test "-Dcucumber.tags=@stateactions"` | CloudLabs |
| Sandbox **student user** (landing/console/notes/Access Lab) | `@SB-user-1` | `mvnw test -Pwatch "-Dcucumber.tags=@SB-user-1"` | CloudLabs |
| **Student** self-service (start/stop own lab) | `@studentaction` | `mvnw test "-Dcucumber.tags=@studentaction"` | CloudLabs |
| **Bulk** action on multiple labs | `@bulk` | `mvnw test "-Dcucumber.tags=@bulk"` | CloudLabs |
| Fresh-lab lifecycle / expired-lab | `@freshlab` | `mvnw test "-Dcucumber.tags=@freshlab"` | CloudLabs |

> Lab tags share ONE fresh-user lab per run: provisioned once, expire→extend→actions, deleted
> once at teardown. State is verified via the **CloudLabs API** (authoritative), not UI scraping.
> See CLAUDE.md → "Lab operations — MANDATORY method".

**Flags**
- `-Dheadless=false` — show the browser (default is headless).
- `-Dslowmo=<ms>` — delay each action so a headed run is easy to follow.
- `-Dcucumber.tags="..."` — scope scenarios by tag: a case (`@PG-1`), a group (`@smoke`),
  provisioning (`@provisioning`), or an expression (`@PG-1 or @PG-8`).
- `-Pwatch` — run ONLY the tagged scenario(s), skipping the plain ConnectivityTest logins.

## Reports (regenerated each run)
- **Cucumber (readable, per scenario):** `target/cucumber-report.html`
- **TestNG/Surefire summary:** `target/surefire-reports/emailable-report.html`
- **Traceability & execution** (manual case → scenario → result, tied to the test-case
  spreadsheet): `reports/traceability_execution.html` / `.csv`
  Generate after runs: `python tools/traceability.py` (all Kiwi module sheets +
  `target/cucumber_fast.json` + `cucumber_slow.json`; save each run's
  `target/cucumber.json` to those names).
- **Screenshots:** `reports/*.png`

## Layout
```
src/main/java/com/nuvepro/moodle/
  config/Settings.java        env/.env + -D overrides (headless, slowmo, ...)
  helpers/Auth.java           UI login
  helpers/ApiClient.java      Moodle Web Services (seed/enrol/delete users)
  helpers/CloudLabsClient.java provider API — lab status polling + extend/expire
  pages/                      Page Objects: BasePage, PlaygroundLanding, PlaygroundControlPanel,
                              ManageLabs, GuidedLanding, AdminUsers
src/test/java/com/nuvepro/moodle/
  BaseTest.java               TestNG Playwright lifecycle (used by ConnectivityTest)
  ConnectivityTest.java       login smoke (plain TestNG)
  RunCucumberTest.java        Cucumber runner (emits target/cucumber.json)
  steps/                      GlobalHooks (login-once/storageState + lab safety-net), Hooks,
                              TestContext (PicoContainer); Playground/SharedLab/FreshLab/Bulk,
                              Guided, MoodleUsers step definitions
src/test/resources/features/  Gherkin .feature files (one @<case> tag per manual case:
                              @PG-* playground, @PGG-* guided, @U-*/@CGAP-U-* users)
testng-fast.xml / -slow.xml / -watch.xml   suite definitions
```

## How the suite is organised
- **All-BDD:** every case is a tagged Cucumber scenario; `@provisioning` scenarios spin a real
  cloud lab and share ONE lab per run (PG-12 provisions, PG-15 stops, `@AfterAll` is the safety net).
- **Login once:** admin logs in in `@BeforeAll`, saved as Playwright `storageState`; every scenario
  starts pre-authenticated (no per-scenario login).
