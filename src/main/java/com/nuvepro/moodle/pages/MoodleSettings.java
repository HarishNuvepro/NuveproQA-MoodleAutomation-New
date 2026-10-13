package com.nuvepro.moodle.pages;

import com.microsoft.playwright.FileChooser;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;

/**
 * Core Moodle admin settings pages (/admin/settings.php?section=...) — NOT the cloudlabs plugin.
 * Covers Course default settings, Grade category/item settings, Activity chooser settings, and the
 * NuveTheme "Running Banner" setting. One generic Page Object because every admin_settingpage form
 * shares the same structure (label + input/select/textarea inside .form-item) — field ids below were
 * read directly off the live site, not guessed.
 * NOTE: the save button here is a plain {@code <button class="btn btn-primary">Save changes</button>}
 * with NO id — unlike moodleform-based pages (e.g. AdminUsers' #id_submitbutton). Verified live: the
 * admin nav also has a "Search" submit button, so the selector must match on text, not just type=submit.
 */
public class MoodleSettings extends BasePage {
    public static final String SUBMIT = "button:has-text('Save changes')";

    public MoodleSettings(Page page) {
        super(page);
    }

    public void open(String section) {
        navigate("/admin/settings.php?section=" + section);
        page.waitForTimeout(800);
    }

    // ---- course forms (for verifying a site default propagates to NEW vs EXISTING courses) ----
    // The add-new-course / edit-course forms mirror the site defaults; field ids are the plain
    // moodleform ids (id_format, id_visible, id_numsections, id_lang, ...), not the s_moodlecourse_* ids.

    /** Open the "Add a new course" form under a category (fields pre-filled from the site defaults). */
    public void openAddCourseForm(String categoryId) {
        navigate("/course/edit.php?category=" + categoryId);
        page.locator("#id_fullname, #id_format").first().waitFor(new Locator.WaitForOptions()
                .setState(com.microsoft.playwright.options.WaitForSelectorState.VISIBLE).setTimeout(30_000));
    }

    /** Open an EXISTING course's edit-settings form by course id. */
    public void openEditCourseForm(String courseId) {
        navigate("/course/edit.php?id=" + courseId);
        page.locator("#id_fullname, #id_format").first().waitFor(new Locator.WaitForOptions()
                .setState(com.microsoft.playwright.options.WaitForSelectorState.VISIBLE).setTimeout(30_000));
    }

    // ---- config change log (Site admin > Reports > Config changes) ----

    public void openConfigLog() {
        navigate("/report/configlog/index.php");
        page.locator("table").first().waitFor(new Locator.WaitForOptions()
                .setState(com.microsoft.playwright.options.WaitForSelectorState.VISIBLE).setTimeout(30_000));
        page.waitForTimeout(500);
    }

    /** True if a recent config-log row mentions this setting name (columns: Plugin/Setting/New/Original). */
    public boolean configLogHasRecent(String settingName, int scanRows) {
        Locator rows = page.locator("table tbody tr");
        int n = Math.min(rows.count(), scanRows);
        for (int i = 0; i < n; i++) {
            if (rows.nth(i).innerText().contains(settingName)) return true;
        }
        return false;
    }

    /** True if a non-admin hit the Moodle permission wall instead of the settings form. */
    public boolean hasAccessError() {
        return page.locator(".errorbox, .alert-danger").count() > 0
                || page.locator("#adminsettings .form-item").count() == 0
                    && page.locator("body").innerText().toLowerCase().contains("error");
    }

    public boolean formLoaded() {
        return page.locator("#adminsettings .form-item, .form-item").count() > 0;
    }

    // ---- select fields (Moodle admin_setting_configselect) ----

    public String selectedText(String fieldId) {
        Locator sel = page.locator("#" + fieldId);
        return (String) sel.evaluate("e => e.options[e.selectedIndex] ? e.options[e.selectedIndex].text.trim() : ''");
    }

    public void selectByLabel(String fieldId, String visibleText) {
        page.locator("#" + fieldId).selectOption(new com.microsoft.playwright.options.SelectOption().setLabel(visibleText));
    }

    public java.util.List<String> optionTexts(String fieldId) {
        return page.locator("#" + fieldId + " option").allInnerTexts();
    }

    // ---- text fields (Moodle admin_setting_configtext) ----

    public String textValue(String fieldId) {
        return page.locator("#" + fieldId).inputValue();
    }

    public void setText(String fieldId, String value) {
        Locator f = page.locator("#" + fieldId);
        try {
            f.fill(value, new Locator.FillOptions().setTimeout(3_000));
        } catch (RuntimeException notEditable) {
            // Some admin text fields are hidden by a hideIf dependency (e.g. the NuveTheme banner text is
            // hidden until the banner checkbox is on). A hidden field still submits, so set it via JS.
            f.evaluate("(el, v) => { el.value = v;"
                    + " el.dispatchEvent(new Event('input', {bubbles:true}));"
                    + " el.dispatchEvent(new Event('change', {bubbles:true})); }", value);
        }
    }

    // ---- checkboxes (Moodle admin_setting_configcheckbox: hidden default + real checkbox, same id) ----

    public boolean isChecked(String fieldId) {
        return page.locator("#" + fieldId).isChecked();
    }

    /**
     * Set a checkbox to an absolute state. Most Moodle admin checkboxes are visible and check()/uncheck()
     * work, but some (e.g. the NuveTheme banner toggle) are rendered as a styled control with the real
     * input hidden — for those, set the property deterministically via JS and fire the change event so
     * Moodle's hideIf dependencies update. NOTE: deliberately does NOT early-return on a pre-read of
     * isChecked() — right after open() that read can be stale, which previously caused a restore to skip
     * unchecking and leave the site-wide banner ON.
     */
    public void setChecked(String fieldId, boolean checked) {
        Locator cb = page.locator("#" + fieldId);
        boolean visible;
        try { visible = cb.isVisible(); } catch (RuntimeException e) { visible = false; }
        if (visible) {
            if (checked) cb.check(); else cb.uncheck();
        } else {
            cb.evaluate("(el, want) => { el.checked = want; el.removeAttribute('checked');"
                    + " el.dispatchEvent(new Event('change', {bubbles:true})); }", checked);
        }
    }

    public void save() {
        submitAndAwaitNewPage(page.locator(SUBMIT));
        page.waitForTimeout(1_200);
    }

    // ---- generic dispatch: one step vocabulary for select / checkbox / text fields ----

    /** Read a field's current value regardless of control type ("Yes"/"No" for checkboxes). */
    public String getValue(String fieldId) {
        Locator ctrl = page.locator("#" + fieldId);
        String tag = (String) ctrl.evaluate("e => e.tagName");
        if ("SELECT".equals(tag)) return selectedText(fieldId);
        if ("checkbox".equals(ctrl.getAttribute("type"))) return isChecked(fieldId) ? "Yes" : "No";
        return textValue(fieldId);
    }

    /** Set a field's value regardless of control type ("Yes"/"No" for checkboxes) and save. */
    public void setValue(String fieldId, String value) {
        Locator ctrl = page.locator("#" + fieldId);
        String tag = (String) ctrl.evaluate("e => e.tagName");
        if ("SELECT".equals(tag)) {
            selectByLabel(fieldId, value);
        } else if ("checkbox".equals(ctrl.getAttribute("type"))) {
            setChecked(fieldId, "Yes".equalsIgnoreCase(value));
        } else {
            setText(fieldId, value);
        }
    }

    /**
     * Advanced grade item options on Grade item settings ({@code grade_item_adv} multi-checkbox).
     * Keys are Moodle's internal names; labels vary slightly by language pack.
     */
    public static final String[] DEFAULT_ADV_KEYS = {
            "iteminfo", "idnumber", "gradepass", "plusfactor", "multfactor",
            "display", "decimals", "hiddenuntil", "locktime"
    };
    public static final String[] EXTRA_ADV_KEYS = {
            "gradetype", "scaleid", "grademin", "grademax",
            "hidden", "locked", "aggregationcoef", "parentcategory"
    };

    public Locator advancedGradeItemRoot() {
        Locator byId = page.locator("#admin-grade_item_adv, [id*='grade_item_adv']").first();
        if (byId.count() > 0) return byId;
        return page.locator(".form-item, .settingsform .row").filter(
                new Locator.FilterOptions().setHasText("Advanced grade item")).first();
    }

    public boolean advancedGradeItemSettingPresent() {
        return advancedGradeItemRoot().count() > 0
                && page.locator("input[type='checkbox'][name*='grade_item_adv'], "
                + "#admin-grade_item_adv input[type='checkbox']").count() > 0;
    }

    @SuppressWarnings("unchecked")
    public java.util.Map<String, Boolean> advancedGradeItemStates() {
        Object raw = page.evaluate("""
                () => {
                  const out = {};
                  const boxes = document.querySelectorAll(
                      "input[type='checkbox'][name*='grade_item_adv']");
                  boxes.forEach(el => {
                    const m = (el.name || '').match(/\\[([^\\]]+)\\]/);
                    const key = m ? m[1] : (el.id || '').replace(/^id_s__grade_item_adv_/, '');
                    if (key) out[key] = !!el.checked;
                  });
                  return out;
                }
                """);
        java.util.Map<String, Boolean> map = new java.util.LinkedHashMap<>();
        if (raw instanceof java.util.Map<?, ?> src) {
            for (java.util.Map.Entry<?, ?> e : src.entrySet()) {
                map.put(String.valueOf(e.getKey()), Boolean.TRUE.equals(e.getValue())
                        || "true".equalsIgnoreCase(String.valueOf(e.getValue())));
            }
        }
        return map;
    }

    public void setAdvancedGradeItemKeys(java.util.Collection<String> keys, boolean checked) {
        page.evaluate("""
                (payload) => {
                  const [csv, want] = payload.split('|');
                  const set = new Set(csv.split(',').filter(Boolean));
                  document.querySelectorAll("input[type='checkbox'][name*='grade_item_adv']")
                    .forEach(el => {
                      const m = (el.name || '').match(/\\[([^\\]]+)\\]/);
                      const key = m ? m[1] : (el.id || '').replace(/^id_s__grade_item_adv_/, '');
                      if (!set.has(key)) return;
                      el.checked = want === 'true';
                      el.dispatchEvent(new Event('change', {bubbles: true}));
                    });
                }
                """, String.join(",", keys) + "|" + checked);
    }

    public void restoreAdvancedGradeItemStates(java.util.Map<String, Boolean> states) {
        if (states == null || states.isEmpty()) return;
        open("gradeitemsettings");
        for (java.util.Map.Entry<String, Boolean> e : states.entrySet()) {
            setAdvancedGradeItemKeys(java.util.List.of(e.getKey()), Boolean.TRUE.equals(e.getValue()));
        }
        save();
    }

    public String addSimpleAssignment(String courseId, String name) {
        navigate("/course/modedit.php?add=assign&type=&course=" + courseId + "&section=1&return=0&sr=0");
        page.locator("#id_name").waitFor(new Locator.WaitForOptions()
                .setState(com.microsoft.playwright.options.WaitForSelectorState.VISIBLE).setTimeout(30_000));
        page.locator("#id_name").fill(name);
        submitAndAwaitNewPage(page.locator("#id_submitbutton"));
        page.waitForTimeout(1_500);
        String cmid = paramFromUrl(page.url(), "id");
        if (cmid == null) {
            throw new IllegalStateException("assignment '" + name + "' did not save: " + page.url());
        }
        return cmid;
    }

    public void openGradeItemEditor(String courseId) {
        navigate("/grade/edit/tree/index.php?id=" + courseId);
        page.waitForTimeout(1_000);
        Locator edit = page.locator("a[href*='grade/edit/tree/item.php']").first();
        if (edit.count() == 0) {
            throw new IllegalStateException("no grade item editor link on gradebook setup for course "
                    + courseId);
        }
        edit.click();
        page.locator("#id_itemname, #id_gradetype, #region-main").first().waitFor(
                new Locator.WaitForOptions()
                        .setState(com.microsoft.playwright.options.WaitForSelectorState.VISIBLE)
                        .setTimeout(30_000));
        collapseGradeItemAdvanced();
    }

    private void collapseGradeItemAdvanced() {
        Locator less = page.locator("a:has-text('Show less'), button:has-text('Show less'), "
                + ".moreless-toggler:has-text('less')").first();
        try {
            if (less.count() > 0 && less.isVisible()) less.click();
        } catch (RuntimeException ignored) {}
        page.waitForTimeout(400);
    }

    /**
     * Whether a grade-item form field is hidden behind Show more / the advanced group.
     * {@code missing} if the label is not on the form at all.
     */
    public String gradeItemFieldVisibility(String label) {
        Object raw = page.evaluate("""
                (label) => {
                  const want = label.toLowerCase().replace(/\\s+/g, ' ').trim();
                  const nodes = [...document.querySelectorAll(
                      'label, .col-form-label, .fitemtitle, .form-label-addon')];
                  const hit = nodes.find(n => {
                    const t = (n.innerText || '').toLowerCase().replace(/\\s+/g, ' ').trim();
                    return t === want || t.startsWith(want);
                  });
                  if (!hit) return 'missing';
                  const item = hit.closest('.fitem, .form-group, [data-groupname], .row');
                  if (!item) return 'visible';
                  const style = window.getComputedStyle(item);
                  const hidden = item.classList.contains('advanced')
                      || item.classList.contains('d-none')
                      || item.hidden
                      || style.display === 'none'
                      || style.visibility === 'hidden';
                  return hidden ? 'advanced' : 'visible';
                }
                """, label);
        return raw == null ? "missing" : String.valueOf(raw);
    }

    public String addFileResource(String courseId, String name, java.nio.file.Path file) {
        navigate("/course/modedit.php?add=resource&type=&course=" + courseId + "&section=1&return=0&sr=0");
        page.locator("#id_name").waitFor(new Locator.WaitForOptions()
                .setState(com.microsoft.playwright.options.WaitForSelectorState.VISIBLE).setTimeout(30_000));
        page.locator("#id_name").fill(name);
        attachToFileManager(file);
        submitAndAwaitNewPage(page.locator("#id_submitbutton"));
        page.waitForTimeout(1_500);
        return page.url();
    }

    /**
     * Attach a file in the File resource filemanager. Returns null on success, or the rejection
     * text when Moodle / the picker refuses the file (size limit).
     */
    public String tryAttachFileOnResourceForm(String courseId, String name, java.nio.file.Path file) {
        navigate("/course/modedit.php?add=resource&type=&course=" + courseId + "&section=1&return=0&sr=0");
        page.locator("#id_name").waitFor(new Locator.WaitForOptions()
                .setState(com.microsoft.playwright.options.WaitForSelectorState.VISIBLE).setTimeout(30_000));
        page.locator("#id_name").fill(name);
        return attachToFileManagerOrReject(file);
    }

    private void attachToFileManager(java.nio.file.Path file) {
        String reject = attachToFileManagerOrReject(file);
        if (reject != null) {
            throw new AssertionError("file '" + file.getFileName() + "' was rejected: " + reject);
        }
    }

    private String attachToFileManagerOrReject(java.nio.file.Path file) {
        String fileName = file.getFileName().toString();
        Locator trigger = page.locator(".fp-btn-add, .fp-btn-choose, "
                + "[data-fieldtype='filemanager'] .dndupload-arrow, "
                + "[data-fieldtype='filemanager'] a, .filemanager .fp-btn-add").first();
        try {
            com.microsoft.playwright.FileChooser chooser = page.waitForFileChooser(
                    new Page.WaitForFileChooserOptions().setTimeout(4_000), () -> {
                        if (trigger.count() > 0) trigger.click();
                    });
            chooser.setFiles(file.toAbsolutePath());
        } catch (RuntimeException ignored) {
            openUploadRepository();
            Locator input = page.locator("input[type='file']");
            long deadline = System.currentTimeMillis() + 8_000;
            while (input.count() == 0 && System.currentTimeMillis() < deadline) {
                page.waitForTimeout(200);
            }
            if (input.count() == 0) {
                return "no file input on " + page.url();
            }
            input.last().setInputFiles(file.toAbsolutePath());
        }
        page.waitForTimeout(800);
        Locator upload = page.locator(".fp-upload-btn, input[value='Upload this file'], "
                + "button:has-text('Upload this file')").first();
        try {
            if (upload.count() > 0 && upload.isVisible()) upload.click();
        } catch (RuntimeException ignored) {}
        long deadline = System.currentTimeMillis() + 45_000;
        while (System.currentTimeMillis() < deadline) {
            String reject = sizeRejectText();
            if (reject != null) return reject;
            if (fileManagerShows(fileName)) return null;
            page.waitForTimeout(400);
        }
        String reject = sizeRejectText();
        return reject != null ? reject : "file never appeared in the file manager: " + fileName;
    }

    private void openUploadRepository() {
        Locator repo = page.locator(".fp-repo:has-text('Upload a file'), "
                + ".fp-repo-name:has-text('Upload a file'), a:has-text('Upload a file')").first();
        try {
            if (repo.count() > 0 && repo.isVisible()) repo.click();
        } catch (RuntimeException ignored) {}
        page.waitForTimeout(600);
    }

    private boolean fileManagerShows(String fileName) {
        String body = page.locator(".filemanager, .fp-content, [data-fieldtype='filemanager'], body")
                .first().innerText();
        return body != null && body.contains(fileName);
    }

    private String sizeRejectText() {
        String body = page.locator("body").innerText();
        if (body == null) return null;
        String lower = body.toLowerCase();
        if (lower.contains("too large") || lower.contains("maximum size")
                || lower.contains("exceeds") || lower.contains("bigger than")
                || lower.contains("maxbytes") || lower.contains("file is larger")) {
            return body.lines()
                    .map(String::trim)
                    .filter(l -> {
                        String s = l.toLowerCase();
                        return s.contains("too large") || s.contains("maximum")
                                || s.contains("exceed") || s.contains("bigger");
                    })
                    .findFirst()
                    .orElse("file size rejected");
        }
        Locator err = page.locator(".moodle-exception-message, .alert-danger, .fp-msg-error, "
                + ".notifyproblem, [data-region='toast']");
        if (err.count() > 0) {
            String t = err.first().innerText();
            if (t != null && !t.isBlank()) {
                String s = t.toLowerCase();
                if (s.contains("large") || s.contains("size") || s.contains("maximum")
                        || s.contains("exceed")) {
                    return t.trim();
                }
            }
        }
        return null;
    }

    private static String paramFromUrl(String url, String name) {
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("[?&]" + name + "=(\\d+)").matcher(url);
        return m.find() ? m.group(1) : null;
    }
}
