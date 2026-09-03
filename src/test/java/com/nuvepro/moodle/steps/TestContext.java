package com.nuvepro.moodle.steps;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.Tracing;
import com.nuvepro.moodle.config.Settings;
import com.nuvepro.moodle.pages.PlaygroundControlPanel;
import com.nuvepro.moodle.pages.PlaygroundLanding;

import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Semaphore;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Per-scenario Playwright state, created by PicoContainer and shared across the step classes
 * of one scenario. A fresh BrowserContext is created from the run-wide browser using the
 * saved storageState, so the scenario is already logged in (no re-login).
 */
public class TestContext {
    private static final int PARALLEL_BROWSER_LIMIT = 5;
    private static final Semaphore BROWSER_PERMITS = new Semaphore(PARALLEL_BROWSER_LIMIT, true);

    public Playwright ownedPlaywright;
    public Browser browser;
    private boolean ownsBrowser;
    private boolean browserPermitHeld;
    public BrowserContext context;
    public Page page;
    public PlaygroundLanding landing;
    public PlaygroundControlPanel cp;
    public final int cmid = Integer.parseInt(Settings.PLAYGROUND_CMID);

    // transient per-scenario carry-state
    public String sectionId;
    public String newActivityCmid;
    public String newActivityName;
    public String newActivityDataset;
    public com.nuvepro.moodle.helpers.AssessmentCreateSpec lastCreateSpec;
    public String labStatus = "";
    public Page openedTab;
    public BrowserContext studentContext;
    public Page studentPage;
    public String bulkAction;
    public String scenarioName;
    public Set<String> scenarioTags = new HashSet<>();

    // failure-diagnostics: browser console/page-errors collected during the scenario, and whether a
    // Playwright trace is currently recording (FailureCapture saves it on failure, discards on pass).
    public List<String> consoleLogs;
    private boolean tracing;
    private BrowserContext traceContext;

    public void start() {
        if (Boolean.getBoolean("sandbox.parallel")) {
            acquireBrowserPermits(1);
            browserPermitHeld = true;
            try {
                ownedPlaywright = Playwright.create();
                browser = ownedPlaywright.chromium().launch(new BrowserType.LaunchOptions()
                        .setHeadless(Settings.HEADLESS).setSlowMo(Settings.SLOWMO_MS));
                ownsBrowser = true;
            } catch (Throwable t) {
                releaseBrowserPermits(1);
                browserPermitHeld = false;
                throw t;
            }
        } else {
            browser = GlobalHooks.browser;
        }
        Browser.NewContextOptions opts = new Browser.NewContextOptions()
                .setBaseURL(Settings.BASE_URL)
                .setIgnoreHTTPSErrors(true);
        if (java.nio.file.Files.exists(GlobalHooks.STORAGE_STATE)) {
            opts.setStorageStatePath(GlobalHooks.STORAGE_STATE);
        }
        context = browser.newContext(opts);
        page = context.newPage();
        page.setDefaultTimeout(Settings.DEFAULT_TIMEOUT_MS);
        GlobalHooks.ensureAdminSession(context, page);
        beginTracing(context);
        // Capture the browser console + uncaught page errors for post-mortem.
        consoleLogs = new CopyOnWriteArrayList<>();
        page.onConsoleMessage(m -> consoleLogs.add("[admin][" + m.type() + "] " + m.text()));
        page.onPageError(e -> consoleLogs.add("[admin][pageerror] " + e));
        landing = new PlaygroundLanding(page);
        cp = new PlaygroundControlPanel(page);
    }

    private void beginTracing(BrowserContext c) {
        try {
            c.tracing().start(new Tracing.StartOptions()
                    .setScreenshots(true).setSnapshots(true).setSources(true));
            tracing = true;
            traceContext = c;
        } catch (Throwable ignored) {
            tracing = false;
            traceContext = null;
        }
    }

    /** Move Playwright trace recording from admin context to student context (user-flow handoff). */
    public void handoffTracingTo(BrowserContext next) {
        stopTracing(null);
        if (next != null) beginTracing(next);
    }

    public void watchPageConsole(Page p, String label) {
        if (p == null) return;
        p.onConsoleMessage(m -> consoleLogs.add("[" + label + "][" + m.type() + "] " + m.text()));
        p.onPageError(e -> consoleLogs.add("[" + label + "][pageerror] " + e));
    }

    public boolean hasTag(String tag) {
        return scenarioTags != null && scenarioTags.contains(tag);
    }

    public boolean hasAnyTagStartingWith(String prefix) {
        if (scenarioTags == null) return false;
        return scenarioTags.stream().anyMatch(t -> t.startsWith(prefix));
    }

    public boolean tracingActive() { return tracing; }

    /** Stop the trace: with a path it writes the zip (on failure); with null it discards (on pass).
     *  Idempotent — safe to call again from stop() after FailureCapture has already stopped it. */
    public void stopTracing(Path path) {
        if (!tracing || traceContext == null) return;
        try {
            if (path != null) traceContext.tracing().stop(new Tracing.StopOptions().setPath(path));
            else traceContext.tracing().stop();
        } catch (Throwable ignored) {}
        tracing = false;
        traceContext = null;
    }

    public void stop() {
        stopTracing(null);   // no-op if FailureCapture already saved/stopped it
        if (openedTab != null) {
            try { openedTab.close(); } catch (Exception ignored) {}
        }
        if (studentContext != null) {
            try { studentContext.close(); } catch (Exception ignored) {}
        }
        if (context != null) context.close();
        if (ownsBrowser && browser != null) {
            try { browser.close(); } catch (Exception ignored) {}
        }
        if (ownedPlaywright != null) {
            try { ownedPlaywright.close(); } catch (Exception ignored) {}
        }
        if (browserPermitHeld) {
            releaseBrowserPermits(1);
            browserPermitHeld = false;
        }
        ownsBrowser = false;
        browser = null;
        ownedPlaywright = null;
    }

    public static void acquireBrowserPermits(int permits) {
        try {
            BROWSER_PERMITS.acquire(permits);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("interrupted waiting for browser slot", e);
        }
    }

    public static void releaseBrowserPermits(int permits) {
        BROWSER_PERMITS.release(permits);
    }

    /**
     * Reserve extra browser slots without blocking forever. A scenario that wants extra slots
     * already holds one, so a hard acquire could stall behind scenarios queued on a shared lane.
     * Returns how many were actually reserved — the caller must release exactly that many.
     */
    public static int tryAcquireBrowserPermits(int permits, int waitSeconds) {
        try {
            if (BROWSER_PERMITS.tryAcquire(permits, waitSeconds, java.util.concurrent.TimeUnit.SECONDS)) {
                return permits;
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        System.out.println("[parallel] browser slots busy after " + waitSeconds
                + "s — running " + permits + " extra browsers unthrottled");
        return 0;
    }

    // ---- shared-resource lanes ----
    // A lane serializes only the scenarios that share one cloud lab (keyed by named student), so
    // scenarios on different labs still run concurrently.
    private static final java.util.Map<String, ReentrantLock> LANES = new java.util.concurrent.ConcurrentHashMap<>();
    private String laneHeld;

    public void enterLane(String key) {
        if (key == null || key.isBlank() || !Boolean.getBoolean("sandbox.parallel")) return;
        if (laneHeld != null) return;   // one lane per scenario; first claim wins
        LANES.computeIfAbsent(key, k -> new ReentrantLock(true)).lock();
        laneHeld = key;
    }

    public void exitLane() {
        if (laneHeld == null) return;
        ReentrantLock lock = LANES.get(laneHeld);
        laneHeld = null;
        if (lock != null && lock.isHeldByCurrentThread()) lock.unlock();
    }
}
