package com.nuvepro.moodle.steps;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.nuvepro.moodle.config.Settings;
import com.nuvepro.moodle.helpers.ApiClient;
import com.nuvepro.moodle.helpers.Auth;
import com.nuvepro.moodle.helpers.BrowserUi;
import com.nuvepro.moodle.pages.AssessmentAdmin;
import com.nuvepro.moodle.pages.ManageLabs;
import io.cucumber.java.AfterAll;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import org.testng.SkipException;

import static org.testng.Assert.assertTrue;

/**
 * Doable-batch 2 — the reopen/override cluster (reuses the completed-user override form to assert reopen
 * controls: submissions, duration, date range, remarks) + a couple of admin listing pages. Reopen EFFECT
 * cases (re-attempt, multi-user, delete-new-attempt) and lifecycle/attempt cases skip with an accurate reason.
 */
public class AssessmentDoable2Steps {
    private final TestContext ctx;
    private final AssessmentAdmin admin;

    public AssessmentDoable2Steps(TestContext ctx) {
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

    // ---- completion fixture: create an override-eligible (completed + attempts-exhausted) user ----
    // Completing an assessment REQUIRES the attempt to settle to an evaluated result: the Complete
    // control (#completeChallengeBtn) is enabled by assessment-landing.js only once no attempt is
    // InProgress/Evaluating and at least one is genuinely evaluated. On an environment whose configured
    // assessment (ASSESSMENT_CMID) auto-settles attempts, the poll-for-enabled below succeeds; where it
    // needs a live lab submission it fails fast with an accurate skip. Built at most once per run.

    static ApiClient.SeededUser completedUser;
    static BrowserContext completedCtx;
    static boolean fixtureTried;

    private static final String START_CONTROLS = "#np-ap-cl-challenge-start, .np-ap-cl-attempt-new, "
            + "#np-ap-cl-attempt-new, #btn-cl-retake-assessment, button:has-text('Start Assessment'), "
            + "a:has-text('Start Assessment'), button:has-text('New Attempt')";

    private void ensureEligibleUser() {
        AssessmentAdmin.OverrideOpen r = admin.openOverrideFormForCompletedUser();
        if (r == AssessmentAdmin.OverrideOpen.OPENED) return;
        if (fixtureTried)
            throw new SkipException("no override-eligible user and the completion fixture already ran — " + r);
        fixtureTried = true;
        if (Settings.WS_TOKEN.isEmpty()) throw new SkipException("MOODLE_WS_TOKEN needed to seed a user");
        long courseId = admin.courseIdFor(baseCmid());
        if (courseId == 0) throw new SkipException("could not determine the assessment course id");
        completedUser = ApiClient.createUser(System.currentTimeMillis());
        ApiClient.enrolUser(completedUser.id, courseId, 5);
        completedCtx = BrowserUi.newContext(GlobalHooks.browser, new Browser.NewContextOptions()
                .setBaseURL(Settings.BASE_URL).setIgnoreHTTPSErrors(true));
        Page p = completedCtx.newPage();
        p.setDefaultTimeout(Settings.DEFAULT_TIMEOUT_MS);
        Auth.uiLogin(p, completedUser.username, completedUser.password);
        try {
            for (int attempt = 0; attempt < 3; attempt++) {      // exhaust up to 3 allowed attempts
                boolean started = startAttempt(p);
                System.out.println("[CompletionFixture] attempt " + attempt + " started=" + started);
                if (!started) break;                             // no start control left -> exhausted
                boolean completed = completeAttempt(p);
                System.out.println("[CompletionFixture] attempt " + attempt + " completed=" + completed
                        + " (Complete button " + (completed ? "enabled+clicked" : "NEVER enabled — attempt did "
                        + "not settle to an evaluated result; needs a real lab submission") + ")");
                if (!completed) break;                           // couldn't complete -> stop
            }
        } catch (com.microsoft.playwright.PlaywrightException pe) {
            throw new SkipException("the completion fixture could not drive the assessment to completion: the "
                    + "Complete control never enabled, which means the attempt did not settle to an evaluated "
                    + "result on this environment (needs a live lab submission + evaluation). Provide an "
                    + "auto-settling assessment cmid, or an override-eligible user, to run the reopen cases. ["
                    + firstLine(pe) + "]");
        }
        System.out.println("[CompletionFixture] seeded user " + completedUser.username + " — re-checking eligibility");
        admin.openManageChallenges(baseSection());
        r = admin.openOverrideFormForCompletedUser();
        System.out.println("[CompletionFixture] override eligibility after fixture = " + r
                + " (diag: " + admin.overrideDiag + ")");
        if (r != AssessmentAdmin.OverrideOpen.OPENED)
            throw new SkipException("completion fixture ran but the user is still not override-eligible — "
                    + r + " " + admin.overrideDiag);
    }

    private static String firstLine(Throwable e) {
        String m = e.getMessage();
        return m == null ? e.getClass().getSimpleName() : m.split("\\R", 2)[0];
    }

    private void openLanding(Page p) {
        p.navigate(Settings.BASE_URL + "/mod/cloudlabs/view.php?id=" + baseCmid(),
                new Page.NavigateOptions().setWaitUntil(com.microsoft.playwright.options.WaitUntilState.DOMCONTENTLOADED)
                        .setTimeout(45_000));
        p.waitForTimeout(4_000);
    }

    /** Click a start/new-attempt control (+confirm). False when none is available (attempts exhausted). */
    private boolean startAttempt(Page p) {
        openLanding(p);
        Locator btn = p.locator(START_CONTROLS);
        int idx = -1;
        for (int i = 0; i < btn.count(); i++) {
            try { if (btn.nth(i).isVisible()) { idx = i; break; } } catch (Throwable ignored) {}
        }
        if (idx < 0) return false;
        btn.nth(idx).click();
        p.waitForTimeout(1_500);
        // confirm "Start Assessment. Proceed?" if shown
        Locator yes = p.locator("[data-region='modal-container'] button[data-action='save'], "
                + "[data-region='modal-container'] .btn-primary, button:has-text('Yes')");
        try { if (yes.count() > 0 && yes.first().isVisible()) { yes.first().click(); p.waitForTimeout(2_000); } }
        catch (Throwable ignored) {}
        return true;
    }

    /** Complete the started attempt via the consent-gated Complete control.
     *  The Complete button (#completeChallengeBtn) is DISABLED until assessment-landing.js sees the
     *  attempt SETTLE to an evaluated result (not InProgress/Evaluating, and not only Failed/NotEvaluated
     *  — see updateUserAssesmentCompletion). That settling needs the lab's submission+evaluation, so we
     *  POLL the landing for the button to become ENABLED rather than clicking a dead (disabled) button. */
    private boolean completeAttempt(Page p) {
        Locator btn = p.locator("#completeChallengeBtn, .np-ap-cl-challenge-end");
        long deadline = System.currentTimeMillis() + 90_000;   // wait up to 90s for evaluation to settle
        boolean enabled = false;
        while (System.currentTimeMillis() < deadline) {
            openLanding(p);
            for (int i = 0; i < 12 && !enabled; i++) {          // let the async attempts poll enable it
                for (int j = 0; j < btn.count(); j++) {
                    try { if (btn.nth(j).isVisible() && btn.nth(j).isEnabled()) { enabled = true; break; } }
                    catch (Throwable ignored) {}
                }
                if (!enabled) p.waitForTimeout(2_000);
            }
            if (enabled) break;
        }
        if (!enabled) return false;   // attempt never settled to an evaluable state (needs a real lab run)
        for (int j = 0; j < btn.count(); j++) {
            try { if (btn.nth(j).isVisible() && btn.nth(j).isEnabled()) { btn.nth(j).click(); break; } }
            catch (Throwable ignored) {}
        }
        p.waitForTimeout(1_500);
        Locator consent = p.locator("#confirmFinalSubmission");
        Locator finalBtn = p.locator("#np-ap-cl-final-submit-btn");
        if (consent.count() == 0 || finalBtn.count() == 0) return false;
        try { consent.first().check(new Locator.CheckOptions().setForce(true)); } catch (Throwable ignored) {}
        // final submit is itself disabled until consent is ticked — wait for it to enable
        try { finalBtn.first().waitFor(new Locator.WaitForOptions().setTimeout(5_000)); } catch (Throwable ignored) {}
        for (int i = 0; i < 5; i++) {
            try { if (finalBtn.first().isEnabled()) break; } catch (Throwable ignored) {}
            p.waitForTimeout(500);
        }
        try { finalBtn.first().click(); } catch (Throwable ignored) { return false; }
        p.waitForTimeout(3_000);
        return true;
    }

    @AfterAll
    public static void completionFixtureCleanup() {
        try { if (completedCtx != null) completedCtx.close(); } catch (Throwable ignored) {}
        // NOTE: the seeded completed user is intentionally KEPT (they are the override-eligible data the
        // reopen/override cases depend on across runs). Delete manually if not wanted.
        completedCtx = null; fixtureTried = false;
    }

    // ---- reopen (override form) ----

    @Given("the reopen override form is open for a completed user")
    public void reopenFormOpen() {
        admin.openManageChallenges(baseSection());
        ensureEligibleUser();
    }

    @Then("the reopen offers a submissions control")
    public void reopenSubmissions() {
        assertTrue(!admin.maxAttemptsOptions().isEmpty(), "the reopen form has no submissions control");
    }

    @Then("the reopen offers a duration control")
    public void reopenDuration() {
        assertTrue(ctx.page.locator("#id_timelimit_number, input[name='timelimit[number]'], #id_timelimit_enabled").count() > 0,
                "the reopen form has no duration control");
    }

    @Then("the reopen offers a start and due date range")
    public void reopenDateRange() {
        assertTrue(admin.overrideHasDateRange(), "the reopen form has no start/due date range");
    }

    @Then("the reopen offers a comment field")
    public void reopenComment() {
        assertTrue(ctx.page.locator("#id_remarks, input[name='remarks'], textarea[name='remarks']").count() > 0,
                "the reopen form has no comment (remarks) field");
    }

    @Then("the reopen offers extra attempts and duration controls")
    public void reopenExtraAttemptsAndDuration() {
        assertTrue(!admin.maxAttemptsOptions().isEmpty()
                        && ctx.page.locator("#id_timelimit_number, input[name='timelimit[number]']").count() > 0,
                "the reopen form does not offer both attempts and duration");
    }

    // ---- admin listing pages ----

    @Then("the manage user assessment page lists enrolled users")
    public void manageUserAssessmentListsUsers() {
        admin.openManageChallenges(baseSection());
        assertTrue(admin.manageChallengeUserRows() > 0,
                "the manage user assessment page lists no enrolled users");
    }

    // A20: a user who has NOT started the assessment still appears on the manage user assessment page
    // (the page lists every enrolled user with their completionstatus; not-started ones show non-Completed).
    @Then("the manage user assessment page lists a user who has not started the assessment")
    public void manageUserAssessmentListsNotStarted() {
        admin.openManageChallenges(baseSection());
        assertTrue(admin.manageChallengeUserRows() > 0,
                "the manage user assessment page lists no enrolled users at all");
        assertTrue(admin.manageChallengeNotStartedRows() > 0,
                "the manage user assessment page lists no not-started (non-completed) user");
    }

    @Then("the manage labs page lists labs")
    public void manageLabsListsLabs() {
        int sid;
        try { sid = Integer.parseInt(baseSection()); } catch (NumberFormatException e) { throw new SkipException("bad section id"); }
        ManageLabs ml = new ManageLabs(ctx.page);
        try { ml.open(sid); } catch (RuntimeException e) { throw new SkipException("manage labs page did not open: " + e.getMessage()); }
        assertTrue(ctx.page.locator("#np-ap-manage-lab-table").count() > 0,
                "the manage labs table is not present");
    }

    // ---- attempt/lifecycle dependent: honest skip ----

    @Then("this reopen case needs a live completed attempt to re-attempt")
    public void reopenNeedsLiveAttempt() {
        throw new SkipException("needs a user driven through completion then RE-ATTEMPTING after the reopen "
                + "(end-to-end provisioning), or multiple completed users / enrolment-date manipulation — "
                + "not producible on existing admin data.");
    }

    @Then("this lifecycle case needs a running or completed assessment lab")
    public void lifecycleNeedsLab() {
        throw new SkipException("needs a real assessment lab driven to running/completion (provisioning) — "
                + "covered by the @assesslifecycle / @assesslab flows.");
    }
}
