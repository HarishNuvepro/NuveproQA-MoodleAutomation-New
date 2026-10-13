# Moodle Upload users.
# One tag per scenario — the same @UU-Sn as Upload_Users Automation_Ref.
#   .\mvnw.cmd test -Pwatch "-Dcucumber.tags=@UU-S1"
#   .\mvnw.cmd test -Pwatch "-Dcucumber.features=src/test/resources/features/upload_users.feature"
#   .\mvnw.cmd test -Pparallel "-DsuiteXmlFile=testng-parallel5.xml" "-Dparallel.browsers=3" "-Dheadless=true" "-Dcucumber.features=src/test/resources/features/upload_users.feature"

Feature: Moodle — Upload users

  @UU-S1
  Scenario: Valid CSV creates users who are listed and can log in
    Given admin opens Upload users
    And the example Upload users CSV downloads with required headers
    When admin uploads a valid three-user CSV
    Then all CSV users are created and one can log in
    And continuing from results returns to Upload users

  @UU-S2
  Scenario: Preview then cancel does not create users
    When admin previews a valid Upload users CSV and cancels
    Then the previewed users were not created

  @UU-S3
  Scenario: Upload users rejects no file and an empty file
    Then Upload users rejects no file and an empty file

  @UU-S4
  Scenario: Upload users rejects a CSV missing a required header
    Then Upload users rejects a CSV missing the email header
    And Upload users rejects a CSV missing the username header

  @UU-S5
  Scenario: A mixed CSV creates valid rows and reports malformed rows
    When admin uploads a CSV containing valid and malformed user rows
    Then the valid row is created and malformed rows report errors

  @UU-S6
  Scenario: Add new only skips an existing username
    Given an existing user for Upload users
    When admin uploads that username and a new username using Add new only
    Then the existing user is unchanged and the new user is created

  @UU-S7
  Scenario: Add new and update existing updates matches and creates new users
    Given an existing user for Upload users
    When admin uploads changed existing details and a new user using Add new and update existing
    Then the existing user is updated and the new user is created

  @UU-S8
  Scenario: Update existing only updates matches without creating unknown users
    Given an existing user for Upload users
    When admin uploads an existing and unknown username using Update existing only
    Then only the existing user is updated

  @UU-S9
  Scenario: Prevent email duplicates rejects a new username with an existing email
    Given an existing user for Upload users
    When admin uploads a new username with the existing email and prevents duplicate emails
    Then the duplicate-email user is not created

  @UU-S10
  Scenario: A weak CSV password is flagged and forces a reset
    When admin uploads a new user with a weak required password
    Then the weak CSV password is flagged and must be reset at login

  @UU-S11
  Scenario: CSV can suspend and reactivate an existing user
    Given an existing user for Upload users
    When admin suspends the existing user through Upload users
    Then the CSV user cannot log in
    When admin reactivates the existing user through Upload users
    Then the CSV user can log in

  @UU-S12
  Scenario: CSV delete obeys Allow deletes
    Given two existing users for CSV deletion
    When admin uploads deleted one with Allow deletes off and deleted one with Allow deletes on
    Then the blocked-delete user remains and the allowed-delete user is deleted

  @UU-S13
  Scenario: CSV rename obeys Allow renames
    Given two existing users for CSV rename
    When admin uploads renames with Allow renames off and on
    Then the blocked rename is unchanged and the allowed rename succeeds

  @UU-S14
  Scenario: Valid course shortname enrols and an unknown course warns
    Given an Upload users course fixture
    When admin uploads users with valid and unknown course1 shortnames
    Then only the valid-course user is enrolled in the course
    And the unknown-course CSV user is still created

  @UU-S15
  Scenario: Upload users applies cohort and profile fields
    Given an Upload users cohort fixture
    When admin uploads a user with cohort and profile values
    Then the user belongs to the cohort and profile values persist

  @UU-S16
  Scenario: Delimiter mismatch is rejected and students cannot access Upload users
    Then a delimiter mismatch creates no user
    And a student cannot access Upload users

  @UU-S17
  Scenario: Add all appends a number to an existing username
    Given an existing user for Upload users
    When admin uploads that username using Add all append number
    Then a numbered username is created and the original is unchanged

  @UU-S18
  Scenario: Upload users cannot delete a protected site administrator
    Then a configured protected administrator cannot be deleted by Upload users

  @UU-S19
  Scenario: Preview rows limits the number of parsed CSV records displayed
    Then the Preview rows setting limits the parsed CSV preview

  @UU-S20
  Scenario: Force password change All applies to every uploaded user
    Then Force password change All is enforced for a new CSV user

  @UU-S21
  Scenario: Existing user password Update replaces the password from CSV
    Then Update existing password replaces the old password with the CSV password

  @UU-S22
  Scenario: Fill in missing updates only empty existing profile values
    Then Fill in missing preserves existing values and fills an empty profile field

  @UU-S23
  Scenario: Standardise usernames normalises an uploaded username
    Then Standardise usernames normalises an uppercase CSV username

  @UU-S24
  Scenario: Uploaded users can be selected for Bulk user actions
    Then selected new CSV users are forwarded to Bulk user actions

  @UU-S25
  Scenario: group1 creates a missing course group and assigns the user
    Then group1 creates a missing course group and assigns the CSV user

  @UU-S26
  Scenario: role1 assigns a course role during upload
    Then role1 assigns the requested course role to the CSV enrolment

  @UU-S27
  Scenario: enrolperiod1 and enrolstatus1 configure a course enrolment
    Then enrolperiod1 and enrolstatus1 control the CSV course enrolment

  @UU-S28
  Scenario: sysrole1 assigns and removes a system role
    Then sysrole1 assigns and removes the Manager system role

  @UU-S29
  Scenario: Database field limits reject an oversized CSV value
    Then an over-limit profile value is refused and creates no user

  @UU-S30
  Scenario: Upload users handles trailing spaces and encoded commas
    Then trailing spaces are trimmed and encoded commas are decoded

  @UU-S31
  Scenario: No login authentication prevents an uploaded user from logging in
    Then auth nologin creates an account that cannot log in
