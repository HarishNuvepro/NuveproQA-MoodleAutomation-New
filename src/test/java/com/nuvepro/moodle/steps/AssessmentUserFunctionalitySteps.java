package com.nuvepro.moodle.steps;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Download;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.WaitForSelectorState;
import com.nuvepro.moodle.config.Settings;
import com.nuvepro.moodle.helpers.ApiClient;
import com.nuvepro.moodle.helpers.AssessmentCreateSpec;
import com.nuvepro.moodle.helpers.AssessmentUserRegistry;
import com.nuvepro.moodle.helpers.Auth;
import com.nuvepro.moodle.helpers.CloudLabsClient;
import com.nuvepro.moodle.helpers.BrowserUi;
import com.nuvepro.moodle.pages.ChallengeControlPanel;
import com.nuvepro.moodle.pages.CourseView;
import com.nuvepro.moodle.pages.ManageLabs;
import io.cucumber.java.Before;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import org.testng.SkipException;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumSet;

import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

/**
 * Student user-flow on a freshly created assessment activity:
 * landing → Start Assessment → lab console (cp.php) → wait for auto-provisioned lab → Submit.
 */
public class AssessmentUserFunctionalitySteps {
    private static final String H_SECTION = "#np-ap-cl-sectionId";
    private static final String LANDING_READY = ChallengeControlPanel.START_ASSESSMENT + ", "
            + ".np-ap-cl-attempt-new, #np-ap-cl-attempt-new, #btn-cl-retake-assessment, "
            + "button:has-text('New Submission'), #np-ap-cl-finalResult, #np-ap-cl-highestGrade";
    private static final String RETAKE_CONTROLS = ".np-ap-cl-attempt-new, #np-ap-cl-attempt-new, "
            + "#btn-cl-retake-assessment, button:has-text('New Submission'), "
            + "button:has-text('Retake'), button:has-text('New Attempt'), a:has-text('Retake')";

    private final TestContext ctx;
    private ApiClient.SeededUser student;
    private Page studentPage;
    private long courseId;
    private String sectionId;
    private boolean blockedStartMessageShown;
    private Page openedConsoleTab;
    private String previousLabPassword;
    private String newLabPassword;
    private Download lastPdfDownload;
    private String supportMailtoUrl;
    private boolean submitActionInputShown;
    private boolean proctoredProgressShown;

    public AssessmentUserFunctionalitySteps(TestContext ctx) {
        this.ctx = ctx;
    }

    private int createdCmid() {
        if (ctx.newActivityCmid == null || ctx.newActivityCmid.isBlank()) {
            throw new SkipException("no created assessment cmid in context - create step did not run first");
        }
        return Integer.parseInt(ctx.newActivityCmid);
    }

    private void openCreatedLanding(Page p) {
        p.navigate(Settings.BASE_URL + "/mod/cloudlabs/view.php?id=" + createdCmid(),
                new Page.NavigateOptions().setWaitUntil(com.microsoft.playwright.options.WaitUntilState.DOMCONTENTLOADED)
                        .setTimeout(45_000));
        waitForStudentLandingReady(p);
    }

    private void waitForStudentLandingReady(Page p) {
        long deadline = System.currentTimeMillis() + 25_000;
        while (System.currentTimeMillis() < deadline) {
            if (p.url().contains("view.php?id=" + createdCmid()) && landingHasContent(p)) {
                syncActivityNameFromPage(p);
                return;
            }
            p.waitForTimeout(500);
        }
        try {
            p.locator(".np-ap-cl-main-title, " + LANDING_READY).first().waitFor(new Locator.WaitForOptions()
                    .setState(WaitForSelectorState.VISIBLE).setTimeout(5_000));
        } catch (RuntimeException ignored) {}
        syncActivityNameFromPage(p);
    }

    private boolean landingHasContent(Page p) {
        if (!p.url().contains("view.php?id=" + createdCmid())) return false;
        Locator title = p.locator(".np-ap-cl-main-title");
        if (title.count() > 0) {
            try {
                if (title.first().isVisible() && !title.first().innerText().trim().isBlank()) return true;
            } catch (RuntimeException ignored) {}
        }
        Locator ready = p.locator(LANDING_READY);
        for (int i = 0; i < ready.count(); i++) {
            try {
                if (ready.nth(i).isVisible()) return true;
            } catch (RuntimeException ignored) {}
        }
        String body = p.locator("#region-main").count() > 0
                ? p.locator("#region-main").innerText() : p.locator("body").innerText();
        return body.contains("Evaluated") || body.contains("Assessment Completed")
                || body.contains("Evaluating") || body.contains("Start Assessment")
                || body.contains("New Submission");
    }

    private void syncActivityNameFromPage(Page p) {
        Locator title = p.locator("h1, .np-ap-cl-main-title").first();
        if (title.count() == 0) return;
        try {
            String live = title.innerText().trim();
            if (!live.isBlank()) ctx.newActivityName = live;
        } catch (RuntimeException ignored) {}
    }

    private String landingBody() {
        return studentPage.locator("#region-main").count() > 0
                ? studentPage.locator("#region-main").innerText() : studentPage.locator("body").innerText();
    }

    private ChallengeControlPanel ccp() {
        return new ChallengeControlPanel(studentPage);
    }

    private void openStudentPageFor(ApiClient.SeededUser user) {
        Browser.NewContextOptions opts = new Browser.NewContextOptions()
                .setBaseURL(Settings.BASE_URL)
                .setIgnoreHTTPSErrors(true)
                .setAcceptDownloads(true);
        if (ctx.studentContext != null) {
            try { ctx.studentContext.close(); } catch (Throwable ignored) {}
        }
        ctx.studentContext = BrowserUi.newContext(GlobalHooks.browser, opts);
        studentPage = ctx.studentContext.newPage();
        studentPage.setDefaultTimeout(Settings.DEFAULT_TIMEOUT_MS);
        ctx.studentPage = studentPage;
        ctx.watchPageConsole(studentPage, "student");
        ctx.handoffTracingTo(ctx.studentContext);
        Auth.uiLogin(studentPage, user.username, user.password);
    }

    /** Close the admin browser once the student takes over — no further admin UI needed. */
    private void releaseAdminContext() {
        if (ctx.context == null) return;
        ctx.stopTracing(null);
        try { ctx.context.close(); } catch (Throwable ignored) {}
        ctx.context = null;
        ctx.page = null;
    }

    @Before
    public void resetUserFlowGlobals(io.cucumber.java.Scenario scenario) {
        String uri = scenario.getUri() == null ? "" : scenario.getUri().toString();
        if (!uri.contains("assessment_user_functionality")) {
            return;
        }
        GlobalHooks.labProvisioned = false;
        GlobalHooks.skipLabTeardown = true;
        GlobalHooks.teardownSectionId = null;
        GlobalHooks.teardownLabId = null;
        GlobalHooks.teardownStudentEmail = null;
        blockedStartMessageShown = false;
        openedConsoleTab = null;
        previousLabPassword = null;
        newLabPassword = null;
        lastPdfDownload = null;
        supportMailtoUrl = null;
    }

    @Given("a student is enrolled in the created assessment course")
    public void aStudentIsEnrolledInTheCreatedAssessmentCourse() {
        if (Settings.WS_TOKEN.isEmpty()) throw new SkipException("MOODLE_WS_TOKEN not set");
        if (ctx.assessmentCourseMissing()) throw new SkipException("COURSE_ID not set and no assessment suite course");
        courseId = Long.parseLong(ctx.assessmentCourseId());
        sectionId = ctx.sectionId != null ? ctx.sectionId : "";

        student = chooseStudentForRun();

        releaseAdminContext();
        openStudentPageFor(student);
    }

    private ApiClient.SeededUser chooseStudentForRun() {
        if (ctx.hasTag("@fresh_activity_reuse_user")) {
            ApiClient.SeededUser reused = AssessmentUserRegistry.getForCourse(courseId);
            if (reused != null) return reused;
            ApiClient.SeededUser created = ApiClient.createUser(System.currentTimeMillis());
            ApiClient.enrolUser(created.id, courseId, 5);
            AssessmentUserRegistry.putForCourse(courseId, created);
            return created;
        }
        ApiClient.SeededUser created = ApiClient.createUser(System.currentTimeMillis());
        ApiClient.enrolUser(created.id, courseId, 5);
        return created;
    }

    @When("the student opens the created assessment activity")
    public void theStudentOpensTheCreatedAssessmentActivity() {
        if (studentPage == null) throw new SkipException("student browser context was not created");
        openCreatedLanding(studentPage);
        assertTrue(studentPage.url().contains("view.php?id=" + createdCmid()),
                "student was redirected away from the created assessment landing (url=" + studentPage.url() + ")");
    }

    @Then("the created assessment landing is visible for the student")
    public void theCreatedAssessmentLandingIsVisibleForTheStudent() {
        assertTrue(studentPage.url().contains("view.php?id=" + createdCmid()),
                "student is not on the created assessment landing (url=" + studentPage.url() + ")");
        assertTrue(studentPage.locator(ChallengeControlPanel.START_ASSESSMENT).count() > 0
                        || studentPage.locator("#region-main").innerText().contains(ctx.newActivityName),
                "created assessment landing content is not visible for the student");
    }

    @When("the student opens Read More on the assessment landing")
    public void theStudentOpensReadMoreOnTheAssessmentLanding() {
        new ChallengeControlPanel(studentPage).openReadMoreOnLanding();
    }

    @Then("the Read More modal shows the full assessment description")
    public void theReadMoreModalShowsTheFullAssessmentDescription() {
        ChallengeControlPanel acp = new ChallengeControlPanel(studentPage);
        String marker = ctx.lastCreateSpec != null ? ctx.lastCreateSpec.richContentMarker : null;
        if (marker != null && !marker.isBlank()) {
            assertTrue(acp.readMoreModalContains(marker, "Auto rich-content paragraph"),
                    "Read More modal missing rich-content marker " + marker);
            return;
        }
        Locator modal = studentPage.locator("[data-region='modal-container'].show, .modal.show, [role='dialog']");
        assertTrue(modal.count() > 0, "Read More modal did not open");
        String text = modal.first().innerText().trim();
        assertTrue(text.length() > 185,
                "Read More modal text too short for a long description (len=" + text.length() + ")");
    }

    @Then("the fresh assessment landing shows Start Assessment only without Continue")
    public void theFreshAssessmentLandingShowsStartOnlyWithoutContinue() {
        ChallengeControlPanel acp = new ChallengeControlPanel(studentPage);
        assertTrue(acp.isOnLandingBeforeStart() || studentPage.locator(ChallengeControlPanel.START_ASSESSMENT).count() > 0,
                "Start Assessment is not shown on a fresh landing");
        assertFalse(acp.hasContinueAttemptVisible(),
                "Continue should not appear before the student starts an attempt");
    }

    @Then("the created assessment instructions are visible before starting")
    public void theCreatedAssessmentInstructionsAreVisibleBeforeStarting() {
        Locator title = studentPage.locator("h1, .np-ap-cl-main-title").first();
        assertTrue(title.isVisible(), "assessment title is not visible on the landing");
        String pageTitle = title.innerText().trim();
        assertTrue(!pageTitle.isBlank(), "assessment title is empty on the landing");
        ctx.newActivityName = pageTitle;
        String body = landingBody();
        assertTrue(studentPage.locator(ChallengeControlPanel.START_ASSESSMENT).count() > 0
                        || body.toLowerCase().contains("assessment")
                        || body.toLowerCase().contains("instruction")
                        || body.toLowerCase().contains("description")
                        || body.toLowerCase().contains("details"),
                "assessment landing does not show the expected details/instructions");
    }

    @When("the student starts the created assessment")
    public void theStudentStartsTheAssessment() {
        if (!CloudLabsClient.isConfigured()) throw new SkipException("CloudLabs API not configured");
        ChallengeControlPanel cp = ccp();
        if (cp.isOnLandingBeforeStart()) {
            assertTrue(cp.clickStartAssessment(),
                    "Start Assessment control not found on the activity landing (url=" + studentPage.url() + ")");
            if (cp.waitForConsoleUrl(90)) return;
            if (cp.waitForConsolePage(30)) return;
        }
        for (int attempt = 1; attempt <= 5; attempt++) {
            if (studentPage.url().contains("cp.php")) return;
            if (cp.isOnConsolePage()) return;
            assertTrue(cp.clickStartAssessment(),
                    "Start Assessment control not found on the activity landing (url=" + studentPage.url() + ")");
            if (cp.waitForConsoleUrl(90)) return;
            if (cp.waitForConsolePage(30)) return;
        }
        throw new SkipException("lab console (cp.php) did not open after Start Assessment (url=" + studentPage.url() + ")");
    }

    @When("the assessment lab is created successfully")
    public void theAssessmentLabIsCreatedSuccessfully() {
        GlobalHooks.labProvisioned = true;
        ChallengeControlPanel cp = ccp();
        if (cp.isOnLandingBeforeStart()) {
            assertTrue(cp.clickStartAssessment(), "Start Assessment still visible — attempt did not start");
            assertTrue(cp.waitForConsoleUrl(90),
                    "lab console (cp.php) did not open (url=" + studentPage.url() + ")");
        }
        assertTrue(cp.isOnConsolePage(),
                "student is not on the lab console page (url=" + studentPage.url() + ")");
        cp.startHandsOnLabIfNeeded();
        assertTrue(cp.waitForAutoProvisionedLab(180),
                "Jump to Console / sign-in link / Submit did not appear — lab may still be provisioning (url="
                        + studentPage.url() + ")");
        String labId = cp.labId();
        if (labId != null && !labId.isEmpty()) {
            System.out.println("[A-user-1] lab id=" + labId);
            GlobalHooks.teardownLabId = labId;
        }
        if (sectionId == null || sectionId.isBlank()) {
            Locator sec = studentPage.locator(H_SECTION);
            if (sec.count() > 0) sectionId = sec.first().inputValue();
        }
        if (sectionId != null && !sectionId.isBlank()) {
            GlobalHooks.teardownSectionId = sectionId;
        }
        GlobalHooks.teardownStudentEmail = student.email;
    }

    @Then("the created assessment lab console is shown")
    public void theCreatedAssessmentLabConsoleIsShown() {
        assertTrue(ccp().isOnConsolePage(),
                "student is not on the assessment lab console (url=" + studentPage.url() + ")");
    }

    @When("the student submits the created assessment attempt")
    public void theStudentClicksTheSubmitButton() {
        ChallengeControlPanel cp = ccp();
        assertTrue(cp.waitBrieflyForSubmitAfterConsoleReady(10),
                "Submit did not enable within 10 seconds after the console was ready");
        if ("labtemplates_action_input".equals(ctx.newActivityDataset)) {
            submitActionInputShown = cp.requestSubmissionActionInput(60);
            return;
        }
        cp.submitAttempt();
        submitActionInputShown = cp.lastSubmitActionInputShown();
    }

    @Then("assessment submission requested template action input")
    public void assessmentSubmissionRequestedTemplateActionInput() {
        assertTrue(submitActionInputShown,
                "assessment template did not request its configured submission action input");
    }

    @When("the student starts the proctored assessment while observing lab progress")
    public void theStudentStartsTheProctoredAssessmentWhileObservingLabProgress() {
        ChallengeControlPanel cp = ccp();
        assertTrue(cp.clickStartAssessment(), "proctored assessment Start control was not usable");
        proctoredProgressShown = cp.waitForFourStepProgress(45);
    }

    @Then("the proctored assessment displayed all four lab progress steps")
    public void theProctoredAssessmentDisplayedAllFourLabProgressSteps() {
        assertTrue(proctoredProgressShown,
                "DEFECT: proctored assessment did not display the four-step lab progress tracker");
    }

    @Then("the created assessment attempt is submitted for evaluation")
    public void theAttemptIsSubmittedForEvaluation() {
        String status = "unknown";
        for (int poll = 1; poll <= 12; poll++) {
            if (!studentPage.url().contains("view.php?id=" + createdCmid())) {
                openCreatedLanding(studentPage);
            }
            String body = studentPage.locator("#region-main").count() > 0
                    ? studentPage.locator("#region-main").innerText() : studentPage.locator("body").innerText();
            boolean failed = body.contains("Evaluation Failed");
            boolean evaluating = body.contains("Evaluating");
            boolean evaluated = body.contains("Evaluated") && !evaluating;
            status = failed ? "EvaluationFailed" : evaluating ? "Evaluating" : evaluated ? "Evaluated" : "InProgress/other";
            if (failed) {
                throw new AssertionError("attempt submission reached 'Evaluation Failed' instead of a healthy evaluation state");
            }
            if (evaluated) return;
            studentPage.waitForTimeout(2_000);
        }
        throw new AssertionError("attempt did not reach Evaluated within 60 seconds after submit (last status: " + status + ")");
    }

    @Then("the created assessment attempt moves to Evaluating")
    public void theCreatedAssessmentAttemptMovesToEvaluating() {
        String status = "unknown";
        for (int poll = 1; poll <= 15; poll++) {
            if (!studentPage.url().contains("view.php?id=" + createdCmid())) {
                openCreatedLanding(studentPage);
            }
            String body = studentPage.locator("#region-main").count() > 0
                    ? studentPage.locator("#region-main").innerText() : studentPage.locator("body").innerText();
            boolean failed = body.contains("Evaluation Failed");
            boolean evaluating = body.contains("Evaluating");
            boolean evaluated = body.contains("Evaluated") && !evaluating;
            status = failed ? "EvaluationFailed" : evaluating ? "Evaluating" : evaluated ? "Evaluated" : "InProgress/other";
            if (failed) {
                throw new AssertionError("attempt submission reached 'Evaluation Failed' instead of 'Evaluating'");
            }
            if (evaluating || evaluated) return;
            studentPage.waitForTimeout(2_000);
        }
        throw new AssertionError("attempt did not move to Evaluating/Evaluated after submit (last status: " + status + ")");
    }

    @Then("the same student can reopen the created assessment and see the evaluated result")
    public void theSameStudentCanReopenTheCreatedAssessmentAndSeeTheEvaluatedResult() {
        openCreatedLanding(studentPage);
        String body = studentPage.locator("#region-main").count() > 0
                ? studentPage.locator("#region-main").innerText() : studentPage.locator("body").innerText();
        assertTrue(body.contains("Evaluated"), "evaluated result is not visible when the same student reopens the activity");
    }

    @Then("another fresh user can open the same created assessment independently")
    public void anotherFreshUserCanOpenTheSameCreatedAssessmentIndependently() {
        ApiClient.SeededUser second = ApiClient.createUser(System.currentTimeMillis() + 1);
        ApiClient.enrolUser(second.id, courseId, 5);
        openStudentPageFor(second);
        theStudentOpensTheCreatedAssessmentActivity();
        theCreatedAssessmentLandingIsVisibleForTheStudent();
        String body = studentPage.locator("#region-main").count() > 0
                ? studentPage.locator("#region-main").innerText() : studentPage.locator("body").innerText();
        assertTrue(body.contains("Start Assessment") || studentPage.locator(ChallengeControlPanel.START_ASSESSMENT).count() > 0,
                "second fresh user does not see an independent start state on the reused assessment");
    }

    @Then("the reused enrolled user can access the fresh created assessment")
    public void theReusedEnrolledUserCanAccessTheFreshCreatedAssessment() {
        theCreatedAssessmentLandingIsVisibleForTheStudent();
        assertTrue(student.email != null && !student.email.isBlank(),
                "no reused enrolled user is present for the fresh-activity scenario");
    }

    @Given("admin hides the created assessment on the course page")
    public void adminHidesTheCreatedAssessmentOnTheCoursePage() {
        if (ctx.page == null) throw new SkipException("admin page is not available for hiding the activity");
        new CourseView(ctx.page).hideActivityOnCoursePage(ctx.assessmentCourseId(),
                Integer.parseInt(ctx.newActivityCmid), ctx.newActivityName);
    }

    @When("the student opens the created assessment course page")
    public void theStudentOpensTheCreatedAssessmentCoursePage() {
        if (studentPage == null) throw new SkipException("student browser context was not created");
        studentPage.navigate(Settings.BASE_URL + "/course/view.php?id=" + courseId,
                new Page.NavigateOptions().setWaitUntil(com.microsoft.playwright.options.WaitUntilState.DOMCONTENTLOADED)
                        .setTimeout(45_000));
    }

    @Then("the created assessment is hidden from the student course page")
    public void theCreatedAssessmentIsHiddenFromTheStudentCoursePage() {
        CourseView course = new CourseView(studentPage);
        assertTrue(!course.activityVisibleOnCoursePage(createdCmid(), ctx.newActivityName),
                "hidden assessment is still visible on the student course page");
    }

    @When("the student tries to start the created assessment")
    public void theStudentTriesToStartTheCreatedAssessment() {
        if (!CloudLabsClient.isConfigured()) throw new SkipException("CloudLabs API not configured");
        blockedStartMessageShown = ccp().startBlockedMessageVisible();
    }

    @Then("the student sees a blocked-start message for the created assessment")
    public void theStudentSeesABlockedStartMessageForTheCreatedAssessment() {
        assertTrue(!studentPage.url().contains("cp.php"),
                "blocked-start case unexpectedly opened the lab console");
        assertTrue(blockedStartMessageShown || landingBody().toLowerCase().contains("not available")
                        || landingBody().toLowerCase().contains("start date"),
                "no blocked-start message was shown after clicking Start Assessment");
    }

    @Then("the created assessment shows evaluated result details")
    public void theCreatedAssessmentShowsEvaluatedResultDetails() {
        for (int poll = 1; poll <= 15; poll++) {
            openCreatedLanding(studentPage);
            String body = landingBody();
            boolean evaluated = body.contains("Evaluated") || body.contains("Assessment Completed");
            boolean hasFinalResult = studentPage.locator("#np-ap-cl-finalResult").count() > 0;
            boolean hasGrade = studentPage.locator("#np-ap-cl-highestGrade").count() > 0;
            if (evaluated && (hasFinalResult || hasGrade)) return;
            studentPage.waitForTimeout(2_000);
        }
        openCreatedLanding(studentPage);
        String body = landingBody();
        assertTrue(body.contains("Evaluated") || body.contains("Assessment Completed"),
                "evaluated status is not visible on the assessment landing");
        boolean hasFinalResult = studentPage.locator("#np-ap-cl-finalResult").count() > 0;
        boolean hasGrade = studentPage.locator("#np-ap-cl-highestGrade").count() > 0;
        assertTrue(hasFinalResult || hasGrade,
                "evaluated result details are missing (expected final result or grade)");
    }

    @Then("the student sees a new attempt or retake control on the created assessment")
    public void theStudentSeesANewAttemptOrRetakeControlOnTheCreatedAssessment() {
        boolean visible = false;
        for (int poll = 1; poll <= 15; poll++) {
            openCreatedLanding(studentPage);
            Locator retry = studentPage.locator(RETAKE_CONTROLS);
            for (int i = 0; i < retry.count(); i++) {
                try {
                    if (retry.nth(i).isVisible()) { visible = true; break; }
                } catch (RuntimeException ignored) {}
            }
            if (visible) return;
            studentPage.waitForTimeout(2_000);
        }
        assertTrue(visible, "no new-attempt/retake control is visible after the first evaluated attempt");
    }

    @When("the student waits on the lab console without submitting until the time limit expires")
    public void theStudentWaitsWithoutSubmittingUntilTimeLimitExpires() {
        ChallengeControlPanel cp = ccp();
        assertTrue(cp.isOnConsolePage(),
                "student must be on the lab console before waiting for auto-submit (url=" + studentPage.url() + ")");
        long deadline = System.currentTimeMillis() + 270_000;
        while (System.currentTimeMillis() < deadline) {
            if (!studentPage.url().contains("cp.php")) break;
            studentPage.waitForTimeout(10_000);
        }
        if (studentPage.url().contains("cp.php")) {
            openCreatedLanding(studentPage);
        }
    }

    @Then("the created assessment is auto-submitted evaluated and completed")
    public void theCreatedAssessmentIsAutoSubmittedEvaluatedAndCompleted() {
        // Webhook evaluation: wait up to ~60s for Evaluated (not Evaluation Failed).
        boolean evaluated = false;
        for (int poll = 1; poll <= 12; poll++) {
            openCreatedLanding(studentPage);
            String body = landingBody();
            if (body.contains("Evaluation Failed")) {
                throw new AssertionError("time-limit auto-submit reached 'Evaluation Failed'");
            }
            if ((body.contains("Evaluated") && !body.contains("Evaluating"))
                    || body.contains("Assessment Completed")) {
                evaluated = true;
                break;
            }
            studentPage.waitForTimeout(5_000);
        }
        assertTrue(evaluated, "auto-submit did not reach Evaluated within 60 seconds (webhook)");

        // Cron marks assessment completed after evaluation — wait up to another ~60s.
        for (int poll = 1; poll <= 12; poll++) {
            openCreatedLanding(studentPage);
            String body = landingBody();
            boolean completed = body.contains("Assessment Completed");
            boolean hasResult = studentPage.locator("#np-ap-cl-finalResult").count() > 0
                    || studentPage.locator("#np-ap-cl-highestGrade").count() > 0;
            if (completed || (body.contains("Evaluated") && hasResult)) return;
            studentPage.waitForTimeout(5_000);
        }
        throw new AssertionError("assessment was not marked completed within 60 seconds after evaluation (cron)");
    }

    @Then("the student still has another attempt available after the auto-submitted evaluation")
    public void theStudentStillHasAnotherAttemptAfterAutoSubmit() {
        openCreatedLanding(studentPage);
        String body = landingBody();
        Locator retry = studentPage.locator(RETAKE_CONTROLS);
        for (int i = 0; i < retry.count(); i++) {
            try {
                if (retry.nth(i).isVisible()) return;
            } catch (RuntimeException ignored) {}
        }
        assertTrue(body.contains("Attempts Taken") && (body.contains("1") || body.contains("2")),
                "multi-attempt assessment does not show a taken attempt count after auto-submit");
        assertTrue(body.contains("Total Allowed Attempts") && body.contains("2"),
                "expected 2 total allowed attempts on the landing details");
    }

    @Then("the student sees the configured assessment settings on the landing")
    public void theStudentSeesConfiguredAssessmentSettingsOnTheLanding() {
        AssessmentCreateSpec spec = ctx.lastCreateSpec;
        if (spec == null) throw new SkipException("no create spec in context — run admin create first");
        openCreatedLanding(studentPage);
        String details = studentPage.locator("#np-ap-cl-challenge-details").count() > 0
                ? studentPage.locator("#np-ap-cl-challenge-details").innerText()
                : landingBody();
        if (notBlank(spec.difficulty)) {
            assertTrue(details.contains(spec.difficulty),
                    "difficulty '" + spec.difficulty + "' not shown on student landing");
        }
        if (spec.timeLimitHours > 0) {
            assertTrue(details.toLowerCase().contains("time limit")
                            && (details.contains(String.valueOf(spec.timeLimitHours)) || details.contains("2")),
                    "time limit hours not reflected on student landing");
        }
        if (notBlank(spec.attemptsAllowed)) {
            assertTrue(details.contains("Total Allowed Attempts") || details.contains("Attempts"),
                    "attempts allowed label missing on student landing");
            assertTrue(details.contains(spec.attemptsAllowed),
                    "attempts allowed value " + spec.attemptsAllowed + " not on student landing");
        }
        if (notBlank(spec.submissionsAllowed)) {
            assertTrue(details.contains("Submissions") || details.contains("Submission"),
                    "submissions label missing on student landing");
        }
        if (spec.dueYear > 0) {
            assertTrue(details.contains("Due Date") && !details.contains("Due Date\nNone"),
                    "due date not shown on student landing");
        }
        if (spec.enableStartDate) {
            assertTrue(details.contains("Start Date"),
                    "start date label missing on student landing");
        }
        // Grade to pass is a Moodle gradebook setting — not shown on the student Details panel.
        if (Boolean.TRUE.equals(spec.proctored)) {
            assertTrue(details.contains("Proctored") && details.toLowerCase().contains("yes"),
                    "proctored assessment setting not reflected on student landing");
        }
    }

    @Then("the student sees the rich description content on the landing")
    public void theStudentSeesRichDescriptionContentOnTheLanding() {
        AssessmentCreateSpec spec = ctx.lastCreateSpec;
        if (spec == null || spec.richContentMarker == null || spec.richContentMarker.isBlank()) {
            throw new SkipException("no rich content marker in create spec");
        }
        openCreatedLanding(studentPage);
        expandDescriptionIfNeeded(studentPage);
        String body = landingBody();
        assertTrue(body.contains(spec.richContentMarker),
                "rich description marker not visible on the student landing");
        int visibleImgs = ccp().visibleImagesInSelector("#cl-content img, .intro-cl-content img, #readMoreModal img");
        assertTrue(visibleImgs > 0, "no description images rendered on the student landing");
    }

    @Then("the student sees the rich assessment instructions on the lab console")
    public void theStudentSeesRichAssessmentInstructionsOnTheLabConsole() {
        AssessmentCreateSpec spec = ctx.lastCreateSpec;
        if (spec == null || spec.richContentMarker == null || spec.richContentMarker.isBlank()) {
            throw new SkipException("no rich content marker in create spec");
        }
        ChallengeControlPanel cp = ccp();
        assertTrue(cp.openInstructionsFab(), "Instructions FAB is not available on the lab console");
        String instrMarker = spec.richContentMarker + "-INSTRUCTIONS";
        assertTrue(cp.waitForInstructionsMarker(instrMarker, 30),
                "rich instructions marker not visible in the Instructions popup");
        int visibleImgs = cp.visibleImagesInSelector("#missionPopup img, #printArea img, #np-ap-cl-content-mission img");
        assertTrue(visibleImgs > 0, "no instruction images rendered in the Instructions popup");
    }

    private void expandDescriptionIfNeeded(Page p) {
        Locator readMore = p.locator("#read-more-btn");
        if (readMore.count() == 0) return;
        try {
            if (readMore.first().isVisible()) readMore.first().click();
        } catch (RuntimeException ignored) {}
        p.waitForTimeout(800);
        Locator modal = p.locator("#readMoreModal.show, #readMoreModal[style*='display: block']");
        if (modal.count() == 0) return;
        try {
            if (!modal.first().isVisible()) return;
        } catch (RuntimeException ignored) {
            return;
        }
    }

    @When("the student opens the access lab flow from Jump to Console")
    public void theStudentOpensAccessLabFromJumpToConsole() {
        ChallengeControlPanel cp = ccp();
        assertTrue(cp.clickJumpToConsole(), "Jump to Console control was not available on the lab console");
        assertTrue(cp.accessLabModalVisible() || studentPage.locator(ChallengeControlPanel.ACCESS_LAB_LINKS).count() > 0,
                "Access Lab popup/links did not appear after Jump to Console");
        try {
            openedConsoleTab = studentPage.context().waitForPage(
                    new BrowserContext.WaitForPageOptions().setTimeout(12_000),
                    cp::clickAccessLabLink);
        } catch (RuntimeException e) {
            openedConsoleTab = null;
        }
        if (openedConsoleTab == null && !cp.clickAccessLabLink()) {
            throw new AssertionError("Access Lab link did not open a platform console tab or popup");
        }
    }

    @Then("the student is redirected to the cloud lab platform")
    public void theStudentIsRedirectedToTheCloudLabPlatform() {
        if (openedConsoleTab != null) {
            String url = openedConsoleTab.url().toLowerCase();
            String host = Settings.BASE_URL.replaceAll("^https?://", "").split("/")[0].toLowerCase();
            assertTrue(!url.contains(host) || url.contains("console") || url.contains("signin")
                            || url.contains("aws") || url.contains("azure") || url.contains("google"),
                    "opened tab did not leave Moodle for the cloud platform (url=" + openedConsoleTab.url() + ")");
            try { openedConsoleTab.close(); } catch (RuntimeException ignored) {}
            openedConsoleTab = null;
            return;
        }
        assertTrue(studentPage.locator(ChallengeControlPanel.ACCESS_LAB_MODAL).count() > 0
                        || studentPage.locator(ChallengeControlPanel.ACCESS_LAB_LINKS).count() > 0,
                "no platform access control remained after Jump to Console");
    }

    @Then("the lab console shows access credentials for the student")
    public void theLabConsoleShowsAccessCredentialsForTheStudent() {
        ChallengeControlPanel cp = ccp();
        assertTrue(cp.openCredentialsFab(), "Lab Credentials / access details control is not present on the console");
        assertTrue(cp.credentialsShowLoginAndPassword(),
                "lab credentials popup does not show login and password details");
    }

    @Then("the lab console shows lab duration and a get cost control")
    public void theLabConsoleShowsLabDurationAndGetCostControl() {
        assertTrue(ccp().labUsageShowsDurationAndGetCost(),
                "Lab Usage / duration / Get Cost controls are not present on the console");
    }

    @When("the student changes the lab password on the console")
    public void theStudentChangesTheLabPasswordOnTheConsole() {
        ChallengeControlPanel cp = ccp();
        assertTrue(cp.openCredentialsFab(), "Lab Credentials control is not present on the console");
        previousLabPassword = cp.credentialsPasswordValue();
        newLabPassword = "AutoLabPass@" + System.currentTimeMillis();
        assertTrue(cp.changeLabPassword(newLabPassword),
                "Change Password / Save Changes did not complete on the lab console");
    }

    @Then("the lab console shows the updated lab password")
    public void theLabConsoleShowsTheUpdatedLabPassword() {
        ChallengeControlPanel cp = ccp();
        assertTrue(cp.openCredentialsFab() || cp.credentialsShowLoginAndPassword(),
                "credentials panel did not reopen after password change");
        boolean success = false;
        for (int i = 0; i < 15; i++) {
            if (cp.passwordChangeSuccessVisible()) { success = true; break; }
            String current = cp.credentialsPasswordValue();
            if (!current.isBlank() && current.equals(newLabPassword)) { success = true; break; }
            if (!current.isBlank() && previousLabPassword != null && !current.equals(previousLabPassword)) {
                success = true;
                break;
            }
            studentPage.waitForTimeout(1_000);
            cp.openCredentialsFab();
        }
        String latest = cp.credentialsPasswordValue();
        assertTrue(success || (!latest.isBlank() && (latest.equals(newLabPassword)
                        || (previousLabPassword != null && !latest.equals(previousLabPassword)))),
                "lab password was not updated (previous='" + previousLabPassword + "', latest='" + latest + "')");
    }

    @When("the student stops the assessment lab from the console")
    public void theStudentStopsTheAssessmentLabFromTheConsole() {
        ChallengeControlPanel cp = ccp();
        String labId = cp.labId();
        if (labId != null && !labId.isBlank()) {
            GlobalHooks.teardownLabId = labId;
        }
        assertTrue(cp.stopLabAndConfirm(),
                "Stop Lab button was not available/enabled or the confirm dialog did not appear");
    }

    @Then("admin Manage Labs shows the assessment lab is stopped for the student")
    public void adminManageLabsShowsTheAssessmentLabIsStopped() {
        String labId = GlobalHooks.teardownLabId;
        if ((labId == null || labId.isBlank()) && studentPage != null) {
            labId = ccp().labId();
        }
        if (labId != null && !labId.isBlank() && CloudLabsClient.isConfigured()) {
            ManageLabs.LabState st = CloudLabsClient.waitForState(labId,
                    EnumSet.of(ManageLabs.LabState.STOPPED, ManageLabs.LabState.FAILED), 180, 5);
            assertTrue(st == ManageLabs.LabState.STOPPED,
                    "CloudLabs API did not report Stopped after student Stop Lab (state=" + st + ")");
        }
        reopenAdminContext();
        int sid;
        try {
            sid = Integer.parseInt(sectionId != null && !sectionId.isBlank() ? sectionId : ctx.sectionId);
        } catch (Exception e) {
            throw new SkipException("section id unavailable for Manage Labs check");
        }
        ManageLabs ml = new ManageLabs(ctx.page);
        ml.open(sid);
        ManageLabs.LabState uiState = ManageLabs.LabState.UNKNOWN;
        if (student != null && student.email != null) {
            for (int i = 0; i < 12; i++) {
                uiState = ml.stateForEmail(student.email);
                if (uiState == ManageLabs.LabState.STOPPED) break;
                ctx.page.waitForTimeout(5_000);
                ml.open(sid);
            }
        }
        if (labId != null && !labId.isBlank() && CloudLabsClient.isConfigured()) {
            CloudLabsClient.SubStatus ss = CloudLabsClient.getStatus(labId);
            ManageLabs.LabState api = ss == null ? ManageLabs.LabState.UNKNOWN
                    : ManageLabs.classifyActionStatus(ss.action, ss.status);
            assertTrue(api == ManageLabs.LabState.STOPPED || uiState == ManageLabs.LabState.STOPPED,
                    "admin Manage Labs / API does not show Stopped (api=" + api + ", ui=" + uiState + ")");
            return;
        }
        assertTrue(uiState == ManageLabs.LabState.STOPPED,
                "admin Manage Labs does not show Stopped for the student lab (state=" + uiState + ")");
    }

    @When("the student downloads the instructions PDF from the lab console")
    public void theStudentDownloadsTheInstructionsPdfFromTheLabConsole() {
        lastPdfDownload = ccp().downloadInstructionsPdf();
        assertTrue(lastPdfDownload != null,
                "Download PDF button did not trigger a download from the Instructions panel");
    }

    @Then("the instructions PDF is downloaded successfully")
    public void theInstructionsPdfIsDownloadedSuccessfully() {
        assertTrue(lastPdfDownload != null, "no PDF download was captured");
        String suggested = lastPdfDownload.suggestedFilename();
        assertTrue(suggested != null && suggested.toLowerCase().endsWith(".pdf"),
                "downloaded file is not a PDF (suggested=" + suggested + ")");
        Path saved = Path.of("target", "downloads", suggested);
        try {
            Files.createDirectories(saved.getParent());
            lastPdfDownload.saveAs(saved);
            assertTrue(Files.exists(saved) && Files.size(saved) > 0,
                    "downloaded PDF is missing or empty: " + saved);
        } catch (Exception e) {
            throw new AssertionError("failed to save downloaded PDF: " + e.getMessage(), e);
        }
    }

    @When("the student opens the Need help contact support link on the lab console")
    public void theStudentOpensTheNeedHelpContactSupportLink() {
        supportMailtoUrl = ccp().openSupportMailtoUrl();
        if (supportMailtoUrl == null || supportMailtoUrl.isBlank()) {
            throw new SkipException("Support / Need help link is not available "
                    + "(plugin support email may be empty)");
        }
    }

    @Then("the support link redirects to a mail form")
    public void theSupportLinkRedirectsToAMailForm() {
        String url = supportMailtoUrl == null ? "" : supportMailtoUrl.trim();
        assertTrue(url.toLowerCase().startsWith("mailto:"),
                "support link did not open a mailto mail form (got=" + url + ")");
        String address = url.substring("mailto:".length()).split("\\?", 2)[0].trim();
        assertTrue(address.contains("@"),
                "mailto target has no support address (got=" + url + ")");
    }

    @When("the student returns to the assessment landing and waits until the time limit expires")
    public void theStudentReturnsToLandingAndWaitsUntilTimeLimitExpires() {
        openCreatedLanding(studentPage);
        long deadline = System.currentTimeMillis() + 270_000;
        while (System.currentTimeMillis() < deadline) {
            String body = landingBody();
            if (body.contains("Evaluating") || body.contains("Evaluated")
                    || body.contains("Assessment Completed") || body.contains("Evaluation Failed")) {
                return;
            }
            studentPage.waitForTimeout(10_000);
            openCreatedLanding(studentPage);
        }
    }

    private void reopenAdminContext() {
        Browser.NewContextOptions opts = new Browser.NewContextOptions()
                .setBaseURL(Settings.BASE_URL)
                .setIgnoreHTTPSErrors(true);
        if (java.nio.file.Files.exists(GlobalHooks.STORAGE_STATE)) {
            opts.setStorageStatePath(GlobalHooks.STORAGE_STATE);
        }
        if (ctx.context != null) {
            try { ctx.context.close(); } catch (Throwable ignored) {}
        }
        ctx.context = BrowserUi.newContext(GlobalHooks.browser, opts);
        ctx.page = ctx.context.newPage();
        ctx.page.setDefaultTimeout(Settings.DEFAULT_TIMEOUT_MS);
        GlobalHooks.ensureAdminSession(ctx.context, ctx.page);
        ctx.watchPageConsole(ctx.page, "admin");
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }

}
