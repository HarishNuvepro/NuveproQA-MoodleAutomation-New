# Assessment — Problem statement using Git URL. Mirrors the 'Problem statement using Git URL' category in
# docs/MoodleRegression_Testing_Kiwi.xlsx (Moodle_Assessment_Labs). Only problem_statement_git_url cases live here.
# Run one: .\mvnw.cmd test -Pwatch "-Dcucumber.tags=@A1"  (use the scenario tag)

Feature: Assessment — Problem statement using Git URL

  @A215
  Scenario: The git URL is accepted and displayed
    Then this case needs specific git repository content or a learner attempt

  @A216
  Scenario: The problem statement is not randomized for a plain git activity
    Then this case needs specific git repository content or a learner attempt

  @A217
  Scenario: The assessment instructions editor is present
    Given a new challenge activity form is open
    Then the assessment instructions editor is present

  @A218
  Scenario: The github url example is shown on the challenge form
    Given a new assessment challenge activity form for structural checks
    Then the problem statement git url example is shown on the form

  @A219
  Scenario: The problem statement is not visible to the user
    Then this case needs specific git repository content or a learner attempt

  @A220
  Scenario: Problem statement support for trainer and admins in user attempts
    Then this case needs specific git repository content or a learner attempt

  @A221
  Scenario: Without a git URL the admin cannot view the problem statement
    Then this case needs specific git repository content or a learner attempt

  @A222
  Scenario: All user attempts and submissions display under user attempts
    Then this case needs specific git repository content or a learner attempt

  @A223
  Scenario: A random problem statement is generated per learner
    Then this case needs specific git repository content or a learner attempt

  @A224
  Scenario: Trainers and admins can view all randomized mappings
    Then this case needs specific git repository content or a learner attempt

  @A225
  Scenario: Randomization enabled with a missing required field is rejected
    Given a new challenge activity form is open
    When randomization is enabled and the form is saved with a missing required field
    Then a required-field validation error is shown

  @A226
  Scenario: Repository markdown and text files render as problem statements
    Then this case needs specific git repository content or a learner attempt

  @A227
  Scenario: An updated GitHub file reflects in Moodle
    Then this case needs specific git repository content or a learner attempt

  @A228
  Scenario: An invalid GitHub file leaves the problem statement empty
    Then this case needs specific git repository content or a learner attempt

  @A230
  Scenario: The same problem statement appears across submissions of one attempt
    Then this case needs specific git repository content or a learner attempt

  @A231
  Scenario: The git token is not completely visible
    Then this case needs specific git repository content or a learner attempt
