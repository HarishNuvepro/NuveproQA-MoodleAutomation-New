package com.nuvepro.moodle.pages;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.WaitUntilState;

/**
 * The assessment "Manage Overrides" listing page (overrides.php → manage-overrides.mustache): lists the
 * per-user overrides with Edit / Delete controls (each disabled until a row is selected). Used by the
 * delete-override cases (A53/A54). Selectors verified against the 4.8.0 template.
 */
public class ManageOverrides extends BasePage {
    public static final String TABLE  = "#np-ap-manage-assesmntoverride-table";
    public static final String EDIT   = "#np-ap-cl-manage-edit-override";
    public static final String DELETE = "#np-ap-cl-manage-delete-override";

    public ManageOverrides(Page page) {
        super(page);
    }

    public void open(String sectionId) {
        page.navigate(com.nuvepro.moodle.config.Settings.BASE_URL + "/mod/cloudlabs/overrides.php?sectionid=" + sectionId,
                new Page.NavigateOptions().setWaitUntil(WaitUntilState.DOMCONTENTLOADED).setTimeout(45_000));
        page.locator(TABLE).first().waitFor(new Locator.WaitForOptions().setTimeout(30_000));
        // bootstrap-table loads rows async — wait for a tr (data row or the no-records placeholder)
        for (int i = 0; i < 40; i++) {
            if (page.locator(TABLE + " tbody tr").count() > 0) { page.waitForTimeout(600); break; }
            page.waitForTimeout(500);
        }
    }

    public int overrideRows() {
        return page.locator(TABLE + " tbody tr:not(.no-records-found)").count();
    }

    private boolean deleteEnabled() { return page.locator(DELETE).getAttribute("disabled") == null; }

    /** True if Edit is enabled — i.e. a row IS selected (bootstrap-table registered it), even when the
     *  plugin keeps Delete disabled (it only allows deleting a NotAttempted user's override). */
    public boolean editEnabled() { return page.locator(EDIT).getAttribute("disabled") == null; }

    /** Select the first override row so bootstrap-table registers it (enabling Delete). Tries native
     *  clicks in order — the radio, a data cell (data-click-to-select), then the row — checking after each
     *  whether Delete became enabled. Returns true only once Delete is actually enabled. */
    public boolean selectFirstOverrideRow() {
        if (overrideRows() == 0) return false;
        Locator row = page.locator(TABLE + " tbody tr:not(.no-records-found)").first();
        Locator radio = row.locator("input[type=radio], input[name='btSelectItem']");
        try { if (radio.count() > 0) radio.first().click(); } catch (RuntimeException ignored) {}
        page.waitForTimeout(500);
        if (deleteEnabled()) return true;
        try { row.locator("td").nth(1).click(); } catch (RuntimeException ignored) {}   // click-to-select
        page.waitForTimeout(500);
        if (deleteEnabled()) return true;
        try { row.click(); } catch (RuntimeException ignored) {}
        page.waitForTimeout(500);
        return deleteEnabled();
    }

    /** Delete the selected override, confirming the Moodle prompt. The confirm fires an async
     *  deleteOverrideById ajax then a bootstrap-table refresh, so POLL for the row count to drop. */
    public boolean deleteSelected() {
        int before = overrideRows();
        Locator del = page.locator(DELETE);
        if (del.count() == 0 || del.first().getAttribute("disabled") != null) return false;
        del.first().click();
        // notification.confirm is async (get_strings().then) — wait for the modal, then click its PRIMARY
        // (confirm) button, not Cancel.
        Locator confirm = page.locator("[data-region='modal-container'] .modal-footer button.btn-primary, "
                + "[data-region='modal-container'] button[data-action='save'], "
                + ".modal.show .modal-footer button.btn-primary");
        for (int i = 0; i < 12; i++) {
            if (confirm.count() > 0 && confirm.first().isVisible()) break;
            page.waitForTimeout(400);
        }
        try {
            if (confirm.count() > 0 && confirm.first().isVisible()) confirm.first().click();
            else {
                Locator yes = page.locator("button:has-text('Yes'), button:has-text('Delete'), button:has-text('Confirm')");
                if (yes.count() > 0) yes.first().click();
            }
        } catch (RuntimeException ignored) {}
        // async delete + table refresh — poll up to ~12s for the row to drop
        for (int i = 0; i < 24; i++) {
            if (overrideRows() < before) return true;
            page.waitForTimeout(500);
        }
        return overrideRows() < before;
    }
}
