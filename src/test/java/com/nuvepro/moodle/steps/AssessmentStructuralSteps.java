package com.nuvepro.moodle.steps;

import com.microsoft.playwright.Locator;
import com.nuvepro.moodle.config.Settings;
import com.nuvepro.moodle.pages.AssessmentAdmin;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import org.testng.SkipException;

import static org.testng.Assert.assertTrue;

/**
 * Non-provisioning STRUCTURAL cases — admin page controls/columns that render without a lab or attempt:
 * the challenge form (test-case-marks radio, cooldown field, github example), the manage-challenges
 * completion column, and the User Attempts page (buttons, columns, search, pagination). Selectors from
 * mod_form.php + manage-challenge/user-attempts templates. Skips if the target activity/data is absent.
 */
public class AssessmentStructuralSteps {
    private final TestContext ctx;
    private final AssessmentAdmin admin;

    public AssessmentStructuralSteps(TestContext ctx) {
        this.ctx = ctx;
        this.admin = new AssessmentAdmin(ctx.page);
    }

    private int cmid() {
        if (Settings.ASSESSMENT_CMID.isEmpty()) throw new SkipException("ASSESSMENT_CMID not set");
        return Integer.parseInt(Settings.ASSESSMENT_CMID);
    }
    private String sectionId() {
        String s = admin.sectionIdFor(cmid());
        if (s.isEmpty()) throw new SkipException("could not read the assessment section id");
        return s;
    }
    private boolean present(String sel) { return ctx.page.locator(sel).count() > 0; }

    // ---- challenge-form structural (A62 / A148 / A218) ----

    @Given("a new assessment challenge activity form for structural checks")
    public void aNewChallengeForm() {
        if (Settings.COURSE_ID.isEmpty()) throw new SkipException("COURSE_ID not set");
        admin.openNewChallengeForm(Settings.COURSE_ID);
    }

    @Then("the test-case different-marks option is available for auto evaluation")
    public void differentMarksOptionAvailable() {
        Locator auto = ctx.page.locator("input[type=radio][name='evaluationtype'][value='AUTO']");
        if (auto.count() > 0) { auto.first().check(new Locator.CheckOptions().setForce(true)); ctx.page.waitForTimeout(600); }
        assertTrue(present("input[type=radio][name='testcaseweightage'][value='DIFFERENT']"),
                "the 'Different marks per test case' option is not on the challenge form");
    }

    @Then("the cooldown period field is available in the challenge form")
    public void cooldownFieldAvailable() {
        assertTrue(present("select[name='cooldownperiod'], input[name='cooldownperiod']"),
                "the cooldown period field is not on the challenge form");
    }

    @Then("the problem statement git url example is shown on the form")
    public void gitUrlExampleShown() {
        assertTrue(present("#fitem_id_problem_statement_sample"),
                "the github url example (#fitem_id_problem_statement_sample) is not on the challenge form");
    }

    // ---- manage challenges column (A34) ----

    @Then("the manage challenges table has a completion status column")
    public void manageChallengesHasCompletionColumn() {
        admin.openManageChallenges(sectionId());
        assertTrue(present(AssessmentAdmin.MC_TABLE + " thead th[data-field='completionstatus']"),
                "the manage-challenges table has no completion status column");
    }

    // ---- User Attempts page structural (A290 / A292 / A293 / A295 / A297) ----

    @Given("the user attempts page is open for an enrolled user")
    public void userAttemptsPageOpen() {
        if (!admin.openFirstUserAttemptsPage(sectionId()))
            throw new SkipException("could not open the User Attempts page (no enrolled user / drill-down unavailable)");
    }

    @Then("the user attempts page shows the user summary details")
    public void userAttemptsShowsSummary() {
        String body = ctx.page.locator(".np-ap-cl-admin-user-details, #region-main").first().innerText().toLowerCase();
        assertTrue(body.contains("name") && body.contains("email") && body.contains("attempt"),
                "the user attempts summary (name/email/attempts) is not shown");
    }

    @Then("the user attempts page shows the download report and view submission controls")
    public void userAttemptsShowsControls() {
        assertTrue(present("#np-ap-user-attempt-export-report-btn"), "no Download Report control on the user attempts page");
        assertTrue(present("#viewUserAttemptsSubmission"), "no View Submission control on the user attempts page");
    }

    // A288 / A294: the User Attempt button is present on manage challenges (assessment + normal activity)
    @Then("the user attempt button is present on manage challenges")
    public void userAttemptButtonPresent() {
        admin.openManageChallenges(sectionId());
        assertTrue(present("#np-ap-user-attempt"), "the User Attempt button is not on manage challenges");
    }

    // A289: selecting a user and opening User Attempts reaches the drill-down page
    @Then("the user attempts drill-down opens")
    public void userAttemptsDrillDownOpens() {
        if (!admin.openFirstUserAttemptsPage(sectionId()))
            throw new SkipException("no enrolled user to drill into on manage challenges");
        assertTrue(ctx.page.url().contains("userattempts.php"),
                "the User Attempt button did not open the user attempts drill-down");
    }

    // A295: the user attempts page has a search control
    @Then("the user attempts page has a search box")
    public void userAttemptsHasSearchBox() {
        assertTrue(present(".fixed-table-toolbar .search input, input.search-input, .search input"),
                "no search box on the user attempts page");
    }

    @Then("the user attempts table supports search and pagination")
    public void userAttemptsSearchAndPagination() {
        assertTrue(present("#np-ap-user-attempts-table"), "user attempts table not present");
        assertTrue(present(".fixed-table-toolbar .search input, input.search-input, .search input"),
                "no search box on the user attempts table");
        assertTrue(present(".fixed-table-pagination, .pagination"),
                "no pagination control on the user attempts table");
    }
}
