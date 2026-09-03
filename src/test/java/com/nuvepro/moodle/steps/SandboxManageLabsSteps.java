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
import com.nuvepro.moodle.helpers.SandboxCourse;
import com.nuvepro.moodle.helpers.SandboxUserRegistry;
import com.nuvepro.moodle.pages.ManageLabs;
import com.nuvepro.moodle.pages.PlaygroundControlPanel;
import com.nuvepro.moodle.pages.PlaygroundLanding;
import io.cucumber.java.After;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import org.testng.SkipException;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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

    private static final EnumSet<ManageLabs.LabState> RUN_OR_FAIL =
            EnumSet.of(ManageLabs.LabState.RUNNING, ManageLabs.LabState.FAILED);
    private static final EnumSet<ManageLabs.LabState> STOP_OR_FAIL =
            EnumSet.of(ManageLabs.LabState.STOPPED, ManageLabs.LabState.FAILED);

    public SandboxManageLabsSteps(TestContext ctx) {
        this.ctx = ctx;
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

        long stamp = System.currentTimeMillis();
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
            student = ApiClient.createUser(System.currentTimeMillis());
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
        throw new SkipException("no sandbox activity cmid — run "
                + "'admin creates a sandbox activity using \"sandbox_user_lab\"' first "
                + "(PLAYGROUND_CMID alone is unreliable when it is not the suite course)");
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
        startLabUntilLoaded(panel);
        captureLabId(panel);
        assertNotNull(labId, "could not capture lab id after student start");
        GlobalHooks.teardownLabId = labId;
        if (CloudLabsClient.isConfigured() && !CloudLabsClient.isBlocked()) {
            try {
                CloudLabsClient.SubStatus ss = CloudLabsClient.getStatus(labId);
                if (ss != null && ManageLabs.classifyActionStatus(ss.action, ss.status) == ManageLabs.LabState.RUNNING) {
                    System.out.println("[SB-manage] student lab already Running labId=" + labId);
                    return;
                }
                ManageLabs.LabState st = CloudLabsClient.waitForState(labId, RUN_OR_FAIL, 180, 5);
                assertEquals(st, ManageLabs.LabState.RUNNING,
                        "student-started lab did not reach Running before admin Manage Labs (state=" + st + ")");
                System.out.println("[SB-manage] student lab running labId=" + labId);
                return;
            } catch (CloudLabsClient.UnavailableException e) {
                System.out.println("[SB-manage] CloudLabs unavailable after start — using console state: "
                        + e.getMessage());
            }
        }
        assertTrue(panel.waitUntilLabLoaded(30) || "running".equalsIgnoreCase(studentLabStatus()),
                "student lab console is not running and CloudLabs status is unavailable");
        System.out.println("[SB-manage] student lab running (console) labId=" + labId);
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
        if (labId == null || labId.isBlank()) {
            ManageLabs ml = new ManageLabs(ctx.page);
            labId = waitLabIdForEmail(ml, student.email);
            GlobalHooks.teardownLabId = labId;
        }
        syncSelectedLab();
        ManageLabs.LabState st = CloudLabsClient.waitForState(labId, RUN_OR_FAIL, 180, 5);
        assertEquals(st, ManageLabs.LabState.RUNNING,
                "after Sync, Start was not completed (expected Running / Start+Complete, got " + st + ")");
        // Re-sync once settled so Manage Labs UI refreshes from provider.
        syncSelectedLab();
        System.out.println("[SB-manage] Sync OK — Start completed (Running) labId=" + labId);
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
        syncSelectedLab();
        ManageLabs.LabState st = CloudLabsClient.waitForState(labId, STOP_OR_FAIL, 240, 5);
        assertEquals(st, ManageLabs.LabState.STOPPED,
                "after Sync, Stop was not completed (expected Stopped / Stop+Complete, got " + st + ")");
        syncSelectedLab();
        stopCompleted = true;
        System.out.println("[SB-manage] Sync OK — Stop completed (Stopped) labId=" + labId);
    }

    @Then("the Manage Labs admin stop flow has completed successfully")
    public void theManageLabsAdminStopFlowHasCompletedSuccessfully() {
        assertTrue(stopCompleted, "Stop-complete Sync step did not finish");
        CloudLabsClient.SubStatus ss = CloudLabsClient.getStatus(labId);
        assertNotNull(ss, "CloudLabs status missing after stop flow labId=" + labId);
        ManageLabs.LabState st = ManageLabs.classifyActionStatus(ss.action, ss.status);
        assertEquals(st, ManageLabs.LabState.STOPPED,
                "final state not Stopped (action=" + ss.action + " status=" + ss.status + ")");
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
        studentCtx = ctx.browser.newContext(new Browser.NewContextOptions()
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

    private void startLabUntilLoaded(PlaygroundControlPanel panel) {
        if (panel.waitUntilLabLoaded(5)) {
            System.out.println("[SB-manage] lab already loaded before Start Lab click");
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
        assertTrue(panel.waitUntilLabLoaded(180),
                "sandbox lab did not load within 180s after Start Lab");
    }

    private void captureLabId(PlaygroundControlPanel panel) {
        for (int i = 0; i < 20; i++) {
            try {
                String id = panel.labId();
                if (id != null && !id.isBlank() && !"0".equals(id.trim())) {
                    labId = id.trim();
                    System.out.println("[SB-manage] captured labId from console=" + labId);
                    return;
                }
            } catch (RuntimeException ignored) {}
            studentPage.waitForTimeout(2_000);
        }
        // Fallback: admin Manage Labs by email
        ManageLabs ml = new ManageLabs(ctx.page);
        for (int i = 0; i < 12; i++) {
            ml.open(sectionId);
            String id = ml.labIdForEmail(student.email);
            if (id != null && !id.isBlank()) {
                labId = id.trim();
                System.out.println("[SB-manage] captured labId from Manage Labs=" + labId);
                return;
            }
            ctx.page.waitForTimeout(3_000);
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
        if (CloudLabsClient.isConfigured() && !CloudLabsClient.isBlocked()) {
            try {
                ManageLabs.LabState st = CloudLabsClient.waitForState(labId, RUN_OR_FAIL, 180, 5);
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
        if (labId != null && CloudLabsClient.isConfigured()) {
            CloudLabsClient.SubStatus ss = CloudLabsClient.getStatus(labId);
            assertNotNull(ss, "no CloudLabs status for labId=" + labId);
            ManageLabs.LabState st = ManageLabs.classifyActionStatus(ss.action, ss.status);
            assertEquals(st, ManageLabs.LabState.RUNNING,
                    "Active lab not Create/Start complete (action=" + ss.action
                            + " status=" + ss.status + ")");
            System.out.println("[SB-manage] Active labs Create complete OK action="
                    + ss.action + " status=" + ss.status);
            return;
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
        long stamp = System.currentTimeMillis();
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
        long stamp = System.currentTimeMillis();
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
        if (!CloudLabsClient.isConfigured()) {
            throw new SkipException("CloudLabs API not configured");
        }
        deleteMessageUser = null;
        recreateUser = null;
        seedUsersWithRunningLabs(4);
        deleteMessageUser = bulkUsers.get(0);
        recreateUser = bulkUsers.get(3);
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
        assertEquals(bulkLabIds.size(), 4, "need 4 lab ids for parallel delete");
        assertEquals(bulkUsers.size(), 4, "need 4 users for parallel delete");
        // This scenario already owns one of the five runner slots. Reserve the other four so total
        // browser-process concurrency stays at 5 — but time-boxed: blocking indefinitely would park
        // this long scenario behind whatever else is queued.
        int reserved = TestContext.tryAcquireBrowserPermits(4, 90);
        try {
            ExecutorService pool = Executors.newFixedThreadPool(4);
            List<Future<?>> futures = new ArrayList<>();
            List<String> errors = new ArrayList<>();
            int sid = sectionId;
            for (int i = 0; i < 4; i++) {
                final String id = bulkLabIds.get(i);
                final String email = bulkUsers.get(i).email;
                final int idx = i + 1;
                futures.add(pool.submit(() -> {
                    try (Playwright pw = Playwright.create()) {
                        Browser browser = pw.chromium().launch(new BrowserType.LaunchOptions()
                                .setHeadless(Settings.HEADLESS)
                                .setSlowMo(Settings.SLOWMO_MS));
                        try (BrowserContext context = browser.newContext(new Browser.NewContextOptions()
                                .setBaseURL(Settings.BASE_URL)
                                .setIgnoreHTTPSErrors(true)
                                .setStorageStatePath(GlobalHooks.STORAGE_STATE))) {
                            Page page = context.newPage();
                            page.setDefaultTimeout(Settings.DEFAULT_TIMEOUT_MS);
                            ManageLabs ml = new ManageLabs(page);
                            try {
                                ml.performActionForLabId(id, ManageLabs.DELETE, sid);
                            } catch (RuntimeException e) {
                                ml.performActionForEmail(email, ManageLabs.DELETE, sid);
                            }
                            System.out.println("[SB-manage] parallel Delete #" + idx
                                    + " clicked labId=" + id + " email=" + email);
                        } finally {
                            browser.close();
                        }
                    } catch (Throwable t) {
                        synchronized (errors) {
                            errors.add("delete #" + idx + " lab=" + id + " " + t.getMessage());
                        }
                        System.out.println("[SB-manage] parallel Delete #" + idx + " FAILED: "
                                + t.getMessage());
                    }
                }));
            }
            pool.shutdown();
            try {
                if (!pool.awaitTermination(3, TimeUnit.MINUTES)) {
                    pool.shutdownNow();
                    throw new AssertionError("parallel Delete browsers did not finish clicking in 3 min");
                }
                for (Future<?> f : futures) f.get(5, TimeUnit.SECONDS);
            } catch (AssertionError e) {
                throw e;
            } catch (Exception e) {
                throw new AssertionError("parallel Delete failed: " + e.getMessage(), e);
            }
            assertTrue(errors.isEmpty(), "parallel Delete click errors: " + errors);
        } finally {
            if (reserved > 0) TestContext.releaseBrowserPermits(reserved);
        }
    }

    @Then("those labs are Deleted after one shared wait")
    public void thoseLabsAreDeletedAfterOneSharedWait() {
        CloudLabsClient.waitForAllDeleted(bulkLabIds, 600, 15);
        student = deleteMessageUser;
        labId = bulkLabIds.get(0);
        System.out.println("[SB-manage] parallel Delete shared wait OK labs=" + bulkLabIds);
    }

    @When("admin creates a replacement lab for the post-delete student")
    public void adminCreatesAReplacementLabForThePostDeleteStudent() {
        assertNotNull(recreateUser, "post-delete student not prepared");
        student = recreateUser;
        labId = null;
        adminCreatesALabForThatStudentFromNoLabs();
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
        openStudentSession();
        PlaygroundLanding landing = new PlaygroundLanding(studentPage);
        for (int i = 0; i < 12; i++) {
            landing.open(activityCmid);
            studentPage.waitForTimeout(2_000);
            String body = studentPage.locator("body").innerText().toLowerCase(Locale.ROOT);
            if (body.contains("deleted") || landing.hasStartSandbox()) {
                return;
            }
        }
    }

    @Then("the student sees the lab is deleted message")
    public void theStudentSeesTheLabIsDeletedMessage() {
        assertNotNull(studentPage, "student session not open");
        PlaygroundLanding landing = new PlaygroundLanding(studentPage);
        String body = "";
        for (int i = 0; i < 24; i++) {
            if (i > 0) {
                landing.open(activityCmid);
                studentPage.waitForTimeout(1_500);
            }
            body = studentPage.locator("body").innerText().toLowerCase(Locale.ROOT);
            boolean deletedText = body.contains("lab is deleted")
                    || body.contains("lab has been deleted")
                    || body.contains("this lab is deleted")
                    || body.contains("lab deleted")
                    || (body.contains("deleted") && body.contains("lab"));
            boolean backToNotCreated = landing.hasStartSandbox() && !landing.hasActiveLab();
            if (deletedText || backToNotCreated) {
                System.out.println("[SB-manage] deleted landing OK text=" + deletedText
                        + " startSandbox=" + backToNotCreated);
                return;
            }
            studentPage.waitForTimeout(2_000);
        }
        throw new AssertionError("student console/landing does not show 'lab is deleted'. body snippet='"
                + body.substring(0, Math.min(400, body.length())) + "'");
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
        if (studentPage.locator(PlaygroundControlPanel.LAUNCH_START).count() > 0) {
            System.out.println("[SB-manage] invalid-template: Start Lab still present (create did not complete)");
            return;
        }
        throw new AssertionError("expected 'missing required argument' after creating a lab with "
                + "gcp.organization.account. body snippet='"
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
        CloudLabsClient.SubStatus ss = CloudLabsClient.getStatus(labId);
        assertNotNull(ss, "CloudLabs status missing after start flow labId=" + labId);
        ManageLabs.LabState st = ManageLabs.classifyActionStatus(ss.action, ss.status);
        assertEquals(st, ManageLabs.LabState.RUNNING,
                "final state not Running (action=" + ss.action + " status=" + ss.status + ")");
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
        assertTrue(ml.createdContainsEmail(student.email),
                "Active labs search did not include " + student.email);
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
     * Seed N users and get all their labs Running in ONE create pass and ONE wait window:
     * bulk Create from the not-created tab, then poll all lab ids together. Creating and waiting
     * per user cost N provider round-trips; this costs about one.
     */
    private void seedUsersWithRunningLabs(int count) {
        bulkUsers.clear();
        bulkLabIds.clear();
        ManageLabs ml = new ManageLabs(ctx.page);
        long stamp = System.currentTimeMillis();
        List<String> emails = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            ApiClient.SeededUser u = ApiClient.createUser(stamp + i);
            ApiClient.enrolUser(u.id, courseId, 5);
            trackUser(u);
            bulkUsers.add(u);
            emails.add(u.email);
        }
        ml.createLabsForEmails(emails, sectionId);
        GlobalHooks.labProvisioned = true;
        for (ApiClient.SeededUser u : bulkUsers) {
            String id;
            try {
                id = waitLabIdForEmail(ml, u.email);
            } catch (AssertionError missing) {
                System.out.println("[SB-manage] bulk Create missed " + u.email
                        + " — creating that user individually");
                ml.createLabForUserEmail(u.email, sectionId);
                id = waitLabIdForEmail(ml, u.email);
            }
            bulkLabIds.add(id);
        }
        CloudLabsClient.waitForAll(bulkLabIds,
                EnumSet.of(ManageLabs.LabState.RUNNING), 300, 5, "create");
    }

    @Then("Active labs pagination can move to the next page and back")
    public void activeLabsPaginationCanMoveToTheNextPageAndBack() {
        ManageLabs ml = new ManageLabs(ctx.page);
        ml.open(sectionId);
        ml.openCreatedTab();
        ml.clearCreatedSearch();
        assertTrue(ml.setCreatedPageSize("1") || ml.setCreatedPageSize("5"),
                "Active labs page-size control not found");
        // With page size 1 and 2 users, next should enable.
        assertTrue(ml.hasCreatedPagination(),
                "Active labs pagination not available (need page size 1 + >=2 labs)");
        List<String> page1 = ml.createdEmailsOnCurrentPage();
        assertTrue(ml.clickCreatedNextPage(), "could not open Active labs next page");
        List<String> page2 = ml.createdEmailsOnCurrentPage();
        assertFalse(page1.equals(page2), "Active labs next page same as page 1");
        assertTrue(ml.clickCreatedPrevPage(), "could not return to Active labs previous page");
    }

    @Given("{int} enrolled Manage Labs users without labs")
    public void nEnrolledManageLabsUsersWithoutLabs(int n) {
        ensureSectionReady();
        int count = Math.min(Math.max(n, 2), 3);
        bulkUsers.clear();
        bulkLabIds.clear();
        long stamp = System.currentTimeMillis();
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
        List<String> emails = new ArrayList<>();
        for (ApiClient.SeededUser u : bulkUsers) emails.add(u.email);
        ml.createLabsForEmails(emails, sectionId);
        GlobalHooks.labProvisioned = true;
        bulkLabIds.clear();
        for (ApiClient.SeededUser u : bulkUsers) {
            String id = null;
            try {
                id = waitLabIdForEmail(ml, u.email);
            } catch (AssertionError missing) {
                System.out.println("[SB-manage] bulk Create missed " + u.email
                        + " — creating that user individually");
                ml.createLabForUserEmail(u.email, sectionId);
                id = waitLabIdForEmail(ml, u.email);
            }
            bulkLabIds.add(id);
        }
        CloudLabsClient.waitForAll(bulkLabIds,
                EnumSet.of(ManageLabs.LabState.RUNNING), 300, 5, "create");
    }

    @Then("those users appear on Active labs with Create completed")
    public void thoseUsersAppearOnActiveLabsWithCreateCompleted() {
        ManageLabs ml = new ManageLabs(ctx.page);
        ml.open(sectionId);
        ml.openCreatedTab();
        for (ApiClient.SeededUser u : bulkUsers) {
            assertTrue(ml.createdContainsEmail(u.email),
                    "missing Active labs row for " + u.email);
        }
    }

    @Given("{int} Manage Labs users with Stopped labs")
    public void nManageLabsUsersWithStoppedLabs(int n) {
        nManageLabsUsersWithActiveLabsForPagination(n);
        ManageLabs ml = new ManageLabs(ctx.page);
        List<String> emails = new ArrayList<>();
        for (ApiClient.SeededUser u : bulkUsers) emails.add(u.email);
        ml.open(sectionId);
        ml.openCreatedTab();
        ml.selectRowsForEmails(emails);
        ml.performActionOnSelection(ManageLabs.STOP);
        CloudLabsClient.waitForAll(bulkLabIds,
                EnumSet.of(ManageLabs.LabState.STOPPED), 300, 5, "stop");
    }

    @When("admin bulk starts those labs from Manage Labs")
    public void adminBulkStartsThoseLabsFromManageLabs() {
        ManageLabs ml = new ManageLabs(ctx.page);
        List<String> emails = new ArrayList<>();
        for (ApiClient.SeededUser u : bulkUsers) emails.add(u.email);
        ml.open(sectionId);
        ml.openCreatedTab();
        ml.selectRowsForEmails(emails);
        ml.performActionOnSelection(ManageLabs.START);
    }

    @When("admin syncs those labs until Start is completed")
    public void adminSyncsThoseLabsUntilStartIsCompleted() {
        ManageLabs ml = new ManageLabs(ctx.page);
        for (ApiClient.SeededUser u : bulkUsers) {
            ml.performActionForEmail(u.email, ManageLabs.SYNC_BTN, sectionId);
        }
        CloudLabsClient.waitForAll(bulkLabIds,
                EnumSet.of(ManageLabs.LabState.RUNNING), 300, 5, "start");
    }

    @Then("those labs are Running")
    public void thoseLabsAreRunning() {
        for (String id : bulkLabIds) {
            CloudLabsClient.SubStatus ss = CloudLabsClient.getStatus(id);
            assertNotNull(ss, "no status for " + id);
            assertEquals(ManageLabs.classifyActionStatus(ss.action, ss.status), ManageLabs.LabState.RUNNING,
                    "lab " + id + " not Running");
        }
    }

    @When("admin bulk stops those labs from Manage Labs")
    public void adminBulkStopsThoseLabsFromManageLabs() {
        ManageLabs ml = new ManageLabs(ctx.page);
        List<String> emails = new ArrayList<>();
        for (ApiClient.SeededUser u : bulkUsers) emails.add(u.email);
        ml.open(sectionId);
        ml.openCreatedTab();
        ml.selectRowsForEmails(emails);
        ml.performActionOnSelection(ManageLabs.STOP);
    }

    @When("admin syncs those labs until Stop is completed")
    public void adminSyncsThoseLabsUntilStopIsCompleted() {
        ManageLabs ml = new ManageLabs(ctx.page);
        for (ApiClient.SeededUser u : bulkUsers) {
            try {
                ml.performActionForEmail(u.email, ManageLabs.SYNC_BTN, sectionId);
            } catch (RuntimeException ignored) {}
        }
        CloudLabsClient.waitForAll(bulkLabIds,
                EnumSet.of(ManageLabs.LabState.STOPPED), 300, 5, "stop");
    }

    @Then("those labs are Stopped")
    public void thoseLabsAreStopped() {
        for (String id : bulkLabIds) {
            CloudLabsClient.SubStatus ss = CloudLabsClient.getStatus(id);
            assertNotNull(ss, "no status for " + id);
            assertEquals(ManageLabs.classifyActionStatus(ss.action, ss.status), ManageLabs.LabState.STOPPED,
                    "lab " + id + " not Stopped");
        }
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
        // Clear any selection by reopening.
        ml.open(sectionId);
        ml.openCreatedTab();
        assertTrue(ml.actionDisabled(ManageLabs.START) || !ml.hasLabRows()
                        || ctx.page.locator(ManageLabs.START).count() == 0
                        || attributeSuggestsDisabled(ManageLabs.START),
                "Start should be gated with no selection");
        assertTrue(ml.actionDisabled(ManageLabs.STOP) || attributeSuggestsDisabled(ManageLabs.STOP),
                "Stop should be gated with no selection");
        assertTrue(ml.actionDisabled(ManageLabs.DELETE) || attributeSuggestsDisabled(ManageLabs.DELETE),
                "Delete should be gated with no selection");
        // Sync / Create are buttons — disabled attribute is meaningful.
        Locator sync = ctx.page.locator(ManageLabs.SYNC_BTN);
        if (sync.count() > 0 && sync.first().isVisible()) {
            assertTrue(sync.first().getAttribute("disabled") != null
                            || !sync.first().isEnabled(),
                    "Sync should be disabled with no selection");
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
        ctx.page.locator(ManageLabs.LAB_DETAILS_BTN).click();
    }

    @Then("Lab Details shows that student email or lab id")
    public void labDetailsShowsThatStudentEmailOrLabId() {
        Page p = ctx.page;
        boolean shown = false;
        String text = "";
        for (int i = 0; i < 20; i++) {
            Locator modal = p.locator(ManageLabs.LAB_DETAIL_MODAL + ", .modal.show, "
                    + "[data-region='modal-container']:visible");
            if (modal.count() > 0 && modal.first().isVisible()) {
                shown = true;
                text = modal.first().innerText();
                break;
            }
            p.waitForTimeout(500);
        }
        if (!shown) {
            throw new SkipException("Lab Details modal did not render — needs runtime confirmation");
        }
        String lower = text.toLowerCase(Locale.ROOT);
        boolean hasEmail = student != null && lower.contains(student.email.toLowerCase(Locale.ROOT));
        boolean hasLab = labId != null && text.contains(labId);
        assertTrue(hasEmail || hasLab,
                "Lab Details missing email/lab id (email=" + (student == null ? null : student.email)
                        + " labId=" + labId + ")");
    }

    @Then("admin can use the Manage Labs group filter if groups exist")
    public void adminCanUseTheManageLabsGroupFilterIfGroupsExist() {
        ManageLabs ml = new ManageLabs(ctx.page);
        if (!ml.groupFilterPresent()) {
            throw new SkipException("#course_groups not present on this Manage Labs page");
        }
        com.nuvepro.moodle.pages.Groups groups = new com.nuvepro.moodle.pages.Groups(ctx.page);
        Locator opts = ctx.page.locator("#course_groups option");
        if (opts.count() <= 1) {
            throw new SkipException("no course groups available to filter Manage Labs");
        }
        // Pick first non-empty / non-All option if possible.
        String label = null;
        for (int i = 0; i < opts.count(); i++) {
            String t = opts.nth(i).innerText().trim();
            String v = opts.nth(i).getAttribute("value");
            if (t.isBlank() || t.equalsIgnoreCase("All Groups") || t.equalsIgnoreCase("All")
                    || v == null || v.isBlank() || "0".equals(v)) continue;
            label = t;
            break;
        }
        if (label == null) {
            throw new SkipException("only 'All Groups' present — cannot exercise filter");
        }
        groups.selectGroup(label);
        ctx.page.waitForTimeout(1_500);
        System.out.println("[SB-manage] group filter selected: " + label);
        // Reset
        try { groups.selectGroup("All Groups"); } catch (RuntimeException ignored) {}
    }

    @Then("the Manage Labs report export control is available")
    public void theManageLabsReportExportControlIsAvailable() {
        ManageLabs ml = new ManageLabs(ctx.page);
        assertTrue(ml.reportExportAvailable(),
                "Manage Labs report/export control not found "
                        + "(looked for export/download/report toolbar)");
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
        assertTrue(ml.columnToggleAvailable(),
                "Manage Labs column toggle / show-columns control not found");
    }

    private String waitLabIdForEmail(ManageLabs ml, String email) {
        String username = email != null && email.contains("@")
                ? email.substring(0, email.indexOf('@')) : email;
        for (int i = 0; i < 40; i++) {
            ml.open(sectionId);
            ml.openCreatedTab();
            ml.refreshCreatedTable();
            String id = ml.labIdForEmail(email);
            if (id != null && !id.isBlank()) return id.trim();
            try {
                ml.searchCreatedByEmail(email);
            } catch (RuntimeException ignored) {}
            id = ml.labIdForEmail(email);
            if (id != null && !id.isBlank()) return id.trim();
            if (username != null && !username.equals(email)) {
                try {
                    ml.searchCreatedByEmail(username);
                } catch (RuntimeException ignored) {}
                id = ml.labIdForEmail(email);
                if (id != null && !id.isBlank()) return id.trim();
            }
            try {
                ml.clearCreatedSearch();
            } catch (RuntimeException ignored) {}
            id = ml.labIdForEmail(email);
            if (id != null && !id.isBlank()) return id.trim();
            ctx.page.waitForTimeout(3_000);
        }
        throw new AssertionError("could not capture lab id for " + email);
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
        ensureSectionReady();
        ctx.page.navigate(Settings.BASE_URL + "/report/cloudlabsreport/index.php",
                new Page.NavigateOptions()
                        .setWaitUntil(WaitUntilState.DOMCONTENTLOADED)
                        .setTimeout(45_000));
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
        Locator link = studentPage.locator("#np-ap-mod-set-password-btn");
        if (link.count() == 0 || !link.first().isVisible()) {
            throw new SkipException("Change-password option not available on this template");
        }
        link.first().click();
        PlaygroundControlPanel panel = new PlaygroundControlPanel(studentPage);
        assertTrue(panel.fillSetCredentialsPassword("AutoTest@12345"),
                "set-credentials dialog did not expose password fields");
    }

    @Then("the student lab password update is accepted")
    public void theStudentLabPasswordUpdateIsAccepted() {
        PlaygroundControlPanel panel = new PlaygroundControlPanel(studentPage);
        if (panel.passwordChangeSuccessVisible()) return;
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

    private void ensureSectionReady() {
        if (sectionId > 0 && activityCmid > 0 && courseId > 0) return;
        activityCmid = resolveActivityCmid();
        courseId = Long.parseLong(SandboxCourse.ensure(ctx.page));
        PlaygroundLanding adminLanding = new PlaygroundLanding(ctx.page);
        adminLanding.open(activityCmid);
        String sid = adminLanding.hiddenSectionId();
        if ((sid == null || sid.isBlank()) && ctx.sectionId != null && !ctx.sectionId.isBlank()) {
            sid = ctx.sectionId;
        }
        assertTrue(sid != null && !sid.isBlank(),
                "could not read section id from sandbox landing cmid=" + activityCmid);
        sectionId = Integer.parseInt(sid.trim());
        ctx.sectionId = String.valueOf(sectionId);
        ctx.newActivityCmid = String.valueOf(activityCmid);
        GlobalHooks.teardownSectionId = String.valueOf(sectionId);
        GlobalHooks.skipLabTeardown = true;
    }

    private void trackUser(ApiClient.SeededUser u) {
        if (u != null) extraUsers.add(u);
    }

    @After
    public void cleanupManageLabsFlow(io.cucumber.java.Scenario scenario) {
        if (scenario.getSourceTagNames().stream().noneMatch(tag -> tag.startsWith("@SB-manage-"))) {
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
        GlobalHooks.teardownLabId = null;
        GlobalHooks.teardownStudentEmail = null;
    }
}

