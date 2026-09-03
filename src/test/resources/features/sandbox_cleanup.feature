# On-demand only — does NOT run with the Sandbox suite.
# Deletes the throwaway suite course (labs, activities, enrolments go with it).
#   .\mvnw.cmd test -Pwatch "-Dcucumber.tags=@sandbox-cleanup"

@sandbox-cleanup
Feature: Sandbox course cleanup

  Scenario: Delete the sandbox suite course
    When admin deletes the sandbox suite course
