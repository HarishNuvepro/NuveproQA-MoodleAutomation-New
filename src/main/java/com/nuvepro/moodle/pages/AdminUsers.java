package com.nuvepro.moodle.pages;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.WaitForSelectorState;
import com.nuvepro.moodle.helpers.CrossProcessLock;
import com.nuvepro.moodle.helpers.UserSpec;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Standard Moodle user administration: Add user form (/user/editadvanced.php),
 * user list (/admin/user.php), edit/suspend/delete. No cloudlabs — pure Moodle core.
 */
public class AdminUsers extends BasePage {
    /** Report-builder filters on /admin/user.php are not safe under concurrent admin sessions. */
    private static final ReentrantLock BROWSE_USERS_FILTER_LOCK = new ReentrantLock(true);
    private static final CrossProcessLock BROWSE_USERS_FILTER_CROSS =
            CrossProcessLock.named("admin-browse-users-filter");
    public AdminUsers(Page page) {
        super(page);
    }

    public void openAddUser() {
        navigate("/user/editadvanced.php?id=-1&course=1");
        page.locator("#id_username").first().waitFor(new Locator.WaitForOptions()
                .setState(WaitForSelectorState.VISIBLE).setTimeout(30_000));
    }

    /** Fill the add-user form (does not submit). */
    public void fillNewUser(String username, String pass, String first, String last, String email) {
        page.locator("#id_username").fill(username);
        Locator gen = page.locator("#id_createpassword");                 // "Generate password and notify"
        if (gen.count() > 0 && gen.first().isChecked()) gen.first().uncheck();
        page.waitForTimeout(300);
        // #id_newpassword is a Moodle "passwordunmask" input that stays hidden (masked/behind the
        // generate-password toggle), so fill() would time out — set the value via JS + fire events.
        Locator np = page.locator("#id_newpassword");
        if (np.count() > 0) {
            np.first().evaluate("(el, v) => { el.value = v;"
                    + " el.dispatchEvent(new Event('input', {bubbles:true}));"
                    + " el.dispatchEvent(new Event('change', {bubbles:true})); }", pass);
        }
        page.locator("#id_firstname").fill(first);
        page.locator("#id_lastname").fill(last);
        page.locator("#id_email").fill(email);
    }

    public void fillNewUser(UserSpec spec) {
        fillNewUser(spec.username, spec.password, spec.firstName, spec.lastName, spec.email);
    }

    public void setForcePasswordChange() {
        Locator box = page.locator("#id_preference_auth_forcepasswordchange");
        if (box.count() > 0) box.first().check();
    }

    public void submit() {
        clickSubmitLeaving("editadvanced.php");
    }

    /** True if the add-user form is showing validation/error messages (still on the form). */
    public boolean hasFormError() {
        String body = page.locator("body").innerText().toLowerCase();
        return page.locator("[id^='id_error_'], .form-control-feedback, .invalid-feedback").count() > 0
                || body.contains("required")
                || body.contains("already") && body.contains("exist")
                || body.contains("must supply")
                || body.contains("invalid");
    }

    /** Add-user fields that must report their own error when left empty (label -> form element name). */
    public static final java.util.Map<String, String> REQUIRED_ADD_USER_FIELDS = java.util.Map.of(
            "username", "username",
            "password", "newpassword",
            "first name", "firstname",
            "last name", "lastname",
            "email", "email");

    /** Visible error text per add-user field (label -> text); fields without an error are omitted. */
    public java.util.Map<String, String> addUserFieldErrors() {
        java.util.Map<String, String> errors = new java.util.TreeMap<>();
        for (int attempt = 0; attempt < 15; attempt++) {
            errors.clear();
            collectAddUserFieldErrors(errors);
            if (!errors.isEmpty() || leftTheForm()) {
                return errors;
            }
            page.waitForTimeout(400);
        }
        collectAddUserFieldErrors(errors);
        return errors;
    }

    private void collectAddUserFieldErrors(java.util.Map<String, String> errors) {
        for (java.util.Map.Entry<String, String> field : REQUIRED_ADD_USER_FIELDS.entrySet()) {
            Object text = page.evaluate("""
                    (name) => {
                      const el = document.getElementById('id_error_' + name);
                      if (!el) return '';
                      return (el.innerText || el.textContent || '').trim();
                    }
                    """, field.getValue());
            String value = text == null ? "" : String.valueOf(text).trim();
            if (!value.isEmpty()) {
                errors.put(field.getKey(), value);
            }
        }
        mergePasswordError(errors, passwordFieldFeedbackText());
        mergePasswordError(errors, passwordNotificationText());
    }

    private static void mergePasswordError(java.util.Map<String, String> errors, String text) {
        if (text == null || text.isBlank()) return;
        errors.merge("password", text.trim(), (a, b) -> a.contains(b) ? a : a + " " + b);
    }

    /** Password-unmask / fitem feedback (Moodle sometimes omits {@code #id_error_newpassword}). */
    private String passwordFieldFeedbackText() {
        Object text = page.evaluate("""
                () => {
                  const root = document.getElementById('fitem_id_newpassword')
                      || document.querySelector('[data-fieldtype="passwordunmask"]')?.closest('.fitem');
                  if (!root) return '';
                  for (const sel of ['.form-control-feedback', '.invalid-feedback', '[id^="id_error_"]']) {
                    const el = root.querySelector(sel);
                    if (el) {
                      const t = (el.innerText || el.textContent || '').trim();
                      if (t.length > 0) return t;
                    }
                  }
                  return '';
                }
                """);
        return text == null ? "" : String.valueOf(text).trim();
    }

    private String passwordNotificationText() {
        Object text = page.evaluate("""
                () => {
                  const nodes = [...document.querySelectorAll(
                      '.notifyproblem, .alert-danger, .alert-warning, [role="alert"]')];
                  for (const el of nodes) {
                    const t = (el.innerText || el.textContent || '').trim();
                    if (/password/i.test(t)) return t;
                  }
                  return '';
                }
                """);
        return text == null ? "" : String.valueOf(text).trim();
    }

    /** Keep "Generate password and notify user" off so the empty password is validated. */
    public void untickGeneratePassword() {
        Locator gen = page.locator("#id_createpassword");
        if (gen.count() > 0 && gen.first().isChecked()) gen.first().uncheck();
    }

    /**
     * Post the add-user form without the browser-side checks. Moodle validates username and password
     * only on the server, and its browser checks block the post while other fields are empty.
     */
    public void submitAddUserToServer() {
        page.evaluate("() => { window.__npSubmitPending = true;"
                + " document.getElementById('id_username').form.submit(); }");
        long deadline = System.currentTimeMillis() + 60_000;
        while (System.currentTimeMillis() < deadline) {
            page.waitForTimeout(300);
            try {
                if (!Boolean.TRUE.equals(page.evaluate("() => window.__npSubmitPending === true"))) {
                    page.waitForLoadState(com.microsoft.playwright.options.LoadState.DOMCONTENTLOADED);
                    return;
                }
            } catch (RuntimeException navigating) {
                // the post is committing
            }
        }
        throw new AssertionError("add-user form did not come back from the server within 60s");
    }

    /** The "The password must have at least …" policy text on the add-user form, or "" when no policy. */
    public String passwordPolicyText() {
        Object text = page.evaluate("""
                () => {
                  for (const el of document.querySelectorAll('#region-main .fitem, #region-main .form-group')) {
                    const t = (el.innerText || '').trim();
                    if (/password must have/i.test(t)) return t;
                  }
                  return '';
                }
                """);
        return text == null ? "" : String.valueOf(text).trim();
    }

    /** After a successful create, Moodle leaves the form (goes to the user's profile/preferences). */
    public boolean leftTheForm() {
        return !page.url().contains("editadvanced.php");
    }

    /** The Moodle user id from the current URL (profile/preferences after create/edit), or null. */
    public String userIdFromUrl() {
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("[?&]id=(\\d+)").matcher(page.url());
        return m.find() ? m.group(1) : null;
    }

    public void openUserList() {
        navigate("/admin/user.php");
        page.waitForTimeout(1_500);
    }

    /**
     * Filter Browse users by that exact email and return the id read from the row that actually
     * contains it, or null.
     *
     * Report-builder filter values are stored per user and survive page loads, so a value left over
     * from an earlier step silently narrows this lookup to zero rows: always Reset all, then set the
     * value, then Apply. And never fall back to "first edit link on screen" — that returns an
     * unrelated account, which callers treat as "the uploaded user exists" (and cleanup would delete).
     */
    public String findUserIdByEmail(String email) {
        BROWSE_USERS_FILTER_LOCK.lock();
        BROWSE_USERS_FILTER_CROSS.lock(15, TimeUnit.MINUTES);
        try {
            AssertionError filterFailure = null;
            for (int round = 1; round <= 4; round++) {
                try {
                    return findUserIdByEmailFiltered(email);
                } catch (AssertionError e) {
                    filterFailure = e;
                    if (round < 4 && String.valueOf(e.getMessage()).contains("Browse users filter did not apply")) {
                        System.out.println("[AdminUsers] email filter retry " + round + " for " + email);
                        page.waitForTimeout(1_500);
                        continue;
                    }
                    throw e;
                }
            }
            if (filterFailure != null) throw filterFailure;
            return null;
        } finally {
            BROWSE_USERS_FILTER_CROSS.unlock();
            BROWSE_USERS_FILTER_LOCK.unlock();
        }
    }

    private String findUserIdByEmailFiltered(String email) {
        openUserList();
        waitForUserTable(30_000);
        resetUserListFilters();
        boolean applied = applyReportBuilderTextFilter("user:email", "3", email);   // Is equal to
        if (!applied) applyLegacyEmailFilter(email);
        for (int i = 0; i < 12; i++) {
            String id = userIdFromRowContaining(email);
            if (id != null) return id;
            page.waitForTimeout(1_000);
        }
        // "No row" is only trustworthy while the filter demonstrably narrowed the table. Without that
        // proof this would report every account as missing, so fail loudly instead of silently.
        if (!applied || !filtersApplied()) {
            throw new AssertionError("Browse users filter did not apply, so '" + email
                    + "' could not be looked up (filter button reads '" + filterButtonLabel()
                    + "', url=" + page.url() + ")");
        }
        return null;
    }

    /**
     * Exact-email id lookup via the Browse users filter (one filtered read per account).
     *
     * @return the user id, or null when the account really is absent
     */
    public String lookupUserIdByEmail(String email) {
        return findUserIdByEmail(email);
    }

    /** Clear every report-builder filter ("Reset all") plus any legacy user_filtering filters. */
    public void resetUserListFilters() {
        for (int round = 0; round < 3; round++) {
            openFiltersPanel();
            Object reset = page.evaluate("""
                    () => {
                      const button = document.querySelector("input[name='resetfilters']");
                      if (!button) return 'no-button';
                      button.click();
                      return 'ok';
                    }
                    """);
            if ("ok".equals(String.valueOf(reset))) {
                page.waitForTimeout(1_500);
                waitForUserTable(20_000);
                for (int i = 0; i < 8 && filtersApplied(); i++) {
                    page.waitForTimeout(500);
                }
                if (!filtersApplied()) return;
                System.out.println("[AdminUsers] Reset all left filters active: " + filterButtonLabel()
                        + " (retry " + (round + 1) + ")");
                openUserList();
                continue;
            }
            if (clickFirstVisible("#id_removeall, input[name='removeall'], input[value='Remove all filters']")) {
                page.waitForTimeout(1_200);
                waitForUserTable(15_000);
                if (!filtersApplied()) return;
            }
            openUserList();
        }
    }

    /** The Filters dropdown holds the filter form; open it so Moodle initialises the fields. */
    private void openFiltersPanel() {
        Locator toggle = page.locator("#dropdownFiltersButton, button[title='Filters']");
        if (toggle.count() == 0) return;
        for (int attempt = 0; attempt < 3; attempt++) {
            try {
                Locator form = page.locator("[data-region='filters-form']").first();
                if (form.count() > 0 && form.isVisible()) return;
            } catch (RuntimeException ignored) {
                // fall through and click the toggle again
            }
            try {
                toggle.first().click(new Locator.ClickOptions().setTimeout(5_000));
            } catch (RuntimeException ignored) {
                // the fields are driven from JS below, so a collapsed panel is still usable
            }
            page.waitForTimeout(700);
        }
    }

    /** True when Browse users reports at least one active filter ("Filters (1)"). */
    private boolean filtersApplied() {
        return filterButtonLabel().matches("(?s).*\\(\\s*\\d+\\s*\\).*");
    }

    private String filterButtonLabel() {
        Locator label = page.locator("[data-region='filter-button-label']").first();
        if (label.count() == 0) return "";
        try {
            return label.innerText().replaceAll("\\s+", " ").trim();
        } catch (RuntimeException e) {
            return "";
        }
    }

    private void applyLegacyEmailFilter(String email) {
        Locator field = page.locator("#id_email");               // user_filtering email text field
        if (field.count() == 0) return;
        field.first().fill(email);
        Locator apply = page.locator("#id_addfilter");
        if (apply.count() > 0) apply.first().click();
        page.waitForTimeout(1_500);
    }

    /** Id from the first listing row whose text contains {@code needle}, or null. */
    private String userIdFromRowContaining(String needle) {
        for (int attempt = 0; attempt < 6; attempt++) {
            try {
                Object id = page.evaluate("""
                        (needle) => {
                          const wanted = String(needle).trim().toLowerCase();
                          for (const row of document.querySelectorAll('table tbody tr')) {
                            if (!(row.innerText || '').toLowerCase().includes(wanted)) continue;
                            for (const link of row.querySelectorAll('a[href*="id="]')) {
                              const found = (link.getAttribute('href') || '').match(/[?&]id=(\\d+)/);
                              if (found) return found[1];
                            }
                          }
                          return '';
                        }
                        """, needle);
                String value = id == null ? "" : String.valueOf(id).trim();
                return value.isEmpty() ? null : value;
            } catch (RuntimeException e) {
                if (attempt == 5) throw e;
                page.waitForTimeout(400);
            }
        }
        return null;
    }

    private void waitForUserTable(int timeoutMs) {
        try {
            page.locator("table tbody tr, [data-region='core_table/dynamic']").first()
                    .waitFor(new Locator.WaitForOptions()
                            .setState(WaitForSelectorState.VISIBLE).setTimeout(timeoutMs));
        } catch (RuntimeException ignored) {
            // an empty result set is a valid outcome
        }
    }

    private boolean clickFirstVisible(String css) {
        Locator candidates = page.locator(css);
        int n = Math.min(candidates.count(), 6);
        for (int i = 0; i < n; i++) {
            Locator candidate = candidates.nth(i);
            try {
                if (!candidate.isVisible()) continue;
                candidate.click(new Locator.ClickOptions().setTimeout(5_000));
                return true;
            } catch (RuntimeException ignored) {
                // try the next candidate
            }
        }
        return false;
    }

    public boolean userListedByEmail(String email) {
        // Rely on the filtered edit-link lookup only. (A body-text check would false-positive on the
        // email we just typed into the filter field.)
        return findUserIdByEmail(email) != null;
    }

    /**
     * Whether a user's profile page renders (account exists & is viewable). Reliable, id-based —
     * a deleted/invalid user shows an error page instead of the profile. Used as a robust proxy for
     * "listed in the user list" because /admin/user.php is a report-builder table whose AJAX search
     * does not filter deterministically.
     */
    public boolean userProfilePresent(String userId) {
        for (int attempt = 0; attempt < 4; attempt++) {
            try {
                navigate("/user/profile.php?id=" + userId);
                page.waitForTimeout(1_200);
                String body = page.locator("body").innerText().toLowerCase();
                boolean error = safeCount(".alert-danger, .errorbox, .notifyproblem") > 0
                        || body.contains("can not find")
                        || body.contains("cannot find")
                        || body.contains("invalid user")
                        || body.contains("deleted")
                        || body.contains("nopermissions");
                if (error) return false;
                return safeCount("#page-user-profile, .userprofile, .profile_tree, [data-region='profile']") > 0;
            } catch (RuntimeException e) {
                if (attempt == 3) throw e;
                page.waitForTimeout(800);
            }
        }
        return false;
    }

    private void expandUserEditForm() {
        Locator exp = page.locator(".collapsible-actions a.collapseexpand, a:has-text('Expand all')");
        if (exp.count() > 0) {
            try {
                if (exp.first().isVisible() && "false".equals(exp.first().getAttribute("aria-expanded"))) {
                    exp.first().click();
                    page.waitForTimeout(400);
                }
            } catch (RuntimeException ignored) {}
        }
        page.evaluate("() => {"
                + "  document.querySelectorAll('fieldset.collapsible.collapsed, fieldset.collapsed')"
                + "    .forEach(fs => fs.classList.remove('collapsed'));"
                + "  document.querySelectorAll('fieldset.collapsible .fcontainer, fieldset .collapse')"
                + "    .forEach(el => { el.style.display = 'block'; el.classList.add('show'); });"
                + "}");
    }

    /** Set (or clear) the suspended flag on a user's edit form and save. Retries once if save did not stick. */
    public void setSuspended(String userId, boolean suspend) {
        for (int attempt = 1; attempt <= 2; attempt++) {
            openEditUser(userId);
            expandUserEditForm();
            setSuspendedCheckbox(suspend);
            if (!suspendedCheckboxMatches(suspend)) {
                setSuspendedCheckbox(suspend);
            }
            if (!suspendedCheckboxMatches(suspend)) {
                throw new AssertionError("could not set Suspended checkbox to " + suspend
                        + " for user id=" + userId + " (attempt " + attempt + ")");
            }
            clickSubmitLeaving("editadvanced.php");
            page.waitForTimeout(800);
            if (editFormShowsSuspended(userId) == suspend) return;
            System.out.println("[AdminUsers] setSuspended did not persist for id=" + userId
                    + " want=" + suspend + " attempt=" + attempt + " — retrying");
        }
        throw new AssertionError("Suspended=" + suspend + " did not persist for user id=" + userId);
    }

    private void setSuspendedCheckbox(boolean suspend) {
        Locator susp = page.locator("#id_suspended");
        if (susp.count() == 0) {
            throw new AssertionError("#id_suspended not found on user edit form");
        }
        try { susp.first().scrollIntoViewIfNeeded(); } catch (RuntimeException ignored) {}
        Locator label = page.locator("label[for='id_suspended']");
        try {
            boolean checked = susp.first().isChecked();
            if (checked == suspend) return;
            if (label.count() > 0 && label.first().isVisible()) {
                label.first().click(new Locator.ClickOptions().setForce(true));
            } else if (suspend) {
                susp.first().check(new Locator.CheckOptions().setForce(true));
            } else {
                susp.first().uncheck(new Locator.UncheckOptions().setForce(true));
            }
        } catch (RuntimeException ignored) {}
        if (!suspendedCheckboxMatches(suspend)) {
            susp.first().evaluate("(el, on) => {"
                    + "  el.checked = !!on;"
                    + "  el.dispatchEvent(new Event('input', {bubbles:true}));"
                    + "  el.dispatchEvent(new Event('change', {bubbles:true}));"
                    + "}", suspend);
        }
        page.waitForTimeout(200);
    }

    private boolean suspendedCheckboxMatches(boolean want) {
        Locator susp = page.locator("#id_suspended");
        try {
            return susp.count() > 0 && susp.first().isChecked() == want;
        } catch (RuntimeException e) {
            return false;
        }
    }

    public boolean editFormShowsSuspended(String userId) {
        openEditUser(userId);
        expandUserEditForm();
        return suspendedCheckboxMatches(true);
    }

    /**
     * Browse-users report-builder: Filters → Suspended = Yes (+ username), then assert the user row.
     * (The table has no Suspended column — the Suspended filter is how the list "flags" them.)
     */
    public boolean userAppearsWhenFilteredSuspendedYes(String username) {
        for (int attempt = 0; attempt < 8; attempt++) {
            if (tryUserAppearsWhenFilteredSuspendedYes(username)) return true;
            page.waitForTimeout(2_000);
        }
        return tryUserAppearsWhenFilteredSuspendedYes(username);
    }

    private boolean tryUserAppearsWhenFilteredSuspendedYes(String username) {
        openUserList();
        waitForUserTable(30_000);
        resetUserListFilters();
        if (!applySuspendedYesAndUsernameFilter(username)) return false;
        for (int i = 0; i < 12; i++) {
            if (rowContainsUsername(username)) return true;
            page.waitForTimeout(500);
        }
        return rowContainsUsername(username);
    }

    /** Report-builder: Suspended = Yes and Username is equal to {@code username}. */
    private boolean applySuspendedYesAndUsernameFilter(String username) {
        for (int attempt = 1; attempt <= 5; attempt++) {
            openFiltersPanel();
            Object applied = page.evaluate("""
                    (username) => {
                      const suspendedOp = document.querySelector("select[name='user:suspended_operator']");
                      const suspendedVal = document.querySelector(
                          "select[name='user:suspended_value'], input[name='user:suspended_value']");
                      const operator = document.querySelector("select[name='user:username_operator']");
                      const input = document.querySelector("input[name='user:username_value']");
                      if (!suspendedOp || !operator || !input) return 'no-field';
                      if (suspendedVal) {
                        suspendedOp.value = '3';
                        suspendedOp.dispatchEvent(new Event('change', {bubbles: true}));
                        suspendedVal.removeAttribute('disabled');
                        suspendedVal.disabled = false;
                        suspendedVal.value = '1';
                        suspendedVal.dispatchEvent(new Event('change', {bubbles: true}));
                      } else {
                        suspendedOp.value = '1';
                        suspendedOp.dispatchEvent(new Event('change', {bubbles: true}));
                      }
                      operator.value = '3';
                      operator.dispatchEvent(new Event('change', {bubbles: true}));
                      input.removeAttribute('disabled');
                      input.disabled = false;
                      input.value = username;
                      input.dispatchEvent(new Event('input', {bubbles: true}));
                      input.dispatchEvent(new Event('change', {bubbles: true}));
                      const form = operator.closest('form');
                      const apply = form && form.querySelector("input[name='submitbutton']");
                      if (!apply) return 'no-apply';
                      apply.click();
                      return 'ok';
                    }
                    """, username);
            if (!"ok".equals(String.valueOf(applied))) {
                page.waitForTimeout(800);
                continue;
            }
            page.waitForTimeout(2_500);
            waitForUserTable(20_000);
            for (int i = 0; i < 10; i++) {
                if (filtersApplied()) return true;
                page.waitForTimeout(400);
            }
        }
        return false;
    }

    /** Change a user's email via the edit form and save. */
    public void changeEmail(String userId, String newEmail) {
        openEditUser(userId);
        page.locator("#id_email").fill(newEmail);
        clickSubmitLeaving("editadvanced.php");
    }

    public String currentEmailValue(String userId) {
        openEditUser(userId);
        return page.locator("#id_email").inputValue();
    }

    public void changeFirstName(String userId, String name) {
        openEditUser(userId);
        page.locator("#id_firstname").fill(name);
        clickSubmitLeaving("editadvanced.php");
    }

    public String currentFirstName(String userId) {
        openEditUser(userId);
        return page.locator("#id_firstname").inputValue();
    }

    /** Delete a user via the admin UI confirmation page (/user/delete.php). */
    /**
     * Delete through the admin UI: Browse users' delete link ({@code admin/user.php?delete=}) and its
     * "Delete" confirmation.
     *
     * @return true when the confirmation was found and submitted
     */
    public boolean deleteUserViaUi(String userId) {
        navigate("/admin/user.php");
        Object sk = page.evaluate("() => (window.M && M.cfg && M.cfg.sesskey) || ''");
        for (String path : java.util.List.of(
                "/admin/user.php?delete=" + userId + "&sesskey=" + sk,
                "/user/delete.php?id=" + userId + "&sesskey=" + sk)) {
            navigate(path);
            page.waitForTimeout(1_000);
            Locator confirm = page.locator("#notice button:has-text('Delete'), #notice input[value='Delete'], "
                    + "#notice button:has-text('Yes'), #notice input[value='Yes'], "
                    + "#region-main button:has-text('Delete'), #region-main input[value='Delete']");
            for (int i = 0; i < confirm.count(); i++) {
                try {
                    if (confirm.nth(i).isVisible()) {
                        System.out.println("[AdminUsers] UI delete of user " + userId + " confirmed at " + page.url());
                        submitAndAwaitNewPage(confirm.nth(i));
                        page.waitForTimeout(1_000);
                        return true;
                    }
                } catch (RuntimeException ignored) {}
            }
        }
        System.out.println("[AdminUsers] no delete confirmation for user " + userId + " (url=" + page.url() + ")");
        return false;
    }

    // ---- cohorts (/cohort) ----
    public void cohortCreate(String name, String idnumber) {
        navigate("/cohort/edit.php?contextid=1");
        page.locator("#id_name").waitFor(new Locator.WaitForOptions()
                .setState(WaitForSelectorState.VISIBLE).setTimeout(30_000));
        page.locator("#id_name").fill(name);
        page.locator("#id_idnumber").fill(idnumber);
        submitAndAwaitNewPage(page.locator("#id_submitbutton"));
        page.waitForTimeout(1_500);
    }

    public boolean cohortListed(String name) {
        navigate("/cohort/index.php");
        page.waitForTimeout(1_500);
        return page.locator("tr:has-text('" + name + "')").count() > 0;
    }

    /** Create a cohort; returns true if it succeeded (left the edit form). */
    public boolean cohortCreateSucceeded(String name, String idnumber) {
        navigate("/cohort/edit.php?contextid=1");
        page.locator("#id_name").waitFor();
        page.locator("#id_name").fill(name);
        if (idnumber != null && page.locator("#id_idnumber").count() > 0) page.locator("#id_idnumber").fill(idnumber);
        submitAndAwaitNewPage(page.locator("#id_submitbutton"));
        page.waitForTimeout(1_500);
        return !page.url().contains("edit.php");
    }

    public int cohortCount(String name) {
        navigate("/cohort/index.php");
        page.waitForTimeout(1_500);
        return page.locator("tr").filter(new Locator.FilterOptions().setHasText(name)).count();
    }

    /** Delete a cohort by clicking the in-row Delete action (data-action=cohort-delete; JS resolves
     *  the real cohort id and opens a confirm modal). */
    public void cohortDelete(String name) {
        navigate("/cohort/index.php");
        page.waitForTimeout(1_500);
        Locator row = page.locator("tr:has-text('" + name + "')");
        if (row.count() == 0) return;
        // the delete action lives inside a per-row actions dropdown — open it first
        Locator toggle = row.first().locator("[data-toggle='dropdown'], .dropdown-toggle, a[role='button']");
        if (toggle.count() > 0) { toggle.first().click(); page.waitForTimeout(500); }
        Locator del = row.first().locator("a[data-action='cohort-delete']");
        if (del.count() > 0) {
            del.first().click();
            page.waitForTimeout(800);
            try { confirmModal(); } catch (Throwable ignored) {}
            page.waitForTimeout(1_500);
        }
    }

    /**
     * Numeric cohort id from the index row. Prefers a real {@code id=123} href (edit/assign)
     * over JS template hrefs that still contain {@code :id}.
     */
    public String cohortIdByName(String name) {
        navigate("/cohort/index.php");
        page.waitForTimeout(1_500);
        Locator row = page.locator("tr").filter(new Locator.FilterOptions().setHasText(name));
        if (row.count() == 0) return null;
        Object id = row.first().evaluate("(el) => {"
                + "  const links = [...el.querySelectorAll('a[href]')];"
                + "  for (const a of links) {"
                + "    const m = (a.getAttribute('href') || '').match(/[?&]id=(\\d+)/);"
                + "    if (m) return m[1];"
                + "  }"
                + "  const node = el.querySelector('[data-id], [data-cohortid], [data-cohort-id]');"
                + "  if (node) return node.getAttribute('data-id') || node.getAttribute('data-cohortid')"
                + "      || node.getAttribute('data-cohort-id') || '';"
                + "  return '';"
                + "}");
        return (id == null || id.toString().isBlank()) ? null : id.toString();
    }

    public boolean cohortAddMember(String cohortId, String username) {
        return cohortAddMember(cohortId, username, null);
    }

    public boolean cohortAddMember(String cohortId, String username, String email) {
        for (String term : new String[] {username, email, username + "@example.com"}) {
            if (term == null || term.isBlank()) continue;
            if (tryCohortMoveMember(cohortId, term, true)) {
                for (int i = 0; i < 12; i++) {
                    if (cohortHasMember(cohortId, username)) return true;
                    page.waitForTimeout(1_000);
                }
            }
        }
        return false;
    }

    public boolean cohortRemoveMember(String cohortId, String username) {
        if (!tryCohortMoveMember(cohortId, username, false)) {
            tryCohortMoveMember(cohortId, username + "@example.com", false);
        }
        for (int i = 0; i < 12; i++) {
            if (!cohortHasMember(cohortId, username)) return true;
            page.waitForTimeout(1_000);
        }
        return !cohortHasMember(cohortId, username);
    }

    public boolean cohortHasMember(String cohortId, String username) {
        openCohortAssign(cohortId);
        return selectorHasUser("#removeselect", username);
    }

    private void openCohortAssign(String cohortId) {
        navigate("/cohort/assign.php?id=" + cohortId);
        page.locator("#addselect, #removeselect, [data-fieldtype='autocomplete']").first()
                .waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE).setTimeout(30_000));
    }

    private boolean tryCohortMoveMember(String cohortId, String searchTerm, boolean add) {
        openCohortAssign(cohortId);
        String searchSel = add ? "#addselect_searchtext" : "#removeselect_searchtext";
        String listSel = add ? "#addselect" : "#removeselect";
        String btnSel = add ? "input#add, input[name='add']" : "input#remove, input[name='remove']";
        Locator search = page.locator(searchSel);
        if (search.count() > 0) {
            search.first().click();
            search.first().fill("");
            search.first().fill(searchTerm);
            page.evaluate("""
                    () => {
                      const el = document.querySelector('#addselect_searchtext, #removeselect_searchtext');
                      if (!el) return;
                      el.dispatchEvent(new Event('input', {bubbles: true}));
                      el.dispatchEvent(new KeyboardEvent('keyup', {bubbles: true}));
                    }
                    """);
        }
        Locator list = page.locator(listSel);
        Locator btn = page.locator(btnSel);
        if (list.count() == 0 || btn.count() == 0) return false;
        String needle = searchTerm.toLowerCase(java.util.Locale.ROOT);
        long deadline = System.currentTimeMillis() + 45_000;
        while (System.currentTimeMillis() < deadline) {
            Locator opts = list.locator("option:not([disabled])");
            int n = opts.count();
            for (int i = 0; i < n; i++) {
                Locator opt = opts.nth(i);
                try {
                    String text = opt.innerText().toLowerCase(java.util.Locale.ROOT);
                    String value = opt.getAttribute("value");
                    if (value == null || value.isBlank() || "0".equals(value)) continue;
                    if (!text.contains(needle)) continue;
                    if (value != null && !value.isBlank()) list.selectOption(value);
                    else opt.click();
                    page.waitForTimeout(400);
                    if (btn.first().isEnabled()) {
                        clickSlowSubmit(btn.first());
                        page.waitForTimeout(1_500);
                        return true;
                    }
                } catch (RuntimeException ignored) {
                    // AJAX refresh can replace the list mid-loop
                }
            }
            page.waitForTimeout(600);
        }
        return false;
    }

    private boolean selectorHasUser(String selectCss, String username) {
        Locator opt = page.locator(selectCss + " option").filter(new Locator.FilterOptions().setHasText(username));
        if (opt.count() > 0) return true;
        return page.locator(selectCss).innerText().contains(username);
    }

    /**
     * Narrow the Moodle 4.5 report-builder user list (/admin/user.php) via Filters.
     * Do NOT type into the site-admin header Search — that does not filter this table.
     * Prefer Username "Is equal to"; fall back to Email "Contains" / legacy #id_email.
     */
    public void filterUserList(String query) {
        openUserList();
        waitForUserTable(30_000);
        resetUserListFilters();
        if (applyReportBuilderTextFilter("user:username", "3", query)) return;   // Is equal to
        if (applyReportBuilderTextFilter("user:email", "1", query)) return;      // Contains
        applyLegacyEmailFilter(query);
    }

    /**
     * Open Filters, set a report-builder text filter (username/email) and Apply.
     *
     * Driven entirely from the DOM: the operator select and its value input live in a Bootstrap
     * dropdown and the input stays {@code disabled} until the operator handler runs, so Playwright's
     * visibility-aware selectOption/fill time out whenever the panel is slow to open — and a silent
     * timeout here reports every account as missing.
     */
    private boolean applyReportBuilderTextFilter(String fieldPrefix, String operatorValue, String value) {
        for (int attempt = 1; attempt <= 5; attempt++) {
            if (submitReportBuilderTextFilter(fieldPrefix, operatorValue, value)) return true;
            System.out.println("[AdminUsers] filter " + fieldPrefix + " did not stick (attempt "
                    + attempt + ", button reads '" + filterButtonLabel() + "')");
            page.waitForTimeout(1_000);
        }
        return false;
    }

    /**
     * One attempt. Resetting the filters re-renders the panel, so the form nodes this queries can be
     * replaced between opening the panel and clicking Apply — the caller retries until Browse users
     * reports an active filter, because a click on a detached form leaves the table unfiltered.
     */
    private boolean submitReportBuilderTextFilter(String fieldPrefix, String operatorValue, String value) {
        openFiltersPanel();
        Object applied = page.evaluate("""
                ([prefix, operatorValue, value]) => {
                  const operator = document.querySelector(`select[name="${prefix}_operator"]`);
                  const input = document.querySelector(`input[name="${prefix}_value"]`);
                  if (!operator || !input) return 'no-field';
                  operator.value = operatorValue;
                  operator.dispatchEvent(new Event('change', {bubbles: true}));
                  input.removeAttribute('disabled');
                  input.disabled = false;
                  input.value = value;
                  input.dispatchEvent(new Event('input', {bubbles: true}));
                  input.dispatchEvent(new Event('change', {bubbles: true}));
                  const form = operator.closest('form');
                  if (!form) return 'no-form';
                  const apply = form.querySelector("input[name='submitbutton']");
                  if (!apply) return 'no-apply';
                  apply.click();
                  return 'ok';
                }
                """, java.util.List.of(fieldPrefix, operatorValue, value));
        if (!"ok".equals(String.valueOf(applied))) {
            System.out.println("[AdminUsers] filter " + fieldPrefix + " not applied: " + applied);
            return false;
        }
        page.waitForTimeout(2_500);
        waitForUserTable(20_000);
        for (int i = 0; i < 20; i++) {
            if (filtersApplied()) return true;
            page.waitForTimeout(400);
        }
        return filtersApplied();
    }

    public boolean userAppearsInFilteredList(String username) {
        for (int attempt = 0; attempt < 8; attempt++) {
            filterUserList(username);
            if (rowContainsUsername(username)) return true;
            page.waitForTimeout(1_500);
        }
        return rowContainsUsername(username);
    }

    private boolean rowContainsUsername(String username) {
        Locator row = page.locator("table tbody tr").filter(new Locator.FilterOptions().setHasText(username));
        if (row.count() > 0) return true;
        String emailGuess = username.contains("@") ? username : username + "@example.com";
        row = page.locator("table tbody tr").filter(new Locator.FilterOptions().setHasText(emailGuess));
        return row.count() > 0;
    }

    public boolean filteredListRowShowsSuspended(String username) {
        filterUserList(username);
        Locator row = page.locator("table tbody tr").filter(new Locator.FilterOptions().setHasText(username));
        if (row.count() == 0) {
            return false;
        }
        String html = "";
        try { html = row.first().innerHTML().toLowerCase(); } catch (RuntimeException ignored) {}
        String text = row.first().innerText().toLowerCase();
        return text.contains("suspend") || html.contains("suspend") || html.contains("fa-ban")
                || row.first().locator(".badge, .label, i.fa-ban, .text-danger, [title*='Suspend'], [title*='suspend']").count() > 0;
    }

    /**
     * Wait for Bulk user actions' user lists: Available `#id_ausers` (name `ausers[]`) and Selected
     * `#id_susers` (name `susers[]`), each headed by an "All …(n/m)" entry with value 0. The page
     * keeps the legacy dual listbox — it is NOT the Browse users report — and the action menu
     * `#id_action` sits below it.
     */
    public boolean bulkUserActionsReady(int timeoutMs) {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            try {
                page.locator("#addselect, #removeselect, #id_susers, select[name='susers[]']").first()
                        .waitFor(new Locator.WaitForOptions()
                                .setState(WaitForSelectorState.ATTACHED).setTimeout(5_000));
                return true;
            } catch (RuntimeException notRendered) {
                page.waitForTimeout(500);
            }
        }
        return false;
    }

    /** Labels of the users in the Selected box, excluding its "All selected (n/m)" header entry. */
    public String bulkSelectedUsersText() {
        String css = bulkUsesLegacyDualList() ? "#removeselect" : "#id_susers";
        Object text = page.evaluate("""
                (sel) => {
                  const box = document.querySelector(sel);
                  if (!box) return '';
                  return Array.from(box.querySelectorAll('option'))
                          .filter(option => /^\\d+$/.test(option.value || '') && option.value !== '0')
                          .map(option => (option.textContent || '').trim())
                          .join(' | ');
                }
                """, css);
        return String.valueOf(text);
    }

    private int bulkSelectedUserCount() {
        String text = bulkSelectedUsersText();
        return text.isEmpty() ? 0 : text.split("\\|").length;
    }

    /**
     * Poll (and log) the Selected box until the session's bulk selection shows up, clearing the
     * legacy user filters once on the way: the lists are built over the active filters, so a filter
     * left behind by an earlier lookup can hide a genuinely selected user.
     *
     * @return the number of users in the Selected box
     */
    public int waitForBulkSelectedUsers(int timeoutMs) {
        boolean filtersCleared = false;
        for (int waited = 0; waited <= timeoutMs; waited += 2_000) {
            int selected = bulkSelectedUserCount();
            System.out.println("[AdminUsers] bulk selection poll " + (waited / 1000) + "s selected="
                    + selected + " url=" + page.url());
            if (selected > 0) return selected;
            if (!filtersCleared && waited >= timeoutMs / 2) {
                filtersCleared = true;
                System.out.println("[AdminUsers] no selection listed - clearing legacy user filters");
                clearLegacyUserFilters();
            }
            page.waitForTimeout(2_000);
        }
        return 0;
    }

    /** Submit "Remove all filters" on a legacy user_filtering page. Leaves the bulk selection alone. */
    public boolean clearLegacyUserFilters() {
        Object clicked = page.evaluate("""
                () => {
                  const button = document.querySelector("#id_removeall, input[name='removeall']");
                  if (!button) return 'none';
                  button.click();
                  return 'ok';
                }
                """);
        if (!"ok".equals(String.valueOf(clicked))) return false;
        try {
            page.waitForLoadState(com.microsoft.playwright.options.LoadState.DOMCONTENTLOADED,
                    new Page.WaitForLoadStateOptions().setTimeout(20_000));
        } catch (RuntimeException slow) {
            // the caller polls; a slow re-render is covered there
        }
        page.waitForTimeout(800);
        return true;
    }

    /**
     * Choose an entry in "With selected users..." without running it (no Go), which is enough to show
     * the forwarded selection is actionable.
     *
     * @return whether the action menu ended up holding that value
     */
    public boolean chooseBulkAction(String label) {
        Object chosen = page.evaluate("""
                (label) => {
                  const action = document.querySelector("#id_action, select[name='action']");
                  if (!action) return 'no-menu';
                  const wanted = String(label).trim().toLowerCase();
                  for (const option of action.querySelectorAll('option')) {
                    const text = (option.textContent || '').trim().toLowerCase();
                    if (!text.includes(wanted) || !option.value || option.value === '0') continue;
                    action.value = option.value;
                    action.dispatchEvent(new Event('change', {bubbles: true}));
                    return action.value === option.value ? 'chosen' : 'not-applied';
                  }
                  return 'no-option';
                }
                """, label);
        String result = String.valueOf(chosen);
        if (!"chosen".equals(result)) {
            System.out.println("[AdminUsers] bulk action '" + label + "' -> " + result);
        }
        return "chosen".equals(result);
    }

    /** Open Bulk user actions (/admin/user/user_bulk.php). */
    public void openBulkUserActions() {
        navigate("/admin/user/user_bulk.php");
        page.locator("#addselect, #removeselect, #id_addfilter, form").first()
                .waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE).setTimeout(30_000));
        page.waitForTimeout(800);
    }

    /**
     * Filter bulk-user list by exact email, move that one user to the selected box,
     * then run a bulk action. Never uses Add all (would hit unrelated accounts).
     */
    public boolean bulkActionOnEmailContains(String emailFragment, String actionValue) {
        openBulkUserActions();
        Locator showMore = page.locator("a:has-text('Show more'), button:has-text('Show more')");
        if (showMore.count() > 0) {
            try { if (showMore.first().isVisible()) showMore.first().click(); } catch (RuntimeException ignored) {}
            page.waitForTimeout(400);
        }
        Locator email = page.locator("#id_email, input[name='email']");
        if (email.count() > 0) {
            try {
                email.first().fill(emailFragment);
            } catch (RuntimeException e) {
                email.first().evaluate("(el, v) => { el.value = v; el.dispatchEvent(new Event('change', {bubbles:true})); }",
                        emailFragment);
            }
            Locator addFilter = page.locator("#id_addfilter, input[name='addfilter'], button:has-text('Add filter')");
            if (addFilter.count() > 0) addFilter.first().click();
            page.waitForTimeout(1_500);
        }
        Locator add = page.locator("input[name='add'], #add");
        Locator opts = page.locator("#addselect option:not([disabled])");
        String needle = emailFragment.toLowerCase();
        boolean selected = false;
        for (int i = 0; i < opts.count(); i++) {
            try {
                String t = opts.nth(i).innerText().toLowerCase();
                if (t.contains(needle)) {
                    String value = opts.nth(i).getAttribute("value");
                    if (value != null && !value.isBlank()) {
                        page.locator("#addselect").selectOption(value);
                    } else {
                        opts.nth(i).click();
                    }
                    if (add.count() > 0) add.first().click();
                    page.waitForTimeout(800);
                    selected = true;
                    break;
                }
            } catch (RuntimeException ignored) {}
        }
        if (!selected) return false;
        Locator action = page.locator("select[name='action'], #id_action, #menueaction");
        if (action.count() == 0) return false;
        if (!selectAction(action.first(), actionValue)) return false;
        Locator go = page.locator("input[name='doaction'], #id_doaction, button:has-text('Go'), input[value='Go']");
        if (go.count() == 0) return false;
        go.first().click();
        page.waitForTimeout(1_500);
        clickDeleteConfirm();
        return true;
    }

    /**
     * Make Bulk user actions' Selected list hold these accounts (id -> email). Uses the same
     * {@code #addselect} / {@code #removeselect} controls as {@link #bulkActionOnEmailContains}.
     *
     * @return user ids now in the Selected list
     */
    public java.util.List<String> bulkSelectExactly(java.util.Map<Long, String> idToEmail) {
        openBulkUserActions();
        clearLegacyUserFilters();
        page.waitForTimeout(800);
        bulkClearSelectedUsers();
        java.util.Set<String> wanted = new java.util.LinkedHashSet<>();
        for (java.util.Map.Entry<Long, String> account : idToEmail.entrySet()) {
            String id = String.valueOf(account.getKey());
            wanted.add(id);
            bulkAddUserToSelection(account.getValue(), id);
            if (!bulkSelectedIds().contains(id)) {
                System.out.println("[AdminUsers] bulk: could not add user " + id + " ("
                        + account.getValue() + ") — available=" + bulkAvailableIds()
                        + " selected=" + bulkSelectedIds());
            }
        }
        bulkDropSelectedExcept(wanted);
        java.util.List<String> selected = bulkSelectedIds();
        System.out.println("[AdminUsers] bulk Selected ids=" + selected + " (wanted " + wanted + ")");
        return selected;
    }

    private boolean bulkUsesLegacyDualList() {
        return safeCount("#addselect") > 0;
    }

    private java.util.List<String> bulkAvailableIds() {
        if (bulkUsesLegacyDualList()) {
            return bulkListIdsIn("#addselect");
        }
        return bulkListIdsIn("#id_ausers", "select[name='ausers[]']");
    }

    private java.util.List<String> bulkSelectedIds() {
        if (bulkUsesLegacyDualList()) {
            return bulkListIdsIn("#removeselect");
        }
        return bulkListIdsIn("#id_susers", "select[name='susers[]']");
    }

    private void bulkClearSelectedUsers() {
        bulkDropSelectedExcept(java.util.Set.of());
    }

    /** Move accounts out of Selected when they are not in {@code keepIds} (empty = clear all). */
    private void bulkDropSelectedExcept(java.util.Set<String> keepIds) {
        for (int attempt = 0; attempt < 12; attempt++) {
            java.util.List<String> stray = bulkSelectedIds().stream()
                    .filter(id -> !keepIds.contains(id))
                    .toList();
            if (stray.isEmpty()) return;
            bulkMoveFromSelectedToAvailable(stray);
        }
        java.util.List<String> left = bulkSelectedIds().stream()
                .filter(id -> !keepIds.contains(id))
                .toList();
        if (!left.isEmpty()) {
            System.out.println("[AdminUsers] bulk: could not deselect stale ids " + left
                    + " (session Selected=" + bulkSelectedIds() + ")");
        }
    }

    private void bulkMoveFromSelectedToAvailable(java.util.List<String> userIds) {
        if (userIds.isEmpty()) return;
        if (bulkUsesLegacyDualList()) {
            page.evaluate("""
                    (ids) => {
                      const box = document.querySelector('#removeselect');
                      if (!box) return;
                      for (const o of box.options) {
                        o.selected = ids.includes(o.value);
                      }
                    }
                    """, userIds);
            Locator remove = page.locator("input[name='remove'], #remove");
            if (remove.count() > 0) {
                clickSlowSubmit(remove.first());
                page.waitForTimeout(900);
            }
            return;
        }
        moveBulkOptions("#id_susers", userIds, "removesel");
    }

    /**
     * Move one account from Available to Selected. Clears the email filter before each user so an
     * earlier filter does not empty Available for the next row.
     */
    private void bulkAddUserToSelection(String email, String expectedUserId) {
        if (bulkSelectedIds().contains(expectedUserId)) return;
        Locator showMore = page.locator("a:has-text('Show more'), button:has-text('Show more')");
        if (showMore.count() > 0) {
            try {
                if (showMore.first().isVisible()) showMore.first().click();
            } catch (RuntimeException ignored) {}
            page.waitForTimeout(400);
        }
        if (bulkMoveFromAvailableToSelected(expectedUserId, email)) {
            waitBulkSelectedContains(expectedUserId, 5_000);
            return;
        }
        filterBulkByEmail(email);
        page.waitForTimeout(1_000);
        if (bulkMoveFromAvailableToSelected(expectedUserId, email)) {
            waitBulkSelectedContains(expectedUserId, 5_000);
        }
    }

    private boolean bulkMoveFromAvailableToSelected(String expectedUserId, String email) {
        if (bulkSelectedIds().contains(expectedUserId)) return true;
        if (bulkUsesLegacyDualList()) {
            Locator opts = page.locator("#addselect option:not([disabled])");
            String needle = email.toLowerCase(java.util.Locale.ROOT);
            for (int i = 0; i < opts.count(); i++) {
                try {
                    String value = opts.nth(i).getAttribute("value");
                    String text = opts.nth(i).innerText().toLowerCase(java.util.Locale.ROOT);
                    if (!expectedUserId.equals(value) && !text.contains(needle)) continue;
                    if (value != null && !value.isBlank()) {
                        page.locator("#addselect").selectOption(value);
                    } else {
                        opts.nth(i).click();
                    }
                    Locator add = page.locator("input[name='add'], #add");
                    if (add.count() > 0) clickSlowSubmit(add.first());
                    return true;
                } catch (RuntimeException ignored) {}
            }
            return false;
        }
        if (bulkAvailableIds().contains(expectedUserId)) {
            moveBulkOptions("#id_ausers", java.util.List.of(expectedUserId), "addsel");
            return true;
        }
        return false;
    }

    private void waitBulkSelectedContains(String expectedUserId, long timeoutMs) {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            if (bulkSelectedIds().contains(expectedUserId)) return;
            page.waitForTimeout(300);
        }
    }

    @SafeVarargs
    private java.util.List<String> bulkListIdsIn(String... selectors) {
        for (String css : selectors) {
            java.util.List<String> ids = bulkListIdsInOne(css);
            if (!ids.isEmpty() || safeCount(css) > 0) return ids;
        }
        return java.util.List.of();
    }

    private java.util.List<String> bulkListIdsInOne(String css) {
        try {
            Object ids = page.evaluate("""
                    (sel) => {
                      const box = document.querySelector(sel);
                      if (!box) return [];
                      return Array.from(box.querySelectorAll('option'))
                          .map(o => o.value || '')
                          .filter(v => /^\\d+$/.test(v) && v !== '0');
                    }
                    """, css);
            java.util.List<String> out = new java.util.ArrayList<>();
            if (ids instanceof java.util.List<?> list) {
                for (Object v : list) out.add(String.valueOf(v));
            }
            return out;
        } catch (RuntimeException navigating) {
            return java.util.List.of();
        }
    }

    /**
     * Choose {@code label} in the bulk action menu, press Go and confirm with Yes/Continue.
     *
     * @return null on success, otherwise why the action could not be applied
     */
    public String applyBulkAction(String label) {
        if (!chooseBulkAction(label)) {
            return "no '" + label + "' bulk action (offered: " + bulkActionLabels() + ")";
        }
        String go = "#id_doaction, input[name='doaction'], button[name='doaction']";
        if (safeCount(go) > 0) clickSlowSubmit(page.locator(go).first());
        page.waitForTimeout(1_000);
        Locator confirm = page.locator("#region-main button:has-text('Yes'), "
                + "#region-main input[type='submit'][value='Yes'], #notice button:has-text('Yes'), "
                + "#region-main button:has-text('Continue'), #region-main input[type='submit'][value='Continue']");
        long deadline = System.currentTimeMillis() + 20_000;
        while (System.currentTimeMillis() < deadline) {
            try {
                int n;
                try {
                    n = confirm.count();
                } catch (RuntimeException destroyed) {
                    n = 0;
                }
                for (int i = 0; i < n; i++) {
                    Locator candidate = confirm.nth(i);
                    if (candidate.isVisible()) {
                        System.out.println("[AdminUsers] bulk '" + label + "' confirm at " + page.url());
                        submitAndAwaitNewPage(candidate);
                        return null;
                    }
                }
            } catch (RuntimeException navigating) {
                // the Go post is still committing
            }
            page.waitForTimeout(500);
        }
        return "no confirmation page after choosing '" + label + "' (url=" + page.url() + ")";
    }

    private void moveBulkOptions(String selectCss, java.util.List<String> ids, String buttonName) {
        page.evaluate("""
                ([css, ids]) => {
                  const box = document.querySelector(css);
                  if (!box) return;
                  for (const o of box.options) o.selected = ids.includes(o.value);
                }
                """, java.util.List.of(selectCss, ids));
        clickSlowSubmit(page.locator("input[name='" + buttonName + "'], button[name='" + buttonName + "']").first());
        page.waitForTimeout(500);
    }

    private void filterBulkByEmail(String email) {
        Locator emailField = page.locator("#id_email, input[name='email']");
        if (emailField.count() == 0) return;
        try {
            emailField.first().fill(email);
        } catch (RuntimeException e) {
            emailField.first().evaluate("(el, v) => { el.value = v;"
                    + " el.dispatchEvent(new Event('change', {bubbles:true})); }", email);
        }
        Locator addFilter = page.locator("#id_addfilter, input[name='addfilter'], button:has-text('Add filter')");
        if (addFilter.count() > 0) {
            clickSlowSubmit(addFilter.first());
            page.waitForTimeout(1_000);
        }
    }

    private String bulkActionLabels() {
        try {
            return String.valueOf(page.evaluate("""
                    () => {
                      const a = document.querySelector("#id_action, select[name='action']");
                      return a ? Array.from(a.options).map(o => (o.textContent || '').trim()).join(' | ') : '(no menu)';
                    }
                    """));
        } catch (RuntimeException e) {
            return "(unreadable)";
        }
    }

    private boolean selectAction(Locator action, String actionValue) {
        try {
            action.selectOption(actionValue);
            return true;
        } catch (RuntimeException e) {
            Locator opt = action.locator("option").filter(
                    new Locator.FilterOptions().setHasText(java.util.regex.Pattern.compile(actionValue, java.util.regex.Pattern.CASE_INSENSITIVE)));
            if (opt.count() == 0) return false;
            String val = opt.first().getAttribute("value");
            if (val == null) return false;
            action.selectOption(val);
            return true;
        }
    }

    private void clickDeleteConfirm() {
        Locator confirm = page.locator("#notice button:has-text('Yes'), #notice input[value='Yes'], "
                + "#region-main button:has-text('Yes'), #region-main input[value='Yes'], "
                + "#region-main button:has-text('Delete'), #region-main input[value='Delete']");
        for (int i = 0; i < confirm.count(); i++) {
            try {
                if (confirm.nth(i).isVisible()) {
                    confirm.nth(i).click();
                    page.waitForTimeout(1_500);
                    return;
                }
            } catch (RuntimeException ignored) {}
        }
        try { confirmModal(); } catch (Throwable ignored) {}
    }


    /**
     * Set account lockout threshold on Site policies. {@code #id_s__lockoutthreshold} is a
     * {@code <select>} on Moodle 4.5 — do not fill() it. Restores via returned previous value.
     */
    public String configureLoginLockout(int threshold, int durationSeconds) {
        navigate("/admin/settings.php?section=sitepolicies");
        page.waitForTimeout(1_200);
        Locator thr = page.locator("select#id_s__lockoutthreshold, select[name='s__lockoutthreshold'], "
                + "#id_s__lockoutthreshold");
        if (thr.count() == 0) return null;
        String prevThr = "";
        try { prevThr = thr.first().inputValue(); } catch (RuntimeException ignored) {}
        setLockoutThreshold(thr.first(), String.valueOf(threshold));
        Locator dur = page.locator("input[name='s__lockoutduration[number]'], #id_s__lockoutduration_number");
        String prevDur = "";
        if (dur.count() > 0) {
            try { prevDur = dur.first().inputValue(); } catch (RuntimeException ignored) {}
            try { dur.first().fill(String.valueOf(Math.max(1, durationSeconds / 60))); } catch (RuntimeException ignored) {}
        }
        clickAdminSave();
        return prevThr + "|" + prevDur;
    }

    public void restoreLoginLockout(String previous) {
        if (previous == null || !previous.contains("|")) return;
        String[] parts = previous.split("\\|", -1);
        navigate("/admin/settings.php?section=sitepolicies");
        page.waitForTimeout(1_000);
        Locator thr = page.locator("select#id_s__lockoutthreshold, select[name='s__lockoutthreshold'], "
                + "#id_s__lockoutthreshold");
        if (thr.count() == 0) return;
        setLockoutThreshold(thr.first(), parts[0].isBlank() ? "0" : parts[0]);
        Locator dur = page.locator("input[name='s__lockoutduration[number]'], #id_s__lockoutduration_number");
        if (dur.count() > 0 && parts.length > 1 && !parts[1].isBlank()) {
            try { dur.first().fill(parts[1]); } catch (RuntimeException ignored) {}
        }
        clickAdminSave();
    }

    private void setLockoutThreshold(Locator thr, String value) {
        String tag = "";
        try { tag = String.valueOf(thr.evaluate("el => el.tagName")); } catch (RuntimeException ignored) {}
        if ("SELECT".equalsIgnoreCase(tag)) {
            try {
                thr.selectOption(value);
                return;
            } catch (RuntimeException e) {
                Locator opt = thr.locator("option").filter(new Locator.FilterOptions().setHasText(value));
                if (opt.count() > 0) {
                    String val = opt.first().getAttribute("value");
                    if (val != null) thr.selectOption(val);
                }
            }
            return;
        }
        try { thr.fill(value); } catch (RuntimeException ignored) {}
    }

    private void clickAdminSave() {
        Locator save = page.locator("form#adminsettings button[type='submit'], "
                + "form#adminsettings input[type='submit'], button:has-text('Save changes')");
        if (save.count() > 0) {
            save.first().click();
            page.waitForTimeout(2_000);
        }
    }

    /** Open a user's admin edit form directly by id. */
    public void openEditUser(String userId) {
        navigate("/user/editadvanced.php?id=" + userId + "&course=1");
        page.locator("#id_email").first().waitFor(new Locator.WaitForOptions()
                .setState(WaitForSelectorState.VISIBLE).setTimeout(30_000));
    }
}
