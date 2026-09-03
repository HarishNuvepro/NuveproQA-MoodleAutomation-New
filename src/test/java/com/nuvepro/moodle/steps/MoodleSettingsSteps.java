package com.nuvepro.moodle.steps;

import com.microsoft.playwright.Browser;
import com.nuvepro.moodle.helpers.Auth;
import com.nuvepro.moodle.pages.CourseAdmin;
import com.nuvepro.moodle.pages.MoodleSettings;
import io.cucumber.java.After;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;

/**
 * Core Moodle admin settings (Grade category/item settings, role gate). These pages are SHARED,
 * site-wide config — not a throwaway per-scenario resource like a cloudlabs lab — so every field a
 * scenario touches is restored to its pre-scenario value in @After, leaving the site as it was found.
 */
public class MoodleSettingsSteps {
    private final TestContext ctx;
    private final MoodleSettings settings;
    private final Map<String, String> originalValues = new LinkedHashMap<>();
    private Map<String, Boolean> originalAdvanced;
    private final List<String> throwawayCourses = new ArrayList<>();
    private String currentSection;
    private MoodleSettings studentSettings;
    private String uploadCourseId;
    private String oversizeReject;

    public MoodleSettingsSteps(TestContext ctx) {
        this.ctx = ctx;
        this.settings = new MoodleSettings(ctx.page);
    }

    @Given("I open the {string} settings page")
    public void openSettingsPage(String section) {
        currentSection = section;
        settings.open(section);
        assertTrue(settings.formLoaded(), "settings form did not load for section=" + section);
    }

    @When("I set the {string} field to {string} and save")
    public void setFieldAndSave(String fieldId, String value) {
        originalValues.putIfAbsent(fieldId, settings.getValue(fieldId));
        settings.setValue(fieldId, value);
        settings.save();
    }

    @Then("the {string} field is {string}")
    public void fieldIs(String fieldId, String expected) {
        settings.open(currentSection);   // reload fresh to confirm the value actually persisted
        String actual = settings.getValue(fieldId);
        assertEquals(actual, expected, "field " + fieldId + " on section " + currentSection);
    }

    @Then("the {string} field offers options {string}")
    public void fieldOffersOptions(String fieldId, String csvExpected) {
        List<String> actualOptions = settings.optionTexts(fieldId);
        for (String want : csvExpected.split(",")) {
            String w = want.trim();
            boolean present = actualOptions.stream().anyMatch(o -> o.trim().equalsIgnoreCase(w));
            assertTrue(present, "expected option '" + w + "' not found in " + actualOptions);
        }
    }

    /** Substring match — for values Moodle renders with a non-breaking space (e.g. "Site upload limit (50 MB)"). */
    @Then("the {string} field contains {string}")
    public void fieldContains(String fieldId, String expectedSubstring) {
        settings.open(currentSection);
        String actual = settings.getValue(fieldId).replace(' ', ' ');   // normalise nbsp
        assertTrue(actual.contains(expectedSubstring),
                "field " + fieldId + " value '" + actual + "' did not contain '" + expectedSubstring + "'");
    }

    // ---- new-course propagation, boundaries, config log (course-dependent deferred cases) ----

    @Then("the {string} field offers no option above {int}")
    public void fieldOffersNoOptionAbove(String fieldId, int max) {
        for (String opt : settings.optionTexts(fieldId)) {
            String t = opt.trim();
            if (t.matches("\\d+") && Integer.parseInt(t) > max) {
                throw new AssertionError("field " + fieldId + " unexpectedly offers option " + t + " (> " + max + ")");
            }
        }
    }

    @Then("the add-new-course form in category {string} shows {string} for the {string} field")
    public void addCourseFormShows(String categoryId, String expected, String courseFieldId) {
        settings.openAddCourseForm(categoryId);
        assertEquals(settings.getValue(courseFieldId), expected,
                "add-new-course " + courseFieldId + " did not reflect the site default");
    }

    @Then("the existing course {string} still shows {string} for the {string} field")
    public void existingCourseStillShows(String courseId, String expected, String courseFieldId) {
        settings.openEditCourseForm(courseId);
        assertEquals(settings.getValue(courseFieldId), expected,
                "existing course " + courseId + " " + courseFieldId + " changed when it should not have");
    }

    @Then("the {string} field is not {string}")
    public void fieldIsNot(String fieldId, String notExpected) {
        settings.open(currentSection);
        assertTrue(!settings.getValue(fieldId).equals(notExpected),
                "field " + fieldId + " was unexpectedly '" + notExpected + "'");
    }

    @Then("the config change log records a change to {string}")
    public void configLogRecordsChange(String settingName) {
        settings.openConfigLog();
        assertTrue(settings.configLogHasRecent(settingName, 40),
                "config change log has no recent entry for '" + settingName + "'");
    }

    // ---- Running Banner front-end display (SET-74/76): NuveTheme notification banner ----

    @Then("the notification banner shows {string} across Moodle")
    public void theNotificationBannerShows(String text) {
        // Load a normal front-end page (dashboard) and confirm the enabled banner's text renders.
        ctx.page.navigate(com.nuvepro.moodle.config.Settings.BASE_URL + "/my/",
                new com.microsoft.playwright.Page.NavigateOptions()
                        .setWaitUntil(com.microsoft.playwright.options.WaitUntilState.DOMCONTENTLOADED).setTimeout(30_000));
        ctx.page.waitForTimeout(1_500);
        String body = ctx.page.locator("body").innerText();
        assertTrue(body.contains(text), "notification banner text '" + text + "' not found on the dashboard");
    }

    // ---- Advanced grade item options (SET-70 / SET-71) ----

    @Then("the advanced grade item options default to the Moodle built-in set")
    public void advancedDefaultsAreSet() {
        assertTrue(settings.advancedGradeItemSettingPresent(),
                "Advanced grade item options control missing on gradeitemsettings");
        Map<String, Boolean> states = settings.advancedGradeItemStates();
        List<String> missing = new ArrayList<>();
        for (String key : MoodleSettings.DEFAULT_ADV_KEYS) {
            if (!Boolean.TRUE.equals(states.get(key))) missing.add(key);
        }
        assertTrue(missing.isEmpty(),
                "default advanced keys not checked: " + missing + " (have " + states + ")");
    }

    @Then("those default advanced options are hidden when editing a grade item")
    public void defaultAdvancedHiddenOnGradeItem() {
        String courseId = newCourse("SET70");
        settings.addSimpleAssignment(courseId, "SET70 grade item");
        settings.openGradeItemEditor(courseId);
        assertAdvancedOnForm(List.of(
                "Item info", "ID number", "Grade to pass",
                "Grade display type", "Overall decimal places"));
    }

    @When("I mark the extra grade item options as advanced and save")
    public void markExtraAdvancedAndSave() {
        assertTrue(settings.advancedGradeItemSettingPresent(),
                "Advanced grade item options control missing on gradeitemsettings");
        originalAdvanced = settings.advancedGradeItemStates();
        settings.setAdvancedGradeItemKeys(List.of(MoodleSettings.EXTRA_ADV_KEYS), true);
        settings.save();
    }

    @Then("those extra options are hidden when editing a grade item")
    public void extraAdvancedHiddenOnGradeItem() {
        String courseId = newCourse("SET71");
        settings.addSimpleAssignment(courseId, "SET71 grade item");
        settings.openGradeItemEditor(courseId);
        assertAdvancedOnForm(List.of("Grade type", "Minimum grade", "Maximum grade", "Hidden", "Locked"));
    }

    private void assertAdvancedOnForm(List<String> labels) {
        List<String> visible = new ArrayList<>();
        List<String> missing = new ArrayList<>();
        for (String label : labels) {
            String vis = settings.gradeItemFieldVisibility(label);
            if ("visible".equals(vis)) visible.add(label);
            if ("missing".equals(vis)) missing.add(label);
        }
        assertTrue(visible.isEmpty(),
                "expected advanced/hidden on the grade item form but still visible: " + visible);
        assertTrue(missing.size() < labels.size(),
                "none of the expected labels were on the grade item form: " + labels
                        + " missing=" + missing);
    }

    // ---- Course max upload size (SET-32 / SET-33) ----

    @Given("a throwaway course for settings uploads")
    public void throwawayCourseForUploads() {
        uploadCourseId = newCourse("SETUpload");
    }

    @When("I upload files of sizes {string} into a file resource")
    public void uploadFilesOfSizes(String csvSizes) {
        assertNotNull(uploadCourseId, "no throwaway course");
        for (String raw : csvSizes.split(",")) {
            String spec = raw.trim();
            Path file = sizedFile(spec);
            String url = settings.addFileResource(uploadCourseId, "Upload " + spec, file);
            assertTrue(!url.contains("modedit.php") || url.contains("id="),
                    spec + " upload did not save a file resource: " + url);
        }
    }

    @Then("each upload is accepted")
    public void eachUploadIsAccepted() {
        assertNotNull(uploadCourseId, "no files were uploaded");
    }

    @When("I try to upload a {string} file into a file resource")
    public void tryUploadOversized(String spec) {
        assertNotNull(uploadCourseId, "no throwaway course");
        Path file = sizedFile(spec);
        oversizeReject = settings.tryAttachFileOnResourceForm(uploadCourseId, "Oversize " + spec, file);
    }

    @Then("the upload is rejected as too large")
    public void uploadRejectedAsTooLarge() {
        assertNotNull(oversizeReject, "Moodle accepted the oversize file instead of rejecting it");
        String lower = oversizeReject.toLowerCase();
        assertTrue(lower.contains("large") || lower.contains("maximum") || lower.contains("exceed")
                        || lower.contains("bigger") || lower.contains("size")
                        || lower.contains("never appeared"),
                "rejection was not a size limit: " + oversizeReject);
    }

    private String newCourse(String prefix) {
        String stamp = String.valueOf(System.currentTimeMillis() % 1_000_000);
        String id = new CourseAdmin(ctx.page).createCourse(prefix + " " + stamp, prefix + stamp);
        throwawayCourses.add(id);
        return id;
    }

    private static Path sizedFile(String spec) {
        String s = spec.trim().toUpperCase().replace(" ", "");
        long bytes;
        if (s.endsWith("KB")) bytes = Long.parseLong(s.replace("KB", "")) * 1024;
        else if (s.endsWith("MB")) bytes = Long.parseLong(s.replace("MB", "")) * 1024 * 1024;
        else bytes = Long.parseLong(s.replaceAll("[^0-9]", ""));
        try {
            Path dir = Path.of("target", "settings-uploads");
            Files.createDirectories(dir);
            Path file = dir.resolve("set_" + s.toLowerCase() + ".bin");
            if (Files.exists(file) && Files.size(file) == bytes) return file;
            try (OutputStream out = Files.newOutputStream(file)) {
                byte[] chunk = new byte[8192];
                long left = bytes;
                while (left > 0) {
                    int n = (int) Math.min(chunk.length, left);
                    out.write(chunk, 0, n);
                    left -= n;
                }
            }
            return file;
        } catch (IOException e) {
            throw new RuntimeException("could not write " + spec + " fixture: " + e.getMessage(), e);
        }
    }

    // ---- role gate (SET-79) ----

    @Given("I am logged in as a student")
    public void iAmLoggedInAsAStudent() {
        ctx.studentContext = GlobalHooks.browser.newContext(new Browser.NewContextOptions()
                .setBaseURL(com.nuvepro.moodle.config.Settings.BASE_URL).setIgnoreHTTPSErrors(true));
        ctx.studentPage = ctx.studentContext.newPage();
        ctx.studentPage.setDefaultTimeout(com.nuvepro.moodle.config.Settings.DEFAULT_TIMEOUT_MS);
        Auth.loginAsStudent(ctx.studentPage);
    }

    @When("I open the {string} settings page as that student")
    public void iOpenTheSettingsPageAsThatStudent(String section) {
        studentSettings = new MoodleSettings(ctx.studentPage);
        studentSettings.open(section);
    }

    @Then("access is denied")
    public void accessIsDenied() {
        assertTrue(studentSettings.hasAccessError() || !studentSettings.formLoaded(),
                "student unexpectedly saw the settings form render");
    }

    /** Leave shared site config exactly as this scenario found it. */
    @After("@settings")
    public void restoreOriginalValues() {
        try {
            if (originalAdvanced != null) {
                settings.restoreAdvancedGradeItemStates(originalAdvanced);
                originalAdvanced = null;
            }
            if (!originalValues.isEmpty() && currentSection != null) {
                settings.open(currentSection);
                for (Map.Entry<String, String> e : originalValues.entrySet()) {
                    settings.setValue(e.getKey(), e.getValue());
                }
                settings.save();
                originalValues.clear();
            }
        } finally {
            CourseAdmin admin = new CourseAdmin(ctx.page);
            for (String id : throwawayCourses) {
                try {
                    admin.deleteCourse(id);
                } catch (RuntimeException e) {
                    System.out.println("[settings] delete course " + id + " failed: " + e.getMessage());
                }
            }
            throwawayCourses.clear();
        }
    }
}
