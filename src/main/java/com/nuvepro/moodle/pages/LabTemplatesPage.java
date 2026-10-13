package com.nuvepro.moodle.pages;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.WaitForSelectorState;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Site-admin Lab Templates pages (cloudlabs/labtemplates.php and addlabtemplate.php).
 *
 * List = bootstrap-table with server-side pagination: data rows carry data-index and arrive after
 * an AJAX call behind a .fixed-table-loading overlay, and columns hidden by the chooser are not
 * rendered in thead at all. Add form = four tab panes (General active, Lab Features holds the
 * action interval durations) with the Add/Cancel buttons outside the panes.
 */
public class LabTemplatesPage extends BasePage {
    public static final String LIST_PATH = "/mod/cloudlabs/labtemplates.php";
    public static final String ADD_PATH = "/mod/cloudlabs/addlabtemplate.php";
    public static final String TABLE = "#np-ap-lt-labtable";

    private static final String DATA_ROWS = TABLE + " tbody tr[data-index]";
    private static final String TOOLBAR = ".fixed-table-toolbar";
    private static final String SEARCH = TOOLBAR + " input.search-input, " + TOOLBAR + " .search input";
    private static final String REFRESH =
            TOOLBAR + " button[title*='Refresh' i], " + TOOLBAR + " .refresh button";
    private static final String COLUMNS = TOOLBAR + " .columns button.dropdown-toggle";
    private static final String COLUMN_MENU = TOOLBAR + " .columns .dropdown-menu";
    private static final String SUBMIT = "#addBtn";
    private static final String FORM = "#np-ap-pl-lab-right-tab";

    private static final String FEATURES_TAB = "np-ap-lt-tab-features";
    private static final List<String> TAB_PANES = List.of(
            "np-ap-lt-tab-general", FEATURES_TAB, "np-ap-lt-tab-notification", "np-ap-lt-tab-advanced");

    /** Ordered as the form renders them: Create, Start, Unpark. */
    public static final List<String> INTERVAL_ACTIONS = List.of("create", "start", "unpark");

    public LabTemplatesPage(Page page) {
        super(page);
    }

    // ---------------------------------------------------------------- listing

    public void openList() {
        navigate(LIST_PATH);
        page.locator(TABLE).waitFor(new Locator.WaitForOptions()
                .setState(WaitForSelectorState.ATTACHED).setTimeout(30_000));
        waitForDataRows();
    }

    /** bootstrap-table renders data rows only after its AJAX call resolves. */
    public void waitForDataRows() {
        long deadline = System.currentTimeMillis() + 30_000;
        while (System.currentTimeMillis() < deadline) {
            if (!isLoading() && page.locator(DATA_ROWS).count() > 0) return;
            page.waitForTimeout(250);
        }
        throw new AssertionError("lab-template table did not render data rows");
    }

    public int visibleRowCount() {
        return page.locator(DATA_ROWS).count();
    }

    public String visibleTableHeaders() {
        return page.locator(TABLE + " thead").innerText().toLowerCase(Locale.ROOT);
    }

    public boolean columnVisible(String field) {
        Locator header = page.locator(TABLE + " thead th[data-field='" + field + "']");
        return header.count() > 0 && header.first().isVisible();
    }

    public boolean chooserOffersColumn(String field) {
        return page.locator(COLUMN_MENU + " input[data-field='" + field + "']").count() > 0;
    }

    public boolean hasColumnChooser() {
        return page.locator(COLUMNS).count() > 0;
    }

    /** Show/hide a column through the chooser and wait for the header to follow. */
    public boolean setColumnVisible(String field, boolean visible) {
        if (columnVisible(field) == visible) return true;
        Locator box = page.locator(COLUMN_MENU + " input[data-field='" + field + "']").first();
        if (box.count() == 0) return false;
        openColumnChooser();
        if (box.isVisible()) box.click();
        else box.evaluate("e => e.click()");
        long deadline = System.currentTimeMillis() + 5_000;
        while (System.currentTimeMillis() < deadline) {
            if (columnVisible(field) == visible) return true;
            page.waitForTimeout(200);
        }
        return false;
    }

    public String firstTemplateName() {
        return rowName(firstTemplateRow());
    }

    public List<String> firstTwoTemplateNames() {
        Locator rows = page.locator(DATA_ROWS);
        List<String> names = new ArrayList<>();
        int limit = Math.min(2, rows.count());
        for (int i = 0; i < limit; i++) names.add(rowName(rows.nth(i)));
        return names;
    }

    public void search(String value) {
        Locator input = page.locator(SEARCH).first();
        input.fill(value);
        input.press("Enter");
        waitWhileLoading();
        page.waitForTimeout(600);
    }

    public String searchValue() {
        return page.locator(SEARCH).first().inputValue();
    }

    public String visibleTableText() {
        return page.locator(TABLE + " tbody").innerText();
    }

    public boolean hasMultiSearchControl() {
        return page.locator(TOOLBAR + " .bootstrap-tagsinput, " + TOOLBAR + " [role='listbox'], "
                + TOOLBAR + " .select2-selection__choice").count() > 0;
    }

    public boolean hasRefreshControl() {
        return page.locator(REFRESH).count() > 0;
    }

    public void refresh() {
        page.locator(REFRESH).first().click();
        waitWhileLoading();
        waitForDataRows();
    }

    public void openFirstView() {
        firstTemplateRow().locator("input[type='radio']").first().check();
        page.locator("#viewBtn").click();
        page.locator("#template_id").waitFor(new Locator.WaitForOptions()
                .setState(WaitForSelectorState.ATTACHED).setTimeout(30_000));
        page.waitForTimeout(800);
    }

    public boolean templateExists(String name) {
        if (!page.url().contains("labtemplates.php")) openList();
        search(name);
        for (int i = 0; i < page.locator(DATA_ROWS).count(); i++) {
            if (rowName(page.locator(DATA_ROWS).nth(i)).equalsIgnoreCase(name)) return true;
        }
        return false;
    }

    public String selectedRowText(String name) {
        return selectTemplateByName(name).innerText();
    }

    public void openEdit(String name) {
        selectTemplateByName(name);
        page.locator("#editBtn").click();
        page.locator("#template_id").waitFor(new Locator.WaitForOptions()
                .setState(WaitForSelectorState.ATTACHED).setTimeout(30_000));
    }

    public void openView(String name) {
        selectTemplateByName(name);
        page.locator("#viewBtn").click();
        page.locator("#template_id").waitFor(new Locator.WaitForOptions()
                .setState(WaitForSelectorState.ATTACHED).setTimeout(30_000));
    }

    public boolean delete(String name) {
        if (!templateExists(name)) return true;
        selectTemplateByName(name);
        page.locator("#deleteBtn").click();
        confirmModal();
        return waitForListCondition(() -> !templateExistsWithoutNavigation(name));
    }

    public boolean changeStatus(String name, boolean active) {
        selectTemplateByName(name);
        String button = active ? "#activateBtn" : "#deActivateBtn";
        Locator action = page.locator(button);
        if (action.count() == 0 || !action.isVisible()) return rowHasStatus(name, active);
        action.click();
        confirmModal();
        return waitForListCondition(() -> rowHasStatus(name, active));
    }

    /**
     * Performs an action expected to be rejected because a production template is in use.
     * The modal opened after the confirmation carries the service error text.
     */
    public String rejectedActionMessage(String name, String button) {
        selectTemplateByName(name);
        page.locator(button).click();
        confirmModal();
        long deadline = System.currentTimeMillis() + 15_000;
        while (System.currentTimeMillis() < deadline) {
            Locator modal = page.locator("[data-region='modal-container']:visible, .modal:visible");
            if (modal.count() > 0) {
                String text = modal.last().innerText().trim();
                if (!text.isBlank()) return text;
            }
            page.waitForTimeout(250);
        }
        return "";
    }

    public boolean viewShowsTemplateDetails() {
        String body = page.locator("#region-main, main, body").first().innerText()
                .toLowerCase(Locale.ROOT);
        return body.contains("plan") && body.contains("team")
                && (body.contains("resource") || body.contains("description"));
    }

    /** View-details footer next to Cancel (excludes Moodle messaging chrome). */
    private static final String VIEW_TEMPLATE_FOOTER = ".col-md-12:has(#cancelBtn)";

    public boolean viewHasEditAction() {
        Locator edit = page.locator(
                "a[href*='editlabtemplate.php'], " + VIEW_TEMPLATE_FOOTER + " a.btn-primary");
        for (int i = 0; i < edit.count(); i++) {
            try {
                if (edit.nth(i).isVisible()) return true;
            } catch (RuntimeException ignored) {}
        }
        return false;
    }

    public boolean viewHasDeleteAction() {
        Locator delete = page.locator(
                "a[href*='deletelabtemplate'], " + VIEW_TEMPLATE_FOOTER + " #deleteBtn, "
                        + VIEW_TEMPLATE_FOOTER + " .btn:has-text('Delete lab template'), "
                        + VIEW_TEMPLATE_FOOTER + " input[type='submit'][value*='Delete']");
        for (int i = 0; i < delete.count(); i++) {
            try {
                if (delete.nth(i).isVisible()) return true;
            } catch (RuntimeException ignored) {}
        }
        return false;
    }

    public boolean scriptExecuted() {
        Object value = page.evaluate("() => Boolean(window.__ltSearchExecuted)");
        return Boolean.TRUE.equals(value);
    }

    // --------------------------------------------------------------- add form

    public void openAddForm() {
        navigate(ADD_PATH);
        page.locator(FORM).first().waitFor(new Locator.WaitForOptions()
                .setState(WaitForSelectorState.VISIBLE).setTimeout(30_000));
        waitForIntervalDefaults();
    }

    public void openAddFormFromList() {
        page.locator("#addNewBtn, a[href*='addlabtemplate.php']").first().click();
        page.locator(FORM).first().waitFor(new Locator.WaitForOptions()
                .setState(WaitForSelectorState.VISIBLE).setTimeout(30_000));
        waitForIntervalDefaults();
    }

    private void waitForIntervalDefaults() {
        long deadline = System.currentTimeMillis() + 10_000;
        while (System.currentTimeMillis() < deadline) {
            try {
                if (!intervalValue("create").isBlank() && !intervalValue("start").isBlank()
                        && !intervalValue("unpark").isBlank()) return;
            } catch (RuntimeException ignored) {}
            page.waitForTimeout(200);
        }
    }

    public boolean onAddForm() {
        return page.url().contains("addlabtemplate.php") && page.locator(FORM).count() > 0;
    }

    public void fillRequiredTemplate(String name, String description, String planId,
                                     String teamId, String resourceType) {
        activateTab(TAB_PANES.get(0));
        page.locator("#name").fill(name);
        page.locator("#description").fill(description);
        page.locator("#plan_id").fill(planId);
        page.locator("#team_id").fill(teamId);
        page.locator("#resource_type").selectOption(resourceType);
        page.waitForTimeout(500);
        String[] defaults = "VIRTUAL_MACHINE".equals(resourceType)
                ? new String[]{"5", "3", "5"}
                : "COMPOSITION".equals(resourceType)
                        ? new String[]{"6", "3", "6"}
                        : new String[]{"4", "2", "0"};
        Locator.FillOptions hiddenField = new Locator.FillOptions().setForce(true);
        page.locator("#createIntervalDuration").fill(defaults[0], hiddenField);
        page.locator("#startIntervalDuration").fill(defaults[1], hiddenField);
        page.locator("#unparkIntervalDuration").fill(defaults[2], hiddenField);
    }

    public boolean submitAdd() {
        // The plugin binds Add through a RequireJS module after DOMContentLoaded. On a busy suite,
        // clicking as soon as the form is visible can precede that binding and silently do nothing.
        page.waitForTimeout(2_000);
        page.locator("#addBtn").click(new Locator.ClickOptions().setNoWaitAfter(true));
        return waitForSubmitOutcome("labtemplates.php");
    }

    public boolean submitUpdate() {
        page.waitForTimeout(2_000);
        page.locator("#updateBtn").click(new Locator.ClickOptions().setNoWaitAfter(true));
        return waitForSubmitOutcome("labtemplates.php");
    }

    public String formAlertText() {
        Locator text = page.locator("#np-ap-lt-alert-text");
        return text.count() == 0 ? "" : text.innerText().trim();
    }

    public void fillGeneralField(String id, String value) {
        activateTab(TAB_PANES.get(0));
        page.locator("#" + id).fill(value);
    }

    public String generalFieldValue(String id) {
        activateTab(TAB_PANES.get(0));
        return page.locator("#" + id).inputValue();
    }

    public void setFeature(String id, boolean enabled) {
        openFeaturesTab();
        Locator box = page.locator("#" + id);
        if (enabled) box.check(); else box.uncheck();
    }

    public boolean featureChecked(String id) {
        openFeaturesTab();
        return page.locator("#" + id).isChecked();
    }

    public void fillNotification(boolean enabled, String duration, String threshold,
                                 String message) {
        activateTab("np-ap-lt-tab-notification");
        Locator toggle = page.locator("#is_notify");
        if (enabled) toggle.check(); else toggle.uncheck();
        if (enabled) {
            page.locator("#lab_duration").fill(duration);
            page.locator("#notification_duration").fill(threshold);
            page.locator("#notification_message").fill(message);
        }
    }

    public void fillLabInfo(String value) {
        activateTab("np-ap-lt-tab-advanced");
        page.locator("#lab_info").fill(value);
    }

    public String controlValue(String paneId, String id) {
        activateTab(paneId);
        return page.locator("#" + id).inputValue();
    }

    public boolean controlChecked(String paneId, String id) {
        activateTab(paneId);
        return page.locator("#" + id).isChecked();
    }

    public String activePaneText(String paneId) {
        activateTab(paneId);
        return page.locator("#" + paneId).innerText();
    }

    public boolean viewTextContains(String paneId, String text) {
        return activePaneText(paneId).toLowerCase(Locale.ROOT)
                .contains(text.toLowerCase(Locale.ROOT));
    }

    public boolean hasCoreConfigurationFields() {
        return page.locator("#name").count() == 1
                && page.locator("#description").count() == 1
                && page.locator("#plan_id").count() == 1
                && page.locator("#team_id").count() == 1
                && page.locator("#resource_type").count() == 1
                && hasIntervalControl("create")
                && page.locator(SUBMIT).count() == 1;
    }

    public void selectResourceType(String value) {
        activateTab(TAB_PANES.get(0));
        page.locator("#resource_type").selectOption(value);
        page.waitForTimeout(500);
    }

    /** The action intervals live on the Lab Features pane, which is not the default tab. */
    public void openFeaturesTab() {
        activateTab(FEATURES_TAB);
    }

    public boolean hasIntervalControl(String action) {
        return intervalInput(action).count() > 0;
    }

    public boolean intervalVisible(String action) {
        Locator field = intervalInput(action);
        return field.count() > 0 && field.isVisible();
    }

    /** Reads the live value; works for an interval its resource type keeps hidden. */
    public String intervalValue(String action) {
        return (String) intervalInput(action).evaluate("e => e.value");
    }

    public boolean intervalIsPrefilled(String action) {
        return !intervalValue(action).trim().isBlank();
    }

    public void fillInterval(String action, String value) {
        openFeaturesTab();
        intervalInput(action).fill(value);
    }

    /** Intervals editable for the resource type currently selected. */
    public List<String> editableIntervals() {
        openFeaturesTab();
        List<String> editable = new ArrayList<>();
        for (String action : INTERVAL_ACTIONS) if (intervalVisible(action)) editable.add(action);
        return editable;
    }

    public boolean submitEmptyShowsRequiredErrors() {
        submit();
        return waitForValidation(() -> validationAlertShown() || hasVisibleFormValidationError());
    }

    /**
     * Clears the Create interval and submits.
     *
     * The plugin reports required fields one at a time starting with Name, so its own
     * #createDurationError message only appears once every other field is valid — which would
     * persist a template. Instead assert the field's own required constraint (valueMissing) and
     * that the submit is blocked; Name is left empty so nothing can ever be saved.
     */
    public boolean emptyCreateIntervalIsRejected() {
        fillInterval("create", "");
        Object missing = intervalInput("create").evaluate("e => Boolean(e.validity && e.validity.valueMissing)");
        if (!Boolean.TRUE.equals(missing)) return false;
        submit();
        return waitForValidation(() -> intervalErrorShown("create") || validationAlertShown());
    }

    public boolean validationAlertShown() {
        try {
            Locator alert = page.locator("#np-ap-lt-alert-display");
            return alert.count() > 0 && alert.first().isVisible()
                    && !page.locator("#np-ap-lt-alert-text").innerText().trim().isEmpty();
        } catch (RuntimeException navigationInProgress) {
            return false;
        }
    }

    public boolean negativeIntervalRejected(String action) {
        fillInterval(action, "-1");
        Object valid = intervalInput(action).evaluate("e => !e.validity || e.validity.valid");
        if (Boolean.FALSE.equals(valid)) return true;
        submit();
        return waitForValidation(() -> intervalErrorShown(action) || hasVisibleFormValidationError());
    }

    public boolean intervalErrorShown(String action) {
        Locator error = page.locator("#" + action + "DurationError");
        return error.count() > 0 && error.first().isVisible()
                && !error.first().innerText().trim().isEmpty();
    }

    // ---------------------------------------------------------------- private

    private void submit() {
        page.locator(SUBMIT).click(new Locator.ClickOptions().setNoWaitAfter(true));
    }

    private boolean waitForValidation(java.util.function.BooleanSupplier condition) {
        long deadline = System.currentTimeMillis() + 10_000;
        while (System.currentTimeMillis() < deadline) {
            if (condition.getAsBoolean()) return true;
            page.waitForTimeout(200);
        }
        return false;
    }

    private Locator intervalInput(String action) {
        return page.locator("input#" + action + "IntervalDuration");
    }

    private Locator selectTemplateByName(String name) {
        if (!page.url().contains("labtemplates.php")) openList();
        search(name);
        Locator rows = page.locator(DATA_ROWS);
        for (int i = 0; i < rows.count(); i++) {
            Locator row = rows.nth(i);
            if (rowName(row).equalsIgnoreCase(name)) {
                row.locator("input[type='radio']").check();
                page.waitForTimeout(300);
                return row;
            }
        }
        throw new AssertionError("lab template not found: " + name);
    }

    private boolean templateExistsWithoutNavigation(String name) {
        waitWhileLoading();
        Locator rows = page.locator(DATA_ROWS);
        for (int i = 0; i < rows.count(); i++) {
            if (rowName(rows.nth(i)).equalsIgnoreCase(name)) return true;
        }
        return false;
    }

    private boolean rowHasStatus(String name, boolean active) {
        if (!page.url().contains("labtemplates.php")) return false;
        search(name);
        Locator rows = page.locator(DATA_ROWS);
        for (int i = 0; i < rows.count(); i++) {
            Locator row = rows.nth(i);
            if (!rowName(row).equalsIgnoreCase(name)) continue;
            String normalized = row.innerText().toLowerCase(Locale.ROOT).replaceAll("\\s+", "");
            return active
                    ? normalized.contains("active") && !normalized.contains("inactive")
                    : normalized.contains("inactive");
        }
        return false;
    }

    private boolean waitForSubmitOutcome(String targetPath) {
        long deadline = System.currentTimeMillis() + 30_000;
        while (System.currentTimeMillis() < deadline) {
            try {
                if (page.url().contains(targetPath)) {
                    if (targetPath.endsWith("labtemplates.php")) {
                        page.locator(TABLE).waitFor(new Locator.WaitForOptions()
                                .setState(WaitForSelectorState.ATTACHED).setTimeout(30_000));
                        waitForDataRows();
                    }
                    return true;
                }
                if (validationAlertShown() || hasVisibleFormValidationError()) return false;
            } catch (RuntimeException navigationInProgress) {
                // The AJAX success handler is replacing the document. Retry against the new context.
            }
            page.waitForTimeout(250);
        }
        return false;
    }

    private boolean waitForListCondition(java.util.function.BooleanSupplier condition) {
        long deadline = System.currentTimeMillis() + 20_000;
        while (System.currentTimeMillis() < deadline) {
            waitWhileLoading();
            if (condition.getAsBoolean()) return true;
            page.waitForTimeout(300);
        }
        return false;
    }

    /**
     * Moodle 4.5 ships Bootstrap 5, so the plugin's Bootstrap 4 data-toggle markup does not always
     * switch the pane on click; fall back to setting the active pane directly.
     */
    private void activateTab(String paneId) {
        if (paneActive(paneId)) return;
        Locator link = page.locator("a[href='#" + paneId + "']").first();
        if (link.count() > 0) {
            link.click();
            page.waitForTimeout(300);
        }
        if (!paneActive(paneId)) {
            page.evaluate("([panes, target]) => {"
                    + " panes.forEach(id => {"
                    + "   const pane = document.getElementById(id);"
                    + "   if (pane) pane.classList.toggle('active', id === target);"
                    + "   const link = document.querySelector(`a[href='#${id}']`);"
                    + "   if (link) link.classList.toggle('active', id === target);"
                    + " });"
                    + "}", List.of(TAB_PANES, paneId));
        }
        page.locator("#" + paneId).waitFor(new Locator.WaitForOptions()
                .setState(WaitForSelectorState.VISIBLE).setTimeout(10_000));
    }

    private boolean paneActive(String paneId) {
        Locator pane = page.locator("#" + paneId);
        return pane.count() > 0 && pane.isVisible();
    }

    /** Same Bootstrap 4/5 mismatch as the tabs: open the chooser menu, forcing it if needed. */
    private void openColumnChooser() {
        Locator menu = page.locator(COLUMN_MENU).first();
        if (menu.count() > 0 && menu.isVisible()) return;
        Locator toggle = page.locator(COLUMNS).first();
        if (toggle.count() > 0) {
            toggle.click();
            page.waitForTimeout(300);
        }
        if (menu.count() > 0 && !menu.isVisible()) {
            menu.evaluate("m => { m.classList.add('show');"
                    + " if (m.parentElement) m.parentElement.classList.add('show'); }");
            page.waitForTimeout(200);
        }
    }

    private String rowName(Locator row) {
        Locator cells = row.locator("td");
        for (int i = 0; i < cells.count(); i++) {
            String value = cells.nth(i).innerText().trim();
            if (!value.isBlank()) return value;
        }
        throw new AssertionError("a lab-template row has no readable name");
    }

    private Locator firstTemplateRow() {
        waitForDataRows();
        return page.locator(DATA_ROWS).first();
    }

    private boolean isLoading() {
        Locator overlay = page.locator(".fixed-table-loading");
        return overlay.count() > 0 && overlay.first().isVisible();
    }

    private void waitWhileLoading() {
        long deadline = System.currentTimeMillis() + 30_000;
        while (System.currentTimeMillis() < deadline && isLoading()) page.waitForTimeout(200);
    }
}
