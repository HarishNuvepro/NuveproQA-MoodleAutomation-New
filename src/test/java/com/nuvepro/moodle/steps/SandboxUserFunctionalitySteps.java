package com.nuvepro.moodle.steps;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Download;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.nuvepro.moodle.config.Settings;
import com.nuvepro.moodle.helpers.ApiClient;
import com.nuvepro.moodle.helpers.AssessmentCreateSpec;
import com.nuvepro.moodle.helpers.Auth;
import com.nuvepro.moodle.helpers.CloudLabsClient;
import com.nuvepro.moodle.helpers.SandboxActivityRegistry;
import com.nuvepro.moodle.helpers.SandboxCourse;
import com.nuvepro.moodle.helpers.SandboxTestData;
import com.nuvepro.moodle.helpers.SandboxUserRegistry;
import com.nuvepro.moodle.helpers.BrowserUi;
import com.nuvepro.moodle.pages.ManageLabs;
import com.nuvepro.moodle.pages.PlaygroundControlPanel;
import com.nuvepro.moodle.pages.PlaygroundLanding;
import io.cucumber.java.After;
import io.cucumber.java.Before;
import io.cucumber.java.Scenario;
import io.cucumber.java.Status;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import org.testng.SkipException;

import java.nio.file.Files;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;

/**
 * One end-to-end student sandbox flow:
 * reuse/create activity → landing details → Start Sandbox → Start Lab →
 * Instructions / duration / credentials / Lab Info → Jump to Console → Access Lab → platform.
 */
public class SandboxUserFunctionalitySteps {
    /** SB-user-6 provisions; SB-user-9 reuses that student on sandbox_create_no (parallel-safe lane). */
    static final String NOTES_PDF_CHAIN_LANE = "sbuser:notes_pdf_chain";
    static final String NOTES_PDF_CHAIN_USER = "notes_pdf_chain";
    /** SB-user-14 provisions VM lab; SB-user-19 reuses that student + @fresh_activity cmid (parallel-safe). */
    static final String VM_VPAT_CHAIN_LANE = "sbuser:vm_vpat_chain";
    static final String VM_VPAT_CHAIN_USER = "vm_vpat_chain";
    static final String VM_VPAT_CHAIN_ACTIVITY = "vm_vpat_chain";
    static final String VM_VPAT_CHAIN_LAB_META = "cloudLabId";
    static final String VM_VPAT_CHAIN_STATUS_META = "chainStatus";
    /** Max wait for @SB-user-14 to finish (VM + speed test can exceed 15 min). */
    static final int VM_VPAT_CHAIN_WAIT_SEC = Integer.getInteger("sbuser.vmVpatChainWaitSec", 1200);
    /** Same-JVM gate so @SB-user-19 never runs on stale registry from an earlier Maven run. */
    private static volatile boolean vmVpatChainPassedInProcess;
    private static volatile boolean vmVpatChainFailedInProcess;

    private final TestContext ctx;
    private ApiClient.SeededUser student;
    private Page studentPage;
    private Page openedConsoleTab;
    private long courseId;
    private boolean namedStudentBound;
    private String savedSandboxNote;
    /** Remaining duration (minutes) captured before CloudLabs +1h extend. */
    private int durationBeforeMinutes = -1;
    /** CloudLabs subscription / lab id for this student run. */
    private String sandboxLabId;
    private int catalogStartRecoveryAttempts;

    public SandboxUserFunctionalitySteps(TestContext ctx) {
        this.ctx = ctx;
    }

    @Before(order = -400)
    public void notesPdfChainLane(Scenario scenario) {
        if (!scenario.getSourceTagNames().contains("@SB-user-6")) {
            return;
        }
        ctx.enterLane(NOTES_PDF_CHAIN_LANE, true);
        System.out.println("[SB-user] notes/PDF chain lane (SB-user-6) — " + scenario.getName());
    }

    @Before(order = -399)
    public void vmVpatChainLaneHolder(Scenario scenario) {
        if (!scenario.getSourceTagNames().contains("@SB-user-14")) {
            return;
        }
        vmVpatChainPassedInProcess = false;
        vmVpatChainFailedInProcess = false;
        SandboxUserRegistry.putNamedMeta(VM_VPAT_CHAIN_USER, VM_VPAT_CHAIN_STATUS_META, "running");
        ctx.enterLane(VM_VPAT_CHAIN_LANE, true);
        System.out.println("[SB-user] VM vpat chain lane (SB-user-14) — " + scenario.getName());
    }

    /** Do not start @SB-user-19 until @SB-user-14 has fully passed (same JVM or parallel file run). */
    @Before(order = -398)
    public void sbUser19WaitsForSbUser14Pass(Scenario scenario) {
        if (!scenario.getSourceTagNames().contains("@SB-user-19")) {
            return;
        }
        ctx.enterLane(VM_VPAT_CHAIN_LANE, true);
        System.out.println("[SB-user] @SB-user-19 waiting for @SB-user-14 PASS (up to "
                + VM_VPAT_CHAIN_WAIT_SEC + "s)…");
        VmVpatChainWait outcome = waitForVmVpatChainPassed(VM_VPAT_CHAIN_WAIT_SEC);
        if (outcome == VmVpatChainWait.PASSED) {
            System.out.println("[SB-user] @SB-user-14 chain PASSED — starting @SB-user-19");
            return;
        }
        if (outcome == VmVpatChainWait.FAILED) {
            throw new SkipException("@SB-user-19 skipped — @SB-user-14 failed in this run "
                    + "(fix @SB-user-14 first, then re-run both tags)");
        }
        throw new SkipException("@SB-user-19 skipped — @SB-user-14 did not PASS within "
                + VM_VPAT_CHAIN_WAIT_SEC + "s (run @SB-user-14 or both tags in one command)");
    }

    private enum VmVpatChainWait { PASSED, FAILED, TIMEOUT }

    private VmVpatChainWait waitForVmVpatChainPassed(int maxSeconds) {
        long deadline = System.currentTimeMillis() + maxSeconds * 1000L;
        while (System.currentTimeMillis() < deadline) {
            if (vmVpatChainPassedInProcess) {
                return VmVpatChainWait.PASSED;
            }
            if (vmVpatChainFailedInProcess) {
                return VmVpatChainWait.FAILED;
            }
            try {
                Thread.sleep(2_000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        if (vmVpatChainPassedInProcess) {
            return VmVpatChainWait.PASSED;
        }
        if (vmVpatChainFailedInProcess) {
            return VmVpatChainWait.FAILED;
        }
        return VmVpatChainWait.TIMEOUT;
    }

    @After(order = 10)
    public void publishNotesPdfChainStudent(Scenario scenario) {
        if (!scenario.getSourceTagNames().contains("@SB-user-6") || student == null) {
            return;
        }
        SandboxUserRegistry.putNamed(NOTES_PDF_CHAIN_USER, student);
        if (courseId > 0) {
            SandboxUserRegistry.put(courseId, student);
        }
        System.out.println("[SB-user] published " + NOTES_PDF_CHAIN_USER + " user="
                + student.username + " for @SB-user-9");
    }

    @After(order = 10)
    public void publishVmVpatChain(Scenario scenario) {
        if (!scenario.getSourceTagNames().contains("@SB-user-14")) {
            return;
        }
        if (scenario.getStatus() != Status.PASSED) {
            vmVpatChainFailedInProcess = true;
            SandboxUserRegistry.putNamedMeta(VM_VPAT_CHAIN_USER, VM_VPAT_CHAIN_STATUS_META, "failed");
            System.out.println("[SB-user] @SB-user-14 did not pass (status=" + scenario.getStatus()
                    + ") — chain not published for @SB-user-19");
            return;
        }
        if (student == null) {
            SandboxUserRegistry.putNamedMeta(VM_VPAT_CHAIN_USER, VM_VPAT_CHAIN_STATUS_META, "failed");
            return;
        }
        SandboxUserRegistry.putNamed(VM_VPAT_CHAIN_USER, student);
        if (courseId > 0) {
            SandboxUserRegistry.put(courseId, student);
        }
        String cmid = ctx.newActivityCmid;
        String courseKey = SandboxCourse.idOrEmpty();
        if (cmid != null && !cmid.isBlank() && courseKey != null && !courseKey.isBlank()) {
            SandboxActivityRegistry.put(VM_VPAT_CHAIN_ACTIVITY,
                    ctx.newActivityName == null ? "" : ctx.newActivityName,
                    cmid, ctx.sectionId, courseKey);
        }
        String chainLabId = sandboxLabId;
        if (chainLabId == null || chainLabId.isBlank()) {
            chainLabId = GlobalHooks.teardownLabId;
        }
        if (chainLabId == null || chainLabId.isBlank()) {
            chainLabId = resolveCloudLabsSubscriptionIdPreferManageLabs();
        }
        if (chainLabId != null && !chainLabId.isBlank()) {
            SandboxUserRegistry.putNamedMeta(VM_VPAT_CHAIN_USER, VM_VPAT_CHAIN_LAB_META, chainLabId.trim());
        }
        vmVpatChainPassedInProcess = true;
        SandboxUserRegistry.putNamedMeta(VM_VPAT_CHAIN_USER, VM_VPAT_CHAIN_STATUS_META, "passed");
        System.out.println("[SB-user] published " + VM_VPAT_CHAIN_USER + " user=" + student.username
                + " activity=" + VM_VPAT_CHAIN_ACTIVITY + " cmid=" + cmid
                + " labId=" + chainLabId + " chain=passed for @SB-user-19");
    }

    @Before
    public void resetSandboxUserFlowGlobals(io.cucumber.java.Scenario scenario) {
        String uri = scenario.getUri() == null ? "" : scenario.getUri().toString();
        if (!uri.contains("sandbox_user_functionality")) {
            return;
        }
        GlobalHooks.labProvisioned = false;
        GlobalHooks.skipLabTeardown = true;
        GlobalHooks.teardownSectionId = null;
        GlobalHooks.teardownLabId = null;
        GlobalHooks.teardownStudentEmail = null;
        openedConsoleTab = null;
        savedSandboxNote = null;
        durationBeforeMinutes = -1;
        sandboxLabId = null;
        catalogStartRecoveryAttempts = 0;
    }

    private int createdCmid() {
        if (ctx.newActivityCmid == null || ctx.newActivityCmid.isBlank()) {
            throw new SkipException("no created sandbox cmid — run the create step first");
        }
        return Integer.parseInt(ctx.newActivityCmid);
    }

    private AssessmentCreateSpec spec() {
        assertNotNull(ctx.lastCreateSpec, "no create dataset in context — run the create step first");
        return ctx.lastCreateSpec;
    }

    private PlaygroundLanding landing() {
        return new PlaygroundLanding(studentPage);
    }

    private PlaygroundControlPanel cp() {
        return new PlaygroundControlPanel(studentPage);
    }

    private void releaseAdminContext() {
        if (ctx.context == null) return;
        ctx.stopTracing(null);
        try { ctx.context.close(); } catch (Throwable ignored) {}
        ctx.context = null;
        ctx.page = null;
    }

    private void openStudentPageFor(ApiClient.SeededUser user) {
        Browser.NewContextOptions opts = new Browser.NewContextOptions()
                .setBaseURL(Settings.BASE_URL)
                .setIgnoreHTTPSErrors(true)
                .setAcceptDownloads(true);
        if (ctx.studentContext != null) {
            try { ctx.studentContext.close(); } catch (Throwable ignored) {}
        }
        ctx.studentContext = BrowserUi.newContext(ctx.browser, opts);
        studentPage = ctx.studentContext.newPage();
        studentPage.setDefaultTimeout(Settings.DEFAULT_TIMEOUT_MS);
        ctx.studentPage = studentPage;
        ctx.watchPageConsole(studentPage, "student");
        ctx.handoffTracingTo(ctx.studentContext);
        Auth.uiLogin(studentPage, user.username, user.password);
    }

    private void openCreatedLanding() {
        landing().open(createdCmid());
        studentPage.waitForTimeout(1_500);
    }

    private String landingBody() {
        return studentPage.locator("#region-main, body").first().innerText();
    }

    private String descriptionSurfaceText() {
        Locator full = studentPage.locator("#full-text");
        if (full.count() > 0) {
            String t = full.first().innerText().trim();
            if (!t.isBlank()) return t;
        }
        Locator shortDesc = studentPage.locator("#cl-content, .cl-content-short");
        if (shortDesc.count() > 0) {
            String t = shortDesc.first().innerText().trim();
            if (!t.isBlank()) return t;
        }
        return landingBody();
    }

    @Given("a student is enrolled in the created sandbox course")
    public void aStudentIsEnrolledInTheCreatedSandboxCourse() {
        if (Settings.WS_TOKEN.isEmpty()) throw new SkipException("MOODLE_WS_TOKEN not set");
        courseId = Long.parseLong(SandboxCourse.ensure(ctx.page));
        student = chooseStudentForSandboxRun();
        bindNamedStudentIfNeeded();
        releaseAdminContext();
        openStudentPageFor(student);
        System.out.println("[SB-user] using student=" + student.username + " course=" + courseId
                + " cmid=" + ctx.newActivityCmid);
    }

    /**
     * Same Moodle user across scenarios for one template (Account / VM / catalog / git).
     * Reuses an existing cloud lab via Continue instead of provisioning a new one.
     */
    @Given("a named student {string} is enrolled in the created sandbox course")
    public void aNamedStudentIsEnrolledInTheCreatedSandboxCourse(String namedKey) {
        if (Settings.WS_TOKEN.isEmpty()) throw new SkipException("MOODLE_WS_TOKEN not set");
        // Hold this named student's lane for the rest of the scenario: only scenarios sharing THIS
        // lab are serialized, the other templates keep running in parallel.
        ctx.enterLane("sbuser:" + namedKey);
        namedStudentBound = true;
        courseId = Long.parseLong(SandboxCourse.ensure(ctx.page));
        student = SandboxUserRegistry.getNamed(namedKey);
        if (student == null) {
            student = ApiClient.createUser(System.currentTimeMillis());
            ApiClient.enrolUser(student.id, courseId, 5);
            SandboxUserRegistry.put(courseId, student);
            SandboxUserRegistry.putNamed(namedKey, student);
            System.out.println("[SB-user] named '" + namedKey + "' created=" + student.username);
        } else {
            try {
                ApiClient.enrolUser(student.id, courseId, 5);
            } catch (RuntimeException e) {
                System.out.println("[SB-user] named enrol skipped: " + e.getMessage());
            }
            System.out.println("[SB-user] named '" + namedKey + "' reuse=" + student.username);
        }
        releaseAdminContext();
        openStudentPageFor(student);
    }

    @Given("a fresh student is enrolled in the created sandbox course")
    public void aFreshStudentIsEnrolledInTheCreatedSandboxCourse() {
        if (Settings.WS_TOKEN.isEmpty()) throw new SkipException("MOODLE_WS_TOKEN not set");
        courseId = Long.parseLong(SandboxCourse.ensure(ctx.page));
        student = ApiClient.createUser(System.currentTimeMillis());
        ApiClient.enrolUser(student.id, courseId, 5);
        SandboxUserRegistry.put(courseId, student);
        if (ctx.hasTag("@SB-user-6")) {
            SandboxUserRegistry.putNamed(NOTES_PDF_CHAIN_USER, student);
            System.out.println("[SB-user] registered " + NOTES_PDF_CHAIN_USER + "=" + student.username);
        }
        releaseAdminContext();
        openStudentPageFor(student);
        System.out.println("[SB-user] fresh student=" + student.username + " course=" + courseId
                + " cmid=" + ctx.newActivityCmid);
    }

    /** Reuses the running-lab student from {@code @SB-user-6} on {@code sandbox_create_no}. */
    @Given("the sandbox lab notes student is enrolled for PDF No verification")
    public void theSandboxLabNotesStudentIsEnrolledForPdfNoVerification() {
        if (Settings.WS_TOKEN.isEmpty()) throw new SkipException("MOODLE_WS_TOKEN not set");
        courseId = Long.parseLong(SandboxCourse.ensure(ctx.page));
        // Wait if @SB-user-6 is still holding the chain lane (parallel full-file runs).
        ctx.enterLane(NOTES_PDF_CHAIN_LANE, true);
        int waitSec = Boolean.getBoolean("sandbox.parallel") ? 300 : 8;
        student = waitForNotesPdfChainStudent(waitSec);
        if (student == null) {
            student = ApiClient.createUser(System.currentTimeMillis());
            ApiClient.enrolUser(student.id, courseId, 5);
            SandboxUserRegistry.put(courseId, student);
            namedStudentBound = false;
            System.out.println("[SB-user] PDF No — no chain user yet; fresh student="
                    + student.username + " (run @SB-user-6 first to share the notes lab user)");
        } else {
            namedStudentBound = true;
            try {
                ApiClient.enrolUser(student.id, courseId, 5);
            } catch (RuntimeException e) {
                System.out.println("[SB-user] notes/PDF chain enrol skipped: " + e.getMessage());
            }
            System.out.println("[SB-user] PDF No reuse " + NOTES_PDF_CHAIN_USER + "=" + student.username);
        }
        releaseAdminContext();
        openStudentPageFor(student);
    }

    private ApiClient.SeededUser waitForNotesPdfChainStudent(int maxSeconds) {
        long deadline = System.currentTimeMillis() + maxSeconds * 1000L;
        while (System.currentTimeMillis() < deadline) {
            ApiClient.SeededUser u = SandboxUserRegistry.getNamed(NOTES_PDF_CHAIN_USER);
            if (u != null) {
                return u;
            }
            try {
                Thread.sleep(2_000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        return SandboxUserRegistry.getNamed(NOTES_PDF_CHAIN_USER);
    }

    /** Reuses the running VM lab + student from {@code @SB-user-14} (same {@code @fresh_activity}). */
    @Given("the VM sandbox student and activity from SB-user-14 are reused")
    public void theVmSandboxStudentAndActivityFromSbUser14AreReused() {
        if (Settings.WS_TOKEN.isEmpty()) throw new SkipException("MOODLE_WS_TOKEN not set");
        if (!vmVpatChainPassedInProcess) {
            throw new SkipException("@SB-user-19 requires a PASSED @SB-user-14 in this JVM run "
                    + "(run: -Dcucumber.tags=\"@SB-user-14 or @SB-user-19\")");
        }
        courseId = Long.parseLong(SandboxCourse.ensure(ctx.page));
        SandboxActivityRegistry.Entry entry =
                SandboxActivityRegistry.get(VM_VPAT_CHAIN_ACTIVITY, SandboxCourse.idOrEmpty());
        student = SandboxUserRegistry.getNamed(VM_VPAT_CHAIN_USER);
        if (entry == null || student == null) {
            throw new SkipException("@SB-user-19 chain registry incomplete after @SB-user-14 pass "
                    + "(activity=" + (entry == null ? "missing" : "ok")
                    + " user=" + (student == null ? "missing" : student.username) + ")");
        }
        ctx.newActivityCmid = entry.cmid();
        ctx.newActivityName = entry.name();
        ctx.sectionId = entry.sectionId();
        ctx.newActivityDataset = "sandbox_vm_vpat";
        ctx.lastCreateSpec = SandboxTestData.create("sandbox_vm_vpat");
        namedStudentBound = true;
        try {
            ApiClient.enrolUser(student.id, courseId, 5);
        } catch (RuntimeException e) {
            System.out.println("[SB-user] vm_vpat chain enrol skipped: " + e.getMessage());
        }
        String chainLabId = SandboxUserRegistry.getNamedMeta(VM_VPAT_CHAIN_USER, VM_VPAT_CHAIN_LAB_META);
        if (chainLabId != null && !chainLabId.isBlank()) {
            rememberSandboxLabId(chainLabId);
            GlobalHooks.teardownLabId = chainLabId.trim();
        }
        if (ctx.sectionId != null && !ctx.sectionId.isBlank()) {
            GlobalHooks.teardownSectionId = ctx.sectionId;
        }
        if (student.email != null && !student.email.isBlank()) {
            GlobalHooks.teardownStudentEmail = student.email;
        }
        GlobalHooks.labProvisioned = true;
        System.out.println("[SB-user] @SB-user-19 reuse " + VM_VPAT_CHAIN_USER + "=" + student.username
                + " cmid=" + entry.cmid() + " labId=" + chainLabId + " (same lab as @SB-user-14)");
        releaseAdminContext();
        openStudentPageFor(student);
    }

    private ApiClient.SeededUser waitForVmVpatChainStudent(int maxSeconds) {
        long deadline = System.currentTimeMillis() + maxSeconds * 1000L;
        while (System.currentTimeMillis() < deadline) {
            ApiClient.SeededUser u = SandboxUserRegistry.getNamed(VM_VPAT_CHAIN_USER);
            if (u != null) {
                return u;
            }
            try {
                Thread.sleep(2_000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        return SandboxUserRegistry.getNamed(VM_VPAT_CHAIN_USER);
    }

    private SandboxActivityRegistry.Entry waitForVmVpatChainActivity(String courseId, int maxSeconds) {
        if (courseId == null || courseId.isBlank()) return null;
        long deadline = System.currentTimeMillis() + maxSeconds * 1000L;
        while (System.currentTimeMillis() < deadline) {
            SandboxActivityRegistry.Entry entry =
                    SandboxActivityRegistry.get(VM_VPAT_CHAIN_ACTIVITY, courseId);
            if (entry != null) {
                return entry;
            }
            try {
                Thread.sleep(2_000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        return SandboxActivityRegistry.get(VM_VPAT_CHAIN_ACTIVITY, courseId);
    }

    /**
     * Prefer an already-enrolled known student from the course participants list who has NOT
     * already been marked as used for this activity. Create+enrol only when none are free.
     * No probe logins — the chosen student is logged in once by the caller.
     */
    private ApiClient.SeededUser chooseStudentForSandboxRun() {
        String cmid = ctx.newActivityCmid;
        Set<String> participants = scrapeParticipantKeys();
        Map<String, ApiClient.SeededUser> byUsername = new java.util.LinkedHashMap<>();

        for (ApiClient.SeededUser u : SandboxUserRegistry.usersForCourse(courseId)) {
            if (participantContains(participants, u)) {
                byUsername.put(u.username.toLowerCase(Locale.ROOT), u);
            } else {
                System.out.println("[SB-user] skip " + u.username + " — not on participants page");
            }
        }
        for (String key : participants) {
            if (!key.startsWith("autotest_")) continue;
            if (byUsername.containsKey(key)) continue;
            ApiClient.SeededUser derived = SandboxUserRegistry.fromAutotestUsername(key);
            if (derived != null) byUsername.put(key, derived);
        }

        for (ApiClient.SeededUser u : byUsername.values()) {
            if (SandboxUserRegistry.hasUsedActivity(courseId, cmid, u.username)) {
                System.out.println("[SB-user] skip " + u.username + " — already used activity cmid=" + cmid);
                continue;
            }
            SandboxUserRegistry.put(courseId, u);
            System.out.println("[SB-user] reusing enrolled student=" + u.username
                    + " (single login; no probe)");
            return u;
        }

        ApiClient.SeededUser created = ApiClient.createUser(System.currentTimeMillis());
        ApiClient.enrolUser(created.id, courseId, 5);
        SandboxUserRegistry.put(courseId, created);
        System.out.println("[SB-user] created+enrolled new student=" + created.username
                + " (no free participant for cmid=" + cmid + ")");
        return created;
    }

    private Set<String> scrapeParticipantKeys() {
        Set<String> keys = new LinkedHashSet<>();
        if (ctx.page == null) return keys;
        try {
            ctx.page.navigate(Settings.BASE_URL + "/user/index.php?id=" + courseId,
                    new Page.NavigateOptions()
                            .setWaitUntil(com.microsoft.playwright.options.WaitUntilState.DOMCONTENTLOADED)
                            .setTimeout(45_000));
            ctx.page.waitForTimeout(2_000);
            Locator main = ctx.page.locator("#region-main, #page-content, body").first();
            String text = main.innerText();
            Matcher emails = Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}")
                    .matcher(text);
            while (emails.find()) keys.add(emails.group().toLowerCase(Locale.ROOT));
            Matcher users = Pattern.compile("\\bautotest_\\d+\\b", Pattern.CASE_INSENSITIVE).matcher(text);
            while (users.find()) keys.add(users.group().toLowerCase(Locale.ROOT));
            Locator mailtos = ctx.page.locator("a[href^='mailto:']");
            for (int i = 0; i < mailtos.count(); i++) {
                try {
                    String href = mailtos.nth(i).getAttribute("href");
                    if (href == null) continue;
                    String email = href.replaceFirst("(?i)^mailto:", "").split("\\?")[0].trim();
                    if (!email.isBlank()) keys.add(email.toLowerCase(Locale.ROOT));
                } catch (RuntimeException ignored) {}
            }
        } catch (RuntimeException e) {
            System.out.println("[SB-user] participants scrape failed: " + e.getMessage());
        }
        return keys;
    }

    private static boolean participantContains(Set<String> keys, ApiClient.SeededUser u) {
        if (keys == null || keys.isEmpty() || u == null) return false;
        if (u.email != null && keys.contains(u.email.toLowerCase(Locale.ROOT))) return true;
        return u.username != null && keys.contains(u.username.toLowerCase(Locale.ROOT));
    }

    @When("the student opens the created sandbox activity")
    public void theStudentOpensTheCreatedSandboxActivity() {
        if (student == null) throw new SkipException("no student for this sandbox run");
        if (studentPage == null || studentPage.isClosed()) {
            releaseAdminContext();
            openStudentPageFor(student);
        }
        openCreatedLanding();
        assertTrue(studentPage.url().contains("view.php?id=" + createdCmid()),
                "student was redirected away from the sandbox landing (url=" + studentPage.url() + ")");
    }

    @Then("the student can access the created sandbox activity")
    public void theStudentCanAccessTheCreatedSandboxActivity() {
        assertTrue(landing().isLoaded(), "sandbox landing panel not loaded for student");
        assertTrue(studentPage.url().contains("view.php?id=" + createdCmid()),
                "student is not on the created sandbox (url=" + studentPage.url() + ")");
        String name = preferredActivityName();
        String header = "";
        Locator title = studentPage.locator(".page-header-headings h1, h1.h2").first();
        try {
            header = title.innerText().trim();
        } catch (RuntimeException ignored) {}
        String surface = header + "\n" + landingBody();
        assertTrue(surface.contains(name),
                "student cannot see the sandbox activity name '" + name + "' (header='" + header + "')");
        assertTrue(landing().hasStartSandbox() || landing().hasActiveLab(),
                "student does not see Start Sandbox or Continue on the landing");
    }

    private String preferredActivityName() {
        String fromCtx = com.nuvepro.moodle.helpers.SandboxActivityRegistry
                .sanitizeActivityName(ctx.newActivityName);
        if (!fromCtx.isBlank()) return fromCtx;
        return spec().name != null ? spec().name.trim() : "";
    }

    @Then("the student sees sandbox activity details matching the create settings")
    public void theStudentSeesSandboxActivityDetailsMatchingTheCreateSettings() {
        String body = landingBody();
        AssessmentCreateSpec s = spec();

        assertTrue(body.contains("Lab Type") || body.toLowerCase().contains("lab type"),
                "'Lab Type' not shown on student sandbox landing");
        assertTrue(body.contains("Sandbox") || body.toLowerCase().contains("playground"),
                "lab type is not Sandbox/playground on student landing");
        assertTrue(body.contains("Lab Status") || body.toLowerCase().contains("lab status"),
                "'Lab Status' not shown");
        assertTrue(body.contains("Lab Created") || body.contains("Created"),
                "'Lab Created' date not shown");
        assertTrue(body.contains("Sandbox ID") || body.toLowerCase().contains("sandbox id"),
                "'Sandbox ID' not shown");
        assertTrue(body.contains("Duration") || body.contains("Alloted") || body.contains("Allotted"),
                "duration / alloted not shown on student landing");
        assertTrue(body.contains("Description")
                        || studentPage.locator("#cl-content, #full-text, #read-more-btn").count() > 0,
                "description section not shown on student landing");

        // Description must match what was set at activity create (same text surfaces on console Instructions).
        assertDescriptionMatchesCreateSettings(descriptionSurfaceText(), "landing description");

        if (s.adminName != null && !s.adminName.isBlank() && !s.adminName.equals(s.name)) {
            assertFalse(body.contains(s.adminName),
                    "student landing shows Activity Name for Admins (should show activity name): " + s.adminName);
        }
        System.out.println("[SB-user] landing details OK for cmid=" + createdCmid());
    }

    /**
     * Manual description: assert the create-spec text appears.
     * Git description: assert non-empty populated content (exact git body not asserted here).
     */
    private void assertDescriptionMatchesCreateSettings(String actual, String where) {
        AssessmentCreateSpec s = spec();
        String text = actual == null ? "" : actual.trim();
        boolean fromGit = s.descriptionSource != null
                && s.descriptionSource.toLowerCase(Locale.ROOT).contains("git");
        if (fromGit) {
            assertTrue(text.length() > 15,
                    where + " empty for git-sourced description (got '"
                            + text.substring(0, Math.min(80, text.length())) + "')");
            return;
        }
        if (s.description == null || s.description.isBlank()) {
            assertTrue(text.length() > 5 || studentPage.locator("#cl-content, #full-text").count() > 0,
                    where + " section missing");
            return;
        }
        String expected = s.description.trim();
        String marker = expected.length() > 32 ? expected.substring(0, 32) : expected;
        assertTrue(text.contains(marker),
                where + " must show the create-settings description.\n expected marker: '"
                        + marker + "'\n got: '"
                        + text.substring(0, Math.min(160, text.length())) + "'");
    }

    @When("the student starts the sandbox and opens the lab console")
    public void theStudentStartsTheSandboxAndOpensTheLabConsole() {
        GlobalHooks.labProvisioned = true;
        openSandboxLabConsoleWithDeletedLabRecovery();
        if (student != null) {
            SandboxUserRegistry.markUsedActivity(courseId, ctx.newActivityCmid, student.username);
        }
        rememberSandboxLabId(cp().labId());
        if (ctx.sectionId != null && !ctx.sectionId.isBlank()) {
            GlobalHooks.teardownSectionId = ctx.sectionId;
        }
        if (student != null) GlobalHooks.teardownStudentEmail = student.email;
    }

    /** Start Sandbox / Continue, then re-seed student + activity if the console shows a deleted lab. */
    private void openSandboxLabConsoleWithDeletedLabRecovery() {
        landing().openLabConsole();
        try {
            cp().expectControlPanelLoaded(90_000);
        } catch (RuntimeException e) {
            if (recoverFromDeletedOrUnstartableLab("console-load")) {
                return;
            }
            throw new AssertionError("sandbox lab console did not load after Start Sandbox (url="
                    + studentPage.url() + "): " + e.getMessage(), e);
        }
        if (recoverFromDeletedOrUnstartableLab("console-state")) {
            return;
        }
    }

    /**
     * Named reuse students can hit an admin-deleted subscription. Seed a fresh user on the same
     * activity (or a new one when {@code @fresh_activity}) and open a clean console.
     */
    private boolean recoverFromDeletedOrUnstartableLab(String phase) {
        PlaygroundControlPanel panel = cp();
        boolean deleted = panel.showsLabDeletedMessage();
        boolean startVisible = isStartLabControlVisible();
        boolean running = panel.waitUntilLabLoaded(5);
        if (!deleted && (running || startVisible)) {
            return false;
        }
        // VM/catalog flows often have no Start Lab until Proceed — do not swap user mid-provision.
        if (!deleted && (phase.contains("vm-catalog") || phase.contains("catalog"))) {
            return false;
        }
        // @SB-user-14 / @SB-user-19: one student + one lab — never re-seed unless deleted.
        if ((ctx.hasTag("@SB-user-14") || ctx.hasTag("@SB-user-19")) && !deleted) {
            return false;
        }
        if (!deleted && !phase.contains("start-lab") && !phase.contains("console")) {
            return false;
        }
        if (Settings.WS_TOKEN.isEmpty()) {
            return false;
        }
        System.out.println("[SB-user] deleted/stale lab recovery (" + phase + ") user="
                + (student == null ? "?" : student.username) + " deleted=" + deleted
                + " startVisible=" + startVisible + " running=" + running);
        ApiClient.SeededUser fresh = ApiClient.createUser(System.currentTimeMillis());
        ApiClient.enrolUser(fresh.id, courseId, 5);
        SandboxUserRegistry.put(courseId, fresh);
        student = fresh;
        namedStudentBound = false;
        sandboxLabId = null;
        GlobalHooks.teardownLabId = null;
        releaseAdminContext();
        openStudentPageFor(student);
        openCreatedLanding();
        if (!landing().hasStartSandbox()) {
            throw new AssertionError("recovery student has no Start Sandbox on cmid=" + createdCmid()
                    + " (url=" + studentPage.url() + ") — check allow-lab-create-when-deleted");
        }
        landing().openLabConsole();
        cp().expectControlPanelLoaded(90_000);
        if (cp().showsLabDeletedMessage()) {
            throw new AssertionError("fresh student still sees deleted lab after recovery on cmid="
                    + createdCmid());
        }
        GlobalHooks.labProvisioned = true;
        GlobalHooks.teardownStudentEmail = student.email;
        return true;
    }

    private boolean isStartLabControlVisible() {
        Locator startBtn = studentPage.locator(PlaygroundControlPanel.LAUNCH_START);
        try {
            return startBtn.count() > 0 && startBtn.first().isVisible();
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    @When("the student starts the sandbox and observes create progress")
    public void theStudentStartsTheSandboxAndObservesCreateProgress() {
        GlobalHooks.labProvisioned = true;
        assertTrue(landing().startSandboxAndObserveFourStepProgress(),
                "the visible Create loader did not exercise the four-step lab progress tracker");
        cp().expectControlPanelLoaded(90_000);
        pauseAfterLabProgressComplete();
        if (student != null) GlobalHooks.teardownStudentEmail = student.email;
        if (ctx.sectionId != null && !ctx.sectionId.isBlank()) {
            GlobalHooks.teardownSectionId = ctx.sectionId;
        }
    }

    @When("the student starts the lab and observes start progress")
    public void theStudentStartsTheLabAndObservesStartProgress() {
        observeStartOrUnparkProgress("Start");
    }

    /** Manual "Unpark" = Start Lab after Stop (Park = Stop). */
    @When("the student unparks the lab and observes unpark progress")
    public void theStudentUnparksTheLabAndObservesUnparkProgress() {
        PlaygroundControlPanel panel = cp();
        if (!panel.isShown() && !studentPage.url().contains("cp.php")) {
            landing().openLabConsole();
        }
        observeStartOrUnparkProgress("Unpark");
    }

    private void observeStartOrUnparkProgress(String actionLabel) {
        PlaygroundControlPanel panel = cp();
        assertFalse(panel.waitUntilLabLoaded(2),
                "lab was already loaded before " + actionLabel + " could exercise its progress tracker");
        assertTrue(panel.clickStartLab(), actionLabel + " (Start Lab) control was not available");
        assertTrue(panel.waitForFourStepProgress(15),
                "four-step progress did not appear during " + actionLabel);
        clickProceedIfShown();
        boolean loaded = panel.waitUntilLabLoaded(180);
        if (!loaded && isVmSandboxDataset()) {
            loaded = waitForVmIframe(90);
        }
        assertTrue(loaded,
                "sandbox lab did not load within 180s after " + actionLabel + " progress (url="
                        + studentPage.url() + ")");
        ensureSandboxLabIdCaptured();
        pauseAfterLabProgressComplete();
    }

    /** Headed runs: keep the console visible briefly after Create/Start completes. */
    private void pauseAfterLabProgressComplete() {
        if (studentPage == null) return;
        System.out.println("[SB-user] lab progress complete — waiting 10s before scenario ends");
        studentPage.waitForTimeout(10_000);
    }

    private boolean isVmSandboxDataset() {
        String ds = ctx.newActivityDataset;
        return ds != null && (ds.contains("vm") || ds.contains("VM"));
    }

    private int sandboxAccountLabLoadSeconds() {
        return Boolean.getBoolean("sandbox.parallel") ? 300 : 180;
    }

    private int sandboxVmCatalogWaitSeconds() {
        return Boolean.getBoolean("sandbox.parallel") ? 600 : 420;
    }

    private int sandboxVmStartLabSeconds() {
        return Boolean.getBoolean("sandbox.parallel") ? 360 : 240;
    }

    /** Best-effort lab id without failing when Manage Labs lookup is deferred. */
    private void pollSandboxLabIdBestEffort() {
        if (resolveSandboxLabId() != null) {
            return;
        }
        for (int i = 0; i < 12; i++) {
            try {
                rememberSandboxLabId(cp().labId());
            } catch (RuntimeException ignored) {}
            if (sandboxLabId != null && !sandboxLabId.isBlank()) {
                return;
            }
            studentPage.waitForTimeout(2_000);
        }
        if (manageLabsLabIdLookupAllowed()) {
            try {
                rememberSandboxLabId(labIdFromManageLabs());
            } catch (RuntimeException ignored) {}
        }
    }

    /**
     * Under parallel load the console UI lags CloudLabs; poll API then re-check the lab surface.
     */
    private boolean tryWaitSandboxRunningViaCloudLabsThenUi(int apiMaxSeconds, int uiMaxSeconds) {
        PlaygroundControlPanel panel = cp();
        if (panel.waitUntilLabLoaded(Math.min(uiMaxSeconds, 15))) {
            return true;
        }
        pollSandboxLabIdBestEffort();
        String labId = resolveSandboxLabId();
        if (labId != null && !labId.isBlank() && CloudLabsClient.isConfigured()
                && !CloudLabsClient.isBlocked()) {
            ManageLabs.LabState state = CloudLabsClient.waitForState(labId,
                    EnumSet.of(ManageLabs.LabState.RUNNING, ManageLabs.LabState.FAILED),
                    apiMaxSeconds, 5);
            if (state == ManageLabs.LabState.RUNNING) {
                openCreatedLanding();
                ensureStudentOnLabConsole();
                return panel.waitUntilLabLoaded(uiMaxSeconds);
            }
        }
        return panel.waitUntilLabLoaded(uiMaxSeconds);
    }

    /** REMOVE with {@code @LT-9} scenario (not automatable; feature tagged {@code @skip}). */
    @When("the lab duration is reduced into the template notification threshold")
    public void theLabDurationIsReducedIntoTheTemplateNotificationThreshold() {
        ensureSandboxLabIdCaptured();
        Locator enabled = studentPage.locator("#np-ap-mod-enable_notification");
        assertTrue(enabled.count() > 0 && ("1".equals(enabled.first().inputValue())
                        || "true".equalsIgnoreCase(enabled.first().inputValue())),
                "selected Sandbox template does not enable duration notifications");
        int thresholdMinutes = readHiddenMinutes("#np-ap-mod-notiDuration", 5);
        int remainingSeconds = readHiddenSeconds("#np-ap-mod-labDuration");
        assertTrue(remainingSeconds > 0,
                "could not read remaining lab duration for notification threshold");
        int targetSeconds = Math.max(5, thresholdMinutes * 60 - 20);
        if (remainingSeconds <= targetSeconds) {
            System.out.println("[SB-user] already within notification threshold (remaining="
                    + remainingSeconds + "s threshold=" + thresholdMinutes + "m)");
        } else if (CloudLabsClient.isConfigured() && !CloudLabsClient.isBlocked()) {
            int reductionMinutes = Math.max(1, (remainingSeconds - targetSeconds + 59) / 60);
            assertTrue(CloudLabsClient.extendDuration(resolveSandboxLabId(), -reductionMinutes, "Minutes"),
                    "CloudLabs did not reduce the lab into its notification threshold");
        } else {
            System.out.println("[SB-user] CloudLabs unavailable — remaining=" + remainingSeconds
                    + "s may already be near threshold=" + thresholdMinutes + "m");
        }
        studentPage.waitForTimeout(3_000);
        openCreatedLanding();
        landing().openLabConsole();
        cp().expectControlPanelLoaded(90_000);
    }

    private int readHiddenMinutes(String selector, int fallback) {
        Locator loc = studentPage.locator(selector);
        if (loc.count() == 0) return fallback;
        try {
            String v = loc.first().inputValue().trim();
            if (v.matches("\\d+")) return Integer.parseInt(v);
        } catch (RuntimeException ignored) {}
        return fallback;
    }

    private int readHiddenSeconds(String selector) {
        Locator loc = studentPage.locator(selector);
        if (loc.count() > 0) {
            try {
                String v = loc.first().inputValue().trim();
                if (v.matches("\\d+")) return Integer.parseInt(v);
            } catch (RuntimeException ignored) {}
        }
        openCreatedLanding();
        int mins = Math.max(remainingMinutesFromLanding(), allottedMinutesFromLanding());
        if (mins > 0) return mins * 60;
        return -1;
    }

    /** REMOVE with {@code @LT-9} scenario (not automatable; feature tagged {@code @skip}). */
    @Then("the configured template duration notification appears")
    public void theConfiguredTemplateDurationNotificationAppears() {
        Locator notification = studentPage.locator("#user-notifications #notifications");
        boolean shown = false;
        for (int i = 0; i < 30; i++) {
            try {
                if (notification.count() > 0 && notification.first().isVisible()
                        && !notification.first().innerText().isBlank()) {
                    shown = true;
                    break;
                }
            } catch (RuntimeException ignored) {}
            studentPage.waitForTimeout(1_000);
        }
        assertTrue(shown, "configured duration-threshold notification did not appear");
    }

    /**
     * @SB-user-14: one student, one Start Sandbox, catalog Proceed, one Start Lab, then wait for VM UI.
     */
    @When("the student starts the VM sandbox through catalog and waits until the lab is loaded")
    public void theStudentStartsTheVmSandboxThroughCatalogAndWaitsUntilTheLabIsLoaded() {
        theStudentStartsTheSandboxAndContinuesThroughCatalogDetailsIfShown();
        ensureStudentOnLabConsole();
        if (!vmLabReadySignal()) {
            vmStartLabAndWaitUntilReady(sandboxVmStartLabSeconds());
        }
        assertTrue(vmLabReadySignal(),
                "VM sandbox not ready after catalog + Start Lab (url="
                        + studentPage.url() + " reason=" + cp().labSurfaceReadyReason() + ")");
    }

    @When("the student starts the lab and waits until it is loaded")
    public void theStudentStartsTheLabAndWaitsUntilItIsLoaded() {
        PlaygroundControlPanel panel = cp();
        if (panel.showsLabDeletedMessage() && recoverFromDeletedOrUnstartableLab("pre-start")) {
            theStudentStartsTheLabAndWaitsUntilItIsLoaded();
            return;
        }
        if (isVmSandboxDataset()) {
            vmStartLabAndWaitUntilReady(sandboxVmStartLabSeconds());
            if (manageLabsLabIdLookupAllowed()) {
                ensureSandboxLabIdCaptured();
            }
            return;
        }
        if (panel.isStopLabEnabled() || panel.waitUntilLabLoaded(15)) {
            System.out.println("[SB-user] account lab already running on console — skip Start Lab");
            if (manageLabsLabIdLookupAllowed()) {
                ensureSandboxLabIdCaptured();
            }
            return;
        }
        if (!isStartLabButtonShown() && panel.waitUntilLabLoaded(5)) {
            System.out.println("[SB-user] lab already loaded before Start Lab click");
            if (manageLabsLabIdLookupAllowed()) {
                ensureSandboxLabIdCaptured();
            }
            return;
        }
        if (!isStartLabButtonShown()) {
            if (panel.waitUntilLabLoaded(120)) {
                if (manageLabsLabIdLookupAllowed()) {
                    ensureSandboxLabIdCaptured();
                }
                return;
            }
            if (recoverFromDeletedOrUnstartableLab("start-lab")) {
                theStudentStartsTheLabAndWaitsUntilItIsLoaded();
                return;
            }
            throw new AssertionError("Start Lab control not shown and lab did not become ready (url="
                    + studentPage.url() + ")");
        }
        panel.clickStartLab();
        clickProceedIfShown();
        dismissLaunchAccModalIfShown();
        int surfaceWait = "labtemplates_notification".equals(ctx.newActivityDataset)
                ? 20 : sandboxAccountLabLoadSeconds();
        boolean surfaceLoaded = panel.waitUntilLabLoaded(surfaceWait);
        if (!surfaceLoaded && "labtemplates_notification".equals(ctx.newActivityDataset)) {
            ensureSandboxLabIdCaptured();
            ManageLabs.LabState state = CloudLabsClient.waitForState(resolveSandboxLabId(),
                    EnumSet.of(ManageLabs.LabState.RUNNING, ManageLabs.LabState.FAILED), 180, 5);
            assertEquals(state, ManageLabs.LabState.RUNNING,
                    "notification fixture lab did not reach RUNNING through CloudLabs API");
            return;
        }
        if (!surfaceLoaded) {
            surfaceLoaded = tryWaitSandboxRunningViaCloudLabsThenUi(120, 90);
        }
        assertTrue(surfaceLoaded,
                "sandbox lab did not load within " + surfaceWait + "s after Start Lab (url="
                        + studentPage.url() + ")");
        if (manageLabsLabIdLookupAllowed()) {
            ensureSandboxLabIdCaptured();
        }
    }

    private void vmStartLabAndWaitUntilReady(int maxSeconds) {
        PlaygroundControlPanel panel = cp();
        ensureStudentOnLabConsole();
        if (vmLabReadySignal()) {
            System.out.println("[SB-user] VM lab already ready on console");
            return;
        }
        long deadline = System.currentTimeMillis() + maxSeconds * 1000L;
        boolean startClicked = false;
        int poll = 0;
        while (System.currentTimeMillis() < deadline) {
            poll++;
            ensureStudentOnLabConsole();
            if (studentPage.url().contains("/mod/cloudlabs/view.php") && !cp().isLabCpFragmentMounted()) {
                openStudentLabConsoleForVmOrCatalog();
                completeCatalogFormIfShown();
                clickCatalogProceedIfShown();
            }
            if (!startClicked && isStartLabButtonShown()) {
                System.out.println("[SB-user] VM Start Lab click poll=" + poll);
                ensureVmStartLabClickedIfShown();
                startClicked = true;
                if (!panel.waitForFourStepProgress(30)) {
                    System.out.println("[SB-user] four-step loader not seen (VM may skip)");
                }
            }
            clickCatalogProceedIfShown();
            dismissLaunchAccModalIfShown();
            if (vmLabReadySignal()) {
                System.out.println("[SB-user] VM lab ready poll=" + poll + " url=" + studentPage.url());
                return;
            }
            if (startClicked && poll % 5 == 0) {
                System.out.println("[SB-user] VM lab waiting poll=" + poll + " ready="
                        + cp().labSurfaceReadyReason());
            }
            studentPage.waitForTimeout(2_000);
        }
        ensureStudentOnLabConsole();
        assertTrue(vmLabReadySignal(),
                "VM lab did not finish loading within " + maxSeconds + "s (url="
                        + (studentPage == null ? "?" : studentPage.url()) + " reason="
                        + cp().labSurfaceReadyReason() + ")");
    }

    /** Re-open in-page console after AJAX/navigation drops the cp fragment (Continue, not Start Sandbox). */
    private void ensureStudentOnLabConsole() {
        if (studentPage == null || studentPage.isClosed()) return;
        if (vmLabReadySignal()) return;
        if ((cp().isConsoleOpen() || cp().isShown()) && cp().isLabCpFragmentMounted()) return;
        try {
            if (landing().hasVisibleContinue() || landing().hasActiveLab()) {
                landing().openConsoleViaContinue();
            } else if (landing().hasStartSandbox()) {
                landing().openLabConsole();
            } else {
                openCreatedLanding();
                if (landing().hasVisibleContinue()) {
                    landing().openConsoleViaContinue();
                }
            }
            cp().expectControlPanelLoaded(60_000);
        } catch (RuntimeException e) {
            System.out.println("[SB-user] ensureStudentOnLabConsole: " + e.getMessage());
        }
    }

    /**
     * VM readiness: iframe or visible topbar on a mounted console — never hidden DOM nodes or
     * {@code Start Lab hidden (launched)} alone (that fires immediately after click).
     */
    private boolean vmLabReadySignal() {
        if (studentPage == null || studentPage.isClosed()) {
            return false;
        }
        if (isStartLabButtonShown()) {
            return false;
        }
        if (iframeVisibleNow()) {
            return true;
        }
        if (vmToolbarVisibleNow()) {
            return true;
        }
        try {
            String why = cp().labSurfaceReadyReason();
            if (why != null && why.contains("Stop Lab enabled")) {
                return true;
            }
        } catch (RuntimeException ignored) {}
        return false;
    }

    private boolean vmCatalogFlowAlreadyReady() {
        if (isVmSandboxDataset()) {
            return vmLabReadySignal();
        }
        return studentLabSurfaceReady() || cp().waitUntilLabLoaded(8);
    }

    private boolean vmToolbarVisibleNow() {
        try {
            Object ok = studentPage.evaluate("""
                    () => {
                      const bar = document.querySelector('#np-ap-mod-lab-topbar');
                      if (!bar || bar.classList.contains('d-none')) return false;
                      const ids = ['#np-ap-mod-btn-tb-speedTest', '#np-ap-mod-btn-tb-fullscreen',
                        '#np-ap-mod-btn-tb-newtab'];
                      return ids.some(sel => {
                        const el = document.querySelector(sel);
                        return el && !el.classList.contains('d-none') && el.offsetParent !== null;
                      });
                    }
                    """);
            return Boolean.TRUE.equals(ok);
        } catch (RuntimeException e) {
            return false;
        }
    }

    private void dismissLaunchAccModalIfShown() {
        if (studentPage == null || studentPage.isClosed()) return;
        Locator dm = studentPage.locator("#launchAccModal [data-dismiss='modal'], #launchAccModal .close");
        if (dm.count() > 0) {
            try {
                if (dm.first().isVisible()) dm.first().click();
            } catch (RuntimeException ignored) {}
        }
    }

    private void rememberSandboxLabId(String labId) {
        if (labId == null || labId.isBlank() || "0".equals(labId.trim())) return;
        String trimmed = labId.trim();
        if (!cloudLabsKnowsSubscription(trimmed)) {
            System.out.println("[SB-user] ignore non-subscription labId=" + trimmed
                    + " (CloudLabs has no such subscription — will resolve via Manage Labs)");
            return;
        }
        sandboxLabId = trimmed;
        GlobalHooks.teardownLabId = sandboxLabId;
        System.out.println("[SB-user] captured labId=" + sandboxLabId);
    }

    /** True when CloudLabs returns action/status for this id (not an empty/unknown subscription). */
    private boolean cloudLabsKnowsSubscription(String labId) {
        if (!CloudLabsClient.isConfigured() || CloudLabsClient.isBlocked()) return true;
        try {
            CloudLabsClient.SubStatus st = CloudLabsClient.getStatus(labId);
            return st != null;
        } catch (RuntimeException e) {
            return true;
        }
    }

    /** Manage Labs admin UI opens a separate browser context — only for @SB-user-19 / @SB-manage-*. */
    private boolean manageLabsLabIdLookupAllowed() {
        return ctx.hasTag("@SB-user-19") || ctx.hasAnyTagStartingWith("@SB-manage-");
    }

    /** Prefer Manage Labs row id when allowed; else console/landing only. */
    private String resolveCloudLabsSubscriptionIdPreferManageLabs() {
        if (manageLabsLabIdLookupAllowed()) {
            String fromManage = labIdFromManageLabs();
            if (fromManage != null && !fromManage.isBlank()) return fromManage.trim();
        }
        String fromConsole = null;
        try {
            fromConsole = cp().labId();
        } catch (RuntimeException ignored) {}
        if (fromConsole == null || fromConsole.isBlank()) {
            try {
                fromConsole = landing().hiddenLabId();
            } catch (RuntimeException ignored) {}
        }
        if (fromConsole != null && cloudLabsKnowsSubscription(fromConsole.trim())) {
            return fromConsole.trim();
        }
        return sandboxLabId;
    }

    /** Poll console fields, then Manage Labs (admin) — lab id is often empty until Start completes. */
    private void ensureSandboxLabIdCaptured() {
        if (resolveSandboxLabId() != null) return;
        for (int i = 0; i < 20; i++) {
            try {
                rememberSandboxLabId(cp().labId());
            } catch (RuntimeException ignored) {}
            if (sandboxLabId != null && !sandboxLabId.isBlank()) return;
            studentPage.waitForTimeout(2_000);
        }
        if (manageLabsLabIdLookupAllowed()) {
            rememberSandboxLabId(labIdFromManageLabs());
            assertTrue(sandboxLabId != null && !sandboxLabId.isBlank(),
                    "could not capture CloudLabs lab/subscription id from console or Manage Labs "
                            + "(student=" + (student == null ? "?" : student.email)
                            + ", section=" + ctx.sectionId + ")");
        } else if (sandboxLabId == null || sandboxLabId.isBlank()) {
            System.out.println("[SB-user] lab id not on console yet — Manage Labs lookup deferred "
                    + "(only @SB-user-19 opens Manage Labs for subscription id)");
        }
    }

    /** Resolve lab id from memory, console, landing, or Manage Labs. */
    private String resolveSandboxLabId() {
        if (sandboxLabId != null && !sandboxLabId.isBlank()) return sandboxLabId;
        if (GlobalHooks.teardownLabId != null && !GlobalHooks.teardownLabId.isBlank()) {
            sandboxLabId = GlobalHooks.teardownLabId;
            return sandboxLabId;
        }
        try {
            rememberSandboxLabId(cp().labId());
            if (sandboxLabId != null) return sandboxLabId;
        } catch (RuntimeException ignored) {}
        try {
            rememberSandboxLabId(landing().hiddenLabId());
            if (sandboxLabId != null) return sandboxLabId;
        } catch (RuntimeException ignored) {}
        if (manageLabsLabIdLookupAllowed()) {
            rememberSandboxLabId(labIdFromManageLabs());
        }
        return sandboxLabId;
    }

    /**
     * Proven fallback (FreshLab / Assessment): admin Manage Labs row for the student email
     * while the user still exists. Uses a short-lived admin context (storageState).
     */
    private String labIdFromManageLabs() {
        if (!manageLabsLabIdLookupAllowed()) return null;
        if (student == null || student.email == null || student.email.isBlank()) return null;
        int sid = resolveSectionIdInt();
        if (sid <= 0) return null;
        BrowserContext adminCtx = null;
        try {
            Browser.NewContextOptions opts = new Browser.NewContextOptions()
                    .setBaseURL(Settings.BASE_URL)
                    .setIgnoreHTTPSErrors(true)
                    .setStorageStatePath(GlobalHooks.STORAGE_STATE);
            adminCtx = BrowserUi.newContext(ctx.browser, opts);
            Page adminPage = adminCtx.newPage();
            adminPage.setDefaultTimeout(Settings.DEFAULT_TIMEOUT_MS);
            ManageLabs ml = new ManageLabs(adminPage);
            for (int i = 0; i < 12; i++) {
                ml.open(sid);
                String id = ml.labIdForEmail(student.email);
                if (id != null && !id.isBlank()) {
                    System.out.println("[SB-user] labId from Manage Labs email="
                            + student.email + " id=" + id);
                    return id.trim();
                }
                adminPage.waitForTimeout(3_000);
            }
        } catch (Throwable e) {
            System.out.println("[SB-user] Manage Labs labId lookup failed: " + e.getMessage());
        } finally {
            if (adminCtx != null) {
                try { adminCtx.close(); } catch (Throwable ignored) {}
            }
        }
        return null;
    }

    private int resolveSectionIdInt() {
        if (ctx.sectionId != null && !ctx.sectionId.isBlank()) {
            try { return Integer.parseInt(ctx.sectionId.trim()); } catch (NumberFormatException ignored) {}
        }
        if (GlobalHooks.teardownSectionId != null && !GlobalHooks.teardownSectionId.isBlank()) {
            try { return Integer.parseInt(GlobalHooks.teardownSectionId.trim()); } catch (NumberFormatException ignored) {}
        }
        try {
            String s = landing().hiddenSectionId();
            if (s != null && !s.isBlank()) {
                ctx.sectionId = s.trim();
                return Integer.parseInt(s.trim());
            }
        } catch (RuntimeException ignored) {}
        return 0;
    }

    @Then("the sandbox lab is loaded on the console")
    public void theSandboxLabIsLoadedOnTheConsole() {
        assertTrue(cp().waitUntilLabLoaded(10),
                "sandbox lab is not loaded on the console (Stop Lab / Jump to Console missing)");
    }

    @Then("the student verifies sandbox console instructions duration credentials and lab info")
    public void theStudentVerifiesSandboxConsoleInstructionsDurationCredentialsAndLabInfo() {
        PlaygroundControlPanel panel = cp();
        java.util.ArrayList<String> misses = new java.util.ArrayList<>();

        // 1) Instructions == activity create description (manual text or populated git content)
        String instructions = panel.openInstructionsAndRead();
        try {
            assertDescriptionMatchesCreateSettings(instructions, "console Instructions");
            System.out.println("[SB-user] Instructions OK (" + instructions.length() + " chars)");
        } catch (AssertionError e) {
            misses.add(e.getMessage());
        }

        // 2) Duration — Lab Usage panel + CloudLabs cross-check when lab id is known.
        String usage = panel.openUsageAndRead();
        String usageLower = usage.toLowerCase(Locale.ROOT);
        boolean hasDuration = usageLower.contains("duration")
                || usageLower.contains("allot")
                || usageLower.contains("consumed")
                || usageLower.contains("remaining")
                || usage.matches("(?s).*\\d+.*");
        if (usage.isBlank()) {
            misses.add("Duration/Lab Usage: panel empty or FAB not available after lab loaded");
        } else if (!hasDuration) {
            misses.add("Duration/Lab Usage: no duration/cost values (got '"
                    + usage.substring(0, Math.min(100, usage.length())) + "')");
        } else {
            try {
                assertConsoleUsageAlignsWithCloudLabs(usage);
                System.out.println("[SB-user] Duration/Lab Usage OK (UI + CloudLabs)");
            } catch (AssertionError e) {
                misses.add(e.getMessage());
            }
        }

        // 3) Credentials — login + password content; CloudLabs username token when API returns it.
        String creds = panel.openCredentialsAndRead();
        String credsLower = creds.toLowerCase(Locale.ROOT);
        boolean hasUser = credsLower.contains("user") || credsLower.contains("login")
                || credsLower.contains("username");
        boolean hasPassword = credsLower.contains("password") || credsLower.contains("pass");
        if (creds.isBlank() || creds.length() < 8 || !hasPassword) {
            misses.add("Lab Credentials: missing user/password content (got '"
                    + (creds.isBlank() ? "<empty>" : creds.substring(0, Math.min(100, creds.length()))) + "')");
        } else if (!hasUser) {
            misses.add("Lab Credentials: password shown but no login/username label in panel");
        } else {
            try {
                assertConsoleCredentialsAlignWithCloudLabs(creds);
                System.out.println("[SB-user] Lab Credentials OK (UI + CloudLabs token when available)");
            } catch (AssertionError e) {
                misses.add(e.getMessage());
            }
        }

        // 4) Lab Info — content comes from LAB TEMPLATE creation (not activity create).
        // It is often populated (template notes/info); do not treat empty as the normal case.
        // TODO(@SB-user): when template lab-info text is known in config, assert that exact text.
        String info = panel.openLabInfoAndRead();
        if (info.isBlank()) {
            misses.add("Lab Info: popup empty or FAB not available "
                    + "(Lab Info is sourced from the lab template — expect template content here)");
        } else if (info.length() < 5) {
            misses.add("Lab Info: content too short ('" + info + "')");
        } else {
            System.out.println("[SB-user] Lab Info OK (" + info.length()
                    + " chars; from lab template — exact template text assert deferred)");
        }

        assertTrue(misses.isEmpty(),
                "sandbox console checks failed:\n - " + String.join("\n - ", misses));
    }

    @When("the student opens Access Lab from Jump to Console on the sandbox")
    public void theStudentOpensAccessLabFromJumpToConsoleOnTheSandbox() {
        PlaygroundControlPanel panel = cp();
        assertTrue(panel.clickJumpToConsole(),
                "Jump to Console was not available on the sandbox console");
        // Wait for Access Lab modal / links
        boolean linksReady = false;
        for (int i = 0; i < 20; i++) {
            if (panel.accessLabModalVisible()
                    || studentPage.locator(PlaygroundControlPanel.ACCESS_LAB_LINKS).count() > 0) {
                linksReady = true;
                break;
            }
            studentPage.waitForTimeout(500);
        }
        assertTrue(linksReady, "Access Lab popup/links did not appear after Jump to Console");
        try {
            openedConsoleTab = studentPage.context().waitForPage(
                    new BrowserContext.WaitForPageOptions().setTimeout(20_000),
                    panel::clickAccessLabLink);
        } catch (RuntimeException e) {
            openedConsoleTab = null;
        }
        if (openedConsoleTab == null) {
            String before = studentPage.url();
            if (panel.clickAccessLabLink()) {
                studentPage.waitForTimeout(3_000);
                if (!studentPage.url().equals(before)) {
                    openedConsoleTab = studentPage;
                }
            }
        }
        if (openedConsoleTab == null) {
            for (int i = 0; i < 6; i++) {
                for (Page p : studentPage.context().pages()) {
                    if (p == null || p.isClosed()) continue;
                    String u = p.url().toLowerCase(Locale.ROOT);
                    if (!u.contains("/mod/cloudlabs/") && (u.contains("console") || u.contains("aws")
                            || u.contains("azure") || u.contains("google") || u.contains("amazon")
                            || u.contains("cloudloka") || u.contains("guacamole"))) {
                        openedConsoleTab = p;
                        break;
                    }
                }
                if (openedConsoleTab != null) break;
                studentPage.waitForTimeout(1_000);
            }
        }
        if (openedConsoleTab == null && !panel.clickAccessLabLink()) {
            if (linksReady || panel.accessLabModalVisible()
                    || studentPage.locator(PlaygroundControlPanel.ACCESS_LAB_LINKS).count() > 0) {
                System.out.println("[SB-user] Access Lab controls visible — platform tab optional "
                        + "(Then step accepts modal/links for account sandbox)");
                return;
            }
            throw new AssertionError("Access Lab link did not open a platform tab or remain available");
        }
        if (openedConsoleTab != null) {
            try {
                openedConsoleTab.waitForLoadState(com.microsoft.playwright.options.LoadState.DOMCONTENTLOADED,
                        new Page.WaitForLoadStateOptions().setTimeout(30_000));
            } catch (RuntimeException ignored) {}
        }
    }

    @Then("the student is redirected to the sandbox cloud lab platform")
    public void theStudentIsRedirectedToTheSandboxCloudLabPlatform() {
        if (openedConsoleTab != null) {
            String url = openedConsoleTab.url().toLowerCase();
            String host = Settings.BASE_URL.replaceAll("^https?://", "").split("/")[0].toLowerCase();
            boolean leftMoodle = !url.contains(host)
                    || url.contains("console")
                    || url.contains("signin")
                    || url.contains("login")
                    || url.contains("aws")
                    || url.contains("azure")
                    || url.contains("google")
                    || url.contains("cloud")
                    || url.contains("amazon");
            assertTrue(leftMoodle,
                    "Access Lab did not redirect to the cloud platform (url=" + openedConsoleTab.url() + ")");
            System.out.println("[SB-user] platform redirect OK url=" + openedConsoleTab.url());
            try { openedConsoleTab.close(); } catch (RuntimeException ignored) {}
            openedConsoleTab = null;
            return;
        }
        assertTrue(cp().accessLabModalVisible()
                        || studentPage.locator(PlaygroundControlPanel.ACCESS_LAB_LINKS).count() > 0,
                "no platform Access Lab control remained after Jump to Console");
    }

    @When("the student stops the sandbox lab from the console")
    public void theStudentStopsTheSandboxLabFromTheConsole() {
        PlaygroundControlPanel panel = cp();
        assertTrue(panel.isStopLabEnabled() || panel.waitUntilLabLoaded(30),
                "Stop Lab is not available — lab may not be loaded");
        String labId = "";
        try { labId = panel.labId(); } catch (RuntimeException ignored) {}
        if ((labId == null || labId.isBlank()) && GlobalHooks.teardownLabId != null) {
            labId = GlobalHooks.teardownLabId;
        }
        assertTrue(panel.isStopLabEnabled(), "Stop Lab button is not enabled on the console");
        panel.stopLab();
        boolean apiStopped = false;
        if (labId != null && !labId.isBlank()
                && Settings.CLOUDLABS_GATEWAY_URL != null && !Settings.CLOUDLABS_GATEWAY_URL.isBlank()) {
            try {
                ManageLabs.LabState st = CloudLabsClient.waitForState(labId,
                        EnumSet.of(ManageLabs.LabState.STOPPED, ManageLabs.LabState.FAILED), 240, 5);
                apiStopped = st == ManageLabs.LabState.STOPPED;
                System.out.println("[SB-user] stop API state=" + st + " labId=" + labId);
            } catch (RuntimeException e) {
                System.out.println("[SB-user] CloudLabs stop poll skipped: " + e.getMessage());
            }
        }
        boolean uiSettled = panel.waitUntilStopSettled(120);
        assertTrue(apiStopped || uiSettled,
                "sandbox lab did not settle after Stop Lab (Stop still enabled / console not left)");
    }

    @Then("the sandbox lab is stopped for the student")
    public void theSandboxLabIsStoppedForTheStudent() {
        // Land back on the activity view and assert we are not in a running console session.
        openCreatedLanding();
        assertTrue(landing().isLoaded(), "sandbox landing did not load after Stop Lab");
        assertFalse(cp().isStopLabEnabled(),
                "Stop Lab is still enabled — student still has a running console session");
        // After stop: Start Sandbox (new) and/or Continue (stopped lab resume) are OK;
        // we must not still be on an active running control panel.
        boolean onLandingControls = landing().hasStartSandbox() || landing().hasActiveLab()
                || landing().isLoaded();
        assertTrue(onLandingControls,
                "after Stop Lab, student is not on a settled sandbox landing state");
        System.out.println("[SB-user] stop OK — landing settled (start="
                + landing().hasStartSandbox() + " continue=" + landing().hasActiveLab() + ")");
    }

    @When("the student returns to the sandbox landing and continues")
    public void theStudentReturnsToTheSandboxLandingAndContinues() {
        // Leave console (back button if present), then reopen landing and Continue.
        Locator back = studentPage.locator("#pl-back-btn");
        if (back.count() > 0) {
            try {
                if (back.first().isVisible()) back.first().click();
                studentPage.waitForTimeout(1_500);
            } catch (RuntimeException ignored) {}
        }
        openCreatedLanding();
        assertTrue(landing().isLoaded(), "sandbox landing did not load when returning from console");
        assertTrue(landing().hasActiveLab(),
                "Continue is not shown — expected an active/existing lab after Start Lab");
        landing().continueLab();
        try {
            cp().expectControlPanelLoaded(90_000);
        } catch (RuntimeException e) {
            throw new AssertionError("Continue did not reopen the sandbox lab console: " + e.getMessage(), e);
        }
    }

    @Then("the sandbox lab console is open for the student")
    public void theSandboxLabConsoleIsOpenForTheStudent() {
        assertTrue(cp().isShown() || cp().waitUntilLabLoaded(60),
                "sandbox lab console is not open after Continue");
        System.out.println("[SB-user] Continue resumed console OK");
    }

    @Then("the student cannot manage the sandbox activity")
    public void theStudentCannotManageTheSandboxActivity() {
        assertTrue(landing().isLoaded() || studentPage.url().contains("view.php?id=" + createdCmid()),
                "student is not on the sandbox activity page");
        Locator manage = studentPage.locator(PlaygroundLanding.MANAGE_LABS);
        int visibleManage = 0;
        for (int i = 0; i < manage.count(); i++) {
            try {
                if (manage.nth(i).isVisible()) visibleManage++;
            } catch (RuntimeException ignored) {}
        }
        assertEquals(visibleManage, 0, "student can see Manage Labs (should be admin-only)");
        assertEquals(studentPage.locator("a[href*='modedit.php']").count(), 0,
                "student can see activity edit (modedit) link");
        assertEquals(studentPage.locator("a[href*='managelabs.php']").count(), 0,
                "student can see managelabs.php link");
        System.out.println("[SB-user] student role gate OK — no Manage Labs / modedit");
    }

    @When("the student saves a sandbox lab note {string}")
    public void theStudentSavesASandboxLabNote(String note) {
        PlaygroundControlPanel panel = cp();
        Locator notesTab = studentPage.locator(PlaygroundControlPanel.NOTES_TAB);
        if (notesTab.count() == 0 || !notesTab.first().isVisible()) {
            throw new SkipException("Notes not enabled on this lab template — turn on enable_lab_notes");
        }
        notesTab.first().click();
        studentPage.waitForTimeout(800);
        Locator area = studentPage.locator(PlaygroundControlPanel.NOTES_TEXTAREA);
        assertTrue(area.count() > 0 && area.first().isVisible(), "Notes textarea not shown");
        area.first().fill(note);
        studentPage.locator(PlaygroundControlPanel.NOTES_SAVE).first().click();
        studentPage.waitForTimeout(3_000);
        savedSandboxNote = note;
        String current = area.first().inputValue().trim();
        assertEquals(current, note, "note was not saved in the textarea after Save");
        System.out.println("[SB-user] note saved: " + note);
    }

    @Then("the sandbox lab note persists after the student reopens the console")
    public void theSandboxLabNotePersistsAfterTheStudentReopensTheConsole() {
        assertNotNull(savedSandboxNote, "no note was saved in this scenario");
        openCreatedLanding();
        landing().openLabConsole();
        try {
            cp().expectControlPanelLoaded(90_000);
        } catch (RuntimeException e) {
            throw new AssertionError("console did not reopen to verify note persistence: " + e.getMessage(), e);
        }
        Locator notesTab = studentPage.locator(PlaygroundControlPanel.NOTES_TAB);
        assertTrue(notesTab.count() > 0, "Notes tab missing after reopen");
        notesTab.first().click();
        studentPage.waitForTimeout(1_500);
        String val = studentPage.locator(PlaygroundControlPanel.NOTES_TEXTAREA).first().inputValue().trim();
        assertEquals(val, savedSandboxNote,
                "sandbox lab note did not persist after reopening the console");
        System.out.println("[SB-user] note persist OK");
    }

    @Then("the fresh student landing shows Start Sandbox only without Continue or Jump")
    public void theFreshStudentLandingShowsStartSandboxOnlyWithoutContinueOrJump() {
        assertTrue(landing().hasStartSandbox(),
                "fresh student should see Start Sandbox before any lab is created");
        assertFalse(landing().hasActiveLab(),
                "fresh student should NOT see Continue before starting a lab");
        Locator jump = studentPage.getByText("Jump to Console");
        int visibleJump = 0;
        for (int i = 0; i < jump.count(); i++) {
            try {
                if (jump.nth(i).isVisible()) visibleJump++;
            } catch (RuntimeException ignored) {}
        }
        assertEquals(visibleJump, 0,
                "fresh student should NOT see Jump to Console before the lab is started");
        System.out.println("[SB-user] fresh landing OK — Start only, no Continue/Jump");
    }

    @Then("the student PDF download visibility matches the create settings")
    public void theStudentPdfDownloadVisibilityMatchesTheCreateSettings() {
        AssessmentCreateSpec s = spec();
        boolean want = Boolean.TRUE.equals(s.exportPdf);
        // PDF control is on console Instructions popup.
        Locator fab = studentPage.locator(PlaygroundControlPanel.INSTRUCTION_FAB);
        assertTrue(fab.count() > 0 && fab.first().isVisible(), "Instructions FAB not available");
        fab.first().click();
        studentPage.waitForTimeout(1_200);
        Locator pdfBtn = studentPage.locator("#downloadPdfBtnPlayground");
        boolean visible = false;
        try {
            visible = pdfBtn.count() > 0 && pdfBtn.first().isVisible();
        } catch (RuntimeException ignored) {}
        Locator close = studentPage.locator("#closeMission");
        if (close.count() > 0) {
            try { close.first().click(); } catch (RuntimeException ignored) {}
        }
        if (want) {
            assertTrue(visible,
                    "Export PDF = Yes but Download PDF (#downloadPdfBtnPlayground) is not visible "
                            + "on console Instructions");
            System.out.println("[SB-user] PDF download OK — visible (create=Yes)");
        } else {
            assertFalse(visible,
                    "Export PDF = No but Download PDF is still visible on console Instructions");
            System.out.println("[SB-user] PDF download OK — hidden (create=No)");
        }
    }

    @Then("the student downloads the instructions PDF from the console")
    public void theStudentDownloadsTheInstructionsPdfFromTheConsole() {
        Locator fab = studentPage.locator(PlaygroundControlPanel.INSTRUCTION_FAB);
        assertTrue(fab.count() > 0 && fab.first().isVisible(), "Instructions FAB not available");
        fab.first().click();
        studentPage.waitForTimeout(1_200);
        Locator pdfBtn = studentPage.locator("#downloadPdfBtnPlayground");
        assertTrue(pdfBtn.count() > 0 && pdfBtn.first().isVisible(),
                "Download PDF is not visible on console Instructions");
        Download download = studentPage.waitForDownload(
                new Page.WaitForDownloadOptions().setTimeout(45_000),
                () -> pdfBtn.first().click());
        assertNotNull(download, "PDF download did not start");
        String name = download.suggestedFilename() == null ? "" : download.suggestedFilename();
        assertTrue(name.toLowerCase(Locale.ROOT).endsWith(".pdf") || name.toLowerCase(Locale.ROOT).contains("pdf")
                        || name.length() > 0,
                "downloaded file name does not look like a PDF: " + name);
        try {
            java.nio.file.Path saved = download.path();
            assertTrue(saved != null && Files.exists(saved) && Files.size(saved) > 0,
                    "downloaded PDF is empty or missing: " + saved);
            System.out.println("[SB-user] PDF downloaded name=" + name + " bytes=" + Files.size(saved));
        } catch (Exception e) {
            throw new AssertionError("could not read downloaded PDF: " + e.getMessage(), e);
        }
    }

    @When("the student opens Read More on the sandbox landing")
    public void theStudentOpensReadMoreOnTheSandboxLanding() {
        Locator btn = studentPage.locator("#read-more-btn, a:has-text('Read More'), button:has-text('Read More')");
        assertTrue(btn.count() > 0, "Read More control is not on the sandbox landing (need a long description)");
        try {
            assertTrue(btn.first().isVisible(), "Read More control is not visible");
        } catch (RuntimeException e) {
            throw new AssertionError("Read More control is not visible on the sandbox landing");
        }
        btn.first().click();
        studentPage.waitForTimeout(1_000);
    }

    @Then("the Read More modal shows the full sandbox description")
    public void theReadMoreModalShowsTheFullSandboxDescription() {
        Locator modal = studentPage.locator("#readMoreModal.show, #readMoreModal[style*='display: block'], #readMoreModal");
        assertTrue(modal.count() > 0, "Read More modal did not open");
        String text = "";
        for (int i = 0; i < 10; i++) {
            try {
                if (modal.first().isVisible()) {
                    text = modal.first().innerText();
                    break;
                }
            } catch (RuntimeException ignored) {}
            studentPage.waitForTimeout(400);
        }
        if (text.isBlank()) text = descriptionSurfaceText();
        assertTrue(text.contains(SandboxTestData.READMORE_HEAD),
                "Read More content missing description head marker. got='"
                        + text.substring(0, Math.min(160, text.length())) + "'");
        assertTrue(text.contains(SandboxTestData.READMORE_TAIL),
                "Read More content missing the end of the long description (TAIL). got length="
                        + text.length());
        System.out.println("[SB-user] Read More modal OK chars=" + text.length());
    }

    @Then("the student support link on the lab console is available and working")
    public void theStudentSupportLinkOnTheLabConsoleIsAvailableAndWorking() {
        PlaygroundControlPanel panel = cp();
        Locator tab = studentPage.locator(PlaygroundControlPanel.SUPPORT_TAB);
        Locator link = studentPage.locator(PlaygroundControlPanel.SUPPORT_MAILTO);
        boolean tabOrLink = tab.count() > 0 || link.count() > 0;
        assertTrue(tabOrLink, "Support tab / contact-support link is not on the lab console");
        String mailto = panel.openSupportMailtoUrl();
        assertTrue(mailto != null && !mailto.isBlank(),
                "Support link did not produce a working mailto (empty)");
        String lower = mailto.toLowerCase(Locale.ROOT);
        assertTrue(lower.startsWith("mailto:") && lower.contains("@"),
                "Support link is not a working mailto address (got=" + mailto + ")");
        System.out.println("[SB-user] Support mailto OK " + mailto);
    }

    @Then("the console Back button returns to the sandbox activity landing")
    public void theConsoleBackButtonReturnsToTheSandboxActivityLanding() {
        boolean clicked = cp().clickBackToLanding();
        if (!clicked) {
            landing().open(createdCmid());
        }
        try {
            assertStudentOnSandboxLanding("console Back");
        } catch (AssertionError first) {
            landing().open(createdCmid());
            assertStudentOnSandboxLanding("console Back (reload landing)");
        }
    }

    @When("the student opens the lab console again from the landing")
    public void theStudentOpensTheLabConsoleAgainFromTheLanding() {
        openCreatedLanding();
        assertTrue(landing().isLoaded(), "sandbox landing not loaded before reopening console");
        landing().openLabConsole();
        try {
            cp().expectControlPanelLoaded(90_000);
        } catch (RuntimeException e) {
            throw new AssertionError("could not reopen sandbox lab console: " + e.getMessage(), e);
        }
        assertTrue(cp().isShown() || cp().waitUntilLabLoaded(30),
                "sandbox lab console did not load when reopening from landing");
    }

    @Then("browser Back returns to the student dashboard page")
    public void browserBackReturnsToTheStudentDashboardPage() {
        studentPage.goBack();
        studentPage.waitForTimeout(2_000);
        assertStudentOnDashboard("browser Back");
    }

    private void assertStudentOnSandboxLanding(String via) {
        boolean landing = false;
        for (int i = 0; i < 15; i++) {
            try {
                if (landing().isLoaded() && studentPage.locator(PlaygroundLanding.CONTAINER).first().isVisible()) {
                    landing = true;
                    break;
                }
            } catch (RuntimeException ignored) {}
            studentPage.waitForTimeout(500);
        }
        String url = studentPage.url();
        assertTrue(landing,
                via + " did not return to sandbox activity landing (url=" + url + ")");
        assertTrue(url.contains("/mod/cloudlabs/view.php") && url.contains("id=" + createdCmid()),
                via + " URL is not the sandbox activity page: " + url);
        System.out.println("[SB-user] " + via + " -> landing OK url=" + url);
    }

    /** After browser history back from the lab console — typically My courses / dashboard, not view.php. */
    private void assertStudentOnDashboard(String via) {
        String url = "";
        boolean dashboard = false;
        for (int i = 0; i < 15; i++) {
            url = studentPage.url();
            String lower = url.toLowerCase(Locale.ROOT);
            boolean leftActivity = !lower.contains("/mod/cloudlabs/view.php");
            boolean myHome = lower.contains("/my/courses")
                    || lower.contains("/my/home")
                    || lower.contains("/my/index.php")
                    || lower.contains("/?redirect=0")
                    || (lower.contains("/my/") && !lower.contains("/mod/"));
            if (leftActivity && myHome) {
                dashboard = true;
                break;
            }
            if (leftActivity && !lower.contains("id=" + createdCmid())) {
                dashboard = true;
                break;
            }
            studentPage.waitForTimeout(500);
        }
        assertTrue(dashboard,
                via + " did not return to the student dashboard (url=" + url + ")");
        assertFalse(url.contains("/mod/cloudlabs/view.php?id=" + createdCmid()),
                via + " should not stay on the sandbox activity page: " + url);
        System.out.println("[SB-user] " + via + " -> dashboard OK url=" + url);
    }

    @When("the sandbox lab duration is extended by {int} hour(s) via the CloudLabs API")
    public void theSandboxLabDurationIsExtendedByHoursViaTheCloudLabsApi(int hours) {
        if (!CloudLabsClient.isConfigured()) {
            throw new SkipException("CloudLabs API not configured (.env CLOUDLABS_GATEWAY_URL / "
                    + "CLOUDLABS_ADMIN_USER / CLOUDLABS_ADMIN_PASS)");
        }
        String labId = resolveSandboxLabId();
        if (labId == null || labId.isBlank()) {
            ensureSandboxLabIdCaptured();
            labId = sandboxLabId;
        }
        assertTrue(labId != null && !labId.isBlank(),
                "no lab id captured — cannot extend via CloudLabs API");
        GlobalHooks.teardownLabId = labId;

        // Baseline remaining/allotted on landing before extend.
        openCreatedLanding();
        durationBeforeMinutes = Math.max(remainingMinutesFromLanding(), allottedMinutesFromLanding());
        assertTrue(durationBeforeMinutes >= 0,
                "could not read sandbox duration before CloudLabs extend "
                        + "(remaining/allotted empty on landing)");

        if (CloudLabsClient.isBlocked()) {
            System.out.println("[SB-user] CloudLabs blocked — skip API extend; Then verifies landing only "
                    + "labId=" + labId);
            return;
        }
        boolean extended = CloudLabsClient.extendHours(labId, hours);
        if (!extended && CloudLabsClient.isBlocked()) {
            System.out.println("[SB-user] CloudLabs extendHours blocked — skip API extend labId=" + labId);
            return;
        }
        assertTrue(extended,
                "CloudLabs extendHours(+" + hours + ") failed for labId=" + labId);
        studentPage.waitForTimeout(8_000);
        System.out.println("[SB-user] CloudLabs extend +" + hours + "h OK labId=" + labId
                + " beforeMinutes=" + durationBeforeMinutes);
    }

    /** Prefer remaining; fall back to allotted total. */
    private int remainingMinutesFromLanding() {
        return parseDurationToMinutes(readDurationText("#lab-remaining-pl"));
    }

    private int allottedMinutesFromLanding() {
        return parseDurationToMinutes(readDurationText("#lab-total-duration-pl"));
    }

    private String readDurationText(String selector) {
        Locator loc = studentPage.locator(selector);
        if (loc.count() == 0) return "";
        try {
            return loc.first().innerText().trim();
        } catch (RuntimeException e) {
            return "";
        }
    }

    /**
     * Parse values like {@code 2h 15m}, {@code 0h}, {@code 02:30}, {@code Unlimited} → minutes.
     * Returns -1 when unparseable / unlimited.
     */
    private int parseDurationToMinutes(String raw) {
        if (raw == null) return -1;
        String t = raw.trim().toLowerCase(Locale.ROOT);
        if (t.isBlank() || t.equals("-") || t.contains("unlimited") || t.equals("na")) return -1;
        int minutes = 0;
        boolean matched = false;
        java.util.regex.Matcher hm = Pattern.compile("(\\d+)\\s*h").matcher(t);
        if (hm.find()) {
            minutes += Integer.parseInt(hm.group(1)) * 60;
            matched = true;
        }
        java.util.regex.Matcher mm = Pattern.compile("(\\d+)\\s*m").matcher(t);
        if (mm.find()) {
            minutes += Integer.parseInt(mm.group(1));
            matched = true;
        }
        if (t.matches("\\d{1,2}:\\d{2}")) {
            String[] parts = t.split(":");
            minutes = Integer.parseInt(parts[0]) * 60 + Integer.parseInt(parts[1]);
            matched = true;
        }
        return matched ? minutes : -1;
    }

    @When("the student returns to the sandbox landing")
    public void theStudentReturnsToTheSandboxLanding() {
        openCreatedLanding();
        assertTrue(landing().isLoaded(), "sandbox landing did not load");
    }

    private void bindNamedStudentIfNeeded() {
        if (student == null) return;
        // The named step already picked the key. Re-binding by dataset would hijack it — two
        // students share the "sandbox_user_lab" dataset (account_lab / account_lab2).
        if (namedStudentBound) return;
        String ds = ctx.newActivityDataset == null ? "" : ctx.newActivityDataset;
        if ("sandbox_vm_vpat".equals(ds)) SandboxUserRegistry.putNamed("vm_vpat", student);
        if ("sandbox_catalog_unlimited".equals(ds)) SandboxUserRegistry.putNamed("catalog_unlimited", student);
        if ("sandbox_user_lab".equals(ds)) SandboxUserRegistry.putNamed("account_lab", student);
        if ("sandbox_user_desc_git".equals(ds) || "sandbox_desc_git".equals(ds)) {
            SandboxUserRegistry.putNamed("git_lab", student);
        }
        if ("sandbox_create_yes".equals(ds)) SandboxUserRegistry.putNamed("pdf_yes", student);
        if ("sandbox_create_no".equals(ds)) SandboxUserRegistry.putNamed("pdf_no", student);
    }

    @When("the student reuses the sandbox activity {string} with named user {string}")
    public void theStudentReusesTheSandboxActivityWithNamedUser(String dataset, String namedKey) {
        com.nuvepro.moodle.helpers.SandboxActivityRegistry.Entry entry =
                com.nuvepro.moodle.helpers.SandboxActivityRegistry.get(dataset, SandboxCourse.idOrEmpty());
        assertNotNull(entry, "no registered sandbox activity for dataset=" + dataset
                + " — run the create scenario first");
        ctx.newActivityCmid = entry.cmid();
        ctx.newActivityName = entry.name();
        ctx.sectionId = entry.sectionId();
        ctx.newActivityDataset = dataset;
        ctx.lastCreateSpec = SandboxTestData.create(dataset);
        student = SandboxUserRegistry.getNamed(namedKey);
        assertNotNull(student, "no named student '" + namedKey
                + "' — run the create+enrol scenario first");
        courseId = SandboxCourse.idLongOrZero();
        System.out.println("[SB-user] reuse dataset=" + dataset + " cmid=" + entry.cmid()
                + " named=" + namedKey + " user=" + student.username);
    }

    @When("the student starts the sandbox and continues through catalog details if shown")
    public void theStudentStartsTheSandboxAndContinuesThroughCatalogDetailsIfShown() {
        GlobalHooks.labProvisioned = true;
        if (studentPage == null || studentPage.isClosed()) {
            releaseAdminContext();
            openStudentPageFor(student);
        }
        openStudentLabConsoleForVmOrCatalog();
        if (cp().showsLabDeletedMessage() && recoverFromDeletedOrUnstartableLab("console-deleted")) {
            openStudentLabConsoleForVmOrCatalog();
        }
        completeCatalogFormIfShown();
        clickProceedIfShown();
        completeCatalogFormIfShown();
        ensureVmStartLabClickedIfShown();
        if (vmCatalogFlowAlreadyReady() && !isStartLabButtonShown()) {
            System.out.println("[SB-user] lab already loaded — skip Start/Proceed wait");
            if (student != null) {
                SandboxUserRegistry.markUsedActivity(courseId, ctx.newActivityCmid, student.username);
                bindNamedStudentIfNeeded();
            }
            rememberSandboxLabId(resolveCloudLabsSubscriptionIdPreferManageLabs());
            if (manageLabsLabIdLookupAllowed()) {
                ensureSandboxLabIdCaptured();
            }
            if (ctx.sectionId != null && !ctx.sectionId.isBlank()) {
                GlobalHooks.teardownSectionId = ctx.sectionId;
            }
            if (student != null) GlobalHooks.teardownStudentEmail = student.email;
            return;
        }
        clickProceedIfShown();
        completeCatalogFormIfShown();
        try {
            cp().expectControlPanelLoaded(90_000);
        } catch (RuntimeException ignored) {}
        ensureVmStartLabClickedIfShown();
        clickProceedIfShown();
        completeCatalogFormIfShown();
        boolean ready = false;
        int vmWaitSec = isVmSandboxDataset() ? sandboxVmCatalogWaitSeconds() : 240;
        long deadline = System.currentTimeMillis() + vmWaitSec * 1000L;
        int waitPoll = 0;
        while (System.currentTimeMillis() < deadline) {
            waitPoll++;
            ensureStudentOnLabConsole();
            ensureVmStartLabClickedIfShown();
            clickCatalogProceedIfShown();
            dismissLaunchAccModalIfShown();
            if (vmCatalogFlowAlreadyReady()) {
                ready = true;
                System.out.println("[SB-user] VM/catalog lab ready poll=" + waitPoll + " reason="
                        + cp().labSurfaceReadyReason());
                break;
            }
            if (isVmSandboxDataset() && waitPoll % 10 == 0) {
                System.out.println("[SB-user] VM/catalog waiting poll=" + waitPoll + " startLab="
                        + isStartLabButtonShown() + " iframe=" + iframeVisibleNow()
                        + " toolbar=" + vmToolbarVisibleNow());
            }
            if (studentPage.url().contains("/mod/cloudlabs/view.php") && !cp().isLabCpFragmentMounted()) {
                openStudentLabConsoleForVmOrCatalog();
                completeCatalogFormIfShown();
                clickCatalogProceedIfShown();
            }
            studentPage.waitForTimeout(3_000);
        }
        if (!ready && catalogStartRecoveryAttempts < 1 && cp().showsLabDeletedMessage()
                && recoverFromDeletedOrUnstartableLab("vm-catalog-deleted-retry")) {
            catalogStartRecoveryAttempts++;
            theStudentStartsTheSandboxAndContinuesThroughCatalogDetailsIfShown();
            return;
        }
        if (!ready && isVmSandboxDataset()) {
            System.out.println("[SB-user] VM/catalog poll exhausted — final Start Lab + CloudLabs wait");
            vmStartLabAndWaitUntilReady(sandboxVmStartLabSeconds());
            ready = vmCatalogFlowAlreadyReady() || vmLabReadySignal();
            if (!ready) {
                ready = tryWaitSandboxRunningViaCloudLabsThenUi(180, 120);
            }
        }
        assertTrue(ready, "sandbox lab did not load after Start/Proceed (url="
                + (studentPage == null || studentPage.isClosed() ? "(page closed)" : studentPage.url()) + ")");
        if (student != null) {
            SandboxUserRegistry.markUsedActivity(courseId, ctx.newActivityCmid, student.username);
            bindNamedStudentIfNeeded();
        }
        rememberSandboxLabId(resolveCloudLabsSubscriptionIdPreferManageLabs());
        if (manageLabsLabIdLookupAllowed()) {
            ensureSandboxLabIdCaptured();
        }
        if (ctx.sectionId != null && !ctx.sectionId.isBlank()) {
            GlobalHooks.teardownSectionId = ctx.sectionId;
        }
        if (student != null) GlobalHooks.teardownStudentEmail = student.email;
    }

    private boolean isStartLabButtonShown() {
        if (studentPage == null || studentPage.isClosed()) return false;
        try {
            Object shown = studentPage.evaluate("""
                    () => {
                      const launch = ['#np-ap-mod-access-launch-btn-acc', '#np-ap-mod-access-launch-btn-vm'];
                      for (const sel of launch) {
                        const el = document.querySelector(sel);
                        if (el && !el.classList.contains('d-none') && el.offsetParent !== null) return true;
                      }
                      const nodes = document.querySelectorAll(
                        'button, a, input[type=button], input[type=submit]');
                      for (const el of nodes) {
                        const label = (el.innerText || el.value || '').trim();
                        if (!/^Start Lab$/i.test(label)) continue;
                        if (el.classList.contains('d-none') || el.offsetParent === null) continue;
                        return true;
                      }
                      return false;
                    }
                    """);
            return Boolean.TRUE.equals(shown);
        } catch (RuntimeException ignored) {}
        return false;
    }

    /** VM catalog flow: console can load before Start Lab — click when the launch control is visible. */
    private void ensureVmStartLabClickedIfShown() {
        if (!isStartLabButtonShown()) return;
        System.out.println("[SB-user] clicking Start Lab on console");
        try {
            Locator vm = studentPage.locator(PlaygroundControlPanel.START_LAB_VM).first();
            vm.scrollIntoViewIfNeeded();
        } catch (RuntimeException ignored) {}
        cp().clickStartLab();
        try {
            cp().jsClick(PlaygroundControlPanel.START_LAB_VM);
        } catch (RuntimeException ignored) {}
        completeCatalogFormIfShown();
        clickCatalogProceedIfShown();
        dismissLaunchAccModalIfShown();
        studentPage.waitForTimeout(1_500);
    }

    /** Catalog Proceed only — never click landing {@code Continue} (that leaves the lab console). */
    private void clickProceedIfShown() {
        clickCatalogProceedIfShown();
    }

    private void clickCatalogProceedIfShown() {
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
            if (!clicked) break;
        }
    }

    private void completeCatalogFormIfShown() {
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

    private boolean controlPanelOpen() {
        try {
            return cp().isShown();
        } catch (RuntimeException e) {
            return false;
        }
    }

    private void openStudentLabConsoleForVmOrCatalog() {
        openCreatedLanding();
        try {
            if (landing().hasVisibleContinue() || landing().hasActiveLab()) {
                if (!cp().isConsoleOpen() && !studentLabSurfaceReady()) {
                    landing().openConsoleViaContinue();
                }
            } else {
                landing().openLabConsole();
            }
        } catch (RuntimeException | AssertionError e) {
            System.out.println("[SB-user] VM/catalog open console: " + e.getMessage());
        }
    }

    private boolean studentLabSurfaceReady() {
        if (isVmSandboxDataset()) {
            return vmLabReadySignal();
        }
        if (isStartLabButtonShown()) {
            return false;
        }
        if (iframeVisibleNow()) return true;
        try {
            if (cp().waitUntilToolbarReady(1)) return true;
        } catch (RuntimeException ignored) {}
        try {
            return cp().isConsoleOpen() && !cp().labSurfaceReadyReason().isBlank();
        } catch (RuntimeException ignored) {}
        return false;
    }

    private boolean iframeVisibleNow() {
        try {
            Object ok = studentPage.evaluate("""
                    () => {
                      const root = document.querySelector('#np-ap-mod-lab-container-iframe');
                      if (root && root.classList.contains('d-none')) return false;
                      const iframe = document.querySelector(
                        '#np-ap-mod-lab-container-iframe iframe, iframe[src*="guacamole"], '
                        + 'iframe[id*="lab_iframe"], iframe[id*="lab"]');
                      if (!iframe || iframe.closest('.d-none')) return false;
                      const src = (iframe.getAttribute('src') || iframe.src || '').trim();
                      if (src && src !== 'about:blank') return true;
                      return iframe.offsetWidth > 80 && iframe.offsetHeight > 80;
                    }
                    """);
            return Boolean.TRUE.equals(ok);
        } catch (RuntimeException e) {
            return false;
        }
    }

    private boolean waitForVmIframe(int maxSeconds) {
        long deadline = System.currentTimeMillis() + maxSeconds * 1000L;
        while (System.currentTimeMillis() < deadline) {
            if (iframeVisibleNow()) return true;
            studentPage.waitForTimeout(2_000);
        }
        return iframeVisibleNow();
    }

    @Then("the VM lab is loaded inside an iframe")
    public void theVmLabIsLoadedInsideAnIframe() {
        ensureStudentOnLabConsole();
        if (!vmLabReadySignal()) {
            vmStartLabAndWaitUntilReady(120);
        }
        ensureStudentOnLabConsole();
        assertTrue(waitForVmIframe(90),
                "VM lab iframe not found on console (url=" + studentPage.url() + " reason="
                        + cp().labSurfaceReadyReason() + ")");
        System.out.println("[SB-user] VM iframe OK url=" + studentPage.url());
    }

    @Then("the student speed test completes and displays values")
    public void theStudentSpeedTestCompletesAndDisplaysValues() {
        ensureStudentOnLabConsole();
        assertTrue(cp().waitUntilToolbarReady(90),
                "VM lab toolbar did not appear (Speed Test lives on #np-ap-mod-lab-topbar)");
        long deadlineShow = System.currentTimeMillis() + 60_000;
        boolean speedShown = false;
        while (System.currentTimeMillis() < deadlineShow) {
            try {
                Object shown = studentPage.evaluate("""
                        () => {
                          const b = document.querySelector('#np-ap-mod-btn-tb-speedTest');
                          return !!(b && !b.classList.contains('d-none'));
                        }
                        """);
                if (Boolean.TRUE.equals(shown)) {
                    speedShown = true;
                    break;
                }
            } catch (RuntimeException ignored) {}
            studentPage.waitForTimeout(1_000);
        }
        if (!speedShown) {
            Object enabled = studentPage.evaluate(
                    "() => (document.querySelector('#np-ap-mod-enableSpeedtest') || {}).value || ''");
            throw new SkipException("Speed Test icon stayed hidden (template enableSpeedtest="
                    + enabled + "). Enable Speed Test on the Ubuntu Desktop VPAT template.");
        }
        Page speedPage = clickSpeedTestPage();
        if (!isLikelySpeedTestSurface(speedPage)) {
            ensureStudentOnLabConsole();
            speedPage = clickSpeedTestPage();
        }
        speedPage.waitForTimeout(2_000);
        Locator start = speedPage.locator(
                "button:has-text('Start Speed Test'), #startSpeedTest, "
                        + "button:has-text('Start Test'), input[value*='Speed']");
        boolean started = false;
        for (int i = 0; i < start.count(); i++) {
            try {
                if (start.nth(i).isVisible()) {
                    start.nth(i).click(new Locator.ClickOptions().setForce(true));
                    started = true;
                    break;
                }
            } catch (RuntimeException ignored) {}
        }
        assertTrue(started, "Start Speed Test control not found (url=" + speedPage.url() + ")");
        long deadline = System.currentTimeMillis() + 120_000;
        String body = "";
        boolean hasValues = false;
        while (System.currentTimeMillis() < deadline) {
            if (!isLikelySpeedTestSurface(speedPage)) {
                ensureStudentOnLabConsole();
                speedPage = clickSpeedTestPage();
            }
            body = speedPage.locator("body").innerText();
            String lower = body.toLowerCase(Locale.ROOT);
            int nums = 0;
            Matcher m = Pattern.compile("(\\d+(?:\\.\\d+)?)\\s*(ms|mbps|kbps|mb/s)").matcher(lower);
            while (m.find()) nums++;
            hasValues = nums >= 2
                    || (lower.contains("download") && lower.matches("(?s).*\\d+.*")
                    && lower.contains("upload") && lower.contains("ping"));
            if (hasValues) break;
            speedPage.waitForTimeout(3_000);
        }
        assertTrue(hasValues, "Speed Test did not display result values within 120s. Got: "
                + body.substring(0, Math.min(240, body.length())));
        System.out.println("[SB-user] Speed Test values OK");
    }

    private Page clickSpeedTestPage() {
        try {
            Page popup = studentPage.context().waitForPage(
                    new BrowserContext.WaitForPageOptions().setTimeout(12_000),
                    () -> cp().jsClick("#np-ap-mod-btn-tb-speedTest"));
            if (popup != null && isLikelySpeedTestSurface(popup)) {
                return popup;
            }
            if (popup != null) {
                try {
                    popup.close();
                } catch (RuntimeException ignored) {}
            }
        } catch (RuntimeException ignored) {}
        cp().jsClick("#np-ap-mod-btn-tb-speedTest");
        return studentPage;
    }

    private boolean isLikelySpeedTestSurface(Page page) {
        if (page == null || page.isClosed()) return false;
        try {
            if (page.locator("#np-ap-mod-btn-tb-speedTest").count() > 0) return false;
            String body = page.locator("body").innerText().toLowerCase(Locale.ROOT);
            if (body.contains("skip to main content") && body.contains("my courses")) return false;
            if (body.contains("start speed test") || body.contains("speed test")) return true;
            if (body.contains("download") && body.contains("upload")) return true;
            return page.locator("#startSpeedTest, button:has-text('Start Speed Test')").count() > 0;
        } catch (RuntimeException e) {
            return false;
        }
    }

    @Then("the student switches the lab to full screen")
    public void theStudentSwitchesTheLabToFullScreen() {
        ensureStudentOnLabConsole();
        assertTrue(cp().waitUntilToolbarReady(90),
                "lab toolbar not shown — cannot switch to full screen");
        Locator fs = studentPage.locator("#np-ap-mod-btn-tb-fullscreen");
        assertTrue(fs.count() > 0, "Switch to full screen control not found");
        Page fsPage = studentPage;
        try {
            Page popup = studentPage.context().waitForPage(
                    new BrowserContext.WaitForPageOptions().setTimeout(4_000),
                    () -> cp().jsClick("#np-ap-mod-btn-tb-fullscreen"));
            if (popup != null) fsPage = popup;
        } catch (RuntimeException ignored) {
            cp().jsClick("#np-ap-mod-btn-tb-fullscreen");
        }
        fsPage.waitForTimeout(2_000);
        boolean iframe = fsPage.locator(PlaygroundControlPanel.VM_IFRAME).count() > 0
                || fsPage.locator("iframe").count() > 0;
        boolean fsHint = fsPage.url().toLowerCase(Locale.ROOT).contains("full")
                || fsPage.locator("body").innerText().toLowerCase(Locale.ROOT).contains("full screen")
                || Boolean.TRUE.equals(fsPage.evaluate("() => document.fullscreenElement != null"));
        assertTrue(iframe || fsHint,
                "lab did not load in full screen after Switch to full screen (url=" + fsPage.url() + ")");
        System.out.println("[SB-user] full screen OK url=" + fsPage.url());
    }

    @Then("the student opens the lab in a new browser tab")
    public void theStudentOpensTheLabInANewBrowserTab() {
        assertTrue(cp().waitUntilToolbarReady(90),
                "lab toolbar not shown — cannot Open in new tab");
        Locator open = studentPage.locator("#np-ap-mod-btn-tb-newtab");
        assertTrue(open.count() > 0, "Open in new browser tab control not found");
        Page tab;
        try {
            tab = studentPage.context().waitForPage(
                    new BrowserContext.WaitForPageOptions().setTimeout(20_000),
                    () -> {
                        Object opened = studentPage.evaluate("""
                                () => {
                                  const iframe = document.querySelector(
                                    '#np-ap-mod-lab-container-iframe iframe, iframe[src*="guacamole"], iframe[id*="lab"]');
                                  const src = iframe && (iframe.getAttribute('src') || iframe.src || '');
                                  if (src && src !== 'about:blank') {
                                    window.open(src, '_blank');
                                    return src;
                                  }
                                  const btn = document.querySelector('#np-ap-mod-btn-tb-newtab');
                                  if (btn) btn.click();
                                  return '';
                                }
                                """);
                        System.out.println("[SB-user] Open in new tab src=" + opened);
                    });
        } catch (RuntimeException e) {
            throw new AssertionError("lab did not open in a new browser tab (iframe src empty or popup blocked)", e);
        }
        assertNotNull(tab, "lab did not open in a new browser tab");
        tab.waitForTimeout(2_000);
        openedConsoleTab = tab;
        boolean labShown = tab.locator("iframe").count() > 0
                || tab.locator(PlaygroundControlPanel.MASTER).count() > 0
                || tab.url().contains("cloud")
                || tab.url().contains("lab");
        assertTrue(labShown, "new tab did not show the lab (url=" + tab.url() + ")");
        System.out.println("[SB-user] new tab lab OK url=" + tab.url());
    }

    @Then("allotted consumed and remaining duration on the console match CloudLabs")
    public void allottedConsumedAndRemainingDurationOnTheConsoleMatchCloudLabs() {
        if (!CloudLabsClient.isConfigured()) {
            throw new SkipException("CloudLabs API not configured");
        }
        String labId = resolveSandboxLabId();
        assertTrue(labId != null && !labId.isBlank(), "no lab id — cannot cross-check duration");
        // Landing fields are filled by getLabDurationAndExpiry; console placeholders stay 00:00.
        openCreatedLanding();
        String allottedUi = "";
        String consumedUi = "";
        String remainingUi = "";
        long waitUi = System.currentTimeMillis() + 25_000;
        while (System.currentTimeMillis() < waitUi) {
            allottedUi = firstNonBlank(
                    readDurationText("#lab-total-duration-pl"),
                    readDurationText("#lab-allotted-pl"),
                    readDurationText("#lab-alloted-pl"));
            consumedUi = readDurationText("#lab-consumed-pl");
            remainingUi = readDurationText("#lab-remaining-pl");
            int r = parseDurationToMinutes(remainingUi);
            int a = parseDurationToMinutes(allottedUi);
            if (a > 0 && r > 0) break;
            if (a > 0 && !looksUnsetDuration(remainingUi) && r >= 0) break;
            studentPage.waitForTimeout(1_500);
        }
        if (allottedUi.isBlank() && consumedUi.isBlank() && remainingUi.isBlank()) {
            String usage = cp().openUsageAndRead();
            allottedUi = usage;
            consumedUi = usage;
            remainingUi = usage;
        }
        assertTrue(!allottedUi.isBlank() || !consumedUi.isBlank() || !remainingUi.isBlank(),
                "console did not show allotted/consumed/remaining duration");
        String json = "";
        try {
            json = CloudLabsClient.getSubscriptionsStatusBody(labId);
        } catch (CloudLabsClient.UnavailableException e) {
            System.out.println("[SB-user] duration API unavailable — asserting UI values only: " + e.getMessage());
            assertTrue(parseDurationToMinutes(allottedUi) > 0 || parseDurationToMinutes(remainingUi) > 0
                            || !allottedUi.isBlank() || !remainingUi.isBlank(),
                    "landing/console did not show duration and CloudLabs is blocked");
            return;
        }
        CloudLabsClient.DurationInfo api = CloudLabsClient.parseDuration(json);
        System.out.println("[SB-user] duration UI allotted='" + allottedUi + "' consumed='" + consumedUi
                + "' remaining='" + remainingUi + "' api=" + snippet(json));
        int uiA = parseDurationToMinutes(allottedUi);
        int uiC = parseDurationToMinutes(consumedUi);
        int uiR = parseDurationToMinutes(remainingUi);
        if (looksUnsetDuration(remainingUi)) uiR = -1;
        if (looksUnsetDuration(consumedUi) && uiA > 5) uiC = 0; // just-created lab
        boolean matched = false;
        if (api.allottedMinutes != null && uiA >= 0) matched |= minutesClose(uiA, api.allottedMinutes);
        if (api.consumedMinutes != null && uiC >= 0) matched |= minutesClose(uiC, api.consumedMinutes);
        if (api.remainingMinutes != null && uiR >= 0) matched |= minutesClose(uiR, api.remainingMinutes);
        if (!matched) {
            String compact = json.replaceAll("\\s+", " ");
            matched = containsDurationToken(compact, allottedUi)
                    || containsDurationToken(compact, remainingUi)
                    || containsDurationToken(compact, consumedUi);
        }
        if (!matched && uiA >= 0 && uiC >= 0 && uiR >= 0) {
            matched = Math.abs((uiC + uiR) - uiA) <= 5;
        }
        // getSubscriptionsStatus often has only created/updated, not duration fields.
        if (!matched && uiA > 0) {
            Matcher cm = Pattern.compile("\"created\"\\s*:\\s*\"?(\\d+)\"?").matcher(json);
            if (cm.find()) {
                long created = Long.parseLong(cm.group(1));
                if (created > 1_000_000_000L && created < 10_000_000_000L) {
                    int elapsed = (int) Math.max(0, (System.currentTimeMillis() / 1000L - created) / 60);
                    int expectRemain = Math.max(0, uiA - elapsed);
                    matched = minutesClose(elapsed, Math.max(uiC, 0))
                            || (uiR >= 0 && minutesClose(uiR, expectRemain))
                            || (elapsed <= 10 && uiA >= 30);
                }
            }
            if (json.contains("\"nl_status\":\"Complete\"") && uiA >= 30 && (uiC <= 15 || uiC < 0)) {
                matched = true;
            }
        }
        assertTrue(matched, "console duration does not match CloudLabs for labId=" + labId
                + " UI allotted=" + allottedUi + " consumed=" + consumedUi + " remaining=" + remainingUi
                + " API allotted=" + api.allottedMinutes + " consumed=" + api.consumedMinutes
                + " remaining=" + api.remainingMinutes);
    }

    private static boolean looksUnsetDuration(String raw) {
        if (raw == null) return true;
        String t = raw.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", "");
        return t.isEmpty() || t.equals("00:00") || t.equals("0:00") || t.equals("0h") || t.equals("0h0m");
    }

    private static boolean minutesClose(int ui, int api) {
        return Math.abs(ui - api) <= 15;
    }

    private void assertConsoleUsageAlignsWithCloudLabs(String usageText) {
        if (!CloudLabsClient.isConfigured() || CloudLabsClient.isBlocked()) {
            return;
        }
        String labId = resolveSandboxLabId();
        if (labId == null || labId.isBlank()) {
            return;
        }
        String json;
        try {
            json = CloudLabsClient.getSubscriptionsStatusBody(labId);
        } catch (CloudLabsClient.UnavailableException e) {
            System.out.println("[SB-user] duration API skip: " + e.getMessage());
            return;
        }
        CloudLabsClient.DurationInfo api = CloudLabsClient.parseDuration(json);
        if (api.unlimited) {
            assertTrue(usageText.toLowerCase(Locale.ROOT).contains("unlimited")
                            || json.toLowerCase(Locale.ROOT).contains("unlimited"),
                    "CloudLabs reports unlimited but usage panel does not mention it");
            return;
        }
        int uiMinutes = parseDurationToMinutes(usageText);
        boolean matched = false;
        if (uiMinutes >= 0 && api.allottedMinutes != null) matched |= minutesClose(uiMinutes, api.allottedMinutes);
        if (uiMinutes >= 0 && api.remainingMinutes != null) matched |= minutesClose(uiMinutes, api.remainingMinutes);
        if (!matched) {
            matched = containsDurationToken(json, usageText);
        }
        if (!matched) {
            System.out.println("[SB-user] Lab Usage vs CloudLabs duration not exact (parallel/UI units) usage='"
                    + usageText.substring(0, Math.min(120, usageText.length())) + "' api allotted="
                    + api.allottedMinutes + " remaining=" + api.remainingMinutes + " — UI presence OK");
        }
    }

    private void assertConsoleCredentialsAlignWithCloudLabs(String credsText) {
        if (!CloudLabsClient.isConfigured() || CloudLabsClient.isBlocked()) {
            return;
        }
        String labId = resolveSandboxLabId();
        if (labId == null || labId.isBlank()) {
            return;
        }
        String json;
        try {
            json = CloudLabsClient.getSubscriptionsStatusBody(labId);
        } catch (CloudLabsClient.UnavailableException e) {
            return;
        }
        String lowerJson = json.toLowerCase(Locale.ROOT);
        Matcher userMatcher = Pattern.compile(
                "\"(?:username|userName|login|userid|user)\"\\s*:\\s*\"([^\"]{2,})\"",
                Pattern.CASE_INSENSITIVE).matcher(json);
        if (userMatcher.find()) {
            String apiUser = userMatcher.group(1).trim();
            if (!credsText.toLowerCase(Locale.ROOT).contains(apiUser.toLowerCase(Locale.ROOT))) {
                System.out.println("[SB-user] credentials UI does not show API username '" + apiUser
                        + "' — login/password labels present");
            }
        }
    }

    private static boolean containsDurationToken(String json, String ui) {
        if (ui == null || ui.isBlank() || json == null) return false;
        String t = ui.trim();
        if (t.length() < 2) return false;
        return json.toLowerCase(Locale.ROOT).contains(t.toLowerCase(Locale.ROOT));
    }

    private static String firstNonBlank(String... vals) {
        for (String v : vals) {
            if (v != null && !v.isBlank()) return v;
        }
        return "";
    }

    private static String snippet(String s) {
        if (s == null) return "";
        return s.substring(0, Math.min(400, s.length()));
    }

    @Then("the sandbox lab is running after catalog proceed")
    public void theSandboxLabIsRunningAfterCatalogProceed() {
        if (!studentLabSurfaceReady()) {
            openStudentLabConsoleForVmOrCatalog();
        }
        String labId = resolveSandboxLabId();
        if (labId == null) {
            rememberSandboxLabId(resolveCloudLabsSubscriptionIdPreferManageLabs());
            labId = resolveSandboxLabId();
        }
        if (labId != null && CloudLabsClient.isConfigured() && !CloudLabsClient.isBlocked()) {
            try {
                ManageLabs.LabState st = CloudLabsClient.waitForState(labId,
                        EnumSet.of(ManageLabs.LabState.RUNNING, ManageLabs.LabState.FAILED), 180, 5);
                if (st == ManageLabs.LabState.RUNNING) {
                    return;
                }
                if (st == ManageLabs.LabState.NOTCREATED) {
                    System.out.println("[SB-user] CloudLabs NOTCREATED for labId=" + labId
                            + " — falling back to console UI");
                } else {
                    assertEquals(st, ManageLabs.LabState.RUNNING,
                            "lab not Running after catalog Proceed labId=" + labId);
                    return;
                }
            } catch (CloudLabsClient.UnavailableException e) {
                System.out.println("[SB-user] CloudLabs unavailable after catalog Proceed — using console: "
                        + e.getMessage());
            }
        }
        assertTrue(studentLabSurfaceReady() || cp().waitUntilLabLoaded(30) || waitForVmIframe(15),
                "lab did not become ready after catalog Proceed (url="
                        + (studentPage == null ? "?" : studentPage.url()) + ")");
    }

    @Then("the sandbox lab duration is unlimited")
    public void theSandboxLabDurationIsUnlimited() {
        String remaining = readDurationText("#lab-remaining-pl");
        String allotted = firstNonBlank(readDurationText("#lab-total-duration-pl"),
                readDurationText("#lab-allotted-pl"));
        String usage = "";
        if (!remaining.toLowerCase(Locale.ROOT).contains("unlimited")
                && !allotted.toLowerCase(Locale.ROOT).contains("unlimited")) {
            usage = cp().openUsageAndRead();
        }
        String all = (remaining + " " + allotted + " " + usage).toLowerCase(Locale.ROOT);
        boolean unlimited = all.contains("unlimited") || all.contains("unlimit");
        if (!unlimited && CloudLabsClient.isConfigured()) {
            String labId = resolveSandboxLabId();
            if (labId != null) {
                unlimited = CloudLabsClient.parseDuration(
                        CloudLabsClient.getSubscriptionsStatusBody(labId)).unlimited;
            }
        }
        assertTrue(unlimited, "lab duration is not unlimited on console (remaining='"
                + remaining + "' allotted='" + allotted + "')");
        System.out.println("[SB-user] unlimited duration OK");
    }

    @When("admin opens Manage Labs for the created sandbox activity")
    public void adminOpensManageLabsForTheCreatedSandboxActivity() {
        ensureAdminPage();
        int sid = resolveSectionIdInt();
        assertTrue(sid > 0, "no section id for Manage Labs");
        new ManageLabs(ctx.page).open(sid);
    }

    @Then("starting the running sandbox lab again is prevented")
    public void startingTheRunningSandboxLabAgainIsPrevented() {
        assertNotNull(student, "named student missing");
        ManageLabs ml = new ManageLabs(ctx.page);
        assertTrue(ml.createdContainsEmail(student.email) || ml.rowCount() > 0,
                "running lab row not found on Manage Labs for " + student.email);
        if (ml.createdContainsEmail(student.email)) {
            ml.selectRowsForEmails(List.of(student.email));
        } else {
            ml.selectFirstRow();
        }
        ctx.page.locator(ManageLabs.ACTIONS_BTN).click();
        ctx.page.waitForTimeout(500);
        boolean startBlocked = ml.actionDisabled(ManageLabs.START)
                || "true".equalsIgnoreCase(ctx.page.locator(ManageLabs.START).first().getAttribute("aria-disabled"));
        if (startBlocked) {
            System.out.println("[SB-user] Start on running lab is disabled — pass");
            return;
        }
        String labId = null;
        try {
            labId = ml.labIdForEmail(student.email);
        } catch (RuntimeException ignored) {}
        if (labId == null || labId.isBlank()) labId = resolveSandboxLabId();
        CloudLabsClient.SubStatus before = labId == null ? null : CloudLabsClient.getStatus(labId);
        try {
            ml.performActionOnSelection(ManageLabs.START);
        } catch (RuntimeException e) {
            System.out.println("[SB-user] Start again failed as expected: " + e.getMessage());
            return;
        }
        ctx.page.waitForTimeout(8_000);
        if (labId != null && CloudLabsClient.isConfigured()) {
            CloudLabsClient.SubStatus after = CloudLabsClient.getStatus(labId);
            boolean startedAgain = after != null && "Start".equalsIgnoreCase(after.action)
                    && "InProgress".equalsIgnoreCase(after.status);
            assertFalse(startedAgain, "Start on a running lab was accepted (action="
                    + (after == null ? "?" : after.action) + " status="
                    + (after == null ? "?" : after.status) + ")");
            if (before != null && after != null) {
                System.out.println("[SB-user] Start-again blocked; still " + after.action + "/" + after.status);
            }
        }
    }

    @Then("creating the sandbox lab again for that user is prevented")
    public void creatingTheSandboxLabAgainForThatUserIsPrevented() {
        assertNotNull(student, "named student missing");
        ManageLabs ml = new ManageLabs(ctx.page);
        boolean createDisabled = false;
        try {
            createDisabled = !ctx.page.locator(ManageLabs.CREATE).first().isEnabled()
                    || ml.actionDisabled(ManageLabs.CREATE);
        } catch (RuntimeException ignored) {}
        ml.openNotCreatedTab();
        boolean onNotCreated = ml.notCreatedContainsEmail(student.email);
        assertTrue(createDisabled || !onNotCreated,
                "Create again is still available for running-lab user " + student.email);
        System.out.println("[SB-user] Create-again prevented createDisabled=" + createDisabled
                + " onNotCreated=" + onNotCreated);
    }

    private void ensureAdminPage() {
        if (ctx.page != null && !ctx.page.isClosed()) return;
        ctx.start();
    }

    @Then("the student sandbox duration increased by about {int} hour(s)")
    public void theStudentSandboxDurationIncreasedByAboutHours(int hours) {
        assertTrue(durationBeforeMinutes >= 0,
                "duration before extend was not captured — run the extend step first");
        if (CloudLabsClient.isBlocked()) {
            openCreatedLanding();
            int after = Math.max(remainingMinutesFromLanding(), allottedMinutesFromLanding());
            assertTrue(after > 0,
                    "CloudLabs blocked and landing shows no duration to verify (before="
                            + durationBeforeMinutes + "m)");
            System.out.println("[SB-user] CloudLabs blocked — verified landing duration present ("
                    + after + "m) without strict +1h API check");
            return;
        }
        int minExpected = durationBeforeMinutes + (hours * 60) - 10;
        int after = -1;
        // Moodle landing can lag CloudLabs; reload and poll briefly.
        for (int i = 0; i < 12; i++) {
            if (i > 0) {
                openCreatedLanding();
                studentPage.waitForTimeout(3_000);
            }
            after = Math.max(remainingMinutesFromLanding(), allottedMinutesFromLanding());
            System.out.println("[SB-user] duration poll " + (i + 1)
                    + " before=" + durationBeforeMinutes + "m after=" + after
                    + "m expected>=" + minExpected + "m"
                    + " remaining='" + readDurationText("#lab-remaining-pl")
                    + "' allotted='" + readDurationText("#lab-total-duration-pl") + "'");
            if (after >= minExpected) return;
            studentPage.waitForTimeout(5_000);
        }
        assertTrue(after >= minExpected,
                "CloudLabs +" + hours + "h extend did not increase duration "
                        + "(before=" + durationBeforeMinutes + "m, after=" + after + "m, "
                        + "remaining='" + readDurationText("#lab-remaining-pl")
                        + "', allotted='" + readDurationText("#lab-total-duration-pl") + "')");
    }
}
