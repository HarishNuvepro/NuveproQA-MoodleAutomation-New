# Assessment — admin lab / submissions actions only.
# Student console cases live in assessment_user_functionality.feature.
#   .\mvnw.cmd test -Pwatch "-Dcucumber.tags=@A10"

Feature: Assessment — admin lab actions

  @A10
  Scenario: The manage labs page lists labs
    Then the manage labs page lists labs

  @A13
  Scenario: When the assessment completes the activity page reflects it
    Then this lifecycle case needs a running or completed assessment lab

  @A14
  Scenario: Perform submit/stop/start/delete lab actions
    Then this lifecycle case needs a running or completed assessment lab

  @A17
  Scenario: Cost/duration/budget settings on a running lab
    Then this lifecycle case needs a running or completed assessment lab

  @A11
  Scenario: Manage challenges lists enrolled users with attempt columns
    When admin opens manage challenges
    Then the manage challenges table lists users with attempt columns

  @A20
  Scenario: A not-started user is listed on the manage user assessment page
    Then the manage user assessment page lists a user who has not started the assessment

  @A33
  Scenario: Admin can add extra attempts and duration on reopen
    Given the reopen override form is open for a completed user
    Then the reopen offers extra attempts and duration controls

  @A124
  Scenario: Search works in the submissions summary
    Given the manage user submissions page is open
    Then the submissions page has a search control

  @A126
  Scenario: The user attempts drill-down has a working search control
    Given the user attempts drill-down is open
    Then the user attempts page has a working search control

  @A129
  Scenario: The updated comment control is present in manage user submissions
    Given the manage user submissions page is open
    Then the comment action control is available on the submissions page
