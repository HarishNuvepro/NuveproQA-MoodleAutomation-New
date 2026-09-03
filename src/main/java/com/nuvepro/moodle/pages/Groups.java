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
    public boolean createGroupSucceeded(int courseId, String name) {
        navigate("/group/group.php?courseid=" + courseId + "&id=0");
        page.locator("#id_name").waitFor();
        page.locator("#id_name").fill(name);
        page.locator("#id_submitbutton").click();
        page.waitForLoadState(com.microsoft.playwright.options.LoadState.DOMCONTENTLOADED);
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
        page.locator("#id_submitbutton").click();
        page.waitForLoadState(com.microsoft.playwright.options.LoadState.DOMCONTENTLOADED);
        page.waitForTimeout(1_500);
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("group=(\\d+)").matcher(page.url());
        return m.find() ? m.group(1) : null;
    }

    /** Add a user (matched by name via the dual-listbox) to a group's members. */
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
        navigate("/group/members.php?group=" + groupId);
        page.waitForTimeout(1_000);
        return page.locator("#removeselect option").filter(new Locator.FilterOptions().setHasText(term)).count() > 0;
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
        navigate("/group/index.php?id=" + courseId);
        page.waitForTimeout(1_200);
        Locator option = page.locator("#groups option")
                .filter(new Locator.FilterOptions().setHasText(name)).first();
        if (option.count() == 0) return null;
        String value = option.getAttribute("value");
        return value == null || value.isBlank() ? null : value;
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

    /** Select a single group by name via the underlying select (select2 syncs on change). */
    public void selectGroup(String name) {
        page.selectOption("#course_groups", new com.microsoft.playwright.options.SelectOption().setLabel(name));
        page.waitForTimeout(1_000);
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
}
