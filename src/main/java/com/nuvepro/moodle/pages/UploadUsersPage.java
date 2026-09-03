package com.nuvepro.moodle.pages;

import com.microsoft.playwright.Download;
import com.microsoft.playwright.FileChooser;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.WaitForSelectorState;

import java.nio.file.Path;
import java.util.Locale;

/** Moodle core Upload users wizard (/admin/tool/uploaduser/index.php). */
public final class UploadUsersPage extends BasePage {
    public UploadUsersPage(Page page) {
        super(page);
    }

    public void open() {
        navigate("/admin/tool/uploaduser/index.php");
        page.locator("input[name='submitbutton'], input[value='Upload users']").first()
                .waitFor(new Locator.WaitForOptions()
                        .setState(WaitForSelectorState.VISIBLE).setTimeout(30_000));
        // The submit button is server-rendered but the file picker only works once its JS has run,
        // so waiting for the picker control keeps the attach below from racing the page.
        try {
            page.locator(".fp-btn-choose, [data-fieldtype='filepicker'] input[type='button'], "
                            + "[data-fieldtype='filemanager'] input[type='button']").first()
                    .waitFor(new Locator.WaitForOptions()
                            .setState(WaitForSelectorState.VISIBLE).setTimeout(20_000));
        } catch (RuntimeException ignored) {
            // some themes render a drag-and-drop target instead; attachCsv retries either way
        }
    }

    public boolean initialFormVisible() {
        return page.locator("input[name='userfile'], [data-fieldtype='filepicker'], "
                + "[data-fieldtype='filemanager']").count() > 0
                && page.locator("select[name='delimiter_name']").count() > 0;
    }

    public boolean exampleCsvLinkVisible() {
        return exampleCsvLink().count() > 0 && exampleCsvLink().isVisible();
    }

    public Path downloadExampleCsv(Path dest) {
        Locator link = exampleCsvLink();
        if (link.count() == 0) {
            throw new AssertionError("Upload users example CSV link is missing at " + page.url());
        }
        String href = link.getAttribute("href");
        try {
            Download download = page.waitForDownload(
                    new Page.WaitForDownloadOptions().setTimeout(8_000), link::click);
            download.saveAs(dest);
        } catch (RuntimeException downloadNotTriggered) {
            if (href == null || href.isBlank()) {
                throw new AssertionError("example CSV neither downloaded nor had an href",
                        downloadNotTriggered);
            }
            byte[] body = page.request().get(href).body();
            try {
                java.nio.file.Files.createDirectories(dest.getParent());
                java.nio.file.Files.write(dest, body);
            } catch (java.io.IOException e) {
                throw new RuntimeException(e);
            }
        }
        if (!initialFormVisible()) {
            open();
        }
        return dest;
    }

    private Locator exampleCsvLink() {
        return page.locator("a[href*='example'], a[href$='.csv'], a:has-text('example')").first();
    }

    /** @return false when Moodle's file picker refused the file (empty file, wrong type) */
    public boolean chooseCsv(Path csv) {
        attachCsv(csv);
        return !dismissBlockingDialogue();
    }

    /**
     * The repository dialogue is YUI-driven and occasionally needs a second nudge before it renders
     * its {@code input[type=file]}; a single attempt fails the scenario for a reason that has nothing
     * to do with the case under test, so open it up to three times.
     */
    private void attachCsv(Path csv) {
        for (int attempt = 1; attempt <= 3; attempt++) {
            if (attach(csv, attempt == 1 ? 1_000 : 2_000)) {
                confirmPickerUpload();
                return;
            }
            openPicker(csv);
            if (attach(csv, 10_000)) {
                confirmPickerUpload();
                return;
            }
            dismissBlockingDialogue();
            page.waitForTimeout(1_000);
        }
        throw new AssertionError("Upload users file picker exposed no input[type=file] at " + page.url());
    }

    private void openPicker(Path csv) {
        Locator trigger = page.locator(".fp-btn-choose, input[value*='Choose a file'], "
                + "[data-fieldtype='filepicker'] input[type='button'], "
                + "[data-fieldtype='filemanager'] input[type='button'], .dndupload-message").first();
        if (trigger.count() == 0) {
            throw new AssertionError("Upload users file picker was not found at " + page.url());
        }
        try {
            FileChooser chooser = page.waitForFileChooser(
                    new Page.WaitForFileChooserOptions().setTimeout(3_000), trigger::click);
            chooser.setFiles(csv.toAbsolutePath());
            return;
        } catch (RuntimeException ignored) {
            // Moodle normally opens its repository dialogue instead of a native chooser.
        }
        page.waitForTimeout(800);
        Locator repo = page.locator(".fp-repo:has-text('Upload a file'), "
                + ".fp-repo-name:has-text('Upload a file'), a:has-text('Upload a file')").first();
        if (repo.count() > 0) {
            try {
                repo.click(new Locator.ClickOptions().setTimeout(5_000));
            } catch (RuntimeException ignored) {
                // already on the upload repository
            }
            page.waitForTimeout(600);
        }
    }

    private void confirmPickerUpload() {
        Locator upload = page.locator(".fp-upload-btn, input[value='Upload this file'], "
                + "button:has-text('Upload this file')").first();
        if (upload.count() > 0 && upload.isVisible()) {
            upload.click();
            page.waitForTimeout(1_000);
        }
    }

    private boolean attach(Path csv, int waitMs) {
        long deadline = System.currentTimeMillis() + waitMs;
        Locator input = page.locator("input[type='file']");
        while (input.count() == 0 && System.currentTimeMillis() < deadline) {
            page.waitForTimeout(200);
        }
        if (input.count() == 0) return false;
        input.last().setInputFiles(csv.toAbsolutePath());
        return true;
    }

    /**
     * CSV delimiter. Moodle labels these options with the literal characters (",", ";", ":", "\t"),
     * so they are chosen by option value ("comma", "semicolon", "colon", "tab") instead.
     */
    public void setDelimiter(String value) {
        Object selected = page.locator("select[name='delimiter_name']").first().evaluate("""
                (el, wanted) => {
                  const option = [...el.options].find(o => o.value === wanted);
                  if (!option) return [...el.options].map(o => o.value).join(' | ');
                  el.value = option.value;
                  el.dispatchEvent(new Event('change', {bubbles: true}));
                  return 'ok';
                }
                """, value);
        if (!"ok".equals(String.valueOf(selected))) {
            throw new AssertionError("No CSV delimiter '" + value + "'. Available: " + selected);
        }
    }

    /**
     * Moodle's file picker rejects some files (a zero-byte CSV) with a YUI exception dialogue whose
     * lightbox then swallows every later click. Close it and report that the file was refused.
     */
    public boolean dismissBlockingDialogue() {
        Locator dialogue = page.locator(".moodle-dialogue-exception, .moodle-dialogue-base:visible");
        if (dialogue.count() == 0) return false;
        boolean dismissed = false;
        for (String selector : new String[]{
                ".moodle-dialogue-exception .closebutton",
                ".moodle-dialogue-base .closebutton",
                ".moodle-dialogue-base button:has-text('OK')",
                ".moodle-dialogue-base input[value='OK']"}) {
            Locator close = page.locator(selector).first();
            if (close.count() == 0) continue;
            try {
                close.click(new Locator.ClickOptions().setTimeout(3_000).setForce(true));
                dismissed = true;
                break;
            } catch (RuntimeException ignored) {
                // try the next close control
            }
        }
        if (!dismissed) {
            // Last resort: drop the dialogue and its lightbox so the page is clickable again.
            page.evaluate("""
                    () => document
                        .querySelectorAll('.moodle-dialogue-base, .moodle-dialogue-lightbox, .yui3-widget-mask')
                        .forEach(el => el.remove())
                    """);
        }
        page.waitForTimeout(300);
        return true;
    }

    /** Submit the file, then settle on either the preview or a rejection — never on a half-loaded page,
     *  which would make a "no preview" assertion pass for the wrong reason. */
    public void submitInitial() {
        dismissBlockingDialogue();
        page.locator("input[name='submitbutton'], input[value='Upload users'], "
                + "button:has-text('Upload users')").first().click();
        try {
            page.waitForLoadState(com.microsoft.playwright.options.LoadState.DOMCONTENTLOADED,
                    new Page.WaitForLoadStateOptions().setTimeout(60_000));
        } catch (RuntimeException ignored) {
            // fall through to the polling below
        }
        for (int i = 0; i < 40; i++) {
            if (previewVisible() || rejectionShown()) return;
            page.waitForTimeout(500);
        }
    }

    /** An mform validation message or a Moodle notification explaining why the file was refused. */
    private boolean rejectionShown() {
        if (page.locator(".alert-danger, .alert-warning, .notifyproblem").count() > 0) return true;
        Object hasError = page.evaluate("""
                () => [...document.querySelectorAll("[id^='id_error_']")]
                        .some(el => (el.textContent || '').trim().length > 0)
                """);
        return Boolean.TRUE.equals(hasError);
    }

    public boolean previewVisible() {
        return page.locator("select[name='uutype']").count() > 0
                || body().contains("upload users preview");
    }

    public boolean previewContains(String text) {
        return body().contains(text.toLowerCase(Locale.ROOT));
    }

    /** Limit the first-page CSV preview (the initial upload form offers 10/20/100/...). */
    public void setPreviewRows(int rows) {
        setSelectValue("select[name='previewrows']", String.valueOf(rows));
    }

    /** Upload type (Add new only / Add all, append number / Add new and update / Update existing). */
    public void setUploadType(String text) {
        selectContaining("select[name='uutype']", text);
    }

    public void setNewUserPassword(String text) {
        selectContaining("select[name='uupasswordnew']", text);
    }

    public void setExistingDetails(String text) {
        selectContaining("select[name='uuupdatetype']", text);
    }

    /** Existing user password ("No changes" / "Update"). */
    public void setExistingPassword(String text) {
        selectContaining("select[name='uupasswordold']", text);
    }

    public void setForcePasswordChange(String text) {
        selectContaining("select[name='uuforcepasswordchange']", text);
    }

    /** Select for bulk user actions: No / New users / Updated users / All users. */
    public void setBulkSelection(String text) {
        selectContaining("select[name='uubulk']", text);
    }

    public void setAllowRenames(boolean yes) {
        setYesNo("select[name='uuallowrenames']", yes);
    }

    public void setAllowDeletes(boolean yes) {
        setYesNo("select[name='uuallowdeletes']", yes);
    }

    public void setAllowSuspends(boolean yes) {
        setYesNo("select[name='uuallowsuspends']", yes);
    }

    /**
     * Prevent email address duplicates. Moodle renders this as a select only when
     * {@code $CFG->allowaccountssameemail} is on; otherwise it is a hidden input fixed at Yes.
     *
     * @return false when the site config leaves it unsettable and a different value was asked for
     */
    public boolean setPreventEmailDuplicates(boolean yes) {
        if (page.locator("select[name='uunoemailduplicates']").count() == 0) {
            return yes;      // hidden input is hard-coded to Yes
        }
        setYesNo("select[name='uunoemailduplicates']", yes);
        return true;
    }

    public void setStandardiseUsernames(boolean yes) {
        setYesNo("select[name='uustandardusernames']", yes);
    }

    /**
     * A "Default values" text field. Moodle removes the default for any field the CSV supplies
     * (definition_after_data), so this only exists for columns absent from the file.
     */
    public boolean setDefaultValue(String field, String value) {
        Locator input = page.locator("input[name='" + field + "']").first();
        if (input.count() == 0) {
            Locator show = page.locator("a:has-text('Show more'), button:has-text('Show more')").first();
            if (show.count() > 0) {
                show.click();
                page.waitForTimeout(400);
                input = page.locator("input[name='" + field + "']").first();
            }
        }
        if (input.count() == 0) return false;
        input.evaluate("(el, v) => { el.value = v;"
                + " el.dispatchEvent(new Event('input', {bubbles: true}));"
                + " el.dispatchEvent(new Event('change', {bubbles: true})); }", value);
        return true;
    }

    /**
     * Submit the preview and wait for the results. Moodle streams the upload output row by row while
     * PHP still processes the file, so the first paint is only the page chrome — asserting straight
     * after the click reads an empty page even though every user was created.
     */
    public void commit() {
        Locator button = page.locator("input[name='submitbutton'], input[value='Upload users'], "
                + "button:has-text('Upload users')").last();
        button.click();
        waitForResults(120);
    }

    public boolean waitForResults(int seconds) {
        try {
            page.waitForLoadState(com.microsoft.playwright.options.LoadState.DOMCONTENTLOADED,
                    new Page.WaitForLoadStateOptions().setTimeout(seconds * 1_000L));
        } catch (RuntimeException ignored) {
            // streamed responses can keep the load state pending; the markers below decide
        }
        long deadline = System.currentTimeMillis() + seconds * 1_000L;
        while (System.currentTimeMillis() < deadline) {
            if (resultVisible()) return true;
            if (previewValidationError()) return false;
            page.waitForTimeout(1_000);
        }
        return resultVisible();
    }

    public void cancel() {
        Locator cancel = page.locator("input[name='cancel'], input[value='Cancel'], "
                + "button:has-text('Cancel'), a:has-text('Cancel')").first();
        if (cancel.count() == 0) throw new AssertionError("Cancel missing on Upload users preview");
        cancel.click();
        page.waitForTimeout(700);
    }

    public boolean resultVisible() {
        if (page.locator("select[name='uutype']").count() > 0) return false;   // still on the preview
        if (page.locator("input[value='Continue'], button:has-text('Continue'), "
                + "a:has-text('Continue')").count() > 0) {
            return true;
        }
        String text = body();
        return text.contains("upload users results")
                || text.contains("users created")
                || text.contains("users updated")
                || text.contains("users skipped")
                || text.contains("errors");
    }

    /** Preview re-rendered with mform validation messages — nothing was uploaded, stop waiting. */
    private boolean previewValidationError() {
        return page.locator("select[name='uutype']").count() > 0 && rejectionShown();
    }

    public String resultText() {
        return page.locator("body").innerText().replaceAll("\\s+", " ").trim();
    }

    /**
     * A count from the results summary, e.g. {@code resultCount(text, "Errors")} for
     * "Users created: 3 Users weak password: 0 Errors: 1", or -1 when the label is absent.
     *
     * The summary always prints "Errors: 0", so asserting on the word alone passes for a clean run —
     * always compare the number.
     */
    public static int resultCount(String resultText, String label) {
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile(java.util.regex.Pattern.quote(label) + "\\s*:\\s*(\\d+)",
                        java.util.regex.Pattern.CASE_INSENSITIVE)
                .matcher(resultText == null ? "" : resultText);
        return m.find() ? Integer.parseInt(m.group(1)) : -1;
    }

    /** The first label that is present, e.g. "Users having a weak password" / "Users weak password". */
    public static int resultCountAny(String resultText, String... labels) {
        for (String label : labels) {
            int count = resultCount(resultText, label);
            if (count >= 0) return count;
        }
        return -1;
    }

    /**
     * How many rows carry a marker such as "User not added - error". Moodle prints the per-row verdict
     * even when a later row aborts the run before the totals are printed.
     */
    public static int rowsContaining(String resultText, String marker) {
        if (resultText == null) return 0;
        String haystack = resultText.toLowerCase(Locale.ROOT);
        String needle = marker.toLowerCase(Locale.ROOT);
        int count = 0;
        for (int from = haystack.indexOf(needle); from >= 0; from = haystack.indexOf(needle, from + 1)) {
            count++;
        }
        return count;
    }

    public boolean resultContains(String text) {
        return resultText().toLowerCase(Locale.ROOT).contains(text.toLowerCase(Locale.ROOT));
    }

    public void continueFromResults() {
        Locator cont = page.locator("button:has-text('Continue'), input[value='Continue'], "
                + "a:has-text('Continue')").first();
        if (cont.count() == 0) throw new AssertionError("Continue missing from Upload users results");
        cont.click();
        for (int i = 0; i < 20; i++) {
            if (initialFormVisible()) return;
            page.waitForTimeout(500);
        }
    }

    public boolean accessDenied() {
        String text = body();
        return !initialFormVisible() && (text.contains("permission") || text.contains("access denied")
                || text.contains("not have permission") || page.url().contains("/login/"));
    }

    private void setYesNo(String selector, boolean yes) {
        selectContaining(selector, yes ? "yes" : "no");
    }

    private void setSelectValue(String selector, String value) {
        Locator select = page.locator(selector).first();
        if (select.count() == 0) {
            throw new AssertionError("Upload users option missing: " + selector + " at " + page.url());
        }
        Object selected = select.evaluate("""
                (el, wanted) => {
                  const option = [...el.options].find(o => o.value === wanted);
                  if (!option) return [...el.options].map(o => o.value).join(' | ');
                  el.value = option.value;
                  el.dispatchEvent(new Event('change', {bubbles: true}));
                  return 'ok';
                }
                """, value);
        if (!"ok".equals(String.valueOf(selected))) {
            throw new AssertionError("No option value '" + value + "' in " + selector
                    + ". Available: " + selected);
        }
    }

    private void selectContaining(String selector, String text) {
        Locator select = page.locator(selector).first();
        if (select.count() == 0) {
            throw new AssertionError("Upload users option missing: " + selector + " at " + page.url());
        }
        Object selected = select.evaluate("""
                (el, wanted) => {
                  const needle = String(wanted).trim().toLowerCase();
                  const option = [...el.options].find(o =>
                    (o.textContent || '').trim().toLowerCase().includes(needle));
                  if (!option) return [...el.options].map(o => (o.textContent || '').trim()).join(' | ');
                  el.removeAttribute('disabled');
                  el.disabled = false;
                  el.value = option.value;
                  el.dispatchEvent(new Event('change', {bubbles: true}));
                  return 'ok';
                }
                """, text);
        if (!"ok".equals(String.valueOf(selected))) {
            throw new AssertionError("No option containing '" + text + "' in " + selector
                    + ". Available: " + selected);
        }
        page.waitForTimeout(200);
    }

    private String body() {
        return page.locator("body").innerText().toLowerCase(Locale.ROOT);
    }
}
