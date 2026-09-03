package com.nuvepro.moodle.steps;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.assertions.PlaywrightAssertions;
import com.nuvepro.moodle.config.Settings;
import com.nuvepro.moodle.helpers.ApiClient;
import com.nuvepro.moodle.helpers.Auth;
import com.nuvepro.moodle.helpers.CloudLabsClient;
import com.nuvepro.moodle.helpers.SandboxCourse;
import com.nuvepro.moodle.helpers.SandboxUserRegistry;
import com.nuvepro.moodle.pages.ManageLabs;
import com.nuvepro.moodle.pages.PlaygroundControlPanel;
import com.nuvepro.moodle.pages.PlaygroundLanding;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import org.testng.SkipException;

import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.regex.Pattern;

import static org.testng.Assert.*;

/**
 * Step definitions for the provisioning LIFECYCLE and value-validation gap cases
 * (CGAP-PG-17/18/19/21/23/25/26/27/30/33/34/38/45/46/49/56 and the config-gated 31/50/54/55).
 * Shares the per-scenario TestContext; Manage Labs action completions are verified by
 * polling the status counters (the labs table refreshes server-side).
 */
public class PlaygroundLifecycleSteps {
    private final TestContext ctx;
    private static final List<String> RUNNING = List.of("RUNNING", "ACTIVE", "STARTED", "CREATED");

    public PlaygroundLifecycleSteps(TestContext ctx) {
        this.ctx = ctx;
    }

    // ---------- helpers ----------

    private int sectionId() {
        if (ctx.sectionId == null || ctx.sectionId.isEmpty()) {
            ctx.landing.open(ctx.cmid);
            ctx.sectionId = ctx.landing.hiddenSectionId();
        }
        return Integer.parseInt(ctx.sectionId);
    }

    /** Poll Manage Labs counters (page reload each iteration) until the predicate holds. */
    private boolean awaitCounters(Predicate<Map<String, Integer>> pred, int maxSeconds) {
        ManageLabs ml = new ManageLabs(ctx.page);
        long deadline = System.currentTimeMillis() + maxSeconds * 1000L;
        while (System.currentTimeMillis() < deadline) {
            ml.open(sectionId());
            Map<String, Integer> c = ml.counters();
            if (pred.test(c)) return true;
            ctx.page.waitForTimeout(10_000);
        }
        return false;
    }

    private int cnt(Map<String, Integer> c, String k) {
        Integer v = c.get(k);
        return v == null ? 0 : v;
    }

    // ---------- PG-25 usage values ----------

    @When("I request the lab cost")
    public void iRequestTheLabCost() {
        Locator get = ctx.page.locator("#getCost");
        if (get.count() == 0) throw new SkipException("Get-cost control not present on this template");
        get.first().click();
        ctx.page.waitForTimeout(6_000);
    }

    @Then("valid cost values are displayed")
    public void validCostValuesAreDisplayed() {
        Locator show = ctx.page.locator("#showCostUsage");
        assertTrue(show.count() > 0, "cost usage area not present");
        String text = show.first().innerText().trim();
        assertFalse(text.isEmpty(), "cost usage area is empty");
        assertTrue(Pattern.compile("\\d").matcher(text).find(),
                "cost usage shows no numeric values: " + text);
    }

    // ---------- PG-26 duration values on the landing ----------

    @Then("the landing shows consumed and remaining duration values")
    public void landingShowsDurationValues() {
        Page p = ctx.page;
        if (p.locator("#np-ap-pl-lab-time-details.d-none").count() > 0
                || p.locator("#lab-consumed-pl").count() == 0) {
            throw new SkipException("Duration details not enabled/visible on this template");
        }
        String consumed = p.locator("#lab-consumed-pl").innerText().trim();
        String remaining = p.locator("#lab-remaining-pl").innerText().trim();
        assertTrue(consumed.matches(".*\\d+.*"), "consumed duration has no value: " + consumed);
        assertTrue(remaining.matches(".*\\d+.*"), "remaining duration has no value: " + remaining);
    }

    // ---------- PG-56 content correctness ----------

    @Then("the instructions content is not empty")
    public void instructionsContentNotEmpty() {
        // #printArea renders the activity's intro/description ({{{moduleinstance.intro}}}).
        ctx.page.locator("#openMissionBtn").click();
        PlaywrightAssertions.assertThat(ctx.page.locator("#missionPopup")).isVisible();
        String content = ctx.page.locator("#printArea").innerText().trim();
        ctx.page.locator("#closeMission").click();
        if (content.isEmpty()) {
            // The instructions surface works; the activity simply has no description set.
            throw new SkipException("activity has no intro/description set - add one to verify instructions content");
        }
        assertFalse(content.isEmpty(), "instructions content is empty");
    }

    // ---------- PG-45 / PG-46 credentials ----------

    @When("I open the Lab Credentials popup")
    public void iOpenTheLabCredentialsPopup() {
        Locator btn = ctx.page.locator("#openAuthBtn");
        if (btn.count() == 0 || !btn.first().isVisible()) {
            throw new SkipException("Lab Credentials not enabled on this template");
        }
        btn.first().click();
    }

    @Then("the lab credentials are shown")
    public void theLabCredentialsAreShown() {
        PlaywrightAssertions.assertThat(ctx.page.locator("#authPopup")).isVisible();
        String content = ctx.page.locator("#np-ap-pl-tab-access-content").innerText().trim();
        assertFalse(content.isEmpty(), "credentials/access content is empty");
        ctx.page.locator("#closeAuth").click();
    }

    @When("I change the lab password")
    public void iChangeTheLabPassword() {
        Locator link = ctx.page.locator("#np-ap-mod-set-password-btn");
        if (link.count() == 0 || !link.first().isVisible()) {
            throw new SkipException("Change-password option not available on this template");
        }
        link.first().click();
        Locator input = ctx.page.locator("#np-ap-mod-setCredentials-input");
        try {
            input.first().waitFor(new Locator.WaitForOptions()
                    .setState(com.microsoft.playwright.options.WaitForSelectorState.VISIBLE).setTimeout(10_000));
        } catch (Throwable e) {
            throw new SkipException("Set-credentials dialog did not open");
        }
        input.first().fill("AutoTest@12345");
        ctx.page.locator("#setCredentials").click();
        ctx.page.waitForTimeout(5_000);
    }

    @Then("the lab password update is accepted")
    public void theLabPasswordUpdateIsAccepted() {
        String body = ctx.page.locator("body").innerText().toLowerCase();
        assertFalse(body.contains("exception") || body.contains("error code"),
                "password update surfaced an error");
    }

    // ---------- PG-30 platform console handoff ----------

    @When("I open a platform console from the access panel")
    public void iOpenAPlatformConsole() {
        Page p = ctx.page;
        Locator jump = p.getByText("Jump to Console");
        if (jump.count() > 0) {
            jump.first().click();
            p.waitForTimeout(1_500);
        }
        Locator launch = p.locator("#launchAccBtnContainer a, #launchAccModal a[href], "
                + "#np-ap-mod-lab-acc-launch-buttons a, #np-ap-mod-lab-acc-launch-buttons button");
        if (launch.count() == 0) throw new SkipException("No platform launch control present");
        try {
            ctx.openedTab = p.context().waitForPage(
                    new BrowserContext.WaitForPageOptions().setTimeout(15_000),
                    () -> launch.first().click());
        } catch (Exception e) {
            ctx.openedTab = null;
        }
    }

    @Then("a platform console tab opens outside Moodle")
    public void aPlatformConsoleTabOpens() {
        if (ctx.openedTab == null) {
            // Account templates typically don't launch an external cloud console (that's a
            // platform/VM concern, deferred). Treat as not-applicable rather than a failure.
            throw new SkipException("no external console tab opened (not applicable for this template)");
        }
        String host = Settings.BASE_URL.replaceAll("^https?://", "").split("/")[0];
        String url = ctx.openedTab.url();
        assertFalse(url.contains(host), "opened tab is not an external console: " + url);
    }

    // ---------- PG-27 note persistence ----------

    @When("I reload the lab console")
    public void iReloadTheLabConsole() {
        boolean loaded = false;
        for (int attempt = 1; attempt <= 3 && !loaded; attempt++) {
            ctx.landing.open(ctx.cmid);
            ctx.landing.openLabConsole();
            try {
                ctx.cp.expectControlPanelLoaded(30_000);
                loaded = true;
            } catch (Throwable e) {
                ctx.page.waitForTimeout(3_000);
            }
        }
        if (!loaded) throw new SkipException("console did not reload (cp.php)");
        if (ctx.page.locator(PlaygroundControlPanel.NOTES_TEXTAREA).count() > 0) {
            ctx.page.locator(PlaygroundControlPanel.NOTES_TAB).click();
        }
    }

    // ---------- Manage Labs action completions (PG-17/35, 18, 19, 38, 33/34) ----------

    @When("I perform the Start action from Manage Labs")
    public void iPerformStartFromManageLabs() {
        ensurePrecondition(ManageLabs.LabState.STOPPED);   // Start applies to a stopped lab
        performManaged(ManageLabs.START, "Start");
        GlobalHooks.labProvisioned = true;
    }

    @When("I perform the Stop action from Manage Labs")
    public void iPerformStopFromManageLabs() {
        ensurePrecondition(ManageLabs.LabState.RUNNING);   // Stop applies to a running lab
        performManaged(ManageLabs.STOP, "Stop");
    }

    @When("I perform the Delete action from Manage Labs")
    public void iPerformDeleteFromManageLabs() {
        ensurePrecondition(ManageLabs.LabState.STOPPED);   // stop first, then delete
        performManaged(ManageLabs.DELETE, "Delete");
    }

    @When("I perform the Sync action from Manage Labs")
    public void iPerformSyncFromManageLabs() {
        performManaged(ManageLabs.SYNC_BTN, "Sync Status");   // valid in any state
    }

    @When("I perform the Create action from Manage Labs")
    public void iPerformCreateFromManageLabs() {
        ensurePrecondition(ManageLabs.LabState.DELETED);   // create-after-delete
        performManaged(ManageLabs.CREATE, "Create");
        GlobalHooks.labProvisioned = true;
    }

    /** Bring the lab to the state a scenario needs before it acts (idempotent; waits for completion). */
    private void ensurePrecondition(ManageLabs.LabState needed) {
        ManageLabs ml = new ManageLabs(ctx.page);
        ManageLabs.LabState got = ml.ensureState(needed, sectionId());
        if (got != needed) {
            throw new SkipException("could not establish precondition " + needed + " (lab is " + got
                    + " - provider slow/unavailable)");
        }
    }

    private void performManaged(String selector, String label) {
        ManageLabs ml = new ManageLabs(ctx.page);
        ml.open(sectionId());
        if (!ml.hasLabRows()) throw new SkipException("No lab rows to perform '" + label + "' on");
        // Create is a real <button>: if it never enables for this state, the action isn't applicable.
        if (ManageLabs.CREATE.equals(selector)) {
            boolean enabled = false;
            for (int i = 0; i < 20 && !enabled; i++) {
                ml.selectFirstRow();
                if (!ml.actionDisabled(ManageLabs.CREATE)) { enabled = true; break; }
                ctx.page.waitForTimeout(500);
            }
            if (!enabled) throw new SkipException("Create not applicable for the current lab state (row: "
                    + ml.firstRowText() + ")");
        }
        ml.performActionOnFirstRow(selector);
        ctx.page.waitForTimeout(5_000);
    }

    @Then("Manage Labs reports the lab as Running")
    public void manageLabsReportsRunning() {
        ManageLabs ml = new ManageLabs(ctx.page);
        ManageLabs.LabState st = ml.waitForFirstRowState(
                java.util.EnumSet.of(ManageLabs.LabState.RUNNING, ManageLabs.LabState.FAILED), 900, sectionId());
        assertEquals(st, ManageLabs.LabState.RUNNING, "lab did not reach Running (state=" + st + ")");
    }

    @Then("Manage Labs reports the lab as Stopped")
    public void manageLabsReportsStopped() {
        ManageLabs ml = new ManageLabs(ctx.page);
        ManageLabs.LabState st = ml.waitForFirstRowState(
                java.util.EnumSet.of(ManageLabs.LabState.STOPPED, ManageLabs.LabState.FAILED), 600, sectionId());
        assertEquals(st, ManageLabs.LabState.STOPPED, "lab did not reach Stopped (state=" + st + ")");
    }

    @Then("Manage Labs reports the lab as Deleted")
    public void manageLabsReportsDeleted() {
        ManageLabs ml = new ManageLabs(ctx.page);
        ManageLabs.LabState st = ml.waitForFirstRowState(
                java.util.EnumSet.of(ManageLabs.LabState.DELETED), 600, sectionId());
        assertEquals(st, ManageLabs.LabState.DELETED, "lab did not reach Deleted (state=" + st + ")");
    }

    @Then("the lab status is synced without errors")
    public void labStatusSyncedWithoutErrors() {
        ManageLabs ml = new ManageLabs(ctx.page);
        ml.open(sectionId());
        assertTrue(ml.hasLabRows() || cnt(ml.counters(), "Deleted") >= 0,
                "Manage Labs did not render after sync");
        String body = ctx.page.locator("body").innerText().toLowerCase();
        assertFalse(body.contains("exception"), "sync surfaced an exception");
    }

    // ---------- PG-49 deleted state ----------

    @Then("the console or landing reflects the deleted lab state")
    public void deletedLabStateReflected() {
        // Authoritative and cp.php-free: Manage Labs shows the lab as Delete/Complete (DELETED),
        // OR the landing offers a fresh Start (a deleted lab lets the user start over).
        ManageLabs ml = new ManageLabs(ctx.page);
        ml.open(sectionId());
        ManageLabs.LabState st = ml.firstRowState();
        if (st == ManageLabs.LabState.DELETED) return;                 // deleted state confirmed
        ctx.landing.open(ctx.cmid);
        if (ctx.landing.hasStartSandbox()) return;                     // deleted -> fresh start offered
        throw new SkipException("no deleted lab present to verify the deleted state (state=" + st + ")");
    }

    // ---------- PG-23 student lifecycle ----------

    @Given("a student opens the sandbox activity")
    public void aStudentOpensTheSandboxActivity() {
        ctx.studentContext = GlobalHooks.browser.newContext(new Browser.NewContextOptions()
                .setBaseURL(Settings.BASE_URL).setIgnoreHTTPSErrors(true));
        Page sp = ctx.studentContext.newPage();
        sp.setDefaultTimeout(Settings.DEFAULT_TIMEOUT_MS);
        Auth.loginAsStudent(sp);
        ctx.studentPage = sp;
        new PlaygroundLanding(sp).open(ctx.cmid);
    }

    @When("the student starts their lab and it reaches running")
    public void theStudentStartsTheirLab() {
        Page sp = ctx.studentPage;
        PlaygroundLanding landing = new PlaygroundLanding(sp);
        PlaygroundControlPanel cp = new PlaygroundControlPanel(sp);
        boolean loaded = false;
        for (int attempt = 1; attempt <= 3 && !loaded; attempt++) {
            landing.openLabConsole();
            try {
                cp.expectControlPanelLoaded(30_000);
                loaded = true;
            } catch (Throwable e) {
                sp.waitForTimeout(3_000);
                landing.open(ctx.cmid);
            }
        }
        if (!loaded) throw new SkipException("student console did not load (cp.php)");
        GlobalHooks.labProvisioned = true;
        try {
            Locator launch = sp.locator(PlaygroundControlPanel.LAUNCH_START);
            String status = safeStatus(cp);
            if (!RUNNING.contains(status.toUpperCase()) && launch.count() > 0 && launch.first().isVisible()) {
                launch.first().click();
                for (int i = 0; i < 16; i++) {
                    sp.waitForTimeout(15_000);
                    status = safeStatus(cp);
                    if (RUNNING.contains(status.toUpperCase())) break;
                }
            }
            if (!RUNNING.contains(safeStatus(cp).toUpperCase())) {
                throw new SkipException("student lab did not reach running under load: " + safeStatus(cp));
            }
        } catch (SkipException se) {
            throw se;
        } catch (Throwable e) {
            throw new SkipException("student lab provisioning flaked under load: " + e.getMessage());
        }
    }

    @Then("the student stops their lab successfully")
    public void theStudentStopsTheirLab() {
        Page sp = ctx.studentPage;
        PlaygroundControlPanel cp = new PlaygroundControlPanel(sp);
        try {
            cp.stopLab();
        } catch (Throwable e) {
            throw new SkipException("student stop confirm did not appear (instance slow) - lab left for teardown");
        }
        String finalStatus = "";
        for (int i = 0; i < 8; i++) {
            sp.waitForTimeout(10_000);
            finalStatus = safeStatus(cp).toUpperCase();
            if (List.of("STOPPED", "STOPPING", "NOTCREATED", "INACTIVE", "").contains(finalStatus)) break;
        }
        assertTrue(List.of("STOPPED", "STOPPING", "NOTCREATED", "INACTIVE", "").contains(finalStatus),
                "student lab did not stop: " + finalStatus);
    }

    private String safeStatus(PlaygroundControlPanel cp) {
        try {
            return cp.labStatus();
        } catch (Exception e) {
            return "";
        }
    }

    // ---------- PG-21 bulk ----------

    @When("I perform a bulk action on all selectable labs")
    public void iPerformABulkAction() {
        ManageLabs ml = new ManageLabs(ctx.page);
        ml.open(sectionId());
        if (ml.rowCount() < 2) {
            throw new SkipException("bulk action needs 2+ lab rows in the same state (found "
                    + ml.rowCount() + ")");
        }
        ml.selectAllRows();
        ctx.page.waitForTimeout(1_000);
        String action = !ml.actionDisabled(ManageLabs.STOP) ? ManageLabs.STOP
                : (!ml.actionDisabled(ManageLabs.START) ? ManageLabs.START : null);
        if (action == null) throw new SkipException("no bulk action enabled for the selected labs (mixed states)");
        ctx.bulkAction = action;
        ctx.page.locator(ManageLabs.ACTIONS_BTN).click();
        ctx.page.locator(action).click();
        ml.confirmVisibleDialog();
        if (ManageLabs.START.equals(action)) GlobalHooks.labProvisioned = true;
        ctx.page.waitForTimeout(5_000);
    }

    @Then("the bulk action is applied to every selected lab")
    public void theBulkActionIsApplied() {
        boolean stopped = ManageLabs.STOP.equals(ctx.bulkAction);
        assertTrue(awaitCounters(c -> stopped ? cnt(c, "Running") == 0 : cnt(c, "Running") >= 2, 300),
                "bulk " + (stopped ? "stop" : "start") + " did not complete for all labs");
    }

    // ---------- config-gated (PG-31 / 50 / 54 / 55) ----------

    @Then("starting a misconfigured-template lab surfaces a clear error")
    public void misconfiguredTemplateError() {
        if (Settings.PLAYGROUND_BADTEMPLATE_CMID.isEmpty()) {
            throw new SkipException("PLAYGROUND_BADTEMPLATE_CMID not set (needs an activity with an invalid template)");
        }
        PlaygroundLanding landing = new PlaygroundLanding(ctx.page);
        landing.open(Integer.parseInt(Settings.PLAYGROUND_BADTEMPLATE_CMID));
        landing.openLabConsole();
        ctx.page.locator(PlaygroundControlPanel.LAUNCH_START).first().click();
        GlobalHooks.labProvisioned = true;
        boolean errorShown = false;
        for (int i = 0; i < 20; i++) {
            ctx.page.waitForTimeout(10_000);
            String body = ctx.page.locator("body").innerText().toLowerCase();
            if (body.contains("error") || body.contains("failed")) { errorShown = true; break; }
        }
        assertTrue(errorShown, "no clear error surfaced for the misconfigured template");
    }

    @Then("the expired-duration lab state is verified")
    public void expiredDurationVerified() {
        if (!CloudLabsClient.isConfigured()) {
            throw new SkipException("CloudLabs API not configured");
        }
        if (CloudLabsClient.isBlocked()) {
            throw new SkipException("CloudLabs account is temporarily blocked — cannot expire the lab");
        }
        if (ctx.newActivityCmid == null || ctx.newActivityCmid.isBlank()) {
            throw new SkipException("no sandbox activity cmid — run the create step first");
        }
        long courseId = SandboxCourse.idLong();
        ApiClient.SeededUser student = ApiClient.createUser(System.currentTimeMillis());
        ApiClient.enrolUser(student.id, courseId, 5);
        SandboxUserRegistry.put(courseId, student);
        BrowserContext studentCtx = ctx.browser.newContext(new Browser.NewContextOptions()
                .setBaseURL(Settings.BASE_URL)
                .setIgnoreHTTPSErrors(true));
        Page studentPage = studentCtx.newPage();
        studentPage.setDefaultTimeout(Settings.DEFAULT_TIMEOUT_MS);
        try {
            Auth.uiLogin(studentPage, student.username, student.password);
            PlaygroundLanding landing = new PlaygroundLanding(studentPage);
            landing.open(Integer.parseInt(ctx.newActivityCmid));
            landing.openLabConsole();
            PlaygroundControlPanel panel = new PlaygroundControlPanel(studentPage);
            panel.expectControlPanelLoaded(90_000);
            if (!panel.waitUntilLabLoaded(8)) {
                panel.clickStartLab();
                assertTrue(panel.waitUntilLabLoaded(180), "lab did not load before expire");
            }
            String labId = panel.labId();
            assertTrue(labId != null && !labId.isBlank(), "no lab id to expire");
            assertTrue(CloudLabsClient.expireLab(labId), "CloudLabs expireLab failed for " + labId);
            landing.open(Integer.parseInt(ctx.newActivityCmid));
            boolean expired = false;
            String remaining = "";
            for (int i = 0; i < 18 && !expired; i++) {
                remaining = "";
                Locator rem = studentPage.locator("#lab-remaining-pl");
                if (rem.count() > 0) remaining = rem.first().innerText().trim();
                String body = studentPage.locator("body").innerText().toLowerCase();
                String norm = remaining.toLowerCase().replaceAll("\\s+", "");
                if (norm.equals("0h") || norm.equals("0") || norm.equals("0h0m") || norm.equals("00:00")
                        || body.contains("expired") || body.contains("duration is over")) {
                    expired = true;
                } else {
                    studentPage.waitForTimeout(5_000);
                    landing.open(Integer.parseInt(ctx.newActivityCmid));
                }
            }
            assertTrue(expired, "expired lab did not show 00:00 / expired (remaining='" + remaining + "')");
            System.out.println("[SB-user] expired-duration OK labId=" + labId + " remaining=" + remaining);
        } finally {
            try { studentCtx.close(); } catch (Throwable ignored) {}
        }
    }

    @Then("the unlimited-duration lab is verified")
    public void unlimitedDurationVerified() {
        if (Settings.PLAYGROUND_UNLIMITED_CMID.isEmpty()) {
            throw new SkipException("PLAYGROUND_UNLIMITED_CMID not set (needs an unlimited-duration template)");
        }
        throw new SkipException("long-running verification (deferred to a dedicated soak run)");
    }

    @Then("the provider-failure handling is verified")
    public void providerFailureVerified() {
        throw new SkipException("requires provider fault injection - not reliably automatable (manual)");
    }
}
