package com.nuvepro.moodle.pages;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.LoadState;

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
    public static final String CREATED_TABLE = "#np-ap-manage-lab-table";
    public static final String CREATED_SEARCH =
            "#np-ap-manage-lab-table .fixed-table-toolbar .search input, "
                    + "#labcreatedusers .fixed-table-toolbar .search input, "
                    + ".tab-pane#labcreatedusers .search input, "
                    + ".tab-pane.active .fixed-table-toolbar .search input";
    public static final String GROUP_FILTER = "#course_groups";
    public static final String REPORT_DROPDOWN = "#np-ap-cl-manage-active-labs-actions";
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
            page.locator(ACTIONS_BTN).click();
            page.waitForTimeout(400);
        }
        page.locator(itemSelector).click(new Locator.ClickOptions().setForce(true));
        // Start/Stop/Delete/Create raise a Moodle confirm; Sync Status fires immediately (no dialog).
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
        selectNotCreatedUserEmail(email, sectionId);
        page.waitForTimeout(1_000);
        page.locator(NOTCREATED_CREATE).click(new Locator.ClickOptions().setForce(true));
        try { confirmModal(); } catch (Throwable ignored) {}
    }

    /**
     * Find a No labs row by email (search first — lists are paginated), then check it.
     * Retries with reload because enrolments can lag a few seconds.
     */
    public void selectNotCreatedUserEmail(String email, int sectionId) {
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("email required");
        }
        String username = email.contains("@") ? email.substring(0, email.indexOf('@')) : email;
        for (int attempt = 1; attempt <= 8; attempt++) {
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
            page.waitForTimeout(2_500);
        }
        throw new IllegalStateException("user not found in not-created list: " + email);
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

    public boolean reportExportAvailable() {
        Locator cands = page.locator(EXPORT_BTNS + ", " + REPORT_DROPDOWN);
        for (int i = 0; i < cands.count(); i++) {
            try {
                if (cands.nth(i).isVisible()) return true;
            } catch (RuntimeException ignored) {}
        }
        String body = page.locator("body").innerText().toLowerCase();
        return body.contains("export") || body.contains("download report") || body.contains("report");
    }

    public boolean columnToggleAvailable() {
        Locator cands = page.locator(COLUMNS_TOGGLE);
        for (int i = 0; i < cands.count(); i++) {
            try {
                if (cands.nth(i).isVisible()) {
                    cands.nth(i).click();
                    page.waitForTimeout(500);
                    return true;
                }
            } catch (RuntimeException ignored) {}
        }
        return false;
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
        java.util.List<String> indexes = new java.util.ArrayList<>();
        for (String email : emails) {
            clearCreatedSearch();
            searchCreatedByEmail(email);
            Locator row = createdRowForEmail(email);
            if (row.count() == 0) {
                throw new IllegalStateException("no Active labs row for bulk select: " + email);
            }
            Locator cb = row.first().locator("input[name='btSelectItem']");
            String idx = cb.getAttribute("data-index");
            if (idx == null || idx.isBlank()) idx = cb.getAttribute("value");
            if (idx != null && !idx.isBlank()) indexes.add(idx.trim());
            if (!cb.isChecked()) cb.check();
        }
        clearCreatedSearch();
        page.waitForTimeout(1_000);
        waitForCreatedRowsOrEmpty();
        for (String idx : indexes) {
            Locator byIdx = page.locator(
                    "#np-ap-manage-lab-table input[name='btSelectItem'][data-index='" + idx + "'], "
                            + "#np-ap-manage-lab-table input[name='btSelectItem'][value='" + idx + "']");
            if (byIdx.count() > 0) {
                if (!byIdx.first().isChecked()) byIdx.first().check();
                continue;
            }
            try {
                page.evaluate("i => { const $t = $('#np-ap-manage-lab-table'); "
                        + "if ($t.length && $t.bootstrapTable) $t.bootstrapTable('check', parseInt(i, 10)); }",
                        idx);
            } catch (RuntimeException ignored) {}
        }
        // Fallback: page through and check any still missing.
        for (String email : emails) {
            if (createdContainsEmail(email)) {
                Locator row = createdRowForEmail(email);
                if (row.count() > 0) {
                    Locator cb = row.first().locator("input[name='btSelectItem']");
                    if (!cb.isChecked()) cb.check();
                }
                continue;
            }
            clearCreatedSearch();
            goToCreatedFirstPageIfPresent();
            boolean found = false;
            for (int p = 0; p < 40; p++) {
                if (createdContainsEmail(email)) {
                    Locator cb = createdRowForEmail(email).first().locator("input[name='btSelectItem']");
                    if (!cb.isChecked()) cb.check();
                    found = true;
                    break;
                }
                if (!clickCreatedNextPage()) break;
            }
            if (!found) throw new IllegalStateException("could not select Active row: " + email);
        }
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
        page.waitForTimeout(1_000);
        boolean inDropdown = START.equals(itemSelector) || STOP.equals(itemSelector) || DELETE.equals(itemSelector);
        if (inDropdown) {
            page.locator(ACTIONS_BTN).click();
            page.waitForTimeout(400);
        }
        page.locator(itemSelector).click(new Locator.ClickOptions().setForce(true));
        try { confirmModal(); } catch (Throwable ignored) {}
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
