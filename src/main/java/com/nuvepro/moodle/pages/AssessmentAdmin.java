package com.nuvepro.moodle.pages;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.WaitUntilState;

import java.util.List;

/**
 * Admin-side assessment (challenge) surfaces that are NOT the running lab — so they exercise WITHOUT
 * provisioning a cloud lab. Covers:
 *   - the Assessment Problem Statements page (assessment-problemstatements.mustache): the Add New / Edit /
 *     View / Delete / Activate / Deactivate toolbar + the data table, and activate/deactivate of a row.
 *   - the activity edit form's "Attempts Summary Columns" config group (mod_form.php `attemptsummaryfields`,
 *     hidden unless labtype=challenge) — the admin control to hide Grade/Marks/Result from the summary.
 *   - the per-user Override form (override_form.php via managechallenges → Override ▸ Add): the
 *     "Unlimited" submissions option + the optional (unlimited) duration.
 * Selectors read directly from the activity-cloudlabs 4.8.0 templates + mod_form.php, not guessed.
 */
public class AssessmentAdmin extends BasePage {

    // problem statements toolbar (assessment-problemstatements.mustache)
    public static final String PS_TABLE   = "#np-ap-ps-problemstatement";
    public static final String PS_ADDNEW  = "#addNewBtn";
    public static final String PS_EDIT    = "#editBtn";
    public static final String PS_VIEW    = "#viewBtn";
    public static final String PS_DELETE  = "#deleteBtn";
    public static final String PS_ACTIVATE   = "#activateBtn";
    public static final String PS_DEACTIVATE = "#deActivateBtn";

    // manage challenges + override dropdown (manage-challenge.mustache)
    public static final String MC_TABLE   = "#np-ap-manage-challenge-table";
    public static final String MANAGE_ATTEMPT_TABLE = "#np-ap-manage-attempt-table";
    public static final String MANAGE_ACTIONS_BTN = "#np-ap-manage-actions-btn";
    public static final String MANAGE_UPDATE_GRADES = "a#np-ap-manage-update-grades-btn.dropdown-item, "
            + "#attempt-toolbar a#np-ap-manage-update-grades-btn";
    public static final String USER_ATTEMPTS_TABLE = "#np-ap-user-attempts-table";
    public static final String VIEW_USER_SUBMISSION = "#viewUserAttemptsSubmission";
    public static final String OVERRIDE_DROPDOWN = "#np-ap-cl-assessment-override-actions";
    public static final String OVERRIDE_ADD      = "#np-ap-cl-add-user-override";

    // override form (override_form.php)
    public static final String OV_MAXATTEMPTS   = "#id_maxattempts, select[name='maxattempts']";
    public static final String OV_TIMELIMIT_EN  = "#id_timelimit_enabled";

    public AssessmentAdmin(Page page) {
        super(page);
    }

    private void go(String path) {
        // Retry on transient network errors (ERR_INTERNET_DISCONNECTED etc.) so a blip during a long run
        // does not cascade into failures — wait for the connection to recover, then retry.
        RuntimeException last = null;
        for (int i = 0; i < 3; i++) {
            try {
                page.navigate(path, new Page.NavigateOptions()
                        .setWaitUntil(WaitUntilState.DOMCONTENTLOADED).setTimeout(45_000));
                page.waitForTimeout(2_500);
                return;
            } catch (RuntimeException e) {
                last = e;
                String m = e.getMessage() == null ? "" : e.getMessage();
                boolean network = m.contains("ERR_INTERNET_DISCONNECTED") || m.contains("ERR_NETWORK")
                        || m.contains("ERR_CONNECTION") || m.contains("ERR_NAME_NOT_RESOLVED")
                        || m.contains("ERR_TIMED_OUT");
                if (!network) throw e;
                page.waitForTimeout(6_000);   // let the network recover before retrying
            }
        }
        throw last;
    }

    /** Read the cloudlabs module-instance id ("sectionid" param) off the activity landing. */
    public String sectionIdFor(int cmid) {
        go(com.nuvepro.moodle.config.Settings.BASE_URL + "/mod/cloudlabs/view.php?id=" + cmid);
        page.waitForTimeout(3_000);
        for (String sel : new String[]{
                "#np-ap-gl-sectionId", "#np-ap-cl-sectionId", "input[name='sectionId']"}) {
            Locator sec = page.locator(sel);
            if (sec.count() == 0) continue;
            String v = sec.first().inputValue();
            if (v != null && !v.isBlank()) return v.trim();
        }
        return "";
    }

    /** The course id an activity belongs to (from the page body's "course-<N>" class). 0 if unknown. */
    public long courseIdFor(int cmid) {
        go(com.nuvepro.moodle.config.Settings.BASE_URL + "/mod/cloudlabs/view.php?id=" + cmid);
        page.waitForTimeout(3_000);
        String bodyClass = page.locator("body").getAttribute("class");
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("course-(\\d+)")
                .matcher(bodyClass == null ? "" : bodyClass);
        return m.find() ? Long.parseLong(m.group(1)) : 0L;
    }

    // ---------------- Problem Statements page ----------------

    public void openProblemStatements(String sectionId) {
        go(com.nuvepro.moodle.config.Settings.BASE_URL
                + "/mod/cloudlabs/assessment-problemstatements.php?sectionid=" + sectionId);
        // the toolbar renders immediately; the table body loads via a custom AJAX call
        // (controllers/services/assessmentpsservices.php) — wait for it to finish before reading rows.
        page.locator(PS_ADDNEW + ", " + PS_TABLE).first()
                .waitFor(new Locator.WaitForOptions().setTimeout(30_000));
        waitTableLoaded(PS_TABLE, 20);
    }

    /** A control counts as PRESENT if it exists in the DOM (Edit/View/Delete start disabled; Activate/
     *  Deactivate start hidden with d-none — all still present, per the template). */
    public boolean present(String selector) {
        return page.locator(selector).count() > 0;
    }

    /** Real problem-statement rows (a bootstrap-table "No matching records" placeholder does not count). */
    public int problemStatementRows() {
        return page.locator(PS_TABLE + " tbody tr:not(.no-records-found)").count();
    }

    /** Click "Add New" on the problem statements page and report whether it navigated to a PS form. */
    public boolean clickAddProblemStatement() {
        if (page.locator(PS_ADDNEW).count() == 0) return false;
        try { page.locator(PS_ADDNEW).first().click(); } catch (RuntimeException ignored) {}
        page.waitForTimeout(2_000);
        return page.url().contains("-ps.php") || page.url().contains("problemstatement")
                || page.locator("#id_name, form").count() > 0;
    }

    /** Select the first PS row then report whether a toolbar control (by selector) became enabled. */
    public boolean psRowControlEnabled(String selector) {
        if (!selectFirstProblemStatementRow()) return false;
        return page.locator(selector).count() > 0 && page.locator(selector).first().getAttribute("disabled") == null;
    }

    /** Wait until a bootstrap-table has finished its async load — it renders at least one tr (a data row
     *  OR a .no-records-found placeholder) only once the AJAX returns. */
    private void waitTableLoaded(String tableSelector, int seconds) {
        for (int i = 0; i < seconds * 2; i++) {
            if (page.locator(tableSelector + " tbody tr").count() > 0) { page.waitForTimeout(600); return; }
            page.waitForTimeout(500);
        }
    }

    private boolean noRecords(String tableSelector) {
        return page.locator(tableSelector + " tbody tr.no-records-found").count() > 0;
    }

    /** Select the first data row's radio (enables the toolbar + reveals Activate/Deactivate). */
    public boolean selectFirstProblemStatementRow() {
        Locator row = page.locator(PS_TABLE + " tbody tr").first();
        // skip a bootstrap-table "No matching records" placeholder row
        if (row.count() == 0 || row.locator("td").count() <= 1) return false;
        Locator radio = row.locator("input[type=radio], input[name='btSelectItem']");
        if (radio.count() == 0) return false;
        radio.first().check(new Locator.CheckOptions().setForce(true));
        page.waitForTimeout(800);
        return true;
    }

    /** Status text of the first data row (Status is the last column: "Active"/"Inactive"). */
    public String firstRowStatus() {
        Locator cells = page.locator(PS_TABLE + " tbody tr").first().locator("td");
        int n = cells.count();
        return n > 0 ? cells.nth(n - 1).innerText().trim() : "";
    }

    public boolean visible(String selector) {
        Locator l = page.locator(selector);
        for (int i = 0; i < l.count(); i++) {
            try { if (l.nth(i).isVisible()) return true; } catch (RuntimeException ignored) {}
        }
        return false;
    }

    /** Click whichever of Activate / Deactivate is currently visible for the selected row. Returns the
     *  action taken ("activate"/"deactivate"/""), handling an optional confirm modal. */
    public String toggleActivation() {
        String action = "";
        if (visible(PS_ACTIVATE))        { action = "activate";   page.locator(PS_ACTIVATE).first().click(); }
        else if (visible(PS_DEACTIVATE)) { action = "deactivate"; page.locator(PS_DEACTIVATE).first().click(); }
        else return "";
        page.waitForTimeout(1_000);
        confirmIfPresent();
        page.waitForTimeout(1_500);
        return action;
    }

    /** Some plugin builds pop a Moodle confirm dialog for activate/deactivate — accept it if shown. */
    private void confirmIfPresent() {
        Locator yes = page.locator("[data-region='modal-container'] button[data-action='save'], "
                + "[data-region='modal-container'] .btn-primary, button:has-text('Yes')");
        try {
            if (yes.count() > 0 && yes.first().isVisible()) { yes.first().click(); page.waitForTimeout(800); }
        } catch (RuntimeException ignored) {}
    }

    // ---------------- Attempts Summary Columns config (activity edit form) ----------------

    public void openActivityEditForm(int cmid) {
        go(com.nuvepro.moodle.config.Settings.BASE_URL + "/course/modedit.php?update=" + cmid);
        expandAll();
    }

    /** Open a fresh "add challenge activity" form (labtype=challenge) so DEFAULT field values are shown. */
    public void openNewChallengeForm(String courseId) {
        go(com.nuvepro.moodle.config.Settings.BASE_URL + "/course/modedit.php?add=cloudlabs&type=&course="
                + courseId + "&section=0&return=0&sr=0");
        Locator ch = page.locator("input[type=radio][name='labtype'][value='challenge']");
        if (ch.count() > 0 && !ch.first().isChecked()) {
            ch.first().check(new Locator.CheckOptions().setForce(true));
            page.waitForTimeout(1_000);
        }
        expandAll();
    }

    /** Open a fresh challenge form with Auto (script) evaluation selected, so the "Test Case Marks"
     *  radio group (testcaseweightage: SAME/DIFFERENT) is shown — it is hidden unless
     *  evaluationtype=AUTO and labtype=challenge (mod_form.php hideIf rules). */
    public void openChallengeFormForAutoEval(String courseId) {
        openNewChallengeForm(courseId); // labtype=challenge + expandAll
        Locator auto = page.locator("input[type=radio][name='evaluationtype'][value='AUTO']");
        if (auto.count() > 0 && !auto.first().isChecked()) {
            auto.first().check(new Locator.CheckOptions().setForce(true));
            page.waitForTimeout(1_000);
        }
    }

    /** Whether the "each test case marks" text field is visible (shown only for SAME weightage). */
    public boolean eachTestCaseMarksVisible() {
        Locator e = page.locator("[name='eachtestcasemarks']");
        for (int i = 0; i < e.count(); i++) {
            try { if (e.nth(i).isVisible()) return true; } catch (RuntimeException ignored) {}
        }
        return false;
    }

    /** Select a Test Case Marks weightage radio ("SAME" or "DIFFERENT") and let the form settle. */
    public void selectTestCaseWeightage(String value) {
        Locator r = page.locator("input[type=radio][name='testcaseweightage'][value='" + value + "']");
        r.first().check(new Locator.CheckOptions().setForce(true));
        page.waitForTimeout(800);
    }

    /** Whether a Test Case Marks weightage radio is currently checked. */
    public boolean testCaseWeightageChecked(String value) {
        Locator r = page.locator("input[type=radio][name='testcaseweightage'][value='" + value + "']");
        return r.count() > 0 && r.first().isChecked();
    }

    /** Open an activity's landing page (student/admin view). */
    public void openLanding(int cmid) {
        go(com.nuvepro.moodle.config.Settings.BASE_URL + "/mod/cloudlabs/view.php?id=" + cmid);
        page.waitForTimeout(3_000);
    }

    /** Selected label of a &lt;select&gt; by its name attribute (empty if absent). */
    public String selectedByName(String name) {
        Locator sel = page.locator("select[name='" + name + "']");
        if (sel.count() == 0) return "";
        return (String) sel.first().evaluate("e => e.options[e.selectedIndex] ? e.options[e.selectedIndex].text.trim() : ''");
    }

    /** Expand EVERY collapsible section on a Moodle form so fields in later sections become visible
     *  (the "Attempts Summary Columns" group lives in a section that may be collapsed by default). */
    private void expandAll() {
        try {
            // 1) the form-level "Expand all" toggle, only if it currently offers to expand
            Locator toggle = page.locator(".collapsible-actions a.collapseexpand");
            if (toggle.count() > 0 && toggle.first().innerText().toLowerCase().contains("expand")) {
                toggle.first().click();
                page.waitForTimeout(600);
            }
            // 2) belt-and-braces: expand any fieldset header still marked collapsed
            Locator collapsed = page.locator("a.collapseexpand[aria-expanded='false'], "
                    + "fieldset .fheader a[aria-expanded='false']");
            int n = collapsed.count();
            for (int i = 0; i < n; i++) {
                try { collapsed.nth(i).click(); page.waitForTimeout(150); } catch (RuntimeException ignored) {}
            }
        } catch (RuntimeException ignored) {}
        page.waitForTimeout(400);
    }

    /** The hide-from-summary checkbox for a named column, e.g. attemptsummaryfields[Grade]. Targets the
     *  real checkbox (advcheckbox also renders a same-named hidden input). */
    private Locator summaryCheckbox(String column) {
        return page.locator("input[type=checkbox][name='attemptsummaryfields[" + column + "]']");
    }

    /** True if the "Attempts Summary Columns" config (Grade/Marks/Result checkboxes) is VISIBLE on the
     *  form (present + not hidden by the labtype!=challenge hideIf). */
    public boolean attemptSummaryConfigVisible() {
        for (String col : new String[]{"Grade", "Marks", "Result"}) {
            Locator cb = summaryCheckbox(col);
            if (cb.count() == 0) return false;
            try { if (!cb.first().isVisible()) return false; } catch (RuntimeException e) { return false; }
        }
        return true;
    }

    /** A single attempt-summary-column checkbox is present + visible on the form. Column is one of
     *  Status/Grade/Result/Tests/Marks/FinalResult/EvaluationResult/adminConfiguration. */
    public boolean summaryFieldVisible(String column) {
        Locator cb = summaryCheckbox(column);
        if (cb.count() == 0) return false;
        try { cb.first().scrollIntoViewIfNeeded(); } catch (RuntimeException ignored) {}
        try { return cb.first().isVisible(); } catch (RuntimeException e) { return false; }
    }

    public boolean summaryFieldChecked(String column) {
        Locator cb = summaryCheckbox(column);
        return cb.count() > 0 && cb.first().isChecked();
    }

    /** Toggle an attempt-summary-column checkbox (no save — verifies the control accepts the change). */
    public void setSummaryField(String column, boolean on) {
        Locator cb = summaryCheckbox(column);
        if (cb.count() == 0) return;
        if (on) cb.first().check(new Locator.CheckOptions().setForce(true));
        else cb.first().uncheck(new Locator.UncheckOptions().setForce(true));
        page.waitForTimeout(200);
    }

    /** Save the activity settings form (persists the config). */
    public void saveActivityForm() {
        Locator save = page.locator("#id_submitbutton");
        if (save.count() > 0) {
            save.first().click();
            page.waitForLoadState(com.microsoft.playwright.options.LoadState.DOMCONTENTLOADED);
            page.waitForTimeout(2_500);
        }
    }

    /** A column in the manage-challenges attempt table is currently visible (bootstrap-table hides it
     *  when the admin-view attempt-summary config turns that column off). dataField e.g. "grade"/"result". */
    public boolean manageChallengeColumnVisible(String dataField) {
        Locator th = page.locator(MC_TABLE + " thead th[data-field='" + dataField + "']");
        if (th.count() == 0) return false;
        try { return th.first().isVisible(); } catch (RuntimeException e) { return false; }
    }

    // ---------------- Override form (submissions/duration to Unlimited) ----------------

    public void openManageChallenges(String sectionId) {
        go(com.nuvepro.moodle.config.Settings.BASE_URL
                + "/mod/cloudlabs/managechallenges.php?sectionid=" + sectionId);
        page.locator(MC_TABLE).first().waitFor(new Locator.WaitForOptions().setTimeout(30_000));
        waitTableLoaded(MC_TABLE, 25);
    }

    /** Real enrolled-user rows (excludes the "No matching records" placeholder). */
    public int manageChallengeUserRows() {
        return page.locator(MC_TABLE + " tbody tr:not(.no-records-found)").count();
    }

    /** Rows for enrolled users who have NOT completed (i.e. not-started / not-attempted / in-progress) —
     *  the completionstatus cell is anything other than "Completed". Proves the manage user assessment
     *  page lists a user who has not started the assessment (no attempt required). */
    public int manageChallengeNotStartedRows() {
        Locator rows = page.locator(MC_TABLE + " tbody tr:not(.no-records-found)");
        int n = 0;
        for (int i = 0; i < rows.count(); i++) {
            String s = "";
            Locator cell = rows.nth(i).locator("td[data-field='completionstatus']");
            try { if (cell.count() > 0) s = cell.first().innerText().trim().toLowerCase(); }
            catch (RuntimeException ignored) {}
            if (!s.contains("completed")) n++;   // not-started / not-attempted / in-progress
        }
        return n;
    }

    /** Drill into the first user's User Attempts page (userattempts.php) via the manage-challenges
     *  "User Attempt" button. Returns true if that page is reached. */
    public boolean openFirstUserAttemptsPage(String sectionId) {
        openManageChallenges(sectionId);
        Locator row = page.locator(MC_TABLE + " tbody tr:not(.no-records-found)").first();
        if (row.count() == 0) return false;
        Locator sel = row.locator("input[type=radio], input[name='btSelectItem']");
        try {
            if (sel.count() > 0) sel.first().check(new Locator.CheckOptions().setForce(true));
            else row.click();
        } catch (RuntimeException ignored) {}
        page.waitForTimeout(600);
        Locator ua = page.locator("#np-ap-user-attempt");
        if (ua.count() == 0) return false;
        try { ua.first().click(); } catch (RuntimeException ignored) {}
        try { page.waitForURL("**/userattempts.php**", new Page.WaitForURLOptions().setTimeout(15_000)); }
        catch (RuntimeException ignored) {}
        page.waitForTimeout(1_500);
        return page.url().contains("userattempts.php");
    }

    // Result of trying to open the override form. The plugin gates the "Add override" action on the
    // selected user's completionstatus === 'Completed' (manage-challenge.js), so a reopen/override is
    // only possible for a user who has already COMPLETED the assessment — mirroring the manual case's
    // "Assessment should be completed" precondition.
    public enum OverrideOpen { OPENED, NO_USERS, NO_COMPLETED_USER, NO_ELIGIBLE_USER, FORM_DID_NOT_LOAD }

    /** Diagnostic set when the override form fails to load: where we ended up + any on-screen alert. */
    public String overrideDiag = "";

    /**
     * Find a user whose assessment is Completed (the only ones the plugin lets you override), select
     * that row, open Override ▸ Add and wait for the override form. We detect "Completed" via the plugin's
     * OWN gate: after selecting a row, the Add control's {@code disabled} attribute is cleared only for a
     * completed user — more robust than parsing the status column by index.
     */
    public OverrideOpen openOverrideFormForCompletedUser() {
        int rows = manageChallengeUserRows();
        if (rows == 0 || noRecords(MC_TABLE)) return OverrideOpen.NO_USERS;

        boolean anyCompleted = false;
        boolean anyBlockedPending = false;
        for (int i = 0; i < rows; i++) {
            Locator row = page.locator(MC_TABLE + " tbody tr:not(.no-records-found)").nth(i);
            Locator radio = row.locator("input[type=radio], input[name='btSelectItem']");
            if (radio.count() == 0) continue;
            try { radio.first().check(new Locator.CheckOptions().setForce(true)); }
            catch (RuntimeException e) { continue; }
            page.waitForTimeout(500);
            // The Add anchor's disabled attribute is toggled by the plugin: null == enabled == Completed.
            if (page.locator(OVERRIDE_ADD).getAttribute("disabled") != null) continue;  // not completed
            anyCompleted = true;

            try { page.locator(OVERRIDE_DROPDOWN).first().click(); page.waitForTimeout(400); }
            catch (RuntimeException ignored) {}
            try { page.locator(OVERRIDE_ADD).first().click(new Locator.ClickOptions().setForce(true)); }
            catch (RuntimeException ignored) {}

            // The handler is async (str.get_strings(...).then). It EITHER navigates to overrideedit.php
            // OR pops a Moodle alert ("Cannot override. Global attempts are pending") and stays put —
            // the plugin only allows an override once the user has used ALL their global attempts.
            try {
                page.waitForURL("**/overrideedit.php**", new Page.WaitForURLOptions().setTimeout(8_000));
            } catch (RuntimeException navMiss) { /* not navigated — check for the alert below */ }

            if (page.url().contains("overrideedit.php")) {
                try {
                    page.locator(OV_MAXATTEMPTS).first().waitFor(new Locator.WaitForOptions()
                            .setState(com.microsoft.playwright.options.WaitForSelectorState.ATTACHED).setTimeout(15_000));
                    overrideDiag = "";
                    return OverrideOpen.OPENED;
                } catch (RuntimeException e) {
                    overrideDiag = "reached overrideedit.php but the maxattempts field never rendered (url="
                            + page.url() + ")";
                    return OverrideOpen.FORM_DID_NOT_LOAD;
                }
            }

            // Stayed on managechallenges → an alert blocked it. Record + dismiss, then try the next user.
            String alert = dismissAlert();
            if (alert.toLowerCase().contains("pending")) anyBlockedPending = true;
            overrideDiag = "blocked on user row " + (i + 1) + ": \"" + alert + "\"";
            page.waitForTimeout(400);
        }

        if (!anyCompleted) return OverrideOpen.NO_COMPLETED_USER;
        if (anyBlockedPending) return OverrideOpen.NO_ELIGIBLE_USER;
        return OverrideOpen.FORM_DID_NOT_LOAD;
    }

    /** Read then dismiss a Moodle notification.alert dialogue (the "OK" button); returns its text. */
    private String dismissAlert() {
        String text = "";
        Locator dlg = page.locator("[data-region='modal-container'], .moodle-dialogue-base, .modal.show");
        try {
            if (dlg.count() > 0) text = dlg.first().innerText().trim().replaceAll("\\s+", " ");
        } catch (RuntimeException ignored) {}
        try {
            Locator ok = page.locator("[data-region='modal-container'] button, .moodle-dialogue-base button, "
                    + ".modal.show button").filter(new Locator.FilterOptions().setHasText("OK"));
            if (ok.count() == 0) ok = page.locator("button:has-text('OK')");
            if (ok.count() > 0) { ok.first().click(new Locator.ClickOptions().setForce(true)); page.waitForTimeout(400); }
        } catch (RuntimeException ignored) {}
        return text;
    }

    /** Drill from manage challenges → user attempts → View Submission → the Manage User Submissions page
     *  (manageuserattempt.php). Returns true only if reached (needs an existing attempt). */
    /** Select the enrolled user row matching {@code emailOrUsername} on Manage Challenges. */
    public boolean selectManageChallengeUser(String sectionId, String emailOrUsername) {
        return selectManageChallengeUser(sectionId, emailOrUsername, null);
    }

    /** Match username or email (whichever appears in the manage-challenges table). */
    public boolean selectManageChallengeUser(String sectionId, String username, String email) {
        openManageChallenges(sectionId);
        Locator rows = page.locator(MC_TABLE + " tbody tr:not(.no-records-found)");
        for (int i = 0; i < rows.count(); i++) {
            Locator row = rows.nth(i);
            String text = row.innerText().toLowerCase();
            boolean match = false;
            if (username != null && !username.isBlank() && text.contains(username.toLowerCase())) match = true;
            if (!match && email != null && !email.isBlank() && text.contains(email.toLowerCase())) match = true;
            if (!match) continue;
            Locator sel = row.locator("input[type=radio], input[name='btSelectItem']");
            try {
                if (sel.count() > 0) sel.first().check(new Locator.CheckOptions().setForce(true));
                else row.click();
            } catch (RuntimeException ignored) {}
            page.waitForTimeout(600);
            return true;
        }
        return false;
    }

    /** Poll manage-challenges until the user's completion status contains {@code fragment} (e.g. "completed"). */
    public boolean waitForUserCompletionStatus(String sectionId, String username, String email,
                                               String fragment, int maxSeconds) {
        String want = fragment.toLowerCase();
        int polls = Math.max(1, maxSeconds / 3);
        for (int i = 0; i < polls; i++) {
            if (userCompletionStatusContains(sectionId, username, email, want)) return true;
            page.waitForTimeout(3_000);
        }
        return userCompletionStatusContains(sectionId, username, email, want);
    }

    public boolean userCompletionStatusContains(String sectionId, String username, String email, String fragment) {
        openManageChallenges(sectionId);
        Locator rows = page.locator(MC_TABLE + " tbody tr:not(.no-records-found)");
        String want = fragment.toLowerCase();
        for (int i = 0; i < rows.count(); i++) {
            Locator row = rows.nth(i);
            String text = row.innerText().toLowerCase();
            boolean match = (username != null && !username.isBlank() && text.contains(username.toLowerCase()))
                    || (email != null && !email.isBlank() && text.contains(email.toLowerCase()));
            if (!match) continue;
            Locator cell = row.locator("td[data-field='completionstatus']");
            String status = cell.count() > 0 ? cell.first().innerText().trim().toLowerCase() : text;
            return status.contains(want);
        }
        return false;
    }

    /** Manage-challenges completionstatus is {@code Completed} (Moodle activity completion for gating). */
    public boolean userAssessmentCompleted(String sectionId, String username, String email) {
        openManageChallenges(sectionId);
        Locator rows = page.locator(MC_TABLE + " tbody tr:not(.no-records-found)");
        for (int i = 0; i < rows.count(); i++) {
            Locator row = rows.nth(i);
            String text = row.innerText().toLowerCase();
            boolean match = (username != null && !username.isBlank() && text.contains(username.toLowerCase()))
                    || (email != null && !email.isBlank() && text.contains(email.toLowerCase()));
            if (!match) continue;
            Locator cell = row.locator("td[data-field='completionstatus']");
            String status = cell.count() > 0 ? cell.first().innerText().trim().toLowerCase() : text;
            return status.equals("completed") || status.startsWith("completed ");
        }
        return false;
    }

    /**
     * After the student has submitted, set their grade on Manage User Submissions (manageuserattempt.php).
     */
    public boolean updateGradeForStudent(String sectionId, String username, String email, double grade) {
        return updateGradeForStudent(sectionId, 0L, username, email, grade);
    }

    public boolean updateGradeForStudent(String sectionId, long moodleUserId, String username, String email,
                                         double grade) {
        if (!openManageUserSubmissionsForStudent(sectionId, moodleUserId, username, email)) return false;
        return applyGradeOnManageUserSubmissionsPage(grade);
    }

    /** userattempts.php → Manage User Submissions for the seeded student (direct URL when userid is known). */
    public boolean openManageUserSubmissionsForStudent(String sectionId, long moodleUserId, String username,
                                                       String email) {
        if (page.url().contains("manageuserattempt.php") && page.url().contains("sectionid=" + sectionId)) {
            waitManageAttemptTableReady();
            return true;
        }
        if (moodleUserId > 0) {
            go(com.nuvepro.moodle.config.Settings.BASE_URL
                    + "/mod/cloudlabs/userattempts.php?userid=" + moodleUserId + "&sectionid=" + sectionId);
            if (drillToManageUserSubmissionsFromUserAttempts(120)) {
                waitManageAttemptTableReady();
                return true;
            }
        }
        if (!selectManageChallengeUser(sectionId, username, email)) return false;
        Locator ua = page.locator("#np-ap-user-attempt");
        if (ua.count() == 0) return false;
        try { ua.first().click(); } catch (RuntimeException ignored) {}
        try { page.waitForURL("**/userattempts.php**", new Page.WaitForURLOptions().setTimeout(15_000)); }
        catch (RuntimeException ignored) {}
        if (!page.url().contains("userattempts.php")) return false;
        if (!drillToManageUserSubmissionsFromUserAttempts(120)) return false;
        waitManageAttemptTableReady();
        return page.url().contains("manageuserattempt.php");
    }

    /** Wait for an attempt row, then open manageuserattempt.php (View Submission or direct link). */
    private boolean drillToManageUserSubmissionsFromUserAttempts(int maxSeconds) {
        int polls = Math.max(1, maxSeconds / 3);
        for (int i = 0; i < polls; i++) {
            waitTableLoaded(USER_ATTEMPTS_TABLE, 5);
            if (selectFirstUserAttemptRow()) {
                Locator viewBtn = page.locator(VIEW_USER_SUBMISSION);
                if (viewBtn.count() > 0 && viewBtn.first().getAttribute("disabled") == null) {
                    try { viewBtn.first().click(); } catch (RuntimeException ignored) {}
                    if (waitForManageUserSubmissionsUrl(20_000)) return true;
                }
                Locator direct = page.locator("a[href*='manageuserattempt.php']");
                if (direct.count() > 0) {
                    try { direct.first().click(); } catch (RuntimeException ignored) {}
                    if (waitForManageUserSubmissionsUrl(20_000)) return true;
                }
            }
            refreshUserAttemptsTable();
            page.waitForTimeout(3_000);
        }
        return page.url().contains("manageuserattempt.php");
    }

    private boolean waitForManageUserSubmissionsUrl(long timeoutMs) {
        try {
            page.waitForURL("**/manageuserattempt.php**",
                    new Page.WaitForURLOptions().setTimeout(timeoutMs));
            return true;
        } catch (RuntimeException ignored) {
            return page.url().contains("manageuserattempt.php");
        }
    }

    private boolean selectFirstUserAttemptRow() {
        Locator row = page.locator(USER_ATTEMPTS_TABLE + " tbody tr:not(.no-records-found)").first();
        if (row.count() == 0) return false;
        Locator sel = row.locator("input[type=radio], input[name='btSelectItem']");
        try {
            if (sel.count() > 0) sel.first().check(new Locator.CheckOptions().setForce(true));
            else row.click();
        } catch (RuntimeException ignored) {}
        page.waitForTimeout(500);
        return true;
    }

    private void refreshUserAttemptsTable() {
        Locator refresh = page.locator("#user-attempts-toolbar button[name='btnAdd'], "
                + "button[title*='Refresh'], .fixed-table-toolbar button[name='btnAdd']");
        try {
            if (refresh.count() > 0) refresh.first().click(new Locator.ClickOptions().setForce(true));
        } catch (RuntimeException ignored) {}
    }

    /** Select a submission row, open Actions → Update Grades, save, then finalize MANUAL eval if needed. */
    public boolean applyGradeOnManageUserSubmissionsPage(double grade) {
        if (!page.url().contains("manageuserattempt.php")) return false;
        waitManageAttemptTableReady();
        if (!waitUntilManageAttemptGradable(120)) return false;
        if (!openManageActionsUpdateGrades()) return false;
        page.waitForTimeout(800);
        Locator gradeInput = page.locator(
                "#np-ap-manage-update-grade-input, input[name='grade'], #id_grade, "
                        + "[data-region='modal-container'] input[type='number'], "
                        + ".modal.show input[type='number'], input[type='number'][name*='grade']");
        if (gradeInput.count() == 0) return false;
        gradeInput.first().fill(String.valueOf(grade));
        page.waitForTimeout(300);
        Locator save = page.locator(
                "#np-ap-manage-update-grade-save, [data-region='modal-container'] button:has-text('Save'), "
                        + ".modal.show button:has-text('Save'), .modal.show button:has-text('Update'), "
                        + "input[value='Save']");
        if (save.count() > 0) {
            try { save.first().click(new Locator.ClickOptions().setForce(true)); } catch (RuntimeException ignored) {}
        } else {
            confirmModal();
        }
        page.waitForTimeout(2_000);
        dismissAlert();
        finalizeManualAttemptAfterGrade();
        page.waitForTimeout(1_500);
        return true;
    }

    private void waitManageAttemptTableReady() {
        waitTableLoaded(MANAGE_ATTEMPT_TABLE, 30);
        for (int i = 0; i < 40; i++) {
            if (page.locator(MANAGE_ATTEMPT_TABLE + " tbody tr[data-index]").count() > 0) return;
            if (page.locator(MANAGE_ATTEMPT_TABLE + " tbody tr td").count() > 3
                    && page.locator(MANAGE_ATTEMPT_TABLE + " .fixed-table-loading").count() == 0) {
                return;
            }
            page.waitForTimeout(500);
        }
    }

    /** After submit the attempt can sit in {@code Evaluating}; Actions stays disabled until it settles. */
    private boolean waitUntilManageAttemptGradable(int maxSeconds) {
        int polls = Math.max(1, maxSeconds / 3);
        for (int i = 0; i < polls; i++) {
            selectFirstManageAttemptRow();
            if (manageActionsEnabled()) return true;
            refreshManageAttemptTable();
            page.waitForTimeout(3_000);
        }
        selectFirstManageAttemptRow();
        return manageActionsEnabled();
    }

    private void selectFirstManageAttemptRow() {
        Locator row = page.locator(MANAGE_ATTEMPT_TABLE + " tbody tr[data-index]").first();
        if (row.count() == 0) {
            row = page.locator(MANAGE_ATTEMPT_TABLE + " tbody tr").first();
        }
        if (row.count() == 0) return;
        Locator cb = row.locator("input[name='btSelectItem']");
        try {
            if (cb.count() > 0) cb.first().check(new Locator.CheckOptions().setForce(true));
            else row.click();
        } catch (RuntimeException ignored) {}
        page.waitForTimeout(400);
    }

    private boolean manageActionsEnabled() {
        Locator actions = page.locator(MANAGE_ACTIONS_BTN);
        if (actions.count() == 0) return false;
        try {
            return actions.first().getAttribute("disabled") == null;
        } catch (RuntimeException e) {
            return false;
        }
    }

    private void refreshManageAttemptTable() {
        Locator refresh = page.locator("#attempt-toolbar button[name='btnAdd'], "
                + "#attempt-toolbar button[title*='Refresh']");
        try {
            if (refresh.count() > 0) refresh.first().click(new Locator.ClickOptions().setForce(true));
        } catch (RuntimeException ignored) {}
    }

    private boolean openManageActionsUpdateGrades() {
        Locator actions = page.locator(MANAGE_ACTIONS_BTN);
        if (actions.count() == 0 || actions.first().getAttribute("disabled") != null) return false;
        try { actions.first().click(new Locator.ClickOptions().setForce(true)); } catch (RuntimeException e) {
            return false;
        }
        page.waitForTimeout(400);
        Locator gradeItem = page.locator(MANAGE_UPDATE_GRADES);
        if (gradeItem.count() == 0) return false;
        try { gradeItem.first().click(new Locator.ClickOptions().setForce(true)); } catch (RuntimeException e) {
            return false;
        }
        return true;
    }

    /** MANUAL evaluation: after a grade is saved, publish/evaluate the attempt so Moodle completion can sync. */
    private void finalizeManualAttemptAfterGrade() {
        clickIfEnabled("#np-ap-manage-eval-result-btn");
        page.waitForTimeout(1_200);
        dismissAlert();
        clickIfEnabled("#np-ap-manage-sync-result-btn");
        page.waitForTimeout(1_200);
        dismissAlert();
        openManageActionsMenuItem("#np-ap-manage-submit-challenge-btn");
        page.waitForTimeout(1_200);
        dismissAlert();
        confirmModal();
    }

    private void openManageActionsMenuItem(String itemSelector) {
        if (!manageActionsEnabled()) selectFirstManageAttemptRow();
        if (!manageActionsEnabled()) return;
        try { page.locator(MANAGE_ACTIONS_BTN).first().click(new Locator.ClickOptions().setForce(true)); }
        catch (RuntimeException ignored) { return; }
        page.waitForTimeout(300);
        Locator item = page.locator(itemSelector);
        if (item.count() == 0) return;
        try { item.first().click(new Locator.ClickOptions().setForce(true)); } catch (RuntimeException ignored) {}
    }

    private void clickIfEnabled(String selector) {
        Locator btn = page.locator(selector);
        if (btn.count() == 0) return;
        try {
            if (btn.first().getAttribute("disabled") != null) return;
            if (!btn.first().isVisible()) return;
            btn.first().click(new Locator.ClickOptions().setForce(true));
        } catch (RuntimeException ignored) {}
    }

    public boolean openManageUserSubmissions(String sectionId) {
        if (!openFirstUserAttemptsPage(sectionId)) return false;   // now on userattempts.php
        Locator row = page.locator("#np-ap-user-attempts-table tbody tr:not(.no-records-found)").first();
        if (row.count() == 0) return false;
        Locator radio = row.locator("input[type=radio]");
        try { if (radio.count() > 0) radio.first().click(); } catch (RuntimeException ignored) {}
        page.waitForTimeout(600);
        Locator viewBtn = page.locator("#viewUserAttemptsSubmission");
        if (viewBtn.count() == 0 || viewBtn.first().getAttribute("disabled") != null) return false;
        try { viewBtn.first().click(); } catch (RuntimeException ignored) {}
        try { page.waitForURL("**/manageuserattempt.php**", new Page.WaitForURLOptions().setTimeout(15_000)); }
        catch (RuntimeException ignored) {}
        page.waitForTimeout(1_500);
        return page.url().contains("manageuserattempt.php");
    }

    public List<String> maxAttemptsOptions() {
        return page.locator(OV_MAXATTEMPTS).first().locator("option").allInnerTexts();
    }

    /** Set submissions to Unlimited (the "Unlimited" option) and duration to unlimited (turn the
     *  optional duration enabler OFF). Does NOT save — this verifies the control accepts unlimited. */
    public void setSubmissionsAndDurationUnlimited() {
        page.locator(OV_MAXATTEMPTS).first().selectOption(
                new com.microsoft.playwright.options.SelectOption().setLabel("Unlimited"));
        page.waitForTimeout(400);
        Locator en = page.locator(OV_TIMELIMIT_EN);
        if (en.count() > 0 && en.first().isChecked()) {
            en.first().uncheck(new Locator.UncheckOptions().setForce(true));
            page.waitForTimeout(300);
        }
    }

    public String selectedMaxAttempts() {
        Locator sel = page.locator(OV_MAXATTEMPTS).first();
        return (String) sel.evaluate("e => e.options[e.selectedIndex] ? e.options[e.selectedIndex].text.trim() : ''");
    }

    /** True when the duration is unlimited, i.e. the optional-duration enabler is unchecked. */
    public boolean durationIsUnlimited() {
        Locator en = page.locator(OV_TIMELIMIT_EN);
        return en.count() == 0 || !en.first().isChecked();
    }

    // ---- override-form setters (used once openOverrideFormForCompletedUser() == OPENED) ----
    // override_form.php: maxattempts (select, [0]=Unlimited then 1..N), timelimit (duration, optional),
    // submissionfromdate / submissionduedate (date_time_selector), remarks (text).

    /** Set "Submissions Allowed" to a specific option label (e.g. "1" to decrease below the global value). */
    public void setOverrideMaxAttempts(String label) {
        page.locator(OV_MAXATTEMPTS).first().selectOption(
                new com.microsoft.playwright.options.SelectOption().setLabel(label));
        page.waitForTimeout(300);
    }

    /** Turn the optional duration ON and set it to {@code hours} (timeunit = hours). */
    public void setOverrideDurationHours(int hours) {
        Locator en = page.locator(OV_TIMELIMIT_EN);
        if (en.count() > 0 && !en.first().isChecked()) {
            en.first().check(new Locator.CheckOptions().setForce(true));
            page.waitForTimeout(200);
        }
        Locator num = page.locator("#id_timelimit_number, input[name='timelimit[number]']");
        if (num.count() > 0) num.first().fill(String.valueOf(hours));
        Locator unit = page.locator("#id_timelimit_timeunit, select[name='timelimit[timeunit]']");
        if (unit.count() > 0) { try { unit.first().selectOption("3600"); } catch (RuntimeException ignored) {} }
        page.waitForTimeout(300);
    }

    /** The duration currently reads {@code hours} (and its enabler is on). */
    public boolean overrideDurationIs(int hours) {
        Locator num = page.locator("#id_timelimit_number, input[name='timelimit[number]']");
        if (num.count() == 0) return false;
        boolean enabled = !durationIsUnlimited();
        return enabled && num.first().inputValue().trim().equals(String.valueOf(hours));
    }

    public String saveDiag = "";

    /** Submit the override form (overrideedit.php) to CREATE/save the override; returns true once it has
     *  redirected off the edit form. On failure, saveDiag holds the validation error. */
    public boolean saveOverrideForm() {
        saveDiag = "";
        Locator save = page.locator("#id_submitbutton");
        if (save.count() == 0) { saveDiag = "no #id_submitbutton on the form"; return false; }
        save.first().click();
        try { page.waitForURL("**/overrides.php**", new Page.WaitForURLOptions().setTimeout(15_000)); }
        catch (RuntimeException ignored) {}
        page.waitForTimeout(2_000);
        if (!page.url().contains("overrideedit.php")) return true;
        Locator err = page.locator("[id^='id_error_']");
        for (int i = 0; i < err.count(); i++) {
            try {
                String t = err.nth(i).innerText().trim();
                if (!t.isEmpty()) { saveDiag = err.nth(i).getAttribute("id") + " -> " + t.replaceAll("\\s+", " "); break; }
            } catch (RuntimeException ignored) {}
        }
        if (saveDiag.isEmpty()) saveDiag = "stayed on overrideedit.php (no visible validation error)";
        return false;
    }

    /** The override form exposes a start-date AND due-date range (date_time_selector fields). */
    public boolean overrideHasDateRange() {
        return page.locator("#id_submissionfromdate_day, select[name='submissionfromdate[day]']").count() > 0
                && page.locator("#id_submissionduedate_day, select[name='submissionduedate[day]']").count() > 0;
    }
}
