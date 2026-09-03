# Guided (practice project) — derived gap cases, no-provisioning batch:
# CGAP-PGG-13/14 (student role split), CGAP-PGG-16/17 (config toggles), CGAP-PGG-15 (date validation).
# Student cases seed a fresh user via WS (deleted at teardown). The date-validation case runs on the
# existing guided activity's edit form — the validation error means NOTHING is saved (safe).
# Run: mvnw test -Dcucumber.tags="@guidedgaps"

Feature: Guided labs - derived gap cases

  @CGAP-PGG-13 @guidedgaps @practice_project
  Scenario: A student does not see the guided admin manage buttons
    Given a student is enrolled in the course
    When the student opens the guided activity
    Then the student does not see the guided admin manage buttons

  @CGAP-PGG-14 @guidedgaps @practice_project
  Scenario: A student cannot open the User Guided Project admin page
    Given a student is enrolled in the course
    When the student opens the guided activity
    Then the student cannot open the User Guided Project admin page

  @CGAP-PGG-16 @guidedgaps @practice_project
  Scenario: Progress tracking toggles the progress-check URL field
    When I open a new guided activity form
    Then toggling progress tracking shows and hides the progress-check URL field

  @CGAP-PGG-17 @guidedgaps @practice_project
  Scenario: The guide content source toggles the git URL field
    When I open a new guided activity form
    Then switching the guide content source toggles the git URL field

  @CGAP-PGG-15 @guidedgaps @practice_project
  Scenario: A due date before the start date is rejected
    When admin sets the guided due date before the start date
    Then the due-date validation error is shown
