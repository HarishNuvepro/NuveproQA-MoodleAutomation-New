# Sandbox Manage Labs — admin flows (@SB-manage-*).
# Same throwaway suite course as create/user (not COURSE_ID). Lab Delete only when
# the scenario is about Delete. Cleanup: @sandbox-cleanup.
# Fresh per-user lab where needed; lab id at creation; STATUS via CloudLabs API.
#
# @SB-manage-1…7   original Sync/Stop/No-labs flows
# @SB-manage-8     combined delete + student message + create-after-delete
# @SB-manage-9…22  gap-fill (restart, search, pagination, bulk, gates, etc.)
# @SB-manage-23    Active + No labs page chrome
# @SB-manage-24    Bulk Stop
# @SB-manage-25    CloudLabs course progress report (admin)
# Student console cases live in sandbox_user_functionality.feature.
#
# Bulk cases use 2 users for now (scale later). Pagination uses page-size=1 with 2–3 users.
# Read-only cases (@SB-manage-10/16) share one named student + Active lab
# (`manage_active`) so they do not each provision a new cloud lab.
# Deletes (@SB-manage-8): 4 labs are deleted from 4 parallel browsers, then ONE
# CloudLabs wait (~8 min) covers all four. Teardown does NOT Delete unless the scenario does.
#
# Run this file: .\mvnw.cmd test -Pwatch "-Dcucumber.features=src/test/resources/features/sandbox_actions.feature"
# Run one:       .\mvnw.cmd test -Pwatch "-Dcucumber.tags=@SB-manage-8"

Feature: Sandbox Manage Labs admin actions

  # ---- Admin Manage Labs flows ----
  # Student provisions from the console; admin verifies Sync Status + Stop on Manage Labs.

  # 1) Admin Stop after student Start
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

  # 2) Student Stop; admin only Syncs until Stop complete
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

  # 3) Create from No labs → user leaves No labs and appears Active (Create complete)
  @SB-manage-3 @provisioning
  Scenario: Admin creates a lab from No labs and user moves to Active labs
    When admin creates a sandbox activity using "sandbox_user_lab"
    Given a fresh student enrolled for Manage Labs admin actions
    When admin opens Manage Labs No labs tab
    And admin creates a lab for that student from No labs
    Then that student is not listed on the No labs tab
    And that student appears on Active labs with Create completed

  # 4) No labs membership = enrolled without a lab (excludes unenrolled + users with labs)
  @SB-manage-4 @provisioning
  Scenario: No labs lists only enrolled users who do not already have a lab
    When admin creates a sandbox activity using "sandbox_user_lab"
    Given Manage Labs No labs fixture users are prepared
    When admin opens Manage Labs No labs tab
    Then the No labs list matches the expected enrolled users without labs exactly

  # 5) Pagination on No labs — 3 users + page size 1 (no large seed set)
  @SB-manage-5
  Scenario: No labs pagination navigates between pages
    When admin creates a sandbox activity using "sandbox_user_lab"
    Given enough No labs users exist to require pagination
    When admin opens Manage Labs No labs tab
    Then No labs pagination can move to the next page and back

  # 6) Search by email on No labs
  @SB-manage-6
  Scenario: No labs search by email filters the list
    When admin creates a sandbox activity using "sandbox_user_lab"
    Given a fresh student enrolled for Manage Labs admin actions
    When admin opens Manage Labs No labs tab
    And admin searches No labs by that student email
    Then No labs search results show only that student email

  # 7) Confirmed green path (same as #2): student Stop + admin Sync Stop.
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

  # ---- Gap-fill admin Manage Labs (@SB-manage-8+) ----

  # 4 cloud deletes in parallel browsers, then one shared Deleted wait (~8 min not ~32).
  # Covers single delete + student message (@SB-manage-8), two-lab delete (@SB-manage-14),
  # and create-after-delete (@SB-manage-19).
  @SB-manage-8 @provisioning
  Scenario: Four labs are deleted in parallel then one is recreated
    When admin creates a sandbox activity using "sandbox_user_lab"
    Given 4 Active labs ready for parallel delete
    When admin deletes those 4 labs from parallel browsers
    Then those labs are Deleted after one shared wait
    When the student opens the sandbox activity after the lab was deleted
    Then the student sees the lab is deleted message
    When admin creates a replacement lab for the post-delete student
    Then that student appears on Active labs with Create completed

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

  @SB-manage-11 @provisioning
  Scenario: Active labs pagination navigates between pages with 2 users
    When admin creates a sandbox activity using "sandbox_user_lab"
    Given 2 Manage Labs users with Active labs for pagination
    When admin opens Manage Labs Active labs tab
    Then Active labs pagination can move to the next page and back

  @SB-manage-12 @provisioning
  Scenario: Bulk Create from No labs for 2 users
    When admin creates a sandbox activity using "sandbox_user_lab"
    Given 2 enrolled Manage Labs users without labs
    When admin bulk creates labs for those users from No labs
    Then those users appear on Active labs with Create completed

  @SB-manage-13 @provisioning
  Scenario: Bulk Start restarts 2 Stopped labs
    When admin creates a sandbox activity using "sandbox_user_lab"
    Given 2 Manage Labs users with Stopped labs
    When admin bulk starts those labs from Manage Labs
    And admin syncs those labs until Start is completed
    Then those labs are Running

  @SB-manage-24 @provisioning
  Scenario: Bulk Stop stops 2 running labs
    When admin creates a sandbox activity using "sandbox_user_lab"
    Given 2 Manage Labs users with Active labs for pagination
    When admin bulk stops those labs from Manage Labs
    And admin syncs those labs until Stop is completed
    Then those labs are Stopped

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

  # ---- Admin report + prevent double Start/Create ----

  @SB-manage-25
  Scenario: CloudLabs course progress report loads with lab activity columns
    When admin creates a sandbox activity using "sandbox_user_lab"
    Given Manage Labs section is ready for admin UI checks
    When admin opens the CloudLabs course progress report
    Then the report loads without errors
    And the report lists courses with lab activity columns
