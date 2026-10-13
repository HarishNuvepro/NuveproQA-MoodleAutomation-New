# Assessment — Hide final grade & result. Mirrors the 'Hide final grade & result' category in
# docs/MoodleRegression_Testing_Kiwi.xlsx (Moodle_Assessment_Labs). Only hide_final_grade cases live here.
# Run one: .\mvnw.cmd test -Pwatch "-Dcucumber.tags=@A1"  (use the scenario tag)

Feature: Assessment — Hide final grade & result

  @A130
  Scenario: Admin can see the grade and result columns
    Then admin can see the grade and result columns on manage challenges

  @A131
  Scenario: A manager can see the grade and result columns
    Then a manager can see the grade and result columns on manage challenges

  @A132
  Scenario: A teacher can see the grade and result columns
    Then a teacher can see the grade and result columns on manage challenges

  @A135 @mediumbatch1
  Scenario: The Final Result column control is available
    Given the assessment activity settings form is open as admin
    Then the "FinalResult" attempt-summary column control is available

  @A136
  Scenario: The Evaluation Result column control is available
    Given the assessment activity settings form is open as admin
    Then the "EvaluationResult" attempt-summary column control is available

  @A137
  Scenario: The Evaluation Result column can be hidden
    Given the assessment activity settings form is open as admin
    Then the "EvaluationResult" attempt-summary column can be hidden

  @A138
  Scenario: The Final Result column can be hidden
    Given the assessment activity settings form is open as admin
    Then the "FinalResult" attempt-summary column can be hidden

  @A139
  Scenario: The Enable-on-Admin-view control is available
    Given the assessment activity settings form is open as admin
    Then the "adminConfiguration" attempt-summary column control is available
