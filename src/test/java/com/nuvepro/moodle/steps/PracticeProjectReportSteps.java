package com.nuvepro.moodle.steps;

import com.nuvepro.moodle.config.Settings;
import com.nuvepro.moodle.helpers.ApiClient;
import com.nuvepro.moodle.helpers.PracticeProjectFixtures;
import com.nuvepro.moodle.pages.GuidedLanding;
import com.nuvepro.moodle.pages.PracticeProjectReportsPage;
import io.cucumber.java.After;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import org.testng.SkipException;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.testng.Assert.assertTrue;

/** Configurable-report coverage. Site-created reports are discovered by name or explicit .env URL. */
public class PracticeProjectReportSteps {
    private final TestContext ctx;
    private PracticeProjectReportsPage report;
    private ApiClient.SeededUser user;
    private long guidedCourseId;
    private int rowsBefore;

    public PracticeProjectReportSteps(TestContext ctx) {
        this.ctx = ctx;
    }

    @Given("the Practice Project detailed report is available")
    public void thePracticeProjectDetailedReportIsAvailable() {
        report = new PracticeProjectReportsPage(ctx.page);
        if (!report.openDetailed()) {
            throw new SkipException("Practice Project detailed configurable report is unavailable; "
                    + "set PRACTICE_DETAILED_REPORT_URL");
        }
        rowsBefore = report.dataRows();
    }

    @Given("the Practice Project summary report is available")
    public void thePracticeProjectSummaryReportIsAvailable() {
        report = new PracticeProjectReportsPage(ctx.page);
        if (!report.openSummary()) {
            throw new SkipException("Practice Project summary configurable report is unavailable; "
                    + "set PRACTICE_SUMMARY_REPORT_URL");
        }
        rowsBefore = report.dataRows();
    }

    @Given("a fresh user is enrolled in the Practice Project course for reporting")
    public void aFreshUserIsEnrolledForReporting() {
        if (Settings.WS_TOKEN.isBlank()) throw new SkipException("MOODLE_WS_TOKEN not configured");
        guidedCourseId = guidedCourseId();
        user = ApiClient.createUser(System.currentTimeMillis());
        ApiClient.enrolUser(user.id, guidedCourseId, 5);
    }

    @When("admin filters the report by the fixture user's full email")
    public void adminFiltersByFixtureEmail() {
        assertTrue(report.applyEmailFilter(user.email), "report has no Email filter");
    }

    @Then("only the fixture user's report record is shown")
    public void onlyFixtureUserIsShown() {
        assertTrue(report.allRowsContain(user.email),
                "report contains no exact matching fixture-email row or unrelated rows");
    }

    @When("admin filters the report by a nonexistent email")
    public void adminFiltersByNonexistentEmail() {
        assertTrue(report.applyEmailFilter("missing." + System.currentTimeMillis() + "@example.invalid"),
                "report has no Email filter");
    }

    @Then("the report shows no records")
    public void reportShowsNoRecords() {
        assertTrue(report.noRowsOrNoRecords(), "nonexistent-email filter returned report rows");
    }

    @When("admin applies the report without filter values")
    public void adminAppliesWithoutFilterValues() {
        assertTrue(report.applyWithoutValues(), "report has no Apply control");
    }

    @Then("canceling a detailed-report filter restores its previous value")
    public void cancelingDetailedReportFilterRestoresPreviousValue() {
        assertTrue(report.cancelFilterChanges(),
                "Configurable detailed report has no working Cancel filter action");
    }

    @Then("the report remains usable and lists records")
    public void reportRemainsUsable() {
        assertTrue(report.dataRows() > 0 || rowsBefore == 0 && report.noRowsOrNoRecords(),
                "report failed after applying empty filters");
    }

    @Then("the detailed report exposes identity course activity enrolment and attempt columns")
    public void detailedReportExposesRequiredColumns() {
        assertTrue(report.hasDetailedIdentityColumns(),
                "detailed report lacks identity/course/activity/enrolment/attempt columns: " + report.headers());
    }

    @When("admin applies the All course filter")
    public void adminAppliesAllCourseFilter() {
        assertTrue(report.applyCourse("All"), "report has no Course filter");
    }

    @Then("the report returns records across courses")
    public void reportReturnsAcrossCourses() {
        assertTrue(report.dataRows() > 0, "All course filter returned no report records");
    }

    @When("admin applies a specific available course filter")
    public void adminAppliesSpecificCourseFilter() {
        String course = report.firstCourseOptionLabel();
        if (course.isBlank()) throw new SkipException("report has no specific course option");
        assertTrue(report.applyCourse(course), "could not apply specific Course filter");
    }

    @Then("the report keeps course and activity data visible")
    public void reportKeepsCourseAndActivityVisible() {
        assertTrue(report.headersContain("course")
                        && (report.headers().toLowerCase().contains("activity")
                        || report.headers().toLowerCase().contains("project")),
                "filtered detailed report lost course/activity columns");
        assertTrue(report.dataRows() > 0, "specific Course filter returned no rows");
    }

    @When("admin searches the summary report by fixture email")
    public void adminSearchesSummaryByFixtureEmail() {
        assertTrue(report.search(user.email), "summary report has no search box");
    }

    @Then("summary completed remaining and total project counts are consistent")
    public void summaryCountsAreConsistent() {
        assertTrue(report.summaryCountsAreConsistent(),
                "summary completed + remaining does not equal total, or completed exceeds total");
    }

    @When("the fixture user is unenrolled from the Practice Project course")
    public void fixtureUserIsUnenrolled() {
        ApiClient.unenrolUser(user.id, guidedCourseId);
        ctx.page.waitForTimeout(1_000);
    }

    @When("the fixture user is re-enrolled in the Practice Project course")
    public void fixtureUserIsReEnrolled() {
        ApiClient.enrolUser(user.id, guidedCourseId, 5);
        ctx.page.waitForTimeout(1_000);
    }

    @Then("the unenrolled fixture user is absent from the report")
    public void unenrolledFixtureIsAbsent() {
        if (report.openSummary()) {
            assertTrue(report.applyEmailFilter(user.email), "summary report has no Email filter");
        } else if (report.openDetailed()) {
            assertTrue(report.applyEmailFilter(user.email), "detailed report has no Email filter");
        } else {
            throw new SkipException("configured Practice Project reports unavailable");
        }
        assertTrue(report.noRowsOrNoRecords(),
                "unenrolled user remains in the Practice Project report");
    }

    @Then("the re-enrolled fixture user returns to the report")
    public void reEnrolledFixtureReturns() {
        if (!report.openDetailed()) throw new SkipException("detailed report unavailable");
        assertTrue(report.applyEmailFilter(user.email), "detailed report has no Email filter");
        assertTrue(report.allRowsContain(user.email), "re-enrolled user did not return to report");
    }

    @Then("the report state requires an external suspension self-enrolment or second-course fixture")
    public void externalReportFixtureRequired() {
        throw new SkipException("case converted but requires suspension/self-enrolment/second-course report fixture");
    }

    @Then("the report comparison requires database read access")
    public void reportComparisonRequiresDatabaseAccess() {
        throw new SkipException("case converted but exact report-to-database comparison requires DB read access");
    }

    private long guidedCourseId() {
        GuidedLanding landing = new GuidedLanding(ctx.page);
        landing.open(Integer.parseInt(PracticeProjectFixtures.primaryCmid(ctx.page)));
        String classes = ctx.page.locator("body").getAttribute("class");
        Matcher matcher = Pattern.compile("course-(\\d+)").matcher(classes == null ? "" : classes);
        if (!matcher.find()) throw new SkipException("could not determine Practice Project course id");
        return Long.parseLong(matcher.group(1));
    }

    @After
    public void cleanupReportFixture(io.cucumber.java.Scenario scenario) {
        String uri = scenario.getUri() == null ? "" : scenario.getUri().toString();
        if (!uri.contains("practice_project_reports")) {
            return;
        }
        if (!com.nuvepro.moodle.helpers.CleanupPolicy.runScenarioCleanup()) {
            System.out.println("[PP-reports] deferCleanup=true — seeded user left for @cleanup-users");
            return;
        }
        if (user != null) {
            try { ApiClient.deleteUser(user.id); } catch (Throwable ignored) {}
            user = null;
        }
    }
}
