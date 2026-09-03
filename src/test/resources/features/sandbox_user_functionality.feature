# Sandbox student user functionality — end-to-end scenarios.
# Uses the same throwaway suite course as create/actions (not COURSE_ID).
# Labs/users are kept. Cleanup: @sandbox-cleanup.
#
# Preferred: run sandbox_create.feature first so matching activities are reused
# (sandbox_create_yes / sandbox_create_no / sandbox_desc_git).
#
# Speed: one cloud lab per template, not per scenario. Scenarios sharing a lab run single-file
# (one lane per named student), so the Account cases are split over TWO Account labs to halve
# that chain — account_lab2 exists only to shorten the critical path, same template as account_lab.
#   account_lab  → @SB-user-1 then 4/5/6/10 reuse that running lab (Continue).
#   account_lab2 → @SB-user-12 then 13/18 reuse that running lab.
#   git_lab      → @SB-user-2
#   vm_vpat      → @SB-user-14 (iframe + speed test + fullscreen + new tab)
#   catalog_unlimited → @SB-user-19 (Proceed + unlimited + invalid Start/Create)
# Stop (@SB-user-3) and empty landing (@SB-user-7) keep a fresh student so they
# do not stop/steal the shared Account lab.
# Student console cases moved from sandbox_actions.feature (same run tags):
#   @SB-manage-26 cancel Start, 27 usage, 28 landing duration, 29 password,
#   31 refresh, 34 invalid-template create error, 35 expired duration.
#
# Validation notes (confirmed with QA):
# 1) Description / Instructions = create settings (manual text or git-populated content)
# 2) Duration / Credentials — presence OK for now; exact CloudLabs portal values deferred
# 3) Lab Info — from LAB TEMPLATE (often populated; not normally empty)
# 4) Notes (@SB-user-6) — requires lab-template enable_lab_notes ON
# 5) Duration (@SB-user-10) — CloudLabs extendDuration +1 hour; assert remaining/allotted increased
#    (negative expire is not supported on this platform)
#
# Run this file: .\mvnw.cmd test -Pwatch "-Dcucumber.features=src/test/resources/features/sandbox_user_functionality.feature"
# Run one:       .\mvnw.cmd test -Pwatch "-Dcucumber.tags=@SB-user-1"

Feature: Sandbox — student user functionality

  @SB-user-1 @provisioning
  Scenario: Student completes sandbox landing, lab console checks and Access Lab redirect
    When admin creates a sandbox activity using "sandbox_user_lab"
    And a named student "account_lab" is enrolled in the created sandbox course
    When the student opens the created sandbox activity
    Then the student can access the created sandbox activity
    And the student sees sandbox activity details matching the create settings
    When the student starts the sandbox and opens the lab console
    And the student starts the lab and waits until it is loaded
    Then the sandbox lab is loaded on the console
    And the student verifies sandbox console instructions duration credentials and lab info
    When the student opens Access Lab from Jump to Console on the sandbox
    Then the student is redirected to the sandbox cloud lab platform

  # Same E2E flow with Description/Instructions sourced from Git (reuses sandbox_desc_git if present).
  @SB-user-2 @provisioning
  Scenario: Student completes sandbox flow with git description and instructions
    When admin creates a sandbox activity using "sandbox_user_desc_git"
    And a named student "git_lab" is enrolled in the created sandbox course
    When the student opens the created sandbox activity
    Then the student can access the created sandbox activity
    And the student sees sandbox activity details matching the create settings
    When the student starts the sandbox and opens the lab console
    And the student starts the lab and waits until it is loaded
    Then the sandbox lab is loaded on the console
    And the student verifies sandbox console instructions duration credentials and lab info
    When the student opens Access Lab from Jump to Console on the sandbox
    Then the student is redirected to the sandbox cloud lab platform

  @SB-user-3 @provisioning
  Scenario: Student stops the sandbox lab from the console
    When admin creates a sandbox activity using "sandbox_user_lab"
    And a fresh student is enrolled in the created sandbox course
    When the student opens the created sandbox activity
    And the student starts the sandbox and opens the lab console
    And the student starts the lab and waits until it is loaded
    And the student stops the sandbox lab from the console
    Then the sandbox lab is stopped for the student

  @LT-59 @provisioning
  Scenario: Four-step progress is displayed while a sandbox lab is created
    When admin creates a sandbox activity using "sandbox_user_lab"
    And a fresh student is enrolled in the created sandbox course
    When the student opens the created sandbox activity
    And the student starts the sandbox and observes create progress

  @LT-60 @provisioning
  Scenario: Four-step progress is displayed while a sandbox lab is started
    When admin creates a sandbox activity using "sandbox_user_lab"
    And a fresh student is enrolled in the created sandbox course
    When the student opens the created sandbox activity
    And the student starts the sandbox and opens the lab console
    And the student starts the lab and observes start progress
    Then the sandbox lab is loaded on the console

  @SB-user-4 @provisioning
  Scenario: Student Continue resumes the sandbox lab console
    When admin creates a sandbox activity using "sandbox_user_lab"
    And a named student "account_lab" is enrolled in the created sandbox course
    When the student opens the created sandbox activity
    And the student starts the sandbox and opens the lab console
    And the student starts the lab and waits until it is loaded
    When the student returns to the sandbox landing and continues
    Then the sandbox lab console is open for the student

  # No cloud lab needed — role gate on the landing only.
  @SB-user-5
  Scenario: Student cannot manage the sandbox activity
    When admin creates a sandbox activity using "sandbox_user_lab"
    And a named student "account_lab" is enrolled in the created sandbox course
    When the student opens the created sandbox activity
    Then the student can access the created sandbox activity
    And the student cannot manage the sandbox activity

  # Requires lab-template "enable lab notes" ON (same Account template used by sandbox_user_lab).
  @SB-user-6 @provisioning
  Scenario: Student saves a sandbox lab note and it persists
    When admin creates a sandbox activity using "sandbox_user_lab"
    And a named student "account_lab" is enrolled in the created sandbox course
    When the student opens the created sandbox activity
    And the student starts the sandbox and opens the lab console
    And the student starts the lab and waits until it is loaded
    And the student saves a sandbox lab note "SB-USER note persist 789"
    Then the sandbox lab note persists after the student reopens the console

  # Fresh student — no lab yet on this activity.
  @SB-user-7
  Scenario: Fresh student landing shows Start Sandbox only with activity details
    When admin creates a sandbox activity using "sandbox_user_lab"
    And a fresh student is enrolled in the created sandbox course
    When the student opens the created sandbox activity
    Then the student can access the created sandbox activity
    And the student sees sandbox activity details matching the create settings
    And the fresh student landing shows Start Sandbox only without Continue or Jump

  # PDF download on console Instructions (create Export PDF Yes/No). Help video is NOT supported on
  # sandbox/playground — only Practice Project / Assessment (do not assert help video here).
  @SB-user-8 @provisioning
  Scenario: Student sees PDF download when Export PDF is Yes
    When admin creates a sandbox activity using "sandbox_create_yes"
    And a named student "pdf_yes" is enrolled in the created sandbox course
    When the student opens the created sandbox activity
    And the student starts the sandbox and opens the lab console
    And the student starts the lab and waits until it is loaded
    Then the student PDF download visibility matches the create settings
    And the student downloads the instructions PDF from the console

  @SB-user-9 @provisioning
  Scenario: Student does not see PDF download when Export PDF is No
    When admin creates a sandbox activity using "sandbox_create_no"
    And a named student "pdf_no" is enrolled in the created sandbox course
    When the student opens the created sandbox activity
    And the student starts the sandbox and opens the lab console
    And the student starts the lab and waits until it is loaded
    Then the student PDF download visibility matches the create settings

  # CloudLabs API: extendDuration +1 hour (negative expire is not supported on this platform).
  @SB-user-10 @provisioning
  Scenario: Student duration increases after CloudLabs extend by 1 hour
    When admin creates a sandbox activity using "sandbox_user_lab"
    And a named student "account_lab" is enrolled in the created sandbox course
    When the student opens the created sandbox activity
    And the student starts the sandbox and opens the lab console
    And the student starts the lab and waits until it is loaded
    When the sandbox lab duration is extended by 1 hour via the CloudLabs API
    And the student returns to the sandbox landing
    Then the student sandbox duration increased by about 1 hour

  @LT-9 @provisioning
  Scenario: Template duration notification appears at its configured threshold
    Given admin creates a notification-enabled disposable Sandbox activity
    And a fresh student is enrolled in the created sandbox course
    When the student opens the created sandbox activity
    And the student starts the sandbox and opens the lab console
    And the student starts the lab and waits until it is loaded
    When the lab duration is reduced into the template notification threshold
    Then the configured template duration notification appears

  @SB-user-11 @fresh_activity
  Scenario: Student Read More opens the full sandbox description
    When admin creates a sandbox activity using "sandbox_long_desc"
    And a fresh student is enrolled in the created sandbox course
    When the student opens the created sandbox activity
    Then the student can access the created sandbox activity
    When the student opens Read More on the sandbox landing
    Then the Read More modal shows the full sandbox description

  @SB-user-12 @provisioning
  Scenario: Student Support tab provides a working contact-support link
    When admin creates a sandbox activity using "sandbox_user_lab"
    And a named student "account_lab2" is enrolled in the created sandbox course
    When the student opens the created sandbox activity
    And the student starts the sandbox and opens the lab console
    And the student starts the lab and waits until it is loaded
    Then the student support link on the lab console is available and working

  @SB-user-13 @provisioning
  Scenario: Console Back and browser Back return to the sandbox activity landing
    When admin creates a sandbox activity using "sandbox_user_lab"
    And a named student "account_lab2" is enrolled in the created sandbox course
    When the student opens the created sandbox activity
    And the student starts the sandbox and opens the lab console
    And the student starts the lab and waits until it is loaded
    Then the console Back button returns to the sandbox activity landing
    When the student opens the lab console again from the landing
    Then browser Back returns to the sandbox activity landing

  @SB-user-14 @provisioning
  Scenario: VM sandbox iframe, Speed Test, fullscreen and new tab
    When admin creates a sandbox activity using "sandbox_vm_vpat"
    And a named student "vm_vpat" is enrolled in the created sandbox course
    When the student opens the created sandbox activity
    And the student starts the sandbox and continues through catalog details if shown
    Then the VM lab is loaded inside an iframe
    And the student speed test completes and displays values
    And the student opens the lab in a new browser tab
    And the student switches the lab to full screen

  @SB-user-18 @provisioning
  Scenario: Allotted consumed and remaining duration on the console match CloudLabs
    When admin creates a sandbox activity using "sandbox_user_lab"
    And a named student "account_lab2" is enrolled in the created sandbox course
    When the student opens the created sandbox activity
    And the student starts the sandbox and opens the lab console
    And the student starts the lab and waits until it is loaded
    Then allotted consumed and remaining duration on the console match CloudLabs

  @SB-user-19 @provisioning
  Scenario: Catalog UI Proceed, unlimited duration, and invalid Start/Create again
    When admin creates a sandbox activity using "sandbox_catalog_unlimited"
    And a named student "catalog_unlimited" is enrolled in the created sandbox course
    When the student opens the created sandbox activity
    And the student starts the sandbox and continues through catalog details if shown
    Then the sandbox lab is running after catalog proceed
    And the sandbox lab duration is unlimited
    When admin opens Manage Labs for the created sandbox activity
    Then starting the running sandbox lab again is prevented
    And creating the sandbox lab again for that user is prevented

  @SB-manage-26
  Scenario: Cancelling Start Sandbox confirmation creates no lab
    When admin creates a sandbox activity using "sandbox_user_lab"
    Given a fresh student enrolled for Manage Labs admin actions
    When the student opens the sandbox activity landing
    And the student clicks Start Sandbox and cancels the confirmation
    Then no sandbox lab was created for the student

  @SB-manage-27 @provisioning
  Scenario: Student Lab Usage shows valid cost values
    When admin creates a sandbox activity using "sandbox_user_lab"
    Given a shared Manage Labs student with an Active lab
    When the student starts the sandbox lab until it is running
    And the student opens the Lab Usage popup on the console
    And the student requests the lab cost on the console
    Then the student sees valid lab cost values on the console

  @SB-manage-28 @provisioning
  Scenario: Student landing shows allotted consumed and remaining duration
    When admin creates a sandbox activity using "sandbox_user_lab"
    Given a shared Manage Labs student with an Active lab
    When the student starts the sandbox lab until it is running
    And the student opens the sandbox activity landing
    Then the student sandbox landing shows consumed and remaining duration

  @SB-manage-29 @provisioning
  Scenario: Student can change the lab password on the console
    When admin creates a sandbox activity using "sandbox_user_lab"
    Given a shared Manage Labs student with an Active lab
    When the student starts the sandbox lab until it is running
    And the student opens the Lab Credentials popup on the console
    And the student changes the sandbox lab password on the console
    Then the student lab password update is accepted

  @SB-manage-31 @provisioning
  Scenario: Student refresh lab keeps the lab running
    When admin creates a sandbox activity using "sandbox_user_lab"
    Given a shared Manage Labs student with an Active lab
    When the student starts the sandbox lab until it is running
    And the student refreshes the sandbox lab on the console
    Then the student sandbox lab remains running after refresh

  @SB-manage-34 @provisioning @fresh_activity
  Scenario: Invalid gcp.organization.account template surfaces missing required argument
    When admin creates a sandbox activity using "sandbox_invalid_gcp_org"
    Given a fresh student enrolled for Manage Labs admin actions
    When the student tries to create a sandbox lab
    Then the sandbox lab create error mentions missing required argument

  @SB-manage-35 @provisioning
  Scenario: Expired-duration lab shows the expired message
    When admin creates a sandbox activity using "sandbox_user_lab"
    Then the expired-duration lab state is verified
