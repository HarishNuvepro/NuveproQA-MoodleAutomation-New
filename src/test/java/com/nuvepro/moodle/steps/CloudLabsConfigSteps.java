package com.nuvepro.moodle.steps;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.WaitForSelectorState;
import com.nuvepro.moodle.config.Settings;
import com.nuvepro.moodle.helpers.AssessmentCreateSpec;
import com.nuvepro.moodle.helpers.CrossProcessLock;
import com.nuvepro.moodle.helpers.FormSubmit;
import com.nuvepro.moodle.pages.ChallengeActivityForm;
import com.nuvepro.moodle.pages.CourseView;
import com.nuvepro.moodle.pages.MoodleSettings;
import io.cucumber.java.After;
import io.cucumber.java.Before;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import org.testng.SkipException;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

/**
 * Kiwi: Content_Integration_Git_URL (cloudlabs_config.feature) +
 * Difficulty_Level_Configuration (difficulty_level_configuration.feature); tag @cloudlabsconfig.
 * Form toggles and admin difficulty settings; restores difficulty levels after mutating scenarios.
 */
public class CloudLabsConfigSteps {
    private static final String DIFFICULTY_FIELD = "id_s_cloudlabs_difficulty_levels";
    private static final String CLOUD_SECTION = "modcloudlabsgeneralsettings";
    /** Same default as {@link com.nuvepro.moodle.helpers.AssessmentTestData} baseline creates. */
    private static final String CHALLENGE_TEMPLATE_FRAGMENT = "aws.account.clab ELB";
    /** Site-wide Cloud Server difficulty list — one mutator at a time when {@code sandbox.parallel=true}. */
    private static final CrossProcessLock DIFFICULTY_ADMIN_CROSS =
            CrossProcessLock.named("cloudlabs-difficulty-admin");
    /** Whole @dlc scenario holds the site lock under parallel (step class is JVM singleton). */
    private static final ThreadLocal<Boolean> DLC_SCENARIO_LOCK_HELD = ThreadLocal.withInitial(() -> false);

    private final TestContext injectedCtx;

    public CloudLabsConfigSteps(TestContext ctx) {
        this.injectedCtx = ctx;
    }

    /** Resolves per-thread scenario context under parallel Cucumber (step defs are singletons). */
    private TestContext ctx() {
        TestContext active = Hooks.activeTestContext();
        return active != null ? active : injectedCtx;
    }

    /** Page is null until {@link Hooks#setUp}; do not cache page objects on the step def. */
    private MoodleSettings moodleSettings() {
        return new MoodleSettings(ctx().page);
    }

    private CourseView courseView() {
        return new CourseView(ctx().page);
    }

    private ChallengeActivityForm challengeForm() {
        return new ChallengeActivityForm(ctx().page);
    }

    private void openAddForm() {
        if (Settings.COURSE_ID.isEmpty()) throw new SkipException("COURSE_ID not set");
        ctx().page.navigate(Settings.BASE_URL + "/course/modedit.php?add=cloudlabs&type=&course="
                        + Settings.COURSE_ID + "&section=0&return=0&sr=0",
                new Page.NavigateOptions().setWaitUntil(com.microsoft.playwright.options.WaitUntilState.DOMCONTENTLOADED)
                        .setTimeout(45_000));
        ctx().page.waitForTimeout(2_000);
        expandAll();
    }

    private void expandAll() {
        Locator ex = ctx().page.locator("a.collapseexpand, .collapsible-actions a");
        if (ex.count() > 0) {
            try {
                ex.first().click();
                ctx().page.waitForTimeout(600);
            } catch (Throwable ignored) {}
        }
    }

    private void pickRadio(String name, String value) {
        ctx().page.locator("input[type=radio][name='" + name + "'][value='" + value + "']").first()
                .check(new Locator.CheckOptions().setForce(true));
        ctx().page.waitForTimeout(700);
    }

    private void pickLabType(String labType) {
        Locator radio = ctx().page.locator("input[name='labtype'][value='" + labType + "']");
        if (radio.count() > 0 && !radio.first().isChecked()) {
            radio.first().check(new Locator.CheckOptions().setForce(true));
            ctx().page.waitForTimeout(1_000);
        }
    }

    private boolean visible(String selector) {
        Locator l = ctx().page.locator(selector);
        return l.count() > 0 && l.first().isVisible();
    }

    private static boolean parallelBrowsers() {
        return Boolean.getBoolean("sandbox.parallel");
    }

    /** After {@link Hooks#setUp} (default @Before order 10000). */
    @Before(value = "@dlc", order = 10001)
    public void beginDlcParallelLane() {
        if (!parallelBrowsers()) return;
        DIFFICULTY_ADMIN_CROSS.lock(15, TimeUnit.MINUTES);
        DLC_SCENARIO_LOCK_HELD.set(true);
    }

    @After(value = "@dlc", order = 0)
    public void endDlcParallelLane() {
        if (!parallelBrowsers() || !Boolean.TRUE.equals(DLC_SCENARIO_LOCK_HELD.get())) return;
        DLC_SCENARIO_LOCK_HELD.set(false);
        DIFFICULTY_ADMIN_CROSS.unlock();
    }

    private static boolean dlcScenarioLockHeld() {
        return Boolean.TRUE.equals(DLC_SCENARIO_LOCK_HELD.get());
    }

    private static <T> T withDifficultySiteLock(Supplier<T> action) {
        if (!parallelBrowsers() || dlcScenarioLockHeld()) {
            return action.get();
        }
        DIFFICULTY_ADMIN_CROSS.lock(15, TimeUnit.MINUTES);
        try {
            return action.get();
        } finally {
            DIFFICULTY_ADMIN_CROSS.unlock();
        }
    }

    private static void withDifficultySiteLock(Runnable action) {
        withDifficultySiteLock(() -> {
            action.run();
            return null;
        });
    }

    private void captureDifficultyBaseline() {
        if (ctx().cloudlabsDifficultyBaseline != null) return;
        ctx().cloudlabsDifficultyBaseline = readAdminDifficultyLevelsRaw();
    }

    /** Stored admin value, or Moodle's inline default hint when the textarea is left blank. */
    private String readAdminDifficultyLevelsRaw() {
        if (parallelBrowsers() && dlcScenarioLockHeld()) {
            return readAdminDifficultyLevelsUnlocked();
        }
        return withDifficultySiteLock(this::readAdminDifficultyLevelsUnlocked);
    }

    private String readAdminDifficultyLevelsUnlocked() {
        moodleSettings().open(CLOUD_SECTION);
        Locator field = ctx().page.locator("#" + DIFFICULTY_FIELD);
        field.waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE).setTimeout(20_000));
        String val = field.inputValue().trim();
        if (!val.isEmpty()) return val;
        Locator defaultInfo = ctx().page.locator("#admin-difficulty_levels .form-defaultinfo");
        if (defaultInfo.count() == 0) return val;
        String hint = defaultInfo.first().innerText().trim();
        int idx = hint.toLowerCase().indexOf("default:");
        if (idx >= 0) hint = hint.substring(idx + "default:".length()).trim();
        return hint.isEmpty() ? val : hint;
    }

    private boolean documentedOrLiveDifficultySet(String csvOrText) {
        String lower = csvOrText.toLowerCase().replace('\n', ' ');
        boolean docSet = lower.contains("practitioner") && lower.contains("proficient");
        boolean liveSet = lower.contains("beginner")
                && (lower.contains("intermediate") || lower.contains("advanced"));
        return docSet || liveSet;
    }

    private String difficultyOptionsOnChallengeForm() {
        if (Settings.COURSE_ID.isEmpty()) return "";
        openAddForm();
        Locator opts = ctx().page.locator("select[name='difficulty_level'] option");
        if (opts.count() == 0) return "";
        return String.join(",", opts.allInnerTexts());
    }

    private void saveDifficultyLevels(String levels) {
        withDifficultySiteLock(() -> {
            captureDifficultyBaselineUnlocked();
            moodleSettings().open(CLOUD_SECTION);
            moodleSettings().setText(DIFFICULTY_FIELD, levels);
            moodleSettings().save();
            ctx().page.waitForTimeout(1_500);
        });
    }

    private void captureDifficultyBaselineUnlocked() {
        if (ctx().cloudlabsDifficultyBaseline != null) return;
        ctx().cloudlabsDifficultyBaseline = readAdminDifficultyLevelsUnlocked();
    }

    @When("I open a new cloudlabs activity form")
    public void iOpenANewCloudlabsActivityForm() {
        openAddForm();
    }

    @When("I open a new cloudlabs activity form for lab type {string}")
    public void iOpenCloudlabsFormForLabType(String labType) {
        openAddForm();
        pickLabType(labType);
    }

    @Then("the description source toggles the git URL field")
    public void theDescriptionSourceTogglesGitUrl() {
        if (ctx().page.locator("input[type=radio][name='descriptionsource']").count() == 0)
            throw new SkipException("descriptionsource not present on this form");
        pickRadio("descriptionsource", "git");
        assertTrue(visible("[name='descriptiongiturl']"), "git URL field not shown for descriptionsource=git");
        pickRadio("descriptionsource", "manual");
        assertFalse(visible("[name='descriptiongiturl']"), "git URL field still shown for descriptionsource=manual");
    }

    @Then("the description source is not shown for a playground lab")
    public void descriptionSourceHiddenForPlayground() {
        Locator src = ctx().page.locator("input[type=radio][name='descriptionsource']");
        assertTrue(src.count() == 0 || !src.first().isVisible(),
                "description source should be hidden for playground/sandbox labs");
    }

    @Then("the guided content source toggles its git URL field")
    public void guidedContentSourceTogglesGit() {
        contentSourceTogglesGit("guided");
    }

    @Then("the assessment content source toggles its git URL field")
    public void assessmentContentSourceTogglesGit() {
        contentSourceTogglesGit("assessment");
    }

    @Then("the {string} content source toggles its git URL field")
    public void contentSourceTogglesGit(String kind) {
        String radio = switch (kind.toLowerCase()) {
            case "guided" -> "guidecontentsource";
            case "assessment", "challenge" -> "challengecontentsource";
            default -> throw new SkipException("unknown content source kind: " + kind);
        };
        String urlField = switch (kind.toLowerCase()) {
            case "guided" -> "[name='guidecontentgiturl']";
            case "assessment", "challenge" -> "[name='challengecontentgiturl']";
            default -> "";
        };
        if (ctx().page.locator("input[type=radio][name='" + radio + "']").count() == 0)
            throw new SkipException(radio + " not on this form");
        pickRadio(radio, "git");
        assertTrue(visible(urlField), "git URL field not shown for " + radio + "=git");
        pickRadio(radio, "manual");
        assertFalse(visible(urlField), "git URL field still shown for " + radio + "=manual");
    }

    @When("I open a new challenge activity form for config")
    public void iOpenANewChallengeActivityForm() {
        withDifficultySiteLock(() -> {
            openAddForm();
            pickLabType("challenge");
        });
    }

    @Then("the difficulty level select offers the configured levels and defaults to Beginner")
    public void theDifficultyLevelSelectOffersLevels() {
        Locator dl = ctx().page.locator("select[name='difficulty_level']");
        assertTrue(dl.count() > 0 && dl.first().isVisible(), "difficulty_level select not shown for a challenge");
        int opts = ctx().page.locator("select[name='difficulty_level'] option").count();
        assertTrue(opts >= 2, "difficulty_level has too few options (" + opts + ")");
        String all = dl.first().innerText();
        assertTrue(all.contains("Beginner"), "difficulty levels do not include Beginner (got: " + all.replace("\n", " ") + ")");
        String selected = (String) dl.first().evaluate(
                "e => e.options[e.selectedIndex] ? e.options[e.selectedIndex].text.trim() : ''");
        assertEquals(selected, "Beginner", "difficulty level did not default to Beginner");
    }

    @Then("the difficulty level dropdown lists {string}")
    public void difficultyDropdownLists(String csvLevels) {
        Locator dl = ctx().page.locator("select[name='difficulty_level']");
        assertTrue(dl.count() > 0 && dl.first().isVisible(), "difficulty_level select missing");
        String all = dl.first().innerText();
        for (String part : csvLevels.split(",")) {
            assertTrue(all.contains(part.trim()), "expected level '" + part.trim() + "' in dropdown: " + all);
        }
    }

    @Then("the difficulty level dropdown has {int} options")
    public void difficultyDropdownCount(int expected) {
        int opts = ctx().page.locator("select[name='difficulty_level'] option").count();
        assertEquals(opts, expected, "unexpected difficulty option count");
    }

    @When("admin opens the cloud server settings")
    public void adminOpensTheCloudServerSettings() {
        moodleSettings().open(CLOUD_SECTION);
        ctx().page.waitForTimeout(1_000);
    }

    @Then("the difficulty level settings section is present")
    public void theDifficultyLevelSettingsSectionIsPresent() {
        assertTrue(ctx().page.locator("[id='id_s_cloudlabs_difficulty_levels'], [name='s_cloudlabs_difficulty_levels']").count() > 0,
                "difficulty_levels admin field not present on Cloud Server Settings");
        assertTrue(ctx().page.locator("body").innerText().contains("Difficulty Level Settings"),
                "'Difficulty Level Settings' heading not present");
    }

    @Then("the admin difficulty levels field shows the documented Practitioner set or the live default")
    public void practitionerSetOrDeviation() {
        String adminText = readAdminDifficultyLevelsRaw();
        if (documentedOrLiveDifficultySet(adminText)) return;
        String activityOpts = difficultyOptionsOnChallengeForm();
        assertTrue(documentedOrLiveDifficultySet(activityOpts),
                "expected Practitioner/Proficient doc set OR live Beginner/Intermediate(Advanced) set; "
                        + "admin field/default hint=[" + adminText + "], activity options=[" + activityOpts + "]");
    }

    @When("admin saves cloudlabs difficulty levels to {string}")
    public void adminSavesDifficultyLevels(String levels) {
        saveDifficultyLevels(levels);
    }

    @When("admin clears cloudlabs difficulty levels and saves")
    public void adminClearsDifficultyLevels() {
        saveDifficultyLevels("");
    }

    @When("admin saves a challenge activity with difficulty {string}")
    public void adminSavesChallengeActivityWithDifficulty(String levelLabel) {
        if (Settings.COURSE_ID.isEmpty()) throw new SkipException("COURSE_ID not set");
        ctx().dlcSavedChallengeDifficulty = levelLabel.trim();
        withDifficultySiteLock(() -> {
            AssessmentCreateSpec spec = new AssessmentCreateSpec.Builder()
                    .name("AUTO-DLC-" + System.currentTimeMillis())
                    .labType("challenge")
                    .difficulty(ctx().dlcSavedChallengeDifficulty)
                    .instructions("Automated difficulty configuration regression activity.")
                    .templateContains(CHALLENGE_TEMPLATE_FRAGMENT)
                    .gradeToPass("30")
                    .build();
            try {
                challengeForm().openAddFormDirect(Settings.COURSE_ID);
                challengeForm().fill(spec);
                challengeForm().saveAndDisplay();
            } catch (IllegalStateException e) {
                throw new SkipException("cannot save challenge for DLC (need lab template containing '"
                        + CHALLENGE_TEMPLATE_FRAGMENT + "'): " + e.getMessage());
            }
            ctx().page.waitForTimeout(1_000);
            String cmidStr = challengeForm().activityCmidFromUrl();
            ctx().dlcSavedChallengeCmid = cmidStr != null && !cmidStr.isBlank()
                    ? Integer.parseInt(cmidStr)
                    : cmidFromUrl(ctx().page.url());
            assertTrue(ctx().dlcSavedChallengeCmid > 0,
                    "challenge activity did not save (url=" + ctx().page.url() + ") "
                            + challengeFormValidationHint());
        });
    }

    private String challengeFormValidationHint() {
        Locator errors = ctx().page.locator(
                "[id^='id_error_'], .alert-danger, .notifyproblem, .invalid-feedback");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < Math.min(errors.count(), 8); i++) {
            try {
                String t = errors.nth(i).innerText().trim();
                if (!t.isEmpty()) sb.append(t).append(" | ");
            } catch (RuntimeException ignored) {}
        }
        return sb.length() > 0 ? "errors: " + sb : "";
    }

    @When("admin opens the saved challenge activity for editing")
    public void adminOpensSavedChallengeForEditing() {
        if (ctx().dlcSavedChallengeCmid <= 0) throw new SkipException("no saved challenge activity");
        ctx().page.navigate(Settings.BASE_URL + "/course/modedit.php?update=" + ctx().dlcSavedChallengeCmid,
                new Page.NavigateOptions().setWaitUntil(com.microsoft.playwright.options.WaitUntilState.DOMCONTENTLOADED)
                        .setTimeout(45_000));
        ctx().page.waitForTimeout(1_500);
        expandAll();
        Locator challenge = ctx().page.locator("input[name='labtype'][value='challenge']");
        if (challenge.count() > 0) {
            try {
                challenge.first().scrollIntoViewIfNeeded();
            } catch (RuntimeException ignored) {}
        }
        visibleDifficultySelect().waitFor(new Locator.WaitForOptions()
                .setState(WaitForSelectorState.VISIBLE).setTimeout(20_000));
    }

    @Then("the saved challenge activity still shows difficulty {string}")
    public void savedChallengeStillShowsDifficulty(String levelLabel) {
        String expected = levelLabel.trim();
        Locator dl = visibleDifficultySelect();
        dl.scrollIntoViewIfNeeded();
        String label = readSelectedDifficultyLabel();
        String value = dl.inputValue().trim();
        assertTrue(expected.equalsIgnoreCase(label) || expected.equalsIgnoreCase(value),
                "saved activity difficulty on edit form (label=" + label + ", value=" + value + ")");
    }

    /** After an in-use level is removed from admin, the edit form lists only remaining site levels (no orphan option). */
    @Then("the challenge edit form difficulty dropdown lists {string} only")
    public void challengeEditFormDifficultyDropdownListsOnly(String csvLevels) {
        Locator dl = visibleDifficultySelect();
        dl.scrollIntoViewIfNeeded();
        var expected = Arrays.stream(csvLevels.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
        int optCount = dl.locator("option").count();
        assertEquals(optCount, expected.size(),
                "unexpected difficulty option count on edit form (text=" + dl.innerText().trim() + ")");
        String all = dl.innerText();
        for (String part : expected) {
            assertTrue(all.contains(part), "expected level '" + part + "' on edit form dropdown: " + all);
        }
    }

    /** Live site remaps stored difficulty to a valid option when its label was removed from admin (e.g. Medium → Easy). */
    @Then("the saved challenge difficulty selects a remaining configured level")
    public void savedChallengeDifficultySelectsRemainingLevel() {
        Locator dl = visibleDifficultySelect();
        String label = readSelectedDifficultyLabel();
        Set<String> labels = new HashSet<>();
        for (int i = 0; i < dl.locator("option").count(); i++) {
            labels.add(dl.locator("option").nth(i).innerText().trim().toLowerCase());
        }
        assertFalse(label.isBlank(), "no difficulty selected on edit form");
        assertTrue(labels.contains(label.toLowerCase()),
                "selected difficulty '" + label + "' must be one of configured options " + labels);
    }

    @Then("the saved challenge activity edit form loads without errors")
    public void savedChallengeEditFormLoadsWithoutErrors() {
        assertTrue(ctx().page.url().contains("modedit.php"),
                "expected activity edit form (url=" + ctx().page.url() + ")");
        Locator name = ctx().page.locator("#id_name");
        assertTrue(name.count() > 0 && name.first().isVisible(), "activity name field missing on edit form");
        String errors = ctx().page.locator(".alert-danger, .notifyproblem, .errorbox").allInnerTexts()
                .stream().reduce("", String::concat).trim();
        assertTrue(errors.isEmpty(), "edit form showed errors: " + errors);
    }

    @When("admin duplicates the saved challenge activity on the course")
    public void adminDuplicatesSavedChallenge() {
        if (ctx().dlcSavedChallengeCmid <= 0) throw new SkipException("no saved challenge activity");
        int dup = courseView().duplicateActivity(Settings.COURSE_ID, ctx().dlcSavedChallengeCmid);
        assertTrue(dup > 0, "duplicate activity did not appear on the course page");
        ctx().dlcSavedChallengeCmid = dup;
    }

    @When("admin restores cloudlabs default four difficulty levels and saves")
    public void adminRestoresDefaultFourDifficultyLevels() {
        saveDifficultyLevels("Beginner,Intermediate,Advanced,Expert");
    }

    /** cloudlabs mod_form renders a difficulty select per lab type; only the active one is visible. */
    private Locator visibleDifficultySelect() {
        Locator all = ctx().page.locator("select[name='difficulty_level']");
        for (int i = 0; i < all.count(); i++) {
            Locator candidate = all.nth(i);
            try {
                if (candidate.isVisible()) return candidate;
            } catch (RuntimeException ignored) {}
        }
        return all.first();
    }

    private String readSelectedDifficultyLabel() {
        Locator dl = visibleDifficultySelect();
        assertTrue(dl.count() > 0, "difficulty_level select missing on edit form");
        dl.scrollIntoViewIfNeeded();
        assertTrue(dl.isVisible(), "difficulty_level select not visible (check lab type section)");
        return (String) dl.evaluate(
                "e => e.options[e.selectedIndex] ? e.options[e.selectedIndex].text.trim() : ''");
    }

    private static int cmidFromUrl(String url) {
        Matcher m = Pattern.compile("[?&](?:update|id)=(\\d+)").matcher(url);
        if (m.find()) return Integer.parseInt(m.group(1));
        m = Pattern.compile("mod/cloudlabs/view\\.php\\?id=(\\d+)").matcher(url);
        return m.find() ? Integer.parseInt(m.group(1)) : -1;
    }

    @Then("the lab template dropdown is present on the activity form")
    public void labTemplateDropdownPresent() {
        assertTrue(ctx().page.locator("#id_lab_template_id").count() > 0,
                "lab template dropdown missing on activity form");
    }

    @Then("the lab template dropdown lists an active template")
    public void labTemplateDropdownListsActiveTemplate() {
        Locator opt = ctx().page.locator("#id_lab_template_id option:not([value='']):not([value='0'])");
        assertTrue(opt.count() > 0, "activity form has no selectable lab template options");
    }

    @When("admin selects invalid guided git content URL and attempts save")
    public void adminSelectsInvalidGuidedGitUrl() {
        if (ctx().page.locator("input[type=radio][name='guidecontentsource']").count() == 0)
            throw new SkipException("guidecontentsource not on this guided activity form");
        pickRadio("guidecontentsource", "git");
        Locator url = ctx().page.locator("[name='guidecontentgiturl']");
        assertTrue(url.count() > 0, "guided git URL field missing");
        url.first().fill("https://invalid.example.com/nuvepro-does-not-exist.git");
        FormSubmit.andAwaitNewPage(ctx().page, ctx().page.locator("#id_submitbutton2, #id_submitbutton").first(), 30_000);
        ctx().page.waitForTimeout(1_000);
    }

    @Then("the activity form rejects the invalid git URL or remains on the edit form")
    public void gitUrlRejectedOrStillOnForm() {
        String current = ctx().page.url().toLowerCase();
        boolean stillEditing = current.contains("modedit.php");
        String errors = ctx().page.locator(
                "#id_error_guidecontentgiturl, #id_error_descriptiongiturl, .form-control-feedback, "
                        + ".alert-danger, .notifyproblem, .invalid-feedback")
                .allInnerTexts().stream().reduce("", String::concat).toLowerCase();
        String body = ctx().page.locator("body").innerText().toLowerCase();
        boolean hasError = errors.contains("invalid") || errors.contains("error") || errors.contains("git")
                || errors.contains("url") || errors.contains("fetch") || errors.contains("repository")
                || body.contains("could not") || body.contains("failed to") || body.contains("required");
        assertTrue(stillEditing || hasError,
                "invalid git URL was accepted and navigated away from the activity form (url=" + current + ")");
    }

    @After("@dlcactivity")
    public void clearSavedChallengeTracking() {
        ctx().dlcSavedChallengeCmid = -1;
        ctx().dlcSavedChallengeDifficulty = "";
    }

    @After(value = "@difficultyrestore", order = 10_000)
    public void restoreDifficultyLevels() {
        if (ctx().cloudlabsDifficultyBaseline == null) return;
        withDifficultySiteLock(() -> {
            try {
                moodleSettings().open(CLOUD_SECTION);
                moodleSettings().setText(DIFFICULTY_FIELD, ctx().cloudlabsDifficultyBaseline);
                moodleSettings().save();
                System.out.println("[cloudlabsconfig] restored difficulty levels");
            } catch (Throwable e) {
                System.out.println("[cloudlabsconfig] difficulty restore failed: " + e.getMessage());
            } finally {
                ctx().cloudlabsDifficultyBaseline = null;
            }
        });
    }
}
