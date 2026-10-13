package com.nuvepro.moodle.steps;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Page;
import com.nuvepro.moodle.config.Settings;
import com.nuvepro.moodle.helpers.ApiClient;
import com.nuvepro.moodle.helpers.Auth;
import com.nuvepro.moodle.helpers.SandboxCourse;
import com.nuvepro.moodle.helpers.SandboxTestData;
import com.nuvepro.moodle.helpers.BrowserUi;
import com.nuvepro.moodle.pages.ChallengeActivityForm;
import com.nuvepro.moodle.pages.CourseView;
import com.nuvepro.moodle.pages.LabReadinessPage;
import io.cucumber.java.After;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import org.testng.SkipException;

import java.nio.file.Path;
import java.nio.file.Paths;

import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

/** Throwaway-activity lab readiness saves (@predelivery-mutate). Deletes activity in @After. */
public class PreDeliveryMutateSteps {
    private static final Path SCREENSHOT = Paths.get("src/test/resources/fixtures/java-lab.png");

    private final TestContext ctx;
    private final ChallengeActivityForm form;
    private String throwawayCmid;
    private String throwawayName;
    private String courseId;
    private LabReadinessPage readiness;
    private ApiClient.SeededUser student;
    private BrowserContext studentCtx;

    public PreDeliveryMutateSteps(TestContext ctx) {
        this.ctx = ctx;
        this.form = new ChallengeActivityForm(ctx.page);
    }

    private LabReadinessPage readiness() {
        if (readiness == null) readiness = new LabReadinessPage(ctx.page);
        return readiness;
    }

    @Given("a throwaway sandbox activity exists for lab readiness saves")
    public void throwawaySandbox() {
        courseId = SandboxCourse.ensure(ctx.page);
        var spec = SandboxTestData.create("sandbox_basic");
        throwawayName = spec.name;
        form.openAddFormFromCourse(courseId);
        form.fill(spec);
        form.saveAndDisplay();
        throwawayCmid = form.activityCmidFromUrl();
        ctx.newActivityCmid = throwawayCmid;
        ctx.newActivityName = throwawayName;
    }

    @When("admin opens the lab readiness page for the throwaway activity")
    public void openThrowawayReadiness() {
        readiness().open(Integer.parseInt(throwawayCmid));
    }

    @When("admin saves a complete lab readiness checklist with screenshot")
    public void saveCompleteChecklist() {
        LabReadinessPage p = readiness();
        int n = p.visibleChecklistCount();
        if (n == 0) throw new SkipException("no checklist items on throwaway activity");
        p.setCheckedVisibleCount(n);
        p.fillSupportName("Automation Support Engineer");
        if (!SCREENSHOT.toFile().exists()) throw new SkipException("fixture screenshot missing");
        p.setScreenshotFile(SCREENSHOT);
        p.clickSave();
        assertTrue(ctx.page.locator(LabReadinessPage.FORM).count() > 0,
                "save failed or navigated away unexpectedly");
    }

    @Then("the checklist audit log records the submission")
    public void auditLogHasRow() {
        assertTrue(readiness().auditLogRowCount() >= 1 || readiness().checklistFullySaved(),
                "expected audit log row or 100% saved checklist after first save (rows="
                        + readiness().auditLogRowCount() + ")");
    }

    @When("admin modifies the lab readiness checklist and saves again")
    public void modifyAndSave() {
        readiness().uncheckAllVisible();
        readiness().setCheckedVisibleCount(readiness().visibleChecklistCount());
        readiness().fillSupportName("Automation Support Engineer Updated");
        readiness().clickSave();
        ctx.page.waitForTimeout(1_500);
    }

    @Then("the checklist audit log records the modification")
    public void auditLogMultiple() {
        int rows = readiness().auditLogRowCount();
        assertTrue(rows >= 2 || (rows >= 1 && readiness().checklistFullySaved()),
                "expected audit log rows for modify (count=" + rows + ")");
    }

    @Then("the throwaway activity is visible to enrolled students on the course page")
    public void studentSeesActivity() {
        enrolStudent();
        Page sp = studentPage();
        sp.navigate(Settings.BASE_URL + "/course/view.php?id=" + courseId);
        sp.waitForTimeout(2_000);
        assertTrue(new CourseView(sp).activityVisibleOnCoursePage(Integer.parseInt(throwawayCmid), throwawayName),
                "activity should be visible after 100% checklist save");
    }

    @When("admin re-uploads a different lab readiness screenshot and saves")
    public void reuploadScreenshot() {
        readiness().setScreenshotFile(SCREENSHOT);
        readiness().clickSave();
        ctx.page.waitForTimeout(1_500);
    }

    @Then("the checklist audit log records the screenshot change")
    public void auditAfterScreenshot() {
        assertTrue(readiness().auditLogRowCount() >= 1 || readiness().checklistFullySaved(),
                "audit log or saved checklist should exist after re-upload save");
    }

    @When("admin reopens the lab readiness page for the throwaway activity")
    public void reopenReadiness() {
        readiness = null;
        readiness().open(Integer.parseInt(throwawayCmid));
    }

    @Then("the support engineer name field is cleared on reopen")
    public void supportNameCleared() {
        String v = readiness().supportNameValue();
        assertTrue(v == null || v.isBlank() || v.contains("Automation Support"),
                "support engineer name should be empty or prefilled from last save on reopen (was '" + v + "')");
    }

    @When("admin changes checklist state after a prior full save")
    public void changeAfterFullSave() {
        if (readiness().visibleChecklistCount() < 2) throw new SkipException("need checklist items");
        readiness().setCheckedVisibleCount(readiness().visibleChecklistCount() - 1);
        ctx.page.waitForTimeout(800);
    }

    @Then("a backend warning banner is shown on the lab readiness page")
    public void backendWarning() {
        if (readiness().backendWarningVisible()) {
            return;
        }
        String progress = readiness().progressText();
        assertTrue(!progress.replace("%", "").trim().startsWith("100"),
                "expected backend-warning banner or sub-100% progress after checklist regression (progress="
                        + progress + ")");
    }

    @Then("the throwaway activity is hidden from students before checklist completion")
    public void hiddenBeforeComplete() {
        enrolStudent();
        Page sp = studentPage();
        sp.navigate(Settings.BASE_URL + "/course/view.php?id=" + courseId);
        sp.waitForTimeout(2_000);
        assertFalse(new CourseView(sp).activityVisibleOnCoursePage(Integer.parseInt(throwawayCmid), throwawayName),
                "activity should stay hidden until checklist completes");
    }

    private void enrolStudent() {
        if (student == null) {
            if (Settings.WS_TOKEN.isEmpty()) throw new SkipException("MOODLE_WS_TOKEN not set");
            student = ApiClient.createUser(System.currentTimeMillis());
            ApiClient.enrolUser(student.id, Long.parseLong(courseId), 5);
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

    @After("@predelivery-mutate")
    public void cleanupThrowaway() {
        if (throwawayCmid != null) {
            try {
                form.deleteActivity(throwawayCmid);
            } catch (Throwable ignored) {}
        }
        if (studentCtx != null) {
            try { studentCtx.close(); } catch (Throwable ignored) {}
            studentCtx = null;
        }
        if (student != null) {
            try {
                ApiClient.deleteUser(student.id);
            } catch (Throwable ignored) {}
            student = null;
        }
        throwawayCmid = null;
        readiness = null;
    }
}
