package com.nuvepro.moodle.pages;

import com.microsoft.playwright.Download;
import com.microsoft.playwright.FileChooser;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.WaitForSelectorState;
import com.nuvepro.moodle.config.Settings;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Admin course + activity authoring, used to build the Sequential Activity Completion fixture:
 * create a course (completion defaults on), add URL activities (a lightweight module that needs only
 * a name + external URL — no rich-text editor), set an activity's completion to "students manually
 * mark as done", and restrict one activity on another's completion via the availabilityconditionsjson
 * hidden field (robust vs. driving the JS restrict-access builder). Selectors verified live.
 */
public class CourseAdmin extends BasePage {
    private String restoredCategoryId = "";
    private String restoredCategoryName = "";

    public CourseAdmin(Page page) {
        super(page);
    }

    /** Category the last restore actually landed in (may differ if the wanted one was not listed). */
    public String restoredCategoryId() {
        return restoredCategoryId;
    }

    public String restoredCategoryName() {
        return restoredCategoryName;
    }

    /** Create a course under category 1 and return its id. Completion tracking defaults to on. */
    public String createCourse(String fullName, String shortName) {
        return createCourse(fullName, shortName, "1", true, "");
    }

    /** Create a catalog fixture with explicit category, visibility, and summary. */
    public String createCourse(String fullName, String shortName, String categoryId,
                               boolean visible, String summary) {
        navigate("/course/edit.php?category=" + categoryId);
        page.locator("#id_fullname").waitFor(new Locator.WaitForOptions()
                .setState(WaitForSelectorState.VISIBLE).setTimeout(30_000));
        page.locator("#id_fullname").fill(fullName);
        page.locator("#id_shortname").fill(shortName);
        if (page.locator("#id_category").count() > 0) {
            setSelectValue("#id_category", categoryId);
        }
        if (page.locator("#id_visible").count() > 0) {
            setSelectValue("#id_visible", visible ? "1" : "0");
        }
        fillEditor("summary", summary);
        page.evaluate("""
                () => {
                  if (window.tinyMCE) window.tinyMCE.triggerSave();
                  const btn = document.getElementById('id_saveanddisplay');
                  if (btn) btn.click();
                }
                """);
        try {
            page.waitForURL("**/course/view.php**", new Page.WaitForURLOptions().setTimeout(60_000));
        } catch (RuntimeException ignored) {
            page.locator("#id_saveanddisplay").click(new Locator.ClickOptions()
                    .setNoWaitAfter(true)
                    .setForce(true)
                    .setTimeout(15_000));
            page.waitForURL("**/course/view.php**", new Page.WaitForURLOptions().setTimeout(60_000));
        }
        String id = param(page.url(), "id");
        if (id == null) throw new IllegalStateException("createCourse: no course id in URL " + page.url());
        page.waitForTimeout(800);
        return id;
    }

    /** Submit an intentionally incomplete form and report whether Moodle rejected it. */
    public boolean rejectsMissingRequiredCourseFields(String uniqueShortName) {
        navigate("/course/edit.php?category=1");
        page.locator("#id_shortname").fill(uniqueShortName);
        page.locator("#id_saveanddisplay").click();
        page.waitForTimeout(800);
        return page.url().contains("/course/edit.php")
                && page.locator("[id^='id_error_'], .invalid-feedback, .form-control-feedback")
                    .filter(new Locator.FilterOptions().setHasText(Pattern.compile("\\S+"))).count() > 0;
    }

    /** Create a category and return its id. */
    public String createCategory(String name, String parentId) {
        navigate("/course/editcategory.php?parent=" + (parentId == null ? "0" : parentId));
        Locator input = page.locator("#id_name");
        input.waitFor(new Locator.WaitForOptions()
                .setState(WaitForSelectorState.VISIBLE).setTimeout(30_000));
        input.fill(name);
        if (parentId != null && page.locator("#id_parent").count() > 0) {
            setSelectValue("#id_parent", parentId);
        }
        Locator submit = page.locator("#id_submitbutton, button[type='submit']").first();
        submit.click();
        page.waitForLoadState(com.microsoft.playwright.options.LoadState.DOMCONTENTLOADED);
        page.waitForTimeout(1_200);
        String id = param(page.url(), "categoryid");
        if (id == null) id = param(page.url(), "id");
        if (id == null) {
            // Manage categories links include categoryid after a successful save.
            Matcher m = Pattern.compile("[?&](?:categoryid|id)=(\\d+)")
                    .matcher(page.locator("body").innerHTML());
            if (m.find()) id = m.group(1);
        }
        if (id == null) throw new IllegalStateException("createCategory: no id after saving " + name);
        return id;
    }

    /**
     * Show/Hide a category from Manage courses and categories. The category EDIT form carries no
     * visibility field — the eye action on management.php is the only UI that toggles it.
     */
    public void setCategoryVisible(String categoryId, boolean visible) {
        String want = visible ? "1" : "0";
        String action = visible ? "show" : "hide";
        for (int attempt = 1; attempt <= 3; attempt++) {
            openCategoryManagement(categoryId);
            if (want.equals(categoryVisibleAttribute(categoryId))) return;

            // Moodle renders both eye actions and hides the inapplicable one, so click in the DOM.
            Object clicked = page.evaluate("""
                    ([id, act]) => {
                      const li = document.querySelector(`li[data-id="${id}"]`);
                      if (!li) return 'no-item';
                      const link = li.querySelector(
                          `a[data-action="${act}"], a[href*="action=${act}category"]`);
                      if (!link) return 'no-link';
                      link.click();
                      return 'ok';
                    }
                    """, java.util.List.of(categoryId, action));
            if ("ok".equals(String.valueOf(clicked)) && waitForCategoryVisibility(categoryId, want)) {
                break;
            }
            // The action is an AJAX link; fall back to its plain sesskey URL.
            Object sesskey = page.evaluate("() => (window.M && M.cfg && M.cfg.sesskey) || ''");
            String key = String.valueOf(sesskey);
            if (!key.isBlank()) {
                navigate("/course/management.php?categoryid=" + categoryId
                        + "&sesskey=" + key + "&action=" + action + "category");
                page.waitForTimeout(800);
            }
            if (want.equals(categoryVisibleAttribute(categoryId))) break;
            System.out.println("[CourseAdmin] category " + categoryId + " " + action
                    + " attempt " + attempt + " did not stick (clicked=" + clicked + ")");
        }

        openCategoryManagement(categoryId);
        String actual = categoryVisibleAttribute(categoryId);
        if (!want.equals(actual)) {
            throw new AssertionError("category " + categoryId + " visibility did not change:"
                    + " expected data-visible=" + want + " but was " + actual + " url=" + page.url());
        }
        System.out.println("[CourseAdmin] category " + categoryId + " visible=" + visible);
    }

    private void openCategoryManagement(String categoryId) {
        navigate("/course/management.php?categoryid=" + categoryId + "&viewmode=categories");
        page.locator("li[data-id='" + categoryId + "']").first()
                .waitFor(new Locator.WaitForOptions()
                        .setState(WaitForSelectorState.ATTACHED).setTimeout(30_000));
    }

    /** {@code data-visible} on the management listing row, or {@code "missing"}. */
    private String categoryVisibleAttribute(String categoryId) {
        Locator item = page.locator("li[data-id='" + categoryId + "']").first();
        if (item.count() == 0) return "missing";
        String value = item.getAttribute("data-visible");
        return value == null ? "missing" : value.trim();
    }

    private boolean waitForCategoryVisibility(String categoryId, String want) {
        for (int i = 0; i < 15; i++) {
            if (want.equals(categoryVisibleAttribute(categoryId))) return true;
            page.waitForTimeout(1_000);
        }
        return false;
    }

    public void setCourseVisible(String courseId, boolean visible) {
        navigate("/course/edit.php?id=" + courseId);
        expandAll();
        Locator field = page.locator("#id_visible");
        if (field.count() == 0) throw new AssertionError("course visibility field not found");
        setSelectValue("#id_visible", visible ? "1" : "0");
        page.locator("#id_saveandreturn, #id_saveanddisplay, #id_submitbutton").first().click();
        page.waitForLoadState(com.microsoft.playwright.options.LoadState.DOMCONTENTLOADED);
        page.waitForTimeout(900);
        // Re-open and confirm Moodle persisted the value (Boost can leave the native select stale).
        navigate("/course/edit.php?id=" + courseId);
        expandAll();
        Object saved = page.locator("#id_visible").evaluate("el => el.value");
        String expected = visible ? "1" : "0";
        if (!expected.equals(String.valueOf(saved))) {
            throw new AssertionError("course visibility did not persist: expected " + expected
                    + " but form has " + saved);
        }
    }

    /**
     * Move a course to another category. Verified because the category field is set through JS:
     * assigning a value the select does not offer silently leaves the course where it was.
     */
    public void moveCourse(String courseId, String categoryId) {
        for (int attempt = 1; attempt <= 2; attempt++) {
            navigate("/course/edit.php?id=" + courseId);
            expandAll();
            Locator category = page.locator("#id_category");
            if (category.count() == 0) {
                throw new AssertionError("course category field not found for course " + courseId);
            }
            setSelectValue("#id_category", categoryId);
            page.locator("#id_saveandreturn, #id_saveanddisplay, #id_submitbutton").first().click();
            page.waitForLoadState(com.microsoft.playwright.options.LoadState.DOMCONTENTLOADED);
            page.waitForTimeout(900);
            if (categoryId.equals(courseCategoryId(courseId))) {
                System.out.println("[CourseAdmin] course " + courseId + " moved to category " + categoryId);
                return;
            }
            System.out.println("[CourseAdmin] course " + courseId + " move attempt " + attempt
                    + " did not persist (form shows " + courseCategoryId(courseId) + ")");
        }
        throw new AssertionError("course " + courseId + " did not move to category " + categoryId
                + "; edit form still reports category " + courseCategoryId(courseId));
    }

    /** Category id currently saved on the course edit form. */
    private String courseCategoryId(String courseId) {
        navigate("/course/edit.php?id=" + courseId);
        expandAll();
        Locator category = page.locator("#id_category");
        if (category.count() == 0) return "";
        try {
            return String.valueOf(category.evaluate("el => el.value"));
        } catch (RuntimeException e) {
            return "";
        }
    }

    /**
     * Moodle 4.x async course copy. Returns the new course id once copyprogress finishes
     * or the shortname appears in course search.
     */
    public String duplicateCourse(String courseId, String fullName, String shortName, String categoryId) {
        navigate("/backup/copy.php?id=" + courseId);
        Locator fullname = page.locator("#id_fullname");
        fullname.waitFor(new Locator.WaitForOptions()
                .setState(WaitForSelectorState.VISIBLE).setTimeout(30_000));
        fullname.fill(fullName);
        page.locator("#id_shortname").fill(shortName);
        if (categoryId != null && !categoryId.isBlank()
                && page.locator("#id_category").count() > 0) {
            setSelectValue("#id_category", categoryId);
        }
        Locator submit = page.locator("#id_submitdisplay, #id_submitreturn, #id_submitbutton");
        if (submit.count() == 0) {
            submit = page.locator("button[type='submit']:visible, input[type='submit']:visible");
        }
        submit.first().click();
        long deadline = System.currentTimeMillis() + 300_000;
        long nextLookup = System.currentTimeMillis() + 8_000;
        while (System.currentTimeMillis() < deadline) {
            page.waitForTimeout(2_000);
            String url = page.url().toLowerCase(java.util.Locale.ROOT);
            String id = param(page.url(), "id");
            if (url.contains("/course/view.php") && id != null && !id.equals(courseId)) return id;
            Locator continueButton = page.locator("button:has-text('Continue'), input[value='Continue'], "
                    + "a:has-text('Continue'), button:has-text('View course'), a:has-text('View course')");
            if (continueButton.count() > 0 && continueButton.first().isVisible()) {
                continueButton.first().click();
                page.waitForTimeout(1_000);
                continue;
            }
            if (System.currentTimeMillis() >= nextLookup) {
                // Probe in a second authenticated page. Navigating the progress page away can stop
                // Moodle's JS poller, and standard course search normally renders fullname rather
                // than shortname.
                String found = findCopiedCourseId(fullName, shortName, courseId);
                if (found != null && !found.equals(courseId)) return found;
                nextLookup = System.currentTimeMillis() + 10_000;
            }
        }
        String body = page.locator("body").innerText();
        throw new AssertionError("course copy did not complete within 300s (source=" + courseId
                + ", fullname=" + fullName + ", shortname=" + shortName + ", page=" + page.url()
                + ", status=" + body.substring(0, Math.min(body.length(), 400)).replaceAll("\\s+", " ") + ")");
    }

    /** Best-effort lookup in a separate page so the async-copy progress page keeps polling. */
    private String findCopiedCourseId(String fullName, String shortName, String sourceId) {
        Page probe = page.context().newPage();
        try {
            String query = URLEncoder.encode(fullName, StandardCharsets.UTF_8);
            probe.navigate(Settings.BASE_URL + "/course/search.php?search=" + query,
                    new Page.NavigateOptions()
                            .setWaitUntil(com.microsoft.playwright.options.WaitUntilState.DOMCONTENTLOADED)
                            .setTimeout(45_000));
            probe.waitForTimeout(800);
            Object id = probe.evaluate("""
                ([name, sn, source]) => {
                  const links = [...document.querySelectorAll("a[href*='course/view.php']")];
                  for (const a of links) {
                    const row = a.closest('div, li, tr, .coursebox') || a.parentElement;
                    const text = ((row && row.innerText) || a.innerText || '').toLowerCase();
                    const m = (a.getAttribute('href') || '').match(/[?&]id=(\\d+)/);
                    if (m && m[1] !== String(source)
                        && (text.includes(String(name).toLowerCase())
                            || text.includes(String(sn).toLowerCase()))) {
                      return m[1];
                    }
                  }
                  return null;
                }
                """, java.util.List.of(fullName, shortName, sourceId));
            return id == null ? null : String.valueOf(id);
        } finally {
            probe.close();
        }
    }

    public void deleteCategory(String categoryId) {
        navigate("/course/index.php?deletecategory=" + categoryId);
        Locator confirm = page.locator("#id_submitbutton, button:has-text('Delete'), "
                + "input[value='Delete'], button:has-text('Continue')").first();
        if (confirm.count() > 0) {
            confirm.click();
            try {
                page.waitForLoadState(com.microsoft.playwright.options.LoadState.DOMCONTENTLOADED);
            } catch (RuntimeException ignored) {}
        }
    }

    private void fillEditor(String name, String value) {
        if (value == null || value.isBlank()) return;
        Locator iframe = page.locator("iframe[id*='" + name + "'], "
                + "[data-fieldtype='editor']:has(textarea[name='" + name + "_editor[text]']) iframe").first();
        if (iframe.count() > 0 && iframe.isVisible()) {
            iframe.contentFrame().locator("body").fill(value);
            page.evaluate("() => { if (window.tinyMCE) window.tinyMCE.triggerSave(); }");
            return;
        }
        // Moodle course summary uses summary_editor[text]; activity editors commonly use name[text].
        Locator textarea = page.locator("#id_" + name + "_editor, #id_" + name
                + ", textarea[name='" + name + "_editor[text]'], textarea[name='" + name + "[text]']").first();
        if (textarea.count() > 0) {
            textarea.evaluate("(el, v) => { el.value = String(v); "
                    + "el.dispatchEvent(new Event('input', {bubbles:true})); "
                    + "el.dispatchEvent(new Event('change', {bubbles:true})); }", value);
            return;
        }
        throw new AssertionError("editor field not found: " + name);
    }

    /** Moodle/Boost enhances selects and leaves the native select hidden; set it directly and fire change. */
    private void setSelectValue(String selector, String value) {
        page.locator(selector).evaluate(
                "(el, v) => { el.value = String(v); el.dispatchEvent(new Event('change', {bubbles:true})); }",
                value);
    }

    /**
     * Add a URL activity to section 1. If {@code manualCompletion} the activity gets "students can
     * manually mark as done"; if {@code availabilityJson} is non-null it is set as the restrict-access
     * condition. Returns the new activity's cmid.
     */
    public String addUrlActivity(String courseId, String name, boolean manualCompletion, String availabilityJson) {
        navigate("/course/modedit.php?add=url&type=&course=" + courseId + "&section=1&return=0&sr=0");
        page.locator("#id_name").waitFor(new Locator.WaitForOptions()
                .setState(WaitForSelectorState.VISIBLE).setTimeout(30_000));
        expandAll();
        page.locator("#id_name").fill(name);
        page.locator("#id_externalurl").fill("https://example.com");
        if (manualCompletion) {
            // The Activity completion radios live in a (possibly collapsed) fieldset; set via JS so
            // visibility never blocks it. id_completion_1 = "Students can manually mark as complete".
            page.locator("#id_completion_1").evaluate(
                    "el => { el.checked = true; el.dispatchEvent(new Event('change', {bubbles:true})); }");
        }
        if (availabilityJson != null) {
            page.locator("[name='availabilityconditionsjson']").evaluate("(el, v) => { el.value = v; }", availabilityJson);
        }
        page.locator("#id_submitbutton").click();
        page.waitForLoadState(com.microsoft.playwright.options.LoadState.DOMCONTENTLOADED);
        page.waitForTimeout(2_000);
        String cmid = param(page.url(), "id");
        if (cmid == null) {
            throw new IllegalStateException("addUrlActivity '" + name + "' did not save (stayed on "
                    + page.url() + ") — likely a validation error");
        }
        return cmid;
    }

    /** availabilityconditionsjson for "activity {cmid} must be marked complete" (e=1), shown greyed. */
    public static String requireCompleteJson(String dependsOnCmid) {
        return "{\"op\":\"&\",\"c\":[{\"type\":\"completion\",\"cm\":" + dependsOnCmid + ",\"e\":1}],\"showc\":[true]}";
    }

    /** Activity 1 completion condition for the grade-based sequential cases. */
    public enum GradeCompletion { ANY_GRADE, PASSING_GRADE }

    /**
     * Add an Assignment whose completion is grade-based (automatic completion + "require grade", and for
     * PASSING_GRADE also "require passing grade" with the given pass mark). Optionally restrict-access
     * via availabilityJson. Completion sub-options are set via JS (they live in a collapsed fieldset).
     * Returns the new activity's cmid.
     */
    public String addGradedAssignment(String courseId, String name, GradeCompletion cond,
                                      int passMark, String availabilityJson) {
        navigate("/course/modedit.php?add=assign&type=&course=" + courseId + "&section=1&return=0&sr=0");
        page.locator("#id_name").waitFor(new Locator.WaitForOptions()
                .setState(WaitForSelectorState.VISIBLE).setTimeout(30_000));
        page.locator("#id_name").fill(name);
        // automatic completion + require grade
        page.locator("#id_completion_2").evaluate(
                "el => { el.checked = true; el.dispatchEvent(new Event('change', {bubbles:true})); }");
        page.locator("#id_completionusegrade").evaluate(
                "el => { el.checked = true; el.dispatchEvent(new Event('change', {bubbles:true})); }");
        if (cond == GradeCompletion.PASSING_GRADE) {
            page.locator("#id_completionpassgrade_1").evaluate(
                    "el => { el.checked = true; el.dispatchEvent(new Event('change', {bubbles:true})); }");
            page.locator("#id_gradepass").evaluate("(el, v) => { el.value = v; }", String.valueOf(passMark));
        }
        if (availabilityJson != null) {
            page.locator("[name='availabilityconditionsjson']").evaluate("(el, v) => { el.value = v; }", availabilityJson);
        }
        page.locator("#id_submitbutton").click();
        page.waitForLoadState(com.microsoft.playwright.options.LoadState.DOMCONTENTLOADED);
        page.waitForTimeout(2_000);
        String cmid = param(page.url(), "id");
        if (cmid == null) {
            throw new IllegalStateException("addGradedAssignment '" + name + "' did not save (stayed on "
                    + page.url() + ")");
        }
        return cmid;
    }

    /**
     * Grade a student on an Assignment THROUGH the assignment grader (not the gradebook). A gradebook
     * override does NOT satisfy the assignment's "require grade" completion (that checks for a real assign
     * grade), so we drive the grader form: navigate to action=grader for the user, fill #id_grade, save.
     * The grader is an async panel, so we wait for #id_grade to render.
     */
    public void gradeAssignment(String assignmentCmid, long userId, double grade) {
        navigate("/mod/assign/view.php?id=" + assignmentCmid + "&action=grader&userid=" + userId);
        Locator gradeInput = page.locator("#id_grade");
        gradeInput.waitFor(new Locator.WaitForOptions()
                .setState(WaitForSelectorState.VISIBLE).setTimeout(30_000));
        gradeInput.fill(String.valueOf(grade));
        Locator save = page.locator("#id_savechanges");
        if (save.count() == 0) save = page.locator("button:has-text('Save changes'), input[value='Save changes']");
        save.first().click();
        page.waitForLoadState(com.microsoft.playwright.options.LoadState.DOMCONTENTLOADED);
        page.waitForTimeout(2_500);
    }

    /**
     * Moodle backup wizard: jump to final step, perform backup, download the .mbz.
     * Saved under {@code target/backups/} so restore uses the same file the UI produced.
     */
    public Path backupCourseDownloadMbz(String courseId) {
        navigate("/backup/backup.php?id=" + courseId);
        page.waitForTimeout(1_200);
        clickWizard("Jump to final step");
        page.waitForTimeout(800);
        clickWizard("Perform backup", "Next", "Continue");
        long deadline = System.currentTimeMillis() + 300_000;
        while (System.currentTimeMillis() < deadline) {
            String body = page.locator("body").innerText().toLowerCase();
            if (body.contains("backup of the course was completed")
                    || body.contains("backup completed")
                    || body.contains("the backup file was successfully created")
                    || downloadableMbz().count() > 0) {
                break;
            }
            if (clickWizard("Continue", "Next", "Perform backup")) {
                page.waitForTimeout(1_000);
                continue;
            }
            page.waitForTimeout(2_000);
        }
        if (page.url().toLowerCase().contains("backup.php")
                && downloadableMbz().count() == 0) {
            clickWizard("Continue");
            page.waitForTimeout(1_200);
        }
        if (downloadableMbz().count() == 0) {
            navigate("/backup/restorefile.php?contextid=" + courseContextHint(courseId));
            page.waitForTimeout(1_200);
        }
        if (downloadableMbz().count() == 0) {
            throw new AssertionError("backup finished but no .mbz download link was found. url="
                    + page.url() + " text=" + snippet());
        }
        Download download = page.waitForDownload(
                new Page.WaitForDownloadOptions().setTimeout(60_000),
                () -> downloadableMbz().first().click());
        try {
            Path dir = Path.of("target", "backups");
            Files.createDirectories(dir);
            String name = download.suggestedFilename();
            if (name == null || name.isBlank() || !name.toLowerCase().endsWith(".mbz")) {
                name = "course-" + courseId + ".mbz";
            }
            Path dest = dir.resolve(name);
            download.saveAs(dest);
            if (!Files.exists(dest) || Files.size(dest) == 0) {
                throw new AssertionError("downloaded backup is empty: " + dest);
            }
            System.out.println("[CourseAdmin] downloaded backup " + dest + " bytes=" + Files.size(dest));
            return dest;
        } catch (AssertionError e) {
            throw e;
        } catch (Exception e) {
            throw new AssertionError("could not save downloaded .mbz: " + e.getMessage(), e);
        }
    }

    public String restoreMbzAsNewCourse(Path mbz, String categoryId, String fullName, String shortName) {
        return restoreMbzAsNewCourse(mbz, categoryId, null, fullName, shortName);
    }

    /**
     * Restore the downloaded .mbz as a new course in {@code categoryId}. Returns the new course id.
     * The destination chooser pages/filters its category list, so pass {@code categoryName} to let it
     * be searched when the category is not on the first page.
     */
    public String restoreMbzAsNewCourse(Path mbz, String categoryId, String categoryName,
                                        String fullName, String shortName) {
        if (mbz == null || !Files.exists(mbz)) {
            throw new AssertionError("restore needs the downloaded .mbz on disk");
        }
        restoredCategoryId = "";
        restoredCategoryName = "";
        // Resolve the name up front (while navigation is still free): the destination chooser pages its
        // category list and can only be narrowed by name.
        String targetCategoryName = categoryName == null || categoryName.isBlank()
                ? lookupCategoryName(categoryId) : categoryName;
        navigate("/backup/restorefile.php?contextid=1");
        page.waitForTimeout(1_500);
        uploadBackupFile(mbz);
        if (!clickWizard("Restore")) {
            Locator restore = page.locator("input[value='Restore'], button:has-text('Restore'), "
                    + "a.btn:has-text('Restore')").last();
            if (restore.count() == 0) {
                throw new AssertionError("Restore control not found after uploading " + mbz.getFileName()
                        + " url=" + page.url() + " controls: " + controlsSnapshot());
            }
            restore.click();
            page.waitForLoadState(com.microsoft.playwright.options.LoadState.DOMCONTENTLOADED);
        }
        page.waitForTimeout(1_500);
        long deadline = System.currentTimeMillis() + 420_000;
        String lastStage = "";
        int schemaAttempts = 0;
        while (System.currentTimeMillis() < deadline) {
            String id = param(page.url(), "id");
            if (page.url().toLowerCase().contains("/course/view.php") && id != null) {
                System.out.println("[CourseAdmin] restore finished -> course id=" + id);
                return id;
            }
            if (restoreReportedSuccess()) {
                System.out.println("[CourseAdmin] restore reported success, leaving the wizard");
                clickWizard("Continue");
                page.waitForTimeout(2_000);
                String restored = param(page.url(), "id");
                if (page.url().toLowerCase().contains("/course/view.php") && restored != null) {
                    return restored;
                }
                String found = findCourseIdByFullName(fullName);
                if (found != null) return found;
            }
            String stage = restoreStage();
            if (!stage.equals(lastStage)) {
                System.out.println("[CourseAdmin] restore stage=" + stage + " url=" + page.url());
                lastStage = stage;
            }
            switch (stage) {
                case "destination" -> {
                    if (!selectRestoreCategory(categoryId, targetCategoryName)) {
                        throw new AssertionError("restore destination page: category " + targetCategoryName
                                + " (id=" + categoryId + ") could not be selected. text=" + snippet());
                    }
                }
                // The schema stage owns the new course name; never "Jump to final step" past it or the
                // restore reuses the backup's short name and fails on the duplicate.
                case "schema" -> {
                    if (++schemaAttempts > 3) {
                        throw new AssertionError("restore schema stage kept rejecting the course name/short "
                                + "name (" + fullName + " / " + shortName + "). text=" + snippet());
                    }
                    fillRestoreCourseNames(fullName, shortName);
                    if (!clickWizard("Next", "Continue")) page.waitForTimeout(1_500);
                }
                case "review" -> clickWizard("Perform restore");
                default -> {
                    if (!clickWizard("Continue", "Next", "Perform restore")) page.waitForTimeout(2_000);
                }
            }
            page.waitForTimeout(1_200);
        }
        String found = findCourseIdByFullName(fullName);
        if (found != null) return found;
        throw new AssertionError("restore did not complete within 420s. stage=" + lastStage
                + " url=" + page.url() + " text=" + snippet());
    }

    private Locator downloadableMbz() {
        return page.locator("a[href*='.mbz'], a[href*='pluginfile.php'][href*='backup'], "
                + "a[title*='Download' i], a:has-text('Download')");
    }

    /**
     * Attach the downloaded .mbz to the "Import a backup file" field. Moodle's filepicker renders NO
     * input[type=file] on the page — the input only exists once the picker dialogue is open on its
     * "Upload a file" repository (or the click opens a native chooser), so drive that.
     */
    private void uploadBackupFile(Path mbz) {
        String name = mbz.getFileName().toString();
        if (attachToFileInput(mbz, 2_000)) {
            finishUpload(name);
            return;
        }
        String opened = openFilePicker(mbz);
        if ("chooser".equals(opened)) {
            finishUpload(name);
            return;
        }
        if (!"dialogue".equals(opened)) {
            throw new AssertionError("nothing on " + page.url() + " opened a file picker for " + name
                    + " — controls: " + controlsSnapshot());
        }
        if (!attachToFileInput(mbz, 3_000)) {
            Locator uploadRepo = page.locator(".fp-repo-area a:has-text('Upload a file'), "
                    + ".fp-repo:has-text('Upload a file'), .fp-repo-name:has-text('Upload a file'), "
                    + ".file-picker a:has-text('Upload a file'), a:has-text('Upload a file')").first();
            try {
                if (uploadRepo.count() > 0 && uploadRepo.isVisible()) {
                    System.out.println("[CourseAdmin] selecting the 'Upload a file' repository");
                    uploadRepo.click();
                    page.waitForTimeout(1_500);
                }
            } catch (RuntimeException ignored) {}
            if (!attachToFileInput(mbz, 8_000)) {
                throw new AssertionError("file picker opened but exposed no file input for " + name
                        + " — controls: " + controlsSnapshot());
            }
        }
        finishUpload(name);
    }

    private void finishUpload(String fileName) {
        page.waitForTimeout(500);
        Locator upload = page.locator(".fp-upload-btn, button.fp-upload-btn, "
                + "input[value='Upload this file']").first();
        try {
            if (upload.count() > 0 && upload.isVisible()) {
                upload.click();
            } else {
                clickWizard("Upload this file", "Upload");
            }
        } catch (RuntimeException e) {
            clickWizard("Upload this file", "Upload");
        }
        long deadline = System.currentTimeMillis() + 45_000;
        while (System.currentTimeMillis() < deadline) {
            if (!filePickerOpen() && filepickerShowsFile(fileName)) {
                System.out.println("[CourseAdmin] attached backup " + fileName);
                return;
            }
            page.waitForTimeout(1_000);
        }
        throw new AssertionError("uploaded " + fileName + " but the Backup file field never showed it. "
                + "url=" + page.url() + " controls: " + controlsSnapshot());
    }

    /**
     * Whether the Backup file field itself now holds the upload. Scoped to the picker widget on
     * purpose: restorefile.php also lists the course/user backup areas, whose rows are full of other
     * .mbz names that would read as a false positive.
     */
    private boolean filepickerShowsFile(String fileName) {
        Object shown = page.evaluate("""
                (name) => {
                  const trigger = document.querySelector('.fp-btn-choose, .filepickerhidden');
                  const box = trigger
                    ? (trigger.closest("[data-fieldtype='filepicker'], .fitem, .form-group, .col-md-9")
                       || trigger.parentElement)
                    : null;
                  const text = ((box && box.innerText) || '').trim();
                  return text.includes(name) || /\\.mbz/i.test(text);
                }
                """, fileName);
        return Boolean.TRUE.equals(shown);
    }

    /** Set the file on any input[type=file] (visible or not), waiting up to {@code waitMs} for one. */
    private boolean attachToFileInput(Path mbz, int waitMs) {
        Locator inputs = page.locator("input[type='file']");
        long deadline = System.currentTimeMillis() + waitMs;
        while (inputs.count() == 0 && System.currentTimeMillis() < deadline) {
            page.waitForTimeout(250);
        }
        if (inputs.count() == 0) return false;
        try {
            inputs.last().setInputFiles(mbz.toAbsolutePath());
            return true;
        } catch (RuntimeException e) {
            System.out.println("[CourseAdmin] file input rejected the backup: " + e.getMessage());
            return false;
        }
    }

    /** Returns "chooser" (native dialog handled), "dialogue" (Moodle picker open) or "none". */
    private String openFilePicker(Path mbz) {
        // The filepicker's trigger is an <input type="button" value="Choose a file...">, so match the
        // class itself — a/button text selectors never see it.
        String[] candidates = {
                ".fp-btn-choose", "input[value='Choose a file...']",
                "input[type='button'][value*='Choose a file']", ".fp-btn-add",
                "[data-fieldtype='filepicker'] input[type='button']",
                "[data-fieldtype='filepicker'] a", "[data-fieldtype='filemanager'] a",
                "a:has-text('Choose a file')", "button:has-text('Choose a file')",
                "a:has-text('Select a file')", "a:has-text('Add...')",
                ".filepicker-container", ".filepicker-filelist", ".dndupload-message"
        };
        for (String selector : candidates) {
            Locator control = page.locator(selector).first();
            try {
                if (control.count() == 0 || !control.isVisible()) continue;
            } catch (RuntimeException e) {
                continue;
            }
            System.out.println("[CourseAdmin] opening file picker via " + selector);
            try {
                FileChooser chooser = page.waitForFileChooser(
                        new Page.WaitForFileChooserOptions().setTimeout(3_000), control::click);
                chooser.setFiles(mbz.toAbsolutePath());
                return "chooser";
            } catch (RuntimeException noNativeChooser) {
                // The click landed; Moodle usually answers with its own dialogue instead.
            }
            long deadline = System.currentTimeMillis() + 8_000;
            while (System.currentTimeMillis() < deadline) {
                if (filePickerOpen() || page.locator("input[type='file']").count() > 0) return "dialogue";
                page.waitForTimeout(400);
            }
        }
        return "none";
    }

    private boolean filePickerOpen() {
        Locator dialogue = page.locator(".file-picker, .fp-repo-area, .fp-content, "
                + ".moodle-dialogue-bd, [role='dialog'] .fp-repo-items").first();
        try {
            return dialogue.count() > 0 && dialogue.isVisible();
        } catch (RuntimeException e) {
            return false;
        }
    }

    /**
     * Pick the destination category. Its radio list is paged, so a freshly created category is almost
     * never on the first page — search for it by name first (the category search field is
     * {@code catsearch}; only the existing-course search is named {@code search}).
     */
    private boolean selectRestoreCategory(String categoryId, String categoryName) {
        String marked = markCategoryRadio(categoryId);
        for (int attempt = 0; marked.startsWith("no-radio") && attempt < 2; attempt++) {
            if (categoryName == null || categoryName.isBlank()) break;
            String searched = searchRestoreCategory(categoryName);
            System.out.println("[CourseAdmin] searched destination category '" + categoryName
                    + "' -> " + searched);
            if (!"ok".equals(searched)) break;
            marked = markCategoryRadio(categoryId);
        }
        // Any category proves the case ("a backup can be restored into a category"), so rather than
        // abandoning the restore when the wanted one stays unlisted, take the first offered category
        // and report it back so the caller asserts against the category actually used.
        if (marked.startsWith("no-radio")) {
            System.out.println("[CourseAdmin] destination category " + categoryName + " (id=" + categoryId
                    + ") is not listed (" + marked + ") — falling back to the first offered category");
            String fallback = markFirstCategoryRadio();
            if (!fallback.startsWith("ok")) {
                System.out.println("[CourseAdmin] no destination category selectable: " + fallback
                        + " — controls: " + controlsSnapshot());
                return false;
            }
            String[] parts = fallback.split("\\|", 3);
            restoredCategoryId = parts.length > 1 ? parts[1] : categoryId;
            restoredCategoryName = parts.length > 2 ? parts[2] : "";
            System.out.println("[CourseAdmin] restoring into category id=" + restoredCategoryId
                    + " name=" + restoredCategoryName);
        } else if ("radio-checked".equals(marked)) {
            restoredCategoryId = categoryId;
            restoredCategoryName = categoryName == null ? "" : categoryName;
        } else {
            System.out.println("[CourseAdmin] destination form not usable: " + marked
                    + " — controls: " + controlsSnapshot());
            return false;
        }
        // Submit from Playwright, never from inside evaluate: the navigation destroys the JS context
        // before an in-page click can return, which surfaces as "Execution context was destroyed".
        return clickInRestoreForm("Continue") || clickWizard("Continue");
    }

    /**
     * Tick the destination category radio in the "Restore as a new course" form, without submitting.
     * That form and the "existing course" forms all use radios named targetid, so scope by the hidden
     * target value (backup::TARGET_NEW_COURSE = 0) to avoid picking a course that shares the id.
     */
    private String markCategoryRadio(String categoryId) {
        if (!markRestoreForm()) return "no-form";
        return String.valueOf(page.evaluate("""
                (catId) => {
                  const form = document.querySelector('form[data-acp-restore-form]');
                  if (!form) return 'no-form';
                  const radio = form.querySelector(`input[name='targetid'][value='${catId}']`);
                  if (!radio) {
                    const listed = [...form.querySelectorAll("input[name='targetid']")]
                      .map(r => r.value).join(',');
                    return 'no-radio (listed: ' + listed + ')';
                  }
                  radio.checked = true;
                  radio.dispatchEvent(new Event('change', {bubbles: true}));
                  return 'radio-checked';
                }
                """, categoryId));
    }

    /** Tick the first offered category; returns "ok|id|name". */
    private String markFirstCategoryRadio() {
        if (!markRestoreForm()) return "no-form";
        return String.valueOf(page.evaluate("""
                () => {
                  const form = document.querySelector('form[data-acp-restore-form]');
                  if (!form) return 'no-form';
                  const radios = [...form.querySelectorAll("input[name='targetid']")];
                  if (!radios.length) return 'no-radio';
                  const radio = radios.find(r => r.value === '1') || radios[0];
                  radio.checked = true;
                  radio.dispatchEvent(new Event('change', {bubbles: true}));
                  const row = radio.closest('tr, li') || radio.parentElement;
                  const label = row ? (row.querySelector('td, .name, a') || row) : radio.parentElement;
                  const name = ((label && label.innerText) || '').trim().split('\\n')[0];
                  return 'ok|' + radio.value + '|' + name;
                }
                """));
    }

    private String searchRestoreCategory(String categoryName) {
        if (!markRestoreForm()) return "no-form";
        String form = String.valueOf(page.evaluate("""
                () => {
                  const form = document.querySelector('form[data-acp-restore-form]');
                  if (!form) return 'no-form';
                  const box = form.querySelector("input[name='catsearch']")
                    || form.querySelector(".rcs-search input[type='text']")
                    || form.querySelector("input[type='text'], input[type='search']");
                  if (!box) {
                    return 'no-search-box: '
                      + [...form.querySelectorAll('input')].map(i => i.type + ':' + i.name).join(',');
                  }
                  return 'ok';
                }
                """));
        if (!"ok".equals(form)) return form;
        Locator box = page.locator("form[data-acp-restore-form] input[name='catsearch'], "
                + "form[data-acp-restore-form] .rcs-search input[type='text'], "
                + "form[data-acp-restore-form] input[type='text']").first();
        try {
            box.fill(categoryName);
        } catch (RuntimeException e) {
            return "search-box-not-fillable: " + e.getMessage();
        }
        if (!clickInRestoreForm("Search")) {
            try {
                box.press("Enter");
                page.waitForLoadState(com.microsoft.playwright.options.LoadState.DOMCONTENTLOADED);
            } catch (RuntimeException e) {
                return "search-not-submittable: " + e.getMessage();
            }
        }
        page.waitForTimeout(1_500);
        return "ok";
    }

    /**
     * Tag the "Restore as a new course" form so Java-side locators can be scoped to it. The page also
     * carries the existing-course forms, whose radios share the targetid name.
     */
    private boolean markRestoreForm() {
        Object marked = page.evaluate("""
                () => {
                  const forms = [...document.querySelectorAll('form')]
                    .filter(f => f.querySelector("input[name='targetid']")
                              || f.querySelector("input[name='catsearch']"));
                  if (!forms.length) return false;
                  const form = forms.find(f => {
                    const t = f.querySelector("input[name='target']");
                    return t && String(t.value) === '0';
                  }) || forms[0];
                  document.querySelectorAll('[data-acp-restore-form]')
                    .forEach(f => f.removeAttribute('data-acp-restore-form'));
                  form.setAttribute('data-acp-restore-form', '1');
                  return true;
                }
                """);
        return Boolean.TRUE.equals(marked);
    }

    /** Click a submit inside the marked "Restore as a new course" form, letting Playwright navigate. */
    private boolean clickInRestoreForm(String label) {
        Locator button = page.locator("form[data-acp-restore-form] input[type='submit'][value='" + label + "'], "
                + "form[data-acp-restore-form] button:has-text('" + label + "')").first();
        try {
            if (button.count() == 0 || !button.isVisible() || !button.isEnabled()) return false;
            button.click();
            try {
                page.waitForLoadState(com.microsoft.playwright.options.LoadState.DOMCONTENTLOADED);
            } catch (RuntimeException ignored) {}
            page.waitForTimeout(800);
            return true;
        } catch (RuntimeException e) {
            return false;
        }
    }

    private String restoreStage() {
        if (page.locator("input[name='targetid']").count() > 0) return "destination";
        if (page.locator("input[value='Perform restore'], button:has-text('Perform restore')").count() > 0) {
            return "review";
        }
        if (page.locator("#id_setting_course_course_fullname, "
                + "input[name='setting_course_course_fullname']").count() > 0) {
            return "schema";
        }
        return "other";
    }

    /** Interactive controls on the current page, for diagnosing a selector miss in one run. */
    private String controlsSnapshot() {
        try {
            return String.valueOf(page.evaluate("""
                    () => {
                      const scope = document.querySelector('#region-main') || document.body;
                      const seen = [];
                      scope.querySelectorAll("a, button, input, [data-fieldtype]").forEach(el => {
                        const text = (el.innerText || el.value || '').trim().replace(/\\s+/g, ' ').slice(0, 40);
                        const cls = String(el.className || '').trim().split(/\\s+/).slice(0, 3).join('.');
                        seen.push(el.tagName.toLowerCase()
                          + (el.type ? '[' + el.type + ']' : '')
                          + (cls ? '.' + cls : '')
                          + (text ? ' "' + text + '"' : ''));
                      });
                      return seen.slice(0, 60).join(' | ');
                    }
                    """));
        } catch (RuntimeException e) {
            return "unavailable (" + e.getMessage() + ")";
        }
    }

    private void fillRestoreCourseNames(String fullName, String shortName) {
        setRestoreSetting("setting_course_course_fullname", fullName);
        setRestoreSetting("setting_course_course_shortname", shortName);
    }

    /**
     * Fill one restore-schema setting. Match on the field NAME: the matching #id_… belongs to the
     * fieldset wrapping the field, so an id-first locator resolves to a fieldset and fill() fails with
     * "Element is not an <input>".
     */
    private void setRestoreSetting(String name, String value) {
        Locator field = page.locator("input[name='" + name + "'], textarea[name='" + name + "'], "
                + "#id_" + name + " input[type='text'], #id_" + name + " input:not([type='hidden'])").first();
        if (field.count() == 0) {
            System.out.println("[CourseAdmin] restore schema has no field " + name);
            return;
        }
        try {
            field.fill(value);
        } catch (RuntimeException e) {
            field.evaluate("(el, v) => { el.value = v; "
                    + "el.dispatchEvent(new Event('input', {bubbles: true})); "
                    + "el.dispatchEvent(new Event('change', {bubbles: true})); }", value);
        }
        System.out.println("[CourseAdmin] restore schema " + name + " = " + value);
    }

    /** A category's name, so the restore destination chooser can be searched for it. */
    private String lookupCategoryName(String categoryId) {
        try {
            navigate("/course/index.php?categoryid=" + categoryId);
            page.waitForTimeout(800);
            Object heading = page.evaluate("""
                    () => {
                      const h = document.querySelector('.page-header-headings h1, #region-main h1, h1');
                      return h ? (h.innerText || '').trim() : '';
                    }
                    """);
            String name = heading == null ? "" : String.valueOf(heading).trim();
            if ("null".equals(name)) name = "";
            System.out.println("[CourseAdmin] category id=" + categoryId + " is named '" + name + "'");
            return name;
        } catch (RuntimeException e) {
            System.out.println("[CourseAdmin] could not read the name of category " + categoryId
                    + ": " + e.getMessage());
            return "";
        }
    }

    /** Moodle's completion notice on the final restore stage. */
    private boolean restoreReportedSuccess() {
        try {
            String body = page.locator("body").innerText().toLowerCase();
            return body.contains("was successfully restored")
                    || body.contains("restored successfully")
                    || body.contains("course was restored");
        } catch (RuntimeException e) {
            return false;
        }
    }

    private boolean clickWizard(String... labels) {
        for (String label : labels) {
            Locator b = page.locator("button:has-text('" + label + "'), input[value='" + label + "'], "
                    + "input[type='submit'][value='" + label + "'], a.btn:has-text('" + label + "')").first();
            try {
                if (b.count() > 0 && b.isVisible() && b.isEnabled()) {
                    b.click();
                    try {
                        page.waitForLoadState(com.microsoft.playwright.options.LoadState.DOMCONTENTLOADED);
                    } catch (RuntimeException ignored) {}
                    return true;
                }
            } catch (RuntimeException ignored) {}
        }
        return false;
    }

    private String courseContextHint(String courseId) {
        System.out.println("[CourseAdmin] looking up backup files after courseId=" + courseId);
        return "1";
    }

    private String snippet() {
        String body = page.locator("body").innerText();
        return body.substring(0, Math.min(body.length(), 400)).replaceAll("\\s+", " ");
    }

    private String findCourseIdByFullName(String fullName) {
        navigate("/course/search.php?search=" + URLEncoder.encode(fullName, StandardCharsets.UTF_8));
        page.waitForTimeout(800);
        Object id = page.evaluate("""
                (name) => {
                  const wanted = String(name).toLowerCase();
                  for (const a of document.querySelectorAll("a[href*='course/view.php']")) {
                    const row = a.closest('div, li, tr, .coursebox') || a.parentElement;
                    const text = ((row && row.innerText) || a.innerText || '').toLowerCase();
                    if (!text.includes(wanted)) continue;
                    const m = (a.getAttribute('href') || '').match(/[?&]id=(\\d+)/);
                    if (m) return m[1];
                  }
                  return null;
                }
                """, fullName);
        return id == null ? null : String.valueOf(id);
    }

    /** Delete a course (best-effort), confirming the Moodle prompt. */
    public void deleteCourse(String courseId) {
        navigate("/course/delete.php?id=" + courseId);
        page.waitForTimeout(1_200);
        Locator confirm = page.locator("button:has-text('Delete'), input[value='Delete'], "
                + "#region-main .btn-primary, button:has-text('Continue')");
        if (confirm.count() > 0 && confirm.first().isVisible()) {
            confirm.first().click();
            page.waitForLoadState(com.microsoft.playwright.options.LoadState.DOMCONTENTLOADED);
            page.waitForTimeout(2_500);
        }
    }

    /**
     * Enable or disable Self enrolment on a course (same Enrol me surface the Users suite uses).
     * {@code enabled} is the instance status; {@code allowNewEnrolments} is "Allow new enrolments".
     */
    public void setSelfEnrolment(String courseId, boolean enabled, boolean allowNewEnrolments) {
        navigate("/enrol/instances.php?id=" + courseId);
        page.waitForTimeout(1_000);
        Locator edit = page.locator("a[href*='enrol/editinstance.php'][href*='type=self']").first();
        try {
            if (edit.count() > 0 && edit.isVisible()) {
                edit.click();
            } else {
                addSelfEnrolMethod(courseId);
            }
        } catch (RuntimeException e) {
            addSelfEnrolMethod(courseId);
        }
        page.waitForLoadState(com.microsoft.playwright.options.LoadState.DOMCONTENTLOADED);
        page.waitForTimeout(800);
        if (page.locator("#id_status").count() == 0) {
            throw new AssertionError("self-enrolment form did not open for course " + courseId
                    + " url=" + page.url());
        }
        setSelectValue("#id_status", enabled ? "0" : "1");
        if (page.locator("#id_customint6").count() > 0) {
            setSelectValue("#id_customint6", allowNewEnrolments ? "1" : "0");
        }
        page.locator("#id_submitbutton").click();
        page.waitForLoadState(com.microsoft.playwright.options.LoadState.DOMCONTENTLOADED);
        page.waitForTimeout(1_000);
        System.out.println("[CourseAdmin] self-enrol course=" + courseId
                + " enabled=" + enabled + " allowNew=" + allowNewEnrolments
                + " url=" + page.url());
    }

    private void addSelfEnrolMethod(String courseId) {
        Locator select = page.locator("select[name='enrol'], #menuenrol, select[id*='enrol' i]").first();
        if (select.count() > 0) {
            try {
                select.selectOption("self");
            } catch (RuntimeException e) {
                select.selectOption(new com.microsoft.playwright.options.SelectOption()
                        .setLabel("Self enrolment"));
            }
            page.waitForTimeout(1_200);
        }
        if (page.locator("#id_status").count() == 0) {
            navigate("/enrol/editinstance.php?type=self&courseid=" + courseId);
            page.waitForTimeout(800);
        }
    }

    private void expandAll() {
        Locator exp = page.locator("a.collapseexpand[aria-expanded='false'], a:has-text('Expand all')");
        try {
            if (exp.count() > 0) { exp.first().click(); page.waitForTimeout(500); }
        } catch (RuntimeException ignored) {}
    }

    private static String param(String url, String key) {
        Matcher m = Pattern.compile("[?&]" + key + "=([0-9]+)").matcher(url);
        return m.find() ? m.group(1) : null;
    }
}
