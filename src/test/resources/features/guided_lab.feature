# Guided (practice project) lab lifecycle — ONE provisioned lab driven through the student flow:
# start attempt (G14 control panel, G15/CGAP-PGG-8 lab reaches Running via the CloudLabs API),
# stop (G16/G17/CGAP-PGG-9 stopped + redirect to landing), continue (CGAP-PGG-1), submit (CGAP-PGG-5).
# Teardown deletes the student (cascade-deletes the lab) and waits for provider deletion.
# Run: mvnw test -Dcucumber.tags="@guidedlab"   (provisions a REAL lab - needs CloudLabs + pool slot)

Feature: Guided lab lifecycle

  @PP-11 @PP-27 @PP-29 @PP-33 @PP-48 @PP-139
  @PGG-14 @PGG-15 @PGG-16 @PGG-17 @CGAP-PGG-1 @CGAP-PGG-5 @CGAP-PGG-8 @CGAP-PGG-9
  @practice_project @guidedlab @provisioning
  Scenario: A student runs the guided project lifecycle - start, stop, continue, submit
    Given a student is enrolled in the course
    When the student starts the guided project
    Then the guided control panel is shown
    And the guided lab reaches Running
    And the in-progress student can open Add Override
    Then the guided console has Submit and no Get Progress action
    And the guided console shows instructions duration and support
    And the running guided console shows Lab Credentials
    And the Stop and Submit actions show meaningful confirmations
    When the student stops the guided lab
    Then the guided lab is Stopped and the student returns to the landing
    When the student continues the guided project
    Then the guided control panel is shown
    When the student submits the guided attempt
