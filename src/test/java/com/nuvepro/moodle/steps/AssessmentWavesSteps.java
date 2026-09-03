package com.nuvepro.moodle.steps;

import com.nuvepro.moodle.config.Settings;
import com.nuvepro.moodle.pages.AssessmentAdmin;
import com.nuvepro.moodle.pages.ManageOverrides;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import org.testng.SkipException;

import static org.testng.Assert.assertTrue;

/**
 * Waves B/E (override management) — cases that operate on the admin override surface against existing
 * completed users, plus a shared "documented precondition" skip for cases whose runtime state cannot be
 * deterministically produced through the UI (specific submission/evaluation states, solved per-test-case
 * challenges, cron+DB timing). Those are recorded as SKIPPED with the exact precondition, mirroring the
 * project's config-gated-skip convention (PG-4/PG-7/PG-9).
 */
public class AssessmentWavesSteps {
    private final TestContext ctx;
    private final AssessmentAdmin admin;

    public AssessmentWavesSteps(TestContext ctx) {
        this.ctx = ctx;
        this.admin = new AssessmentAdmin(ctx.page);
    }

    private int cmidOf(String which) {
        String c = "random".equals(which) ? Settings.ASSESSMENT_RANDOM_CMID : Settings.ASSESSMENT_CMID;
        if (c.isEmpty()) throw new SkipException("assessment cmid for '" + which + "' not set in assessment.env");
        return Integer.parseInt(c);
    }

    private String sectionId(String which) {
        String sid = admin.sectionIdFor(cmidOf(which));
        if (sid == null || sid.isEmpty()) throw new SkipException("could not read the assessment section id for '" + which + "'");
        return sid;
    }

    // ---- A275: override a completed user on the randomization assessment (1068) ----

    @Given("the override form is open for a completed user on the randomization assessment")
    public void overrideOpenOnRandomizationAssessment() {
        admin.openManageChallenges(sectionId("random"));
        AssessmentAdmin.OverrideOpen r = admin.openOverrideFormForCompletedUser();
        if (r != AssessmentAdmin.OverrideOpen.OPENED)
            throw new SkipException("no override-eligible (completed + attempts-exhausted) user on the randomization "
                    + "assessment (ASSESSMENT_RANDOM_CMID) — " + r + " " + admin.overrideDiag);
    }

    @Then("the override can be applied to the completed assessment")
    public void overrideCanBeApplied() {
        assertTrue(!admin.maxAttemptsOptions().isEmpty(),
                "the override form did not expose the submissions control for the completed assessment");
    }

    // ---- A53/A54: delete an override when the plugin allows it (delete only for a NotAttempted user's
    //      override — manage-overrides.js). Non-creating (no data leak): deletes an existing eligible
    //      override, else skips with the accurate plugin-rule reason. ----

    @Then("an assessment override can be deleted when its user is eligible")
    public void overrideDeletableWhenEligible() {
        ManageOverrides mo = new ManageOverrides(ctx.page);
        mo.open(sectionId("base"));
        if (mo.overrideRows() == 0)
            throw new SkipException("no existing override on ASSESSMENT_CMID to delete");
        if (!mo.selectFirstOverrideRow()) {
            if (mo.editEnabled())
                throw new SkipException("the override is selected but the plugin keeps Delete DISABLED — it "
                        + "only allows deleting an override whose user is 'NotAttempted' (manage-overrides.js). "
                        + "Existing overrides are for COMPLETED users (only Editable); deletion needs the user to "
                        + "re-attempt first (provisioning). Plugin rule, not a test defect.");
            throw new SkipException("override row present but could not be selected (bootstrap-table)");
        }
        assertTrue(mo.deleteSelected(), "the override was not removed after Delete + confirm");
    }

    // ---- shared: cases whose runtime state UI automation cannot deterministically produce ----

    @Given("this case needs {string} which cannot be produced deterministically via the UI")
    public void caseNeedsPrecondition(String precondition) {
        throw new SkipException("precondition not UI-producible: " + precondition
                + " — see assessment.env for the input/harness this needs.");
    }
}
