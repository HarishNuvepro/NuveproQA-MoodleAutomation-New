package com.nuvepro.moodle.steps;

import com.microsoft.playwright.Locator;
import com.nuvepro.moodle.config.Settings;
import com.nuvepro.moodle.pages.AssessmentAdmin;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import org.testng.SkipException;

import static org.testng.Assert.assertTrue;

/**
 * "Easy" (non-provisioning) assessment cases — the form/config/presence subset that runs in the fast
 * suite (no cloud lab). Grounded in mod_form.php (assessment_randomization default 'No', challenge-only)
 * and the assessment landing (#filter_attempts_id). The many other "easy"-classified rows actually need
 * attempt DATA / git content / visual checks and are NOT covered here.
 */
public class AssessmentEasySteps {
    private final TestContext ctx;
    private final AssessmentAdmin admin;

    public AssessmentEasySteps(TestContext ctx) {
        this.ctx = ctx;
        this.admin = new AssessmentAdmin(ctx.page);
    }

    private int cmid() {
        if (Settings.ASSESSMENT_CMID.isEmpty()) throw new SkipException("ASSESSMENT_CMID not set");
        return Integer.parseInt(Settings.ASSESSMENT_CMID);
    }

    // ---- A233 / A234 / A238 : randomized problem statement field on the challenge form ----

    @Given("a new assessment challenge activity form")
    public void aNewChallengeForm() {
        if (Settings.COURSE_ID.isEmpty()) throw new SkipException("COURSE_ID not set");
        admin.openNewChallengeForm(Settings.COURSE_ID);
    }

    @Then("the randomized problem statement field is present")
    public void randomizationFieldPresent() {
        assertTrue(ctx.page.locator("select[name='assessment_randomization']").count() > 0,
                "the 'Randomized problem Statement' field is not on the challenge form");
    }

    @Then("the randomized problem statement field defaults to {string}")
    public void randomizationDefaultsTo(String expected) {
        String actual = admin.selectedByName("assessment_randomization");
        assertTrue(actual.equalsIgnoreCase(expected),
                "randomized problem statement default was '" + actual + "', expected '" + expected + "'");
    }

    // ---- A235 / A237 / A238 : randomized problem statement hideIf effects ----

    private boolean visibleBecomes(String selector, boolean want, int tenths) {
        boolean state = !want;
        for (int i = 0; i < tenths; i++) {
            Locator l = ctx.page.locator(selector);
            state = false;
            for (int j = 0; j < l.count(); j++) {
                try { if (l.nth(j).isVisible()) { state = true; break; } } catch (RuntimeException ignored) {}
            }
            if (state == want) return state;
            ctx.page.waitForTimeout(200);
        }
        return state;
    }

    @Then("enabling randomized problem statement hides the problem statement content field")
    public void enablingRandomizationHidesPS() {
        ctx.page.selectOption("select[name='assessment_randomization']",
                new com.microsoft.playwright.options.SelectOption().setLabel("Yes"));
        assertTrue(!visibleBecomes("[name='problem_statement']", false, 20),
                "the problem statement field is still visible when randomized problem statement = Yes");
    }

    @Then("disabling randomized problem statement shows the problem statement content field")
    public void disablingRandomizationShowsPS() {
        ctx.page.selectOption("select[name='assessment_randomization']",
                new com.microsoft.playwright.options.SelectOption().setLabel("No"));
        assertTrue(visibleBecomes("[name='problem_statement']", true, 20),
                "the problem statement field is not visible when randomized problem statement = No");
    }

    @Then("a sandbox activity form has no randomized problem statement field")
    public void sandboxHasNoRandomizationField() {
        if (Settings.PLAYGROUND_CMID.isEmpty()) throw new SkipException("PLAYGROUND_CMID not set");
        admin.openActivityEditForm(Integer.parseInt(Settings.PLAYGROUND_CMID));
        assertTrue(!visibleBecomes("select[name='assessment_randomization']", true, 10),
                "the randomized problem statement field is unexpectedly visible on a sandbox activity form");
    }

    // ---- A298 / A303 : attempts filter dropdown on the landing ----

    @Then("the assessment landing shows the attempts filter dropdown")
    public void assessmentLandingHasAttemptsFilter() {
        admin.openLanding(cmid());
        assertTrue(ctx.page.locator("#filter_attempts_id").count() > 0,
                "the attempts filter dropdown (#filter_attempts_id) is not on the assessment landing");
    }

    @Then("a sandbox activity landing has no attempts filter dropdown")
    public void sandboxLandingHasNoAttemptsFilter() {
        if (Settings.PLAYGROUND_CMID.isEmpty()) throw new SkipException("PLAYGROUND_CMID not set");
        admin.openLanding(Integer.parseInt(Settings.PLAYGROUND_CMID));
        assertTrue(ctx.page.locator("#filter_attempts_id").count() == 0,
                "a sandbox landing unexpectedly shows the attempts filter dropdown (assessment-only)");
    }

    // ---- A21 : create-form validation — due date before start date is rejected ----

    @When("admin creates an assessment with the due date before the start date")
    public void createWithDueBeforeStart() {
        if (Settings.COURSE_ID.isEmpty()) throw new SkipException("COURSE_ID not set");
        admin.openNewChallengeForm(Settings.COURSE_ID);
        ctx.page.locator("#id_name").fill("Auto invalid-dates " + System.currentTimeMillis());
        for (String f : new String[]{"submissionfromdate", "submissionduedate"}) {
            Locator en = ctx.page.locator("#id_" + f + "_enabled");
            if (en.count() > 0 && !en.first().isChecked()) en.first().check(new Locator.CheckOptions().setForce(true));
        }
        ctx.page.waitForTimeout(400);
        Locator fromYear = ctx.page.locator("#id_submissionfromdate_year");
        if (fromYear.count() == 0) throw new SkipException("submission date fields not on the form");
        ctx.page.selectOption("#id_submissionduedate_year",
                String.valueOf(Integer.parseInt(fromYear.inputValue()) - 1));   // due a year before start
        ctx.page.locator("#id_submitbutton").click();
        ctx.page.waitForTimeout(2_500);
    }

    @Then("the activity is not created and a date validation error is shown")
    public void activityNotCreatedDateError() {
        assertTrue(ctx.page.url().contains("modedit.php"),
                "the activity was accepted with due<start — validation missing (an activity may have been created!)");
        assertTrue(ctx.page.locator("[id^='id_error_'], .error, .alert-danger").count() > 0
                        || ctx.page.locator("body").innerText().toLowerCase().contains("date"),
                "no validation error shown for due-date-before-start on create");
    }
}
