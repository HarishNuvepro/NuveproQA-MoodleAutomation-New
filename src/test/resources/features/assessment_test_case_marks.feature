# Assessment — Test Case Marks (different marks per test case). Mirrors the 'Test Case Marks (different marks per test case)' category in
# docs/MoodleRegression_Testing_Kiwi.xlsx (Moodle_Assessment_Labs). Only test_case_marks cases live here.
# Run one: .\mvnw.cmd test -Pwatch "-Dcucumber.tags=@A1"  (use the scenario tag)

Feature: Assessment — Test Case Marks (different marks per test case)

  @A62
  Scenario: The different-marks-per-test-case option is on the challenge form
    Given a new assessment challenge activity form for structural checks
    Then the test-case different-marks option is available for auto evaluation

  @A63
  Scenario: Selecting "Different for each" selects that Test Case Marks option
    Given a challenge form with auto evaluation
    When I select the "Different for each" test case marks option
    Then the "Different for each" option is selected and the per-test-case marks field is hidden

  @A64
  Scenario: Selecting "Same for all" unselects "Different for each"
    Given a challenge form with auto evaluation
    When I select the "Different for each" test case marks option
    And I select the "Same for all" test case marks option
    Then the "Different for each" option is no longer selected and the per-test-case marks field is shown

  @A81
  Scenario: Changing Test Case Marks from "Same for all" to "Different for each"
    Given a challenge form with auto evaluation
    Then the "Same for all" option is selected by default and the per-test-case marks field is shown
    When I select the "Different for each" test case marks option
    Then the "Different for each" option is selected and the per-test-case marks field is hidden
