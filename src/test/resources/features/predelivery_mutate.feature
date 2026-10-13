# Lab Readiness saves on throwaway sandbox activities (@predelivery-mutate @easybatch2). Activities deleted in @After.
# Run: mvnw test -Dcucumber.features=src/test/resources/features/predelivery_mutate.feature

Feature: Pre-Delivery Lab Readiness saves

  @PDL-save-first @PDL-6 @PDL-9 @predelivery @predelivery-mutate @easybatch2
  Scenario: First checklist save with screenshot is recorded in the audit log
    Given a throwaway sandbox activity exists for lab readiness saves
    When admin opens the lab readiness page for the throwaway activity
    And admin saves a complete lab readiness checklist with screenshot
    Then the checklist audit log records the submission

  @PDL-audit-modify @PDL-10 @predelivery @predelivery-mutate @easybatch2
  Scenario: Modifying the checklist adds another audit log entry
    Given a throwaway sandbox activity exists for lab readiness saves
    When admin opens the lab readiness page for the throwaway activity
    And admin saves a complete lab readiness checklist with screenshot
    And admin modifies the lab readiness checklist and saves again
    Then the checklist audit log records the modification

  @PDL-visibility @PDL-7 @PDL-8 @predelivery @predelivery-mutate @easybatch2
  Scenario: Checklist completion controls student visibility of the activity
    Given a throwaway sandbox activity exists for lab readiness saves
    When admin opens the lab readiness page for the throwaway activity
    Then the throwaway activity is hidden from students before checklist completion
    When admin saves a complete lab readiness checklist with screenshot
    Then the throwaway activity is visible to enrolled students on the course page

  @PDL-screenshot @PDL-11 @predelivery @predelivery-mutate @easybatch2
  Scenario: Re-uploading a screenshot is audited
    Given a throwaway sandbox activity exists for lab readiness saves
    When admin opens the lab readiness page for the throwaway activity
    And admin saves a complete lab readiness checklist with screenshot
    And admin re-uploads a different lab readiness screenshot and saves
    Then the checklist audit log records the screenshot change

  @PDL-support-clear @PDL-12 @predelivery @predelivery-mutate @easybatch2
  Scenario: Support engineer name clears when the checklist page is reopened
    Given a throwaway sandbox activity exists for lab readiness saves
    When admin opens the lab readiness page for the throwaway activity
    And admin saves a complete lab readiness checklist with screenshot
    And admin reopens the lab readiness page for the throwaway activity
    Then the support engineer name field is cleared on reopen

  @CGAP-PDL-4 @PDL-77 @predelivery @predelivery-mutate @easybatch2
  Scenario: A backend warning appears after checklist regression post-save
    Given a throwaway sandbox activity exists for lab readiness saves
    When admin opens the lab readiness page for the throwaway activity
    And admin saves a complete lab readiness checklist with screenshot
    And admin changes checklist state after a prior full save
    Then a backend warning banner is shown on the lab readiness page
