package com.nuvepro.moodle.steps;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Page;
import com.nuvepro.moodle.config.Settings;
import com.nuvepro.moodle.helpers.ApiClient;
import com.nuvepro.moodle.helpers.Auth;
import com.nuvepro.moodle.helpers.BrowserUi;
import com.nuvepro.moodle.pages.AssessmentAdmin;
import io.cucumber.java.After;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import org.testng.SkipException;

import java.util.ArrayList;
import java.util.List;

import static org.testng.Assert.assertTrue;

/**
 * Wave D — hide marks/grade/result ("Attempts Summary Columns") configuration + the role matrix that may
 * manage it. The CONFIG-form parts (which columns can be hidden; that manager/teacher can also open the
 * config) run WITHOUT a lab. The end-user/admin VISIBILITY-after-attempt parts need a completed attempt
 * and live in the provisioning waves. Manager/teacher are seeded via the WS token (roleids 1 / 3) unless
 * MANAGER_USER/TEACHER_USER are set in assessment.env.
 */
public class AssessmentGradeConfigSteps {
    private final TestContext ctx;
    private final AssessmentAdmin admin;
    private final List<BrowserContext> roleContexts = new ArrayList<>();
    private final List<ApiClient.SeededUser> seeded = new ArrayList<>();

    public AssessmentGradeConfigSteps(TestContext ctx) {
        this.ctx = ctx;
        this.admin = new AssessmentAdmin(ctx.page);
    }

    private int assessmentCmid() {
        if (Settings.ASSESSMENT_CMID.isEmpty()) throw new SkipException("ASSESSMENT_CMID not set");
        return Integer.parseInt(Settings.ASSESSMENT_CMID);
    }

    @Given("the assessment activity settings form is open as admin")
    public void theAssessmentFormIsOpenAsAdmin() {
        admin.openActivityEditForm(assessmentCmid());
    }

    @Then("the {string} attempt-summary column can be hidden")
    public void theColumnCanBeHidden(String column) {
        assertTrue(admin.summaryFieldVisible(column),
                "the '" + column + "' attempt-summary-column control is not available on the assessment form");
        admin.setSummaryField(column, false);   // no save — verify the control accepts toggling off
        assertTrue(!admin.summaryFieldChecked(column),
                "the '" + column + "' column could not be unchecked (hidden)");
    }

    @Then("the {string} attempt-summary column control is available")
    public void theColumnControlIsAvailable(String column) {
        assertTrue(admin.summaryFieldVisible(column),
                "the '" + column + "' attempt-summary-column control is not available on the assessment form");
    }

    // ---- role matrix (A59): admin + manager + teacher can open the config ----

    @Then("admin, manager and teacher can each open the attempt-summary configuration")
    public void rolesCanOpenTheConfig() {
        // admin (current session)
        admin.openActivityEditForm(assessmentCmid());
        assertTrue(admin.attemptSummaryConfigVisible(), "admin cannot see the attempt-summary configuration");

        if (Settings.WS_TOKEN.isEmpty())
            throw new SkipException("admin verified, but MOODLE_WS_TOKEN not set to seed manager/teacher for the role matrix");
        long courseId = admin.courseIdFor(assessmentCmid());
        if (courseId == 0) throw new SkipException("could not determine the assessment activity's course id for enrolment");

        // manager (roleid 1) and editing teacher (roleid 3)
        assertTrue(roleCanSeeConfig("manager", 1, courseId), "a manager cannot open/see the attempt-summary configuration");
        assertTrue(roleCanSeeConfig("teacher", 3, courseId), "a teacher cannot open/see the attempt-summary configuration");
    }

    private boolean roleCanSeeConfig(String label, int roleId, long courseId) {
        ApiClient.SeededUser u = ApiClient.createUser(System.currentTimeMillis());
        seeded.add(u);
        ApiClient.enrolUser(u.id, courseId, roleId);
        BrowserContext c = BrowserUi.newContext(GlobalHooks.browser, new Browser.NewContextOptions()
                .setBaseURL(Settings.BASE_URL).setIgnoreHTTPSErrors(true));
        roleContexts.add(c);
        Page p = c.newPage();
        p.setDefaultTimeout(Settings.DEFAULT_TIMEOUT_MS);
        Auth.uiLogin(p, u.username, u.password);
        AssessmentAdmin roleAdmin = new AssessmentAdmin(p);
        roleAdmin.openActivityEditForm(assessmentCmid());
        boolean ok = roleAdmin.attemptSummaryConfigVisible();
        System.out.println("[GradeConfig] role " + label + " sees config = " + ok);
        return ok;
    }

    // ---- A60: admin-view config actually hides the column from the manage-challenges attempt table ----

    @Then("hiding the {string} column in admin config removes it from the manage challenges table")
    public void hidingColumnRemovesFromManageChallenges(String column) {
        int cmid = assessmentCmid();
        String sid = admin.sectionIdFor(cmid);
        // apply: turn on the admin-view flag + hide the column, save
        admin.openActivityEditForm(cmid);
        admin.setSummaryField("adminConfiguration", true);
        admin.setSummaryField(column, false);
        admin.saveActivityForm();
        admin.openManageChallenges(sid);
        boolean hidden = !admin.manageChallengeColumnVisible(column.toLowerCase());
        // restore: show the column again + turn the admin-view flag off, save (leave 781 as found)
        admin.openActivityEditForm(cmid);
        admin.setSummaryField(column, true);
        admin.setSummaryField("adminConfiguration", false);
        admin.saveActivityForm();
        assertTrue(hidden, "the '" + column + "' column was not hidden from manage challenges after the admin-view config");
    }

    @After("@assessgradecfg")
    public void cleanup() {
        for (BrowserContext c : roleContexts) { try { c.close(); } catch (Throwable ignored) {} }
        roleContexts.clear();
        for (ApiClient.SeededUser u : seeded) { try { ApiClient.deleteUser(u.id); } catch (Throwable ignored) {} }
        seeded.clear();
    }
}
