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
 * AWS Marketplace Integration (local_nuveawsmarketplace) — batch 1, the admin settings page
 * (/admin/settings.php?section=local_nuveawsmarketplace_settings). Fields: dropdown (region),
 * access_key, secret_key, support_email (admin_setting_configtext_with_validation: non-empty +
 * support_email must be a valid email). No successful save of changed values — the validation test
 * enters an INVALID email which Moodle rejects (not persisted).
 */
public class AwsMarketplaceSteps {
    private static final String SECTION = "/admin/settings.php?section=local_nuveawsmarketplace_settings";
    private static final String P = "id_s_local_nuveawsmarketplace_";   // Moodle admin-setting id prefix

    private final TestContext ctx;
    private ApiClient.SeededUser student;
    private BrowserContext studentCtx;

    public AwsMarketplaceSteps(TestContext ctx) {
        this.ctx = ctx;
    }

    private void openSettings(Page p) {
        p.navigate(Settings.BASE_URL + SECTION,
                new Page.NavigateOptions().setWaitUntil(com.microsoft.playwright.options.WaitUntilState.DOMCONTENTLOADED)
                        .setTimeout(45_000));
        p.waitForTimeout(2_500);
    }

    @When("admin opens the aws marketplace settings")
    public void adminOpensTheAwsMarketplaceSettings() {
        openSettings(ctx.page);
    }

    @Then("the aws settings show the region access-key secret-key and support-email fields")
    public void theAwsSettingsShowTheFields() {
        for (String f : new String[]{"dropdown", "access_key", "secret_key", "support_email"})
            assertTrue(ctx.page.locator("#" + P + f).count() > 0, "AWS setting field missing: " + f);
    }

    @Then("the region dropdown offers at least one region")
    public void theRegionDropdownOffersRegions() {
        assertTrue(ctx.page.locator("#" + P + "dropdown option").count() > 0,
                "region dropdown has no options");
    }

    @Then("an invalid support email is rejected on save")
    public void anInvalidSupportEmailIsRejected() {
        // enter an INVALID email; Moodle rejects it on save (not persisted). Other fields unchanged.
        ctx.page.locator("#" + P + "support_email").fill("not-an-email");
        Locator save = ctx.page.locator("#adminsettings input[type=submit], button:has-text('Save changes'), "
                + "input[value='Save changes']");
        save.first().click();
        ctx.page.waitForTimeout(2_500);
        String body = ctx.page.locator("body").innerText().toLowerCase();
        assertTrue(body.contains("not changed due to an error") || body.contains("invalid")
                        || ctx.page.locator(".alert-danger, .error").count() > 0,
                "invalid support email was not rejected (no validation error shown)");
    }

    @Then("a student cannot open the aws marketplace settings")
    public void aStudentCannotOpenTheAwsSettings() {
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
        assertTrue(sp.locator("#" + P + "dropdown").count() == 0,
                "DEFECT: a student can open the AWS Marketplace admin settings");
        String body = sp.locator("body").innerText().toLowerCase();
        // Moodle hides the admin section for non-admins -> "Section error / Error code: sectionerror"
        assertTrue(body.contains("permission") || body.contains("not allowed") || body.contains("cannot")
                        || body.contains("section error") || body.contains("sectionerror")
                        || body.contains("error code") || body.contains("denied") || sp.url().contains("login"),
                "student was not blocked from the AWS Marketplace settings (body=" + body.substring(0, Math.min(80, body.length())) + ")");
    }

    @Then("the aws secret key field is masked")
    public void theAwsSecretKeyFieldIsMasked() {
        // FINDING (fails by design): secret_key is a plain text input, not a password/masked field.
        Locator sk = ctx.page.locator("#" + P + "secret_key");
        String type = sk.count() > 0 ? sk.first().getAttribute("type") : null;
        assertTrue("password".equals(type),
                "FINDING: the AWS Secret Key is NOT masked (type=" + type + ") - a sensitive credential "
                        + "is shown in clear; should use admin_setting_configpasswordunmask");
    }

    @After("@awsmarket")
    public void cleanup() {
        if (studentCtx != null) { try { studentCtx.close(); } catch (Throwable ignored) {} studentCtx = null; }
        if (student != null) { try { ApiClient.deleteUser(student.id); } catch (Throwable ignored) {} student = null; }
    }
}
