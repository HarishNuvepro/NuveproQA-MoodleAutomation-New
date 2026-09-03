package com.nuvepro.moodle.pages;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.WaitForSelectorState;
import com.microsoft.playwright.options.WaitUntilState;
import com.nuvepro.moodle.config.Settings;

/**
 * Practice Project Manage Overrides listing ({@code manageguidedprojectoverrides.php}).
 */
public class GuidedProjectOverridesPage extends BasePage {
    public GuidedProjectOverridesPage(Page page) {
        super(page);
    }

    public void open(String sectionId) {
        page.navigate(Settings.BASE_URL
                        + "/mod/cloudlabs/manageguidedprojectoverrides.php?sectionid=" + sectionId,
                new Page.NavigateOptions().setWaitUntil(WaitUntilState.DOMCONTENTLOADED).setTimeout(45_000));
        page.waitForTimeout(1_500);
    }

    public boolean isLoaded() {
        String url = page.url().toLowerCase();
        String body = page.locator("body").innerText().toLowerCase();
        return url.contains("manageguidedprojectoverrides")
                || body.contains("override");
    }

    public boolean statusColumnPresent() {
        return page.locator("th[data-field='overridestatus'], th:has-text('Override Status')").count() > 0;
    }

    public boolean overrideStatusIsKnown(String email) {
        String status = overrideCellText(email, 5).toLowerCase();
        return status.contains("completed") || status.contains("started")
                || status.contains("not started") || status.contains("inprogress")
                || status.contains("in progress") || status.contains("not attempted");
    }

    public boolean hasStatusOrEmptyState() {
        String body = page.locator("body").innerText().toLowerCase();
        return body.contains("override")
                || body.contains("no matching records")
                || body.contains("no override")
                || page.locator("table tbody tr").count() > 0;
    }

    public void openAdd(String cmid, long userId) {
        page.navigate(Settings.BASE_URL + "/mod/cloudlabs/addguidedprojectoverride.php?cmid="
                        + cmid + "&userid=" + userId,
                new Page.NavigateOptions().setWaitUntil(WaitUntilState.DOMCONTENTLOADED).setTimeout(45_000));
        page.locator("#id_allowedattempts, [name='allowedattempts']").first()
                .waitFor(new Locator.WaitForOptions().setTimeout(30_000));
        Locator users = page.locator("#id_userids");
        if (users.count() > 0) {
            Locator option = users.locator("option[value='" + userId + "']");
            if (option.count() == 0) {
                throw new IllegalStateException("fresh Practice Project user " + userId
                        + " is not offered on the override form");
            }
            users.selectOption(new String[]{String.valueOf(userId)},
                    new Locator.SelectOptionOptions().setForce(true));
        }
    }

    /** The deployed form lists only participants eligible for an override. Select a non-admin
     * participant and return the email shown in the option label. */
    public String openAddForEligibleUser(String cmid) {
        page.navigate(Settings.BASE_URL + "/mod/cloudlabs/addguidedprojectoverride.php?cmid="
                        + cmid + "&userid=2",
                new Page.NavigateOptions().setWaitUntil(WaitUntilState.DOMCONTENTLOADED).setTimeout(45_000));
        Locator users = page.locator("#id_userids");
        users.first().waitFor(new Locator.WaitForOptions()
                .setState(WaitForSelectorState.ATTACHED).setTimeout(30_000));
        Locator options = users.locator("option");
        for (int i = 0; i < options.count(); i++) {
            String text = options.nth(i).innerText().trim();
            String lower = text.toLowerCase();
            if (!lower.contains("@") || lower.contains("admin")) continue;
            String value = options.nth(i).getAttribute("value");
            users.selectOption(new String[]{value},
                    new Locator.SelectOptionOptions().setForce(true));
            int comma = text.lastIndexOf(',');
            return comma >= 0 ? text.substring(comma + 1).trim() : text;
        }
        throw new IllegalStateException("override form has no eligible non-admin Practice Project participant");
    }

    public void openAddForUserEmail(String cmid, String email) {
        page.navigate(Settings.BASE_URL + "/mod/cloudlabs/addguidedprojectoverride.php?cmid="
                        + cmid + "&userid=2",
                new Page.NavigateOptions().setWaitUntil(WaitUntilState.DOMCONTENTLOADED).setTimeout(45_000));
        Locator users = page.locator("#id_userids");
        users.first().waitFor(new Locator.WaitForOptions()
                .setState(WaitForSelectorState.ATTACHED).setTimeout(30_000));
        Locator options = users.locator("option");
        for (int i = 0; i < options.count(); i++) {
            String text = options.nth(i).innerText().trim();
            if (!text.toLowerCase().contains(email.toLowerCase())) continue;
            String value = options.nth(i).getAttribute("value");
            users.selectOption(new String[]{value},
                    new Locator.SelectOptionOptions().setForce(true));
            return;
        }
        throw new IllegalStateException("completed Practice Project participant " + email
                + " is not offered for re-override");
    }

    public boolean addFormHasRequiredFields() {
        return page.locator("[name='allowedattempts'], #id_allowedattempts").count() > 0
                && page.locator("[name='remarks'], #id_remarks").count() > 0
                && page.locator("[id^='id_submissionfromdate']").count() > 0
                && page.locator("[id^='id_timelimit']").count() > 0;
    }

    public boolean noAttemptsOptionPresent() {
        return page.locator("#id_allowedattempts option[value='-1']").count() > 0;
    }

    public void saveOverride(String allowedAttempts, Integer timeLimitMinutes, String remarks) {
        page.locator("#id_allowedattempts, [name='allowedattempts']").first()
                .selectOption(allowedAttempts);
        if (timeLimitMinutes != null) setOptionalDurationMinutes("timelimit", timeLimitMinutes);
        page.locator("#id_remarks, [name='remarks']").first().fill(remarks);
        page.locator("#id_submitbutton, button[type='submit']:has-text('Add'), "
                + "input[type='submit']").first().click();
        page.waitForLoadState();
        page.waitForTimeout(1_000);
    }

    public boolean hasOverrideFor(String email) {
        return page.locator("body").innerText().toLowerCase().contains(email.toLowerCase());
    }

    public String overrideTextFor(String email) {
        Locator row = page.locator("table tbody tr").filter(
                new Locator.FilterOptions().setHasText(email)).first();
        return row.count() > 0 ? row.innerText() : "";
    }

    public String overrideCellText(String email, int columnIndex) {
        Locator row = page.locator("table tbody tr").filter(
                new Locator.FilterOptions().setHasText(email)).first();
        Locator cells = row.locator("td");
        return row.count() > 0 && cells.count() > columnIndex
                ? cells.nth(columnIndex).innerText().trim() : "";
    }

    public boolean editOverrideFor(String email, String allowedAttempts,
                                   Integer timeLimitMinutes, String remarks) {
        Locator row = page.locator("table tbody tr").filter(
                new Locator.FilterOptions().setHasText(email)).first();
        Locator edit = row.locator("a[href*='editguidedprojectoverride']");
        if (edit.count() == 0) return false;
        edit.first().click();
        page.waitForLoadState();
        saveOverride(allowedAttempts, timeLimitMinutes, remarks);
        return true;
    }

    public boolean deleteOverrideFor(String email) {
        Locator row = page.locator("table tbody tr").filter(
                new Locator.FilterOptions().setHasText(email)).first();
        Locator delete = row.locator(".delete-guided-override-row, a.delete-guided-override, "
                + "a[href*='action=delete']");
        if (delete.count() == 0) return false;
        delete.first().click();
        try { confirmModal(); } catch (RuntimeException ignored) {}
        for (int i = 0; i < 40; i++) {
            if (!hasOverrideFor(email)) return true;
            page.waitForTimeout(250);
        }
        return !hasOverrideFor(email);
    }

    private void setOptionalDurationMinutes(String name, int minutes) {
        Locator enable = page.locator("#id_" + name + "_enabled");
        if (enable.count() > 0 && !enable.first().isChecked()) {
            enable.first().check(new Locator.CheckOptions().setForce(true));
        }
        Locator value = page.locator("#id_" + name + "_number, [name='" + name + "[number]'], "
                + "#id_" + name + ", [name='" + name + "']").first();
        if (value.count() > 0) value.fill(String.valueOf(minutes));
        Locator unit = page.locator("#id_" + name + "_timeunit, [name='" + name + "[timeunit]'], "
                + "#id_" + name + "_unit, [name='" + name + "_unit']").first();
        if (unit.count() > 0) {
            try { unit.selectOption("60"); } catch (RuntimeException ignored) {}
        }
    }

    public Locator heading() {
        return page.locator("h2, h3, .page-header-headings");
    }
}
