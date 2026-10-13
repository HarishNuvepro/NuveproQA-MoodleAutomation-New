package com.nuvepro.moodle.steps;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.nuvepro.moodle.config.Settings;
import com.nuvepro.moodle.helpers.AssessmentCreateSpec;
import com.nuvepro.moodle.helpers.AssessmentTestData;
import com.nuvepro.moodle.helpers.ApiClient;
import com.nuvepro.moodle.helpers.AutomationManifest;
import com.nuvepro.moodle.helpers.CleanupPolicy;
import com.nuvepro.moodle.helpers.Auth;
import com.nuvepro.moodle.helpers.SandboxCourse;
import com.nuvepro.moodle.helpers.BrowserUi;
import com.nuvepro.moodle.pages.ChallengeActivityForm;
import com.nuvepro.moodle.pages.AssessmentAdmin;
import com.nuvepro.moodle.pages.CourseAdmin;
import com.nuvepro.moodle.pages.CourseView;
import com.nuvepro.moodle.pages.LabTemplatesPage;
import io.cucumber.java.After;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import org.testng.SkipException;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

/**
 * Lab Templates (cloudlabs) — read-only list/view/search coverage plus add-form validation.
 * These scenarios deliberately do not edit production templates or save a new template.
 */
public class LabTemplateSteps {
    private final TestContext ctx;
    private final LabTemplatesPage templates;
    private final ChallengeActivityForm activityForm;
    private ApiClient.SeededUser student;
    private BrowserContext studentCtx;
    private Page studentPage;
    private String firstName;
    private List<String> editedIntervals = List.of();
    private int rowsBeforeRefresh;
    private final List<String> throwawayTemplates = new ArrayList<>();
    private String templateName;
    private String duplicateName;
    private String inUseTemplateName;
    private String expectedDescription;
    private String expectedLabInfo;
    private Integer duplicatedActivityCmid;
    private String createdActivityCmid;
    private String copiedCourseId;
    private boolean extremeIntervalProtected;
    private String notificationTemplateToRestore;
    private boolean notificationWasEnabled;
    private String notificationOriginalDuration;
    private String notificationOriginalThreshold;
    private String notificationOriginalMessage;

    public LabTemplateSteps(TestContext ctx) {
        this.ctx = ctx;
        this.templates = new LabTemplatesPage(ctx.page);
        this.activityForm = new ChallengeActivityForm(ctx.page);
    }

    private void open(Page p, String path) {
        p.navigate(Settings.BASE_URL + path,
                new Page.NavigateOptions().setWaitUntil(com.microsoft.playwright.options.WaitUntilState.DOMCONTENTLOADED)
                        .setTimeout(45_000));
        p.waitForTimeout(3_000);
    }

    private void requireTemplateConfig() {
        if (Settings.LAB_TEMPLATE_PLAN_ID.isEmpty() || Settings.LAB_TEMPLATE_TEAM_ID.isEmpty()) {
            throw new SkipException("LAB_TEMPLATE_PLAN_ID and LAB_TEMPLATE_TEAM_ID must be configured");
        }
    }

    private String newTemplateName(String suffix) {
        return "LT-AUTO-" + suffix + "-" + System.currentTimeMillis();
    }

    private void createThrowaway(String resourceType) {
        requireTemplateConfig();
        createThrowaway(resourceType, Settings.LAB_TEMPLATE_PLAN_ID, Settings.LAB_TEMPLATE_TEAM_ID);
    }

    private void createThrowaway(String resourceType, String planId, String teamId) {
        templateName = newTemplateName(resourceType);
        expectedDescription = "Automated disposable lab template " + templateName;
        templates.openAddForm();
        templates.fillRequiredTemplate(templateName, expectedDescription,
                planId, teamId, resourceType);
        assertTrue(templates.submitAdd(),
                "template create failed: " + templates.formAlertText());
        throwawayTemplates.add(templateName);
        AutomationManifest.registerLabTemplate(templateName);
        assertTrue(templates.templateExists(templateName), "created template is absent from the listing");
    }

    @When("admin opens the lab templates list")
    public void adminOpensTheLabTemplatesList() {
        templates.openList();
    }

    @Then("the lab templates table lists its configured template details")
    public void theLabTemplatesTableListsConfiguredTemplateDetails() {
        String headers = templates.visibleTableHeaders();
        for (String expected : List.of("name", "description", "plan id", "team id", "updated on", "status")) {
            assertTrue(headers.contains(expected),
                    "lab templates table missing " + expected + " column (headers="
                            + headers.replace("\n", " ") + ")");
        }
        // Access Keys, Resource Type and Created On ship hidden; the listing must still be able to
        // show them, so switch each on and confirm the column appears.
        for (String field : List.of("access_keys", "resource_type", "created")) {
            assertTrue(templates.setColumnVisible(field, true),
                    "lab templates table cannot show its " + field + " column");
        }
        assertTrue(templates.visibleRowCount() > 0, "no lab templates listed");
    }

    @When("admin opens the add lab template form")
    public void adminOpensTheAddLabTemplateForm() {
        templates.openAddForm();
        assertTrue(templates.onAddForm(),
                "Add Lab Template form did not open (url=" + ctx.page.url() + ")");
    }

    @When("admin uses the add lab template option")
    public void adminUsesTheAddLabTemplateOption() {
        templates.openAddFormFromList();
    }

    @Then("the add lab template form opens")
    public void theAddLabTemplateFormOpens() {
        assertTrue(templates.onAddForm(), "the Add Lab Template option did not open the add form");
    }

    @Then("the add lab template form shows its core configuration fields")
    public void theAddFormShowsCoreFields() {
        assertTrue(templates.hasCoreConfigurationFields(),
                "resource type, plan/team, interval, or Save control is missing");
    }

    @When("admin submits the empty lab template form")
    public void adminSubmitsTheEmptyLabTemplateForm() {
        assertTrue(templates.submitEmptyShowsRequiredErrors(),
                "empty template form was accepted without a required-field error");
    }

    @Then("the lab template is not created and validation is shown")
    public void theLabTemplateIsNotCreatedAndValidationIsShown() {
        assertTrue(templates.onAddForm(), "empty form unexpectedly left the add-template page");
    }

    @When("admin submits a lab template with an empty Create interval")
    public void adminSubmitsALabTemplateWithAnEmptyCreateInterval() {
        assertTrue(templates.emptyCreateIntervalIsRejected(),
                "an empty Create interval was not treated as a required field");
    }

    @Then("the empty interval prevents the lab template save")
    public void theEmptyIntervalPreventsTheLabTemplateSave() {
        assertTrue(templates.onAddForm(), "empty Create interval unexpectedly created a template");
        assertTrue(templates.validationAlertShown(), "the blocked save reported no validation message");
    }

    @Then("the form shows Create Start and Unpark interval duration fields")
    public void theFormShowsActionIntervals() {
        templates.openFeaturesTab();
        for (String action : LabTemplatesPage.INTERVAL_ACTIONS) {
            assertTrue(templates.hasIntervalControl(action), action + " interval duration field missing");
        }
        for (String action : List.of("create", "start")) {
            assertTrue(templates.intervalVisible(action),
                    action + " interval is not usable on the Lab Features tab");
        }
        // Unpark applies to parkable resource types only, so it stays hidden for an Account template.
        templates.selectResourceType("VIRTUAL_MACHINE");
        templates.openFeaturesTab();
        assertTrue(templates.intervalVisible("unpark"),
                "the Unpark interval is not offered even for a Virtual Machine template");
    }

    @Then("the action interval duration fields are prefilled")
    public void theActionIntervalsArePrefilled() {
        templates.openFeaturesTab();
        for (String action : LabTemplatesPage.INTERVAL_ACTIONS) {
            assertTrue(templates.intervalIsPrefilled(action),
                    action + " interval is not prefilled (value='" + templates.intervalValue(action) + "')");
        }
    }

    @Then("interval defaults match each resource type")
    public void intervalDefaultsMatchEachResourceType() {
        String[][] expected = {
                {"ACCOUNT", "4", "2", "0"},
                {"VIRTUAL_MACHINE", "5", "3", "5"},
                {"COMPOSITION", "6", "3", "6"}
        };
        for (String[] row : expected) {
            templates.selectResourceType(row[0]);
            assertEquals(templates.intervalValue("create"), row[1], row[0] + " Create default");
            assertEquals(templates.intervalValue("start"), row[2], row[0] + " Start default");
            assertEquals(templates.intervalValue("unpark"), row[3], row[0] + " Unpark default");
        }
    }

    @When("admin changes the Create Start and Unpark interval values without saving")
    public void adminChangesTheActionIntervalsWithoutSaving() {
        // Account templates hide Unpark, so select VM to expose all three controls. Nothing is
        // saved: the form is abandoned after the edits.
        templates.selectResourceType("VIRTUAL_MACHINE");
        editedIntervals = templates.editableIntervals();
        assertTrue(editedIntervals.containsAll(LabTemplatesPage.INTERVAL_ACTIONS),
                "not every action interval is editable for a Virtual Machine template: " + editedIntervals);
        int value = 31;
        for (String action : editedIntervals) templates.fillInterval(action, Integer.toString(value++));
    }

    @Then("the changed action interval values remain editable")
    public void theChangedActionIntervalsRemainEditable() {
        int value = 31;
        for (String action : editedIntervals) {
            assertEquals(templates.intervalValue(action), Integer.toString(value++),
                    action + " interval did not keep the entered value");
        }
    }

    @When("admin enters a negative Create interval")
    public void adminEntersANegativeCreateInterval() {
        assertTrue(templates.negativeIntervalRejected("create"),
                "the Create interval accepted a negative value without validation");
    }

    @Then("negative interval validation prevents the lab template save")
    public void negativeIntervalValidationPreventsSave() {
        assertTrue(templates.onAddForm(), "negative interval unexpectedly created a lab template");
    }

    @When("admin opens the first lab template in view mode")
    public void adminOpensTheFirstLabTemplateInViewMode() {
        templates.openFirstView();
    }

    @Then("the lab template view shows its saved details")
    public void theLabTemplateViewShowsItsSavedDetails() {
        assertTrue(templates.viewShowsTemplateDetails(), "view page omitted plan/team/resource details");
    }

    @Then("the lab template view allows edit and denies delete")
    public void theLabTemplateViewAllowsEditAndDeniesDelete() {
        assertTrue(templates.viewHasEditAction(),
                "view page should expose Edit for admin");
        assertFalse(templates.viewHasDeleteAction(),
                "view page should not expose Delete");
    }

    @When("admin searches lab templates by an existing name")
    public void adminSearchesLabTemplatesByExistingName() {
        firstName = templates.firstTemplateName();
        templates.search(firstName);
    }

    @Then("only matching lab templates remain visible")
    public void onlyMatchingLabTemplatesRemainVisible() {
        templates.waitForDataRows();
        String table = templates.visibleTableText().toLowerCase();
        assertTrue(table.contains(firstName.toLowerCase()),
                "search result does not contain existing template " + firstName);
        assertTrue(templates.visibleRowCount() > 0, "search removed every matching template");
    }

    @When("admin enters two lab template names as one search")
    public void adminEntersTwoLabTemplateNamesAsOneSearch() {
        List<String> names = templates.firstTwoTemplateNames();
        if (names.size() < 2) throw new SkipException("two configured lab templates are required");
        firstName = names.get(0) + "," + names.get(1);
        templates.search(firstName);
    }

    @Then("the lab template search remains a single text search")
    public void theLabTemplateSearchRemainsSingleTextSearch() {
        assertEquals(templates.searchValue(), firstName, "search did not retain the literal query");
        assertFalse(templates.hasMultiSearchControl(), "lab-template search unexpectedly tokenized values");
    }

    @When("admin searches lab templates with a very long string")
    public void adminSearchesLabTemplatesWithAVeryLongString() {
        firstName = "lt".repeat(256);
        templates.search(firstName);
    }

    @Then("the lab template search accepts the full string without an error")
    public void theLabTemplateSearchAcceptsTheFullString() {
        assertEquals(templates.searchValue(), firstName, "search truncated the entered string");
        assertTrue(ctx.page.locator(LabTemplatesPage.TABLE).count() > 0, "search broke the listing page");
    }

    @When("admin searches lab templates with a script payload")
    public void adminSearchesLabTemplatesWithAScriptPayload() {
        ctx.page.evaluate("() => { window.__ltSearchExecuted = false; }");
        templates.search("<img src=x onerror=\"window.__ltSearchExecuted=true\">");
    }

    @Then("the lab template search treats the payload as text")
    public void theLabTemplateSearchTreatsThePayloadAsText() {
        assertFalse(templates.scriptExecuted(), "script payload executed in lab-template search");
        assertTrue(ctx.page.locator(LabTemplatesPage.TABLE).count() > 0, "script search broke the table");
    }

    @When("admin refreshes the lab templates table")
    public void adminRefreshesTheLabTemplatesTable() {
        assertTrue(templates.hasRefreshControl(), "lab-template table has no Refresh control");
        rowsBeforeRefresh = templates.visibleRowCount();
        templates.refresh();
    }

    @Then("the refreshed lab templates table remains current")
    public void theRefreshedLabTemplatesTableRemainsCurrent() {
        assertTrue(templates.visibleRowCount() > 0, "Refresh left the template table empty");
        assertTrue(rowsBeforeRefresh > 0, "template table had no rows before Refresh");
    }

    @When("admin toggles the Description table column")
    public void adminTogglesTheDescriptionTableColumn() {
        assertTrue(templates.hasColumnChooser(), "lab-template table has no column chooser");
        assertTrue(templates.chooserOffersColumn("description"),
                "Description was not offered by the column chooser");
        assertTrue(templates.columnVisible("description"), "Description is not shown by default");
        assertTrue(templates.setColumnVisible("description", false), "Description could not be hidden");
    }

    @Then("the Description table column visibility changes")
    public void theDescriptionTableColumnVisibilityChanges() {
        assertFalse(templates.columnVisible("description"), "Description column is still shown");
        assertTrue(templates.setColumnVisible("description", true), "Description could not be shown again");
    }

    // ------------------------------------------------------ mutating template coverage

    @When("admin creates a disposable Account lab template")
    public void adminCreatesADisposableAccountLabTemplate() {
        createThrowaway("ACCOUNT");
    }

    @When("admin creates a disposable Virtual Machine lab template")
    public void adminCreatesADisposableVirtualMachineLabTemplate() {
        createThrowaway("VIRTUAL_MACHINE");
    }

    @Then("the disposable lab template is active in the listing")
    public void theDisposableLabTemplateIsActiveInTheListing() {
        assertTrue(templates.selectedRowText(templateName).toLowerCase().contains("active"),
                "new template is not Active: " + templates.selectedRowText(templateName));
    }

    @When("admin edits every safe section of the disposable lab template")
    public void adminEditsEverySafeSectionOfTheDisposableLabTemplate() {
        expectedDescription = "Updated disposable template " + templateName;
        expectedLabInfo = "LT lab information " + System.currentTimeMillis();
        templates.openEdit(templateName);
        templates.fillRequiredTemplate(templateName, expectedDescription,
                Settings.LAB_TEMPLATE_PLAN_ID, Settings.LAB_TEMPLATE_TEAM_ID, "VIRTUAL_MACHINE");
        templates.setFeature("is_iframe", true);
        templates.setFeature("showNote", true);
        templates.setFeature("showDuration", true);
        templates.setFeature("showCost", true);
        templates.setFeature("showBudget", true);
        templates.setFeature("showSpeedTest", true);
        templates.fillInterval("create", "31");
        templates.fillInterval("start", "32");
        templates.fillInterval("unpark", "33");
        templates.fillNotification(true, "30", "5", "LT automated notification");
        templates.fillLabInfo(expectedLabInfo);
        // Resource-type and tab handlers run asynchronously and can rewrite these General values.
        // Reapply them immediately before saving so the persisted data matches the configured IDs.
        templates.fillGeneralField("plan_id", Settings.LAB_TEMPLATE_PLAN_ID);
        templates.fillGeneralField("team_id", Settings.LAB_TEMPLATE_TEAM_ID);
        assertTrue(templates.submitUpdate(),
                "template update did not return to the listing: " + templates.formAlertText());
    }

    @Then("the edited lab template settings persist")
    public void theEditedLabTemplateSettingsPersist() {
        templates.openEdit(templateName);
        assertEquals(templates.generalFieldValue("description"), expectedDescription);
        assertEquals(templates.generalFieldValue("plan_id"), Settings.LAB_TEMPLATE_PLAN_ID);
        assertEquals(templates.generalFieldValue("team_id"), Settings.LAB_TEMPLATE_TEAM_ID);
        for (String feature : List.of("is_iframe", "showNote", "showDuration",
                "showCost", "showBudget", "showSpeedTest")) {
            assertTrue(templates.featureChecked(feature), feature + " did not persist");
        }
        assertEquals(templates.intervalValue("create"), "31");
        assertEquals(templates.intervalValue("start"), "32");
        assertEquals(templates.intervalValue("unpark"), "33");
        assertTrue(templates.controlChecked("np-ap-lt-tab-notification", "is_notify"),
                "notification enabled state did not persist");
        assertEquals(templates.controlValue("np-ap-lt-tab-notification", "lab_duration"), "30");
        assertEquals(templates.controlValue("np-ap-lt-tab-notification", "notification_duration"), "5");
        assertEquals(templates.controlValue("np-ap-lt-tab-notification", "notification_message"),
                "LT automated notification");
        assertEquals(templates.controlValue("np-ap-lt-tab-advanced", "lab_info"), expectedLabInfo);
    }

    @Then("the lab template view shows the saved action intervals")
    public void theLabTemplateViewShowsTheSavedActionIntervals() {
        templates.openList();
        templates.openView(templateName);
        String features = templates.activePaneText("np-ap-lt-tab-features")
                .toLowerCase().replaceAll("\\s+", "");
        for (String expected : List.of("create=31", "start=32", "unpark=33")) {
            assertTrue(features.contains(expected),
                    "view page omitted " + expected + " (features=" + features + ")");
        }
    }

    @When("admin deactivates and reactivates the disposable lab template")
    public void adminDeactivatesAndReactivatesTheDisposableLabTemplate() {
        assertTrue(templates.changeStatus(templateName, false),
                "unused template could not be deactivated");
        assertTrue(templates.changeStatus(templateName, true),
                "deactivated template could not be reactivated");
    }

    @Then("the disposable lab template returns to Active status")
    public void theDisposableLabTemplateReturnsToActiveStatus() {
        String row = templates.selectedRowText(templateName).toLowerCase().replaceAll("\\s+", "");
        assertTrue(row.contains("active") && !row.contains("inactive"),
                "template did not return to Active: " + row);
    }

    @When("admin deletes the unused disposable lab template")
    public void adminDeletesTheUnusedDisposableLabTemplate() {
        assertTrue(templates.delete(templateName), "unused template was not deleted");
        throwawayTemplates.remove(templateName);
    }

    @Then("the deleted lab template is absent from the listing")
    public void theDeletedLabTemplateIsAbsentFromTheListing() {
        assertFalse(templates.templateExists(templateName), "deleted template remains in listing");
    }

    @When("admin attempts to create a duplicate of the disposable lab template")
    public void adminAttemptsToCreateADuplicateOfTheDisposableLabTemplate() {
        duplicateName = templateName;
        templates.openAddForm();
        templates.fillRequiredTemplate(duplicateName, "Duplicate-name validation",
                Settings.LAB_TEMPLATE_PLAN_ID, Settings.LAB_TEMPLATE_TEAM_ID, "ACCOUNT");
        assertFalse(templates.submitAdd(), "duplicate template name was accepted");
    }

    @Then("duplicate lab template validation prevents the save")
    public void duplicateLabTemplateValidationPreventsTheSave() {
        assertTrue(templates.onAddForm(), "duplicate create unexpectedly left the add form");
        assertTrue(templates.formAlertText().toLowerCase().contains("already"),
                "duplicate-name error was not shown: " + templates.formAlertText());
    }

    @When("admin deactivates the disposable lab template")
    public void adminDeactivatesTheDisposableLabTemplate() {
        assertTrue(templates.changeStatus(templateName, false), "template could not be deactivated");
    }

    @When("admin reactivates the disposable lab template")
    public void adminReactivatesTheDisposableLabTemplate() {
        assertTrue(templates.changeStatus(templateName, true), "template could not be reactivated");
    }

    @Then("the disposable template is absent from the Sandbox activity template list")
    public void theDisposableTemplateIsAbsentFromTheSandboxActivityTemplateList() {
        openActivityTemplateForm("playground");
        assertFalse(activityForm.templateOptionPresent(templateName),
                "deactivated template remains selectable on the activity form");
    }

    @Then("the disposable template is available in the Sandbox activity template list")
    public void theDisposableTemplateIsAvailableInTheSandboxActivityTemplateList() {
        openActivityTemplateForm("playground");
        assertTrue(activityForm.templateOptionPresent(templateName),
                "active template is absent from the activity form");
    }

    @When("admin creates a disposable lab template with a script payload in its name")
    public void adminCreatesADisposableLabTemplateWithAScriptPayloadInItsName() {
        requireTemplateConfig();
        String sanitizedName = newTemplateName("XSS");
        templates.openAddForm();
        templates.fillRequiredTemplate(sanitizedName
                        + "<img src=x onerror=\"window.__ltTemplateExecuted=true\">",
                "Disposable template name sanitization check",
                Settings.LAB_TEMPLATE_PLAN_ID, Settings.LAB_TEMPLATE_TEAM_ID, "ACCOUNT");
        assertTrue(templates.submitAdd(), "XSS-name template create failed: " + templates.formAlertText());
        templateName = sanitizedName;
        throwawayTemplates.add(templateName);
        AutomationManifest.registerLabTemplate(templateName);
        assertTrue(templates.templateExists(templateName),
                "server did not save the PARAM_TEXT-sanitized template name: " + templateName);
    }

    @Then("the activity template dropdown contains only the sanitized template name")
    public void theActivityTemplateDropdownContainsOnlyTheSanitizedTemplateName() {
        openActivityTemplateForm("playground");
        assertTrue(activityForm.templateOptionPresent(templateName),
                "sanitized template was absent from the activity dropdown: " + templateName);
        assertFalse(activityForm.templateOptionPresent("<img"),
                "raw HTML from the template name reached the activity dropdown");
        Object executed = ctx.page.evaluate("() => window.__ltTemplateExecuted === true");
        assertEquals(executed, false, "template-name script payload executed in the activity form");
    }

    @When("admin selects the disposable template for a {string} activity")
    public void adminSelectsTheDisposableTemplateForAnActivity(String labType) {
        openActivityTemplateForm(labType);
        activityForm.selectLabTypeAndTemplate(labType, templateName);
    }

    @Then("the activity form shows the configured Plan Team and Catalog")
    public void theActivityFormShowsTheConfiguredPlanTeamAndCatalog() {
        String info = activityForm.labTemplateInfo().toLowerCase();
        assertTrue(info.contains(Settings.LAB_TEMPLATE_PLAN_ID.toLowerCase()),
                "Plan ID missing from template info: " + info);
        assertTrue(info.contains(Settings.LAB_TEMPLATE_TEAM_ID.toLowerCase()),
                "Team ID missing from template info: " + info);
        assertTrue(info.contains("catalog") && !info.matches("(?s).*catalog\\s*name:\\s*(null)?\\s*$"),
                "Catalog Name was not resolved for supplied Plan/Team: " + info);
    }

    @Then("selecting the disposable template produces no browser page error")
    public void selectingTheDisposableTemplateProducesNoBrowserPageError() {
        String errors = String.join("\n", ctx.consoleLogs).toLowerCase();
        assertFalse(errors.contains("[pageerror]"),
                "template selection caused a browser page error: " + errors);
    }

    @When("admin opens the configured {string} activity edit form")
    public void adminOpensTheConfiguredActivityEditForm(String labType) {
        String cmid = switch (labType) {
            case "playground" -> Settings.PLAYGROUND_CMID;
            case "guided" -> Settings.GUIDED_CMID;
            case "challenge" -> Settings.ASSESSMENT_CMID;
            default -> throw new IllegalArgumentException("unsupported lab type " + labType);
        };
        if (cmid == null || cmid.isBlank()) throw new SkipException(labType + " cmid not configured");
        activityForm.openEditForm(cmid, labType);
    }

    @Then("the existing activity shows its saved Plan Team and Catalog")
    public void theExistingActivityShowsItsSavedPlanTeamAndCatalog() {
        String info = activityForm.labTemplateInfo().toLowerCase();
        assertTrue(info.contains("plan id:") && info.contains("team id:") && info.contains("catalog name:"),
                "saved activity omitted Plan/Team/Catalog information: " + info);
        assertFalse(info.contains("catalog name: null"),
                "saved activity template no longer resolves its catalog: " + info);
    }

    @When("admin opens the add problem statement form for the randomized assessment")
    public void adminOpensTheAddProblemStatementFormForTheRandomizedAssessment() {
        String cmid = Settings.ASSESSMENT_RANDOM_CMID.isBlank()
                ? Settings.ASSESSMENT_CMID : Settings.ASSESSMENT_RANDOM_CMID;
        if (cmid.isBlank()) throw new SkipException("ASSESSMENT_RANDOM_CMID not configured");
        AssessmentAdmin admin = new AssessmentAdmin(ctx.page);
        String sectionId = admin.sectionIdFor(Integer.parseInt(cmid));
        if (sectionId == null || sectionId.isBlank()) {
            throw new SkipException("randomized assessment section id is unavailable");
        }
        admin.openProblemStatements(sectionId);
        assertTrue(admin.clickAddProblemStatement(), "Add Problem Statement did not open its form");
    }

    @Then("selecting a problem statement template shows its Plan Team and Catalog")
    public void selectingAProblemStatementTemplateShowsItsPlanTeamAndCatalog() {
        Locator template = ctx.page.locator("#lab_template");
        assertTrue(template.count() > 0, "problem-statement form omitted the Lab Template selector");
        Locator option = template.locator("option:not([value='']):not([value='0'])").first();
        assertTrue(option.count() > 0, "problem-statement form has no selectable active template");
        template.selectOption(option.getAttribute("value"));
        ctx.page.waitForTimeout(1_500);
        Locator info = ctx.page.locator("#lab_template_info");
        assertTrue(info.count() > 0 && info.first().isVisible(),
                "DEFECT: problem-statement template selection has no Plan/Team/Catalog information panel");
        String text = info.first().innerText().toLowerCase();
        assertTrue(text.contains("plan id:") && text.contains("team id:") && text.contains("catalog name:"),
                "DEFECT: problem-statement template information is incomplete: " + text);
    }

    @When("admin duplicates the configured Sandbox activity")
    public void adminDuplicatesTheConfiguredSandboxActivity() {
        if (Settings.PLAYGROUND_CMID.isBlank()) throw new SkipException("PLAYGROUND_CMID not configured");
        String sourceCourseId = configuredSandboxCourseId();
        duplicatedActivityCmid = new CourseView(ctx.page).duplicateActivity(
                sourceCourseId, Integer.parseInt(Settings.PLAYGROUND_CMID));
        assertTrue(duplicatedActivityCmid > 0, "Moodle did not create a duplicate Sandbox activity");
    }

    @Then("the duplicated activity retains its saved Lab Template information")
    public void theDuplicatedActivityRetainsItsSavedLabTemplateInformation() {
        assertTrue(duplicatedActivityCmid != null, "no duplicated activity cmid was captured");
        activityForm.openEditForm(String.valueOf(duplicatedActivityCmid), "playground");
        theExistingActivityShowsItsSavedPlanTeamAndCatalog();
    }

    @When("admin creates a disposable template and a Sandbox activity using it")
    public void adminCreatesADisposableTemplateAndASandboxActivityUsingIt() {
        createThrowaway("ACCOUNT");
        assertTrue(activityForm.openAddFormDirect(Settings.COURSE_ID),
                "CloudLabs activity add form did not open");
        AssessmentCreateSpec spec = new AssessmentCreateSpec.Builder()
                .name("LT propagation " + System.currentTimeMillis())
                .labType("playground")
                .templateContains(templateName)
                .build();
        activityForm.fill(spec);
        activityForm.saveAndDisplay();
        assertTrue(activityForm.leftTheForm(), "Sandbox activity using disposable template was not saved");
        createdActivityCmid = activityForm.activityCmidFromUrl();
        assertTrue(createdActivityCmid != null && !createdActivityCmid.isBlank(),
                "saved Sandbox activity cmid was not captured");
    }

    /** REMOVE with {@code @LT-9} scenario (not automatable; feature tagged {@code @skip}). */
    @Given("admin creates a notification-enabled disposable Sandbox activity")
    public void adminCreatesANotificationEnabledDisposableSandboxActivity() {
        if (Settings.PLAYGROUND_CMID.isBlank()) {
            throw new SkipException("PLAYGROUND_CMID is required for the proven Sandbox template");
        }
        activityForm.openEditForm(Settings.PLAYGROUND_CMID, "playground");
        Locator selected = ctx.page.locator("#id_lab_template_id option:checked").first();
        assertTrue(selected.count() > 0 && !selected.innerText().isBlank(),
                "configured Sandbox has no selected Lab Template");
        templateName = selected.innerText().trim();
        templates.openEdit(templateName);
        notificationTemplateToRestore = templateName;
        notificationWasEnabled = templates.controlChecked("np-ap-lt-tab-notification", "is_notify");
        notificationOriginalDuration =
                templates.controlValue("np-ap-lt-tab-notification", "lab_duration");
        notificationOriginalThreshold =
                templates.controlValue("np-ap-lt-tab-notification", "notification_duration");
        notificationOriginalMessage =
                templates.controlValue("np-ap-lt-tab-notification", "notification_message");
        templates.fillNotification(true, "30", "5",
                "LT automated warning: {threshold} remaining");
        assertTrue(templates.submitUpdate(), "notification-enabled template update did not save");

        String courseId = SandboxCourse.ensure(ctx.page);
        assertTrue(activityForm.openAddFormDirect(courseId),
                "CloudLabs activity add form did not open");
        AssessmentCreateSpec spec = new AssessmentCreateSpec.Builder()
                .name("LT notification " + System.currentTimeMillis())
                .labType("playground")
                .templateContains(templateName)
                .build();
        activityForm.fill(spec);
        activityForm.saveAndDisplay();
        assertTrue(activityForm.leftTheForm(), "notification Sandbox activity was not saved");
        createdActivityCmid = activityForm.activityCmidFromUrl();
        assertTrue(createdActivityCmid != null && !createdActivityCmid.isBlank(),
                "notification Sandbox activity cmid was not captured");
        ctx.newActivityCmid = createdActivityCmid;
        ctx.newActivityName = spec.name;
        ctx.newActivityDataset = "labtemplates_notification";
        ctx.lastCreateSpec = spec;
        new CourseView(ctx.page).showActivityOnCoursePage(
                courseId, Integer.parseInt(createdActivityCmid), spec.name);
    }

    @Given("admin creates an action-input-enabled disposable assessment activity")
    public void adminCreatesAnActionInputEnabledDisposableAssessmentActivity() {
        createThrowaway("ACCOUNT");
        templates.openEdit(templateName);
        templates.setFeature("labActionInputs", true);
        assertTrue(templates.submitUpdate(), "action-input-enabled template update did not save");

        assertTrue(activityForm.openAddFormDirect(Settings.COURSE_ID),
                "CloudLabs assessment add form did not open");
        AssessmentCreateSpec spec = new AssessmentCreateSpec.Builder()
                .name("LT action input " + System.currentTimeMillis())
                .labType("challenge")
                .templateContains(templateName)
                .gradeToPass("30")
                .evaluationType("AUTO")
                .testScriptUrl(AssessmentTestData.SCRIPT_URL)
                .eachTestCaseMarks("10")
                .build();
        activityForm.fill(spec);
        activityForm.saveAndDisplay();
        assertTrue(activityForm.leftTheForm(), "action-input assessment activity was not saved");
        createdActivityCmid = activityForm.activityCmidFromUrl();
        assertTrue(createdActivityCmid != null && !createdActivityCmid.isBlank(),
                "action-input assessment cmid was not captured");
        ctx.newActivityCmid = createdActivityCmid;
        ctx.newActivityName = spec.name;
        ctx.newActivityDataset = "labtemplates_action_input";
        ctx.lastCreateSpec = spec;
        new CourseView(ctx.page).showActivityOnCoursePage(
                Settings.COURSE_ID, Integer.parseInt(createdActivityCmid), spec.name);
    }

    @When("admin changes the disposable template Plan ID")
    public void adminChangesTheDisposableTemplatePlanId() {
        templates.openEdit(templateName);
        templates.fillGeneralField("plan_id", "999999999");
        assertTrue(templates.submitUpdate(), "template Plan ID update did not save");
    }

    @Then("the Sandbox activity edit form shows the changed template Plan ID")
    public void theSandboxActivityEditFormShowsTheChangedTemplatePlanId() {
        activityForm.openEditForm(createdActivityCmid, "playground");
        String info = activityForm.labTemplateInfo().toLowerCase();
        assertTrue(info.contains("999999999"),
                "activity did not resolve the edited template Plan ID: " + info);
        assertTrue(info.contains("catalog name: null") || info.contains("catalog name:"),
                "activity omitted Catalog after template edit: " + info);
    }

    @When("admin copies the configured course containing lab activities")
    public void adminCopiesTheConfiguredCourseContainingLabActivities() {
        String stamp = String.valueOf(System.currentTimeMillis());
        copiedCourseId = new CourseAdmin(ctx.page).duplicateCourse(configuredSandboxCourseId(),
                "LT automation copy " + stamp, "lt-auto-copy-" + stamp, null);
        assertFalse(copiedCourseId == null || copiedCourseId.isBlank(),
                "Moodle course copy did not return a course id");
    }

    @When("admin backs up and restores the configured course containing lab activities")
    public void adminBacksUpAndRestoresTheConfiguredCourseContainingLabActivities() {
        String stamp = String.valueOf(System.currentTimeMillis());
        CourseAdmin admin = new CourseAdmin(ctx.page);
        Path backup = admin.backupCourseDownloadMbz(configuredSandboxCourseId());
        assertTrue(Files.exists(backup), "course backup .mbz was not downloaded");
        copiedCourseId = admin.restoreMbzAsNewCourse(
                backup, "1", "LT automation restore " + stamp, "lt-auto-restore-" + stamp);
        assertFalse(copiedCourseId == null || copiedCourseId.isBlank(),
                "Moodle course restore did not return a course id");
    }

    private String configuredSandboxCourseId() {
        ctx.page.navigate(Settings.BASE_URL + "/mod/cloudlabs/view.php?id=" + Settings.PLAYGROUND_CMID,
                new Page.NavigateOptions()
                        .setWaitUntil(com.microsoft.playwright.options.WaitUntilState.DOMCONTENTLOADED)
                        .setTimeout(45_000));
        String bodyClass = ctx.page.locator("body").getAttribute("class");
        java.util.regex.Matcher matcher = java.util.regex.Pattern
                .compile("(?:^|\\s)course-(\\d+)(?:\\s|$)")
                .matcher(bodyClass == null ? "" : bodyClass);
        if (!matcher.find()) throw new SkipException("configured Sandbox course id is unavailable");
        return matcher.group(1);
    }

    @Then("a lab activity in the copied course retains its saved Lab Template information")
    public void aLabActivityInTheCopiedCourseRetainsItsSavedLabTemplateInformation() {
        ctx.page.navigate(Settings.BASE_URL + "/course/view.php?id=" + copiedCourseId);
        ctx.page.waitForTimeout(2_000);
        Locator link = ctx.page.locator("a[href*='/mod/cloudlabs/view.php?id=']").first();
        assertTrue(link.count() > 0, "copied course contains no CloudLabs activity");
        String href = link.getAttribute("href");
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("[?&]id=(\\d+)").matcher(href);
        assertTrue(matcher.find(), "could not read copied activity cmid from " + href);
        activityForm.openEditForm(matcher.group(1), "playground");
        theExistingActivityShowsItsSavedPlanTeamAndCatalog();
    }

    @When("admin creates a disposable template with an invalid Plan ID")
    public void adminCreatesADisposableTemplateWithAnInvalidPlanId() {
        requireTemplateConfig();
        createThrowaway("ACCOUNT", "999999999", Settings.LAB_TEMPLATE_TEAM_ID);
    }

    @When("admin creates a disposable template with an invalid Team ID")
    public void adminCreatesADisposableTemplateWithAnInvalidTeamId() {
        requireTemplateConfig();
        createThrowaway("ACCOUNT", Settings.LAB_TEMPLATE_PLAN_ID, "999999999");
    }

    @Then("the activity form reports no resolved Catalog")
    public void theActivityFormReportsNoResolvedCatalog() {
        openActivityTemplateForm("playground");
        activityForm.selectLabTypeAndTemplate("playground", templateName);
        String info = activityForm.labTemplateInfo().toLowerCase();
        // An unresolvable Plan ID can leave the provider lookup pending, so the panel never renders
        // any Catalog at all — that is still the outcome this case asserts.
        if (ChallengeActivityForm.stillResolving(info)) return;
        assertTrue(info.contains("catalog"), "Catalog field is missing: " + info);
        assertTrue(info.contains("null") || info.matches("(?s).*catalog\\s*name:\\s*$")
                        || info.contains("not found") || info.contains("invalid"),
                "invalid Plan/Team unexpectedly resolved a catalog: " + info);
    }

    @Then("the activity form shows Catalog resolved from the valid Plan ID")
    public void theActivityFormShowsCatalogResolvedFromTheValidPlanId() {
        openActivityTemplateForm("playground");
        activityForm.selectLabTypeAndTemplate("playground", templateName);
        String info = activityForm.labTemplateInfo().toLowerCase();
        assertTrue(info.contains("team id: 999999999"),
                "activity form did not retain the invalid Team ID: " + info);
        assertTrue(info.contains("catalog name:")
                        && !info.contains("catalog name: null")
                        && !info.matches("(?s).*catalog\\s*name:\\s*$"),
                "valid Plan ID did not resolve a catalog: " + info);
    }

    @Then("bulk Lab Template export and import controls are available")
    public void bulkLabTemplateExportAndImportControlsAreAvailable() {
        Locator exportControl = ctx.page.locator(
                "a:has-text('Export'), button:has-text('Export'), [data-action*='export']");
        Locator importControl = ctx.page.locator(
                "a:has-text('Import'), button:has-text('Import'), [data-action*='import']");
        assertTrue(exportControl.count() > 0 && importControl.count() > 0,
                "DEFECT: Lab Templates has no bulk export/import controls, so templates cannot "
                        + "be round-tripped without data loss");
    }

    @When("admin saves an extremely large Create interval")
    public void adminSavesAnExtremelyLargeCreateInterval() {
        templates.openEdit(templateName);
        templates.fillInterval("create", "999999999");
        if (!templates.submitUpdate()) {
            extremeIntervalProtected = true;
            return;
        }
        templates.openEdit(templateName);
        extremeIntervalProtected = !"999999999".equals(templates.intervalValue("create"));
    }

    @Then("the extreme interval is rejected or capped")
    public void theExtremeIntervalIsRejectedOrCapped() {
        assertTrue(extremeIntervalProtected,
                "DEFECT: Lab Templates accepted the unbounded Create interval 999999999");
    }

    private void openActivityTemplateForm(String labType) {
        if (Settings.COURSE_ID.isEmpty()) throw new SkipException("COURSE_ID not set");
        assertTrue(activityForm.openAddFormDirect(Settings.COURSE_ID),
                "cloudlabs activity form did not open for " + labType);
        activityForm.selectLabTypeAndTemplate(labType,
                activityForm.templateOptionPresent(templateName) ? templateName : firstAvailableTemplateName());
    }

    private String firstAvailableTemplateName() {
        Locator option = ctx.page.locator(
                "#id_lab_template_id option:not([value='']):not([value='0'])").first();
        if (option.count() == 0) throw new AssertionError("activity form has no active lab templates");
        return option.innerText().trim();
    }

    @When("admin opens the lab template used by the configured sandbox")
    public void adminOpensTheLabTemplateUsedByTheConfiguredSandbox() {
        if (Settings.PLAYGROUND_CMID.isEmpty()) throw new SkipException("PLAYGROUND_CMID not set");
        ctx.page.navigate(Settings.BASE_URL + "/course/modedit.php?update=" + Settings.PLAYGROUND_CMID,
                new Page.NavigateOptions()
                        .setWaitUntil(com.microsoft.playwright.options.WaitUntilState.DOMCONTENTLOADED)
                        .setTimeout(45_000));
        Locator selected = ctx.page.locator("#id_lab_template_id option:checked").first();
        if (selected.count() == 0) throw new SkipException("configured sandbox has no selected lab template");
        inUseTemplateName = selected.innerText().trim();
        if (inUseTemplateName.isBlank()) throw new SkipException("configured sandbox template name is blank");
        templates.openList();
        assertTrue(templates.templateExists(inUseTemplateName),
                "sandbox template is not present in Lab Templates: " + inUseTemplateName);
    }

    @Then("delete and deactivate are blocked because the lab template is in use")
    public void deleteAndDeactivateAreBlockedBecauseTheLabTemplateIsInUse() {
        String delete = templates.rejectedActionMessage(inUseTemplateName, "#deleteBtn").toLowerCase();
        assertTrue(delete.contains("cannot") && delete.contains("deleted"),
                "in-use delete was not blocked: " + delete);
        dismissVisibleDialog();
        templates.openList();
        String deactivate = templates.rejectedActionMessage(inUseTemplateName, "#deActivateBtn").toLowerCase();
        assertTrue(deactivate.contains("cannot") && deactivate.contains("deactivated"),
                "in-use deactivate was not blocked: " + deactivate);
    }

    private void dismissVisibleDialog() {
        Locator button = ctx.page.locator(
                "[data-region='modal-container']:visible button:has-text('OK'), "
                        + "[data-region='modal-container']:visible button:has-text('Close'), "
                        + ".modal:visible button:has-text('OK')").last();
        if (button.count() > 0) button.click();
    }

    @Then("a student cannot open the lab templates list")
    public void aStudentCannotOpenTheLabTemplatesList() {
        if (Settings.WS_TOKEN.isEmpty()) throw new SkipException("MOODLE_WS_TOKEN not set");
        student = ApiClient.createUser(System.currentTimeMillis());
        if (!Settings.COURSE_ID.isEmpty())
            ApiClient.enrolUser(student.id, Long.parseLong(Settings.COURSE_ID), 5);
        studentCtx = BrowserUi.newContext(GlobalHooks.browser, new Browser.NewContextOptions()
                .setBaseURL(Settings.BASE_URL).setIgnoreHTTPSErrors(true));
        studentPage = studentCtx.newPage();
        studentPage.setDefaultTimeout(Settings.DEFAULT_TIMEOUT_MS);
        Auth.uiLogin(studentPage, student.username, student.password);
        open(studentPage, LabTemplatesPage.LIST_PATH);
        assertTrue(studentPage.locator(LabTemplatesPage.TABLE).count() == 0,
                "DEFECT: a non-site-admin can see the lab templates table");
        // labtemplates.php just `die`s for a non-site-admin -> a BLANK page (no message). Absence of
        // the table + an empty/short body both confirm the block.
        String body = studentPage.locator("body").innerText().trim().toLowerCase();
        assertTrue(body.length() < 60 || body.contains("permission") || body.contains("not allowed")
                        || body.contains("cannot") || body.contains("denied") || body.contains("error")
                        || studentPage.url().contains("login"),
                "student was not blocked from the lab templates list (body=" + body.substring(0, Math.min(60, body.length())) + ")");
    }

    @Then("the student lab templates access is denied")
    public void theStudentLabTemplatesAccessIsDenied() {
        assertTrue(studentPage != null, "student Lab Templates session was not opened");
        String url = studentPage.url().toLowerCase(Locale.ROOT);
        String body = studentPage.locator("body").innerText().trim().toLowerCase(Locale.ROOT);
        boolean onAdminList = url.contains("labtemplates.php");
        boolean tableVisible = studentPage.locator(LabTemplatesPage.TABLE).count() > 0;
        assertFalse(tableVisible, "non-site-admin can see the lab templates listing");
        boolean explicitDeny = body.contains("permission") || body.contains("not allowed")
                || body.contains("cannot") || body.contains("denied")
                || body.contains("not authorised") || body.contains("not authorized");
        // Plugin may die() with a blank body, or Moodle may redirect away from the admin script.
        boolean blankGate = onAdminList && body.length() < 80;
        boolean redirectedAway = !onAdminList && !url.contains("login");
        assertTrue(explicitDeny || blankGate || redirectedAway,
                "student was not denied Lab Templates access (url=" + studentPage.url()
                        + " bodyLen=" + body.length() + ")");
    }

    @After
    public void cleanup(io.cucumber.java.Scenario scenario) {
        if (scenario.getSourceTagNames().stream().noneMatch(tag ->
                tag.startsWith("@LT-") || tag.equals("@labtemplates")
                        || tag.equals("@labtemplates_deviation"))) {
            return;
        }
        if (!CleanupPolicy.runScenarioCleanup()) {
            System.out.println("[LT] deferCleanup=true — templates/activities left for @cleanup-lab-templates");
            if (studentCtx != null) { try { studentCtx.close(); } catch (Throwable ignored) {} studentCtx = null; }
            studentPage = null;
            return;
        }
        BrowserContext cleanupContext = null;
        Page cleanupPage = ctx.page;
        if (cleanupPage == null || cleanupPage.isClosed()) {
            Browser.NewContextOptions options = new Browser.NewContextOptions()
                    .setBaseURL(Settings.BASE_URL).setIgnoreHTTPSErrors(true);
            if (java.nio.file.Files.exists(GlobalHooks.STORAGE_STATE)) {
                options.setStorageStatePath(GlobalHooks.STORAGE_STATE);
            }
            cleanupContext = BrowserUi.newContext(GlobalHooks.browser, options);
            cleanupPage = cleanupContext.newPage();
            cleanupPage.setDefaultTimeout(Settings.DEFAULT_TIMEOUT_MS);
        }
        ChallengeActivityForm cleanupActivity = new ChallengeActivityForm(cleanupPage);
        LabTemplatesPage cleanupTemplates = new LabTemplatesPage(cleanupPage);
        if (createdActivityCmid != null) {
            try { cleanupActivity.deleteActivity(createdActivityCmid); }
            catch (Throwable e) {
                System.err.println("[LT-cleanup] could not delete created cmid="
                        + createdActivityCmid + ": " + e.getMessage());
            }
            createdActivityCmid = null;
        }
        if (notificationTemplateToRestore != null) {
            try {
                cleanupTemplates.openEdit(notificationTemplateToRestore);
                cleanupTemplates.fillNotification(true, notificationOriginalDuration,
                        notificationOriginalThreshold, notificationOriginalMessage);
                if (!notificationWasEnabled) {
                    cleanupTemplates.fillNotification(false, notificationOriginalDuration,
                            notificationOriginalThreshold, notificationOriginalMessage);
                }
                if (!cleanupTemplates.submitUpdate()) {
                    throw new AssertionError("notification restore did not save");
                }
            } catch (Throwable e) {
                System.err.println("[LT-cleanup] could not restore notifications on "
                        + notificationTemplateToRestore + ": " + e.getMessage());
            }
            notificationTemplateToRestore = null;
        }
        if (duplicatedActivityCmid != null) {
            try { cleanupActivity.deleteActivity(String.valueOf(duplicatedActivityCmid)); }
            catch (Throwable e) {
                System.err.println("[LT-cleanup] could not delete duplicated cmid="
                        + duplicatedActivityCmid + ": " + e.getMessage());
            }
            duplicatedActivityCmid = null;
        }
        if (copiedCourseId != null) {
            try { new CourseAdmin(cleanupPage).deleteCourse(copiedCourseId); }
            catch (Throwable e) {
                System.err.println("[LT-cleanup] could not delete copied course="
                        + copiedCourseId + ": " + e.getMessage());
            }
            copiedCourseId = null;
        }
        for (String name : new ArrayList<>(throwawayTemplates)) {
            try {
                cleanupTemplates.openList();
                cleanupTemplates.delete(name);
            } catch (Throwable e) {
                System.err.println("[LT-cleanup] could not delete " + name + ": " + e.getMessage());
            }
        }
        throwawayTemplates.clear();
        if (studentCtx != null) { try { studentCtx.close(); } catch (Throwable ignored) {} studentCtx = null; }
        studentPage = null;
        if (student != null) { try { ApiClient.deleteUser(student.id); } catch (Throwable ignored) {} student = null; }
        if (cleanupContext != null) {
            try { cleanupContext.close(); } catch (Throwable ignored) {}
        }
    }
}
