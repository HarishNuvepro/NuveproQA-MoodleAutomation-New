package com.nuvepro.moodle.helpers;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.nuvepro.moodle.config.Settings;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * Triggers Moodle cron / a specific scheduled task headlessly from an admin browser session (no CLI /
 * server access). Two entry points: web cron (runs all DUE tasks) and a named scheduled task via the
 * admin "Run now" flow (admin/tool/task/run_userfriendly_task.php). Best-effort — returns whether the run
 * page reported execution.
 */
public final class CronRunner {
    private CronRunner() {}

    /** Hit the site cron URL (runs due scheduled tasks). */
    public static boolean runWebCron(Page admin) {
        if (Settings.CRON_URL.isEmpty()) return false;
        try {
            admin.navigate(Settings.CRON_URL, new Page.NavigateOptions()
                    .setWaitUntil(com.microsoft.playwright.options.WaitUntilState.DOMCONTENTLOADED).setTimeout(120_000));
            admin.waitForTimeout(4_000);
            String body = admin.locator("body").innerText().toLowerCase();
            return body.contains("cron") || body.contains("scheduled") || body.contains("execution");
        } catch (Throwable e) { return false; }
    }

    /** Run a specific scheduled task now via the admin "Run now" page. taskClass e.g.
     *  \\mod_cloudlabs\\task\\delete_deleted_submissions */
    public static boolean runScheduledTask(Page admin, String taskClass) {
        try {
            admin.navigate(Settings.BASE_URL + "/admin/tool/task/scheduledtasks.php",
                    new Page.NavigateOptions().setWaitUntil(com.microsoft.playwright.options.WaitUntilState.DOMCONTENTLOADED)
                            .setTimeout(60_000));
            admin.waitForTimeout(1_500);
            String sesskey = (String) admin.evaluate("() => (window.M && M.cfg && M.cfg.sesskey) || ''");
            if (sesskey == null) sesskey = "";
            String url = Settings.BASE_URL + "/admin/tool/task/run_userfriendly_task.php?task="
                    + URLEncoder.encode(taskClass, StandardCharsets.UTF_8) + "&sesskey=" + sesskey;
            admin.navigate(url, new Page.NavigateOptions()
                    .setWaitUntil(com.microsoft.playwright.options.WaitUntilState.DOMCONTENTLOADED).setTimeout(120_000));
            admin.waitForTimeout(2_000);
            // a confirmation "Run now" button may be shown first
            Locator run = admin.locator("button:has-text('Run now'), input[value='Run now'], "
                    + "#adminsettings button[type=submit], form button[type=submit]");
            if (run.count() > 0 && run.first().isVisible()) { run.first().click(); admin.waitForTimeout(4_000); }
            String body = admin.locator("body").innerText().toLowerCase();
            return body.contains("execution took") || body.contains("finished") || body.contains("scheduled task complete")
                    || body.contains("... started") || body.contains("trace");
        } catch (Throwable e) { return false; }
    }
}
