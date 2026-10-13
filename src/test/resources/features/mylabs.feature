# My Labs (theme_nuvetheme /theme/nuvetheme/mylabs.php).
# Fast (no cloud): .\mvnw.cmd test "-Dcucumber.features=src/test/resources/features/mylabs.feature" "-Dcucumber.tags=not @provisioning"
# Provisioning (@ML-3..5): .\mvnw.cmd test -Pslow "-Dcucumber.features=src/test/resources/features/mylabs.feature" "-Dcucumber.tags=@mylabsprovision"
# Theme toggles restore after @mylabstheme. Env student cases need MOODLE_STUDENT_PASS; provision uses WS user.

Feature: My Labs page

  @ML-page @easybatch2 @mylabs
  Scenario: The My Labs page lists lab cards with a search
    When admin opens the My Labs page
    Then the My Labs page shows lab cards and a search field

  @ML-unauth @mylabs
  Scenario: My Labs requires authentication
    Then an unauthenticated user is redirected to login from My Labs

  @ML-search @mylabs
  Scenario: The My Labs search treats script input as text (XSS-safe)
    When admin opens the My Labs page
    Then entering a script payload in the My Labs search does not execute it

  @ML-6 @CGAP-ML-3 @easybatch2 @mylabs
  Scenario: My Labs search filters cards by partial lab name
    When admin opens the My Labs page
    Then My Labs search filters cards by a partial lab name

  @ML-7 @mylabs
  Scenario: My Labs layout works on a mobile viewport
    When the browser viewport is mobile size on My Labs
    Then the My Labs page shows lab cards and a search field

  @ML-15 @mylabs
  Scenario: NuveTheme exposes My Labs configuration controls
    Given admin opens NuveTheme settings related to My Labs
    Then NuveTheme exposes My Labs show activity-type and status-text controls

  @ML-16 @ML-17 @mylabs @mylabstheme
  Scenario: My Labs navigation hides when disabled and direct URL is redirected
    When admin sets Show My Labs in theme settings to "No"
    And student opens the Moodle home page
    Then the student "does not" see My Labs in the site navigation
    When student opens the My Labs page via direct URL
    Then direct access to My Labs is blocked when Show My Labs is disabled

  @ML-18 @mylabs @mylabstheme
  Scenario: My Labs navigation appears when enabled in theme settings
    When admin sets Show My Labs in theme settings to "Yes"
    And student opens the Moodle home page
    Then the student "does" see My Labs in the site navigation

  @ML-19 @mylabs @mylabstheme
  Scenario: Activity status text is hidden on cards when the theme setting is off
    When admin sets Show activity status text on My Labs cards to "No"
    And admin opens the My Labs page
    Then My Labs cards "do not" show activity status text

  @ML-20 @mylabs @mylabstheme
  Scenario: Activity status text appears on cards when the theme setting is on
    When admin sets Show activity status text on My Labs cards to "Yes"
    And admin opens the My Labs page
    Then My Labs cards "do" show activity status text

  @ML-3 @mylabs @mylabsprovision @provisioning @freshlab
  Scenario: Student opens lab details from a My Labs card
    Given a freshly enrolled user
    When I create a lab for that user from Manage Labs
    Then that user's lab reaches Running
    When admin sets Show My Labs in theme settings to "Yes"
    And admin ensures My Labs includes Sandbox activity type
    And that user opens the My Labs page
    And that user opens lab details from the My Labs card
    Then the cloudlabs activity view opens for that user

  @ML-4 @mylabs @mylabsprovision @provisioning @freshlab
  Scenario: Student launches a lab from the My Labs page
    Given a freshly enrolled user
    When I create a lab for that user from Manage Labs
    Then that user's lab reaches Running
    When admin sets Show My Labs in theme settings to "Yes"
    And admin ensures My Labs includes Sandbox activity type
    And that user opens the My Labs page
    And that user launches a lab from the My Labs page
    Then the cloudlabs activity view opens for that user

  @ML-5 @mylabs @mylabsprovision @provisioning @freshlab
  Scenario: Student stops a running lab from the My Labs page
    Given a freshly enrolled user
    When I create a lab for that user from Manage Labs
    Then that user's lab reaches Running
    When admin sets Show My Labs in theme settings to "Yes"
    And admin ensures My Labs includes Sandbox activity type
    And that user opens the My Labs page
    And that user launches a lab from the My Labs page
    Then that user's lab reaches Running
    And that user opens the My Labs page
    And that user stops a running lab from the My Labs page
    Then that user's lab reaches Stopped

  @ML-10 @CGAP-ML-1 @easybatch2 @mylabs
  Scenario: Empty state when the user has no labs assigned
    Given a freshly enrolled user with no lab
    When admin sets Show My Labs in theme settings to "Yes"
    And that user opens the My Labs page allowing empty state
    Then the My Labs empty state is shown for that user

  @ML-21 @easybatch2 @mylabs @mylabstheme
  Scenario: My Labs lists sandbox activities when only Sandbox type is selected
    When admin sets Show My Labs in theme settings to "Yes"
    And admin sets My Labs activity types to only "Sandbox"
    And admin opens the My Labs page
    Then every visible My Labs card is a sandbox activity

  @ML-22 @easybatch2 @mylabs @mylabstheme
  Scenario: My Labs sandbox-only filter matches row 22
    When admin sets Show My Labs in theme settings to "Yes"
    And admin sets My Labs activity types to only "Sandbox"
    And admin opens the My Labs page
    Then every visible My Labs card is a sandbox activity

  @ML-23 @easybatch2 @mylabs @mylabstheme
  Scenario: My Labs lists assessment activities when only Assessment type is selected
    When admin sets Show My Labs in theme settings to "Yes"
    And admin sets My Labs activity types to only "Assessment"
    And admin opens the My Labs page
    Then every visible My Labs card is an assessment activity

  @ML-26 @easybatch2 @mylabs @mylabstheme
  Scenario: My Labs lists practice project activities when only Practice Project type is selected
    When admin sets Show My Labs in theme settings to "Yes"
    And admin sets My Labs activity types to only "Practice Project"
    And admin opens the My Labs page
    Then every visible My Labs card is a practice project activity

  @ML-24 @easybatch2 @mylabs @mylabstheme
  Scenario: My Labs shows cards when all activity types are selected
    When admin sets Show My Labs in theme settings to "Yes"
    And admin sets My Labs activity types to all configured types
    And admin opens the My Labs page
    Then the My Labs page shows lab cards and a search field

  @ML-25 @easybatch2 @mylabs @mylabstheme
  Scenario: My Labs with no activity types selected shows no lab cards
    When admin sets Show My Labs in theme settings to "Yes"
    And admin clears My Labs activity type selection
    And admin opens the My Labs page
    Then the My Labs page shows no lab cards

  @ML-45 @CGAP-ML-3 @easybatch2 @mylabs
  Scenario: Clearing My Labs search restores the full card list
    When admin opens the My Labs page
    Then My Labs search clear restores the full card list
