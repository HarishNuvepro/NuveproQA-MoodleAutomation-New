# Assessment — Assessment reopening / override. Mirrors the 'Assessment reopening / override' category in
# docs/MoodleRegression_Testing_Kiwi.xlsx (Moodle_Assessment_Labs). Only reopening cases live here.
# Run one: .\mvnw.cmd test -Pwatch "-Dcucumber.tags=@A1"  (use the scenario tag)

Feature: Assessment — Assessment reopening / override

  @A24
  Scenario: Admin has access to reopen a completed assessment
    Given the reopen override form is open for a completed user
    Then the reopen offers a submissions control

  @A25
  Scenario: Admin can reopen when attempts are completed
    Given the reopen override form is open for a completed user
    Then the reopen offers a submissions control

  @A26
  Scenario: Admin can reopen when duration is completed
    Given the reopen override form is open for a completed user
    Then the reopen offers a duration control

  @A27
  Scenario: A completed assessment can be reopened via override
    Given an override-eligible completed user on the assessment
    Then the assessment can be reopened by overriding submissions

  @A28
  Scenario: Admin can reopen when the enrolment end date is completed
    Then this reopen case needs a live completed attempt to re-attempt

  @A29
  Scenario: Admin can reopen by giving a date range
    Given the reopen override form is open for a completed user
    Then the reopen offers a start and due date range

  @A30
  Scenario: Admin can reopen for multiple users
    Then this reopen case needs a live completed attempt to re-attempt

  @A31
  Scenario: Admin can reopen an assessment already reopened
    Then this reopen case needs a live completed attempt to re-attempt

  @A32
  Scenario: A reopened assessment lets the user attempt again
    Then this reopen case needs a live completed attempt to re-attempt

  @A34
  Scenario: Manage challenges has a completion status column
    Then the manage challenges table has a completion status column

  @A35
  Scenario: Reopen when both duration and attempts are completed
    Then this reopen case needs a live completed attempt to re-attempt

  @A36
  Scenario: Reopen when a submission was in progress
    Then this reopen case needs a live completed attempt to re-attempt

  @A37
  Scenario: Reopening grants an extra attempt via override
    Given an override-eligible completed user on the assessment
    Then the assessment can be reopened by overriding submissions

  @A38
  Scenario: The reopen form exposes the values the admin can change
    Given the reopen override form is open for a completed user
    Then the reopen offers extra attempts and duration controls

  @A39
  Scenario: Admin can modify the reopen time and attempts
    Given the reopen override form is open for a completed user
    Then the reopen offers extra attempts and duration controls

  @A40
  Scenario: Admin can delete the new attempt added by reopen
    Then this reopen case needs a live completed attempt to re-attempt

  @A41
  Scenario: Reopen after un-enrol and re-enrol
    Then this reopen case needs a live completed attempt to re-attempt

  @A42
  Scenario: Admin can add a comment when reopening
    Given the reopen override form is open for a completed user
    Then the reopen offers a comment field

  @A43
  Scenario: Admin can clear all submissions when reopening
    Then this reopen case needs a live completed attempt to re-attempt

  @A44
  Scenario: Admin clears all attempts of a user
    Then this cooldown case needs a live attempt or cooldown state

  @A45
  Scenario: Admin can override a user's submissions and duration to unlimited
    Given the assessment manage challenges page is open
    When admin opens the override form for the first enrolled user
    Then the submissions and duration can be set to unlimited

  @A46
  Scenario: Admin increases the number of attempts
    Then this cooldown case needs a live attempt or cooldown state

  @A47
  Scenario: Admin can override submissions to fewer than the global setting
    Given the override form is open for a completed user
    When admin sets the override submissions to "1"
    Then the override submissions value is "1"

  @A48
  Scenario: Activity does not complete automatically before due
    Then this cooldown case needs a live attempt or cooldown state

  @A49
  Scenario: Admin can override the duration to more than the global setting
    Given the override form is open for a completed user
    When admin sets the override duration to 999 hours
    Then the override duration is 999 hours

  @A50
  Scenario: An overridden user gets extra attempts
    Given an override-eligible completed user on the assessment
    Then the assessment can be reopened by overriding submissions

  @A51
  Scenario: Attempted assessments are removed when the user is removed
    Then this cooldown case needs a live attempt or cooldown state

  @A52
  Scenario: The override form offers a start and due date range
    Given the override form is open for a completed user
    Then the override form offers a start and due date range

  @A53
  Scenario: Admin can delete an override
    Then an assessment override can be deleted when its user is eligible

  @A54
  Scenario: Admin can delete an override after extra attempts are used
    Then an assessment override can be deleted when its user is eligible

  @A-override
  Scenario: The override, report and user-attempt controls are present
    When admin opens manage challenges
    Then the override report and user-attempt controls are present
