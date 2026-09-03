# Assessment — Randomization / Problem Statements. Mirrors the 'Randomization / Problem Statements' category in
# docs/MoodleRegression_Testing_Kiwi.xlsx (Moodle_Assessment_Labs). Only randomization_problem_statements cases live here.
# Run one: .\mvnw.cmd test -Pwatch "-Dcucumber.tags=@A1"  (use the scenario tag)

Feature: Assessment — Randomization / Problem Statements

  @A232
  Scenario: The normal assessment workflow works end to end
    Then this case needs a live attempt, an integration session, or a course-management action

  @A233
  Scenario: The randomized problem statement field is present on the challenge form
    Given a new assessment challenge activity form
    Then the randomized problem statement field is present

  @A234
  Scenario: The randomized problem statement field defaults to No
    Given a new assessment challenge activity form
    Then the randomized problem statement field defaults to "No"

  @A235
  Scenario: Enabling randomized problem statement hides the problem statement content field
    Given a new assessment challenge activity form
    Then enabling randomized problem statement hides the problem statement content field

  @A236
  Scenario: Enabling randomization hides the manual content source
    Then enabling randomization hides the manual content source

  @A237
  Scenario: Disabling randomized problem statement shows the problem statement content field
    Given a new assessment challenge activity form
    Then disabling randomized problem statement shows the problem statement content field

  @A238
  Scenario: A sandbox activity form has no randomized problem statement field
    Then a sandbox activity form has no randomized problem statement field

  @A239
  Scenario: A randomized activity is created correctly
    Then this case needs a live attempt, an integration session, or a course-management action

  @A240
  Scenario: After adding problem statements the admin view updates
    Then this case needs a live attempt, an integration session, or a course-management action

  @A241
  Scenario: The randomized assessment landing shows the problem statements button
    Then the randomized assessment landing shows the problem statements button

  @A242
  Scenario: A non-randomized assessment landing has no problem statements button
    Then the base assessment landing has no problem statements button

  @A243
  Scenario: A randomized assessment activity has randomization enabled
    Then the randomized assessment activity has randomization set to Yes

  @A244
  Scenario: A base assessment activity has randomization disabled
    Then the base assessment activity has randomization set to No

  @A245
  Scenario: A duplicated randomized activity works
    Then this case needs a live attempt, an integration session, or a course-management action

  @A246
  Scenario: A duplicated normal assessment activity works
    Then this case needs a live attempt, an integration session, or a course-management action

  @A247
  Scenario: Importing a course with a randomized activity works
    Then this case needs a live attempt, an integration session, or a course-management action

  @A248
  Scenario: Copying a course with a randomized activity works
    Then this case needs a live attempt, an integration session, or a course-management action

  @A249admin
  Scenario: Admin can open the assessment problem statements page
    When admin opens the assessment problem statements page
    Then the problem statements admin page renders for admin

  @A250
  Scenario: The Problem Statements page shows all admin controls and the data table
    Given the assessment problem statements page is open
    Then the problem statements toolbar shows the Add New, Edit, View, Delete, Activate and Deactivate controls with the data table

  @A251
  Scenario: Admins can add a new problem statement
    Given the randomization problem statements page is open
    Then clicking Add New opens the problem statement form

  @A252
  Scenario: Admins can edit a problem statement
    Given the randomization problem statements page is open
    Then the edit control becomes available when a problem statement row is selected

  @A253
  Scenario: Admins can view a problem statement
    Given the randomization problem statements page is open
    Then the view control becomes available when a problem statement row is selected

  @A254
  Scenario: Admins can delete a problem statement
    Given the randomization problem statements page is open
    Then the delete control becomes available when a problem statement row is selected

  @A255
  Scenario: Admin can activate or deactivate a problem statement
    Given the assessment problem statements page is open
    Then admin can toggle a problem statement between active and inactive

  @A256
  Scenario: A problem statement in use can still be deactivated by admin
    Then this case needs a live attempt on the randomization assessment

  @A257
  Scenario: Admin can toggle a problem statement on the randomization assessment
    Then admin can toggle a problem statement on the randomization assessment

  @A258
  Scenario: A problem statement in use remains editable
    Then this case needs a live attempt on the randomization assessment

  @A259
  Scenario: Deactivated problem statements cannot be used by users
    Then this case needs a live attempt on the randomization assessment

  @A260
  Scenario: Reactivated problem statements can be used by users
    Then this case needs a live attempt on the randomization assessment

  @A261
  Scenario: The lab template comes from the problem statement during randomized creation
    Then this case needs a live attempt on the randomization assessment

  @A262
  Scenario: The lab template comes from the lab template table during normal creation
    Then this case needs a live attempt on the randomization assessment

  @A263
  Scenario: Users get a different problem statement each attempt
    Then this case needs a live attempt on the randomization assessment

  @A264
  Scenario: Remaining attempts reuse a problem statement when none new is available
    Then this case needs a live attempt on the randomization assessment

  @A265
  Scenario: Each attempt creates a new lab
    Then this case needs a live attempt on the randomization assessment

  @A266
  Scenario: Each lab has a retake number
    Then this case needs a live attempt on the randomization assessment

  @A267
  Scenario: Each submission records ps_name and ps_id
    Then this case needs a live attempt on the randomization assessment

  @A268
  Scenario: When all problem statements are deactivated the user cannot access the lab
    Then this case needs a live attempt on the randomization assessment

  @A269
  Scenario: Reactivating one problem statement lets the user access the lab
    Then this case needs a live attempt on the randomization assessment

  @A270
  Scenario: After reactivating all statements users get different ones per attempt
    Then this case needs a live attempt on the randomization assessment

  @A271
  Scenario: Deleting all problem statements blocks lab access
    Then this case needs a live attempt on the randomization assessment

  @A272
  Scenario: An updated problem statement reflects to users
    Then this case needs a live attempt on the randomization assessment

  @A273
  Scenario: Un-enrolling a user deletes their labs
    Then this case needs a live attempt on the randomization assessment

  @A274
  Scenario: Admin can pre-create the lab for a randomized activity
    Then this case needs a live attempt on the randomization assessment

  @A275
  Scenario: Admin can override a completed user's assessment on the randomization activity
    Given the override form is open for a completed user on the randomization assessment
    Then the override can be applied to the completed assessment

  @A276
  Scenario: An overridden attempt keeps the last attempted problem statement
    Then this case needs a live attempt on the randomization assessment

  @A277
  Scenario: After cooldown the last problem statement is reused
    Then this case needs a live attempt, an integration session, or a course-management action

  @A278
  Scenario: After re-enrol the user can start the randomized assessment
    Then this case needs a live attempt, an integration session, or a course-management action

  @A279
  Scenario: A problem statement added mid-run reaches the user
    Then this case needs a live attempt, an integration session, or a course-management action

  @A280
  Scenario: A problem statement added after all attempted reaches the user
    Then this case needs a live attempt, an integration session, or a course-management action

  @A281
  Scenario: The assessment problem statements page initialises
    Then the assessment problem statements page initialises

  @A282
  Scenario: The assessment problem statements page has a search control
    Then the assessment problem statements page has a search control

  @A283
  Scenario: System Moodle reports are reachable
    Then the moodle reports area loads

  @A284
  Scenario: Assessment result APIs work
    Then this case needs the result API token

  @A285
  Scenario: The SSO and LTI workflow works
    Then this case needs a live attempt, an integration session, or a course-management action

  @A286
  Scenario: Course deletion removes the randomized activities
    Then this case needs a live attempt, an integration session, or a course-management action

  @A287
  Scenario: Normal sandbox and guided workflows are reachable
    Then the sandbox and guided landings render

  @A288
  Scenario: The user attempt button is present on manage challenges
    Then the user attempt button is present on manage challenges

  @A289
  Scenario: Selecting a user opens the user attempts drill-down
    Then the user attempts drill-down opens

  @A290
  Scenario: The user attempts page shows the user summary details
    Given the user attempts page is open for an enrolled user
    Then the user attempts page shows the user summary details

  @A291
  Scenario: Table data is correct on the user attempts page
    Then this case needs a live attempt on the randomization assessment

  @A292
  Scenario: The user attempts page shows the download report and view submission controls
    Given the user attempts page is open for an enrolled user
    Then the user attempts page shows the download report and view submission controls

  @A293
  Scenario: View submission opens the selected attempt
    Then this case needs a live attempt on the randomization assessment

  @A294
  Scenario: The user attempt button is present for a normal assessment activity
    Then the user attempt button is present on manage challenges

  @A295
  Scenario: The user attempts page has a search box
    Given the user attempts page is open for an enrolled user
    Then the user attempts page has a search box

  @A296
  Scenario: The downloaded user attempts report has correct data
    Then this case needs a live attempt on the randomization assessment

  @A297
  Scenario: The user attempts table supports search and pagination
    Given the user attempts page is open for an enrolled user
    Then the user attempts table supports search and pagination

  @A298
  Scenario: The assessment landing shows the attempts filter dropdown
    Then the assessment landing shows the attempts filter dropdown

  @A299
  Scenario: A new attempt is added to the attempts table
    Then this case needs a live attempt on the randomization assessment

  @A300
  Scenario: A user with no attempts shows zero attempts
    Then this case needs a live attempt on the randomization assessment

  @A301
  Scenario: A user with no attempts shows an info message
    Then this case needs a live attempt on the randomization assessment

  @A302
  Scenario: Multiple attempts appear in the attempts filter
    Then this case needs a live attempt on the randomization assessment

  @A303
  Scenario: A sandbox activity has no attempts filter dropdown
    Then a sandbox activity landing has no attempts filter dropdown

  @A304
  Scenario: When duration ends the attempt is marked complete
    Then this case needs a live attempt on the randomization assessment

  @A305
  Scenario: Mark-as-complete without a submission is recorded
    Then this case needs a live attempt on the randomization assessment

  @A306
  Scenario: A deleted submission is removed from the activity page
    Then this case needs a live attempt on the randomization assessment

  @A308
  Scenario: Clearing all attempts empties the attempts dropdown
    Then this case needs a live attempt on the randomization assessment

  @A310
  Scenario: The new version parameter works for course and assessment result APIs
    Then this case needs the result API token

  @A311
  Scenario: version v1 returns the old response
    Then this case needs the result API token

  @A312
  Scenario: version v2 returns the new response
    Then this case needs the result API token

  @A313
  Scenario: an empty version parameter returns a defined response
    Then this case needs the result API token

  @A314
  Scenario: an invalid version value returns a defined response
    Then this case needs the result API token

  @A315
  Scenario: no version parameter returns a defined response
    Then this case needs the result API token

  @A316
  Scenario: v2 returns each attempt's data
    Then this case needs the result API token

  @A317
  Scenario: assessment and course result data match
    Then this case needs the result API token

  @A318
  Scenario: the update and external mapping API work with the old workflow
    Then this case needs the result API token

  @A319
  Scenario: The add and edit problem statement page design loads
    Then the add problem statement page loads

  @A320
  Scenario: Each attempt loads new assessment instructions
    Then this case needs a live attempt, an integration session, or a course-management action

  @A321
  Scenario: The lab partition page shows the assessment content and image
    Then this case needs specific git repository content or a learner attempt

  @A322
  Scenario: The problem statement card and table view loads
    Then the problem statements page offers a card or table view
