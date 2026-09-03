package com.nuvepro.moodle.steps;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.nuvepro.moodle.config.Settings;
import com.nuvepro.moodle.helpers.ApiClient;
import com.nuvepro.moodle.helpers.AssessmentCreateSpec;
import com.nuvepro.moodle.helpers.Auth;
import com.nuvepro.moodle.helpers.SandboxActivityRegistry;
import com.nuvepro.moodle.helpers.SandboxCourse;
import com.nuvepro.moodle.helpers.SandboxTestData;
import com.nuvepro.moodle.helpers.SandboxUserRegistry;
import com.nuvepro.moodle.pages.ChallengeActivityForm;
import com.nuvepro.moodle.pages.CourseView;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import org.testng.SkipException;

import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;

/**
 * Sandbox activity creation (labtype=playground). Activities and the suite course
 * are kept — no auto-delete. Course cleanup is {@code @sandbox-cleanup} only.
 * {@code @SB-create-*} / {@code @SB-user-*} / {@code @SB-manage-*} reuse a registered
 * activity per dataset (unless {@code @fresh_activity}).
 */
public class SandboxCreateSteps {
    private static final String H_SECTION = "#np-ap-pl-sectionId, #np-ap-cl-sectionId";

    private final TestContext ctx;
    private final ChallengeActivityForm form;
    private AssessmentCreateSpec last;
    private String createdCmid;
    private ApiClient.SeededUser student;

    public SandboxCreateSteps(TestContext ctx) {
        this.ctx = ctx;
        this.form = new ChallengeActivityForm(ctx.page);
    }

    @When("admin creates a sandbox activity using {string}")
    public void adminCreatesASandboxActivityUsing(String dataset) {
        String courseId = SandboxCourse.ensure(ctx.page);
        ctx.newActivityDataset = dataset;
        boolean reuseSuite = sandboxSuite() && !ctx.hasTag("@fresh_activity");

        synchronized (SandboxCreateSteps.class) {
            if (reuseSuite && reuseOnSuiteCourse(dataset, courseId)) return;
        }
        // Parallel: actions/user must not grab the old COURSE_ID activity. Wait (lock released)
        // until create.feature has registered an activity on this suite course.
        if (reuseSuite && Boolean.getBoolean("sandbox.parallel")) {
            System.out.println("[SB-create] waiting for suite-course activity on course=" + courseId
                    + " dataset=" + dataset);
            SandboxActivityRegistry.waitForAny(courseId, SandboxTestData.reuseCandidates(dataset), 240_000);
        }
        synchronized (SandboxCreateSteps.class) {
            if (reuseSuite && reuseOnSuiteCourse(dataset, courseId)) return;

            last = SandboxTestData.create(dataset);
            ctx.lastCreateSpec = last;
            form.openAddFormFromCourse(courseId);
            form.fill(last);
            form.saveAndDisplay();
            createdCmid = form.activityCmidFromUrl();
            ctx.newActivityCmid = createdCmid;
            ctx.newActivityName = last.name;
            captureSectionIdFromLanding();
            ensureShownOnCourse();
            if (reuseSuite || sandboxSuite()) {
                SandboxActivityRegistry.put(dataset, last.name, createdCmid, ctx.sectionId, courseId);
            }
            System.out.println("[SB-create] kept sandbox activity cmid=" + createdCmid + " name=" + last.name
                    + " dataset=" + dataset + " course=" + courseId);
        }
    }

    private boolean sandboxSuite() {
        return ctx.hasAnyTagStartingWith("@SB-create-")
                || ctx.hasAnyTagStartingWith("@SB-user-")
                || ctx.hasAnyTagStartingWith("@SB-manage-")
                || ctx.hasAnyTagStartingWith("@LT-");
    }

    /** Try preferred registry keys that belong to the suite course (never the old COURSE_ID). */
    private boolean reuseOnSuiteCourse(String requestedDataset, String courseId) {
        for (String candidate : SandboxTestData.reuseCandidates(requestedDataset)) {
            if (reuseRegisteredActivity(candidate, requestedDataset, courseId)) return true;
        }
        return false;
    }

    /**
     * @param registryKey key to look up in the registry (may be a create-suite activity)
     * @param requestedDataset original scenario dataset (kept for logging; spec loaded from registryKey)
     */
    private boolean reuseRegisteredActivity(String registryKey, String requestedDataset, String courseId) {
        SandboxActivityRegistry.Entry entry = SandboxActivityRegistry.get(registryKey, courseId);
        if (entry == null || entry.cmid() == null || entry.cmid().isBlank()) return false;
        createdCmid = entry.cmid();
        ctx.newActivityCmid = entry.cmid();
        ctx.newActivityName = entry.name();
        ctx.sectionId = entry.sectionId();
        // Spec must match the activity we actually open (create-yes vs user-details markers).
        last = SandboxTestData.create(registryKey);
        ctx.lastCreateSpec = last;
        ctx.newActivityDataset = registryKey;
        // Already-created activities are left as-is — do NOT toggle "Show on course page".
        ctx.page.navigate(Settings.BASE_URL + "/mod/cloudlabs/view.php?id=" + entry.cmid(),
                new Page.NavigateOptions()
                        .setWaitUntil(com.microsoft.playwright.options.WaitUntilState.DOMCONTENTLOADED)
                        .setTimeout(45_000));
        syncNameFromLanding(registryKey, entry.cmid(), entry.sectionId());
        // Also alias under the requested key so later scenarios find it quickly.
        if (!registryKey.equals(requestedDataset)) {
            SandboxActivityRegistry.put(requestedDataset, ctx.newActivityName, entry.cmid(),
                    entry.sectionId(), courseId);
        }
        return true;
    }

    private void syncNameFromLanding(String dataset, String cmid, String sectionId) {
        // Prefer the real Moodle activity h1 — NOT .page-header-headings (includes "LAB" badge text).
        Locator title = ctx.page.locator(".page-header-headings h1, h1.h2.mb-0, h1.h2").first();
        try {
            title.waitFor(new com.microsoft.playwright.Locator.WaitForOptions().setTimeout(15_000));
            String live = SandboxActivityRegistry.sanitizeActivityName(title.innerText());
            if (!live.isBlank()) {
                ctx.newActivityName = live;
                SandboxActivityRegistry.put(dataset, live, cmid, sectionId, SandboxCourse.idOrEmpty());
            }
        } catch (RuntimeException ignored) {}
    }

    @When("admin edits the sandbox activity using {string}")
    public void adminEditsTheSandboxActivityUsing(String dataset) {
        assertNotNull(createdCmid != null ? createdCmid : ctx.newActivityCmid,
                "no sandbox cmid to edit — create first");
        if (createdCmid == null) createdCmid = ctx.newActivityCmid;
        last = SandboxTestData.create(dataset);
        ctx.lastCreateSpec = last;
        ctx.newActivityDataset = dataset;
        form.openEditForm(createdCmid, "playground");
        form.fill(last);
        form.saveAndDisplay();
        ctx.newActivityName = last.name;
        ensureShownOnCourse();
        // Keep registry in sync when edit changes the activity name (create_edit → edited).
        String registryKey = ctx.hasTag("@SB-create-11") ? "sandbox_create_edit" : dataset;
        if (sandboxSuite()) {
            SandboxActivityRegistry.put(registryKey, last.name, createdCmid, ctx.sectionId,
                    SandboxCourse.idOrEmpty());
        }
        System.out.println("[SB-create] edited sandbox activity cmid=" + createdCmid + " name=" + last.name
                + " dataset=" + dataset);
    }

    @Then("the sandbox activity is created and visible to admin")
    public void theSandboxActivityIsCreatedAndVisibleToAdmin() {
        assertNotNull(last, "no sandbox create dataset was filled");
        assertTrue(form.leftTheForm(), "still on the activity form after Save and display (url=" + ctx.page.url() + ")");
        assertNotNull(createdCmid, "created sandbox cmid was not in the landing URL: " + ctx.page.url());
        assertFalse(createdCmid.isBlank() || "-1".equals(createdCmid), "create did not produce an activity id");
        String expectName = ctx.newActivityName != null ? ctx.newActivityName : last.name;
        assertTrue(form.landingShows(expectName),
                "created sandbox name not shown on Save and display: " + expectName);

        new CourseView(ctx.page).open(SandboxCourse.id());
        String courseBody = ctx.page.locator("#region-main").count() > 0
                ? ctx.page.locator("#region-main").innerText() : ctx.page.locator("body").innerText();
        assertTrue(courseBody.contains(expectName),
                "created sandbox is not visible on the course page for admin: " + expectName);

        ctx.page.navigate(Settings.BASE_URL + "/mod/cloudlabs/view.php?id=" + createdCmid,
                new Page.NavigateOptions()
                        .setWaitUntil(com.microsoft.playwright.options.WaitUntilState.DOMCONTENTLOADED)
                        .setTimeout(45_000));
        assertTrue(form.landingShows(expectName),
                "created sandbox landing does not show activity name for admin: " + expectName);
    }

    @Then("the sandbox saved settings match {string}")
    public void theSandboxSavedSettingsMatch(String dataset) {
        assertNotNull(last, "no sandbox create dataset was filled (expected " + dataset + ")");
        assertNotNull(createdCmid, "no created sandbox cmid to re-open");
        form.openEditForm(createdCmid, "playground");
        form.assertSavedAs(last);
    }

    @When("admin tries to create a sandbox activity using {string}")
    public void adminTriesToCreateASandboxActivityUsing(String dataset) {
        String courseId = SandboxCourse.ensure(ctx.page);
        last = SandboxTestData.create(dataset);
        ctx.lastCreateSpec = last;
        ctx.newActivityDataset = dataset;
        form.openAddFormFromCourse(courseId);
        form.fill(last);
        form.saveAndDisplay();
    }

    @Then("admin sees the sandbox Activity Name for Admins on the edit form")
    public void adminSeesTheSandboxActivityNameForAdminsOnTheEditForm() {
        assertNotNull(last, "no sandbox create dataset");
        assertNotNull(last.adminName, "dataset has no admin name");
        assertFalse(last.adminName.isBlank(), "dataset admin name is blank");
        form.openEditForm(createdCmid, "playground");
        String actual = form.readActivityNameForAdmins();
        assertTrue(actual != null && actual.contains(last.adminName),
                "Activity Name for Admins on edit form expected '" + last.adminName + "' but was '" + actual + "'");
    }

    @Then("a student sees the sandbox activity name on the landing not the admin name")
    public void aStudentSeesTheSandboxActivityNameOnTheLandingNotTheAdminName() {
        assertNotNull(last, "no sandbox create dataset");
        assertNotNull(createdCmid, "no sandbox cmid");
        if (Settings.WS_TOKEN.isEmpty()) throw new SkipException("MOODLE_WS_TOKEN not set");

        student = ApiClient.createUser(System.currentTimeMillis());
        ApiClient.enrolUser(student.id, SandboxCourse.idLong(), 5);
        SandboxUserRegistry.put(SandboxCourse.idLong(), student);
        ctx.studentContext = ctx.browser.newContext(new Browser.NewContextOptions()
                .setBaseURL(Settings.BASE_URL)
                .setIgnoreHTTPSErrors(true));
        ctx.studentPage = ctx.studentContext.newPage();
        ctx.studentPage.setDefaultTimeout(Settings.DEFAULT_TIMEOUT_MS);
        Auth.uiLogin(ctx.studentPage, student.username, student.password);
        ctx.studentPage.navigate(Settings.BASE_URL + "/mod/cloudlabs/view.php?id=" + createdCmid,
                new Page.NavigateOptions()
                        .setWaitUntil(com.microsoft.playwright.options.WaitUntilState.DOMCONTENTLOADED)
                        .setTimeout(45_000));
        ctx.studentPage.waitForTimeout(1_500);

        String body = ctx.studentPage.locator("body").innerText();
        String expectName = ctx.newActivityName != null ? ctx.newActivityName : last.name;
        assertTrue(body.contains(expectName),
                "student landing does not show activity name: " + expectName);
        if (last.adminName != null && !last.adminName.isBlank() && !last.adminName.equals(expectName)) {
            assertFalse(body.contains(last.adminName),
                    "student landing shows Activity Name for Admins (should show activity name only): "
                            + last.adminName);
        }
        // Keep the student — course delete (@sandbox-cleanup) is the teardown.
    }

    private void captureSectionIdFromLanding() {
        var sec = ctx.page.locator(H_SECTION);
        if (sec.count() > 0) ctx.sectionId = sec.first().inputValue();
    }

    private void ensureShownOnCourse() {
        if (ctx.newActivityCmid == null || ctx.newActivityCmid.isBlank()) return;
        new CourseView(ctx.page).showActivityOnCoursePage(SandboxCourse.id(),
                Integer.parseInt(ctx.newActivityCmid), ctx.newActivityName);
    }
}
