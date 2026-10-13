# Practice Project — admin actions (overrides, User Attempts, activity reports, API).
# Site-level Moodle configurable reports stay in practice_project_reports.feature.
#   .\mvnw.cmd test -Pwatch "-Dcucumber.tags=@PP-113"
#   .\mvnw.cmd test -Pwatch "-Dcucumber.features=src/test/resources/features/practice_project_actions.feature"

Feature: Practice Project admin actions

  @PGG-6 @practice_project
  Scenario: Admin sees the User Labs and User Guided Project options
    Given I open the guided activity
    Then admin sees the guided manage buttons

  @PGG-9 @practice_project
  Scenario: The User Labs page lists the labs for the practice project activity
    Given I open the guided activity
    When I open the guided User Labs page
    Then the guided labs are listed

  @PP-113 @PGG-10 @PGG-12
  Scenario: Admin can open User Attempts and download the consolidated practice project report
  Scenario: Admin can open User Attempts and download the consolidated practice project report
    Given I open the guided activity
    When I open the User Guided Project page
    Then the reports dropdown has download and email options
    And the user attempts button is disabled until a participant is selected
    When admin selects a participant and opens User Attempts
    Then the User Attempts page shows the required header, table columns and report actions
    And the User Attempts check-details and submit-attempt buttons start disabled
    When admin downloads the consolidated practice project report
    Then the practice project report file is downloaded

  @PP-7
  Scenario: Admin can open the Manage Practice Project Overrides page
    Given I open the guided activity
    When admin opens the practice project Manage Overrides page
    Then the practice project overrides page is shown

  @CGAP-PP-3
  Scenario: Admin can send the practice project report by email
    Given I open the guided activity
    When I open the User Guided Project page
    Then admin can send the practice project report to a recipient

  @PP-49 @practice_project_override
  Scenario: Admin manages unlimited and finite Practice Project overrides
    Given admin selects an eligible Practice Project override user
    When admin creates an unlimited Practice Project override
    Then the override lists unlimited attempts and duration
    And another participant's allowed attempts remain unchanged
    When admin changes the override to finite attempts and duration
    Then the override lists the finite attempts and duration
    And the override status is shown for the user
    And the downloadable report contains the overridden participant
    When admin deletes the Practice Project override
    Then the Practice Project override is removed
    When admin re-overrides the completed Practice Project user
    Then the recreated Practice Project override is listed
    When admin deletes the Practice Project override
    Then the Practice Project override is removed

  @PP-59 @skip
  Scenario: Practice Project API returns renamed project configuration fields
    Given I open the guided activity
    When admin requests the Practice Project API response
    Then the API uses Practice Project names and returns dates time limit and attempts

  @PP-63 @practice_project_override @skip
  Scenario: Practice Project API returns user attempts checkpoints and override values
    Given admin selects an eligible Practice Project override user
    When admin creates an unlimited Practice Project override
    And admin requests the override user's Practice Project API response
    Then the API returns the user attempts checkpoints and override values

  @PP-123
  Scenario: User Attempts supports search, pagination, download and attempt actions
    Given I open the guided activity
    When I open the User Guided Project page
    And admin selects a participant and opens User Attempts
    Then selecting an attempt enables Submit Attempt and Check Details
    And the User Attempts table shows attempt values including override token and checkpoints
    And User Attempts card view displays the attempt
    And User Attempts pagination and search work
    When admin downloads the User Attempts report
    Then the User Attempts report file is downloaded

  Scenario: Admin can cancel the practice project report download
    Given I open the guided activity
    When I open the User Guided Project page
    Then admin can cancel the report download modal

  @PP-131 @deviation
  Scenario: Admin can open checkpoint results for a Practice Project attempt
    Given I open the guided activity
    When I open the User Guided Project page
    And admin selects a participant and opens User Attempts
    Then admin can open checkpoint details for the selected attempt

  @PP-52 @deviation
  Scenario: Admin can configure a Practice Project override with no attempts
    Given admin selects an eligible Practice Project override user
    Then the override form offers the No Attempts configuration

  @PP-117 @deviation
  Scenario: The User Practice Project table prevents multiple participant selection
    Given I open the guided activity
    When I open the User Guided Project page
    Then the participants table prevents multiple selection

  @CGAP-PGG-19 @deviation
  Scenario: The User Practice Project table offers xml csv txt and json export
    Given I open the guided activity
    When I open the User Guided Project page
    Then the User Practice Project table offers xml csv txt and json export
