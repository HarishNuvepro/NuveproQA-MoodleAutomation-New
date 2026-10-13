package com.nuvepro.moodle.pages;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;

/**
 * System-level role assignment (/admin/roles/assign.php?contextid=1&roleid=N). Standard Moodle
 * dual-listbox user selector: search -> #addselect (potential) -> Add -> #removeselect (assigned).
 */
public class RoleAssign extends BasePage {
    public RoleAssign(Page page) {
        super(page);
    }

    public void openSystemRoleAssign(int roleId) {
        RuntimeException last = null;
        for (int attempt = 1; attempt <= 3; attempt++) {
            try {
                navigate("/admin/roles/assign.php?contextid=1&roleid=" + roleId);
                page.locator("#addselect").waitFor(new com.microsoft.playwright.Locator.WaitForOptions()
                        .setTimeout(30_000));
                page.waitForTimeout(800);
                return;
            } catch (RuntimeException e) {
                last = e;
                page.waitForTimeout(1_500);
            }
        }
        if (last != null) throw last;
    }

    /** Search for a user (username or email) and Add them to this role. */
    public void assignUser(String searchTerm) {
        assignUser(searchTerm, searchTerm);
    }

    /** Tries {@code primary} then {@code alternate} (e.g. username then email) after WS-created users index. */
    public void assignUser(String primary, String alternate) {
        for (String term : new String[] {primary, alternate}) {
            if (term == null || term.isBlank()) continue;
            if (tryAssignSearchTerm(term)) return;
        }
        throw new AssertionError("Could not assign user to role; tried search terms ["
                + primary + ", " + alternate + "]. Potential list: "
                + safeAddSelectText());
    }

    private boolean tryAssignSearchTerm(String searchTerm) {
        Locator search = page.locator("#addselect_searchtext");
        Locator addBtn = page.locator("input[name='add']");
        if (search.count() > 0) {
            search.click();
            search.fill("");
            search.fill(searchTerm);
            page.evaluate("""
                    () => {
                      const el = document.querySelector('#addselect_searchtext');
                      if (!el) return;
                      el.dispatchEvent(new Event('input', {bubbles: true}));
                      el.dispatchEvent(new KeyboardEvent('keyup', {bubbles: true}));
                    }
                    """);
        }
        long deadline = System.currentTimeMillis() + 45_000;
        while (System.currentTimeMillis() < deadline) {
            Locator match = page.locator("#addselect option:not([disabled])")
                    .filter(new Locator.FilterOptions().setHasText(searchTerm));
            if (match.count() > 0) {
                match.first().click();
                page.waitForTimeout(400);
                try {
                    if (addBtn.count() > 0 && addBtn.isEnabled()) {
                        addBtn.click();
                        page.waitForTimeout(1_800);
                        return userAssigned(searchTerm);
                    }
                } catch (RuntimeException ignored) {
                    // re-poll
                }
            }
            page.waitForTimeout(600);
        }
        return false;
    }

    private String safeAddSelectText() {
        try {
            Locator sel = page.locator("#addselect");
            if (sel.count() == 0) return "(no #addselect)";
            return sel.first().innerText().replaceAll("\\s+", " ").trim();
        } catch (RuntimeException e) {
            return "(unreadable #addselect)";
        }
    }

    /** Whether the user (matched by name/email substring) is in the assigned (#removeselect) list. */
    public boolean userAssigned(String term) {
        for (int attempt = 0; attempt < 8; attempt++) {
            try {
                return page.locator("#removeselect option")
                        .filter(new Locator.FilterOptions().setHasText(term)).count() > 0;
            } catch (RuntimeException e) {
                if (attempt == 7) throw e;
                page.waitForTimeout(500);
            }
        }
        return false;
    }

    public void removeUser(String term) {
        Locator opt = page.locator("#removeselect option")
                .filter(new Locator.FilterOptions().setHasText(term));
        if (opt.count() > 0) {
            opt.first().click();
            page.locator("input[name='remove']").click();
            page.waitForTimeout(1_800);
        }
    }
}
