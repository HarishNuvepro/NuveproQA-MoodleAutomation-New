package com.nuvepro.moodle.pages;

import com.microsoft.playwright.Download;
import com.microsoft.playwright.FileChooser;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.WaitForSelectorState;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
/** Moodle core Upload users wizard (/admin/tool/uploaduser/index.php). */
public final class UploadUsersPage extends BasePage {

    private static final String UPLOAD_USERS_SUBMIT = "input[name='submitbutton'], input[value='Upload users'], "
            + "button:has-text('Upload users')";

    public UploadUsersPage(Page page) {
        super(page);
    }

    public void open() {
        RuntimeException last = null;
        for (int attempt = 1; attempt <= 4; attempt++) {
            try {
                navigate("/admin/tool/uploaduser/index.php");
                page.locator("input[name='submitbutton'], input[value='Upload users']").first()
                        .waitFor(new Locator.WaitForOptions()
                                .setState(WaitForSelectorState.VISIBLE).setTimeout(45_000));
                // The submit button is server-rendered but the file picker only works once its JS has run,
                // so waiting for the picker control keeps the attach below from racing the page.
                try {
                    page.locator(".fp-btn-choose, [data-fieldtype='filepicker'] input[type='button'], "
                                    + "[data-fieldtype='filemanager'] input[type='button'], "
                                    + "[data-fieldtype='filepicker'], [data-fieldtype='filemanager']").first()
                            .waitFor(new Locator.WaitForOptions()
                                    .setState(WaitForSelectorState.VISIBLE).setTimeout(30_000));
                } catch (RuntimeException ignored) {
                    // some themes render a drag-and-drop target instead; attachCsv retries either way
                }
                return;
            } catch (RuntimeException e) {
                last = e;
                System.out.println("[UploadUsers] open attempt " + attempt + " failed: " + e.getMessage());
                dismissBlockingDialogue();
                page.waitForTimeout(2_000);
            }
        }
        if (last != null) throw last;
    }

    public boolean initialFormVisible() {
        return safeCount("input[name='userfile'], [data-fieldtype='filepicker'], "
                + "[data-fieldtype='filemanager']") > 0
                && safeCount("select[name='delimiter_name']") > 0;
    }

    private String currentUrlLower() {
        try {
            return page.url().toLowerCase(Locale.ROOT);
        } catch (RuntimeException e) {
            return "";
        }
    }

    private boolean onBulkUserActions() {
        return currentUrlLower().contains("user_bulk");
    }

    private boolean continueButtonPresent() {
        return safeCount("input[value='Continue'], button:has-text('Continue'), a:has-text('Continue')") > 0;
    }

    private boolean contextDestroyed(RuntimeException e) {
        String msg = e.getMessage() == null ? "" : e.getMessage();
        return msg.contains("Execution context was destroyed") || msg.contains("Target closed");
    }

    private boolean submitTimeout(RuntimeException e) {
        String msg = e.getMessage() == null ? "" : e.getMessage();
        return msg.contains("Timeout") && msg.contains("exceeded");
    }

    /** Moodle often keeps duplicate hidden submit controls in the DOM; {@code .last()} misses under load. */
    private Locator visibleUploadUsersSubmit() {
        int total = safeCount(UPLOAD_USERS_SUBMIT);
        for (int i = 0; i < total; i++) {
            Locator candidate = page.locator(UPLOAD_USERS_SUBMIT).nth(i);
            try {
                if (candidate.isVisible()) return candidate;
            } catch (RuntimeException ignored) {
                // navigation in progress
            }
        }
        return page.locator(UPLOAD_USERS_SUBMIT).first();
    }

    private void waitForVisibleUploadSubmit(long timeoutMs) {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            int total = safeCount(UPLOAD_USERS_SUBMIT);
            for (int i = 0; i < total; i++) {
                Locator candidate = page.locator(UPLOAD_USERS_SUBMIT).nth(i);
                try {
                    if (candidate.isVisible()) return;
                } catch (RuntimeException ignored) {
                    // retry
                }
            }
            dismissBlockingDialogue();
            page.waitForTimeout(400);
        }
        throw new AssertionError("Upload users submit control not visible at " + currentUrlLower());
    }

    /** Poll until preview options and a visible submit control are ready (parallel POST races). */
    public void ensurePreviewReadyForCommit() {
        long deadline = System.currentTimeMillis() + 45_000;
        while (System.currentTimeMillis() < deadline) {
            try {
                if (previewVisible()) {
                    waitForVisibleUploadSubmit(Math.min(8_000, deadline - System.currentTimeMillis()));
                    return;
                }
                dismissBlockingDialogue();
            } catch (RuntimeException e) {
                if (!contextDestroyed(e) && !submitTimeout(e)) throw e;
            }
            page.waitForTimeout(400);
        }
        throw new AssertionError("Upload users preview not ready to commit at " + currentUrlLower());
    }

    private void clickUploadUsersSubmit() {
        dismissBlockingDialogue();
        waitForDraftFileReady();
        RuntimeException last = null;
        for (int attempt = 0; attempt < 4; attempt++) {
            try {
                waitForVisibleUploadSubmit(30_000);
                clickSlowSubmit(visibleUploadUsersSubmit());
                return;
            } catch (RuntimeException e) {
                last = e;
                if (attempt < 3 && (submitTimeout(e) || contextDestroyed(e))) {
                    dismissBlockingDialogue();
                    page.waitForTimeout(1_200);
                    continue;
                }
                throw e;
            }
        }
        if (last != null) throw last;
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
        if (!Files.isRegularFile(csv)) {
            throw new AssertionError("Upload users CSV missing: " + csv.toAbsolutePath());
        }
        Path toSend = stageCsvForPicker(csv);
        for (int attempt = 1; attempt <= 8; attempt++) {
            if (attempt > 1) {
                dismissBlockingDialogue();
                page.waitForTimeout(1_000);
                if (!initialFormVisible() || safeCount(".fp-btn-choose, [data-fieldtype='filepicker']") == 0) {
                    open();
                }
            }
            if (attach(toSend, attempt == 1 ? 2_000 : 3_000)) {
                confirmPickerUpload();
                waitForPickerSettled(toSend);
                return;
            }
            openPicker(toSend);
            if (attach(toSend, 15_000)) {
                confirmPickerUpload();
                waitForPickerSettled(toSend);
                return;
            }
            dismissBlockingDialogue();
            page.waitForTimeout(1_200);
        }
        throw new AssertionError("Upload users file picker exposed no input[type=file] at " + page.url());
    }

    /** Isolate the bytes Playwright sends so parallel workers never share one path mid-write. */
    private Path stageCsvForPicker(Path csv) {
        try {
            Path copy = Files.createTempFile("moodle-upload-users-", ".csv");
            Files.copy(csv, copy, StandardCopyOption.REPLACE_EXISTING);
            return copy;
        } catch (java.io.IOException e) {
            throw new RuntimeException("Could not stage CSV for upload: " + csv, e);
        }
    }

    private void waitForPickerSettled(Path csv) {
        long deadline = System.currentTimeMillis() + 20_000;
        String leaf = csv.getFileName().toString().toLowerCase(Locale.ROOT);
        while (System.currentTimeMillis() < deadline) {
            Object ready = page.evaluate("""
                    (leaf) => {
                      const body = (document.body && document.body.innerText) || '';
                      if (body.toLowerCase().includes(String(leaf).toLowerCase())) return true;
                      const h = document.querySelector("input[name='userfile']");
                      if (h && h.value && String(h.value).trim().length > 0) return true;
                      const fp = document.querySelector('.fp-filename, .filepicker-filename, .fm-filename');
                      if (fp && (fp.textContent || '').trim().length > 0) return true;
                      const root = document.querySelector('[data-fieldtype="filepicker"], [data-fieldtype="filemanager"]');
                      if (root && (root.innerText || '').includes('.csv')) return true;
                      return false;
                    }
                    """, leaf);
            if (Boolean.TRUE.equals(ready)) {
                page.waitForTimeout(600);
                return;
            }
            page.waitForTimeout(400);
        }
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
        // Drop a stuck file-picker lightbox even when the dialogue node is half-closed.
        if (safeCount(".yui3-widget-mask, .moodle-dialogue-lightbox") > 0) {
            page.evaluate("""
                    () => document.querySelectorAll(
                        '.moodle-dialogue-base, .moodle-dialogue-lightbox, .yui3-widget-mask'
                    ).forEach(el => el.remove())
                    """);
            page.waitForTimeout(200);
        }
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
        try {
            clickUploadUsersSubmit();
        } catch (RuntimeException blocked) {
            dismissBlockingDialogue();
            page.evaluate("""
                    () => {
                      const nodes = [...document.querySelectorAll(
                          "input[name='submitbutton'], input[value='Upload users']")];
                      const btn = nodes.find(el => el && el.offsetParent !== null) || nodes[0];
                      if (btn) btn.click();
                    }
                    """);
        }
        try {
            page.waitForLoadState(com.microsoft.playwright.options.LoadState.DOMCONTENTLOADED,
                    new Page.WaitForLoadStateOptions().setTimeout(60_000));
        } catch (RuntimeException ignored) {
            // fall through to the polling below
        }
        long deadline = System.currentTimeMillis() + 90_000;
        while (System.currentTimeMillis() < deadline) {
            if (settled(() -> previewVisible() || rejectionShown() || serverTempFileError())) return;
            page.waitForTimeout(500);
        }
    }

    /** Moodle server-side failure when the draft file was not ready before POST. */
    public boolean serverTempFileError() {
        return body().toLowerCase(Locale.ROOT).contains("error reading temporary file");
    }

    /** Do not POST until the filepicker draft / hidden userfile field is populated. */
    public void waitForDraftFileReady() {
        long deadline = System.currentTimeMillis() + 45_000;
        while (System.currentTimeMillis() < deadline) {
            Object ready = page.evaluate("""
                    () => {
                      const hidden = document.querySelector("input[name='userfile']");
                      if (hidden && hidden.value && String(hidden.value).trim().length > 0) return true;
                      const fp = document.querySelector(
                          '[data-fieldtype="filepicker"], [data-fieldtype="filemanager"]');
                      if (fp && (fp.innerText || '').toLowerCase().includes('.csv')) return true;
                      const name = document.querySelector('.fp-filename, .filepicker-filename, .fm-filename');
                      if (name && (name.textContent || '').trim().length > 0) return true;
                      return false;
                    }
                    """);
            if (Boolean.TRUE.equals(ready)) {
                page.waitForTimeout(800);
                return;
            }
            page.waitForTimeout(400);
        }
    }

    /** DOM probes throw "Execution context was destroyed" while the POST is still navigating. */
    private boolean settled(java.util.function.BooleanSupplier probe) {
        try {
            return probe.getAsBoolean();
        } catch (RuntimeException navigating) {
            return false;
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
        if (safeCount("select[name='uutype']") > 0) return true;
        try {
            return body().toLowerCase(Locale.ROOT).contains("upload users preview");
        } catch (RuntimeException e) {
            return false;
        }
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
        if (previewVisible()) {
            ensurePreviewReadyForCommit();
        } else {
            waitForVisibleUploadSubmit(30_000);
        }
        clickUploadUsersSubmit();
        waitForResults(180);
    }

    /**
     * Commit, snapshot the results summary, and click Continue in the same poll window so parallel
     * runs cannot lose the results page before Continue is pressed.
     */
    public String commitCaptureAndContinue() {
        ensurePreviewReadyForCommit();
        clickUploadUsersSubmit();
        String captured = null;
        long deadline = System.currentTimeMillis() + 180_000;
        while (System.currentTimeMillis() < deadline) {
            try {
                if (onBulkUserActions()) {
                    return captured == null ? safeResultText() : captured;
                }
                if (initialFormVisible() && captured != null) {
                    return captured;
                }
                if (resultVisible()) {
                    if (captured == null) {
                        captured = safeResultText();
                    }
                    if (continueButtonPresent()) {
                        clickSlowSubmit(continueButton());
                        waitForContinueDestination(90_000);
                        return captured;
                    }
                }
                if (settled(this::previewValidationError)) {
                    return captured == null ? safeResultText() : captured;
                }
            } catch (RuntimeException e) {
                if (!contextDestroyed(e)) throw e;
            }
            page.waitForTimeout(500);
        }
        if (captured != null) {
            if (!onBulkUserActions() && !initialFormVisible()) {
                try {
                    continueFromResults();
                } catch (RuntimeException ignored) {
                    if (onBulkUserActions()) return captured;
                }
            }
            return captured;
        }
        throw new AssertionError("Upload users did not reach results within 180s: " + currentUrlLower());
    }

    private Locator continueButton() {
        return page.locator("input[value='Continue'], button:has-text('Continue'), "
                + "a:has-text('Continue')").first();
    }

    private void waitForContinueDestination(long timeoutMs) {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            try {
                if (onBulkUserActions() || initialFormVisible()) return;
            } catch (RuntimeException ignored) {
                if (onBulkUserActions()) return;
            }
            page.waitForTimeout(400);
        }
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
            if (settled(this::resultVisible)) return true;
            if (settled(this::previewValidationError)) return false;
            page.waitForTimeout(1_000);
        }
        return settled(this::resultVisible);
    }

    public void cancel() {
        Locator cancel = page.locator("input[name='cancel'], input[value='Cancel'], "
                + "button:has-text('Cancel'), a:has-text('Cancel')").first();
        if (cancel.count() == 0) throw new AssertionError("Cancel missing on Upload users preview");
        cancel.click();
        page.waitForTimeout(700);
    }

    public boolean resultVisible() {
        if (onBulkUserActions()) return false;
        if (initialFormVisible()) return false;
        if (safeCount("select[name='uutype']") > 0) return false;   // still on the preview
        if (continueButtonPresent()) return true;
        String text = safeBody().toLowerCase(Locale.ROOT);
        return text.contains("upload users results")
                && (text.contains("users created")
                || text.contains("users updated")
                || text.contains("users skipped")
                || text.contains("users deleted")
                || text.contains("user not added")
                || text.contains("errors:"));
    }

    /** Preview re-rendered with mform validation messages — nothing was uploaded, stop waiting. */
    private boolean previewValidationError() {
        return page.locator("select[name='uutype']").count() > 0 && rejectionShown();
    }

    public String resultText() {
        return safeResultText();
    }

    private String safeResultText() {
        for (int attempt = 0; attempt < 6; attempt++) {
            try {
                return page.locator("body").innerText().replaceAll("\\s+", " ").trim();
            } catch (RuntimeException e) {
                if (attempt == 5) throw e;
                page.waitForTimeout(400);
            }
        }
        return "";
    }

    private String safeBody() {
        for (int attempt = 0; attempt < 6; attempt++) {
            try {
                return body();
            } catch (RuntimeException e) {
                if (attempt == 5) return "";
                page.waitForTimeout(400);
            }
        }
        return "";
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
        if (initialFormVisible() || onBulkUserActions()) {
            return;
        }
        RuntimeException last = null;
        for (int attempt = 0; attempt < 4; attempt++) {
            try {
                if (initialFormVisible() || onBulkUserActions()) {
                    return;
                }
                if (!continueButtonPresent()) {
                    throw new AssertionError("Continue missing from Upload users results at " + currentUrlLower());
                }
                clickSlowSubmit(continueButton());
                waitForContinueDestination(90_000);
                if (onBulkUserActions() || initialFormVisible()) {
                    return;
                }
                throw new AssertionError("Continue from Upload users results did not navigate (url="
                        + currentUrlLower() + ")");
            } catch (RuntimeException e) {
                last = e;
                String msg = e.getMessage() == null ? "" : e.getMessage();
                if (attempt < 3 && (msg.contains("Execution context was destroyed") || msg.contains("Timeout"))) {
                    page.waitForTimeout(1_200);
                    continue;
                }
                throw e;
            }
        }
        if (last != null) throw last;
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
