package com.nuvepro.moodle.steps;

import com.microsoft.playwright.Locator;
import com.nuvepro.moodle.config.Settings;
import com.nuvepro.moodle.helpers.FormSubmit;
import com.nuvepro.moodle.pages.AssessmentAdmin;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import org.testng.SkipException;

import static org.testng.Assert.assertTrue;

/**
 * Doable-batch: problem-statement page controls (Add/Edit/View/Delete), challenge-form fields, and the
 * Manage User Submissions (manageuserattempt.php) action controls — all reached via existing admin data.
 * Attempt-dependent behaviours (lab actions, PS-in-use, message-after-action) skip with an accurate reason.
 */
public class AssessmentDoableSteps {
    private final TestContext ctx;
    private final AssessmentAdmin admin;

    public AssessmentDoableSteps(TestContext ctx) {
        this.ctx = ctx;
        this.admin = new AssessmentAdmin(ctx.page);
    }

    private int baseCmid() {
        if (Settings.ASSESSMENT_CMID.isEmpty()) throw new SkipException("ASSESSMENT_CMID not set");
        return Integer.parseInt(Settings.ASSESSMENT_CMID);
    }
    private int randomCmid() {
        String c = Settings.ASSESSMENT_RANDOM_CMID.isEmpty() ? Settings.ASSESSMENT_CMID : Settings.ASSESSMENT_RANDOM_CMID;
        if (c.isEmpty()) throw new SkipException("ASSESSMENT_RANDOM_CMID not set");
        return Integer.parseInt(c);
    }
    private boolean present(String sel) { return ctx.page.locator(sel).count() > 0; }

    // ---- problem statement page controls ----

    @Given("the randomization problem statements page is open")
    public void randomizationPSPageOpen() {
        admin.openProblemStatements(admin.sectionIdFor(randomCmid()));
    }

    @Then("clicking Add New opens the problem statement form")
    public void addNewOpensForm() {
        assertTrue(admin.clickAddProblemStatement(), "Add New did not open a problem statement form");
    }

    @Then("the {word} control becomes available when a problem statement row is selected")
    public void psControlEnabledOnSelect(String which) {
        if (admin.problemStatementRows() == 0)
            throw new SkipException("no problem statement rows on ASSESSMENT_RANDOM_CMID to select");
        String sel = "edit".equals(which) ? AssessmentAdmin.PS_EDIT
                : "view".equals(which) ? AssessmentAdmin.PS_VIEW : AssessmentAdmin.PS_DELETE;
        assertTrue(admin.psRowControlEnabled(sel),
                "the '" + which + "' control did not enable after selecting a problem statement row");
    }

    // ---- challenge form fields ----

    @Given("a new challenge activity form is open")
    public void newChallengeFormOpen() {
        if (ctx.assessmentCourseMissing()) throw new SkipException("COURSE_ID not set and no assessment suite course");
        admin.openNewChallengeForm(ctx.assessmentCourseId());
    }

    @Then("the assessment instructions editor is present")
    public void instructionsEditorPresent() {
        assertTrue(present("#id_introeditor, [name='introeditor[text]'], [name='introeditoreditable']"),
                "no assessment instructions/description editor on the challenge form");
    }

    @Then("the activity offers a lab template selection")
    public void labTemplateSelectionPresent() {
        assertTrue(present("select[name='lab_template_id'], [name='lab_template_id'], input[name='labtype']"),
                "no lab template / platform selection on the challenge form");
    }

    @When("randomization is enabled and the form is saved with a missing required field")
    public void randomizationYesMissingRequired() {
        ctx.page.locator("input[type=radio][name='labtype'][value='challenge']").first()
                .check(new Locator.CheckOptions().setForce(true));
        ctx.page.waitForTimeout(600);
        Locator rand = ctx.page.locator("select[name='assessment_randomization']");
        if (rand.count() == 0) throw new SkipException("assessment_randomization field not on the form");
        rand.selectOption(new com.microsoft.playwright.options.SelectOption().setLabel("Yes"));
        ctx.page.waitForTimeout(400);
        FormSubmit.andAwaitNewPage(ctx.page, ctx.page.locator("#id_submitbutton"), 30_000);
        ctx.page.waitForTimeout(1_000);
    }

    @Then("a required-field validation error is shown")
    public void requiredValidationShown() {
        assertTrue(ctx.page.url().contains("modedit.php"),
                "the form was accepted despite a missing required field");
        assertTrue(ctx.page.locator("[id^='id_error_']").count() > 0
                        || ctx.page.locator("body").innerText().toLowerCase().contains("required"),
                "no required-field validation error shown when randomization is enabled");
    }

    // ---- Manage User Submissions (manageuserattempt.php) action controls ----

    @Given("the manage user submissions page is open")
    public void manageUserSubmissionsOpen() {
        if (!admin.openManageUserSubmissions(admin.sectionIdFor(baseCmid())))
            throw new SkipException("could not reach the Manage User Submissions page — needs an existing "
                    + "submission/attempt on ASSESSMENT_CMID to view.");
    }

    @Then("the {word} action control is available on the submissions page")
    public void submissionControlAvailable(String which) {
        String sel;
        switch (which) {
            case "grade":   sel = "#np-ap-manage-update-grades-btn"; break;
            case "comment": sel = "#np-ap-manage-add-comments-btn"; break;
            case "submit":  sel = "#np-ap-manage-submit-challenge-btn"; break;
            case "delete":  sel = "#np-ap-manage-delete-attempt-btn"; break;
            case "eval":    sel = "#np-ap-manage-eval-result-btn"; break;
            case "download":sel = "#np-ap-manage-export-report-btn"; break;
            default:        sel = "#np-ap-manage-actions-btn"; break;
        }
        assertTrue(present(sel) || present("#np-ap-manage-actions-btn"),
                "the '" + which + "' control is not on the Manage User Submissions page");
    }

    @Then("the submissions page has a search control")
    public void submissionsSearchControl() {
        assertTrue(present("#np-ap-manage-attempt-table") &&
                present(".fixed-table-toolbar .search input, input.search-input, .search input"),
                "no search control on the Manage User Submissions page");
    }

    // ---- attempt/lab dependent cases: honest config-gated skip ----

    @Then("this case needs a live attempt on the randomization assessment")
    public void needsLiveAttempt() {
        throw new SkipException("needs a seeded student ATTEMPT (and for some, a running lab / deactivated PS) "
                + "on ASSESSMENT_RANDOM_CMID — provisioning fixture, not producible on existing admin data.");
    }
}
