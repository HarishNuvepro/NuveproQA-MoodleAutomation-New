package com.nuvepro.moodle.pages;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Custom role admin: create (2-step add form with an archetype preset), find, delete.
 * /admin/roles/define.php add flow — step 1 picks a preset (resettype), Continue -> step 2 has
 * #shortname / #name / context checkboxes (contextlevel50 = course) / "Create this role".
 */
public class CustomRole extends BasePage {
    public CustomRole(Page page) {
        super(page);
    }

    /** Create a role prefilled from an archetype preset (e.g. "editingteacher"); returns its roleid. */
    public String createRole(String presetArchetype, String shortname, String name) {
        navigate("/admin/roles/define.php?action=add");
        page.locator("#id_resettype").waitFor();
        page.locator("#id_resettype").selectOption(presetArchetype);
        submitAndAwaitNewPage(page.locator("#id_submitbutton"));  // Continue
        page.locator("#shortname").waitFor();
        page.locator("#shortname").fill(shortname);
        page.locator("#name").fill(name);
        Locator courseCtx = page.locator("input[type=checkbox][name='contextlevel50']");
        if (courseCtx.count() > 0 && !courseCtx.first().isChecked()) courseCtx.first().check();
        submitAndAwaitNewPage(page.locator("input[value='Create this role']").first());
        page.waitForTimeout(2_000);
        Matcher m = Pattern.compile("roleid=(\\d+)").matcher(page.url());
        if (m.find()) return m.group(1);
        return roleIdByName(name);
    }

    /** Find a role's id from the manage page by its display name. */
    public String roleIdByName(String name) {
        navigate("/admin/roles/manage.php");
        page.waitForTimeout(1_500);
        Locator link = page.locator("a[href*='roleid=']").filter(new Locator.FilterOptions().setHasText(name));
        for (int i = 0; i < Math.min(link.count(), 5); i++) {
            String href = link.nth(i).getAttribute("href");
            Matcher m = Pattern.compile("roleid=(\\d+)").matcher(href == null ? "" : href);
            if (m.find()) return m.group(1);
        }
        return null;
    }

    public boolean roleListed(String name) {
        navigate("/admin/roles/manage.php");
        page.waitForTimeout(1_500);
        return page.locator("table").filter(new Locator.FilterOptions().setHasText(name)).count() > 0;
    }

    public void deleteRole(String roleId) {
        if (roleId == null) return;
        navigate("/admin/roles/define.php?action=delete&roleid=" + roleId);
        page.waitForTimeout(1_500);
        Locator confirm = page.locator("input[value='Yes'], input[value='Delete'], "
                + "button:has-text('Yes'), button:has-text('Delete'), "
                + "#region-main button[type='submit'], #region-main .btn-primary");
        for (int i = 0; i < confirm.count(); i++) {
            Locator btn = confirm.nth(i);
            if (btn.isVisible()) {
                btn.click();
                break;
            }
        }
        page.waitForLoadState(com.microsoft.playwright.options.LoadState.DOMCONTENTLOADED);
        page.waitForTimeout(2_000);
    }

    /** Whether the role appears in the "Enrol users" role dropdown for a course. */
    public boolean inEnrolDropdown(int courseId, String name) {
        navigate("/user/index.php?id=" + courseId);
        page.waitForTimeout(3_000);
        Locator all = page.locator("a:has-text('Enrol users'), button:has-text('Enrol users')");
        Locator visible = null;
        for (int i = 0; i < all.count(); i++) {
            if (all.nth(i).isVisible()) { visible = all.nth(i); break; }
        }
        if (visible == null) return false;
        visible.click();
        page.waitForTimeout(3_500);   // modal + its "Assign role" select populate via JS
        // native select option OR a form-autocomplete option carrying the role name
        return page.locator(".modal.show select option, [role='dialog'] select option")
                .filter(new Locator.FilterOptions().setHasText(name)).count() > 0
                || page.locator(".modal.show [role='option'], [role='dialog'] [role='option']")
                .filter(new Locator.FilterOptions().setHasText(name)).count() > 0;
    }
}
