package com.nuvepro.moodle.steps;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.nuvepro.moodle.config.Settings;
import com.nuvepro.moodle.helpers.ApiClient;
import com.nuvepro.moodle.helpers.AssessmentActivityRegistry;
import com.nuvepro.moodle.helpers.AssessmentCreateSpec;
import com.nuvepro.moodle.helpers.AssessmentTestData;
import com.nuvepro.moodle.helpers.Auth;
import com.nuvepro.moodle.pages.ChallengeActivityForm;
import com.nuvepro.moodle.pages.CourseView;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import org.testng.SkipException;

import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;

/**
 * Assessment activity creation. Dataset names live in {@link AssessmentTestData};
 * the form UI lives in {@link ChallengeActivityForm}.
 */
public class AssessmentCreateSteps {
    private static final String H_SECTION = "#np-ap-cl-sectionId";

    private final TestContext ctx;
    private final ChallengeActivityForm form;
    private AssessmentCreateSpec last;
    private String createdCmid;
    private ApiClient.SeededUser student;

    public AssessmentCreateSteps(TestContext ctx) {
        this.ctx = ctx;
        this.form = new ChallengeActivityForm(ctx.page);
    }

    @When("admin creates an assessment activity using {string}")
    public void adminCreatesAnAssessmentActivityUsing(String dataset) {
        if (Settings.COURSE_ID.isEmpty()) throw new SkipException("COURSE_ID not set");
        ctx.newActivityDataset = dataset;
        if ((ctx.hasAnyTagStartingWith("@A-user-") || ctx.hasAnyTagStartingWith("@LT-"))
                && !ctx.hasTag("@fresh_activity_reuse_user") && !ctx.hasTag("@fresh_activity")
                && reuseRegisteredActivity(dataset)) {
            return;
        }
        last = AssessmentTestData.create(dataset);
        ctx.lastCreateSpec = last;
        form.openAddFormFromCourse(Settings.COURSE_ID);
        form.fill(last);
        form.saveAndDisplay();
        createdCmid = form.activityCmidFromUrl();
        ctx.newActivityCmid = createdCmid;
        ctx.newActivityName = last.name;
        captureSectionIdFromLanding();
        ensureShownAndRegister(dataset);
    }

    private void captureSectionIdFromLanding() {
        var sec = ctx.page.locator(H_SECTION);
        if (sec.count() > 0) ctx.sectionId = sec.first().inputValue();
    }

    private boolean reuseRegisteredActivity(String dataset) {
        AssessmentActivityRegistry.Entry entry = AssessmentActivityRegistry.get(dataset);
        if (entry == null || entry.cmid() == null || entry.cmid().isBlank()) return false;
        createdCmid = entry.cmid();
        ctx.newActivityCmid = entry.cmid();
        ctx.newActivityName = entry.name();
        ctx.sectionId = entry.sectionId();
        // A-user-9 may hide eval_auto; later scenarios still need a visible, launchable activity.
        new CourseView(ctx.page).showActivityOnCoursePage(Settings.COURSE_ID,
                Integer.parseInt(entry.cmid()), entry.name());
        ctx.page.navigate(Settings.BASE_URL + "/mod/cloudlabs/view.php?id=" + entry.cmid(),
                new Page.NavigateOptions()
                        .setWaitUntil(com.microsoft.playwright.options.WaitUntilState.DOMCONTENTLOADED)
                        .setTimeout(45_000));
        syncActivityNameFromLanding(dataset, entry.cmid(), entry.sectionId());
        return true;
    }

    /** Keep registry/context name aligned with the live landing (registry can drift across runs). */
    private void syncActivityNameFromLanding(String dataset, String cmid, String sectionId) {
        Locator title = ctx.page.locator("h1, .np-ap-cl-main-title").first();
        try {
            title.waitFor(new Locator.WaitForOptions().setTimeout(15_000));
        } catch (RuntimeException ignored) {
            return;
        }
        String live = title.innerText().trim();
        if (live.isBlank()) return;
        ctx.newActivityName = live;
        AssessmentActivityRegistry.put(dataset, live, cmid, sectionId);
    }

    private void ensureShownAndRegister(String dataset) {
        if (ctx.newActivityCmid == null || ctx.newActivityCmid.isBlank()) return;
        new CourseView(ctx.page).showActivityOnCoursePage(Settings.COURSE_ID,
                Integer.parseInt(ctx.newActivityCmid), ctx.newActivityName);
        ctx.page.navigate(Settings.BASE_URL + "/mod/cloudlabs/view.php?id=" + ctx.newActivityCmid,
                new Page.NavigateOptions()
                        .setWaitUntil(com.microsoft.playwright.options.WaitUntilState.DOMCONTENTLOADED)
                        .setTimeout(45_000));
        captureSectionIdFromLanding();
        AssessmentActivityRegistry.put(dataset, ctx.newActivityName, ctx.newActivityCmid, ctx.sectionId);
    }

    @Given("admin shows the created assessment on the course page")
    public void adminShowsTheCreatedAssessmentOnTheCoursePage() {
        if (Settings.COURSE_ID.isEmpty()) throw new SkipException("COURSE_ID not set");
        if (ctx.newActivityCmid == null || ctx.newActivityCmid.isBlank()) {
            throw new SkipException("no created assessment cmid — run the create step first");
        }
        new CourseView(ctx.page).showActivityOnCoursePage(Settings.COURSE_ID,
                Integer.parseInt(ctx.newActivityCmid), ctx.newActivityName);
    }

    @Then("the assessment is created and the saved settings match {string}")
    public void theAssessmentIsCreatedAndTheSavedSettingsMatch(String dataset) {
        assertNotNull(last, "no create dataset was filled (expected " + dataset + ")");
        assertTrue(form.leftTheForm(), "still on the activity form after Save and display (url=" + ctx.page.url() + ")");
        assertNotNull(createdCmid, "created assessment cmid was not in the landing URL: " + ctx.page.url());
        assertFalse(createdCmid.equals("-1"), "create did not produce an activity id");
        assertTrue(form.landingShows(last.name),
                "created assessment name not shown on Save and display: " + last.name);
        form.openEditForm(createdCmid);
        form.assertSavedAs(last);
    }

    @When("admin tries to create an assessment activity using {string}")
    public void adminTriesToCreateAnAssessmentActivityUsing(String dataset) {
        if (Settings.COURSE_ID.isEmpty()) throw new SkipException("COURSE_ID not set");
        last = AssessmentTestData.create(dataset);
        form.openAddFormFromCourse(Settings.COURSE_ID);
        form.fill(last);
        form.saveAndDisplay();
    }

    @Then("the activity is not created and a validation error is shown")
    public void theActivityIsNotCreatedAndAValidationErrorIsShown() {
        assertTrue(form.rejectedCreate(),
                "create was accepted (url=" + ctx.page.url() + ") — expected to stay on the form with validation");
        assertTrue(ctx.page.url().contains("modedit.php"),
                "left the add form; activity may have been created: " + ctx.page.url());
    }

    @When("an enrolled student opens the course")
    public void anEnrolledStudentOpensTheCourse() {
        if (Settings.COURSE_ID.isEmpty()) throw new SkipException("COURSE_ID not set");
        if (Settings.WS_TOKEN.isEmpty()) throw new SkipException("MOODLE_WS_TOKEN not set");
        student = ApiClient.createUser(System.currentTimeMillis());
        ApiClient.enrolUser(student.id, Long.parseLong(Settings.COURSE_ID), 5);
        ctx.studentContext = GlobalHooks.browser.newContext(new Browser.NewContextOptions()
                .setBaseURL(Settings.BASE_URL)
                .setIgnoreHTTPSErrors(true));
        ctx.studentPage = ctx.studentContext.newPage();
        ctx.studentPage.setDefaultTimeout(Settings.DEFAULT_TIMEOUT_MS);
        Auth.uiLogin(ctx.studentPage, student.username, student.password);
        ctx.studentPage.navigate(Settings.BASE_URL + "/course/view.php?id=" + Settings.COURSE_ID,
                new Page.NavigateOptions()
                        .setWaitUntil(com.microsoft.playwright.options.WaitUntilState.DOMCONTENTLOADED)
                        .setTimeout(45_000));
        ctx.studentPage.waitForTimeout(1_500);
    }

    @Then("the student cannot add a Lab activity")
    public void theStudentCannotAddALabActivity() {
        Page p = ctx.studentPage;
        assertNotNull(p, "student page was not opened");
        int chooser = p.locator("#section-0 [data-action='open-chooser'], "
                + "li#section-0 .activity-add, #section-0 a.section-modchooser-link, "
                + "button:has-text('Add an activity or resource'), "
                + "button:has-text('Add an activity')").count();
        assertTrue(chooser == 0, "student sees Add an activity on the course page");
        p.navigate(Settings.BASE_URL + "/course/modedit.php?add=cloudlabs&type=&course="
                        + Settings.COURSE_ID + "&section=0&return=0&sr=0",
                new Page.NavigateOptions()
                        .setWaitUntil(com.microsoft.playwright.options.WaitUntilState.DOMCONTENTLOADED)
                        .setTimeout(45_000));
        p.waitForTimeout(1_500);
        String body = p.locator("body").innerText().toLowerCase();
        boolean formOpen = p.locator("#id_submitbutton").count() > 0 && p.locator("#id_name").count() > 0;
        assertFalse(formOpen,
                "student opened the Lab add form (capability should deny add=cloudlabs)");
        assertTrue(!p.url().contains("modedit.php")
                        || body.contains("sorry")
                        || body.contains("not allowed")
                        || body.contains("permission"),
                "student was not blocked from adding a Lab (url=" + p.url() + ")");
        try {
            if (student != null) ApiClient.deleteUser(student.id);
        } catch (RuntimeException ignored) {}
    }
}
