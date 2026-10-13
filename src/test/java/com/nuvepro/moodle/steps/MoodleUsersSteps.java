package com.nuvepro.moodle.steps;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Page;
import com.nuvepro.moodle.config.Settings;
import com.nuvepro.moodle.helpers.ApiClient;
import com.nuvepro.moodle.helpers.Auth;
import com.nuvepro.moodle.helpers.BrowserUi;
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

    /** Under multi-module parallel load, WS/indexing can lag behind a successful add-user redirect. */
    private Long waitForCreatedUserId(String email, long timeoutMs) {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            try {
                Long id = ApiClient.findUserIdByEmail(email);
                if (id != null) return id;
            } catch (RuntimeException ignored) {
                // WS may be briefly unavailable under load
            }
            try {
                String uiId = admin.lookupUserIdByEmail(email);
                if (uiId != null) return Long.parseLong(uiId);
            } catch (RuntimeException ignored) {
                // browse filter can race other terminals
            }
            ctx.page.waitForTimeout(1_500);
        }
        return null;
    }

    private Browser browserForFreshSession() {
        if (ctx.browser != null) return ctx.browser;
        if (GlobalHooks.browser != null) return GlobalHooks.browser;
        throw new IllegalStateException("Playwright browser not initialized for this scenario");
    }

    private Page loginFresh(String username, String password) {
        if (freshCtx != null) { try { freshCtx.close(); } catch (Throwable ignored) {} }
        freshCtx = BrowserUi.newContext(browserForFreshSession(), new Browser.NewContextOptions()
                .setBaseURL(Settings.BASE_URL).setIgnoreHTTPSErrors(true));
        Page p = freshCtx.newPage();
        BrowserUi.preparePage(p);
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
        lastDataset = dataset;
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
        assertTrue(admin.leftTheForm(), "still on the add-user form after submit (create failed): "
                + admin.addUserFieldErrors());
        Long id = waitForCreatedUserId(created.email, 90_000);
        assertTrue(id != null, "add-user form left the page but no account exists for " + created.email);
        System.out.println("[U-user-1] created " + created.username + " id=" + id);
        if (!toDelete.contains(id)) toDelete.add(id);
    }

    /** Field each rejected dataset must flag (the reason it is rejected), or null when not field-specific. */
    private static String expectedErrorField(String dataset) {
        if (dataset == null) return null;
        return switch (dataset) {
            case "invalid_email", "duplicate_email" -> "email";
            case "duplicate_username", "uppercase_username", "spaces_username" -> "username";
            default -> null;
        };
    }

    private String lastDataset;
    private final java.util.Map<String, String> emptyFormErrors = new java.util.TreeMap<>();
    private String weakPassword;
    private String weakRule;

    @When("admin submits an empty add-user form")
    public void adminSubmitsAnEmptyAddUserForm() {
        admin.openAddUser();
        admin.untickGeneratePassword();
        admin.submit();
        java.util.Map<String, String> browser = admin.addUserFieldErrors();
        System.out.println("[U-user-2] after Save (browser checks): " + browser);
        admin.submitAddUserToServer();
        java.util.Map<String, String> server = admin.addUserFieldErrors();
        System.out.println("[U-user-2] after server validation: " + server);
        emptyFormErrors.putAll(browser);
        emptyFormErrors.putAll(server);
    }

    @Then("username, password, first name, last name and email each show a required-field error")
    public void everyRequiredAddUserFieldShowsAnError() {
        List<String> missing = new ArrayList<>();
        for (String field : new java.util.TreeSet<>(AdminUsers.REQUIRED_ADD_USER_FIELDS.keySet())) {
            String error = emptyFormErrors.get(field);
            System.out.println("[U-user-2] " + field + " -> " + (error == null ? "NO ERROR" : error));
            if (error == null) missing.add(field);
        }
        assertTrue(missing.isEmpty(), "empty add-user form showed no error for " + missing
                + " (errors seen: " + emptyFormErrors + ")");
    }

    @When("admin submits the add-user form with password {string} that breaks the {string} rule")
    public void adminSubmitsWeakPassword(String password, String rule) {
        weakPassword = password;
        weakRule = rule;
        admin.openAddUser();
        String policy = admin.passwordPolicyText();
        System.out.println("[U-user-3] site password policy: " + (policy.isEmpty() ? "(none shown)" : policy));
        if (!policyEnforces(policy, rule, password)) {
            throw new SkipException("site password policy does not enforce '" + rule + "' for '"
                    + password + "': " + policy);
        }
        created = UserTestData.createForm("weak_password", null).withPassword(password);
        admin.fillNewUser(created);
        admin.submit();
        if (!admin.leftTheForm() && admin.addUserFieldErrors().isEmpty()) {
            admin.submitAddUserToServer();
        }
    }

    @Then("the password error explains the broken rule")
    public void thePasswordErrorExplainsTheBrokenRule() {
        String error = admin.addUserFieldErrors().getOrDefault("password", "");
        System.out.println("[U-user-3] password '" + weakPassword + "' (" + weakRule + ") -> "
                + (error.isEmpty() ? "NO PASSWORD ERROR" : error));
        assertTrue(error.toLowerCase(java.util.Locale.ROOT).contains(errorKeyword(weakRule)),
                "password '" + weakPassword + "' (" + weakRule + ") was not rejected for that rule; "
                        + "password error: '" + error + "'");
    }

    private static boolean policyEnforces(String policy, String rule, String password) {
        String p = policy.toLowerCase(java.util.Locale.ROOT);
        if ("too short".equals(rule)) {
            java.util.regex.Matcher m = java.util.regex.Pattern.compile("at least (\\d+) characters").matcher(p);
            return m.find() && password.length() < Integer.parseInt(m.group(1));
        }
        return p.contains(errorKeyword(rule));
    }

    /** Wording shared by Moodle's policy summary and its per-rule password error. */
    private static String errorKeyword(String rule) {
        return switch (rule) {
            case "too short" -> "characters";
            case "no digit" -> "digit";
            case "no lower case letter" -> "lower case";
            case "no upper case letter" -> "upper case";
            case "no special character" -> "special character";
            default -> throw new IllegalArgumentException("unknown password rule '" + rule + "'");
        };
    }

    @Then("the add-user form is rejected")
    public void theAddUserFormIsRejected() {
        assertFalse(admin.leftTheForm(), "invalid add-user form was accepted (should be rejected)");
        java.util.Map<String, String> errors = admin.addUserFieldErrors();
        System.out.println("[add-user] dataset=" + lastDataset + " field errors: " + errors);
        if (errors.isEmpty() && weakPassword != null) {
            errors = admin.addUserFieldErrors();
        }
        assertTrue(!errors.isEmpty() || admin.hasFormError(),
                "add-user form stayed put but shows no field error");
        if (errors.isEmpty()) {
            return;
        }
        String field = expectedErrorField(lastDataset);
        if (field != null) {
            assertTrue(errors.containsKey(field), "dataset '" + lastDataset + "' was rejected, but not with a "
                    + field + " error (errors: " + errors + ")");
        }
    }

    @Given("a registered user")
    public void aRegisteredUser() {
        requireWs();
        user = ApiClient.createUser(System.currentTimeMillis());
        toDelete.add(user.id);
        com.nuvepro.moodle.helpers.AutomationManifest.registerUser(user.id, user.username);
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
        com.nuvepro.moodle.helpers.AutomationManifest.registerUser(user.id, user.username);
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
        assertTrue(admin.deleteUserViaUi(String.valueOf(user.id)),
                "admin UI offered no Delete confirmation for user " + user.username);
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
        assertTrue(admin.cohortAddMember(cohortId, user.username, user.email),
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
        com.nuvepro.moodle.helpers.AutomationManifest.registerUser(user.id, user.username);
        }
        if (freshCtx != null) { try { freshCtx.close(); } catch (Throwable ignored) {} }
        freshCtx = BrowserUi.newContext(browserForFreshSession(), new Browser.NewContextOptions()
                .setBaseURL(Settings.BASE_URL).setIgnoreHTTPSErrors(true));
        Page p = freshCtx.newPage();
        BrowserUi.preparePage(p);
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
        roleAssign.assignUser(user.username, user.email);
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
        com.nuvepro.moodle.helpers.AutomationManifest.registerUser(user.id, user.username);
        pageWait();
        secondUser = ApiClient.createUser(System.currentTimeMillis() + 1);
        toDelete.add(secondUser.id);
    }

    private void pageWait() {
        try { Thread.sleep(50); } catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }
    }

    @When("admin bulk deletes the two registered users")
    public void adminBulkDeletesTheTwoRegisteredUsers() {
        bulkSelectBothRegisteredUsers();
        String problem = admin.applyBulkAction("Delete");
        assertTrue(problem == null, "bulk Delete was not applied: " + problem);
    }

    /** Put exactly the two registered users in Bulk user actions' Selected list. */
    private void bulkSelectBothRegisteredUsers() {
        assertTrue(user != null && secondUser != null, "need two registered users");
        java.util.Map<Long, String> both = new java.util.LinkedHashMap<>();
        both.put(user.id, user.email);
        both.put(secondUser.id, secondUser.email);
        List<String> selected = admin.bulkSelectExactly(both);
        assertEquals(new java.util.TreeSet<>(selected),
                new java.util.TreeSet<>(List.of(String.valueOf(user.id), String.valueOf(secondUser.id))),
                "Bulk user actions Selected list is not exactly the two registered users");
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
        // Browse users (/admin/user.php): legacy Bulk user actions on this site has Delete but no Suspend.
        for (ApiClient.SeededUser account : List.of(user, secondUser)) {
            admin.filterUserList(account.username);
            assertTrue(admin.userAppearsInFilteredList(account.username),
                    "user is not on Browse users before suspend: " + account.username);
            admin.setSuspended(String.valueOf(account.id), true);
        }
    }

    @Then("both registered users are shown as suspended in the list")
    public void bothRegisteredUsersAreShownAsSuspendedInTheList() {
        for (ApiClient.SeededUser u : List.of(user, secondUser)) {
            assertTrue(admin.editFormShowsSuspended(String.valueOf(u.id)),
                    u.username + " Suspended checkbox is not set on the edit form");
            assertTrue(admin.userAppearsWhenFilteredSuspendedYes(u.username),
                    u.username + " does not appear when Browse users is filtered Suspended=Yes");
        }
    }

    @Then("the registered user no longer has the Manager system role")
    public void theRegisteredUserNoLongerHasTheManagerSystemRole() {
        RoleAssign roleAssign = new RoleAssign(ctx.page);
        roleAssign.openSystemRoleAssign(MANAGER_ROLE_ID);
        assertFalse(roleAssign.userAssigned(user.username),
                "Manager system role is still assigned to " + user.username + " after removal");
    }

    @After
    public void cleanup(io.cucumber.java.Scenario scenario) {
        if (scenario.getSourceTagNames().stream().noneMatch(tag -> tag.startsWith("@U-user-"))) {
            return;
        }
        if (!com.nuvepro.moodle.helpers.CleanupPolicy.runScenarioCleanup()) {
            System.out.println("[U-user] deferCleanup=true — users left for @cleanup-users");
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
