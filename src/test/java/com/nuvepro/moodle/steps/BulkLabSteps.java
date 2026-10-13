package com.nuvepro.moodle.steps;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Page;
import com.nuvepro.moodle.config.Settings;
import com.nuvepro.moodle.helpers.ApiClient;
import com.nuvepro.moodle.helpers.CloudLabsClient;
import com.nuvepro.moodle.helpers.BrowserUi;
import com.nuvepro.moodle.pages.ManageLabs;
import io.cucumber.java.After;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import org.testng.SkipException;

import java.util.EnumSet;
import java.util.List;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;

/**
 * PG-21: a bulk lab action applies to MULTIPLE selected labs. Seeds TWO fresh users + labs,
 * selects both rows in Manage Labs, performs one Stop on the selection, and verifies BOTH labs
 * reach Stopped via the CloudLabs API. Both labs + users are deleted at teardown.
 */
public class BulkLabSteps {
    private final TestContext ctx;

    static ApiClient.SeededUser userA;
    static ApiClient.SeededUser userB;
    static String labIdA;
    static String labIdB;
    static int bulkSectionId;

    public BulkLabSteps(TestContext ctx) {
        this.ctx = ctx;
    }

    private int sectionId() {
        if (bulkSectionId == 0) {
            ctx.landing.open(ctx.cmid);
            bulkSectionId = Integer.parseInt(ctx.landing.hiddenSectionId());
        }
        return bulkSectionId;
    }

    @Given("two fresh enrolled users with running labs")
    public void twoFreshEnrolledUsersWithRunningLabs() {
        if (Settings.WS_TOKEN.isEmpty() || Settings.COURSE_ID.isEmpty() || !CloudLabsClient.isConfigured()) {
            throw new SkipException("WS token / COURSE_ID / CloudLabs not configured");
        }
        long course = Long.parseLong(Settings.COURSE_ID);
        ManageLabs ml = new ManageLabs(ctx.page);
        int sid = sectionId();
        userA = provisionLab(ml, sid, course);
        labIdA = captureAndAwaitRunning(ml, sid, userA);
        userB = provisionLab(ml, sid, course);
        labIdB = captureAndAwaitRunning(ml, sid, userB);
        GlobalHooks.labProvisioned = true;
        System.out.println("[Bulk] labs A=" + labIdA + " B=" + labIdB);
    }

    private ApiClient.SeededUser provisionLab(ManageLabs ml, int sid, long course) {
        ApiClient.SeededUser u = ApiClient.createUser(System.currentTimeMillis());
        ApiClient.enrolUser(u.id, course, 5);
        ml.createLabForUserEmail(u.email, sid);
        return u;
    }

    private String captureAndAwaitRunning(ManageLabs ml, int sid, ApiClient.SeededUser u) {
        String id = null;
        for (int i = 0; i < 12 && id == null; i++) {
            ml.open(sid);
            id = ml.labIdForEmail(u.email);
            if (id == null) ctx.page.waitForTimeout(3_000);
        }
        assertNotNull(id, "could not capture lab id for " + u.email);
        ManageLabs.LabState st = CloudLabsClient.waitForState(id,
                EnumSet.of(ManageLabs.LabState.RUNNING, ManageLabs.LabState.FAILED), 180, 5);
        assertEquals(st, ManageLabs.LabState.RUNNING, "lab " + id + " did not reach Running (state=" + st + ")");
        return id;
    }

    @When("I stop all selected labs from Manage Labs")
    public void iStopAllSelectedLabs() {
        ManageLabs ml = new ManageLabs(ctx.page);
        ml.open(sectionId());
        ml.selectRowsForEmails(List.of(userA.email, userB.email));
        ml.performActionOnSelection(ManageLabs.STOP);
    }

    @Then("both labs are Stopped")
    public void bothLabsAreStopped() {
        ManageLabs.LabState a = CloudLabsClient.waitForState(labIdA,
                EnumSet.of(ManageLabs.LabState.STOPPED, ManageLabs.LabState.FAILED), 240, 5);
        ManageLabs.LabState b = CloudLabsClient.waitForState(labIdB,
                EnumSet.of(ManageLabs.LabState.STOPPED, ManageLabs.LabState.FAILED), 240, 5);
        assertEquals(a, ManageLabs.LabState.STOPPED, "lab A (" + labIdA + ") not stopped: " + a);
        assertEquals(b, ManageLabs.LabState.STOPPED, "lab B (" + labIdB + ") not stopped: " + b);
    }

    @After("@bulk")
    public void cleanupBulk() {
        // Use a FRESH context (not ctx.page — the @After ordering may have already closed it) to
        // trigger both deletes; then delete the users (which also cascade-deletes their labs).
        try (BrowserContext c = BrowserUi.newContext(GlobalHooks.browser, new Browser.NewContextOptions()
                .setBaseURL(Settings.BASE_URL).setIgnoreHTTPSErrors(true)
                .setStorageStatePath(GlobalHooks.STORAGE_STATE))) {
            Page p = c.newPage();
            p.setDefaultTimeout(Settings.DEFAULT_TIMEOUT_MS);
            ManageLabs ml = new ManageLabs(p);
            if (userA != null) { ml.open(bulkSectionId); ml.performActionForEmail(userA.email, ManageLabs.DELETE, bulkSectionId); }
            if (userB != null) { ml.open(bulkSectionId); ml.performActionForEmail(userB.email, ManageLabs.DELETE, bulkSectionId); }
            if (labIdA != null) CloudLabsClient.waitForState(labIdA, EnumSet.of(ManageLabs.LabState.DELETED), 600, 15);
            if (labIdB != null) CloudLabsClient.waitForState(labIdB, EnumSet.of(ManageLabs.LabState.DELETED), 600, 15);
        } catch (Throwable e) {
            System.out.println("[Bulk] lab cleanup via UI failed (non-fatal): " + e.getMessage());
        }
        if (userA != null) ApiClient.deleteUser(userA.id);   // also cascade-deletes the lab
        if (userB != null) ApiClient.deleteUser(userB.id);
        System.out.println("[Bulk] cleaned up users + labs");
        userA = userB = null; labIdA = labIdB = null; bulkSectionId = 0;
    }
}
