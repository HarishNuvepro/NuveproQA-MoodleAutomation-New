package com.nuvepro.moodle.helpers;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.nuvepro.moodle.config.Settings;

/**
 * Headed-run defaults: maximized window (no fixed viewport) and page zoom for readability.
 */
public final class BrowserUi {
    private BrowserUi() {}

    public static BrowserType.LaunchOptions defaultLaunchOptions() {
        BrowserType.LaunchOptions opts = new BrowserType.LaunchOptions()
                .setHeadless(Settings.HEADLESS)
                .setSlowMo(Settings.SLOWMO_MS);
        if (!Settings.HEADLESS) {
            opts.setArgs(java.util.List.of("--start-maximized"));
        }
        return opts;
    }

    public static void enrichContextOptions(Browser.NewContextOptions opts) {
        if (opts == null || Settings.HEADLESS) {
            return;
        }
        try {
            opts.setViewportSize(null);
        } catch (RuntimeException e) {
            opts.setViewportSize(1920, 1080);
        }
    }

    public static BrowserContext newContext(Browser browser, Browser.NewContextOptions options) {
        Browser.NewContextOptions opts = options != null ? options : new Browser.NewContextOptions();
        enrichContextOptions(opts);
        BrowserContext ctx = browser.newContext(opts);
        prepareContext(ctx);
        return ctx;
    }

    /** Apply zoom on every navigation in this context and on pages opened later. */
    public static void prepareContext(BrowserContext context) {
        if (context == null || Settings.BROWSER_ZOOM_PERCENT <= 0
                || Settings.BROWSER_ZOOM_PERCENT == 100) {
            return;
        }
        String z = Settings.BROWSER_ZOOM_PERCENT + "%";
        context.addInitScript("() => { const zoom = '" + z + "'; const apply = () => { "
                + "try { document.documentElement.style.zoom = zoom; } catch (e) {} }; "
                + "apply(); document.addEventListener('DOMContentLoaded', apply); }");
        context.onPage(BrowserUi::preparePage);
    }

    public static void preparePage(Page page) {
        if (page == null || Settings.BROWSER_ZOOM_PERCENT <= 0
                || Settings.BROWSER_ZOOM_PERCENT == 100) {
            return;
        }
        try {
            page.evaluate("zoomPct => { document.documentElement.style.zoom = zoomPct + '%'; }",
                    Settings.BROWSER_ZOOM_PERCENT);
        } catch (RuntimeException ignored) {}
    }
}
