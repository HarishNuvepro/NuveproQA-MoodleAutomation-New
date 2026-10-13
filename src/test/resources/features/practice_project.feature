# Practice Project — create / add-activity form (includes former guided_gaps form cases).
# One tag per scenario — the same @PP-* / @CGAP-PGG-* as Practice_Project Automation_Ref.
#   .\mvnw.cmd test -Pwatch "-Dcucumber.tags=@PP-2"
#   .\mvnw.cmd test -Pwatch "-Dcucumber.features=src/test/resources/features/practice_project.feature"

Feature: Practice Project create activity

  @PP-2 @PP-23 @PP-28 @PP-38 @PP-136 @PP-140 @easybatch2
  Scenario: The add-activity form exposes Practice Project type, dates, stop, cooldown and attempts
    When I open a new guided activity form
    Then the Practice Project lab type is selectable
    And the practice project type, time limit, submission, stop and cooldown fields are present
    And switching the guide content source toggles the git URL field
    And allowed attempts can be set to 1 and 100
    And duration between attempts is not shown for a playground activity

  @PP-15 @PP-24 @PP-25 @PP-30 @easybatch2
  Scenario: Admin can configure progress tracking, time units, attempts and lab details
    When I open a new guided activity form
    Then with and without progress tracking can be selected
    And the progress-check URL is shown only for with progress tracking
    And the time limit can be set in minutes hours days and seconds
    And allowed attempts can be set to 1 and 100
    And the Lab details configuration section is present

  @CGAP-PGG-15 @practice_project
  Scenario: A due date before the start date is rejected
    When admin sets the guided due date before the start date
    Then the due-date validation error is shown
