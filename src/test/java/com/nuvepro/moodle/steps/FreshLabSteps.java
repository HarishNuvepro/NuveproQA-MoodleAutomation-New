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
import io.cucumber.java.After;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import org.testng.SkipException;

import java.util.EnumSet;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;

/**
 * Lab-action tests on a FRESH per-user lab, to avoid the shared-lab expiry problem.
 * Flow: seed a user + enrol (Web Services) -> create a lab for them (Manage Labs, admin,
 * cp.php-free) -> perform actions and verify completion via the user's Manage Labs row.
 *
 * NOTE: the seeding token has no delete-user function, so test users accumulate (delete via
 * the Moodle UI or add core_user_delete_users to the service to enable API cleanup).
 */
public class FreshLabSteps {
    private static final EnumSet<ManageLabs.LabState> RUN_OR_FAIL =
            EnumSet.of(ManageLabs.LabState.RUNNING, ManageLabs.LabState.FAILED);
    private static final EnumSet<ManageLabs.LabState> STOP_OR_FAIL =
            EnumSet.of(ManageLabs.LabState.STOPPED, ManageLabs.LabState.FAILED);

    private final TestContext ctx;
    private ApiClient.SeededUser user;
    private String subscriptionId;
    private boolean extendOk;

    public FreshLabSteps(TestContext ctx) {
        this.ctx = ctx;
    }

    private int sectionId() {
        if (ctx.sectionId == null || ctx.sectionId.isEmpty()) {
            ctx.landing.open(ctx.cmid);
            ctx.sectionId = ctx.landing.hiddenSectionId();
        }
        return Integer.parseInt(ctx.sectionId);
    }

    @Given("a freshly enrolled user")
    public void aFreshlyEnrolledUser() {
        if (Settings.WS_TOKEN.isEmpty()) throw new SkipException("MOODLE_WS_TOKEN not set");
        if (Settings.PLAYGROUND_CMID.isEmpty()) throw new SkipException("PLAYGROUND_CMID not set");
        long stamp = System.currentTimeMillis();
        user = ApiClient.createUser(stamp);
        PlaygroundLanding landing = new PlaygroundLanding(ctx.page);
        landing.open(ctx.cmid);
        String sid = landing.hiddenSectionId();
        if (sid != null && !sid.isBlank()) {
            ctx.sectionId = sid.trim();
        }
        long courseId = resolvePlaygroundCourseId(landing);
        ApiClient.enrolUser(user.id, courseId, 5); // 5 = student
        ctx.page.waitForTimeout(5_000); // Manage Labs not-created tab can lag WS enrol
        ctx.seededUser = user;
        System.out.println("[FreshLab] seeded user " + user.username + " (id=" + user.id
                + ") course=" + courseId + " section=" + ctx.sectionId);
    }

    private long resolvePlaygroundCourseId(PlaygroundLanding landing) {
        String fromLanding = landing.hiddenCourseId();
        if (fromLanding != null && !fromLanding.isBlank()) {
            return Long.parseLong(fromLanding.trim());
        }
        if (Settings.COURSE_ID.isEmpty()) {
            throw new SkipException("COURSE_ID not set and playground landing has no course id");
        }
        return Long.parseLong(Settings.COURSE_ID);
    }

    @When("I create a lab for that user from Manage Labs")
    public void iCreateALabForThatUser() {
        ManageLabs ml = new ManageLabs(ctx.page);
        int sid = sectionId();
        boolean created = false;
        for (int attempt = 1; attempt <= 6 && !created; attempt++) {
            try {
                clickManageLabsSyncIfPresent(ml, sid);
                ml.createLabForUserEmail(user.email, sid);
                created = true;
            } catch (RuntimeException e) {
                System.out.println("[FreshLab] Manage Labs Create attempt " + attempt
                        + " for " + user.email + ": " + e.getMessage());
                ctx.page.waitForTimeout(8_000);
            }
        }
        if (!created) {
            System.out.println("[FreshLab] No labs Create failed — provisioning via student Start Sandbox");
            recreateLabViaStudentStart();
        }
        GlobalHooks.labProvisioned = true;
        // Capture the lab id (== provider subscriptionId) from the created row while the user exists
        // (its email is shown). From here on we poll STATUS via the CloudLabs API, not the UI.
        for (int i = 0; i < 12 && subscriptionId == null; i++) {
            ml.open(sectionId());
            subscriptionId = ml.labIdForEmail(user.email);
            if (subscriptionId == null) ctx.page.waitForTimeout(3_000);
        }
        assertNotNull(subscriptionId, "could not capture lab id after create for " + user.email);
        ctx.freshLabSubscriptionId = subscriptionId;
        System.out.println("[FreshLab] captured lab id (subscriptionId) = " + subscriptionId);
    }

    private void clickManageLabsSyncIfPresent(ManageLabs ml, int sectionId) {
        try {
            ml.open(sectionId);
            ctx.page.locator(ManageLabs.SYNC_BTN).click(new Locator.ClickOptions().setForce(true));
            ctx.page.waitForTimeout(3_000);
        } catch (RuntimeException ignored) {
        }
    }

    /** Fallback when the not-created tab has not indexed the enrolment yet. */
    private void recreateLabViaStudentStart() {
        BrowserContext sc = BrowserUi.newContext(GlobalHooks.browser, new Browser.NewContextOptions()
                .setBaseURL(Settings.BASE_URL).setIgnoreHTTPSErrors(true));
        Page sp = sc.newPage();
        sp.setDefaultTimeout(Settings.DEFAULT_TIMEOUT_MS);
        try {
            Auth.uiLogin(sp, user.username, user.password);
            PlaygroundLanding landing = new PlaygroundLanding(sp);
            landing.open(ctx.cmid);
            sp.waitForTimeout(1_500);
            if (sp.locator(PlaygroundLanding.START_SANDBOX).count() > 0
                    && sp.locator(PlaygroundLanding.START_SANDBOX).first().isVisible()) {
                landing.startSandbox(true);
            } else {
                landing.openLabConsole();
            }
            PlaygroundControlPanel panel = new PlaygroundControlPanel(sp);
            if (!panel.waitUntilLabLoaded(180)) {
                throw new IllegalStateException("student Start Sandbox did not load lab console");
            }
            for (int i = 0; i < 20 && (subscriptionId == null || subscriptionId.isBlank()); i++) {
                try {
                    String id = panel.labId();
                    if (id != null && !id.isBlank() && !"0".equals(id.trim())) {
                        subscriptionId = id.trim();
                        break;
                    }
                } catch (RuntimeException ignored) {
                }
                String hidden = landing.hiddenLabId();
                if (hidden != null && !hidden.isBlank() && !"0".equals(hidden.trim())) {
                    subscriptionId = hidden.trim();
                    break;
                }
                sp.waitForTimeout(2_000);
            }
        } finally {
            try {
                sc.close();
            } catch (Throwable ignored) {
            }
        }
        if (subscriptionId == null || subscriptionId.isBlank()) {
            ManageLabs ml = new ManageLabs(ctx.page);
            for (int i = 0; i < 12; i++) {
                ml.open(sectionId());
                subscriptionId = ml.labIdForEmail(user.email);
                if (subscriptionId != null) break;
                ctx.page.waitForTimeout(3_000);
            }
        }
        assertNotNull(subscriptionId, "could not capture lab id after student Start Sandbox for " + user.email);
    }

    @Then("that user's lab reaches Running")
    public void thatUsersLabReachesRunning() {
        assertNotNull(subscriptionId, "lab id not captured");
        ManageLabs.LabState st = pollRunningViaCloudLabs();
        if (st != ManageLabs.LabState.RUNNING) {
            ManageLabs ml = new ManageLabs(ctx.page);
            clickManageLabsSyncIfPresent(ml, sectionId());
            st = ml.waitForEmailState(user.email, RUN_OR_FAIL, 180, sectionId());
        }
        assertEquals(st, ManageLabs.LabState.RUNNING, "fresh lab did not reach Running (state=" + st + ")");
    }

    private ManageLabs.LabState pollRunningViaCloudLabs() {
        if (!CloudLabsClient.isConfigured() || CloudLabsClient.isBlocked()) {
            return ManageLabs.LabState.NOTCREATED;
        }
        try {
            return CloudLabsClient.waitForState(subscriptionId, RUN_OR_FAIL, 180, 5);
        } catch (RuntimeException e) {
            System.out.println("[FreshLab] CloudLabs Running poll failed — Manage Labs UI fallback: "
                    + e.getMessage());
            return ManageLabs.LabState.NOTCREATED;
        }
    }

    @When("I extend that user's lab duration by {int} hour")
    public void iExtendThatUsersLabDuration(int hours) {
        if (!CloudLabsClient.isConfigured()) throw new SkipException("CloudLabs API not configured (.env)");
        assertNotNull(subscriptionId, "lab id not captured");
        extendOk = CloudLabsClient.extendHours(subscriptionId, hours);   // reuse captured id
    }

    @Then("the lab duration extension succeeds")
    public void theLabDurationExtensionSucceeds() {
        assertTrue(extendOk, "CloudLabs extendDuration did not return SUCCESS for subscription " + subscriptionId);
    }

    @When("I expire that user's lab")
    public void iExpireThatUsersLab() {
        if (!CloudLabsClient.isConfigured()) throw new SkipException("CloudLabs API not configured (.env)");
        assertNotNull(subscriptionId, "lab id not captured");
        assertTrue(CloudLabsClient.expireLab(subscriptionId), "CloudLabs reduce/expire did not succeed");
        ctx.page.waitForTimeout(8_000);   // let the provider apply the new (past) end time
    }

    @Then("that user sees the lab as expired")
    public void thatUserSeesLabAsExpired() {
        // The expired state is user-facing: log in AS the seeded user and open their lab console.
        ctx.studentContext = BrowserUi.newContext(GlobalHooks.browser, new Browser.NewContextOptions()
                .setBaseURL(Settings.BASE_URL).setIgnoreHTTPSErrors(true));
        Page sp = ctx.studentContext.newPage();
        sp.setDefaultTimeout(Settings.DEFAULT_TIMEOUT_MS);
        Auth.uiLogin(sp, user.username, user.password);
        String bodyClass = sp.locator("body").getAttribute("class");
        System.out.println("[Expired] after login url=" + sp.url() + " loggedIn="
                + (bodyClass == null || !bodyClass.contains("notloggedin")));
        PlaygroundLanding landing = new PlaygroundLanding(sp);
        // Expiry surfaces on the LANDING as "Lab Duration Remaining" = 0h (#lab-remaining-pl),
        // populated by the status worker — no console/cp.php needed.
        boolean expired = false;
        String remaining = "";
        for (int i = 0; i < 18 && !expired; i++) {
            if (i == 0 || i == 6 || i == 12) landing.open(ctx.cmid);   // fresh loads to recompute
            Locator rem = sp.locator("#lab-remaining-pl");
            if (rem.count() > 0) {
                remaining = rem.first().innerText().trim();
                String norm = remaining.toLowerCase().replaceAll("\\s", "");
                if (norm.equals("0h") || norm.equals("0") || norm.equals("0h0m") || norm.equals("00:00")
                        || norm.matches("0h0m.*")) {
                    expired = true;
                    break;
                }
            }
            sp.waitForTimeout(5_000);
        }
        System.out.println("[Expired] lab-remaining-pl='" + remaining + "' status="
                + (sp.locator("#lab-status-pl").count() > 0 ? sp.locator("#lab-status-pl").first().innerText().trim() : "?"));
        assertTrue(expired, "expired lab 'Lab Duration Remaining' was not 0h (was '" + remaining + "')");
    }

    @When("I stop that user's lab")
    public void iStopThatUsersLab() {
        new ManageLabs(ctx.page).performActionForEmail(user.email, ManageLabs.STOP, sectionId());
    }

    @Then("that user's lab reaches Stopped")
    public void thatUsersLabReachesStopped() {
        assertNotNull(subscriptionId, "lab id not captured");
        ManageLabs.LabState st = pollStoppedViaCloudLabs();
        if (st != ManageLabs.LabState.STOPPED) {
            ManageLabs ml = new ManageLabs(ctx.page);
            clickManageLabsSyncIfPresent(ml, sectionId());
            st = ml.waitForEmailState(user.email, STOP_OR_FAIL, 240, sectionId());
        }
        assertEquals(st, ManageLabs.LabState.STOPPED, "fresh lab did not reach Stopped (state=" + st + ")");
    }

    private ManageLabs.LabState pollStoppedViaCloudLabs() {
        if (!CloudLabsClient.isConfigured() || CloudLabsClient.isBlocked()) {
            return ManageLabs.LabState.NOTCREATED;
        }
        try {
            return CloudLabsClient.waitForState(subscriptionId, STOP_OR_FAIL, 240, 5);
        } catch (RuntimeException e) {
            System.out.println("[FreshLab] CloudLabs Stopped poll failed — Manage Labs UI fallback: "
                    + e.getMessage());
            return ManageLabs.LabState.NOTCREATED;
        }
    }

    @When("I delete that user's lab")
    public void iDeleteThatUsersLab() {
        new ManageLabs(ctx.page).performActionForEmail(user.email, ManageLabs.DELETE, sectionId());
    }

    @Then("that user's lab reaches Deleted")
    public void thatUsersLabReachesDeleted() {
        ManageLabs.LabState st = CloudLabsClient.waitForState(subscriptionId,
                EnumSet.of(ManageLabs.LabState.DELETED), 600, 15);   // delete ≈5-8 min (slow, variable)
        assertEquals(st, ManageLabs.LabState.DELETED, "fresh lab did not reach Deleted (state=" + st + ")");
    }

    /** Delete the seeded user after the scenario (core_user_delete_users is now enabled). */
    @After("@freshlab")
    public void cleanupSeededUser() {
        if (user != null) {
            ApiClient.deleteUser(user.id);
            System.out.println("[FreshLab] cleaned up user " + user.username + " (id=" + user.id + ")");
            user = null;
            ctx.seededUser = null;
            ctx.freshLabSubscriptionId = null;
        }
    }
}
