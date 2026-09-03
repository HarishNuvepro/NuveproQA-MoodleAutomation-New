package com.nuvepro.moodle.pages;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.SelectOption;
import com.microsoft.playwright.options.WaitUntilState;
import com.nuvepro.moodle.config.Settings;

import java.util.ArrayList;
import java.util.List;

/**
 * Moodle configurable reports used by the Practice Project detailed/summary manual cases.
 * The report URLs are site-created artifacts, so callers may provide explicit URLs through .env;
 * otherwise this page searches Moodle's custom-report index by report name.
 */
public class PracticeProjectReportsPage extends BasePage {
    private static final String TABLE =
            "[data-region='report-table'] table, .reportbuilder-table, table.generaltable, table";

    public PracticeProjectReportsPage(Page page) {
        super(page);
    }

    public boolean openDetailed() {
        return open(Settings.PRACTICE_DETAILED_REPORT_URL,
                "Practice project detailed report", "Detailed practice project report");
    }

    public boolean openSummary() {
        return open(Settings.PRACTICE_SUMMARY_REPORT_URL,
                "Summary_practice_project_report_trainocate", "Practice project summary report");
    }

    private boolean open(String configuredUrl, String... names) {
        if (configuredUrl != null && !configuredUrl.isBlank()) {
            page.navigate(absolute(configuredUrl), new Page.NavigateOptions()
                    .setWaitUntil(WaitUntilState.DOMCONTENTLOADED).setTimeout(45_000));
            page.waitForTimeout(1_500);
            return table().count() > 0;
        }
        // The manual cases target the third-party Configurable Reports block. Some Moodle sites
        // instead migrated these definitions to core Custom Reports, so discover from both indexes.
        for (String index : new String[]{
                "/blocks/configurable_reports/managereport.php?courseid=1",
                "/reportbuilder/index.php"}) {
            page.navigate(Settings.BASE_URL + index, new Page.NavigateOptions()
                    .setWaitUntil(WaitUntilState.DOMCONTENTLOADED).setTimeout(45_000));
            for (String name : names) {
                Locator link = page.locator("a").filter(new Locator.FilterOptions().setHasText(name));
                if (link.count() > 0) {
                    link.first().click();
                    page.waitForLoadState();
                    page.waitForTimeout(1_500);
                    return table().count() > 0;
                }
            }
        }
        return false;
    }

    private String absolute(String url) {
        return url.startsWith("http://") || url.startsWith("https://")
                ? url : Settings.BASE_URL + (url.startsWith("/") ? url : "/" + url);
    }

    public boolean applyEmailFilter(String email) {
        openFiltersIfNeeded();
        Locator input = firstVisible(
                "input[name*='email' i], input[id*='email' i], input[placeholder*='email' i]");
        if (input == null) return false;
        input.fill(email);
        clickApply();
        return true;
    }

    public boolean applyCourse(String label) {
        openFiltersIfNeeded();
        Locator select = firstVisible(
                "select[name*='course' i], select[id*='course' i]");
        if (select == null) return false;
        if ("All".equalsIgnoreCase(label)) {
            select.selectOption(new SelectOption().setIndex(0));
        } else {
            select.selectOption(new SelectOption().setLabel(label));
        }
        clickApply();
        return true;
    }

    public boolean applyWithoutValues() {
        openFiltersIfNeeded();
        return clickApply();
    }

    public boolean cancelFilterChanges() {
        openFiltersIfNeeded();
        Locator input = firstVisible(
                "input[name*='email' i], input[id*='email' i], input[placeholder*='email' i]");
        if (input == null) return false;
        String original = input.inputValue();
        input.fill("cancelled-filter@example.invalid");
        Locator cancel = page.locator("button:has-text('Cancel'), input[type='submit'][value*='Cancel' i]");
        for (int i = 0; i < cancel.count(); i++) {
            if (!cancel.nth(i).isVisible()) continue;
            cancel.nth(i).click();
            page.waitForTimeout(700);
            Locator after = firstVisible(
                    "input[name*='email' i], input[id*='email' i], input[placeholder*='email' i]");
            return after != null && original.equals(after.inputValue());
        }
        return false;
    }

    public boolean search(String value) {
        Locator search = firstVisible("input[type='search'], input[placeholder*='search' i]");
        if (search == null) return false;
        search.fill(value);
        page.waitForTimeout(1_200);
        return true;
    }

    private void openFiltersIfNeeded() {
        if (firstVisible("input[name*='email' i], select[name*='course' i]") != null) return;
        Locator button = page.locator("button:has-text('Filters'), button:has-text('Show filters'), "
                + "[data-action='toggle-filters']");
        if (button.count() > 0) {
            try { button.first().click(); page.waitForTimeout(500); } catch (RuntimeException ignored) {}
        }
    }

    private boolean clickApply() {
        Locator apply = page.locator("button:has-text('Apply'), input[type='submit'][value*='Apply' i]");
        for (int i = 0; i < apply.count(); i++) {
            if (apply.nth(i).isVisible()) {
                apply.nth(i).click();
                page.waitForTimeout(1_500);
                return true;
            }
        }
        return false;
    }

    public int dataRows() {
        return table().locator("tbody tr:not(.emptyrow):not(.no-records-found)").count();
    }

    public List<String> rowTexts() {
        List<String> rows = new ArrayList<>();
        Locator locator = table().locator("tbody tr:not(.emptyrow):not(.no-records-found)");
        for (int i = 0; i < locator.count(); i++) rows.add(locator.nth(i).innerText());
        return rows;
    }

    public boolean allRowsContain(String text) {
        List<String> rows = rowTexts();
        if (rows.isEmpty()) return false;
        String expected = text.toLowerCase();
        return rows.stream().allMatch(row -> row.toLowerCase().contains(expected));
    }

    public boolean noRowsOrNoRecords() {
        String body = page.locator("body").innerText().toLowerCase();
        return dataRows() == 0 || body.contains("no records") || body.contains("nothing to display");
    }

    public String headers() {
        return table().locator("thead").count() > 0 ? table().locator("thead").innerText() : "";
    }

    public boolean hasDetailedIdentityColumns() {
        String h = headers().toLowerCase();
        return (h.contains("user") || h.contains("name")) && h.contains("email")
                && h.contains("course") && (h.contains("activity") || h.contains("project"))
                && (h.contains("enrol") || h.contains("attempt"));
    }

    public boolean summaryCountsAreConsistent() {
        Locator rows = table().locator("tbody tr:not(.emptyrow):not(.no-records-found)");
        if (rows.count() == 0) return false;
        for (int i = 0; i < rows.count(); i++) {
            String text = rows.nth(i).innerText();
            List<Integer> numbers = new ArrayList<>();
            java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("\\b\\d+\\b").matcher(text);
            while (matcher.find()) numbers.add(Integer.parseInt(matcher.group()));
            if (numbers.size() >= 3) {
                int total = numbers.get(numbers.size() - 3);
                int completed = numbers.get(numbers.size() - 2);
                int remaining = numbers.get(numbers.size() - 1);
                if (completed > total || completed + remaining != total) return false;
            }
        }
        return true;
    }

    public boolean rowContainsIdentity(String username, String email) {
        String all = String.join("\n", rowTexts()).toLowerCase();
        return all.contains(username.toLowerCase()) && all.contains(email.toLowerCase());
    }

    public String firstCourseOptionLabel() {
        openFiltersIfNeeded();
        Locator select = firstVisible("select[name*='course' i], select[id*='course' i]");
        if (select == null) return "";
        Locator options = select.locator("option");
        for (int i = 0; i < options.count(); i++) {
            String label = options.nth(i).innerText().trim();
            String value = options.nth(i).getAttribute("value");
            if (!label.isBlank() && !"all".equalsIgnoreCase(label)
                    && value != null && !value.isBlank() && !"0".equals(value)) return label;
        }
        return "";
    }

    public boolean headersContain(String... names) {
        String h = headers().toLowerCase();
        for (String name : names) if (!h.contains(name.toLowerCase())) return false;
        return true;
    }

    private Locator table() {
        return page.locator(TABLE).first();
    }

    private Locator firstVisible(String selector) {
        Locator all = page.locator(selector);
        for (int i = 0; i < all.count(); i++) {
            if (all.nth(i).isVisible()) return all.nth(i);
        }
        return null;
    }
}
