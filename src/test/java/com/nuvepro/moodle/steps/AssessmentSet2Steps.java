package com.nuvepro.moodle.steps;

import com.microsoft.playwright.Locator;
import com.nuvepro.moodle.config.Settings;
import com.nuvepro.moodle.pages.AssessmentAdmin;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import org.testng.SkipException;

import static org.testng.Assert.assertTrue;

/**
 * Set 2 — Retake Cooldown config + manage-challenges columns (verifiable on the form/admin pages) plus
 * honest skips for the cooldown-state / attempt-dependent cases. Cooldown fields: cooldownperiod (select)
 * and attemptsaftercooldown (shown only when a cooldown is set) — from mod_form.php.
 */
public class AssessmentSet2Steps {
    private final TestContext ctx;
    private final AssessmentAdmin admin;

    public AssessmentSet2Steps(TestContext ctx) {
        this.ctx = ctx;
        this.admin = new AssessmentAdmin(ctx.page);
    }

    private int baseCmid() {
        if (Settings.ASSESSMENT_CMID.isEmpty()) throw new SkipException("ASSESSMENT_CMID not set");
        return Integer.parseInt(Settings.ASSESSMENT_CMID);
    }
    private String baseSection() {
        String s = admin.sectionIdFor(baseCmid());
        if (s.isEmpty()) throw new SkipException("could not read the assessment section id");
        return s;
    }
    private boolean present(String sel) { return ctx.page.locator(sel).count() > 0; }
    private boolean vis(String sel) {
        Locator l = ctx.page.locator(sel);
        for (int i = 0; i < l.count(); i++) { try { if (l.nth(i).isVisible()) return true; } catch (RuntimeException ignored) {} }
        return false;
    }

    @Given("a challenge activity form for cooldown checks")
    public void challengeFormForCooldown() {
        if (ctx.assessmentCourseMissing()) throw new SkipException("COURSE_ID not set and no assessment suite course");
        admin.openNewChallengeForm(ctx.assessmentCourseId());
    }

    @Then("the cooldown period field is on the form")
    public void cooldownFieldOnForm() {
        assertTrue(present("select[name='cooldownperiod'], input[name='cooldownperiod']"),
                "the cooldown period field is not on the challenge form");
    }

    @Then("setting a cooldown period shows the attempts-after-cooldown field")
    public void cooldownShowsAttemptsAfter() {
        Locator cd = ctx.page.locator("select[name='cooldownperiod']");
        if (cd.count() == 0) throw new SkipException("cooldownperiod field not on the form");
        cd.selectOption("1");
        ctx.page.waitForTimeout(1_000);
        assertTrue(vis("[name='attemptsaftercooldown']"),
                "attempts-after-cooldown was not shown when a cooldown period is set");
    }

    @Then("setting cooldown to None hides the attempts-after-cooldown field")
    public void cooldownNoneHidesAttemptsAfter() {
        Locator cd = ctx.page.locator("select[name='cooldownperiod']");
        if (cd.count() == 0) throw new SkipException("cooldownperiod field not on the form");
        cd.selectOption("0");
        ctx.page.waitForTimeout(1_000);
        assertTrue(!vis("[name='attemptsaftercooldown']"),
                "attempts-after-cooldown is still shown when cooldown = None");
    }

    @Then("the attempts allowed field is on the form")
    public void attemptsAllowedFieldOnForm() {
        assertTrue(present("select[name='assessmentretake'], [name='assessmentretake']"),
                "the Attempts Allowed field is not on the challenge form");
    }

    // manage challenges columns (attempts / submissions / retake)
    @Then("manage challenges has the {word} column")
    public void manageChallengesHasColumn(String field) {
        admin.openManageChallenges(baseSection());
        assertTrue(present(AssessmentAdmin.MC_TABLE + " thead th[data-field='" + field + "']"),
                "manage challenges has no '" + field + "' column");
    }

    // ---- honest skips: cooldown-state / attempt-dependent / terminology-on-attempt ----
    @Then("this cooldown case needs a live attempt or cooldown state")
    public void cooldownNeedsState() {
        throw new SkipException("needs a user with attempts + an active/elapsed cooldown window (provisioning) "
                + "— not producible from existing admin data.");
    }
}
