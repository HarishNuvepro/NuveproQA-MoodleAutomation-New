package com.nuvepro.moodle.steps;

import com.microsoft.playwright.Locator;
import com.nuvepro.moodle.config.Settings;
import com.nuvepro.moodle.pages.AssessmentAdmin;
import io.cucumber.java.en.Then;
import org.testng.SkipException;

import static org.testng.Assert.assertTrue;

/** Set 3 (final) — a couple of real checks (randomization hides content editor; PS page keyboard nav) +
 *  honest skips for the integration / course-management / attempt cases and the API-token cases. */
public class AssessmentSet3Steps {
    private final TestContext ctx;
    private final AssessmentAdmin admin;

    public AssessmentSet3Steps(TestContext ctx) {
        this.ctx = ctx;
        this.admin = new AssessmentAdmin(ctx.page);
    }

    private int randomCmid() {
        String c = Settings.ASSESSMENT_RANDOM_CMID.isEmpty() ? Settings.ASSESSMENT_CMID : Settings.ASSESSMENT_RANDOM_CMID;
        if (c.isEmpty()) throw new SkipException("no assessment cmid set");
        return Integer.parseInt(c);
    }

    // A236: enabling randomization hides the manual challenge content source on the form
    @Then("enabling randomization hides the manual content source")
    public void randomizationHidesContentSource() {
        if (ctx.assessmentCourseMissing()) throw new SkipException("COURSE_ID not set and no assessment suite course");
        admin.openNewChallengeForm(ctx.assessmentCourseId());
        Locator rand = ctx.page.locator("select[name='assessment_randomization']");
        if (rand.count() == 0) throw new SkipException("assessment_randomization field not on the form");
        rand.selectOption(new com.microsoft.playwright.options.SelectOption().setLabel("Yes"));
        ctx.page.waitForTimeout(1_000);
        boolean hidden = true;
        Locator src = ctx.page.locator("[name='challengecontentsource'], [id*='challenge_content_editor'], [name='problem_statement']");
        for (int i = 0; i < src.count(); i++) {
            try { if (src.nth(i).isVisible()) hidden = false; } catch (RuntimeException ignored) {}
        }
        assertTrue(hidden, "the manual content source/editor is still visible when randomization is enabled");
    }

    // A327: the problem statements page is keyboard-navigable (interactive elements are focusable)
    @Then("the problem statements page is keyboard navigable")
    public void psPageKeyboardNavigable() {
        admin.openProblemStatements(admin.sectionIdFor(randomCmid()));
        ctx.page.locator("body").click();
        ctx.page.keyboard().press("Tab");
        ctx.page.keyboard().press("Tab");
        String active = (String) ctx.page.evaluate("() => document.activeElement ? document.activeElement.tagName : ''");
        assertTrue(active != null && (active.equals("A") || active.equals("BUTTON") || active.equals("INPUT") || active.equals("SELECT")),
                "keyboard Tab did not move focus to an interactive element on the problem statements page (was: " + active + ")");
    }

    // ---- honest skips ----
    @Then("this case needs a live attempt, an integration session, or a course-management action")
    public void needsIntegrationOrAttempt() {
        throw new SkipException("needs a learner attempt, an SSO/LTI session, or a course duplicate/import/"
                + "copy/delete action — not producible from existing admin data on this fixture.");
    }

    @Then("this case needs the result API token")
    public void needsResultApiToken() {
        throw new SkipException("needs RESULT_API_TOKEN in assessment.env (a WS token with the cloudlabs "
                + "assessment/course-result functions) to drive the version (v1/v2) API checks.");
    }
}
