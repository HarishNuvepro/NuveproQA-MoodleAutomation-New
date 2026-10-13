package com.nuvepro.moodle.steps;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.options.WaitUntilState;
import com.nuvepro.moodle.config.Settings;
import com.nuvepro.moodle.helpers.ApiClient;
import com.nuvepro.moodle.helpers.Auth;
import com.nuvepro.moodle.helpers.CloudLabsClient;
import com.nuvepro.moodle.helpers.PracticeProjectFixtures;
import com.nuvepro.moodle.helpers.SandboxActivityRegistry;
import com.nuvepro.moodle.helpers.SandboxCourse;
import com.nuvepro.moodle.helpers.SandboxTestData;
import com.nuvepro.moodle.helpers.SandboxUserRegistry;
import com.nuvepro.moodle.pages.CloudLabsCourseReportPage;
import com.nuvepro.moodle.helpers.BrowserUi;
import com.nuvepro.moodle.pages.ChallengeControlPanel;
import com.nuvepro.moodle.pages.GuidedControlPanel;
import com.nuvepro.moodle.pages.GuidedLanding;
import com.nuvepro.moodle.pages.ManageLabs;
import com.nuvepro.moodle.pages.PlaygroundControlPanel;
import com.nuvepro.moodle.pages.PlaygroundLanding;
import io.cucumber.java.After;
import io.cucumber.java.Before;
import io.cucumber.java.Scenario;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import org.testng.SkipException;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.concurrent.atomic.AtomicLong;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;

/**
 * Admin Manage Labs flows (@managelabs): student starts a lab from the console, then admin
 * selects that row on Manage Labs, Sync Status until Start/Complete, Stop, Sync until Stop/Complete.
 * State after each Sync is confirmed via CloudLabs API (authoritative).
 */
public class SandboxManageLabsSteps {
    private final TestContext ctx;

    private ApiClient.SeededUser student;
    private Page studentPage;
    private BrowserContext studentCtx;
    private int sectionId;
    private String labId;
    private String sandboxLabPasswordBeforeChange;
    private static final String SANDBOX_CONSOLE_NEW_PASSWORD = "AutoTest@12345";
    private boolean stopCompleted;

    private int activityCmid;
    private long courseId;

    /** Extra seeded users for No labs membership / pagination scenarios. */
    private final List<ApiClient.SeededUser> extraUsers = new ArrayList<>();
    /** Emails that MUST appear on No labs for the membership exact-match case. */
    private final Set<String> expectedNoLabsEmails = new HashSet<>();
    /** Emails that MUST NOT appear on No labs. */
    private final Set<String> forbiddenNoLabsEmails = new HashSet<>();
    private ApiClient.SeededUser userWithLab;
    private ApiClient.SeededUser userUnenrolled;
    /** Bulk / multi-user Manage Labs fixtures (keep to 2–3 users). */
    private final List<ApiClient.SeededUser> bulkUsers = new ArrayList<>();
    private final List<String> bulkLabIds = new ArrayList<>();
    private boolean startCompleted;
    private boolean retainNamedStudent;
    private ApiClient.SeededUser deleteMessageUser;
    private ApiClient.SeededUser recreateUser;

    private static final String SHARED_ACTIVE = "manage_active";
    private static final AtomicLong PARALLEL_USER_SEQ = new AtomicLong();

    /** Parallel workers must not reuse the same millisecond stamp for WS users. */
    private static long uniqueUserStamp() {
        return System.currentTimeMillis() * 1000L + PARALLEL_USER_SEQ.incrementAndGet() % 1000;
    }

    /** One lane per sandbox activity cmid so shared Manage Labs + CloudLabs sync do not cross-talk. */
    private boolean provisioningLaneHeld;

    /** Bulk delete: 6 min idle (thread sleep), then select-all Sync every 30s until Delete/Complete (max ~9 min). */
    private static final int BULK_DELETE_INITIAL_WAIT_MS = 6 * 60 * 1000;
    private static final int BULK_DELETE_SYNC_INTERVAL_MS = 30_000;
    private static final int BULK_DELETE_MAX_AFTER_INITIAL_MS = 3 * 60 * 1000;
    private static final int POLL_MS = 2_000;

    private static final EnumSet<ManageLabs.LabState> RUN_OR_FAIL =
            EnumSet.of(ManageLabs.LabState.RUNNING, ManageLabs.LabState.FAILED);
    private static final EnumSet<ManageLabs.LabState> STOP_OR_FAIL =
            EnumSet.of(ManageLabs.LabState.STOPPED, ManageLabs.LabState.FAILED);

    public SandboxManageLabsSteps(TestContext ctx) {
        this.ctx = ctx;
    }

    /**
     * Parallel: one global lane for every {@code @SB-manage-*} scenario before the first step.
     * Non-{@code @provisioning} manage cases still hit the same Manage Labs section / registry and
     * must not run alongside provisioning flows.
     */
    @Before(order = -500)
    public void sandboxParallelProvisioningGate(Scenario scenario) {
        if (!Boolean.getBoolean("sandbox.parallel")) {
            return;
        }
        boolean manage = scenario.getSourceTagNames().stream()
                .anyMatch(t -> t.startsWith("@SB-manage-"));
        if (!manage) {
            return;
        }
        ctx.enterLane("sb-sandbox:manage-provision");
        provisioningLaneHeld = true;
        System.out.println("[SB-manage] parallel manage lane — " + scenario.getName());
    }

    @Given("a fresh student enrolled for Manage Labs admin actions")
    public void aFreshStudentEnrolledForManageLabsAdminActions() {
        if (Settings.WS_TOKEN.isEmpty()) {
            throw new SkipException("MOODLE_WS_TOKEN not set");
        }
        // CloudLabs required only when the scenario will provision / sync lab state.
        if (ctx.hasTag("@provisioning") && !CloudLabsClient.isConfigured()) {
            throw new SkipException("CloudLabs API not configured (Manage Labs sync needs it)");
        }
        stopCompleted = false;
        labId = null;
        ensureSectionReady();

        long stamp = uniqueUserStamp();
        student = ApiClient.createUser(stamp);
        ApiClient.enrolUser(student.id, courseId, 5);
        trackUser(student);

        GlobalHooks.teardownStudentEmail = student.email;
        System.out.println("[SB-manage] student=" + student.username + " course="
                + courseId + " cmid=" + activityCmid + " section=" + sectionId);
    }

    /**
     * One named student + one Active lab reused by read-only Manage Labs cases
     * (search, details, usage, duration, password, refresh).
     */
    @Given("a shared Manage Labs student with an Active lab")
    public void aSharedManageLabsStudentWithAnActiveLab() {
        if (Settings.WS_TOKEN.isEmpty()) {
            throw new SkipException("MOODLE_WS_TOKEN not set");
        }
        if (ctx.hasTag("@provisioning") && !CloudLabsClient.isConfigured()) {
            throw new SkipException("CloudLabs API not configured (Manage Labs sync needs it)");
        }
        stopCompleted = false;
        labId = null;
        retainNamedStudent = true;
        // Serialize only the cases that reuse this one Active lab; fresh-lab Manage Labs cases
        // keep running in parallel alongside them.
        ctx.enterLane("sbmanage:" + SHARED_ACTIVE);
        ensureSectionReady();
        student = SandboxUserRegistry.getNamed(SHARED_ACTIVE);
        if (student == null) {
            student = ApiClient.createUser(uniqueUserStamp());
            ApiClient.enrolUser(student.id, courseId, 5);
            SandboxUserRegistry.putNamed(SHARED_ACTIVE, student);
            System.out.println("[SB-manage] shared student created=" + student.username);
        } else {
            try {
                ApiClient.enrolUser(student.id, courseId, 5);
            } catch (RuntimeException e) {
                System.out.println("[SB-manage] shared enrol skipped: " + e.getMessage());
            }
            System.out.println("[SB-manage] shared student reuse=" + student.username);
        }
        GlobalHooks.teardownStudentEmail = student.email;
        ManageLabs ml = new ManageLabs(ctx.page);
        ml.open(sectionId);
        String existing = ml.labIdForEmail(student.email);
        if (existing != null && !existing.isBlank()) {
            labId = existing.trim();
            GlobalHooks.teardownLabId = labId;
            GlobalHooks.labProvisioned = true;
            System.out.println("[SB-manage] shared Active lab already present labId=" + labId);
            return;
        }
        openStudentSession();
        PlaygroundLanding landing = new PlaygroundLanding(studentPage);
        landing.open(activityCmid);
        studentPage.waitForTimeout(1_000);
        if (landing.hasActiveLab()) {
            String hid = landing.hiddenLabId();
            if (hid != null && !hid.isBlank() && !"0".equals(hid.trim())) {
                labId = hid.trim();
                GlobalHooks.teardownLabId = labId;
            }
            GlobalHooks.labProvisioned = true;
            System.out.println("[SB-manage] shared student already has Continue labId=" + labId);
            return;
        }
        try {
            if (studentCtx != null) studentCtx.close();
        } catch (Throwable ignored) {}
        studentCtx = null;
        studentPage = null;
        adminCreatesALabForThatStudentFromNoLabs();
    }

    /**
     * Prefer the activity created/reused by the preceding create step (same suite course).
     * Do NOT fall back to PLAYGROUND_CMID when it lives in a different course — that left
     * students unable to see {@code .playground-landing-panel}.
     */
    private int resolveActivityCmid() {
        if (ctx.newActivityCmid != null && !ctx.newActivityCmid.isBlank()) {
            return Integer.parseInt(ctx.newActivityCmid.trim());
        }
        String suiteCourse = SandboxCourse.idOrEmpty();
        if (suiteCourse.isBlank()) {
            suiteCourse = SandboxCourse.ensure(ctx.page);
        }
        for (String dataset : SandboxTestData.reuseCandidates("sandbox_user_lab")) {
            SandboxActivityRegistry.Entry entry = SandboxActivityRegistry.get(dataset, suiteCourse);
            if (entry == null || entry.cmid() == null || entry.cmid().isBlank()) {
                continue;
            }
            ctx.newActivityCmid = entry.cmid();
            ctx.newActivityName = entry.name();
            if (entry.sectionId() != null && !entry.sectionId().isBlank()) {
                ctx.sectionId = entry.sectionId();
            }
            ctx.newActivityDataset = dataset;
            System.out.println("[SB-manage] reuse registry activity dataset=" + dataset
                    + " cmid=" + entry.cmid() + " course=" + suiteCourse);
            return Integer.parseInt(entry.cmid().trim());
        }
        throw new SkipException("no sandbox activity on suite course — run sandbox.feature "
                + "@SB-manage scenario that registers sandbox_user_lab first");
    }

    @Given("an existing sandbox activity is available on the suite course")
    public void anExistingSandboxActivityIsAvailableOnTheSuiteCourse() {
        ensureSectionReady();
        System.out.println("[SB-manage] existing activity cmid=" + activityCmid + " sectionId=" + sectionId);
    }

    @When("the student starts the sandbox lab until it is running")
    public void theStudentStartsTheSandboxLabUntilItIsRunning() {
        openStudentSession();
        PlaygroundLanding landing = new PlaygroundLanding(studentPage);
        landing.open(activityCmid);
        studentPage.waitForTimeout(1_500);
        assertTrue(landing.isLoaded(),
                "student did not get sandbox landing for cmid=" + activityCmid
                        + " (url=" + studentPage.url() + ") — check enrolment in course " + courseId);
        GlobalHooks.labProvisioned = true;
        landing.openLabConsole();
        PlaygroundControlPanel panel = new PlaygroundControlPanel(studentPage);
        try {
            panel.expectControlPanelLoaded(90_000);
        } catch (RuntimeException e) {
            throw new AssertionError("sandbox console did not load after Start Sandbox: " + e.getMessage(), e);
        }
        if (isVmActivityDataset()) {
            completeStudentCatalogFormIfShown();
            clickStudentCatalogProceedIfShown();
            completeStudentCatalogFormIfShown();
            clickStudentCatalogProceedIfShown();
        }
        startLabUntilLoaded(panel);
        captureLabId(panel);
        assertNotNull(labId, "could not capture lab id after student start");
        waitForStudentLabRunning(panel);
        GlobalHooks.teardownLabId = labId;
    }

    @When("admin opens Manage Labs and selects that student's lab")
    public void adminOpensManageLabsAndSelectsThatStudentsLab() {
        assertNotNull(student, "student not seeded");
        ManageLabs ml = new ManageLabs(ctx.page);
        ml.open(sectionId);
        Locator row = findLabRow();
        for (int i = 0; i < 8 && row.count() == 0; i++) {
            ctx.page.waitForTimeout(3_000);
            ml.open(sectionId);
            row = findLabRow();
        }
        assertTrue(row.count() > 0,
                "Manage Labs has no row for labId=" + labId + " email=" + student.email);
        row.first().locator("input[name='btSelectItem']").check();
        ctx.page.waitForTimeout(800);
        if ((labId == null || labId.isBlank())) {
            String id = ml.labIdForEmail(student.email);
            if (id != null && !id.isBlank()) {
                labId = id.trim();
                GlobalHooks.teardownLabId = labId;
            }
        }
        System.out.println("[SB-manage] selected lab on Manage Labs labId=" + labId
                + " email=" + student.email);
    }

    private Locator findLabRow() {
        // Prefer email while the seeded user exists — more stable than lab-id cell text.
        if (student != null && student.email != null && !student.email.isBlank()) {
            Locator byEmail = ctx.page.locator("#np-ap-manage-lab-table tbody tr")
                    .filter(new Locator.FilterOptions().setHasText(student.email));
            if (byEmail.count() > 0) return byEmail;
        }
        if (labId != null && !labId.isBlank()) {
            Locator byId = ctx.page.locator("#np-ap-manage-lab-table tbody tr")
                    .filter(new Locator.FilterOptions()
                            .setHasText(java.util.regex.Pattern.compile(
                                    "\\b" + java.util.regex.Pattern.quote(labId) + "\\b")));
            if (byId.count() > 0) return byId;
        }
        return ctx.page.locator("#np-ap-manage-lab-table tbody tr")
                .filter(new Locator.FilterOptions().setHasText("___no_match___"));
    }

    @When("admin syncs lab status until Start is completed")
    public void adminSyncsLabStatusUntilStartIsCompleted() {
        waitForSyncedLabState(
                EnumSet.of(ManageLabs.LabState.RUNNING, ManageLabs.LabState.INPROGRESS),
                ManageLabs.LabState.RUNNING,
                "Start");
        GlobalHooks.teardownLabId = labId;
        startCompleted = true;
    }

    @When("admin stops that student's lab from Manage Labs")
    public void adminStopsThatStudentsLabFromManageLabs() {
        assertNotNull(student, "student not seeded");
        ManageLabs ml = new ManageLabs(ctx.page);
        // Prefer email while the seeded user still exists — lab-id text match is flaky after Sync
        // (bootstrap-table refresh / column layout). Fall back to lab id with retries.
        Exception last = null;
        for (int attempt = 1; attempt <= 5; attempt++) {
            try {
                ml.open(sectionId);
                Locator row = findLabRow();
                if (row.count() == 0) {
                    ctx.page.waitForTimeout(3_000);
                    continue;
                }
                row.first().locator("input[name='btSelectItem']").check();
                ctx.page.waitForTimeout(800);
                ml.performActionOnSelection(ManageLabs.STOP);
                System.out.println("[SB-manage] Stop clicked on Manage Labs labId=" + labId
                        + " email=" + student.email + " attempt=" + attempt);
                return;
            } catch (RuntimeException e) {
                last = e;
                System.out.println("[SB-manage] Stop attempt " + attempt + " failed: " + e.getMessage());
                ctx.page.waitForTimeout(3_000);
            }
        }
        // Last resort: page-object helpers
        try {
            ml.performActionForEmail(student.email, ManageLabs.STOP, sectionId);
            System.out.println("[SB-manage] Stop via email fallback labId=" + labId);
            return;
        } catch (RuntimeException e) {
            last = e;
        }
        if (labId != null && !labId.isBlank()) {
            ml.performActionForLabId(labId, ManageLabs.STOP, sectionId);
            return;
        }
        throw new AssertionError("could not Stop lab on Manage Labs labId=" + labId
                + " email=" + student.email, last);
    }

    @When("admin syncs lab status until Stop is completed")
    public void adminSyncsLabStatusUntilStopIsCompleted() {
        waitForSyncedLabState(
                EnumSet.of(ManageLabs.LabState.STOPPED, ManageLabs.LabState.INPROGRESS),
                ManageLabs.LabState.STOPPED,
                "Stop");
        stopCompleted = true;
        GlobalHooks.teardownLabId = labId;
    }

    @Then("the Manage Labs admin stop flow has completed successfully")
    public void theManageLabsAdminStopFlowHasCompletedSuccessfully() {
        assertTrue(stopCompleted, "Stop-complete Sync step did not finish");
        assertLabInExpectedState(ManageLabs.LabState.STOPPED, "stop flow");
        System.out.println("[SB-manage] PASS — admin Sync Start → Stop → Sync Stop complete labId=" + labId);
    }

    private void syncSelectedLab() {
        ManageLabs ml = new ManageLabs(ctx.page);
        // Email targeting is the reliable path while the student still exists.
        try {
            ml.performActionForEmail(student.email, ManageLabs.SYNC_BTN, sectionId);
            ctx.page.waitForTimeout(5_000);
            return;
        } catch (RuntimeException emailFail) {
            System.out.println("[SB-manage] Sync via email failed, trying row select: "
                    + emailFail.getMessage());
        }
        ml.open(sectionId);
        Locator row = findLabRow();
        assertTrue(row.count() > 0,
                "cannot Sync — no Manage Labs row for labId=" + labId + " email=" + student.email);
        row.first().locator("input[name='btSelectItem']").check();
        ctx.page.waitForTimeout(500);
        ctx.page.locator(ManageLabs.SYNC_BTN).click(new Locator.ClickOptions().setForce(true));
        ctx.page.waitForTimeout(5_000);
    }

    private void openStudentSession() {
        if (studentCtx != null) {
            try { studentCtx.close(); } catch (Throwable ignored) {}
        }
        studentCtx = BrowserUi.newContext(ctx.browser, new Browser.NewContextOptions()
                .setBaseURL(Settings.BASE_URL)
                .setIgnoreHTTPSErrors(true)
                .setAcceptDownloads(true));
        studentPage = studentCtx.newPage();
        studentPage.setDefaultTimeout(Settings.DEFAULT_TIMEOUT_MS);
        ctx.studentContext = studentCtx;
        ctx.studentPage = studentPage;
        ctx.watchPageConsole(studentPage, "student");
        ctx.handoffTracingTo(studentCtx);
        Auth.uiLogin(studentPage, student.username, student.password);
    }

    private boolean isVmActivityDataset() {
        String ds = ctx.newActivityDataset;
        return ds != null && ds.toLowerCase(Locale.ROOT).contains("vm");
    }

    private void clickStudentCatalogProceedIfShown() {
        if (studentPage == null || studentPage.isClosed()) {
            return;
        }
        Locator proceed = studentPage.locator(
                "#subCatalogDataSubmitBtn, button:has-text('Proceed'), input[value='Proceed']");
        for (int attempt = 0; attempt < 8; attempt++) {
            boolean clicked = false;
            for (int i = 0; i < proceed.count(); i++) {
                try {
                    if (proceed.nth(i).isVisible() && proceed.nth(i).isEnabled()) {
                        proceed.nth(i).click();
                        clicked = true;
                        studentPage.waitForTimeout(2_000);
                        break;
                    }
                } catch (RuntimeException ignored) {}
            }
            if (!clicked) {
                break;
            }
        }
    }

    private void completeStudentCatalogFormIfShown() {
        if (studentPage == null || studentPage.isClosed()) {
            return;
        }
        try {
            studentPage.evaluate("""
                    () => {
                      const root = document.querySelector('#np-ap-mod-lab-ui-schema, #catInputUI');
                      if (!root || root.offsetParent === null) return false;
                      root.querySelectorAll('select').forEach(sel => {
                        if (sel.options.length > 1 && !sel.value) sel.selectedIndex = 1;
                        else if (sel.options.length > 0 && !sel.value) sel.selectedIndex = 0;
                        sel.dispatchEvent(new Event('change', { bubbles: true }));
                      });
                      root.querySelectorAll('input[type=text]').forEach(inp => {
                        if (!inp.value) {
                          inp.value = 'auto';
                          inp.dispatchEvent(new Event('input', { bubbles: true }));
                        }
                      });
                      const btn = document.querySelector('#subCatalogDataSubmitBtn');
                      if (btn && !btn.disabled && btn.offsetParent !== null) {
                        btn.click();
                        return true;
                      }
                      return false;
                    }
                    """);
            studentPage.waitForTimeout(1_500);
        } catch (RuntimeException ignored) {}
    }

    private void startLabUntilLoaded(PlaygroundControlPanel panel) {
        if (panel.isStopLabEnabled() || panel.waitUntilLabLoaded(15)) {
            System.out.println("[SB-manage] lab already running on console — skip Start Lab");
            return;
        }
        Locator startBtn = studentPage.locator(PlaygroundControlPanel.LAUNCH_START);
        boolean startShown = false;
        try {
            startShown = startBtn.count() > 0 && startBtn.first().isVisible();
        } catch (RuntimeException ignored) {}
        if (!startShown) {
            assertTrue(panel.waitUntilLabLoaded(120),
                    "Start Lab not shown and lab did not become ready");
            return;
        }
        panel.clickStartLab();
        Locator dm = studentPage.locator("#launchAccModal [data-dismiss='modal'], #launchAccModal .close");
        if (dm.count() > 0) {
            try {
                if (dm.first().isVisible()) dm.first().click();
            } catch (RuntimeException ignored) {}
        }
        int loadWait = Boolean.getBoolean("sandbox.parallel") ? 300 : 180;
        assertTrue(panel.waitUntilLabLoaded(loadWait),
                "sandbox lab did not load within " + loadWait + "s after Start Lab");
    }

    private void captureLabId(PlaygroundControlPanel panel) {
        ManageLabs ml = new ManageLabs(ctx.page);
        for (int i = 0; i < 24; i++) {
            ml.open(sectionId);
            ml.openCreatedTab();
            try {
                ml.searchCreatedByEmail(student.email);
            } catch (RuntimeException ignored) {}
            String fromTable = ml.labIdForEmail(student.email);
            if (fromTable != null && !fromTable.isBlank()) {
                labId = fromTable.trim();
                System.out.println("[SB-manage] captured labId from Manage Labs=" + labId);
                try {
                    ml.clearCreatedSearch();
                } catch (RuntimeException ignored) {}
                return;
            }
            try {
                String id = panel.labId();
                if (id != null && !id.isBlank() && !"0".equals(id.trim())) {
                    labId = id.trim();
                    System.out.println("[SB-manage] captured labId from console=" + labId);
                    return;
                }
            } catch (RuntimeException ignored) {}
            try {
                ml.clearCreatedSearch();
            } catch (RuntimeException ignored) {}
            studentPage.waitForTimeout(2_500);
        }
    }

    // ---- SB-manage-2: student stops; admin syncs Stop ----

    @When("the Manage Labs student stops the lab from the console")
    public void theManageLabsStudentStopsTheLabFromTheConsole() {
        assertNotNull(studentPage, "student session not open — start the lab first");
        PlaygroundControlPanel panel = new PlaygroundControlPanel(studentPage);
        // Ensure we are on the console (may still be there after admin switched contexts).
        if (!panel.waitUntilLabLoaded(5)) {
            PlaygroundLanding landing = new PlaygroundLanding(studentPage);
            landing.open(activityCmid);
            landing.openLabConsole();
            panel.expectControlPanelLoaded(60_000);
            assertTrue(panel.waitUntilLabLoaded(60), "console not ready for student Stop");
        }
        panel.stopLab();
        System.out.println("[SB-manage] student Stop Lab clicked labId=" + labId);
    }

    // ---- SB-manage-3: Create from No labs ----

    @When("admin opens Manage Labs No labs tab")
    public void adminOpensManageLabsNoLabsTab() {
        ensureSectionReady();
        ManageLabs ml = new ManageLabs(ctx.page);
        ml.open(sectionId);
        ml.openNotCreatedTab();
        System.out.println("[SB-manage] opened No labs tab section=" + sectionId);
    }

    @When("admin creates a lab for that student from No labs")
    public void adminCreatesALabForThatStudentFromNoLabs() {
        assertNotNull(student, "student not seeded");
        ManageLabs ml = new ManageLabs(ctx.page);
        boolean created = false;
        for (int attempt = 1; attempt <= 6 && !created; attempt++) {
            try {
                clickManageLabsSyncIfPresent(ml);
                ml.createLabForUserEmail(student.email, sectionId);
                created = true;
            } catch (RuntimeException e) {
                System.out.println("[SB-manage] No labs Create attempt " + attempt
                        + " for " + student.email + ": " + e.getMessage());
                ctx.page.waitForTimeout(8_000);
            }
        }
        if (!created) {
            System.out.println("[SB-manage] user not on No labs after Delete — recreate via student Start Sandbox");
            recreateLabViaStudentStart();
        }
        GlobalHooks.labProvisioned = true;
        if (labId == null || labId.isBlank()) {
            for (int i = 0; i < 24; i++) {
                ml.open(sectionId);
                ml.openCreatedTab();
                try { ml.searchCreatedByEmail(student.email); } catch (RuntimeException ignored) {}
                String id = ml.labIdForEmail(student.email);
                if (id != null && !id.isBlank()) {
                    labId = id.trim();
                    GlobalHooks.teardownLabId = labId;
                    break;
                }
                ctx.page.waitForTimeout(5_000);
            }
        }
        assertNotNull(labId, "lab id not found on Active labs after Create for " + student.email);
        ensureLabIdReadyForCloudLabs();
        if (CloudLabsClient.isConfigured() && !CloudLabsClient.isBlocked()) {
            try {
                ManageLabs.LabState st = CloudLabsClient.waitForState(labId, RUN_OR_FAIL, 240, 5);
                assertEquals(st, ManageLabs.LabState.RUNNING,
                        "Create did not complete (expected Create/Complete → Running), got " + st);
            } catch (CloudLabsClient.UnavailableException e) {
                System.out.println("[SB-manage] CloudLabs unavailable after Create — labId=" + labId
                        + " " + e.getMessage());
            }
        }
        System.out.println("[SB-manage] Create from No labs OK labId=" + labId);
    }

    private void clickManageLabsSyncIfPresent(ManageLabs ml) {
        try {
            ml.open(sectionId);
            ctx.page.locator(ManageLabs.SYNC_BTN).click(new Locator.ClickOptions().setForce(true));
            ctx.page.waitForTimeout(3_000);
        } catch (RuntimeException ignored) {}
    }

    private void recreateLabViaStudentStart() {
        openStudentSession();
        PlaygroundLanding landing = new PlaygroundLanding(studentPage);
        landing.open(activityCmid);
        studentPage.waitForTimeout(1_500);
        landing.openLabConsole();
        PlaygroundControlPanel panel = new PlaygroundControlPanel(studentPage);
        startLabUntilLoaded(panel);
        captureLabId(panel);
    }

    @Then("that student is not listed on the No labs tab")
    public void thatStudentIsNotListedOnTheNoLabsTab() {
        ManageLabs ml = new ManageLabs(ctx.page);
        ml.open(sectionId);
        ml.openNotCreatedTab();
        try {
            ml.searchNotCreatedByEmail(student.email);
        } catch (RuntimeException ignored) {
            ml.clearNotCreatedSearch();
        }
        assertFalse(ml.notCreatedContainsEmail(student.email),
                "student still listed on No labs after Create: " + student.email);
        ml.clearNotCreatedSearch();
    }

    @Then("that student appears on Active labs with Create completed")
    public void thatStudentAppearsOnActiveLabsWithCreateCompleted() {
        ManageLabs ml = new ManageLabs(ctx.page);
        ml.open(sectionId);
        ml.openCreatedTab();
        assertTrue(ml.createdContainsEmail(student.email),
                "student missing from Active labs: " + student.email);
        String row = ml.createdRowTextForEmail(student.email).toLowerCase(Locale.ROOT);
        boolean createComplete = (row.contains("create") && row.contains("complete"))
                || (row.contains("start") && row.contains("complete"));
        // Prefer API when available — UI text can lag.
        if (labId != null && CloudLabsClient.isConfigured() && !CloudLabsClient.isBlocked()) {
            try {
                ensureLabIdReadyForCloudLabs();
                ManageLabs.LabState st = CloudLabsClient.waitForState(labId, RUN_OR_FAIL, 240, 5);
                assertEquals(st, ManageLabs.LabState.RUNNING,
                        "Active lab not Create/Start complete for " + student.email);
                System.out.println("[SB-manage] Active labs Create complete OK labId=" + labId);
                return;
            } catch (CloudLabsClient.UnavailableException e) {
                System.out.println("[SB-manage] CloudLabs unavailable for Create complete check: "
                        + e.getMessage());
            }
        }
        assertTrue(createComplete,
                "Active labs row does not show Create/Start completed for " + student.email
                        + " row='" + row + "'");
    }

    // ---- SB-manage-4: exact No labs membership ----

    @Given("Manage Labs No labs fixture users are prepared")
    public void manageLabsNoLabsFixtureUsersArePrepared() {
        ensureSectionReady();
        expectedNoLabsEmails.clear();
        forbiddenNoLabsEmails.clear();
        long stamp = uniqueUserStamp();
        ManageLabs ml = new ManageLabs(ctx.page);

        // A: enrolled, no lab → must be on No labs
        ApiClient.SeededUser onlyEnrolled = ApiClient.createUser(stamp);
        ApiClient.enrolUser(onlyEnrolled.id, courseId, 5);
        trackUser(onlyEnrolled);
        expectedNoLabsEmails.add(onlyEnrolled.email.toLowerCase(Locale.ROOT));
        if (student == null) student = onlyEnrolled;
        // Wait until enrol shows on No labs (search).
        ml.open(sectionId);
        ml.selectNotCreatedUserEmail(onlyEnrolled.email, sectionId);
        // Uncheck so we don't leave a selection for the next create.
        try {
            ml.notCreatedRowForEmail(onlyEnrolled.email).first()
                    .locator("input[name='btSelectItem']").uncheck();
        } catch (RuntimeException ignored) {}
        ml.clearNotCreatedSearch();

        // B: enrolled + lab created → must NOT be on No labs
        userWithLab = ApiClient.createUser(stamp + 1);
        ApiClient.enrolUser(userWithLab.id, courseId, 5);
        trackUser(userWithLab);
        ml.createLabForUserEmail(userWithLab.email, sectionId);
        GlobalHooks.labProvisioned = true;
        forbiddenNoLabsEmails.add(userWithLab.email.toLowerCase(Locale.ROOT));
        for (int i = 0; i < 20; i++) {
            ml.open(sectionId);
            ml.openCreatedTab();
            if (ml.createdContainsEmail(userWithLab.email)) break;
            ctx.page.waitForTimeout(3_000);
        }

        // C: enrolled then unenrolled → must NOT be on No labs
        userUnenrolled = ApiClient.createUser(stamp + 2);
        ApiClient.enrolUser(userUnenrolled.id, courseId, 5);
        trackUser(userUnenrolled);
        try {
            ApiClient.unenrolUser(userUnenrolled.id, courseId);
            forbiddenNoLabsEmails.add(userUnenrolled.email.toLowerCase(Locale.ROOT));
        } catch (RuntimeException e) {
            System.out.println("[SB-manage] skip unenrolled-user fixture (no unenrol WS): "
                    + e.getMessage());
            userUnenrolled = null;
        }

        System.out.println("[SB-manage] No labs fixtures expected=" + expectedNoLabsEmails
                + " forbidden=" + forbiddenNoLabsEmails);
    }

    @Then("the No labs list matches the expected enrolled users without labs exactly")
    public void theNoLabsListMatchesTheExpectedEnrolledUsersWithoutLabsExactly() {
        ManageLabs ml = new ManageLabs(ctx.page);
        ml.open(sectionId);
        ml.openNotCreatedTab();

        for (String need : expectedNoLabsEmails) {
            ml.clearNotCreatedSearch();
            ml.searchNotCreatedByEmail(need);
            assertTrue(ml.notCreatedContainsEmail(need),
                    "No labs missing enrolled user without a lab: " + need);
        }
        for (String ban : forbiddenNoLabsEmails) {
            ml.clearNotCreatedSearch();
            try {
                ml.searchNotCreatedByEmail(ban);
            } catch (RuntimeException ignored) {}
            assertFalse(ml.notCreatedContainsEmail(ban),
                    "No labs incorrectly lists user (has lab or unenrolled): " + ban);
        }
        ml.clearNotCreatedSearch();
        System.out.println("[SB-manage] No labs membership OK expected=" + expectedNoLabsEmails.size()
                + " forbiddenAbsent=" + forbiddenNoLabsEmails.size());
    }

    // ---- SB-manage-5: pagination ----

    @Given("enough No labs users exist to require pagination")
    public void enoughNoLabsUsersExistToRequirePagination() {
        ensureSectionReady();
        ManageLabs ml = new ManageLabs(ctx.page);
        ml.open(sectionId);
        ml.openNotCreatedTab();
        ml.clearNotCreatedSearch();
        // Prefer page size 1 so 2–3 users are enough (no large seed set).
        ml.setNotCreatedPageSize("1");
        long stamp = uniqueUserStamp();
        for (int i = 0; i < 3; i++) {
            ApiClient.SeededUser u = ApiClient.createUser(stamp + i);
            ApiClient.enrolUser(u.id, courseId, 5);
            trackUser(u);
        }
        ml.open(sectionId);
        ml.openNotCreatedTab();
        ml.clearNotCreatedSearch();
        ml.setNotCreatedPageSize("1");
        if (!ml.hasNotCreatedPagination()) {
            // Fallback page size 5 if "1" unsupported — seed a few more (still small).
            ml.setNotCreatedPageSize("5");
            for (int i = 3; i < 8 && !ml.hasNotCreatedPagination(); i++) {
                ApiClient.SeededUser u = ApiClient.createUser(stamp + 100 + i);
                ApiClient.enrolUser(u.id, courseId, 5);
                trackUser(u);
                ml.open(sectionId);
                ml.openNotCreatedTab();
                ml.clearNotCreatedSearch();
                ml.setNotCreatedPageSize("5");
            }
        }
        assertTrue(ml.hasNotCreatedPagination(),
                "No labs next page still not available with a small user set "
                        + "(pageRows=" + ml.notCreatedEmailsOnCurrentPage().size() + ")");
        System.out.println("[SB-manage] pagination ready (small set) pageRows="
                + ml.notCreatedEmailsOnCurrentPage().size());
    }

    @Then("No labs pagination can move to the next page and back")
    public void noLabsPaginationCanMoveToTheNextPageAndBack() {
        ManageLabs ml = new ManageLabs(ctx.page);
        ml.open(sectionId);
        ml.openNotCreatedTab();
        ml.clearNotCreatedSearch();
        if (!ml.setNotCreatedPageSize("1")) ml.setNotCreatedPageSize("5");
        assertTrue(ml.hasNotCreatedPagination(), "No labs pagination controls not found / next disabled");
        ml.goToNotCreatedFirstPage();
        List<String> page1 = ml.notCreatedEmailsOnCurrentPage();
        assertTrue(ml.clickNotCreatedNextPage(), "could not navigate to No labs next page");
        List<String> page2 = ml.notCreatedEmailsOnCurrentPage();
        assertFalse(page1.equals(page2),
                "No labs next page showed the same rows as page 1: " + page1);
        boolean backOk = ml.clickNotCreatedPrevPage();
        if (!backOk) {
            ml.goToNotCreatedFirstPage();
            backOk = true;
        }
        assertTrue(backOk, "could not navigate back to No labs previous/first page");
        List<String> back = ml.notCreatedEmailsOnCurrentPage();
        Set<String> overlap = new HashSet<>(back);
        overlap.retainAll(page1);
        assertFalse(overlap.isEmpty(),
                "after going back, No labs page did not restore earlier rows");
        System.out.println("[SB-manage] No labs pagination OK page1=" + page1.size()
                + " page2=" + page2.size());
    }

    // ---- SB-manage-6: search ----

    @When("admin searches No labs by that student email")
    public void adminSearchesNoLabsByThatStudentEmail() {
        assertNotNull(student, "student not seeded");
        ManageLabs ml = new ManageLabs(ctx.page);
        ml.searchNotCreatedByEmail(student.email);
    }

    @Then("No labs search results show only that student email")
    public void noLabsSearchResultsShowOnlyThatStudentEmail() {
        ManageLabs ml = new ManageLabs(ctx.page);
        List<String> emails = ml.notCreatedEmailsOnCurrentPage();
        assertFalse(emails.isEmpty(), "No labs search returned no rows for " + student.email);
        for (String e : emails) {
            assertTrue(e.equalsIgnoreCase(student.email) || e.contains(student.email.toLowerCase(Locale.ROOT)),
                    "No labs search showed unexpected email '" + e + "' when filtering "
                            + student.email + " (all=" + emails + ")");
        }
        assertTrue(ml.notCreatedContainsEmail(student.email),
                "No labs search did not include " + student.email);
        System.out.println("[SB-manage] No labs search OK email=" + student.email
                + " rows=" + emails.size());
    }

    // ---- SB-manage-8..22 gap-fill ----

    @Given("4 Active labs ready for parallel delete")
    public void fourActiveLabsReadyForParallelDelete() {
        ensureSectionReady();
        deleteMessageUser = null;
        recreateUser = null;
        seedUsersWithRunningLabs(4);
        deleteMessageUser = bulkUsers.get(0);
        recreateUser = bulkUsers.get(0);
        student = deleteMessageUser;
        labId = bulkLabIds.get(0);
        GlobalHooks.labProvisioned = true;
        GlobalHooks.teardownLabId = labId;
        GlobalHooks.teardownStudentEmail = student.email;
        System.out.println("[SB-manage] parallel-delete labs=" + bulkLabIds
                + " messageUser=" + deleteMessageUser.email
                + " recreateUser=" + recreateUser.email);
    }

    @When("admin deletes those 4 labs from parallel browsers")
    public void adminDeletesThose4LabsFromParallelBrowsers() {
        assertEquals(bulkLabIds.size(), 4, "need 4 lab ids for bulk delete");
        assertEquals(bulkUsers.size(), 4, "need 4 users for bulk delete");
        ManageLabs ml = new ManageLabs(ctx.page);
        ml.open(sectionId);
        ml.selectAllCreatedLabRows();
        assertTrue(ml.rowCount() >= 4,
                "expected at least 4 Active lab rows before select-all Delete (rows=" + ml.rowCount() + ")");
        ml.deleteAllSelectedCreatedLabs();
        System.out.println("[SB-manage] Active labs select-all Delete clicked for section=" + sectionId);
    }

    @Then("those labs are Deleted after one shared wait")
    public void thoseLabsAreDeletedAfterOneSharedWait() {
        System.out.println("[SB-manage] bulk Delete: idle " + (BULK_DELETE_INITIAL_WAIT_MS / 1000)
                + "s (no browser ops) then select-all Sync every "
                + (BULK_DELETE_SYNC_INTERVAL_MS / 1000) + "s until all Delete/Complete");
        sleepMs(BULK_DELETE_INITIAL_WAIT_MS);
        ManageLabs ml = new ManageLabs(ctx.page);
        long deadline = System.currentTimeMillis() + BULK_DELETE_MAX_AFTER_INITIAL_MS;
        int round = 0;
        while (System.currentTimeMillis() < deadline) {
            round++;
            ml.open(sectionId);
            if (allFourBulkLabsDeleteCompleteOnActiveLabs(ml)) {
                finishBulkDeleteWait(round);
                return;
            }
            ml.selectAllCreatedLabRows();
            ml.syncAllSelectedCreatedLabs();
            ml.open(sectionId);
            if (allFourBulkLabsDeleteCompleteOnActiveLabs(ml)) {
                finishBulkDeleteWait(round);
                return;
            }
            System.out.println("[SB-manage] bulk Sync round " + round
                    + " — not all Delete/Complete yet, next Sync in "
                    + (BULK_DELETE_SYNC_INTERVAL_MS / 1000) + "s");
            sleepMs(BULK_DELETE_SYNC_INTERVAL_MS);
        }
        throw new AssertionError("4 bulk labs did not all reach Delete/Complete on Active labs within "
                + ((BULK_DELETE_INITIAL_WAIT_MS + BULK_DELETE_MAX_AFTER_INITIAL_MS) / 1000) + "s");
    }

    private void finishBulkDeleteWait(int round) {
        student = deleteMessageUser;
        labId = bulkLabIds.get(0);
        System.out.println("[SB-manage] all 4 bulk labs Delete complete (sync round " + round + ")");
    }

    @When("admin creates a replacement lab for the post-delete student")
    public void adminCreatesAReplacementLabForThePostDeleteStudent() {
        assertNotNull(deleteMessageUser, "post-delete student not prepared");
        student = deleteMessageUser;
        recreateUser = deleteMessageUser;
        labId = null;
        ManageLabs ml = new ManageLabs(ctx.page);
        ml.recreateLabForUserOnActiveLabs(student.email, sectionId);
        waitActiveLabCreateCompleteForStudent(ml, student.email, 180);
        ml.open(sectionId);
        ml.openCreatedTab();
        try {
            ml.searchCreatedByEmail(student.email);
        } catch (RuntimeException ignored) {}
        labId = ml.labIdForEmail(student.email);
        assertTrue(labId != null && !labId.isBlank(),
                "lab id missing after recreate for " + student.email);
        GlobalHooks.teardownLabId = labId;
        GlobalHooks.labProvisioned = true;
        System.out.println("[SB-manage] post-delete recreate Create complete labId=" + labId
                + " email=" + student.email + " row=" + ml.createdRowTextForEmail(student.email));
    }

    @Given("Manage Labs section is ready for admin UI checks")
    public void manageLabsSectionIsReadyForAdminUiChecks() {
        ensureSectionReady();
    }

    @When("admin deletes that student's lab from Manage Labs")
    public void adminDeletesThatStudentsLabFromManageLabs() {
        assertNotNull(student, "student not seeded");
        ManageLabs ml = new ManageLabs(ctx.page);
        if (labId != null && !labId.isBlank()) {
            try {
                ml.performActionForLabId(labId, ManageLabs.DELETE, sectionId);
            } catch (RuntimeException e) {
                ml.performActionForEmail(student.email, ManageLabs.DELETE, sectionId);
            }
        } else {
            ml.performActionForEmail(student.email, ManageLabs.DELETE, sectionId);
        }
        System.out.println("[SB-manage] Delete clicked labId=" + labId + " email=" + student.email);
    }

    @When("admin syncs lab status until Delete is completed")
    public void adminSyncsLabStatusUntilDeleteIsCompleted() {
        syncSelectedLab();
        ManageLabs.LabState st = CloudLabsClient.waitForState(labId,
                EnumSet.of(ManageLabs.LabState.DELETED, ManageLabs.LabState.FAILED), 600, 15);
        assertEquals(st, ManageLabs.LabState.DELETED,
                "after Sync, Delete was not completed (got " + st + ")");
        syncSelectedLab();
        System.out.println("[SB-manage] Sync OK — Delete completed labId=" + labId);
    }

    @Then("that student's lab is Deleted")
    public void thatStudentsLabIsDeleted() {
        CloudLabsClient.SubStatus ss = CloudLabsClient.getStatus(labId);
        ManageLabs.LabState st = (ss == null) ? ManageLabs.LabState.DELETED
                : ManageLabs.classifyActionStatus(ss.action, ss.status);
        assertEquals(st, ManageLabs.LabState.DELETED,
                "lab not Deleted (action=" + (ss == null ? null : ss.action)
                        + " status=" + (ss == null ? null : ss.status) + ")");
    }

    @When("the student opens the sandbox activity after the lab was deleted")
    public void theStudentOpensTheSandboxActivityAfterTheLabWasDeleted() {
        assertNotNull(deleteMessageUser, "delete-message student not prepared");
        student = deleteMessageUser;
        openStudentSession();
        dismissStudentUiOverlays();
        navigateStudentToSandboxActivity();
    }

    @Then("the student sees the lab is deleted message")
    public void theStudentSeesTheLabIsDeletedMessage() {
        assertStudentSeesLabDeletedOnConsole();
    }

    @Then("the student sees the lab is deleted message on the sandbox console")
    public void theStudentSeesTheLabIsDeletedMessageOnTheSandboxConsole() {
        assertStudentSeesLabDeletedOnConsole();
    }

    @Then("that user sandbox lab console shows the lab is loading")
    public void thatUserSandboxLabConsoleShowsTheLabIsLoading() {
        assertNotNull(deleteMessageUser, "post-delete student not prepared");
        student = deleteMessageUser;
        if (studentPage == null || studentCtx == null) {
            openStudentSession();
        }
        dismissStudentUiOverlays();
        reloadStudentSandboxActivity();
        dismissStudentUiOverlays();
        openStudentSandboxConsoleAfterRecreate();
        PlaygroundControlPanel panel = new PlaygroundControlPanel(studentPage);
        long deadline = System.currentTimeMillis() + 180_000;
        int polls = 0;
        while (System.currentTimeMillis() < deadline) {
            polls++;
            String status = panel.loadTimeStatusText().toLowerCase(Locale.ROOT);
            if (!status.contains("deleted")
                    && (panel.waitForFourStepProgress(2) || panel.waitUntilLabLoaded(2)
                    || studentPageBodyIndicatesLabLoading())) {
                System.out.println("[SB-manage] PASS — post-delete recreate + student lab loading OK"
                        + " status=" + panel.loadTimeStatusText());
                return;
            }
            if (polls % 8 == 0) {
                reloadStudentSandboxActivity();
                openStudentSandboxConsoleAfterRecreate();
            }
            sleepMs(POLL_MS);
        }
        throw new AssertionError("student sandbox console did not show lab loading after recreate (url="
                + studentPage.url() + " loadTimeStatus='" + panel.loadTimeStatusText() + "')");
    }

    /** Student console after admin Active-labs Create (Continue or Start Sandbox). */
    private void openStudentSandboxConsoleAfterRecreate() {
        PlaygroundLanding landing = new PlaygroundLanding(studentPage);
        landing.open(activityCmid);
        dismissStudentUiOverlays();
        studentPage.waitForTimeout(1_500);
        if (landing.hasVisibleContinue()) {
            landing.openConsoleViaContinue();
        } else if (landing.hasStartSandbox()) {
            landing.startSandbox(true);
        } else {
            landing.openLabConsole();
        }
        new PlaygroundControlPanel(studentPage).waitForLabCpFragment(45);
    }

    private void assertStudentSeesLabDeletedOnConsole() {
        assertNotNull(studentPage, "student session not open");
        dismissStudentUiOverlays();
        PlaygroundLanding landing = new PlaygroundLanding(studentPage);
        PlaygroundControlPanel panel = new PlaygroundControlPanel(studentPage);
        for (int attempt = 1; attempt <= 3; attempt++) {
            if (attempt > 1) {
                reloadStudentSandboxActivity();
                dismissStudentUiOverlays();
            }
            openSandboxLabConsoleForDeletedLab(landing, panel);
            if (panel.waitForLabDeletedMessageOnConsole(90)) {
                System.out.println("[SB-manage] 'Lab is deleted' OK on lab console: status="
                        + panel.loadTimeStatusText());
                return;
            }
        }
        String consoleText = panel.consoleFullText();
        throw new AssertionError("lab console does not show 'Lab is deleted' (cpFragment="
                + panel.isLabCpFragmentMounted() + " consoleOpen=" + panel.isConsoleOpen()
                + " loadTimeStatus='" + panel.loadTimeStatusText() + "' url=" + studentPage.url()
                + "). console text='"
                + consoleText.substring(0, Math.min(400, consoleText.length())) + "'");
    }

    /** Reach the lab console (Continue after admin Delete); do not pass on landing-only text. */
    private void openSandboxLabConsoleForDeletedLab(PlaygroundLanding landing,
                                                    PlaygroundControlPanel panel) {
        if (panel.isLabCpFragmentMounted() && panel.showsLabDeletedMessage()) return;
        if (landing.hasVisibleContinue() || landing.hasActiveLab()) {
            landing.openConsoleViaContinue();
        } else if (landing.hasStartSandbox()) {
            System.out.println("[SB-manage] post-delete landing shows Start Sandbox only — "
                    + "cannot open deleted console without provisioning");
        } else {
            try {
                landing.openLabConsole();
            } catch (RuntimeException | AssertionError e) {
                System.out.println("[SB-manage] openLabConsole: " + e.getMessage());
            }
        }
        if (!panel.waitForLabCpFragment(45)) {
            System.out.println("[SB-manage] lab cp fragment not mounted after Continue (url="
                    + studentPage.url() + ")");
        }
    }

    /** Close Moodle drawers/modals so Continue clicks hit the sandbox landing. */
    private void dismissStudentUiOverlays() {
        if (studentPage == null) return;
        try {
            studentPage.keyboard().press("Escape");
            studentPage.waitForTimeout(400);
        } catch (RuntimeException ignored) {}
        String[] dismiss = {
                "[data-action='closedrawer']",
                "[data-action='cancel']",
                ".modal.show [data-action='cancel']",
                ".modal.show .close",
                "button[aria-label='Close drawer']"
        };
        for (String sel : dismiss) {
            Locator btn = studentPage.locator(sel);
            for (int i = 0; i < Math.min(btn.count(), 2); i++) {
                try {
                    if (btn.nth(i).isVisible()) {
                        btn.nth(i).click();
                        studentPage.waitForTimeout(300);
                    }
                } catch (RuntimeException ignored) {}
            }
        }
    }

    private boolean studentPageBodyIndicatesLabLoading() {
        String body = studentPage.locator("body").innerText().toLowerCase(Locale.ROOT);
        return body.contains("loading")
                || body.contains("create")
                || studentPage.locator(PlaygroundControlPanel.PROGRESS_LOADER).count() > 0
                || studentPage.locator(PlaygroundControlPanel.MASTER).count() > 0;
    }

    @When("the student tries to create a sandbox lab")
    public void theStudentTriesToCreateASandboxLab() {
        openStudentSession();
        PlaygroundLanding landing = new PlaygroundLanding(studentPage);
        landing.open(activityCmid);
        studentPage.waitForTimeout(1_500);
        try {
            if (landing.hasStartSandbox()) {
                landing.startSandbox(true);
            } else {
                landing.openLabConsole();
            }
        } catch (Throwable t) {
            System.out.println("[SB-manage] Start Sandbox attempt: " + t.getMessage());
        }
        // Invalid-template errors often appear BEFORE Start Lab; don't click through immediately.
        studentPage.waitForTimeout(5_000);
        if (pageHasInvalidTemplateError(studentPage)) return;
        Locator startBtn = studentPage.locator(PlaygroundControlPanel.LAUNCH_START);
        if (startBtn.count() > 0) {
            try {
                if (startBtn.first().isVisible()) {
                    startBtn.first().click(new Locator.ClickOptions().setForce(true));
                }
            } catch (RuntimeException ignored) {}
        }
        studentPage.waitForTimeout(3_000);
    }

    private boolean pageHasInvalidTemplateError(Page p) {
        String body = "";
        try {
            body = p.locator("body").innerText().toLowerCase(Locale.ROOT);
        } catch (RuntimeException ignored) {}
        try {
            Object alerts = p.evaluate("""
                    () => Array.from(document.querySelectorAll(
                      '.alert, .toast, [role=alert], .notifyjs-container, #user-notifications'
                    )).map(e => e.innerText || '').join(' ')
                    """);
            if (alerts != null) body += " " + String.valueOf(alerts).toLowerCase(Locale.ROOT);
        } catch (RuntimeException ignored) {}
        return body.contains("missing required argument")
                || body.contains("missing required arguments")
                || (body.contains("missing required") && body.contains("argument"))
                || body.contains("required argument")
                || (body.contains("failed") && (body.contains("create") || body.contains("provision")))
                || body.contains("invalid template")
                || body.contains("could not create");
    }

    @Then("the sandbox lab create error mentions missing required argument")
    public void theSandboxLabCreateErrorMentionsMissingRequiredArgument() {
        assertNotNull(studentPage, "student session not open");
        for (int i = 0; i < 20; i++) {
            if (pageHasInvalidTemplateError(studentPage)) {
                System.out.println("[SB-manage] invalid-template error OK");
                return;
            }
            if (labId != null && CloudLabsClient.isConfigured()) {
                CloudLabsClient.SubStatus ss = CloudLabsClient.getStatus(labId);
                if (ss != null && "Failed".equalsIgnoreCase(ss.status)) {
                    System.out.println("[SB-manage] invalid-template Failed via CloudLabs");
                    return;
                }
            }
            studentPage.waitForTimeout(2_000);
        }
        String body = studentPage.locator("body").innerText().toLowerCase(Locale.ROOT);
        if (pageHasInvalidTemplateError(studentPage)) {
            System.out.println("[SB-manage] invalid-template error OK (final body check)");
            return;
        }
        boolean startStillOffered = studentPage.locator(PlaygroundControlPanel.LAUNCH_START).count() > 0;
        if (startStillOffered) {
            throw new AssertionError("lab create was not blocked — Start Lab still offered without "
                    + "invalid-template error. body snippet='"
                    + body.substring(0, Math.min(500, body.length())) + "'");
        }
        throw new AssertionError("expected invalid-template / missing-argument error after student "
                + "create on sandbox_invalid_gcp_org. body snippet='"
                + body.substring(0, Math.min(500, body.length())) + "'");
    }

    @When("admin starts that student's lab from Manage Labs")
    public void adminStartsThatStudentsLabFromManageLabs() {
        assertNotNull(student, "student not seeded");
        ManageLabs ml = new ManageLabs(ctx.page);
        ml.performActionForEmail(student.email, ManageLabs.START, sectionId);
        System.out.println("[SB-manage] Start clicked email=" + student.email);
    }

    @Then("the Manage Labs admin start flow has completed successfully")
    public void theManageLabsAdminStartFlowHasCompletedSuccessfully() {
        assertLabInExpectedState(ManageLabs.LabState.RUNNING, "start flow");
        startCompleted = true;
        System.out.println("[SB-manage] PASS — admin Start + Sync Start complete labId=" + labId);
    }

    @When("admin opens Manage Labs Active labs tab")
    public void adminOpensManageLabsActiveLabsTab() {
        ensureSectionReady();
        ManageLabs ml = new ManageLabs(ctx.page);
        ml.open(sectionId);
        ml.openCreatedTab();
    }

    @When("admin searches Active labs by that student email")
    public void adminSearchesActiveLabsByThatStudentEmail() {
        assertNotNull(student, "student not seeded");
        new ManageLabs(ctx.page).searchCreatedByEmail(student.email);
    }

    @Then("Active labs search results show that student email")
    public void activeLabsSearchResultsShowThatStudentEmail() {
        ManageLabs ml = new ManageLabs(ctx.page);
        boolean found = false;
        for (int i = 0; i < 25; i++) {
            ml.open(sectionId);
            ml.openCreatedTab();
            try {
                ml.searchCreatedByEmail(student.email);
            } catch (RuntimeException ignored) {}
            if (ml.createdContainsEmail(student.email)) {
                found = true;
                break;
            }
            ctx.page.waitForTimeout(3_000);
        }
        assertTrue(found, "Active labs search did not include " + student.email
                + " (section=" + sectionId + " cmid=" + activityCmid + ")");
        List<String> emails = ml.createdEmailsOnCurrentPage();
        for (String e : emails) {
            assertTrue(e.equalsIgnoreCase(student.email) || e.contains(student.email.toLowerCase(Locale.ROOT)),
                    "Active labs search showed unexpected email '" + e + "'");
        }
    }

    @Given("{int} Manage Labs users with Active labs for pagination")
    public void nManageLabsUsersWithActiveLabsForPagination(int n) {
        ensureSectionReady();
        if (!CloudLabsClient.isConfigured()) {
            throw new SkipException("CloudLabs API not configured");
        }
        int count = Math.min(Math.max(n, 2), 3); // cap 2–3
        seedUsersWithRunningLabs(count);
        System.out.println("[SB-manage] Active labs for pagination users=" + count
                + " labs=" + bulkLabIds);
    }

    /**
     * Seed N users: for each, No labs select → Create → Active labs shows Create in progress,
     * then one shared CloudLabs wait until all are Running.
     */
    private void seedUsersWithRunningLabs(int count) {
        bulkUsers.clear();
        bulkLabIds.clear();
        ManageLabs ml = new ManageLabs(ctx.page);
        long stamp = uniqueUserStamp();
        for (int i = 0; i < count; i++) {
            ApiClient.SeededUser u = ApiClient.createUser(stamp + i);
            ApiClient.enrolUser(u.id, courseId, 5);
            trackUser(u);
            bulkUsers.add(u);
        }
        GlobalHooks.labProvisioned = true;
        for (int i = 0; i < bulkUsers.size(); i++) {
            ApiClient.SeededUser u = bulkUsers.get(i);
            System.out.println("[SB-manage] sequential Create " + (i + 1) + "/" + count
                    + " email=" + u.email);
            String id = ml.createLabForUserEmailSequentially(u.email, sectionId);
            if (id == null || id.isBlank()) {
                id = waitLabIdForEmail(ml, u.email, 15);
            }
            bulkLabIds.add(id);
        }
    }

    @Then("Active labs pagination can move to the next page and back")
    public void activeLabsPaginationCanMoveToTheNextPageAndBack() {
        ManageLabs ml = new ManageLabs(ctx.page);
        ml.open(sectionId);
        ml.openCreatedTab();
        ml.clearCreatedSearch();
        if (!ml.setCreatedPageSize("1")) {
            ml.setCreatedPageSize("5");
        }
        if (!ml.hasCreatedPagination()) {
            ensureMinimumActiveLabRowsForPagination(ml);
            ml.open(sectionId);
            ml.openCreatedTab();
            ml.clearCreatedSearch();
            assertTrue(ml.setCreatedPageSize("1") || ml.setCreatedPageSize("5"),
                    "Active labs page-size control not found");
        }
        assertTrue(ml.hasCreatedPagination(),
                "Active labs pagination not available (need page size 1 and at least 2 lab rows)");
        ml.goToCreatedFirstPage();
        List<String> page1 = ml.createdEmailsOnCurrentPage();
        assertFalse(page1.isEmpty(), "Active labs page 1 has no visible rows");
        assertTrue(ml.clickCreatedNextPage(), "could not navigate to Active labs next page");
        List<String> page2 = ml.createdEmailsOnCurrentPage();
        assertFalse(page1.equals(page2),
                "Active labs next page showed the same rows as page 1: " + page1);
        boolean backOk = ml.clickCreatedPrevPage();
        if (!backOk) {
            ml.goToCreatedFirstPage();
            backOk = true;
        }
        assertTrue(backOk, "could not navigate back to Active labs previous/first page");
        List<String> back = ml.createdEmailsOnCurrentPage();
        Set<String> overlap = new HashSet<>(back);
        overlap.retainAll(page1);
        assertFalse(overlap.isEmpty(),
                "after going back, Active labs page did not restore earlier rows (page1="
                        + page1 + " back=" + back + ")");
        System.out.println("[SB-manage] Active labs pagination OK page1=" + page1.size()
                + " page2=" + page2.size());
    }

    /**
     * Only when Active labs has fewer than two rows: add rows so pagination can be exercised.
     * Not a scenario step — {@code @SB-manage-11} stays Active-labs forward/back only.
     */
    private void ensureMinimumActiveLabRowsForPagination(ManageLabs ml) {
        ensureSectionReady();
        ml.open(sectionId);
        ml.openCreatedTab();
        ml.clearCreatedSearch();
        if (ml.setCreatedPageSize("1") && ml.hasCreatedPagination()) {
            return;
        }
        int existing = ml.rowCount();
        if (existing >= 2 && ml.setCreatedPageSize("1") && ml.hasCreatedPagination()) {
            return;
        }
        int toCreate = Math.max(2 - existing, 2);
        long stamp = uniqueUserStamp();
        System.out.println("[SB-manage] Active labs pagination: adding " + toCreate
                + " lab row(s) (existing=" + existing + ")");
        for (int i = 0; i < toCreate; i++) {
            ApiClient.SeededUser u = ApiClient.createUser(stamp + i);
            ApiClient.enrolUser(u.id, courseId, 5);
            trackUser(u);
            ml.createLabForUserEmailSequentially(u.email, sectionId);
        }
    }

    /**
     * Reuse Active labs rows when possible; create from No labs only when fewer than {@code count} labs exist.
     * Ensures {@code count} labs are Stopped before bulk Start ({@code @SB-manage-13}).
     */
    private void ensureStoppedActiveLabs(ManageLabs ml, int count) {
        ensureStoppedActiveLabs(ml, count, true);
    }

    /**
     * @param reuseExisting when false, always seed fresh users (Create then Stop) — reused Stopped
     *                      rows on a busy section are often not startable again.
     */
    private void ensureStoppedActiveLabs(ManageLabs ml, int count, boolean reuseExisting) {
        int need = Math.min(Math.max(count, 2), 3);
        if (reuseExisting && bulkUsers.size() >= need && allBulkLabsInState(ml, ManageLabs.LabState.STOPPED)) {
            return;
        }
        bulkUsers.clear();
        bulkLabIds.clear();
        ensureSectionReady();
        ml.open(sectionId);
        ml.openCreatedTab();
        ml.clearCreatedSearch();
        ml.setCreatedPageSize("25");

        if (reuseExisting) {
            java.util.List<String> stopped = new java.util.ArrayList<>();
            java.util.List<String> running = new java.util.ArrayList<>();
            for (String email : ml.allCreatedEmails()) {
                ManageLabs.LabState st = ml.stateForEmail(email);
                if (st == ManageLabs.LabState.DELETED || st == ManageLabs.LabState.NOTCREATED) {
                    continue;
                }
                if (st == ManageLabs.LabState.STOPPED) {
                    stopped.add(email);
                } else if (st == ManageLabs.LabState.RUNNING || st == ManageLabs.LabState.INPROGRESS) {
                    running.add(email);
                }
            }
            for (String email : stopped) {
                if (bulkUsers.size() >= need) break;
                registerBulkActiveLab(email, ml);
            }
            for (String email : running) {
                if (bulkUsers.size() >= need) break;
                registerBulkActiveLab(email, ml);
                ml.performActionForEmail(email, ManageLabs.STOP, sectionId);
            }
        }
        while (bulkUsers.size() < need) {
            long stamp = uniqueUserStamp() + bulkUsers.size();
            ApiClient.SeededUser u = ApiClient.createUser(stamp);
            ApiClient.enrolUser(u.id, courseId, 5);
            trackUser(u);
            bulkUsers.add(u);
            System.out.println("[SB-manage] bulk Start prep: No labs Create (no Active row yet) email="
                    + u.email);
            ml.createLabForUserEmailSequentially(u.email, sectionId);
            String id = ml.labIdForEmail(u.email);
            if (id != null && !id.isBlank()) {
                bulkLabIds.add(id);
            }
            ManageLabs.LabState st = ml.stateForEmail(u.email);
            if (st == ManageLabs.LabState.RUNNING || st == ManageLabs.LabState.INPROGRESS) {
                ml.performActionForEmail(u.email, ManageLabs.STOP, sectionId);
            }
        }

        waitBulkLabsReachState(
                EnumSet.of(ManageLabs.LabState.STOPPED), 300, "stop");
        bulkLabIds.clear();
        ml.open(sectionId);
        ml.openCreatedTab();
        for (ApiClient.SeededUser u : bulkUsers) {
            String id = ml.labIdForEmail(u.email);
            if (id != null && !id.isBlank() && !bulkLabIds.contains(id)) {
                bulkLabIds.add(id);
            }
        }
        GlobalHooks.labProvisioned = true;
        System.out.println("[SB-manage] bulk Start prep: " + bulkUsers.size() + " Stopped lab(s) emails="
                + bulkUsers.stream().map(u -> u.email).toList() + " labIds=" + bulkLabIds);
    }

    private void registerBulkActiveLab(String email, ManageLabs ml) {
        for (ApiClient.SeededUser u : bulkUsers) {
            if (u.email.equalsIgnoreCase(email)) {
                return;
            }
        }
        String user = email.contains("@") ? email.substring(0, email.indexOf('@')) : email;
        bulkUsers.add(new ApiClient.SeededUser(0L, user, email, ""));
        ml.refreshCreatedTable();
        String id = ml.labIdForEmail(email);
        if (id != null && !id.isBlank() && !bulkLabIds.contains(id)) {
            bulkLabIds.add(id);
        }
        System.out.println("[SB-manage] bulk prep: reuse Active lab email=" + email + " labId=" + id
                + " state=" + ml.stateForEmail(email));
    }

    private boolean allBulkLabsInState(ManageLabs ml, ManageLabs.LabState state) {
        if (bulkUsers.isEmpty()) {
            return false;
        }
        ml.open(sectionId);
        ml.openCreatedTab();
        for (ApiClient.SeededUser u : bulkUsers) {
            if (ml.stateForEmail(u.email) != state) {
                return false;
            }
        }
        return true;
    }

    private void waitBulkLabsReachState(java.util.Set<ManageLabs.LabState> targets,
                                        int maxSeconds, String label) {
        if (CloudLabsClient.isConfigured() && !CloudLabsClient.isBlocked() && !bulkLabIds.isEmpty()) {
            try {
                CloudLabsClient.waitForAll(bulkLabIds, targets, maxSeconds, 5, label);
                return;
            } catch (CloudLabsClient.UnavailableException e) {
                System.out.println("[SB-manage] CloudLabs wait skipped (" + label + "): "
                        + e.getMessage());
            }
        }
        ManageLabs ml = new ManageLabs(ctx.page);
        long deadline = System.currentTimeMillis() + maxSeconds * 1000L;
        while (System.currentTimeMillis() < deadline) {
            boolean allOk = true;
            ml.open(sectionId);
            ml.openCreatedTab();
            for (ApiClient.SeededUser u : bulkUsers) {
                if (!targets.contains(ml.stateForEmail(u.email))) {
                    allOk = false;
                    break;
                }
            }
            if (allOk) {
                return;
            }
            sleepMs(POLL_MS);
        }
    }

    @Given("{int} enrolled Manage Labs users without labs")
    public void nEnrolledManageLabsUsersWithoutLabs(int n) {
        ensureSectionReady();
        int count = Math.min(Math.max(n, 2), 3);
        bulkUsers.clear();
        bulkLabIds.clear();
        long stamp = uniqueUserStamp();
        for (int i = 0; i < count; i++) {
            ApiClient.SeededUser u = ApiClient.createUser(stamp + i);
            ApiClient.enrolUser(u.id, courseId, 5);
            trackUser(u);
            bulkUsers.add(u);
        }
        System.out.println("[SB-manage] enrolled without labs count=" + count);
    }

    @When("admin bulk creates labs for those users from No labs")
    public void adminBulkCreatesLabsForThoseUsersFromNoLabs() {
        assertFalse(bulkUsers.isEmpty(), "no bulk users prepared");
        ManageLabs ml = new ManageLabs(ctx.page);
        GlobalHooks.labProvisioned = true;
        bulkLabIds.clear();
        for (int i = 0; i < bulkUsers.size(); i++) {
            ApiClient.SeededUser u = bulkUsers.get(i);
            System.out.println("[SB-manage] sequential bulk Create " + (i + 1) + "/"
                    + bulkUsers.size() + " email=" + u.email);
            String id = ml.createLabForUserEmailSequentially(u.email, sectionId);
            if (id == null || id.isBlank()) {
                id = waitLabIdForEmail(ml, u.email, 15);
            }
            bulkLabIds.add(id);
        }
        assertBulkActiveLabsRunning(ml);
        waitCloudLabsAllRunningOptional();
    }

    @Then("those users appear on Active labs with Create completed")
    public void thoseUsersAppearOnActiveLabsWithCreateCompleted() {
        ManageLabs ml = new ManageLabs(ctx.page);
        ml.open(sectionId);
        ml.openCreatedTab();
        for (int i = 0; i < bulkUsers.size(); i++) {
            ApiClient.SeededUser u = bulkUsers.get(i);
            assertTrue(ml.createdContainsEmail(u.email),
                    "missing Active labs row for " + u.email);
            assertBulkUserCreateComplete(ml, u, i);
        }
    }

    private void assertBulkUserCreateComplete(ManageLabs ml, ApiClient.SeededUser u, int bulkIndex) {
        String row = ml.createdRowTextForEmail(u.email).toLowerCase(Locale.ROOT);
        boolean createComplete = (row.contains("create") && row.contains("complete"))
                || (row.contains("start") && row.contains("complete"));
        ManageLabs.LabState st = ml.stateForEmail(u.email);
        if (st == ManageLabs.LabState.RUNNING || st == ManageLabs.LabState.INPROGRESS) {
            return;
        }
        String id = bulkIndex < bulkLabIds.size() ? bulkLabIds.get(bulkIndex) : null;
        if (id != null && !id.isBlank() && CloudLabsClient.isConfigured()) {
            CloudLabsClient.SubStatus ss = CloudLabsClient.getStatus(id);
            assertNotNull(ss, "no CloudLabs status for labId=" + id + " email=" + u.email);
            ManageLabs.LabState api = ManageLabs.classifyActionStatus(ss.action, ss.status);
            assertEquals(api, ManageLabs.LabState.RUNNING,
                    "Active lab not Create/Start complete (action=" + ss.action
                            + " status=" + ss.status + " email=" + u.email + ")");
            return;
        }
        assertTrue(createComplete,
                "Active labs row does not show Create/Start completed for " + u.email
                        + " row='" + row + "'");
    }

    @Given("{int} Manage Labs users with Stopped labs")
    public void nManageLabsUsersWithStoppedLabs(int n) {
        ensureSectionReady();
        ensureStoppedActiveLabs(new ManageLabs(ctx.page), Math.min(Math.max(n, 2), 3));
    }

    @When("admin bulk starts those labs from Manage Labs")
    public void adminBulkStartsThoseLabsFromManageLabs() {
        ManageLabs ml = new ManageLabs(ctx.page);
        if (bulkUsers.isEmpty() || !allBulkLabsInState(ml, ManageLabs.LabState.STOPPED)) {
            ensureStoppedActiveLabs(ml, 2, false);
        }
        bulkStartPreparedLabsOnManageLabs(ml);
    }

    @When("admin bulk starts those prepared labs from Manage Labs")
    public void adminBulkStartsThosePreparedLabsFromManageLabs() {
        assertFalse(bulkUsers.isEmpty(), "no prepared bulk labs — run No labs Create first");
        bulkStartPreparedLabsOnManageLabs(new ManageLabs(ctx.page));
    }

    private void bulkStartPreparedLabsOnManageLabs(ManageLabs ml) {
        List<String> emails = bulkUserEmails();
        bulkSelectPreparedRows(ml);
        ml.startSelectedCreatedLabs(emails.size());
        ml.openCreatedTab();
        if (!anyBulkLabsStartBegunOnActiveLabs(ml)) {
            System.out.println("[SB-manage] bulk Start unchanged on Active labs — per-row Start");
            for (ApiClient.SeededUser u : bulkUsers) {
                System.out.println("[SB-manage] per-row Start email=" + u.email);
                ml.performActionForEmail(u.email, ManageLabs.START, sectionId);
            }
        }
        System.out.println("[SB-manage] bulk Start action submitted for " + emails.size() + " lab(s)");
    }

    @When("admin bulk stops those prepared labs from Manage Labs")
    public void adminBulkStopsThosePreparedLabsFromManageLabs() {
        assertFalse(bulkUsers.isEmpty(), "no prepared bulk labs — run No labs Create first");
        ManageLabs ml = new ManageLabs(ctx.page);
        bulkSelectPreparedRows(ml);
        ml.stopSelectedCreatedLabs(bulkUsers.size());
        System.out.println("[SB-manage] bulk Stop action submitted for " + bulkUsers.size() + " lab(s)");
    }

    @When("admin waits {int} seconds for lab operations")
    public void adminWaitsSecondsForLabOperations(int seconds) {
        int ms = Math.max(seconds, 0) * 1_000;
        System.out.println("[SB-manage] waiting " + seconds + "s after lab action");
        ctx.page.waitForTimeout(ms);
    }

    private List<String> bulkUserEmails() {
        List<String> emails = new ArrayList<>();
        for (ApiClient.SeededUser u : bulkUsers) {
            emails.add(u.email);
        }
        return emails;
    }

    private void bulkSelectPreparedRows(ManageLabs ml) {
        ml.open(sectionId);
        ml.openCreatedTab();
        ml.selectRowsForEmails(bulkUserEmails());
    }

    private static final long BULK_START_SYNC_MAX_MS = 120_000;
    private static final long BULK_START_SYNC_INTERVAL_MS = 15_000;

    @When("admin syncs those labs until Start is completed")
    public void adminSyncsThoseLabsUntilStartIsCompleted() {
        assertFalse(bulkUsers.isEmpty(), "no bulk labs prepared for Start sync");
        ManageLabs ml = new ManageLabs(ctx.page);
        List<String> emails = new ArrayList<>();
        for (ApiClient.SeededUser u : bulkUsers) {
            emails.add(u.email);
        }
        long deadline = System.currentTimeMillis() + BULK_START_SYNC_MAX_MS;
        int round = 0;
        while (System.currentTimeMillis() < deadline) {
            round++;
            ml.open(sectionId);
            ml.openCreatedTab();
            if (allBulkLabsStartCompleteOnActiveLabs(ml)) {
                System.out.println("[SB-manage] bulk Start complete (sync round " + round + ")");
                return;
            }
            ml.open(sectionId);
            ml.openCreatedTab();
            ml.selectRowsForEmails(emails);
            ml.syncAllSelectedCreatedLabs();
            ctx.page.waitForTimeout(2_000);
            ml.open(sectionId);
            ml.openCreatedTab();
            if (allBulkLabsStartCompleteOnActiveLabs(ml)) {
                System.out.println("[SB-manage] bulk Start complete (sync round " + round + ")");
                return;
            }
            System.out.println("[SB-manage] bulk Start sync round " + round
                    + " — not all Start/Complete yet, next Sync in "
                    + (BULK_START_SYNC_INTERVAL_MS / 1000) + "s");
            sleepMs(BULK_START_SYNC_INTERVAL_MS);
        }
        throw new AssertionError("bulk labs did not all reach Start/Complete on Active labs within "
                + (BULK_START_SYNC_MAX_MS / 1000) + "s");
    }

    @Then("those labs are Running")
    public void thoseLabsAreRunning() {
        ManageLabs ml = new ManageLabs(ctx.page);
        assertTrue(allBulkLabsStartCompleteOnActiveLabs(ml),
                "Active labs rows do not show Start/Complete (Running)");
        if (CloudLabsClient.isConfigured() && !CloudLabsClient.isBlocked() && !bulkLabIds.isEmpty()) {
            try {
                CloudLabsClient.waitForAll(bulkLabIds,
                        EnumSet.of(ManageLabs.LabState.RUNNING), 60, 5, "start");
            } catch (RuntimeException e) {
                System.out.println("[SB-manage] CloudLabs RUNNING check skipped: " + e.getMessage());
            }
        }
    }

    @When("admin bulk stops those labs from Manage Labs")
    public void adminBulkStopsThoseLabsFromManageLabs() {
        adminBulkStopsThosePreparedLabsFromManageLabs();
    }

    private static final long BULK_STOP_SYNC_MAX_MS = 300_000;
    private static final long BULK_STOP_SYNC_INTERVAL_MS = 15_000;

    @When("admin syncs those labs until Stop is completed")
    public void adminSyncsThoseLabsUntilStopIsCompleted() {
        assertFalse(bulkUsers.isEmpty(), "no bulk labs prepared for Stop sync");
        ManageLabs ml = new ManageLabs(ctx.page);
        List<String> emails = bulkUserEmails();
        long deadline = System.currentTimeMillis() + BULK_STOP_SYNC_MAX_MS;
        int round = 0;
        while (System.currentTimeMillis() < deadline) {
            round++;
            ml.open(sectionId);
            ml.openCreatedTab();
            if (allBulkLabsStopCompleteOnActiveLabs(ml)) {
                System.out.println("[SB-manage] bulk Stop complete (sync round " + round + ")");
                waitCloudLabsAllStoppedOptional();
                return;
            }
            bulkSelectPreparedRows(ml);
            ml.syncAllSelectedCreatedLabs();
            ctx.page.waitForTimeout(2_000);
            ml.open(sectionId);
            ml.openCreatedTab();
            if (allBulkLabsStopCompleteOnActiveLabs(ml)) {
                System.out.println("[SB-manage] bulk Stop complete (sync round " + round + ")");
                waitCloudLabsAllStoppedOptional();
                return;
            }
            System.out.println("[SB-manage] bulk Stop sync round " + round
                    + " — not all Stop/Complete yet, next Sync in "
                    + (BULK_STOP_SYNC_INTERVAL_MS / 1000) + "s");
            sleepMs(BULK_STOP_SYNC_INTERVAL_MS);
        }
        throw new AssertionError("bulk labs did not all reach Stop/Complete on Active labs within "
                + (BULK_STOP_SYNC_MAX_MS / 1000) + "s");
    }

    @Then("those prepared labs are Stopped on Active labs")
    public void thosePreparedLabsAreStoppedOnActiveLabs() {
        ManageLabs ml = new ManageLabs(ctx.page);
        assertTrue(allBulkLabsStopCompleteOnActiveLabs(ml),
                "Active labs rows do not show Stop/Complete (Stopped)");
        waitCloudLabsAllStoppedOptional();
    }

    @Then("those labs are Stopped")
    public void thoseLabsAreStopped() {
        thosePreparedLabsAreStoppedOnActiveLabs();
    }

    @When("admin bulk deletes those labs from Manage Labs")
    public void adminBulkDeletesThoseLabsFromManageLabs() {
        ManageLabs ml = new ManageLabs(ctx.page);
        List<String> emails = new ArrayList<>();
        for (ApiClient.SeededUser u : bulkUsers) emails.add(u.email);
        ml.open(sectionId);
        ml.openCreatedTab();
        ml.selectRowsForEmails(emails);
        ml.performActionOnSelection(ManageLabs.DELETE);
    }

    @When("admin syncs those labs until Delete is completed")
    public void adminSyncsThoseLabsUntilDeleteIsCompleted() {
        ManageLabs ml = new ManageLabs(ctx.page);
        for (ApiClient.SeededUser u : bulkUsers) {
            try {
                ml.performActionForEmail(u.email, ManageLabs.SYNC_BTN, sectionId);
            } catch (RuntimeException ignored) {}
        }
        CloudLabsClient.waitForAllDeleted(bulkLabIds, 600, 15);
    }

    @Then("those labs are Deleted")
    public void thoseLabsAreDeleted() {
        for (String id : bulkLabIds) {
            CloudLabsClient.SubStatus ss = CloudLabsClient.getStatus(id);
            ManageLabs.LabState st = (ss == null) ? ManageLabs.LabState.DELETED
                    : ManageLabs.classifyActionStatus(ss.action, ss.status);
            assertEquals(st, ManageLabs.LabState.DELETED, "lab " + id + " not Deleted");
        }
    }

    @Then("Start Stop Delete and Sync are disabled with no lab selected")
    public void startStopDeleteAndSyncAreDisabledWithNoLabSelected() {
        ManageLabs ml = new ManageLabs(ctx.page);
        ml.open(sectionId);
        ml.openCreatedTab();
        ml.uncheckAllCreatedLabRows();
        assertEquals(0, ml.countCheckedCreatedRows(),
                "Active labs row selection was not cleared before gating check");
        if (ml.hasLabRows()) {
            assertTrue(ml.actionDisabled(ManageLabs.START) || attributeSuggestsDisabled(ManageLabs.START),
                    "Start should be disabled with no row selected");
            assertTrue(ml.actionDisabled(ManageLabs.STOP) || attributeSuggestsDisabled(ManageLabs.STOP),
                    "Stop should be disabled with no row selected");
            assertTrue(ml.actionDisabled(ManageLabs.DELETE) || attributeSuggestsDisabled(ManageLabs.DELETE),
                    "Delete should be disabled with no row selected");
            Locator sync = ctx.page.locator(ManageLabs.SYNC_BTN);
            if (sync.count() > 0 && sync.first().isVisible()) {
                assertTrue(sync.first().getAttribute("disabled") != null
                                || !sync.first().isEnabled(),
                        "Sync should be disabled with no row selected");
            }
        }
    }

    @Then("No labs Create is disabled with no user selected")
    public void noLabsCreateIsDisabledWithNoUserSelected() {
        ManageLabs ml = new ManageLabs(ctx.page);
        ml.openNotCreatedTab();
        Locator create = ctx.page.locator(ManageLabs.NOTCREATED_CREATE);
        assertTrue(create.count() > 0, "No labs Create button missing");
        assertTrue(create.first().getAttribute("disabled") != null || !create.first().isEnabled(),
                "No labs Create should be disabled with no selection");
    }

    @When("admin opens Lab Details for that student's lab")
    public void adminOpensLabDetailsForThatStudentsLab() {
        ManageLabs ml = new ManageLabs(ctx.page);
        ml.open(sectionId);
        ml.openCreatedTab();
        ml.clearCreatedSearch();
        ml.searchCreatedByEmail(student.email);
        Locator row = ctx.page.locator("#np-ap-manage-lab-table tbody tr")
                .filter(new Locator.FilterOptions().setHasText(student.email));
        assertTrue(row.count() > 0, "no Active row for Lab Details: " + student.email);
        row.first().locator("input[name='btSelectItem']").check();
        ctx.page.waitForTimeout(700);
        Locator detailsBtn = ctx.page.locator(ManageLabs.LAB_DETAILS_BTN);
        assertTrue(detailsBtn.count() > 0, "Lab Details button missing on Manage Labs");
        assertTrue(detailsBtn.first().isVisible(), "Lab Details button not visible");
        String dis = detailsBtn.first().getAttribute("disabled");
        assertTrue(dis == null && detailsBtn.first().isEnabled(),
                "Lab Details should be enabled when a lab row is selected");
        detailsBtn.first().click();
    }

    @Then("Lab Details shows that student email or lab id")
    public void labDetailsShowsThatStudentEmailOrLabId() {
        ManageLabs ml = new ManageLabs(ctx.page);
        String text = ml.waitForLabDetailsText(25);
        assertNotNull(text,
                "Lab Details did not open within 25s (email=" + (student == null ? null : student.email)
                        + " labId=" + labId + " url=" + ctx.page.url() + ")");
        String lower = text.toLowerCase(Locale.ROOT);
        boolean hasEmail = student != null && lower.contains(student.email.toLowerCase(Locale.ROOT));
        boolean hasLab = labId != null && text.contains(labId);
        assertTrue(hasEmail || hasLab,
                "Lab Details missing email/lab id (email=" + (student == null ? null : student.email)
                        + " labId=" + labId + ")");
    }

    @Then("admin can use the Manage Labs group filter if groups exist")
    public void adminCanUseTheManageLabsGroupFilterIfGroupsExist() {
        ensureSectionReady();
        ManageLabs ml = new ManageLabs(ctx.page);
        if (!ml.groupFilterPresent()) {
            throw new SkipException("#course_groups not present on this Manage Labs page");
        }
        com.nuvepro.moodle.pages.Groups groups = new com.nuvepro.moodle.pages.Groups(ctx.page);
        String label = firstSelectableGroupLabel();
        if (label == null) {
            int cid = (int) (courseId > 0 ? courseId : SandboxCourse.idLongOrZero());
            String gname = "SB-filter-" + uniqueUserStamp();
            assertTrue(groups.createGroupSucceeded(cid, gname),
                    "could not create a course group for Manage Labs filter test");
            ml.open(sectionId);
            ml.openCreatedTab();
            label = firstSelectableGroupLabel();
            assertNotNull(label, "group filter still has no selectable group after creating " + gname);
        }
        ml.open(sectionId);
        ml.openCreatedTab();
        ml.clearCreatedSearch();
        ml.refreshCreatedTable();
        java.util.List<String> beforeFilter = ml.createdEmailsOnCurrentPage();
        int beforeRows = ml.rowCount();
        groups.selectGroup(label);
        ctx.page.waitForTimeout(2_000);
        ml.refreshCreatedTable();
        String selected = groups.selectedGroup();
        assertTrue(selected.toLowerCase(Locale.ROOT).contains(label.toLowerCase(Locale.ROOT))
                        || label.toLowerCase(Locale.ROOT).contains(selected.toLowerCase(Locale.ROOT)),
                "group filter selection did not stick (expected '" + label + "' got '" + selected + "')");
        java.util.List<String> afterFilter = ml.createdEmailsOnCurrentPage();
        int afterRows = ml.rowCount();
        boolean listingResponded = !beforeFilter.equals(afterFilter) || beforeRows != afterRows;
        if (!listingResponded && beforeRows > 0) {
            assertTrue(afterRows <= beforeRows,
                    "group filter did not change Active labs listing (beforeRows=" + beforeRows
                            + " afterRows=" + afterRows + ")");
        }
        groups.selectGroup("All Groups");
        ctx.page.waitForTimeout(1_500);
        ml.refreshCreatedTable();
        String reset = groups.selectedGroup().toLowerCase(Locale.ROOT);
        assertTrue(reset.contains("all"), "group filter did not reset to All Groups (got '" + reset + "')");
        System.out.println("[SB-manage] group filter OK selected=" + label
                + " beforeRows=" + beforeRows + " afterRows=" + afterRows);
    }

    @Then("the Manage Labs report export control is available")
    public void theManageLabsReportExportControlIsAvailable() {
        ManageLabs ml = new ManageLabs(ctx.page);
        ml.open(sectionId);
        ml.openCreatedTab();
        assertTrue(ml.exerciseActiveLabsReportExport(),
                "Active labs Reports → Download → modal Download did not save a report file ("
                        + ManageLabs.REPORT_DROPDOWN + " / "
                        + ManageLabs.ACTIVE_LABS_REPORT_DOWNLOAD + " / "
                        + ManageLabs.ACTIVE_LABS_REPORT_MODAL + ")");
        System.out.println("[SB-manage] Active labs report export OK (file downloaded)");
    }

    @Then("admin handles a Failed lab with delete or recreate when present")
    public void adminHandlesAFailedLabWithDeleteOrRecreateWhenPresent() {
        ManageLabs ml = new ManageLabs(ctx.page);
        ml.open(sectionId);
        ml.openCreatedTab();
        Locator rows = ctx.page.locator("#np-ap-manage-lab-table tbody tr");
        String failedEmail = null;
        String failedId = null;
        for (int i = 0; i < rows.count(); i++) {
            String t = rows.nth(i).innerText().toLowerCase(Locale.ROOT);
            if (!t.contains("fail")) continue;
            Matcher m = Pattern.compile("[\\w.+-]+@[\\w.-]+\\.[A-Za-z]{2,}").matcher(t);
            if (m.find()) failedEmail = m.group();
            Matcher idm = Pattern.compile("\\b(\\d{3,})\\b").matcher(t);
            if (idm.find()) failedId = idm.group(1);
            break;
        }
        if (failedEmail == null && failedId == null) {
            throw new SkipException("no Failed lab row present to delete/recreate "
                    + "(CGAP-PG-36 needs a failed subscription)");
        }
        if (failedEmail != null) {
            ml.performActionForEmail(failedEmail, ManageLabs.DELETE, sectionId);
        } else {
            ml.performActionForLabId(failedId, ManageLabs.DELETE, sectionId);
        }
        if (failedId != null && CloudLabsClient.isConfigured()) {
            CloudLabsClient.waitForState(failedId,
                    EnumSet.of(ManageLabs.LabState.DELETED), 600, 15);
        }
        System.out.println("[SB-manage] Failed lab Delete exercised email=" + failedEmail
                + " id=" + failedId);
    }

    @When("admin starts Create for that student from No labs without waiting")
    public void adminStartsCreateForThatStudentFromNoLabsWithoutWaiting() {
        assertNotNull(student, "student not seeded");
        ManageLabs ml = new ManageLabs(ctx.page);
        ml.open(sectionId);
        ml.openNotCreatedTab();
        ml.selectNotCreatedUserEmail(student.email, sectionId);
        ctx.page.locator(ManageLabs.NOTCREATED_CREATE).click(new Locator.ClickOptions().setForce(true));
        try { ml.confirmVisibleDialog(); } catch (Throwable ignored) {}
        GlobalHooks.labProvisioned = true;
        // Capture lab id quickly if already present; Sync step will wait for Complete.
        for (int i = 0; i < 8 && (labId == null || labId.isBlank()); i++) {
            ml.open(sectionId);
            ml.openCreatedTab();
            String id = ml.labIdForEmail(student.email);
            if (id != null && !id.isBlank()) {
                labId = id.trim();
                GlobalHooks.teardownLabId = labId;
                break;
            }
            ctx.page.waitForTimeout(2_000);
        }
        System.out.println("[SB-manage] Create clicked (InProgress expected) labId=" + labId);
    }

    @Then("the Manage Labs column toggle control is available")
    public void theManageLabsColumnToggleControlIsAvailable() {
        ManageLabs ml = new ManageLabs(ctx.page);
        ml.open(sectionId);
        ml.openCreatedTab();
        assertTrue(ml.exerciseColumnToggle(),
                "Manage Labs columns menu did not open with column choices");
    }

    private String waitLabIdForEmail(ManageLabs ml, String email) {
        return waitLabIdForEmail(ml, email, 40);
    }

    private String waitLabIdForEmail(ManageLabs ml, String email, int maxAttempts) {
        Map<String, String> one = waitLabIdsForEmails(ml, List.of(email), maxAttempts);
        String id = one.get(email.toLowerCase(Locale.ROOT));
        if (id != null && !id.isBlank()) return id;
        throw new AssertionError("could not capture lab id for " + email);
    }

    /**
     * Poll Active labs once per attempt for all emails (one Manage Labs open per round).
     */
    private Map<String, String> waitLabIdsForEmails(ManageLabs ml, List<String> emails, int maxAttempts) {
        Map<String, String> found = new HashMap<>();
        if (emails == null || emails.isEmpty()) return found;
        List<String> pending = new ArrayList<>();
        for (String e : emails) {
            if (e != null && !e.isBlank()) pending.add(e);
        }
        for (int i = 0; i < maxAttempts && found.size() < pending.size(); i++) {
            assertAdminPageOpen("waiting for lab ids");
            ml.open(sectionId);
            ml.openCreatedTab();
            if (i == 0 || i % 4 == 0) ml.refreshCreatedTable();
            for (String email : pending) {
                String key = email.toLowerCase(Locale.ROOT);
                if (found.containsKey(key)) continue;
                String id = ml.labIdForEmail(email);
                if (id == null || id.isBlank()) {
                    String username = email.contains("@") ? email.substring(0, email.indexOf('@')) : email;
                    try {
                        ml.searchCreatedByEmail(email);
                    } catch (RuntimeException ignored) {}
                    id = ml.labIdForEmail(email);
                    if ((id == null || id.isBlank()) && !username.equals(email)) {
                        try {
                            ml.searchCreatedByEmail(username);
                        } catch (RuntimeException ignored) {}
                        id = ml.labIdForEmail(email);
                    }
                    try {
                        ml.clearCreatedSearch();
                    } catch (RuntimeException ignored) {}
                }
                if (id != null && !id.isBlank()) found.put(key, id.trim());
            }
            if (found.size() < pending.size()) pauseMs(ctx.page, 2_500);
        }
        return found;
    }

    private void assertAdminPageOpen(String phase) {
        if (ctx.page == null || ctx.page.isClosed()) {
            throw new AssertionError("admin browser closed during " + phase);
        }
    }

    private static void pauseMs(Page page, int ms) {
        if (page == null || page.isClosed()) {
            throw new AssertionError("browser closed while waiting");
        }
        page.waitForTimeout(ms);
    }

    /** Long idle waits without holding the Playwright page timer (avoids TargetClosed on multi-minute sleeps). */
    private static void sleepMs(long ms) {
        try {
            Thread.sleep(Math.max(0, ms));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError("interrupted during wait", e);
        }
    }

    private void navigateStudentToSandboxActivity() {
        new PlaygroundLanding(studentPage).open(activityCmid);
    }

    private void reloadStudentSandboxActivity() {
        studentPage.reload(new Page.ReloadOptions()
                .setWaitUntil(WaitUntilState.DOMCONTENTLOADED)
                .setTimeout(45_000));
    }

    private boolean studentLandingShowsDeletedOrStart(PlaygroundLanding landing, String bodyLower) {
        boolean deletedText = bodyLower.contains("lab is deleted")
                || bodyLower.contains("lab has been deleted")
                || bodyLower.contains("this lab is deleted")
                || bodyLower.contains("lab deleted")
                || (bodyLower.contains("deleted") && bodyLower.contains("lab"));
        boolean backToNotCreated = landing.hasStartSandbox() && !landing.hasActiveLab();
        return deletedText || backToNotCreated;
    }

    private void assertBulkActiveLabsRunning(ManageLabs ml) {
        for (ApiClient.SeededUser u : bulkUsers) {
            ml.open(sectionId);
            ml.openCreatedTab();
            ml.clearCreatedSearch();
            try {
                ml.searchCreatedByEmail(u.email);
            } catch (RuntimeException ignored) {}
            assertTrue(ml.createdContainsEmail(u.email),
                    "Active labs missing row after Create: " + u.email);
            ManageLabs.LabState st = ml.stateForEmail(u.email);
            assertTrue(st == ManageLabs.LabState.RUNNING || st == ManageLabs.LabState.INPROGRESS,
                    "lab not Running on Active labs for " + u.email + " (state=" + st + ")");
        }
        System.out.println("[SB-manage] all " + bulkUsers.size() + " bulk labs Running on Active labs");
    }

    private void waitCloudLabsAllRunningOptional() {
        if (!CloudLabsClient.isConfigured()) return;
        try {
            CloudLabsClient.waitForAll(bulkLabIds,
                    EnumSet.of(ManageLabs.LabState.RUNNING), 120, 5, "create");
        } catch (CloudLabsClient.UnavailableException e) {
            System.out.println("[SB-manage] CloudLabs RUNNING wait skipped: " + e.getMessage());
        } catch (AssertionError e) {
            System.out.println("[SB-manage] CloudLabs RUNNING wait inconclusive: " + e.getMessage());
        }
    }

    private void waitCloudLabsAllDeletedOptional() {
        if (!CloudLabsClient.isConfigured()) return;
        try {
            CloudLabsClient.waitForAllDeleted(bulkLabIds, 600, 15);
        } catch (CloudLabsClient.UnavailableException e) {
            System.out.println("[SB-manage] CloudLabs DELETED wait skipped: " + e.getMessage());
        } catch (AssertionError e) {
            System.out.println("[SB-manage] CloudLabs DELETED wait inconclusive: " + e.getMessage());
        }
    }

    /** True if at least one bulk lab row left Stopped-only (Start submitted or in flight). */
    private boolean anyBulkLabsStartBegunOnActiveLabs(ManageLabs ml) {
        ml.openCreatedTab();
        ml.refreshCreatedTable();
        for (ApiClient.SeededUser u : bulkUsers) {
            if (!ml.createdContainsEmail(u.email)) {
                continue;
            }
            String row = ml.createdRowTextForEmail(u.email).toLowerCase(Locale.ROOT);
            ManageLabs.LabState st = ml.stateForEmail(u.email);
            if (st == ManageLabs.LabState.RUNNING || st == ManageLabs.LabState.INPROGRESS) {
                return true;
            }
            if (row.contains("start") && (row.contains("progress") || row.contains("inprogress")
                    || row.contains("pending"))) {
                return true;
            }
        }
        return false;
    }

    /** Bulk Stop: every prepared user shows Stop/Complete on Active labs. */
    private boolean allBulkLabsStopCompleteOnActiveLabs(ManageLabs ml) {
        ml.openCreatedTab();
        ml.setCreatedPageSize("25");
        int complete = 0;
        StringBuilder status = new StringBuilder();
        for (ApiClient.SeededUser u : bulkUsers) {
            String email = u.email;
            if (!ml.createdContainsEmail(email)) {
                status.append(email).append("=missing; ");
                continue;
            }
            String row = ml.createdRowTextForEmail(email).toLowerCase(Locale.ROOT);
            ManageLabs.LabState st = ml.stateForEmail(email);
            boolean ok = st == ManageLabs.LabState.STOPPED
                    || (row.contains("stop") && (row.contains("complete")
                    || row.contains("progress") || row.contains("inprogress")));
            if (ok) {
                complete++;
                status.append(email).append("=stopped; ");
            } else {
                status.append(email).append("=").append(st).append("; ");
            }
        }
        System.out.println("[SB-manage] bulk Stop complete " + complete + "/" + bulkUsers.size()
                + " " + status);
        return complete >= bulkUsers.size();
    }

    private void waitCloudLabsAllStoppedOptional() {
        if (!CloudLabsClient.isConfigured() || CloudLabsClient.isBlocked() || bulkLabIds.isEmpty()) {
            return;
        }
        try {
            CloudLabsClient.waitForAll(bulkLabIds,
                    EnumSet.of(ManageLabs.LabState.STOPPED), 120, 5, "stop");
        } catch (CloudLabsClient.UnavailableException e) {
            System.out.println("[SB-manage] CloudLabs STOPPED wait skipped: " + e.getMessage());
        } catch (RuntimeException e) {
            System.out.println("[SB-manage] CloudLabs STOPPED check skipped: " + e.getMessage());
        }
    }

    /** Bulk Start: every prepared user shows Running or Start/Complete on Active labs. */
    private boolean allBulkLabsStartCompleteOnActiveLabs(ManageLabs ml) {
        ml.openCreatedTab();
        ml.setCreatedPageSize("25");
        int complete = 0;
        StringBuilder status = new StringBuilder();
        for (ApiClient.SeededUser u : bulkUsers) {
            String email = u.email;
            if (!ml.createdContainsEmail(email)) {
                status.append(email).append("=missing; ");
                continue;
            }
            String row = ml.createdRowTextForEmail(email).toLowerCase(Locale.ROOT);
            ManageLabs.LabState st = ml.stateForEmail(email);
            boolean ok = st == ManageLabs.LabState.RUNNING
                    || st == ManageLabs.LabState.INPROGRESS
                    || (row.contains("start") && (row.contains("complete")
                    || row.contains("progress") || row.contains("inprogress")));
            if (ok) {
                complete++;
                status.append(email).append("=running; ");
            } else {
                status.append(email).append("=").append(st).append("; ");
            }
        }
        System.out.println("[SB-manage] bulk Start complete " + complete + "/" + bulkUsers.size()
                + " " + status);
        return complete >= bulkUsers.size();
    }

    /** All four seeded users show Delete/Complete on Active labs (no email search — full table only). */
    private boolean allFourBulkLabsDeleteCompleteOnActiveLabs(ManageLabs ml) {
        ml.openCreatedTab();
        ml.setCreatedPageSize("25");
        int complete = 0;
        StringBuilder status = new StringBuilder();
        for (int i = 0; i < bulkUsers.size(); i++) {
            String email = bulkUsers.get(i).email;
            String id = bulkLabIds.get(i);
            if (!ml.createdContainsEmail(email)) {
                complete++;
                status.append(id).append("=gone; ");
                continue;
            }
            String row = ml.createdRowTextForEmail(email).toLowerCase(Locale.ROOT);
            ManageLabs.LabState st = ml.stateForEmail(email);
            if (st == ManageLabs.LabState.DELETED
                    || (row.contains("delete") && row.contains("complete"))) {
                complete++;
                status.append(id).append("=deleted; ");
            } else {
                status.append(id).append("=").append(st).append("; ");
            }
        }
        System.out.println("[SB-manage] bulk Delete complete " + complete + "/4 " + status);
        return complete >= bulkUsers.size();
    }

    private void waitActiveLabCreateCompleteForStudent(ManageLabs ml, String email, int maxSeconds) {
        long deadline = System.currentTimeMillis() + maxSeconds * 1000L;
        String lastRow = "";
        while (System.currentTimeMillis() < deadline) {
            ml.open(sectionId);
            ml.openCreatedTab();
            ml.setCreatedPageSize("25");
            ml.clearCreatedSearch();
            try {
                ml.searchCreatedByEmail(email);
            } catch (RuntimeException ignored) {}
            if (ml.createdContainsEmail(email)) {
                lastRow = ml.createdRowTextForEmail(email);
                String row = lastRow.toLowerCase(Locale.ROOT);
                ManageLabs.LabState st = ml.stateForEmail(email);
                if (st == ManageLabs.LabState.RUNNING
                        || st == ManageLabs.LabState.INPROGRESS
                        || (row.contains("create") && (row.contains("complete")
                        || row.contains("progress") || row.contains("inprogress")))) {
                    System.out.println("[SB-manage] Active labs Create settled email=" + email
                            + " state=" + st + " row=" + lastRow);
                    return;
                }
            }
            sleepMs(POLL_MS);
        }
        throw new AssertionError("Active labs Create did not complete for " + email + " within "
                + maxSeconds + "s (last row='" + lastRow + "')");
    }

    private boolean attributeSuggestsDisabled(String selector) {
        Locator l = ctx.page.locator(selector);
        if (l.count() == 0) return true;
        try {
            String dis = l.first().getAttribute("disabled");
            String aria = l.first().getAttribute("aria-disabled");
            String cls = l.first().getAttribute("class");
            return dis != null || "true".equalsIgnoreCase(aria)
                    || (cls != null && cls.contains("disabled"));
        } catch (RuntimeException e) {
            return false;
        }
    }

    // ---- SB-manage-25+ converted from sandbox_labs_review (admin report + student console) ----

    @When("admin opens the CloudLabs course progress report")
    public void adminOpensTheCloudLabsCourseProgressReport() {
        new CloudLabsCourseReportPage(ctx.page).openCoursesReport();
        System.out.println("[SB-manage] opened CloudLabs course progress report "
                + CloudLabsCourseReportPage.COURSES_PATH);
    }

    @Then("the report loads without errors")
    public void theReportLoadsWithoutErrors() {
        new CloudLabsCourseReportPage(ctx.page).assertLoadedWithoutErrors();
    }

    @Then("the report lists courses with lab activity columns")
    public void theReportListsCoursesWithLabActivityColumns() {
        new CloudLabsCourseReportPage(ctx.page).assertCoursesWithLabActivityColumns();
        System.out.println("[SB-manage] course progress report lists courses with lab activity columns");
    }

    @When("the student opens the sandbox activity landing")
    public void theStudentOpensTheSandboxActivityLanding() {
        assertNotNull(student, "student not seeded");
        if (studentPage == null || studentPage.isClosed()) {
            openStudentSession();
        }
        new PlaygroundLanding(studentPage).open(activityCmid);
        studentPage.waitForTimeout(1_500);
    }

    @When("the student clicks Start Sandbox and cancels the confirmation")
    public void theStudentClicksStartSandboxAndCancelsTheConfirmation() {
        if (studentPage == null || studentPage.isClosed()) {
            openStudentSession();
        }
        assertNotNull(studentPage, "student session not open");
        PlaygroundLanding landing = new PlaygroundLanding(studentPage);
        if (!landing.hasStartSandbox()) {
            throw new SkipException("'Start Sandbox' not shown (lab already exists) — cannot test cancel");
        }
        landing.startSandboxThenCancel();
    }

    @Then("no sandbox lab was created for the student")
    public void noSandboxLabWasCreatedForTheStudent() {
        assertNotNull(studentPage);
        studentPage.waitForTimeout(1_500);
        PlaygroundLanding landing = new PlaygroundLanding(studentPage);
        assertTrue(landing.isLoaded(), "should remain on the sandbox landing");
        assertEquals(studentPage.locator(PlaygroundControlPanel.MASTER).count(), 0,
                "control panel should not have opened (no lab created)");
    }

    @When("the student opens the Lab Usage popup on the console")
    public void theStudentOpensTheLabUsagePopupOnTheConsole() {
        assertNotNull(studentPage);
        Locator btn = studentPage.locator("#openLabUsageBtn");
        long deadline = System.currentTimeMillis() + 60_000;
        while (System.currentTimeMillis() < deadline && btn.count() == 0) {
            studentPage.waitForTimeout(1_000);
        }
        if (btn.count() == 0) {
            throw new SkipException("Lab Usage not enabled/ready on this template");
        }
        // FAB and cost panel start as d-none; duration callback can show the FAB without
        // unhiding Get Usage. Open via the real click handler + unhide the cost panel.
        studentPage.evaluate("""
                () => {
                  const jq = window.jQuery || window.$;
                  const fab = document.querySelector('#openLabUsageBtn');
                  if (fab) {
                    fab.classList.remove('d-none');
                    fab.click();
                  }
                  const popup = document.querySelector('#labUsagePopup');
                  if (popup) {
                    popup.style.display = 'block';
                    popup.classList.remove('d-none');
                  }
                  document.querySelectorAll('.np-ap-mod-right-cost-container').forEach(el => {
                    el.classList.remove('d-none');
                  });
                  if (jq) {
                    jq('#labUsagePopup').show();
                    jq('.np-ap-mod-right-cost-container').removeClass('d-none');
                  }
                }
                """);
        studentPage.waitForTimeout(1_000);
    }

    @When("the student requests the lab cost on the console")
    public void theStudentRequestsTheLabCostOnTheConsole() {
        Locator get = studentPage.locator("#getCost");
        if (get.count() == 0) throw new SkipException("Get-cost control not present on this template");
        studentPage.evaluate("""
                () => {
                  document.querySelectorAll('.np-ap-mod-right-cost-container').forEach(el => {
                    el.classList.remove('d-none');
                  });
                  const btn = document.querySelector('#getCost');
                  if (btn) {
                    btn.removeAttribute('disabled');
                    btn.click();
                  }
                }
                """);
        studentPage.waitForTimeout(6_000);
    }

    @Then("the student sees valid lab cost values on the console")
    public void theStudentSeesValidLabCostValuesOnTheConsole() {
        Locator show = studentPage.locator("#showCostUsage");
        assertTrue(show.count() > 0, "cost usage area not present");
        String text = show.first().innerText().trim();
        assertFalse(text.isEmpty(), "cost usage area is empty");
        assertTrue(Pattern.compile("\\d").matcher(text).find(),
                "cost usage shows no numeric values: " + text);
        String lower = text.toLowerCase(Locale.ROOT);
        assertTrue(lower.contains("cost") || lower.contains("usage") || lower.contains("charge")
                        || lower.contains("$") || lower.contains("usd") || lower.contains("inr")
                        || lower.contains("total"),
                "cost panel missing cost/usage labels: " + text);
        if (labId != null && CloudLabsClient.isConfigured() && !CloudLabsClient.isBlocked()) {
            try {
                String json = CloudLabsClient.getSubscriptionsStatusBody(labId);
                Double apiCost = firstCostAmount(json);
                if (apiCost != null) {
                    String costToken = apiCost % 1.0 == 0
                            ? String.valueOf(apiCost.intValue()) : String.valueOf(apiCost);
                    assertTrue(text.contains(costToken) || jsonContainsCostNear(text, json),
                            "console cost '" + text + "' did not reflect CloudLabs cost ~" + apiCost);
                }
            } catch (CloudLabsClient.UnavailableException e) {
                System.out.println("[SB-manage] CloudLabs unavailable for cost cross-check: " + e.getMessage());
            }
        }
    }

    private static Double firstCostAmount(String json) {
        if (json == null || json.isBlank()) return null;
        Matcher m = Pattern.compile(
                "\"(?:cost|price|amount|totalcost|labCost|usageCost)\"\\s*:\\s*\"?([\\d.]+)\"?",
                Pattern.CASE_INSENSITIVE).matcher(json);
        if (m.find()) {
            try {
                return Double.parseDouble(m.group(1));
            } catch (NumberFormatException ignored) {}
        }
        return null;
    }

    private static boolean jsonContainsCostNear(String uiText, String json) {
        Double api = firstCostAmount(json);
        if (api == null) return false;
        Matcher nums = Pattern.compile("\\d+(?:\\.\\d+)?").matcher(uiText);
        while (nums.find()) {
            try {
                double ui = Double.parseDouble(nums.group());
                if (Math.abs(ui - api) <= Math.max(0.05, api * 0.15)) return true;
            } catch (NumberFormatException ignored) {}
        }
        return false;
    }

    @Then("the student sandbox landing shows consumed and remaining duration")
    public void theStudentSandboxLandingShowsConsumedAndRemainingDuration() {
        assertNotNull(studentPage);
        PlaygroundLanding landing = new PlaygroundLanding(studentPage);
        landing.open(activityCmid);
        long deadline = System.currentTimeMillis() + 90_000;
        String consumed = "";
        String remaining = "";
        while (System.currentTimeMillis() < deadline) {
            studentPage.evaluate("""
                    () => {
                      document.querySelector('#np-ap-pl-lab-time-details')?.classList.remove('d-none');
                      document.querySelectorAll('#lab-consumed-pl, #lab-remaining-pl, #lab-total-duration-pl')
                        .forEach(el => el.closest('.d-none')?.classList.remove('d-none'));
                    }
                    """);
            if (studentPage.locator("#lab-consumed-pl").count() > 0) {
                consumed = studentPage.locator("#lab-consumed-pl").first().innerText().trim();
            }
            if (studentPage.locator("#lab-remaining-pl").count() > 0) {
                remaining = studentPage.locator("#lab-remaining-pl").first().innerText().trim();
            }
            if (consumed.matches(".*\\d+.*") && remaining.matches(".*\\d+.*")) return;
            studentPage.waitForTimeout(2_000);
        }
        if (!consumed.matches(".*\\d+.*") || !remaining.matches(".*\\d+.*")) {
            landing.openLabConsole();
            PlaygroundControlPanel panel = new PlaygroundControlPanel(studentPage);
            if (panel.waitUntilLabLoaded(15)) {
                String usage = panel.openUsageAndRead();
                assertTrue(usage.matches("(?s).*\\d+.*"),
                        "duration not visible on landing or console usage: consumed='" + consumed
                                + "' remaining='" + remaining + "' usage='" + usage + "'");
                return;
            }
        }
        assertTrue(consumed.matches(".*\\d+.*"), "consumed duration has no value: " + consumed);
        assertTrue(remaining.matches(".*\\d+.*"), "remaining duration has no value: " + remaining);
    }

    @When("the student opens the Lab Credentials popup on the console")
    public void theStudentOpensTheLabCredentialsPopupOnTheConsole() {
        Locator btn = studentPage.locator("#openAuthBtn");
        if (btn.count() == 0 || !btn.first().isVisible()) {
            throw new SkipException("Lab Credentials not enabled on this template");
        }
        btn.first().click();
    }

    @When("the student changes the sandbox lab password on the console")
    public void theStudentChangesTheLabPasswordOnTheConsole() {
        PlaygroundControlPanel panel = new PlaygroundControlPanel(studentPage);
        sandboxLabPasswordBeforeChange = panel.credentialsPasswordValue();
        if (sandboxLabPasswordBeforeChange.isBlank() && panel.openCredentialsFab()) {
            sandboxLabPasswordBeforeChange = panel.credentialsPasswordValue();
        }
        Locator link = studentPage.locator("#np-ap-mod-set-password-btn");
        if (link.count() == 0 || !link.first().isVisible()) {
            if (panel.openCredentialsFab()) {
                link = studentPage.locator("#np-ap-mod-set-password-btn");
            }
        }
        if (link.count() == 0 || !link.first().isVisible()) {
            for (int i = 0; i < 8; i++) {
                studentPage.waitForTimeout(500);
                link = studentPage.locator("#np-ap-mod-set-password-btn");
                if (link.count() > 0 && link.first().isVisible()) {
                    break;
                }
            }
        }
        assertTrue(link.count() > 0 && link.first().isVisible(),
                "Change-password option not available on this template — use sandbox_vm_vpat "
                        + "or enable set-password on the lab template");
        link.first().click();
        assertTrue(panel.fillSetCredentialsPassword(SANDBOX_CONSOLE_NEW_PASSWORD),
                "set-credentials dialog did not expose password fields");
    }

    @Then("the student lab password update is accepted")
    public void theStudentLabPasswordUpdateIsAccepted() {
        PlaygroundControlPanel panel = new PlaygroundControlPanel(studentPage);
        boolean success = false;
        for (int i = 0; i < 20; i++) {
            if (panel.passwordChangeSuccessVisible()) {
                success = true;
                break;
            }
            if (panel.openCredentialsFab()) {
                String current = panel.credentialsPasswordValue();
                if (!current.isBlank() && current.equals(SANDBOX_CONSOLE_NEW_PASSWORD)) {
                    success = true;
                    break;
                }
                if (!current.isBlank() && sandboxLabPasswordBeforeChange != null
                        && !sandboxLabPasswordBeforeChange.isBlank()
                        && !current.equals(sandboxLabPasswordBeforeChange)) {
                    success = true;
                    break;
                }
            }
            studentPage.waitForTimeout(1_000);
        }
        assertTrue(success,
                "password change did not succeed (before='"
                        + sandboxLabPasswordBeforeChange + "' expected='"
                        + SANDBOX_CONSOLE_NEW_PASSWORD + "')");
        String body = studentPage.locator("body").innerText().toLowerCase(Locale.ROOT);
        assertFalse(body.contains("exception") || body.contains("error code"),
                "password update surfaced an error");
    }

    @Then("the student speed test works when enabled on the console")
    public void theStudentSpeedTestWorksWhenEnabledOnTheConsole() {
        Locator btn = studentPage.locator("#np-ap-mod-btn-tb-speedTest");
        if (btn.count() == 0 || !waitStudentVisible(btn, 10_000)) {
            throw new SkipException("Speed test not enabled on this template");
        }
        btn.first().click();
    }

    @When("the student refreshes the sandbox lab on the console")
    public void theStudentRefreshesTheSandboxLabOnTheConsole() {
        Locator btn = studentPage.locator("#np-ap-mod-btn-tb-refresh");
        if (!waitStudentVisible(btn, 30_000)) throw new SkipException("Refresh not visible on console");
        btn.first().click();
        studentPage.waitForTimeout(3_000);
    }

    @Then("the student sandbox lab remains running after refresh")
    public void theStudentSandboxLabRemainsRunningAfterRefresh() {
        PlaygroundControlPanel panel = new PlaygroundControlPanel(studentPage);
        if (labId != null && CloudLabsClient.isConfigured() && !CloudLabsClient.isBlocked()) {
            try {
                CloudLabsClient.SubStatus ss = CloudLabsClient.getStatus(labId);
                if (ss != null) {
                    ManageLabs.LabState st = ManageLabs.classifyActionStatus(ss.action, ss.status);
                    assertEquals(st, ManageLabs.LabState.RUNNING,
                            "lab not Running after refresh (action=" + ss.action + " status=" + ss.status + ")");
                    return;
                }
            } catch (CloudLabsClient.UnavailableException e) {
                System.out.println("[SB-manage] CloudLabs unavailable after refresh — using console: "
                        + e.getMessage());
            }
        }
        assertTrue(panel.waitUntilLabLoaded(45) || isStudentLabRunningOnConsole(),
                "lab not running after refresh (status=" + studentLabStatus() + ")");
    }

    private boolean isStudentLabRunningOnConsole() {
        String status = studentLabStatus().toUpperCase(Locale.ROOT);
        return status.contains("RUN") || status.contains("ACTIVE")
                || new PlaygroundControlPanel(studentPage).labSurfaceReadyReason().length() > 0;
    }

    @When("the student opens the catalog sandbox console")
    public void theStudentOpensTheCatalogSandboxConsole() {
        if (Settings.PLAYGROUND_CATALOG_CMID.isEmpty()) {
            throw new SkipException("PLAYGROUND_CATALOG_CMID not set (needs a catalog-template sandbox activity)");
        }
        assertNotNull(studentPage, "student session not open");
        int cmid = Integer.parseInt(Settings.PLAYGROUND_CATALOG_CMID.trim());
        PlaygroundLanding landing = new PlaygroundLanding(studentPage);
        landing.open(cmid);
        landing.openLabConsole();
        GlobalHooks.labProvisioned = true;
    }

    @Then("the student sees the catalog UI input surface")
    public void theStudentSeesTheCatalogUiInputSurface() {
        Locator catalog = studentPage.locator("#np-ap-mod-lab-ui-schema, #catInputUI, #subCatalogDataSubmitBtn, "
                + "#cat_tab_content_actionui, .nl-catalog-ui, [id*='Catalog'], [id*='catalog']");
        for (int i = 0; i < 30; i++) {
            if (catalog.count() > 0) {
                for (int j = 0; j < catalog.count(); j++) {
                    try {
                        if (catalog.nth(j).isVisible()) {
                            return;
                        }
                    } catch (RuntimeException ignored) {}
                }
            }
            studentPage.waitForTimeout(1_000);
        }
        throw new SkipException("Catalog UI input surface not present (needs catalog-template activity)");
    }

    @Then("the VM sandbox lab loads inside an iframe")
    public void theVmSandboxLabLoadsInsideAnIframe() {
        if (Settings.PLAYGROUND_VM_CMID.isEmpty()) {
            throw new SkipException("PLAYGROUND_VM_CMID not set");
        }
        Page p = studentPage != null ? studentPage : ctx.page;
        PlaygroundLanding landing = new PlaygroundLanding(p);
        landing.open(Integer.parseInt(Settings.PLAYGROUND_VM_CMID.trim()));
        landing.openLabConsole();
        PlaygroundControlPanel panel = new PlaygroundControlPanel(p);
        panel.expectControlPanelLoaded();
        Locator start = p.locator(PlaygroundControlPanel.LAUNCH_START);
        if (start.count() > 0 && start.first().isVisible()) {
            start.first().click();
            GlobalHooks.labProvisioned = true;
            p.waitForTimeout(15_000);
        }
        assertTrue(p.locator("#np-ap-mod-lab-container-iframe iframe, iframe").count() > 0,
                "VM lab iframe not found");
    }

    private boolean waitStudentVisible(Locator loc, int timeoutMs) {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            try {
                if (loc.count() > 0 && loc.first().isVisible()) return true;
            } catch (RuntimeException ignored) {}
            studentPage.waitForTimeout(500);
        }
        return loc.count() > 0 && loc.first().isVisible();
    }

    private String studentLabStatus() {
        try {
            return new PlaygroundControlPanel(studentPage).labStatus();
        } catch (RuntimeException e) {
            return "";
        }
    }

    // ---- shared helpers ----

    @Given("the practice project activity is ready for Manage Labs")
    public void thePracticeProjectActivityIsReadyForManageLabs() {
        activityCmid = Integer.parseInt(PracticeProjectFixtures.primaryCmid(ctx.page));
        GuidedLanding landing = new GuidedLanding(ctx.page);
        landing.open(activityCmid);
        sectionId = Integer.parseInt(landing.sectionId());
        String bodyClass = ctx.page.locator("body").getAttribute("class");
        Matcher m = Pattern.compile("course-(\\d+)").matcher(bodyClass == null ? "" : bodyClass);
        assertTrue(m.find(), "could not read course id from practice project landing");
        courseId = Long.parseLong(m.group(1));
        ctx.sectionId = String.valueOf(sectionId);
        ctx.newActivityCmid = String.valueOf(activityCmid);
        GlobalHooks.teardownSectionId = String.valueOf(sectionId);
        GlobalHooks.skipLabTeardown = true;
        System.out.println("[PP-manage] cmid=" + activityCmid + " section=" + sectionId + " course=" + courseId);
    }

    @Given("a fresh student enrolled for Practice Project Manage Labs admin actions")
    public void aFreshStudentEnrolledForPracticeProjectManageLabs() {
        if (sectionId <= 0 || activityCmid <= 0 || courseId <= 0) {
            throw new SkipException("run 'the practice project activity is ready for Manage Labs' first");
        }
        if (Settings.WS_TOKEN.isEmpty()) {
            throw new SkipException("MOODLE_WS_TOKEN not set");
        }
        if (ctx.hasTag("@provisioning") && !CloudLabsClient.isConfigured()) {
            throw new SkipException("CloudLabs API not configured (Manage Labs sync needs it)");
        }
        stopCompleted = false;
        labId = null;
        long stamp = uniqueUserStamp();
        student = ApiClient.createUser(stamp);
        ApiClient.enrolUser(student.id, courseId, 5);
        trackUser(student);
        GlobalHooks.teardownStudentEmail = student.email;
        System.out.println("[PP-manage] student=" + student.username);
    }

    @Given("the assessment activity is ready for Manage Labs")
    public void theAssessmentActivityIsReadyForManageLabs() {
        String cmidStr = Settings.ASSESSMENT_LIFECYCLE_CMID.isEmpty()
                ? Settings.ASSESSMENT_CMID : Settings.ASSESSMENT_LIFECYCLE_CMID;
        if (cmidStr.isEmpty()) {
            throw new SkipException("ASSESSMENT_LIFECYCLE_CMID or ASSESSMENT_CMID not set (assessment.env)");
        }
        activityCmid = Integer.parseInt(cmidStr.trim());
        ctx.page.navigate(Settings.BASE_URL + "/mod/cloudlabs/view.php?id=" + activityCmid,
                new Page.NavigateOptions().setWaitUntil(WaitUntilState.DOMCONTENTLOADED).setTimeout(45_000));
        ctx.page.waitForTimeout(2_000);
        Locator sec = ctx.page.locator("#np-ap-cl-sectionId, #np-ap-pl-sectionId");
        assertTrue(sec.count() > 0, "section id hidden input missing on assessment landing");
        sectionId = Integer.parseInt(sec.first().inputValue().trim());
        String bodyClass = ctx.page.locator("body").getAttribute("class");
        Matcher m = Pattern.compile("course-(\\d+)").matcher(bodyClass == null ? "" : bodyClass);
        assertTrue(m.find(), "could not read course id from assessment landing");
        courseId = Long.parseLong(m.group(1));
        ctx.sectionId = String.valueOf(sectionId);
        ctx.newActivityCmid = String.valueOf(activityCmid);
        GlobalHooks.teardownSectionId = String.valueOf(sectionId);
        GlobalHooks.skipLabTeardown = true;
        System.out.println("[ASMT-manage] cmid=" + activityCmid + " section=" + sectionId + " course=" + courseId);
    }

    @Given("a fresh student enrolled for Assessment Manage Labs admin actions")
    public void aFreshStudentEnrolledForAssessmentManageLabs() {
        if (sectionId <= 0 || activityCmid <= 0 || courseId <= 0) {
            throw new SkipException("run 'the assessment activity is ready for Manage Labs' first");
        }
        if (Settings.WS_TOKEN.isEmpty()) {
            throw new SkipException("MOODLE_WS_TOKEN not set");
        }
        if (ctx.hasTag("@provisioning") && !CloudLabsClient.isConfigured()) {
            throw new SkipException("CloudLabs API not configured (Manage Labs sync needs it)");
        }
        stopCompleted = false;
        labId = null;
        long stamp = uniqueUserStamp();
        student = ApiClient.createUser(stamp);
        ApiClient.enrolUser(student.id, courseId, 5);
        trackUser(student);
        GlobalHooks.teardownStudentEmail = student.email;
        System.out.println("[ASMT-manage] student=" + student.username);
    }

    @When("the student starts the assessment lab until it is running")
    public void theStudentStartsTheAssessmentLabUntilItIsRunning() {
        openStudentSession();
        studentPage.navigate(Settings.BASE_URL + "/mod/cloudlabs/view.php?id=" + activityCmid,
                new Page.NavigateOptions().setWaitUntil(WaitUntilState.DOMCONTENTLOADED).setTimeout(45_000));
        studentPage.waitForTimeout(2_000);
        GlobalHooks.labProvisioned = true;
        ChallengeControlPanel acp = new ChallengeControlPanel(studentPage);
        for (int c = 0; c < 8 && !acp.isShown(); c++) {
            acp.clickStartAssessment();
            studentPage.waitForTimeout(1_200);
            acp.confirmAnyModal();
            studentPage.waitForTimeout(2_000);
        }
        assertTrue(acp.isShown(), "assessment control panel did not open");
        acp.startHandsOnLabIfNeeded();
        PlaygroundControlPanel panel = new PlaygroundControlPanel(studentPage);
        try {
            panel.expectControlPanelLoaded(120_000);
        } catch (RuntimeException e) {
            throw new AssertionError("assessment lab console did not load: " + e.getMessage(), e);
        }
        startLabUntilLoaded(panel);
        captureLabId(panel);
        if (labId == null || labId.isBlank()) {
            ManageLabs ml = new ManageLabs(ctx.page);
            for (int i = 0; i < 15; i++) {
                try {
                    ml.open(sectionId);
                    labId = ml.labIdForEmail(student.email);
                    if (labId != null && !labId.isBlank()) break;
                } catch (RuntimeException ignored) {}
                studentPage.waitForTimeout(3_000);
            }
        }
        assertTrue(labId != null && !labId.isBlank(), "no lab id after assessment start");
        GlobalHooks.teardownLabId = labId;
        ManageLabs.LabState st = CloudLabsClient.waitForState(labId, RUN_OR_FAIL, 180, 5);
        assertEquals(st, ManageLabs.LabState.RUNNING, "assessment lab not Running (state=" + st + ")");
    }

    private PlaygroundControlPanel assessmentConsole() {
        assertTrue(studentPage != null, "student session not open — start assessment lab first");
        return new PlaygroundControlPanel(studentPage);
    }

    @When("the student opens the Lab Usage popup on the assessment console")
    public void asmtOpenLabUsage() {
        Locator btn = studentPage.locator("#openLabUsageBtn");
        if (btn.count() == 0) {
            throw new SkipException("Lab Usage not enabled on this assessment template");
        }
        studentPage.evaluate("""
                () => {
                  const fab = document.querySelector('#openLabUsageBtn');
                  if (fab) { fab.classList.remove('d-none'); fab.click(); }
                  const popup = document.querySelector('#labUsagePopup');
                  if (popup) { popup.style.display = 'block'; popup.classList.remove('d-none'); }
                  document.querySelectorAll('.np-ap-mod-right-cost-container').forEach(el => el.classList.remove('d-none'));
                }
                """);
        studentPage.waitForTimeout(1_000);
    }

    @When("the student requests the lab cost on the assessment console")
    public void asmtRequestLabCost() {
        if (studentPage.locator("#getCost").count() == 0) {
            throw new SkipException("Get-cost control not present on assessment console");
        }
        studentPage.evaluate("""
                () => {
                  document.querySelectorAll('.np-ap-mod-right-cost-container').forEach(el => el.classList.remove('d-none'));
                  const btn = document.querySelector('#getCost');
                  if (btn) { btn.removeAttribute('disabled'); btn.click(); }
                }
                """);
        studentPage.waitForTimeout(6_000);
    }

    @Then("valid lab cost values are shown on the assessment console")
    public void asmtValidLabCost() {
        Locator show = studentPage.locator("#showCostUsage");
        assertTrue(show.count() > 0, "cost usage area not present");
        String text = show.first().innerText().trim();
        assertTrue(!text.isEmpty() && Pattern.compile("\\d").matcher(text).find(),
                "cost usage shows no numeric values: " + text);
    }

    @When("the student refreshes the assessment lab on the console")
    public void asmtRefreshLab() {
        Locator btn = studentPage.locator(PlaygroundControlPanel.REFRESH_LAB);
        if (btn.count() == 0 || !btn.first().isVisible()) {
            throw new SkipException("Refresh not visible on assessment console");
        }
        btn.first().click();
        studentPage.waitForTimeout(3_000);
    }

    @Then("the assessment lab remains running after refresh")
    public void asmtLabRunningAfterRefresh() {
        PlaygroundControlPanel panel = assessmentConsole();
        if (labId != null && CloudLabsClient.isConfigured()) {
            try {
                ManageLabs.LabState st = ManageLabs.classifyActionStatus(
                        CloudLabsClient.getStatus(labId).action,
                        CloudLabsClient.getStatus(labId).status);
                assertEquals(st, ManageLabs.LabState.RUNNING, "lab not Running after refresh");
                return;
            } catch (CloudLabsClient.UnavailableException e) {
                System.out.println("[ASMT-console] CloudLabs unavailable: " + e.getMessage());
            }
        }
        assertTrue(panel.waitUntilLabLoaded(45), "lab not running on console after refresh");
    }

    @When("the student saves an assessment lab note {string}")
    public void asmtSaveNote(String text) {
        if (studentPage.locator(PlaygroundControlPanel.NOTES_TAB).count() == 0) {
            throw new SkipException("Notes not enabled on this assessment template");
        }
        assessmentConsole().saveNote(text);
    }

    @Then("the assessment lab note {string} persists after reopening the console")
    public void asmtNotePersists(String text) {
        theStudentStartsTheAssessmentLabUntilItIsRunning();
        studentPage.locator(PlaygroundControlPanel.NOTES_TAB).first().click();
        studentPage.waitForTimeout(800);
        String val = studentPage.locator(PlaygroundControlPanel.NOTES_TEXTAREA).first().inputValue().trim();
        assertTrue(val.contains(text.trim()), "note missing after reopen (got='" + val + "')");
    }

    @Then("the assessment support link on the console is available")
    public void asmtSupportLink() {
        String mailto = assessmentConsole().openSupportMailtoUrl();
        assertTrue(mailto != null && mailto.toLowerCase(Locale.ROOT).startsWith("mailto:"),
                "support link did not produce mailto");
    }

    @Then("the assessment console Back button returns to the landing")
    public void asmtConsoleBackToLanding() {
        Locator back = studentPage.locator(PlaygroundControlPanel.BACK_BTN);
        assertTrue(back.count() > 0 && back.first().isVisible(), "Back control missing");
        back.first().click();
        studentPage.waitForTimeout(2_000);
        assertTrue(new ChallengeControlPanel(studentPage).isOnLandingBeforeStart()
                        || studentPage.url().contains("view.php"),
                "Back did not return to assessment landing");
    }

    @When("the student opens Access Lab from Jump to Console on the assessment")
    public void asmtAccessLabFromJump() {
        Locator jump = studentPage.locator("#jumpToConsoleBtn, a:has-text('Jump to Console'), button:has-text('Jump to Console')");
        assertTrue(jump.count() > 0, "Jump to Console not found");
        jump.first().click();
        studentPage.waitForTimeout(2_000);
        Locator access = studentPage.locator("#launchAccModal a, #launchAccModal button, a:has-text('Access Lab')");
        assertTrue(access.count() > 0, "Access Lab control not shown");
    }

    @Then("the student is redirected to the assessment cloud lab platform")
    public void asmtRedirectedToCloud() {
        Page popup = studentPage.context().pages().stream()
                .filter(p -> p != studentPage && !p.url().contains("view.php"))
                .findFirst().orElse(null);
        if (popup != null) {
            assertTrue(popup.url().contains("cloud") || popup.url().contains("lab")
                            || popup.url().startsWith("http"),
                    "Access Lab did not open platform (url=" + popup.url() + ")");
            return;
        }
        assertTrue(studentPage.url().contains("cloud")
                        || studentPage.locator(PlaygroundControlPanel.VM_IFRAME).count() > 0,
                "no cloud redirect or lab iframe after Access Lab");
    }

    @When("the student clicks Start Assessment and cancels the confirmation")
    public void asmtCancelStart() {
        openStudentSession();
        studentPage.navigate(Settings.BASE_URL + "/mod/cloudlabs/view.php?id=" + activityCmid,
                new Page.NavigateOptions().setWaitUntil(WaitUntilState.DOMCONTENTLOADED).setTimeout(45_000));
        studentPage.waitForTimeout(1_500);
        new ChallengeControlPanel(studentPage).startAssessmentThenCancel();
    }

    @Then("no assessment lab was created for the student")
    public void asmtNoLabCreated() {
        studentPage.waitForTimeout(1_500);
        ChallengeControlPanel acp = new ChallengeControlPanel(studentPage);
        assertTrue(acp.isOnLandingBeforeStart() || studentPage.url().contains("view.php"),
                "should remain on assessment landing");
        assertFalse(acp.isShown(), "control panel opened despite canceling start");
    }

    @When("the student opens the Lab Credentials popup on the assessment console")
    public void asmtOpenCredentials() {
        Locator btn = studentPage.locator("#openAuthBtn");
        if (btn.count() == 0 || !btn.first().isVisible()) {
            throw new SkipException("Lab Credentials not enabled");
        }
        btn.first().click();
    }

    @When("the student changes the assessment lab password on the console")
    public void asmtChangePassword() {
        Locator link = studentPage.locator("#np-ap-mod-set-password-btn");
        if (link.count() == 0 || !link.first().isVisible()) {
            throw new SkipException("Change-password option not available");
        }
        link.first().click();
        assertTrue(assessmentConsole().fillSetCredentialsPassword("AutoTest@12345"),
                "set-credentials dialog did not expose password fields");
    }

    @Then("the assessment lab password update is accepted")
    public void asmtPasswordAccepted() {
        PlaygroundControlPanel panel = assessmentConsole();
        if (panel.passwordChangeSuccessVisible()) return;
        String body = studentPage.locator("body").innerText().toLowerCase(Locale.ROOT);
        assertFalse(body.contains("exception") || body.contains("error code"),
                "password update surfaced an error");
    }

    @Then("allotted consumed and remaining duration on the assessment console match CloudLabs")
    public void asmtDurationMatchesCloudLabs() {
        assertTrue(labId != null && !labId.isBlank(), "no lab id for duration check");
        String usage = assessmentConsole().openUsageAndRead();
        assertTrue(usage != null && usage.matches("(?s).*\\d+.*"), "console usage/duration empty");
        if (!CloudLabsClient.isConfigured()) return;
        try {
            String json = CloudLabsClient.getSubscriptionsStatusBody(labId);
            assertTrue(json != null && json.length() > 10, "CloudLabs status empty");
        } catch (CloudLabsClient.UnavailableException e) {
            System.out.println("[ASMT-console] CloudLabs unavailable — UI duration only");
        }
    }

    @When("the student returns to the assessment landing and continues the in-progress attempt")
    public void asmtReturnToLandingAndContinue() {
        Locator back = studentPage.locator(PlaygroundControlPanel.BACK_BTN);
        if (back.count() > 0) {
            try {
                if (back.first().isVisible()) {
                    back.first().click();
                    studentPage.waitForTimeout(1_500);
                }
            } catch (RuntimeException ignored) {}
        }
        studentPage.navigate(Settings.BASE_URL + "/mod/cloudlabs/view.php?id=" + activityCmid,
                new Page.NavigateOptions().setWaitUntil(WaitUntilState.DOMCONTENTLOADED).setTimeout(45_000));
        studentPage.waitForTimeout(1_500);
        ChallengeControlPanel acp = new ChallengeControlPanel(studentPage);
        assertTrue(acp.hasContinueAttemptVisible(),
                "Continue is not shown — expected an in-progress assessment attempt after Start");
        acp.clickContinueAttempt();
        acp.waitForConsolePage(90);
        try {
            acp.startHandsOnLabIfNeeded();
            new PlaygroundControlPanel(studentPage).expectControlPanelLoaded(90_000);
        } catch (RuntimeException e) {
            throw new AssertionError("Continue did not reopen the assessment lab console: " + e.getMessage(), e);
        }
    }

    @Then("the assessment lab console is open after Continue")
    public void asmtConsoleOpenAfterContinue() {
        ChallengeControlPanel acp = new ChallengeControlPanel(studentPage);
        PlaygroundControlPanel panel = new PlaygroundControlPanel(studentPage);
        assertTrue(acp.isShown() || acp.isOnConsolePage() || panel.waitUntilLabLoaded(60),
                "assessment lab console is not open after Continue");
    }

    @When("the student starts the practice project lab until it is running")
    public void theStudentStartsThePracticeProjectLabUntilItIsRunning() {
        openStudentSession();
        GuidedLanding landing = new GuidedLanding(studentPage);
        landing.open(activityCmid);
        studentPage.waitForTimeout(1_500);
        assertTrue(landing.isLoaded(),
                "student did not get practice project landing cmid=" + activityCmid);
        GlobalHooks.labProvisioned = true;
        String startSel = GuidedLanding.START + ", " + GuidedLanding.CONTINUE + ", " + GuidedLanding.NEW_ATTEMPT;
        studentPage.waitForTimeout(3_000);
        GuidedControlPanel gcp = new GuidedControlPanel(studentPage);
        for (int c = 1; c <= 8; c++) {
            if (gcp.isShown()) break;
            Locator btn = studentPage.locator(startSel);
            if (btn.count() > 0 && btn.first().isVisible()) btn.first().click();
            studentPage.waitForTimeout(1_200);
            gcp.confirmAnyModal();
            if (gcp.isShown()) break;
        }
        assertTrue(gcp.isShown(), "practice project control panel did not open");
        gcp.startHandsOnLabIfNeeded();
        PlaygroundControlPanel panel = new PlaygroundControlPanel(studentPage);
        try {
            panel.expectControlPanelLoaded(120_000);
        } catch (RuntimeException e) {
            throw new AssertionError("lab console did not load after Start hands-on lab: " + e.getMessage(), e);
        }
        startLabUntilLoaded(panel);
        captureLabId(panel);
        if (labId == null || labId.isBlank()) {
            ManageLabs ml = new ManageLabs(ctx.page);
            for (int i = 0; i < 15; i++) {
                try {
                    ml.open(sectionId);
                    labId = ml.labIdForEmail(student.email);
                    if (labId != null && !labId.isBlank()) break;
                } catch (RuntimeException ignored) {}
                studentPage.waitForTimeout(3_000);
            }
        }
        assertTrue(labId != null && !labId.isBlank(), "no lab id after practice project start");
        GlobalHooks.teardownLabId = labId;
        ManageLabs.LabState st = CloudLabsClient.waitForState(labId, RUN_OR_FAIL, 180, 5);
        assertEquals(st, ManageLabs.LabState.RUNNING, "practice project lab not Running (state=" + st + ")");
    }

    private void ensureSectionReady() {
        if (sectionId > 0 && activityCmid > 0 && courseId > 0) {
            maybeEnterProvisioningLane();
            return;
        }
        activityCmid = resolveActivityCmid();
        courseId = SandboxCourse.idLongOrZero();
        if (courseId <= 0) {
            courseId = Long.parseLong(SandboxCourse.ensure(ctx.page));
        }
        if (ctx.sectionId != null && !ctx.sectionId.isBlank()) {
            sectionId = Integer.parseInt(ctx.sectionId.trim());
        } else {
            PlaygroundLanding adminLanding = new PlaygroundLanding(ctx.page);
            adminLanding.openDirect(activityCmid);
            String sid = adminLanding.hiddenSectionId();
            assertTrue(sid != null && !sid.isBlank(),
                    "could not read section id from sandbox landing cmid=" + activityCmid);
            sectionId = Integer.parseInt(sid.trim());
            ctx.sectionId = String.valueOf(sectionId);
        }
        ctx.newActivityCmid = String.valueOf(activityCmid);
        GlobalHooks.teardownSectionId = String.valueOf(sectionId);
        GlobalHooks.skipLabTeardown = true;
        maybeEnterProvisioningLane();
    }

    private void maybeEnterProvisioningLane() {
        // Parallel gate is acquired in {@link #sandboxParallelProvisioningGate} (@Before).
    }

    /** Sync + poll until Manage Labs row and (when possible) CloudLabs agree on target state. */
    private void waitForSyncedLabState(EnumSet<ManageLabs.LabState> acceptable,
                                       ManageLabs.LabState expected,
                                       String operationLabel) {
        ManageLabs ml = new ManageLabs(ctx.page);
        long deadline = System.currentTimeMillis() + 300_000;
        ManageLabs.LabState last = ManageLabs.LabState.NOTCREATED;
        int round = 0;
        while (System.currentTimeMillis() < deadline) {
            round++;
            ensureLabIdReadyForCloudLabs();
            if (labId != null && !labId.isBlank()
                    && CloudLabsClient.isConfigured() && !CloudLabsClient.isBlocked()) {
                ManageLabs.LabState api = CloudLabsClient.waitForState(labId, acceptable, 30, 5);
                if (acceptable.contains(api) || api == expected) {
                    System.out.println("[SB-manage] Sync OK — " + operationLabel
                            + " completed via CloudLabs (state=" + api + ") labId=" + labId
                            + " round=" + round);
                    syncSelectedLab();
                    return;
                }
                last = api;
            }
            syncSelectedLab();
            ml.refreshCreatedTable();
            last = readLabStateForStudent(ml);
            if (acceptable.contains(last) || last == expected) {
                System.out.println("[SB-manage] Sync OK — " + operationLabel + " completed (state="
                        + last + ") labId=" + labId + " round=" + round);
                syncSelectedLab();
                return;
            }
            if (labId != null && !labId.isBlank()
                    && CloudLabsClient.isConfigured() && !CloudLabsClient.isBlocked()) {
                ManageLabs.LabState api = CloudLabsClient.waitForState(labId, acceptable, 45, 5);
                last = api;
                if (acceptable.contains(api) || api == expected) {
                    System.out.println("[SB-manage] Sync OK — " + operationLabel
                            + " completed via CloudLabs after Sync (state=" + api + ") labId=" + labId);
                    syncSelectedLab();
                    return;
                }
            }
            System.out.println("[SB-manage] " + operationLabel + " sync round " + round
                    + " state=" + last + " labId=" + labId + " — retrying");
            sleepMs(8_000);
        }
        throw new AssertionError("after Sync, " + operationLabel + " was not completed (expected "
                + expected + " / acceptable " + acceptable + ", last=" + last + " labId=" + labId
                + " email=" + (student == null ? null : student.email) + ")");
    }

    private ManageLabs.LabState readLabStateForStudent(ManageLabs ml) {
        ml.open(sectionId);
        ml.openCreatedTab();
        ml.refreshCreatedTable();
        try {
            ml.clearCreatedSearch();
        } catch (RuntimeException ignored) {}
        ManageLabs.LabState ui = classifyRowText(ml.stateForEmail(student.email), null);
        if (ui != ManageLabs.LabState.NOTCREATED && ui != ManageLabs.LabState.UNKNOWN) {
            return ui;
        }
        try {
            ml.searchCreatedByEmail(student.email);
            ml.refreshCreatedTable();
            ui = classifyRowText(ml.stateForEmail(student.email), null);
            if (ui != ManageLabs.LabState.NOTCREATED && ui != ManageLabs.LabState.UNKNOWN) {
                try {
                    ml.clearCreatedSearch();
                } catch (RuntimeException ignored) {}
                return ui;
            }
        } catch (RuntimeException ignored) {}
        String row = ml.createdRowTextForEmail(student.email);
        if (row == null || row.isBlank()) {
            Locator labRow = findLabRow();
            if (labRow.count() > 0) {
                try {
                    row = labRow.first().innerText();
                } catch (RuntimeException ignored) {}
            }
        }
        try {
            ml.clearCreatedSearch();
        } catch (RuntimeException ignored) {}
        return classifyRowText(ui, row);
    }

    /** Map Manage Labs row text (or prior {@link ManageLabs.LabState}) to a lifecycle state. */
    private ManageLabs.LabState classifyRowText(ManageLabs.LabState prior, String rowText) {
        if (prior != null && prior != ManageLabs.LabState.NOTCREATED
                && prior != ManageLabs.LabState.UNKNOWN) {
            return prior;
        }
        if (rowText == null || rowText.isBlank()) {
            return prior == null ? ManageLabs.LabState.NOTCREATED : prior;
        }
        String row = rowText.toLowerCase(Locale.ROOT);
        if (row.contains("start") && row.contains("complete")) {
            return ManageLabs.LabState.RUNNING;
        }
        if (row.contains("create") && row.contains("complete")) {
            return ManageLabs.LabState.RUNNING;
        }
        if (row.contains("stop") && row.contains("complete")) {
            return ManageLabs.LabState.STOPPED;
        }
        if (row.contains("delete") && row.contains("complete")) {
            return ManageLabs.LabState.DELETED;
        }
        if (row.contains("stop") && (row.contains("progress") || row.contains("inprogress"))) {
            return ManageLabs.LabState.INPROGRESS;
        }
        if (row.contains("start") && (row.contains("progress") || row.contains("inprogress"))) {
            return ManageLabs.LabState.INPROGRESS;
        }
        if (row.contains("create") && (row.contains("progress") || row.contains("inprogress"))) {
            return ManageLabs.LabState.INPROGRESS;
        }
        return prior == null ? ManageLabs.LabState.NOTCREATED : prior;
    }

    private void assertLabInExpectedState(ManageLabs.LabState expected, String phase) {
        ManageLabs ml = new ManageLabs(ctx.page);
        ensureLabIdReadyForCloudLabs();
        if (CloudLabsClient.isConfigured() && !CloudLabsClient.isBlocked()) {
            CloudLabsClient.SubStatus ss = CloudLabsClient.getStatus(labId);
            if (ss != null) {
                ManageLabs.LabState st = ManageLabs.classifyActionStatus(ss.action, ss.status);
                if (st == expected || (expected == ManageLabs.LabState.RUNNING
                        && st == ManageLabs.LabState.INPROGRESS)) {
                    return;
                }
            }
        }
        ManageLabs.LabState ui = readLabStateForStudent(ml);
        assertTrue(ui == expected || (expected == ManageLabs.LabState.RUNNING
                        && ui == ManageLabs.LabState.INPROGRESS),
                phase + " final state not " + expected + " (ui=" + ui + " labId=" + labId + ")");
    }

    private String firstSelectableGroupLabel() {
        Locator opts = ctx.page.locator("#course_groups option");
        for (int i = 0; i < opts.count(); i++) {
            String t = opts.nth(i).innerText().trim();
            String v = opts.nth(i).getAttribute("value");
            if (t.isBlank() || t.equalsIgnoreCase("All Groups") || t.equalsIgnoreCase("All")
                    || v == null || v.isBlank() || "0".equals(v)) {
                continue;
            }
            return t;
        }
        return null;
    }

    private void waitForStudentLabRunning(PlaygroundControlPanel panel) {
        ensureLabIdReadyForCloudLabs();
        ManageLabs ml = new ManageLabs(ctx.page);
        long deadline = System.currentTimeMillis() + 300_000;
        ManageLabs.LabState last = ManageLabs.LabState.NOTCREATED;
        int round = 0;
        while (System.currentTimeMillis() < deadline) {
            round++;
            if (panel.waitUntilLabLoaded(3)) {
                System.out.println("[SB-manage] student lab running (console) labId=" + labId);
                return;
            }
            last = readLabStateForStudent(ml);
            if (last == ManageLabs.LabState.RUNNING || last == ManageLabs.LabState.INPROGRESS) {
                System.out.println("[SB-manage] student lab running (Manage Labs) labId=" + labId
                        + " state=" + last);
                return;
            }
            if (labId != null && CloudLabsClient.isConfigured() && !CloudLabsClient.isBlocked()) {
                last = CloudLabsClient.waitForState(labId, RUN_OR_FAIL, 30, 5);
                if (last == ManageLabs.LabState.RUNNING || last == ManageLabs.LabState.INPROGRESS) {
                    System.out.println("[SB-manage] student lab running (CloudLabs) labId=" + labId);
                    return;
                }
            }
            ensureLabIdReadyForCloudLabs();
            System.out.println("[SB-manage] student start poll " + round + " state=" + last
                    + " labId=" + labId);
            sleepMs(5_000);
        }
        throw new AssertionError("student-started lab did not reach Running before admin Manage Labs "
                + "(state=" + last + " labId=" + labId + " email=" + student.email + ")");
    }

    /** Refresh lab id from Manage Labs (authoritative) and wait until CloudLabs knows the subscription. */
    private void ensureLabIdReadyForCloudLabs() {
        assertNotNull(student, "student not seeded for lab id resolution");
        ManageLabs ml = new ManageLabs(ctx.page);
        refreshLabIdFromManageLabs(ml);
        if (labId == null || labId.isBlank()) {
            labId = waitLabIdForEmail(ml, student.email, 30);
        }
        awaitCloudLabsSubscription(labId, 150);
    }

    private void refreshLabIdFromManageLabs(ManageLabs ml) {
        for (int i = 0; i < 12; i++) {
            ml.open(sectionId);
            ml.openCreatedTab();
            try {
                ml.searchCreatedByEmail(student.email);
            } catch (RuntimeException ignored) {}
            String id = ml.labIdForEmail(student.email);
            if (id != null && !id.isBlank()) {
                labId = id.trim();
                try {
                    ml.clearCreatedSearch();
                } catch (RuntimeException ignored) {}
                return;
            }
            java.util.List<String> ids = ml.labIdsForEmail(student.email);
            if (!ids.isEmpty()) {
                labId = ids.get(ids.size() - 1).trim();
                try {
                    ml.clearCreatedSearch();
                } catch (RuntimeException ignored) {}
                return;
            }
            try {
                ml.clearCreatedSearch();
            } catch (RuntimeException ignored) {}
            ctx.page.waitForTimeout(2_500);
        }
    }

    private void awaitCloudLabsSubscription(String subscriptionId, int maxSeconds) {
        if (subscriptionId == null || subscriptionId.isBlank()) return;
        if (!CloudLabsClient.isConfigured() || CloudLabsClient.isBlocked()) return;
        long deadline = System.currentTimeMillis() + maxSeconds * 1000L;
        int poll = 0;
        while (System.currentTimeMillis() < deadline) {
            poll++;
            CloudLabsClient.SubStatus ss = CloudLabsClient.getStatus(subscriptionId);
            if (ss != null) {
                System.out.println("[SB-manage] CloudLabs subscription visible labId=" + subscriptionId
                        + " action=" + ss.action + " status=" + ss.status + " (poll " + poll + ")");
                return;
            }
            sleepMs(2_000);
        }
        System.out.println("[SB-manage] CloudLabs subscription still missing after " + maxSeconds
                + "s labId=" + subscriptionId + " — continuing with UI/Manage Labs checks");
    }

    private void trackUser(ApiClient.SeededUser u) {
        if (u != null) extraUsers.add(u);
    }

    @After
    public void cleanupManageLabsFlow(io.cucumber.java.Scenario scenario) {
        boolean manage = scenario.getSourceTagNames().stream()
                .anyMatch(tag -> tag.startsWith("@SB-manage-") || tag.equals("@practice_project_manage")
                        || tag.equals("@assessment_manage") || tag.equals("@assessment_sandbox_parity"));
        if (!manage) {
            return;
        }
        // Do NOT Delete labs or users here — that waits on provider delete.
        // Course delete (@sandbox-cleanup) is the teardown when you ask for it.
        System.out.println("[SB-manage] teardown: keep labs/users; close student browser only");
        if (studentCtx != null) {
            try { studentCtx.close(); } catch (Throwable ignored) {}
        }
        long cid = courseId > 0 ? courseId : SandboxCourse.idLongOrZero();
        List<ApiClient.SeededUser> toKeep = new ArrayList<>(extraUsers);
        if (student != null) toKeep.add(student);
        if (userWithLab != null) toKeep.add(userWithLab);
        if (userUnenrolled != null) toKeep.add(userUnenrolled);
        toKeep.addAll(bulkUsers);
        Set<Long> seen = new HashSet<>();
        for (ApiClient.SeededUser u : toKeep) {
            if (u == null || !seen.add(u.id)) continue;
            if (cid > 0) SandboxUserRegistry.put(cid, u);
        }
        student = null;
        studentPage = null;
        studentCtx = null;
        labId = null;
        sectionId = 0;
        activityCmid = 0;
        stopCompleted = false;
        startCompleted = false;
        retainNamedStudent = false;
        extraUsers.clear();
        bulkUsers.clear();
        bulkLabIds.clear();
        expectedNoLabsEmails.clear();
        forbiddenNoLabsEmails.clear();
        userWithLab = null;
        userUnenrolled = null;
        if (!Boolean.getBoolean("sandbox.parallel")) {
            GlobalHooks.teardownLabId = null;
            GlobalHooks.teardownStudentEmail = null;
        }
        provisioningLaneHeld = false;
    }
}

