# Practice Project — student / user functionality (landing, console, lab variants).
#   .\mvnw.cmd test -Pwatch "-Dcucumber.tags=@PP-5"
#   .\mvnw.cmd test -Pwatch "-Dcucumber.features=src/test/resources/features/practice_project_user_functionality.feature"

Feature: Practice Project user functionality

  @PP-5
  Scenario: The practice project landing shows details, start, summary and Manage Overrides
    Given I open the guided activity
    Then the guided landing offers a start option
    And the guided landing shows the project dates
    And the practice project landing shows the attempts summary
    And admin sees the Manage Practice Project Overrides control

  @CGAP-PGG-20
  Scenario: The landing shows dates, time limit, description, Read More and intro video
    Given I open the guided activity
    Then the practice project landing shows dates time limit and allowed attempts
    And the practice project landing shows description Read More and intro video

  @PP-9
  Scenario: Practice Project console exposes renamed controls and permits in-progress override
    Given a student is enrolled in the course
    When the student starts the guided project
    Then the guided control panel is shown
    And the guided console has Submit and no Get Progress action
    And the guided console shows instructions duration and support
    And the guided Stop button is present

  @PP-19 @guidedlab @provisioning
  Scenario: Without-progress project submits successfully without progress controls
    Given the without-progress Practice Project fixture is configured
    And a student is enrolled in the course
    When the student starts the guided project
    Then the guided control panel is shown
    And the guided lab reaches Running
    And the Stop and Submit actions show meaningful confirmations
    When the student submits the guided attempt while the lab is running
    Then the attempt is submitted and the student returns to the landing
    And the without-progress attempt succeeds and hides progress actions
    And further attempts are blocked by the configured limit

  @PP-14 @guidedlab @provisioning
  Scenario: Parallel-view Practice Project renders the lab separately
    Given the parallel-view Practice Project fixture is configured
    And a student is enrolled in the course
    When the student starts the guided project
    Then the guided control panel is shown
    And the guided lab reaches Running
    And the parallel-view lab renders separately from the instruction area

  @PP-45
  Scenario: Stop button is absent when disabled in activity configuration
    Given the no-stop-button Practice Project fixture is configured
    And a student is enrolled in the course
    When the student starts the guided project
    Then the guided control panel is shown
    And the guided Stop button is absent

  @PP-22 @guidedlab @provisioning
  Scenario: Timed Practice Project auto-submits and runs its configured lab action
    Given the timed Practice Project fixture is configured
    And a student is enrolled in the course
    When the student starts the guided project
    Then the guided control panel is shown
    And the guided lab reaches Running
    Then the timed attempt completes and its configured lab action occurs

  @PP-31
  Scenario: Practice Project exposes the manual Complete action
    Given I open the guided activity
    Then the manual Practice Project Complete action is available
