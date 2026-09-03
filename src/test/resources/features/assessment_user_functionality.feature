Feature: Assessment — user functionality

  # Flow: landing → Start Assessment → lab console (cp.php) → wait for lab (auto) → Submit
  # Lab auto-provisions after Start Assessment; wait for Jump to Console / sign-in link (no Start lab click).
  # eval_auto uses lab template: aws.Account.Clab_ACCOUNT_VPAT Testing
  # Modes:
  #   default @A-user-* = reuse activity + fresh user
  #   @fresh_activity = always create a new activity (required for timelimit / rich content / full-config checks)
  #   @fresh_activity_reuse_user = fresh activity + reuse enrolled user for the same course
  #   @timelimit_wait = scenarios that wait ~3 minutes for auto-submit (A-user-13, A-user-14, A-user-24)
  @A-user-1
  Scenario: Student starts an auto-evaluation assessment and submits the attempt
    Given admin creates an assessment activity using "eval_auto"
    And a student is enrolled in the created assessment course
    When the student opens the created assessment activity
    And the student starts the created assessment
    And the assessment lab is created successfully
    And the student submits the created assessment attempt
    Then the created assessment attempt is submitted for evaluation

  @LT-6 @fresh_activity
  Scenario: Assessment template requests configured input during submission
    Given admin creates an action-input-enabled disposable assessment activity
    And a student is enrolled in the created assessment course
    When the student opens the created assessment activity
    And the student starts the created assessment
    And the assessment lab is created successfully
    And the student submits the created assessment attempt
    Then assessment submission requested template action input

  @LT-71 @provisioning @fresh_activity
  Scenario: A proctored assessment displays the four-step lab progress tracker
    Given admin creates an assessment activity using "proctored_yes"
    And a student is enrolled in the created assessment course
    When the student opens the created assessment activity
    And the student starts the proctored assessment while observing lab progress
    Then the proctored assessment displayed all four lab progress steps

  @A-user-2
  Scenario: Student can open an already visible auto-evaluation assessment
    Given admin creates an assessment activity using "eval_auto"
    And a student is enrolled in the created assessment course
    When the student opens the created assessment activity
    Then the created assessment landing is visible for the student

  @A-user-3
  Scenario: Student sees assessment details before starting
    Given admin creates an assessment activity using "eval_auto"
    And a student is enrolled in the created assessment course
    When the student opens the created assessment activity
    Then the created assessment instructions are visible before starting

  @A-user-4
  Scenario: Student starts an auto-evaluation assessment and reaches the lab console
    Given admin creates an assessment activity using "eval_auto"
    And a student is enrolled in the created assessment course
    When the student opens the created assessment activity
    And the student starts the created assessment
    Then the created assessment lab console is shown

  @A-user-5
  Scenario: Student sees the submission move into Evaluating after submit
    Given admin creates an assessment activity using "eval_auto"
    And a student is enrolled in the created assessment course
    When the student opens the created assessment activity
    And the student starts the created assessment
    And the assessment lab is created successfully
    And the student submits the created assessment attempt
    Then the created assessment attempt moves to Evaluating

  @A-user-6
  Scenario: The same student can reopen the submitted assessment and see the evaluated result
    Given admin creates an assessment activity using "eval_auto"
    And a student is enrolled in the created assessment course
    When the student opens the created assessment activity
    And the student starts the created assessment
    And the assessment lab is created successfully
    And the student submits the created assessment attempt
    And the created assessment attempt is submitted for evaluation
    Then the same student can reopen the created assessment and see the evaluated result

  @A-user-7
  Scenario: A second fresh user can use the same existing assessment independently
    Given admin creates an assessment activity using "eval_auto"
    And a student is enrolled in the created assessment course
    When the student opens the created assessment activity
    Then another fresh user can open the same created assessment independently

  @A-user-8 @fresh_activity_reuse_user
  Scenario: A reused enrolled user can access a fresh assessment activity
    Given admin creates an assessment activity using "eval_auto"
    And a student is enrolled in the created assessment course
    When the student opens the created assessment activity
    Then the reused enrolled user can access the fresh created assessment

  @A-user-9
  Scenario: Student cannot access the assessment from the course page when it is hidden
    Given admin creates an assessment activity using "eval_auto"
    And admin hides the created assessment on the course page
    And a student is enrolled in the created assessment course
    When the student opens the created assessment course page
    Then the created assessment is hidden from the student course page

  @A-user-10 @fresh_activity_reuse_user
  Scenario: A future start date blocks the student from starting the assessment
    Given admin creates an assessment activity using "future_start"
    And a student is enrolled in the created assessment course
    When the student opens the created assessment activity
    And the student tries to start the created assessment
    Then the student sees a blocked-start message for the created assessment

  @A-user-11
  Scenario: Student can view evaluated result details after completion
    Given admin creates an assessment activity using "eval_auto"
    And a student is enrolled in the created assessment course
    When the student opens the created assessment activity
    And the student starts the created assessment
    And the assessment lab is created successfully
    And the student submits the created assessment attempt
    And the created assessment attempt is submitted for evaluation
    Then the created assessment shows evaluated result details

  @A-user-12
  Scenario: Student sees a new attempt or retake control when multiple attempts are allowed
    Given admin creates an assessment activity using "eval_auto_attempts"
    And a student is enrolled in the created assessment course
    When the student opens the created assessment activity
    And the student starts the created assessment
    And the assessment lab is created successfully
    And the student submits the created assessment attempt
    And the created assessment attempt is submitted for evaluation
    Then the student sees a new attempt or retake control on the created assessment

  # --- Extended user-flow (fresh activity; timelimit scenarios ~3+ min each) ---
  @A-user-13 @fresh_activity @timelimit_wait
  Scenario: Time limit auto-submits a single-attempt assessment after 3 minutes
    Given admin creates an assessment activity using "autosubmit_3min"
    And a student is enrolled in the created assessment course
    When the student opens the created assessment activity
    And the student starts the created assessment
    And the assessment lab is created successfully
    And the student waits on the lab console without submitting until the time limit expires
    Then the created assessment is auto-submitted evaluated and completed

  @A-user-14 @fresh_activity @timelimit_wait
  Scenario: Time limit auto-submits the first attempt when multiple attempts are allowed
    Given admin creates an assessment activity using "autosubmit_3min_attempts"
    And a student is enrolled in the created assessment course
    When the student opens the created assessment activity
    And the student starts the created assessment
    And the assessment lab is created successfully
    And the student waits on the lab console without submitting until the time limit expires
    Then the created assessment is auto-submitted evaluated and completed
    And the student still has another attempt available after the auto-submitted evaluation

  @A-user-15 @fresh_activity
  Scenario: Student sees full-configuration settings on the assessment landing
    Given admin creates an assessment activity using "userflow_full_config"
    And a student is enrolled in the created assessment course
    When the student opens the created assessment activity
    Then the student sees the configured assessment settings on the landing

  @A-user-16 @fresh_activity
  Scenario: Student sees rich description and instruction content
    Given admin creates an assessment activity using "rich_content"
    And a student is enrolled in the created assessment course
    When the student opens the created assessment activity
    Then the student sees the rich description content on the landing
    When the student starts the created assessment
    And the assessment lab is created successfully
    Then the student sees the rich assessment instructions on the lab console

  @A-user-17 @fresh_activity
  Scenario: Jump to Console opens the access lab popup and platform redirect
    Given admin creates an assessment activity using "eval_auto"
    And a student is enrolled in the created assessment course
    When the student opens the created assessment activity
    And the student starts the created assessment
    And the assessment lab is created successfully
    And the student opens the access lab flow from Jump to Console
    Then the student is redirected to the cloud lab platform

  @A-user-18 @fresh_activity
  Scenario: Lab console shows access credentials for the student
    Given admin creates an assessment activity using "eval_auto"
    And a student is enrolled in the created assessment course
    When the student opens the created assessment activity
    And the student starts the created assessment
    And the assessment lab is created successfully
    Then the lab console shows access credentials for the student

  @A-user-19 @fresh_activity
  Scenario: Lab console shows lab duration and get cost control
    Given admin creates an assessment activity using "eval_auto"
    And a student is enrolled in the created assessment course
    When the student opens the created assessment activity
    And the student starts the created assessment
    And the assessment lab is created successfully
    Then the lab console shows lab duration and a get cost control

  @A-user-20 @fresh_activity
  Scenario: Student changes the lab password from credentials on the console
    Given admin creates an assessment activity using "eval_auto"
    And a student is enrolled in the created assessment course
    When the student opens the created assessment activity
    And the student starts the created assessment
    And the assessment lab is created successfully
    And the student changes the lab password on the console
    Then the lab console shows the updated lab password

  @A-user-21 @fresh_activity
  Scenario: Student stops the lab from the console and admin Manage Labs shows Stopped
    Given admin creates an assessment activity using "eval_auto_stop_lab"
    And a student is enrolled in the created assessment course
    When the student opens the created assessment activity
    And the student starts the created assessment
    And the assessment lab is created successfully
    And the student stops the assessment lab from the console
    Then admin Manage Labs shows the assessment lab is stopped for the student

  @A-user-22 @fresh_activity
  Scenario: Student downloads the instructions PDF from the lab console
    Given admin creates an assessment activity using "eval_auto_pdf"
    And a student is enrolled in the created assessment course
    When the student opens the created assessment activity
    And the student starts the created assessment
    And the assessment lab is created successfully
    And the student downloads the instructions PDF from the lab console
    Then the instructions PDF is downloaded successfully

  @A-user-23 @fresh_activity
  Scenario: Student opens Need help Contact our support team and reaches a mail form
    Given admin creates an assessment activity using "eval_auto"
    And a student is enrolled in the created assessment course
    When the student opens the created assessment activity
    And the student starts the created assessment
    And the assessment lab is created successfully
    And the student opens the Need help contact support link on the lab console
    Then the support link redirects to a mail form

  # A-user-13 already covers stay-on-console auto-submit → eval (≤1 min) → complete (≤1 min).
  # A-user-24 is the activity-landing twin of the same 3-minute time-limit flow.
  @A-user-24 @fresh_activity @timelimit_wait
  Scenario: Time limit auto-submits while the student stays on the assessment landing
    Given admin creates an assessment activity using "autosubmit_3min"
    And a student is enrolled in the created assessment course
    When the student opens the created assessment activity
    And the student starts the created assessment
    And the assessment lab is created successfully
    And the student returns to the assessment landing and waits until the time limit expires
    Then the created assessment is auto-submitted evaluated and completed

  @A2
  Scenario: When due date reached the assessment completes
    Then this cooldown case needs a live attempt or cooldown state

  @A3
  Scenario: When duration reached the assessment completes
    Then this cooldown case needs a live attempt or cooldown state

  @A4
  Scenario: When attempts are completed a new submission is blocked
    Then this lifecycle case needs a running or completed assessment lab

  @A5 @provisioning
  Scenario: The assessment can be completed with the consent gate
    Given a running assessment lab for a fresh student
    Then the assessment can be completed with the consent gate

  @A8 @provisioning
  Scenario: A submitted attempt is evaluated and a grade is shown
    Given a running assessment lab for a fresh student
    When the student submits the attempt for evaluation
    Then the attempt is evaluated and a grade is shown

  @A12
  Scenario: When the assessment completes the timer stops
    Then this lifecycle case needs a running or completed assessment lab

  @A15
  Scenario: When global duration completes all users complete
    Then this lifecycle case needs a running or completed assessment lab

  @A16
  Scenario: Marks update correctly for all users
    Then this lifecycle case needs a running or completed assessment lab

  @A19
  Scenario: Cloud cost displays correctly
    Then this lifecycle case needs a running or completed assessment lab

  @A22
  Scenario: Remaining time matches between lab and activity page
    Then this lifecycle case needs a running or completed assessment lab

  @A23
  Scenario: When enrolment end date completes the lab is gone
    Then this lifecycle case needs a running or completed assessment lab

  @A101
  Scenario: A future start date blocks starting the assessment
    Then this cooldown case needs a live attempt or cooldown state

  @A107
  Scenario: A popup appears on clicking start assessment
    Then this cooldown case needs a live attempt or cooldown state

  @A108 @provisioning
  Scenario: Stopping the lab shows a confirmation popup
    Given a running assessment lab for a fresh student
    Then stopping the lab shows a confirmation popup

  @A109 @provisioning
  Scenario: Submitting for evaluation shows a confirmation popup
    Given a running assessment lab for a fresh student
    Then submitting for evaluation shows a confirmation popup

  @A110
  Scenario: A user can manually complete the assessment
    Then this cooldown case needs a live attempt or cooldown state

  @A111
  Scenario: The consent checkbox is available in the complete popup
    Then this case needs a live attempt, an integration session, or a course-management action

  @A112
  Scenario: Access lab button pops up when clicked
    Then this cooldown case needs a live attempt or cooldown state

  @A113 @provisioning
  Scenario: The lab credentials show a username and password
    Given a running assessment lab for a fresh student
    Then the lab credentials panel shows a username and password

  @A114 @provisioning
  Scenario: The lab page offers a change-password control
    Given a running assessment lab for a fresh student
    Then the lab page offers a change password control

  @A128 @provisioning
  Scenario: A new submission cannot be started while one is in progress
    Given a running assessment lab for a fresh student
    Then a new submission cannot be started while one is in progress

  @CGAP-A-8
  Scenario: A student cannot access the assessment admin pages
    Given a student is enrolled in the assessment course
    When the student opens the assessment activity
    Then the student sees no assessment admin controls and cannot open manage challenges

  @CGAP-A-9 @provisioning
  Scenario: The assessment auto-completes with a final result and the consent gate is wired
    Given a student is enrolled in the assessment course
    When the student starts the assessment
    Then the assessment control panel is shown
    And the assessment lab reaches Running
    When the student submits the assessment attempt
    Then the attempt is evaluated
    And the assessment auto-completes with a final result
    And the complete-assessment consent gate is present and wired

  Scenario: The assessment landing offers the attempt controls
    When I open the assessment activity
    Then the assessment landing shows the attempt controls and details

  @provisioning
  Scenario: A student submits an assessment attempt and it evaluates end to end
    Given a student is enrolled in the assessment course
    When the student starts the assessment
    Then the assessment control panel is shown
    And the assessment lab reaches Running
    When the student submits the assessment attempt
    Then the attempt is evaluated

