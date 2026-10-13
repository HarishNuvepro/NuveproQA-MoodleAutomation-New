package com.nuvepro.moodle.pages;

import com.microsoft.playwright.Download;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.LoadState;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Manage Labs listing (managelabs.php). Used for independent verification of lab state
 * (Running/Stopped/Deleted/Failed counters). Mirrors pages/manage_labs.py.
 */
public class ManageLabs extends BasePage {
    private static final String[] KEYS = {"Running", "Stopped", "Deleted", "Failed"};

    // Toolbar controls (created-users tab)
    public static final String ACTIONS_BTN = "#np-ap-cl-assessment-lab-actions";
    public static final String START = "#np-ap-manage-start-btn";
    public static final String STOP = "#np-ap-manage-stop-btn";
    public static final String DELETE = "#np-ap-manage-delete-btn";
    public static final String CREATE = "#np-ap-manage-create-btn";
    public static final String LAB_DETAILS_BTN = "#np-ap-manage-view-btn";
    public static final String LAB_DETAIL_MODAL = "#lab-detail-modal";
    public static final String SYNC_BTN = "#np-ap-manage-sync-btn";
    public static final String ROW_CHECKBOX = "#np-ap-manage-lab-table input[name='btSelectItem']";
    // Tabs
    public static final String CREATED_TAB = "a[href='#labcreatedusers']";
    public static final String NOTCREATED_TAB = "a[href='#labnotcreatedusers']";
    public static final String NOTCREATED_CREATE = "#np-ap-manage-labsnotcreated-create-btn";
    public static final String NOTCREATED_TABLE = "#labnotcreatedusers";
    /** Scope pagination/search to the No labs tab pane / bootstrap-table wrapper. */
    public static final String NOTCREATED_ROOT =
            "#labnotcreatedusers, #labnotcreatedusers .bootstrap-table, "
                    + ".tab-pane#labnotcreatedusers, .tab-pane.active";
    public static final String NOTCREATED_SEARCH =
            "#labnotcreatedusers .fixed-table-toolbar .search input, "
                    + "#toolbarnotcreated .search input, "
                    + ".tab-pane#labnotcreatedusers .search input, "
                    + ".tab-pane.active .fixed-table-toolbar .search input, "
                    + "input.search-input";
    public static final String NOTCREATED_PAGINATION =
            "#labnotcreatedusers .fixed-table-pagination, "
                    + ".tab-pane#labnotcreatedusers .fixed-table-pagination, "
                    + ".tab-pane.active .fixed-table-pagination, "
                    + ".bootstrap-table .fixed-table-pagination";
    /** Active labs tab pane — scope toolbar clicks here (avoid report / My labs dropdown). */
    public static final String CREATED_PANE = "#labcreatedusers";
    public static final String CREATED_TABLE = "#np-ap-manage-lab-table";
    public static final String CREATED_SEARCH =
            "#np-ap-manage-lab-table .fixed-table-toolbar .search input, "
                    + "#labcreatedusers .fixed-table-toolbar .search input, "
                    + ".tab-pane#labcreatedusers .search input, "
                    + ".tab-pane.active .fixed-table-toolbar .search input";
    public static final String GROUP_FILTER = "#course_groups";
    public static final String REPORT_DROPDOWN = "#np-ap-cl-manage-active-labs-actions";
    public static final String ACTIVE_LABS_REPORT_DOWNLOAD = "#np-ap-manage-active-labs-report-btn";
    public static final String ACTIVE_LABS_REPORT_MODAL = "#downloadManageActiveLabsReport";
    /** Confirm button inside the Active labs download modal (not the toolbar Download item). */
    public static final String ACTIVE_LABS_REPORT_MODAL_DOWNLOAD_BTN =
            "#downloadReportBtn, button#downloadReportBtn, .modal-footer .btn-primary";
    public static final String EXPORT_BTNS =
            "#np-ap-manage-export-report-btn, "
                    + "#np-ap-cl-manage-active-labs-actions, "
                    + "a[href*='export'], button:has-text('Export'), button:has-text('Download'), "
                    + ".export .dropdown-toggle, .keep-open.btn-group";
    public static final String COLUMNS_TOGGLE =
            ".keep-open.btn-group.btn-group-sm button, "
                    + "button[title*='Columns'], button[name='toggle'], "
                    + ".columns .dropdown-toggle, .fixed-table-toolbar .columns";


    /** True if the given action item (`<a disabled>`) currently carries the disabled attribute. */
    public boolean actionDisabled(String selector) {
        Locator l = page.locator(selector);
        return l.count() > 0 && l.first().getAttribute("disabled") != null;
    }

    public boolean hasLabRows() {
        return page.locator(ROW_CHECKBOX).count() > 0;
    }

    public void selectFirstRow() {
        page.locator(ROW_CHECKBOX).first().check();
    }

    public int rowCount() {
        return page.locator(ROW_CHECKBOX).count();
    }

    public void selectAllRows() {
        int n = rowCount();
        for (int i = 0; i < n; i++) {
            page.locator(ROW_CHECKBOX).nth(i).check();
        }
    }

    /** Active labs tab: show all rows on one page, then check the header “select all” (bootstrap-table). */
    public void selectAllCreatedLabRows() {
        openCreatedTab();
        clearCreatedSearch();
        setCreatedPageSize("25");
        refreshCreatedTable();
        page.waitForTimeout(800);
        boolean checkAll = false;
        try {
            Object ok = page.evaluate("""
                    () => {
                      const jq = window.jQuery || window.$;
                      if (!jq) return false;
                      const $t = jq('#np-ap-manage-lab-table');
                      if ($t.length && $t.bootstrapTable) {
                        $t.bootstrapTable('checkAll');
                        return true;
                      }
                      return false;
                    }
                    """);
            checkAll = Boolean.TRUE.equals(ok) || "true".equals(String.valueOf(ok));
        } catch (RuntimeException ignored) {}
        if (!checkAll) {
            Locator header = page.locator(
                    "#np-ap-manage-lab-table thead input[name='btSelectAll'], "
                            + "#np-ap-manage-lab-table thead .bs-checkbox input");
            if (header.count() > 0) {
                try {
                    header.first().check();
                } catch (RuntimeException ignored) {}
            }
        }
        int total = rowCount();
        int checked = page.locator(ROW_CHECKBOX + ":checked").count();
        if (total > 0 && checked < total) {
            selectAllRows();
            checked = page.locator(ROW_CHECKBOX + ":checked").count();
        }
        System.out.println("[ManageLabs] Active labs select-all checked=" + checked + " of " + total);
    }

    /** Select all visible Active lab rows and click Sync Status (no Actions dropdown). */
    public void syncAllSelectedCreatedLabs() {
        page.waitForTimeout(400);
        clickSyncStatusButton();
        page.waitForTimeout(1_000);
    }

    /** Sync Status toolbar button only — not Actions / report (My labs) dropdown. */
    public void clickSyncStatusButton() {
        dismissOpenDropdowns();
        Locator sync = visibleCreatedToolbarControl(SYNC_BTN);
        sync.click(new Locator.ClickOptions().setForce(true));
    }

    /** Active labs: select these users, then Sync Status once. */
    public void syncCreatedRowsForEmails(java.util.List<String> emails, int sectionId) {
        open(sectionId);
        openCreatedTab();
        selectRowsForEmails(emails);
        syncAllSelectedCreatedLabs();
    }

    /** Select all visible Active lab rows and Delete via the Actions dropdown. */
    public void deleteAllSelectedCreatedLabs() {
        performActionOnSelection(DELETE);
    }

    /** Active labs: Start on the current checkbox selection (Actions dropdown, not My labs report). */
    public void startSelectedCreatedLabs(int minimumChecked) {
        performCreatedTabLabAction(START, minimumChecked);
    }

    /** Active labs: Stop on the current checkbox selection. */
    public void stopSelectedCreatedLabs(int minimumChecked) {
        performCreatedTabLabAction(STOP, minimumChecked);
    }

    public int countCheckedCreatedRows() {
        return page.locator(CREATED_TABLE + " tbody input[name='btSelectItem']:checked").count();
    }

    /** Clear Active labs row selection (bootstrap-table + DOM checkboxes). */
    public void uncheckAllCreatedLabRows() {
        openCreatedTab();
        try {
            page.evaluate("""
                    () => {
                      const jq = window.jQuery || window.$;
                      if (!jq) return;
                      const $t = jq('#np-ap-manage-lab-table');
                      if ($t.length && $t.bootstrapTable) $t.bootstrapTable('uncheckAll');
                    }
                    """);
        } catch (RuntimeException ignored) {}
        Locator boxes = page.locator(CREATED_TABLE + " tbody input[name='btSelectItem']:checked");
        for (int i = boxes.count() - 1; i >= 0; i--) {
            try {
                boxes.nth(i).uncheck();
            } catch (RuntimeException ignored) {}
        }
        page.waitForTimeout(400);
    }

    /** Poll until the Actions dropdown is enabled (selection registered by manage-labs.js). */
    public void waitForCreatedLabActionToolbarEnabled(int minimumSelected, long timeoutMs) {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            int checked = countCheckedCreatedRows();
            Locator actions = page.locator(CREATED_PANE + " " + ACTIONS_BTN);
            boolean actionsEnabled = actions.count() > 0
                    && actions.first().getAttribute("disabled") == null;
            if (checked >= minimumSelected && actionsEnabled) {
                System.out.println("[ManageLabs] toolbar ready checked=" + checked
                        + " Actions enabled");
                return;
            }
            page.waitForTimeout(400);
        }
        throw new IllegalStateException("Active labs toolbar not ready (need " + minimumSelected
                + " selected + Actions enabled) checked=" + countCheckedCreatedRows());
    }

    /** First row's text (Lab ID | email | Action | Status | ...) for state assertions. */
    public String firstRowText() {
        Locator row = page.locator("#np-ap-manage-lab-table tbody tr").first();
        return row.count() > 0 ? row.innerText().replaceAll("\\s+", " ").trim() : "";
    }

    /**
     * Select the first lab row, open the Actions dropdown (for Start/Stop/Delete), click the
     * action and confirm the Moodle dialog.
     *
     * NOTE: Start/Stop/Delete are <a class="lab-action-btn"> anchors inside the Actions dropdown.
     * The 'disabled' ATTRIBUTE has no effect on an anchor (only <button>/<input> honour it) and the
     * click handler fires regardless — so we do NOT gate on it (doing so wrongly skipped Delete,
     * which carries a cosmetic 'disabled' attr from a missing comma in setToolbarButtonState but is
     * fully functional). Create is a real <button>, so its disabled state IS respected by the caller.
     */
    public void performActionOnFirstRow(String itemSelector) {
        selectFirstRow();
        page.waitForTimeout(1_000); // let the selection event update the toolbar
        boolean inDropdown = START.equals(itemSelector) || STOP.equals(itemSelector) || DELETE.equals(itemSelector);
        if (inDropdown) {
            performCreatedTabLabAction(itemSelector, 1);
            return;
        }
        page.locator(itemSelector).click(new Locator.ClickOptions().setForce(true));
        try {
            confirmModal();
        } catch (Throwable noDialog) {
            // no confirmation for this action — proceed
        }
    }

    /** Public wrapper so step classes can confirm the Moodle dialog raised by a bulk action. */
    public void confirmVisibleDialog() {
        confirmModal();
    }

    // ===================== lab state machine (provider-aware, cp.php-free) =====================

    public enum LabState { NOTCREATED, RUNNING, STOPPED, DELETED, INPROGRESS, FAILED, UNKNOWN }

    private static final java.util.Set<LabState> SETTLED =
            java.util.EnumSet.of(LabState.NOTCREATED, LabState.RUNNING, LabState.STOPPED,
                    LabState.DELETED, LabState.FAILED);

    /** Map an action + status pair (e.g. from the CloudLabs API's nl_action/nl_status) to a state. */
    public static LabState classifyActionStatus(String action, String status) {
        if (action == null) return LabState.NOTCREATED;
        String a = action.toLowerCase();
        String s = status == null ? "" : status.toLowerCase();
        if (s.contains("inprogress") || s.contains("in progress") || s.contains("pending")
                || s.contains("processing") || s.contains("queued")) return LabState.INPROGRESS;
        if (s.contains("failed") || s.contains("error")) return LabState.FAILED;
        if (a.contains("delete") && s.contains("complete")) return LabState.DELETED;
        if (a.contains("stop") && s.contains("complete")) return LabState.STOPPED;
        if ((a.contains("create") || a.contains("start")) && (s.contains("complete") || s.contains("success")))
            return LabState.RUNNING;
        return LabState.UNKNOWN;
    }

    /** Map a Manage Labs row's text (action + status) to a logical lab state. */
    private LabState classify(String rowTextLower) {
        String r = rowTextLower;
        if (r.contains("inprogress") || r.contains("in progress") || r.contains("pending")
                || r.contains("processing") || r.contains("queued")) return LabState.INPROGRESS;
        if (r.contains("failed") || r.contains("error")) return LabState.FAILED;
        if (r.contains("delete") && r.contains("complete")) return LabState.DELETED;
        if (r.contains("stop") && r.contains("complete")) return LabState.STOPPED;
        if ((r.contains("start") || r.contains("create") || r.contains("running"))
                && (r.contains("complete") || r.contains("running") || r.contains("success")))
            return LabState.RUNNING;
        System.out.println("[ManageLabs] classify UNKNOWN for row: " + r.replaceAll("\\s+", " ").trim());
        return LabState.UNKNOWN;
    }

    /** Authoritative current state of the (first) lab, read from its Manage Labs row (action+status). */
    public LabState firstRowState() {
        if (!hasLabRows()) return LabState.NOTCREATED;
        return classify(firstRowText().toLowerCase());
    }

    // ---- email-targeted operations (for fresh per-user labs) ----

    private Locator createdRowForEmail(String email) {
        return page.locator("#np-ap-manage-lab-table tbody tr")
                .filter(new Locator.FilterOptions().setHasText(email));
    }

    /** State of the lab belonging to {@code email} (created-users tab). */
    public LabState stateForEmail(String email) {
        Locator row = createdRowForEmail(email);
        if (row.count() == 0) return LabState.NOTCREATED;
        return classify(row.first().innerText().toLowerCase());
    }

    /** The lab id shown in Manage Labs = the CloudLabs provider subscriptionId. */
    private String extractLabId(Locator row) {
        for (String cell : row.locator("td").allInnerTexts()) {
            String t = cell.trim();
            if (t.contains("@") || t.toLowerCase(java.util.Locale.ROOT).startsWith("autotest")) continue;
            if (t.matches("\\d{5,}")) return t;
            Matcher m = Pattern.compile("\\b(\\d{6,})\\b").matcher(t);
            if (m.find()) return m.group(1);
        }
        return null;
    }

    public void refreshCreatedTable() {
        try {
            page.evaluate("""
                    () => {
                      const jq = window.jQuery || window.$;
                      if (!jq) return;
                      const $t = jq('#np-ap-manage-lab-table');
                      if ($t.length && $t.bootstrapTable) $t.bootstrapTable('refresh');
                    }
                    """);
        } catch (RuntimeException ignored) {}
        page.waitForTimeout(2_000);
        waitForCreatedRowsOrEmpty();
    }

    /**
     * Read {@code labid} from bootstrap-table row data (the UI cell is often blank until refresh).
     */
    public String labIdFromTableData(String email) {
        if (email == null || email.isBlank()) return null;
        try {
            Object raw = page.evaluate("""
                    (email) => {
                      const jq = window.jQuery || window.$;
                      if (!jq) return '';
                      const $t = jq('#np-ap-manage-lab-table');
                      if (!$t.length || !$t.bootstrapTable) return '';
                      const rows = $t.bootstrapTable('getData') || [];
                      const el = String(email).toLowerCase();
                      const user = el.includes('@') ? el.split('@')[0] : el;
                      for (const r of rows) {
                        const hay = JSON.stringify(r || {}).toLowerCase();
                        if (!hay.includes(el) && !(user && hay.includes(user))) continue;
                        const id = r.labid || r.labId || r.subscriptionId || '';
                        if (id && String(id) !== '0') return String(id);
                      }
                      return '';
                    }
                    """, email);
            if (raw != null) {
                String v = String.valueOf(raw).trim();
                if (!v.isEmpty() && !"null".equals(v)) return v;
            }
        } catch (RuntimeException ignored) {}
        return null;
    }

    public String labIdForEmail(String email) {
        String fromData = labIdFromTableData(email);
        if (fromData != null && !fromData.isBlank()) return fromData;
        Locator row = createdRowForEmail(email);
        if (row.count() == 0 && email != null && email.contains("@")) {
            String user = email.substring(0, email.indexOf('@'));
            row = page.locator("#np-ap-manage-lab-table tbody tr")
                    .filter(new Locator.FilterOptions().setHasText(user));
        }
        return row.count() > 0 ? extractLabId(row.first()) : null;
    }

    /** All lab ids in rows for this user (a user can have an old deleted lab + a fresh one). */
    public java.util.List<String> labIdsForEmail(String email) {
        java.util.List<String> ids = new java.util.ArrayList<>();
        Locator rows = createdRowForEmail(email);
        for (int i = 0; i < rows.count(); i++) {
            String id = extractLabId(rows.nth(i));
            if (id != null) ids.add(id);
        }
        return ids;
    }

    public String firstRowLabId() {
        Locator row = page.locator("#np-ap-manage-lab-table tbody tr").first();
        return row.count() > 0 ? extractLabId(row) : null;
    }

    /** Poll until this user's lab reaches a target state (reloading Manage Labs each time). */
    public LabState waitForEmailState(String email, java.util.Set<LabState> targets, int maxSeconds, int sectionId) {
        long deadline = System.currentTimeMillis() + maxSeconds * 1000L;
        LabState st = stateForEmail(email);
        while (System.currentTimeMillis() < deadline) {
            if (targets.contains(st)) return st;
            page.waitForTimeout(15_000);
            open(sectionId);
            st = stateForEmail(email);
        }
        return st;
    }

    /** Create a lab for an enrolled user via the not-created-users tab (admin action, no cp.php). */
    public void createLabForUserEmail(String email, int sectionId) {
        open(sectionId);
        openNotCreatedTab();
        clearNotCreatedRowSelection();
        selectNotCreatedUserEmail(email, sectionId);
        clickNotCreatedCreateButton();
    }

    /**
     * One user at a time: select on No labs → Create → verify on Active labs (Create in progress or Running).
     * Returns lab id when the Active labs row exposes it (may still be blank while Create is in flight).
     */
    public String createLabForUserEmailSequentially(String email, int sectionId) {
        open(sectionId);
        openNotCreatedTab();
        clearNotCreatedRowSelection();
        selectNotCreatedUserEmail(email, sectionId);
        clickNotCreatedCreateButton();
        return waitForActiveLabRowAfterCreate(email, sectionId);
    }

    private void clickNotCreatedCreateButton() {
        page.waitForTimeout(800);
        Locator createBtn = page.locator(NOTCREATED_CREATE);
        if (createBtn.count() == 0) {
            throw new IllegalStateException("No labs Create button not found (" + NOTCREATED_CREATE + ")");
        }
        String disabled = createBtn.first().getAttribute("disabled");
        String aria = createBtn.first().getAttribute("aria-disabled");
        if (disabled != null || "true".equalsIgnoreCase(aria)) {
            throw new IllegalStateException("No labs Create button disabled — no row selected?");
        }
        createBtn.first().click(new Locator.ClickOptions().setForce(true));
        try {
            confirmModal();
        } catch (Throwable ignored) {}
        page.waitForTimeout(1_200);
        System.out.println("[ManageLabs] No labs Create clicked for selection on " + page.url());
    }

    /** Uncheck all rows on the No labs tab so each sequential Create targets one user. */
    public void clearNotCreatedRowSelection() {
        try {
            page.evaluate("""
                    () => {
                      const jq = window.jQuery || window.$;
                      if (!jq) return;
                      const $t = jq('#labnotcreatedusers table');
                      if ($t.length && $t.bootstrapTable) $t.bootstrapTable('uncheckAll');
                    }
                    """);
        } catch (RuntimeException ignored) {}
        Locator checked = page.locator(NOTCREATED_TABLE + " input[name='btSelectItem']:checked");
        for (int i = checked.count() - 1; i >= 0; i--) {
            try {
                checked.nth(i).uncheck();
            } catch (RuntimeException ignored) {}
        }
    }

    /**
     * After No labs Create, open Active labs and confirm the user appears with Create in progress
     * (or already Running when the provider is fast).
     */
    public String waitForActiveLabRowAfterCreate(String email, int sectionId) {
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("email required");
        }
        String lastRow = "";
        for (int attempt = 1; attempt <= 16; attempt++) {
            open(sectionId);
            openCreatedTab();
            clearCreatedSearch();
            try {
                searchCreatedByEmail(email);
            } catch (RuntimeException ignored) {}
            if (!createdContainsEmail(email)) {
                if (attempt % 4 == 0) refreshCreatedTable();
                page.waitForTimeout(1_500);
                continue;
            }
            lastRow = createdRowTextForEmail(email);
            LabState st = stateForEmail(email);
            String lower = lastRow.toLowerCase(java.util.Locale.ROOT);
            boolean createOk = st == LabState.INPROGRESS || st == LabState.RUNNING
                    || (lower.contains("create") && (lower.contains("progress")
                    || lower.contains("inprogress") || lower.contains("pending")
                    || lower.contains("complete")));
            if (createOk) {
                String id = labIdForEmail(email);
                System.out.println("[ManageLabs] Active labs OK after Create email=" + email
                        + " state=" + st + " labId=" + id + " row=" + lastRow);
                return id;
            }
            page.waitForTimeout(1_500);
        }
        throw new IllegalStateException("Active labs did not show Create in progress for " + email
                + " (last row='" + lastRow + "')");
    }

    /**
     * Find a No labs row by email (search first — lists are paginated), then check it.
     * Retries with reload because enrolments can lag a few seconds.
     */
    public void selectNotCreatedUserEmail(String email, int sectionId) {
        selectNotCreatedUserEmail(email, sectionId, 8, 2_500);
    }

    public void selectNotCreatedUserEmail(String email, int sectionId, int maxAttempts, int retryMs) {
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("email required");
        }
        String username = email.contains("@") ? email.substring(0, email.indexOf('@')) : email;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            open(sectionId);
            openNotCreatedTab();
            // 1) Search by full email (manage-6 proven path)
            try {
                clearNotCreatedSearch();
                searchNotCreatedByEmail(email);
                if (notCreatedContainsEmail(email)) {
                    notCreatedRowForEmail(email).first().locator("input[name='btSelectItem']").check();
                    System.out.println("[ManageLabs] No labs selected via email search: " + email);
                    return;
                }
            } catch (RuntimeException e) {
                System.out.println("[ManageLabs] No labs email search attempt " + attempt
                        + ": " + e.getMessage());
            }
            // 2) Search by username prefix
            try {
                clearNotCreatedSearch();
                searchNotCreatedByEmail(username);
                if (notCreatedContainsEmail(email) || notCreatedContainsEmail(username)) {
                    Locator row = notCreatedRowForEmail(email);
                    if (row.count() == 0) row = notCreatedRowForEmail(username);
                    row.first().locator("input[name='btSelectItem']").check();
                    System.out.println("[ManageLabs] No labs selected via username search: " + username);
                    return;
                }
            } catch (RuntimeException ignored) {}
            // 3) Clear search and scan pages
            try {
                clearNotCreatedSearch();
                goToNotCreatedFirstPage();
                for (int p = 0; p < 40; p++) {
                    if (notCreatedContainsEmail(email)) {
                        notCreatedRowForEmail(email).first().locator("input[name='btSelectItem']").check();
                        System.out.println("[ManageLabs] No labs selected via pagination: " + email);
                        return;
                    }
                    if (!clickNotCreatedNextPage()) break;
                }
            } catch (RuntimeException ignored) {}
            System.out.println("[ManageLabs] No labs user not visible yet (attempt " + attempt
                    + "): " + email);
            page.waitForTimeout(retryMs);
        }
        throw new IllegalStateException("user not found in not-created list: " + email);
    }

    /**
     * Recreate after admin Delete: the user stays on <strong>Active labs</strong> with Delete/Complete
     * (not on No labs). Select that row and click the toolbar Create button.
     */
    public void recreateLabForUserOnActiveLabs(String email, int sectionId) {
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("email required");
        }
        open(sectionId);
        openCreatedTab();
        setCreatedPageSize("25");
        clearCreatedSearch();
        try {
            searchCreatedByEmail(email);
        } catch (RuntimeException ignored) {}
        if (!createdContainsEmail(email)) {
            goToCreatedFirstPageIfPresent();
            for (int p = 0; p < 40 && !createdContainsEmail(email); p++) {
                if (!clickCreatedNextPage()) break;
            }
        }
        if (!createdContainsEmail(email)) {
            throw new IllegalStateException("no Active labs row for recreate: " + email);
        }
        Locator row = createdRowForEmail(email);
        Locator cb = row.first().locator("input[name='btSelectItem']");
        if (!cb.isChecked()) {
            cb.check();
        }
        page.waitForTimeout(1_000);
        Locator createBtn = page.locator(CREATE);
        if (createBtn.count() == 0) {
            throw new IllegalStateException("Active labs Create button missing (" + CREATE + ")");
        }
        if (actionDisabled(CREATE)) {
            throw new IllegalStateException("Active labs Create disabled — select the deleted row first");
        }
        createBtn.first().click(new Locator.ClickOptions().setForce(true));
        try {
            confirmModal();
        } catch (Throwable ignored) {}
        page.waitForTimeout(1_200);
        System.out.println("[ManageLabs] Active labs Create (recreate after Delete) email=" + email);
    }

    /** Post-delete recreate: Active labs first; No labs only if the user never had a row. */
    public void createLabForUserEmailQuick(String email, int sectionId) {
        try {
            recreateLabForUserOnActiveLabs(email, sectionId);
        } catch (RuntimeException activeFail) {
            System.out.println("[ManageLabs] Active labs recreate failed, trying No labs: "
                    + activeFail.getMessage());
            open(sectionId);
            openNotCreatedTab();
            clearNotCreatedRowSelection();
            selectNotCreatedUserEmail(email, sectionId, 4, 1_500);
            clickNotCreatedCreateButton();
        }
    }

    public void openCreatedTab() {
        if (page.locator(CREATED_TAB).count() > 0) {
            page.locator(CREATED_TAB).click();
            page.waitForTimeout(1_500);
            waitForCreatedRowsOrEmpty();
        }
    }

    public void openNotCreatedTab() {
        page.locator(NOTCREATED_TAB).click();
        page.waitForTimeout(1_500);
        waitForNotCreatedRowsOrEmpty();
    }

    private void waitForNotCreatedRowsOrEmpty() {
        for (int i = 0; i < 20; i++) {
            boolean hasRow = page.locator(NOTCREATED_TABLE + " tbody input[name='btSelectItem'], "
                    + NOTCREATED_TABLE + " tbody tr td").count() > 0;
            boolean noRecords = page.locator(NOTCREATED_TABLE + " tbody .no-records-found, "
                    + NOTCREATED_TABLE + " tbody tr.no-records-found").count() > 0;
            if (hasRow || noRecords) return;
            page.waitForTimeout(500);
        }
    }

    private void waitForCreatedRowsOrEmpty() {
        for (int i = 0; i < 20; i++) {
            boolean hasRow = page.locator(ROW_CHECKBOX).count() > 0;
            boolean noRecords = page.locator(CREATED_TABLE + " tbody .no-records-found, "
                    + CREATED_TABLE + " tbody tr.no-records-found").count() > 0;
            if (hasRow || noRecords) return;
            page.waitForTimeout(500);
        }
    }

    public Locator notCreatedRowForEmail(String email) {
        return page.locator(NOTCREATED_TABLE + " tbody tr")
                .filter(new Locator.FilterOptions().setHasText(email));
    }

    public boolean notCreatedContainsEmail(String email) {
        return notCreatedRowForEmail(email).count() > 0;
    }

    public boolean createdContainsEmail(String email) {
        return createdRowForEmail(email).count() > 0;
    }

    /** Emails visible on the current No labs page (skip no-records rows). */
    public java.util.List<String> notCreatedEmailsOnCurrentPage() {
        java.util.LinkedHashSet<String> out = new java.util.LinkedHashSet<>();
        Locator rows = page.locator(NOTCREATED_TABLE + " tbody tr");
        for (int i = 0; i < rows.count(); i++) {
            Locator row = rows.nth(i);
            try {
                if (row.locator(".no-records-found").count() > 0) continue;
                String text = row.innerText();
                Matcher m = Pattern.compile("[\\w.+-]+@[\\w.-]+\\.[A-Za-z]{2,}").matcher(text);
                while (m.find()) out.add(m.group().toLowerCase());
            } catch (RuntimeException ignored) {}
        }
        return new java.util.ArrayList<>(out);
    }

    /** Collect emails across all No labs pages (pagination). */
    public java.util.Set<String> allNotCreatedEmails() {
        java.util.LinkedHashSet<String> all = new java.util.LinkedHashSet<>();
        clearNotCreatedSearch();
        goToNotCreatedFirstPage();
        for (int pageNum = 0; pageNum < 50; pageNum++) {
            all.addAll(notCreatedEmailsOnCurrentPage());
            if (!clickNotCreatedNextPage()) break;
        }
        goToNotCreatedFirstPage();
        return all;
    }

    private Locator notCreatedSearchInput() {
        Locator search = page.locator(NOTCREATED_SEARCH);
        for (int i = 0; i < search.count(); i++) {
            try {
                if (search.nth(i).isVisible()) return search.nth(i);
            } catch (RuntimeException ignored) {}
        }
        return search.first();
    }

    public void searchNotCreatedByEmail(String email) {
        Locator search = notCreatedSearchInput();
        if (search.count() == 0) {
            throw new IllegalStateException("No labs search input not found");
        }
        search.click();
        search.fill("");
        search.fill(email);
        page.waitForTimeout(800);
        try { search.press("Enter"); } catch (RuntimeException ignored) {}
        page.waitForTimeout(1_500);
        waitForNotCreatedRowsOrEmpty();
    }

    public void clearNotCreatedSearch() {
        try {
            Locator search = notCreatedSearchInput();
            if (search.count() == 0) return;
            search.fill("");
            try { search.press("Enter"); } catch (RuntimeException ignored) {}
            page.waitForTimeout(1_000);
            waitForNotCreatedRowsOrEmpty();
        } catch (RuntimeException ignored) {}
    }

    public boolean hasNotCreatedPagination() {
        return notCreatedNextEnabled() || notCreatedHasPageNumber(2);
    }

    private boolean notCreatedHasPageNumber(int n) {
        Locator pageN = page.locator(
                ".fixed-table-pagination .pagination li a, "
                        + ".fixed-table-pagination .pagination li button, "
                        + ".fixed-table-pagination .page-item a")
                .filter(new Locator.FilterOptions().setHasText(Pattern.compile("^" + n + "$")));
        for (int i = 0; i < pageN.count(); i++) {
            try {
                if (pageN.nth(i).isVisible()) return true;
            } catch (RuntimeException ignored) {}
        }
        return false;
    }

    private boolean notCreatedNextEnabled() {
        Locator nextItems = page.locator(
                ".fixed-table-pagination .page-next, "
                        + ".fixed-table-pagination li.page-next, "
                        + ".fixed-table-pagination a[aria-label='next'], "
                        + ".fixed-table-pagination .page-item.page-next");
        for (int i = 0; i < nextItems.count(); i++) {
            try {
                Locator n = nextItems.nth(i);
                if (!n.isVisible()) continue;
                String cls = n.getAttribute("class");
                if (cls != null && cls.contains("disabled")) continue;
                String aria = n.getAttribute("aria-disabled");
                if ("true".equalsIgnoreCase(aria)) continue;
                return true;
            } catch (RuntimeException ignored) {}
        }
        return false;
    }

    public void goToNotCreatedFirstPage() {
        Locator page1 = page.locator(
                ".fixed-table-pagination .pagination li a, "
                        + ".fixed-table-pagination .pagination li button, "
                        + ".fixed-table-pagination .page-item a")
                .filter(new Locator.FilterOptions().setHasText(Pattern.compile("^1$")));
        for (int i = 0; i < page1.count(); i++) {
            try {
                if (page1.nth(i).isVisible()) {
                    page1.nth(i).click();
                    page.waitForTimeout(1_200);
                    waitForNotCreatedRowsOrEmpty();
                    return;
                }
            } catch (RuntimeException ignored) {}
        }
    }

    /** Click next page on No labs; returns false if next is disabled / missing. */
    public boolean clickNotCreatedNextPage() {
        java.util.List<String> before = notCreatedEmailsOnCurrentPage();
        int cur = currentNotCreatedPageNumber();

        // Prefer numeric page (cur+1) — most reliable across bootstrap-table skins.
        if (cur > 0 && notCreatedHasPageNumber(cur + 1)) {
            Locator pageN = page.locator(
                    ".fixed-table-pagination .pagination li a, "
                            + ".fixed-table-pagination .pagination li button, "
                            + ".fixed-table-pagination .page-item a")
                    .filter(new Locator.FilterOptions().setHasText(Pattern.compile("^" + (cur + 1) + "$")));
            for (int i = 0; i < pageN.count(); i++) {
                try {
                    if (!pageN.nth(i).isVisible()) continue;
                    pageN.nth(i).click();
                    page.waitForTimeout(1_500);
                    waitForNotCreatedRowsOrEmpty();
                    return !before.equals(notCreatedEmailsOnCurrentPage())
                            || currentNotCreatedPageNumber() == cur + 1;
                } catch (RuntimeException ignored) {}
            }
        }

        Locator nextLinks = page.locator(
                ".fixed-table-pagination .page-next:not(.disabled) a, "
                        + ".fixed-table-pagination li.page-next:not(.disabled) a, "
                        + ".fixed-table-pagination a[aria-label='next']:not([aria-disabled='true']), "
                        + ".fixed-table-pagination .page-item.page-next:not(.disabled) .page-link, "
                        + ".fixed-table-pagination .page-next:not(.disabled)");
        for (int i = 0; i < nextLinks.count(); i++) {
            try {
                Locator n = nextLinks.nth(i);
                if (!n.isVisible()) continue;
                String cls = n.getAttribute("class");
                if (cls != null && cls.contains("disabled")) continue;
                n.click();
                page.waitForTimeout(1_500);
                waitForNotCreatedRowsOrEmpty();
                java.util.List<String> after = notCreatedEmailsOnCurrentPage();
                if (!before.equals(after) || currentNotCreatedPageNumber() > cur) return true;
            } catch (RuntimeException ignored) {}
        }
        return false;
    }

    public boolean clickNotCreatedPrevPage() {
        java.util.List<String> before = notCreatedEmailsOnCurrentPage();
        int cur = currentNotCreatedPageNumber();
        if (cur > 1 && notCreatedHasPageNumber(cur - 1)) {
            Locator pageN = page.locator(
                    ".fixed-table-pagination .pagination li a, "
                            + ".fixed-table-pagination .page-item a")
                    .filter(new Locator.FilterOptions().setHasText(Pattern.compile("^" + (cur - 1) + "$")));
            for (int i = 0; i < pageN.count(); i++) {
                try {
                    if (!pageN.nth(i).isVisible()) continue;
                    pageN.nth(i).click();
                    page.waitForTimeout(1_500);
                    waitForNotCreatedRowsOrEmpty();
                    return true;
                } catch (RuntimeException ignored) {}
            }
        }
        Locator prev = page.locator(
                ".fixed-table-pagination .page-pre:not(.disabled) a, "
                        + ".fixed-table-pagination li.page-pre:not(.disabled) a, "
                        + ".fixed-table-pagination a[aria-label='previous']:not([aria-disabled='true']), "
                        + ".fixed-table-pagination .page-item.page-pre:not(.disabled) .page-link");
        for (int i = 0; i < prev.count(); i++) {
            try {
                if (!prev.nth(i).isVisible()) continue;
                prev.nth(i).click();
                page.waitForTimeout(1_500);
                waitForNotCreatedRowsOrEmpty();
                return !before.equals(notCreatedEmailsOnCurrentPage());
            } catch (RuntimeException ignored) {}
        }
        return false;
    }

    private int currentNotCreatedPageNumber() {
        Locator active = page.locator(
                ".fixed-table-pagination .pagination li.active a, "
                        + ".fixed-table-pagination .pagination li.active span, "
                        + ".fixed-table-pagination .page-item.active .page-link, "
                        + ".fixed-table-pagination .page-item.active");
        for (int i = 0; i < active.count(); i++) {
            try {
                if (!active.nth(i).isVisible()) continue;
                String t = active.nth(i).innerText().trim();
                if (t.matches("\\d+")) return Integer.parseInt(t);
            } catch (RuntimeException ignored) {}
        }
        return 1;
    }

    /** Set No labs page size when the bootstrap-table control exists. */
    public boolean setNotCreatedPageSize(String size) {
        Locator pageSize = page.locator(
                "#labnotcreatedusers .page-list select, "
                        + ".tab-pane#labnotcreatedusers .page-list select, "
                        + ".tab-pane.active .page-list select, "
                        + ".fixed-table-pagination .page-list select");
        for (int i = 0; i < pageSize.count(); i++) {
            try {
                if (!pageSize.nth(i).isVisible()) continue;
                pageSize.nth(i).selectOption(size);
                page.waitForTimeout(1_500);
                waitForNotCreatedRowsOrEmpty();
                return true;
            } catch (RuntimeException ignored) {}
        }
        return false;
    }

    public boolean setCreatedPageSize(String size) {
        openCreatedTab();
        Locator pageSize = page.locator(
                "#labcreatedusers .page-list select, "
                        + "#np-ap-manage-lab-table .page-list select, "
                        + ".tab-pane#labcreatedusers .page-list select, "
                        + ".tab-pane.active .page-list select, "
                        + ".fixed-table-pagination .page-list select, "
                        + ".bootstrap-table .page-list select, "
                        + "select.change-pagesize, .pagination-detail select");
        for (int i = 0; i < pageSize.count(); i++) {
            try {
                Locator sel = pageSize.nth(i);
                if (!sel.isVisible()) continue;
                sel.selectOption(size);
                page.waitForTimeout(1_500);
                waitForCreatedRowsOrEmpty();
                return true;
            } catch (RuntimeException ignored) {}
        }
        Locator sizeBtn = page.locator(
                ".fixed-table-pagination .page-list .btn, .pagination-detail .dropdown-toggle, "
                        + ".page-list button");
        for (int i = 0; i < sizeBtn.count(); i++) {
            try {
                if (!sizeBtn.nth(i).isVisible()) continue;
                sizeBtn.nth(i).click();
                page.waitForTimeout(400);
                Locator opt = page.locator(
                        ".dropdown-menu.show a, .dropdown-menu.show li, .page-list a")
                        .filter(new Locator.FilterOptions().setHasText(size));
                if (opt.count() > 0) {
                    opt.first().click();
                    page.waitForTimeout(1_500);
                    waitForCreatedRowsOrEmpty();
                    return true;
                }
            } catch (RuntimeException ignored) {}
        }
        return false;
    }

    public void searchCreatedByEmail(String email) {
        openCreatedTab();
        Locator search = page.locator(CREATED_SEARCH);
        Locator visible = null;
        for (int i = 0; i < search.count(); i++) {
            try {
                if (search.nth(i).isVisible()) { visible = search.nth(i); break; }
            } catch (RuntimeException ignored) {}
        }
        if (visible == null) throw new IllegalStateException("Active labs search input not found");
        visible.fill("");
        visible.fill(email);
        try { visible.press("Enter"); } catch (RuntimeException ignored) {}
        page.waitForTimeout(1_500);
        waitForCreatedRowsOrEmpty();
    }

    public void clearCreatedSearch() {
        try {
            Locator search = page.locator(CREATED_SEARCH);
            for (int i = 0; i < search.count(); i++) {
                if (!search.nth(i).isVisible()) continue;
                search.nth(i).fill("");
                try { search.nth(i).press("Enter"); } catch (RuntimeException ignored) {}
                page.waitForTimeout(800);
                break;
            }
            waitForCreatedRowsOrEmpty();
        } catch (RuntimeException ignored) {}
    }

    public java.util.List<String> createdEmailsOnCurrentPage() {
        java.util.LinkedHashSet<String> out = new java.util.LinkedHashSet<>();
        Locator rows = page.locator(CREATED_TABLE + " tbody tr");
        for (int i = 0; i < rows.count(); i++) {
            try {
                Locator row = rows.nth(i);
                if (row.locator(".no-records-found").count() > 0) continue;
                Matcher m = Pattern.compile("[\\w.+-]+@[\\w.-]+\\.[A-Za-z]{2,}").matcher(row.innerText());
                while (m.find()) out.add(m.group().toLowerCase());
            } catch (RuntimeException ignored) {}
        }
        return new java.util.ArrayList<>(out);
    }

    /** Collect user emails across all Active labs pages (pagination). */
    public java.util.Set<String> allCreatedEmails() {
        java.util.LinkedHashSet<String> all = new java.util.LinkedHashSet<>();
        openCreatedTab();
        clearCreatedSearch();
        goToCreatedFirstPage();
        for (int pageNum = 0; pageNum < 50; pageNum++) {
            all.addAll(createdEmailsOnCurrentPage());
            if (!clickCreatedNextPage()) break;
        }
        goToCreatedFirstPage();
        return all;
    }

    public boolean hasCreatedPagination() {
        return createdNextEnabled() || createdHasPageNumber(2);
    }

    private boolean createdHasPageNumber(int n) {
        Locator pageN = page.locator(
                ".fixed-table-pagination .pagination li a, .fixed-table-pagination .page-item a")
                .filter(new Locator.FilterOptions().setHasText(Pattern.compile("^" + n + "$")));
        for (int i = 0; i < pageN.count(); i++) {
            try { if (pageN.nth(i).isVisible()) return true; } catch (RuntimeException ignored) {}
        }
        return false;
    }

    private boolean createdNextEnabled() {
        Locator nextItems = page.locator(
                ".fixed-table-pagination .page-next, .fixed-table-pagination li.page-next, "
                        + ".fixed-table-pagination a[aria-label='next']");
        for (int i = 0; i < nextItems.count(); i++) {
            try {
                Locator n = nextItems.nth(i);
                if (!n.isVisible()) continue;
                String cls = n.getAttribute("class");
                if (cls != null && cls.contains("disabled")) continue;
                if ("true".equalsIgnoreCase(n.getAttribute("aria-disabled"))) continue;
                return true;
            } catch (RuntimeException ignored) {}
        }
        return false;
    }

    public boolean clickCreatedNextPage() {
        java.util.List<String> before = createdEmailsOnCurrentPage();
        Locator page2 = page.locator(
                ".fixed-table-pagination .pagination li a, .fixed-table-pagination .page-item a")
                .filter(new Locator.FilterOptions().setHasText(Pattern.compile("^2$")));
        for (int i = 0; i < page2.count(); i++) {
            try {
                if (!page2.nth(i).isVisible()) continue;
                page2.nth(i).click();
                page.waitForTimeout(1_500);
                waitForCreatedRowsOrEmpty();
                return !before.equals(createdEmailsOnCurrentPage());
            } catch (RuntimeException ignored) {}
        }
        Locator next = page.locator(
                ".fixed-table-pagination .page-next:not(.disabled) a, "
                        + ".fixed-table-pagination li.page-next:not(.disabled) a");
        for (int i = 0; i < next.count(); i++) {
            try {
                if (!next.nth(i).isVisible()) continue;
                next.nth(i).click();
                page.waitForTimeout(1_500);
                waitForCreatedRowsOrEmpty();
                return !before.equals(createdEmailsOnCurrentPage());
            } catch (RuntimeException ignored) {}
        }
        return false;
    }

    public boolean clickCreatedPrevPage() {
        Locator page1 = page.locator(
                ".fixed-table-pagination .pagination li a, .fixed-table-pagination .page-item a")
                .filter(new Locator.FilterOptions().setHasText(Pattern.compile("^1$")));
        for (int i = 0; i < page1.count(); i++) {
            try {
                if (!page1.nth(i).isVisible()) continue;
                page1.nth(i).click();
                page.waitForTimeout(1_500);
                waitForCreatedRowsOrEmpty();
                return true;
            } catch (RuntimeException ignored) {}
        }
        Locator prev = page.locator(
                ".fixed-table-pagination .page-pre:not(.disabled) a, "
                        + ".fixed-table-pagination li.page-pre:not(.disabled) a");
        for (int i = 0; i < prev.count(); i++) {
            try {
                if (!prev.nth(i).isVisible()) continue;
                prev.nth(i).click();
                page.waitForTimeout(1_500);
                waitForCreatedRowsOrEmpty();
                return true;
            } catch (RuntimeException ignored) {}
        }
        return false;
    }

    /**
     * Bulk-create labs for several No labs emails (2–3 users).
     * Collects each row's data-index via search, clears the filter, re-checks all, then one Create.
     */
    public void createLabsForEmails(java.util.List<String> emails, int sectionId) {
        open(sectionId);
        openNotCreatedTab();
        java.util.List<String> indexes = new java.util.ArrayList<>();
        for (String email : emails) {
            clearNotCreatedSearch();
            searchNotCreatedByEmail(email);
            if (!notCreatedContainsEmail(email)) {
                throw new IllegalStateException("user not found for bulk Create: " + email);
            }
            Locator cb = notCreatedRowForEmail(email).first().locator("input[name='btSelectItem']");
            String idx = cb.getAttribute("data-index");
            if (idx == null || idx.isBlank()) idx = cb.getAttribute("value");
            if (idx != null && !idx.isBlank()) indexes.add(idx.trim());
            if (!cb.isChecked()) cb.check();
            System.out.println("[ManageLabs] bulk Create queued email=" + email + " index=" + idx);
        }
        clearNotCreatedSearch();
        page.waitForTimeout(1_000);
        waitForNotCreatedRowsOrEmpty();
        // Re-apply selection on the unfiltered table (search would otherwise leave only the last row).
        int checked = 0;
        for (String idx : indexes) {
            Locator byIdx = page.locator(
                    "#labnotcreatedusers input[name='btSelectItem'][data-index='" + idx + "'], "
                            + "#labnotcreatedusers input[name='btSelectItem'][value='" + idx + "']");
            if (byIdx.count() > 0) {
                if (!byIdx.first().isChecked()) byIdx.first().check();
                checked++;
                continue;
            }
            try {
                page.evaluate("i => { const $t = $('#labnotcreatedusers table'); "
                        + "if ($t.length && $t.bootstrapTable) $t.bootstrapTable('check', parseInt(i, 10)); }",
                        idx);
                checked++;
            } catch (RuntimeException ignored) {}
        }
        if (checked < emails.size()) {
            // Fallback: ensure each email is checked via pagination (no search).
            for (String email : emails) {
                checkNotCreatedUserEmailKeepingSelection(email);
            }
        }
        page.waitForTimeout(800);
        page.locator(NOTCREATED_CREATE).click(new Locator.ClickOptions().setForce(true));
        try { confirmModal(); } catch (Throwable ignored) {}
    }

    /**
     * Check a No labs row by email without search filter (paginate). Used as multi-select fallback.
     */
    public void checkNotCreatedUserEmailKeepingSelection(String email) {
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("email required");
        }
        clearNotCreatedSearch();
        goToNotCreatedFirstPage();
        for (int p = 0; p < 40; p++) {
            if (notCreatedContainsEmail(email)) {
                Locator box = notCreatedRowForEmail(email).first().locator("input[name='btSelectItem']");
                if (!box.isChecked()) box.check();
                System.out.println("[ManageLabs] No labs multi-select checked: " + email);
                return;
            }
            if (!clickNotCreatedNextPage()) break;
        }
        throw new IllegalStateException("user not found for multi-select Create: " + email);
    }

    /**
     * Active labs: Reports → Download (menu) → Download (modal) and verify a file is saved.
     */
    public boolean exerciseActiveLabsReportExport() {
        openActiveLabsReportDownloadModal();
        Path saved = confirmActiveLabsReportDownloadModal();
        if (saved == null) {
            return false;
        }
        try {
            long bytes = Files.size(saved);
            System.out.println("[ManageLabs] Active labs report saved " + saved.getFileName()
                    + " (" + bytes + " bytes)");
            return bytes > 0;
        } catch (IOException | RuntimeException e) {
            System.out.println("[ManageLabs] Active labs report save check failed: " + e.getMessage());
            return false;
        } finally {
            dismissActiveLabsReportModal();
        }
    }

    /** Open Reports dropdown and choose Download to show the modal. */
    public void openActiveLabsReportDownloadModal() {
        openCreatedTab();
        page.waitForTimeout(600);
        dismissOpenDropdowns();
        Locator toggle = visibleCreatedToolbarControl(REPORT_DROPDOWN);
        toggle.scrollIntoViewIfNeeded();
        toggle.click();
        page.waitForTimeout(500);
        Locator openMenu = page.locator(
                CREATED_PANE + " .dropdown.show .dropdown-menu, "
                        + CREATED_PANE + " .dropdown-menu.show");
        try {
            openMenu.first().waitFor(new Locator.WaitForOptions().setTimeout(8_000));
        } catch (RuntimeException notOpen) {
            toggle.click(new Locator.ClickOptions().setForce(true));
            page.waitForTimeout(500);
        }
        if (!clickActiveLabsReportDownloadItem()) {
            try {
                page.evaluate("""
                        () => {
                          const pane = document.querySelector('#labcreatedusers');
                          const toggle = pane && pane.querySelector('#np-ap-cl-manage-active-labs-actions');
                          if (toggle) toggle.click();
                          const dl = document.querySelector('#np-ap-manage-active-labs-report-btn');
                          if (dl) dl.dispatchEvent(new MouseEvent('click', { bubbles: true, cancelable: true }));
                          return !!(toggle && dl);
                        }
                        """);
            } catch (RuntimeException ignored) {}
        }
        for (int i = 0; i < 40; i++) {
            if (activeLabsReportModalVisible()) return;
            page.waitForTimeout(250);
        }
        throw new IllegalStateException("Active labs download modal did not open");
    }

    /** Click Download in the modal and wait for the browser download event. */
    public Path confirmActiveLabsReportDownloadModal() {
        Locator modal = page.locator(ACTIVE_LABS_REPORT_MODAL + ".show, " + ACTIVE_LABS_REPORT_MODAL);
        Locator modalBtn = modal.locator(ACTIVE_LABS_REPORT_MODAL_DOWNLOAD_BTN);
        if (modalBtn.count() == 0) {
            modalBtn = modal.locator("button, a.btn, input[type=button], input[type=submit]")
                    .filter(new Locator.FilterOptions().setHasText(
                            Pattern.compile("^\\s*Download\\b", Pattern.CASE_INSENSITIVE)));
        }
        for (int i = 0; i < 40; i++) {
            if (modalBtn.count() > 0) {
                try {
                    if (modalBtn.first().isVisible()) break;
                } catch (RuntimeException ignored) {}
            }
            page.waitForTimeout(250);
        }
        if (modalBtn.count() == 0) {
            System.out.println("[ManageLabs] modal Download button not found in "
                    + ACTIVE_LABS_REPORT_MODAL);
            return null;
        }
        Locator clickTarget = modalBtn.first();
        Download download;
        try {
            download = page.waitForDownload(new Page.WaitForDownloadOptions().setTimeout(60_000),
                    () -> clickTarget.click(new Locator.ClickOptions().setForce(true)));
        } catch (RuntimeException e) {
            System.out.println("[ManageLabs] waitForDownload failed: " + e.getMessage());
            return null;
        }
        String name = download.suggestedFilename();
        if (name == null || name.isBlank()) {
            name = "manage-active-labs-report.csv";
        }
        Path dest = Path.of("target", "downloads", "active-labs-" + System.currentTimeMillis() + "-" + name);
        try {
            dest.getParent().toFile().mkdirs();
            download.saveAs(dest);
            return dest;
        } catch (RuntimeException e) {
            System.out.println("[ManageLabs] saveAs failed: " + e.getMessage());
            return null;
        }
    }

    /** @deprecated for assertions — use {@link #exerciseActiveLabsReportExport()}. */
    public boolean reportExportAvailable() {
        return exerciseActiveLabsReportExport();
    }

    private boolean clickActiveLabsReportDownloadItem() {
        Locator inMenu = page.locator(
                CREATED_PANE + " .dropdown-menu.show " + ACTIVE_LABS_REPORT_DOWNLOAD + ", "
                        + CREATED_PANE + " .dropdown.show " + ACTIVE_LABS_REPORT_DOWNLOAD);
        for (int i = 0; i < inMenu.count(); i++) {
            try {
                if (inMenu.nth(i).isVisible()) {
                    inMenu.nth(i).click(new Locator.ClickOptions().setForce(true));
                    return true;
                }
            } catch (RuntimeException ignored) {}
        }
        Locator byText = page.locator(CREATED_PANE + " .dropdown-menu.show a.dropdown-item, "
                        + CREATED_PANE + " .dropdown.show a.dropdown-item")
                .filter(new Locator.FilterOptions().setHasText(Pattern.compile("^\\s*Download\\b", Pattern.CASE_INSENSITIVE)));
        if (byText.count() > 0) {
            try {
                byText.first().click(new Locator.ClickOptions().setForce(true));
                return true;
            } catch (RuntimeException ignored) {}
        }
        return false;
    }

    private boolean activeLabsReportModalVisible() {
        Locator modal = page.locator(ACTIVE_LABS_REPORT_MODAL);
        for (int i = 0; i < modal.count(); i++) {
            try {
                Locator m = modal.nth(i);
                if (!m.isVisible()) continue;
                String cls = m.getAttribute("class");
                if (cls != null && cls.contains("show")) return true;
                return true;
            } catch (RuntimeException ignored) {}
        }
        try {
            Object ok = page.evaluate(
                    """
                            (sel) => {
                              const m = document.querySelector(sel);
                              if (!m) return false;
                              return m.classList.contains('show')
                                  || m.getAttribute('aria-hidden') === 'false'
                                  || m.getClientRects().length > 0;
                            }
                            """,
                    ACTIVE_LABS_REPORT_MODAL);
            return Boolean.TRUE.equals(ok) || "true".equals(String.valueOf(ok));
        } catch (RuntimeException ignored) {}
        return false;
    }

    private void dismissActiveLabsReportModal() {
        try {
            Locator close = page.locator(ACTIVE_LABS_REPORT_MODAL + " [data-dismiss='modal'], "
                    + ACTIVE_LABS_REPORT_MODAL + " .btn-secondary");
            if (close.count() > 0 && close.first().isVisible()) {
                close.first().click();
                page.waitForTimeout(400);
            }
        } catch (RuntimeException ignored) {}
        dismissOpenDropdowns();
    }

    /**
     * Open the bootstrap-table columns menu and confirm it exposes column choices.
     */
    public boolean exerciseColumnToggle() {
        openCreatedTab();
        Locator cands = page.locator(COLUMNS_TOGGLE);
        boolean clicked = false;
        for (int i = 0; i < cands.count(); i++) {
            try {
                if (cands.nth(i).isVisible()) {
                    cands.nth(i).click();
                    clicked = true;
                    break;
                }
            } catch (RuntimeException ignored) {}
        }
        if (!clicked) {
            return false;
        }
        page.waitForTimeout(400);
        Locator menu = page.locator(
                ".fixed-table-toolbar .dropdown-menu.show, "
                        + ".keep-open .dropdown-menu.show, "
                        + ".columns .dropdown-menu.show");
        for (int attempt = 0; attempt < 24; attempt++) {
            if (menu.count() > 0) {
                try {
                    if (menu.first().isVisible()) {
                        Locator labels = menu.first().locator("label, li, .dropdown-item");
                        if (labels.count() >= 2) {
                            return true;
                        }
                        String text = menu.first().innerText().toLowerCase();
                        if (text.contains("column") || text.contains("email") || text.contains("status")
                                || text.contains("user") || text.contains("lab")) {
                            return true;
                        }
                    }
                } catch (RuntimeException ignored) {}
            }
            page.waitForTimeout(250);
        }
        return false;
    }

    /** @deprecated use {@link #exerciseColumnToggle()} */
    public boolean columnToggleAvailable() {
        return exerciseColumnToggle();
    }

    /**
     * Poll for Lab Details content (modal or Moodle modal region).
     *
     * @return visible modal/panel text, or {@code null} if nothing appeared in time
     */
    public String waitForLabDetailsText(int maxSeconds) {
        long deadline = System.currentTimeMillis() + maxSeconds * 1000L;
        while (System.currentTimeMillis() < deadline) {
            for (String sel : new String[]{
                    LAB_DETAIL_MODAL,
                    LAB_DETAIL_MODAL + ".show",
                    ".modal.show",
                    "[data-region='modal-container']"
            }) {
                Locator modal = page.locator(sel);
                for (int i = 0; i < modal.count(); i++) {
                    try {
                        Locator m = modal.nth(i);
                        if (!m.isVisible()) continue;
                        String text = m.innerText();
                        if (text != null && text.trim().length() > 20) {
                            return text;
                        }
                    } catch (RuntimeException ignored) {}
                }
            }
            page.waitForTimeout(500);
        }
        return null;
    }

    public boolean groupFilterPresent() {
        return page.locator(GROUP_FILTER).count() > 0;
    }

    /** Created-tab row text for assertions (action/status). */
    public String createdRowTextForEmail(String email) {
        Locator row = createdRowForEmail(email);
        if (row.count() == 0) return "";
        return row.first().innerText().replaceAll("\\s+", " ").trim();
    }


    /** Check the row checkboxes for several users (for a bulk action). */
    public void selectRowsForEmails(java.util.List<String> emails) {
        openCreatedTab();
        clearCreatedSearch();
        setCreatedPageSize("25");
        goToCreatedFirstPageIfPresent();
        uncheckAllCreatedLabRows();
        if (!checkCreatedRowsByEmailViaBootstrapTable(emails)) {
            checkCreatedRowsByEmailViaDom(emails);
        }
        clearCreatedSearch();
        page.waitForTimeout(800);
        waitForCreatedRowsOrEmpty();
        int checked = countCheckedCreatedRows();
        System.out.println("[ManageLabs] selectRowsForEmails checked=" + checked + " of " + emails.size());
        if (checked != emails.size()) {
            uncheckAllCreatedLabRows();
            checkCreatedRowsByEmailViaDom(emails);
            checked = countCheckedCreatedRows();
            System.out.println("[ManageLabs] selectRowsForEmails DOM fallback checked=" + checked);
        }
        if (checked != emails.size()) {
            throw new IllegalStateException("expected " + emails.size()
                    + " Active lab rows selected, got " + checked);
        }
        waitForCreatedLabActionToolbarEnabled(emails.size(), 15_000);
    }

    @SuppressWarnings("unchecked")
    private boolean checkCreatedRowsByEmailViaBootstrapTable(java.util.List<String> emails) {
        try {
            Object raw = page.evaluate(
                    """
                            (want) => {
                              const jq = window.jQuery || window.$;
                              if (!jq) return { ok: false, reason: 'no-jquery' };
                              const $t = jq('#np-ap-manage-lab-table');
                              if (!$t.length || !$t.bootstrapTable) return { ok: false, reason: 'no-table' };
                              $t.bootstrapTable('uncheckAll');
                              const targets = want.map(e => String(e).toLowerCase());
                              let matched = 0;
                              document.querySelectorAll('#np-ap-manage-lab-table tbody tr').forEach(tr => {
                                const text = (tr.innerText || '').toLowerCase();
                                for (const e of targets) {
                                  if (!text.includes(e)) continue;
                                  const idx = tr.getAttribute('data-index');
                                  if (idx == null) continue;
                                  $t.bootstrapTable('check', parseInt(idx, 10));
                                  matched++;
                                  break;
                                }
                              });
                              return { ok: true, matched, want: targets.length };
                            }
                            """,
                    emails);
            if (raw instanceof Map<?, ?> m) {
                Object matched = m.get("matched");
                Object want = m.get("want");
                int mCount = matched instanceof Number ? ((Number) matched).intValue() : 0;
                int wCount = want instanceof Number ? ((Number) want).intValue() : emails.size();
                System.out.println("[ManageLabs] bootstrapTable check matched=" + mCount + " want=" + wCount);
                page.waitForTimeout(600);
                return mCount >= wCount;
            }
        } catch (RuntimeException e) {
            System.out.println("[ManageLabs] bootstrapTable check failed: " + e.getMessage());
        }
        return false;
    }

    private void checkCreatedRowsByEmailViaDom(java.util.List<String> emails) {
        clearCreatedSearch();
        setCreatedPageSize("25");
        goToCreatedFirstPageIfPresent();
        for (String email : emails) {
            boolean found = false;
            for (int p = 0; p < 40; p++) {
                Locator row = createdRowForEmail(email);
                if (row.count() > 0) {
                    row.first().locator("input[name='btSelectItem']")
                            .check(new Locator.CheckOptions().setForce(true));
                    page.waitForTimeout(300);
                    found = true;
                    break;
                }
                if (!clickCreatedNextPage()) break;
            }
            if (!found) {
                clearCreatedSearch();
                searchCreatedByEmail(email);
                Locator row = createdRowForEmail(email);
                if (row.count() == 0) {
                    throw new IllegalStateException("no Active labs row for bulk select: " + email);
                }
                row.first().locator("input[name='btSelectItem']")
                        .check(new Locator.CheckOptions().setForce(true));
                clearCreatedSearch();
                page.waitForTimeout(500);
            }
        }
    }

    /** Jump to page 1 on the Active labs bootstrap-table (for pagination tests). */
    public void goToCreatedFirstPage() {
        goToCreatedFirstPageIfPresent();
    }

    private void goToCreatedFirstPageIfPresent() {
        clickCreatedPrevPage(); // idempotent-ish; may already be on page 1
        Locator page1 = page.locator(
                ".fixed-table-pagination .pagination li a, .fixed-table-pagination .page-item a")
                .filter(new Locator.FilterOptions().setHasText(Pattern.compile("^1$")));
        for (int i = 0; i < page1.count(); i++) {
            try {
                if (page1.nth(i).isVisible()) {
                    page1.nth(i).click();
                    page.waitForTimeout(1_000);
                    waitForCreatedRowsOrEmpty();
                    return;
                }
            } catch (RuntimeException ignored) {}
        }
    }

    /** Perform an action on the CURRENT multi-row selection (open Actions dropdown, click, confirm). */
    public void performActionOnSelection(String itemSelector) {
        boolean inDropdown = START.equals(itemSelector) || STOP.equals(itemSelector) || DELETE.equals(itemSelector);
        if (inDropdown) {
            performCreatedTabLabAction(itemSelector, 1);
            return;
        }
        page.waitForTimeout(400);
        page.locator(itemSelector).first().click(new Locator.ClickOptions().setForce(true));
    }

    /**
     * Start/Stop/Delete on the current Active-labs selection. Clicks only the lab Actions control
     * inside {@link #CREATED_PANE} — never {@link #REPORT_DROPDOWN} (My labs).
     */
    private void performCreatedTabLabAction(String itemSelector, int minimumChecked) {
        openCreatedTab();
        page.waitForTimeout(800);
        waitForCreatedLabActionToolbarEnabled(minimumChecked, 20_000);
        Locator actions = visibleCreatedToolbarControl(ACTIONS_BTN);
        if (actions.getAttribute("disabled") != null) {
            throw new IllegalStateException("Actions dropdown still disabled before " + itemSelector);
        }
        actions.click();
        page.locator(CREATED_PANE + " .dropdown-menu.show").first()
                .waitFor(new Locator.WaitForOptions().setTimeout(8_000));
        if (!clickLabActionMenuItemViaDom(itemSelector)) {
            Locator menuItem = visibleLabActionMenuItem(itemSelector);
            if (menuItem == null) {
                throw new IllegalStateException("lab action menu item not visible: " + itemSelector);
            }
            menuItem.click(new Locator.ClickOptions().setForce(true));
        }
        confirmModal();
        page.waitForTimeout(800);
        refreshCreatedTable();
        dismissOpenDropdowns();
    }

    /** Prefer a DOM click on the open Actions menu item (works with bootstrap-table selection). */
    private boolean clickLabActionMenuItemViaDom(String itemSelector) {
        try {
            Object ok = page.evaluate(
                    """
                            (sel) => {
                              const pane = document.querySelector('#labcreatedusers');
                              if (!pane) return false;
                              const menu = pane.querySelector('.dropdown-menu.show');
                              const btn = menu ? menu.querySelector(sel) : document.querySelector(sel);
                              if (!btn) return false;
                              btn.dispatchEvent(new MouseEvent('click', { bubbles: true, cancelable: true }));
                              return true;
                            }
                            """,
                    itemSelector);
            return Boolean.TRUE.equals(ok) || "true".equals(String.valueOf(ok));
        } catch (RuntimeException e) {
            return false;
        }
    }

    private void dismissOpenDropdowns() {
        try {
            page.keyboard().press("Escape");
            page.waitForTimeout(200);
        } catch (RuntimeException ignored) {}
    }

    private Locator visibleCreatedToolbarControl(String selector) {
        Locator inPane = page.locator(CREATED_PANE + " " + selector);
        for (int i = 0; i < inPane.count(); i++) {
            try {
                if (inPane.nth(i).isVisible()) return inPane.nth(i);
            } catch (RuntimeException ignored) {}
        }
        Locator global = page.locator(selector);
        for (int i = 0; i < global.count(); i++) {
            try {
                Locator el = global.nth(i);
                if (!el.isVisible()) continue;
                String id = el.getAttribute("id");
                if (REPORT_DROPDOWN.substring(1).equals(id)) continue;
                return el;
            } catch (RuntimeException ignored) {}
        }
        throw new IllegalStateException("toolbar control not visible in Active labs: " + selector);
    }

    private Locator visibleLabActionMenuItem(String itemSelector) {
        Locator scoped = page.locator(
                CREATED_PANE + " .dropdown-menu.show " + itemSelector + ", "
                        + CREATED_PANE + " .show " + itemSelector);
        for (int i = 0; i < scoped.count(); i++) {
            try {
                if (scoped.nth(i).isVisible()) return scoped.nth(i);
            } catch (RuntimeException ignored) {}
        }
        Locator fallback = page.locator(itemSelector);
        for (int i = 0; i < fallback.count(); i++) {
            try {
                if (fallback.nth(i).isVisible()) return fallback.nth(i);
            } catch (RuntimeException ignored) {}
        }
        return null;
    }

    /** Perform Start/Stop/Delete on the created-tab row belonging to {@code email}. */
    public void performActionForEmail(String email, String itemSelector, int sectionId) {
        open(sectionId);
        Locator row = createdRowForEmail(email);
        if (row.count() == 0) throw new IllegalStateException("no lab row for user: " + email);
        row.first().locator("input[name='btSelectItem']").check();
        performActionOnSelection(itemSelector);
    }

    /** Perform Start/Stop/Delete on the row whose lab id matches {@code labId}. */
    public void performActionForLabId(String labId, String itemSelector, int sectionId) {
        open(sectionId);
        Locator row = page.locator("#np-ap-manage-lab-table tbody tr")
                .filter(new Locator.FilterOptions().setHasText(Pattern.compile("\\b" + Pattern.quote(labId) + "\\b")));
        if (row.count() == 0) throw new IllegalStateException("no lab row for id: " + labId);
        row.first().locator("input[name='btSelectItem']").check();
        performActionOnSelection(itemSelector);
    }

    /**
     * Robust read that tolerates slow bootstrap-table AJAX: an UNKNOWN result is usually a
     * not-yet-loaded row, so re-read a few times (short waits, one reload) before concluding.
     * This prevents mis-reading an already-settled lab as UNKNOWN and triggering a long wait.
     */
    public LabState firstRowStateStable(int sectionId) {
        LabState st = firstRowState();
        for (int i = 0; i < 3 && st == LabState.UNKNOWN; i++) {
            page.waitForTimeout(2_000);
            st = firstRowState();               // cheap re-read (data may have just populated)
        }
        if (st == LabState.UNKNOWN) {           // one full reload before giving up
            open(sectionId);
            st = firstRowState();
        }
        return st;
    }

    /** Poll (reloading Manage Labs) until the lab reaches one of the target states, or timeout. */
    public LabState waitForFirstRowState(java.util.Set<LabState> targets, int maxSeconds, int sectionId) {
        long deadline = System.currentTimeMillis() + maxSeconds * 1000L;
        LabState st = firstRowState();
        while (System.currentTimeMillis() < deadline) {
            if (targets.contains(st)) return st;
            page.waitForTimeout(15_000);
            open(sectionId);
            st = firstRowState();
        }
        return st;
    }

    /**
     * Drive the (single) lab to {@code target} using Manage Labs actions, waiting for each
     * transition to COMPLETE. Idempotent: if already there (or reachable in one step) it returns
     * quickly; otherwise it performs the needed action(s) and waits. Does NOT use cp.php.
     * Returns the state actually reached.
     */
    public LabState ensureState(LabState target, int sectionId) {
        LabState cur = LabState.UNKNOWN;
        for (int hop = 0; hop < 4; hop++) {   // RUNNING is the hub: any state reachable in <=3 hops
            open(sectionId);
            cur = firstRowStateStable(sectionId);   // robust read (won't misfire on a slow AJAX load)
            if (cur == LabState.INPROGRESS) {
                cur = waitForFirstRowState(SETTLED, 900, sectionId);   // real transition: wait long
            } else if (cur == LabState.UNKNOWN) {
                cur = waitForFirstRowState(SETTLED, 90, sectionId);    // read glitch: brief wait, then give up
            }
            if (cur == target) return cur;
            LabState next = stepToward(cur, target, sectionId);
            if (next == cur) break;           // no progress possible -> give up
        }
        return cur;
    }

    /** Take ONE transition toward {@code target}, waiting for it to complete; returns the new state. */
    private LabState stepToward(LabState cur, LabState target, int sectionId) {
        java.util.Set<LabState> runOrFail = java.util.EnumSet.of(LabState.RUNNING, LabState.FAILED);
        java.util.Set<LabState> stopOrFail = java.util.EnumSet.of(LabState.STOPPED, LabState.FAILED);
        switch (target) {
            case RUNNING:
                if (cur == LabState.STOPPED) { performActionOnFirstRow(START); return waitForFirstRowState(runOrFail, 900, sectionId); }
                if (cur == LabState.DELETED || cur == LabState.NOTCREATED || cur == LabState.FAILED) {
                    createFirstLab(sectionId); return waitForFirstRowState(runOrFail, 1200, sectionId);
                }
                break;
            case STOPPED:
                if (cur == LabState.RUNNING) { performActionOnFirstRow(STOP); return waitForFirstRowState(stopOrFail, 600, sectionId); }
                if (cur == LabState.DELETED || cur == LabState.NOTCREATED || cur == LabState.FAILED) {
                    createFirstLab(sectionId); return waitForFirstRowState(runOrFail, 1200, sectionId); // next hop stops it
                }
                break;
            case DELETED:
                if (cur == LabState.RUNNING) { performActionOnFirstRow(STOP); return waitForFirstRowState(stopOrFail, 600, sectionId); } // next hop deletes
                if (cur == LabState.STOPPED || cur == LabState.FAILED) {
                    performActionOnFirstRow(DELETE); return waitForFirstRowState(java.util.EnumSet.of(LabState.DELETED), 600, sectionId);
                }
                break;
            default:
                break;
        }
        return cur;   // no applicable transition
    }

    /** Create/provision the lab. For a Deleted/Complete row the created-users Create button applies;
     *  for a truly not-created lab, use the not-created-users tab. */
    private void createFirstLab(int sectionId) {
        open(sectionId);
        if (hasLabRows() && !actionDisabled(CREATE)) {
            selectFirstRow();
            page.waitForTimeout(800);
            performActionOnFirstRow(CREATE);
            return;
        }
        // fall back to the not-created-users tab
        if (page.locator(NOTCREATED_TAB).count() > 0) {
            page.locator(NOTCREATED_TAB).click();
            page.waitForTimeout(1_500);
            Locator cb = page.locator("#labnotcreatedusers input[name='btSelectItem']");
            if (cb.count() > 0) {
                cb.first().check();
                page.waitForTimeout(800);
                page.locator(NOTCREATED_CREATE).click(new Locator.ClickOptions().setForce(true));
                try { confirmModal(); } catch (Throwable ignored) {}
            }
        }
    }

    public ManageLabs(Page page) {
        super(page);
    }

    public void open(int sectionId) {
        navigate("/mod/cloudlabs/managelabs.php?sectionid=" + sectionId);
        page.locator("#np-ap-manage-lab-table").first().waitFor(new Locator.WaitForOptions()
                .setState(com.microsoft.playwright.options.WaitForSelectorState.VISIBLE).setTimeout(30_000));
        // bootstrap-table loads its rows via AJAX AFTER the table element renders. Wait for the
        // data to actually populate (a real lab row OR a definitive "no records" row) before
        // reading state — otherwise we read a placeholder and mis-detect the state as UNKNOWN.
        for (int i = 0; i < 30; i++) {
            boolean hasRow = page.locator(ROW_CHECKBOX).count() > 0;
            boolean noRecords = page.locator("#np-ap-manage-lab-table tbody .no-records-found,"
                    + " #np-ap-manage-lab-table tbody tr.no-records-found").count() > 0;
            if (hasRow || noRecords) break;
            page.waitForTimeout(1_000);
        }
        page.waitForTimeout(1_500);
    }

    /** {Running, Stopped, Deleted, Failed} from the status summary (null if not found). */
    public Map<String, Integer> counters() {
        String text = page.locator("body").innerText();
        Map<String, Integer> out = new LinkedHashMap<>();
        for (String key : KEYS) {
            Matcher m = Pattern.compile(key + "\\s*:?\\s*(\\d+)", Pattern.CASE_INSENSITIVE).matcher(text);
            out.put(key, m.find() ? Integer.parseInt(m.group(1)) : null);
        }
        return out;
    }

    public int runningCount() {
        Integer r = counters().get("Running");
        return r == null ? 0 : r;
    }
}
