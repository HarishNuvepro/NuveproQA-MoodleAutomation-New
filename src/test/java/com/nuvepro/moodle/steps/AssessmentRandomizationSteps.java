package com.nuvepro.moodle.steps;

import com.nuvepro.moodle.config.Settings;
import com.nuvepro.moodle.pages.AssessmentAdmin;
import io.cucumber.java.en.Then;
import org.testng.SkipException;

import static org.testng.Assert.assertTrue;
import static org.testng.Assert.assertEquals;

/**
 * Randomization-within-activity — the non-provisioning subset (landing/form/page controls that render
 * without a lab or attempt). Randomized activity = ASSESSMENT_RANDOM_CMID (1068); base/non-randomized =
 * ASSESSMENT_CMID (781). The many attempt/data-dependent rows in this cluster stay in the provisioning set.
 */
public class AssessmentRandomizationSteps {
    private final TestContext ctx;
    private final AssessmentAdmin admin;

    public AssessmentRandomizationSteps(TestContext ctx) {
        this.ctx = ctx;
        this.admin = new AssessmentAdmin(ctx.page);
    }

    private int randomCmid() {
        if (Settings.ASSESSMENT_RANDOM_CMID.isEmpty()) throw new SkipException("ASSESSMENT_RANDOM_CMID not set");
        return Integer.parseInt(Settings.ASSESSMENT_RANDOM_CMID);
    }
    private int baseCmid() {
        if (Settings.ASSESSMENT_CMID.isEmpty()) throw new SkipException("ASSESSMENT_CMID not set");
        return Integer.parseInt(Settings.ASSESSMENT_CMID);
    }

    // A241: the randomized activity landing offers the Assessment Problem Statements button
    @Then("the randomized assessment landing shows the problem statements button")
    public void randomizedLandingHasPSButton() {
        admin.openLanding(randomCmid());
        assertTrue(ctx.page.locator("a[href*='assessment-problemstatements.php'], "
                        + "a:has-text('Assessment Problem Statements')").count() > 0,
                "the 'Assessment Problem Statements' button is not on the randomized assessment landing");
    }

    // A243: a randomized activity's form has Randomized problem statement = Yes
    @Then("the randomized assessment activity has randomization set to Yes")
    public void randomizedActivityFieldYes() {
        admin.openActivityEditForm(randomCmid());
        assertEquals(admin.selectedByName("assessment_randomization"), "Yes",
                "the randomized activity's 'Randomized problem Statement' field is not Yes");
    }

    // A244: a non-randomized (base) activity's form has Randomized problem statement = No
    @Then("the base assessment activity has randomization set to No")
    public void baseActivityFieldNo() {
        admin.openActivityEditForm(baseCmid());
        assertEquals(admin.selectedByName("assessment_randomization"), "No",
                "the base activity's 'Randomized problem Statement' field is not No");
    }

    // A281: the problem statements page initialises (table renders)
    @Then("the assessment problem statements page initialises")
    public void psPageInitialises() {
        admin.openProblemStatements(admin.sectionIdFor(randomCmid()));
        assertTrue(admin.present(AssessmentAdmin.PS_TABLE),
                "the assessment problem statements table did not initialise");
    }

    // A282: the problem statements page has a working search control
    @Then("the assessment problem statements page has a search control")
    public void psPageHasSearch() {
        admin.openProblemStatements(admin.sectionIdFor(randomCmid()));
        assertTrue(ctx.page.locator(".fixed-table-toolbar .search input, input.search-input, .search input").count() > 0,
                "no search control on the assessment problem statements page");
    }
}
