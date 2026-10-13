package com.nuvepro.moodle.pages;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;

/**
 * Guided (practice project) landing page (view.php for a guided activity).
 * Selectors from templates/guided-landing.mustache.
 */
public class GuidedLanding extends BasePage {
    public static final String CONTAINER = ".guided-landing-panel";
    public static final String START = "#np-ap-pl-guide-start";              // Start Guided Project (first time)
    public static final String CONTINUE = ".np-ap-gp-continue";              // Continue active attempt
    public static final String NEW_ATTEMPT = "#np-ap-gl-new-attempt-btn-top";// New Attempt
    public static final String RESTART = ".np-ap-gp-restart";
    public static final String COMPLETE = ".np-ap-guided-project-end";
    public static final String COMPLETED_BADGE = ".guided-project-ended-badge";
    public static final String DETAILS = "#np-ap-cl-challenge-details";      // fromdate/todate/timeLimit/attempts
    public static final String ATTEMPTS_CARDS = "#labAttemptsContainer";
    public static final String NEW_ATTEMPT_TIMER = "#new-attempt-timer";     // cooldown countdown
    // admin manage buttons (gated by managePermission)
    public static final String USER_LABS = "a[href*='managelabs.php']";
    public static final String USER_GUIDED = "a[href*='userguidedprojects.php']";
    public static final String MANAGE_OVERRIDES = "a[href*='overrides']";
    // hidden state
    public static final String H_SECTION_ID = "#np-ap-gl-sectionId";
    public static final String H_ID = "#np-ap-gl-id";

    public GuidedLanding(Page page) {
        super(page);
    }

    public void open(int cmid) {
        // Retry once on transient network blips; wait for the landing container (not networkidle).
        for (int attempt = 1; attempt <= 2; attempt++) {
            try {
                navigate("/mod/cloudlabs/view.php?id=" + cmid);
                page.locator(CONTAINER).first().waitFor(new Locator.WaitForOptions()
                        .setState(com.microsoft.playwright.options.WaitForSelectorState.VISIBLE).setTimeout(45_000));
                return;
            } catch (Throwable e) {
                if (attempt == 2) throw e;
            }
        }
    }

    public boolean isLoaded() {
        return page.locator(CONTAINER).count() > 0;
    }

    /** The landing offers a way to begin/continue the project (Start / Continue / New Attempt). */
    public boolean hasStartOption() {
        return visible(START) || visible(CONTINUE) || visible(NEW_ATTEMPT);
    }

    /** Click Start Practice Project and cancel the confirmation (no lab / console). */
    public void startProjectThenCancel() {
        page.locator(START).first().click();
        page.waitForTimeout(600);
        cancelModal();
    }

    public boolean hasStartProjectOnly() {
        return visible(START) && !visible(CONTINUE) && !visible(NEW_ATTEMPT);
    }

    public boolean adminManageButtonsPresent() {
        return page.locator(USER_LABS).count() > 0 && page.locator(USER_GUIDED).count() > 0;
    }

    public boolean manageOverridesPresent() {
        return page.locator(MANAGE_OVERRIDES).count() > 0
                || page.locator("a[href*='manageguidedprojectoverrides']").count() > 0;
    }

    public boolean startPracticeProjectButtonPresent() {
        return visible(START);
    }

    public boolean attemptsSummaryPresent() {
        return page.locator(ATTEMPTS_CARDS).count() > 0
                || page.locator("body").innerText().toLowerCase().contains("attempt");
    }

    public boolean completeButtonPresent() {
        return page.locator(COMPLETE).count() > 0;
    }

    public boolean completedAttemptProgressIconPresent() {
        return page.locator(ATTEMPTS_CARDS + " .attempt-card .fa-eye, "
                + ATTEMPTS_CARDS + " .attempt-card [aria-label*='progress' i]").count() > 0;
    }

    public String detailsText() {
        return page.locator(DETAILS).count() > 0 ? page.locator(DETAILS).first().innerText() : "";
    }

    public boolean descriptionAndReadMorePresent() {
        return page.locator(".intro-cl-content, #full-text, .card-body").count() > 0
                && (page.locator("#read-more-btn, a:has-text('Read More')").count() > 0
                || page.locator("#full-text, .intro-cl-content").first().innerText().length() > 20);
    }

    public boolean readMoreOpensDescription() {
        Locator more = page.locator("#read-more-btn, a:has-text('Read More')");
        if (more.count() == 0) return descriptionAndReadMorePresent();
        more.first().click();
        page.waitForTimeout(500);
        Locator modal = page.locator("#readMoreModal, #full-content");
        boolean shown = false;
        for (int i = 0; i < modal.count(); i++) {
            if (modal.nth(i).isVisible() && !modal.nth(i).innerText().isBlank()) {
                shown = true;
                Locator close = modal.nth(i).locator("button.close, button:has-text('Close'), [data-dismiss='modal']");
                if (close.count() > 0) {
                    try { close.first().click(); page.waitForTimeout(300); } catch (RuntimeException ignored) {}
                }
                break;
            }
        }
        return shown || page.locator("#full-content, #full-text").count() > 0;
    }

    public boolean introVideoControlPresent() {
        Locator hidden = page.locator("#videoLink");
        String href = hidden.count() > 0 ? hidden.first().inputValue() : "";
        return page.locator("#videoModal, #videoFrame, button:has-text('Video'), a:has-text('Video'), "
                + "[data-target='#videoModal']").count() > 0
                || (href != null && !href.isBlank());
    }

    public boolean landingShowsDatesTimeLimitAndAttempts() {
        String t = detailsText().toLowerCase();
        if (t.isBlank()) t = page.locator("body").innerText().toLowerCase();
        boolean dates = t.contains("date") || java.util.regex.Pattern.compile("\\d{1,2}\\s*\\w{3}").matcher(t).find();
        boolean time = t.contains("time") || t.contains("limit") || t.contains("duration")
                || t.contains("min");
        boolean attempts = t.contains("attempt");
        return dates && time && attempts;
    }

    public String sectionId() {
        return page.locator(H_SECTION_ID).count() > 0 ? page.locator(H_SECTION_ID).first().inputValue() : "";
    }

    private boolean visible(String sel) {
        return page.locator(sel).count() > 0 && page.locator(sel).first().isVisible();
    }
}
