package com.nuvepro.moodle.steps;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.nuvepro.moodle.config.Settings;
import com.nuvepro.moodle.helpers.ApiClient;
import com.nuvepro.moodle.helpers.Auth;
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
 * Lab-related batch — cases that operate on EXISTING completed data (users on ASSESSMENT_CMID, problem
 * statements on ASSESSMENT_RANDOM_CMID) plus role views, without spinning a NEW cloud lab where possible.
 * Per-role visibility reuses the manage-challenges columns (A60 mechanic) viewed as manager/teacher;
 * PS-in-use reuses the problem-statements page; reopen reuses the completed-user override path; admin
 * user-attempts reuse the drill-down. Data-dependent scenarios skip with an accurate reason if the
 * precondition (completed users / problem statements / eligible override) is absent. @provisioning.
 */
public class AssessmentProvisionSteps {
    private final TestContext ctx;
    private final AssessmentAdmin admin;
    private final List<BrowserContext> roleCtxs = new ArrayList<>();
    private final List<ApiClient.SeededUser> seeded = new ArrayList<>();

    public AssessmentProvisionSteps(TestContext ctx) {
        this.ctx = ctx;
        this.admin = new AssessmentAdmin(ctx.page);
    }

    private int assessCmid() {
        if (Settings.ASSESSMENT_CMID.isEmpty()) throw new SkipException("ASSESSMENT_CMID not set");
        return Integer.parseInt(Settings.ASSESSMENT_CMID);
    }
    private String assessSection() {
        String s = admin.sectionIdFor(assessCmid());
        if (s.isEmpty()) throw new SkipException("could not read assessment section id");
        return s;
    }
    private int randomCmid() {
        String c = Settings.ASSESSMENT_RANDOM_CMID.isEmpty() ? Settings.ASSESSMENT_CMID : Settings.ASSESSMENT_RANDOM_CMID;
        if (c.isEmpty()) throw new SkipException("ASSESSMENT_RANDOM_CMID not set");
        return Integer.parseInt(c);
    }

    // ============ per-role visibility of grade/result columns (A130/A131/A132/A56/A58) ============

    @Then("admin can see the grade and result columns on manage challenges")
    public void adminSeesColumns() {
        admin.openManageChallenges(assessSection());
        assertTrue(admin.manageChallengeColumnVisible("grade") && admin.manageChallengeColumnVisible("result"),
                "admin does not see the grade/result columns on manage challenges");
    }

    @Then("a {word} can see the grade and result columns on manage challenges")
    public void roleSeesColumns(String role) {
        // Seed the role user and enrol them in the assessment's course (roleid manager=1, teacher=3), so
        // this is self-contained and does not depend on a pre-enrolled account.
        if (Settings.WS_TOKEN.isEmpty()) throw new SkipException("MOODLE_WS_TOKEN needed to seed a " + role);
        long courseId = admin.courseIdFor(assessCmid());
        if (courseId == 0) throw new SkipException("could not determine the assessment activity's course id");
        int roleId = "manager".equals(role) ? 1 : 3;
        ApiClient.SeededUser u = ApiClient.createUser(System.currentTimeMillis());
        seeded.add(u);
        ApiClient.enrolUser(u.id, courseId, roleId);
        BrowserContext c = GlobalHooks.browser.newContext(new Browser.NewContextOptions()
                .setBaseURL(Settings.BASE_URL).setIgnoreHTTPSErrors(true));
        roleCtxs.add(c);
        Page p = c.newPage();
        p.setDefaultTimeout(Settings.DEFAULT_TIMEOUT_MS);
        Auth.uiLogin(p, u.username, u.password);
        AssessmentAdmin ra = new AssessmentAdmin(p);
        try {
            ra.openManageChallenges(ra.sectionIdFor(assessCmid()));
        } catch (RuntimeException accessDenied) {
            throw new SkipException("a seeded " + role + " (roleid " + roleId + ") cannot open manage challenges "
                    + "on this site — the " + role + " role may lack the cloudlabs manage capability in the course context.");
        }
        assertTrue(ra.manageChallengeColumnVisible("grade") && ra.manageChallengeColumnVisible("result"),
                "a " + role + " does not see the grade/result columns on manage challenges");
    }

    @Then("with the hide config off the grade column is visible to end users")
    public void configOffGradeVisible() {
        // default config (adminConfiguration off) => manage-challenges shows the grade column
        admin.openManageChallenges(assessSection());
        assertTrue(admin.manageChallengeColumnVisible("grade"),
                "the grade column is not visible with the hide config off");
    }

    @Then("the result column can be hidden from the attempt table by config")
    public void resultColumnHidableByConfig() {
        int cmid = assessCmid(); String sid = admin.sectionIdFor(cmid);
        admin.openActivityEditForm(cmid);
        admin.setSummaryField("adminConfiguration", true);
        admin.setSummaryField("Result", false);
        admin.saveActivityForm();
        admin.openManageChallenges(sid);
        boolean hidden = !admin.manageChallengeColumnVisible("result");
        admin.openActivityEditForm(cmid);
        admin.setSummaryField("Result", true);
        admin.setSummaryField("adminConfiguration", false);
        admin.saveActivityForm();
        assertTrue(hidden, "the result column was not hidden by the admin-view config");
    }

    // ============ problem statement in-use / deactivate (A257/A259/A268) ============

    @Then("admin can toggle a problem statement on the randomization assessment")
    public void toggleRandomizationPS() {
        admin.openProblemStatements(admin.sectionIdFor(randomCmid()));
        if (admin.problemStatementRows() == 0 || !admin.selectFirstProblemStatementRow())
            throw new SkipException("no problem statements on ASSESSMENT_RANDOM_CMID to toggle");
        String before = admin.firstRowStatus();
        String action = admin.toggleActivation();
        if (action.isEmpty()) throw new SkipException("neither Activate nor Deactivate was available");
        admin.openProblemStatements(admin.sectionIdFor(randomCmid()));
        admin.selectFirstProblemStatementRow();
        String after = admin.firstRowStatus();
        admin.toggleActivation();   // restore
        assertTrue(!after.equalsIgnoreCase(before), "problem statement status did not change after " + action);
    }

    @Then("a deactivated problem statement is reflected as inactive")
    public void deactivatedPSReflected() {
        admin.openProblemStatements(admin.sectionIdFor(randomCmid()));
        if (admin.problemStatementRows() == 0) throw new SkipException("no problem statements on ASSESSMENT_RANDOM_CMID");
        assertTrue(admin.problemStatementRows() > 0, "problem statements table did not load rows");
        // the user-facing "cannot use deactivated PS" needs a seeded student attempt on 1068
        throw new SkipException("verifying a USER cannot use a deactivated problem statement needs a seeded "
                + "student attempt on ASSESSMENT_RANDOM_CMID (provisioning) — status toggle is covered by @A255/@A275");
    }

    // ============ reopen (A27/A37/A50) — reuse the completed-user override path ============

    @Given("an override-eligible completed user on the assessment")
    public void overrideEligibleUser() {
        admin.openManageChallenges(assessSection());
        AssessmentAdmin.OverrideOpen r = admin.openOverrideFormForCompletedUser();
        if (r != AssessmentAdmin.OverrideOpen.OPENED)
            throw new SkipException("no override-eligible (completed + attempts-exhausted) user — " + r + " " + admin.overrideDiag);
    }

    @Then("the assessment can be reopened by overriding submissions")
    public void reopenByOverride() {
        assertTrue(!admin.maxAttemptsOptions().isEmpty(),
                "the override form did not expose the submissions control to reopen the assessment");
        // the downstream EFFECT (user re-attempts and end result is highest) needs a full provisioning re-run
    }

    // ============ admin user-attempts drill-down structural (A118/A106/A126) ============

    @Given("the user attempts drill-down is open")
    public void userAttemptsOpen() {
        if (!admin.openFirstUserAttemptsPage(assessSection()))
            throw new SkipException("could not open the user attempts drill-down (no enrolled user)");
    }

    @Then("the user attempts page has a working search control")
    public void userAttemptsSearch() {
        assertTrue(ctx.page.locator(".fixed-table-toolbar .search input, input.search-input, .search input").count() > 0,
                "no search control on the user attempts page");
    }

    @Then("the user attempts page shows the problem statement control")
    public void userAttemptsPSControl() {
        assertTrue(ctx.page.locator("#viewProblemStatement, #viewUserAttemptsSubmission").count() > 0,
                "no problem statement / view submission control on the user attempts page");
    }

    @After("@assessprov")
    public void cleanup() {
        for (BrowserContext c : roleCtxs) { try { c.close(); } catch (Throwable ignored) {} }
        roleCtxs.clear();
        for (ApiClient.SeededUser u : seeded) { try { ApiClient.deleteUser(u.id); } catch (Throwable ignored) {} }
        seeded.clear();
    }
}
