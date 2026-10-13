# Site-created Moodle configurable reports — deferred (not automatable on QA until Report Builder URLs exist).
# Excluded from default runs via @skip. Kiwi: Automated=No on report rows.
#   .\mvnw.cmd test -Pwatch "-Dcucumber.tags=@PP-69"   (only if URLs configured later)

@skip @pp-reports-deferred
Feature: Practice Project detailed and summary reports

  @PP-69
  Scenario: Detailed report filters exact and nonexistent users and exposes required columns
    Given a fresh user is enrolled in the Practice Project course for reporting
    And the Practice Project detailed report is available
    Then the detailed report exposes identity course activity enrolment and attempt columns
    When admin filters the report by the fixture user's full email
    Then only the fixture user's report record is shown
    When admin filters the report by a nonexistent email
    Then the report shows no records
    When admin applies the report without filter values
    Then the report remains usable and lists records

  @PP-71
  Scenario: Detailed report supports All and specific course filters
    Given the Practice Project detailed report is available
    When admin applies the All course filter
    Then the report returns records across courses
    When admin applies a specific available course filter
    Then the report keeps course and activity data visible

  @PP-73
  Scenario: Detailed report filter changes can be cancelled
    Given the Practice Project detailed report is available
    Then canceling a detailed-report filter restores its previous value

  @PP-79
  Scenario: Practice Project reports remove unenrolled users and restore re-enrolled users
    Given a fresh user is enrolled in the Practice Project course for reporting
    And the Practice Project detailed report is available
    When admin filters the report by the fixture user's full email
    Then only the fixture user's report record is shown
    When the fixture user is unenrolled from the Practice Project course
    Then the unenrolled fixture user is absent from the report
    When the fixture user is re-enrolled in the Practice Project course
    Then the re-enrolled fixture user returns to the report

  @PP-94
  Scenario: Summary report filters a not-started user and keeps project counts consistent
    Given a fresh user is enrolled in the Practice Project course for reporting
    And the Practice Project summary report is available
    When admin filters the report by the fixture user's full email
    Then only the fixture user's report record is shown
    When admin searches the summary report by fixture email
    Then only the fixture user's report record is shown
    And summary completed remaining and total project counts are consistent
    When admin filters the report by a nonexistent email
    Then the report shows no records

  @PP-80
  Scenario: Detailed report handles suspension, enrolment dates, self-enrolment and another course
    Then the report state requires an external suspension self-enrolment or second-course fixture

  @PP-89
  Scenario: Reports reflect lab, attempt, activity deletion, creation and visibility states
    Then the report state requires an external suspension self-enrolment or second-course fixture

  @PP-137
  Scenario: Downloaded report data matches UI and database records exactly
    Then the report comparison requires database read access
