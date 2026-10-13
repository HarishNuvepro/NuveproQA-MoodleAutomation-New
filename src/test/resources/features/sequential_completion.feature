# Sequential Activity Completion (Moodle core: Activity completion + Restrict access).
# Assessment @SAC-<n> and Practice Project @CGAP-SAC-PP-* map to 'Sequential_Activity_Completion' in Kiwi.
#
# Fixtures: SERIAL runs reuse one shared mark-as-done fixture (@sac / @sacpp) across the scenarios in
# feature order. PARALLEL runs (-Pparallel, sandbox.parallel=true) build an ISOLATED fixture per scenario
# (own course + student + browser) and delete it in @After — so scenarios are order-independent there.
# @sacgrade / @sacppcomplete always build their own fixture. Step state lives in SacSupport.Holder.
# @provisioning (@CGAP-SAC-PP-9, @CGAP-SAC-PP-12): student Start hands-on lab + Submit (CloudLabs in .env).
#
# Serial (full file): .\mvnw.cmd test -Pwatch "-Dcucumber.features=src/test/resources/features/sequential_completion.feature"
# 5 parallel threads: .\mvnw.cmd test -Pparallel "-DsuiteXmlFile=testng-parallel5.xml" "-Dparallel.browsers=5" "-Dautomation.deferCleanup=true" "-Dcucumber.features=src/test/resources/features/sequential_completion.feature"
# Needs MOODLE_WS_TOKEN; -Dcucumber.features= activates single-feature / RunCucumberTest (see RUNNERS.md).

Feature: Sequential activity completion (assessment and Practice Project)

  @SAC-1 @sac
  Scenario: Activity 2 stays locked while Activity 1 is not completed
    Given a course where Activity 1 uses mark-as-done completion and Activity 2 is restricted until Activity 1 is complete
    Then Activity 2 is not accessible to the student

  # Kiwi SAC-10 (duplicate of SAC-1) — traced to @SAC-1 in the workbook; no separate scenario.

  @SAC-12 @sac
  Scenario: Activity 1 is shown as incomplete before completion
    Given a course where Activity 1 uses mark-as-done completion and Activity 2 is restricted until Activity 1 is complete
    Then Activity 1 is shown as not completed

  @SAC-16 @sac @augmented
  Scenario: The restriction message names the required prior activity
    Given a course where Activity 1 uses mark-as-done completion and Activity 2 is restricted until Activity 1 is complete
    Then the restriction message names Activity 1

  @SAC-11 @sac
  Scenario: Activity 1 is shown as completed once marked as done
    Given a course where Activity 1 uses mark-as-done completion and Activity 2 is restricted until Activity 1 is complete
    When the student marks Activity 1 as done
    Then Activity 1 is shown as completed

  @SAC-9 @sac
  Scenario: Activity 2 unlocks once Activity 1 is marked as done
    Given a course where Activity 1 uses mark-as-done completion and Activity 2 is restricted until Activity 1 is complete
    When the student marks Activity 1 as done
    Then Activity 2 becomes accessible to the student

  @SAC-13 @sac
  Scenario: Activity 2 stays unlocked after the student logs out and back in
    Given a course where Activity 1 uses mark-as-done completion and Activity 2 is restricted until Activity 1 is complete
    When the student marks Activity 1 as done
    When the student logs out and logs in again
    Then Activity 2 remains accessible to the student

  # ================= grade-based cases (@sacgrade) =================
  # Activity 1 = CloudLabs assessment (1 submission, MANUAL eval, grade-based completion). Student submits;
  # admin updates the submission grade on Manage User Submissions. Each scenario builds its own fixture.

  @SAC-2 @sac @sacgrade
  Scenario: Activity 2 unlocks once Activity 1 is completed with any grade
    Given a course where Activity 1 requires any grade and Activity 2 is restricted until Activity 1 is complete
    When the student submits Activity 1 assessment attempt
    And the teacher gives the student a grade of 60 on Activity 1
    Then the graded Activity 2 becomes accessible to the student

  @SAC-3 @sac @sacgrade
  Scenario: Activity 2 stays locked when Activity 1 has no grade
    Given a course where Activity 1 requires any grade and Activity 2 is restricted until Activity 1 is complete
    Then the graded Activity 2 is not accessible to the student

  @SAC-4 @sac @sacgrade
  Scenario: Activity 2 unlocks once Activity 1 receives a passing grade
    Given a course where Activity 1 requires a passing grade of 50 and Activity 2 is restricted until Activity 1 is complete
    When the student submits Activity 1 assessment attempt
    And the teacher gives the student a grade of 60 on Activity 1
    Then the graded Activity 2 becomes accessible to the student

  @SAC-5 @sac @sacgrade
  Scenario: Activity 2 stays locked when Activity 1 receives a failing grade
    Given a course where Activity 1 requires a passing grade of 50 and Activity 2 is restricted until Activity 1 is complete
    When the student submits Activity 1 assessment attempt
    And the teacher gives the student a grade of 30 on Activity 1
    Then the graded Activity 2 is not accessible to the student

  @SAC-14 @sac @sacgrade
  Scenario: Activity 2 unlocks when Activity 1 grade is updated from failing to passing
    Given a course where Activity 1 requires a passing grade of 50 and Activity 2 is restricted until Activity 1 is complete
    When the student submits Activity 1 assessment attempt
    And the teacher gives the student a grade of 30 on Activity 1
    Then the graded Activity 2 is not accessible to the student
    When the teacher gives the student a grade of 70 on Activity 1
    Then the graded Activity 2 becomes accessible to the student

  @SAC-6 @sac @sacgrade
  Scenario: Activity 2 unlocks when Activity 1 has a grade and is marked as done
    Given a course where Activity 1 requires passing grade of 50 and manual mark-as-done and Activity 2 is restricted until Activity 1 is complete
    When the student submits Activity 1 assessment attempt
    And the teacher gives the student a grade of 60 on Activity 1
    And the student marks Activity 1 as done after receiving a grade
    Then Activity 1 is fully complete for grade and manual requirements
    And the graded Activity 2 becomes accessible to the student

  # SAC-7 vs SAC-8 (same fixture: passing grade 50 + manual mark-as-done on Activity 1; Activity 2 until Activity 1 fully complete):
  # |                    | SAC-7              | SAC-8              |
  # | Teacher grade      | 30 (below pass 50) | 60 (passing)       |
  # | Student mark done  | Yes                | No                 |
  # | Activity 1 complete| No                 | Yes (grade met)    |
  # | Activity 2         | Restricted         | Restricted         |

  @SAC-7 @sac @sacgrade
  Scenario: Activity 2 stays locked when Activity 1 has a failing grade and is marked done
    Given a course where Activity 1 requires passing grade of 50 and manual mark-as-done and Activity 2 is restricted until Activity 1 is complete
    When the student submits Activity 1 assessment attempt
    And the teacher gives the student a grade of 30 on Activity 1
    And the student marks Activity 1 as done after receiving a grade
    Then Activity 1 is not fully complete for grade and manual requirements
    And the graded Activity 2 is not accessible to the student

  @SAC-8 @sac @sacgrade
  Scenario: Activity 2 stays locked when Activity 1 has a passing grade but is not marked done
    Given a course where Activity 1 requires passing grade of 50 and manual mark-as-done and Activity 2 is restricted until Activity 1 is complete
    When the student submits Activity 1 assessment attempt
    And the teacher gives the student a grade of 60 on Activity 1
    And the student does not mark Activity 1 as done
    Then Activity 1 meets the passing grade requirement but is not marked as done by the student
    And the graded Activity 2 is not accessible to the student

  @SAC-15 @sac
  Scenario: A three-activity chain keeps Activity 3 locked until Activity 1 and 2 are complete
    Given a course with three activities where Activity 3 requires Activity 1 and Activity 2 complete
    Then Activity 3 stays restricted until both Activity 1 and Activity 2 are complete

  # ================= Practice Project — manual mark-as-done (@sacpp), shared fixture, feature order =================

  @CGAP-SAC-PP-1 @sacpp
  Scenario: Practice Project Activity 2 stays locked while Activity 1 is not marked done
    Given a Practice Project course where Activity 1 uses mark-as-done completion and Activity 2 is restricted until Activity 1 is complete
    Then Practice Project Activity 2 is not accessible to the student

  @CGAP-SAC-PP-2 @sacpp
  Scenario: Practice Project Activity 1 is incomplete before mark-as-done
    Given a Practice Project course where Activity 1 uses mark-as-done completion and Activity 2 is restricted until Activity 1 is complete
    Then Practice Project Activity 1 is shown as not completed

  @CGAP-SAC-PP-3 @sacpp
  Scenario: Practice Project restriction message names Activity 1
    Given a Practice Project course where Activity 1 uses mark-as-done completion and Activity 2 is restricted until Activity 1 is complete
    Then the Practice Project restriction message names Activity 1

  @CGAP-SAC-PP-4 @sacpp
  Scenario: Practice Project Activity 1 shows completed after mark-as-done
    Given a Practice Project course where Activity 1 uses mark-as-done completion and Activity 2 is restricted until Activity 1 is complete
    When the student marks Practice Project Activity 1 as done
    Then Practice Project Activity 1 is shown as completed

  @CGAP-SAC-PP-5 @sacpp
  Scenario: Practice Project Activity 2 unlocks when Activity 1 is marked done
    Given a Practice Project course where Activity 1 uses mark-as-done completion and Activity 2 is restricted until Activity 1 is complete
    When the student marks Practice Project Activity 1 as done
    Then Practice Project Activity 2 becomes accessible to the student

  @CGAP-SAC-PP-6 @sacpp
  Scenario: Practice Project Activity 2 stays unlocked after student re-login
    Given a Practice Project course where Activity 1 uses mark-as-done completion and Activity 2 is restricted until Activity 1 is complete
    When the student marks Practice Project Activity 1 as done
    When the student logs out and logs in again on the Practice Project course
    Then Practice Project Activity 2 remains accessible to the student

  @CGAP-SAC-PP-7 @sacpp
  Scenario: Practice Project three-activity chain keeps Activity 3 locked until 1 and 2 are marked done
    Given a Practice Project course with three activities where Activity 3 requires Activity 1 and Activity 2 complete
    Then Practice Project Activity 3 stays restricted until both Activity 1 and Activity 2 are complete

  # ================= Practice Project — student must complete activity (@sacppcomplete), per-scenario fixture =================

  @CGAP-SAC-PP-8 @sacpp @sacppcomplete
  Scenario: Practice Project Activity 2 stays locked until Activity 1 is completed
    Given a Practice Project course where Activity 1 requires the student to complete the activity and Activity 2 is restricted until Activity 1 is complete
    Then Practice Project Activity 2 is not accessible until Activity 1 is completed

  @CGAP-SAC-PP-9 @sacpp @sacppcomplete @provisioning
  Scenario: Practice Project Activity 2 unlocks after the student completes Activity 1
    Given a Practice Project course where Activity 1 requires the student to complete the activity and Activity 2 is restricted until Activity 1 is complete
    When the student completes Practice Project Activity 1
    Then Practice Project Activity 2 becomes accessible after Activity 1 is completed

  @CGAP-SAC-PP-10 @sacpp @sacppcomplete
  Scenario: Practice Project Activity 1 is incomplete before the student completes it
    Given a Practice Project course where Activity 1 requires the student to complete the activity and Activity 2 is restricted until Activity 1 is complete
    Then Practice Project Activity 1 is shown as not completed by activity completion

  @CGAP-SAC-PP-11 @sacpp @sacppcomplete
  Scenario: Practice Project Activity 2 stays locked when Activity 1 is only opened not completed
    Given a Practice Project course where Activity 1 requires the student to complete the activity and Activity 2 is restricted until Activity 1 is complete
    When the student opens Practice Project Activity 1 without completing it
    Then Practice Project Activity 2 is not accessible until Activity 1 is completed

  @CGAP-SAC-PP-12 @sacpp @sacppcomplete @provisioning
  Scenario: Practice Project Activity 2 remains unlocked after completion and re-login
    Given a Practice Project course where Activity 1 requires the student to complete the activity and Activity 2 is restricted until Activity 1 is complete
    When the student completes Practice Project Activity 1
    When the student logs out and logs in again on the Practice Project activity-complete course
    Then Practice Project Activity 2 remains accessible after Activity 1 was completed and the student re-logged in
