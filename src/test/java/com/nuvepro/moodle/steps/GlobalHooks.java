package com.nuvepro.moodle.steps;

import com.microsoft.playwright.*;
import com.nuvepro.moodle.config.Settings;
import com.nuvepro.moodle.helpers.Auth;
import com.nuvepro.moodle.pages.PlaygroundControlPanel;
import com.nuvepro.moodle.pages.PlaygroundLanding;
import io.cucumber.java.AfterAll;
import io.cucumber.java.BeforeAll;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Run-wide setup/teardown for the BDD suite:
 *  - @BeforeAll: launch ONE browser (no login here — avoids a throwaway tab in headed runs).
 *  - Per-scenario {@link TestContext#start()}: load storageState and verify admin session on the
 *    scenario page (login once on that tab if the saved session expired).
 *  - @AfterAll: best-effort stop any running lab (safety net for @provisioning scenarios), then
 *    close the browser. When {@link #teardownSectionId} is set (assessment user-flow), stop THAT
 *    activity's lab — not the shared sandbox.
 */
public final class GlobalHooks {
    static Playwright playwright;
    static Browser browser;
    static final Path STORAGE_STATE = Paths.get("target", "state.json");
    /** Set true by a step that provisions/starts a lab, so the @AfterAll safety net only
     *  runs when there could actually be a running lab (keeps non-provisioning runs clean). */
    public static volatile boolean labProvisioned = false;
    /** Scenario-scoped opt-out for flows that should not return to admin during teardown. */
    public static volatile boolean skipLabTeardown = false;
    /** When set, globalTeardown stops the lab for this assessment section (not PLAYGROUND_CMID). */
    public static volatile String teardownSectionId = null;
    public static volatile String teardownLabId = null;
    public static volatile String teardownStudentEmail = null;

    private GlobalHooks() {}

    @BeforeAll
    public static void globalSetup() {
        // The five-worker Sandbox runner gives each scenario thread its own Playwright process.
        // Do not launch an unused sixth global browser.
        if (Boolean.getBoolean("sandbox.parallel")) return;
        playwright = Playwright.create();
        browser = playwright.chromium().launch(new BrowserType.LaunchOptions()
                .setHeadless(Settings.HEADLESS).setSlowMo(Settings.SLOWMO_MS));
    }

    /** Ensure the scenario's admin context is logged in; persist session when a fresh login was needed. */
    public static void ensureAdminSession(BrowserContext context, Page page) {
        page.navigate(Settings.BASE_URL + "/my/",
                new Page.NavigateOptions()
                        .setWaitUntil(com.microsoft.playwright.options.WaitUntilState.DOMCONTENTLOADED)
                        .setTimeout(45_000));
        if (Auth.isLoggedIn(page)) return;
        Auth.loginAsAdmin(page);
        context.storageState(new BrowserContext.StorageStateOptions().setPath(STORAGE_STATE));
    }

    @AfterAll
    public static void globalTeardown() {
        if (browser != null && labProvisioned && !skipLabTeardown) {
            try (BrowserContext c = browser.newContext(new Browser.NewContextOptions()
                    .setBaseURL(Settings.BASE_URL).setIgnoreHTTPSErrors(true)
                    .setStorageStatePath(STORAGE_STATE))) {
                Page p = c.newPage();
                p.setDefaultTimeout(Settings.DEFAULT_TIMEOUT_MS);
                com.nuvepro.moodle.pages.ManageLabs ml = new com.nuvepro.moodle.pages.ManageLabs(p);
                if (teardownSectionId != null && !teardownSectionId.isBlank()) {
                    stopAssessmentLab(ml, p);
                } else {
                    stopPlaygroundLab(ml, p);
                }
            } catch (Throwable e) {
                System.out.println("[globalTeardown] best-effort stop failed: " + e.getMessage());
            }
        }
        teardownSectionId = null;
        teardownLabId = null;
        teardownStudentEmail = null;
        labProvisioned = false;
        skipLabTeardown = false;
        if (browser != null) browser.close();
        if (playwright != null) playwright.close();
    }

    private static void stopAssessmentLab(com.nuvepro.moodle.pages.ManageLabs ml, Page p) {
        int sid = Integer.parseInt(teardownSectionId);
        ml.open(sid);
        try {
            if (teardownLabId != null && !teardownLabId.isBlank()) {
                ml.performActionForLabId(teardownLabId, com.nuvepro.moodle.pages.ManageLabs.STOP, sid);
                p.waitForTimeout(10_000);
                System.out.println("[globalTeardown] assessment lab " + teardownLabId + " stopped (section=" + sid + ")");
                return;
            }
            if (teardownStudentEmail != null && !teardownStudentEmail.isBlank()) {
                if (ml.stateForEmail(teardownStudentEmail) == com.nuvepro.moodle.pages.ManageLabs.LabState.RUNNING) {
                    ml.performActionForEmail(teardownStudentEmail, com.nuvepro.moodle.pages.ManageLabs.STOP, sid);
                    p.waitForTimeout(10_000);
                    System.out.println("[globalTeardown] student lab stopped for " + teardownStudentEmail
                            + " (section=" + sid + ")");
                } else {
                    System.out.println("[globalTeardown] no running lab for " + teardownStudentEmail);
                }
                return;
            }
        } catch (Throwable e) {
            System.out.println("[globalTeardown] assessment lab stop failed: " + e.getMessage());
        }
    }

    private static void stopPlaygroundLab(com.nuvepro.moodle.pages.ManageLabs ml, Page p) {
        PlaygroundLanding landing = new PlaygroundLanding(p);
        int cmid = Integer.parseInt(Settings.PLAYGROUND_CMID);
        landing.open(cmid);
        String sectionId = landing.hiddenSectionId();
        ml.open(Integer.parseInt(sectionId));
        Integer running = ml.counters().get("Running");
        if (running != null && running > 0) {
            try {
                ml.performActionOnFirstRow(com.nuvepro.moodle.pages.ManageLabs.STOP);
                p.waitForTimeout(10_000);
                System.out.println("[globalTeardown] sandbox lab stopped via Manage Labs");
            } catch (Throwable e) {
                System.out.println("[globalTeardown] ML stop failed: " + e.getMessage());
            }
        } else {
            System.out.println("[globalTeardown] no running sandbox lab to stop");
        }
    }
}
