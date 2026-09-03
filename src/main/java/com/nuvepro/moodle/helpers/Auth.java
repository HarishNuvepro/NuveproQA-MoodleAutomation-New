package com.nuvepro.moodle.helpers;

import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.LoadState;
import com.nuvepro.moodle.config.Settings;

/** Login helpers. UI login for the flow under test. Mirrors helpers/auth.py. */
public final class Auth {
    private Auth() {}

    public static void uiLogin(Page page, String username, String password) {
        // Retry once and use DOMCONTENTLOADED + a settle wait (the dashboard polls, so
        // networkidle never fires; login can also flake transiently under a slow instance).
        for (int attempt = 1; attempt <= 2; attempt++) {
            attemptLoginOnce(page, username, password);
            if (isLoggedIn(page)) return;
        }
        // Left on the login page after retries; the calling test will assert/skip accordingly.
    }

    /** Single login attempt (no retry) — used for lockout / negative login cases. */
    public static void attemptLoginOnce(Page page, String username, String password) {
        page.navigate(Settings.BASE_URL + "/login/index.php",
                new com.microsoft.playwright.Page.NavigateOptions().setTimeout(45_000)
                        .setWaitUntil(com.microsoft.playwright.options.WaitUntilState.DOMCONTENTLOADED));
        page.locator("#username").fill(username);
        page.locator("#password").fill(password);
        page.locator("#loginbtn").click();
        waitUntilStable(page);
        page.waitForTimeout(600);
    }

    /** Login-page body text after a failed attempt (lockout / invalid credentials). */
    public static String loginPageMessage(Page page) {
        waitUntilStable(page);
        try {
            return page.locator("#loginerrormessage, .loginerrors, .alert, #region-main, body")
                    .first().innerText().toLowerCase();
        } catch (RuntimeException e) {
            return page.locator("body").innerText().toLowerCase();
        }
    }

    /** Wait until the document is usable. Moodle POSTs often destroy the JS context mid-call. */
    public static void waitUntilStable(Page page) {
        for (int i = 0; i < 8; i++) {
            try {
                page.waitForLoadState(LoadState.DOMCONTENTLOADED,
                        new Page.WaitForLoadStateOptions().setTimeout(5_000));
                page.locator("body").getAttribute("class");
                return;
            } catch (RuntimeException e) {
                page.waitForTimeout(400);
            }
        }
    }

    public static void logout(Page page) {
        waitUntilStable(page);
        String sesskey = "";
        for (int i = 0; i < 5; i++) {
            try {
                Object sk = page.evaluate("() => (window.M && M.cfg && M.cfg.sesskey) || ''");
                sesskey = sk == null ? "" : sk.toString();
                break;
            } catch (RuntimeException e) {
                waitUntilStable(page);
            }
        }
        page.navigate(Settings.BASE_URL + "/login/logout.php?sesskey=" + sesskey,
                new Page.NavigateOptions()
                        .setTimeout(30_000)
                        .setWaitUntil(com.microsoft.playwright.options.WaitUntilState.DOMCONTENTLOADED));
        waitUntilStable(page);
    }

    public static boolean isLoggedIn(Page page) {
        waitUntilStable(page);
        try {
            String bodyClass = page.locator("body").getAttribute("class");
            return bodyClass != null && !bodyClass.contains("notloggedin");
        } catch (RuntimeException e) {
            waitUntilStable(page);
            String bodyClass = page.locator("body").getAttribute("class");
            return bodyClass != null && !bodyClass.contains("notloggedin");
        }
    }

    public static void loginAsAdmin(Page page) {
        uiLogin(page, Settings.ADMIN_USER, Settings.ADMIN_PASS);
    }

    public static void loginAsStudent(Page page) {
        uiLogin(page, Settings.STUDENT_USER, Settings.STUDENT_PASS);
    }
}
