package com.nuvepro.moodle.steps;

import com.microsoft.playwright.Page;
import com.nuvepro.moodle.config.Settings;
import com.nuvepro.moodle.helpers.ApiClient;
import com.nuvepro.moodle.pages.AssessmentAdmin;
import com.nuvepro.moodle.pages.ChallengeControlPanel;
import com.nuvepro.moodle.pages.CourseAdmin;
import com.nuvepro.moodle.pages.CourseView;
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
 * Sequential Activity Completion (@sac) for CloudLabs ASSESSMENT activities.
 *
 * <ul>
 *   <li><b>Mark-as-done</b> (SAC-1, 9, 11, 12, 13, 16): course with Activity 1 (manual mark-as-done) and
 *       Activity 2 (restricted until Activity 1 complete) + a seeded student.</li>
 *   <li><b>Grade-based</b> (@sacgrade, SAC-2..8, 14): own fixture per scenario; student submits, admin grades.</li>
 *   <li><b>Chain</b> (SAC-15): three activities, Activity 3 needs 1 and 2.</li>
 * </ul>
 *
 * <p>Serial runs reuse ONE mark-as-done fixture across the ordered scenarios (fast). Parallel runs
 * ({@code -Dsandbox.parallel=true}) build an isolated fixture per scenario and tear it down in
 * {@link #afterScenario()} — step definitions are shared across worker threads, so all state lives in
 * {@link SacSupport.Holder}s, never in plain fields. Needs MOODLE_WS_TOKEN.</p>
 */
public class SequentialCompletionSteps {
    private final TestContext injectedCtx;

    static final String A1 = "Activity 1";
    static final String A2 = "Activity 2";

    // ---------- fixtures ----------

    static final class MarkFx extends SacSupport.StudentFx {
        String a1Cmid;
        String a2Cmid;
    }

    static final class GradeFx extends SacSupport.StudentFx {
        CourseAdmin.GradeCompletion cond;
        String a1Cmid;
        String a1SectionId;
        boolean submitted;
        boolean teacherGradeApplied;
        int lastTeacherGrade = -1;
        int passMark = 50;
    }

    static final class ChainFx extends SacSupport.StudentFx {
        String a1;
        String a2;
        String a3;
    }

    private static final SacSupport.Holder<MarkFx> MARK = new SacSupport.Holder<>();
    private static final SacSupport.Holder<GradeFx> GRADE = new SacSupport.Holder<>();
    private static final SacSupport.Holder<ChainFx> CHAIN = new SacSupport.Holder<>();

    public SequentialCompletionSteps(TestContext ctx) {
        this.injectedCtx = ctx;
    }

    private TestContext ctx() {
        return SacSupport.ctx(injectedCtx);
    }

    private static MarkFx mark() {
        return MARK.require("SAC mark-as-done");
    }

    private static GradeFx grade() {
        return GRADE.require("SAC grade");
    }

    private static ChainFx chain() {
        return CHAIN.require("SAC chain");
    }

    private static void requireWsToken() {
        if (Settings.WS_TOKEN.isEmpty()) throw new SkipException("MOODLE_WS_TOKEN not set (needed to seed the student)");
    }

    // ================= mark-as-done (SAC-1, 9, 11, 12, 13, 16) =================

    @Given("a course where Activity 1 uses mark-as-done completion and Activity 2 is restricted until Activity 1 is complete")
    public void aSequentialCompletionCourse() {
        requireWsToken();
        TestContext c = ctx();
        if (SacSupport.parallel()) {
            MarkFx stale = MARK.get();
            if (stale != null) {
                stale.teardown(c, "SAC");
                MARK.set(null);
            }
        } else if (MARK.get() != null) {
            return;   // serial run: the shared fixture was already built
        }
        long stamp = SacSupport.uniqueStamp();
        MarkFx f = new MarkFx();
        CourseAdmin admin = new CourseAdmin(c.page);
        f.courseId = admin.createCourse("SAC Auto " + stamp, "sacauto" + stamp);
        MARK.set(f);   // registered early so a later failure still gets torn down
        f.a1Cmid = admin.addCloudlabsAssessmentActivity(f.courseId, A1, true, null);
        f.a2Cmid = admin.addCloudlabsAssessmentActivity(f.courseId, A2, false,
                CourseAdmin.requireCompleteJson(f.a1Cmid));
        f.student = ApiClient.createUser(stamp);
        ApiClient.enrolUser(f.student.id, Long.parseLong(f.courseId), 5);   // 5 = student
        f.startStudentSession(c);
        f.view.open(f.courseId);
        System.out.println("[SAC] fixture course=" + f.courseId + " a1=" + f.a1Cmid
                + " a2=" + f.a2Cmid + " student=" + f.student.username);
    }

    @Then("Activity 2 is not accessible to the student")
    public void activity2NotAccessible() {
        MarkFx f = mark();
        f.view.open(f.courseId);
        assertTrue(f.view.isRestricted(A2), "Activity 2 should be restricted while Activity 1 is incomplete");
        assertFalse(f.view.isAccessible(A2), "Activity 2 must not be accessible while Activity 1 is incomplete");
        assertFalse(f.view.isCompleted(A1), "Activity 1 must still be incomplete (nothing was marked done)");
    }

    @Then("Activity 1 is shown as not completed")
    public void activity1NotCompleted() {
        MarkFx f = mark();
        f.view.open(f.courseId);
        assertTrue(f.view.hasManualCompletionToggle(A1),
                "Activity 1 should offer manual 'Mark as done' (otherwise 'not completed' proves nothing)");
        assertFalse(f.view.isCompleted(A1), "Activity 1 should be incomplete before it is marked done");
        assertTrue(f.view.isRestricted(A2), "Activity 2 should still be restricted while Activity 1 is incomplete");
    }

    @Then("the restriction message names Activity 1")
    public void restrictionMessageNamesActivity1() {
        MarkFx f = mark();
        f.view.open(f.courseId);
        String msg = f.view.restrictionMessage(A2);
        assertFalse(msg.isBlank(), "Activity 2 should show a restriction message to the student");
        String lower = msg.toLowerCase();
        assertTrue(lower.contains("activity 1"),
                "restriction message should name Activity 1, was: '" + msg + "'");
        assertTrue(lower.contains("complete"),
                "restriction message should state the completion requirement, was: '" + msg + "'");
    }

    @When("the student marks Activity 1 as done")
    public void studentMarksActivity1Done() {
        MarkFx f = mark();
        f.view.open(f.courseId);
        assertTrue(f.view.hasManualCompletionToggle(A1),
                "Activity 1 should show the manual 'Mark as done' control");
        if (!f.view.isCompleted(A1)) {
            f.view.markDone(A1);
        }
    }

    @Then("Activity 1 is shown as completed")
    public void activity1Completed() {
        MarkFx f = mark();
        f.view.open(f.courseId);
        assertTrue(f.view.isCompleted(A1), "Activity 1 should be completed after being marked done");
        // survives a fresh page load, i.e. it was saved server-side (not just toggled in the DOM)
        f.view.open(f.courseId);
        assertTrue(f.view.isCompleted(A1), "Activity 1 completion should persist after reloading the course page");
    }

    @Then("Activity 2 becomes accessible to the student")
    public void activity2Accessible() {
        MarkFx f = mark();
        waitUntilActivity2Unrestricted(f);
        assertFalse(f.view.isRestricted(A2), "Activity 2 should be accessible after Activity 1 completion");
        assertTrue(f.view.isAccessible(A2), "Activity 2 should be listed and enabled on the course page");
        assertTrue(f.view.isCompleted(A1), "Activity 1 should show completed once Activity 2 is unlocked");
    }

    @When("the student logs out and logs in again")
    public void studentReLogsIn() {
        MarkFx f = mark();
        f.startStudentSession(ctx());   // brand-new browser context = fresh login, no session reuse
        f.view.open(f.courseId);
    }

    @Then("Activity 2 remains accessible to the student")
    public void activity2RemainsAccessible() {
        MarkFx f = mark();
        waitUntilActivity2Unrestricted(f);
        assertFalse(f.view.isRestricted(A2), "Activity 2 should remain accessible after re-login");
        assertTrue(f.view.isAccessible(A2), "Activity 2 should still be enabled after re-login");
        assertTrue(f.view.isCompleted(A1), "Activity 1 should still show completed after re-login");
    }

    /** Availability on the course page can lag briefly after manual completion toggles. */
    private static void waitUntilActivity2Unrestricted(MarkFx f) {
        for (int i = 0; i < 12; i++) {
            f.view.open(f.courseId);
            if (!f.view.isRestricted(A2)) return;
            f.page().waitForTimeout(1_000);
        }
    }

    // ================= grade-based cases (SAC-2..8, SAC-14) =================
    // Each grade scenario builds its OWN fresh fixture (independent, no ordering coupling): a course
    // with Activity 1 = CloudLabs assessment (grade-based completion) + Activity 2 restricted until
    // Activity 1 complete + a seeded enrolled student.

    private void buildGradeFixture(CourseAdmin.GradeCompletion cond, int passMark) {
        requireWsToken();
        TestContext c = ctx();
        GradeFx stale = GRADE.get();
        if (stale != null) {   // fresh per scenario
            stale.teardown(c, "SAC-grade");
            GRADE.set(null);
        }
        SacSupport.clearLabTeardownFlags();
        long stamp = SacSupport.uniqueStamp();
        GradeFx f = new GradeFx();
        CourseAdmin admin = new CourseAdmin(c.page);
        f.courseId = admin.createCourse("SAC Grade " + stamp, "sacg" + stamp);
        GRADE.set(f);
        f.student = ApiClient.createUser(stamp);
        ApiClient.enrolUser(f.student.id, Long.parseLong(f.courseId), 5);
        f.cond = cond;
        f.a1Cmid = admin.addGradedCloudlabsAssessmentActivity(f.courseId, A1, cond, passMark, null);
        f.a1SectionId = new AssessmentAdmin(c.page).sectionIdFor(Integer.parseInt(f.a1Cmid));
        f.passMark = passMark > 0 ? passMark : 50;
        String a2Restriction = CourseAdmin.requireCompleteJson(f.a1Cmid);
        if (cond == CourseAdmin.GradeCompletion.GRADE_AND_MANUAL) {
            // Moodle core ignores "require passing grade" once completion is MANUAL (a failing grade + Mark as
            // done still completes Activity 1), so the grade half of the rule is enforced on Activity 2's own
            // restriction: Activity 1 marked complete AND Activity 1 grade >= pass mark.
            String gradeItemId = admin.resolveGradeItemIdForNamedActivity(f.courseId, f.a1Cmid, A1);
            if (gradeItemId == null) {
                throw new IllegalStateException("gradebook item id for '" + A1 + "' not found (course " + f.courseId + ")");
            }
            a2Restriction = CourseAdmin.requireCompleteAndMinGradeJson(f.a1Cmid, gradeItemId, f.passMark);
            System.out.println("[SAC-grade] Activity 2 restriction = completion(A1) AND grade(item " + gradeItemId
                    + ") >= " + f.passMark);
        }
        admin.addCloudlabsAssessmentActivity(f.courseId, A2, false, a2Restriction);
        f.startStudentSession(c);
        f.view.open(f.courseId);
        System.out.println("[SAC-grade] course=" + f.courseId + " a1=" + f.a1Cmid + " section="
                + f.a1SectionId + " student=" + f.student.username + " cond=" + cond);
    }

    @Given("a course where Activity 1 requires any grade and Activity 2 is restricted until Activity 1 is complete")
    public void gradeFixtureAnyGrade() {
        buildGradeFixture(CourseAdmin.GradeCompletion.ANY_GRADE, 0);
    }

    @Given("a course where Activity 1 requires a passing grade of {int} and Activity 2 is restricted until Activity 1 is complete")
    public void gradeFixturePassing(int passMark) {
        buildGradeFixture(CourseAdmin.GradeCompletion.PASSING_GRADE, passMark);
    }

    @Given("a course where Activity 1 requires passing grade of {int} and manual mark-as-done and Activity 2 is restricted until Activity 1 is complete")
    public void gradePassingAndManualFixture(int passMark) {
        buildGradeFixture(CourseAdmin.GradeCompletion.GRADE_AND_MANUAL, passMark);
    }

    @Given("a course where Activity 1 requires a grade and manual mark-as-done and Activity 2 is restricted until Activity 1 is complete")
    public void gradeAndManualFixture() {
        gradePassingAndManualFixture(50);
    }

    @When("the student marks Activity 1 as done without receiving a grade")
    public void studentMarksDoneWithoutGrade() {
        GradeFx f = grade();
        f.view.open(f.courseId);
        if (!f.view.isCompleted(A1)) {
            f.view.markDone(A1);
        }
        f.view.open(f.courseId);
        f.page().waitForTimeout(2_000);
    }

    @When("the student submits Activity 1 assessment attempt")
    public void studentSubmitsActivity1AssessmentAttempt() {
        ensureStudentSubmittedActivity1(grade());
    }

    @When("the teacher gives the student a grade of {int} on Activity 1 without marking it done")
    public void teacherGradesWithoutStudentMarkDone(int grade) {
        GradeFx f = grade();
        if (!f.submitted) ensureStudentSubmittedActivity1(f);
        gradeActivity1Submission(f, grade);
    }

    @When("the student marks Activity 1 as done after receiving a grade")
    public void studentMarksDoneAfterGrade() {
        GradeFx f = grade();
        f.view.open(f.courseId);
        assertTrue(f.view.hasManualCompletionToggle(A1),
                "Activity 1 should offer manual mark-as-done on the course page (SAC-7/6)");
        f.view.markDoneIfPresent(A1);
        f.page().waitForTimeout(1_500);
    }

    @When("the student does not mark Activity 1 as done")
    public void studentDoesNotMarkActivity1Done() {
        GradeFx f = grade();
        f.view.open(f.courseId);
        assertTrue(f.view.hasManualCompletionToggle(A1),
                "Activity 1 should show manual mark-as-done on the course page (SAC-8)");
        assertFalse(f.view.isManualMarkDone(A1),
                "Student must not mark Activity 1 done (SAC-8: passing grade alone is not mark-as-done)");
        assertTrue(f.view.isRestricted(A2),
                "Activity 2 should stay restricted when Activity 1 is not marked done (even with a passing grade)");
        f.page().waitForTimeout(2_000);
    }

    @When("the teacher gives the student a grade of {int} on Activity 1")
    public void teacherGradesActivity1(int grade) {
        GradeFx f = grade();
        if (!f.submitted) ensureStudentSubmittedActivity1(f);
        gradeActivity1Submission(f, grade);
        f.view.open(f.courseId);
        f.page().waitForTimeout(2_000);
    }

    @Then("Activity 1 is fully complete for grade and manual requirements")
    public void activity1FullyCompleteGradeAndManual() {
        GradeFx f = grade();
        f.view.open(f.courseId);
        assertTrue(f.teacherGradeApplied, "Teacher must have graded Activity 1 before this assertion (SAC-6)");
        assertTrue(f.lastTeacherGrade >= f.passMark,
                "SAC-6: Activity 1 grade must be passing (got " + f.lastTeacherGrade + ", pass=" + f.passMark + ")");
        assertTrue(f.view.isCompleted(A1),
                "Activity 1 should show marked done after passing grade and student mark-as-done");
        assertTrue(activity1CompleteForRestrictAccess(f),
                "Activity 2 should unlock only when Activity 1 has passing grade and is marked done");
    }

    @Then("Activity 1 is not fully complete for grade and manual requirements")
    public void activity1NotFullyCompleteGradeAndManual() {
        GradeFx f = grade();
        f.view.open(f.courseId);
        assertTrue(f.view.isManualMarkDone(A1),
                "SAC-7: student marked Activity 1 done but failing grade must block full completion");
        assertTrue(f.lastTeacherGrade >= 0 && f.lastTeacherGrade < f.passMark,
                "SAC-7: Activity 1 grade must be below pass (got " + f.lastTeacherGrade + ", pass=" + f.passMark + ")");
        assertFalse(activity1CompleteForRestrictAccess(f),
                "Activity 1 must not count complete for restrict-access when grade and/or mark-as-done is missing "
                        + "(teacherGradeApplied=" + f.teacherGradeApplied + ", lastGrade="
                        + f.lastTeacherGrade + ", manualMark=" + f.view.isManualMarkDone(A1) + ")");
    }

    @Then("Activity 1 meets the passing grade requirement but is not marked as done by the student")
    public void activity1PassingGradeWithoutManualMark() {
        GradeFx f = grade();
        f.view.open(f.courseId);
        assertTrue(f.teacherGradeApplied, "Teacher must have graded Activity 1 before this assertion (SAC-8)");
        assertTrue(f.lastTeacherGrade >= f.passMark,
                "Activity 1 should have a passing teacher grade (got " + f.lastTeacherGrade
                        + ", pass=" + f.passMark + ")");
        assertFalse(f.view.isManualMarkDone(A1), "Student must not have marked Activity 1 as done (SAC-8)");
        assertFalse(activity1CompleteForRestrictAccess(f),
                "Activity 2 must stay locked until both passing grade and mark-as-done (SAC-8)");
    }

    @Then("the graded Activity 2 becomes accessible to the student")
    public void gradedActivity2Accessible() {
        GradeFx f = grade();
        assertTrue(f.submitted, "Student must have submitted Activity 1 before Activity 2 can unlock");
        assertTrue(f.teacherGradeApplied, "Teacher must have graded Activity 1 before Activity 2 can unlock");
        if (f.cond == CourseAdmin.GradeCompletion.PASSING_GRADE
                || f.cond == CourseAdmin.GradeCompletion.GRADE_AND_MANUAL) {
            assertTrue(f.lastTeacherGrade >= f.passMark,
                    "Unlock requires a passing grade (got " + f.lastTeacherGrade + ", pass=" + f.passMark + ")");
        }
        waitUntilGradedActivity2Unrestricted(f);
        f.view.open(f.courseId);
        assertFalse(f.view.isRestricted(A2),
                "Activity 2 should not show restrict-access on the course page after Activity 1 is graded");
        assertTrue(f.view.isAccessible(A2),
                "Activity 2 should be accessible on the course page (no sandbox / other activity navigation)");
        SacSupport.clearLabTeardownFlags();
    }

    @Then("the graded Activity 2 is not accessible to the student")
    public void gradedActivity2NotAccessible() {
        GradeFx f = grade();
        if (f.teacherGradeApplied && f.cond == CourseAdmin.GradeCompletion.PASSING_GRADE) {
            assertTrue(f.lastTeacherGrade < f.passMark,
                    "Locked-state check is only valid for a failing grade (got " + f.lastTeacherGrade
                            + ", pass=" + f.passMark + ")");
        }
        waitUntilGradedActivity2Restricted(f);
        f.view.open(f.courseId);
        assertTrue(f.view.isRestricted(A2),
                "Activity 2 should stay restricted until Activity 1 is marked done and has a passing grade "
                        + "(restriction=" + f.view.restrictionMessage(A2) + ")");
        assertFalse(f.view.isAccessible(A2), "Activity 2 should not be accessible on the course page");
        SacSupport.clearLabTeardownFlags();
    }

    @After("@sacgrade")
    public void afterSacGradeScenario() {
        SacSupport.clearLabTeardownFlags();
    }

    // ================= three-activity chain (SAC-15 / row 16) =================

    @Given("a course with three activities where Activity 3 requires Activity 1 and Activity 2 complete")
    public void threeActivityChainCourse() {
        requireWsToken();
        TestContext c = ctx();
        ChainFx stale = CHAIN.get();
        if (stale != null) {
            stale.teardown(c, "SAC-chain");
            CHAIN.set(null);
        }
        long stamp = SacSupport.uniqueStamp();
        ChainFx f = new ChainFx();
        CourseAdmin admin = new CourseAdmin(c.page);
        f.courseId = admin.createCourse("SAC Chain " + stamp, "sacchain" + stamp);
        CHAIN.set(f);
        f.a1 = admin.addCloudlabsAssessmentActivity(f.courseId, "Activity 1", true, null);
        f.a2 = admin.addCloudlabsAssessmentActivity(f.courseId, "Activity 2", true, null);
        f.a3 = admin.addCloudlabsAssessmentActivity(f.courseId, "Activity 3", false,
                CourseAdmin.requireTwoCompleteJson(f.a1, f.a2));
        f.student = ApiClient.createUser(stamp);
        ApiClient.enrolUser(f.student.id, Long.parseLong(f.courseId), 5);
        f.startStudentSession(c);
        f.view.open(f.courseId);
        System.out.println("[SAC-chain] course=" + f.courseId + " a1=" + f.a1 + " a2=" + f.a2 + " a3=" + f.a3);
    }

    @Then("Activity 3 stays restricted until both Activity 1 and Activity 2 are complete")
    public void activity3RestrictedUntilBoth() {
        ChainFx f = chain();
        f.view.open(f.courseId);
        assertTrue(f.view.isRestricted("Activity 3"),
                "Activity 3 should stay restricted before Activity 1 and 2 complete");

        f.view.markDone("Activity 1");
        f.view.open(f.courseId);
        assertTrue(f.view.isCompleted("Activity 1"), "Activity 1 should show completed after mark-as-done");
        assertFalse(f.view.isCompleted("Activity 2"), "Activity 2 must still be incomplete");
        assertTrue(f.view.isRestricted("Activity 3"),
                "Activity 3 should stay restricted after only Activity 1 is complete");

        f.view.markDone("Activity 2");
        f.view.open(f.courseId);
        assertTrue(f.view.isCompleted("Activity 2"), "Activity 2 should show completed after mark-as-done");
        for (int i = 0; i < 10 && f.view.isRestricted("Activity 3"); i++) {   // availability can lag a moment
            f.page().waitForTimeout(1_000);
            f.view.open(f.courseId);
        }
        assertFalse(f.view.isRestricted("Activity 3"),
                "Activity 3 should unlock after Activity 1 and 2 are complete");
        assertTrue(f.view.isAccessible("Activity 3"), "Activity 3 should be listed and enabled once unlocked");
    }

    // ================= helpers =================

    /** Activity 1 satisfied for Activity 2 restrict-access (course page is authoritative). */
    private boolean activity1CompleteForRestrictAccess(GradeFx f) {
        f.view.open(f.courseId);
        return !f.view.isRestricted(A2);
    }

    private void ensureStudentSubmittedActivity1(GradeFx f) {
        if (f.submitted) return;
        if (f.a1Cmid == null || f.view == null) {
            throw new SkipException("grade fixture Activity 1 not created");
        }
        f.view.open(f.courseId);
        Page sp = f.page();
        sp.navigate(Settings.BASE_URL + "/mod/cloudlabs/view.php?id=" + f.a1Cmid);
        sp.waitForTimeout(2_000);
        ChallengeControlPanel cp = new ChallengeControlPanel(sp);
        if (cp.isOnLandingBeforeStart()) {
            assertTrue(cp.clickStartAssessment(),
                    "Start Assessment not available on Activity 1 (url=" + sp.url() + ")");
        }
        assertTrue(cp.waitForConsoleUrl(120) || cp.waitForConsolePage(30),
                "assessment console did not open after Start Assessment (url=" + sp.url() + ")");
        cp.startHandsOnLabIfNeeded();
        assertTrue(cp.waitEnabled(ChallengeControlPanel.SUBMIT, 180),
                "Submit did not become enabled — lab may still be provisioning (url=" + sp.url() + ")");
        cp.submitAttempt();
        sp.waitForTimeout(5_000);
        f.submitted = true;
        f.view.open(f.courseId);
        System.out.println("[SAC-grade] student submitted Activity 1 cmid=" + f.a1Cmid);
    }

    private void gradeActivity1Submission(GradeFx f, int grade) {
        TestContext c = ctx();
        if (f.a1SectionId == null || f.a1SectionId.isBlank()) {
            f.a1SectionId = new AssessmentAdmin(c.page).sectionIdFor(Integer.parseInt(f.a1Cmid));
        }
        AssessmentAdmin admin = new AssessmentAdmin(c.page);
        assertTrue(admin.updateGradeForStudent(f.a1SectionId, f.student.id, f.student.username,
                        f.student.email, grade),
                "admin could not update grade on Manage User Submissions for " + f.student.username
                        + " (userId=" + f.student.id + ", section=" + f.a1SectionId + ", url="
                        + c.page.url() + ")");
        // Student-side course page only — do not open Activity 1/2 mod URLs (assessment console / shared sandbox).
        f.view.open(f.courseId);
        f.page().waitForTimeout(2_000);
        f.teacherGradeApplied = true;
        f.lastTeacherGrade = grade;
        System.out.println("[SAC-grade] admin set grade " + grade + " on Activity 1 for " + f.student.username);
    }

    private static void waitUntilGradedActivity2Unrestricted(GradeFx f) {
        for (int i = 0; i < 25; i++) {
            f.view.open(f.courseId);
            if (!f.view.isRestricted(A2)) return;
            f.page().waitForTimeout(2_000);
        }
    }

    /** Wait for restrict-access / gradebook to settle when Activity 2 must stay locked. */
    private static void waitUntilGradedActivity2Restricted(GradeFx f) {
        for (int i = 0; i < 15; i++) {
            f.view.open(f.courseId);
            if (f.view.isRestricted(A2)) return;
            f.page().waitForTimeout(2_000);
        }
    }

    // ================= teardown =================

    /**
     * Parallel runs: every scenario owns its fixtures — delete them now (admin page is still alive;
     * the context hook runs later). Serial runs keep the shared mark-as-done fixture until
     * {@link #tearDownFixture()}.
     */
    @After("@sac")
    public void afterScenario(Scenario scenario) {
        if (!SacSupport.parallel()) return;
        TestContext c = ctx();
        boolean failed = scenario.isFailed();
        SacSupport.release(MARK, c, "SAC", failed);
        SacSupport.release(GRADE, c, "SAC-grade", failed);
        SacSupport.release(CHAIN, c, "SAC-chain", failed);
        SacSupport.clearLabTeardownFlags();
    }

    private static <T extends SacSupport.StudentFx> void teardownHolder(SacSupport.Holder<T> holder,
                                                                         TestContext c, String label) {
        T f = holder.get();
        if (f == null) return;
        try {
            f.teardown(c, label);
        } finally {
            holder.set(null);
        }
    }

    /** Serial runs: delete whatever shared fixtures are left (parallel runs already cleaned up per scenario). */
    @AfterAll
    public static void tearDownFixture() {
        if (SacSupport.parallel()) return;
        teardownHolder(CHAIN, null, "SAC-chain");
        teardownHolder(GRADE, null, "SAC-grade");
        teardownHolder(MARK, null, "SAC");
    }
}
