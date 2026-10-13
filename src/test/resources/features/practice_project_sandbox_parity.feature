# Practice Project — scenarios ported from Sandbox Yes cases not previously on Practice_Project.
# Console cases reuse @guidedlab student session; Manage Labs cases use @practice_project_manage.
#   .\mvnw.cmd test -Pslow -Pwatch "-Dcucumber.features=src/test/resources/features/practice_project_sandbox_parity.feature"

Feature: Practice Project sandbox parity

  @CGAP-PP-7 @practice_project @guidedlab @provisioning
  Scenario: Lab Usage shows cost values on the Practice Project console
    Given a student is enrolled in the course
    When the student starts the guided project
    And the guided lab reaches Running
    When the student opens the Lab Usage popup on the practice project console
    And the student requests the lab cost on the practice project console
    Then valid lab cost values are shown on the practice project console

  @CGAP-PP-8 @practice_project @guidedlab @provisioning
  Scenario: A saved note persists on the Practice Project lab console
    Given a student is enrolled in the course
    When the student starts the guided project
    And the guided lab reaches Running
    When the student saves a practice project lab note "PP parity note 901"
    Then the practice project lab note "PP parity note 901" persists after reopening the console

  @CGAP-PP-9 @practice_project @guidedlab @provisioning
  Scenario: Refresh keeps the Practice Project lab running
    Given a student is enrolled in the course
    When the student starts the guided project
    And the guided lab reaches Running
    When the student refreshes the practice project lab on the console
    Then the practice project lab remains running after refresh

  @CGAP-PP-10 @practice_project @guidedlab @provisioning
  Scenario: Support tab mailto works on the Practice Project console
    Given a student is enrolled in the course
    When the student starts the guided project
    And the guided lab reaches Running
    Then the practice project support link on the console is available

  @CGAP-PP-11 @practice_project @guidedlab @provisioning
  Scenario: Console Back returns to the Practice Project landing
    Given a student is enrolled in the course
    When the student starts the guided project
    And the guided lab reaches Running
    Then the practice project console Back button returns to the landing

  @CGAP-PP-13 @practice_project @guidedlab @provisioning
  Scenario: Student can change the lab password on the Practice Project console
    Given a student is enrolled in the course
    When the student starts the guided project
    And the guided lab reaches Running
    When the student opens the Lab Credentials popup on the practice project console
    And the student changes the practice project lab password on the console
    Then the practice project lab password update is accepted

  @CGAP-PP-14 @practice_project @guidedlab @provisioning
  Scenario: Jump to Console opens Access Lab for Practice Project
    Given a student is enrolled in the course
    When the student starts the guided project
    And the guided lab reaches Running
    When the student opens Access Lab from Jump to Console on the practice project
    Then the student is redirected to the practice project cloud lab platform

  @CGAP-PP-15 @practice_project @guidedgaps
  Scenario: Canceling Start Practice Project does not create a lab
    Given a student is enrolled in the course
    When the student opens the guided activity
    When the student clicks Start Practice Project and cancels the confirmation
    Then no practice project lab was created for the student

  @CGAP-PP-16 @practice_project @guidedlab @provisioning
  Scenario: Console duration values align with CloudLabs for Practice Project
    Given a student is enrolled in the course
    When the student starts the guided project
    And the guided lab reaches Running
    Then allotted consumed and remaining duration on the practice project console match CloudLabs

  @CGAP-PP-17 @practice_project @practice_project_manage @provisioning
  Scenario: Admin syncs and stops a student-started Practice Project lab
    Given the practice project activity is ready for Manage Labs
    Given a fresh student enrolled for Practice Project Manage Labs admin actions
    When the student starts the practice project lab until it is running
    And admin opens Manage Labs and selects that student's lab
    And admin syncs lab status until Start is completed
    When admin stops that student's lab from Manage Labs
    And admin syncs lab status until Stop is completed
    Then the Manage Labs admin stop flow has completed successfully

  @CGAP-PP-18 @practice_project @practice_project_manage @provisioning
  Scenario: Admin creates a Practice Project lab from No labs
    Given the practice project activity is ready for Manage Labs
    Given a fresh student enrolled for Practice Project Manage Labs admin actions
    When admin opens Manage Labs No labs tab
    And admin creates a lab for that student from No labs
    Then that student is not listed on the No labs tab
    And that student appears on Active labs with Create completed

  @CGAP-PP-19 @practice_project @practice_project_manage @provisioning
  Scenario: Delete confirmation removes a Practice Project lab
    Given the practice project activity is ready for Manage Labs
    Given a fresh student enrolled for Practice Project Manage Labs admin actions
    When the student starts the practice project lab until it is running
    And admin opens Manage Labs and selects that student's lab
    When admin deletes that student's lab from Manage Labs
    And admin syncs lab status until Delete is completed
    Then that student's lab is Deleted

  @CGAP-PP-20 @practice_project @practice_project_manage @provisioning
  Scenario: A stopped Practice Project lab can be started again from Manage Labs
    Given the practice project activity is ready for Manage Labs
    Given a fresh student enrolled for Practice Project Manage Labs admin actions
    When the student starts the practice project lab until it is running
    And admin opens Manage Labs and selects that student's lab
    When admin stops that student's lab from Manage Labs
    And admin syncs lab status until Stop is completed
    When admin starts that student's lab from Manage Labs
    And admin syncs lab status until Start is completed
    Then the Manage Labs admin start flow has completed successfully

  @CGAP-PP-21 @practice_project @practice_project_manage
  Scenario: Manage Labs actions stay gated with no Practice Project lab row selected
    Given the practice project activity is ready for Manage Labs
    When admin opens Manage Labs Active labs tab
    Then Start Stop Delete and Sync are disabled with no lab selected
    When admin opens Manage Labs No labs tab
    Then No labs Create is disabled with no user selected

  @CGAP-PP-23 @practice_project @practice_project_manage
  Scenario: Manage Labs exposes Active and No labs chrome for Practice Project
    Given the practice project activity is ready for Manage Labs
    When admin opens Manage Labs Active labs tab
    Then the labs are listed with status counters
    And per-lab action controls are available
    And the not-created users tab offers Create but not lab actions
