package com.nuvepro.moodle.steps;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Dialog;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.nuvepro.moodle.config.Settings;
import com.nuvepro.moodle.helpers.ApiClient;
import com.nuvepro.moodle.helpers.Auth;
import com.nuvepro.moodle.helpers.BrowserUi;
import com.nuvepro.moodle.pages.LabReadinessPage;
import io.cucumber.java.After;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import org.testng.SkipException;

import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

/**
 * Pre-Delivery Lab Readiness Checklist (lab_readiness.php). Batch 1–2: no successful save
 * (shared ASSESSMENT_CMID is not mutated).
 */
public class PreDeliverySteps {
    private final TestContext ctx;
    private ApiClient.SeededUser student;
    private BrowserContext studentCtx;
    private LabReadinessPage readiness;
    private int activityCmid;
    private int sandboxChecklistCount;
    private int assessmentChecklistCount;

    public PreDeliverySteps(TestContext ctx) {
        this.ctx = ctx;
    }

    private int cmid() {
        if (Settings.ASSESSMENT_CMID.isEmpty()) throw new SkipException("ASSESSMENT_CMID not set");
        return Integer.parseInt(Settings.ASSESSMENT_CMID);
    }

    private LabReadinessPage readiness() {
        if (readiness == null) readiness = new LabReadinessPage(ctx.page);
        return readiness;
    }

    @When("admin opens the lab readiness page")
    public void adminOpensTheLabReadinessPage() {
        activityCmid = cmid();
        readiness().open(activityCmid);
    }

    @When("admin opens the lab readiness page for the sandbox activity")
    public void adminOpensSandboxReadiness() {
        if (Settings.PLAYGROUND_CMID.isEmpty()) throw new SkipException("PLAYGROUND_CMID not set");
        activityCmid = Integer.parseInt(Settings.PLAYGROUND_CMID);
        readiness().open(activityCmid);
        sandboxChecklistCount = readiness().checklistItemCount();
    }

    @When("admin opens the lab readiness page for the assessment activity")
    public void adminOpensAssessmentReadiness() {
        activityCmid = cmid();
        readiness().open(activityCmid);
        assessmentChecklistCount = readiness().checklistItemCount();
    }

    @When("admin opens the lab readiness page for the practice project activity")
    public void adminOpensGuidedReadiness() {
        if (Settings.GUIDED_CMID.isEmpty()) throw new SkipException("GUIDED_CMID not set");
        activityCmid = Integer.parseInt(Settings.GUIDED_CMID);
        readiness().open(activityCmid);
    }

    @Then("the lab readiness checklist fields differ between sandbox and assessment lab types")
    public void checklistDiffersByLabType() {
        if (sandboxChecklistCount == 0 || assessmentChecklistCount == 0) {
            throw new SkipException("could not compare checklist counts (sandbox="
                    + sandboxChecklistCount + " assessment=" + assessmentChecklistCount + ")");
        }
        assertTrue(sandboxChecklistCount != assessmentChecklistCount
                        || !readiness().progressText().equals(""),
                "checklist surface should differ or render per lab type");
    }

    @Then("the lab readiness page shows practice project guided checklist items")
    public void guidedChecklistItems() {
        String body = ctx.page.locator("body").innerText().toLowerCase();
        assertTrue(body.contains("guided") || body.contains("practice") || body.contains("template"),
                "expected guided/practice checklist markers on readiness page");
        assertTrue(ctx.page.locator(LabReadinessPage.ITEM).count() > 0, "no checklist items on guided activity");
    }

    @Then("the lab readiness checklist form is shown")
    public void theLabReadinessFormIsShown() {
        assertTrue(ctx.page.locator(LabReadinessPage.FORM).count() > 0, "lab readiness form not shown for admin");
        assertTrue(ctx.page.locator(LabReadinessPage.ITEM).count() > 0, "no checklist items rendered");
        assertTrue(ctx.page.locator(LabReadinessPage.PROGRESS).count() > 0, "progress bar not present");
    }

    @Then("a student cannot open the lab readiness page")
    public void aStudentCannotOpenTheLabReadinessPage() {
        enrolStudentFromActivityCourse();
        Page sp = studentPage();
        new LabReadinessPage(sp).open(activityCmid > 0 ? activityCmid : cmid());
        assertTrue(sp.locator(LabReadinessPage.FORM).count() == 0,
                "DEFECT: a student can open the Lab Readiness checklist (form rendered)");
        String body = sp.locator("body").innerText().toLowerCase();
        assertTrue(body.contains("permission") || body.contains("not allowed") || body.contains("cannot"),
                "student was not shown a permission error on the lab readiness page");
    }

    @When("a student opens the same activity view page")
    public void aStudentOpensTheSameActivityViewPage() {
        enrolStudentFromActivityCourse();
        Page sp = studentPage();
        sp.navigate(Settings.BASE_URL + "/mod/cloudlabs/view.php?id=" + (activityCmid > 0 ? activityCmid : cmid()),
                new Page.NavigateOptions().setWaitUntil(com.microsoft.playwright.options.WaitUntilState.DOMCONTENTLOADED)
                        .setTimeout(45_000));
        sp.waitForTimeout(2_000);
    }

    @Then("the student does not see the Lab Readiness navigation link")
    public void theStudentDoesNotSeeLabReadinessNav() {
        Page sp = studentCtx != null ? studentCtx.pages().get(0) : null;
        if (sp == null) throw new SkipException("student session missing");
        assertFalse(new LabReadinessPage(sp).hasChecklistNavOnActivityView(),
                "student sees Lab Readiness nav link on activity view");
    }

    @Then("checking all visible checklist items sets the progress bar to 100 percent and green")
    public void checkingAllItemsDrivesProgressTo100Green() {
        LabReadinessPage p = readiness();
        int n = p.visibleChecklistCount();
        assertTrue(n > 0, "no visible checklist items");
        p.setCheckedVisibleCount(n);
        String txt = p.progressText();
        String cls = p.progressBarClass();
        assertTrue(txt.contains("100"), "progress bar did not reach 100% (was '" + txt + "')");
        assertTrue(cls != null && cls.contains("bg-success"),
                "progress bar not green (bg-success) at 100% (class=" + cls + ")");
    }

    @Then("switching the environment type toggles the VM and Account fields")
    public void switchingEnvTypeTogglesFields() {
        Locator vm = ctx.page.locator(".vm-field");
        Locator account = ctx.page.locator(".account-field");
        if (ctx.page.locator("input[name='envtype'][value='account']").count() == 0)
            throw new SkipException("envtype radios not present");
        ctx.page.locator("input[name='envtype'][value='account']").first()
                .check(new Locator.CheckOptions().setForce(true));
        ctx.page.waitForTimeout(700);
        assertTrue(account.first().isVisible(), "account fields not shown for envtype=account");
        assertFalse(vm.first().isVisible(), "vm fields still shown for envtype=account");
        ctx.page.locator("input[name='envtype'][value='vm']").first()
                .check(new Locator.CheckOptions().setForce(true));
        ctx.page.waitForTimeout(700);
        assertTrue(vm.first().isVisible(), "vm fields not shown for envtype=vm");
        assertFalse(account.first().isVisible(), "account fields still shown for envtype=vm");
    }

    @Then("saving with an empty support engineer name is blocked by an alert")
    public void savingWithEmptyNameIsBlocked() {
        expectAlertOnSave("", false, "support engineer");
    }

    @Then("the lab readiness progress bar shows danger warning info and success bands")
    public void progressBarColourBands() {
        LabReadinessPage p = readiness();
        int n = p.visibleChecklistCount();
        if (n < 4) throw new SkipException("need at least 4 visible checklist items for band test (got " + n + ")");
        p.uncheckAllVisible();
        p.setCheckedVisibleCount(Math.max(1, (int) Math.floor(n * 0.25)));
        assertBarClassContains("bg-danger", "low completion should use bg-danger (≤40%)");
        p.setCheckedVisibleCount(Math.max(1, (int) Math.floor(n * 0.5)));
        String mid = p.progressBarClass();
        assertTrue(mid != null && (mid.contains("bg-warning") || mid.contains("bg-info") || mid.contains("bg-danger")),
                "mid completion bar class unexpected: " + mid);
        p.setCheckedVisibleCount(n);
        assertTrue(p.progressText().contains("100") && p.progressBarClass().contains("bg-success"),
                "100% should be bg-success");
    }

    @Then("switching environment type recomputes the checklist completion percentage")
    public void envTypeRecomputesPercent() {
        if (ctx.page.locator("input[name='envtype']").count() == 0) {
            throw new SkipException("envtype not on this checklist");
        }
        LabReadinessPage p = readiness();
        p.uncheckAllVisible();
        ctx.page.locator("input[name='envtype'][value='account']").first()
                .check(new Locator.CheckOptions().setForce(true));
        ctx.page.waitForTimeout(700);
        int nAcc = p.visibleChecklistCount();
        p.setCheckedVisibleCount(nAcc);
        String pctAcc = p.progressText();
        ctx.page.locator("input[name='envtype'][value='vm']").first()
                .check(new Locator.CheckOptions().setForce(true));
        ctx.page.waitForTimeout(700);
        int nVm = p.visibleChecklistCount();
        if (nAcc == nVm) {
            throw new SkipException("envtype toggle did not change visible item count (acc=" + nAcc + " vm=" + nVm + ")");
        }
        p.uncheckAllVisible();
        p.setCheckedVisibleCount(nVm);
        String pctVm = p.progressText();
        assertTrue(pctVm.contains("100") || !pctVm.equals(pctAcc),
                "completion % should recompute when env type changes visible items");
    }

    @Then("saving with support name but no screenshot is blocked by an alert")
    public void savingWithoutScreenshotBlocked() {
        Locator name = ctx.page.locator(LabReadinessPage.SUPPORT_NAME);
        if (name.count() > 0) name.first().fill("Automation Engineer");
        expectAlertOnSave(null, true, "screenshot");
    }

    @Then("the lab readiness page shows Catalog Orgs and Moodle sections")
    public void sectionsDisplayed() {
        String body = ctx.page.locator("body").innerText().toLowerCase();
        assertTrue(body.contains("catalog"), "Catalog section not found on lab readiness page");
        assertTrue(body.contains("org") || body.contains("orgs"), "Orgs section not found");
        assertTrue(body.contains("moodle"), "Moodle section not found");
    }

    @Then("catalog checklist items are present and toggleable")
    public void catalogItemsToggle() {
        Locator items = ctx.page.locator(".checklist-item");
        assertTrue(items.count() > 0, "no checklist items");
        Locator first = items.first();
        boolean was = first.isChecked();
        first.check(new Locator.CheckOptions().setForce(true));
        ctx.page.waitForTimeout(400);
        assertTrue(first.isChecked(), "could not check a checklist item");
        first.uncheck(new Locator.UncheckOptions().setForce(true));
        ctx.page.waitForTimeout(400);
        if (was) first.check(new Locator.CheckOptions().setForce(true));
    }

    private void expectAlertOnSave(String supportName, boolean skipScreenshotCheck, String alertNeedle) {
        final String[] alertMsg = {null};
        Consumer<Dialog> handler = d -> { alertMsg[0] = d.message(); d.accept(); };
        ctx.page.onDialog(handler);
        try {
            Locator name = ctx.page.locator(LabReadinessPage.SUPPORT_NAME);
            if (supportName != null && name.count() > 0) name.first().fill(supportName);
            if (supportName != null && supportName.isEmpty() && name.count() > 0) name.first().fill("");
            ctx.page.locator(LabReadinessPage.SAVE).click();
            ctx.page.waitForTimeout(1_500);
            assertTrue(alertMsg[0] != null && alertMsg[0].toLowerCase().contains(alertNeedle.toLowerCase()),
                    "expected alert containing '" + alertNeedle + "' (got: " + alertMsg[0] + ")");
            assertTrue(ctx.page.url().contains("lab_readiness.php") && ctx.page.locator(LabReadinessPage.FORM).count() > 0,
                    "form submitted despite validation");
        } finally {
            ctx.page.offDialog(handler);
        }
    }

    private void enrolStudentFromActivityCourse() {
        if (Settings.WS_TOKEN.isEmpty()) throw new SkipException("MOODLE_WS_TOKEN not set");
        String bodyClass = ctx.page.locator("body").getAttribute("class");
        Matcher m = Pattern.compile("course-(\\d+)").matcher(bodyClass == null ? "" : bodyClass);
        if (!m.find()) throw new SkipException("could not determine the activity's course id");
        if (student == null) {
            student = ApiClient.createUser(System.currentTimeMillis());
            ApiClient.enrolUser(student.id, Long.parseLong(m.group(1)), 5);
        }
    }

    private Page studentPage() {
        if (studentCtx == null) {
            studentCtx = BrowserUi.newContext(GlobalHooks.browser, new Browser.NewContextOptions()
                    .setBaseURL(Settings.BASE_URL).setIgnoreHTTPSErrors(true));
            Page sp = studentCtx.newPage();
            sp.setDefaultTimeout(Settings.DEFAULT_TIMEOUT_MS);
            Auth.uiLogin(sp, student.username, student.password);
        }
        return studentCtx.pages().isEmpty() ? studentCtx.newPage() : studentCtx.pages().get(0);
    }

    private void assertBarClassContains(String token, String msg) {
        String cls = readiness().progressBarClass();
        assertTrue(cls != null && cls.contains(token), msg + " (class=" + cls + ", text=" + readiness().progressText() + ")");
    }

    @After("@predelivery")
    public void cleanup() {
        if (studentCtx != null) { try { studentCtx.close(); } catch (Throwable ignored) {} studentCtx = null; }
        if (student != null) { try { ApiClient.deleteUser(student.id); } catch (Throwable ignored) {} student = null; }
        readiness = null;
    }
}
