# On-demand cleanup ONLY — excluded from default mvn test (see pom.xml / -Pcleanup).
# Run after a green module run when you want to remove automation artefacts.
#
# Examples:
#   .\mvnw.cmd test -Pcleanup -Pwatch "-Dcucumber.tags=@cleanup-all"
#   .\mvnw.cmd test -Pcleanup -Pwatch "-Dcucumber.tags=@cleanup-users-all-courses-sandbox"
#   .\mvnw.cmd test -Pcleanup -Pwatch "-Dcucumber.tags=@cleanup-users"
#   .\mvnw.cmd test -Pcleanup -Pwatch "-Dcucumber.tags=@sandbox-cleanup"
#
@module-cleanup
Feature: On-demand automation cleanup (manual)

  # Users + Upload users (manifest) + All Courses fixtures + Sandbox suite — one shot after green runs
  @cleanup-users-all-courses-sandbox
  Scenario: Delete Users, Upload users, All Courses, and Sandbox automation artefacts
    When admin deletes all automation-created users
    And admin deletes all automation-created All Courses fixtures
    And admin deletes the sandbox suite course

  @cleanup-users
  Scenario: Delete all automation-created Moodle and Upload users
    When admin deletes all automation-created users

  @sandbox-cleanup
  Scenario: Delete the sandbox suite course only
    When admin deletes the sandbox suite course

  @cleanup-assessment
  Scenario: Delete the assessment suite course
    When admin deletes the assessment suite course

  @cleanup-practice
  Scenario: Delete the practice suite course
    When admin deletes the practice suite course

  @cleanup-all-courses
  Scenario: Delete All Courses page automation fixtures
    When admin deletes all automation-created All Courses fixtures

  @cleanup-lab-templates
  Scenario: Delete disposable lab templates created during automation
    When admin deletes all automation-created lab templates

  @settings-cleanup
  Scenario: Restore Moodle admin settings baseline
    When admin restores the automation Moodle settings baseline

  @cleanup-all
  Scenario: Run full automation cleanup
    When admin deletes all automation-created users
    And admin deletes the sandbox suite course
    And admin deletes the assessment suite course
    And admin deletes the practice suite course
    And admin deletes all automation-created All Courses fixtures
    And admin deletes all automation-created lab templates
    And admin restores the automation Moodle settings baseline
