# Assessment — Assessment Retake Cooldown. Mirrors the 'Assessment Retake Cooldown' category in
# docs/MoodleRegression_Testing_Kiwi.xlsx (Moodle_Assessment_Labs). Only retake_cooldown cases live here.
# Run one: .\mvnw.cmd test -Pwatch "-Dcucumber.tags=@A1"  (use the scenario tag)

Feature: Assessment — Assessment Retake Cooldown

  @A146
  Scenario: The attempts allowed field is present on the form
    Given a challenge activity form for cooldown checks
    Then the attempts allowed field is on the form

  @A147
  Scenario: The retakes field is present on the form
    Given a challenge activity form for cooldown checks
    Then the attempts allowed field is on the form

  @A148
  Scenario: The cooldown period field is on the challenge form
    Given a new assessment challenge activity form for structural checks
    Then the cooldown period field is available in the challenge form

  @A149
  Scenario: Attempts after the cooldown period can be set
    Given a challenge activity form for cooldown checks
    Then setting a cooldown period shows the attempts-after-cooldown field

  @A150
  Scenario: Cooldown set to None hides attempts-after-cooldown
    Given a challenge activity form for cooldown checks
    Then setting cooldown to None hides the attempts-after-cooldown field

  @A151
  Scenario: Cooldown set to None keeps the cooldown field present
    Given a challenge activity form for cooldown checks
    Then the cooldown period field is on the form

  @A152
  Scenario: Cooldown and attempts-after-cooldown fields coexist
    Given a challenge activity form for cooldown checks
    Then setting a cooldown period shows the attempts-after-cooldown field

  @A153
  Scenario: Manage challenges shows the attempts column
    Then manage challenges has the attempts column

  @A154
  Scenario: Manage challenges shows the attempts-taken column
    Then manage challenges has the attempts column

  @A155
  Scenario: User activity page retakes allowed reflects config
    Then this cooldown case needs a live attempt or cooldown state

  @A156
  Scenario: User activity page retakes taken reflects config
    Then this cooldown case needs a live attempt or cooldown state

  @A157
  Scenario: The attempts-after-cooldown field is available
    Given a challenge activity form for cooldown checks
    Then setting a cooldown period shows the attempts-after-cooldown field

  @A158
  Scenario: The cooldown period field is displayed
    Given a challenge activity form for cooldown checks
    Then the cooldown period field is on the form

  @A159
  Scenario: The retake button appears when final result is available
    Then this cooldown case needs a live attempt or cooldown state

  @A160
  Scenario: The cooldown-ends message is displayed
    Then this cooldown case needs a live attempt or cooldown state

  @A161
  Scenario: The cooldown-ends day is calculated
    Then this cooldown case needs a live attempt or cooldown state

  @A162
  Scenario: The cooldown-ends time is displayed
    Then this cooldown case needs a live attempt or cooldown state

  @A163
  Scenario: After cooldown the retake attempt is allowed
    Then this cooldown case needs a live attempt or cooldown state

  @A164
  Scenario: After cooldown the retake button enables an attempt
    Then this cooldown case needs a live attempt or cooldown state

  @A165
  Scenario: After cooldown a new attempt is created
    Then this cooldown case needs a live attempt or cooldown state

  @A166
  Scenario: After cooldown the cooldown resets
    Then this cooldown case needs a live attempt or cooldown state

  @A167
  Scenario: After cooldown a new lab is created
    Then this cooldown case needs a live attempt or cooldown state

  @A168
  Scenario: The attempts column is added to the user assessment page
    Then manage challenges has the attempts column

  @A169
  Scenario: The attempts column shows attempts taken vs total
    Then manage challenges has the attempts column

  @A170
  Scenario: The user assessment page attempts term is updated
    Then this cooldown case needs a live attempt or cooldown state

  @A171
  Scenario: The submissions column is present
    Then manage challenges has the submissions column

  @A172
  Scenario: A new attempt number column is present
    Then manage challenges has the completionstatus column

  @A173
  Scenario: The attempt number column displays per submission
    Then this cooldown case needs a live attempt or cooldown state

  @A174
  Scenario: All attempts terminology is changed to submissions
    Then this cooldown case needs a live attempt or cooldown state

  @A175
  Scenario: User attempts page total attempts changes
    Then this cooldown case needs a live attempt or cooldown state

  @A176
  Scenario: User override page attempts become submissions
    Then this cooldown case needs a live attempt or cooldown state

  @A177
  Scenario: If retakes are available the retake button shows
    Then this cooldown case needs a live attempt or cooldown state

  @A178
  Scenario: If final result is pass the retake button shows
    Then this cooldown case needs a live attempt or cooldown state

  @A179
  Scenario: User submission page submit attempt becomes submit
    Then this cooldown case needs a live attempt or cooldown state

  @A180
  Scenario: Submit popup terminology is changed
    Then this cooldown case needs a live attempt or cooldown state

  @A181
  Scenario: Delete popup terminology is changed
    Then this cooldown case needs a live attempt or cooldown state

  @A182
  Scenario: Jump to console shows for all retake attempts
    Then this cooldown case needs a live attempt or cooldown state

  @A183
  Scenario: Jump to console shows for all override attempts
    Then this cooldown case needs a live attempt or cooldown state

  @A184
  Scenario: The completing-attempt popup message is shown
    Then this cooldown case needs a live attempt or cooldown state

  @A185
  Scenario: The global-attempt cron task runs without error
    When the cloudlabs scheduled tasks are run
    Then the cron run completes without error

  @A186
  Scenario: The retake-attempt cron task runs without error
    When the cloudlabs scheduled tasks are run
    Then the cron run completes without error

  @A187
  Scenario: The retake-after-cooldown cron task runs without error
    When the cloudlabs scheduled tasks are run
    Then the cron run completes without error

  @A188
  Scenario: The override-attempt cron task runs without error
    When the cloudlabs scheduled tasks are run
    Then the cron run completes without error

  @A189
  Scenario: Cooldown details show only during cooldown
    Then this cooldown case needs a live attempt or cooldown state

  @A190
  Scenario: Cooldown details are hidden outside cooldown
    Then this cooldown case needs a live attempt or cooldown state
