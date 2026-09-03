# Guided attempt lifecycle — ONE provisioned lab: submit while RUNNING (CGAP-PGG-5 firm),
# cooldown gap after submit (CGAP-PGG-3, each_attempt_time_gap=1min - observed or already elapsed),
# then New Attempt (CGAP-PGG-2). The second attempt does NOT provision a lab (CP-shown is enough).
# CGAP-PGG-6 (Complete button) is DORMANT (commented out in guided-landing.mustache) - deferred.
# Run: mvnw test -Dcucumber.tags="@guidedattempts"   (provisions a REAL lab)

Feature: Guided attempt lifecycle

  @PP-12 @PP-20 @PP-21 @PP-37 @PP-42 @CGAP-PGG-2 @CGAP-PGG-3 @CGAP-PGG-5
  @practice_project @guidedattempts @guidedlab @provisioning
  Scenario: A student submits an attempt and starts a new one after the cooldown
    Given a student is enrolled in the course
    When the student starts the guided project
    Then the guided control panel is shown
    And the guided lab reaches Running
    When the student submits the guided attempt while the lab is running
    Then the attempt is submitted and the student returns to the landing
    And the submitted guided attempt records evaluation and checkpoint results
    And the completed with-progress attempt shows its progress icon
    And the new-attempt cooldown is observed or already elapsed
    When the student starts a new attempt after the cooldown
    Then the guided control panel is shown
