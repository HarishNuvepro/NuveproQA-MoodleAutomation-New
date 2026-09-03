# All Course Page — coverage review (2026-08-25)

Gate before scripting. Source: `All Course Page ` in `docs/MoodleRegression_Testing_Kiwi.xlsx` (106 original rows).
Plugin templates were not in this repo; inventory is from the 106 summaries + the Nuvepro catalog (All Labs / All Courses) chrome described there. Roles under test = **admin + student** only. Low-priority cases deferred.

**Legend:** CONVERT = automate on this sheet · Deferred = later · No = not automatable or wrong sheet.

---

## Verdict

| Bucket | Original rows | After combining |
|---|---|---|
| Convert now (High/Medium, All Courses behaviour) | **91** | **24 scenarios** in `all_courses.feature` (`@ACP-S1`–`@ACP-S4`, `@ACP-S6`–`@ACP-S12`, `@ACP-S14`–`@ACP-S21`, `@ACP-S23`–`@ACP-S27`) |
| Deferred (Low / Slow-3G / other roles / mobile / pending-approval) | **11** | `@ACP-S5`, `@ACP-S13` Read More, `@ACP-S28` Slow-3G, `@ACP-S29` mobile + 1 role-scope row |
| Not automatable | **3** | description visual overlap; scroll smoothness; **empty catalog (ACP-97 / @ACP-S22)** |
| Wrong sheet (Moodle_Roles define-role CRUD) | **3** | 0 here |
| Derived gaps appended | **+4** `CGAP-ACP-1..4` | **4** later (`@CGAP-ACP-*`) |
| **Sheet total after this activity** | **110 rows** | **24 scenarios in the feature; S5/S13 deferred; S22 not automatable** |

Not automatable (3): description bold/special-character **visual overlap**; infinite-scroll **smoothness/no-stutter**; **empty catalog** (`ACP-97` / `@ACP-S22`) — needs a site with zero courses, which a lived-in setup never has.

Execution update 2026-08-25: all 26 scenarios compile and have bound steps. Green so far:
`@ACP-S7` (delete), `@ACP-S8` (valid create), `@ACP-S9` (invalid create), and `@ACP-S26`
(injection-safe search) — 4 exact Kiwi rows. `@ACP-S1` exposed that Manage/Add links are currently
commented out in the live All Courses DOM; `@ACP-S4` exposed that a hidden course remains visible to
a fresh student. These remain red and are not counted as automated.

---

## Combined scenarios (same execution path)

| Tag | Original ACP ids (row-1) | What the one scenario does |
|---|---|---|
| `@ACP-S1` | 1,2,3,18,19 | Admin chrome: Manage courses, search field, Add new course — enabled + redirect |
| `@ACP-S2` | 4–8, 53–55 | Search character classes + empty + numeric; ordered results |
| `@ACP-S3` | 9–12, 28, 95 | Tile fields (name, category, duration, image, description) including later scroll batches |
| `@ACP-S4` | 13, 22 | Hidden course not listed (either hide path) |
| `@ACP-S5` | 14, 15 | **Deferred** — pending-approval needs an in-progress create job; removed from `all_courses.feature` |
| `@ACP-S6` | 16 | Completed course still listed |
| `@ACP-S7` | 17 | Deleted course not listed |
| `@ACP-S8` | 20 | Valid create appears on All Courses |
| `@ACP-S9` | 21 | Invalid create does not appear |
| `@ACP-S10` | 23 | Duplicated course listed |
| `@ACP-S11` | 24, 25 | Move course / duplicate to another category |
| `@ACP-S12` | 26, 27 | Backup then restore; listed under target category |
| `@ACP-S14` | 36–44 | Nested parent/sub categories + courses under them |
| `@ACP-S15` | 45–52, 56–58 | Alphabetical order survives refresh, add, delete, enable/disable, sub-filter |
| `@ACP-S16` | 59 | Import still alphabetical |
| `@ACP-S17` | 60–65, 67–71, 82–83 | `viewhiddencourses` prohibit student; admin still sees; URL denied; no dup role; confirm remove |
| `@ACP-S18` | 72, 73 | New unenrolled user still sees catalog + category dropdown |
| `@ACP-S19` | 74, 78–81, 84 | Self-enrol Yes/No / already enrolled hides Enrol me — **Yes** (green 2026-08-26) |
| `@ACP-S20` | 85–92, 100, 103 | Infinite scroll happy path + debounce + click-through + one API per batch |
| `@ACP-S21` | 94 | Refresh resets to first batch |
| `@ACP-S22` | 97 | **Not automatable** — empty catalog needs a zero-course site; lived-in setups always have at least one course. Removed from `all_courses.feature` |
| `@ACP-S23` | 98, 99 | Scroll to the last card; page shows **No more courses** (not 24/25 batch-size math) |
| `@ACP-S24` | 101 | Student sandbox lab: card **0%**, Start Sandbox then **Start Lab** on the console, card progress **updated** — **Yes** (green 2026-08-26, `@provisioning`). Course delete at teardown releases the lab |
| `@ACP-S25` | 102 | Filter/search resets list; scroll stays inside filter |
| `@ACP-S26` | 104 | Search XSS/SQL treated as text |
| `@ACP-S27` | 105 | Long course name does not break the tile |

`@ACP-S13` is the Low Read More cluster (ACP-29..34) — deferred, one scenario later.

---

## Gaps appended (not in the original 106)

| Id | Gap |
|---|---|
| `CGAP-ACP-1` | Student cannot see Manage courses / Add a new course |
| `CGAP-ACP-2` | Clear search restores the full catalog |
| `CGAP-ACP-3` | Unauthenticated visitor can browse the public All Courses catalog (live theme intentionally disables `require_login`) |
| `CGAP-ACP-4` | Hide then **show** re-lists the course (re-entry) |

Skipped as duplicates of other sheets: define-role CRUD (ACP-75..77) → Moodle_Roles. Manager/teacher visibility (ACP-66) → standing role scope.

Live implementation note: `theme_nuvetheme/allcourses_pagination` uses **24 cards per batch** and
POSTs `service=getAllCourses` to
`/theme/nuvetheme/controllers/courseservices.php`. The Kiwi optimization rows use 16 as an example;
automation follows the deployed 24-card contract and keeps the mismatch explicit.
