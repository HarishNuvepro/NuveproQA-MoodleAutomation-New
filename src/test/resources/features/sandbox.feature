# Sandbox — activity create (@SB-create-*) + Manage Labs admin (@SB-manage-*).
# One throwaway suite course (not COURSE_ID). Activities/labs kept — no auto-delete. Cleanup: @sandbox-cleanup.
#
# Create registry (target/sandbox-activity-registry.properties):
#   sandbox_create_yes  → @SB-create-1/2   (Yes toggles + admin/student names)
#   sandbox_create_no   → @SB-create-3     (PDF / Parallel / Help = No)
#   sandbox_create_edit → @SB-create-11    (create then edit+save)
#   sandbox_desc_git    → @SB-create-6; sandbox_create_git_yes → @SB-create-2
# @SB-create-7/8/9 — validation / student denied (no activity created).
# Re-runs reuse registered cmids unless @fresh_activity is set.
#
# Manage Labs: fresh per-user lab where needed; lab id at creation; STATUS via CloudLabs API.
# Student console cases: sandbox_user_functionality.feature.
# Bulk: 2 users; pagination page-size=1. @SB-manage-10/16 share `manage_active` student + lab.
# @SB-manage-8: 4 parallel deletes + one shared CloudLabs wait (~8 min).
#
# Run all (sequential): .\mvnw.cmd test -Pwatch "-Dcucumber.features=src/test/resources/features/sandbox.feature"
# Create:   .\mvnw.cmd test -Pwatch "-Dcucumber.tags=@SB-create-1"
# Manage:   .\mvnw.cmd test -Pwatch "-Dcucumber.tags=@SB-manage-8"
#
# Parallel (5 workers, sandbox.parallel=true) — see RUNNERS.md § Sandbox parallel
#   Full file (incl. @provisioning): .\mvnw.cmd test -Psandbox-parallel
#   Same via -Pparallel:             .\mvnw.cmd test -Pparallel "-DsuiteXmlFile=testng-parallel5.xml" "-Dparallel.browsers=5" "-Dcucumber.features=src/test/resources/features/sandbox.feature" "-Dcucumber.tags=not @sandbox-cleanup and not @module-cleanup"
# working parallel run cmd : .\mvnw.cmd test -Pparallel "-DsuiteXmlFile=testng-parallel5.xml" "-Dparallel.browsers=5" "-Dcucumber.features=src/test/resources/features/sandbox.feature" "-Dcucumber.tags=not @sandbox-cleanup and not @module-cleanup" "-Dautomation.deferCleanup=true"



#   UI-only (skip cloud):            add "and not @provisioning" to -Dcucumber.tags
#
# Dependencies (parallel-safe design):
#   • One suite course (CrossProcessLock) — all scenarios share target/sandbox-course.properties
#   • Activity registry per dataset — @SB-create-* register cmids; @SB-manage-* reuse sandbox_user_lab
#     (parallel workers wait up to 240s on registry if create has not run yet)
#   • @fresh_activity (@SB-manage-8, -13/-24, -20) — new activity per run, no registry reuse
#   • Lane sb-provision:shared:{cmid} — all @provisioning on sandbox_user_lab serialize CloudLabs/Manage Labs
#   • Lane sbmanage:manage_active — @SB-manage-10 and @SB-manage-16 serialize (shared student/lab)
#   • @SB-manage-8 — long delete wait (~6–9 min); uses @fresh_activity section (isolated)
#   • @SB-manage-25 — reuses existing suite activity (no When-create in scenario)
#   • Optional phased run: execute @SB-create-* once ( -Pwatch ), then -Psandbox-parallel for manage



Feature: Sandbox — create activity and Manage Labs admin

  # ========== Activity create (@SB-create-*) ==========

  @SB-create-1
  Scenario: Admin creates a sandbox with Yes settings and verifies admin vs student names
    When admin creates a sandbox activity using "sandbox_create_yes"
    Then the sandbox activity is created and visible to admin
    And the sandbox saved settings match "sandbox_create_yes"
    And admin sees the sandbox Activity Name for Admins on the edit form
    And a student sees the sandbox activity name on the landing not the admin name

  @SB-create-2
  Scenario: Admin creates a sandbox with Git description PDF and Parallel enabled and verifies admin vs student names
    When admin creates a sandbox activity using "sandbox_create_git_yes"
    Then the sandbox activity is created and visible to admin
    And the sandbox saved settings match "sandbox_create_git_yes"
    And admin sees the sandbox Activity Name for Admins on the edit form
    And a student sees the sandbox activity name on the landing not the admin name

  @SB-create-3
  Scenario: Admin creates a sandbox with PDF Parallel and Help video set to No
    When admin creates a sandbox activity using "sandbox_create_no"
    Then the sandbox activity is created and visible to admin
    And the sandbox saved settings match "sandbox_create_no"

  @SB-create-6
  Scenario: Admin creates a sandbox with description from Git URL
    When admin creates a sandbox activity using "sandbox_desc_git"
    Then the sandbox activity is created and visible to admin
    And the sandbox saved settings match "sandbox_desc_git"

  @SB-create-7
  Scenario: Create is rejected when the sandbox activity name is missing
    When admin tries to create a sandbox activity using "sandbox_missing_name"
    Then the sandbox activity is not created and a validation error is shown

  @SB-create-8
  Scenario: Create is rejected when the sandbox lab template is missing
    When admin tries to create a sandbox activity using "sandbox_missing_template"
    Then the sandbox activity is not created and a validation error is shown

  @SB-create-9
  Scenario: Student cannot create a sandbox activity
    When an enrolled student opens the sandbox suite course
    Then the student cannot add a Lab activity on the sandbox suite course

  @SB-create-11
  Scenario: Admin edits an existing sandbox and saved settings persist
    When admin creates a sandbox activity using "sandbox_create_edit"
    And admin edits the sandbox activity using "sandbox_edit_update"
    Then the sandbox activity is created and visible to admin
    And the sandbox saved settings match "sandbox_edit_update"

  # ========== Manage Labs admin (@SB-manage-*) ==========

  @SB-manage-1 @provisioning
  Scenario: Admin syncs Start complete then stops the student-started lab
    When admin creates a sandbox activity using "sandbox_user_lab"
    Given a fresh student enrolled for Manage Labs admin actions
    When the student starts the sandbox lab until it is running
    And admin opens Manage Labs and selects that student's lab
    And admin syncs lab status until Start is completed
    When admin stops that student's lab from Manage Labs
    And admin syncs lab status until Stop is completed
    Then the Manage Labs admin stop flow has completed successfully

  @SB-manage-2 @provisioning
  Scenario: Admin syncs Stop complete after the student stops from the console
    When admin creates a sandbox activity using "sandbox_user_lab"
    Given a fresh student enrolled for Manage Labs admin actions
    When the student starts the sandbox lab until it is running
    And admin opens Manage Labs and selects that student's lab
    And admin syncs lab status until Start is completed
    When the Manage Labs student stops the lab from the console
    And admin opens Manage Labs and selects that student's lab
    And admin syncs lab status until Stop is completed
    Then the Manage Labs admin stop flow has completed successfully

  @SB-manage-3 @provisioning
  Scenario: Admin creates a lab from No labs and user moves to Active labs
    When admin creates a sandbox activity using "sandbox_user_lab"
    Given a fresh student enrolled for Manage Labs admin actions
    When admin opens Manage Labs No labs tab
    And admin creates a lab for that student from No labs
    Then that student is not listed on the No labs tab
    And that student appears on Active labs with Create completed

  @SB-manage-4 @provisioning
  Scenario: No labs lists only enrolled users who do not already have a lab
    When admin creates a sandbox activity using "sandbox_user_lab"
    Given Manage Labs No labs fixture users are prepared
    When admin opens Manage Labs No labs tab
    Then the No labs list matches the expected enrolled users without labs exactly

  @SB-manage-5
  Scenario: No labs pagination navigates between pages
    When admin creates a sandbox activity using "sandbox_user_lab"
    Given enough No labs users exist to require pagination
    When admin opens Manage Labs No labs tab
    Then No labs pagination can move to the next page and back

  @SB-manage-6
  Scenario: No labs search by email filters the list
    When admin creates a sandbox activity using "sandbox_user_lab"
    Given a fresh student enrolled for Manage Labs admin actions
    When admin opens Manage Labs No labs tab
    And admin searches No labs by that student email
    Then No labs search results show only that student email

  @SB-manage-7 @provisioning
  Scenario: Student stop then admin Sync confirms Stop completed
    When admin creates a sandbox activity using "sandbox_user_lab"
    Given a fresh student enrolled for Manage Labs admin actions
    When the student starts the sandbox lab until it is running
    And admin opens Manage Labs and selects that student's lab
    And admin syncs lab status until Start is completed
    When the Manage Labs student stops the lab from the console
    And admin opens Manage Labs and selects that student's lab
    And admin syncs lab status until Stop is completed
    Then the Manage Labs admin stop flow has completed successfully

  @SB-manage-8 @provisioning @fresh_activity
  Scenario: Four labs are bulk deleted on Active labs then one is recreated
    When admin creates a sandbox activity using "sandbox_user_lab"
    Given 4 Active labs ready for parallel delete
    When admin deletes those 4 labs from parallel browsers
    Then those labs are Deleted after one shared wait
    When the student opens the sandbox activity after the lab was deleted
    Then the student sees the lab is deleted message on the sandbox console
    When admin creates a replacement lab for the post-delete student
    Then that user sandbox lab console shows the lab is loading

  @SB-manage-9 @provisioning
  Scenario: Admin restarts a Stopped lab with Start and Syncs Start complete
    When admin creates a sandbox activity using "sandbox_user_lab"
    Given a fresh student enrolled for Manage Labs admin actions
    And admin creates a lab for that student from No labs
    When admin stops that student's lab from Manage Labs
    And admin syncs lab status until Stop is completed
    When admin starts that student's lab from Manage Labs
    And admin syncs lab status until Start is completed
    Then the Manage Labs admin start flow has completed successfully

  @SB-manage-10 @provisioning
  Scenario: Active labs search by email filters the list
    When admin creates a sandbox activity using "sandbox_user_lab"
    Given a shared Manage Labs student with an Active lab
    When admin opens Manage Labs Active labs tab
    And admin searches Active labs by that student email
    Then Active labs search results show that student email

  @SB-manage-11
  Scenario: Active labs pagination navigates forward and back
    When admin creates a sandbox activity using "sandbox_user_lab"
    When admin opens Manage Labs Active labs tab
    Then Active labs pagination can move to the next page and back

  @SB-manage-12 @provisioning
  Scenario: Bulk Create from No labs for 2 users
    When admin creates a sandbox activity using "sandbox_user_lab"
    Given 2 enrolled Manage Labs users without labs
    When admin bulk creates labs for those users from No labs
    Then those users appear on Active labs with Create completed

  @SB-manage-13 @SB-manage-24 @provisioning @fresh_activity
  Scenario: Bulk Stop then Start on two fresh labs from No labs
    When admin creates a sandbox activity using "sandbox_user_lab"
    Given 2 enrolled Manage Labs users without labs
    When admin bulk creates labs for those users from No labs
    Then those users appear on Active labs with Create completed
    When admin bulk stops those prepared labs from Manage Labs
    And admin waits 30 seconds for lab operations
    And admin syncs those labs until Stop is completed
    Then those prepared labs are Stopped on Active labs
    When admin bulk starts those prepared labs from Manage Labs
    And admin syncs those labs until Start is completed
    Then those labs are Running

  @SB-manage-15
  Scenario: Manage Labs actions stay gated with no row selected
    When admin creates a sandbox activity using "sandbox_user_lab"
    Given Manage Labs section is ready for admin UI checks
    When admin opens Manage Labs Active labs tab
    Then Start Stop Delete and Sync are disabled with no lab selected
    When admin opens Manage Labs No labs tab
    Then No labs Create is disabled with no user selected

  @SB-manage-16 @provisioning
  Scenario: Lab Details shows identity for a known Active lab
    When admin creates a sandbox activity using "sandbox_user_lab"
    Given a shared Manage Labs student with an Active lab
    When admin opens Lab Details for that student's lab
    Then Lab Details shows that student email or lab id

  @SB-manage-17
  Scenario: Group filter on Manage Labs narrows or resets the list
    When admin creates a sandbox activity using "sandbox_user_lab"
    Given Manage Labs section is ready for admin UI checks
    When admin opens Manage Labs Active labs tab
    Then admin can use the Manage Labs group filter if groups exist

  @SB-manage-18
  Scenario: Manage Labs report export control is available
    When admin creates a sandbox activity using "sandbox_user_lab"
    Given Manage Labs section is ready for admin UI checks
    When admin opens Manage Labs Active labs tab
    Then the Manage Labs report export control is available

  @SB-manage-20 @provisioning @fresh_activity
  Scenario: Admin recreates a lab after failed create with invalid template
    When admin creates a sandbox activity using "sandbox_invalid_gcp_org"
    Given a fresh student enrolled for Manage Labs admin actions
    When the student tries to create a sandbox lab
    Then the sandbox lab create error mentions missing required argument
    When admin edits the sandbox activity using "sandbox_user_lab"
    And the student starts the sandbox lab until it is running
    Then that student appears on Active labs with Create completed

  @SB-manage-21 @provisioning
  Scenario: Admin Syncs during Create InProgress until Complete
    When admin creates a sandbox activity using "sandbox_user_lab"
    Given a fresh student enrolled for Manage Labs admin actions
    When admin starts Create for that student from No labs without waiting
    And admin syncs lab status until Start is completed
    Then that student appears on Active labs with Create completed

  @SB-manage-22
  Scenario: Manage Labs column toggle is available
    When admin creates a sandbox activity using "sandbox_user_lab"
    Given Manage Labs section is ready for admin UI checks
    When admin opens Manage Labs Active labs tab
    Then the Manage Labs column toggle control is available

  @SB-manage-23
  Scenario: Manage Labs exposes Active and No labs page chrome
    When admin creates a sandbox activity using "sandbox_user_lab"
    Given Manage Labs section is ready for admin UI checks
    When admin opens Manage Labs Active labs tab
    Then the labs are listed with status counters
    And per-lab action controls are available
    And the not-created users tab offers Create but not lab actions

  @SB-manage-25 @LT-67 @easybatch2
  Scenario: CloudLabs course progress report loads with lab activity columns
    Given an existing sandbox activity is available on the suite course
    When admin opens the CloudLabs course progress report
    Then the report loads without errors
    And the report lists courses with lab activity columns
