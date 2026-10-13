# Lab Templates (cloudlabs labtemplates.php / addlabtemplate.php).
# This first conversion batch is read-only except for invalid form submissions, which must not
# create a template. Safe throwaway-template CRUD follows once Plan ID / Team ID test data is known.
# One tag per scenario — the same @LT-* as Moodle_Lab_Templates Automation_Ref.
#   .\mvnw.cmd test -Pwatch "-Dcucumber.tags=@LT-1"
#   .\mvnw.cmd test -Pwatch "-Dcucumber.features=src/test/resources/features/labtemplates.feature"

Feature: Lab Templates management

  @LT-2 @LT-24 @easybatch1
  Scenario: The lab templates list shows all configured template details
    When admin opens the lab templates list
    Then the lab templates table lists its configured template details

  @LT-1
  Scenario: Admin can open Add Lab Template from the listing
    When admin opens the lab templates list
    And admin uses the add lab template option
    Then the add lab template form opens

  @LT-2
  Scenario: The add lab template form renders its core configuration fields
    When admin opens the add lab template form
    Then the add lab template form shows its core configuration fields

  @LT-3
  Scenario: Required fields prevent an empty lab template from being created
    When admin opens the add lab template form
    And admin submits the empty lab template form
    Then the lab template is not created and validation is shown

  # Name stays empty so the submit can never persist a template; see LabTemplatesPage.
  @LT-50
  Scenario: An empty Create interval prevents a lab template from being created
    When admin opens the add lab template form
    And admin submits a lab template with an empty Create interval
    Then the empty interval prevents the lab template save

  @LT-47
  Scenario: Create Start and Unpark intervals are available on a lab template
    When admin opens the add lab template form
    Then the form shows Create Start and Unpark interval duration fields

  @LT-51
  Scenario: Lab action intervals are prefilled
    When admin opens the add lab template form
    Then the action interval duration fields are prefilled
    And interval defaults match each resource type

  @LT-52
  Scenario: Admin can change the prefilled lab action intervals
    When admin opens the add lab template form
    And admin changes the Create Start and Unpark interval values without saving
    Then the changed action interval values remain editable

  @LT-53
  Scenario: A negative action interval is rejected
    When admin opens the add lab template form
    And admin enters a negative Create interval
    Then negative interval validation prevents the lab template save

  @LT-15
  Scenario: View Lab Template displays the saved template details
    When admin opens the lab templates list
    And admin opens the first lab template in view mode
    Then the lab template view shows its saved details

  @LT-16
  Scenario: View Lab Template allows edit but not delete on the details page
    When admin opens the lab templates list
    And admin opens the first lab template in view mode
    Then the lab template view allows edit and denies delete

  @LT-25
  Scenario: Admin can show or hide a lab-template table column
    When admin opens the lab templates list
    And admin toggles the Description table column
    Then the Description table column visibility changes

  @LT-26
  Scenario: Refresh reloads the lab templates listing
    When admin opens the lab templates list
    And admin refreshes the lab templates table
    Then the refreshed lab templates table remains current

  @LT-27 @LT-33 @easybatch1
  Scenario: Admin can search lab templates by name
    When admin opens the lab templates list
    And admin searches lab templates by an existing name
    Then only matching lab templates remain visible

  @LT-28
  Scenario: Lab-template search does not treat multiple names as separate searches
    When admin opens the lab templates list
    And admin enters two lab template names as one search
    Then the lab template search remains a single text search

  @LT-29
  Scenario: Lab-template search accepts a long search string
    When admin opens the lab templates list
    And admin searches lab templates with a very long string
    Then the lab template search accepts the full string without an error

  @LT-30
  Scenario: Lab-template search escapes a script payload
    When admin opens the lab templates list
    And admin searches lab templates with a script payload
    Then the lab template search treats the payload as text

  @LT-role
  Scenario: Lab templates are accessible only to a site admin
    When admin opens the lab templates list
    Then a student cannot open the lab templates list

  @CGAP-LT-3
  Scenario: A non-site-admin user is denied access to Lab Templates
    Then a student cannot open the lab templates list
    And the student lab templates access is denied

  # Mutating scenarios create stamped templates with Plan 1747 / Team 90 and always delete them.
  @LT-11 @LT-12 @LT-13 @LT-14 @easybatch1
  Scenario: Admin can edit all safe sections of a disposable lab template
    When admin creates a disposable Account lab template
    Then the disposable lab template is active in the listing
    When admin edits every safe section of the disposable lab template
    Then the edited lab template settings persist
    And the lab template view shows the saved action intervals

  @LT-17 @LT-19 @easybatch1
  Scenario: Admin can delete a lab template that is not used by an activity
    When admin creates a disposable Account lab template
    And admin deletes the unused disposable lab template
    Then the deleted lab template is absent from the listing

  @LT-20 @LT-21 @LT-22 @LT-23 @easybatch1
  Scenario: Admin can deactivate and reactivate an unused lab template
    When admin creates a disposable Account lab template
    And admin deactivates and reactivates the disposable lab template
    Then the disposable lab template returns to Active status

  @LT-18
  Scenario: A lab template used by an activity cannot be deleted or deactivated
    When admin opens the lab template used by the configured sandbox
    Then delete and deactivate are blocked because the lab template is in use

  @LT-80
  Scenario: A duplicate lab template name is rejected
    When admin creates a disposable Account lab template
    And admin attempts to create a duplicate of the disposable lab template
    Then duplicate lab template validation prevents the save

  @LT-32
  Scenario: Only active lab templates are offered on the Sandbox activity form
    When admin creates a disposable Account lab template
    And admin deactivates the disposable lab template
    Then the disposable template is absent from the Sandbox activity template list
    When admin reactivates the disposable lab template
    Then the disposable template is available in the Sandbox activity template list

  @LT-31
  Scenario: A script payload in a lab template name is sanitized in the activity dropdown
    When admin creates a disposable lab template with a script payload in its name
    Then the activity template dropdown contains only the sanitized template name

  @LT-34 @LT-35 @LT-36 @easybatch1
  Scenario Outline: Plan Team and Catalog resolve when an active template is selected
    When admin creates a disposable Account lab template
    And admin selects the disposable template for a "<labType>" activity
    Then the activity form shows the configured Plan Team and Catalog
    And selecting the disposable template produces no browser page error

    Examples:
      | labType   |
      | playground |
      | guided     |
      | challenge  |

  @LT-38
  Scenario Outline: Existing activities retain their Lab Template information
    When admin opens the configured "<labType>" activity edit form
    Then the existing activity shows its saved Plan Team and Catalog

    Examples:
      | labType   |
      | playground |
      | guided     |
      | challenge  |

  @LT-41
  Scenario: A duplicated activity retains its Lab Template information
    When admin duplicates the configured Sandbox activity
    Then the duplicated activity retains its saved Lab Template information

  @CGAP-LT-4
  Scenario: Template edits propagate to activities using that template
    When admin creates a disposable template and a Sandbox activity using it
    And admin changes the disposable template Plan ID
    Then the Sandbox activity edit form shows the changed template Plan ID

  @LT-40
  Scenario: A copied course retains Lab Template information in its activities
    When admin copies the configured course containing lab activities
    Then a lab activity in the copied course retains its saved Lab Template information

  @skip @LT-39
  Scenario: A restored course retains Lab Template information in its activities
    When admin backs up and restores the configured course containing lab activities
    Then a lab activity in the copied course retains its saved Lab Template information

  # The plugin's problem-statement form has a template dropdown but no Plan/Team/Catalog panel.
  @skip @LT-37
  Scenario: Randomized assessment problem statements show selected template information
    When admin opens the add problem statement form for the randomized assessment
    Then selecting a problem statement template shows its Plan Team and Catalog

  @LT-42 @LT-43 @easybatch1
  Scenario: An invalid Plan ID does not resolve a Catalog on the activity form
    When admin creates a disposable template with an invalid Plan ID
    Then the activity form reports no resolved Catalog

  # Catalog is resolved from Plan ID; the manual case expects it to remain visible with an invalid Team ID.
  @LT-44 @LT-45 @easybatch1
  Scenario: Valid Plan ID still resolves Catalog when Team ID is invalid
    When admin creates a disposable template with an invalid Team ID
    Then the activity form shows Catalog resolved from the valid Plan ID

  # The deployed Lab Templates listing has no bulk export/import controls.
  @LT-81
  Scenario: Lab templates can be exported and re-imported in bulk
    When admin opens the lab templates list
    Then bulk Lab Template export and import controls are available

  # The interval inputs have min=0 but no maximum; retain this fail-by-design check for QA.
  @LT-82
  Scenario: An extremely large action interval is rejected or capped
    When admin creates a disposable Account lab template
    And admin saves an extremely large Create interval
    Then the extreme interval is rejected or capped

  # Kiwi Add Lab Template rows (LT-4..LT-10) — activity form template dropdown, not template CRUD page.
  @LT-4 @easybatch1
  Scenario: Playground add activity shows the lab template dropdown
    When I open a new cloudlabs activity form for lab type "playground"
    Then the lab template dropdown is present on the activity form

  @LT-5 @easybatch1
  Scenario: Guided add activity shows the lab template dropdown
    When I open a new cloudlabs activity form for lab type "guided"
    Then the lab template dropdown is present on the activity form

  @LT-7 @LT-8 @easybatch1
  Scenario: Challenge add activity shows the lab template dropdown
    When I open a new cloudlabs activity form for lab type "challenge"
    Then the lab template dropdown is present on the activity form

  @LT-10 @easybatch1
  Scenario: Playground activity form lists at least one active lab template option
    When I open a new cloudlabs activity form for lab type "playground"
    Then the lab template dropdown lists an active template
