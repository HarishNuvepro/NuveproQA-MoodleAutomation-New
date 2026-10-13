package com.nuvepro.moodle.pages;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;
import com.microsoft.playwright.options.SelectOption;

import java.util.HashSet;
import java.util.Set;

/**
 * The student's view of a course page (course/view.php) — used to verify sequential activity
 * completion: whether an activity is access-restricted, its restriction message, whether it is
 * marked complete, and the "Mark as done" manual-completion toggle. Selectors verified live.
 */
public class CourseView extends BasePage {
    public CourseView(Page page) {
        super(page);
    }

    public void open(String courseId) {
        navigate("/course/view.php?id=" + courseId);
        page.waitForTimeout(800);
    }

    /**
     * New assessment activities default to hidden. Turn editing on, open the activity action menu,
     * choose Availability → Show on course page so enrolled students can see and launch it.
     */
    public void showActivityOnCoursePage(String courseId, int cmid) {
        showActivityOnCoursePage(courseId, cmid, null);
    }

    public void showActivityOnCoursePage(String courseId, int cmid, String activityName) {
        open(courseId);
        turnEditingOn(courseId);
        if (!showViaCourseMenu(cmid, activityName)) {
            showViaModEdit(cmid);
            open(courseId);
        }
        if (!isActivityShownOnCoursePage(cmid, activityName)) {
            throw new AssertionError("activity cmid=" + cmid + " is still hidden on the course page after "
                    + "Availability → Show on course page");
        }
    }

    public void hideActivityOnCoursePage(String courseId, int cmid, String activityName) {
        open(courseId);
        turnEditingOn(courseId);
        hideViaModEdit(cmid);
        open(courseId);
        if (isActivityShownOnCoursePage(cmid, activityName)) {
            throw new AssertionError("activity cmid=" + cmid + " is still visible on the course page after hiding it");
        }
    }

    public boolean activityVisibleOnCoursePage(int cmid, String activityName) {
        return isActivityShownOnCoursePage(cmid, activityName);
    }

    /** Duplicate one course-module through Moodle's activity action menu and return the new cmid. */
    public int duplicateActivity(String courseId, int cmid) {
        open(courseId);
        turnEditingOn(courseId);
        Set<Integer> before = courseModuleIds();
        Locator activity = findActivity(cmid, null);
        if (activity.count() == 0 || !openActivityActionMenu(activity)) return -1;
        Locator duplicate = activity.locator(
                "a[href*='duplicate='], [data-action='duplicate'], .editing_duplicate");
        if (!clickFirstVisible(duplicate)) {
            duplicate = page.locator(
                    ".action-menu .dropdown-menu.show a:has-text('Duplicate'), "
                            + ".action-menu .dropdown-menu.show [role='menuitem']:has-text('Duplicate')");
            if (!clickFirstVisible(duplicate)) return -1;
        }
        for (int i = 0; i < 30; i++) {
            for (int id : courseModuleIds()) {
                if (!before.contains(id)) return id;
            }
            page.waitForTimeout(1_000);
        }
        return -1;
    }

    private Set<Integer> courseModuleIds() {
        Set<Integer> ids = new HashSet<>();
        Locator modules = page.locator("[data-for='cmitem'][data-id], li.activity[data-id]");
        for (int i = 0; i < modules.count(); i++) {
            String value = modules.nth(i).getAttribute("data-id");
            try { if (value != null) ids.add(Integer.parseInt(value)); }
            catch (NumberFormatException ignored) {}
        }
        return ids;
    }

    private Locator findActivity(int cmid, String activityName) {
        Locator activity = activityByCmid(cmid);
        try {
            if (activity.count() > 0) {
                activity.scrollIntoViewIfNeeded();
                return activity;
            }
        } catch (RuntimeException ignored) {}
        if (activityName != null && !activityName.isBlank()) {
            Locator byName = page.locator("[data-for='cmitem'], li.activity, .activity-item")
                    .filter(new Locator.FilterOptions().setHasText(activityName)).first();
            if (byName.count() > 0) {
                byName.scrollIntoViewIfNeeded();
                return byName;
            }
        }
        return activity;
    }

    private boolean showViaCourseMenu(int cmid, String activityName) {
        Locator activity = findActivity(cmid, activityName);
        try {
            activity.waitFor(new Locator.WaitForOptions().setTimeout(20_000));
        } catch (RuntimeException e) {
            return false;
        }

        if (!openActivityActionMenu(activity)) return false;

        // Some formats expose Show directly in the menu when hidden.
        if (clickCmShow(cmid)) return waitForShown(cmid, activityName);

        Locator menu = activity.locator(".action-menu .dropdown-menu.show");
        if (menu.count() == 0) menu = activity.locator(".action-menu .dropdown-menu");
        if (menu.count() == 0) menu = page.locator(".action-menu .dropdown-menu.show").last();

        Locator availability = scopedMenuItem(menu, "Availability",
                ".editing_availability, .dropdown-submenu, [data-region='availability']");
        if (availability == null) {
            availability = page.locator(
                    ".action-menu .dropdown-menu.show [role='menuitem']:has-text('Availability'), "
                            + ".action-menu .dropdown-menu.show a.dropdown-item:has-text('Availability'), "
                            + ".action-menu .dropdown-menu.show .dropdown-toggle:has-text('Availability'), "
                            + "[role='menuitem']:has-text('Availability')").first();
        }
        if (availability.count() == 0) return false;

        openAvailabilitySubpanel(availability);

        if (clickCmShow(cmid)) return waitForShown(cmid, activityName);

        Locator showOption = page.locator(
                "[data-action='cmShow'][data-id='" + cmid + "'], "
                        + ".dropdown-subpanel:visible [data-action='cmShow'], "
                        + ".editing_availability [data-action='cmShow'], "
                        + ".dropdown-subpanel:visible label:has-text('Show on course page'), "
                        + ".dropdown-subpanel:visible :text('Show on course page'), "
                        + ".editing_availability :text('Show on course page'), "
                        + "input[type='radio'][value='show'], "
                        + "[role='menuitemradio']:has-text('Show on course page'), "
                        + "[role='menuitem']:has-text('Show on course page')");
        if (clickFirstVisible(showOption)) return waitForShown(cmid, activityName);

        try {
            page.getByRole(AriaRole.MENUITEM, new Page.GetByRoleOptions().setName("Show on course page")).click();
            page.waitForTimeout(2_000);
            return waitForShown(cmid, activityName);
        } catch (RuntimeException ignored) {}

        return clickCmShowViaJs(cmid) && waitForShown(cmid, activityName);
    }

    private boolean openActivityActionMenu(Locator activity) {
        Locator menuToggle = activity.locator(
                "[data-action='toggleactionmenu'], [data-bs-toggle='dropdown'], "
                        + "button.action-menu-trigger, .action-menu .dropdown-toggle, "
                        + ".action-menu [aria-haspopup='true']");
        if (menuToggle.count() == 0) return false;
        try {
            menuToggle.first().scrollIntoViewIfNeeded();
            menuToggle.first().click();
            page.waitForTimeout(900);
            return true;
        } catch (RuntimeException e) {
            return false;
        }
    }

    private Locator scopedMenuItem(Locator menu, String text, String extraSelectors) {
        Locator item = menu.locator("a:has-text('" + text + "'), button:has-text('" + text + "'), "
                + "span:has-text('" + text + "'), [role='menuitem']:has-text('" + text + "')");
        if (item.count() > 0) return item.first();
        if (extraSelectors != null && !extraSelectors.isBlank()) {
            Locator extra = menu.locator(extraSelectors);
            if (extra.count() > 0) return extra.first();
        }
        return null;
    }

    private void openAvailabilitySubpanel(Locator availability) {
        for (int attempt = 0; attempt < 3; attempt++) {
            try {
                availability.hover();
                page.waitForTimeout(500);
            } catch (RuntimeException ignored) {}
            try {
                availability.click();
                page.waitForTimeout(700);
            } catch (RuntimeException ignored) {}
            Locator subpanel = page.locator(".dropdown-subpanel:visible, .editing_availability:visible");
            if (subpanel.count() > 0) return;
        }
    }

    private boolean clickCmShow(int cmid) {
        Locator show = page.locator("[data-action='cmShow'][data-id='" + cmid + "']");
        return clickFirstVisible(show);
    }

    private boolean clickCmShowViaJs(int cmid) {
        Object clicked = page.evaluate("cmid => {"
                + "  const el = document.querySelector(`[data-action='cmShow'][data-id='${cmid}']`);"
                + "  if (!el) return false;"
                + "  el.click();"
                + "  return true;"
                + "}", cmid);
        page.waitForTimeout(2_000);
        return Boolean.TRUE.equals(clicked);
    }

    private boolean clickFirstVisible(Locator loc) {
        for (int i = 0; i < loc.count(); i++) {
            try {
                Locator item = loc.nth(i);
                if (item.isVisible()) {
                    item.click(new Locator.ClickOptions().setForce(true));
                    page.waitForTimeout(2_000);
                    return true;
                }
            } catch (RuntimeException ignored) {}
        }
        return false;
    }

    private boolean waitForShown(int cmid, String activityName) {
        for (int i = 0; i < 10; i++) {
            if (isActivityShownOnCoursePage(cmid, activityName)) return true;
            page.waitForTimeout(1_000);
        }
        return isActivityShownOnCoursePage(cmid, activityName);
    }

    private boolean isActivityShownOnCoursePage(int cmid, String activityName) {
        Locator activity = findActivity(cmid, activityName);
        if (activity.count() == 0) return false;
        try {
            String cls = nz(activity.getAttribute("class"));
            if (cls.contains("dimmed")) return false;
            Locator hiddenBadge = activity.locator(".badge:has-text('Hidden'), "
                    + ".activity-badge:has-text('Hidden'), "
                    + "[data-region='visibility-badge']:has-text('Hidden')");
            if (hiddenBadge.count() > 0 && hiddenBadge.first().isVisible()) return false;
        } catch (RuntimeException ignored) {}
        return true;
    }

    private void showViaModEdit(int cmid) {
        navigate("/course/modedit.php?update=" + cmid + "&return=1");
        expandFormSections();
        Locator visible = page.locator("#id_visible, select[name='visible']");
        if (visible.count() == 0) {
            throw new AssertionError("visible field not found on activity edit form for cmid=" + cmid);
        }
        try {
            visible.first().selectOption(new SelectOption().setLabel("Show on course page"));
        } catch (RuntimeException e) {
            visible.first().selectOption("1");
        }
        clickSubmitLeaving("modedit.php");
        page.waitForTimeout(1_500);
    }

    private void hideViaModEdit(int cmid) {
        navigate("/course/modedit.php?update=" + cmid + "&return=1");
        expandFormSections();
        Locator visible = page.locator("#id_visible, select[name='visible']");
        if (visible.count() == 0) {
            throw new AssertionError("visible field not found on activity edit form for cmid=" + cmid);
        }
        try {
            visible.first().selectOption(new SelectOption().setLabel("Hide on course page"));
        } catch (RuntimeException e) {
            visible.first().selectOption("0");
        }
        clickSubmitLeaving("modedit.php");
        page.waitForTimeout(1_500);
    }

    private void expandFormSections() {
        try {
            Locator toggle = page.locator(".collapsible-actions a.collapseexpand, a:has-text('Expand all')");
            if (toggle.count() > 0 && toggle.first().innerText().toLowerCase().contains("expand")) {
                toggle.first().click();
                page.waitForTimeout(500);
            }
        } catch (RuntimeException ignored) {}
        page.evaluate("() => {"
                + "  document.querySelectorAll('fieldset.collapsible.collapsed, fieldset.collapsed')"
                + "    .forEach(fs => fs.classList.remove('collapsed'));"
                + "  document.querySelectorAll('fieldset.collapsible .fcontainer, fieldset .collapse')"
                + "    .forEach(el => { el.classList.add('show'); el.style.display = ''; el.style.height = 'auto'; });"
                + "}");
        page.waitForTimeout(300);
    }

    private Locator activityByCmid(int cmid) {
        Locator byData = page.locator("[data-for='cmitem'][data-id='" + cmid + "']");
        if (byData.count() > 0) return byData.first();
        Locator byId = page.locator("#module-" + cmid);
        if (byId.count() > 0) return byId.first();
        return page.locator("li.activity[data-id='" + cmid + "']").first();
    }

    private void turnEditingOn(String courseId) {
        String body = nz(page.locator("body").getAttribute("class"));
        if (body.contains("editing")) return;
        Locator sw = page.locator(".editmode-switch-form input[name='setmode'], input[name='setmode']");
        if (sw.count() > 0) {
            try {
                sw.first().check(new Locator.CheckOptions().setForce(true));
                page.waitForTimeout(2_000);
            } catch (RuntimeException ignored) {}
        }
        body = nz(page.locator("body").getAttribute("class"));
        if (body.contains("editing")) return;
        Locator btn = page.locator("button:has-text('Turn editing on'), a:has-text('Turn editing on')");
        if (btn.count() > 0) {
            btn.first().click();
            page.waitForTimeout(2_000);
        }
        body = nz(page.locator("body").getAttribute("class"));
        if (body.contains("editing")) return;
        Object sk = page.evaluate("() => (window.M && M.cfg && M.cfg.sesskey) || ''");
        navigate("/course/view.php?id=" + courseId + "&sesskey=" + sk + "&edit=on");
        page.waitForTimeout(1_500);
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }

    /** The activity list item whose text contains {@code name}. */
    private Locator activity(String name) {
        return page.locator("li.activity, .activity-item")
                .filter(new Locator.FilterOptions().setHasText(name)).first();
    }

    /** True if the activity is gated by restrict-access (message and/or dimmed on the course page). */
    public boolean isRestricted(String name) {
        Locator act = activity(name);
        if (act.count() == 0) return false;
        try {
            String cls = nz(act.getAttribute("class"));
            if (cls.contains("dimmed") || cls.contains("restricted")) return true;
        } catch (RuntimeException ignored) {}
        Locator info = act.locator(".availabilityinfo, [data-region='availability-info']");
        if (info.count() == 0) return false;
        String t = info.first().innerText().toLowerCase();
        return t.contains("not available") || t.contains("requires:") || t.contains("available until");
    }

    /** The restriction message text (empty if none). */
    public String restrictionMessage(String name) {
        Locator info = activity(name).locator(".availabilityinfo");
        return info.count() > 0 ? info.first().innerText().trim() : "";
    }

    /** True when the student has clicked manual "Mark as done" (toggle shows Done / aria-pressed). */
    public boolean isManualMarkDone(String name) {
        Locator toggle = activity(name).locator("[data-action='toggle-manual-completion']");
        if (toggle.count() == 0) return false;
        String t = toggle.first().innerText().trim().toLowerCase();
        String pressed = toggle.first().getAttribute("aria-pressed");
        return "true".equals(pressed) || (t.equals("done") || t.contains("done") && !t.contains("mark"));
    }

    /** Grade/automatic completion badge on the course page (independent of manual mark-as-done). */
    public boolean showsActivityCompletionBadge(String name) {
        return activity(name).locator(".completion-dropdown .badge-success, "
                + "[data-region='completion-info'] .badge-success").count() > 0;
    }

    /** True once the activity is marked complete (manual toggle Done, or completion badge when no toggle). */
    public boolean isCompleted(String name) {
        if (hasManualCompletionToggle(name)) {
            return isManualMarkDone(name);
        }
        return showsActivityCompletionBadge(name);
    }

    public boolean hasManualCompletionToggle(String name) {
        return activity(name).locator("[data-action='toggle-manual-completion']").count() > 0;
    }

    /** Click the activity's "Mark as done" manual-completion toggle. */
    public void markDone(String name) {
        // The toggle is an AJAX call. A fixed short wait is not enough under load: navigating (or closing
        // the context on re-login) while it is in flight loses the completion. So: click, wait until the
        // control itself flips to Done (the DOM only updates after the server accepted it), then RELOAD
        // and confirm it persisted; retry the whole thing if it did not.
        // NOTE: the course page may reload itself after the click ("Execution context was destroyed"),
        // so every DOM probe goes through the navigation-tolerant doneNow().
        for (int attempt = 1; attempt <= 3; attempt++) {
            if (!doneNow(name)) {
                try {
                    Locator toggle = activity(name).locator("[data-action='toggle-manual-completion']");
                    if (toggle.count() == 0) {
                        throw new IllegalStateException("no manual 'Mark as done' control for '" + name + "'");
                    }
                    toggle.first().click();
                } catch (com.microsoft.playwright.PlaywrightException navigating) {
                    // the click can itself trigger the reload — fine, verified below
                }
                for (int i = 0; i < 30 && !doneNow(name); i++) {   // up to ~15s
                    page.waitForTimeout(500);
                }
            }
            page.waitForTimeout(1_000);
            try {
                page.reload(new Page.ReloadOptions().setWaitUntil(
                        com.microsoft.playwright.options.WaitUntilState.DOMCONTENTLOADED));
            } catch (com.microsoft.playwright.PlaywrightException ignored) {
                // a self-triggered navigation was already in flight; the probe below waits it out
            }
            page.waitForTimeout(1_000);
            for (int i = 0; i < 6 && !doneNow(name); i++) {
                page.waitForTimeout(500);
            }
            if (doneNow(name)) {
                return;   // persisted server-side (checked on a freshly loaded page)
            }
            System.out.println("[CourseView] markDone('" + name + "') not persisted after attempt " + attempt);
        }
        throw new IllegalStateException("'Mark as done' for '" + name + "' did not persist after 3 attempts");
    }

    /** {@link #isManualMarkDone} that survives the page navigating underneath the probe. */
    private boolean doneNow(String name) {
        for (int i = 0; i < 5; i++) {
            try {
                return isManualMarkDone(name);
            } catch (com.microsoft.playwright.PlaywrightException navigating) {
                page.waitForTimeout(700);
            }
        }
        return false;
    }

    /** Mark as done when the course page shows the manual-completion control. */
    public void markDoneIfPresent(String name) {
        if (!hasManualCompletionToggle(name)) return;
        if (!isCompleted(name)) {
            markDone(name);
        }
    }

    /** True if the activity is present and has a working view link (i.e. accessible/enabled). */
    public boolean isAccessible(String name) {
        return activity(name).count() > 0 && !isRestricted(name);
    }
}
