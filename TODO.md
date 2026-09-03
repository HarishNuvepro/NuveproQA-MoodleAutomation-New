# TODO / Backlog — Nuvepro Moodle UI Automation

Deferred work items and scope decisions. See `CLAUDE.md` for the standing conventions these follow.
Keep this updated as phases complete.

> **Live module status (done / open / deferred) lives in `PROGRESS.md`** — the single reference for
> what's automated and what's pending. This file holds the longer-lived scope decisions + backlog notes.

## Scope decisions (locked)
- **Priority:** automate **High then Medium** first; **Low-priority cases are deferred** (not now).
  Respect the `Importance` column set by QA.
- **Roles:** test **admin + student** only (default). Other roles deferred / on request.
- **Sequencing:** finish the full case set on **Account** lab templates first; add platform /
  Composition / VM variants at the END (see below).

## Deferred — lab-template-type variants (do these at the END, after Account cases are complete)
These add variants of the **lab-action-relevant** cases (create/start/stop/delete/console) for each
template type. Each needs an activity configured with that template type (record cmids in `.env`).

- [ ] **Cloud platforms** — add AWS / CSP / GCP variants for the relevant playground cases.
      Prereq: activities with AWS, CSP, GCP account templates → cmids.
      (Note: `CGAP-PG-22` create-per-platform already drafted; expand action cases similarly.)
- [ ] **Composition labs** — add cases for lab actions with a Composition template.
      Prereq: a composition playground activity → `PLAYGROUND_COMPOSITION_CMID`.
      (Coverage matrix §C flags composition provision + §E items as gaps.)
- [ ] **VM labs** — add cases for lab actions with a VM template (iframe, machines dropdown, etc.).
      Prereq: a VM playground activity → `PLAYGROUND_VM_CMID` (already an env slot; PG-4 skips until set).

### Prerequisite (blocks the above)
- [ ] **Identify/select test activities** that use Account / VM / Composition / per-platform templates
      and record their cmids in `.env`. Discovery helper: `../automation/... discover_activities.py`
      (classifies activities by lab type; extend to detect template type where possible).

## Playground — remaining case gaps (from Playground_Coverage_Matrix.md §E)
- [ ] Review §E (~22 candidate gaps) → accept/trim → add approved as `CGAP-PG-40+`.
- [ ] Then automate the full playground set (Account templates) and mark playground complete.

## Next modules (apply the same coverage-matrix method) — see PROGRESS.md for current state
- [x] Guided (presence batch done; lab-actions/attempt-lifecycle open — see PROGRESS.md)
- [x] Users (~24 green; setup-dependent cases open)
- [x] Tags (creation + management done; T17/T41 open, Orgo deferred)
- [ ] Assessment (challenge) — largest sheet (~145+); coverage matrix → gap cases → automate.
- [ ] Then Roles, Reports, Activities, All-Course-Page, Settings, Groups, cron, etc.

## Cosmetic note (NOT a functional defect — retracted 2026-07-02)
- Manage Labs `#np-ap-manage-delete-btn` keeps a harmless `disabled` ATTRIBUTE in the "normal lab"
  branch of `manage-labs.js setToolbarButtonState` (a missing comma makes
  `"#np-ap-manage-delete-btn #np-ap-cl-assessment-lab-actions"` a descendant selector). **This does
  NOT break Delete:** the element is an `<a>`, `disabled` has no effect on anchors, and the click
  handler fires normally — verified live: clicking Delete shows the "Delete selected lab(s). Are you
  sure?" confirmation. Delete/create-after-delete/deleted-state all work. Earlier "High defect" report
  was WRONG (my automation mis-gated on the anchor's disabled attr) and has been retracted; automation
  now force-clicks the action anchors and does not gate on `disabled`.

## Known / to verify at runtime
- [ ] **PG-37 Lab Details** (`CGAP-PG-37`): `#lab-detail-modal` is not in the manage-labs DOM and
      clicking Lab Details (with a lab selected, button enabled) shows nothing detectable for a
      stopped lab. Scenario currently SKIPS if no details view appears. Verify how Lab Details
      renders (AJAX modal? different lab state? separate window?) and make the assertion firm.

## Sandbox @SB-user — deferred exact value checks (presence OK for now)
- [ ] **Duration** — assert allotted/consumed/remaining against **CloudLabs portal** values for the lab.
- [ ] **Credentials** — assert exact username/password against **CloudLabs portal** values.
- [ ] **Lab Info** — content is from **lab template creation** (often populated; not normally empty).
      When template lab-info text is available in config/test data, assert that exact text on the console.
- [x] **Description / Instructions** — must match activity-create description (landing + console Instructions).

## Tooling / onboarding
- [ ] Traceability tool: keep in Python or port to Java (Apache POI + Jackson) if all-Java desired.
- [ ] Consider Allure reporting for a shareable dashboard.
- [ ] Trace-on-failure (Playwright trace) for debugging headless failures without re-running headed.
