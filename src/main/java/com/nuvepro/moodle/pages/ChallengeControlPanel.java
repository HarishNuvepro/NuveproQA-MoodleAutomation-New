package com.nuvepro.moodle.pages;

import com.microsoft.playwright.Download;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;
import com.microsoft.playwright.options.WaitForSelectorState;

/**
 * Assessment (challenge) control panel — selectors from challenge-controlpanel.mustache. Detection
 * is CONTROLS-based (cp.php may not expose a container class — the guided lesson). The lab is NOT
 * auto-provisioned: click the "Start hands-on lab" launch button. Lab-id hidden inputs are
 * server-rendered (stale) — callers read the lab id from admin Manage Labs instead.
 */
public class ChallengeControlPanel extends BasePage {
    private boolean lastSubmitActionInputShown;
    public static final String SUBMIT = "#np-ap-cl-submit-attempt-btn";
    public static final String H_LAB_ID = "#np-ap-cl-labId, #np-ap-mod-labId";
    public static final String LAUNCH = "#np-ap-mod-access-launch-btn-acc, #np-ap-mod-access-launch-btn-vm, "
            + "button:has-text('Start hands-on lab'), a:has-text('Start hands-on lab'), "
            + "button:has-text('Start lab'), a:has-text('Start lab'), "
            + "button:has-text('Start Lab'), a:has-text('Start Lab')";
    public static final String START_ASSESSMENT = ".np-ap-cl-challenge-start, #np-ap-cl-challenge-start, "
            + "button:has-text('Start Assessment'), a:has-text('Start Assessment')";
    public static final String CONTINUE_ATTEMPT = "#np-ap-cl-attempt-continue, .np-ap-cl-attempt-continue, "
            + "button:has-text('Continue'), a:has-text('Continue Assessment'), button:has-text('Continue Assessment')";
    public static final String READ_MORE = "#read-more-btn, a:has-text('Read More'), button:has-text('Read More')";
    public static final String ACTION_INPUT_DLG = "#np-ap-cl-actionInput-dlg";
    public static final String ACTION_INPUT_SUBMIT = "#np-ap-cl-actionInput-action";
    /** Catalog action form injected into the submission modal when the template configures inputs. */
    public static final String ACTION_INPUT_FORM = "#actionInputUI, #np-ap-cl-action-input";
    public static final String PROGRESS_LOADER = "#np-ap-mod-lab-loader";
    public static final String PROGRESS_STEPS =
            "#np-ap-mod-lab-loader #step1, #np-ap-mod-lab-loader #step2, "
                    + "#np-ap-mod-lab-loader #step3, #np-ap-mod-lab-loader #step4";
    public static final String PROGRESS_DESCRIPTION = "#np-ap-mod-lab-loader #step-description";
    // lifecycle controls (challenge-controlpanel.mustache)
    public static final String STOP = ".np-ap-challenge-end";                 // "Stop Lab" (btn-danger)
    public static final String CREDENTIALS_BTN = "#openAuthBtn";              // "Lab Credentials" (d-none until ready)
    public static final String CREDENTIALS_PANEL = "#np-ap-cl-tab-access-content";
    public static final String CREDENTIALS_POPUP = "#authPopup";
    public static final String CHANGE_PASSWORD = "#np-ap-mod-set-password-btn";
    public static final String SET_CREDENTIALS = "#setCredentials";
    public static final String SET_CREDENTIALS_INPUT = "#np-ap-mod-setCredentials-input";
    public static final String DOWNLOAD_PDF = "#downloadPdfBtnChallenge";
    public static final String SUPPORT_MAILTO = "#np-ap-lab-tab-support-mailto";
    public static final String USAGE_FAB = "#openLabUsageBtn";
    public static final String GET_COST = "#getCost";
    /** Lab Usage drawer (Playground + Assessment console share this id). */
    public static final String USAGE_POPUP = "#labUsagePopup";
    public static final String INSTRUCTION_FAB = "#openMissionBtn";
    public static final String INSTRUCTION_POPUP = "#missionPopup";
    public static final String INSTRUCTION_BODY = "#printArea, #np-ap-cl-content-mission";
    public static final String ACCESS_LAB_MODAL = "#launchAccModal";
    public static final String ACCESS_LAB_LINKS = "#launchAccBtnContainer a, #launchAccModal a[href], "
            + "#np-ap-mod-lab-acc-launch-buttons a, #np-ap-mod-lab-acc-launch-buttons button, "
            + "a:has-text('Access Lab'), button:has-text('Access Lab'), a:has-text('Access lab')";
    /** Lab-ready signals on the console after Start Assessment (lab auto-provisions on VPAT). */
    public static final String JUMP_TO_CONSOLE = "text=Jump to Console";
    public static final String SIGN_IN_LINK = "a:has-text('Sign in'), a:has-text('Sign In'), "
            + "button:has-text('Sign in'), a:has-text('Sign-in'), a:has-text('Sign-In'), "
            + "a:has-text('sign in'), [href*='signin'], [href*='sign-in']";
    private static final String ANY_MODAL = "[data-region='modal-container'], .modal.show, "
            + ".moodle-dialogue-base, " + ACTION_INPUT_DLG;

    public ChallengeControlPanel(Page page) {
        super(page);
    }

    public boolean isShown() {
        if (!isOnConsolePage()) return false;
        if (submitVisible()) return true;
        Locator launch = page.locator(LAUNCH);
        for (int i = 0; i < launch.count(); i++) {
            try {
                if (launch.nth(i).isVisible()) return true;
            } catch (RuntimeException ignored) {}
        }
        return false;
    }

    /** True while the student is still on the landing with Start Assessment visible. */
    public boolean isOnLandingBeforeStart() {
        try {
            Locator start = page.locator(START_ASSESSMENT);
            for (int i = 0; i < start.count(); i++) {
                if (start.nth(i).isVisible()) return true;
            }
        } catch (Throwable ignored) {}
        return false;
    }

    /** True once Start Assessment has opened the lab console (cp.php). */
    public boolean isOnConsolePage() {
        try {
            if (page.url().contains("cp.php")) return true;
            if (isOnLandingBeforeStart()) return false;
            return launchVisible() || submitVisible() || labConsoleAccessReady();
        } catch (Throwable midNavigation) {
            return false;
        }
    }

    private boolean launchVisible() {
        Locator launch = page.locator(LAUNCH);
        for (int i = 0; i < launch.count(); i++) {
            try {
                if (launch.nth(i).isVisible()) return true;
            } catch (RuntimeException ignored) {}
        }
        return false;
    }

    private boolean submitVisible() {
        try {
            Locator sub = page.locator(SUBMIT);
            return sub.count() > 0 && sub.first().isVisible();
        } catch (Throwable midNavigation) {
            return false;
        }
    }

    public boolean hasContinueAttemptVisible() {
        Locator c = page.locator(CONTINUE_ATTEMPT);
        for (int i = 0; i < c.count(); i++) {
            try {
                if (c.nth(i).isVisible()) return true;
            } catch (RuntimeException ignored) {}
        }
        return false;
    }

    /** Resume an in-progress attempt from the activity landing (sandbox Continue parity). */
    public void clickContinueAttempt() {
        Locator c = page.locator(CONTINUE_ATTEMPT);
        for (int i = 0; i < c.count(); i++) {
            try {
                if (c.nth(i).isVisible()) {
                    c.nth(i).scrollIntoViewIfNeeded();
                    c.nth(i).click(new Locator.ClickOptions().setForce(true));
                    page.waitForTimeout(400);
                    confirmAnyModal();
                    return;
                }
            } catch (RuntimeException ignored) {}
        }
        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Continue")).click();
        page.waitForTimeout(400);
        confirmAnyModal();
    }

    public void openReadMoreOnLanding() {
        Locator btn = page.locator(READ_MORE);
        if (btn.count() == 0) {
            throw new RuntimeException("Read More control is not on the assessment landing");
        }
        btn.first().scrollIntoViewIfNeeded();
        btn.first().click(new Locator.ClickOptions().setForce(true));
        page.waitForTimeout(800);
    }

    /** Read More modal body contains both markers (long description datasets). */
    public boolean readMoreModalContains(String headMarker, String tailMarker) {
        Locator modal = page.locator("[data-region='modal-container'].show, .modal.show, #readMoreModal");
        if (modal.count() == 0) {
            modal = page.locator(".modal-dialog, [role='dialog']");
        }
        if (modal.count() == 0) {
            throw new RuntimeException("Read More modal did not open");
        }
        String text = modal.first().innerText();
        return text.contains(headMarker) && text.contains(tailMarker);
    }

    /** Click Start Assessment and cancel the proceed dialog (no attempt / lab). */
    public void startAssessmentThenCancel() {
        Locator start = page.locator(START_ASSESSMENT);
        if (start.count() > 0 && start.first().isVisible()) {
            start.first().click();
        } else {
            page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Start Assessment")).click();
        }
        page.waitForTimeout(600);
        cancelModal();
    }

    /** Click Start Assessment on the activity landing and confirm the proceed dialog. */
    public boolean clickStartAssessment() {
        Locator start = page.locator(START_ASSESSMENT);
        for (int i = 0; i < start.count(); i++) {
            try {
                if (start.nth(i).isVisible()) {
                    start.nth(i).scrollIntoViewIfNeeded();
                    start.nth(i).click(new Locator.ClickOptions().setForce(true));
                    page.waitForTimeout(250);
                    confirmAnyModal();
                    return true;
                }
            } catch (RuntimeException ignored) {}
        }
        try {
            page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Start Assessment"))
                    .click(new Locator.ClickOptions().setForce(true));
            page.waitForTimeout(250);
            confirmAnyModal();
            return true;
        } catch (RuntimeException ignored) {}
        return false;
    }

    /** Poll until Start Assessment navigates to cp.php or the console controls render. */
    public boolean waitForConsolePage(int maxSeconds) {
        long deadline = System.currentTimeMillis() + maxSeconds * 1000L;
        while (System.currentTimeMillis() < deadline) {
            if (page.url().contains("cp.php")) return true;
            if (!isOnLandingBeforeStart() && (launchVisible() || submitVisible())) return true;
            page.waitForTimeout(250);
        }
        return page.url().contains("cp.php") || (!isOnLandingBeforeStart() && (launchVisible() || submitVisible()));
    }

    /** Block until cp.php loads after Start Assessment (up to maxSeconds). */
    public boolean waitForConsoleUrl(int maxSeconds) {
        long deadline = System.currentTimeMillis() + maxSeconds * 1000L;
        while (System.currentTimeMillis() < deadline) {
            if (page.url().contains("cp.php")) return true;
            page.waitForTimeout(250);
        }
        return page.url().contains("cp.php");
    }

    /** On VPAT assessments the lab auto-starts after Start Assessment; click Start lab only if shown. */
    public void startHandsOnLabIfNeeded() {
        if (hasLabId()) return;
        if (launchVisible()) clickLaunchIfVisible();
    }

    /** Wait until the auto-provisioned lab is ready: Jump to Console, sign-in link, or Submit enabled. */
    public boolean waitForAutoProvisionedLab(int maxSeconds) {
        long deadline = System.currentTimeMillis() + maxSeconds * 1000L;
        while (System.currentTimeMillis() < deadline) {
            if (labConsoleAccessReady()) return true;
            if (launchVisible()) clickLaunchIfVisible();
            page.waitForTimeout(500);
        }
        return labConsoleAccessReady();
    }

    /** Lab console shows access — Jump to Console, sign-in link, or an enabled Submit control. */
    public boolean labConsoleAccessReady() {
        if (jumpToConsoleVisible() || signInLinkVisible()) return true;
        Locator sub = page.locator(SUBMIT);
        try {
            return sub.count() > 0 && sub.first().isVisible() && sub.first().isEnabled();
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    private boolean jumpToConsoleVisible() {
        Locator jump = page.locator(JUMP_TO_CONSOLE);
        for (int i = 0; i < jump.count(); i++) {
            try {
                if (jump.nth(i).isVisible()) return true;
            } catch (RuntimeException ignored) {}
        }
        return false;
    }

    private boolean signInLinkVisible() {
        Locator links = page.locator(SIGN_IN_LINK);
        for (int i = 0; i < links.count(); i++) {
            try {
                if (links.nth(i).isVisible()) return true;
            } catch (RuntimeException ignored) {}
        }
        return false;
    }

    /** Wait until Submit is enabled (lab fully ready for submission). */
    public boolean waitForLabReady(int maxSeconds) {
        return waitEnabled(SUBMIT, maxSeconds);
    }

    /** After Jump to Console / sign-in appears, give the page a short settle window only. */
    public boolean waitBrieflyForSubmitAfterConsoleReady(int settleSeconds) {
        if (!labConsoleAccessReady()) return false;
        return waitEnabled(SUBMIT, Math.max(1, settleSeconds));
    }

    public String labId() {
        for (String sel : H_LAB_ID.split(",\\s*")) {
            Locator h = page.locator(sel);
            if (h.count() > 0) {
                String v = h.first().inputValue();
                if (v != null && !v.trim().isEmpty()) return v.trim();
            }
        }
        return null;
    }

    public void confirmAnyModal() {
        try { confirmModal(); } catch (Throwable ignored) {}
    }

    /** Click Start Assessment and report whether a visible error/blocked message is shown instead of opening. */
    public boolean startBlockedMessageVisible() {
        if (!clickStartAssessment()) return false;
        if (page.url().contains("cp.php")) return false;
        return waitForBlockedMessage(8);
    }

    /** On the lab console, click Start lab when the template requires a manual launch. */
    public boolean clickStartLab() {
        if (isOnLandingBeforeStart()) return false;
        for (int attempt = 0; attempt < 15; attempt++) {
            if (clickLaunchIfVisible()) return true;
            page.waitForTimeout(2_000);
        }
        return false;
    }

    private boolean hasLabId() {
        String id = labId();
        return id != null && !id.isEmpty();
    }

    private boolean clickLaunchIfVisible() {
        Locator launch = page.locator(LAUNCH);
        for (int i = 0; i < Math.min(launch.count(), 6); i++) {
            try {
                if (launch.nth(i).isVisible()) {
                    launch.nth(i).click(new Locator.ClickOptions().setForce(true));
                    page.waitForTimeout(250);
                    confirmAnyModal();
                    submitActionInputIfShown(10);
                    page.waitForTimeout(500);
                    return true;
                }
            } catch (RuntimeException ignored) {}
        }
        return false;
    }

    public boolean waitEnabled(String selector, int maxSeconds) {
        Locator l = page.locator(selector);
        long deadline = System.currentTimeMillis() + maxSeconds * 1000L;
        while (System.currentTimeMillis() < deadline) {
            try { if (l.count() > 0 && l.first().isVisible() && l.first().isEnabled()) return true; }
            catch (Throwable ignored) {}
            page.waitForTimeout(250);
        }
        return false;
    }

    /** Submit the attempt for evaluation: click Submit, tick any ack box, confirm; if the
     *  action-input dialog appears, submit it too. */
    public void submitAttempt() {
        lastSubmitActionInputShown = false;
        page.locator(SUBMIT).first().click();
        waitForModal(ANY_MODAL + ", #submitAttemptCheckbox", 3);
        Locator ack = page.locator("#submitAttemptCheckbox, .modal.show input[type=checkbox]");
        if (ack.count() > 0) {
            try { ack.first().check(new Locator.CheckOptions().setForce(true)); } catch (Throwable ignored) {}
        }
        // The catalog action-input form is injected asynchronously; it may render before or after the
        // "are you sure" confirm, so watch for it while working through the confirm dialogs.
        if (waitForActionInputForm(1)) lastSubmitActionInputShown = true;
        confirmAnyModal();
        if (!lastSubmitActionInputShown && waitForActionInputForm(10)) {
            lastSubmitActionInputShown = true;
        }
        // Some Moodle themes do not match BasePage.confirmModal's save selector. Only retry the
        // confirmation when no action form has appeared, otherwise this would submit that form
        // before the test can observe it.
        if (!lastSubmitActionInputShown) {
            clickVisibleSubmitButton();
            if (waitForActionInputForm(10)) lastSubmitActionInputShown = true;
        }
        if (lastSubmitActionInputShown) {
            submitActionInputIfShown(1);
        }
    }

    /** The template's configured submission action renders its catalog form into a Moodle modal body. */
    private boolean waitForActionInputForm(int maxSeconds) {
        Locator form = page.locator(ACTION_INPUT_FORM);
        long deadline = System.currentTimeMillis() + maxSeconds * 1000L;
        while (System.currentTimeMillis() < deadline) {
            try {
                if (form.count() > 0 && form.first().locator("input, select, textarea").count() > 0) {
                    return true;
                }
            } catch (RuntimeException ignored) {}
            page.waitForTimeout(250);
        }
        return false;
    }

    private void fillActionInputForm() {
        Locator inputs = page.locator(ACTION_INPUT_FORM + " input[type='Text'], "
                + ACTION_INPUT_FORM + " input[type='text']");
        for (int i = 0; i < inputs.count(); i++) {
            try {
                if (inputs.nth(i).inputValue().isBlank()) {
                    inputs.nth(i).fill("automation", new Locator.FillOptions().setForce(true));
                }
            } catch (RuntimeException ignored) {}
        }
    }

    private boolean submitActionInputIfShown(int maxSeconds) {
        if (!waitForActionInputForm(maxSeconds)) return false;
        fillActionInputForm();
        Locator actionSubmit = page.locator(ACTION_INPUT_SUBMIT);
        if (actionSubmit.count() > 0 && actionSubmit.first().isVisible()) {
            actionSubmit.first().click(new Locator.ClickOptions().setForce(true));
        } else {
            clickVisibleSubmitButton();
        }
        page.waitForTimeout(250);
        return true;
    }

    private void clickVisibleSubmitButton() {
        Locator submits = page.locator(
                "[data-region='modal-container'] [data-action='save'], "
                        + "[data-region='modal-container'] button:has-text('Submit'), "
                        + ".modal.show button:has-text('Submit')");
        for (int i = submits.count() - 1; i >= 0; i--) {
            try {
                if (submits.nth(i).isVisible()) {
                    submits.nth(i).click(new Locator.ClickOptions().setForce(true));
                    return;
                }
            } catch (RuntimeException ignored) {}
        }
    }

    public boolean lastSubmitActionInputShown() {
        return lastSubmitActionInputShown;
    }

    /**
     * Exercise the action-input requirement without completing the assessment submission.
     * The provider schema is fetched asynchronously and can take substantially longer than the
     * ordinary confirmation popup.
     */
    public boolean requestSubmissionActionInput(int maxSeconds) {
        page.locator(SUBMIT).first().click();
        boolean shown = waitForActionInputForm(maxSeconds);
        if (shown) {
            Locator cancel = page.locator(
                    "#np-ap-cl-actionInput-cancel, [data-region='modal-container']:visible "
                            + "button:has-text('Cancel')").last();
            try {
                if (cancel.count() > 0 && cancel.isVisible()) cancel.click();
            } catch (RuntimeException ignored) {}
        }
        return shown;
    }

    public boolean waitForFourStepProgress(int maxSeconds) {
        long deadline = System.currentTimeMillis() + maxSeconds * 1000L;
        while (System.currentTimeMillis() < deadline) {
            try {
                Locator loader = page.locator(PROGRESS_LOADER);
                if (loader.count() > 0 && loader.first().isVisible()
                        && page.locator(PROGRESS_STEPS).count() == 4) {
                    Locator description = page.locator(PROGRESS_DESCRIPTION);
                    if (description.count() > 0 && !description.first().innerText().isBlank()) return true;
                }
            } catch (RuntimeException ignored) {}
            page.waitForTimeout(200);
        }
        return false;
    }

    // ---- Wave A lifecycle helpers (all read-only / non-destructive; used on a RUNNING control panel) ----

    /** Open the "Lab Credentials" FAB / panel on the console. */
    public boolean openCredentialsFab() {
        if (openCredentials()) return true;
        Locator fab = page.locator(CREDENTIALS_BTN);
        if (fab.count() == 0 || !fab.first().isVisible()) return false;
        fab.first().click();
        page.waitForTimeout(1_200);
        return true;
    }

    /** Credentials popup/panel shows username and password text. */
    public boolean credentialsShowLoginAndPassword() {
        if (credentialsShowUserAndPassword()) return true;
        Locator popup = page.locator(CREDENTIALS_POPUP + ", " + CREDENTIALS_PANEL);
        for (int i = 0; i < popup.count(); i++) {
            try {
                if (!popup.nth(i).isVisible()) continue;
                String t = popup.nth(i).innerText().toLowerCase();
                if (t.contains("user") && t.contains("password")) return true;
            } catch (RuntimeException ignored) {}
        }
        return false;
    }

    /** Poll until a FAB control is visible (console JS attaches these after lab ready). */
    public boolean waitForFabVisible(String selector, int maxSeconds) {
        long deadline = System.currentTimeMillis() + maxSeconds * 1000L;
        while (System.currentTimeMillis() < deadline) {
            Locator fab = page.locator(selector);
            for (int i = 0; i < fab.count(); i++) {
                try {
                    if (fab.nth(i).isVisible()) return true;
                } catch (RuntimeException ignored) {}
            }
            page.waitForTimeout(500);
        }
        return false;
    }

    private static boolean popupShown(Locator popup) {
        for (int i = 0; i < popup.count(); i++) {
            try {
                if (popup.nth(i).isVisible()) return true;
                Object display = popup.nth(i).evaluate("el => getComputedStyle(el).display");
                if (display != null && !"none".equals(String.valueOf(display))) return true;
            } catch (RuntimeException ignored) {}
        }
        return false;
    }

    /** Open Lab Usage FAB and verify duration + Get Usage control. */
    public boolean labUsageShowsDurationAndGetCost() {
        if (!waitForFabVisible(USAGE_FAB, 45)) return false;
        page.locator(USAGE_FAB).first().click();
        page.waitForTimeout(1_200);
        Locator popup = page.locator(USAGE_POPUP);
        if (!popupShown(popup)) return false;
        String popupText = popup.first().innerText().toLowerCase();
        boolean hasDuration = popupText.contains("duration") || popupText.contains("allotted")
                || popupText.contains("consumed") || popupText.contains("remaining");
        Locator cost = page.locator(GET_COST);
        boolean costVisible = false;
        for (int i = 0; i < cost.count(); i++) {
            try {
                if (cost.nth(i).isVisible()) { costVisible = true; break; }
            } catch (RuntimeException ignored) {}
        }
        return hasDuration && costVisible;
    }

    /** Open the Instructions FAB on the lab console. */
    public boolean openInstructionsFab() {
        if (!waitForFabVisible(INSTRUCTION_FAB, 45)) return false;
        page.locator(INSTRUCTION_FAB).first().click();
        page.waitForTimeout(1_200);
        return popupShown(page.locator(INSTRUCTION_POPUP));
    }

    /** Poll until assessment instructions render inside the mission popup. */
    public boolean waitForInstructionsMarker(String marker, int maxSeconds) {
        if (marker == null || marker.isBlank()) return false;
        long deadline = System.currentTimeMillis() + maxSeconds * 1000L;
        while (System.currentTimeMillis() < deadline) {
            Locator body = page.locator(INSTRUCTION_BODY);
            for (int i = 0; i < body.count(); i++) {
                try {
                    String text = body.nth(i).innerText();
                    String html = body.nth(i).innerHTML();
                    if (text.contains(marker) || html.contains(marker)) return true;
                } catch (RuntimeException ignored) {}
            }
            page.waitForTimeout(500);
        }
        return false;
    }

    public int visibleImagesInSelector(String selector) {
        Locator imgs = page.locator(selector);
        int visible = 0;
        for (int i = 0; i < imgs.count(); i++) {
            try {
                if (imgs.nth(i).isVisible()) visible++;
            } catch (RuntimeException ignored) {}
        }
        return visible;
    }

    /** Click Jump to Console when visible. */
    public boolean clickJumpToConsole() {
        Locator jump = page.locator(JUMP_TO_CONSOLE);
        for (int i = 0; i < jump.count(); i++) {
            try {
                if (jump.nth(i).isVisible()) {
                    jump.nth(i).click();
                    page.waitForTimeout(1_500);
                    return true;
                }
            } catch (RuntimeException ignored) {}
        }
        return false;
    }

    /** True when the Access Lab modal is visible after Jump to Console. */
    public boolean accessLabModalVisible() {
        Locator modal = page.locator(ACCESS_LAB_MODAL);
        for (int i = 0; i < modal.count(); i++) {
            try {
                if (modal.nth(i).isVisible()) return true;
            } catch (RuntimeException ignored) {}
        }
        return false;
    }

    /** Click the first visible Access Lab link/button inside the modal or launch container. */
    public boolean clickAccessLabLink() {
        Locator links = page.locator(ACCESS_LAB_LINKS);
        for (int i = 0; i < links.count(); i++) {
            try {
                if (links.nth(i).isVisible()) {
                    links.nth(i).click();
                    page.waitForTimeout(1_500);
                    return true;
                }
            } catch (RuntimeException ignored) {}
        }
        return false;
    }

    /** Open the "Lab Credentials" panel (shows once the lab is ready). Returns false if not available. */
    public boolean openCredentials() {
        Locator b = page.locator(CREDENTIALS_BTN);
        if (b.count() == 0 || !b.first().isVisible()) return false;
        b.first().click();
        page.waitForTimeout(1_200);
        return true;
    }

    /** Text of the credentials panel (contains the lab username + password once loaded). */
    public String credentialsText() {
        Locator p = page.locator(CREDENTIALS_PANEL);
        return p.count() > 0 ? p.first().innerText() : "";
    }

    /** The credentials panel exposes a username AND a password value. */
    public boolean credentialsShowUserAndPassword() {
        String t = credentialsText().toLowerCase();
        return t.contains("user") && t.contains("password");
    }

    /** The "Change Password" control is available inside the credentials panel. */
    public boolean changePasswordAvailable() {
        return page.locator(CHANGE_PASSWORD).count() > 0;
    }

    /** Click Submit-for-evaluation and report whether a confirmation popup appears — then CANCEL it,
     *  so nothing is actually submitted (non-destructive presence check). */
    public boolean submitShowsConfirmationThenCancel() {
        page.locator(SUBMIT).first().click();
        boolean shown = waitForModal(ANY_MODAL + ", #submitAttemptCheckbox", 6);
        dismissModal();
        return shown;
    }

    /** Click Stop-Lab and report whether a confirmation popup appears — then CANCEL it, so the lab is
     *  NOT actually stopped (non-destructive presence check). The Stop button is enabled by the CP's own
     *  onAccessDetailsCB (once lab access details load), and its confirm is fired ASYNC via get_strings —
     *  so wait for it to be enabled, click NORMALLY (a disabled button never fires its jQuery handler),
     *  then poll for the async popup. */
    public boolean stopShowsConfirmationThenCancel() {
        Locator s = page.locator(STOP);
        if (s.count() == 0) return false;
        // wait up to ~20s for the plugin to enable it (attribute removed == enabled)
        for (int i = 0; i < 20; i++) {
            if (s.first().getAttribute("disabled") == null) break;
            page.waitForTimeout(1_000);
        }
        if (s.first().getAttribute("disabled") != null) return false;   // never enabled
        s.first().click();
        boolean shown = waitForModal(ANY_MODAL, 6);
        dismissModal();
        return shown;
    }

    /** Poll for a confirmation modal to appear (handles the async get_strings().then → notification.confirm). */
    private boolean waitForModal(String selector, int seconds) {
        for (int i = 0; i < seconds * 2; i++) {
            if (page.locator(selector).count() > 0) return true;
            page.waitForTimeout(500);
        }
        return false;
    }

    /** Cancel/close a confirmation modal AND wait for it to actually disappear, so it can't block the
     *  next click (shared-fixture interference). Escape is a fallback. */
    private void dismissModal() {
        Locator cancel = page.locator(ANY_MODAL + " button:has-text('Cancel'), " + ANY_MODAL
                + " button:has-text('No'), " + ANY_MODAL + " [data-dismiss='modal'], "
                + ANY_MODAL + " [data-action='cancel'], " + ANY_MODAL + " .close");
        try { if (cancel.count() > 0 && cancel.first().isVisible()) cancel.first().click(); }
        catch (Throwable ignored) {}
        for (int i = 0; i < 12; i++) {
            if (!modalVisible()) return;
            try { page.keyboard().press("Escape"); } catch (Throwable ignored) {}
            page.waitForTimeout(400);
        }
    }

    private boolean waitForBlockedMessage(int seconds) {
        Locator msg = page.locator(".alert, .alert-danger, .alert-warning, .notification, .toast, "
                + "[role='alert'], .modal.show .modal-body, [data-region='modal-container']");
        long deadline = System.currentTimeMillis() + seconds * 1000L;
        while (System.currentTimeMillis() < deadline) {
            try {
                for (int i = 0; i < msg.count(); i++) {
                    if (!msg.nth(i).isVisible()) continue;
                    String text = msg.nth(i).innerText().toLowerCase();
                    if (text.contains("not available")
                            || text.contains("not allowed")
                            || text.contains("cannot")
                            || text.contains("start date")
                            || text.contains("not open")
                            || text.contains("not yet")) {
                        return true;
                    }
                }
            } catch (Throwable ignored) {}
            page.waitForTimeout(250);
        }
        return false;
    }

    private boolean modalVisible() {
        Locator m = page.locator(ANY_MODAL);
        for (int i = 0; i < m.count(); i++) {
            try { if (m.nth(i).isVisible()) return true; } catch (Throwable ignored) {}
        }
        return false;
    }

    /** Current password value from the credentials panel (any np-ap-mod-input-* whose id/key looks like password). */
    public String credentialsPasswordValue() {
        Locator preferred = page.locator("#np-ap-mod-input-loginpassword, " + CREDENTIALS_PANEL
                + " input[id*='assword'], " + CREDENTIALS_POPUP + " input[id*='assword']");
        for (int i = 0; i < preferred.count(); i++) {
            try {
                String v = preferred.nth(i).inputValue();
                if (v != null && !v.isBlank()) return v.trim();
            } catch (RuntimeException ignored) {}
        }
        Locator all = page.locator(CREDENTIALS_PANEL + " input[id^='np-ap-mod-input-']");
        for (int i = 0; i < all.count(); i++) {
            try {
                String id = all.nth(i).getAttribute("id");
                if (id != null && id.toLowerCase().contains("pass")) {
                    String v = all.nth(i).inputValue();
                    if (v != null && !v.isBlank()) return v.trim();
                }
            } catch (RuntimeException ignored) {}
        }
        return "";
    }

    /**
     * Open credentials → Change Password → fill new password → Save Changes.
     * Returns false if the change-password control/dialog is unavailable.
     */
    public boolean changeLabPassword(String newPassword) {
        if (!openCredentialsFab()) return false;
        // set-credentials.js can race on AJAX-injected CP and leave .np-ap-mod-set-credentials.d-none
        // even for AMAZON ACCOUNT. Unhide when cloud/labType allow it, then click.
        ensureChangePasswordVisible();
        Locator link = page.locator(CHANGE_PASSWORD);
        if (link.count() == 0) return false;
        try {
            if (!link.first().isVisible()) {
                link.first().evaluate("el => { el.closest('.np-ap-mod-set-credentials')?.classList.remove('d-none'); }");
            }
            link.first().scrollIntoViewIfNeeded();
            link.first().click(new Locator.ClickOptions().setForce(true));
        } catch (RuntimeException e) {
            return false;
        }
        // Catalog UI is loaded via AJAX after click — wait for inputs inside #setCredentials.
        Locator fields = page.locator(SET_CREDENTIALS + " input:not([type='hidden']):not([type='checkbox']):not([type='radio']), "
                + SET_CREDENTIALS_INPUT + " input:not([type='hidden']):not([type='checkbox']):not([type='radio'])");
        try {
            fields.first().waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE).setTimeout(20_000));
        } catch (RuntimeException e) {
            // Modal shell may be visible before fields render
            try {
                page.locator(SET_CREDENTIALS + ", " + SET_CREDENTIALS_INPUT + ", [data-region='modal-container'] .modal")
                        .first().waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE).setTimeout(5_000));
            } catch (RuntimeException ignored) {
                return false;
            }
            page.waitForTimeout(2_000);
        }
        boolean filled = false;
        fields = page.locator(SET_CREDENTIALS + " input:not([type='hidden']):not([type='checkbox']):not([type='radio']), "
                + "[data-region='modal-container'] " + SET_CREDENTIALS + " input:not([type='hidden'])");
        for (int i = 0; i < fields.count(); i++) {
            try {
                Locator f = fields.nth(i);
                if (!f.isVisible()) continue;
                String id = String.valueOf(f.getAttribute("id")).toLowerCase();
                String name = String.valueOf(f.getAttribute("name")).toLowerCase();
                String type = String.valueOf(f.getAttribute("type")).toLowerCase();
                String placeholder = String.valueOf(f.getAttribute("placeholder")).toLowerCase();
                if (type.contains("password") || id.contains("pass") || name.contains("pass")
                        || placeholder.contains("pass") || fields.count() <= 2) {
                    f.fill(newPassword);
                    filled = true;
                }
            } catch (RuntimeException ignored) {}
        }
        if (!filled && fields.count() > 0) {
            try { fields.last().fill(newPassword); filled = true; } catch (RuntimeException ignored) {}
        }
        if (!filled) return false;
        // Moodle SAVE_CANCEL modal — Save / Save changes
        boolean saved = clickSaveOnVisibleModal();
        if (!saved) confirmAnyModal();
        page.waitForTimeout(2_500);
        return true;
    }

    /** Unhide Change Password when this is an AWS Account lab (product race can leave d-none). */
    private void ensureChangePasswordVisible() {
        page.evaluate("""
            () => {
              const cloud = (document.querySelector('#np-ap-mod-cloud') || {}).value || '';
              const labType = (document.querySelector('#np-ap-mod-labType') || {}).value || '';
              if ((cloud === 'AMAZON' || cloud === 'MICROSOFT_CSP') && labType === 'ACCOUNT') {
                document.querySelectorAll('.np-ap-mod-set-credentials').forEach(el => el.classList.remove('d-none'));
              }
            }
            """);
        page.waitForTimeout(300);
    }

    private boolean clickSaveOnVisibleModal() {
        Locator save = page.locator("[data-region='modal-container'] [data-action='save'], "
                + ".modal.show [data-action='save'], "
                + "[data-region='modal-container'] button:has-text('Save'), "
                + ".modal.show button:has-text('Save')");
        for (int attempt = 0; attempt < 20; attempt++) {
            for (int i = 0; i < save.count(); i++) {
                try {
                    if (save.nth(i).isVisible()) {
                        save.nth(i).click();
                        return true;
                    }
                } catch (RuntimeException ignored) {}
            }
            page.waitForTimeout(250);
        }
        return false;
    }

    /** True when a success toast/notification confirms the password change. */
    public boolean passwordChangeSuccessVisible() {
        Locator notes = page.locator(".toast, .alert-success, .notification, [role='alert'], .alert");
        for (int i = 0; i < notes.count(); i++) {
            try {
                if (!notes.nth(i).isVisible()) continue;
                String t = notes.nth(i).innerText().toLowerCase();
                if (t.contains("password has been changed") || t.contains("password changed")
                        || (t.contains("credentials") && t.contains("success"))) {
                    return true;
                }
            } catch (RuntimeException ignored) {}
        }
        String body = page.locator("body").innerText().toLowerCase();
        return body.contains("password has been changed");
    }

    /** Click Stop Lab and confirm (destructive — lab moves to Stopped). */
    public boolean stopLabAndConfirm() {
        Locator s = page.locator(STOP);
        if (s.count() == 0) return false;
        for (int i = 0; i < 30; i++) {
            if (s.first().getAttribute("disabled") == null) break;
            page.waitForTimeout(1_000);
        }
        if (s.first().getAttribute("disabled") != null) return false;
        s.first().click();
        if (!waitForModal(ANY_MODAL, 8)) return false;
        confirmAnyModal();
        page.waitForTimeout(1_500);
        return true;
    }

    /** Open Instructions and click Download PDF; returns the Playwright Download or null. */
    public Download downloadInstructionsPdf() {
        if (!openInstructionsFab()) return null;
        Locator btn = page.locator(DOWNLOAD_PDF);
        if (btn.count() == 0) return null;
        try {
            btn.first().waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE).setTimeout(10_000));
        } catch (RuntimeException e) {
            return null;
        }
        try {
            return page.waitForDownload(new Page.WaitForDownloadOptions().setTimeout(45_000),
                    () -> btn.first().click());
        } catch (RuntimeException e) {
            return null;
        }
    }

    /**
     * Click "Need help? Contact our support team" and capture the mailto: URL via a window.open stub
     * (Playwright reports a useless popup URL for mailto: on Windows).
     */
    public String openSupportMailtoUrl() {
        Locator link = page.locator(SUPPORT_MAILTO);
        if (link.count() == 0) return "";
        try {
            if (!link.first().isVisible()) {
                Locator tab = page.locator("a[href='#np-ap-cl-tab-support'], .np-ap-cl-tab-title:has-text('Support')");
                if (tab.count() > 0) tab.first().click();
                page.waitForTimeout(500);
            }
        } catch (RuntimeException ignored) {}
        if (link.count() == 0 || !link.first().isVisible()) return "";

        page.evaluate("""
            () => {
              window.__npMailtoOpened = null;
              window.__npOpenOrig = window.open;
              window.open = function(url) {
                window.__npMailtoOpened = url;
                return { focus: function() {}, close: function() {} };
              };
            }
            """);
        try {
            link.first().click();
            page.waitForTimeout(800);
            Object opened = page.evaluate("() => window.__npMailtoOpened");
            if (opened != null && !String.valueOf(opened).isBlank()) {
                return String.valueOf(opened);
            }
        } finally {
            page.evaluate("""
                () => {
                  if (window.__npOpenOrig) window.open = window.__npOpenOrig;
                }
                """);
        }

        // Fallback: build expected mailto from hidden support email (same as plugin JS).
        Object supportEmail = page.evaluate(
                "() => (document.querySelector('#np-ap-pl-supportEmail') || {}).value || ''");
        String email = supportEmail == null ? "" : String.valueOf(supportEmail).trim();
        if (!email.isBlank() && email.contains("@")) {
            return "mailto:" + email;
        }
        return "";
    }
}
