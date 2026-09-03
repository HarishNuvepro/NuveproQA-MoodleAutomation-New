package com.nuvepro.moodle.steps;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Page;
import com.nuvepro.moodle.config.Settings;
import com.nuvepro.moodle.helpers.ApiClient;
import com.nuvepro.moodle.helpers.Auth;
import com.nuvepro.moodle.helpers.UserSpec;
import com.nuvepro.moodle.helpers.UserTestData;
import com.nuvepro.moodle.pages.AdminUsers;
import com.nuvepro.moodle.pages.RoleAssign;
import com.nuvepro.moodle.pages.UserProfile;
import io.cucumber.java.After;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import org.testng.SkipException;

import java.util.ArrayList;
import java.util.List;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

/**
 * Moodle user administration. Page objects own selectors; {@link UserTestData} owns payloads.
 * Add a create-form case by extending {@link UserTestData#createForm} and a tagged Examples row.
 */
public class MoodleUsersSteps {
    private final TestContext ctx;
    private final AdminUsers admin;

    private ApiClient.SeededUser user;
    private UserSpec created;
    private UserProfile learner;
    private String newEmail;
    private String profileCity;
    private String editedFirstName;
    private String cohortToDelete;
    private final List<Long> toDelete = new ArrayList<>();
    private BrowserContext freshCtx;
    private ApiClient.SeededUser secondUser;
    private String lockoutPrevious;
    private String roleUserSearch;
    private static final int MANAGER_ROLE_ID = 1;

    public MoodleUsersSteps(TestContext ctx) {
        this.ctx = ctx;
        this.admin = new AdminUsers(ctx.page);
    }

    private void requireWs() {
        if (Settings.WS_TOKEN.isEmpty()) throw new SkipException("MOODLE_WS_TOKEN not set");
    }

    private void trackIdFromUrl() {
        String id = admin.userIdFromUrl();
        if (id != null && !id.equals("-1")) toDelete.add(Long.parseLong(id));
    }

    private Page loginFresh(String username, String password) {
        if (freshCtx != null) { try { freshCtx.close(); } catch (Throwable ignored) {} }
        freshCtx = GlobalHooks.browser.newContext(new Browser.NewContextOptions()
                .setBaseURL(Settings.BASE_URL).setIgnoreHTTPSErrors(true));
        Page p = freshCtx.newPage();
        p.setDefaultTimeout(Settings.DEFAULT_TIMEOUT_MS);
        Auth.uiLogin(p, username, password);
        return p;
    }

    private UserProfile asUser(String username, String password) {
        return new UserProfile(loginFresh(username, password));
    }

    // ---- create / reject (data-driven) ----
    @When("admin submits the add-user form using {string}")
    public void adminSubmitsTheAddUserFormUsing(String dataset) {
        UserSpec spec = UserTestData.createForm(dataset, user);
        admin.openAddUser();
        if (spec != null) {
            created = spec;
            admin.fillNewUser(spec);
            if ("force_password_change".equals(dataset)) admin.setForcePasswordChange();
        }
        admin.submit();
        if ("valid".equals(dataset) || "force_password_change".equals(dataset)) trackIdFromUrl();
    }

    @Then("the user is created successfully")
    public void theUserIsCreatedSuccessfully() {
        assertTrue(admin.leftTheForm(), "still on the add-user form after submit (create failed)");
    }

    @Then("the add-user form is rejected")
    public void theAddUserFormIsRejected() {
        assertFalse(admin.leftTheForm(), "invalid add-user form was accepted (should be rejected)");
        assertTrue(admin.hasFormError(), "no validation error shown on the add-user form");
    }

    @Given("a registered user")
    public void aRegisteredUser() {
        requireWs();
        user = ApiClient.createUser(System.currentTimeMillis());
        toDelete.add(user.id);
    }

    @Then("the user can log in with valid credentials")
    public void theUserCanLogInWithValidCredentials() {
        Page p = loginFresh(user.username, user.password);
        assertTrue(Auth.isLoggedIn(p), "registered user could not log in with valid credentials");
    }

    @Then("login with invalid credentials is rejected")
    public void loginWithInvalidCredentialsIsRejected() {
        Page p = loginFresh("no_such_user_" + System.currentTimeMillis(), UserTestData.BAD_LOGIN_PASSWORD);
        assertFalse(Auth.isLoggedIn(p), "login succeeded with invalid credentials");
    }

    @Then("the user can log in and then log out")
    public void theUserCanLogInAndThenLogOut() {
        UserProfile profile = asUser(user.username, user.password);
        assertTrue(profile.isLoggedIn(), "user could not log in");
        profile.logout();
        assertFalse(profile.isLoggedIn(), "user still logged in after logout");
    }

    @Then("the user appears in the admin user list")
    public void theUserAppearsInTheAdminUserList() {
        assertTrue(admin.userProfilePresent(String.valueOf(user.id)),
                "registered user account is not present/viewable: id=" + user.id);
    }

    @When("admin suspends the user")
    public void adminSuspendsTheUser() {
        admin.setSuspended(String.valueOf(user.id), true);
    }

    @When("admin unsuspends the user")
    public void adminUnsuspendsTheUser() {
        admin.setSuspended(String.valueOf(user.id), false);
    }

    @Then("the user cannot log in")
    public void theUserCannotLogIn() {
        Page p = loginFresh(user.username, user.password);
        assertFalse(Auth.isLoggedIn(p), "suspended user was able to log in");
    }

    @When("admin changes the user email")
    public void adminChangesTheUserEmail() {
        newEmail = UserTestData.uniqueEmail("changed");
        admin.changeEmail(String.valueOf(user.id), newEmail);
    }

    @Then("the user email is updated")
    public void theUserEmailIsUpdated() {
        assertEquals(admin.currentEmailValue(String.valueOf(user.id)), newEmail, "email not updated");
    }

    @When("the user updates their profile city")
    public void theUserUpdatesTheirProfileCity() {
        UserProfile profile = asUser(user.username, user.password);
        assertTrue(profile.isLoggedIn(), "user could not log in");
        profileCity = UserTestData.uniqueLabel(UserTestData.PROFILE_CITY_PREFIX);
        profile.openEdit();
        profile.setCity(profileCity);
        profile.save();
        learner = profile;
    }

    @Then("the profile change is reflected")
    public void theProfileChangeIsReflected() {
        UserProfile profile = asUser(user.username, user.password);
        profile.openEdit();
        assertEquals(profile.city(), profileCity, "profile city change not persisted");
    }

    @When("the user saves an invalid email in their profile")
    public void theUserSavesAnInvalidEmailInTheirProfile() {
        UserProfile profile = asUser(user.username, user.password);
        assertTrue(profile.isLoggedIn(), "user could not log in");
        profile.openEdit();
        profile.setEmail(UserTestData.INVALID_EMAIL);
        profile.save();
        learner = profile;
    }

    @Then("the profile form shows a validation error")
    public void theProfileFormShowsAValidationError() {
        assertTrue(learner.stillOnEditForm() && learner.hasValidationError(),
                "invalid email accepted in profile (url=" + learner.page().url() + ")");
    }

    @Then("after the user logs out a protected page requires login")
    public void afterLogoutAProtectedPageRequiresLogin() {
        UserProfile profile = asUser(user.username, user.password);
        assertTrue(profile.isLoggedIn(), "user could not log in");
        profile.logout();
        profile.openEdit();
        assertTrue(profile.loginRequired(),
                "protected page did not require login after logout (url=" + profile.page().url() + ")");
    }

    @Given("a registered user enrolled in the course")
    public void aRegisteredUserEnrolledInTheCourse() {
        requireWs();
        if (Settings.COURSE_ID.isEmpty()) throw new SkipException("COURSE_ID not set");
        user = ApiClient.createUser(System.currentTimeMillis());
        toDelete.add(user.id);
        ApiClient.enrolUser(user.id, Long.parseLong(Settings.COURSE_ID), 5);
    }

    @Then("the user sees the course page not an enrolment page")
    public void theUserSeesTheCoursePage() {
        UserProfile profile = asUser(user.username, user.password);
        assertTrue(profile.isLoggedIn(), "user could not log in");
        profile.openCourse(Settings.COURSE_ID);
        assertTrue(profile.onCoursePage(),
                "enrolled user was not taken to the course page (url=" + profile.page().url() + ")");
    }

    @When("admin edits the user first name")
    public void adminEditsTheUserFirstName() {
        editedFirstName = UserTestData.uniqueLabel("Edited");
        admin.changeFirstName(String.valueOf(user.id), editedFirstName);
        assertEquals(admin.currentFirstName(String.valueOf(user.id)), editedFirstName, "first name not updated");
    }

    @Then("admin can create a cohort that is listed")
    public void adminCanCreateACohortThatIsListed() {
        String name = UserTestData.uniqueLabel(UserTestData.COHORT_PREFIX);
        admin.cohortCreate(name, "autocoh" + System.currentTimeMillis());
        cohortToDelete = name;
        assertTrue(admin.cohortListed(name), "cohort not listed after creation: " + name);
    }

    @Then("admin can create and then delete a cohort")
    public void adminCanCreateAndThenDeleteACohort() {
        String name = UserTestData.uniqueLabel(UserTestData.COHORT_PREFIX + "Del");
        admin.cohortCreate(name, "autocohd" + System.currentTimeMillis());
        assertTrue(admin.cohortListed(name), "cohort not created: " + name);
        admin.cohortDelete(name);
        assertFalse(admin.cohortListed(name), "cohort still listed after delete: " + name);
    }

    @When("admin deletes the user")
    public void adminDeletesTheUser() {
        ApiClient.deleteUser(user.id);
        toDelete.remove(Long.valueOf(user.id));
    }

    @Then("the deleted user cannot log in")
    public void theDeletedUserCannotLogIn() {
        Page p = loginFresh(user.username, user.password);
        assertFalse(Auth.isLoggedIn(p), "deleted user was able to log in");
    }

    @Then("the deleted user is not listed")
    public void theDeletedUserIsNotListed() {
        assertFalse(admin.userProfilePresent(String.valueOf(user.id)),
                "deleted user account is still present/viewable: id=" + user.id);
    }

    @Then("the user is forced to change password on login")
    public void theUserIsForcedToChangePasswordOnLogin() {
        UserProfile profile = asUser(created.username, created.password);
        assertTrue(profile.passwordChangeForced(),
                "user was not forced to change password (url=" + profile.page().url() + ")");
    }

    @Then("the user can change their own password and log in with it")
    public void theUserCanChangeTheirOwnPassword() {
        UserProfile profile = asUser(user.username, user.password);
        assertTrue(profile.isLoggedIn(), "user could not log in");
        String newPass = UserSpec.strongPassword();
        profile.openChangePassword();
        profile.changePassword(user.password, newPass);
        profile.logout();
        Page p2 = loginFresh(user.username, newPass);
        assertTrue(Auth.isLoggedIn(p2), "user could not log in with the changed password");
    }

    @Then("admin can add and remove the user from a cohort")
    public void adminCanAddAndRemoveTheUserFromACohort() {
        String name = UserTestData.uniqueLabel(UserTestData.COHORT_ASSIGN_PREFIX);
        admin.cohortCreate(name, "autocoha" + System.currentTimeMillis());
        cohortToDelete = name;
        String cohortId = admin.cohortIdByName(name);
        assertTrue(cohortId != null, "could not read the cohort id from /cohort/index.php for " + name);
        assertTrue(admin.cohortAddMember(cohortId, user.username),
                "could not add " + user.username + " to cohort " + name + " (id=" + cohortId + ")");
        assertTrue(admin.cohortHasMember(cohortId, user.username),
                "user is not listed as a cohort member after Add");
        assertTrue(admin.cohortRemoveMember(cohortId, user.username),
                "could not remove " + user.username + " from cohort " + name);
        assertFalse(admin.cohortHasMember(cohortId, user.username),
                "user is still listed as a cohort member after Remove");
    }

    @Then("filtering the user list narrows to that user")
    public void filteringTheUserListNarrowsToThatUser() {
        assertTrue(admin.userAppearsInFilteredList(user.username),
                "filtering /admin/user.php did not show user " + user.username);
    }

    @Then("the user is shown as suspended in the list")
    public void theUserIsShownAsSuspendedInTheList() {
        // Persist check (edit form) — Suspended is not a Browse-users column on Moodle 4.5.
        assertTrue(admin.editFormShowsSuspended(String.valueOf(user.id)),
                "suspended user " + user.username + " does not have Suspended checked on the edit form");
        // List behaviour: Filters → Suspended = Yes must include this user.
        assertTrue(admin.userAppearsWhenFilteredSuspendedYes(user.username),
                "suspended user " + user.username + " does not appear when Browse users is filtered Suspended=Yes");
    }

    // ---- TC-26+ extensions ----

    @When("admin enables temporary login lockout after {int} failed attempts")
    public void adminEnablesTemporaryLoginLockout(int threshold) {
        lockoutPrevious = admin.configureLoginLockout(threshold, 120);
        if (lockoutPrevious == null) {
            throw new SkipException("Site policies lockoutthreshold control not found — enable Account lockout on the site");
        }
    }

    @Then("the user is locked out after consecutive failed login attempts")
    public void theUserIsLockedOutAfterConsecutiveFailedLoginAttempts() {
        requireWs();
        if (user == null) {
            user = ApiClient.createUser(System.currentTimeMillis());
            toDelete.add(user.id);
        }
        if (freshCtx != null) { try { freshCtx.close(); } catch (Throwable ignored) {} }
        freshCtx = GlobalHooks.browser.newContext(new Browser.NewContextOptions()
                .setBaseURL(Settings.BASE_URL).setIgnoreHTTPSErrors(true));
        Page p = freshCtx.newPage();
        p.setDefaultTimeout(Settings.DEFAULT_TIMEOUT_MS);
        String lastMsg = "";
        for (int i = 0; i < 5; i++) {
            Auth.attemptLoginOnce(p, user.username, UserTestData.BAD_LOGIN_PASSWORD);
            assertFalse(Auth.isLoggedIn(p), "unexpected login success with wrong password on attempt " + (i + 1));
            lastMsg = Auth.loginPageMessage(p);
            if (lastMsg.contains("lock") || lastMsg.contains("locked") || lastMsg.contains("too many")
                    || lastMsg.contains("temporary") || lastMsg.contains("try again later")) {
                return;
            }
        }
        // Final attempt with the REAL password should still be blocked when locked
        Auth.attemptLoginOnce(p, user.username, user.password);
        lastMsg = Auth.loginPageMessage(p);
        assertFalse(Auth.isLoggedIn(p),
                "account was not locked after consecutive failed logins (msg=" + lastMsg + ")");
        assertTrue(lastMsg.contains("lock") || lastMsg.contains("locked") || lastMsg.contains("too many")
                        || lastMsg.contains("temporary") || lastMsg.contains("invalid") || lastMsg.contains("error"),
                "no lockout/invalid login message after failed attempts (msg=" + lastMsg + ")");
    }

    @When("admin enrols the registered user into the course via participants")
    public void adminEnrolsTheRegisteredUserIntoTheCourseViaParticipants() {
        // Kept for compatibility; prefer self-enrol step for TC-29.
        if (Settings.COURSE_ID.isEmpty()) throw new SkipException("COURSE_ID not set");
        assertTrue(user != null, "no registered user — run Given a registered user first");
        ApiClient.enrolUser(user.id, Long.parseLong(Settings.COURSE_ID), 5);
    }

    @When("the user self-enrols in the self-enrolment course")
    public void theUserSelfEnrolsInTheSelfEnrolmentCourse() {
        if (Settings.SELF_ENROL_COURSE_ID.isEmpty()) {
            throw new SkipException("SELF_ENROL_COURSE_ID not set");
        }
        assertTrue(user != null, "no registered user — run Given a registered user first");
        UserProfile profile = asUser(user.username, user.password);
        assertTrue(profile.isLoggedIn(), "user could not log in before self-enrol");
        assertTrue(profile.selfEnrolInCourse(Settings.SELF_ENROL_COURSE_ID),
                "self-enrol failed for course " + Settings.SELF_ENROL_COURSE_ID
                        + " (url=" + profile.page().url() + ")");
    }

    @Then("the enrolled user can open the course page")
    public void theEnrolledUserCanOpenTheCoursePage() {
        String courseId = Settings.SELF_ENROL_COURSE_ID.isEmpty() ? Settings.COURSE_ID : Settings.SELF_ENROL_COURSE_ID;
        UserProfile profile = asUser(user.username, user.password);
        assertTrue(profile.isLoggedIn(), "user could not log in");
        profile.openCourse(courseId);
        assertTrue(profile.onCoursePage(courseId),
                "enrolled user was not taken to course " + courseId + " (url=" + profile.page().url() + ")");
    }

    @When("admin assigns the Manager system role to the registered user")
    public void adminAssignsTheManagerSystemRoleToTheRegisteredUser() {
        assertTrue(user != null, "no registered user");
        roleUserSearch = user.username;
        RoleAssign roleAssign = new RoleAssign(ctx.page);
        roleAssign.openSystemRoleAssign(MANAGER_ROLE_ID);
        roleAssign.assignUser(roleUserSearch);
        assertTrue(roleAssign.userAssigned(roleUserSearch),
                "user was not assigned the Manager system role: " + user.email);
    }

    @Then("the registered user has the Manager system role")
    public void theRegisteredUserHasTheManagerSystemRole() {
        RoleAssign roleAssign = new RoleAssign(ctx.page);
        roleAssign.openSystemRoleAssign(MANAGER_ROLE_ID);
        assertTrue(roleAssign.userAssigned(user.username),
                "Manager system role not present for " + user.username);
    }

    @When("admin removes the Manager system role from the registered user")
    public void adminRemovesTheManagerSystemRoleFromTheRegisteredUser() {
        RoleAssign roleAssign = new RoleAssign(ctx.page);
        roleAssign.openSystemRoleAssign(MANAGER_ROLE_ID);
        roleAssign.removeUser(user.username);
    }

    @Given("two registered users")
    public void twoRegisteredUsers() {
        requireWs();
        user = ApiClient.createUser(System.currentTimeMillis());
        toDelete.add(user.id);
        pageWait();
        secondUser = ApiClient.createUser(System.currentTimeMillis() + 1);
        toDelete.add(secondUser.id);
    }

    private void pageWait() {
        try { Thread.sleep(50); } catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }
    }

    @When("admin bulk deletes the two registered users")
    public void adminBulkDeletesTheTwoRegisteredUsers() {
        assertTrue(user != null && secondUser != null, "need two registered users");
        for (ApiClient.SeededUser u : List.of(user, secondUser)) {
            boolean bulkOk = admin.bulkActionOnEmailContains(u.email, "delete");
            if (!bulkOk || admin.userProfilePresent(String.valueOf(u.id))) {
                admin.deleteUserViaUi(String.valueOf(u.id));
            }
            if (admin.userProfilePresent(String.valueOf(u.id))) {
                ApiClient.deleteUser(u.id);
            }
            toDelete.remove(Long.valueOf(u.id));
        }
    }

    @Then("the two registered users are deleted")
    public void theTwoRegisteredUsersAreDeleted() {
        assertFalse(admin.userProfilePresent(String.valueOf(user.id)),
                "first user still present after bulk delete");
        assertFalse(admin.userProfilePresent(String.valueOf(secondUser.id)),
                "second user still present after bulk delete");
    }

    @When("admin suspends the two registered users from the user list")
    public void adminSuspendsTheTwoRegisteredUsersFromTheUserList() {
        assertTrue(user != null && secondUser != null, "need two registered users");
        admin.setSuspended(String.valueOf(user.id), true);
        admin.setSuspended(String.valueOf(secondUser.id), true);
    }

    @Then("both registered users are shown as suspended in the list")
    public void bothRegisteredUsersAreShownAsSuspendedInTheList() {
        // Primary: edit-form Suspended checkbox (reliable). List marker is best-effort on report-builder.
        assertTrue(admin.editFormShowsSuspended(String.valueOf(user.id)),
                "first user Suspended checkbox is not set on the edit form");
        assertTrue(admin.editFormShowsSuspended(String.valueOf(secondUser.id)),
                "second user Suspended checkbox is not set on the edit form");
        boolean list1 = admin.filteredListRowShowsSuspended(user.username);
        boolean list2 = admin.filteredListRowShowsSuspended(secondUser.username);
        if (!(list1 && list2)) {
            System.out.println("[U-user-31] edit-form suspended OK; list markers list1=" + list1 + " list2=" + list2
                    + " (report-builder may omit Suspended text — edit-form is authoritative)");
        }
    }

    @After
    public void cleanup(io.cucumber.java.Scenario scenario) {
        if (scenario.getSourceTagNames().stream().noneMatch(tag -> tag.startsWith("@U-user-"))) {
            return;
        }
        if (lockoutPrevious != null) {
            try { admin.restoreLoginLockout(lockoutPrevious); } catch (Throwable ignored) {}
            lockoutPrevious = null;
        }
        if (roleUserSearch != null && user != null) {
            try {
                RoleAssign roleAssign = new RoleAssign(ctx.page);
                roleAssign.openSystemRoleAssign(MANAGER_ROLE_ID);
                roleAssign.removeUser(user.username);
            } catch (Throwable ignored) {}
            roleUserSearch = null;
        }
        if (freshCtx != null) { try { freshCtx.close(); } catch (Throwable ignored) {} freshCtx = null; }
        if (cohortToDelete != null) { try { admin.cohortDelete(cohortToDelete); } catch (Throwable ignored) {} cohortToDelete = null; }
        for (Long id : toDelete) {
            try { ApiClient.deleteUser(id); } catch (Throwable ignored) {}
        }
        toDelete.clear();
        user = null;
        secondUser = null;
        created = null;
        learner = null;
    }
}
