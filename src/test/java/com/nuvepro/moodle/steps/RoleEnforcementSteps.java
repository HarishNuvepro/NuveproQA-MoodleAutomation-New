package com.nuvepro.moodle.steps;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.nuvepro.moodle.config.Settings;
import com.nuvepro.moodle.helpers.ApiClient;
import com.nuvepro.moodle.helpers.Auth;
import com.nuvepro.moodle.helpers.PlaygroundAnchor;
import com.nuvepro.moodle.helpers.BrowserUi;
import com.nuvepro.moodle.pages.CustomRole;
import com.nuvepro.moodle.pages.ManageLabs;
import com.nuvepro.moodle.pages.RolePermissions;
import io.cucumber.java.After;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import org.testng.SkipException;

import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

/** Runtime enforcement of cloudlabs capabilities (CGAP-R-1/2/7). */
public class RoleEnforcementSteps {
    private static final int EDITING_TEACHER = 3;
    private static final int STUDENT = 5;

    private final TestContext ctx;
    private ApiClient.SeededUser student;
    private ApiClient.SeededUser teacher;
    private ApiClient.SeededUser customUser;
    private BrowserContext altCtx;
    private Page altPage;
    private int customRoleId;
    private int sectionId;
    private final CustomRole customRole;
    private final RolePermissions rolePerms;

    public RoleEnforcementSteps(TestContext ctx) {
        this.ctx = ctx;
        this.customRole = new CustomRole(ctx.page);
        this.rolePerms = new RolePermissions(ctx.page);
    }

    private PlaygroundAnchor sandboxAnchor() {
        if (Settings.PLAYGROUND_CMID.isEmpty()) throw new SkipException("PLAYGROUND_CMID not set");
        PlaygroundAnchor anchor = PlaygroundAnchor.resolve(ctx.page);
        sectionId = anchor.sectionId;
        return anchor;
    }

    private Page altSession(ApiClient.SeededUser user) {
        altCtx = BrowserUi.newContext(GlobalHooks.browser, new Browser.NewContextOptions()
                .setBaseURL(Settings.BASE_URL).setIgnoreHTTPSErrors(true));
        altPage = altCtx.newPage();
        altPage.setDefaultTimeout(Settings.DEFAULT_TIMEOUT_MS);
        Auth.uiLogin(altPage, user.username, user.password);
        return altPage;
    }

    @Given("a student enrolled in the sandbox course")
    public void aStudentEnrolledInSandbox() {
        if (Settings.WS_TOKEN.isEmpty()) throw new SkipException("MOODLE_WS_TOKEN not set");
        student = ApiClient.createUser(System.currentTimeMillis());
        PlaygroundAnchor anchor = sandboxAnchor();
        ApiClient.enrolUser(student.id, anchor.courseId, STUDENT);
    }

    @Given("an editing teacher enrolled in the sandbox course")
    public void anEditingTeacherEnrolled() {
        if (Settings.WS_TOKEN.isEmpty()) throw new SkipException("MOODLE_WS_TOKEN not set");
        teacher = ApiClient.createUser(System.currentTimeMillis() + 1);
        PlaygroundAnchor anchor = sandboxAnchor();
        ApiClient.enrolUser(teacher.id, anchor.courseId, EDITING_TEACHER);
    }

    private void openManageLabsFromPlayground(Page p) {
        if (sectionId <= 0) {
            throw new SkipException("sandbox section id not resolved — check PLAYGROUND_CMID");
        }
        p.navigate(Settings.BASE_URL + "/mod/cloudlabs/managelabs.php?sectionid=" + sectionId,
                new Page.NavigateOptions()
                        .setWaitUntil(com.microsoft.playwright.options.WaitUntilState.DOMCONTENTLOADED)
                        .setTimeout(45_000));
        p.waitForTimeout(2_500);
    }

    @When("the student opens Manage User Labs for the sandbox")
    public void studentOpensManageLabs() {
        Page p = altSession(student);
        openManageLabsFromPlayground(p);
    }

    @When("the editing teacher opens Manage User Labs for the sandbox")
    public void teacherOpensManageLabs() {
        Page p = altSession(teacher);
        openManageLabsFromPlayground(p);
    }

    @Then("lab create actions are not available to the student")
    public void createNotAvailableToStudent() {
        assertTrue(altPage != null, "student session missing");
        String body = altPage.locator("body").innerText().toLowerCase();
        if (body.contains("access denied") || body.contains("not allowed") || body.contains("permission")) {
            return;
        }
        if (altPage.url().toLowerCase().contains("login")) {
            org.testng.Assert.fail("student was redirected to login instead of Manage User Labs");
        }
        assertFalse(createActionVisible(altPage),
                "student can see Manage Labs create controls (capability not enforced)");
    }

    @Then("lab create actions are available to the editing teacher")
    public void createAvailableToTeacher() {
        assertTrue(altPage != null, "teacher session missing");
        assertTrue(manageLabsAdminPageLoaded(altPage),
                "editing teacher was blocked from Manage User Labs (url=" + altPage.url() + ")");
        assertTrue(createActionVisible(altPage) || manageLabsAdminChromeVisible(altPage),
                "editing teacher cannot see Manage Labs create/admin chrome");
    }

    private boolean manageLabsAdminPageLoaded(Page p) {
        if (p.url().toLowerCase().contains("login")) return false;
        String body = p.locator("body").innerText().toLowerCase();
        if (body.contains("access denied") || body.contains("not allowed") || body.contains("permission")) {
            return false;
        }
        return p.locator("#np-ap-manage-lab-table, #labnotcreatedusers, #toolbarnotcreated, "
                + ManageLabs.NOTCREATED_TAB).count() > 0;
    }

    private boolean manageLabsAdminChromeVisible(Page p) {
        return p.locator(ManageLabs.ACTIONS_BTN).count() > 0
                || p.locator(ManageLabs.NOTCREATED_TAB).count() > 0
                || p.locator("#toolbarnotcreated").count() > 0;
    }

    private boolean createActionVisible(Page p) {
        Locator notCreatedTab = p.locator(ManageLabs.NOTCREATED_TAB);
        if (notCreatedTab.count() > 0) {
            try {
                notCreatedTab.first().click();
                p.waitForTimeout(1_500);
            } catch (Throwable ignored) {}
        }
        Locator notCreatedCreate = p.locator(ManageLabs.NOTCREATED_CREATE);
        if (notCreatedCreate.count() > 0 && notCreatedCreate.first().isVisible()) {
            return true;
        }
        Locator create = p.locator(ManageLabs.CREATE);
        return create.count() > 0 && create.first().isVisible();
    }

    @When("admin creates a custom role with Manage User Labs enabled")
    public void adminCreatesCustomRoleWithManageLabs() {
        long s = System.currentTimeMillis();
        String name = "AutoEnf" + s;
        String id = customRole.createRole("editingteacher", "autoenf" + s, name);
        customRoleId = Integer.parseInt(id);
        rolePerms.openRoleView(customRoleId);
        // ensure Manage User Labs is allowed (inherits from editing teacher archetype)
        org.testng.Assert.assertEquals(rolePerms.permissionOf("mod/cloudlabs:activityuserlabs"), "allow",
                "custom role missing Manage User Labs capability");
    }

    @When("a user is enrolled with that custom role in the sandbox course")
    public void enrollUserWithCustomRole() {
        if (Settings.WS_TOKEN.isEmpty()) throw new SkipException("MOODLE_WS_TOKEN not set");
        customUser = ApiClient.createUser(System.currentTimeMillis() + 2);
        PlaygroundAnchor anchor = sandboxAnchor();
        ApiClient.enrolUser(customUser.id, anchor.courseId, customRoleId);
    }

    @When("that custom-role user opens Manage User Labs for the sandbox")
    public void customUserOpensManageLabs() {
        Page p = altSession(customUser);
        openManageLabsFromPlayground(p);
    }

    @After("@roleenforce")
    public void cleanupRoleEnforcement() {
        if (altCtx != null) {
            try { altCtx.close(); } catch (Throwable ignored) {}
            altCtx = null;
            altPage = null;
        }
        for (ApiClient.SeededUser u : new ApiClient.SeededUser[]{student, teacher, customUser}) {
            if (u != null) {
                try { ApiClient.deleteUser(u.id); } catch (Throwable ignored) {}
            }
        }
        student = teacher = customUser = null;
        if (customRoleId > 0) {
            try { customRole.deleteRole(String.valueOf(customRoleId)); } catch (Throwable ignored) {}
            customRoleId = 0;
        }
    }
}
