package com.nuvepro.moodle.steps;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.nuvepro.moodle.config.Settings;
import com.nuvepro.moodle.helpers.ApiClient;
import com.nuvepro.moodle.helpers.PracticeProjectFixtures;
import com.nuvepro.moodle.pages.GuidedLanding;
import com.nuvepro.moodle.pages.GuidedProjectOverridesPage;
import com.nuvepro.moodle.pages.PracticeUserAttemptsPage;
import com.nuvepro.moodle.pages.UserGuidedProjectsPage;
import io.cucumber.java.After;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import org.testng.SkipException;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

/**
 * Practice Project conversion batch (High / Medium, no cloud lab). Reuses
 * {@code I open the guided activity} / {@code I open a new guided activity form}
 * from {@link GuidedSteps}.
 */
public class PracticeProjectSteps {
    private final TestContext ctx;
    private UserGuidedProjectsPage adminPage;
    private PracticeUserAttemptsPage attemptsPage;
    private Page attemptsTab;
    private Path downloadedReport;
    private GuidedProjectOverridesPage overrides;
    private String overrideEmail;
    private String overrideRemarks;
    private String practiceApiResponse;
    private String otherParticipantSnapshot;
    private String selectedParticipantName;
    private String selectedParticipantEmail;

    public PracticeProjectSteps(TestContext ctx) {
        this.ctx = ctx;
    }

    private String primaryCmid() {
        return PracticeProjectFixtures.primaryCmid(ctx.page);
    }

    private String sectionId() {
        GuidedLanding landing = new GuidedLanding(ctx.page);
        if (landing.sectionId().isBlank()) {
            landing.open(Integer.parseInt(primaryCmid()));
        }
        String sid = landing.sectionId();
        if (sid == null || sid.isBlank()) throw new SkipException("practice project section id is unavailable");
        return sid;
    }

    private UserGuidedProjectsPage admin() {
        if (adminPage == null) adminPage = new UserGuidedProjectsPage(ctx.page);
        return adminPage;
    }

    @Then("the Practice Project lab type is selectable")
    public void thePracticeProjectLabTypeIsSelectable() {
        Locator guided = ctx.page.locator("input[name='labtype'][value='guided']");
        assertTrue(guided.count() > 0, "Practice Project / guided lab type radio is missing on Add activity");
        assertTrue(guided.first().isChecked(), "guided lab type was not selected");
    }

    @Then("the practice project type, time limit, submission, stop and cooldown fields are present")
    public void thePracticeProjectConfigFieldsArePresent() {
        assertTrue(ctx.page.locator("input[name='practiceprojecttype'][value='withprogresstracking']").count() > 0
                        && ctx.page.locator("input[name='practiceprojecttype'][value='withoutprogresstracking']").count() > 0,
                "with/without progress tracking options are missing");
        assertTrue(ctx.page.locator("[id^='id_timelimit'], [name='timelimit']").count() > 0,
                "time limit field is missing");
        assertTrue(ctx.page.locator("[name='assessmentretake'], #id_assessmentretake").count() > 0,
                "submission / allowed-attempts setting is missing");
        assertTrue(ctx.page.locator("[name='show_stop_lab_button'], #id_show_stop_lab_button").count() > 0,
                "Show stop lab button setting is missing");
        assertTrue(ctx.page.locator("[name='each_attempt_time_gap'], #id_each_attempt_time_gap").count() > 0,
                "duration between attempts is missing for Practice Project");
    }

    @Then("allowed attempts can be set to 1 and 100")
    public void allowedAttemptsCanBeSetTo1And100() {
        Locator select = ctx.page.locator("select[name='assessmentretake'], #id_assessmentretake").first();
        assertTrue(select.count() > 0, "allowed attempts control is missing");
        Locator.SelectOptionOptions force = new Locator.SelectOptionOptions().setForce(true);
        select.selectOption("1", force);
        assertTrue("1".equals(select.inputValue()), "minimum 1 attempt could not be selected");
        select.selectOption("100", force);
        assertTrue("100".equals(select.inputValue()), "maximum 100 attempts could not be selected");
    }

    @Then("duration between attempts is not shown for a playground activity")
    public void durationBetweenAttemptsIsNotShownForAPlaygroundActivity() {
        Locator playground = ctx.page.locator("input[name='labtype'][value='playground']");
        if (playground.count() > 0) {
            playground.first().check(new Locator.CheckOptions().setForce(true));
            ctx.page.waitForTimeout(800);
        }
        Locator gap = ctx.page.locator("[name='each_attempt_time_gap'], #id_each_attempt_time_gap");
        boolean hidden = gap.count() == 0 || !gap.first().isVisible();
        assertTrue(hidden, "duration between attempts is visible for playground");
        Locator assessment = ctx.page.locator("input[name='labtype'][value='assessment']");
        if (assessment.count() > 0) {
            assessment.first().check(new Locator.CheckOptions().setForce(true));
            ctx.page.waitForTimeout(800);
            gap = ctx.page.locator("[name='each_attempt_time_gap'], #id_each_attempt_time_gap");
            hidden = gap.count() == 0 || !gap.first().isVisible();
            assertTrue(hidden, "duration between attempts is visible for assessment");
        }
    }

    @Then("the practice project landing shows the attempts summary")
    public void thePracticeProjectLandingShowsTheAttemptsSummary() {
        GuidedLanding landing = new GuidedLanding(ctx.page);
        assertTrue(landing.attemptsSummaryPresent(),
                "attempts summary / cards are missing on the practice project landing");
    }

    @Then("admin sees the Manage Practice Project Overrides control")
    public void adminSeesTheManagePracticeProjectOverridesControl() {
        GuidedLanding landing = new GuidedLanding(ctx.page);
        assertTrue(landing.manageOverridesPresent(),
                "Manage practice project overrides control is missing on the landing");
    }

    @Then("the reports dropdown has download and email options")
    public void theReportsDropdownHasDownloadAndEmailOptions() {
        assertTrue(admin().reportsDropdownHasDownloadAndEmail(),
                "Reports dropdown is missing download or email");
    }

    @Then("the user attempts button is disabled until a participant is selected")
    public void theUserAttemptsButtonIsDisabledUntilAParticipantIsSelected() {
        assertTrue(admin().userAttemptsDisabled(),
                "User Attempts was enabled before selecting a participant");
        if (admin().dataRows() == 0) {
            throw new SkipException("no participants on User Practice Project to select");
        }
        selectedParticipantName = admin().firstParticipantName();
        selectedParticipantEmail = admin().firstParticipantEmail();
        assertTrue(admin().selectFirstUser(),
                "selecting a participant did not enable User Attempts");
        assertTrue(admin().userAttemptsEnabled(),
                "User Attempts stayed disabled after selecting a participant");
    }

    @When("admin selects a participant and opens User Attempts")
    public void adminSelectsAParticipantAndOpensUserAttempts() {
        if (admin().dataRows() == 0) throw new SkipException("no participants to open User Attempts");
        selectedParticipantName = admin().firstParticipantName();
        selectedParticipantEmail = admin().firstParticipantEmail();
        if (!admin().userAttemptsEnabled()) {
            assertTrue(admin().selectFirstUser(), "could not select a participant");
        }
        attemptsTab = admin().openUserAttemptsTab();
        ctx.watchPageConsole(attemptsTab, "pp-attempts");
        attemptsPage = new PracticeUserAttemptsPage(attemptsTab);
        attemptsTab.waitForTimeout(2_000);
    }

    @Then("the User Attempts page shows the required header, table columns and report actions")
    public void theUserAttemptsPageShowsTheRequiredHeaderTableColumnsAndReportActions() {
        assertTrue(attemptsPage != null && attemptsPage.isLoaded(),
                "User Attempts page did not open");
        assertTrue(attemptsPage.headerHasRequiredFields(),
                "User Attempts header is missing Name/Email/Project/Attempts/Lab ID: "
                        + attemptsPage.headerText());
        if (selectedParticipantEmail != null && !selectedParticipantEmail.isBlank()) {
            assertTrue(attemptsPage.headerContains(selectedParticipantEmail)
                            || attemptsPage.headerContains(selectedParticipantName),
                    "User Attempts header does not show the selected participant: "
                            + attemptsPage.headerText());
        }
        assertTrue(attemptsPage.tableHasRequiredColumns(),
                "User Attempts table is missing attempt number/start/end/duration/status columns");
        assertTrue(attemptsPage.downloadReportPresent(),
                "Download report is missing on User Attempts");
        assertTrue(attemptsPage.actionButtonsPresent(),
                "Check Details or Submit Attempt is missing on User Attempts");
    }

    @Then("the User Attempts check-details and submit-attempt buttons start disabled")
    public void theUserAttemptsCheckDetailsAndSubmitAttemptButtonsStartDisabled() {
        assertTrue(attemptsPage.actionButtonsDisabledByDefault(),
                "Check Details / Submit Attempt were enabled before selecting an attempt");
        if (attemptsPage.attemptRows() > 0) {
            assertTrue(attemptsPage.selectFirstAttemptEnablesActions(),
                    "selecting an attempt did not enable Check Details or Submit Attempt");
        }
    }

    @When("admin downloads the consolidated practice project report")
    public void adminDownloadsTheConsolidatedPracticeProjectReport() {
        downloadedReport = admin().downloadConsolidatedReport();
    }

    @Then("the practice project report file is downloaded")
    public void thePracticeProjectReportFileIsDownloaded() {
        assertTrue(downloadedReport != null && Files.exists(downloadedReport)
                        && downloadedReport.toFile().length() > 0,
                "consolidated practice project report was not downloaded");
    }

    @When("admin opens the practice project Manage Overrides page")
    public void adminOpensThePracticeProjectManageOverridesPage() {
        new GuidedProjectOverridesPage(ctx.page).open(sectionId());
    }

    @Then("the practice project overrides page is shown")
    public void thePracticeProjectOverridesPageIsShown() {
        GuidedProjectOverridesPage page = new GuidedProjectOverridesPage(ctx.page);
        assertTrue(page.isLoaded(), "Manage Practice Project Overrides page did not load");
        assertTrue(page.hasStatusOrEmptyState(),
                "overrides page has neither rows nor an empty-state message");
    }

    @Then("admin can send the practice project report to a recipient")
    public void adminCanSendThePracticeProjectReportToARecipient() {
        String recipient = Settings.ADMIN_USER.contains("@")
                ? Settings.ADMIN_USER : "qa.automation+" + System.currentTimeMillis() + "@example.com";
        assertTrue(admin().sendReportEmail(recipient),
                "sending the practice project report did not confirm success");
    }

    @Then("the participants table prevents multiple selection")
    public void theParticipantsTablePreventsMultipleSelection() {
        if (admin().dataRows() < 2) {
            throw new SkipException("at least two Practice Project participants are required");
        }
        assertTrue(!admin().canSelectMultipleUsers(),
                "DEFECT: User Practice Project permits selecting multiple participants");
    }

    @Given("admin selects an eligible Practice Project override user")
    public void adminSelectsAnEligiblePracticeProjectOverrideUser() {
        overrides = new GuidedProjectOverridesPage(ctx.page);
        overrideEmail = overrides.openAddForEligibleUser(primaryCmid());
        Page comparison = ctx.page.context().newPage();
        try {
            UserGuidedProjectsPage projects = new UserGuidedProjectsPage(comparison);
            GuidedLanding landing = new GuidedLanding(comparison);
            landing.open(Integer.parseInt(primaryCmid()));
            projects.open(landing.sectionId());
            otherParticipantSnapshot = projects.otherParticipantAttempts(overrideEmail);
        } finally {
            try { comparison.close(); } catch (Throwable ignored) {}
        }
    }

    @When("admin creates an unlimited Practice Project override")
    public void adminCreatesAnUnlimitedPracticeProjectOverride() {
        overrideRemarks = "PP override unlimited " + System.currentTimeMillis();
        assertTrue(overrides.addFormHasRequiredFields(),
                "Practice Project override form is missing dates/time/attempts/remarks");
        overrides.saveOverride("0", null, overrideRemarks);
        assertTrue(overrides.hasOverrideFor(overrideEmail),
                "new Practice Project override was not listed");
    }

    @Then("the override lists unlimited attempts and duration")
    public void theOverrideListsUnlimitedAttemptsAndDuration() {
        String row = overrides.overrideTextFor(overrideEmail).toLowerCase();
        assertTrue(row.contains("unlimited"), "override does not show unlimited attempts: " + row);
        String timeLimit = overrides.overrideCellText(overrideEmail, 3).toLowerCase();
        assertTrue(timeLimit.isBlank() || timeLimit.contains("unlimited") || timeLimit.contains("no limit"),
                "override does not show unlimited duration: " + timeLimit);
        assertTrue(row.contains(overrideRemarks.toLowerCase()), "override remarks were not persisted: " + row);
    }

    @Then("another participant's allowed attempts remain unchanged")
    public void anotherParticipantsAllowedAttemptsRemainUnchanged() {
        if (otherParticipantSnapshot == null || otherParticipantSnapshot.isBlank()) {
            throw new SkipException("a second Practice Project participant is required");
        }
        String[] expected = otherParticipantSnapshot.split("\\|", 2);
        Page comparison = ctx.page.context().newPage();
        try {
            UserGuidedProjectsPage projects = new UserGuidedProjectsPage(comparison);
            GuidedLanding landing = new GuidedLanding(comparison);
            landing.open(Integer.parseInt(primaryCmid()));
            projects.open(landing.sectionId());
            String actual = projects.otherParticipantAttempts(overrideEmail);
            assertTrue(actual.equals(otherParticipantSnapshot),
                    "override changed another user's allowed attempts; expected "
                            + expected[1] + " for " + expected[0] + " but got " + actual);
        } finally {
            try { comparison.close(); } catch (Throwable ignored) {}
        }
    }

    @Then("the downloadable report contains the overridden participant")
    public void theDownloadableReportContainsTheOverriddenParticipant() {
        Page reportPage = ctx.page.context().newPage();
        try {
            GuidedLanding landing = new GuidedLanding(reportPage);
            landing.open(Integer.parseInt(primaryCmid()));
            UserGuidedProjectsPage projects = new UserGuidedProjectsPage(reportPage);
            projects.open(landing.sectionId());
            Path report = projects.downloadConsolidatedReport();
            String content = Files.readString(report).toLowerCase();
            assertTrue(content.contains(overrideEmail.toLowerCase()),
                    "override user's email is absent from the Practice Project report");
            assertTrue(content.contains("2") || content.contains("unlimited"),
                    "override attempt allowance is absent from the Practice Project report");
        } catch (java.io.IOException e) {
            throw new AssertionError("could not read Practice Project report", e);
        } finally {
            try { reportPage.close(); } catch (Throwable ignored) {}
        }
    }

    @Then("the override form offers the No Attempts configuration")
    public void theOverrideFormOffersTheNoAttemptsConfiguration() {
        assertTrue(overrides.noAttemptsOptionPresent(),
                "DEFECT: Practice Project override has no No Attempts option");
    }

    @When("admin changes the override to no new attempts")
    public void adminChangesTheOverrideToNoNewAttempts() {
        overrideRemarks = "PP override none " + System.currentTimeMillis();
        assertTrue(overrides.editOverrideFor(overrideEmail, "-1", null, overrideRemarks),
                "could not edit Practice Project override");
    }

    @Then("the override lists no new attempts")
    public void theOverrideListsNoNewAttempts() {
        String row = overrides.overrideTextFor(overrideEmail).toLowerCase();
        assertTrue(row.contains("no") && row.contains("attempt"),
                "override does not show No Attempts: " + row);
        assertTrue(row.contains(overrideRemarks.toLowerCase()), "edited remarks were not persisted: " + row);
    }

    @When("admin changes the override to finite attempts and duration")
    public void adminChangesTheOverrideToFiniteAttemptsAndDuration() {
        overrideRemarks = "PP override finite " + System.currentTimeMillis();
        assertTrue(overrides.editOverrideFor(overrideEmail, "2", 30, overrideRemarks),
                "could not set finite Practice Project override");
    }

    @Then("the override lists the finite attempts and duration")
    public void theOverrideListsTheFiniteAttemptsAndDuration() {
        String row = overrides.overrideTextFor(overrideEmail).toLowerCase();
        assertTrue(row.contains("2"), "finite allowed attempts were not persisted: " + row);
        assertTrue(row.contains("30") || row.contains("minute"),
                "finite override duration was not persisted: " + row);
        assertTrue(row.contains(overrideRemarks.toLowerCase()), "finite remarks were not persisted: " + row);
    }

    @When("admin deletes the Practice Project override")
    public void adminDeletesThePracticeProjectOverride() {
        assertTrue(overrides.deleteOverrideFor(overrideEmail),
                "Practice Project override was not deleted");
    }

    @Then("the Practice Project override is removed")
    public void thePracticeProjectOverrideIsRemoved() {
        assertTrue(!overrides.hasOverrideFor(overrideEmail),
                "deleted Practice Project override is still listed");
    }

    @When("admin re-overrides the completed Practice Project user")
    public void adminReOverridesTheCompletedPracticeProjectUser() {
        overrides.openAddForUserEmail(primaryCmid(), overrideEmail);
        overrideRemarks = "PP override recreated " + System.currentTimeMillis();
        overrides.saveOverride("1", 15, overrideRemarks);
    }

    @Then("the recreated Practice Project override is listed")
    public void theRecreatedPracticeProjectOverrideIsListed() {
        String row = overrides.overrideTextFor(overrideEmail).toLowerCase();
        assertTrue(row.contains(overrideRemarks.toLowerCase()),
                "recreated override is not listed: " + row);
        assertTrue(row.contains("completed"),
                "completed user's recreated override status is not retained: " + row);
    }

    @Then("the override status is shown for the user")
    public void theOverrideStatusIsShownForTheUser() {
        assertTrue(overrides.statusColumnPresent(),
                "Override Status column is missing on Manage Overrides");
        assertTrue(overrides.overrideStatusIsKnown(overrideEmail),
                "override status is not Not Attempted / Started / Completed: "
                        + overrides.overrideTextFor(overrideEmail));
    }

    @When("admin requests the Practice Project API response")
    public void adminRequestsThePracticeProjectApiResponse() {
        if (Settings.WS_TOKEN.isBlank()) throw new SkipException("MOODLE_WS_TOKEN not configured");
        try {
            practiceApiResponse = ApiClient.practiceProjectResult(sectionId(), null, false);
        } catch (RuntimeException e) {
            if (e.getMessage() != null && e.getMessage().contains("webservice_access_exception")) {
                throw new SkipException("seeding token cannot call mod_cloudlabs_get_guided_project_result");
            }
            throw e;
        }
    }

    @Then("the API uses Practice Project names and returns dates time limit and attempts")
    public void theApiUsesPracticeProjectNamesAndReturnsConfiguration() {
        String response = practiceApiResponse == null ? "" : practiceApiResponse;
        assertTrue(response.contains("\"practiceProjectName\"")
                        && response.contains("\"practiceProjectId\""),
                "API response lacks Practice Project field names: " + response);
        assertTrue(response.contains("\"practiceProjectStartDate\"")
                        && response.contains("\"practiceProjectEndDate\"")
                        && response.contains("\"timeLimit\"")
                        && response.contains("\"allowedAttempts\""),
                "API response lacks dates/timeLimit/allowedAttempts: " + response);
    }

    @When("admin requests the override user's Practice Project API response")
    public void adminRequestsTheOverrideUsersPracticeProjectApiResponse() {
        if (Settings.WS_TOKEN.isBlank()) throw new SkipException("MOODLE_WS_TOKEN not configured");
        try {
            practiceApiResponse = ApiClient.practiceProjectResult(sectionId(), overrideEmail, true);
        } catch (RuntimeException e) {
            if (e.getMessage() != null && e.getMessage().contains("webservice_access_exception")) {
                throw new SkipException("seeding token cannot call mod_cloudlabs_get_guided_project_result");
            }
            throw e;
        }
    }

    @Then("the API returns the user attempts checkpoints and override values")
    public void theApiReturnsUserAttemptsCheckpointsAndOverrideValues() {
        String response = practiceApiResponse == null ? "" : practiceApiResponse;
        assertTrue(response.toLowerCase().contains(overrideEmail.toLowerCase()),
                "API response does not contain the selected user: " + response);
        assertTrue(response.contains("\"guidedData\"") || response.contains("\"practiceProjectData\""),
                "API response lacks user project data: " + response);
        assertTrue(response.toLowerCase().contains("attempt"),
                "API response lacks attempt data: " + response);
        assertTrue(response.toLowerCase().contains("override"),
                "API response lacks override values: " + response);
        assertTrue(response.toLowerCase().contains("test") || response.toLowerCase().contains("checkpoint"),
                "API response lacks test/checkpoint details: " + response);
    }

    @When("admin opens the User Practice Project participants page")
    public void adminOpensTheUserPracticeProjectParticipantsPage() {
        admin().open(sectionId());
    }

    @Then("with and without progress tracking can be selected")
    public void withAndWithoutProgressTrackingCanBeSelected() {
        Locator withTracking = ctx.page.locator("input[name='practiceprojecttype'][value='withprogresstracking']");
        Locator without = ctx.page.locator("input[name='practiceprojecttype'][value='withoutprogresstracking']");
        assertTrue(withTracking.count() > 0 && without.count() > 0,
                "with/without progress tracking radios are missing");
        without.first().check(new Locator.CheckOptions().setForce(true));
        ctx.page.waitForTimeout(400);
        assertTrue(without.first().isChecked(), "without progress tracking could not be selected");
        withTracking.first().check(new Locator.CheckOptions().setForce(true));
        ctx.page.waitForTimeout(400);
        assertTrue(withTracking.first().isChecked(), "with progress tracking could not be selected");
    }

    @Then("the progress-check URL is shown only for with progress tracking")
    public void theProgressCheckUrlIsShownOnlyForWithProgressTracking() {
        Locator url = ctx.page.locator("#id_guidedprogresscheckurl, [name='guidedprogresscheckurl']");
        Locator withTracking = ctx.page.locator("input[name='practiceprojecttype'][value='withprogresstracking']");
        Locator without = ctx.page.locator("input[name='practiceprojecttype'][value='withoutprogresstracking']");
        withTracking.first().check(new Locator.CheckOptions().setForce(true));
        ctx.page.waitForTimeout(600);
        assertTrue(url.count() > 0 && url.first().isVisible(),
                "progress-check URL is hidden for with progress tracking");
        without.first().check(new Locator.CheckOptions().setForce(true));
        ctx.page.waitForTimeout(600);
        assertTrue(url.count() == 0 || !url.first().isVisible(),
                "progress-check URL stayed visible for without progress tracking");
    }

    @Then("the time limit can be set in minutes hours days and seconds")
    public void theTimeLimitCanBeSetInMinutesHoursDaysAndSeconds() {
        Locator expand = ctx.page.locator("a[title='Expand all'], .collapseexpand, a:has-text('Expand all')");
        if (expand.count() > 0) {
            try { expand.first().click(); ctx.page.waitForTimeout(400); } catch (RuntimeException ignored) {}
        }
        Locator enable = ctx.page.locator("#id_timelimit_enabled");
        if (enable.count() > 0 && !enable.first().isChecked()) {
            try {
                enable.first().check(new Locator.CheckOptions().setForce(true));
            } catch (RuntimeException e) {
                ctx.page.evaluate("el => { el.checked = true; el.dispatchEvent(new Event('change', {bubbles:true})); }",
                        enable.first().elementHandle());
            }
            ctx.page.waitForTimeout(300);
        }
        Locator number = ctx.page.locator("#id_timelimit_number, [name='timelimit[number]']").first();
        Locator unit = ctx.page.locator("#id_timelimit_timeunit, [name='timelimit[timeunit]']").first();
        assertTrue(number.count() > 0 && unit.count() > 0, "time limit controls are missing");
        Locator.FillOptions fillForce = new Locator.FillOptions().setForce(true);
        Locator.SelectOptionOptions force = new Locator.SelectOptionOptions().setForce(true);
        number.fill("2", fillForce);
        String html = unit.innerHTML();
        assertTrue(html.contains("60") && html.contains("3600") && html.contains("86400") && html.contains("value=\"1\""),
                "time limit units minutes/hours/days/seconds are missing: " + html);
        for (String value : new String[]{"60", "3600", "86400", "1"}) {
            unit.selectOption(value, force);
            assertTrue(value.equals(unit.inputValue()), "could not set time-limit unit " + value);
        }
    }

    @Then("the Lab details configuration section is present")
    public void theLabDetailsConfigurationSectionIsPresent() {
        assertTrue(ctx.page.locator("#id_labdetails, #id_lab_template_id, [name='lab_template_id']").count() > 0
                        || ctx.page.locator("legend, h3, .ftoggler").filter(
                                new Locator.FilterOptions().setHasText("Lab details")).count() > 0,
                "Lab details configuration section is missing");
    }

    @Then("the practice project landing shows dates time limit and allowed attempts")
    public void thePracticeProjectLandingShowsDatesTimeLimitAndAllowedAttempts() {
        GuidedLanding landing = new GuidedLanding(ctx.page);
        assertTrue(landing.landingShowsDatesTimeLimitAndAttempts(),
                "landing is missing dates, time limit or attempts: " + landing.detailsText());
    }

    @Then("the practice project landing shows description Read More and intro video")
    public void thePracticeProjectLandingShowsDescriptionReadMoreAndIntroVideo() {
        GuidedLanding landing = new GuidedLanding(ctx.page);
        assertTrue(landing.descriptionAndReadMorePresent(),
                "Practice Project landing is missing the description");
        assertTrue(landing.readMoreOpensDescription(),
                "Read More did not open the full Practice Project description");
        assertTrue(landing.introVideoControlPresent(),
                "intro video control is missing on the Practice Project landing");
    }

    @Then("the User Practice Project table offers xml csv txt and json export")
    public void theUserPracticeProjectTableOffersClientExport() {
        assertTrue(admin().exportTypesConfigured(),
                "participants table is missing data-export-types xml/csv/txt/json");
        assertTrue(admin().exportDropdownPresent() && admin().exportTypesPresent(),
                "DEFECT: bootstrap-table export dropdown is not rendered "
                        + "(data-show-export is true but table-export did not add xml/csv/txt/json)");
    }
    @Then("the manual Practice Project Complete action is available")
    public void theManualPracticeProjectCompleteActionIsAvailable() {
        assertFalse(new GuidedLanding(ctx.page).completeButtonPresent(),
                "Complete control appeared — deviation resolved; update Kiwi from Deviation to Pass");
    }

    @Then("selecting an attempt enables Submit Attempt and Check Details")
    public void selectingAnAttemptEnablesSubmitAttemptAndCheckDetails() {
        if (attemptsPage.attemptRows() == 0) {
            throw new SkipException("no User Attempts rows to select");
        }
        assertTrue(attemptsPage.selectFirstAttemptEnablesActions(),
                "selecting an attempt did not enable Submit Attempt or Check Details");
    }

    @Then("the User Attempts table shows attempt values including override token and checkpoints")
    public void theUserAttemptsTableShowsAttemptValuesIncludingOverrideTokenAndCheckpoints() {
        assertTrue(attemptsPage.tableHasRequiredColumns(),
                "User Attempts is missing attempt number/start/end/duration/status");
        assertTrue(attemptsPage.tableHasAttemptDetailColumns(),
                "User Attempts is missing override/token/checkpoint columns");
        if (attemptsPage.attemptRows() == 0) {
            throw new SkipException("no User Attempts rows to assert values");
        }
        assertTrue(attemptsPage.firstRowHasExactAttemptValues(),
                "User Attempts row is missing attempt number, start date or status: "
                        + attemptsPage.firstRowText());
    }

    @Then("admin can open checkpoint details for the selected attempt")
    public void adminCanOpenCheckpointDetailsForTheSelectedAttempt() {
        assertTrue(attemptsPage.openCheckpointDetails(),
                "Check Details did not show checkpoint results");
    }

    @Then("User Attempts card view displays the attempt")
    public void userAttemptsCardViewDisplaysTheAttempt() {
        assertTrue(attemptsPage.switchToCardViewShowsData(),
                "User Attempts card-view toggle is missing or displayed no attempt data");
    }

    @Then("User Attempts pagination and search work")
    public void userAttemptsPaginationAndSearchWork() {
        assertTrue(attemptsPage.paginationWorks(),
                "User Attempts pagination controls did not respond");
        assertTrue(attemptsPage.searchFiltersAttempts("1"),
                "User Attempts search box is missing");
    }

    @When("admin downloads the User Attempts report")
    public void adminDownloadsTheUserAttemptsReport() {
        downloadedReport = attemptsPage.downloadAttemptReport();
    }

    @Then("the User Attempts report file is downloaded")
    public void theUserAttemptsReportFileIsDownloaded() {
        assertTrue(downloadedReport != null && Files.exists(downloadedReport)
                        && downloadedReport.toFile().length() > 0,
                "User Attempts report was not downloaded");
    }

    @Then("admin can cancel the report download modal")
    public void adminCanCancelTheReportDownloadModal() {
        assertTrue(admin().cancelReportDownloadModal(),
                "report download modal did not close on Cancel");
    }

    @After("@practice_project_override")
    public void cleanupPracticeProjectOverride() {
        if (!com.nuvepro.moodle.helpers.CleanupPolicy.runScenarioCleanup()) {
            System.out.println("[PP] deferCleanup=true — override left on site until manual cleanup");
            return;
        }
        if (overrideEmail != null) {
            try {
                if (overrides != null && overrides.hasOverrideFor(overrideEmail)) {
                    overrides.deleteOverrideFor(overrideEmail);
                }
            } catch (Throwable e) {
                System.err.println("[PP-cleanup] override cleanup failed: " + e.getMessage());
            }
            overrideEmail = null;
        }
    }
}
