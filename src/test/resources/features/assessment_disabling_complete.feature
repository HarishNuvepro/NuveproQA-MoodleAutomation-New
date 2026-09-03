# Assessment — Disabling complete assessment. Mirrors the 'Disabling complete assessment' category in
# docs/MoodleRegression_Testing_Kiwi.xlsx (Moodle_Assessment_Labs). Only disabling_complete cases live here.
# Run one: .\mvnw.cmd test -Pwatch "-Dcucumber.tags=@A1"  (use the scenario tag)

Feature: Assessment — Disabling complete assessment

  @A195
  Scenario: The complete button state when a submission is evaluated
    Then this case needs a live attempt, an integration session, or a course-management action

  @A202
  Scenario: The disabling-complete feature works for SSO/LTI users
    Then this case needs a live attempt, an integration session, or a course-management action
