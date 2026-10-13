package com.nuvepro.moodle.steps;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Page;
import com.nuvepro.moodle.config.Settings;
import com.nuvepro.moodle.helpers.ApiClient;
import com.nuvepro.moodle.helpers.Auth;
import com.nuvepro.moodle.helpers.BrowserUi;
import com.nuvepro.moodle.pages.CourseAdmin;
import com.nuvepro.moodle.pages.CourseView;

import java.util.concurrent.atomic.AtomicInteger;

import static org.testng.Assert.assertTrue;

/**
 * Shared plumbing for the sequential-completion step classes ({@link SequentialCompletionSteps},
 * {@link SequentialCompletionPracticeSteps}).
 *
 * <p>Parallel-safety rules (Cucumber step definitions are shared across worker threads):</p>
 * <ul>
 *   <li>Never keep per-scenario state in plain static/instance fields — use {@link Holder}.</li>
 *   <li>In parallel ({@code -Dsandbox.parallel=true}) every scenario builds its OWN fixture and tears
 *       it down in an {@code @After}; serial runs keep the old shared, ordered fixture.</li>
 *   <li>Browsers come from the scenario's {@link TestContext} (parallel workers own their browser;
 *       {@code GlobalHooks.browser} is null there).</li>
 *   <li>Seeded users / course short names use {@link #uniqueStamp()} so two threads never collide.</li>
 * </ul>
 */
final class SacSupport {
    private static final AtomicInteger SEQ = new AtomicInteger();

    private SacSupport() {}

    static boolean parallel() {
        return Boolean.getBoolean("sandbox.parallel");
    }

    /** Unique across threads even within the same millisecond. */
    static long uniqueStamp() {
        return System.currentTimeMillis() * 100L + Math.floorMod(SEQ.incrementAndGet(), 100);
    }

    /** The scenario context of the CURRENT thread (step defs are shared, the injected one may be stale). */
    static TestContext ctx(TestContext injected) {
        TestContext active = Hooks.activeTestContext();
        return active != null ? active : injected;
    }

    static void loginStudent(Page page, ApiClient.SeededUser user) {
        Auth.uiLogin(page, user.username, user.password);
        assertTrue(Auth.isLoggedIn(page),
                "seeded student login failed for " + user.username + " (url=" + page.url() + ")");
    }

    /** Per-thread fixture in parallel runs; one shared fixture in serial runs. */
    static final class Holder<T> {
        private final ThreadLocal<T> perThread = new ThreadLocal<>();
        private volatile T shared;

        T get() {
            return parallel() ? perThread.get() : shared;
        }

        void set(T value) {
            if (parallel()) {
                if (value == null) perThread.remove();
                else perThread.set(value);
            } else {
                shared = value;
            }
        }

        T require(String what) {
            T value = get();
            if (value == null) {
                throw new IllegalStateException(what + " fixture was not built — the Given step did not run "
                        + "for this scenario/thread");
            }
            return value;
        }
    }

    /** A course + one seeded, enrolled student with a live browser session on the course page. */
    static class StudentFx {
        String courseId;
        ApiClient.SeededUser student;
        BrowserContext studentCtx;
        CourseView view;

        Page page() {
            return view.page();
        }

        /** (Re)open a fresh logged-in student browser context on the scenario's browser. */
        void startStudentSession(TestContext c) {
            closeStudentCtx();
            studentCtx = BrowserUi.newContext(c.browser, new Browser.NewContextOptions()
                    .setBaseURL(Settings.BASE_URL).setIgnoreHTTPSErrors(true));
            Page sp = studentCtx.newPage();
            BrowserUi.preparePage(sp);
            sp.setDefaultTimeout(Settings.DEFAULT_TIMEOUT_MS);
            loginStudent(sp, student);
            view = new CourseView(sp);
        }

        void closeStudentCtx() {
            try { if (studentCtx != null) studentCtx.close(); } catch (Exception ignored) { }
            studentCtx = null;
            view = null;
        }

        void extraTeardown() { }

        /** Close the student session, delete the course (admin page of {@code c}, else the global browser) and user. */
        void teardown(TestContext c, String label) {
            closeStudentCtx();
            extraTeardown();
            if (courseId != null) {
                deleteCourse(c, courseId, label);
                courseId = null;
            }
            if (student != null) {
                try { ApiClient.deleteUser(student.id); } catch (Throwable e) {
                    System.out.println("[" + label + "] user cleanup failed: " + e.getMessage());
                }
                student = null;
            }
        }
    }

    /**
     * Tear down (or, for a FAILED scenario, deliberately keep) the thread's fixture held in {@code holder}.
     * Keeping the course on failure matters: {@code FailureCapture} screenshots the admin page AFTER this
     * hook, and deleting the course first would make every failure artifact show "course deleted". The
     * kept course/student are tracked in the automation manifest and removed by {@code cleanup.cmd}.
     */
    static <T extends StudentFx> void release(Holder<T> holder, TestContext c, String label, boolean failed) {
        T f = holder.get();
        if (f == null) return;
        try {
            if (failed) {
                f.closeStudentCtx();
                System.out.println("[" + label + "] scenario failed — keeping course " + f.courseId
                        + " / user " + (f.student != null ? f.student.username : "-")
                        + " for diagnosis (remove with cleanup.cmd)");
            } else {
                f.teardown(c, label);
            }
        } finally {
            holder.set(null);
        }
    }

    static void deleteCourse(TestContext c, String courseId, String label) {
        try {
            if (c != null && c.page != null) {
                new CourseAdmin(c.page).deleteCourse(courseId);
            } else if (GlobalHooks.browser != null) {
                BrowserContext bc = BrowserUi.newContext(GlobalHooks.browser, new Browser.NewContextOptions()
                        .setBaseURL(Settings.BASE_URL).setIgnoreHTTPSErrors(true)
                        .setStorageStatePath(GlobalHooks.STORAGE_STATE));
                try {
                    Page p = bc.newPage();
                    p.setDefaultTimeout(Settings.DEFAULT_TIMEOUT_MS);
                    new CourseAdmin(p).deleteCourse(courseId);
                } finally {
                    bc.close();
                }
            } else {
                System.out.println("[" + label + "] no browser available to delete course " + courseId);
                return;
            }
            System.out.println("[" + label + "] deleted course " + courseId);
        } catch (Throwable e) {
            System.out.println("[" + label + "] course cleanup failed: " + e.getMessage());
        }
    }

    static void clearLabTeardownFlags() {
        GlobalHooks.labProvisioned = false;
        GlobalHooks.teardownSectionId = null;
        GlobalHooks.teardownLabId = null;
        GlobalHooks.teardownStudentEmail = null;
    }
}
