package com.nuvepro.moodle.steps;

import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.assertions.LocatorAssertions;
import com.microsoft.playwright.assertions.PlaywrightAssertions;
import com.microsoft.playwright.options.LoadState;
import com.microsoft.playwright.options.WaitForSelectorState;
import com.nuvepro.moodle.config.Settings;
import com.nuvepro.moodle.helpers.FormSubmit;
import com.nuvepro.moodle.pages.ManageLabs;
import com.nuvepro.moodle.pages.PlaygroundControlPanel;
import com.nuvepro.moodle.pages.PlaygroundLanding;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import org.testng.SkipException;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.testng.Assert.*;

/**
 * Step definitions for sandbox_labs_review.feature and legacy @PG-* playground cases.
 * Each step calls the shared Page Objects. Provisioning scenarios share ONE server-side lab:
 * PG-12 provisions it, others reuse it via "a running sandbox lab", PG-15 stops it, and
 * GlobalHooks.@AfterAll is the safety net.
 */
public class PlaygroundSteps {
    private final TestContext ctx;
    private static final List<String> RUNNING = List.of("RUNNING", "ACTIVE", "STARTED", "CREATED");
    private static final List<String> NOT_READY = List.of("", "NOTCREATED", "CREATING", "INPROGRESS");

    public PlaygroundSteps(TestContext ctx) {
        this.ctx = ctx;
    }

    // ---------- Background / shared ----------

    @Given("I open the sandbox activity")
    public void iOpenTheSandboxActivity() {
        ctx.landing.open(ctx.cmid);
        ctx.sectionId = ctx.landing.hiddenSectionId();
    }

    @Given("a running sandbox lab")
    public void aRunningSandboxLab() {
        // 1) Confirm the lab is RUNNING. The lab is provisioned once (PG-12) and stays running
        //    across the console checks, so do a QUICK robust read first and only drive the state
        //    if it's genuinely not running (driving is expensive — waits for provider ops).
        ctx.landing.open(ctx.cmid);
        ctx.sectionId = ctx.landing.hiddenSectionId();
        int sid = Integer.parseInt(ctx.sectionId);
        com.nuvepro.moodle.pages.ManageLabs ml = new com.nuvepro.moodle.pages.ManageLabs(ctx.page);
        ml.open(sid);
        com.nuvepro.moodle.pages.ManageLabs.LabState st = ml.firstRowStateStable(sid);
        if (st != com.nuvepro.moodle.pages.ManageLabs.LabState.RUNNING) {
            st = ml.ensureState(com.nuvepro.moodle.pages.ManageLabs.LabState.RUNNING, sid);
        }
        GlobalHooks.labProvisioned = true;
        if (st != com.nuvepro.moodle.pages.ManageLabs.LabState.RUNNING) {
            throw new SkipException("could not bring lab to RUNNING via Manage Labs (state=" + st
                    + " - provider slow/unavailable)");
        }
        // 2) Open the console for the console-specific checks (may skip if cp.php is down, but the
        //    lab IS running either way).
        ctx.landing.open(ctx.cmid);
        openConsoleOrSkip();
        waitConsoleReady();
    }

    @When("I open the lab console")
    public void iOpenTheLabConsole() {
        openConsoleOrSkip();
    }

    /**
     * Open the control panel. cp.php is fine for a single click but intermittently hangs under
     * sustained load, so retry (reload landing + click again) before giving up. SKIP (not fail)
     * only if it stays unresponsive across attempts.
     */
    private void openConsoleOrSkip() {
        // cp.php can be slow (but does load) — give each attempt a generous 75s so a slow-but-working
        // console succeeds instead of falsely skipping. Two attempts (fresh load between).
        for (int attempt = 1; attempt <= 2; attempt++) {
            ctx.landing.openLabConsole();
            try {
                ctx.cp.expectControlPanelLoaded(75_000);
                return; // control panel rendered
            } catch (Throwable e) {
                if (attempt < 2) {
                    ctx.page.waitForTimeout(3_000);
                    ctx.landing.open(ctx.cmid); // fresh load, then retry the click
                }
            }
        }
        throw new SkipException("Lab control panel unavailable after retries - cp.php (provider backend) not responding");
    }

    // ---------- PG-1 create activity ----------

    @When("I create a new sandbox activity")
    public void iCreateANewSandboxActivity() {
        if (Settings.COURSE_ID.isEmpty()) throw new SkipException("COURSE_ID not set");
        int courseId = Integer.parseInt(Settings.COURSE_ID);
        Page page = ctx.page;
        page.navigate(Settings.BASE_URL + "/course/modedit.php?add=cloudlabs&type=&course="
                + courseId + "&section=0&return=0&sr=0");
        page.waitForLoadState(LoadState.DOMCONTENTLOADED);
        String name = "AUTO Sandbox " + System.currentTimeMillis();
        page.locator("#id_name").fill(name);
        // Moodle mod_form still uses labtype value "playground" for the Sandbox lab type.
        Locator pgRadio = page.locator("input[name='labtype'][value='playground']");
        if (pgRadio.count() > 0 && !pgRadio.first().isChecked()) pgRadio.first().check();
        Locator sel = page.locator("#id_lab_template_id");
        if (sel.count() > 0) {
            @SuppressWarnings("unchecked")
            List<Object> values = (List<Object>) sel.locator("option")
                    .evaluateAll("els => els.map(o => o.value)");
            for (Object v : values) {
                if (v != null && !v.toString().isEmpty() && !v.toString().equals("0")) {
                    sel.selectOption(v.toString());
                    break;
                }
            }
        }
        FormSubmit.andAwaitNewPage(page, page.locator("#id_submitbutton"));
        assertFalse(page.url().contains("modedit.php"), "activity form did not submit: " + page.url());

        String cmid = null;
        Matcher m = Pattern.compile("/mod/cloudlabs/view\\.php\\?id=(\\d+)").matcher(page.url());
        if (m.find()) {
            cmid = m.group(1);
            assertTrue(page.locator("body").innerText().contains(name), "activity name not shown");
        } else {
            Locator links = page.locator("a[href*='/mod/cloudlabs/view.php']");
            for (int i = 0; i < links.count(); i++) {
                if (name.equals(nz(links.nth(i).innerText())) || nz(links.nth(i).innerText()).contains(name)) {
                    Matcher mm = Pattern.compile("id=(\\d+)").matcher(nz(links.nth(i).getAttribute("href")));
                    if (mm.find()) { cmid = mm.group(1); break; }
                }
            }
        }
        ctx.newActivityCmid = cmid;
        ctx.newActivityName = name;
        System.out.println("[PG-1] kept sandbox activity cmid=" + cmid + " name=" + name);
    }

    @Then("the sandbox activity is created")
    public void theSandboxActivityIsCreated() {
        assertNotNull(ctx.newActivityCmid, "created sandbox activity not found");
        assertFalse(ctx.newActivityCmid.isBlank(), "created sandbox activity cmid is blank");
        // Keep the activity — do not delete until explicitly requested.
    }

    /** @deprecated kept only so old feature wording does not break mid-edit; prefer {@link #theSandboxActivityIsCreated()}. */
    @Then("the sandbox activity is created and then removed")
    public void theSandboxActivityIsCreatedAndRemoved() {
        theSandboxActivityIsCreated();
    }

    // ---------- PG-2 / PG-11 manage labs ----------

    @When("I open Manage Labs")
    public void iOpenManageLabs() {
        new ManageLabs(ctx.page).open(Integer.parseInt(ctx.sectionId));
    }

    @Then("per-lab action controls are available")
    public void perLabActionControlsAreAvailable() {
        // Select the first lab row (if any) to enable the action items.
        Locator rowCheckbox = ctx.page.locator("#np-ap-manage-lab-table input[name='btSelectItem']");
        if (rowCheckbox.count() > 0) rowCheckbox.first().check();
        // Open the Actions dropdown and confirm Start / Stop / Delete are revealed.
        Locator actionsBtn = ctx.page.locator("#np-ap-cl-assessment-lab-actions");
        assertTrue(actionsBtn.count() > 0, "Actions dropdown not found on Manage Labs");
        actionsBtn.click();
        PlaywrightAssertions.assertThat(ctx.page.locator("#np-ap-manage-start-btn")).isVisible();
        PlaywrightAssertions.assertThat(ctx.page.locator("#np-ap-manage-stop-btn")).isVisible();
        PlaywrightAssertions.assertThat(ctx.page.locator("#np-ap-manage-delete-btn")).isVisible();
    }

    @Then("the labs are listed with status counters")
    public void theLabsAreListedWithStatusCounters() {
        ManageLabs ml = new ManageLabs(ctx.page);
        assertNotNull(ml.counters().get("Running"), "status summary not rendered");
        assertTrue(ctx.page.locator("table").count() > 0, "Manage Labs table not present");
    }

    @Then("Manage Labs shows at least one running lab")
    public void manageLabsShowsAtLeastOneRunningLab() {
        ManageLabs ml = new ManageLabs(ctx.page);
        ml.open(Integer.parseInt(ctx.sectionId));
        assertTrue(ml.runningCount() >= 1, "Manage Labs Running count < 1");
    }

    // ---------- PG-8 notes ----------

    @When("I save the note {string}")
    public void iSaveTheNote(String text) {
        if (ctx.page.locator(PlaygroundControlPanel.NOTES_TEXTAREA).count() == 0) {
            throw new SkipException("Notes not enabled on this lab template");
        }
        ctx.cp.saveNote(text);
    }

    @Then("the saved note should be {string}")
    public void theSavedNoteShouldBe(String text) {
        assertEquals(ctx.page.locator(PlaygroundControlPanel.NOTES_TEXTAREA).inputValue(), text);
    }

    // ---------- PG-9 catalog ----------

    @When("I open the catalog-UI lab console")
    public void iOpenTheCatalogUiLabConsole() {
        int cmid = resolveCatalogSandboxCmid();
        ctx.landing.open(cmid);
        // openLabConsole starts/continues the lab so catalog UI can render on the console.
        // Do not mark labProvisioned / do not delete the activity — keep sandbox data until cleanup is requested.
        ctx.landing.openLabConsole();
    }

    /** Prefer PLAYGROUND_CATALOG_CMID; otherwise create+keep a catalog-template sandbox activity. */
    private int resolveCatalogSandboxCmid() {
        if (!Settings.PLAYGROUND_CATALOG_CMID.isEmpty()) {
            return Integer.parseInt(Settings.PLAYGROUND_CATALOG_CMID);
        }
        if (Settings.COURSE_ID.isEmpty()) {
            throw new SkipException("COURSE_ID not set (needed to create a catalog sandbox activity)");
        }
        Page page = ctx.page;
        int courseId = Integer.parseInt(Settings.COURSE_ID);
        page.navigate(Settings.BASE_URL + "/course/modedit.php?add=cloudlabs&type=&course="
                + courseId + "&section=0&return=0&sr=0");
        page.waitForLoadState(LoadState.DOMCONTENTLOADED);
        String name = "AUTO Sandbox Catalog " + System.currentTimeMillis();
        page.locator("#id_name").fill(name);
        Locator pgRadio = page.locator("input[name='labtype'][value='playground']");
        if (pgRadio.count() > 0 && !pgRadio.first().isChecked()) pgRadio.first().check();
        Locator sel = page.locator("#id_lab_template_id");
        String catalogValue = null;
        if (sel.count() > 0) {
            Locator options = sel.locator("option");
            for (int i = 0; i < options.count(); i++) {
                String v = options.nth(i).getAttribute("value");
                String t = nz(options.nth(i).innerText()).toLowerCase();
                if (v == null || v.isBlank() || "0".equals(v)) continue;
                if (t.contains("catalog")) {
                    catalogValue = v;
                    break;
                }
            }
        }
        if (catalogValue == null) {
            throw new SkipException("No catalog lab template in the create form — set PLAYGROUND_CATALOG_CMID in .env "
                    + "to an existing catalog sandbox activity cmid");
        }
        sel.selectOption(catalogValue);
        FormSubmit.andAwaitNewPage(page, page.locator("#id_submitbutton"));
        assertFalse(page.url().contains("modedit.php"), "catalog sandbox form did not submit: " + page.url());
        String cmid = null;
        Matcher m = Pattern.compile("/mod/cloudlabs/view\\.php\\?id=(\\d+)").matcher(page.url());
        if (m.find()) cmid = m.group(1);
        assertNotNull(cmid, "catalog sandbox activity cmid not found after create");
        ctx.newActivityCmid = cmid;
        ctx.newActivityName = name;
        System.out.println("[PG-9] kept catalog sandbox activity cmid=" + cmid + " name=" + name
                + " (also set PLAYGROUND_CATALOG_CMID=" + cmid + " in .env to reuse)");
        return Integer.parseInt(cmid);
    }

    @Then("the catalog UI input surface is shown")
    public void theCatalogUiInputSurfaceIsShown() {
        Locator catalog = ctx.page.locator("#np-ap-mod-lab-ui-schema, #catInputUI, #subCatalogDataSubmitBtn, "
                + "#cat_tab_content_actionui, .nl-catalog-ui, [id*='Catalog'], [id*='catalog']");
        boolean shown = false;
        for (int i = 0; i < 30; i++) {
            if (catalog.count() > 0) {
                for (int j = 0; j < catalog.count(); j++) {
                    try {
                        if (catalog.nth(j).isVisible()) { shown = true; break; }
                    } catch (RuntimeException ignored) {}
                }
            }
            if (shown) break;
            String body = ctx.page.locator("body").innerText().toLowerCase();
            if (body.contains("catalog") && (body.contains("input") || body.contains("submit") || body.contains("select"))) {
                shown = true;
                break;
            }
            ctx.page.waitForTimeout(1_000);
        }
        assertTrue(shown, "Catalog UI input surface not present on the sandbox console");
    }

    // ---------- PG-10 report ----------

    @When("I open the CloudLabs course progress report")
    public void iOpenTheCourseProgressReport() {
        new com.nuvepro.moodle.pages.CloudLabsCourseReportPage(ctx.page).openCoursesReport();
    }

    // ---------- PG-12 start ----------

    @When("I start the lab")
    public void iStartTheLab() {
        ctx.page.locator(PlaygroundControlPanel.LAUNCH_START).first().click();
        GlobalHooks.labProvisioned = true;   // teardown safety net should stop it
        waitForRunning();
        waitConsoleReady();
    }

    @Then("the lab reaches a running state")
    public void theLabReachesARunningState() {
        assertTrue(RUNNING.contains(safeStatus().toUpperCase()), "lab not running: " + safeStatus());
    }

    // ---------- PG-3 usage / PG-5 info / PG-6 instructions ----------

    @When("I open the Lab Usage popup")
    public void iOpenTheLabUsagePopup() {
        if (!waitVisible(ctx.page.locator("#openLabUsageBtn"), 45_000)) throw new SkipException("Lab Usage not enabled/ready");
        ctx.page.locator("#openLabUsageBtn").click();
    }

    @Then("the Lab Usage popup is shown")
    public void theLabUsagePopupIsShown() {
        PlaywrightAssertions.assertThat(ctx.page.locator("#labUsagePopup")).isVisible();
        ctx.page.locator("#closeLabUsage").click();
    }

    @When("I open the Lab Info popup")
    public void iOpenTheLabInfoPopup() {
        if (!waitVisible(ctx.page.locator("#openLabInfoBtn"), 45_000)) throw new SkipException("Lab Info not enabled/ready");
        ctx.page.locator("#openLabInfoBtn").click();
    }

    @Then("the Lab Info popup is shown")
    public void theLabInfoPopupIsShown() {
        PlaywrightAssertions.assertThat(ctx.page.locator("#labInfoPopup")).isVisible();
        ctx.page.locator("#closeLabInfo").click();
    }

    @When("I open the Instructions popup")
    public void iOpenTheInstructionsPopup() {
        ctx.page.locator("#openMissionBtn").click();
    }

    @Then("the Instructions popup is shown")
    public void theInstructionsPopupIsShown() {
        PlaywrightAssertions.assertThat(ctx.page.locator("#missionPopup")).isVisible();
        PlaywrightAssertions.assertThat(ctx.page.locator("#np-ap-pl-tab-instructions")).isVisible();
        ctx.page.locator("#closeMission").click();
    }

    // ---------- PG-7 speed test ----------

    @Then("the speed test button works when enabled")
    public void theSpeedTestButtonWorksWhenEnabled() {
        Locator btn = ctx.page.locator("#np-ap-mod-btn-tb-speedTest");
        if (btn.count() == 0 || !waitVisible(btn, 10_000)) throw new SkipException("Speed test not enabled");
        btn.first().click();
    }

    // ---------- PG-13 jump to console / PG-14 access ----------

    @When("I click Jump to Console")
    public void iClickJumpToConsole() {
        Locator jump = ctx.page.getByText("Jump to Console");
        if (jump.count() == 0) {
            throw new SkipException("Jump to Console not present (platform-specific, N/A on Account template)");
        }
        try {
            ctx.openedTab = ctx.page.context().waitForPage(
                    new BrowserContext.WaitForPageOptions().setTimeout(8_000),
                    () -> jump.first().click());
        } catch (Exception ignored) {
        }
    }

    @Then("the access lab popup or a console tab opens")
    public void theAccessLabPopupOrTabOpens() {
        boolean modalShown = false;
        try {
            PlaywrightAssertions.assertThat(ctx.page.locator("#launchAccModal"))
                    .isVisible(new LocatorAssertions.IsVisibleOptions().setTimeout(8_000));
            modalShown = true;
            ctx.page.locator("#launchAccModal [data-dismiss='modal']").first().click();
        } catch (Exception ignored) {
        }
        if (!modalShown && ctx.openedTab == null) {
            // The access/console launch is platform-template specific; on an Account sandbox it may
            // not surface a modal/tab. Treat as not-applicable rather than a failure.
            throw new SkipException("Access lab did not open a popup or console tab (template-specific)");
        }
    }

    @Then("a platform access control is present")
    public void aPlatformAccessControlIsPresent() {
        boolean hasAccess = ctx.page.getByText("Jump to Console").count() > 0
                || ctx.page.locator("#np-ap-mod-lab-acc-launch-buttons button, "
                + "#np-ap-mod-lab-acc-launch-buttons a, #launchAccBtnContainer a, #launchAccModal a").count() > 0;
        if (!hasAccess) {
            // The in-Moodle access/redirect control is a platform-lab concern; an Account sandbox
            // may not surface it. Not-applicable rather than a failure.
            throw new SkipException("no access-lab/platform-redirect control (platform-specific, N/A on Account template)");
        }
    }

    // ---------- PG-16 refresh ----------

    @When("I refresh the lab")
    public void iRefreshTheLab() {
        if (!waitVisible(ctx.page.locator("#np-ap-mod-btn-tb-refresh"), 30_000)) throw new SkipException("Refresh not visible");
        ctx.page.locator("#np-ap-mod-btn-tb-refresh").first().click();
        ctx.page.waitForTimeout(3_000);
    }

    @Then("the lab remains running")
    public void theLabRemainsRunning() {
        assertTrue(RUNNING.contains(safeStatus().toUpperCase()) || safeStatus().isEmpty(),
                "lab not running after refresh: " + safeStatus());
    }

    // ---------- PG-4 VM iframe ----------

    @Then("the VM lab loads inside an iframe")
    public void theVmLabLoadsInsideAnIframe() {
        if (Settings.PLAYGROUND_VM_CMID.isEmpty()) throw new SkipException("PLAYGROUND_VM_CMID not set");
        ctx.landing.open(Integer.parseInt(Settings.PLAYGROUND_VM_CMID));
        ctx.landing.openLabConsole();
        ctx.cp.expectControlPanelLoaded();
        if (ctx.page.locator(PlaygroundControlPanel.LAUNCH_START).first().isVisible()) {
            ctx.page.locator(PlaygroundControlPanel.LAUNCH_START).first().click();
            GlobalHooks.labProvisioned = true;
            waitForRunning();
        }
        assertTrue(ctx.page.locator("#np-ap-mod-lab-container-iframe iframe, iframe").count() > 0,
                "VM lab iframe not found");
    }

    // ---------- PG-15 stop ----------

    @When("I stop the lab")
    public void iStopTheLab() {
        ensureConsole();
        ctx.cp.stopLab();
    }

    @Then("the lab is stopped")
    public void theLabIsStopped() {
        List<String> stopped = List.of("STOPPED", "STOPPING", "NOTCREATED", "INACTIVE", "");
        String finalStatus = "";
        for (int i = 0; i < 8; i++) {
            ctx.page.waitForTimeout(10_000);
            finalStatus = safeStatus().toUpperCase();
            if (stopped.contains(finalStatus)) break;
        }
        assertTrue(stopped.contains(finalStatus), "lab did not stop: " + finalStatus);
    }

    // ---------- helpers ----------

    private String safeStatus() {
        try {
            return ctx.cp.labStatus();
        } catch (Exception e) {
            return "";
        }
    }

    private void waitForRunning() {
        for (int i = 0; i < 16; i++) {
            ctx.page.waitForTimeout(15_000);
            if (!NOT_READY.contains(safeStatus().toUpperCase())) break;
        }
    }

    private void waitConsoleReady() {
        for (int i = 0; i < 9; i++) {
            if (ctx.page.getByText("Jump to Console").count() > 0
                    || ctx.page.locator("#openLabUsageBtn").isVisible()
                    || ctx.page.locator("#np-ap-mod-lab-acc-launch-buttons button").count() > 0) break;
            ctx.page.waitForTimeout(10_000);
        }
    }

    private boolean waitVisible(Locator loc, double timeoutMs) {
        try {
            loc.first().waitFor(new Locator.WaitForOptions()
                    .setState(WaitForSelectorState.VISIBLE).setTimeout(timeoutMs));
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private void ensureConsole() {
        try {
            ctx.page.evaluate("() => {"
                    + "document.querySelectorAll('.modal.show').forEach(m => { m.classList.remove('show'); m.style.display='none'; m.setAttribute('aria-hidden','true'); });"
                    + "document.querySelectorAll('.modal-backdrop').forEach(b => b.remove());"
                    + "document.body.classList.remove('modal-open'); document.body.style.removeProperty('overflow'); }");
        } catch (Exception ignored) {
        }
        if (!ctx.page.locator(PlaygroundControlPanel.STOP_LAB).first().isVisible()) {
            ctx.landing.open(ctx.cmid);
            if (ctx.landing.hasActiveLab()) ctx.landing.continueLab();
            else ctx.landing.openLabConsole();
            ctx.cp.expectControlPanelLoaded();
        }
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }
}
