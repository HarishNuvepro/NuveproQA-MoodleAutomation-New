package com.nuvepro.moodle.pages;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.WaitUntilState;
import com.nuvepro.moodle.config.Settings;

/**
 * CloudLabs course progress report ({@code report/cloudlabsreport/courses.php}).
 */
public class CloudLabsCourseReportPage extends BasePage {
    public static final String COURSES_PATH = "/report/cloudlabsreport/courses.php";
    public static final String COURSES_TABLE = "#np-rp-courses-npcourses";

    public CloudLabsCourseReportPage(Page page) {
        super(page);
    }

    public void openCoursesReport() {
        page.navigate(Settings.BASE_URL + COURSES_PATH,
                new Page.NavigateOptions()
                        .setWaitUntil(WaitUntilState.DOMCONTENTLOADED)
                        .setTimeout(45_000));
        waitForCourseRows();
    }

    /** Bootstrap-table loads course rows via AJAX. */
    public void waitForCourseRows() {
        for (int i = 0; i < 25; i++) {
            if (page.locator(COURSES_TABLE + " tbody tr").count() > 0) {
                page.waitForTimeout(500);
                return;
            }
            boolean noRecords = page.locator(COURSES_TABLE + " tbody .no-records-found").count() > 0;
            if (noRecords) return;
            page.waitForTimeout(1_000);
        }
    }

    public void assertLoadedWithoutErrors() {
        String url = page.url().toLowerCase();
        if (!url.contains("/report/cloudlabsreport/")) {
            throw new AssertionError("not on CloudLabs course report (url=" + page.url() + ")");
        }
        Locator main = page.locator("#region-main");
        if (main.count() == 0 || !main.first().isVisible()) {
            throw new AssertionError("report page blank — #region-main missing");
        }
        String body = page.locator("body").innerText().toLowerCase();
        if (body.isBlank()) {
            throw new AssertionError("report page body is empty");
        }
        if (body.contains("exception") || body.contains("error/") || body.contains("fatal error")) {
            throw new AssertionError("report page shows an error: "
                    + body.substring(0, Math.min(400, body.length())));
        }
        if (page.locator(COURSES_TABLE).count() == 0) {
            throw new AssertionError("course progress table " + COURSES_TABLE + " not present");
        }
    }

    public void assertCoursesWithLabActivityColumns() {
        Locator table = page.locator(COURSES_TABLE);
        if (table.count() == 0) {
            throw new AssertionError("course report table not present");
        }
        int rows = page.locator(COURSES_TABLE + " tbody tr:not(.no-records-found)").count();
        if (rows == 0) {
            throw new AssertionError("no courses listed in the progress report");
        }
        String headers = table.first().innerText().toLowerCase();
        if (!headers.contains("course") || !headers.contains("participant") || !headers.contains("date")) {
            throw new AssertionError("missing course/participant/date columns (headers=" + headers + ")");
        }
        if (!headers.contains("assessment") && !headers.contains("lab") && !headers.contains("activit")) {
            throw new AssertionError("missing lab-activity count column (Assessments/Activities) (headers="
                    + headers + ")");
        }
    }
}
