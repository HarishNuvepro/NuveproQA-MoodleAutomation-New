# Kiwi sheet: Difficulty_Level_Configuration — 16 manual rows, 13 Cucumber scenarios (@DLC-1 … @DLC-14, no @DLC-7).
# Three CGAP rows in Kiwi trace to existing @DLC-3 / @DLC-4 / @DLC-5 (not separate scenarios).
# Admin Cloud Server Settings difficulty list + challenge activity form dropdown.
#
# @DLC-* maps to manual row (row N -> @DLC-(N-1)). Mutating cases use @difficultyrestore (After hook).
# Live site may show Beginner/Intermediate/Advanced/Expert — manual text may say Practitioner/Proficient (@DLC-3 Deviation).
#
# Run (PowerShell):
#   Serial (headed): .\mvnw.cmd test -Pwatch -Dheadless=false "-Dcucumber.features=src/test/resources/features/difficulty_level_configuration.feature" "-Dcucumber.tags=@dlc"
#  .\mvnw.cmd test -Pparallel "-DsuiteXmlFile=testng-parallel5.xml" "-Dparallel.browsers=5" "-Dcucumber.features=src/test/resources/features/difficulty_level_configuration.feature" "-Dcucumber.tags=@dlc"
#  .\mvnw.cmd test -Pwatch -Dheadless=false "-Dcucumber.tags=@DLC-11"

Feature: Difficulty level configuration

  @DLC-1 @difficulty @cloudlabsconfig @dlc
  Scenario: Default difficulty levels appear on the challenge activity form
    When I open a new challenge activity form for config
    Then the difficulty level select offers the configured levels and defaults to Beginner

  @DLC-2 @difficulty @cloudlabsconfig @dlc
  Scenario: Difficulty Level Settings appear on Cloud Server Settings
    When admin opens the cloud server settings
    Then the difficulty level settings section is present

  @DLC-3 @difficulty @deviation @cloudlabsconfig @dlc
  Scenario: DEVIATION - documented Practitioner levels vs live admin defaults
    When admin opens the cloud server settings
    Then the admin difficulty levels field shows the documented Practitioner set or the live default

  @DLC-4 @difficulty @difficultyrestore @cloudlabsconfig @dlc
  Scenario: Empty difficulty admin settings fall back to defaults on the activity form
    When admin clears cloudlabs difficulty levels and saves
    And I open a new challenge activity form for config
    Then the difficulty level select offers the configured levels and defaults to Beginner

  @DLC-5 @difficulty @difficultyrestore @cloudlabsconfig @dlc
  Scenario: Custom string difficulty levels flow to the activity form
    When admin saves cloudlabs difficulty levels to "Easy,Medium,Hard"
    And I open a new challenge activity form for config
    Then the difficulty level dropdown lists "Easy,Medium,Hard"

  @DLC-6 @difficulty @difficultyrestore @cloudlabsconfig @dlc
  Scenario: Custom numeric difficulty labels flow to the activity form
    When admin saves cloudlabs difficulty levels to "1,2,3"
    And I open a new challenge activity form for config
    Then the difficulty level dropdown lists "1,2,3"

  @DLC-8 @difficulty @difficultyrestore @cloudlabsconfig @dlc
  Scenario: A single custom difficulty level appears alone on the activity form
    When admin saves cloudlabs difficulty levels to "SoloLevel"
    And I open a new challenge activity form for config
    Then the difficulty level dropdown has 1 options

  @DLC-9 @difficulty @difficultyrestore @cloudlabsconfig @dlc
  Scenario: Ten custom difficulty levels all appear on the activity form
    When admin saves cloudlabs difficulty levels to "L1,L2,L3,L4,L5,L6,L7,L8,L9,L10"
    And I open a new challenge activity form for config
    Then the difficulty level dropdown has 10 options

  @DLC-10 @DLC-batch2-low @easybatch2 @difficulty @difficultyrestore @cloudlabsconfig @dlc
  Scenario: Special-character difficulty labels flow to the activity form
    When admin saves cloudlabs difficulty levels to "Easy*,Medium#"
    And I open a new challenge activity form for config
    Then the difficulty level dropdown lists "Easy*,Medium#"

  @DLC-11 @difficulty @difficultyrestore @dlcactivity @cloudlabsconfig @dlc
  Scenario: Saved challenge activity keeps its difficulty after admin changes site levels
    When admin saves cloudlabs difficulty levels to "Beginner,Intermediate,Advanced,Expert"
    And admin saves a challenge activity with difficulty "Intermediate"
    # Relabel site levels but keep "Intermediate" so the saved activity label remains valid on the form.
    When admin saves cloudlabs difficulty levels to "Easy,Intermediate,Hard"
    And admin opens the saved challenge activity for editing
    Then the saved challenge activity still shows difficulty "Intermediate"

  @DLC-12 @difficulty @difficultyrestore @dlcactivity @cloudlabsconfig @dlc
  Scenario: Duplicating a challenge activity preserves customized difficulty on the copy
    When admin saves cloudlabs difficulty levels to "Easy,Medium,Hard"
    And admin saves a challenge activity with difficulty "Hard"
    When admin duplicates the saved challenge activity on the course
    And admin opens the saved challenge activity for editing
    Then the saved challenge activity still shows difficulty "Hard"

  @DLC-13 @difficulty @difficultyrestore @cloudlabsconfig @dlc
  Scenario: Restoring default four admin difficulty levels applies on a new challenge form
    When admin saves cloudlabs difficulty levels to "Easy,Medium,Hard"
    And admin restores cloudlabs default four difficulty levels and saves
    And I open a new challenge activity form for config
    Then the difficulty level select offers the configured levels and defaults to Beginner

  @DLC-14 @CGAP-DLC-4 @difficulty @difficultyrestore @dlcactivity @cloudlabsconfig @dlc
  Scenario: Removing an in-use admin difficulty level does not break the saved activity
    When admin saves cloudlabs difficulty levels to "Easy,Medium,Hard"
    And admin saves a challenge activity with difficulty "Medium"
    When admin saves cloudlabs difficulty levels to "Easy,Hard"
    And admin opens the saved challenge activity for editing
    Then the challenge edit form difficulty dropdown lists "Easy,Hard" only
    And the saved challenge difficulty selects a remaining configured level
    And the saved challenge activity edit form loads without errors
