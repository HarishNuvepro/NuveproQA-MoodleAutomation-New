# Assessment — User Attempts page. Mirrors the 'User Attempts page' category in
# docs/MoodleRegression_Testing_Kiwi.xlsx (Moodle_Assessment_Labs). Only user_attempts_page cases live here.
# Run one: .\mvnw.cmd test -Pwatch "-Dcucumber.tags=@A1"  (use the scenario tag)

Feature: Assessment — User Attempts page

  @A9
  Scenario: The manage user assessment page lists users
    Then the manage user assessment page lists enrolled users

  @A102
  Scenario: The add-comment control is available in manage user submissions
    Given the manage user submissions page is open
    Then the comment action control is available on the submissions page

  @A103
  Scenario: The update-grades control is available in manage user submissions
    Given the manage user submissions page is open
    Then the grade action control is available on the submissions page

  @A104
  Scenario: The submit-submission control is available in manage user submissions
    Given the manage user submissions page is open
    Then the submit action control is available on the submissions page

  @A105
  Scenario: The delete-attempt control is available in manage user submissions
    Given the manage user submissions page is open
    Then the delete action control is available on the submissions page

  @A106
  Scenario: The user attempts drill-down shows the problem statement control
    Given the user attempts drill-down is open
    Then the user attempts page shows the problem statement control

  @A115
  Scenario: Admin can manually update the grade via manage user submissions
    Given the manage user submissions page is open
    Then the grade action control is available on the submissions page

  @A116
  Scenario: Admin can delete submissions via manage user submissions
    Given the manage user submissions page is open
    Then the delete action control is available on the submissions page

  @A117
  Scenario: Admin can update the comment via manage user submissions
    Given the manage user submissions page is open
    Then the comment action control is available on the submissions page

  @A118
  Scenario: The evaluation result control is available in manage user submissions
    Given the manage user submissions page is open
    Then the eval action control is available on the submissions page

  @A119
  Scenario: Search by status works in manage user submissions
    Given the manage user submissions page is open
    Then the submissions page has a search control

  @A120
  Scenario: Admin can start a lab action in manage user submissions
    Then this case needs a live attempt on the randomization assessment

  @A121
  Scenario: Admin can stop a lab action in manage user submissions
    Then this case needs a live attempt on the randomization assessment

  @A122
  Scenario: Admin can delete a lab action in manage user submissions
    Then this case needs a live attempt on the randomization assessment

  @A123
  Scenario: An incorrect script shows a lab error in user submissions
    Then this case needs a live attempt on the randomization assessment

  @A125
  Scenario: Admin can open the user console with credentials
    Then this case needs a live attempt on the randomization assessment

  @A-userattempts
  Scenario: Admin can open a user's attempts drill-down
    When admin opens manage challenges
    Then admin can open the user attempts drill-down
