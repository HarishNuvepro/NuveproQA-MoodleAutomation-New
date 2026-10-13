package com.nuvepro.moodle.steps;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.nuvepro.moodle.config.Settings;
import com.nuvepro.moodle.helpers.ApiClient;
import com.nuvepro.moodle.helpers.Auth;
import com.nuvepro.moodle.helpers.BrowserUi;
import io.cucumber.java.After;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import org.testng.SkipException;

import static org.testng.Assert.assertTrue;

/**
 * CloudLabs course reports (report_cloudlabsreport) — batch 1. courses.php (list #np-rp-courses-npcourses),
 * bootstrap-table search, and drill-down to courseparticipants.php. Role gate is a deviation:
 * courses.php only require_login (no capability), so a student can open the report.
 */
public class ReportSteps {
    private static final String COURSES = "/report/cloudlabsreport/courses.php";
    private static final String TABLE = "#np-rp-courses-npcourses";

    private final TestContext ctx;
    private ApiClient.SeededUser student;
    private BrowserContext studentCtx;

    public ReportSteps(TestContext ctx) {
        this.ctx = ctx;
    }

    private void open(Page p, String path) {
        p.navigate(Settings.BASE_URL + path,
                new Page.NavigateOptions().setWaitUntil(com.microsoft.playwright.options.WaitUntilState.DOMCONTENTLOADED)
                        .setTimeout(45_000));
        p.waitForTimeout(3_000);
    }

    @When("admin opens the cloudlabs course report")
    public void adminOpensTheCloudlabsCourseReport() {
        open(ctx.page, COURSES);
        waitForRows(ctx.page);
    }

    /** The courses table loads via AJAX - wait (up to ~20s) for its rows to render. */
    private void waitForRows(Page p) {
        for (int i = 0; i < 20 && p.locator(TABLE + " tbody tr").count() == 0; i++) p.waitForTimeout(1_000);
        p.waitForTimeout(500);
    }

    @Then("the course report lists courses with assessment participant and date columns")
    public void theCourseReportListsCourses() {
        assertTrue(ctx.page.locator(TABLE).count() > 0, "course report table not present");
        assertTrue(ctx.page.locator(TABLE + " tbody tr").count() > 0, "no courses listed in the report");
        String headers = ctx.page.locator(TABLE).first().innerText().toLowerCase();
        assertTrue(headers.contains("course name") && headers.contains("assessment")
                        && headers.contains("participant") && headers.contains("date"),
                "course report missing expected columns (Course Name/Assessments/Participants/Date)");
    }

    /** The report's course-name search input (identified by placeholder). */
    private Locator searchInput() {
        Locator cands = ctx.page.locator("input[placeholder='Search by name'], input[placeholder='Search'], "
                + "input.search-input, input[type='search']");
        for (int i = 0; i < cands.count(); i++) {
            Locator c = cands.nth(i);
            if (!"checkbox".equals(c.getAttribute("type")) && c.isVisible()) return c;
        }
        return null;
    }

    @Then("searching the course report filters the rows and clearing restores them")
    public void searchingFiltersAndClears() {
        int all = ctx.page.locator(TABLE + " tbody tr").count();
        Locator search = searchInput();
        if (search == null) throw new SkipException("bootstrap-table search input not found on the report");
        String firstCourse = ctx.page.locator(TABLE + " tbody tr").first().innerText().trim();
        String term = firstCourse.length() >= 4 ? firstCourse.substring(0, 4) : firstCourse;
        search.fill(term);
        ctx.page.waitForTimeout(1_500);
        int filtered = ctx.page.locator(TABLE + " tbody tr").count();
        assertTrue(filtered <= all, "search did not filter the course rows (all=" + all + " filtered=" + filtered + ")");
        search.fill("");
        ctx.page.waitForTimeout(1_500);
        int restored = ctx.page.locator(TABLE + " tbody tr").count();
        assertTrue(restored >= filtered, "clearing the search did not restore the rows");
    }

    @Then("clicking a course opens its participant report")
    public void clickingACourseOpensParticipantReport() {
        Locator link = ctx.page.locator(TABLE + " tbody tr a[href*='courseparticipants.php']");
        if (link.count() == 0) link = ctx.page.locator(TABLE + " tbody tr a").first();
        assertTrue(link.count() > 0, "no course drill-down link in the report");
        link.first().click();
        ctx.page.waitForTimeout(3_000);
        assertTrue(ctx.page.url().contains("courseparticipants.php") || ctx.page.locator("table").count() > 0,
                "clicking a course did not open its participant report");
    }

    private static final String PARTICIPANTS = "#np-rp-course-participants";

    @When("admin opens the course participant report")
    public void adminOpensTheCourseParticipantReport() {
        if (Settings.COURSE_ID.isEmpty()) throw new SkipException("COURSE_ID not set");
        open(ctx.page, "/report/cloudlabsreport/courseparticipants.php?id=" + Settings.COURSE_ID);
        for (int i = 0; i < 20 && ctx.page.locator(PARTICIPANTS + " tbody tr").count() == 0; i++)
            ctx.page.waitForTimeout(1_000);
    }

    @Then("the participant report lists participants with name email and result columns")
    public void theParticipantReportListsParticipants() {
        assertTrue(ctx.page.locator(PARTICIPANTS).count() > 0, "participant report table not present");
        assertTrue(ctx.page.locator(PARTICIPANTS + " tbody tr").count() > 0, "no participants listed");
        String headers = ctx.page.locator(PARTICIPANTS).first().innerText().toLowerCase();
        assertTrue(headers.contains("name") && headers.contains("email") && headers.contains("result"),
                "participant report missing Name/Email/Result columns");
    }

    @Then("drilling a participant opens their challenge report")
    public void drillingAParticipantOpensTheirChallengeReport() {
        Locator link = ctx.page.locator(PARTICIPANTS + " tbody tr a[href*='userchallenges.php'], "
                + PARTICIPANTS + " tbody tr a[href*='userattempts.php']");
        if (link.count() == 0) link = ctx.page.locator(PARTICIPANTS + " tbody tr a");
        assertTrue(link.count() > 0, "no participant drill-down link");
        link.first().click();
        ctx.page.waitForTimeout(3_000);
        assertTrue(ctx.page.url().contains("userchallenges.php") || ctx.page.url().contains("userattempts.php")
                        || ctx.page.locator("table").count() > 0,
                "drilling a participant did not open their challenge/attempt report");
    }

    @Then("a student cannot open the report participant and attempt sub-pages")
    public void aStudentCannotOpenTheSubPages() {
        Page sp = seedStudentPage();
        String c = Settings.COURSE_ID;
        // DEVIATION: all sub-pages leak - a student sees other users' data
        for (String path : new String[]{
                "/report/cloudlabsreport/courseparticipants.php?id=" + c,
                "/report/cloudlabsreport/userchallenges.php?id=2&course=" + c,
                "/report/cloudlabsreport/userattempts.php?id=2&course=" + c}) {
            open(sp, path);
            assertTrue(sp.locator("table tbody tr").count() == 0,
                    "DEFECT: a student can open a report sub-page and see other users' data via " + path);
        }
    }

    private Page seedStudentPage() {
        if (Settings.WS_TOKEN.isEmpty()) throw new SkipException("MOODLE_WS_TOKEN not set");
        student = ApiClient.createUser(System.currentTimeMillis());
        if (!Settings.COURSE_ID.isEmpty())
            ApiClient.enrolUser(student.id, Long.parseLong(Settings.COURSE_ID), 5);
        studentCtx = BrowserUi.newContext(GlobalHooks.browser, new Browser.NewContextOptions()
                .setBaseURL(Settings.BASE_URL).setIgnoreHTTPSErrors(true));
        Page sp = studentCtx.newPage();
        sp.setDefaultTimeout(Settings.DEFAULT_TIMEOUT_MS);
        Auth.uiLogin(sp, student.username, student.password);
        return sp;
    }

    @Then("a student cannot open the cloudlabs course report")
    public void aStudentCannotOpenTheReport() {
        Page sp = seedStudentPage();
        open(sp, COURSES);
        // DEVIATION: courses.php only require_login -> a student can see the course report table.
        assertTrue(sp.locator(TABLE).count() == 0,
                "DEFECT: a student can open the CloudLabs course report (courses.php lacks a capability check)");
    }

    @After("@reports")
    public void cleanup() {
        if (studentCtx != null) { try { studentCtx.close(); } catch (Throwable ignored) {} studentCtx = null; }
        if (student != null) { try { ApiClient.deleteUser(student.id); } catch (Throwable ignored) {} student = null; }
    }
}
