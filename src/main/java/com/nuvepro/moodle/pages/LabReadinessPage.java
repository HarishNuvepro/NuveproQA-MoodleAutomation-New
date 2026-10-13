package com.nuvepro.moodle.pages;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;

/**
 * Pre-delivery lab readiness checklist ({@code lab_readiness.php?id=cmid}).
 * Selectors aligned with lab_readiness.mustache / lab_readiness.js.
 */
public class LabReadinessPage extends BasePage {
    public static final String FORM = "#labReadinessForm";
    public static final String PROGRESS = "#labProgressBar";
    public static final String ITEM = ".checklist-item";
    public static final String SAVE = "#saveChecklistBtn";
    public static final String SUPPORT_NAME = "input[name='supportname'], #supportName";
    public static final String SCREENSHOT = "input[name='screenshot']";
    public static final String AUDIT_TABLE = "#checklistAuditLog, table.audit-log, "
            + "table:has-text('Audit Log'), .checklist-audit-log table";
    public static final String BACKEND_WARNING = ".backend-warning, #backendWarningBanner, "
            + ".alert-warning:has-text('backend'), .alert:has-text('warning')";

    public LabReadinessPage(Page page) {
        super(page);
    }

    public void open(int cmid) {
        navigate("/mod/cloudlabs/lab_readiness.php?id=" + cmid);
        page.waitForTimeout(2_500);
    }

    public int visibleChecklistCount() {
        Object n = page.evaluate("""
                () => Array.from(document.querySelectorAll('.checklist-item'))
                  .filter(c => c.offsetParent !== null).length
                """);
        return n instanceof Number ? ((Number) n).intValue() : 0;
    }

    public void setCheckedVisibleCount(int targetChecked) {
        page.evaluate("""
                (target) => {
                  const items = Array.from(document.querySelectorAll('.checklist-item'))
                    .filter(c => c.offsetParent !== null);
                  items.forEach((c, i) => {
                    const want = i < target;
                    if (c.checked !== want) {
                      c.checked = want;
                      c.dispatchEvent(new Event('change', { bubbles: true }));
                    }
                  });
                }
                """, targetChecked);
        page.waitForTimeout(800);
    }

    public void uncheckAllVisible() {
        setCheckedVisibleCount(0);
    }

    public String progressText() {
        Locator bar = page.locator(PROGRESS);
        return bar.count() > 0 ? bar.first().innerText().trim() : "";
    }

    public String progressBarClass() {
        Locator bar = page.locator(PROGRESS);
        return bar.count() > 0 ? bar.first().getAttribute("class") : "";
    }

    public boolean sectionCardVisible(String headingFragment) {
        String h = headingFragment.toLowerCase();
        Locator cards = page.locator(".card, .checklist-section, fieldset, .form-group");
        for (int i = 0; i < cards.count(); i++) {
            String t = cards.nth(i).innerText().toLowerCase();
            if (t.contains(h)) return cards.nth(i).isVisible();
        }
        return page.locator("body").innerText().toLowerCase().contains(h);
    }

    public int catalogCheckboxCount() {
        return page.locator(".checklist-item[data-label*='Catalog' i], "
                + ".catalog-section .checklist-item, "
                + "div:has-text('Catalog') .checklist-item").count();
    }

    public boolean hasChecklistNavOnActivityView() {
        return page.locator("a[href*='lab_readiness.php']").count() > 0;
    }

    public void fillSupportName(String value) {
        Locator field = page.locator(SUPPORT_NAME);
        if (field.count() > 0) field.first().fill(value);
    }

    public void setScreenshotFile(java.nio.file.Path path) {
        Locator file = page.locator(SCREENSHOT);
        if (file.count() == 0) throw new AssertionError("screenshot upload input missing");
        file.first().setInputFiles(path);
        page.waitForTimeout(500);
    }

    public void clickSave() {
        page.locator(SAVE).click();
        page.waitForLoadState(com.microsoft.playwright.options.LoadState.DOMCONTENTLOADED);
        page.waitForTimeout(2_500);
    }

    public int auditLogRowCount() {
        Locator table = page.locator("h5:has-text('Checklist Audit Log') + table, "
                + "h5:has-text('Audit Log') + table, " + AUDIT_TABLE);
        if (table.count() == 0) {
            String body = page.locator("body").innerText().toLowerCase();
            if (!body.contains("audit log")) return 0;
            table = page.locator("table.table-bordered, table.table").filter(
                    new com.microsoft.playwright.Locator.FilterOptions().setHasText("Modified By"));
        }
        if (table.count() == 0) return 0;
        int rows = table.first().locator("tbody tr").count();
        if (rows == 0) {
            String tbody = table.first().innerText().toLowerCase();
            if (tbody.contains("modified by") && !tbody.contains("no ")) {
                return 0;
            }
        }
        return rows;
    }

    public boolean backendWarningVisible() {
        if (page.locator(".alert.alert-warning:has-text('Lab configuration changed')").count() > 0) {
            return page.locator(".alert.alert-warning:has-text('Lab configuration changed')").first().isVisible();
        }
        return page.locator(BACKEND_WARNING).count() > 0
                && page.locator(BACKEND_WARNING).first().isVisible();
    }

    public boolean checklistFullySaved() {
        String progress = progressText();
        return progress.contains("100");
    }

    public int checklistItemCount() {
        return page.locator(ITEM).count();
    }

    public String supportNameValue() {
        Locator name = page.locator(SUPPORT_NAME);
        return name.count() > 0 ? name.first().inputValue() : "";
    }
}
