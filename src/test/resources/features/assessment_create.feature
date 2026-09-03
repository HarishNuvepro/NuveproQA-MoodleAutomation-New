# Assessment activity creation. One tagged scenario per configuration cluster.
# Each case creates its own activity (not deleted) and read-back checks the saved settings.
#
# One tag per scenario — the same @A-create-* as Moodle_Assessment_Labs Automation_Ref.
#   .\mvnw.cmd test -Pwatch "-Dcucumber.tags=@A-create-full"
#   .\mvnw.cmd test -Pwatch "-Dcucumber.features=src/test/resources/features/assessment_create.feature"

Feature: Assessment — create activity

  # TC-1 Dataset "full_config" = General + Lab + Availability + Proctoring + Submission + Grade
  # Values: AssessmentTestData.create("full_config")
  @A-create-full
  Scenario: Create an assessment activity with all the configuration
    When admin creates an assessment activity using "full_config"
    Then the assessment is created and the saved settings match "full_config"

  # TC-2 Dataset "general" = lab image, admin name, description, instructions, export PDF Yes, difficulty Advanced
  # Values: AssessmentTestData.create("general")
  @A-create-general
  Scenario: Create an assessment with General details
    When admin creates an assessment activity using "general"
    Then the assessment is created and the saved settings match "general"

  # TC-3 Dataset "pdf_no" = Export content to PDF No
  # Values: AssessmentTestData.create("pdf_no")
  @A-create-pdf-no
  Scenario: Create an assessment with Export content to PDF No
    When admin creates an assessment activity using "pdf_no"
    Then the assessment is created and the saved settings match "pdf_no"

  # TC-4..TC-7 Datasets "difficulty_*" = difficulty Beginner / Intermediate / Advanced / Expert
  # Values: AssessmentTestData.create("<dataset>")
  @A-create-difficulty
  Scenario Outline: Create an assessment with difficulty <level>
    When admin creates an assessment activity using "<dataset>"
    Then the assessment is created and the saved settings match "<dataset>"
    Examples: beginner
      | tc | dataset             | level    |
      | 4  | difficulty_beginner | Beginner |
    Examples: intermediate
      | tc | dataset                 | level        |
      | 5  | difficulty_intermediate | Intermediate |
    Examples: advanced
      | tc | dataset             | level    |
      | 6  | difficulty_advanced | Advanced |
    Examples: expert
      | tc | dataset           | level  |
      | 7  | difficulty_expert | Expert |

  # TC-8 Dataset "eval_auto" = evaluation Auto, Cygnus script URL, marks per test case 10
  # Values: AssessmentTestData.create("eval_auto")
  @A-create-eval-auto
  Scenario: Create an assessment with Auto (using script) evaluation
    When admin creates an assessment activity using "eval_auto"
    Then the assessment is created and the saved settings match "eval_auto"

  # TC-9 Dataset "eval_answer" = evaluation Answer, challenge answer text, challenge marks 10
  # Values: AssessmentTestData.create("eval_answer")
  @A-create-eval-answer
  Scenario: Create an assessment with Answer evaluation
    When admin creates an assessment activity using "eval_answer"
    Then the assessment is created and the saved settings match "eval_answer"

  # TC-10 Dataset "eval_manual" = evaluation Manual
  # Values: AssessmentTestData.create("eval_manual")
  @A-create-eval-manual
  Scenario: Create an assessment with Manual evaluation
    When admin creates an assessment activity using "eval_manual"
    Then the assessment is created and the saved settings match "eval_manual"

  # TC-11 Dataset "lab_yes" = parallel view Yes, show stop lab button Yes
  # Values: AssessmentTestData.create("lab_yes")
  @A-create-lab-yes
  Scenario: Create an assessment with parallel view and stop-lab button Yes
    When admin creates an assessment activity using "lab_yes"
    Then the assessment is created and the saved settings match "lab_yes"

  # TC-12 Dataset "lab_no" = parallel view No, show stop lab button No
  # Values: AssessmentTestData.create("lab_no")
  @A-create-lab-no
  Scenario: Create an assessment with parallel view and stop-lab button No
    When admin creates an assessment activity using "lab_no"
    Then the assessment is created and the saved settings match "lab_no"

  # TC-13 Dataset "dates" = start date enabled, due date 20 June 2027
  # Values: AssessmentTestData.create("dates")
  @A-create-dates
  Scenario: Create an assessment with start date enabled and due date 20 June 2027
    When admin creates an assessment activity using "dates"
    Then the assessment is created and the saved settings match "dates"

  # TC-14 Dataset "timelimit" = time limit enabled, 2 hours
  # Values: AssessmentTestData.create("timelimit")
  @A-create-timelimit
  Scenario: Create an assessment with a 2 hour time limit
    When admin creates an assessment activity using "timelimit"
    Then the assessment is created and the saved settings match "timelimit"

  # TC-15 Dataset "no_timelimit" = time limit checkbox left off
  # Values: AssessmentTestData.create("no_timelimit")
  @A-create-no-timelimit
  Scenario: Create an assessment with time limit left off
    When admin creates an assessment activity using "no_timelimit"
    Then the assessment is created and the saved settings match "no_timelimit"

  # TC-16 Dataset "proctored_yes" = proctored Yes (Moodle also requires start date, due date, time limit 2h)
  # Values: AssessmentTestData.create("proctored_yes")
  @A-create-proctored-yes
  Scenario: Create a proctored assessment with start date, due date and time limit
    When admin creates an assessment activity using "proctored_yes"
    Then the assessment is created and the saved settings match "proctored_yes"

  # TC-17 Dataset "proctored_no" = proctored No
  # Values: AssessmentTestData.create("proctored_no")
  @A-create-proctored-no
  Scenario: Create a non-proctored assessment
    When admin creates an assessment activity using "proctored_no"
    Then the assessment is created and the saved settings match "proctored_no"

  # TC-18 Dataset "attempts" = submissions allowed 2, attempts allowed 2, cooling period 2 days
  # Values: AssessmentTestData.create("attempts")
  @A-create-attempts
  Scenario: Create an assessment with submissions 2, attempts 2 and cooldown 2 days
    When admin creates an assessment activity using "attempts"
    Then the assessment is created and the saved settings match "attempts"

  # TC-19 Dataset "action_stop" = Action After Attempt Completes Stop
  # Values: AssessmentTestData.create("action_stop")
  @A-create-action-stop
  Scenario: Create an assessment with action after attempt completes Stop
    When admin creates an assessment activity using "action_stop"
    Then the assessment is created and the saved settings match "action_stop"

  # TC-20 Dataset "action_park" = Action After Attempt Completes Park
  # Values: AssessmentTestData.create("action_park")
  @A-create-action-park
  Scenario: Create an assessment with action after attempt completes Park
    When admin creates an assessment activity using "action_park"
    Then the assessment is created and the saved settings match "action_park"

  # TC-21 Dataset "action_delete" = Action After Attempt Completes Delete
  # Values: AssessmentTestData.create("action_delete")
  @A-create-action-delete
  Scenario: Create an assessment with action after attempt completes Delete
    When admin creates an assessment activity using "action_delete"
    Then the assessment is created and the saved settings match "action_delete"

  # TC-22 Dataset "email_yes" = email after completion Yes (requires evaluation Auto + script + marks)
  # Values: AssessmentTestData.create("email_yes")
  @A-create-email-yes
  Scenario: Create an Auto-eval assessment with email after completion Yes
    When admin creates an assessment activity using "email_yes"
    Then the assessment is created and the saved settings match "email_yes"

  # TC-23 Dataset "email_no" = email after completion No (requires evaluation Auto + script + marks)
  # Values: AssessmentTestData.create("email_no")
  @A-create-email-no
  Scenario: Create an Auto-eval assessment with email after completion No
    When admin creates an assessment activity using "email_no"
    Then the assessment is created and the saved settings match "email_no"

  # TC-24 Dataset "grade" = grade to pass 30 (also part of every baseline create)
  # Values: AssessmentTestData.create("grade")
  @A-create-grade
  Scenario: Create an assessment with grade to pass 30
    When admin creates an assessment activity using "grade"
    Then the assessment is created and the saved settings match "grade"

  # TC-25 Dataset "desc_git" = description source Git + GitHub instructions URL
  # Values: AssessmentTestData.create("desc_git")
  @A-create-desc-git
  Scenario: Create an assessment with description from Git URL
    When admin creates an assessment activity using "desc_git"
    Then the assessment is created and the saved settings match "desc_git"

  # TC-26 Dataset "content_git" = challenge content source Git + same GitHub instructions URL
  # Values: AssessmentTestData.create("content_git")
  @A-create-content-git
  Scenario: Create an assessment with content source Git URL
    When admin creates an assessment activity using "content_git"
    Then the assessment is created and the saved settings match "content_git"

  # TC-27 Dataset "randomization_yes" = randomization of problem statement Yes
  # Values: AssessmentTestData.create("randomization_yes")
  @A-create-randomization
  Scenario: Create an assessment with randomization Yes
    When admin creates an assessment activity using "randomization_yes"
    Then the assessment is created and the saved settings match "randomization_yes"

  # TC-28 Dataset "overdue_autosubmit" = When time expires auto-submit (needs time limit)
  # Values: AssessmentTestData.create("overdue_autosubmit")
  @A-create-overdue-autosubmit
  Scenario: Create an assessment that auto-submits when time expires
    When admin creates an assessment activity using "overdue_autosubmit"
    Then the assessment is created and the saved settings match "overdue_autosubmit"

  # TC-29 Dataset "overdue_remove" = When time expires remove lab access (needs time limit)
  # Values: AssessmentTestData.create("overdue_remove")
  @A-create-overdue-remove
  Scenario: Create an assessment that removes lab access when time expires
    When admin creates an assessment activity using "overdue_remove"
    Then the assessment is created and the saved settings match "overdue_remove"

  # TC-30 Dataset "attempts_after_cooldown" = cooldown 2 days so attempts-after-cooldown is shown = 2
  # Values: AssessmentTestData.create("attempts_after_cooldown")
  @A-create-after-cooldown
  Scenario: Create an assessment with attempts after cooldown 2
    When admin creates an assessment activity using "attempts_after_cooldown"
    Then the assessment is created and the saved settings match "attempts_after_cooldown"

  # TC-31 Dataset "eval_timeout" = evaluation timeout 1 hour + action Park (not action after attempt)
  # Values: AssessmentTestData.create("eval_timeout")
  @A-create-eval-timeout
  Scenario: Create an assessment with evaluation timeout duration and action
    When admin creates an assessment activity using "eval_timeout"
    Then the assessment is created and the saved settings match "eval_timeout"

  # TC-32 Dataset "missing_name" = name left empty; Moodle must reject the create
  # Values: AssessmentTestData.create("missing_name")
  @A-create-missing-name
  Scenario: Create is rejected when the activity name is missing
    When admin tries to create an assessment activity using "missing_name"
    Then the activity is not created and a validation error is shown

  # TC-33 Dataset "missing_template" = lab template left empty; Moodle must reject the create
  # Values: AssessmentTestData.create("missing_template")
  @A-create-missing-template
  Scenario: Create is rejected when the lab template is missing
    When admin tries to create an assessment activity using "missing_template"
    Then the activity is not created and a validation error is shown

  # TC-34 Student role: enrolled student cannot add a Lab (cloudlabs) activity
  @A-create-student-denied
  Scenario: Student cannot add a Lab activity
    When an enrolled student opens the course
    Then the student cannot add a Lab activity

  # TC-35 Dataset "stop_lab_yes" = Lab details: Show stop lab button in lab Control Panel Yes
  # Values: AssessmentTestData.create("stop_lab_yes")
  @A-create-stop-lab-yes
  Scenario: Create an assessment with show stop lab button Yes
    When admin creates an assessment activity using "stop_lab_yes"
    Then the assessment is created and the saved settings match "stop_lab_yes"

  # TC-36 Dataset "stop_lab_no" = Lab details: Show stop lab button in lab Control Panel No
  # Values: AssessmentTestData.create("stop_lab_no")
  @A-create-stop-lab-no
  Scenario: Create an assessment with show stop lab button No
    When admin creates an assessment activity using "stop_lab_no"
    Then the assessment is created and the saved settings match "stop_lab_no"

  # TC-37 Dataset "attempts_after_passing_yes" = Submission: Allow Attempts After Passing Yes
  # Values: AssessmentTestData.create("attempts_after_passing_yes")
  @A-create-after-passing-yes
  Scenario: Create an assessment with allow attempts after passing Yes
    When admin creates an assessment activity using "attempts_after_passing_yes"
    Then the assessment is created and the saved settings match "attempts_after_passing_yes"

  # TC-38 Dataset "attempts_after_passing_no" = Submission: Allow Attempts After Passing No
  # Values: AssessmentTestData.create("attempts_after_passing_no")
  @A-create-after-passing-no
  Scenario: Create an assessment with allow attempts after passing No
    When admin creates an assessment activity using "attempts_after_passing_no"
    Then the assessment is created and the saved settings match "attempts_after_passing_no"

  # TC-39 Dataset "help_video_yes" = Activity help: show help video Yes + Help Video Link
  # Values: AssessmentTestData.create("help_video_yes")
  @A-create-help-video-yes
  Scenario: Create an assessment with help video Yes and video link
    When admin creates an assessment activity using "help_video_yes"
    Then the assessment is created and the saved settings match "help_video_yes"

  # TC-40 Dataset "help_video_no" = Activity help: show help video No (no link required)
  # Values: AssessmentTestData.create("help_video_no")
  @A-create-help-video-no
  Scenario: Create an assessment with help video No
    When admin creates an assessment activity using "help_video_no"
    Then the assessment is created and the saved settings match "help_video_no"

  # TC-41 Dataset "action_none" = Action After Attempt Completes = No Action
  # Values: AssessmentTestData.create("action_none")
  @A-create-action-none
  Scenario: Create an assessment with action after attempt completes No Action
    When admin creates an assessment activity using "action_none"
    Then the assessment is created and the saved settings match "action_none"

  # TC-42 Dataset "marks_different" = Auto eval + Test Case Marks Different for each
  # Values: AssessmentTestData.create("marks_different")
  @A-create-marks-different
  Scenario: Create an assessment with test case marks Different for each
    When admin creates an assessment activity using "marks_different"
    Then the assessment is created and the saved settings match "marks_different"

  # TC-43 Dataset "randomization_no" = randomization of problem statement No
  # Values: AssessmentTestData.create("randomization_no")
  @A-create-randomization-no
  Scenario: Create an assessment with randomization No
    When admin creates an assessment activity using "randomization_no"
    Then the assessment is created and the saved settings match "randomization_no"

  # TC-44 Dataset "grade_1" = grade to pass boundary minimum valid (1)
  # Values: AssessmentTestData.create("grade_1")
  @A-create-grade-1
  Scenario: Create an assessment with grade to pass 1
    When admin creates an assessment activity using "grade_1"
    Then the assessment is created and the saved settings match "grade_1"

  # TC-45 Dataset "grade_100" = grade to pass boundary upper typical (100)
  # Values: AssessmentTestData.create("grade_100")
  @A-create-grade-100
  Scenario: Create an assessment with grade to pass 100
    When admin creates an assessment activity using "grade_100"
    Then the assessment is created and the saved settings match "grade_100"

  # TC-46 Dataset "grade_0" = grade to pass 0 rejected (plugin: gradepass <= 0)
  # Values: AssessmentTestData.create("grade_0")
  @A-create-grade-0
  Scenario: Create is rejected when grade to pass is 0
    When admin tries to create an assessment activity using "grade_0"
    Then the activity is not created and a validation error is shown

  # TC-47 Dataset "grade_negative" = grade to pass -1 rejected
  # Values: AssessmentTestData.create("grade_negative")
  @A-create-grade-negative
  Scenario: Create is rejected when grade to pass is negative
    When admin tries to create an assessment activity using "grade_negative"
    Then the activity is not created and a validation error is shown

  @A1
  Scenario: An assessment activity offers a lab template / platform selection
    Given a new challenge activity form is open
    Then the activity offers a lab template selection

  @A21
  Scenario: Creating an assessment with the due date before the start date is rejected
    When admin creates an assessment with the due date before the start date
    Then the activity is not created and a date validation error is shown

  @CGAP-A-1
  Scenario: Evaluation type toggles the matching config fields
    When I open a new challenge activity form
    Then the evaluation type toggles the matching config fields

  @CGAP-A-2
  Scenario: Randomization hides the manual content editor
    When I open a new challenge activity form
    Then enabling randomization hides the manual content editor

  @CGAP-A-3
  Scenario: The challenge content source toggles the git URL field
    When I open a new challenge activity form
    Then switching the challenge content source toggles the git URL field

  @CGAP-A-4
  Scenario: Cooldown period toggles attempts-after-cooldown
    When I open a new challenge activity form
    Then setting a cooldown period shows attempts-after-cooldown

  @CGAP-A-5
  Scenario: A challenge due date before the start date is rejected
    When admin sets the challenge due date before the start date
    Then the challenge due-date validation error is shown

  @CGAP-A-6
  Scenario: A challenge without a passing grade is rejected
    When admin clears the challenge passing grade and saves
    Then the passing-grade validation error is shown

  @CGAP-A-7
  Scenario: Invalid test-script-parameters JSON is rejected
    When admin enters invalid test script parameters and saves
    Then the test-script-parameters validation error is shown

  @A327
  Scenario: The problem statements page is keyboard navigable
    Then the problem statements page is keyboard navigable


