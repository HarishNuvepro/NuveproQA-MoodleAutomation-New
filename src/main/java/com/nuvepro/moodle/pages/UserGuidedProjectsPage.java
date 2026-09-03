package com.nuvepro.moodle.pages;

import com.microsoft.playwright.Download;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.WaitUntilState;
import com.nuvepro.moodle.config.Settings;

import java.nio.file.Path;

/**
 * Admin User Practice Project page ({@code userguidedprojects.php} /
 * {@code user-guided-projects.mustache}).
 */
public class UserGuidedProjectsPage extends BasePage {
    public static final String TABLE = "#np-ap-manage-user-guided-projects";
    public static final String USER_ATTEMPTS = "#np-guided-user-attempt";
    public static final String CHECKPOINTS = "#np-ap-manage-guided-projects-checkpoints";
    public static final String REPORTS = "#np-ap-cl-assessment-override-actions";
    public static final String REPORT_DOWNLOAD = "#np-ap-assessment-report-btn";
    public static final String REPORT_EMAIL = "#np-ap-assessment-report-email-btn";
    public static final String ADD_OVERRIDE = "#np-ap-add-override-btn";
    public static final String DOWNLOAD_MODAL_BTN = "#downloadReportBtn";
    public static final String EMAIL_MODAL = "#sendEmailModal";
    public static final String EMAIL_RECIPIENT = "#recipientEmail";
    public static final String EMAIL_SEND = "#emailBtnAssessmentReport";

    public UserGuidedProjectsPage(Page page) {
        super(page);
    }

    public void open(String sectionId) {
        page.navigate(Settings.BASE_URL + "/mod/cloudlabs/userguidedprojects.php?sectionid=" + sectionId,
                new Page.NavigateOptions().setWaitUntil(WaitUntilState.DOMCONTENTLOADED).setTimeout(45_000));
        page.locator(TABLE).first().waitFor(new Locator.WaitForOptions().setTimeout(30_000));
        for (int i = 0; i < 40; i++) {
            if (page.locator(TABLE + " tbody tr").count() > 0) {
                page.waitForTimeout(600);
                break;
            }
            page.waitForTimeout(500);
        }
    }

    public boolean reportsDropdownHasDownloadAndEmail() {
        openReportsMenu();
        return page.locator(REPORT_DOWNLOAD).count() > 0 && page.locator(REPORT_EMAIL).count() > 0;
    }

    public void openReportsMenu() {
        Locator toggle = page.locator(REPORTS);
        if (toggle.count() == 0) return;
        try { toggle.first().click(); } catch (RuntimeException ignored) {}
        page.waitForTimeout(400);
    }

    public boolean userAttemptsDisabled() {
        Locator btn = page.locator(USER_ATTEMPTS);
        if (btn.count() == 0) {
            Locator overrideToggle = page.locator("#overrideDropdown");
            if (overrideToggle.count() > 0) {
                try { overrideToggle.first().click(); page.waitForTimeout(300); } catch (RuntimeException ignored) {}
            }
            btn = page.locator(USER_ATTEMPTS);
        }
        return btn.count() > 0 && btn.first().getAttribute("disabled") != null;
    }

    public int dataRows() {
        return page.locator(TABLE + " tbody tr:not(.no-records-found)").count();
    }

    public String firstParticipantName() {
        Locator cells = page.locator(TABLE + " tbody tr:not(.no-records-found)").first().locator("td");
        return cells.count() > 1 ? cells.nth(1).innerText().trim() : "";
    }

    public String firstParticipantEmail() {
        Locator cells = page.locator(TABLE + " tbody tr:not(.no-records-found)").first().locator("td");
        return cells.count() > 2 ? cells.nth(2).innerText().trim() : "";
    }

    public boolean exportTypesConfigured() {
        String types = page.locator(TABLE).getAttribute("data-export-types");
        if (types == null) return false;
        String t = types.toLowerCase();
        return t.contains("xml") && t.contains("csv") && t.contains("json")
                && (t.contains("txt") || t.contains("text"));
    }

    public boolean exportDropdownPresent() {
        return page.locator(".fixed-table-toolbar .export, "
                + ".fixed-table-toolbar button[name='export']").count() > 0;
    }

    public boolean exportTypesPresent() {
        if (!exportTypesConfigured()) return false;
        Locator toggle = page.locator(".fixed-table-toolbar .export .dropdown-toggle, "
                + ".fixed-table-toolbar button[name='export']").first();
        if (toggle.count() == 0) return false;
        try { toggle.click(); page.waitForTimeout(400); } catch (RuntimeException ignored) {}
        Locator menu = page.locator(".fixed-table-toolbar .export .dropdown-menu, .dropdown-menu.show");
        String text = menu.count() > 0 ? menu.first().innerText().toLowerCase() : "";
        try { page.keyboard().press("Escape"); } catch (RuntimeException ignored) {}
        return text.contains("csv") && text.contains("xml") && text.contains("json")
                && (text.contains("txt") || text.contains("text"));
    }

    public boolean selectFirstUser() {
        if (dataRows() == 0) return false;
        Locator row = page.locator(TABLE + " tbody tr:not(.no-records-found)").first();
        Locator radio = row.locator("input[type=radio], input[name='btSelectItem']");
        try {
            if (radio.count() > 0) radio.first().click();
            else row.locator("td").nth(1).click();
        } catch (RuntimeException ignored) {}
        page.waitForTimeout(500);
        return page.locator(USER_ATTEMPTS).first().getAttribute("disabled") == null;
    }

    public boolean selectUserByEmail(String email) {
        Locator row = page.locator(TABLE + " tbody tr:not(.no-records-found)").filter(
                new Locator.FilterOptions().setHasText(email)).first();
        if (row.count() == 0) return false;
        Locator select = row.locator("input[type=radio], input[name='btSelectItem']");
        try {
            if (select.count() > 0) select.first().click();
            else row.click();
        } catch (RuntimeException ignored) {}
        page.waitForTimeout(500);
        return true;
    }

    public boolean addOverrideDisabled() {
        Locator add = page.locator(ADD_OVERRIDE);
        return add.count() > 0 && (add.first().getAttribute("disabled") != null
                || add.first().getAttribute("class") != null
                && add.first().getAttribute("class").contains("disabled"));
    }

    /** Return "email|allowed attempts" for a participant other than the excluded user. */
    public String otherParticipantAttempts(String excludedEmail) {
        Locator rows = page.locator(TABLE + " tbody tr:not(.no-records-found)");
        for (int i = 0; i < rows.count(); i++) {
            String text = rows.nth(i).innerText();
            if (text.toLowerCase().contains(excludedEmail.toLowerCase())) continue;
            Locator cells = rows.nth(i).locator("td");
            if (cells.count() < 5) continue;
            String email = cells.nth(2).innerText().trim();
            String allowed = cells.nth(3).innerText().trim();
            if (!email.isBlank()) return email + "|" + allowed;
        }
        return "";
    }

    public boolean userAttemptsEnabled() {
        Locator btn = page.locator(USER_ATTEMPTS);
        return btn.count() > 0 && btn.first().getAttribute("disabled") == null;
    }

    public boolean canSelectMultipleUsers() {
        Locator boxes = page.locator(TABLE + " tbody tr:not(.no-records-found) input[name='btSelectItem']");
        if (boxes.count() <= 1) return false;
        try {
            if (!boxes.first().isChecked()) boxes.first().click();
        } catch (RuntimeException ignored) {}
        try { boxes.nth(1).click(); } catch (RuntimeException ignored) {}
        page.waitForTimeout(400);
        return page.locator(TABLE + " tbody input[name='btSelectItem']:checked").count() > 1;
    }

    public Page openUserAttemptsTab() {
        Page popup = page.waitForPopup(() -> page.locator(USER_ATTEMPTS).first().click());
        popup.setDefaultTimeout(Settings.DEFAULT_TIMEOUT_MS);
        return popup;
    }

    public Path downloadConsolidatedReport() {
        openReportsMenu();
        page.locator(REPORT_DOWNLOAD).first().click();
        Locator modalBtn = page.locator(DOWNLOAD_MODAL_BTN);
        for (int i = 0; i < 20; i++) {
            if (modalBtn.count() > 0 && modalBtn.first().isVisible()) break;
            page.waitForTimeout(250);
        }
        Download download = page.waitForDownload(new Page.WaitForDownloadOptions().setTimeout(45_000),
                () -> modalBtn.first().click());
        Path dest = Path.of("target", "downloads", download.suggestedFilename());
        dest.getParent().toFile().mkdirs();
        download.saveAs(dest);
        return dest;
    }

    public boolean sendReportEmail(String recipient) {
        openReportsMenu();
        Locator emailItem = page.locator(REPORT_EMAIL);
        if (emailItem.count() == 0) return false;
        try {
            page.evaluate("el => { el.removeAttribute('disabled'); el.classList.remove('disabled'); }",
                    emailItem.first().elementHandle());
        } catch (RuntimeException ignored) {}
        try { emailItem.first().click(new Locator.ClickOptions().setForce(true)); } catch (RuntimeException ignored) {}
        Locator modal = page.locator(EMAIL_MODAL);
        for (int i = 0; i < 20; i++) {
            if (modal.count() > 0 && modal.first().isVisible()) break;
            page.waitForTimeout(250);
        }
        if (modal.count() == 0 || !modal.first().isVisible()) return false;
        page.locator(EMAIL_RECIPIENT).first().fill(recipient);
        page.locator(EMAIL_SEND).first().click();
        page.waitForTimeout(2_000);
        String body = page.locator("body").innerText().toLowerCase();
        return body.contains("email") || body.contains("sent") || body.contains("success")
                || body.contains("queued") || page.locator(".alert-success, .toast").count() > 0;
    }

    public boolean clickAddOverride() {
        Locator add = page.locator(ADD_OVERRIDE);
        if (add.count() == 0) return false;
        Locator menu = page.locator("#overrideDropdown");
        if (menu.count() > 0) {
            try { menu.first().click(); page.waitForTimeout(250); } catch (RuntimeException ignored) {}
        }
        add.first().click();
        page.waitForTimeout(1_000);
        return page.url().contains("addguidedprojectoverride.php")
                || page.locator(".modal.show, [data-region=modal-container]").count() > 0;
    }

    public boolean cancelReportDownloadModal() {
        openReportsMenu();
        Locator download = page.locator(REPORT_DOWNLOAD);
        if (download.count() == 0) return false;
        download.first().click();
        Locator modal = page.locator("#downloadAssessmentReport");
        for (int i = 0; i < 20; i++) {
            if (modal.count() > 0 && modal.first().isVisible()) break;
            page.waitForTimeout(250);
        }
        if (modal.count() == 0 || !modal.first().isVisible()) return false;
        Locator close = modal.locator("button[data-dismiss='modal'], .close, button:has-text('Close')");
        close.first().click();
        page.waitForTimeout(400);
        return !modal.first().isVisible();
    }
}
