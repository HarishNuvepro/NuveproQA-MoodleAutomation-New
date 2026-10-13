# Easy automation backlog — batch 2 of 2 (51 Kiwi cases).
# See reports/easy_automation_batches.csv (batch=2).
#
# Run (headless):
#   .\mvnw.cmd test "-Dcucumber.tags=@easybatch2 and not @provisioning and not @skip"
#
# Many @LT-* / My Labs / Pre-Delivery / PP form cases are tagged on their home feature files.

Feature: Easy automation batch 2

  # ---- Moodle_Roles (CGAP-R) ----

  @CGAP-R-8 @easybatch2 @roles @customrole
  Scenario: Admin can create a custom role and see it listed
    When admin creates a custom role based on the editing-teacher archetype
    Then the custom role is listed

  @CGAP-R-9 @easybatch2 @roles @customrole
  Scenario: Admin can delete a custom role
    When admin creates a custom role based on the editing-teacher archetype
    And admin deletes the custom role
    Then the custom role is no longer listed

  @CGAP-R-4 @easybatch2 @roles
  Scenario: Role permissions page exposes Allow Prevent and Prohibit controls
    When admin opens system role permissions for the student role
    Then cloudlabs capabilities show allow prevent or prohibit states

  @CGAP-R-5 @easybatch2 @roles @roleassign
  Scenario: A user can hold system manager and course student roles together
    When admin assigns the Manager role to a new user at system level
    And admin enrols that user as a student in the sandbox course
    Then the user is a system manager

  @CGAP-R-11 @easybatch2 @roles
  Scenario: Activity module context permissions page loads for cloudlabs
    When admin opens cloudlabs module permissions in the sandbox course
    Then the module permissions table lists cloudlabs capabilities

  # ---- Moodle_Tags (CGAP-T) ----

  @CGAP-T-3 @easybatch2 @tags
  Scenario: Whitespace in a tag name is trimmed on save
    When I open the activity edit form
    And I enter the tag "  trimtag  "
    And I save the activity
    Then the trimmed tag "trimtag" is present on the activity

  @CGAP-T-11 @easybatch2 @tags
  Scenario: Standard tags are suggested in the activity autocomplete
    Given a standard tag exists
    Then the tag is suggested in the activity tag field

  @CGAP-T-6 @easybatch2 @tags @tagmgmt
  Scenario: Tag management search finds an existing standard tag
    Given a new standard tag
    When admin searches tag management for that tag
    Then the standard tag appears in tag management results

  @CGAP-T-8 @easybatch2 @tags @tagmgmt
  Scenario: The tag management page loads related tag information
    Given a new standard tag on the activity
    When admin opens the tag browse page for that tag
    Then the tagged activity appears on the tag browse page

  @CGAP-T-9 @easybatch2 @tags @tagmgmt
  Scenario: A tag can be flagged from tag management
    Given a new standard tag
    When admin flags the standard tag as inappropriate
    Then the tag flag is recorded in tag management

  # ---- Practice Project deferred (env/fixture) ----

  @skip @CGAP-PP-12 @easybatch2
  Scenario: Practice Project PDF instructions need PDF Yes fixture
    When I open a new guided activity form
    Then the Practice Project lab type is selectable

  @skip @CGAP-PP-22 @easybatch2
  Scenario: Practice Project bulk create deferred to sandbox SB-manage-12 port
    When I open a new guided activity form
    Then the Practice Project lab type is selectable

  @skip @CGAP-PP-24 @easybatch2
  Scenario: Practice Project catalog unlimited fixture deferred
    When I open a new guided activity form
    Then the Practice Project lab type is selectable

  @skip @CGAP-PP-25 @easybatch2
  Scenario: Practice Project invalid template fixture deferred
    When I open a new guided activity form
    Then the Practice Project lab type is selectable

  @skip @LT-73 @easybatch2
  Scenario: Orgo suspend resume progress LT-73 needs external Orgo access
    When admin opens the lab templates list
    Then the lab templates table lists its configured template details

  @skip @LT-76 @easybatch2
  Scenario: LTI launch progress LT-76 needs consumer harness
    When admin opens the lab templates list
    Then the lab templates table lists its configured template details
