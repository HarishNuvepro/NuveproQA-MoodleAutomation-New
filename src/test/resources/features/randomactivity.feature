# Randomization Activity (mod_randomactivity) — batch 1. Add-form fields, name validation
# (accepts alphanumeric+special; required), and create-and-delete (a real activity is created then
# removed via UI in @After, since the WS token lacks core_course_delete_modules).
# Run: mvnw test -Dcucumber.tags="@randomactivity"
# Medium batch 1 slice: mvnw test -Pwatch "-Dcucumber.tags=@mediumbatch1 and not @skip"

Feature: Randomization Activity

  @RA-form @CGAP-RAND-3 @mediumbatch1 @randomactivity
  Scenario: The randomization activity add form renders its fields
    When admin opens a new randomization activity form
    Then the randomization activity form shows the name and its options

  @RA-name @mediumbatch1 @randomactivity
  Scenario: The activity name accepts alphanumeric and special characters
    When admin opens a new randomization activity form
    Then the activity name field accepts alphanumeric and special characters

  @RA-required @CGAP-RAND-1 @mediumbatch1 @randomactivity
  Scenario: The activity name is required
    When admin opens a new randomization activity form
    Then saving without a name is blocked by the required validation

  @RA-create @mediumbatch1 @randomactivity
  Scenario: Admin can add a randomization activity
    When admin opens a new randomization activity form
    Then admin can create a randomization activity and it appears in the course

  @RA-dupnames @CGAP-RAND-2 @mediumbatch1 @randomactivity
  Scenario: Duplicate randomization activity names are allowed in the same course
    When admin opens a new randomization activity form
    And admin creates a randomization activity named "DupRandShared"
    And admin opens a new randomization activity form
    And admin creates a randomization activity named "DupRandShared"
    Then the course lists 2 activities named "DupRandShared"
