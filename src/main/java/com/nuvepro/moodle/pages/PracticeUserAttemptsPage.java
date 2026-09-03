package com.nuvepro.moodle.pages;

import com.microsoft.playwright.Download;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;

import java.nio.file.Path;

/**
 * Admin User Attempts drill-down ({@code practiceuserattempts.php} /
 * {@code practice-user-attempts.mustache}).
 */
public class PracticeUserAttemptsPage extends BasePage {
    public static final String TABLE = "#np-ap-user-attempts-table";
    public static final String HEADER = ".np-ap-cl-admin-user-details";
    public static final String DOWNLOAD = "#np-ap-user-attempt-export-report-btn";
    public static final String CHECK_DETAILS = "#np-ap-manage-practice-projects-each-checkpoints";
    public static final String SUBMIT_ATTEMPT = "#np-ap-practice-projects-each-checkprogress";

    public PracticeUserAttemptsPage(Page page) {
        super(page);
    }

    public boolean isLoaded() {
        return page.url().contains("practiceuserattempts.php")
                && page.locator(TABLE).count() > 0;
    }

    public String headerText() {
        Locator h = page.locator(HEADER);
        return h.count() > 0 ? h.first().innerText() : page.locator("body").innerText();
    }

    public boolean headerHasRequiredFields() {
        String t = headerText().toLowerCase();
        return t.contains("name") && t.contains("email")
                && (t.contains("practice project") || t.contains("project"))
                && t.contains("allowed") && t.contains("taken")
                && (t.contains("lab id") || t.contains("labid"));
    }

    public boolean headerContains(String... values) {
        String t = headerText().toLowerCase();
        for (String value : values) {
            if (value == null || value.isBlank()) continue;
            if (!t.contains(value.toLowerCase())) return false;
        }
        return true;
    }

    public boolean firstRowHasExactAttemptValues() {
        if (attemptRows() == 0) return false;
        Locator row = page.locator(TABLE + " tbody tr:not(.no-records-found)").first();
        Locator cells = row.locator("td");
        if (cells.count() < 5) return false;
        String attemptNo = cells.nth(1).innerText().trim();
        String start = cells.nth(2).innerText().trim();
        String status = row.innerText().toLowerCase();
        boolean numbered = attemptNo.matches(".*\\d.*");
        boolean dated = start.matches(".*\\d.*");
        boolean stated = status.contains("complete") || status.contains("progress")
                || status.contains("success") || status.contains("fail")
                || status.contains("started") || status.contains("submitted");
        return numbered && dated && stated;
    }

    public boolean tableHasRequiredColumns() {
        String tbl = TABLE;
        return page.locator(tbl + " th[data-field='attemptnumber']").count() > 0
                && page.locator(tbl + " th[data-field='startdate']").count() > 0
                && page.locator(tbl + " th[data-field='enddate']").count() > 0
                && page.locator(tbl + " th[data-field='durationtaken']").count() > 0
                && page.locator(tbl + " th[data-field='attemptstatus']").count() > 0;
    }

    public boolean downloadReportPresent() {
        return page.locator(DOWNLOAD).count() > 0;
    }

    public boolean actionButtonsPresent() {
        return page.locator(CHECK_DETAILS + ", " + SUBMIT_ATTEMPT).count() > 0;
    }

    public boolean actionButtonsDisabledByDefault() {
        Locator details = page.locator(CHECK_DETAILS);
        Locator submit = page.locator(SUBMIT_ATTEMPT);
        boolean detailsOk = details.count() == 0 || details.first().getAttribute("disabled") != null;
        boolean submitOk = submit.count() == 0 || submit.first().getAttribute("disabled") != null;
        return detailsOk && submitOk;
    }

    public int attemptRows() {
        Locator rows = page.locator(TABLE + " tbody tr:not(.no-records-found)");
        for (int i = 0; i < 20; i++) {
            if (page.locator(TABLE + " tbody tr").count() > 0) break;
            page.waitForTimeout(400);
        }
        return rows.count();
    }

    public boolean selectFirstAttemptEnablesActions() {
        if (attemptRows() == 0) return false;
        Locator row = page.locator(TABLE + " tbody tr:not(.no-records-found)").first();
        Locator radio = row.locator("input[type=radio], input[name='btSelectItem']");
        try {
            if (radio.count() > 0) radio.first().click();
            else row.click();
        } catch (RuntimeException ignored) {}
        page.waitForTimeout(500);
        Locator submit = page.locator(SUBMIT_ATTEMPT);
        Locator details = page.locator(CHECK_DETAILS);
        return (submit.count() > 0 && submit.first().getAttribute("disabled") == null)
                || (details.count() > 0 && details.first().getAttribute("disabled") == null);
    }

    public boolean tableHasAttemptDetailColumns() {
        return page.locator(TABLE + " th[data-field='ispracticeoverrideattempt']").count() > 0
                && page.locator(TABLE + " th[data-field='practicetoken']").count() > 0
                && page.locator(TABLE + " th[data-field='totaltests']").count() > 0
                && page.locator(TABLE + " th[data-field='completedtests']").count() > 0;
    }

    public boolean firstRowHasAttemptValues() {
        if (attemptRows() == 0) return false;
        String row = page.locator(TABLE + " tbody tr:not(.no-records-found)").first().innerText();
        return row != null && !row.isBlank();
    }

    public String firstRowText() {
        if (attemptRows() == 0) return "";
        return page.locator(TABLE + " tbody tr:not(.no-records-found)").first().innerText();
    }

    public boolean progressActionsAbsent() {
        return page.locator(CHECK_DETAILS).count() == 0 && page.locator(SUBMIT_ATTEMPT).count() == 0;
    }

    public boolean openCheckpointDetails() {
        if (!selectFirstAttemptEnablesActions()) return false;
        Locator details = page.locator(CHECK_DETAILS);
        if (details.count() == 0 || !details.first().isEnabled()) return false;
        details.first().click();
        page.waitForTimeout(800);
        Locator modal = page.locator("[data-region='modal-container'], .modal.show");
        for (int i = 0; i < modal.count(); i++) {
            if (modal.nth(i).isVisible() && !modal.nth(i).innerText().isBlank()) {
                Locator close = modal.nth(i).locator("button:has-text('Close'), button.close, [data-action='cancel']");
                if (close.count() > 0) {
                    try { close.first().click(); page.waitForTimeout(300); } catch (RuntimeException ignored) {}
                }
                return true;
            }
        }
        return page.locator("#checkPoints, .checkPoints, [class*='checkpoint' i]").count() > 0;
    }

    public boolean switchToCardViewShowsData() {
        Locator toggle = page.locator(".fixed-table-toolbar button[name='cardView'], "
                + ".fixed-table-toolbar button[title*='card' i], "
                + ".fixed-table-toolbar .icon-list-alt").first();
        if (toggle.count() == 0) return false;
        toggle.click();
        page.waitForTimeout(600);
        return page.locator(TABLE + ".card-view, " + TABLE + " .card-view, "
                + ".fixed-table-container .card-view").count() > 0
                || firstRowHasAttemptValues();
    }

    public boolean paginationWorks() {
        Locator next = page.locator(".fixed-table-pagination .page-next, "
                + ".fixed-table-pagination a[aria-label='Next']");
        Locator size = page.locator(".fixed-table-pagination .dropdown-toggle, "
                + ".page-list .btn, select.change-page-size").first();
        if (next.count() > 0) {
            boolean disabled = next.first().getAttribute("class") != null
                    && next.first().getAttribute("class").contains("disabled");
            if (!disabled) {
                try { next.first().click(); page.waitForTimeout(800); } catch (RuntimeException ignored) {}
            }
            return page.locator(TABLE).count() > 0;
        }
        if (size.count() > 0) {
            try { size.click(); page.waitForTimeout(300); } catch (RuntimeException ignored) {}
            return true;
        }
        return page.locator(".fixed-table-pagination, .pagination").count() > 0;
    }

    public boolean searchFiltersAttempts(String query) {
        Locator input = page.locator(".fixed-table-toolbar .search-input, "
                + TABLE + " ~ * input[type='search'], .bootstrap-table .search input").first();
        if (input.count() == 0) return false;
        input.fill(query);
        page.waitForTimeout(1_200);
        return true;
    }

    public Path downloadAttemptReport() {
        Locator btn = page.locator(DOWNLOAD);
        Download download = page.waitForDownload(new Page.WaitForDownloadOptions().setTimeout(45_000),
                () -> btn.first().click());
        Path dest = Path.of("target", "downloads", download.suggestedFilename());
        dest.getParent().toFile().mkdirs();
        download.saveAs(dest);
        return dest;
    }
}
