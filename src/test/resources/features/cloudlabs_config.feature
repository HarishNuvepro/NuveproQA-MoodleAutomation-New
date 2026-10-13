# Kiwi sheet: Content_Integration_Git_URL (@CIG-*).
# Description / instructions content source (manual vs git URL) on cloudlabs activity forms.
#
# Difficulty cases live in difficulty_level_configuration.feature (@DLC-*).
#
# Run (PowerShell — use -Pwatch so ONLY RunCucumberTest runs this file):
#   .\mvnw.cmd test -Pwatch "-Dcucumber.features=src/test/resources/features/cloudlabs_config.feature"
# Git URL only: add "-Dcucumber.tags=@contentsource and not @deviation"

Feature: Cloudlabs activity config - description source and git URL

  @CIG-1 @contentsource @cloudlabsconfig
  Scenario: Description source toggles manual entry and git URL on the lab form
    When I open a new cloudlabs activity form
    Then the description source toggles the git URL field

  @CIG-2 @contentsource @cloudlabsconfig
  Scenario: Manual description source works for playground guided and assessment lab types
    When I open a new cloudlabs activity form for lab type "playground"
    Then the description source toggles the git URL field
    When I open a new cloudlabs activity form for lab type "guided"
    Then the description source toggles the git URL field
    When I open a new cloudlabs activity form for lab type "challenge"
    Then the description source toggles the git URL field

  @CIG-3 @contentsource @cloudlabsconfig
  Scenario: Git URL description source works for playground guided and assessment lab types
    When I open a new cloudlabs activity form for lab type "playground"
    Then the description source toggles the git URL field
    When I open a new cloudlabs activity form for lab type "guided"
    Then the description source toggles the git URL field
    When I open a new cloudlabs activity form for lab type "challenge"
    Then the description source toggles the git URL field

  @CIG-7 @contentsource @deviation @cloudlabsconfig
  Scenario: DEVIATION - manual expects playground to hide description source but the form shows it
    When I open a new cloudlabs activity form for lab type "playground"
    Then the description source toggles the git URL field

  @CIG-10 @contentsource @cloudlabsconfig
  Scenario: Guided project content source toggles the git URL field
    When I open a new cloudlabs activity form for lab type "guided"
    Then the guided content source toggles its git URL field

  @CIG-11 @contentsource @cloudlabsconfig
  Scenario: Assessment instructions source toggles the git URL field
    When I open a new challenge activity form for config
    Then the assessment content source toggles its git URL field
