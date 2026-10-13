package com.nuvepro.moodle.steps;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.nuvepro.moodle.config.Settings;
import com.nuvepro.moodle.pages.ChallengeActivityForm;
import com.nuvepro.moodle.pages.LabTemplatesPage;
import com.nuvepro.moodle.pages.MoodleCloudlabsIntegrationPage;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import org.testng.SkipException;

import static org.testng.Assert.assertTrue;

/**
 * Manual sheet {@code Moodle-Cloudlabs Integration} (@MCI-1 … @MCI-11 in feature). Admin smoke:
 * settings, lab templates admin, lab activity chooser/types, reports plugin listing.
 */
public class MoodleCloudlabsIntegrationSteps {
    private final TestContext ctx;
    private final MoodleCloudlabsIntegrationPage integration;
    private final ChallengeActivityForm activityForm;

    public MoodleCloudlabsIntegrationSteps(TestContext ctx) {
        this.ctx = ctx;
        this.integration = new MoodleCloudlabsIntegrationPage(ctx.page);
        this.activityForm = new ChallengeActivityForm(ctx.page);
    }

    private String courseId() {
        if (Settings.COURSE_ID.isEmpty()) throw new SkipException("COURSE_ID not set");
        return Settings.COURSE_ID;
    }

    @When("admin opens Site administration plugin overview for additional plugins")
    public void adminOpensSiteAdminPluginOverviewForAdditionalPlugins() {
        integration.openSiteAdminPluginOverviewAdditionalPlugins();
        assertTrue(ctx.page.url().contains("/admin/"),
                "Site administration plugin overview did not open (url=" + ctx.page.url() + ")");
    }

    @Then("the Nuvepro Cloudlabs activity plugin is available and enabled")
    public void theNuveproCloudlabsActivityPluginIsAvailableAndEnabled() {
        MoodleCloudlabsIntegrationPage.NuveproCloudlabsPluginStatus status =
                integration.nuveproCloudlabsPluginOnCurrentOverviewPage();
        if (!status.listed) {
            integration.openPluginOverviewActivityModulesIfNeeded();
            status = integration.nuveproCloudlabsPluginOnCurrentOverviewPage();
        }
        assertTrue(status.listed,
                "Nuvepro Cloudlabs (mod_cloudlabs) is not listed under "
                        + "Site administration → Plugins → Plugin overview → Additional plugins "
                        + "(url=" + ctx.page.url() + "). " + status.detail);
        assertTrue(status.enabled,
                "Nuvepro Cloudlabs plugin is listed but not enabled — enable it in plugin overview. "
                        + status.detail);
        System.out.println("[MCI-1] Nuvepro Cloudlabs plugin OK (listed + enabled) " + status.detail);
    }

    @When("admin opens the cloudlabs cloud server settings page")
    public void adminOpensCloudServerSettings() {
        integration.openCloudServerSettings();
        assertTrue(integration.onCloudServerSettings(),
                "Cloud Server Settings did not load (url=" + ctx.page.url() + ")");
    }

    @Then("the cloudlabs cloud server settings page is available")
    public void cloudServerSettingsPageIsAvailable() {
        assertTrue(integration.onCloudServerSettings(),
                "expected Cloud Server Settings for the cloudlabs activity plugin");
        assertAdminSettingVisible("id_s_cloudlabs_serverurl", "Cloud Server URL");
        assertTrue(integration.hasCloudlabsNamedSettingInput(),
                "expected multiple cloudlabs admin settings on the form");
    }

    @Then("the cloud server settings expose connection fields for URL company and credentials")
    public void connectionFieldsPresent() {
        assertAdminSettingVisible("id_s_cloudlabs_serverurl", "server URL");
        assertAdminSettingVisible("id_s_cloudlabs_company_id", "company id");
        assertAdminSettingVisible("id_s_cloudlabs_company_name", "company name");
        assertAdminSettingVisible("id_s_cloudlabs_admin_username", "admin username");
        assertAdminPasswordSettingPresent("id_s_cloudlabs_admin_pass", "admin password");
    }

    @Then("the cloud server settings offer a debug log toggle")
    public void debugLogTogglePresent() {
        assertTrue(integration.hasDebugLogControl(),
                "debug log enable/disable control not found on Cloud Server Settings");
        Locator debug = ctx.page.locator(
                "#id_s_cloudlabs_developerlogs, input[name='s_cloudlabs_developerlogs'][type='checkbox']");
        assertTrue(debug.count() > 0 && debug.first().isVisible(),
                "debug log control is not visible on Cloud Server Settings");
    }

    @Then("the cloud server settings include a support email field")
    public void supportEmailFieldPresent() {
        assertTrue(integration.hasSupportEmailField(),
                "support email field not found on Cloud Server Settings");
        assertAdminSettingVisible("id_s_cloudlabs_support_email", "support email");
    }

    @When("admin looks for Lab Templates in site administration")
    public void adminLooksForLabTemplatesInSiteAdmin() {
        assertTrue(integration.adminTreeLinksToLabTemplates(),
                "Lab Templates admin entry not found (no link to labtemplates.php)");
    }

    @Then("the Lab Templates admin list is reachable")
    public void labTemplatesAdminListReachable() {
        integration.openLabTemplatesList();
        assertTrue(ctx.page.url().contains("labtemplates.php"),
                "site admin Lab Templates list did not open");
        Locator table = ctx.page.locator(LabTemplatesPage.TABLE);
        assertTrue(table.count() > 0 && table.first().isVisible(),
                "Lab Templates bootstrap-table is not visible");
        assertTrue(ctx.page.locator(LabTemplatesPage.TABLE + " tbody tr[data-index]").count() > 0
                        || ctx.page.locator(".fixed-table-toolbar").count() > 0,
                "Lab Templates list did not render toolbar or data rows");
    }

    @Then("the lab template form includes access keys or links required for lab creation")
    public void labTemplateFormAccessFields() {
        assertTrue(ctx.page.url().contains("addlabtemplate.php"),
                "lab template add form URL expected (url=" + ctx.page.url() + ")");
        assertTrue(integration.addTemplateFormShowsPlanAndTeamFields(),
                "plan_id / team_id not present on add lab template form");
        assertAdminFieldVisible("#plan_id", "plan_id");
        assertAdminFieldVisible("#team_id", "team_id");
        assertTrue(integration.addTemplateFormMentionsAccessOrKeys(),
                "access keys / links not evident on lab template form (Advanced tab or access fields)");
    }

    @When("admin opens the activity chooser to add a lab activity")
    public void adminOpensActivityChooserForLab() {
        activityForm.openActivityChooserModalStrict(courseId());
    }

    @Then("the cloudlabs lab activity is listed in the activity chooser")
    public void cloudlabsLabListedInChooser() {
        assertTrue(activityForm.cloudlabsLabListedInOpenActivityChooserModal(),
                "CloudLabs Lab (mod_cloudlabs) is not visible/selectable in the Activity Chooser "
                        + "(url=" + ctx.page.url() + "). Expected: course Activity Chooser modal, "
                        + "not /course/mod.php or direct modedit.");
        String label = activityForm.cloudlabsLabChooserLabel();
        assertTrue(label.isBlank() || label.toLowerCase(java.util.Locale.ROOT).contains("lab")
                        || label.toLowerCase(java.util.Locale.ROOT).contains("cloud"),
                "Activity Chooser entry did not look like CloudLabs Lab (label='" + label + "')");
        System.out.println("[MCI-9] CloudLabs Lab in Activity Chooser OK label="
                + (label.isBlank() ? "(mod_cloudlabs option)" : label));
    }

    @When("admin opens a new cloudlabs lab activity form for integration")
    public void adminOpensNewLabActivityForm() {
        assertTrue(activityForm.openAddFormDirect(courseId()), "cloudlabs add form did not open");
        assertTrue(ctx.page.url().contains("modedit.php") && ctx.page.url().contains("cloudlabs"),
                "expected cloudlabs modedit add form (url=" + ctx.page.url() + ")");
        assertTrue(ctx.page.locator("#id_name").count() > 0 && ctx.page.locator("#id_name").first().isVisible(),
                "activity name field not visible on cloudlabs add form");
    }

    @Then("the lab activity form links activities to a lab template plan")
    public void labActivityFormLinksTemplatePlan() {
        Locator sel = ctx.page.locator("#id_lab_template_id, select[name='lab_template_id']");
        assertTrue(sel.count() > 0 && sel.first().isVisible(),
                "lab template selector not present on cloudlabs activity form");
        int options = sel.first().locator("option").count();
        assertTrue(options > 0,
                "lab template dropdown has no options (configure lab templates in site admin)");
    }

    @Then("the lab activity form offers playground guided and assessment lab types")
    public void labTypesOffered() {
        assertLabType("playground");
        assertLabType("guided");
        if (ctx.page.locator("input[name='labtype'][value='assessment']").count() > 0) {
            assertLabType("assessment");
        } else {
            assertLabType("challenge");
        }
    }

    private void assertLabType(String value) {
        Locator radio = ctx.page.locator("input[name='labtype'][value='" + value + "']");
        assertTrue(radio.count() > 0 && radio.first().isVisible(),
                "labtype " + value + " not offered as a visible option on add form");
    }

    @When("admin opens the Moodle reports index")
    public void adminOpensReportsIndex() {
        integration.openReportsIndex();
        assertTrue(ctx.page.url().contains("/report/"),
                "Moodle reports index did not open (url=" + ctx.page.url() + ")");
    }

    @Then("cloudlabs-related reports are listed when the reports plugin is installed")
    public void cloudlabsReportsListed() {
        Locator reportLinks = ctx.page.locator("a[href*='cloudlabsreport']");
        boolean listed = integration.reportsIndexListsCloudlabsReports();
        if (listed) {
            assertTrue(reportLinks.count() > 0,
                    "reports index text mentions CloudLabs but no cloudlabsreport link found");
            assertTrue(reportLinks.first().isVisible(),
                    "cloudlabs report link is not visible on the reports index");
        } else {
            ctx.page.navigate(Settings.BASE_URL + "/report/cloudlabsreport/index.php",
                    new Page.NavigateOptions()
                            .setWaitUntil(com.microsoft.playwright.options.WaitUntilState.DOMCONTENTLOADED)
                            .setTimeout(45_000));
            ctx.page.waitForTimeout(2_000);
            listed = ctx.page.url().contains("cloudlabsreport")
                    && ctx.page.locator("#region-main, table, .reportlink").count() > 0;
            assertTrue(listed,
                    "cloudlabs report plugin not reachable from index or direct URL "
                            + "(install report_cloudlabsreport?)");
        }
    }

    private void assertAdminSettingVisible(String fieldId, String label) {
        Locator f = ctx.page.locator("#" + fieldId);
        assertTrue(f.count() > 0 && f.first().isVisible(),
                label + " field (#" + fieldId + ") is not visible on Cloud Server Settings");
    }

    private void assertAdminFieldVisible(String selector, String label) {
        Locator f = ctx.page.locator(selector);
        assertTrue(f.count() > 0 && f.first().isVisible(),
                label + " (" + selector + ") is not visible");
    }

    /** Moodle password-unmask hides {@code type=password} inputs ({@code d-none}); assert label + widget. */
    private void assertAdminPasswordSettingPresent(String fieldId, String label) {
        Locator labelFor = ctx.page.locator("label[for='" + fieldId + "']");
        Locator unmask = ctx.page.locator("[data-passwordunmaskid='" + fieldId + "']");
        Locator input = ctx.page.locator("#" + fieldId);
        assertTrue(labelFor.count() > 0 && labelFor.first().isVisible(),
                label + " label (for=" + fieldId + ") is not visible on Cloud Server Settings");
        assertTrue(unmask.count() > 0 && unmask.first().isVisible(),
                label + " password-unmask control is not visible on Cloud Server Settings");
        assertTrue(input.count() > 0,
                label + " input (#" + fieldId + ") is missing from Cloud Server Settings");
    }

}
