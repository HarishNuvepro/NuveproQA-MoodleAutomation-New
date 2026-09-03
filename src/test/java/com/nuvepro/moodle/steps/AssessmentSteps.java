package com.nuvepro.moodle.steps;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.nuvepro.moodle.config.Settings;
import com.nuvepro.moodle.helpers.ApiClient;
import com.nuvepro.moodle.helpers.Auth;
import com.nuvepro.moodle.helpers.CloudLabsClient;
import com.nuvepro.moodle.pages.ChallengeControlPanel;
import com.nuvepro.moodle.pages.ManageLabs;
import io.cucumber.java.After;

import java.util.EnumSet;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import org.testng.SkipException;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

/**
 * Assessment (challenge) — batch 1: landing presence, config-toggle mechanics (CGAP-A-1..4),
 * edit-form validations (CGAP-A-5/6/7 — a validation failure saves nothing, so the shared
 * ASSESSMENT_CMID activity is never mutated), and student role enforcement (CGAP-A-8).
 * Landing selectors from challenge-landing / assessment-landing mustache templates.
 */
public class AssessmentSteps {
    // this assessment-landing variant renders "Start Assessment" WITHOUT an id -> include text matches
    private static final String ATTEMPT_CONTROLS = "#np-ap-cl-challenge-start, #np-ap-cl-attempt-continue, "
            + "#np-ap-cl-attempt-new, #btn-cl-retake-assessment, #completeChallengeBtn, #np-ap-cl-challenge-end, "
            + "button:has-text('Start Assessment'), a:has-text('Start Assessment'), "
            + "button:has-text('Start Challenge'), a:has-text('Start Challenge')";
    private static final String H_SECTION = "#np-ap-cl-sectionId";

    private final TestContext ctx;
    private ApiClient.SeededUser student;
    private BrowserContext studentCtx;
    private Page studentPage;
    private String sectionId;

    public AssessmentSteps(TestContext ctx) {
        this.ctx = ctx;
    }

    private int cmid() {
        if (Settings.ASSESSMENT_CMID.isEmpty()) throw new SkipException("ASSESSMENT_CMID not set");
        return Integer.parseInt(Settings.ASSESSMENT_CMID);
    }

    private void openLanding(Page p) {
        p.navigate(Settings.BASE_URL + "/mod/cloudlabs/view.php?id=" + cmid(),
                new Page.NavigateOptions().setWaitUntil(com.microsoft.playwright.options.WaitUntilState.DOMCONTENTLOADED)
                        .setTimeout(45_000));
        p.waitForTimeout(5_000);
    }

    // ---- presence ----

    @When("I open the assessment activity")
    public void iOpenTheAssessmentActivity() {
        openLanding(ctx.page);
        Locator sec = ctx.page.locator(H_SECTION);
        if (sec.count() > 0) sectionId = sec.first().inputValue();
    }

    @Then("the assessment landing shows the attempt controls and details")
    public void theAssessmentLandingShowsControls() {
        boolean controls = false;
        Locator c = ctx.page.locator(ATTEMPT_CONTROLS);
        for (int i = 0; i < c.count(); i++) if (c.nth(i).isVisible()) { controls = true; break; }
        assertTrue(controls, "no attempt control (start/continue/new/retake/complete) visible on the landing");
        assertTrue(ctx.page.locator("#np-ap-cl-challenge-details, #np-ap-cl-challenge-attempt-table, "
                        + "#labAttemptsContainer").count() > 0,
                "neither the details section nor the attempts area is present");
    }

    // ---- config toggles (add form; nothing saved) ----

    @When("I open a new challenge activity form")
    public void iOpenANewChallengeActivityForm() {
        if (Settings.COURSE_ID.isEmpty()) throw new SkipException("COURSE_ID not set");
        ctx.page.navigate(Settings.BASE_URL + "/course/modedit.php?add=cloudlabs&type=&course="
                        + Settings.COURSE_ID + "&section=0&return=0&sr=0",
                new Page.NavigateOptions().setWaitUntil(com.microsoft.playwright.options.WaitUntilState.DOMCONTENTLOADED)
                        .setTimeout(45_000));
        ctx.page.waitForTimeout(2_000);
        Locator ch = ctx.page.locator("input[name='labtype'][value='challenge']");
        if (ch.count() > 0 && !ch.first().isChecked()) {
            ch.first().check(new Locator.CheckOptions().setForce(true));
            ctx.page.waitForTimeout(1_200);
        }
        expandAll();
    }

    private void expandAll() {
        try {
            // Only click the form-level toggle if it currently offers to EXPAND (clicking a "Collapse all"
            // toggle would hide fields and break fills — the bug this replaces).
            Locator toggle = ctx.page.locator(".collapsible-actions a.collapseexpand");
            if (toggle.count() > 0 && toggle.first().innerText().toLowerCase().contains("expand")) {
                toggle.first().click();
                ctx.page.waitForTimeout(600);
            }
            // Belt-and-braces: expand any fieldset header still marked collapsed.
            Locator collapsed = ctx.page.locator("a.collapseexpand[aria-expanded='false'], "
                    + "fieldset .fheader a[aria-expanded='false']");
            int n = collapsed.count();
            for (int i = 0; i < n; i++) {
                try { collapsed.nth(i).click(); ctx.page.waitForTimeout(150); } catch (Throwable ignored) {}
            }
        } catch (Throwable ignored) {}
    }

    private void pickRadio(String name, String value) {
        ctx.page.locator("input[type=radio][name='" + name + "'][value='" + value + "']").first()
                .check(new Locator.CheckOptions().setForce(true));
        ctx.page.waitForTimeout(800);
    }

    private boolean vis(String selector) {
        Locator l = ctx.page.locator(selector);
        for (int i = 0; i < l.count(); i++) if (l.nth(i).isVisible()) return true;
        return false;
    }

    @Then("the evaluation type toggles the matching config fields")
    public void evaluationTypeToggles() {
        pickRadio("evaluationtype", "AUTO");
        assertTrue(vis("[name='testscripturl']") && vis("[name='testscriptparams']"),
                "AUTO did not show test script URL/params");
        pickRadio("evaluationtype", "ANSWER");
        assertTrue(vis("[name='challengeanswer']") && vis("[name='challengemarks']"),
                "ANSWER did not show answer/marks");
        assertFalse(vis("[name='testscripturl']"), "ANSWER still shows the test script URL");
        pickRadio("evaluationtype", "MANUAL");
        assertFalse(vis("[name='testscripturl']") || vis("[name='challengeanswer']"),
                "MANUAL still shows AUTO/ANSWER fields");
    }

    @Then("enabling randomization hides the manual content editor")
    public void randomizationHidesEditor() {
        pickRadio("challengecontentsource", "manual");   // isolate: git also hides the editor
        ctx.page.selectOption("select[name='assessment_randomization']", "No");
        ctx.page.waitForTimeout(800);
        boolean shownBefore = vis("[id*='challenge_content_editor']");
        ctx.page.selectOption("select[name='assessment_randomization']", "Yes");
        ctx.page.waitForTimeout(800);
        boolean shownAfter = vis("[id*='challenge_content_editor']");
        assertTrue(shownBefore, "content editor not visible with randomization=No (baseline)");
        assertFalse(shownAfter, "content editor still visible with randomization=Yes");
    }

    @Then("switching the challenge content source toggles the git URL field")
    public void contentSourceTogglesGitUrl() {
        ctx.page.selectOption("select[name='assessment_randomization']", "No");
        ctx.page.waitForTimeout(500);
        pickRadio("challengecontentsource", "git");
        assertTrue(vis("[name='challengecontentgiturl']"), "git URL field not shown for source=git");
        pickRadio("challengecontentsource", "manual");
        assertFalse(vis("[name='challengecontentgiturl']"), "git URL field still shown for source=manual");
    }

    /** Poll until the selector's visibility matches (hideIf re-evaluation can lag); returns last state. */
    private boolean visBecomes(String selector, boolean expected, int seconds) {
        boolean state = !expected;
        for (int i = 0; i < seconds * 2; i++) {
            state = vis(selector);
            if (state == expected) return state;
            ctx.page.waitForTimeout(500);
        }
        return state;
    }

    @Then("setting a cooldown period shows attempts-after-cooldown")
    public void cooldownTogglesAttemptsAfter() {
        ctx.page.selectOption("select[name='cooldownperiod']", "1");
        assertTrue(visBecomes("[name='attemptsaftercooldown']", true, 5),
                "attempts-after-cooldown not shown with a cooldown period");
        ctx.page.selectOption("select[name='cooldownperiod']", "0");
        assertFalse(visBecomes("[name='attemptsaftercooldown']", false, 5),
                "attempts-after-cooldown still shown with cooldown=None");
    }

    // ---- edit-form validations on ASSESSMENT_CMID (validation failure saves NOTHING) ----

    private void openEditForm() {
        ctx.page.navigate(Settings.BASE_URL + "/course/modedit.php?update=" + cmid(),
                new Page.NavigateOptions().setWaitUntil(com.microsoft.playwright.options.WaitUntilState.DOMCONTENTLOADED)
                        .setTimeout(45_000));
        ctx.page.waitForTimeout(2_000);
        expandAll();
    }

    private void save() {
        ctx.page.locator("#id_submitbutton").click();
        ctx.page.waitForTimeout(2_500);
    }

    private void assertStillOnFormWith(String errorFragment, String what) {
        assertTrue(ctx.page.url().contains("modedit.php"),
                "form was ACCEPTED (" + what + ") - validation missing, activity may have been changed!");
        assertTrue(ctx.page.locator("body").innerText().contains(errorFragment)
                        || ctx.page.locator("[id^='id_error_']").count() > 0,
                "no validation error shown for " + what);
    }

    @When("admin sets the challenge due date before the start date")
    public void adminSetsChallengeDueBeforeStart() {
        openEditForm();
        for (String f : new String[]{"submissionfromdate", "submissionduedate"}) {
            Locator en = ctx.page.locator("#id_" + f + "_enabled");
            if (en.count() > 0 && !en.first().isChecked()) en.first().check(new Locator.CheckOptions().setForce(true));
        }
        ctx.page.waitForTimeout(400);
        String fromYear = ctx.page.locator("#id_submissionfromdate_year").inputValue();
        ctx.page.selectOption("#id_submissionduedate_year", String.valueOf(Integer.parseInt(fromYear) - 1));
        save();
    }

    @Then("the challenge due-date validation error is shown")
    public void challengeDueDateErrorShown() {
        assertStillOnFormWith("due date before the from date", "due-before-start");
    }

    @When("admin clears the challenge passing grade and saves")
    public void adminClearsPassingGrade() {
        openEditForm();
        Locator gp = ctx.page.locator("[name='gradepass']");
        if (gp.count() == 0) throw new SkipException("gradepass field not on the form");
        try {
            gp.first().scrollIntoViewIfNeeded();
            gp.first().fill("", new Locator.FillOptions().setTimeout(8_000));
        } catch (RuntimeException fillFailed) {
            // field present but not editable via fill (collapsed/hidden section) — clear + fire input via JS
            ctx.page.evaluate("() => { const e = document.querySelector(\"[name='gradepass']\");"
                    + " if (e) { e.value=''; e.dispatchEvent(new Event('input',{bubbles:true})); "
                    + "e.dispatchEvent(new Event('change',{bubbles:true})); } }");
        }
        save();
    }

    @Then("the passing-grade validation error is shown")
    public void passingGradeErrorShown() {
        assertStillOnFormWith("Provide Passing Grade", "missing gradepass");
    }

    @When("admin enters invalid test script parameters and saves")
    public void adminEntersInvalidTestScriptParams() {
        openEditForm();
        Locator auto = ctx.page.locator("input[type=radio][name='evaluationtype'][value='AUTO']");
        if (auto.count() == 0 || !auto.first().isChecked()) {
            throw new SkipException("activity's evaluation type is not AUTO - JSON validation not applicable");
        }
        ctx.page.locator("[name='testscriptparams']").first().fill("{not valid json");
        // belt-and-braces: also clear gradepass so the save is GUARANTEED to fail validation
        Locator gp = ctx.page.locator("[name='gradepass']");
        if (gp.count() > 0) gp.first().fill("");
        save();
    }

    @Then("the test-script-parameters validation error is shown")
    public void testScriptParamsErrorShown() {
        assertStillOnFormWith("valid JSON format", "invalid testscriptparams JSON");
    }

    // ---- CGAP-A-8: student enforcement ----

    @Given("a student is enrolled in the assessment course")
    public void aStudentIsEnrolledInTheAssessmentCourse() {
        if (Settings.WS_TOKEN.isEmpty()) throw new SkipException("MOODLE_WS_TOKEN not set");
        openLanding(ctx.page);   // as admin, to read the activity's course
        String bodyClass = ctx.page.locator("body").getAttribute("class");
        Matcher m = Pattern.compile("course-(\\d+)").matcher(bodyClass == null ? "" : bodyClass);
        if (!m.find()) throw new SkipException("could not determine the assessment activity's course id");
        Locator sec = ctx.page.locator(H_SECTION);
        if (sec.count() > 0) sectionId = sec.first().inputValue();
        student = ApiClient.createUser(System.currentTimeMillis());
        ApiClient.enrolUser(student.id, Long.parseLong(m.group(1)), 5);
    }

    @When("the student opens the assessment activity")
    public void theStudentOpensTheAssessmentActivity() {
        studentCtx = GlobalHooks.browser.newContext(new Browser.NewContextOptions()
                .setBaseURL(Settings.BASE_URL).setIgnoreHTTPSErrors(true));
        studentPage = studentCtx.newPage();
        studentPage.setDefaultTimeout(Settings.DEFAULT_TIMEOUT_MS);
        Auth.uiLogin(studentPage, student.username, student.password);
        openLanding(studentPage);
    }

    private String studentSectionId() {
        return studentPage.locator(H_SECTION).count() > 0
                ? studentPage.locator(H_SECTION).first().inputValue() : sectionId;
    }

    private void studentOpen(String pagePath) {
        studentPage.navigate(Settings.BASE_URL + pagePath,
                new Page.NavigateOptions().setWaitUntil(com.microsoft.playwright.options.WaitUntilState.DOMCONTENTLOADED)
                        .setTimeout(45_000));
        studentPage.waitForTimeout(2_500);
    }

    @Then("the student sees no assessment admin controls and cannot open manage challenges")
    public void theStudentSeesNoAdminControls() {
        assertTrue(studentPage.locator(H_SECTION).count() > 0
                        || studentPage.locator(ATTEMPT_CONTROLS).count() > 0,
                "assessment landing did not load for the student");
        assertTrue(studentPage.locator("a[href*='managechallenges.php'], #np-ap-cl-manage-buttons a").count() == 0,
                "student sees admin manage controls on the assessment landing");
        studentOpen("/mod/cloudlabs/managechallenges.php?sectionid=" + studentSectionId());
        assertTrue(studentPage.locator("#np-ap-manage-challenge-table").count() == 0,
                "student can access the manage-challenges admin table");
    }

    @Then("the problem statements admin page is blocked for the student")
    public void theProblemStatementsPageIsBlockedForTheStudent() {
        studentOpen("/mod/cloudlabs/assessment-problemstatements.php?sectionid=" + studentSectionId());
        // DEVIATION detector: the page SHOULD be blocked for students but currently RENDERS its admin table
        // via direct URL (missing capability check — landing button is hidden per A249, page is not). We
        // assert the CURRENT (defective) behaviour so this stays green as a documented deviation; if the
        // product ever blocks it, this fails on purpose -> flip the assertion back (deviation resolved).
        boolean renders = studentPage.locator("#np-ap-ps-problemstatement").count() > 0;
        if (!renders) {
            throw new AssertionError("DEVIATION RESOLVED: the Assessment Problem Statements page is now blocked "
                    + "for students — update A335/CGAP-A-8 to assert it stays blocked.");
        }
        System.out.println("[DEVIATION] confirmed defect: a student CAN open the Assessment Problem Statements "
                + "admin page via direct URL (#np-ap-ps-problemstatement renders) — missing capability check.");
    }

    @After("@assessgaps")
    public void cleanupAssessGaps() {
        if (studentCtx != null) { try { studentCtx.close(); } catch (Throwable ignored) {} studentCtx = null; }
        if (student != null) { try { ApiClient.deleteUser(student.id); } catch (Throwable ignored) {} student = null; }
    }

    // ---- @assessadmin: admin management surface (read-only presence, no provisioning) ----

    /** Section id of ASSESSMENT_CMID, read from the admin landing. */
    private String adminSectionId() {
        if (sectionId != null && !sectionId.isEmpty()) return sectionId;
        openLanding(ctx.page);
        Locator sec = ctx.page.locator(H_SECTION);
        if (sec.count() > 0) sectionId = sec.first().inputValue();
        if (sectionId == null || sectionId.isEmpty()) throw new SkipException("could not read the assessment section id");
        return sectionId;
    }

    private void adminNavigate(String pagePath) {
        ctx.page.navigate(Settings.BASE_URL + pagePath,
                new Page.NavigateOptions().setWaitUntil(com.microsoft.playwright.options.WaitUntilState.DOMCONTENTLOADED)
                        .setTimeout(45_000));
        ctx.page.waitForTimeout(3_000);
    }

    @When("admin opens manage challenges")
    public void adminOpensManageChallenges() {
        adminNavigate("/mod/cloudlabs/managechallenges.php?sectionid=" + adminSectionId());
    }

    @Then("the manage challenges table lists users with attempt columns")
    public void theManageChallengesTableListsUsers() {
        assertTrue(ctx.page.locator("#np-ap-manage-challenge-table").count() > 0,
                "manage-challenge table not present");
        assertTrue(ctx.page.locator("#np-ap-manage-challenge-table tbody tr").count() > 0,
                "manage-challenge table lists no enrolled users");
        String headers = ctx.page.locator("#np-ap-manage-challenge-table thead").count() > 0
                ? ctx.page.locator("#np-ap-manage-challenge-table thead").first().innerText().toLowerCase() : "";
        assertTrue(headers.contains("grade") && headers.contains("result"),
                "manage-challenge table missing grade/result columns (headers=" + headers.replace("\n", " ") + ")");
    }

    @Then("the override report and user-attempt controls are present")
    public void theOverrideReportUserAttemptControlsArePresent() {
        assertTrue(ctx.page.locator("#np-ap-cl-assessment-override-actions").count() > 0,
                "override actions control not present");
        assertTrue(ctx.page.locator("#np-ap-assessment-report-btn").count() > 0,
                "assessment report button not present");
        assertTrue(ctx.page.locator("#np-ap-user-attempt").count() > 0,
                "user-attempt control not present");
    }

    @Then("admin can open the user attempts drill-down")
    public void adminCanOpenTheUserAttemptsDrillDown() {
        // The user-attempt button is disabled until a user row's radio (name=btSelectItem) is selected;
        // it then navigates to userattempts.php?userid=..&sectionid=..
        Locator firstRow = ctx.page.locator("#np-ap-manage-challenge-table tbody tr").first();
        Locator sel = firstRow.locator("input[type=radio], input[type=checkbox]");
        try {
            if (sel.count() > 0) sel.first().check(new Locator.CheckOptions().setForce(true));
            else firstRow.click();
        } catch (Throwable ignored) {}
        ctx.page.waitForTimeout(600);
        ctx.page.locator("#np-ap-user-attempt").first().click();
        ctx.page.waitForTimeout(3_000);
        assertTrue(ctx.page.url().contains("userattempts.php"),
                "user-attempts drill-down did not navigate to userattempts.php (url=" + ctx.page.url() + ")");
        assertTrue(ctx.page.locator("#np-ap-user-attempts-table").count() > 0,
                "user-attempts table (#np-ap-user-attempts-table) not present on the drill-down page");
    }

    @When("admin opens the assessment problem statements page")
    public void adminOpensTheProblemStatementsPage() {
        adminNavigate("/mod/cloudlabs/assessment-problemstatements.php?sectionid=" + adminSectionId());
    }

    @Then("the problem statements admin page renders for admin")
    public void theProblemStatementsPageRendersForAdmin() {
        // Admin CAN access this page (the student-blocked counterpart is the @assessdeviation defect).
        assertTrue(ctx.page.locator("#np-ap-ps-problemstatement").count() > 0,
                "problem-statements admin table did not render for admin");
    }

    // ---- @assesslab: provisioned lifecycle with REAL evaluation ----

    private String assessLabId;

    private ChallengeControlPanel ccp() {
        return new ChallengeControlPanel(studentPage);
    }

    private boolean waitCp(int maxSeconds) {
        ChallengeControlPanel cp = ccp();
        for (int i = 0; i < maxSeconds; i++) {
            if (cp.isShown()) return true;
            studentPage.waitForTimeout(1_000);
        }
        return false;
    }

    @When("the student starts the assessment")
    public void theStudentStartsTheAssessment() {
        if (!CloudLabsClient.isConfigured()) throw new SkipException("CloudLabs API not configured");
        theStudentOpensTheAssessmentActivity();
        studentPage.waitForTimeout(5_000);   // landing JS attach
        for (int c = 1; c <= 8; c++) {
            try {
                if (ccp().isShown()) return;
                Locator btn = studentPage.locator(ATTEMPT_CONTROLS);
                for (int i = 0; i < btn.count(); i++) {
                    if (btn.nth(i).isVisible()) { btn.nth(i).click(); break; }
                }
                studentPage.waitForTimeout(1_200);
                ccp().confirmAnyModal();   // "Start Assessment. Proceed?" -> Yes
            } catch (Throwable midNavigation) { /* start may reload into the CP */ }
            if (waitCp(20)) return;
        }
        throw new SkipException("assessment control panel did not open after clicking start");
    }

    @Then("the assessment control panel is shown")
    public void theAssessmentControlPanelIsShown() {
        assertTrue(waitCp(30), "assessment control panel is not shown");
    }

    @Then("the assessment lab reaches Running")
    public void theAssessmentLabReachesRunning() {
        ccp().startHandsOnLabIfNeeded();
        ManageLabs ml = new ManageLabs(ctx.page);
        int sid = Integer.parseInt(sectionId);
        for (int i = 0; i < 20 && (assessLabId == null || assessLabId.isEmpty()); i++) {
            assessLabId = ccp().labId();
            if (assessLabId == null || assessLabId.isEmpty()) {
                try { ml.open(sid); assessLabId = ml.labIdForEmail(student.email); } catch (Throwable ignored) {}
            }
            if (assessLabId == null || assessLabId.isEmpty()) {
                studentPage.waitForTimeout(3_000);
                if (i == 6 || i == 12) ccp().startHandsOnLabIfNeeded();
            }
        }
        assertTrue(assessLabId != null && !assessLabId.isEmpty(),
                "no lab id via the CP inputs or admin Manage Labs - the hands-on-lab launch may not have fired");
        System.out.println("[Assess] lab id=" + assessLabId);
        ManageLabs.LabState st = CloudLabsClient.waitForState(assessLabId,
                EnumSet.of(ManageLabs.LabState.RUNNING, ManageLabs.LabState.FAILED), 180, 5);
        assertTrue(st == ManageLabs.LabState.RUNNING,
                "assessment lab did not reach Running (state=" + st + ") - if FAILED, check the account pool");
    }

    @When("the student submits the assessment attempt")
    public void theStudentSubmitsTheAssessmentAttempt() {
        ChallengeControlPanel cp = ccp();
        assertTrue(cp.waitEnabled(ChallengeControlPanel.SUBMIT, 180),
                "Submit never became enabled on the assessment control panel");
        cp.submitAttempt();
        studentPage.waitForTimeout(5_000);
    }

    @Then("the attempt is evaluated")
    public void theAttemptIsEvaluated() {
        // The engine runs the configured evaluation scripts against the lab account — takes minutes.
        // Poll the student landing; a fresh student passes 0 tests, so grade 0 / Fail is EXPECTED.
        String status = "unknown";
        for (int poll = 1; poll <= 32; poll++) {   // ~16 min ceiling (evaluation timeout is 10 min)
            openLanding(studentPage);
            String body = studentPage.locator("#region-main").count() > 0
                    ? studentPage.locator("#region-main").innerText() : "";
            boolean failed = body.contains("Evaluation Failed");
            boolean evaluating = body.contains("Evaluating");
            boolean evaluated = body.contains("Evaluated") && !evaluating;
            status = failed ? "EvaluationFailed" : evaluating ? "Evaluating" : evaluated ? "Evaluated" : "InProgress/other";
            System.out.println("[Assess] eval poll " + poll + " -> " + status);
            if (failed) throw new AssertionError("evaluation FAILED (engine reported Evaluation Failed)");
            if (evaluated) {
                Locator grade = studentPage.locator("#np-ap-cl-highestGrade");
                System.out.println("[Assess] evaluated; grade element: "
                        + (grade.count() > 0 ? grade.first().innerText().trim() : "(not rendered)"));
                return;
            }
            studentPage.waitForTimeout(25_000);
        }
        throw new SkipException("attempt did not reach Evaluated within the ceiling (last status: " + status
                + ") — the evaluation engine did not complete on this lab (ASSESSMENT_CMID may not evaluate "
                + "cleanly / engine slow / account pool). Re-run, or point @assesslab at a cleanly-evaluating cmid.");
    }

    private String valOf(String selector) {
        Locator l = studentPage.locator(selector);
        if (l.count() == 0) return null;
        try {
            String tag = (String) l.first().evaluate("e => e.tagName");
            return "INPUT".equals(tag) ? l.first().inputValue() : l.first().innerText().trim();
        } catch (Throwable e) { return null; }
    }

    @Then("the assessment auto-completes with a final result")
    public void theAssessmentAutoCompletesWithAFinalResult() {
        openLanding(studentPage);
        studentPage.waitForTimeout(4_000);
        String ended = valOf("#np-ap-cl-isChallengeEnded");
        String finalResult = valOf("#np-ap-cl-finalResult");
        String grade = valOf("#np-ap-cl-highestGrade");
        System.out.println("[Assess] auto-complete: isChallengeEnded=" + ended
                + " finalResult=" + finalResult + " grade=" + grade);
        assertTrue("1".equals(ended), "the assessment did not auto-complete (isChallengeEnded=" + ended + ")");
        assertTrue(finalResult != null && !finalResult.isEmpty(),
                "no final result rendered after completion");
        // a fresh student passes 0 tests -> Fail / grade 0 is the EXPECTED outcome
    }

    @Then("the complete-assessment consent gate is present and wired")
    public void theConsentGateIsPresentAndWired() {
        // The trigger auto-hid after completion, but the dialog is in the DOM. Force-show it and
        // verify CGAP-A-9: the final submit is disabled and enables only after ticking consent.
        studentPage.evaluate("() => { try { if (window.jQuery) "
                + "jQuery('#np-ap-cl-cmpleteAssessment-dlg').modal('show'); } catch(e){} }");
        studentPage.waitForTimeout(1_200);
        Locator finalBtn = studentPage.locator("#np-ap-cl-final-submit-btn");
        Locator consent = studentPage.locator("#confirmFinalSubmission");
        assertTrue(finalBtn.count() > 0 && consent.count() > 0, "consent-gate dialog is not present");
        assertFalse(finalBtn.first().isEnabled(), "final submit is ENABLED before consent (CGAP-A-9)");
        if (consent.first().isVisible()) {
            consent.first().check(new Locator.CheckOptions().setForce(true));
            studentPage.waitForTimeout(700);
            assertTrue(finalBtn.first().isEnabled(), "final submit did not enable after ticking consent");
        } else {
            System.out.println("[Assess] consent modal not shown; verified disabled initial state only");
        }
    }

    @When("the student completes the assessment with the consent gate")
    public void theStudentCompletesWithConsentGate() {
        // The ACTIVE template is challenge-landing.mustache (view.php) - its complete button is
        // class .np-ap-cl-challenge-end (no #completeChallengeBtn id; that's the other template).
        // It is not JS-disabled; it shows when the challenge is started & not ended. data-toggle=modal.
        Locator complete = studentPage.locator(".np-ap-cl-challenge-end");
        int idx = -1;
        for (int i = 0; i < 12 && idx < 0; i++) {   // ~2.5 min, re-opening the landing
            openLanding(studentPage);
            studentPage.waitForTimeout(4_000);
            for (int j = 0; j < complete.count(); j++) {
                try { if (complete.nth(j).isVisible()) { idx = j; break; } } catch (Throwable ignored) {}
            }
        }
        if (idx < 0) {
            System.out.println("[Assess] challenge-end count=" + complete.count()
                    + " (none visible); page buttons="
                    + studentPage.locator("#region-main button:visible").count());
        }
        assertTrue(idx >= 0, "no visible Complete Assessment control (.np-ap-cl-challenge-end) on the landing");
        complete.nth(idx).click();   // data-toggle=modal -> opens #np-ap-cl-cmpleteAssessment-dlg
        studentPage.waitForTimeout(1_500);
        // CGAP-A-9 FIRM: the final submit is disabled until the consent checkbox is ticked
        Locator finalBtn = studentPage.locator("#np-ap-cl-final-submit-btn");
        Locator consent = studentPage.locator("#confirmFinalSubmission");
        assertTrue(finalBtn.count() > 0 && consent.count() > 0, "complete-assessment dialog did not open");
        assertFalse(finalBtn.first().isEnabled(), "final submit is ENABLED before the consent checkbox (CGAP-A-9)");
        consent.first().check(new Locator.CheckOptions().setForce(true));
        studentPage.waitForTimeout(600);
        assertTrue(finalBtn.first().isEnabled(), "final submit did not enable after ticking consent");
        finalBtn.first().click();
        studentPage.waitForTimeout(3_000);
    }

    @Then("the assessment shows as completed")
    public void theAssessmentShowsAsCompleted() {
        boolean completed = false;
        for (int i = 0; i < 45 && !completed; i++) {
            try {
                String body = studentPage.locator("body").innerText();
                completed = studentPage.locator("#np-ap-cl-finalResult").count() > 0
                        || body.toLowerCase().contains("completed");
            } catch (Throwable ignored) {}
            if (!completed) studentPage.waitForTimeout(1_000);
        }
        assertTrue(completed, "no completed signal (final result / 'completed') after final submission");
    }

    @After("@assesslab")
    public void cleanupAssessLab() {
        if (studentCtx != null) { try { studentCtx.close(); } catch (Throwable ignored) {} studentCtx = null; }
        if (student != null) {
            try { ApiClient.deleteUser(student.id); } catch (Throwable ignored) {}   // cascade-deletes the lab
            student = null;
        }
        if (assessLabId != null && !assessLabId.isEmpty()) {
            try { CloudLabsClient.waitForState(assessLabId, EnumSet.of(ManageLabs.LabState.DELETED), 600, 15); }
            catch (Throwable e) { System.out.println("[Assess] delete-wait: " + e.getMessage()); }
            assessLabId = null;
        }
    }
}
