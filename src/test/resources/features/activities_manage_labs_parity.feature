# Activities sheet — Manage Labs parity (slim replays; canonical @SB-manage-* tags live on
# sandbox.feature only — do NOT duplicate @SB-manage-* here or Cucumber runs twice).
# Run batch: .\mvnw.cmd test "-Dcucumber.tags=@activities_parity and not @provisioning"

Feature: Activities sheet Manage Labs parity

  @ACT-parity @activities_parity @ACT-manage-23
  Scenario: Manage Labs shows Active and No labs listing chrome
    When admin opens Manage Labs Active labs tab
    Then the labs are listed with status counters
    And per-lab action controls are available
    And the not-created users tab offers Create but not lab actions

  @ACT-parity @activities_parity @ACT-manage-15
  Scenario: Manage Labs actions stay gated with no lab row selected
    When admin opens Manage Labs Active labs tab
    Then Start Stop Delete and Sync are disabled with no lab selected
    When admin opens Manage Labs No labs tab
    Then No labs Create is disabled with no user selected

  @ACT-parity @activities_parity @ACT-manage-18
  Scenario: Manage Labs report export control is available
    When admin opens Manage Labs Active labs tab
    Then the Manage Labs report export control is available
