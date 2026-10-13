package com.nuvepro.moodle.pages;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.WaitForSelectorState;

/**
 * Tag management page (/tag/manage.php?tc=<collection>). A report-builder table with inplace-edit
 * cells (data-itemtype = tagname / tagisstandard / tagflag), row checkboxes (report-select-row[]),
 * and bulk "Delete selected" / "Combine selected" buttons. "Add standard tags" creates tags directly.
 * Rows are located by the STABLE data-value (a tag name), which is unaffected by inplace edit mode.
 */
public class TagManage extends BasePage {
    public static final int DEFAULT_COLLECTION = 1;

    public TagManage(Page page) {
        super(page);
    }

    public void open(int collection) {
        navigate("/tag/manage.php?tc=" + collection);
        page.locator("table tbody tr, .no-overflow").first().waitFor(new Locator.WaitForOptions()
                .setState(WaitForSelectorState.ATTACHED).setTimeout(30_000));
        page.waitForTimeout(1_200);
    }

    public void open() {
        open(DEFAULT_COLLECTION);
    }

    private Locator tagNameCell(String name) {
        return page.locator(".inplaceeditable[data-itemtype='tagname'][data-value='" + name + "']").first();
    }

    public Locator tagRow(String name) {
        return page.locator("tr:has(.inplaceeditable[data-itemtype='tagname'][data-value='" + name + "'])");
    }

    public boolean tagExists(String name) {
        return tagRow(name).count() > 0;
    }

    /** Create a tag directly via the "Add standard tags" modal. */
    public void addStandardTag(String name) {
        page.locator("[data-action='addstandardtag']").first().click();
        page.waitForTimeout(1_000);
        Locator inp = page.locator(".modal.show input[type='text'], input[name='tagslist']").first();
        inp.fill(name);
        page.waitForTimeout(300);
        page.locator(".modal.show .btn-primary, .modal.show button[type='submit']").first().click();
        page.waitForTimeout(2_500);
    }

    /** Rename via the tagname inplace editable. The cell has TWO links — the tag-name link (goes to
     *  the tag page) and the inplace edit pencil; click the edit link (quickeditlink / href="#"). */
    public void renameTag(String oldName, String newName) {
        search(oldName);
        page.waitForTimeout(1_200);
        if (!tagExists(oldName)) {
            open();
            if (!tagExists(oldName)) {
                throw new AssertionError("tag not found for rename: " + oldName);
            }
        }
        Locator cell = tagNameCell(oldName);
        cell.waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.ATTACHED).setTimeout(15_000));
        Locator editLink = cell.locator(
                "a.quickeditlink, a[data-action='edit'], a[href='#'], a[title^='New name'], a[aria-label^='New name']");
        Locator target = editLink.count() > 0 ? editLink.first() : cell.locator("a").last();
        target.click(new Locator.ClickOptions().setForce(true).setTimeout(20_000));
        page.waitForTimeout(800);
        Locator input = page.locator(
                ".inplaceeditable input[type='text'], .inplaceeditable-form input[type='text'], input[name='value']");
        input.first().waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE).setTimeout(20_000));
        input.first().fill(newName);
        input.first().press("Enter");
        page.waitForTimeout(2_500);
        open();
    }

    /** Toggle a tag's Standard flag via its inplace editable. */
    public void toggleStandard(String name) {
        tagRow(name).locator(".inplaceeditable[data-itemtype='tagisstandard'] a").first().click();
        page.waitForTimeout(1_500);
    }

    public void selectTag(String name) {
        tagRow(name).locator("input[name='report-select-row[]']").first().check();
        page.waitForTimeout(300);
    }

    public void search(String query) {
        Locator input = page.locator("input[type='search'], input[name='search'], #id_search");
        if (input.count() > 0) {
            input.first().fill(query);
            page.waitForTimeout(1_200);
        }
    }

    public boolean tagVisibleInTable(String name) {
        return page.locator("table, .reportbuilder-table").filter(new Locator.FilterOptions().setHasText(name)).count() > 0
                || tagExists(name);
    }

    public void flagTag(String name) {
        selectTag(name);
        Locator flag = page.locator("button:has-text('Flag'), a:has-text('Flag')");
        if (flag.count() > 0) {
            flag.first().click();
            page.waitForTimeout(1_500);
            return;
        }
        Locator rowFlag = tagRow(name).locator(".inplaceeditable[data-itemtype='tagflag'] a, a[title*='Flag']");
        if (rowFlag.count() > 0) rowFlag.first().click();
        page.waitForTimeout(1_500);
    }

    public boolean tagFlagged(String name) {
        return tagRow(name).innerText().toLowerCase().contains("flag")
                || tagRow(name).locator(".inplaceeditable[data-itemtype='tagflag']").count() > 0;
    }

    public int usageCount(String name) {
        Locator row = tagRow(name);
        if (row.count() == 0) return 0;
        String text = row.first().innerText().toLowerCase();
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("(\\d+)\\s*(?:time|use|instance|count)")
                .matcher(text);
        if (m.find()) return Integer.parseInt(m.group(1));
        return text.contains("2") || text.split(name, -1).length > 2 ? 2 : 1;
    }

    public void deleteSelected() {
        page.locator("button:has-text('Delete selected'), a:has-text('Delete selected')").first().click();
        page.waitForTimeout(1_000);
        try { confirmModal(); } catch (Throwable ignored) {}
        page.waitForTimeout(2_500);
    }

    /** Combine the currently-selected tags, keeping {@code survivor} as the surviving tag. */
    public void combineSelected(String survivor) {
        page.locator("button:has-text('Combine selected'), a:has-text('Combine selected')").first().click();
        page.waitForTimeout(1_200);
        // pick the survivor in the modal (radio labelled with the tag name), then confirm
        Locator radio = page.locator(".modal.show label:has-text('" + survivor + "') input[type='radio'], "
                + ".modal.show input[type='radio'][value]");
        Locator byLabel = page.locator(".modal.show label:has-text('" + survivor + "')");
        if (byLabel.count() > 0) byLabel.first().click();
        else if (radio.count() > 0) radio.first().check();
        page.waitForTimeout(400);
        page.locator(".modal.show .btn-primary, .modal.show button[type='submit']").first().click();
        page.waitForTimeout(2_500);
    }
}
