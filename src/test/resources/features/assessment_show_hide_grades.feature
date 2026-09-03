# Assessment — Show / hide grades of users. Mirrors the 'Show / hide grades of users' category in
# docs/MoodleRegression_Testing_Kiwi.xlsx (Moodle_Assessment_Labs). Only show_hide_grades cases live here.
# Run one: .\mvnw.cmd test -Pwatch "-Dcucumber.tags=@A1"  (use the scenario tag)

Feature: Assessment — Show / hide grades of users

  @A55
  Scenario: The assessment activity offers the Attempts Summary Columns hide configuration
    Given the assessment activity edit form is open
    Then the Attempts Summary Columns hide configuration is available

  @A56
  Scenario: With the hide config off the grade column is visible
    Then with the hide config off the grade column is visible to end users

  @A58
  Scenario: The result column can be hidden by the admin-view config
    Then the result column can be hidden from the attempt table by config

  @A59
  Scenario: Admin, manager and teacher can all manage the hide-columns configuration
    Then admin, manager and teacher can each open the attempt-summary configuration

  @A60
  Scenario: The admin-view config hides the grade column from the manage challenges table
    Then hiding the "Grade" column in admin config removes it from the manage challenges table

  @A61
  Scenario: A sandbox activity does not offer the Attempts Summary Columns configuration
    Given the sandbox activity edit form is open
    Then the Attempts Summary Columns hide configuration is not available
