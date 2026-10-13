package com.nuvepro.moodle.pages;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;

/**
 * Moodle course groups (/group/index.php, /group/group.php) + the cloudlabs groups filter dropdown
 * (#course_groups, a select2) on the Manage User Labs page (managelabs.php).
 */
public class Groups extends BasePage {
    public Groups(Page page) {
        super(page);
    }

    /** Submit the create-group form; returns true if it succeeded (left the form to the group index). */
    public boolean createGroupWithEmptyNameRejected(int courseId) {
        navigate("/group/group.php?courseid=" + courseId + "&id=0");
        page.locator("#id_name").waitFor();
        page.locator("#id_name").fill("");
        submitAndAwaitNewPage(page.locator("#id_submitbutton"));
        page.waitForTimeout(1_000);
        return page.url().contains("group.php") && page.locator("#id_name").count() > 0;
    }

    public boolean createGroupSucceeded(int courseId, String name) {
        navigate("/group/group.php?courseid=" + courseId + "&id=0");
        page.locator("#id_name").waitFor();
        page.locator("#id_name").fill(name);
        submitAndAwaitNewPage(page.locator("#id_submitbutton"));
        page.waitForTimeout(1_500);
        return !page.url().contains("group.php");   // still on group.php == validation error (e.g. duplicate name)
    }

    public void createGroup(int courseId, String name) {
        createGroupSucceeded(courseId, name);
    }

    /** Create a group and return its id (captured from the ?group=<id> redirect). */
    public String createGroupReturningId(int courseId, String name) {
        navigate("/group/group.php?courseid=" + courseId + "&id=0");
        page.locator("#id_name").waitFor();
        page.locator("#id_name").fill(name);
        submitAndAwaitNewPage(page.locator("#id_submitbutton"));
        page.waitForTimeout(1_500);
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("group=(\\d+)").matcher(page.url());
        return m.find() ? m.group(1) : null;
    }

    /** Add a user (matched by name via the dual-listbox) to a group's members. */
    public void removeUserFromGroup(String groupId, String searchTerm) {
        navigate("/group/members.php?group=" + groupId);
        page.locator("#removeselect").waitFor();
        Locator opt = page.locator("#removeselect option").filter(new Locator.FilterOptions().setHasText(searchTerm));
        if (opt.count() > 0) opt.first().click();
        Locator remove = page.locator("input[name='remove']");
        if (remove.count() > 0) remove.first().click();
        page.waitForTimeout(1_500);
    }

    public void addUserToGroup(String groupId, String searchTerm) {
        navigate("/group/members.php?group=" + groupId);
        page.locator("#addselect").waitFor();
        Locator search = page.locator("#addselect_searchtext");
        if (search.count() > 0) { search.click(); search.type(searchTerm); page.waitForTimeout(2_000); }
        Locator opt = page.locator("#addselect option:not([disabled])");
        if (opt.count() > 0) opt.first().click();
        page.locator("input[name='add']").click();
        page.waitForTimeout(1_500);
    }

    public boolean userInGroup(String groupId, String term) {
        for (int attempt = 0; attempt < 6; attempt++) {
            try {
                navigate("/group/members.php?group=" + groupId);
                page.waitForTimeout(1_000);
                return page.locator("#removeselect option")
                        .filter(new Locator.FilterOptions().setHasText(term)).count() > 0;
            } catch (RuntimeException e) {
                if (attempt == 5) throw e;
                page.waitForTimeout(500);
            }
        }
        return false;
    }

    public void deleteGroupById(int courseId, String groupId) {
        // fall back to name-based deletion is handled elsewhere; delete via the index by selecting value
        navigate("/group/index.php?id=" + courseId);
        page.waitForTimeout(800);
        Locator opt = page.locator("#groups option[value='" + groupId + "']");
        if (opt.count() == 0) return;
        opt.first().click();
        page.waitForTimeout(200);
        page.locator("#deletegroup").click();
        page.waitForTimeout(1_000);
        Locator yes = page.locator("input[name='delete'], input[value='Yes'], button:has-text('Yes'), #region-main .btn-primary");
        if (yes.count() > 0 && yes.first().isVisible()) yes.first().click();
        page.waitForTimeout(1_500);
    }

    /** How many groups whose name matches {@code name} exist in the course groups list. */
    public int groupCount(int courseId, String name) {
        navigate("/group/index.php?id=" + courseId);
        page.waitForTimeout(1_200);
        return page.locator("#groups option").filter(new Locator.FilterOptions().setHasText(name)).count();
    }

    /** Numeric id of a course group selected by its exact visible name, or null. */
    public String groupIdByName(int courseId, String name) {
        for (int attempt = 0; attempt < 6; attempt++) {
            try {
                navigate("/group/index.php?id=" + courseId);
                page.waitForTimeout(1_200);
                Locator option = page.locator("#groups option")
                        .filter(new Locator.FilterOptions().setHasText(name)).first();
                if (safeCount("#groups option") == 0 || option.count() == 0) {
                    return null;
                }
                String value = option.getAttribute("value");
                return value == null || value.isBlank() ? null : value;
            } catch (RuntimeException e) {
                if (attempt == 5) throw e;
                page.waitForTimeout(500);
            }
        }
        return null;
    }

    /** Delete every group matching {@code name} (one per pass — deletion re-renders the list). */
    public void deleteGroups(int courseId, String name) {
        for (int i = 0; i < 8; i++) {
            navigate("/group/index.php?id=" + courseId);
            page.waitForTimeout(1_000);
            Locator opt = page.locator("#groups option").filter(new Locator.FilterOptions().setHasText(name));
            if (opt.count() == 0) return;
            opt.first().click();
            page.waitForTimeout(300);
            Locator del = page.locator("#deletegroup");   // <input name=action value=deletegroup>
            if (del.count() == 0) return;
            del.first().click();
            page.waitForTimeout(1_000);
            Locator yes = page.locator("input[name='delete'], input[value='Yes'], "
                    + "button:has-text('Yes'), #region-main .btn-primary");
            if (yes.count() > 0 && yes.first().isVisible()) yes.first().click();
            page.waitForTimeout(1_500);
        }
    }

    // ---- groups filter dropdown (#course_groups, single-select select2) on Manage User Labs ----

    /** The currently selected group label in the dropdown (default "All Groups"). */
    public String selectedGroup() {
        Locator sel = page.locator("#course_groups option[selected], #course_groups option:checked");
        if (sel.count() > 0) return sel.first().innerText().trim();
        // fall back to the select2 rendered text
        Locator r = page.locator("#select2-course_groups-container, .select2-selection__rendered");
        return r.count() > 0 ? r.first().innerText().trim() : "";
    }

    /** Select a single group by name (select2 on Manage User Labs). */
    public void selectGroup(String name) {
        page.waitForTimeout(800);
        if (selectGroupViaNativeSelect(name)) {
            return;
        }
        Locator container = page.locator("#select2-course_groups-container, .select2-selection");
        if (container.count() > 0 && container.first().isVisible()) {
            container.first().click();
            page.waitForTimeout(500);
            Locator search = page.locator("input.select2-search__field, .select2-search__field");
            if (search.count() > 0) {
                search.first().fill(name);
                page.waitForTimeout(1_000);
            }
            Locator option = page.locator(".select2-results__option")
                    .filter(new Locator.FilterOptions().setHasText(name));
            if (option.count() > 0) {
                option.first().click();
                page.waitForTimeout(1_200);
                return;
            }
        }
        selectGroupViaNativeSelect(name);
    }

    public void selectGroupByValue(String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        page.waitForTimeout(800);
        page.selectOption("#course_groups",
                new com.microsoft.playwright.options.SelectOption().setValue(value));
        page.evaluate("""
                (val) => {
                  const sel = document.querySelector('#course_groups');
                  if (!sel) return;
                  sel.value = val;
                  if (window.jQuery) {
                    window.jQuery(sel).val(val).trigger('change');
                  } else {
                    sel.dispatchEvent(new Event('change', { bubbles: true }));
                  }
                }
                """, value);
        page.waitForTimeout(1_500);
    }

    private boolean selectGroupViaNativeSelect(String name) {
        Locator match = page.locator("#course_groups option").filter(new Locator.FilterOptions().setHasText(name));
        if (match.count() == 0) {
            return false;
        }
        String value = match.first().getAttribute("value");
        if (value == null || value.isBlank()) {
            return false;
        }
        page.selectOption("#course_groups",
                new com.microsoft.playwright.options.SelectOption().setValue(value));
        page.evaluate("""
                (val) => {
                  const sel = document.querySelector('#course_groups');
                  if (!sel) return;
                  sel.value = val;
                  if (window.jQuery) {
                    window.jQuery(sel).val(val).trigger('change');
                  } else {
                    sel.dispatchEvent(new Event('change', { bubbles: true }));
                  }
                }
                """, value);
        page.waitForTimeout(1_500);
        return true;
    }

    public boolean labsTableHasGroupColumn() {
        return page.locator("#np-ap-manage-lab-table th, table th")
                .filter(new Locator.FilterOptions().setHasText("Group")).count() > 0;
    }

    public boolean isGroupsDropdownSingleSelect() {
        String multiple = page.locator("#course_groups").getAttribute("multiple");
        return multiple == null;   // native <select multiple> would have the attribute
    }

    /** Open the select2, type a group name, and whether it appears in the filtered results. */
    public boolean searchGroupInDropdown(String name) {
        Locator container = page.locator("#select2-course_groups-container, .select2-selection");
        if (container.count() > 0) container.first().click();
        page.waitForTimeout(600);
        Locator search = page.locator("input.select2-search__field, .select2-search__field");
        if (search.count() > 0) {
            search.first().fill(name);
            page.waitForTimeout(1_000);
        }
        return page.locator(".select2-results__option")
                .filter(new Locator.FilterOptions().setHasText(name)).count() > 0;
    }

    public void openManageLabs(int sectionId) {
        navigate("/mod/cloudlabs/managelabs.php?sectionid=" + sectionId);
        page.waitForTimeout(2_500);
    }

    public boolean groupsDropdownPresent() {
        return page.locator("#course_groups").count() > 0;
    }

    public boolean groupsDropdownHasGroup(String name) {
        return page.locator("#course_groups option").filter(new Locator.FilterOptions().setHasText(name)).count() > 0;
    }

    public void openCourseGroupsIndex(int courseId) {
        navigate("/group/index.php?id=" + courseId);
        page.waitForTimeout(1_200);
    }

    public boolean courseGroupsPageLoaded() {
        return page.locator("#groups, #groupeditform, form[action*='group']").count() > 0
                && page.url().contains("group");
    }

    public boolean groupingsTabOrLinkPresent() {
        return page.locator("a[href*='groupings'], .nav-link:has-text('Groupings')").count() > 0;
    }

    public boolean autoCreateGroupsControlPresent() {
        return page.locator("a[href*='autogroup'], input[value='autogroup'], button:has-text('Auto-create groups')")
                .count() > 0;
    }

    public boolean importGroupsControlPresent() {
        return page.locator("a[href*='import'], input[value='importgroups'], button:has-text('Import')")
                .count() > 0;
    }

    public void openCourseEnrolmentMethods(int courseId) {
        navigate("/enrol/instances.php?id=" + courseId);
        page.waitForTimeout(1_500);
    }

    public boolean cohortSyncEnrolmentOffered() {
        String body = page.locator("body").innerText().toLowerCase();
        return body.contains("cohort sync") || body.contains("cohort_sync")
                || page.locator("a:has-text('Cohort sync'), button:has-text('Cohort sync')").count() > 0;
    }
}
