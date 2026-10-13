# Lab Readiness admin dashboard — read-only filters/table (no provisioning).
# Set LAB_READINESS_DASHBOARD_PATH in .env when auto-discovery misses your env.
# Run: mvnw test -Dcucumber.features=src/test/resources/features/predelivery_dashboard.feature

Feature: Pre-Delivery Lab Readiness Dashboard

  @PDL-dash-ui @PDL-19 @PDL-28 @PDL-30 @PDL-31 @PDL-32 @PDL-33 @PDL-35 @PDL-58 @PDL-59 @PDL-60 @PDL-63 @predelivery @predelivery-dashboard @easybatch2
  Scenario: The dashboard shows summary cards status values and table columns
    When admin opens the lab readiness dashboard
    Then the lab readiness dashboard shows summary cards and a labs table

  @PDL-dash-search @PDL-20 @PDL-36 @PDL-37 @PDL-39 @PDL-40 @predelivery @predelivery-dashboard @easybatch2
  Scenario: Activity name search filters and clears on the dashboard
    When admin opens the lab readiness dashboard
    And admin searches the lab readiness dashboard for the configured assessment activity name
    Then the dashboard table narrows to matching lab activities
    When admin clears the lab readiness dashboard activity search and applies filters
    Then the dashboard table shows the full lab list again

  @PDL-dash-filters @PDL-21 @PDL-22 @PDL-23 @PDL-41 @PDL-42 @PDL-44 @PDL-45 @PDL-46 @PDL-47 @PDL-49 @PDL-50 @PDL-51 @PDL-52 @PDL-55 @PDL-56 @predelivery @predelivery-dashboard @easybatch2
  Scenario: Lab type status and support filters narrow the dashboard table
    When admin opens the lab readiness dashboard
    And admin applies lab type and status filters on the lab readiness dashboard
    Then the dashboard table reflects the selected lab type and status filters

  @PDL-dash-empty @PDL-38 @PDL-48 @PDL-57 @predelivery @predelivery-dashboard @easybatch2
  Scenario: Impossible filters show an empty state on the dashboard
    When admin opens the lab readiness dashboard
    And admin applies impossible combined filters on the lab readiness dashboard
    Then the lab readiness dashboard shows an empty or no-match state

  @PDL-dash-open @PDL-24 @PDL-61 @PDL-62 @predelivery @predelivery-dashboard @easybatch2
  Scenario: Open navigates from the dashboard to the activity checklist
    When admin opens the lab readiness dashboard
    And admin opens the first lab checklist from the readiness dashboard
    Then the lab readiness checklist form is shown

  @PDL-dash-apply @PDL-64 @PDL-65 @predelivery @predelivery-dashboard @easybatch2
  Scenario: Dashboard filters apply when Apply is clicked
    When admin opens the lab readiness dashboard
    Then filters on the lab readiness dashboard apply only when Apply is clicked

  @PDL-67 @predelivery @predelivery-dashboard @easybatch2
  Scenario: A loading indicator may appear while dashboard data loads
    When admin opens the lab readiness dashboard
    Then a loading indicator may appear while the lab readiness dashboard loads data

  @PDL-69 @PDL-70 @predelivery @predelivery-dashboard @easybatch2
  Scenario: Special characters in dashboard search do not break the page
    When admin opens the lab readiness dashboard
    And admin searches the lab readiness dashboard with special characters
    Then the lab readiness dashboard handles special characters without error
