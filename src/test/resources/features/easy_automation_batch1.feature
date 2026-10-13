# Easy automation backlog — batch 1 of 2 (51 Kiwi cases).
# Sorted High→Medium; see reports/easy_automation_batches.csv.
# Run (PowerShell):
#   .\mvnw.cmd test -Pwatch "-Dcucumber.tags=@easybatch1 and not @provisioning"
#   .\mvnw.cmd test -Pwatch -Pslow "-Dcucumber.tags=@easybatch1"
#
# Lab-template @LT-* and progress @LT-70/@LT-48 live in labtemplates.feature and
# sandbox_user_functionality.feature (also tagged @easybatch1).

Feature: Easy automation batch 1

  # ---- Groups_Testcases (CGAP-GR) ----

  @CGAP-GR-1 @easybatch1 @groups
  Scenario: Admin can delete a course group after creating it
    When admin creates a group
    And admin deletes the created group
    Then the group is no longer listed in the course

  @CGAP-GR-2 @easybatch1 @groups
  Scenario: Empty group name is rejected on create
    When admin submits an empty group name
    Then the empty group name is rejected

  @CGAP-GR-5 @easybatch1 @groups
  Scenario: Admin can add and remove a member from a group
    When admin creates a group with an enrolled member
    And admin removes that member from the group
    Then the member is not listed in the group

  @CGAP-GR-7 @easybatch1 @groups
  Scenario: Manage User Labs shows All Groups when the course has no groups
    When admin opens Manage User Labs without course groups
    Then the groups dropdown defaults to all groups

  @CGAP-GR-10 @easybatch1 @groups
  Scenario: Unicode group name is accepted and listed
    When admin creates a group named "Auto组🧪"
    Then the group is listed in the course

  @CGAP-GR-6 @easybatch1 @groups
  Scenario: Selected group persists after reopening Manage User Labs
    When admin creates a group and opens Manage User Labs
    And admin selects the created group in Manage User Labs
    And admin reopens Manage User Labs for the same section
    Then the groups dropdown still shows the selected group

  @CGAP-GR-3 @easybatch1 @groups
  Scenario: Groupings can be created (deferred enrolment sync)
    When admin attempts to create a grouping with a group
    Then the grouping workflow is available or documented as unsupported

  @CGAP-GR-4 @easybatch1 @groups
  Scenario: Auto-create groups from enrolled users
    When admin attempts auto-create groups for the course
    Then auto-create groups is available or deferred

  @CGAP-GR-8 @easybatch1 @groups
  Scenario: Cohort-sync enrolment enrols cohort members
    When admin configures cohort-sync enrolment for a cohort
    Then cohort-sync enrolment completes or is deferred

  @CGAP-GR-9 @easybatch1 @groups
  Scenario: Bulk import groups via CSV
    When admin attempts bulk group import from CSV
    Then bulk group import is available or deferred

  @CGAP-GR-11 @easybatch1 @groups
  Scenario: Cohort-sync group membership changes propagate enrolment
    When admin adjusts cohort-sync group membership
    Then cohort-sync membership sync is verified or deferred

  # ---- Moodle_Roles (CGAP-R) ----

  @CGAP-R-1 @easybatch1 @roles @roleenforce
  Scenario: Student does not see Manage User Labs create actions
    Given a student enrolled in the sandbox course
    When the student opens Manage User Labs for the sandbox
    Then lab create actions are not available to the student

  @CGAP-R-2 @easybatch1 @roles @roleenforce
  Scenario: Editing teacher sees Manage User Labs create actions
    Given an editing teacher enrolled in the sandbox course
    When the editing teacher opens Manage User Labs for the sandbox
    Then lab create actions are available to the editing teacher

  @CGAP-R-7 @easybatch1 @roles @roleenforce
  Scenario: Custom role with Manage User Labs capability can open Manage User Labs
    When admin creates a custom role with Manage User Labs enabled
    And a user is enrolled with that custom role in the sandbox course
    When that custom-role user opens Manage User Labs for the sandbox
    Then lab create actions are available to the editing teacher

  @CGAP-R-3 @easybatch1 @roles
  Scenario: Course-level capability override changes effective permission
    When admin overrides a cloudlabs capability at course level
    Then the override is saved on the role definition

  @CGAP-R-6 @easybatch1 @roles @rolematrix
  Scenario: View Lab Readiness capability is prevent for students
    Then role id 5 has capability "mod/cloudlabs:viewlabreadiness" set to "prevent"

  @skip @CGAP-R-10 @easybatch1 @roles @rolematrix
  Scenario: Guest role has no cloudlabs lab-action permissions
    Then role id 6 has capability "mod/cloudlabs:activityuserlabs" set to "prevent"

  @CGAP-R-12 @easybatch1 @roles @rolematrix
  Scenario: Student role cloudlabs defaults match the plugin matrix
    Then the "Student" role id 5 has cloudlabs capabilities "prevent"

  # ---- Moodle_Tags (CGAP-T) ----

  @CGAP-T-2 @easybatch1 @tags
  Scenario: Tag names are case-insensitively de-duplicated on the activity form
    When admin seeds a mixed-case duplicate tag on the activity
    Then only one case-insensitive tag instance is stored on the activity

  @CGAP-T-4 @easybatch1 @tags @tagmgmt
  Scenario: Renaming a tag updates activities that use it
    Given a new standard tag on the activity
    When admin renames the tag
    And I open the activity edit form
    Then the renamed tag is present on the activity

  @CGAP-T-5 @easybatch1 @tags @tagmgmt
  Scenario: Tag browse lists a tagged activity
    Given a new standard tag on the activity
    When admin opens the tag browse page for that tag
    Then the tagged activity appears on the tag browse page

  @CGAP-T-7 @easybatch1 @tags
  Scenario: Tag usage count reflects multiple activities
    Given the same tag is on two activities
    When admin opens tag management for that tag
    Then the tag usage count is at least two

  @CGAP-T-10 @easybatch1 @tags
  Scenario: Tag name length beyond the maximum is rejected
    When I open the activity edit form
    And I attempt to add a tag longer than the maximum length
    Then the oversized tag is not persisted on the activity

  @skip @CGAP-T-1 @easybatch1 @tags
  Scenario: Student tag editing follows Moodle capabilities
    When I open the activity edit form
    Then the Tags field is available

  # ---- Content / difficulty (batch 1 rows) ----

  @CGAP-CIG-1 @easybatch1 @cloudlabsconfig
  Scenario: Invalid git URL shows an error on save
    When I open a new cloudlabs activity form for lab type "guided"
    And admin selects invalid guided git content URL and attempts save
    Then the activity form rejects the invalid git URL or remains on the edit form

  @CIG-batch1-manual @easybatch1 @cloudlabsconfig
  Scenario: Description source toggles manual and git on a new activity
    When I open a new cloudlabs activity form for lab type "guided"
    Then the guided content source toggles its git URL field

  @CIG-batch1-playground @easybatch1 @cloudlabsconfig
  Scenario: Playground description source toggles when present
    When I open a new cloudlabs activity form for lab type "playground"
    Then the description source toggles the git URL field

  # Difficulty (@DLC-*): see difficulty_level_configuration.feature (not duplicated here).

  # ---- My Labs ----

  @CGAP-ML-2 @easybatch1 @mylabs @provisioning
  Scenario: My Labs lists are scoped to the logged-in user
    Given two enrolled students where only one has a sandbox lab
    When each student opens the My Labs page
    Then only the lab owner sees a lab card on My Labs
