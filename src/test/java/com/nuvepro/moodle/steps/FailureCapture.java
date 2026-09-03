package com.nuvepro.moodle.steps;

import com.microsoft.playwright.Page;
import io.cucumber.java.Scenario;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * On scenario FAILURE, dump everything needed to diagnose a UI test WITHOUT re-running it, into
 * {@code target/failures/<scenario>/}:
 *   - screenshot.png  — full-page screenshot at the moment of failure
 *   - page.html       — the DOM at failure (inspect selectors that didn't match)
 *   - console.log     — the browser console + page errors captured during the scenario
 *   - trace.zip       — Playwright trace (open with: npx playwright show-trace trace.zip) — the
 *                       timeline of every action, network call and DOM snapshot
 *   - summary.txt     — scenario name, tags, status, final URL, timestamp
 * The screenshot + console are also ATTACHED to the Cucumber HTML report. On a PASS the trace is
 * discarded (cheap on green runs). Every step is best-effort: a capture failure must never mask or
 * replace the real test failure. The per-scenario/skip REASON text lives in target/cucumber.json and
 * is consolidated by tools/failure_report.py.
 */
final class FailureCapture {
    static final Path DIR = Paths.get("target", "failures");

    private FailureCapture() {}

    static void onScenarioEnd(Scenario scenario, TestContext ctx) {
        boolean failed = scenario.isFailed();
        Path folder = DIR.resolve(sanitize(scenario.getName()));

        // Trace: save the zip only for failures; otherwise stop-and-discard.
        if (ctx != null && ctx.tracingActive()) {
            try {
                if (failed) { Files.createDirectories(folder); ctx.stopTracing(folder.resolve("trace.zip")); }
                else ctx.stopTracing(null);
            } catch (Exception ignored) { try { ctx.stopTracing(null); } catch (Exception ignored2) {} }
        }
        if (!failed) return;

        Page page = ctx != null ? activePage(ctx) : null;
        boolean pageUsable = page != null;
        try { pageUsable = page != null && !page.isClosed(); } catch (Exception e) { pageUsable = false; }

        try { Files.createDirectories(folder); } catch (Exception ignored) {}

        // Screenshot (also attached to the Cucumber report).
        if (pageUsable) {
            try {
                byte[] png = page.screenshot(new Page.ScreenshotOptions()
                        .setFullPage(true).setPath(folder.resolve("screenshot.png")));
                scenario.attach(png, "image/png", "failure-screenshot");
            } catch (Exception ignored) {}
        }

        // DOM at failure.
        if (pageUsable) {
            try {
                Files.write(folder.resolve("page.html"), page.content().getBytes(StandardCharsets.UTF_8));
            } catch (Exception ignored) {}
        }

        // Browser console + page errors gathered during the scenario.
        try {
            if (ctx != null && ctx.consoleLogs != null && !ctx.consoleLogs.isEmpty()) {
                String log = String.join(System.lineSeparator(), ctx.consoleLogs);
                Files.write(folder.resolve("console.log"), log.getBytes(StandardCharsets.UTF_8));
                scenario.attach(log, "text/plain", "browser-console");
            }
        } catch (Exception ignored) {}

        // Human summary.
        try {
            String url = pageUsable ? page.url() : "(page closed)";
            String stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            String summary = "scenario : " + scenario.getName() + System.lineSeparator()
                    + "tags     : " + String.join(" ", scenario.getSourceTagNames()) + System.lineSeparator()
                    + "status   : " + scenario.getStatus() + System.lineSeparator()
                    + "url      : " + url + System.lineSeparator()
                    + "when     : " + stamp + System.lineSeparator();
            Files.write(folder.resolve("summary.txt"), summary.getBytes(StandardCharsets.UTF_8));
            System.out.println("[FailureCapture] FAILED '" + scenario.getName()
                    + "' -> artifacts in " + folder.toAbsolutePath());
        } catch (Exception ignored) {}
    }

    /** Mirror of the sanitize rule in tools/failure_report.py so the artifact folder is discoverable. */
    private static String sanitize(String s) {
        String base = (s == null ? "scenario" : s).replaceAll("[^a-zA-Z0-9._-]+", "_");
        return base.length() > 80 ? base.substring(0, 80) : base;
    }

    /** Prefer admin page; fall back to student page when admin context was released early. */
    private static Page activePage(TestContext ctx) {
        Page admin = ctx.page;
        if (admin != null) {
            try {
                if (!admin.isClosed()) return admin;
            } catch (Exception ignored) {}
        }
        Page student = ctx.studentPage;
        if (student != null) {
            try {
                if (!student.isClosed()) return student;
            } catch (Exception ignored) {}
        }
        return admin;
    }
}
