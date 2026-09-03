# Moodle user administration.
# One tag per scenario — the same @U-user-n as Moodle_users Automation_Ref.
#   .\mvnw.cmd test -Pwatch "-Dcucumber.tags=@U-user-1"
#   .\mvnw.cmd test -Pwatch "-Dcucumber.features=src/test/resources/features/moodle_users.feature"

Feature: Moodle — user administration

  # --- Create user ---

  @U-user-1
  Scenario: Admin can create a new user
    When admin submits the add-user form using "valid"
    Then the user is created successfully

  Scenario Outline: Add-user form rejects <dataset>
    When admin submits the add-user form using "<dataset>"
    Then the add-user form is rejected

    @U-user-2
    Examples: empty form
      | dataset |
      | empty   |

    @U-user-3
    Examples: weak password
      | dataset       |
      | weak_password |

    @U-user-4
    Examples: invalid email
      | dataset       |
      | invalid_email |

  Scenario Outline: Add-user form rejects duplicate <dataset>
    Given a registered user
    When admin submits the add-user form using "<dataset>"
    Then the add-user form is rejected

    @U-user-5
    Examples: duplicate username
      | dataset            |
      | duplicate_username |

    @U-user-6
    Examples: duplicate email
      | dataset         |
      | duplicate_email |

  # --- Login / session ---

  @U-user-7
  Scenario: A registered user can log in with valid credentials
    Given a registered user
    Then the user can log in with valid credentials

  @U-user-8
  Scenario: Login with invalid credentials is rejected
    Then login with invalid credentials is rejected

  @U-user-9
  Scenario: A user can log out
    Given a registered user
    Then the user can log in and then log out

  @U-user-10
  Scenario: After logout a protected page requires login
    Given a registered user
    Then after the user logs out a protected page requires login

  # --- Admin user list / lifecycle ---

  @U-user-11
  Scenario: A created user appears in the admin user list
    Given a registered user
    Then the user appears in the admin user list

  @U-user-12
  Scenario: A suspended user cannot log in and a re-enabled user can
    Given a registered user
    When admin suspends the user
    Then the user cannot log in
    When admin unsuspends the user
    Then the user can log in with valid credentials

  @U-user-13
  Scenario: Admin can change a user's email
    Given a registered user
    When admin changes the user email
    Then the user email is updated

  @U-user-14
  Scenario: Admin can edit and delete a user
    Given a registered user
    When admin edits the user first name
    And admin deletes the user
    Then the deleted user is not listed

  @U-user-15
  Scenario: A deleted user cannot log in and is not listed
    Given a registered user
    When admin deletes the user
    Then the deleted user cannot log in
    And the deleted user is not listed

  @U-user-16
  Scenario: The user-list filter narrows to the matching user
    Given a registered user
    Then filtering the user list narrows to that user

  @U-user-17
  Scenario: A suspended user is flagged in the user list
    Given a registered user
    When admin suspends the user
    Then the user is shown as suspended in the list

  # --- Profile ---

  @U-user-18
  Scenario: A user can update their profile and the change is reflected
    Given a registered user
    When the user updates their profile city
    Then the profile change is reflected

  @U-user-19
  Scenario: Invalid profile data is rejected
    Given a registered user
    When the user saves an invalid email in their profile
    Then the profile form shows a validation error

  # --- Password ---

  @U-user-20
  Scenario: Force password change on first login is enforced
    When admin submits the add-user form using "force_password_change"
    Then the user is forced to change password on login

  @U-user-21
  Scenario: A user can change their own password
    Given a registered user
    Then the user can change their own password and log in with it

  # --- Enrolment ---

  @U-user-22
  Scenario: An already-enrolled user lands on the course page
    Given a registered user enrolled in the course
    Then the user sees the course page not an enrolment page

  # --- Cohorts ---

  @U-user-23
  Scenario: Admin can create a cohort
    Then admin can create a cohort that is listed

  @U-user-24
  Scenario: Admin can delete a cohort
    Then admin can create and then delete a cohort

  @U-user-25
  Scenario: Admin can add and remove a user from a cohort
    Given a registered user
    Then admin can add and remove the user from a cohort

  Scenario Outline: Add-user form rejects invalid username <dataset>
    When admin submits the add-user form using "<dataset>"
    Then the add-user form is rejected

    @U-user-26
    Examples: invalid username
      | dataset             |
      | uppercase_username  |
      | spaces_username     |

  @U-user-28
  Scenario: Account locks after consecutive failed login attempts
    Given a registered user
    When admin enables temporary login lockout after 3 failed attempts
    Then the user is locked out after consecutive failed login attempts

  @U-user-29
  Scenario: A user can self-enrol into a course successfully
    Given a registered user
    When the user self-enrols in the self-enrolment course
    Then the enrolled user can open the course page

  @U-user-30
  Scenario: Admin can assign a system role to a user
    Given a registered user
    When admin assigns the Manager system role to the registered user
    Then the registered user has the Manager system role
    When admin removes the Manager system role from the registered user

  @U-user-31
  Scenario: Admin can suspend multiple users from the user list
    Given two registered users
    When admin suspends the two registered users from the user list
    Then both registered users are shown as suspended in the list

  @U-user-31
  Scenario: Admin can bulk delete users
    Given two registered users
    When admin bulk deletes the two registered users
    Then the two registered users are deleted
