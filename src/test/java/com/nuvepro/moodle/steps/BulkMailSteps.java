package com.nuvepro.moodle.steps;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.SelectOption;
import com.nuvepro.moodle.config.Settings;
import com.nuvepro.moodle.helpers.ApiClient;
import com.nuvepro.moodle.helpers.Auth;
import com.nuvepro.moodle.helpers.FormSubmit;
import com.nuvepro.moodle.helpers.BrowserUi;
import io.cucumber.java.After;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import org.testng.SkipException;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

/**
 * NuveBulkMail (local_bulkmail) settings form — batch 1, no mail sending. Field/option presence,
 * custom-text validation, role gate, and one save-and-restore (the settings are the plugin's global
 * config, captured and restored so nothing is left changed). Page: /local/bulkmail/settingspage.php.
 */
public class BulkMailSteps {
    private static final String SETTINGS_URL = "/local/bulkmail/settingspage.php";
    private static final String[] FIELDS = {"passwordpattern", "customtext", "passwordbehavior",
            "fromname", "fromemail", "alloweddomains"};

    private final TestContext ctx;
    private ApiClient.SeededUser student;
    private BrowserContext studentCtx;
    private Map<String, String> original;

    public BulkMailSteps(TestContext ctx) {
        this.ctx = ctx;
    }

    private void openSettings(Page p) {
        p.navigate(Settings.BASE_URL + SETTINGS_URL,
                new Page.NavigateOptions().setWaitUntil(com.microsoft.playwright.options.WaitUntilState.DOMCONTENTLOADED)
                        .setTimeout(45_000));
        p.waitForTimeout(2_500);
    }

    @When("admin opens the bulk mail settings")
    public void adminOpensTheBulkMailSettings() {
        openSettings(ctx.page);
    }

    @Then("the settings form shows the password pattern and behavior fields")
    public void theSettingsFormShowsFields() {
        for (String f : FIELDS)
            assertTrue(ctx.page.locator("[name='" + f + "']").count() > 0, "settings field missing: " + f);
        String patterns = ctx.page.locator("select[name='passwordpattern']").first().innerText();
        assertTrue(patterns.toLowerCase().contains("custom text"),
                "passwordpattern options missing 'custom text' (got: " + patterns.replace("\n", " ") + ")");
        String behavior = ctx.page.locator("select[name='passwordbehavior']").first().innerText().toLowerCase();
        assertTrue(behavior.contains("pattern from settings") && behavior.contains("reset link"),
                "passwordbehavior missing the expected options (got: " + behavior.replace("\n", " ") + ")");
    }

    @Then("the custom text field accepts alphanumeric and special characters")
    public void theCustomTextFieldAcceptsSpecialChars() {
        String val = "Auto_9!@#Ab";
        Locator ct = ctx.page.locator("input[name='customtext']");
        ct.first().fill(val);            // client-side only; no save
        assertEquals(ct.first().inputValue(), val, "custom text field did not accept the input");
    }

    @Then("a student cannot open the bulk mail settings")
    public void aStudentCannotOpenTheBulkMailSettings() {
        if (Settings.WS_TOKEN.isEmpty()) throw new SkipException("MOODLE_WS_TOKEN not set");
        student = ApiClient.createUser(System.currentTimeMillis());
        if (!Settings.COURSE_ID.isEmpty())
            ApiClient.enrolUser(student.id, Long.parseLong(Settings.COURSE_ID), 5);
        studentCtx = BrowserUi.newContext(GlobalHooks.browser, new Browser.NewContextOptions()
                .setBaseURL(Settings.BASE_URL).setIgnoreHTTPSErrors(true));
        Page sp = studentCtx.newPage();
        sp.setDefaultTimeout(Settings.DEFAULT_TIMEOUT_MS);
        Auth.uiLogin(sp, student.username, student.password);
        openSettings(sp);
        assertTrue(sp.locator("select[name='passwordpattern']").count() == 0,
                "DEFECT: a student can open the bulk mail settings form (cap local/bulkmail:managesettings)");
        String body = sp.locator("body").innerText().toLowerCase();
        assertTrue(body.contains("permission") || body.contains("not allowed") || body.contains("cannot")
                        || sp.url().contains("login"),
                "student was not blocked from the bulk mail settings");
    }

    private String read(String field) {
        Locator l = ctx.page.locator("[name='" + field + "']");
        return l.count() > 0 ? l.first().inputValue() : "";
    }

    private void set(String field, String value) {
        Locator l = ctx.page.locator("[name='" + field + "']");
        if (l.count() == 0) return;
        String tag = (String) l.first().evaluate("e => e.tagName");
        if ("SELECT".equals(tag)) ctx.page.selectOption("select[name='" + field + "']", value);
        else l.first().fill(value);
    }

    private void save() {
        FormSubmit.andAwaitNewPage(ctx.page, ctx.page.locator("#id_submitbutton").first());
        ctx.page.waitForTimeout(1_000);
    }

    @Then("admin can save a password pattern and behavior and it persists")
    public void adminCanSaveAndItPersists() {
        // capture ALL current settings first, so we can restore them in @After
        original = new LinkedHashMap<>();
        for (String f : FIELDS) original.put(f, read(f));
        // choose values distinct from the current ones where possible
        String patternValue = ctx.page.locator("select[name='passwordpattern'] option")
                .nth(3).getAttribute("value");   // 'Username + First name'
        String behaviorValue = ctx.page.locator("select[name='passwordbehavior'] option")
                .last().getAttribute("value");   // 'Send reset link'
        set("passwordpattern", patternValue);
        set("passwordbehavior", behaviorValue);
        save();
        openSettings(ctx.page);
        assertEquals(read("passwordpattern"), patternValue, "password pattern did not persist after save");
        assertEquals(read("passwordbehavior"), behaviorValue, "password behavior did not persist after save");
    }

    @After("@bulkmail")
    public void cleanup() {
        // restore the original settings so the plugin config is left unchanged
        if (original != null) {
            try {
                openSettings(ctx.page);
                for (Map.Entry<String, String> e : original.entrySet()) set(e.getKey(), e.getValue());
                save();
            } catch (Throwable ignored) {}
            original = null;
        }
        if (studentCtx != null) { try { studentCtx.close(); } catch (Throwable ignored) {} studentCtx = null; }
        if (student != null) { try { ApiClient.deleteUser(student.id); } catch (Throwable ignored) {} student = null; }
    }
}
