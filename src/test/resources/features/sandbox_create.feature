# Sandbox activity creation (admin). Uses one throwaway suite course (not COURSE_ID).
# Activities and labs are kept — no auto-delete. Cleanup: @sandbox-cleanup.
#
# Only THREE activities are created (registry: target/sandbox-activity-registry.properties):
#   sandbox_create_yes  → @SB-create-1/2/10  (Yes toggles + admin name + student name check)
#   sandbox_create_no   → @SB-create-3/4/5    (PDF / Parallel / Help = No)
#   sandbox_create_edit → @SB-create-11       (create then edit+save)
# Plus sandbox_desc_git → @SB-create-6        (4th only if git case is run)
# @SB-create-7/8/9 create nothing (validation / student denied).
# Re-runs reuse registered cmids unless @fresh_activity is set.
#
# Run this file: .\mvnw.cmd test -Pwatch "-Dcucumber.features=src/test/resources/features/sandbox_create.feature"
# Run one:       .\mvnw.cmd test -Pwatch "-Dcucumber.tags=@SB-create-1"

Feature: Sandbox — create activity

  # Activity A — covers basic create, general Yes settings, admin-name vs student name
  @SB-create-1
  Scenario: Admin creates a sandbox with Yes settings and verifies admin vs student names
    When admin creates a sandbox activity using "sandbox_create_yes"
    Then the sandbox activity is created and visible to admin
    And the sandbox saved settings match "sandbox_create_yes"
    And admin sees the sandbox Activity Name for Admins on the edit form
    And a student sees the sandbox activity name on the landing not the admin name

  # Activity B — PDF No + Parallel No + Help video No in one create
  @SB-create-3
  Scenario: Admin creates a sandbox with PDF Parallel and Help video set to No
    When admin creates a sandbox activity using "sandbox_create_no"
    Then the sandbox activity is created and visible to admin
    And the sandbox saved settings match "sandbox_create_no"

  # Activity C — description from Git
  @SB-create-6
  Scenario: Admin creates a sandbox with description from Git URL
    When admin creates a sandbox activity using "sandbox_desc_git"
    Then the sandbox activity is created and visible to admin
    And the sandbox saved settings match "sandbox_desc_git"

  # No activity created — form validation
  @SB-create-7
  Scenario: Create is rejected when the sandbox activity name is missing
    When admin tries to create a sandbox activity using "sandbox_missing_name"
    Then the activity is not created and a validation error is shown

  @SB-create-8
  Scenario: Create is rejected when the sandbox lab template is missing
    When admin tries to create a sandbox activity using "sandbox_missing_template"
    Then the activity is not created and a validation error is shown

  # Student cannot create a sandbox — Kiwi gap row (student/non-privileged).
  @SB-create-9
  Scenario: Student cannot create a sandbox activity
    When an enrolled student opens the course
    Then the student cannot add a Lab activity

  # Activity D — create (or reuse) then edit+save; settings persist
  @SB-create-11
  Scenario: Admin edits an existing sandbox and saved settings persist
    When admin creates a sandbox activity using "sandbox_create_edit"
    And admin edits the sandbox activity using "sandbox_edit_update"
    Then the sandbox activity is created and visible to admin
    And the sandbox saved settings match "sandbox_edit_update"
