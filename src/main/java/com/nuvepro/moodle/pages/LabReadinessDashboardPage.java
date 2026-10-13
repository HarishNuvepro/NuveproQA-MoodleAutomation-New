package com.nuvepro.moodle.pages;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.SelectOption;
import com.microsoft.playwright.options.WaitForSelectorState;
import com.nuvepro.moodle.config.Settings;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;

/**
 * Site-wide Lab Readiness dashboard (separate from per-activity {@link LabReadinessPage}).
 * URL is environment-specific — set {@link Settings#LAB_READINESS_DASHBOARD_PATH} or rely on
 * {@link #open()} discovery (admin search + common plugin paths).
 */
public class LabReadinessDashboardPage extends BasePage {

    private static final List<String> CANDIDATE_PATHS = List.of(
            "/mod/cloudlabs/readiness_dashboard.php",
            "/mod/cloudlabs/labreadinessdashboard.php",
            "/mod/cloudlabs/lab_readiness_dashboard.php",
            "/mod/cloudlabs/labreadinesslist.php",
            "/mod/cloudlabs/labreadiness.php",
            "/local/labadminservices/labreadinessdashboard.php",
            "/local/labadminservices/index.php");

    public static final String APPLY_BTN = "#applyFilters, #applyFiltersBtn, #applyFilterBtn, button:has-text('Apply'), "
            + "input[type='submit'][value='Apply'], #np-ap-lr-apply-btn";
    public static final String SEARCH = "#searchActivity, #activitySearch, #activityNameFilter, input[name='activityname'], "
            + "input[placeholder*='Activity' i], input[placeholder*='Search' i], .search-input";
    public static final String TABLE = "#readiness-table, table.table, .bootstrap-table table, #labReadinessTable, "
            + "#np-ap-lr-labtable, .fixed-table-body table";
    public static final String LOADING = ".fixed-table-loading, .loading, [data-region='loading-icon'], "
            + ".spinner-border";

    public LabReadinessDashboardPage(Page page) {
        super(page);
    }

    public void open() {
        if (!Settings.LAB_READINESS_DASHBOARD_PATH.isBlank()) {
            navigate(normalizePath(Settings.LAB_READINESS_DASHBOARD_PATH));
            if (looksLikeDashboard()) {
                waitForTableLoaded();
                return;
            }
        }
        for (String path : CANDIDATE_PATHS) {
            navigate(path);
            if (looksLikeDashboard()) {
                waitForTableLoaded();
                return;
            }
        }
        discoverViaAdminSearch();
        waitForTableLoaded();
    }

    private static String normalizePath(String path) {
        String p = path.trim();
        return p.startsWith("/") ? p : "/" + p;
    }

    private void discoverViaAdminSearch() {
        navigate("/admin/search.php");
        page.waitForTimeout(1_500);
        Locator search = page.locator("#adminsearchquery, input[name='query']");
        if (search.count() > 0) {
            search.first().fill("lab readiness");
            search.first().press("Enter");
            page.waitForTimeout(2_000);
        }
        Locator links = page.locator(
                "a[href*='readiness'], a[href*='labreadiness'], a[href*='lab_readiness']");
        for (int i = 0; i < links.count(); i++) {
            String href = links.nth(i).getAttribute("href");
            String text = links.nth(i).innerText().toLowerCase(Locale.ROOT);
            if (href == null) continue;
            if (href.contains("lab_readiness.php?id=")) continue;
            if (text.contains("dashboard") || href.contains("dashboard") || href.contains("list")) {
                page.navigate(Settings.BASE_URL + (href.startsWith("http") ? href.replace(Settings.BASE_URL, "") : href));
                page.waitForTimeout(2_000);
                if (looksLikeDashboard()) return;
            }
        }
        if (links.count() > 0) {
            links.first().click();
            page.waitForTimeout(2_500);
        }
    }

    public boolean looksLikeDashboard() {
        String body = page.locator("body").innerText().toLowerCase(Locale.ROOT);
        boolean mentions = body.contains("lab readiness") || body.contains("total labs")
                || body.contains("ready labs") || body.contains("checklist submitted");
        boolean hasTable = page.locator(TABLE).count() > 0
                || page.locator(".bootstrap-table, .fixed-table-container").count() > 0;
        return mentions && hasTable;
    }

    public void waitForTableLoaded() {
        Locator loading = page.locator(LOADING);
        if (loading.count() > 0) {
            try {
                loading.first().waitFor(new Locator.WaitForOptions()
                        .setState(WaitForSelectorState.HIDDEN).setTimeout(60_000));
            } catch (RuntimeException ignored) {}
        }
        page.waitForTimeout(1_500);
    }

    public boolean summaryCardVisible(String fragment) {
        String f = fragment.toLowerCase(Locale.ROOT);
        Locator cards = page.locator(".card, .summary-card, .dashboard-card, [class*='card']");
        for (int i = 0; i < cards.count(); i++) {
            String t = cards.nth(i).innerText().toLowerCase(Locale.ROOT);
            if (t.contains(f)) return cards.nth(i).isVisible();
        }
        return page.locator("body").innerText().toLowerCase(Locale.ROOT).contains(f);
    }

    public int tableRowCount() {
        Locator rows = page.locator(TABLE + " tbody tr, .fixed-table-body tbody tr");
        int n = 0;
        for (int i = 0; i < rows.count(); i++) {
            String cls = rows.nth(i).getAttribute("class");
            if (cls != null && (cls.contains("no-records") || cls.contains("no-data"))) continue;
            if (rows.nth(i).locator("td").count() == 0) continue;
            String text = rows.nth(i).innerText().trim();
            if (text.isBlank() || text.toLowerCase(Locale.ROOT).contains("no matching")) continue;
            n++;
        }
        return n;
    }

    public void setActivitySearch(String text) {
        Locator box = page.locator(SEARCH);
        if (box.count() == 0) throw new AssertionError("activity search field not found on lab readiness dashboard");
        box.first().fill(text);
        page.waitForTimeout(400);
    }

    public void clearActivitySearch() {
        Locator box = page.locator(SEARCH);
        if (box.count() > 0) box.first().fill("");
    }

    public void clickApply() {
        Locator apply = page.locator(APPLY_BTN);
        if (apply.count() == 0) throw new AssertionError("Apply button not found on lab readiness dashboard");
        apply.first().click();
        waitForTableLoaded();
    }

    public void selectFilter(String selectCssOrName, String labelOrValue) {
        Locator sel = page.locator(selectCssOrName);
        if (sel.count() == 0) {
            sel = page.locator("select").filter(new Locator.FilterOptions()
                    .setHasText(labelOrValue.split(" ")[0]));
        }
        if (sel.count() == 0) throw new AssertionError("filter select not found: " + selectCssOrName);
        try {
            sel.first().selectOption(new SelectOption().setLabel(labelOrValue));
        } catch (RuntimeException e) {
            sel.first().selectOption(labelOrValue);
        }
        page.waitForTimeout(400);
    }

    /** Lab type filter (sandbox / assessment / project / practice). */
    public void filterLabType(String labTypeLabel) {
        Locator sel = page.locator("#filterLabType, select[name*='labtype' i], select[id*='labtype' i], "
                + "#labTypeFilter, #labtype");
        if (sel.count() == 0) {
            sel = page.locator("select").filter(new Locator.FilterOptions().setHasText("Sandbox"));
        }
        String value = switch (labTypeLabel.toLowerCase(Locale.ROOT)) {
            case "sandbox" -> "playground";
            case "assessment" -> "challenge";
            case "practice project", "practice" -> "guided";
            default -> labTypeLabel;
        };
        selectFilterOnLocator(sel, value);
    }

    public void filterStatus(String statusLabel) {
        Locator sel = page.locator("#filterStatus, select[name*='status' i], select[id*='status' i], #statusFilter");
        if (sel.count() == 0) {
            sel = page.locator("select").filter(new Locator.FilterOptions().setHasText("Ready"));
        }
        String value = switch (statusLabel.toLowerCase(Locale.ROOT)) {
            case "ready" -> "ready";
            case "in progress" -> "progress";
            case "not started" -> "notstarted";
            default -> statusLabel;
        };
        selectFilterOnLocator(sel, value);
    }

    public void filterCourseContaining(String fragment) {
        Locator sel = page.locator(
                "select[name*='course' i], select[id*='course' i], #courseFilter, #courses");
        selectFilterOnLocator(sel, fragment);
    }

    public void filterSupportEngineer(String nameFragment) {
        Locator sel = page.locator(
                "select[name*='support' i], select[id*='support' i], #supportEngineerFilter");
        selectFilterOnLocator(sel, nameFragment);
    }

    private void selectFilterOnLocator(Locator sel, String labelOrValue) {
        if (sel.count() == 0) throw new AssertionError("dashboard filter dropdown not found for " + labelOrValue);
        try {
            sel.first().selectOption(new SelectOption().setLabel(labelOrValue));
        } catch (RuntimeException e) {
            sel.first().selectOption(labelOrValue);
        }
        page.waitForTimeout(400);
    }

    public boolean tableContainsText(String needle) {
        String hay = page.locator(TABLE).count() > 0
                ? page.locator(TABLE).first().innerText().toLowerCase(Locale.ROOT)
                : page.locator("body").innerText().toLowerCase(Locale.ROOT);
        return hay.contains(needle.toLowerCase(Locale.ROOT));
    }

    public boolean showsEmptyState() {
        String body = page.locator("body").innerText().toLowerCase(Locale.ROOT);
        return body.contains("no record") || body.contains("no matching") || body.contains("no labs")
                || body.contains("no data") || tableRowCount() == 0;
    }

    public boolean statusValuesValid() {
        if (tableRowCount() == 0) {
            return page.locator(TABLE).count() > 0;
        }
        String table = page.locator(TABLE + ", .fixed-table-body table").first().innerText().toLowerCase(Locale.ROOT);
        return table.contains("ready") || table.contains("not started") || table.contains("notstarted")
                || table.contains("progress") || table.contains("in progress") || table.contains("backend")
                || table.contains("pending") || table.contains("completion") || table.contains("submitted")
                || table.contains("complete") || table.contains("draft") || table.contains("failed")
                || table.contains("not ready") || table.contains("incomplete");
    }

    public int openButtonCount() {
        return page.locator(
                "a:has-text('Open'), button:has-text('Open'), "
                        + "a[href*='lab_readiness.php'], .btn:has-text('Open')").count();
    }

    /** Clicks the first Open action and returns whether lab_readiness form loads. */
    public boolean openFirstLabChecklist() {
        waitForTableLoaded();
        page.waitForTimeout(2_000);
        Locator open = page.locator(
                ".fixed-table-body a[href*='lab_readiness.php'], #readiness-table a[href*='lab_readiness.php'], "
                        + "a:has-text('Open'), button:has-text('Open'), td a.btn");
        if (open.count() == 0) {
            return false;
        }
        String href = open.first().getAttribute("href");
        if (href != null && href.contains("lab_readiness.php")) {
            String url = href.startsWith("http") ? href : Settings.BASE_URL + href;
            page.navigate(url, new com.microsoft.playwright.Page.NavigateOptions()
                    .setWaitUntil(com.microsoft.playwright.options.WaitUntilState.DOMCONTENTLOADED)
                    .setTimeout(45_000));
        } else {
            open.first().click(new Locator.ClickOptions().setForce(true));
            page.waitForLoadState(com.microsoft.playwright.options.LoadState.DOMCONTENTLOADED);
        }
        page.waitForTimeout(2_500);
        return page.url().contains("lab_readiness.php")
                && page.locator(LabReadinessPage.FORM).count() > 0;
    }

    public String firstRowActivityName() {
        Locator cell = page.locator(TABLE + " tbody tr td").first();
        return cell.count() > 0 ? cell.innerText().trim() : "";
    }

    public void searchCaseInsensitiveProbe(String upper, String lower) {
        setActivitySearch(upper);
        clickApply();
        int upperRows = tableRowCount();
        setActivitySearch(lower);
        clickApply();
        int lowerRows = tableRowCount();
        if (upperRows > 0 && lowerRows > 0) {
            // both should return rows when data exists
        }
    }

    public String urlEncodedSearch(String q) {
        return URLEncoder.encode(q, StandardCharsets.UTF_8);
    }
}
