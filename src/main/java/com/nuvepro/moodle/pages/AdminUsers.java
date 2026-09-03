package com.nuvepro.moodle.pages;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.WaitForSelectorState;
import com.nuvepro.moodle.helpers.UserSpec;

/**
 * Standard Moodle user administration: Add user form (/user/editadvanced.php),
 * user list (/admin/user.php), edit/suspend/delete. No cloudlabs — pure Moodle core.
 */
public class AdminUsers extends BasePage {
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
        openUserList();
        waitForUserTable(30_000);
        resetUserListFilters();
        boolean applied = applyReportBuilderTextFilter("user:email", "3", email);   // Is equal to
        if (!applied) applyLegacyEmailFilter(email);
        for (int i = 0; i < 10; i++) {
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
     * Exact-email id lookup, preferring Bulk user actions' legacy filter form over the Browse users
     * report-builder table.
     *
     * The report-builder table applies its filters over AJAX and keeps them in the user's session, so
     * a lookup can silently read an unfiltered (paged) table and report a freshly created account as
     * missing. The bulk form is a plain POST whose "Available" list carries the user id in each option
     * value, which makes both the hit and the miss trustworthy.
     *
     * @return the user id, or null when the account really is absent
     */
    public String lookupUserIdByEmail(String email) {
        for (int attempt = 1; attempt <= 2; attempt++) {
            String viaBulk = userIdViaBulkFilter(email);
            if (viaBulk != null) return viaBulk.isEmpty() ? null : viaBulk;
            System.out.println("[AdminUsers] bulk email filter unusable for " + email
                    + " (attempt " + attempt + ")");
        }
        return findUserIdByEmail(email);
    }

    /** @return the id, "" when the applied filter proves the account is absent, null when unusable */
    private String userIdViaBulkFilter(String email) {
        openBulkUserActions();
        if (clickFirstVisible("input[name='removeall'], input[value='Remove all filters']")) {
            try {
                page.waitForLoadState(com.microsoft.playwright.options.LoadState.DOMCONTENTLOADED,
                        new Page.WaitForLoadStateOptions().setTimeout(20_000));
            } catch (RuntimeException ignored) {
                // the filter form is server-rendered; the wait below covers a slow reload
            }
            page.waitForTimeout(800);
        }
        Object applied = page.evaluate("""
                (email) => {
                  const field = document.querySelector("#id_email, input[name='email']");
                  if (!field) return 'no-field';
                  const operator = document.querySelector("#id_email_op, select[name='email_op']");
                  if (operator) {
                    operator.value = '2';                     // is equal to
                    operator.dispatchEvent(new Event('change', {bubbles: true}));
                  }
                  field.value = email;
                  field.dispatchEvent(new Event('input', {bubbles: true}));
                  field.dispatchEvent(new Event('change', {bubbles: true}));
                  const add = document.querySelector("#id_addfilter, input[name='addfilter']");
                  if (!add) return 'no-apply';
                  add.click();
                  return 'ok';
                }
                """, email);
        if (!"ok".equals(String.valueOf(applied))) return null;
        page.waitForTimeout(1_500);
        try {
            page.locator("#addselect").first().waitFor(new Locator.WaitForOptions()
                    .setState(WaitForSelectorState.ATTACHED).setTimeout(20_000));
        } catch (RuntimeException notRendered) {
            return null;
        }
        // Legacy user_filtering echoes each active filter with its value ("Email address is equal to
        // x@y"), which is the proof that the Available list below really is the filtered set. Option
        // labels follow the site's user identity fields and need not show the email, so the id is read
        // from the list rather than matched on text.
        String bodyText = "";
        try {
            bodyText = page.locator("body").innerText().toLowerCase();
        } catch (RuntimeException ignored) {
            return null;
        }
        boolean filterLive = page.locator("input[name='removeall'], input[value='Remove all filters']").count() > 0
                && bodyText.contains(email.toLowerCase());
        if (!filterLive) return null;
        Object result = page.evaluate("""
                (email) => {
                  const wanted = String(email).trim().toLowerCase();
                  const available = document.querySelector('#addselect');
                  if (!available) return 'no-list';
                  for (const option of available.querySelectorAll('option')) {
                    if (/^\\d+$/.test(option.value || '')) return option.value;
                  }
                  // The "selected for bulk action" list is session state and is never filtered, so it
                  // only ever confirms a hit — and only when the label carries the email.
                  const selected = document.querySelector('#removeselect');
                  for (const option of selected ? selected.querySelectorAll('option') : []) {
                    const text = (option.textContent || '').toLowerCase();
                    if (text.includes(wanted) && /^\\d+$/.test(option.value || '')) return option.value;
                  }
                  return '';
                }
                """, email);
        String value = result == null ? "" : String.valueOf(result).trim();
        if ("no-list".equals(value)) return null;
        return value;
    }

    /** Clear every report-builder filter ("Reset all") plus any legacy user_filtering filters. */
    public void resetUserListFilters() {
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
            // A surviving filter count would later be mistaken for "my filter applied", so say so.
            for (int i = 0; i < 6 && filtersApplied(); i++) {
                page.waitForTimeout(500);
            }
            if (filtersApplied()) {
                System.out.println("[AdminUsers] Reset all left filters active: " + filterButtonLabel());
            }
            return;
        }
        clickFirstVisible("#id_removeall, input[name='removeall'], input[value='Remove all filters']");
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
        navigate("/user/profile.php?id=" + userId);
        page.waitForTimeout(1_200);
        String body = page.locator("body").innerText().toLowerCase();
        boolean error = page.locator(".alert-danger, .errorbox, .notifyproblem").count() > 0
                || body.contains("can not find")
                || body.contains("cannot find")
                || body.contains("invalid user")
                || body.contains("deleted")
                || body.contains("nopermissions");
        if (error) return false;
        boolean profile = page.locator("#page-user-profile, .userprofile, .profile_tree, [data-region='profile']").count() > 0;
        return profile;
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
        openUserList();
        waitForUserTable(30_000);
        resetUserListFilters();
        openFiltersPanel();
        Object applied = page.evaluate("""
                (username) => {
                  const suspended = document.querySelector("select[name='user:suspended_operator']");
                  const operator = document.querySelector("select[name='user:username_operator']");
                  const input = document.querySelector("input[name='user:username_value']");
                  if (!suspended || !operator || !input) return 'no-field';
                  suspended.value = '1';                       // Yes
                  suspended.dispatchEvent(new Event('change', {bubbles: true}));
                  operator.value = '3';                        // Is equal to
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
        if (!"ok".equals(String.valueOf(applied))) return false;
        page.waitForTimeout(2_500);
        waitForUserTable(20_000);
        Locator row = page.locator("table tbody tr").filter(new Locator.FilterOptions().setHasText(username));
        if (row.count() > 0) return true;
        String emailGuess = username.contains("@") ? username : username + "@example.com";
        return page.locator("table tbody tr").filter(new Locator.FilterOptions().setHasText(emailGuess)).count() > 0;
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
    public void deleteUserViaUi(String userId) {
        Object sk = page.evaluate("() => (window.M && M.cfg && M.cfg.sesskey) || ''");
        navigate("/user/delete.php?id=" + userId + "&sesskey=" + sk);
        page.waitForTimeout(1_000);
        Locator confirm = page.locator("#notice button:has-text('Delete'), #notice input[value='Delete'], "
                + "#notice button:has-text('Yes'), #notice input[value='Yes'], "
                + "#region-main button:has-text('Delete'), #region-main input[value='Delete'], "
                + "#region-main button:has-text('Continue'), input[value='Continue']");
        for (int i = 0; i < confirm.count(); i++) {
            try {
                if (confirm.nth(i).isVisible()) {
                    confirm.nth(i).click();
                    page.waitForLoadState(com.microsoft.playwright.options.LoadState.DOMCONTENTLOADED);
                    page.waitForTimeout(1_500);
                    return;
                }
            } catch (RuntimeException ignored) {}
        }
    }

    // ---- cohorts (/cohort) ----
    public void cohortCreate(String name, String idnumber) {
        navigate("/cohort/edit.php?contextid=1");
        page.locator("#id_name").waitFor(new Locator.WaitForOptions()
                .setState(WaitForSelectorState.VISIBLE).setTimeout(30_000));
        page.locator("#id_name").fill(name);
        page.locator("#id_idnumber").fill(idnumber);
        page.locator("#id_submitbutton").click();
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
        page.locator("#id_submitbutton").click();
        page.waitForLoadState(com.microsoft.playwright.options.LoadState.DOMCONTENTLOADED);
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
        return cohortMoveMember(cohortId, username, true);
    }

    public boolean cohortRemoveMember(String cohortId, String username) {
        return cohortMoveMember(cohortId, username, false);
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

    private boolean cohortMoveMember(String cohortId, String username, boolean add) {
        openCohortAssign(cohortId);
        String searchSel = add ? "#addselect_searchtext" : "#removeselect_searchtext";
        String listSel = add ? "#addselect" : "#removeselect";
        String btnSel = add ? "input#add, input[name='add']" : "input#remove, input[name='remove']";
        Locator search = page.locator(searchSel);
        if (search.count() > 0) {
            search.first().click();
            search.first().fill("");
            search.first().pressSequentially(username, new Locator.PressSequentiallyOptions().setDelay(40));
            page.waitForTimeout(2_000);
        }
        Locator list = page.locator(listSel);
        if (list.count() == 0) return false;
        Locator opt = list.locator("option").filter(new Locator.FilterOptions().setHasText(username));
        if (opt.count() == 0) {
            // user selector labels are often "First Last (email)" — retry on lastname/email fragment
            opt = list.locator("option").filter(new Locator.FilterOptions().setHasText("Auto"));
        }
        if (opt.count() == 0) return false;
        String value = opt.first().getAttribute("value");
        if (value != null && !value.isBlank()) list.selectOption(value);
        else opt.first().click();
        Locator btn = page.locator(btnSel);
        if (btn.count() == 0) return false;
        btn.first().click();
        page.waitForTimeout(1_500);
        return true;
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
        for (int attempt = 1; attempt <= 3; attempt++) {
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
        return filtersApplied();
    }

    public boolean userAppearsInFilteredList(String username) {
        filterUserList(username);
        Locator row = page.locator("table tbody tr").filter(new Locator.FilterOptions().setHasText(username));
        if (row.count() > 0) return true;
        // Email column usually embeds the username (autotest_N@example.com)
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
        try {
            page.locator("#id_susers, select[name='susers[]']").first().waitFor(new Locator.WaitForOptions()
                    .setState(WaitForSelectorState.ATTACHED).setTimeout(timeoutMs));
            return true;
        } catch (RuntimeException notRendered) {
            return false;
        }
    }

    /** Labels of the users in the Selected box, excluding its "All selected (n/m)" header entry. */
    public String bulkSelectedUsersText() {
        Object text = page.evaluate("""
                () => {
                  const box = document.querySelector("#id_susers, select[name='susers[]']");
                  if (!box) return '';
                  return Array.from(box.querySelectorAll('option'))
                          .filter(option => /^\\d+$/.test(option.value || '') && option.value !== '0')
                          .map(option => (option.textContent || '').trim())
                          .join(' | ');
                }
                """);
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
