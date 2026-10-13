# Assessment — sandbox Yes cases ported before Practice Project parity (same mechanics, labtype=challenge).
# Uses ASSESSMENT_LIFECYCLE_CMID (or ASSESSMENT_CMID) + CloudLabs for @provisioning.
#   .\mvnw.cmd test -Pslow -Pwatch "-Dcucumber.features=src/test/resources/features/assessment_sandbox_parity.feature"

Feature: Assessment sandbox parity

  @CGAP-A-SB-7 @assessment_sandbox_parity @assessment_manage @provisioning
  Scenario: Lab Usage shows cost values on the assessment console
    Given the assessment activity is ready for Manage Labs
    And a fresh student enrolled for Assessment Manage Labs admin actions
    When the student starts the assessment lab until it is running
    And the student opens the Lab Usage popup on the assessment console
    And the student requests the lab cost on the assessment console
    Then valid lab cost values are shown on the assessment console

  @CGAP-A-SB-8 @assessment_sandbox_parity @assessment_manage @provisioning
  Scenario: A saved note persists on the assessment lab console
    Given the assessment activity is ready for Manage Labs
    And a fresh student enrolled for Assessment Manage Labs admin actions
    When the student starts the assessment lab until it is running
    When the student saves an assessment lab note "ASMT parity note 901"
    Then the assessment lab note "ASMT parity note 901" persists after reopening the console

  @CGAP-A-SB-9 @assessment_sandbox_parity @assessment_manage @provisioning
  Scenario: Refresh keeps the assessment lab running
    Given the assessment activity is ready for Manage Labs
    And a fresh student enrolled for Assessment Manage Labs admin actions
    When the student starts the assessment lab until it is running
    When the student refreshes the assessment lab on the console
    Then the assessment lab remains running after refresh

  @CGAP-A-SB-10 @assessment_sandbox_parity @assessment_manage @provisioning
  Scenario: Support tab mailto works on the assessment console
    Given the assessment activity is ready for Manage Labs
    And a fresh student enrolled for Assessment Manage Labs admin actions
    When the student starts the assessment lab until it is running
    Then the assessment support link on the console is available

  @CGAP-A-SB-11 @assessment_sandbox_parity @assessment_manage @provisioning
  Scenario: Console Back returns to the assessment landing
    Given the assessment activity is ready for Manage Labs
    And a fresh student enrolled for Assessment Manage Labs admin actions
    When the student starts the assessment lab until it is running
    Then the assessment console Back button returns to the landing

  @CGAP-A-SB-13 @assessment_sandbox_parity @assessment_manage @provisioning
  Scenario: Student can change the lab password on the assessment console
    Given the assessment activity is ready for Manage Labs
    And a fresh student enrolled for Assessment Manage Labs admin actions
    When the student starts the assessment lab until it is running
    When the student opens the Lab Credentials popup on the assessment console
    And the student changes the assessment lab password on the console
    Then the assessment lab password update is accepted

  @CGAP-A-SB-14 @assessment_sandbox_parity @assessment_manage @provisioning
  Scenario: Jump to Console opens Access Lab for assessment
    Given the assessment activity is ready for Manage Labs
    And a fresh student enrolled for Assessment Manage Labs admin actions
    When the student starts the assessment lab until it is running
    When the student opens Access Lab from Jump to Console on the assessment
    Then the student is redirected to the assessment cloud lab platform

  @CGAP-A-SB-15 @assessment_sandbox_parity @assessment_manage
  Scenario: Canceling Start Assessment does not create a lab
    Given the assessment activity is ready for Manage Labs
    And a fresh student enrolled for Assessment Manage Labs admin actions
    When the student clicks Start Assessment and cancels the confirmation
    Then no assessment lab was created for the student

  @CGAP-A-SB-16 @assessment_sandbox_parity @assessment_manage @provisioning
  Scenario: Console duration values align with CloudLabs for assessment
    Given the assessment activity is ready for Manage Labs
    And a fresh student enrolled for Assessment Manage Labs admin actions
    When the student starts the assessment lab until it is running
    Then allotted consumed and remaining duration on the assessment console match CloudLabs

  @CGAP-A-SB-17 @assessment_sandbox_parity @assessment_manage @provisioning
  Scenario: Admin syncs and stops a student-started assessment lab
    Given the assessment activity is ready for Manage Labs
    And a fresh student enrolled for Assessment Manage Labs admin actions
    When the student starts the assessment lab until it is running
    And admin opens Manage Labs and selects that student's lab
    And admin syncs lab status until Start is completed
    When admin stops that student's lab from Manage Labs
    And admin syncs lab status until Stop is completed
    Then the Manage Labs admin stop flow has completed successfully

  @CGAP-A-SB-18 @assessment_sandbox_parity @assessment_manage @provisioning
  Scenario: Admin creates an assessment lab from No labs
    Given the assessment activity is ready for Manage Labs
    And a fresh student enrolled for Assessment Manage Labs admin actions
    When admin opens Manage Labs No labs tab
    And admin creates a lab for that student from No labs
    Then that student is not listed on the No labs tab
    And that student appears on Active labs with Create completed

  @CGAP-A-SB-19 @assessment_sandbox_parity @assessment_manage @provisioning
  Scenario: Delete confirmation removes an assessment lab
    Given the assessment activity is ready for Manage Labs
    And a fresh student enrolled for Assessment Manage Labs admin actions
    When the student starts the assessment lab until it is running
    And admin opens Manage Labs and selects that student's lab
    When admin deletes that student's lab from Manage Labs
    And admin syncs lab status until Delete is completed
    Then that student's lab is Deleted

  @CGAP-A-SB-20 @assessment_sandbox_parity @assessment_manage @provisioning
  Scenario: A stopped assessment lab can be started again from Manage Labs
    Given the assessment activity is ready for Manage Labs
    And a fresh student enrolled for Assessment Manage Labs admin actions
    When the student starts the assessment lab until it is running
    And admin opens Manage Labs and selects that student's lab
    When admin stops that student's lab from Manage Labs
    And admin syncs lab status until Stop is completed
    When admin starts that student's lab from Manage Labs
    And admin syncs lab status until Start is completed
    Then the Manage Labs admin start flow has completed successfully

  @CGAP-A-SB-21 @assessment_sandbox_parity @assessment_manage
  Scenario: Manage Labs actions stay gated with no assessment lab row selected
    Given the assessment activity is ready for Manage Labs
    When admin opens Manage Labs Active labs tab
    Then Start Stop Delete and Sync are disabled with no lab selected
    When admin opens Manage Labs No labs tab
    Then No labs Create is disabled with no user selected

  @CGAP-A-SB-23 @assessment_sandbox_parity @assessment_manage
  Scenario: Manage Labs exposes Active and No labs chrome for assessment
    Given the assessment activity is ready for Manage Labs
    When admin opens Manage Labs Active labs tab
    Then the labs are listed with status counters
    And per-lab action controls are available
    And the not-created users tab offers Create but not lab actions

  @CGAP-A-SB-26 @assessment_sandbox_parity @assessment_manage @provisioning
  Scenario: Continue resumes the assessment lab console after Start
    Given the assessment activity is ready for Manage Labs
    And a fresh student enrolled for Assessment Manage Labs admin actions
    When the student starts the assessment lab until it is running
    When the student returns to the assessment landing and continues the in-progress attempt
    Then the assessment lab console is open after Continue

  @CGAP-A-SB-27 @assessment_sandbox_parity @fresh_activity
  Scenario: Read More opens the full assessment description on the landing
    When admin creates an assessment activity using "rich_content"
    And a student is enrolled in the created assessment course
    When the student opens the created assessment activity
    When the student opens Read More on the assessment landing
    Then the Read More modal shows the full assessment description

  @CGAP-A-SB-28 @assessment_sandbox_parity @fresh_activity
  Scenario: Fresh student landing shows Start Assessment only before any lab
    When admin creates an assessment activity using "eval_auto"
    And a student is enrolled in the created assessment course
    When the student opens the created assessment activity
    Then the created assessment landing is visible for the student
    And the fresh assessment landing shows Start Assessment only without Continue
