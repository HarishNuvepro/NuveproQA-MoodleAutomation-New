# On-demand automation cleanup

Cleanup **does not run** with normal `mvnw test`. Run it only after a green module run when you want to remove automation artefacts.

## Manifest and registries

| Artefact | Registry file |
|----------|-----------------|
| Users, All Courses ids, lab template names | `target/automation-manifest.properties` |
| Sandbox suite course | `target/sandbox-course.properties` |
| Assessment suite course | `target/assessment-suite-course.properties` |
| Practice suite course | `target/practice-suite-course.properties` |
| Moodle settings baseline | `target/settings-baseline.properties` |

**Never deleted by cleanup:** `.env` / `assessment.env` fixtures only (`COURSE_ID`, `PLAYGROUND_CMID`, `GUIDED_CMID`, optional `GUIDED_*_CMID` variants).

Command shortcuts: **`cleanup.cmd`** (repo root) and [RUNNERS.md](../RUNNERS.md).

## Recommended module run (leave data until you approve)

Run the module with deferred per-scenario deletes; artefacts stay registered for cleanup:

```bash
.\mvnw.cmd test -Pwatch "-Dautomation.deferCleanup=true" "-Dcucumber.tags=@LT-1"
```

After the run is green, **dry-run** cleanup first:

```bash
.\mvnw.cmd test -Pcleanup -Pwatch "-DcleanupDryRun=true" "-Dcucumber.tags=@cleanup-all"
```

Then execute for real:

```bash
.\mvnw.cmd test -Pcleanup -Pwatch "-Dcucumber.tags=@cleanup-all"
```

## Flags

| Flag | Default | Effect |
|------|---------|--------|
| `-Dautomation.deferCleanup=true` | `false` | Skips module `@After` deletes/restores (Users, Upload, All Courses, Lab Templates, Settings, PP overrides). Provisioning/lab `@After` hooks still run (cost/safety). |
| `-DcleanupDryRun=true` | `false` | With `-Pcleanup`, logs what would be deleted/restored; **does not** change Moodle or clear manifest/registry files. |

Env equivalents: `AUTOMATION_DEFER_CLEANUP=true`, `AUTOMATION_CLEANUP_DRY_RUN=true`.

## Tag-filtered cleanup

```bash
# After green Users, Upload users, All Courses, and/or Sandbox runs (one command):
.\mvnw.cmd test -Pcleanup -Pwatch "-Dcucumber.tags=@cleanup-users-all-courses-sandbox"
# Or: cleanup.cmd core

.\mvnw.cmd test -Pcleanup -Pwatch "-Dcucumber.tags=@cleanup-users"
.\mvnw.cmd test -Pcleanup -Pwatch "-Dcucumber.tags=@sandbox-cleanup"
.\mvnw.cmd test -Pcleanup -Pwatch "-Dcucumber.tags=@cleanup-assessment"
.\mvnw.cmd test -Pcleanup -Pwatch "-Dcucumber.tags=@cleanup-practice"
.\mvnw.cmd test -Pcleanup -Pwatch "-Dcucumber.tags=@cleanup-all-courses"
.\mvnw.cmd test -Pcleanup -Pwatch "-Dcucumber.tags=@cleanup-lab-templates"
.\mvnw.cmd test -Pcleanup -Pwatch "-Dcucumber.tags=@settings-cleanup"
```

## Module behaviour

1. **Moodle users / Upload users** — manifest user ids (`@cleanup-users`).
2. **Users + Upload + All Courses + Sandbox** — combined (`@cleanup-users-all-courses-sandbox` in `module_cleanup.feature`; `cleanup.cmd core`).
3. **Sandbox only** — **AUTO Sandbox Suite** course (`@sandbox-cleanup`, same feature file).
4. **All Courses only** — manifest course ids (`@cleanup-all-courses`).
5. **Assessment** — deletes **AUTO Assessment Suite** course when registered (`@cleanup-assessment`).
6. **Practice project** — `@PP-*` create/form flows use **AUTO Practice Suite** course; landing/lab scenarios still use `GUIDED_CMID` from `.env`. Delete suite with `@cleanup-practice`.
7. **Moodle settings** — `@settings-cleanup` restores `settings-baseline.properties` (especially when `deferCleanup=true` during the run).
8. **Lab templates** — manifest disposable template names (`@cleanup-lab-templates`).

Per-scenario `@After` still runs when `deferCleanup` is false (default). Use defer + `-Pcleanup` for the “run all, then one-shot cleanup” workflow.
