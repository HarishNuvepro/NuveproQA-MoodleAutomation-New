package com.nuvepro.moodle.pages;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.WaitForSelectorState;

/**
 * Tags on an activity's edit form (/course/modedit.php). The Tags section is collapsible and
 * starts collapsed; the field is a Moodle form-autocomplete (type + Enter adds a free-text tag).
 */
public class ActivityTags extends BasePage {
    // the autocomplete typing input lives under the tags container (not always under #fitem_id_tags)
    private static final String TAG_INPUT = "#id_tagshdrcontainer input[type='text'], #fitem_id_tags input[type='text']";

    public ActivityTags(Page page) {
        super(page);
    }

    public void openEdit(int cmid) {
        navigate("/course/modedit.php?update=" + cmid);
        page.locator("#id_submitbutton, #id_submitbutton2").first().waitFor(new Locator.WaitForOptions()
                .setState(WaitForSelectorState.VISIBLE).setTimeout(30_000));
        page.waitForTimeout(1_500);   // let the form-autocomplete JS enhance the tags select
    }

    /** Expand the (collapsed-by-default) Tags section — retry until the container is visible. */
    public void expandTags() {
        Locator container = page.locator("#id_tagshdrcontainer");
        Locator toggle = page.locator("a[href='#id_tagshdrcontainer']");
        for (int i = 0; i < 3; i++) {
            if (container.count() > 0 && container.first().isVisible()) return;
            if (toggle.count() > 0) {
                try { toggle.first().click(); } catch (Throwable ignored) {}
                page.waitForTimeout(700);
            }
        }
    }

    public boolean tagsFieldPresent() {
        expandTags();
        Locator inp = page.locator(TAG_INPUT);
        return inp.count() > 0 && inp.first().isVisible();
    }

    /** Type text into the autocomplete and commit with Enter (no assertion). */
    public void typeAndEnter(String text) {
        expandTags();
        Locator inp = page.locator(TAG_INPUT).first();
        inp.click();
        inp.fill(text);
        page.waitForTimeout(500);
        page.keyboard().press("Enter");
        page.waitForTimeout(700);
    }

    /** Type a tag into the autocomplete and commit it (Enter creates a free-text tag). */
    public void addTag(String tag) {
        typeAndEnter(tag);
    }

    public boolean chipPresent(String tag) {
        return page.locator(".form-autocomplete-selection:has-text('" + tag + "')").count() > 0;
    }

    /** Number of committed tag chips in the selection region. */
    public int chipCount() {
        return page.locator(".form-autocomplete-selection [role='option'], "
                + ".form-autocomplete-selection [role='listitem']").count();
    }

    /** Save (Save and display -> the activity view). */
    public void save() {
        Locator save = page.locator("#id_submitbutton");
        submitAndAwaitNewPage((save.count() > 0 ? save : page.locator("#id_submitbutton2")).first());
        page.waitForTimeout(2_000);
    }

    /** After save, the form redirects away (to the activity view / course). */
    public boolean savedOk() {
        return page.url().contains("view.php") || page.url().contains("/course/view");
    }

    public boolean tagPersisted(int cmid, String tag) {
        openEdit(cmid);
        expandTags();
        return chipPresent(tag);
    }

    public int tagCountCaseInsensitive(int cmid, String tag) {
        openEdit(cmid);
        expandTags();
        String lower = tag.toLowerCase();
        int n = 0;
        Locator chips = page.locator(".form-autocomplete-selection [role='option'], "
                + ".form-autocomplete-selection [role='listitem']");
        for (int i = 0; i < chips.count(); i++) {
            if (chips.nth(i).innerText().trim().toLowerCase().equals(lower)) n++;
        }
        return n;
    }

    public void removeTag(String tag) {
        expandTags();
        Locator chip = page.locator(".form-autocomplete-selection [role='option']:has-text('" + tag + "'), "
                + ".form-autocomplete-selection [role='listitem']:has-text('" + tag + "')").first();
        if (chip.count() > 0) {
            Locator remove = chip.locator("[aria-label*='Remove'], .close, button");
            if (remove.count() > 0) remove.first().click();
            page.waitForTimeout(500);
        }
    }

    /**
     * Whether {@code tag} is offered as an EXISTING suggestion in the activity tag autocomplete.
     * Types the tag name and looks for a suggestion option whose text exactly equals it. A
     * non-existent/non-standard tag yields no matching option (typing a new name shows none here).
     * Does not commit or save — clears the input afterwards.
     */
    public boolean suggestionShows(int cmid, String tag) {
        openEdit(cmid);
        expandTags();
        Locator inp = page.locator(TAG_INPUT).first();
        inp.click();
        inp.fill(tag);
        page.waitForTimeout(1_600);
        Object shown = page.evaluate(
                "(t) => Array.from(document.querySelectorAll('.form-autocomplete-suggestions [role=option]'))"
                + ".some(e => (e.textContent||'').trim() === t)", tag);
        try { inp.fill(""); } catch (Throwable ignored) {}
        return Boolean.TRUE.equals(shown);
    }
}
