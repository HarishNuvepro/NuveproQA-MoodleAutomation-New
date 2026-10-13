# Moodle–CloudLabs integration (Kiwi sheet: 13 manual cases; automation @MCI-1 … @MCI-11 only).
# One tag per scenario — same @MCI-n as Automation_Ref on that sheet.
#
# What this module covers (admin smoke only):
#   • @MCI-1: Site administration → Plugins → Plugin overview → Additional plugins —
#     Nuvepro Cloudlabs (mod_cloudlabs) listed and enabled
#   • Site admin: Cloud Server Settings (URL, company, credentials, debug log, support email)
#   • Site admin: Lab Templates list and add-template form fields
#   • Course: cloudlabs activity in the activity chooser; mod_form lab types (playground / guided / assessment)
#   • Reports index lists CloudLabs reports when the reports plugin is present
#
# @MCI-12 / @MCI-13 — not in this feature (manual or not required in automation suite).
#
# Preconditions: .env MOODLE_* login; COURSE_ID for @MCI-7 / @MCI-9 / @MCI-10 (course mod_form + chooser).
#
# Run (PowerShell — use .\mvnw.cmd):
#   Serial:     .\mvnw.cmd test -Pwatch "-Dcucumber.features=src/test/resources/features/moodle_cloudlabs_integration.feature"
#   One case:   .\mvnw.cmd test -Pwatch -Dheadless=false "-Dcucumber.tags=@MCI-1" "-Dcucumber.features=src/test/resources/features/moodle_cloudlabs_integration.feature"
#   All @mci:   .\mvnw.cmd test -Pwatch "-Dcucumber.tags=@mci" "-Dcucumber.features=src/test/resources/features/moodle_cloudlabs_integration.feature"
#
# Parallel (11 admin smoke scenarios — no @provisioning; safe to run concurrently):
#   .\mvnw.cmd test -Pparallel "-DsuiteXmlFile=testng-parallel5.xml" "-Dparallel.browsers=5" "-Dcucumber.features=src/test/resources/features/moodle_cloudlabs_integration.feature" "-Dcucumber.tags=@mci"
#
# Headed: add -Pwatch -Dheadless=false. Always pass -Dcucumber.features for this file on full mvn test.

Feature: Moodle — CloudLabs integration

  # --- Plugin presence (@MCI-1: plugin overview, not Cloud Server Settings) ---

  @MCI-1 @mci
  Scenario: Nuvepro Cloudlabs plugin is available and enabled in plugin overview
    When admin opens Site administration plugin overview for additional plugins
    Then the Nuvepro Cloudlabs activity plugin is available and enabled

  # --- Cloud Server Settings (Site administration → CloudLabs) ---

  @MCI-2 @mci
  Scenario: Cloud Server Settings is available after the activity plugin is installed
    When admin opens the cloudlabs cloud server settings page
    Then the cloudlabs cloud server settings page is available

  @MCI-3 @mci
  Scenario: Cloud Server Settings exposes URL company and credential fields
    When admin opens the cloudlabs cloud server settings page
    Then the cloud server settings expose connection fields for URL company and credentials

  @MCI-4 @mci
  Scenario: Cloud Server Settings offers a debug log toggle
    When admin opens the cloudlabs cloud server settings page
    Then the cloud server settings offer a debug log toggle

  @MCI-5 @mci
  Scenario: Cloud Server Settings includes a support email field
    When admin opens the cloudlabs cloud server settings page
    Then the cloud server settings include a support email field

  # --- Lab Templates (site admin plan configuration) ---

  @MCI-6 @mci
  Scenario: Lab Templates appears in site administration after plugin install
    When admin looks for Lab Templates in site administration
    Then the Lab Templates admin list is reachable

  @MCI-8 @mci
  Scenario: Lab template form includes plan team and access fields
    When admin opens the add lab template form
    Then the add lab template form shows its core configuration fields
    And the lab template form includes access keys or links required for lab creation

  # --- Lab activity (course mod_form + activity chooser) ---

  @MCI-7 @mci
  Scenario: Lab templates act as the plan configuration for lab activities
    When admin opens a new cloudlabs lab activity form for integration
    Then the lab activity form links activities to a lab template plan

  @MCI-9 @mci
  Scenario: CloudLabs Lab appears in the Activity Chooser when adding a course activity
    # Manual: course → Turn editing on → Add activity → Activity Chooser (modal), not mod.php list.
    When admin opens the activity chooser to add a lab activity
    Then the cloudlabs lab activity is listed in the activity chooser

  @MCI-10 @mci
  Scenario: The lab activity supports playground guided and assessment types
    When admin opens a new cloudlabs lab activity form for integration
    Then the lab activity form offers playground guided and assessment lab types

  # --- Reports ---

  @MCI-11 @mci
  Scenario: CloudLabs reports are listed when the reports plugin is installed
    When admin opens the Moodle reports index
    Then cloudlabs-related reports are listed when the reports plugin is installed
