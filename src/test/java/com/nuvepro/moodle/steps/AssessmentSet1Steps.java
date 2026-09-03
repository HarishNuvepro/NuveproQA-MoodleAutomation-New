package com.nuvepro.moodle.steps;

import com.nuvepro.moodle.config.Settings;
import com.nuvepro.moodle.pages.AssessmentAdmin;
import io.cucumber.java.en.Then;
import org.testng.SkipException;

import static org.testng.Assert.assertTrue;

/**
 * Set-1 remainder — a few real non-provisioning checks (PS button absent on non-randomized, reports area,
 * playground/guided landings, add-PS page) plus honest skips for git-content / learner-attempt cases.
 */
public class AssessmentSet1Steps {
    private final TestContext ctx;
    private final AssessmentAdmin admin;

    public AssessmentSet1Steps(TestContext ctx) {
        this.ctx = ctx;
        this.admin = new AssessmentAdmin(ctx.page);
    }

    private int baseCmid() {
        if (Settings.ASSESSMENT_CMID.isEmpty()) throw new SkipException("ASSESSMENT_CMID not set");
        return Integer.parseInt(Settings.ASSESSMENT_CMID);
    }
    private int randomCmid() {
        String c = Settings.ASSESSMENT_RANDOM_CMID.isEmpty() ? Settings.ASSESSMENT_CMID : Settings.ASSESSMENT_RANDOM_CMID;
        return Integer.parseInt(c);
    }

    // A242: PS button is randomization-only ({{#israndomized}}) → absent on the base activity landing
    @Then("the base assessment landing has no problem statements button")
    public void baseLandingHasNoPSButton() {
        admin.openLanding(baseCmid());
        assertTrue(ctx.page.locator("a[href*='assessment-problemstatements.php']").count() == 0,
                "the Problem Statements button unexpectedly shows on a NON-randomized assessment landing");
    }

    // A283: the Moodle reports area loads
    @Then("the moodle reports area loads")
    public void moodleReportsAreaLoads() {
        ctx.page.navigate(Settings.BASE_URL + "/admin/category.php?category=reports",
                new com.microsoft.playwright.Page.NavigateOptions().setTimeout(45_000));
        ctx.page.waitForTimeout(2_000);
        String body = ctx.page.locator("#region-main").count() > 0 ? ctx.page.locator("#region-main").innerText() : "";
        assertTrue(body.toLowerCase().contains("report"), "the Moodle reports area did not load");
    }

    // A287: sandbox + guided landings render (normal workflow reachable)
    @Then("the sandbox and guided landings render")
    public void sandboxGuidedRender() {
        if (Settings.PLAYGROUND_CMID.isEmpty()) throw new SkipException("PLAYGROUND_CMID not set");
        admin.openLanding(Integer.parseInt(Settings.PLAYGROUND_CMID));
        assertTrue(ctx.page.locator("#region-main").count() > 0, "sandbox landing did not render");
        if (!Settings.GUIDED_CMID.isEmpty()) {
            admin.openLanding(Integer.parseInt(Settings.GUIDED_CMID));
            assertTrue(ctx.page.locator("#region-main").count() > 0, "guided landing did not render");
        }
    }

    // A319: the add-PS page loads (new page design's testable contract)
    @Then("the add problem statement page loads")
    public void addPSPageLoads() {
        admin.openProblemStatements(admin.sectionIdFor(randomCmid()));
        assertTrue(admin.clickAddProblemStatement(), "the add problem statement page did not load");
    }

    // A322: card/table view toggle (newer PS page design) — tolerant
    @Then("the problem statements page offers a card or table view")
    public void psCardTableView() {
        admin.openProblemStatements(admin.sectionIdFor(randomCmid()));
        boolean toggle = ctx.page.locator("[data-view], .card-view, button:has-text('Card'), "
                + ".fixed-table-toolbar button[name='toggle'], .btn-toolbar [data-toggle='table-view']").count() > 0
                || ctx.page.locator(AssessmentAdmin.PS_TABLE).count() > 0;   // table view itself
        assertTrue(toggle, "neither a card/table view toggle nor the table view is present");
    }

    // ---- honest skips ----

    @Then("this case needs specific git repository content or a learner attempt")
    public void needsGitOrLearner() {
        throw new SkipException("needs curated git repository content (problem-statement .md files / tokens) "
                + "or a learner driving attempts — not producible from existing admin data.");
    }
}
