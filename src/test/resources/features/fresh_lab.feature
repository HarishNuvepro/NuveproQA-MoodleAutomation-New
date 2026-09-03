# Lab-action tests on a FRESH per-user lab. Seeds a new user + enrols them (Web Services),
# creates a lab for them via Manage Labs (admin, no cp.php), then exercises the lab actions.
# This avoids the shared-lab expiry problem (a long shared lab times out on the provider).
# Run: mvnw test -Dcucumber.tags="@freshlab"

Feature: Fresh per-user sandbox lab lifecycle

  @freshlab @provisioning
  Scenario: A fresh per-user lab can be created, extended, stopped and deleted
    Given a freshly enrolled user
    When I create a lab for that user from Manage Labs
    Then that user's lab reaches Running
    When I extend that user's lab duration by 1 hour
    Then the lab duration extension succeeds
    When I stop that user's lab
    Then that user's lab reaches Stopped
    When I delete that user's lab
    Then that user's lab reaches Deleted

  @freshlab @expired @provisioning
  Scenario: Actions on an expired lab (expired state shown; admin can still delete)
    Given a freshly enrolled user
    When I create a lab for that user from Manage Labs
    Then that user's lab reaches Running
    When I expire that user's lab
    Then that user sees the lab as expired
    When I delete that user's lab
    Then that user's lab reaches Deleted
