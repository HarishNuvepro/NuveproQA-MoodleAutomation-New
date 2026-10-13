# All Courses page.
# One tag per scenario — the same @ACP-Sn / @CGAP-ACP-n as All Course Page Automation_Ref.
#   .\mvnw.cmd test -Pwatch "-Dcucumber.tags=@ACP-S1"
#   .\mvnw.cmd test -Pwatch "-Dcucumber.features=src/test/resources/features/all_courses.feature"
#   .\mvnw.cmd test -Pparallel "-DsuiteXmlFile=testng-parallel5.xml" "-Dparallel.browsers=3" "-Dheadless=true" "-Dcucumber.features=src/test/resources/features/all_courses.feature"

Feature: All Courses page

  @ACP-S1
  Scenario: Admin sees courses, categories, and search on All Courses
    When admin opens the All Courses page
    Then All Courses shows courses, the categories filter, and an enabled search
    And Manage courses and Add a new course are not on the All Courses page

  @ACP-S2
  Scenario: Course search accepts supported character classes and returns ordered matches
    Given uniquely named courses exist for search
    When admin searches All Courses using alphabetic alphanumeric numeric and special inputs
    Then every search returns only matching courses in alphabetical order
    And a search with no match shows the empty result

  @ACP-S3
  Scenario: Course cards match configured details in initial and later batches
    Given courses with distinct names descriptions categories durations and images exist
    When admin loads all configured course cards across All Courses batches
    Then every course card matches its configured details

  @ACP-S4
  Scenario: A hidden course is listed on All Courses but unavailable to the student
    Given a visible course is changed to hidden
    Then the hidden course is listed on All Courses for the student
    When the student opens the hidden course
    Then the course is currently unavailable to students

  @ACP-S6
  Scenario: A completed course remains listed at 100 percent
    Given student two has a completed course
    Then that course is listed on All Courses at 100 percent complete

  @ACP-S7
  Scenario: A deleted course is removed from All Courses
    Given a temporary course exists
    When admin deletes the temporary course
    Then the deleted course is absent from All Courses

  @ACP-S8
  Scenario: A valid new course is listed after save
    When admin creates a valid course
    Then the created course is listed in All Courses

  @ACP-S9
  Scenario: Invalid course data is rejected and not listed
    When admin attempts to create a course with invalid required data
    Then course validation is shown and no invalid course is listed

  @ACP-S10
  Scenario: A duplicated course is listed
    Given a temporary course exists
    When admin duplicates the temporary course
    Then the duplicated course is listed in All Courses

  @ACP-S11
  Scenario: Original and duplicated courses can move to another category
    Given a course copy and two categories exist
    When admin moves the original and copied course to the destination category
    Then both courses are listed under the destination category

  @ACP-S12
  Scenario: Course backup can be restored into a category
    Given a temporary course and destination category exist
    When admin backs up and restores the course into the destination category
    Then the restored course is listed under the destination category

  @ACP-S14
  Scenario: Nested categories and their courses are created and listed correctly
    When admin creates parent categories multiple subcategories and courses beneath them
    Then parent and subcategories appear in the parent selector and category hierarchy
    And each nested course is listed under its configured category

  @ACP-S15
  Scenario: All Courses ordering remains stable through common lifecycle changes
    Given alphabetically varied courses categories and subcategories exist
    When admin refreshes filters adds deletes hides and shows course fixtures
    Then courses categories and subcategories remain in alphabetical order

  @ACP-S16
  Scenario: Imported courses are placed in alphabetical order
    Given a course to export exists among alphabetically ordered courses
    When admin imports that course from its downloaded backup
    Then the imported course appears in alphabetical order

  @ACP-S17
  Scenario: Hidden categories remain available to admin but not students
    Given admin creates a visible category with a course and a student
    When admin hides that category
    Then the hidden category remains visible in the admin All Courses categories
    But the hidden category is not visible in the student All Courses categories
    When admin shows that category again
    Then the category is visible in both admin and student All Courses categories

  @ACP-S18
  Scenario: An unenrolled user can browse courses and categories
    Given a new user with no course enrolments exists
    When the unenrolled user opens All Courses
    Then courses and the categories filter are visible

  @ACP-S19
  Scenario: Self-enrol visibility follows enrolment configuration and existing enrolment
    Given a course and student exist
    Then manual-only enrolment does not offer self enrol
    When admin enables new self enrolments
    Then the student can see the self-enrol action
    When admin disables new self enrolments
    Then the student cannot see the self-enrol action
    When admin manually enrols the student and enables self enrolment
    Then the enrolled student sees the course without another self-enrol action

  @ACP-S20
  Scenario: Infinite scroll loads unique batches without redundant requests
    Given enough courses exist for three All Courses batches
    When the user opens and rapidly scrolls through All Courses
    Then the first batch size and one initial All Courses request are observed
    And later unique batches append with a transient loader and no pagination controls
    And no duplicate or overlapping batch request is sent
    And cards from the first and later batches open the correct course
    And scrolling after the end sends no additional All Courses request

  @ACP-S21
  Scenario: Refresh after scrolling resets All Courses to the first batch
    Given enough courses exist for three All Courses batches
    When the user scrolls through multiple batches and refreshes
    Then only the first All Courses batch is rendered

  @ACP-S23
  Scenario: Scrolling to the last course shows No more courses
    When the user scrolls All Courses down to the last course card
    Then All Courses shows No more courses

  @ACP-S24 @provisioning
  Scenario: Course card progress updates after the student creates a sandbox lab
    Given a visible course with one available sandbox activity and a student
    Then that course card shows 0 percent complete for the student
    When the student creates a lab in that sandbox activity
    Then the course card progress is updated

  @ACP-S25
  Scenario: Search and category filters reset and constrain infinite scroll
    Given enough courses in two categories exist for filtered batches
    When the user searches and filters after scrolling All Courses
    Then the list resets and every appended card matches the active filter

  @ACP-S26
  Scenario: Course search treats SQL and script payloads as plain text
    When the user searches All Courses with SQL and script payloads
    Then no script executes and no database or page error is shown

  @ACP-S27
  Scenario: A long course name does not break its card
    Given a course with a name longer than one hundred characters exists
    When the user views that course in All Courses
    Then its name remains readable without breaking the course card

  @ACP-S13
  Scenario: Read More appears only for long descriptions and opens the popup
    Given courses with short and long All Courses descriptions exist
    When admin searches All Courses for those description fixtures
    Then the long description shows Read More that opens a popup with the course name
    And the short description does not show Read More
    And both description fixtures show an image on the card

  @ACP-S29
  Scenario: All Courses remains usable on a mobile viewport
    When admin opens All Courses on a mobile viewport
    Then All Courses shows courses, the categories filter, and an enabled search

  @CGAP-ACP-1
  Scenario: A student does not see Manage courses or Add a new course
    Given a new user with no course enrolments exists
    When the unenrolled user opens All Courses
    Then Manage courses and Add a new course are not on the All Courses page

  @CGAP-ACP-2
  Scenario: Clearing All Courses search restores the catalog
    Given uniquely named courses exist for search
    When admin searches All Courses then clears the search
    Then the full All Courses catalog is restored

  @CGAP-ACP-3
  Scenario: An unauthenticated visitor can browse All Courses
    When admin opens the All Courses page
    When an unauthenticated visitor opens that catalog
    Then All Courses shows courses, the categories filter, and an enabled search

  @CGAP-ACP-4
  Scenario: Showing a hidden course lists it again on All Courses
    Given a visible course is changed to hidden
    When admin shows that course again
    Then the course is listed on All Courses again
