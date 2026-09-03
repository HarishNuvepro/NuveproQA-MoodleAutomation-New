package com.nuvepro.moodle.pages;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.WaitForSelectorState;
import com.nuvepro.moodle.config.Settings;
import com.nuvepro.moodle.helpers.Auth;

/**
 * Learner self-service: own profile, change-password, logout.
 * Selectors live here so step classes stay data/flow only.
 */
public class UserProfile extends BasePage {
    public UserProfile(Page page) {
        super(page);
    }

    public void openEdit() {
        navigate("/user/edit.php");
        Auth.waitUntilStable(page);
        // After logout Moodle redirects to /login/ — do not wait for profile fields that never appear.
        if (page.url().contains("/login/")) return;
        page.locator("#id_city, #id_email, #id_submitbutton").first()
                .waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE).setTimeout(30_000));
    }

    public void setCity(String city) {
        page.locator("#id_city").fill(city);
    }

    public String city() {
        return page.locator("#id_city").inputValue();
    }

    public void setEmail(String email) {
        page.locator("#id_email").fill(email);
    }

    public void save() {
        clickSubmitLeaving("/user/edit.php");
        Auth.waitUntilStable(page);
    }

    public boolean stillOnEditForm() {
        return page.url().contains("/user/edit.php");
    }

    public boolean hasValidationError() {
        Auth.waitUntilStable(page);
        try {
            if (safeCount("[id^='id_error_']") > 0) return true;
            if (safeCount(".invalid-feedback, .form-control-feedback") > 0) return true;
            String body = page.locator("body").innerText().toLowerCase();
            return body.contains("invalid email") || body.contains("invalid");
        } catch (RuntimeException e) {
            Auth.waitUntilStable(page);
            return stillOnEditForm();
        }
    }

    public void openChangePassword() {
        navigate("/login/change_password.php");
        page.locator("#id_password").waitFor(new Locator.WaitForOptions()
                .setState(WaitForSelectorState.VISIBLE).setTimeout(30_000));
    }

    public void changePassword(String current, String next) {
        page.locator("#id_password").fill(current);
        page.locator("#id_newpassword1").fill(next);
        page.locator("#id_newpassword2").fill(next);
        clickSubmitLeaving("change_password.php");
        Auth.waitUntilStable(page);
    }

    public boolean passwordChangeForced() {
        return page.url().contains("change_password")
                || page.locator("#id_newpassword1").count() > 0
                || page.locator("body").innerText().toLowerCase().contains("change your password");
    }

    public void logout() {
        Auth.logout(page);
    }

    public boolean isLoggedIn() {
        return Auth.isLoggedIn(page);
    }

    public void openCourse(String courseId) {
        page.navigate(Settings.BASE_URL + "/course/view.php?id=" + courseId,
                new Page.NavigateOptions()
                        .setWaitUntil(com.microsoft.playwright.options.WaitUntilState.DOMCONTENTLOADED)
                        .setTimeout(45_000));
        page.waitForTimeout(1_500);
    }

    public boolean onCoursePage() {
        return page.url().contains("/course/view.php") && !page.url().contains("/enrol/");
    }

    public boolean onCoursePage(String courseId) {
        String url = page.url();
        return url.contains("/course/view.php") && url.contains("id=" + courseId) && !url.contains("/enrol/");
    }

    /** True when the enrolment page offers Enrol me (does not click it). */
    public boolean hasSelfEnrolAction(String courseId) {
        openEnrolPage(courseId);
        if (onCoursePage(courseId)) return false;
        Locator enrol = selfEnrolControl();
        for (int i = 0; i < enrol.count(); i++) {
            try {
                if (enrol.nth(i).isVisible()) return true;
            } catch (RuntimeException ignored) {}
        }
        return false;
    }

    /**
     * Self-enrol as the current logged-in user into a course that has the self-enrol instance enabled.
     * Opens the enrolment page and clicks Enrol me (Moodle 4.5).
     */
    public boolean selfEnrolInCourse(String courseId) {
        openEnrolPage(courseId);
        if (onCoursePage(courseId)) return true;
        Locator enrol = selfEnrolControl();
        boolean clicked = false;
        for (int i = 0; i < enrol.count(); i++) {
            try {
                if (enrol.nth(i).isVisible()) {
                    enrol.nth(i).click();
                    clicked = true;
                    break;
                }
            } catch (RuntimeException ignored) {}
        }
        if (!clicked) {
            try {
                page.getByRole(com.microsoft.playwright.options.AriaRole.BUTTON,
                        new Page.GetByRoleOptions().setName(java.util.regex.Pattern.compile("Enrol", java.util.regex.Pattern.CASE_INSENSITIVE)))
                        .first().click();
                clicked = true;
            } catch (RuntimeException ignored) {}
        }
        page.waitForTimeout(1_500);
        try { confirmModal(); } catch (Throwable ignored) {}
        page.waitForTimeout(1_000);
        if (!onCoursePage(courseId)) {
            openCourse(courseId);
        }
        return onCoursePage(courseId);
    }

    private void openEnrolPage(String courseId) {
        page.navigate(Settings.BASE_URL + "/enrol/index.php?id=" + courseId,
                new Page.NavigateOptions()
                        .setWaitUntil(com.microsoft.playwright.options.WaitUntilState.DOMCONTENTLOADED)
                        .setTimeout(45_000));
        page.waitForTimeout(1_200);
    }

    private Locator selfEnrolControl() {
        return page.locator("#region-main input[type='submit'][value*='Enrol'], "
                + "#region-main button:has-text('Enrol me'), "
                + "#region-main input[value*='Enrol me'], "
                + "form[action*='enrol/self'] input[type='submit'], "
                + "form[action*='enrol/self'] button[type='submit']");
    }

    public boolean loginRequired() {
        return !isLoggedIn() || page.url().contains("/login/");
    }
}
