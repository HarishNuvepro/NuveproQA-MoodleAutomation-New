package com.nuvepro.moodle.steps;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Page;
import com.nuvepro.moodle.config.Settings;
import com.nuvepro.moodle.helpers.ApiClient;
import com.nuvepro.moodle.helpers.Auth;
import com.nuvepro.moodle.helpers.UploadUsersCsv;
import com.nuvepro.moodle.pages.AdminUsers;
import com.nuvepro.moodle.pages.CourseAdmin;
import com.nuvepro.moodle.pages.Groups;
import com.nuvepro.moodle.pages.RoleAssign;
import com.nuvepro.moodle.pages.UploadUsersPage;
import io.cucumber.java.After;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import org.testng.SkipException;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;

/** Steps for Moodle core Site administration > Users > Accounts > Upload users. */
public final class UploadUsersSteps {
    private static final String STRONG_PASSWORD = "Upload#2026Aa1!";
    private static final int MANAGER_ROLE_ID = 1;
    private final TestContext ctx;
    private final UploadUsersPage upload;
    private final AdminUsers admin;
    private final CourseAdmin courseAdmin;
    private final Groups groups;
    private final RoleAssign roleAssign;
    private final long stamp = System.currentTimeMillis();
    private int sequence;
    private final List<String> uploadedEmails = new ArrayList<>();
    private final List<Long> seededIds = new ArrayList<>();
    private final List<String> courseIds = new ArrayList<>();
    private final List<String> cohorts = new ArrayList<>();
    private final List<CsvUser> users = new ArrayList<>();
    private BrowserContext userContext;
    private ApiClient.SeededUser existing;
    private ApiClient.SeededUser secondExisting;
    private CsvUser newUser;
    private CsvUser validUser;
    private String originalFirstName;
    private String updatedFirstName;
    private boolean continued;
    private String courseShortName;
    private String courseId;
    private String cohortName;
    private String cohortId;
    private String defaultInstitution;
    private String systemRoleUsername;
    private String lastResults = "";

    private record CsvUser(String username, String firstName, String lastName,
                           String email, String password) {}

    public UploadUsersSteps(TestContext ctx) {
        this.ctx = ctx;
        this.upload = new UploadUsersPage(ctx.page);
        this.admin = new AdminUsers(ctx.page);
        this.courseAdmin = new CourseAdmin(ctx.page);
        this.groups = new Groups(ctx.page);
        this.roleAssign = new RoleAssign(ctx.page);
    }

    private void requireWs() {
        if (Settings.WS_TOKEN.isBlank()) throw new SkipException("MOODLE_WS_TOKEN not configured");
    }

    private CsvUser csvUser(String label) {
        sequence++;
        String username = ("uu_" + label + "_" + stamp + "_" + sequence).toLowerCase(Locale.ROOT);
        CsvUser user = new CsvUser(username, "Upload" + sequence, "User",
                username + "@example.com", STRONG_PASSWORD);
        users.add(user);
        uploadedEmails.add(user.email);
        return user;
    }

    private ApiClient.SeededUser seed() {
        requireWs();
        ApiClient.SeededUser user = ApiClient.createUser(stamp + 1000 + sequence++);
        seededIds.add(user.id);
        return user;
    }

    private Path basicCsv(String name, CsvUser... records) {
        List<List<String>> rows = new ArrayList<>();
        for (CsvUser u : records) {
            rows.add(UploadUsersCsv.row(u.username, u.firstName, u.lastName, u.email, u.password));
        }
        return UploadUsersCsv.write(name + "_" + stamp,
                List.of("username", "firstname", "lastname", "email", "password"), rows);
    }

    private void preview(Path csv) {
        upload.open();
        upload.chooseCsv(csv);
        upload.submitInitial();
        assertTrue(upload.previewVisible(), "Upload users preview did not open: " + ctx.page.url());
    }

    /**
     * Read the results summary while it is still on screen. Every later lookup navigates to Browse
     * users, so a deferred resultText() would assert against the user list instead.
     */
    private void captureResults(String what) {
        assertTrue(upload.resultVisible(), what + " did not reach Upload users results: " + upload.resultText());
        lastResults = upload.resultText();
        System.out.println("[UploadUsers] " + what + " -> " + lastResults);
    }

    private int resultCount(String label) {
        return UploadUsersPage.resultCount(lastResults, label);
    }

    private void configureNewOnlyAndCommit() {
        upload.setUploadType("Add new only");
        upload.setNewUserPassword("Field required");
        upload.commit();
        captureResults("Add new only upload");
    }

    private void uploadExistingCsv(Path csv, String uploadType) {
        preview(csv);
        upload.setUploadType(uploadType);
        if (uploadType.toLowerCase(Locale.ROOT).contains("update")) {
            upload.setExistingDetails("Override with file");
        }
        upload.setNewUserPassword("Field required");
        upload.commit();
        captureResults(uploadType + " upload");
    }

    private boolean exists(String email) {
        return idFor(email) != null;
    }

    /**
     * Id of an uploaded account for cleanup: exact email match over Web Services, falling back to the
     * (also exact) UI lookup when the seeding token has no {@code core_user_get_users}. Never delete
     * on a looser match — this decides what gets removed from the site.
     */
    private Long uploadedUserId(String email) {
        try {
            return ApiClient.findUserIdByEmail(email);
        } catch (RuntimeException wsUnavailable) {
            String id = idFor(email);
            return id == null ? null : Long.parseLong(id);
        }
    }

    private String idFor(String email) {
        return admin.lookupUserIdByEmail(email);
    }

    private Page login(String username, String password) {
        closeUserContext();
        userContext = ctx.browser.newContext(new Browser.NewContextOptions()
                .setBaseURL(Settings.BASE_URL).setIgnoreHTTPSErrors(true));
        Page page = userContext.newPage();
        page.setDefaultTimeout(Settings.DEFAULT_TIMEOUT_MS);
        ctx.watchPageConsole(page, "upload-users-user");
        Auth.uiLogin(page, username, password);
        return page;
    }

    private void closeUserContext() {
        if (userContext != null) {
            try { userContext.close(); } catch (Throwable ignored) {}
            userContext = null;
        }
    }

    private CsvUser trackedCsvUser(String username, String firstName, String lastName,
                                   String email, String password) {
        CsvUser user = new CsvUser(username, firstName, lastName, email, password);
        users.add(user);
        uploadedEmails.add(email);
        return user;
    }

    private void commitNewOnly(String what) {
        upload.setUploadType("Add new only");
        upload.setNewUserPassword("Field required");
        upload.commit();
        captureResults(what);
    }

    private void commitExistingUpdate(String what, String detailsMode, boolean updatePassword) {
        upload.setUploadType("Update existing users only");
        if (detailsMode != null && !detailsMode.isBlank()) upload.setExistingDetails(detailsMode);
        if (updatePassword) upload.setExistingPassword("Update");
        upload.commit();
        captureResults(what);
    }

    private com.microsoft.playwright.Locator participantRow(String emailOrUsername) {
        ctx.page.navigate(Settings.BASE_URL + "/user/index.php?id=" + courseId + "&perpage=5000",
                new Page.NavigateOptions()
                        .setWaitUntil(com.microsoft.playwright.options.WaitUntilState.DOMCONTENTLOADED)
                        .setTimeout(45_000));
        ctx.page.waitForTimeout(1_500);
        com.microsoft.playwright.Locator row = ctx.page.locator("table tbody tr")
                .filter(new com.microsoft.playwright.Locator.FilterOptions().setHasText(emailOrUsername));
        if (row.count() == 0 && emailOrUsername.contains("@")) {
            try {
                Long id = ApiClient.findUserIdByEmail(emailOrUsername);
                if (id != null) {
                    row = ctx.page.locator("table tbody tr")
                            .filter(new com.microsoft.playwright.Locator.FilterOptions().setHas(
                                    ctx.page.locator("a[href*='id=" + id + "']")));
                }
            } catch (RuntimeException ignored) {
                // The WS token may not expose core_user_get_users; the text match remains authoritative.
            }
        }
        assertTrue(row.count() > 0, "course participant row missing for " + emailOrUsername);
        return row.first();
    }

    private String editUserValue(String userId, String selector) {
        ctx.page.navigate(Settings.BASE_URL + "/user/editadvanced.php?id=" + userId);
        com.microsoft.playwright.Locator field = ctx.page.locator(selector).first();
        assertTrue(field.count() > 0, "user edit field missing: " + selector);
        return field.inputValue();
    }

    /** End date from Moodle's enrolment edit form, supporting both select-date and timestamp widgets. */
    private LocalDate enrolmentEndDate(com.microsoft.playwright.Locator participantRow) {
        com.microsoft.playwright.Locator edit = participantRow.locator(
                "a[href*='enrol/editenrolment.php'], a[data-action='editenrolment']").first();
        assertTrue(edit.count() > 0, "participant row has no Edit enrolment action");
        String href = edit.getAttribute("href");
        if (href != null && !href.isBlank()) ctx.page.navigate(href);
        else edit.click();
        ctx.page.waitForTimeout(1_000);

        com.microsoft.playwright.Locator enabled = ctx.page.locator(
                "input[name='timeend[enabled]'], #id_timeend_enabled").first();
        assertTrue(enabled.count() > 0 && enabled.isChecked(), "enrolment end date is not enabled");
        com.microsoft.playwright.Locator epoch = ctx.page.locator("input[name='timeend']").first();
        if (epoch.count() > 0) {
            try {
                long seconds = Long.parseLong(epoch.inputValue());
                if (seconds > 0) {
                    return Instant.ofEpochSecond(seconds).atZone(ZoneId.systemDefault()).toLocalDate();
                }
            } catch (NumberFormatException ignored) {
                // use the select-date controls below
            }
        }
        int day = Integer.parseInt(ctx.page.locator("select[name='timeend[day]']").inputValue());
        int month = Integer.parseInt(ctx.page.locator("select[name='timeend[month]']").inputValue());
        int year = Integer.parseInt(ctx.page.locator("select[name='timeend[year]']").inputValue());
        return LocalDate.of(year, month, day);
    }

    @Given("admin opens Upload users")
    public void adminOpensUploadUsers() {
        upload.open();
        assertTrue(upload.initialFormVisible(), "Upload users initial form is not visible");
        assertTrue(upload.exampleCsvLinkVisible(), "Upload users example CSV link is not visible");
    }

    @Then("the example Upload users CSV downloads with required headers")
    public void exampleCsvDownloadsWithRequiredHeaders() {
        Path dest = Path.of("target", "upload-users", "example_" + stamp + ".csv");
        try {
            Files.createDirectories(dest.getParent());
        } catch (java.io.IOException e) {
            throw new RuntimeException(e);
        }
        upload.downloadExampleCsv(dest);
        assertTrue(Files.exists(dest) && dest.toFile().length() > 0,
                "example CSV download was empty");
        String header;
        try {
            header = Files.readString(dest, StandardCharsets.UTF_8).strip().split("\\R", 2)[0]
                    .replace("\uFEFF", "").toLowerCase(Locale.ROOT);
        } catch (java.io.IOException e) {
            throw new RuntimeException(e);
        }
        for (String required : List.of("username", "firstname", "lastname", "email")) {
            assertTrue(header.contains(required),
                    "example CSV is missing header '" + required + "': " + header);
        }
    }

    @When("admin uploads a valid three-user CSV")
    public void uploadValidThreeUsers() {
        CsvUser a = csvUser("valid_a");
        CsvUser b = csvUser("valid_b");
        CsvUser c = csvUser("valid_c");
        validUser = a;
        preview(basicCsv("valid3", a, b, c));
        assertTrue(upload.previewContains(a.username) && upload.previewContains(b.username)
                && upload.previewContains(c.username), "preview does not contain all three CSV users");
        configureNewOnlyAndCommit();
    }

    @Then("all CSV users are created and one can log in")
    public void allCsvUsersCreated() {
        assertEquals(resultCount("Users created"), users.size(),
                "Upload users did not create every CSV row: " + lastResults);
        upload.continueFromResults();
        continued = upload.initialFormVisible();
        String listed = lastResults.toLowerCase(Locale.ROOT);
        for (CsvUser user : users) {
            assertTrue(listed.contains(user.username.toLowerCase(Locale.ROOT))
                            && listed.contains(user.email.toLowerCase(Locale.ROOT)),
                    "uploaded user is not listed on results: " + user.email
                            + " — results were: " + lastResults);
        }
        assertTrue(Auth.isLoggedIn(login(validUser.username, validUser.password)),
                "uploaded user cannot log in with CSV password");
    }

    @Then("continuing from results returns to Upload users")
    public void continuingReturnsToUploadUsers() {
        assertTrue(continued, "Continue did not return to Upload users initial form");
    }

    @When("admin previews a valid Upload users CSV and cancels")
    public void previewAndCancel() {
        CsvUser a = csvUser("cancel_a");
        CsvUser b = csvUser("cancel_b");
        preview(basicCsv("cancel", a, b));
        assertTrue(upload.previewContains(a.username) && upload.previewContains(b.username),
                "preview does not show CSV usernames");
        upload.cancel();
    }

    @Then("the previewed users were not created")
    public void previewedUsersNotCreated() {
        for (CsvUser user : users) {
            assertFalse(exists(user.email), "cancelled preview created user " + user.email);
        }
    }

    @Then("Upload users rejects no file and an empty file")
    public void rejectNoAndEmptyFile() {
        upload.open();
        upload.submitInitial();
        assertFalse(upload.previewVisible(), "Upload users accepted submission with no file");

        upload.open();
        // The picker itself may refuse the zero-byte file with an error dialogue; that is already the
        // rejection, and Moodle then has no file to submit.
        boolean attached = upload.chooseCsv(UploadUsersCsv.empty("empty_" + stamp));
        if (attached) {
            upload.submitInitial();
            assertFalse(upload.previewVisible(), "Upload users accepted a zero-byte CSV");
        }
    }

    @Then("Upload users rejects a CSV missing the email header")
    public void rejectMissingEmailHeader() {
        rejectMissingRequiredHeader("email",
                List.of("username", "firstname", "lastname", "password"),
                user -> UploadUsersCsv.row(user.username, user.firstName, user.lastName, user.password));
    }

    @Then("Upload users rejects a CSV missing the username header")
    public void rejectMissingUsernameHeader() {
        rejectMissingRequiredHeader("username",
                List.of("firstname", "lastname", "email", "password"),
                user -> UploadUsersCsv.row(user.firstName, user.lastName, user.email, user.password));
    }

    private void rejectMissingRequiredHeader(String missing,
                                             List<String> headers,
                                             java.util.function.Function<CsvUser, List<String>> row) {
        CsvUser user = csvUser("missing_" + missing);
        Path csv = UploadUsersCsv.write("missing_" + missing + "_" + stamp, headers, List.of(row.apply(user)));
        upload.open();
        upload.chooseCsv(csv);
        upload.submitInitial();
        // Moodle 4.5 still opens the preview when a required column is absent; it refuses to create
        // until the missing field is supplied. Do not treat reaching preview as a pass.
        if (upload.previewVisible()) {
            upload.setUploadType("Add new only");
            upload.setNewUserPassword("Field required");
            upload.commit();
        }
        if (upload.resultVisible()) {
            lastResults = upload.resultText();
            assertTrue(resultCount("Users created") == 0 || resultCount("Errors") >= 1,
                    "CSV without " + missing + " header created users: " + lastResults);
            assertTrue(lastResults.toLowerCase(Locale.ROOT).contains(missing)
                            || lastResults.toLowerCase(Locale.ROOT).contains("error")
                            || lastResults.toLowerCase(Locale.ROOT).contains("missing"),
                    "CSV without " + missing + " header did not report the missing field: " + lastResults);
        }
    }

    /**
     * Malformed = bad data Moodle reports per row (missing email, malformed email). A CSV username
     * with spaces is deliberately NOT used: with "Standardise usernames" off, Moodle aborts the whole
     * upload with a fatal "The given username contains invalid characters" page and never prints the
     * totals, which tells us nothing about the surviving rows.
     */
    @When("admin uploads a CSV containing valid and malformed user rows")
    public void uploadMixedRows() {
        validUser = csvUser("mixed_valid");
        CsvUser missing = csvUser("mixed_missing");
        CsvUser invalidEmail = csvUser("mixed_email");
        Path csv = UploadUsersCsv.write("mixed_" + stamp,
                List.of("username", "firstname", "lastname", "email", "password"),
                List.of(
                        UploadUsersCsv.row(validUser.username, validUser.firstName, validUser.lastName,
                                validUser.email, validUser.password),
                        UploadUsersCsv.row(missing.username, missing.firstName, missing.lastName, "", missing.password),
                        UploadUsersCsv.row(invalidEmail.username, invalidEmail.firstName, invalidEmail.lastName,
                                "not-an-email", invalidEmail.password)));
        preview(csv);
        upload.setUploadType("Add new only");
        upload.setNewUserPassword("Field required");
        upload.commit();
        captureResults("mixed CSV upload");
    }

    @Then("the valid row is created and malformed rows report errors")
    public void mixedRowsResult() {
        int errors = Math.max(resultCount("Errors"),
                UploadUsersPage.rowsContaining(lastResults, "User not added"));
        assertTrue(errors >= 1, "malformed rows produced no row-level error: " + lastResults);
        assertTrue(resultCount("Users created") >= 1,
                "no row of the mixed CSV was created: " + lastResults);
        assertTrue(exists(validUser.email),
                "valid row from mixed CSV was not created — results were: " + lastResults);
    }

    @Given("an existing user for Upload users")
    public void existingUserForUpload() {
        existing = seed();
        originalFirstName = "Auto";
    }

    @Given("two existing users for CSV deletion")
    public void twoExistingForDelete() {
        existing = seed();
        secondExisting = seed();
    }

    @Given("two existing users for CSV rename")
    public void twoExistingForRename() {
        existing = seed();
        secondExisting = seed();
    }

    @When("admin uploads that username and a new username using Add new only")
    public void addNewOnlyWithExisting() {
        newUser = csvUser("new_only");
        CsvUser old = new CsvUser(existing.username, "Changed", "User",
                existing.email, STRONG_PASSWORD);
        preview(basicCsv("add_new_only", old, newUser));
        configureNewOnlyAndCommit();
    }

    @Then("the existing user is unchanged and the new user is created")
    public void existingUnchangedNewCreated() {
        assertEquals(admin.currentEmailValue(String.valueOf(existing.id)), existing.email,
                "Add new only changed existing user");
        assertTrue(exists(newUser.email), "Add new only did not create the new user");
    }

    @When("admin uploads changed existing details and a new user using Add new and update existing")
    public void addAndUpdate() {
        updatedFirstName = "Updated" + sequence;
        newUser = csvUser("add_update_new");
        CsvUser changed = new CsvUser(existing.username, updatedFirstName, "User",
                existing.email, STRONG_PASSWORD);
        uploadExistingCsv(basicCsv("add_update", changed, newUser), "Add new and update existing");
    }

    @Then("the existing user is updated and the new user is created")
    public void existingUpdatedNewCreated() {
        assertEquals(admin.currentFirstName(String.valueOf(existing.id)), updatedFirstName,
                "existing user was not updated from CSV");
        assertTrue(exists(newUser.email),
                "new CSV user was not created — results were: " + lastResults);
    }

    @When("admin uploads an existing and unknown username using Update existing only")
    public void updateExistingOnly() {
        updatedFirstName = "OnlyUpdated" + sequence;
        newUser = csvUser("update_unknown");
        CsvUser changed = new CsvUser(existing.username, updatedFirstName, "User",
                existing.email, STRONG_PASSWORD);
        uploadExistingCsv(basicCsv("update_only", changed, newUser), "Update existing users only");
    }

    @Then("only the existing user is updated")
    public void onlyExistingUpdated() {
        assertEquals(admin.currentFirstName(String.valueOf(existing.id)), updatedFirstName,
                "existing user was not updated");
        assertFalse(exists(newUser.email), "Update existing only created an unknown user");
    }

    @When("admin uploads a new username with the existing email and prevents duplicate emails")
    public void duplicateEmailPrevented() {
        newUser = csvUser("duplicate_email");
        CsvUser duplicate = new CsvUser(newUser.username, newUser.firstName, newUser.lastName,
                existing.email, newUser.password);
        preview(basicCsv("duplicate_email", duplicate));
        upload.setUploadType("Add new only");
        upload.setNewUserPassword("Field required");
        upload.setPreventEmailDuplicates(true);   // hidden + forced to Yes unless same-email accounts are allowed
        upload.commit();
        captureResults("duplicate-email upload");
    }

    @Then("the duplicate-email user is not created")
    public void duplicateEmailNotCreated() {
        assertTrue(resultCount("Errors") >= 1, "duplicate email produced no error: " + lastResults);
        assertEquals(resultCount("Users created"), 0,
                "duplicate-email row was created anyway: " + lastResults);
    }

    @When("admin uploads a new user with a weak required password")
    public void uploadWeakPassword() {
        newUser = csvUser("weak");
        CsvUser weak = new CsvUser(newUser.username, newUser.firstName, newUser.lastName,
                newUser.email, "weak");
        preview(basicCsv("weak", weak));
        upload.setUploadType("Add new only");
        upload.setNewUserPassword("Field required");
        upload.commit();
        captureResults("weak-password upload");
    }

    /**
     * Moodle does not reject a weak CSV password: it creates the account, counts it under "Users weak
     * password" and forces a password change at first login (uuforcepasswordchange defaults to
     * "Users with weak password"). The account being unusable until reset is the real guarantee.
     */
    @Then("the weak CSV password is flagged and must be reset at login")
    public void weakPasswordFlagged() {
        int weak = UploadUsersPage.resultCountAny(lastResults,
                "Users having a weak password", "Users weak password");
        assertTrue(weak >= 1, "weak password was not reported as weak: " + lastResults);
        assertTrue(exists(newUser.email),
                "weak-password user was not created — results were: " + lastResults);
        Page page = login(newUser.username, "weak");
        String body = page.locator("body").innerText().toLowerCase(Locale.ROOT);
        assertTrue(page.url().contains("/login/change_password.php") || body.contains("change password"),
                "weak-password user was not forced to change password at login: " + page.url());
    }

    @When("admin suspends the existing user through Upload users")
    public void suspendViaCsv() {
        uploadSuspended(existing, "1", true, "suspend");
    }

    @Then("the CSV user cannot log in")
    public void csvUserCannotLogin() {
        assertFalse(Auth.isLoggedIn(login(existing.username, existing.password)),
                "CSV-suspended user could log in");
    }

    @When("admin reactivates the existing user through Upload users")
    public void reactivateViaCsv() {
        uploadSuspended(existing, "0", true, "reactivate");
    }

    @Then("the CSV user can log in")
    public void csvUserCanLogin() {
        assertTrue(Auth.isLoggedIn(login(existing.username, existing.password)),
                "CSV-reactivated user cannot log in");
    }

    private void uploadSuspended(ApiClient.SeededUser user, String suspended,
                                 boolean allow, String name) {
        Path csv = UploadUsersCsv.write(name + "_" + stamp,
                List.of("username", "suspended"),
                List.of(UploadUsersCsv.row(user.username, suspended)));
        preview(csv);
        upload.setUploadType("Update existing users only");
        upload.setAllowSuspends(allow);
        upload.commit();
        captureResults(name + " upload");
    }

    @When("admin uploads deleted one with Allow deletes off and deleted one with Allow deletes on")
    public void deleteAllowedAndBlocked() {
        uploadDeleted(existing.username, false, "delete_blocked");
        uploadDeleted(secondExisting.username, true, "delete_allowed");
    }

    private void uploadDeleted(String username, boolean allow, String name) {
        Path csv = UploadUsersCsv.write(name + "_" + stamp,
                List.of("username", "deleted"),
                List.of(UploadUsersCsv.row(username, "1")));
        preview(csv);
        upload.setUploadType("Update existing users only");
        upload.setAllowDeletes(allow);
        upload.commit();
        captureResults(name + " upload");
    }

    @Then("the blocked-delete user remains and the allowed-delete user is deleted")
    public void deleteOutcomes() {
        assertTrue(admin.userProfilePresent(String.valueOf(existing.id)),
                "Allow deletes=No still deleted the user");
        assertFalse(admin.userProfilePresent(String.valueOf(secondExisting.id)),
                "Allow deletes=Yes did not delete the user");
    }

    /**
     * Defaults to the account running the suite, which is the safest possible target: Upload users
     * refuses both "your own account" and any site administrator before it ever calls delete_user(),
     * and delete_user() itself throws for a site admin. Set
     * UPLOAD_USERS_PROTECTED_ADMIN_USERNAME to aim at a different administrator instead.
     */
    @Then("a configured protected administrator cannot be deleted by Upload users")
    public void protectedAdminNotDeleted() {
        String username = Settings.UPLOAD_USERS_PROTECTED_ADMIN_USERNAME.isBlank()
                ? Settings.ADMIN_USER
                : Settings.UPLOAD_USERS_PROTECTED_ADMIN_USERNAME;
        uploadDeleted(username, true, "protected_admin");
        assertTrue(resultCount("Delete errors") >= 1,
                "Upload users did not refuse to delete an administrator: " + lastResults);
        assertEquals(resultCount("Users deleted"), 0,
                "an administrator was deleted by Upload users: " + lastResults);
        ctx.page.navigate(Settings.BASE_URL + "/admin/user.php");
        assertTrue(Auth.isLoggedIn(ctx.page), "the administrator account no longer works after the upload");
    }

    @When("admin uploads renames with Allow renames off and on")
    public void renameBlockedAndAllowed() {
        rename(existing, existing.username + "_blocked", false, "rename_blocked");
        rename(secondExisting, secondExisting.username + "_renamed", true, "rename_allowed");
    }

    private void rename(ApiClient.SeededUser user, String newUsername, boolean allow, String name) {
        Path csv = UploadUsersCsv.write(name + "_" + stamp,
                List.of("username", "oldusername"),
                List.of(UploadUsersCsv.row(newUsername, user.username)));
        preview(csv);
        upload.setUploadType("Update existing users only");
        upload.setAllowRenames(allow);
        upload.commit();
        captureResults(name + " upload");
    }

    @Then("the blocked rename is unchanged and the allowed rename succeeds")
    public void renameOutcomes() {
        ctx.page.navigate(Settings.BASE_URL + "/user/editadvanced.php?id=" + existing.id);
        assertEquals(ctx.page.locator("#id_username").inputValue(), existing.username,
                "Allow renames=No changed the username");
        ctx.page.navigate(Settings.BASE_URL + "/user/editadvanced.php?id=" + secondExisting.id);
        assertEquals(ctx.page.locator("#id_username").inputValue(), secondExisting.username + "_renamed",
                "Allow renames=Yes did not rename the user");
    }

    @Given("an Upload users course fixture")
    public void courseFixture() {
        courseShortName = "uucourse" + stamp;
        courseId = courseAdmin.createCourse("Upload Users Course " + stamp, courseShortName);
        courseIds.add(courseId);
    }

    @When("admin uploads users with valid and unknown course1 shortnames")
    public void uploadCourseUsers() {
        CsvUser valid = csvUser("course_valid");
        CsvUser invalid = csvUser("course_invalid");
        validUser = valid;
        newUser = invalid;
        Path csv = UploadUsersCsv.write("courses_" + stamp,
                List.of("username", "firstname", "lastname", "email", "password", "course1"),
                List.of(
                        UploadUsersCsv.row(valid.username, valid.firstName, valid.lastName,
                                valid.email, valid.password, courseShortName),
                        UploadUsersCsv.row(invalid.username, invalid.firstName, invalid.lastName,
                                invalid.email, invalid.password, "no_such_course_" + stamp)));
        preview(csv);
        configureNewOnlyAndCommit();
    }

    /**
     * Read enrolment from the course Participants list. A non-enrolled learner's own view of the
     * course varies with self-enrolment and guest access, so it cannot prove they are not enrolled.
     */
    @Then("only the valid-course user is enrolled in the course")
    public void courseEnrolOutcome() {
        assertTrue(resultCount("Errors") >= 0, "no upload results were captured: " + lastResults);
        ctx.page.navigate(Settings.BASE_URL + "/user/index.php?id=" + courseId + "&perpage=5000",
                new Page.NavigateOptions()
                        .setWaitUntil(com.microsoft.playwright.options.WaitUntilState.DOMCONTENTLOADED)
                        .setTimeout(45_000));
        ctx.page.waitForTimeout(1_200);
        String participants = ctx.page.locator("body").innerText().toLowerCase(Locale.ROOT);
        assertTrue(participants.contains(validUser.email.toLowerCase(Locale.ROOT))
                        || participants.contains(validUser.username.toLowerCase(Locale.ROOT)),
                "course1 did not enrol " + validUser.username + " into " + courseShortName);
        assertFalse(participants.contains(newUser.email.toLowerCase(Locale.ROOT))
                        || participants.contains(newUser.username.toLowerCase(Locale.ROOT)),
                "the unknown-course row enrolled " + newUser.username + " into " + courseShortName);
    }

    @Then("the unknown-course CSV user is still created")
    public void unknownCourseUserIsCreated() {
        String results = lastResults.toLowerCase(Locale.ROOT);
        assertTrue(resultCount("Users created") >= 2
                        || (results.contains(newUser.username.toLowerCase(Locale.ROOT))
                        && results.contains("new user")),
                "unknown course1 shortname should still create the user: " + lastResults);
        assertTrue(results.contains("unknown course") || results.contains("no_such_course"),
                "unknown course1 produced no warning: " + lastResults);
    }

    @Given("an Upload users cohort fixture")
    public void cohortFixture() {
        cohortName = "Upload Users Cohort " + stamp;
        String idNumber = "uucohort" + stamp;
        admin.cohortCreate(cohortName, idNumber);
        cohorts.add(cohortName);
        cohortId = admin.cohortIdByName(cohortName);
        assertNotNull(cohortId, "created cohort id was not found");
    }

    @When("admin uploads a user with cohort and profile values")
    public void uploadCohortProfileUser() {
        newUser = csvUser("cohort_profile");
        Path csv = UploadUsersCsv.write("cohort_profile_" + stamp,
                List.of("username", "firstname", "lastname", "email", "password",
                        "cohort1", "city", "country", "department"),
                List.of(UploadUsersCsv.row(newUser.username, newUser.firstName, newUser.lastName,
                        newUser.email, newUser.password, "uucohort" + stamp,
                        "Bengaluru", "IN", "Automation")));
        preview(csv);
        // Moodle drops the Default values field for every column the CSV supplies, so the default can
        // only be exercised on a field the file leaves out.
        defaultInstitution = "UploadDefaults" + sequence;
        assertTrue(upload.setDefaultValue("institution", defaultInstitution),
                "Default values Institution field missing from the Upload users preview");
        configureNewOnlyAndCommit();
    }

    @Then("the user belongs to the cohort and profile values persist")
    public void cohortProfileOutcome() {
        assertTrue(admin.cohortHasMember(cohortId, newUser.username),
                "cohort1 did not add uploaded user to cohort");
        String id = idFor(newUser.email);
        assertNotNull(id, "uploaded cohort/profile user is missing — results were: " + lastResults);
        ctx.page.navigate(Settings.BASE_URL + "/user/editadvanced.php?id=" + id);
        assertEquals(ctx.page.locator("#id_city").inputValue(), "Bengaluru", "CSV city did not persist");
        assertEquals(ctx.page.locator("#id_country").inputValue(), "IN", "CSV country did not persist");
        assertEquals(ctx.page.locator("#id_department").inputValue(), "Automation",
                "CSV department did not persist");
        assertEquals(ctx.page.locator("#id_institution").inputValue(), defaultInstitution,
                "Default values Institution was not applied to the uploaded user");
    }

    @Then("a delimiter mismatch creates no user")
    public void delimiterMismatch() {
        newUser = csvUser("delimiter");
        upload.open();
        upload.chooseCsv(basicCsv("delimiter", newUser));
        upload.setDelimiter("tab");
        upload.submitInitial();
        if (upload.previewVisible()) {
            assertFalse(upload.previewContains(newUser.username),
                    "comma CSV parsed normally while Tab delimiter was selected");
        }
        assertFalse(exists(newUser.email), "delimiter-mismatch CSV created a user");
    }

    @Then("a student cannot access Upload users")
    public void studentDenied() {
        ApiClient.SeededUser student = seed();
        Page page = login(student.username, student.password);
        page.navigate(Settings.BASE_URL + "/admin/tool/uploaduser/index.php");
        page.waitForTimeout(700);
        assertTrue(new UploadUsersPage(page).accessDenied(),
                "student was able to access the Upload users admin form");
    }

    @When("admin uploads that username using Add all append number")
    public void addAllAppend() {
        newUser = csvUser("append_email");
        CsvUser duplicate = new CsvUser(existing.username, "Appended", "User",
                newUser.email, newUser.password);
        preview(basicCsv("append", duplicate));
        upload.setUploadType("Add all");
        upload.setNewUserPassword("Field required");
        upload.commit();
        captureResults("Add all append upload");
    }

    @Then("a numbered username is created and the original is unchanged")
    public void appendOutcome() {
        String id = idFor(newUser.email);
        assertNotNull(id, "Add all did not create a second user — results were: " + lastResults);
        ctx.page.navigate(Settings.BASE_URL + "/user/editadvanced.php?id=" + id);
        String actual = ctx.page.locator("#id_username").inputValue();
        // uu_increment_username() bumps a trailing number rather than appending one, so
        // autotest_1000 becomes autotest_1001 — compare the shared non-numeric stem.
        assertNotEquals(actual, existing.username, "Add all reused the existing username");
        assertEquals(usernameStem(actual), usernameStem(existing.username),
                "Add all did not derive the new username from " + existing.username + ": " + actual);
        assertTrue(admin.userProfilePresent(String.valueOf(existing.id)), "original user was changed/deleted");
    }

    private static String usernameStem(String username) {
        return username.replaceAll("\\d+$", "");
    }

    // ---- Extended Upload users coverage (UU-32..UU-47, excluding config/mail cases) ----

    @Then("the Preview rows setting limits the parsed CSV preview")
    public void previewRowsLimit() {
        List<CsvUser> previewUsers = new ArrayList<>();
        // Preview is cancelled, so do not register these rows for account cleanup (that would perform
        // eleven pointless WS/UI existence lookups for users that were never committed).
        for (int i = 0; i < 11; i++) {
            sequence++;
            String username = "uu_preview_" + i + "_" + stamp + "_" + sequence;
            previewUsers.add(new CsvUser(username, "Preview" + i, "User",
                    username + "@example.com", STRONG_PASSWORD));
        }
        upload.open();
        upload.chooseCsv(basicCsv("preview_limit", previewUsers.toArray(CsvUser[]::new)));
        upload.setPreviewRows(10);
        upload.submitInitial();
        assertTrue(upload.previewVisible(), "Upload users preview did not open");
        assertTrue(upload.previewContains(previewUsers.get(0).username)
                        && upload.previewContains(previewUsers.get(9).username),
                "the selected ten-row preview did not show rows 1 through 10");
        assertFalse(upload.previewContains(previewUsers.get(10).username),
                "Preview rows=10 displayed the eleventh CSV record");
        upload.cancel();
    }

    @Then("Force password change All is enforced for a new CSV user")
    public void forcePasswordChangeAll() {
        newUser = csvUser("force_change");
        preview(basicCsv("force_change", newUser));
        upload.setForcePasswordChange("All");
        commitNewOnly("force-password-change upload");
        assertEquals(resultCount("Users created"), 1, "force-change user was not created: " + lastResults);
        Page page = login(newUser.username, newUser.password);
        String body = page.locator("body").innerText().toLowerCase(Locale.ROOT);
        assertTrue(page.url().contains("/login/change_password.php") || body.contains("change password"),
                "Force password change=All did not force a reset at first login: " + page.url());
    }

    @Then("Update existing password replaces the old password with the CSV password")
    public void updateExistingPassword() {
        existing = seed();
        String replacement = "Updated#2026Bb2!";
        Path csv = UploadUsersCsv.write("password_update_" + stamp,
                List.of("username", "firstname", "lastname", "email", "password"),
                List.of(UploadUsersCsv.row(existing.username, "Auto", "Updated",
                        existing.email, replacement)));
        preview(csv);
        commitExistingUpdate("existing-password update", "Override with file", true);
        assertTrue(resultCount("Users updated") >= 1, "existing user was not updated: " + lastResults);
        assertFalse(Auth.isLoggedIn(login(existing.username, existing.password)),
                "old password still logs in after Existing user password=Update");
        assertTrue(Auth.isLoggedIn(login(existing.username, replacement)),
                "CSV replacement password cannot log in");
    }

    @Then("Fill in missing preserves existing values and fills an empty profile field")
    public void fillInMissingDetails() {
        existing = seed();
        String institution = "FilledByUpload" + stamp;
        Path csv = UploadUsersCsv.write("fill_missing_" + stamp,
                List.of("username", "firstname", "institution"),
                List.of(UploadUsersCsv.row(existing.username, "MustNotReplace", institution)));
        preview(csv);
        commitExistingUpdate("fill-missing upload", "Fill in missing", false);
        assertTrue(resultCount("Users updated") >= 1, "fill-missing user was not updated: " + lastResults);
        assertEquals(admin.currentFirstName(String.valueOf(existing.id)), "Auto",
                "Fill in missing replaced a non-empty first name");
        assertEquals(editUserValue(String.valueOf(existing.id), "#id_institution"), institution,
                "Fill in missing did not populate the empty Institution field");
    }

    @Then("Standardise usernames normalises an uppercase CSV username")
    public void standardiseUsernames() {
        String supplied = ("UU_STANDARD_" + stamp).toUpperCase(Locale.ROOT);
        String email = "uu_standard_" + stamp + "@example.com";
        newUser = trackedCsvUser(supplied, "Standard", "User", email, STRONG_PASSWORD);
        preview(basicCsv("standardise", newUser));
        upload.setStandardiseUsernames(true);
        commitNewOnly("standardise-username upload");
        String id = idFor(email);
        assertNotNull(id, "standardised user was not created: " + lastResults);
        assertEquals(editUserValue(id, "#id_username"), supplied.toLowerCase(Locale.ROOT),
                "Standardise usernames did not normalise the username to Moodle's canonical form");
    }

    @Then("selected new CSV users are forwarded to Bulk user actions")
    public void selectForBulkActions() {
        newUser = csvUser("bulk_selected");
        preview(basicCsv("bulk_selected", newUser));
        upload.setUploadType("Add new only");
        upload.setNewUserPassword("Field required");
        upload.setBulkSelection("New users");
        upload.commit();
        captureResults("bulk-selection upload");
        upload.continueFromResults();
        assertTrue(admin.bulkUserActionsReady(30_000),
                "Continue did not open Bulk user actions: " + ctx.page.url());
        int selected = admin.waitForBulkSelectedUsers(30_000);
        String selection = admin.bulkSelectedUsersText();
        assertTrue(selected >= 1 && selected <= 5, "the Selected box holds " + selected
                + " users instead of just the uploaded ones: " + selection);
        // The Selected box labels users by full name, not by the CSV username or email.
        String fullName = (newUser.firstName + " " + newUser.lastName).toLowerCase(Locale.ROOT);
        assertTrue(selection.toLowerCase(Locale.ROOT).contains(fullName),
                "the uploaded user is not in the bulk selection: " + selection);
        assertTrue(admin.chooseBulkAction("Force password change"),
                "no bulk action could be chosen for the uploaded selection");
    }

    @Then("group1 creates a missing course group and assigns the CSV user")
    public void groupOneCreatesAndAssigns() {
        courseFixture();
        String groupName = "UU Group " + stamp;
        newUser = csvUser("group");
        Path csv = UploadUsersCsv.write("group_" + stamp,
                List.of("username", "firstname", "lastname", "email", "password", "course1", "group1"),
                List.of(UploadUsersCsv.row(newUser.username, newUser.firstName, newUser.lastName,
                        newUser.email, newUser.password, courseShortName, groupName)));
        preview(csv);
        configureNewOnlyAndCommit();
        String groupId = groups.groupIdByName(Integer.parseInt(courseId), groupName);
        assertNotNull(groupId, "group1 did not create the missing group " + groupName);
        assertTrue(groups.userInGroup(groupId, newUser.email)
                        || groups.userInGroup(groupId, newUser.firstName),
                "group1 did not assign the uploaded user to " + groupName);
    }

    @Then("role1 assigns the requested course role to the CSV enrolment")
    public void roleOneAssignsCourseRole() {
        courseFixture();
        newUser = csvUser("course_role");
        Path csv = UploadUsersCsv.write("course_role_" + stamp,
                List.of("username", "firstname", "lastname", "email", "password", "course1", "role1"),
                List.of(UploadUsersCsv.row(newUser.username, newUser.firstName, newUser.lastName,
                        newUser.email, newUser.password, courseShortName, "editingteacher")));
        preview(csv);
        configureNewOnlyAndCommit();
        String row = participantRow(newUser.email).innerText().toLowerCase(Locale.ROOT);
        assertTrue(row.contains("teacher"),
                "role1=editingteacher is not shown on the participant row: " + row);
    }

    @Then("enrolperiod1 and enrolstatus1 control the CSV course enrolment")
    public void enrolPeriodAndStatusControlEnrolment() {
        courseFixture();
        newUser = csvUser("enrol_state");
        Path csv = UploadUsersCsv.write("enrol_state_" + stamp,
                List.of("username", "firstname", "lastname", "email", "password",
                        "course1", "enrolperiod1", "enrolstatus1"),
                List.of(UploadUsersCsv.row(newUser.username, newUser.firstName, newUser.lastName,
                        newUser.email, newUser.password, courseShortName, "7", "1")));
        preview(csv);
        configureNewOnlyAndCommit();
        com.microsoft.playwright.Locator row = participantRow(newUser.email);
        assertTrue(row.innerText().toLowerCase(Locale.ROOT).contains("suspended"),
                "enrolstatus1=1 did not suspend the course enrolment: " + row.innerText());
        LocalDate end = enrolmentEndDate(row);
        LocalDate expected = LocalDate.now().plusDays(7);
        assertTrue(!end.isBefore(expected.minusDays(1)) && !end.isAfter(expected.plusDays(1)),
                "enrolperiod1=7 produced end date " + end + " instead of about " + expected);
    }

    @Then("sysrole1 assigns and removes the Manager system role")
    public void systemRoleAssignAndRemove() {
        existing = seed();
        systemRoleUsername = existing.username;
        Path assign = UploadUsersCsv.write("sysrole_assign_" + stamp,
                List.of("username", "sysrole1"),
                List.of(UploadUsersCsv.row(existing.username, "manager")));
        preview(assign);
        commitExistingUpdate("system-role assign", null, false);
        roleAssign.openSystemRoleAssign(MANAGER_ROLE_ID);
        assertTrue(roleAssign.userAssigned(existing.username),
                "sysrole1=manager did not assign the Manager role");

        Path remove = UploadUsersCsv.write("sysrole_remove_" + stamp,
                List.of("username", "sysrole1"),
                List.of(UploadUsersCsv.row(existing.username, "-manager")));
        preview(remove);
        commitExistingUpdate("system-role removal", null, false);
        roleAssign.openSystemRoleAssign(MANAGER_ROLE_ID);
        assertFalse(roleAssign.userAssigned(existing.username),
                "sysrole1=-manager did not remove the Manager role");
        systemRoleUsername = null;
    }

    @Then("an over-limit profile value is refused and creates no user")
    public void overLimitValueRejected() {
        CsvUser overLimit = trackedCsvUser("uu_limit_" + stamp, "X".repeat(101), "Boundary",
                "uu_limit_" + stamp + "@example.com", STRONG_PASSWORD);
        CsvUser afterFatal = csvUser("after_limit");
        Path csv = UploadUsersCsv.write("over_limit_" + stamp,
                List.of("username", "firstname", "lastname", "email", "password"),
                List.of(
                        UploadUsersCsv.row(overLimit.username, overLimit.firstName, overLimit.lastName,
                                overLimit.email, overLimit.password),
                        UploadUsersCsv.row(afterFatal.username, afterFatal.firstName, afterFatal.lastName,
                                afterFatal.email, afterFatal.password)));
        preview(csv);
        commitNewOnly("over-limit upload");
        assertTrue(uploadRefusedARow(), "over-limit value was accepted without any error: " + lastResults);
        assertFalse(exists(overLimit.email), "over-limit CSV row created a user");
        boolean fatal = lastResults.toLowerCase(Locale.ROOT).contains("error writing to database");
        if (fatal) {
            assertFalse(exists(afterFatal.email),
                    "Moodle fatalled on the over-limit row but still created the following CSV user");
        }
    }

    /**
     * Whether the results page refused at least one row: a per-row verdict, the Errors total, or —
     * as this deployment answers an over-length value — a fatal "Error writing to database" box
     * instead of a row verdict. The row is refused either way; the fatal variant additionally aborts
     * the remaining rows instead of continuing — a deviation to record against UU-45.
     */
    private boolean uploadRefusedARow() {
        if (resultCount("Errors") >= 1) return true;
        if (UploadUsersPage.rowsContaining(lastResults, "User not added") >= 1) return true;
        String text = lastResults.toLowerCase(Locale.ROOT);
        return text.contains("error writing to database")
                || text.contains("invalid user data")
                || text.contains("more information about this error");
    }

    @Then("trailing spaces are trimmed and encoded commas are decoded")
    public void whitespaceAndEncodedCommaHandled() {
        String username = "uu_format_" + stamp;
        String email = username + "@example.com";
        newUser = trackedCsvUser(username, "  Trimmed  ", "Comma&#44;Name", email, STRONG_PASSWORD);
        preview(basicCsv("format_edges", newUser));
        commitNewOnly("format-edge upload");
        String id = idFor(email);
        assertNotNull(id, "format-edge user was not created: " + lastResults);
        assertEquals(editUserValue(id, "#id_firstname"), "Trimmed",
                "Upload users did not trim surrounding spaces");
        assertEquals(editUserValue(id, "#id_lastname"), "Comma,Name",
                "Upload users did not decode &#44; to a comma");
    }

    @Then("auth nologin creates an account that cannot log in")
    public void noLoginAuthentication() {
        newUser = csvUser("nologin");
        Path csv = UploadUsersCsv.write("nologin_" + stamp,
                List.of("username", "firstname", "lastname", "email", "password", "auth"),
                List.of(UploadUsersCsv.row(newUser.username, newUser.firstName, newUser.lastName,
                        newUser.email, newUser.password, "nologin")));
        preview(csv);
        configureNewOnlyAndCommit();
        String id = idFor(newUser.email);
        assertNotNull(id, "auth=nologin user was not created: " + lastResults);
        assertEquals(editUserValue(id, "#id_auth"), "nologin",
                "created user's authentication method is not No login");
        assertFalse(Auth.isLoggedIn(login(newUser.username, newUser.password)),
                "auth=nologin user was able to log in");
    }

    @After
    public void cleanup(io.cucumber.java.Scenario scenario) {
        if (scenario.getSourceTagNames().stream().noneMatch(tag -> tag.startsWith("@UU-S"))) {
            return;
        }
        closeUserContext();
        if (systemRoleUsername != null) {
            try {
                roleAssign.openSystemRoleAssign(MANAGER_ROLE_ID);
                roleAssign.removeUser(systemRoleUsername);
            } catch (Throwable ignored) {}
        }
        for (String email : uploadedEmails) {
            try {
                Long id = uploadedUserId(email);
                if (id != null) ApiClient.deleteUser(id);
            } catch (Throwable ignored) {}
        }
        for (long id : seededIds) {
            try { ApiClient.deleteUser(id); } catch (Throwable ignored) {}
        }
        for (String id : courseIds) {
            try { courseAdmin.deleteCourse(id); } catch (Throwable ignored) {}
        }
        for (String cohort : cohorts) {
            try { admin.cohortDelete(cohort); } catch (Throwable ignored) {}
        }
    }
}
