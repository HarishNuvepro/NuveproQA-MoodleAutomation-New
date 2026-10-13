package com.nuvepro.moodle.steps;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.nuvepro.moodle.config.Settings;
import com.nuvepro.moodle.helpers.ApiClient;
import com.nuvepro.moodle.helpers.CloudLabsClient;
import com.nuvepro.moodle.pages.AssessmentAdmin;
import com.nuvepro.moodle.pages.CourseAdmin;
import com.nuvepro.moodle.pages.GuidedControlPanel;
import com.nuvepro.moodle.pages.GuidedLanding;
import com.nuvepro.moodle.pages.ManageLabs;
import io.cucumber.java.After;
import io.cucumber.java.AfterAll;
import io.cucumber.java.Scenario;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import org.testng.SkipException;

import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

/**
 * Practice Project sequential activity completion (@sacpp / @sacppcomplete). No grade-based
 * restrict rules — only manual mark-as-done and student-must-complete Activity 1 completion.
 *
 * <p>Serial runs share ONE manual-mark fixture across the ordered {@code @sacpp} scenarios; parallel runs
 * ({@code -Dsandbox.parallel=true}) build an isolated fixture per scenario and delete it in
 * {@link #afterScenario()}. Step definitions are shared across worker threads, so all state lives in
 * {@link SacSupport.Holder}s (never plain fields) and browsers/pages come from the scenario's
 * {@link TestContext}.</p>
 */
public class SequentialCompletionPracticeSteps {
    private final TestContext injectedCtx;

    static final String A1 = "Activity 1";
    static final String A2 = "Activity 2";

    // ---------- fixtures ----------

    /** Manual mark-as-done: A1 manual, A2 restricted until A1 complete. */
    static final class MarkFx extends SacSupport.StudentFx {
        String a1Cmid;
        String a2Cmid;
    }

    /** Student must complete A1 (submit the Practice Project); A2 restricted until A1 complete. */
    static final class CompleteFx extends SacSupport.StudentFx {
        String a1Cmid;
        String a2Cmid;
        String sectionId;
        String labId;
        boolean submitted;

        @Override
        void extraTeardown() {
            SacSupport.clearLabTeardownFlags();
        }
    }

    /** Three manual activities; A3 needs A1 and A2. */
    static final class ChainFx extends SacSupport.StudentFx {
        String a1;
        String a2;
        String a3;
    }

    private static final SacSupport.Holder<MarkFx> MARK = new SacSupport.Holder<>();
    private static final SacSupport.Holder<CompleteFx> COMPLETE = new SacSupport.Holder<>();
    private static final SacSupport.Holder<ChainFx> CHAIN = new SacSupport.Holder<>();

    public SequentialCompletionPracticeSteps(TestContext ctx) {
        this.injectedCtx = ctx;
    }

    private TestContext ctx() {
        return SacSupport.ctx(injectedCtx);
    }

    private static MarkFx mark() {
        return MARK.require("Practice Project mark-as-done");
    }

    private static CompleteFx complete() {
        return COMPLETE.require("Practice Project student-must-complete");
    }

    private static ChainFx chain() {
        return CHAIN.require("Practice Project chain");
    }

    private static void requireWsToken() {
        if (Settings.WS_TOKEN.isEmpty()) throw new SkipException("MOODLE_WS_TOKEN not set");
    }

    // ================= fixtures (Given) =================

    @Given("a Practice Project course where Activity 1 uses mark-as-done completion and Activity 2 is restricted until Activity 1 is complete")
    public void ppManualMarkFixture() {
        requireWsToken();
        TestContext c = ctx();
        if (SacSupport.parallel()) {
            MarkFx stale = MARK.get();
            if (stale != null) {
                stale.teardown(c, "SAC-PP");
                MARK.set(null);
            }
        } else if (MARK.get() != null) {
            return;   // serial run: shared fixture already built
        }
        long stamp = SacSupport.uniqueStamp();
        MarkFx f = new MarkFx();
        CourseAdmin admin = new CourseAdmin(c.page);
        f.courseId = admin.createCourse("SAC PP Mark " + stamp, "sacpp" + stamp);
        MARK.set(f);   // registered early so a later failure still gets torn down
        f.a1Cmid = admin.addCloudlabsPracticeProjectActivity(f.courseId, A1,
                CourseAdmin.PracticeCompletionMode.MANUAL_MARK, null);
        f.a2Cmid = admin.addCloudlabsPracticeProjectActivity(f.courseId, A2,
                CourseAdmin.PracticeCompletionMode.MANUAL_MARK,
                CourseAdmin.requireCompleteJson(f.a1Cmid));
        f.student = ApiClient.createUser(stamp);
        ApiClient.enrolUser(f.student.id, Long.parseLong(f.courseId), 5);
        f.startStudentSession(c);
        f.view.open(f.courseId);
        System.out.println("[SAC-PP] manual-mark course=" + f.courseId + " a1=" + f.a1Cmid
                + " a2=" + f.a2Cmid + " student=" + f.student.username);
    }

    @Given("a Practice Project course where Activity 1 requires the student to complete the activity and Activity 2 is restricted until Activity 1 is complete")
    public void ppStudentMustCompleteFixture() {
        requireWsToken();
        TestContext c = ctx();
        CompleteFx stale = COMPLETE.get();
        if (stale != null) {   // always a fresh fixture per scenario
            stale.teardown(c, "SAC-PP");
            COMPLETE.set(null);
        }
        SacSupport.clearLabTeardownFlags();
        long stamp = SacSupport.uniqueStamp();
        CompleteFx f = new CompleteFx();
        CourseAdmin admin = new CourseAdmin(c.page);
        f.courseId = admin.createCourse("SAC PP Complete " + stamp, "sacppc" + stamp);
        COMPLETE.set(f);
        // Activity 1: automatic completion when the student completes/submits the Practice Project.
        f.a1Cmid = admin.addCloudlabsPracticeProjectActivity(f.courseId, A1,
                CourseAdmin.PracticeCompletionMode.STUDENT_MUST_COMPLETE, null);
        // Activity 2: only restrict-access until Activity 1 is complete (no extra completion rules).
        f.a2Cmid = admin.addCloudlabsPracticeProjectActivity(f.courseId, A2,
                CourseAdmin.PracticeCompletionMode.MANUAL_MARK,
                CourseAdmin.requireCompleteJson(f.a1Cmid));
        f.student = ApiClient.createUser(stamp);
        ApiClient.enrolUser(f.student.id, Long.parseLong(f.courseId), 5);
        c.page.waitForTimeout(5_000);
        f.sectionId = new AssessmentAdmin(c.page).sectionIdFor(Integer.parseInt(f.a1Cmid));
        assertTrue(f.sectionId != null && !f.sectionId.isBlank(),
                "Practice Project Activity 1 section id missing (cmid=" + f.a1Cmid + ")");
        f.startStudentSession(c);
        f.view.open(f.courseId);
        System.out.println("[SAC-PP] activity-complete course=" + f.courseId + " a1=" + f.a1Cmid
                + " a2=" + f.a2Cmid + " section=" + f.sectionId + " student=" + f.student.username);
    }

    @Given("a Practice Project course with three activities where Activity 3 requires Activity 1 and Activity 2 complete")
    public void ppThreeActivityChain() {
        requireWsToken();
        TestContext c = ctx();
        ChainFx stale = CHAIN.get();
        if (stale != null) {
            stale.teardown(c, "SAC-PP");
            CHAIN.set(null);
        }
        long stamp = SacSupport.uniqueStamp();
        ChainFx f = new ChainFx();
        CourseAdmin admin = new CourseAdmin(c.page);
        f.courseId = admin.createCourse("SAC PP Chain " + stamp, "sacppch" + stamp);
        CHAIN.set(f);
        f.a1 = admin.addCloudlabsPracticeProjectActivity(f.courseId, "Activity 1",
                CourseAdmin.PracticeCompletionMode.MANUAL_MARK, null);
        f.a2 = admin.addCloudlabsPracticeProjectActivity(f.courseId, "Activity 2",
                CourseAdmin.PracticeCompletionMode.MANUAL_MARK, null);
        f.a3 = admin.addCloudlabsPracticeProjectActivity(f.courseId, "Activity 3",
                CourseAdmin.PracticeCompletionMode.MANUAL_MARK,
                CourseAdmin.requireTwoCompleteJson(f.a1, f.a2));
        f.student = ApiClient.createUser(stamp);
        ApiClient.enrolUser(f.student.id, Long.parseLong(f.courseId), 5);
        f.startStudentSession(c);
        f.view.open(f.courseId);
    }

    // ================= actions (When) =================

    @When("the student marks Practice Project Activity 1 as done")
    public void ppStudentMarksA1Done() {
        MarkFx f = mark();
        f.view.open(f.courseId);
        assertTrue(f.view.hasManualCompletionToggle(A1),
                "Practice Project Activity 1 should show the manual 'Mark as done' control");
        if (!f.view.isManualMarkDone(A1)) {
            f.view.markDone(A1);
        }
    }

    @When("the student logs out and logs in again on the Practice Project course")
    public void ppStudentRelogin() {
        MarkFx f = mark();
        f.startStudentSession(ctx());   // brand-new browser context = fresh login
        f.view.open(f.courseId);
    }

    @When("the student logs out and logs in again on the Practice Project activity-complete course")
    public void ppCompleteStudentRelogin() {
        CompleteFx f = complete();
        f.startStudentSession(ctx());
        f.view.open(f.courseId);
    }

    @When("the student opens Practice Project Activity 1 without completing it")
    public void ppStudentOpensA1Only() {
        CompleteFx f = complete();
        GuidedLanding landing = new GuidedLanding(f.page());
        landing.open(Integer.parseInt(f.a1Cmid));
        assertTrue(landing.isLoaded(), "Practice Project Activity 1 landing should open");
        f.view.open(f.courseId);
    }

    @When("the student completes Practice Project Activity 1")
    public void ppStudentCompletesActivity1() {
        if (!CloudLabsClient.isConfigured()) {
            throw new SkipException("CloudLabs API not configured — Practice Project completion needs a real lab");
        }
        ensurePracticeProjectActivity1Submitted(complete());
    }

    /**
     * Student opens Activity 1, starts the hands-on lab from the control panel (no admin Manage Labs
     * create), waits until Submit is enabled, then submits — same pattern as {@code GuidedSteps}.
     */
    private void ensurePracticeProjectActivity1Submitted(CompleteFx f) {
        int sectionId = Integer.parseInt(requireSectionId(f));
        Page sp = f.page();
        f.view.open(f.courseId);
        sp.waitForTimeout(1_500);

        GuidedLanding landing = new GuidedLanding(sp);
        landing.open(Integer.parseInt(f.a1Cmid));
        sp.waitForTimeout(5_000);
        assertTrue(openGuidedControlPanelFromLanding(sp),
                "Practice Project control panel did not open (url=" + sp.url() + ")");
        GuidedControlPanel cp = new GuidedControlPanel(sp);
        cp.startHandsOnLabIfNeeded();
        cp.confirmAnyModal();

        assertTrue(waitForSubmitEnabledSafe(f, cp, sp, sectionId, 240),
                "Submit did not become enabled after student started the lab (lab=" + f.labId
                        + ", url=" + sp.url() + ")");
        cp.clickGuardedAction(GuidedControlPanel.SUBMIT, GuidedControlPanel.SUBMIT_ACK);
        assertTrue(waitGuidedLandingBackSafe(sp, 90),
                "Practice Project landing did not return after submit (url=" + sp.url() + ")");
        f.submitted = true;
        f.view.open(f.courseId);
        f.page().waitForTimeout(3_000);
    }

    private String requireSectionId(CompleteFx f) {
        if (f.sectionId == null || f.sectionId.isBlank()) {
            f.sectionId = new AssessmentAdmin(ctx().page).sectionIdFor(Integer.parseInt(f.a1Cmid));
        }
        assertTrue(f.sectionId != null && !f.sectionId.isBlank(),
                "section id missing for Activity 1 cmid=" + f.a1Cmid);
        return f.sectionId.trim();
    }

    /** Read-only: capture subscription id after the student provisions (CP hidden field or Manage Labs list). */
    private void tryCapturePpLabIdFromStudentLab(CompleteFx f, GuidedControlPanel cp, ManageLabs ml, int sectionId) {
        if (f.labId != null && !f.labId.isBlank()) {
            return;
        }
        String id = cp.labId();
        if (id == null || id.isBlank()) {
            try {
                ml.open(sectionId);
                id = ml.labIdForEmail(f.student.email);
            } catch (RuntimeException ignored) {
            }
        }
        if (id == null || id.isBlank()) {
            return;
        }
        f.labId = id.trim();
        GlobalHooks.labProvisioned = true;
        GlobalHooks.teardownSectionId = String.valueOf(sectionId);
        GlobalHooks.teardownLabId = f.labId;
        GlobalHooks.teardownStudentEmail = f.student.email;
        System.out.println("[SAC-PP] student-provisioned lab id=" + f.labId);
    }

    /** Prefer Continue when a lab already exists; otherwise Start / New Attempt. */
    private boolean openGuidedControlPanelFromLanding(Page sp) {
        String startSel = GuidedLanding.CONTINUE + ", " + GuidedLanding.START + ", "
                + GuidedLanding.NEW_ATTEMPT;
        for (int c = 1; c <= 10; c++) {
            try {
                GuidedControlPanel cp = new GuidedControlPanel(sp);
                if (cp.isShown()) {
                    return true;
                }
                Locator btn = sp.locator(startSel);
                if (btn.count() > 0 && btn.first().isVisible()) {
                    btn.first().click();
                    sp.waitForTimeout(1_500);
                    cp.confirmAnyModal();
                }
            } catch (Throwable midNavigation) { /* landing/CP reload */ }
            if (waitGuidedControlPanel(sp, 15)) {
                return true;
            }
        }
        return waitGuidedControlPanel(sp, 20);
    }

    private boolean waitForSubmitEnabledSafe(CompleteFx f, GuidedControlPanel cp, Page sp, int sectionId,
                                             int maxSeconds) {
        ManageLabs ml = new ManageLabs(ctx().page);
        for (int i = 0; i < maxSeconds; i++) {
            try {
                if (cp.waitEnabled(GuidedControlPanel.SUBMIT, 1)) {
                    tryCapturePpLabIdFromStudentLab(f, cp, ml, sectionId);
                    return true;
                }
                if (i == 0 || i % 15 == 0) {
                    cp.startHandsOnLabIfNeeded();
                    cp.confirmAnyModal();
                }
                if (i > 0 && i % 25 == 0) {
                    tryCapturePpLabIdFromStudentLab(f, cp, ml, sectionId);
                }
            } catch (RuntimeException ignored) { /* navigation */ }
            sp.waitForTimeout(1_000);
        }
        try {
            return cp.waitEnabled(GuidedControlPanel.SUBMIT, 1);
        } catch (RuntimeException e) {
            return false;
        }
    }

    private boolean waitGuidedLandingBackSafe(Page sp, int maxSeconds) {
        for (int i = 0; i < maxSeconds; i++) {
            try {
                String url = sp.url();
                if (url.contains("/mod/cloudlabs/view.php")) {
                    Locator panel = sp.locator(GuidedLanding.CONTAINER);
                    if (panel.count() > 0 && panel.first().isVisible()) {
                        return true;
                    }
                }
                if (url.contains("view.php") && !new GuidedControlPanel(sp).isShown()) {
                    return true;
                }
            } catch (RuntimeException ignored) { /* context destroyed during redirect */ }
            sp.waitForTimeout(1_000);
        }
        return false;
    }

    // ================= assertions (Then) — manual mark-as-done =================

    @Then("Practice Project Activity 2 is not accessible to the student")
    public void ppA2NotAccessible() {
        MarkFx f = mark();
        f.view.open(f.courseId);
        assertTrue(f.view.isRestricted(A2),
                "Practice Project Activity 2 should stay restricted while Activity 1 is incomplete");
        assertFalse(f.view.isAccessible(A2),
                "Practice Project Activity 2 must not be accessible while Activity 1 is incomplete");
        assertFalse(f.view.isCompleted(A1), "Practice Project Activity 1 must still be incomplete");
    }

    @Then("Practice Project Activity 1 is shown as not completed")
    public void ppA1NotCompleted() {
        MarkFx f = mark();
        f.view.open(f.courseId);
        assertTrue(f.view.hasManualCompletionToggle(A1),
                "Practice Project Activity 1 should offer manual 'Mark as done' (otherwise 'not completed' proves nothing)");
        assertFalse(f.view.isManualMarkDone(A1),
                "Practice Project Activity 1 should be incomplete before mark-as-done");
        assertTrue(f.view.isRestricted(A2),
                "Practice Project Activity 2 should still be restricted while Activity 1 is incomplete");
    }

    @Then("the Practice Project restriction message names Activity 1")
    public void ppRestrictionNamesA1() {
        MarkFx f = mark();
        f.view.open(f.courseId);
        String msg = f.view.restrictionMessage(A2);
        assertFalse(msg.isBlank(), "Practice Project Activity 2 should show a restriction message");
        String lower = msg.toLowerCase();
        assertTrue(lower.contains("activity 1"),
                "restriction message should name Activity 1, was: '" + msg + "'");
        assertTrue(lower.contains("complete"),
                "restriction message should state the completion requirement, was: '" + msg + "'");
    }

    @Then("Practice Project Activity 1 is shown as completed")
    public void ppA1Completed() {
        MarkFx f = mark();
        f.view.open(f.courseId);
        assertTrue(f.view.isManualMarkDone(A1), "Practice Project Activity 1 should show marked done");
        // survives a fresh page load, i.e. it was saved server-side
        f.view.open(f.courseId);
        assertTrue(f.view.isManualMarkDone(A1),
                "Practice Project Activity 1 completion should persist after reloading the course page");
    }

    @Then("Practice Project Activity 2 becomes accessible to the student")
    public void ppA2Accessible() {
        MarkFx f = mark();
        waitUntilPpA2Unrestricted(f);
        assertFalse(f.view.isRestricted(A2),
                "Practice Project Activity 2 should unlock after Activity 1 is marked done");
        assertTrue(f.view.isAccessible(A2), "Practice Project Activity 2 should be listed and enabled");
        assertTrue(f.view.isManualMarkDone(A1), "Practice Project Activity 1 should show marked done");
    }

    @Then("Practice Project Activity 2 remains accessible to the student")
    public void ppA2RemainsAccessible() {
        MarkFx f = mark();
        waitUntilPpA2Unrestricted(f);
        assertFalse(f.view.isRestricted(A2),
                "Practice Project Activity 2 should remain accessible after re-login");
        assertTrue(f.view.isAccessible(A2), "Practice Project Activity 2 should still be enabled after re-login");
        assertTrue(f.view.isManualMarkDone(A1),
                "Practice Project Activity 1 should still show marked done after re-login");
    }

    @Then("Practice Project Activity 3 stays restricted until both Activity 1 and Activity 2 are complete")
    public void ppA3ChainRestricted() {
        ChainFx f = chain();
        f.view.open(f.courseId);
        assertTrue(f.view.isRestricted("Activity 3"),
                "Activity 3 should stay restricted before Activity 1 and 2 complete");

        f.view.markDone("Activity 1");
        f.view.open(f.courseId);
        assertTrue(f.view.isManualMarkDone("Activity 1"), "Activity 1 should show marked done");
        assertFalse(f.view.isManualMarkDone("Activity 2"), "Activity 2 must still be incomplete");
        assertTrue(f.view.isRestricted("Activity 3"),
                "Activity 3 should stay restricted after only Activity 1 is marked done");

        f.view.markDone("Activity 2");
        f.view.open(f.courseId);
        assertTrue(f.view.isManualMarkDone("Activity 2"), "Activity 2 should show marked done");
        for (int i = 0; i < 10 && f.view.isRestricted("Activity 3"); i++) {   // availability can lag a moment
            f.page().waitForTimeout(1_000);
            f.view.open(f.courseId);
        }
        assertFalse(f.view.isRestricted("Activity 3"),
                "Activity 3 should unlock after Activity 1 and 2 are marked done");
        assertTrue(f.view.isAccessible("Activity 3"), "Activity 3 should be listed and enabled once unlocked");
    }

    // ================= assertions (Then) — student must complete the activity =================

    @Then("Practice Project Activity 2 is not accessible until Activity 1 is completed")
    public void ppCompleteA2Locked() {
        CompleteFx f = complete();
        f.view.open(f.courseId);
        assertTrue(f.view.isRestricted(A2),
                "Activity 2 should stay restricted until the student completes Activity 1");
        assertFalse(f.view.isAccessible(A2), "Activity 2 must not be accessible until Activity 1 is completed");
        assertFalse(f.view.isCompleted(A1), "Activity 1 must still be incomplete");
        assertFalse(f.submitted, "Locked-state check must not run after the student already submitted Activity 1");
    }

    @Then("Practice Project Activity 1 is shown as not completed by activity completion")
    public void ppCompleteA1Incomplete() {
        CompleteFx f = complete();
        f.view.open(f.courseId);
        assertFalse(f.view.isCompleted(A1),
                "Activity 1 should be incomplete before the student completes the Practice Project");
        // the badge check alone is weak (absent selector also reads "not completed") — the gated
        // Activity 2 is the authoritative proof that Moodle considers Activity 1 incomplete
        assertTrue(f.view.isRestricted(A2),
                "Activity 2 must stay restricted while Activity 1 is incomplete");
    }

    @Then("Practice Project Activity 2 becomes accessible after Activity 1 is completed")
    public void ppCompleteA2Accessible() {
        CompleteFx f = complete();
        assertTrue(f.submitted, "Student must have submitted the Practice Project before Activity 2 can unlock");
        waitUntilPpA2Unrestricted(f);
        assertFalse(f.view.isRestricted(A2),
                "Activity 2 should unlock after Activity 1 Practice Project completion");
        assertTrue(f.view.isAccessible(A2), "Activity 2 should be listed and enabled after unlock");
    }

    @Then("Practice Project Activity 2 remains accessible after Activity 1 was completed and the student re-logged in")
    public void ppCompleteA2Persists() {
        CompleteFx f = complete();
        assertTrue(f.submitted, "Student must have submitted the Practice Project before this check");
        waitUntilPpA2Unrestricted(f);
        assertFalse(f.view.isRestricted(A2), "Activity 2 should remain unlocked after re-login");
        assertTrue(f.view.isAccessible(A2), "Activity 2 should still be enabled after re-login");
        SacSupport.clearLabTeardownFlags();
    }

    // ================= teardown =================

    /**
     * The student-must-complete fixture is per-scenario in every mode. Parallel runs additionally own
     * their manual-mark and chain fixtures per scenario; serial runs keep those shared until
     * {@link #tearDownPracticeSacFixtures()}.
     */
    @After("@sacpp")
    public void afterScenario(Scenario scenario) {
        TestContext c = ctx();
        boolean failed = scenario.isFailed();
        SacSupport.release(COMPLETE, c, "SAC-PP", failed);
        if (SacSupport.parallel()) {
            SacSupport.release(MARK, c, "SAC-PP", failed);
            SacSupport.release(CHAIN, c, "SAC-PP", failed);
        }
        SacSupport.clearLabTeardownFlags();
    }

    @AfterAll
    public static void tearDownPracticeSacFixtures() {
        if (SacSupport.parallel()) return;   // parallel scenarios already cleaned up after themselves
        teardownHolder(COMPLETE, null);
        teardownHolder(CHAIN, null);
        teardownHolder(MARK, null);
    }

    private static <T extends SacSupport.StudentFx> void teardownHolder(SacSupport.Holder<T> holder,
                                                                         TestContext c) {
        T f = holder.get();
        if (f == null) return;
        try {
            f.teardown(c, "SAC-PP");
        } finally {
            holder.set(null);
        }
    }

    // ================= helpers =================

    private static void waitUntilPpA2Unrestricted(SacSupport.StudentFx f) {
        for (int i = 0; i < 40; i++) {
            f.view.open(f.courseId);
            if (!f.view.isRestricted(A2)) return;
            f.page().waitForTimeout(2_000);
        }
    }

    private static boolean waitGuidedControlPanel(Page page, int maxSeconds) {
        GuidedControlPanel cp = new GuidedControlPanel(page);
        for (int i = 0; i < maxSeconds; i++) {
            if (cp.isShown()) return true;
            page.waitForTimeout(1_000);
        }
        return false;
    }
}
