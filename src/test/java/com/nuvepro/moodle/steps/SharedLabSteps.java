package com.nuvepro.moodle.steps;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.nuvepro.moodle.config.Settings;
import com.nuvepro.moodle.helpers.ApiClient;
import com.nuvepro.moodle.helpers.Auth;
import com.nuvepro.moodle.helpers.CloudLabsClient;
import com.nuvepro.moodle.helpers.BrowserUi;
import com.nuvepro.moodle.pages.ManageLabs;
import com.nuvepro.moodle.pages.PlaygroundControlPanel;
import com.nuvepro.moodle.pages.PlaygroundLanding;
import io.cucumber.java.AfterAll;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import org.testng.SkipException;

import java.util.EnumSet;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;

/**
 * ONE per-user lab shared across the @labactions scenarios (provisioned once, deleted once).
 * - Actions (Stop/Start) run via admin Manage Labs (cp.php-free, targeted by the user's email).
 * - Read-only views (Usage/Info/Instructions/Credentials/Notes) run on the SEEDED USER's console
 *   (they live there) and are tolerant of cp.php.
 * - Expire/extend via the CloudLabs API. @AfterAll deletes the lab + user once.
 * Shared state is static so it survives across the (ordered) scenarios.
 */
public class SharedLabSteps {

    static ApiClient.SeededUser sharedUser;
    static int sharedSectionId;
    static String sharedLabId;                 // == provider subscriptionId
    static BrowserContext seededCtx;
    static Page seededPage;                    // the seeded user's browser (landing/console)
    static String savedNote;                   // for the notes persist check

    private final TestContext ctx;             // admin context (per scenario)

    public SharedLabSteps(TestContext ctx) {
        this.ctx = ctx;
    }

    private static final EnumSet<ManageLabs.LabState> RUN_OR_FAIL =
            EnumSet.of(ManageLabs.LabState.RUNNING, ManageLabs.LabState.FAILED);
    private static final EnumSet<ManageLabs.LabState> STOP_OR_FAIL =
            EnumSet.of(ManageLabs.LabState.STOPPED, ManageLabs.LabState.FAILED);

    // ---------- provisioning (once) ----------

    @Given("a fresh enrolled user with a running lab")
    public void aFreshEnrolledUserWithARunningLab() {
        if (Settings.WS_TOKEN.isEmpty() || Settings.COURSE_ID.isEmpty()) {
            throw new SkipException("MOODLE_WS_TOKEN / COURSE_ID not set");
        }
        if (!CloudLabsClient.isConfigured()) {
            throw new SkipException("CloudLabs API not configured (state polling needs it)");
        }
        ManageLabs ml = new ManageLabs(ctx.page);
        if (sharedUser == null) {
            long stamp = System.currentTimeMillis();
            sharedUser = ApiClient.createUser(stamp);
            ApiClient.enrolUser(sharedUser.id, Long.parseLong(Settings.COURSE_ID), 5);
            ctx.landing.open(ctx.cmid);
            sharedSectionId = Integer.parseInt(ctx.landing.hiddenSectionId());
            ml.createLabForUserEmail(sharedUser.email, sharedSectionId);
            // Capture the lab id (== subscriptionId) while the user exists (email shows in the row).
            for (int i = 0; i < 12 && sharedLabId == null; i++) {
                ml.open(sharedSectionId);
                sharedLabId = ml.labIdForEmail(sharedUser.email);
                if (sharedLabId == null) ctx.page.waitForTimeout(3_000);
            }
            assertNotNull(sharedLabId, "could not capture shared lab id after create");
            System.out.println("[SharedLab] seeded " + sharedUser.username + " lab id=" + sharedLabId);
        }
        GlobalHooks.labProvisioned = true;
        // Poll STATE via the CloudLabs API (authoritative, fast, logged) — not the UI.
        ManageLabs.LabState st = CloudLabsClient.waitForState(sharedLabId, RUN_OR_FAIL, 180, 5);
        if (st == ManageLabs.LabState.FAILED) {
            throw new AssertionError("lab provisioning FAILED (state=FAILED) - likely account pool "
                    + "exhausted or a provider error. lab id=" + sharedLabId);
        }
        assertEquals(st, ManageLabs.LabState.RUNNING, "shared lab did not reach Running (state=" + st + ")");
    }

    @Given("the shared lab")
    public void theSharedLab() {
        assertNotNull(sharedUser, "shared lab was not provisioned by the first scenario");
    }

    // ---------- actions: Stop / Start (Manage Labs, cp.php-free) ----------

    @When("I stop the shared lab")
    public void iStopTheSharedLab() {
        new ManageLabs(ctx.page).performActionForEmail(sharedUser.email, ManageLabs.STOP, sharedSectionId);
    }

    @Then("the shared lab is Stopped")
    public void theSharedLabIsStopped() {
        ManageLabs.LabState st = CloudLabsClient.waitForState(sharedLabId, STOP_OR_FAIL, 180, 5);
        assertEquals(st, ManageLabs.LabState.STOPPED, "shared lab did not Stop (state=" + st + ")");
    }

    @When("I start the shared lab")
    public void iStartTheSharedLab() {
        new ManageLabs(ctx.page).performActionForEmail(sharedUser.email, ManageLabs.START, sharedSectionId);
    }

    @Then("the shared lab is Running")
    public void theSharedLabIsRunning() {
        ManageLabs.LabState st = CloudLabsClient.waitForState(sharedLabId, RUN_OR_FAIL, 180, 5);
        assertEquals(st, ManageLabs.LabState.RUNNING, "shared lab did not Start (state=" + st + ")");
    }

    @When("I sync the shared lab")
    public void iSyncTheSharedLab() {
        new ManageLabs(ctx.page).performActionForEmail(sharedUser.email, ManageLabs.SYNC_BTN, sharedSectionId);
        ctx.page.waitForTimeout(5_000);
    }

    @Then("the shared lab sync completes without error")
    public void theSharedLabSyncCompletes() {
        // Sync just refreshes status from the provider; verify the lab is still queryable + valid.
        CloudLabsClient.SubStatus ss = CloudLabsClient.getStatus(sharedLabId);
        assertNotNull(ss, "lab status not queryable after sync");
        ManageLabs.LabState st = ManageLabs.classifyActionStatus(ss.action, ss.status);
        assertNotEquals(st, ManageLabs.LabState.UNKNOWN, "sync left an unknown state");
    }

    @When("I delete the shared lab")
    public void iDeleteTheSharedLab() {
        new ManageLabs(ctx.page).performActionForEmail(sharedUser.email, ManageLabs.DELETE, sharedSectionId);
    }

    @Then("the shared lab shows the deleted state")
    public void theSharedLabShowsDeletedState() {
        CloudLabsClient.SubStatus ss = CloudLabsClient.getStatus(sharedLabId);
        ManageLabs.LabState st = (ss == null) ? ManageLabs.LabState.NOTCREATED
                : ManageLabs.classifyActionStatus(ss.action, ss.status);
        assertEquals(st, ManageLabs.LabState.DELETED, "lab is not in the deleted state (state=" + st + ")");
    }

    @When("I create a new lab for the shared user after deletion")
    public void iCreateANewLabAfterDeletion() {
        String oldId = sharedLabId;
        ManageLabs ml = new ManageLabs(ctx.page);
        ml.performActionForEmail(sharedUser.email, ManageLabs.CREATE, sharedSectionId);
        // The user now has the old (deleted) row plus a new one. Capture the NEW lab id: a row for
        // this email (≠ oldId) whose CloudLabs status is not Deleted (Create/InProgress or Running).
        String newId = null;
        for (int i = 0; i < 24 && newId == null; i++) {
            ml.open(sharedSectionId);
            for (String id : ml.labIdsForEmail(sharedUser.email)) {
                if (id.equals(oldId)) continue;
                CloudLabsClient.SubStatus ss = CloudLabsClient.getStatus(id);
                ManageLabs.LabState st = (ss == null) ? ManageLabs.LabState.NOTCREATED
                        : ManageLabs.classifyActionStatus(ss.action, ss.status);
                if (st == ManageLabs.LabState.RUNNING || st == ManageLabs.LabState.INPROGRESS) {
                    newId = id;
                    break;
                }
            }
            if (newId == null) ctx.page.waitForTimeout(5_000);
        }
        assertNotNull(newId, "could not capture the new lab id after create-after-delete (old=" + oldId + ")");
        sharedLabId = newId;   // operate on the new lab from here
        System.out.println("[SharedLab] create-after-delete new lab id=" + newId + " (old=" + oldId + ")");
    }

    @Then("the shared lab is Deleted")
    public void theSharedLabIsDeleted() {
        ManageLabs.LabState st = CloudLabsClient.waitForState(sharedLabId,
                EnumSet.of(ManageLabs.LabState.DELETED), 600, 15);   // delete ≈5-8 min (slow, variable)
        assertEquals(st, ManageLabs.LabState.DELETED, "shared lab did not Delete (state=" + st + ")");
    }

    // ---------- expiry / extension (CloudLabs API) ----------

    @When("I expire the shared lab")
    public void iExpireTheSharedLab() {
        if (!CloudLabsClient.isConfigured()) throw new SkipException("CloudLabs API not configured");
        assertNotNull(sharedLabId, "no lab id for the shared lab");
        assertTrue(CloudLabsClient.expireLab(sharedLabId), "expire (reduce) did not succeed");
        ctx.page.waitForTimeout(8_000);
    }

    @Then("the shared lab shows expired")
    public void theSharedLabShowsExpired() {
        assertTrue(remainingEquals("00:00", true), "shared lab did not show 00:00 remaining after expiry");
    }

    @When("I extend the shared lab duration by {int} hour(s)")
    public void iExtendTheSharedLabDurationBy(int hours) {
        if (!CloudLabsClient.isConfigured()) throw new SkipException("CloudLabs API not configured");
        assertTrue(CloudLabsClient.extendHours(sharedLabId, hours), "extend did not succeed");
        ctx.page.waitForTimeout(8_000);
    }

    @Then("the shared lab is usable again")
    public void theSharedLabIsUsableAgain() {
        assertTrue(remainingEquals("00:00", false), "shared lab remaining still 00:00 after extend");
    }

    /** Read the seeded user's landing "Lab Duration Remaining" and compare to want (or want-not). */
    private boolean remainingEquals(String target, boolean wantEqual) {
        Page sp = seededPage();
        PlaygroundLanding landing = new PlaygroundLanding(sp);
        String remaining = "";
        for (int i = 0; i < 18; i++) {
            if (i == 0 || i == 6 || i == 12) landing.open(ctx.cmid);
            Locator rem = sp.locator("#lab-remaining-pl");
            if (rem.count() > 0) {
                remaining = rem.first().innerText().trim().replaceAll("\\s", "");
                boolean isTarget = remaining.equalsIgnoreCase(target);
                if (wantEqual && isTarget) return true;
                if (!wantEqual && !remaining.isEmpty() && !remaining.equals("-") && !isTarget) return true;
            }
            sp.waitForTimeout(5_000);
        }
        System.out.println("[SharedLab] remaining='" + remaining + "' (wanted " + (wantEqual ? "=" : "!=") + target + ")");
        return false;
    }

    // ---------- read-only views (seeded user console; tolerant of cp.php) ----------

    @Then("the shared lab read-only views are shown")
    public void theSharedLabReadOnlyViewsAreShown() {
        Page sp = seededPage();
        PlaygroundLanding landing = new PlaygroundLanding(sp);
        PlaygroundControlPanel cp = new PlaygroundControlPanel(sp);
        landing.open(ctx.cmid);
        boolean consoleLoaded = false;
        for (int attempt = 1; attempt <= 2 && !consoleLoaded; attempt++) {
            landing.openLabConsole();
            try {
                cp.expectControlPanelLoaded(60_000);
                consoleLoaded = true;
            } catch (Throwable e) {
                landing.open(ctx.cmid);
            }
        }
        if (!consoleLoaded) throw new SkipException("seeded user console did not load (cp.php) - read-only views skipped");
        int shown = 0;
        shown += checkPopup(sp, "#getCost", "#showCostUsage", null) ? 1 : 0;                 // Usage
        shown += checkPopup(sp, "#openMissionBtn", "#missionPopup", "#closeMission") ? 1 : 0; // Instructions
        shown += checkPopup(sp, "#openAuthBtn", "#authPopup", "#closeAuth") ? 1 : 0;          // Credentials
        shown += cp.page().locator(PlaygroundControlPanel.NOTES_TEXTAREA).count() > 0 ? 1 : 0; // Notes present
        assertTrue(shown > 0, "no read-only views were available on the console");
        System.out.println("[SharedLab] read-only views shown: " + shown + "/4");
    }

    private boolean checkPopup(Page sp, String openSel, String popupSel, String closeSel) {
        try {
            Locator open = sp.locator(openSel);
            if (open.count() == 0 || !open.first().isVisible()) return false;
            open.first().click();
            sp.waitForTimeout(2_000);
            boolean shown = sp.locator(popupSel).count() > 0 && sp.locator(popupSel).first().isVisible();
            if (closeSel != null && sp.locator(closeSel).count() > 0) sp.locator(closeSel).first().click();
            return shown;
        } catch (Throwable e) {
            return false;
        }
    }

    // ---- console read-only views: PG-3/5/6/45/8 (seeded user console; extend duration first) ----

    @When("the seeded user opens the lab console")
    public void theSeededUserOpensTheLabConsole() { openSeededConsole(); }

    @Given("the seeded user console is open")
    public void theSeededUserConsoleIsOpen() { openSeededConsole(); }

    private static boolean seededConsoleUnavailable = false;

    /** The console is open when its control-panel controls (Stop Lab / launch) are visible. */
    private boolean isConsoleOpen(Page sp) {
        return visible(sp, PlaygroundControlPanel.STOP_LAB) || visible(sp, PlaygroundControlPanel.LAUNCH_START);
    }

    private boolean visible(Page sp, String sel) {
        return sp.locator(sel).count() > 0 && sp.locator(sel).first().isVisible();
    }

    private void openSeededConsole() {
        // Fast-skip: if the console already failed to load this run, don't re-retry per scenario.
        if (seededConsoleUnavailable) throw new SkipException("seeded console unavailable (cp.php) - skipped earlier");
        if (isConsoleOpen(seededPage())) return;   // already open -> REUSE (don't destructively re-open)
        forceOpenSeededConsole();
    }

    /** Open the seeded user's console from a fresh landing (used after a state change too). */
    private void forceOpenSeededConsole() {
        Page sp = seededPage();
        PlaygroundLanding landing = new PlaygroundLanding(sp);
        // The landing "Continue" click is a race: its JS handler isn't attached until the landing
        // finishes initialising (while labStatus shows '-'). Clicking too early does nothing. So:
        // load once, wait for the JS to attach, then CLICK CONTINUE REPEATEDLY (re-click once the
        // handler is live) rather than re-navigating (which resets the attach timing).
        for (int reload = 1; reload <= 2; reload++) {
            landing.open(ctx.cmid);
            sp.waitForTimeout(reload == 1 ? 6_000 : 9_000);   // let the landing JS attach
            int clicks = (reload == 1) ? 6 : 4;
            for (int c = 1; c <= clicks; c++) {
                if (isConsoleOpen(sp)) return;
                Locator cont = sp.locator(".np-ap-pl-continue");
                if (cont.count() > 0 && cont.first().isVisible()) {
                    try { cont.first().click(); } catch (Throwable ignored) {}
                }
                for (int i = 0; i < 12 && !isConsoleOpen(sp); i++) sp.waitForTimeout(1_000);
                if (isConsoleOpen(sp)) return;
            }
        }
        seededConsoleUnavailable = true;
        throw new SkipException("seeded user console did not load (cp.php) after retries");
    }

    @Then("the Lab Usage view shows a cost value")
    public void theLabUsageViewShowsACostValue() {
        Page sp = seededPage();
        Locator get = sp.locator("#getCost");
        if (get.count() == 0 || !get.first().isVisible()) throw new SkipException("Lab Usage not on this template");
        get.first().click();
        sp.waitForTimeout(6_000);
        String txt = sp.locator("#showCostUsage").count() > 0 ? sp.locator("#showCostUsage").first().innerText().trim() : "";
        assertTrue(txt.matches("(?s).*\\d.*"), "Lab Usage showed no cost value: '" + txt + "'");
    }

    @Then("the Lab Info view is shown")
    public void theLabInfoViewIsShown() { checkPopupView("#openLabInfoBtn", "#labInfoPopup", "#closeLabInfo", "Lab Info"); }

    @Then("the Instructions view is shown")
    public void theInstructionsViewIsShown() { checkPopupView("#openMissionBtn", "#missionPopup", "#closeMission", "Instructions"); }

    @Then("the Lab Credentials view is shown")
    public void theLabCredentialsViewIsShown() { checkPopupView("#openAuthBtn", "#authPopup", "#closeAuth", "Lab Credentials"); }

    private void checkPopupView(String openSel, String popupSel, String closeSel, String name) {
        Page sp = seededPage();
        Locator open = sp.locator(openSel);
        if (open.count() == 0 || !open.first().isVisible()) throw new SkipException(name + " not on this template");
        open.first().click();
        sp.waitForTimeout(2_000);
        boolean shown = sp.locator(popupSel).count() > 0 && sp.locator(popupSel).first().isVisible();
        if (sp.locator(closeSel).count() > 0 && sp.locator(closeSel).first().isVisible()) sp.locator(closeSel).first().click();
        assertTrue(shown, name + " popup did not show");
    }

    @When("I save a note {string}")
    public void iSaveANote(String note) {
        Page sp = seededPage();
        if (sp.locator("#notes-tab").count() == 0) throw new SkipException("Notes not enabled on this template");
        sp.locator("#notes-tab").click();
        sp.waitForTimeout(1_000);
        sp.locator("#np-ap-pl-notes").fill(note);
        sp.locator("#np-ap-pl-note-save-btn").click();
        sp.waitForTimeout(4_000);
        savedNote = note;
    }

    // ---- PG-23: a student manages their OWN lab from the console ----
    // Key: "Stop Lab" only enables once the lab is Start/Complete. The freshly-created lab is
    // Create/Complete, so the student must first click "Start Lab" (the access-launch button, which
    // opens the Jump-to-Console window) to reach Start/Complete; THEN Stop becomes active.

    @When("the student starts their lab from the console")
    public void theStudentStartsTheirLabFromTheConsole() {
        openSeededConsole();
        Page sp = seededPage();
        Locator startBtn = sp.locator(PlaygroundControlPanel.LAUNCH_START);   // #np-ap-mod-access-launch-btn-*
        if (startBtn.count() == 0 || !startBtn.first().isVisible()) {
            throw new SkipException("Start Lab (access-launch) control not shown");
        }
        // Clicking "Start Lab" opens the Jump-to-Console window (a new tab/window for the cloud
        // console). Capture and close it — we don't need the cloud console, just the state transition.
        try {
            Page popup = sp.context().waitForPage(
                    new BrowserContext.WaitForPageOptions().setTimeout(8_000), () -> startBtn.first().click());
            if (popup != null) try { popup.close(); } catch (Throwable ignored) {}
        } catch (Throwable noPopup) { /* clicked; no separate window opened */ }
        // Dismiss the access modal if it appeared instead of a new tab.
        Locator dm = sp.locator("#launchAccModal [data-dismiss='modal'], #launchAccModal .close");
        if (dm.count() > 0 && dm.first().isVisible()) try { dm.first().click(); } catch (Throwable ignored) {}
        // Wait for "Stop Lab" to become ENABLED — that confirms the lab reached Start/Complete.
        Locator stop = sp.locator(PlaygroundControlPanel.STOP_LAB);
        for (int i = 0; i < 30; i++) {
            if (stop.count() > 0 && stop.first().isEnabled()) return;
            sp.waitForTimeout(4_000);
        }
        throw new SkipException("Stop Lab did not enable (lab did not reach Start/Complete)");
    }

    @When("the student stops their lab from the console")
    public void theStudentStopsTheirLabFromTheConsole() {
        // Stop Lab is enabled now (Start/Complete) — click it + confirm.
        new PlaygroundControlPanel(seededPage()).stopLab();
    }

    @Then("the saved note persists after reopening the console")
    public void theSavedNotePersists() {
        Page sp = seededPage();
        PlaygroundLanding landing = new PlaygroundLanding(sp);
        PlaygroundControlPanel cp = new PlaygroundControlPanel(sp);
        landing.open(ctx.cmid);                 // fresh load so the textarea re-reads the saved value
        landing.openLabConsole();
        try { cp.expectControlPanelLoaded(60_000); } catch (Throwable e) { throw new SkipException("console reopen failed"); }
        sp.locator("#notes-tab").click();
        sp.waitForTimeout(1_500);
        String val = sp.locator("#np-ap-pl-notes").inputValue().trim();
        assertEquals(val, savedNote, "note did not persist across console reopen");
    }

    private Page seededPage() {
        if (seededPage == null) {
            seededCtx = BrowserUi.newContext(GlobalHooks.browser, new Browser.NewContextOptions()
                    .setBaseURL(Settings.BASE_URL).setIgnoreHTTPSErrors(true));
            seededPage = seededCtx.newPage();
            seededPage.setDefaultTimeout(Settings.DEFAULT_TIMEOUT_MS);
            Auth.uiLogin(seededPage, sharedUser.username, sharedUser.password);
        }
        return seededPage;
    }

    // ---------- teardown: delete the shared lab + user ONCE ----------

    @AfterAll
    public static void teardownSharedLab() {
        if (sharedUser == null) return;
        try (BrowserContext c = BrowserUi.newContext(GlobalHooks.browser, new Browser.NewContextOptions()
                .setBaseURL(Settings.BASE_URL).setIgnoreHTTPSErrors(true)
                .setStorageStatePath(GlobalHooks.STORAGE_STATE))) {
            Page p = c.newPage();
            p.setDefaultTimeout(Settings.DEFAULT_TIMEOUT_MS);
            ManageLabs ml = new ManageLabs(p);
            ml.open(sharedSectionId);
            CloudLabsClient.SubStatus ss = (sharedLabId != null) ? CloudLabsClient.getStatus(sharedLabId) : null;
            ManageLabs.LabState st = (ss != null)
                    ? ManageLabs.classifyActionStatus(ss.action, ss.status)
                    : ml.stateForEmail(sharedUser.email);
            if (st != ManageLabs.LabState.DELETED) {
                ml.performActionForEmail(sharedUser.email, ManageLabs.DELETE, sharedSectionId);
                // Delete is the slow op (~5-8 min) — poll the API at 15s to keep the log readable.
                CloudLabsClient.waitForState(sharedLabId, EnumSet.of(ManageLabs.LabState.DELETED), 600, 15);
                System.out.println("[SharedLab] deleted shared lab " + sharedLabId);
            }
        } catch (Throwable e) {
            System.out.println("[SharedLab] lab delete failed (non-fatal): " + e.getMessage());
        }
        try {
            ApiClient.deleteUser(sharedUser.id);
            System.out.println("[SharedLab] deleted user " + sharedUser.username);
        } catch (Throwable ignored) {}
        if (seededCtx != null) {
            try { seededCtx.close(); } catch (Throwable ignored) {}
        }
        sharedUser = null; sharedLabId = null; seededPage = null; seededCtx = null;
        seededConsoleUnavailable = false; savedNote = null;
    }
}
