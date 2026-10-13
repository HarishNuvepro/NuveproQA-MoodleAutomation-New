# Pre-Delivery Lab Readiness Checklist (lab_readiness.php) — batch 1, no provisioning.
# Role gate (cap mod/cloudlabs:viewlabreadiness — admin yes, student no), checklist progress bar
# (100% -> green bg-success), env-type VM/Account field toggle, mandatory support-engineer validation
# (alert, blocks save -> nothing persists). NONE of these do a SUCCESSFUL save, so the shared
# activity (ASSESSMENT_CMID) is never mutated. Run: mvnw test -Dcucumber.tags="@predelivery"

Feature: Pre-Delivery Lab Readiness Checklist

  @PDL-role @predelivery
  Scenario: The Lab Readiness page is admin-only
    When admin opens the lab readiness page
    Then the lab readiness checklist form is shown
    And a student cannot open the lab readiness page

  @PDL-progress @predelivery
  Scenario: Completing all checklist items drives the progress bar to 100% green
    When admin opens the lab readiness page
    Then checking all visible checklist items sets the progress bar to 100 percent and green

  @PDL-envtype @predelivery
  Scenario: The environment type toggles the VM and Account fields
    When admin opens the lab readiness page
    Then switching the environment type toggles the VM and Account fields

  @PDL-validation @predelivery
  Scenario: Saving without a support engineer name is rejected
    When admin opens the lab readiness page
    Then saving with an empty support engineer name is blocked by an alert

  @CGAP-PDL-1 @predelivery
  Scenario: Progress bar colour reflects completion bands
    When admin opens the lab readiness page
    Then the lab readiness progress bar shows danger warning info and success bands

  @CGAP-PDL-2 @predelivery
  Scenario: Environment type change recomputes checklist completion percentage
    When admin opens the lab readiness page
    Then switching environment type recomputes the checklist completion percentage

  @CGAP-PDL-3 @predelivery
  Scenario: Saving without a reference screenshot is rejected
    When admin opens the lab readiness page
    Then saving with support name but no screenshot is blocked by an alert

  @PDL-sections @predelivery
  Scenario: Catalog Orgs and Moodle checklist sections are displayed
    When admin opens the lab readiness page
    Then the lab readiness page shows Catalog Orgs and Moodle sections

  @PDL-catalog @predelivery
  Scenario: Catalog checklist items can be checked and unchecked
    When admin opens the lab readiness page
    Then catalog checklist items are present and toggleable

  @PDL-student-tab @PDL-26 @predelivery
  Scenario: Students do not see the Lab Readiness navigation on the activity
    When admin opens the lab readiness page
    And a student opens the same activity view page
    Then the student does not see the Lab Readiness navigation link

  @PDL-labtype @PDL-25 @predelivery
  Scenario: Checklist fields differ by lab type
    When admin opens the lab readiness page for the sandbox activity
    And admin opens the lab readiness page for the assessment activity
    Then the lab readiness checklist fields differ between sandbox and assessment lab types

  @PDL-guided @PDL-17 @predelivery
  Scenario: Practice project activities show guided checklist items
    When admin opens the lab readiness page for the practice project activity
    Then the lab readiness page shows practice project guided checklist items
