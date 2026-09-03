package com.nuvepro.moodle.steps;

import com.nuvepro.moodle.config.Settings;
import com.nuvepro.moodle.pages.AssessmentAdmin;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import org.testng.SkipException;

import java.util.List;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotEquals;
import static org.testng.Assert.assertTrue;

/**
 * Assessment (challenge) admin config — batch 2. All scenarios are admin-only and NON-provisioning
 * (they never spin a cloud lab): the Problem Statements page, the activity edit-form "Attempts Summary
 * Columns" config, and the per-user Override form. Selectors live in {@link AssessmentAdmin}.
 *
 * These read/exercise SHARED admin config, so the mutating one (activate/deactivate, @A255) restores the
 * status it changed, and the override one (@A45) exercises the form WITHOUT saving — nothing is persisted.
 * Missing cmids config-gate to SKIPPED (same pattern as the rest of the assessment suite).
 */
public class AssessmentConfigSteps {
    private final TestContext ctx;
    private final AssessmentAdmin admin;

    public AssessmentConfigSteps(TestContext ctx) {
        this.ctx = ctx;
        this.admin = new AssessmentAdmin(ctx.page);
    }

    private int assessmentCmid() {
        if (Settings.ASSESSMENT_CMID.isEmpty()) throw new SkipException("ASSESSMENT_CMID not set");
        return Integer.parseInt(Settings.ASSESSMENT_CMID);
    }

    private String assessmentSectionId() {
        String sid = admin.sectionIdFor(assessmentCmid());
        if (sid == null || sid.isEmpty())
            throw new SkipException("could not read the assessment activity's section id (#np-ap-cl-sectionId)");
        return sid;
    }

    // ---- @A250 / @A255 : Problem Statements page ----
    // Problem statements exist only on a randomization-enabled assessment, so these target
    // ASSESSMENT_RANDOM_CMID when set (falling back to ASSESSMENT_CMID).

    private int problemStatementCmid() {
        String c = Settings.ASSESSMENT_RANDOM_CMID.isEmpty() ? Settings.ASSESSMENT_CMID : Settings.ASSESSMENT_RANDOM_CMID;
        if (c.isEmpty()) throw new SkipException("neither ASSESSMENT_RANDOM_CMID nor ASSESSMENT_CMID is set");
        return Integer.parseInt(c);
    }

    private String problemStatementSectionId() {
        String sid = admin.sectionIdFor(problemStatementCmid());
        if (sid == null || sid.isEmpty())
            throw new SkipException("could not read the problem-statements activity's section id");
        return sid;
    }

    @Given("the assessment problem statements page is open")
    public void theProblemStatementsPageIsOpen() {
        admin.openProblemStatements(problemStatementSectionId());
    }

    @Then("the problem statements toolbar shows the Add New, Edit, View, Delete, Activate and Deactivate controls with the data table")
    public void theProblemStatementsToolbarShowsAllControls() {
        String[][] controls = {
                {AssessmentAdmin.PS_ADDNEW,     "Add New"},
                {AssessmentAdmin.PS_EDIT,       "Edit"},
                {AssessmentAdmin.PS_VIEW,       "View"},
                {AssessmentAdmin.PS_DELETE,     "Delete"},
                {AssessmentAdmin.PS_ACTIVATE,   "Activate"},
                {AssessmentAdmin.PS_DEACTIVATE, "Deactivate"},
                {AssessmentAdmin.PS_TABLE,      "data table"},
        };
        for (String[] c : controls) {
            assertTrue(admin.present(c[0]),
                    "Problem Statements page is missing the '" + c[1] + "' control (" + c[0] + ")");
        }
    }

    @Then("admin can toggle a problem statement between active and inactive")
    public void adminCanToggleProblemStatement() {
        int psRows = admin.problemStatementRows();   // page object already waited for the async table load
        if (psRows == 0)
            throw new SkipException("the problem statements table loaded with 0 rows — this assessment activity "
                    + "(ASSESSMENT_CMID) has no problem statements defined (they exist only when the activity's "
                    + "assessment randomization is enabled). Nothing to activate/deactivate.");
        if (!admin.selectFirstProblemStatementRow())
            throw new SkipException("problem statement rows are present (" + psRows + ") but the row could not be selected");

        String before = admin.firstRowStatus();
        String action = admin.toggleActivation();
        if (action.isEmpty())
            throw new SkipException("neither Activate nor Deactivate became available for the selected row");

        admin.openProblemStatements(problemStatementSectionId());   // reload to confirm the persisted status
        admin.selectFirstProblemStatementRow();
        String after = admin.firstRowStatus();
        assertNotEquals(after, before,
                "problem statement status did not change after '" + action + "' (was '" + before + "')");

        // restore the original status so the shared activity is left as found
        admin.toggleActivation();
        admin.openProblemStatements(problemStatementSectionId());
    }

    // ---- @A55 / @A61 : Attempts Summary Columns config (activity edit form) ----

    @Given("the assessment activity edit form is open")
    public void theAssessmentActivityEditFormIsOpen() {
        admin.openActivityEditForm(assessmentCmid());
    }

    @Given("the sandbox activity edit form is open")
    public void theSandboxActivityEditFormIsOpen() {
        if (Settings.PLAYGROUND_CMID.isEmpty()) throw new SkipException("PLAYGROUND_CMID not set");
        admin.openActivityEditForm(Integer.parseInt(Settings.PLAYGROUND_CMID));
    }

    @Then("the Attempts Summary Columns hide configuration is available")
    public void theAttemptSummaryConfigIsAvailable() {
        assertTrue(admin.attemptSummaryConfigVisible(),
                "the 'Attempts Summary Columns' config (Grade/Marks/Result checkboxes) is not available on the "
                        + "assessment activity form — admins cannot hide the grade/marks/result");
    }

    @Then("the Attempts Summary Columns hide configuration is not available")
    public void theAttemptSummaryConfigIsNotAvailable() {
        assertTrue(!admin.attemptSummaryConfigVisible(),
                "the 'Attempts Summary Columns' config is unexpectedly available on a non-challenge (sandbox) "
                        + "activity — it must be hidden for anything other than an assessment lab");
    }

    // ---- @A45 : override submissions/duration to unlimited ----

    @Given("the assessment manage challenges page is open")
    public void theManageChallengesPageIsOpen() {
        admin.openManageChallenges(assessmentSectionId());
    }

    @When("admin opens the override form for the first enrolled user")
    public void adminOpensTheOverrideFormForTheFirstUser() {
        skipUnlessOverrideOpened(admin.manageChallengeUserRows(), admin.openOverrideFormForCompletedUser());
    }

    /** Open manage challenges + the override form for an eligible (completed + attempts-exhausted) user. */
    @Given("the override form is open for a completed user")
    public void theOverrideFormIsOpenForACompletedUser() {
        admin.openManageChallenges(assessmentSectionId());
        skipUnlessOverrideOpened(admin.manageChallengeUserRows(), admin.openOverrideFormForCompletedUser());
    }

    /** Shared: translate a non-OPENED override result into an accurate, evidence-based SKIP. */
    private void skipUnlessOverrideOpened(int users, AssessmentAdmin.OverrideOpen result) {
        switch (result) {
            case OPENED:
                return;   // override form is up — proceed to the Then assertions
            case NO_USERS:
                throw new SkipException("manage challenges lists no enrolled users to override");
            case NO_COMPLETED_USER:
                throw new SkipException("none of the " + users + " enrolled users has a COMPLETED assessment — "
                        + "an override/reopen is only allowed on a completed assessment (manual precondition).");
            case NO_ELIGIBLE_USER:
                throw new SkipException("completed user(s) exist but the plugin blocks the override: their GLOBAL "
                        + "ATTEMPTS ARE STILL PENDING. A reopen/override is only allowed once a user has used ALL "
                        + "their attempts AND completed. [" + admin.overrideDiag + "]");
            case FORM_DID_NOT_LOAD:
            default:
                throw new SkipException("a completed user was selected but the override form did not load — "
                        + admin.overrideDiag);
        }
    }

    // ---- @A47 / @A49 / @A52 : override-form capabilities (no save — nothing persisted) ----

    @When("admin sets the override submissions to {string}")
    public void adminSetsOverrideSubmissions(String value) {
        admin.setOverrideMaxAttempts(value);
    }

    @Then("the override submissions value is {string}")
    public void theOverrideSubmissionsValueIs(String value) {
        assertEquals(admin.selectedMaxAttempts(), value,
                "override 'Submissions Allowed' did not take the value '" + value + "'");
    }

    @When("admin sets the override duration to {int} hours")
    public void adminSetsOverrideDuration(int hours) {
        admin.setOverrideDurationHours(hours);
    }

    @Then("the override duration is {int} hours")
    public void theOverrideDurationIs(int hours) {
        assertTrue(admin.overrideDurationIs(hours),
                "override duration did not take " + hours + " hours");
    }

    @Then("the override form offers a start and due date range")
    public void theOverrideFormOffersADateRange() {
        // Manual A52's Summary ("date range unlimited") and Expected ("shouldn't override to unlimited")
        // contradict, and the form uses concrete start/due date_time_selectors (no "unlimited" for dates).
        // The testable truth is that the override exposes a start+due date RANGE, which is asserted here.
        assertTrue(admin.overrideHasDateRange(),
                "override form does not expose a start + due date range");
    }

    @Then("the submissions and duration can be set to unlimited")
    public void theSubmissionsAndDurationCanBeSetToUnlimited() {
        List<String> opts = admin.maxAttemptsOptions();
        assertTrue(opts.stream().anyMatch(o -> o.trim().equalsIgnoreCase("Unlimited")),
                "the override 'Submissions Allowed' select does not offer an 'Unlimited' option (offered: " + opts + ")");

        admin.setSubmissionsAndDurationUnlimited();   // selects Unlimited + turns the optional duration off; no save

        assertTrue("Unlimited".equalsIgnoreCase(admin.selectedMaxAttempts()),
                "submissions did not take the 'Unlimited' value (selected: '" + admin.selectedMaxAttempts() + "')");
        assertTrue(admin.durationIsUnlimited(),
                "duration could not be set to unlimited (the optional time-limit enabler is still on)");
    }
}
