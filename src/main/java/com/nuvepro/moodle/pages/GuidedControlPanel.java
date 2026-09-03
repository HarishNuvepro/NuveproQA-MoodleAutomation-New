package com.nuvepro.moodle.pages;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;

/**
 * Guided (practice project) control panel — selectors from templates/guided-controlpanel.mustache.
 * Stop and Submit start disabled and are enabled by the access-ready callback once the lab is up.
 * Stop's confirm modal has a mandatory acknowledge checkbox (#guidedStopCheckbox) before Yes.
 */
public class GuidedControlPanel extends BasePage {
    public static final String CONTAINER = ".guided-control-panel";
    public static final String STOP = ".np-ap-gl-guide-end";
    public static final String SUBMIT = "#np-ap-gl-submit-project-btn";
    public static final String H_LAB_ID = "#np-ap-gl-labId";
    public static final String STOP_ACK = "#guidedStopCheckbox";
    public static final String SUBMIT_ACK = "#submitAttemptCheckbox";
    /** The in-CP "Start hands-on lab" launch button (provisions the lab; same as playground). */
    public static final String LAUNCH = "#np-ap-mod-access-launch-btn-acc, #np-ap-mod-access-launch-btn-vm, "
            + "button:has-text('Start hands-on lab'), a:has-text('Start hands-on lab')";

    public GuidedControlPanel(Page page) {
        super(page);
    }

    /** CP detection by its CONTROLS (Stop/Submit visible) — the cp.php rendering does not expose
     *  the .guided-control-panel container (same lesson as the playground console). */
    public boolean isShown() {
        try {
            Locator stop = page.locator(STOP);
            Locator sub = page.locator(SUBMIT);
            return (stop.count() > 0 && stop.first().isVisible())
                    || (sub.count() > 0 && sub.first().isVisible());
        } catch (Throwable midNavigation) {   // page may be navigating (start reloads into the CP)
            return false;
        }
    }

    /** If no lab id yet, click the "Start hands-on lab" launch button (+ confirm) to provision. */
    public void startHandsOnLabIfNeeded() {
        String id = labId();
        if (id != null && !id.isEmpty()) return;
        Locator launch = page.locator(LAUNCH);
        for (int i = 0; i < Math.min(launch.count(), 4); i++) {
            if (launch.nth(i).isVisible()) {
                launch.nth(i).click();
                page.waitForTimeout(1_200);
                confirmAnyModal();
                return;
            }
        }
    }

    public String labId() {
        // both are SERVER-rendered at page load (empty until a reload after provisioning)
        for (String sel : new String[]{H_LAB_ID, "#np-ap-mod-labId"}) {
            Locator h = page.locator(sel);
            if (h.count() > 0) {
                String v = h.first().inputValue();
                if (v != null && !v.trim().isEmpty()) return v.trim();
            }
        }
        return null;
    }

    /** Wait until a control (Stop/Submit) becomes enabled; false on timeout. */
    public boolean waitEnabled(String selector, int maxSeconds) {
        Locator l = page.locator(selector);
        for (int i = 0; i < maxSeconds; i++) {
            if (l.count() > 0 && l.first().isVisible() && l.first().isEnabled()) return true;
            page.waitForTimeout(1_000);
        }
        return false;
    }

    /** Confirm whatever modal is visible (e.g. the "Start Practice Project. Proceed?" Yes). */
    public void confirmAnyModal() {
        try { confirmModal(); } catch (Throwable ignored) {}
    }

    /**
     * Click a guarded action (Stop/Submit): opens a confirm modal whose Yes is gated by an
     * acknowledge checkbox — tick it (force; modals may stack hidden duplicates), then confirm.
     */
    public void clickGuardedAction(String actionSelector, String ackSelector) {
        page.locator(actionSelector).first().click();
        page.waitForTimeout(1_000);
        Locator ack = page.locator(ackSelector);
        if (ack.count() > 0) {
            try { ack.first().check(new Locator.CheckOptions().setForce(true)); } catch (Throwable ignored) {}
            page.waitForTimeout(400);
        }
        try { confirmModal(); } catch (Throwable ignored) {}
        page.waitForTimeout(1_500);
    }

    public boolean submitPresentAndGetProgressRemoved() {
        Locator submit = page.locator(SUBMIT);
        Locator progress = page.locator("#performProgressCheck");
        boolean submitShown = submit.count() > 0 && submit.first().isVisible();
        boolean progressRemoved = progress.count() == 0 || !progress.first().isVisible();
        return submitShown && progressRemoved;
    }

    public boolean consoleLabelsAndUtilitiesPresent() {
        Locator instruction = page.locator("#openMissionBtn");
        String title = instruction.count() > 0 ? instruction.first().getAttribute("title") : "";
        boolean labels = title != null && title.toLowerCase().contains("instruction");
        boolean timer = page.locator("#np-ap-cl-timer-cp, .time-remaining-text").count() > 0;
        boolean support = page.locator("#np-ap-lab-tab-support-mailto").count() > 0;
        return labels && timer && support;
    }

    public boolean credentialsPresent() {
        Locator credentials = page.locator("#openAuthBtn");
        return credentials.count() > 0 && credentials.first().isVisible()
                && page.locator("body").innerText().toLowerCase().contains("lab credential");
    }

    /** Open a guarded action, assert a meaningful visible confirmation, then cancel it. */
    public boolean hasMeaningfulConfirmation(String actionSelector) {
        Locator action = page.locator(actionSelector);
        if (action.count() == 0 || !action.first().isVisible() || !action.first().isEnabled()) return false;
        action.first().click();
        page.waitForTimeout(700);
        Locator modals = page.locator("[data-region='modal-container'], .modal.show");
        Locator modal = null;
        for (int i = 0; i < modals.count(); i++) {
            if (modals.nth(i).isVisible()) { modal = modals.nth(i); break; }
        }
        if (modal == null) return false;
        String text = modal.innerText().trim().toLowerCase();
        boolean meaningful = text.length() > 15
                && (text.contains("submit") || text.contains("stop") || text.contains("attempt"));
        Locator cancel = modal.locator("button:has-text('Cancel'), button:has-text('No'), "
                + "button.close, [data-action='cancel']");
        if (cancel.count() > 0) {
            try { cancel.first().click(); } catch (RuntimeException ignored) {}
        }
        page.waitForTimeout(400);
        return meaningful;
    }

    public boolean viewProgressPresent() {
        return page.locator("a[href*='progress'], [title*='progress' i], .view-progress").count() > 0;
    }
}
