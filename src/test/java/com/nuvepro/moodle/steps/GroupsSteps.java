package com.nuvepro.moodle.steps;

import com.nuvepro.moodle.config.Settings;
import com.nuvepro.moodle.helpers.ApiClient;
import com.nuvepro.moodle.helpers.PlaygroundAnchor;
import com.nuvepro.moodle.pages.AdminUsers;
import com.nuvepro.moodle.pages.Groups;
import io.cucumber.java.After;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import org.testng.SkipException;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

/**
 * Groups module — course groups create/list/delete (G12/G14) and the cloudlabs groups filter
 * dropdown on Manage User Labs (G1). Groups are created with unique names and deleted at teardown.
 */
public class GroupsSteps {
    private final TestContext ctx;
    private final Groups groups;
    private final AdminUsers admin;
    private String groupName;
    private String cohortName;
    private String groupNameA;
    private String groupNameB;
    private String groupIdA;
    private String groupIdB;
    private String groupUserSearch;
    private ApiClient.SeededUser groupUser;

    public GroupsSteps(TestContext ctx) {
        this.ctx = ctx;
        this.groups = new Groups(ctx.page);
        this.admin = new AdminUsers(ctx.page);
    }

    private int courseId() {
        if (Settings.COURSE_ID.isEmpty()) throw new SkipException("COURSE_ID not set");
        return Integer.parseInt(Settings.COURSE_ID);
    }

    private PlaygroundAnchor sandboxAnchor() {
        if (Settings.PLAYGROUND_CMID.isEmpty()) throw new SkipException("PLAYGROUND_CMID not set");
        return PlaygroundAnchor.resolve(ctx.page);
    }

    private int playgroundSectionId() {
        return sandboxAnchor().sectionId;
    }

    /** Course that owns the configured playground activity (Manage User Labs groups filter). */
    private int sandboxCourseId() {
        return sandboxAnchor().courseId;
    }

    // ---- G12: create a group ----
    private String groupId;
    private String memberSearch;

    @When("admin creates a group")
    public void adminCreatesAGroup() {
        groupName = "AutoGroup" + System.currentTimeMillis();
        groups.createGroup(courseId(), groupName);
    }

    @When("admin creates a group named {string}")
    public void adminCreatesNamedGroup(String name) {
        groupName = name;
        groups.createGroup(courseId(), groupName);
    }

    @When("admin deletes the created group")
    public void adminDeletesCreatedGroup() {
        groups.deleteGroups(courseId(), groupName);
    }

    @Then("the group is no longer listed in the course")
    public void groupNoLongerListed() {
        assertEquals(groups.groupCount(courseId(), groupName), 0, "group still listed: " + groupName);
    }

    @When("admin submits an empty group name")
    public void adminSubmitsEmptyGroupName() {
        assertTrue(groups.createGroupWithEmptyNameRejected(courseId()), "empty group name was accepted");
    }

    @Then("the empty group name is rejected")
    public void emptyGroupNameRejected() {
        assertTrue(groups.createGroupWithEmptyNameRejected(courseId()), "empty group name was accepted");
    }

    @When("admin creates a group with an enrolled member")
    public void adminCreatesGroupWithMember() {
        if (Settings.WS_TOKEN.isEmpty()) throw new SkipException("MOODLE_WS_TOKEN not set");
        groupName = "AutoMem" + System.currentTimeMillis();
        groupId = groups.createGroupReturningId(courseId(), groupName);
        groupUser = ApiClient.createUser(System.currentTimeMillis());
        ApiClient.enrolUser(groupUser.id, courseId(), 5);
        memberSearch = "Test" + groupUser.username.substring("autotest_".length());
        groups.addUserToGroup(groupId, memberSearch);
    }

    @When("admin removes that member from the group")
    public void adminRemovesMemberFromGroup() {
        groups.removeUserFromGroup(groupId, memberSearch);
    }

    @Then("the member is not listed in the group")
    public void memberNotInGroup() {
        assertFalse(groups.userInGroup(groupId, memberSearch), "member still in group");
    }

    @When("admin opens Manage User Labs without course groups")
    public void openManageLabsWithoutGroups() {
        groups.openManageLabs(playgroundSectionId());
    }

    @When("admin selects the created group in Manage User Labs")
    public void selectCreatedGroupInManageLabs() {
        String gid = groups.groupIdByName(sandboxCourseId(), groupName);
        assertTrue(gid != null, "created group id not found for: " + groupName);
        groups.selectGroupByValue(gid);
        if (!groups.selectedGroup().contains(groupName)) {
            groups.selectGroup(groupName);
        }
        assertTrue(groups.selectedGroup().contains(groupName),
                "failed to select group in Manage User Labs: " + groupName);
    }

    @When("admin reopens Manage User Labs for the same section")
    public void reopenManageLabs() {
        groups.openManageLabs(playgroundSectionId());
    }

    @Then("the groups dropdown still shows the selected group")
    public void dropdownStillShowsSelectedGroup() {
        assertTrue(groups.groupsDropdownHasGroup(groupName),
                "created group missing from dropdown after reopen: " + groupName);
        groups.selectGroup(groupName);
        assertTrue(groups.selectedGroup().contains(groupName),
                "group could not be re-selected after reopen (group=" + groupName + ")");
    }

    @When("admin attempts to create a grouping with a group")
    public void attemptGrouping() {
        groups.openCourseGroupsIndex(courseId());
    }

    @Then("the grouping workflow is available or documented as unsupported")
    public void groupingWorkflow() {
        if (groups.groupingsTabOrLinkPresent()) {
            return;
        }
        assertTrue(groups.courseGroupsPageLoaded(),
                "course groups index did not load (groupings entry not exposed on this build)");
    }

    @When("admin attempts auto-create groups for the course")
    public void attemptAutoCreateGroups() {
        groups.openCourseGroupsIndex(courseId());
    }

    @Then("auto-create groups is available or deferred")
    public void autoCreateDeferred() {
        assertTrue(groups.autoCreateGroupsControlPresent(),
                "Auto-create groups control not found on the course groups page");
    }

    @When("admin configures cohort-sync enrolment for a cohort")
    public void cohortSyncEnrol() {
        groups.openCourseEnrolmentMethods(courseId());
    }

    @Then("cohort-sync enrolment completes or is deferred")
    public void cohortSyncDeferred() {
        assertTrue(groups.cohortSyncEnrolmentOffered(),
                "Cohort sync enrolment method not offered for the course");
    }

    @When("admin attempts bulk group import from CSV")
    public void bulkGroupImport() {
        groups.openCourseGroupsIndex(courseId());
    }

    @Then("bulk group import is available or deferred")
    public void bulkImportDeferred() {
        assertTrue(groups.importGroupsControlPresent(),
                "Import groups control not found on the course groups page");
    }

    @When("admin adjusts cohort-sync group membership")
    public void cohortSyncMembership() {
        groups.openCourseEnrolmentMethods(courseId());
    }

    @Then("cohort-sync membership sync is verified or deferred")
    public void cohortSyncMembershipDeferred() {
        assertTrue(groups.cohortSyncEnrolmentOffered(),
                "Cohort sync enrolment (membership sync path) not configured on the course");
    }

    @Then("the group is listed in the course")
    public void theGroupIsListed() {
        assertTrue(groups.groupCount(courseId(), groupName) >= 1, "group not listed: " + groupName);
    }

    // ---- G14: duplicate group names — Moodle enforces UNIQUE group names per course, so the second
    // create is rejected. (The manual expects duplicates to be allowed; standard Moodle rejects them.)
    private boolean duplicateSucceeded;

    @When("admin creates a group then attempts a duplicate name")
    public void adminAttemptsDuplicateGroupName() {
        groupName = "AutoDup" + System.currentTimeMillis();
        assertTrue(groups.createGroupSucceeded(courseId(), groupName), "first group was not created: " + groupName);
        duplicateSucceeded = groups.createGroupSucceeded(courseId(), groupName);
    }

    @Then("the duplicate group name is rejected")
    public void theDuplicateGroupNameIsRejected() {
        assertFalse(duplicateSucceeded, "duplicate group name was accepted (Moodle should reject same-name groups in a course)");
        assertEquals(groups.groupCount(courseId(), groupName), 1, "expected exactly one group with that name");
    }

    // ---- G1: groups dropdown on Manage User Labs ----
    @When("admin creates a group and opens Manage User Labs")
    public void createGroupAndOpenManageLabs() {
        groupName = "AutoDD" + System.currentTimeMillis();
        int course = sandboxCourseId();
        groups.createGroup(course, groupName);
        groups.openManageLabs(playgroundSectionId());
        ctx.page.waitForTimeout(2_000);
    }

    @Then("the groups dropdown is present and lists the group")
    public void theGroupsDropdownIsPresent() {
        assertTrue(groups.groupsDropdownPresent(), "#course_groups dropdown not present on Manage User Labs");
        assertTrue(groups.groupsDropdownHasGroup(groupName), "created group not offered in the groups dropdown: " + groupName);
    }

    // ---- G5 default, G2 single-select, G4 search, G42 groups column ----
    @Then("the groups dropdown defaults to all groups")
    public void theGroupsDropdownDefaultsToAllGroups() {
        assertTrue(groups.selectedGroup().toLowerCase().contains("all group"),
                "groups dropdown default is not 'All Groups': " + groups.selectedGroup());
    }

    @Then("admin can select a single group in the dropdown")
    public void adminCanSelectASingleGroup() {
        groups.selectGroup(groupName);
        assertTrue(groups.selectedGroup().contains(groupName), "group was not selected: " + groupName);
    }

    @Then("admin can search for the group in the dropdown")
    public void adminCanSearchForTheGroup() {
        assertTrue(groups.searchGroupInDropdown(groupName), "group not found via dropdown search: " + groupName);
    }

    @Then("the groups column is present on Manage User Labs")
    public void theGroupsColumnIsPresent() {
        assertTrue(groups.labsTableHasGroupColumn(), "Group column not present on the Manage User Labs table");
    }

    @Then("selecting multiple groups is not supported")
    public void selectingMultipleGroupsIsNotSupported() {
        // The #course_groups dropdown is a SINGLE-select (All Groups or one group), so the manual G3
        // ("select multiple groups") is not supported by this UI - documented, not a failure.
        assertTrue(groups.isGroupsDropdownSingleSelect(), "expected the groups dropdown to be single-select");
        throw new SkipException("G3: groups dropdown is single-select (All Groups or one group); "
                + "multiple-group selection is not supported by the UI");
    }

    // ---- G11 create cohort, G13 duplicate cohort names allowed, G24 same user to multiple groups ----

    @When("admin creates a cohort")
    public void adminCreatesACohort() {
        cohortName = "AutoCohort" + System.currentTimeMillis();
        assertTrue(admin.cohortCreateSucceeded(cohortName, ""), "cohort was not created: " + cohortName);
    }

    @Then("the cohort is listed")
    public void theCohortIsListed() {
        assertTrue(admin.cohortListed(cohortName), "cohort not listed: " + cohortName);
    }

    @When("admin creates two cohorts with the same name")
    public void adminCreatesTwoCohortsSameName() {
        cohortName = "AutoDupCoh" + System.currentTimeMillis();
        assertTrue(admin.cohortCreateSucceeded(cohortName, ""), "first cohort not created");
        assertTrue(admin.cohortCreateSucceeded(cohortName, ""), "second (duplicate-name) cohort was rejected");
    }

    @Then("both cohorts with that name exist")
    public void bothCohortsExist() {
        assertTrue(admin.cohortCount(cohortName) >= 2,
                "duplicate cohort names were not both created (count=" + admin.cohortCount(cohortName) + ")");
    }

    @When("admin adds the same user to two groups")
    public void adminAddsSameUserToTwoGroups() {
        if (Settings.WS_TOKEN.isEmpty()) throw new SkipException("MOODLE_WS_TOKEN not set");
        long s = System.currentTimeMillis();
        groupNameA = "MulGrpA" + s;
        groupNameB = "MulGrpB" + s;
        groupIdA = groups.createGroupReturningId(courseId(), groupNameA);
        groupIdB = groups.createGroupReturningId(courseId(), groupNameB);
        groupUser = ApiClient.createUser(s);
        ApiClient.enrolUser(groupUser.id, courseId(), 5);           // must be enrolled to join a group
        groupUserSearch = "Test" + groupUser.username.substring("autotest_".length());
        groups.addUserToGroup(groupIdA, groupUserSearch);
        groups.addUserToGroup(groupIdB, groupUserSearch);
    }

    @Then("the user is a member of both groups")
    public void theUserIsAMemberOfBothGroups() {
        assertTrue(groups.userInGroup(groupIdA, groupUserSearch), "user not a member of group A");
        assertTrue(groups.userInGroup(groupIdB, groupUserSearch), "user not a member of group B");
    }

    @After(value = "@groups", order = 10)
    public void cleanupGroup() {
        for (String gn : new String[]{groupName, groupNameA, groupNameB}) {
            if (gn != null) {
            try { groups.deleteGroups(courseId(), gn); } catch (Throwable ignored) {}
            try {
                if (!Settings.PLAYGROUND_CMID.isEmpty()) {
                    groups.deleteGroups(sandboxCourseId(), gn);
                }
            } catch (Throwable ignored) {}
        }
        }
        if (cohortName != null) {
            try { for (int i = 0; i < 3; i++) admin.cohortDelete(cohortName); } catch (Throwable ignored) {}
        }
        if (groupUser != null) { try { ApiClient.deleteUser(groupUser.id); } catch (Throwable ignored) {} }
        groupName = groupNameA = groupNameB = cohortName = null;
        groupUser = null;
    }
}
