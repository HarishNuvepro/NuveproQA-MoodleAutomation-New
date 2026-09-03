package com.nuvepro.moodle.pages;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.assertions.LocatorAssertions;
import com.microsoft.playwright.assertions.PlaywrightAssertions;

/**
 * Playground lab CONTROL PANEL (console). Selectors read directly from the plugin source:
 *  - Activity\cloudlabs\templates\lab.mustache
 *  - Activity\cloudlabs\templates\playground-controlpanel.mustache
 * Mirrors pages/playground_control_panel.py.
 */
public class PlaygroundControlPanel extends BasePage {
    public static final String MASTER = ".np-ap-pl-template-master-container";
    public static final String START_LAB_ACCOUNT = "#np-ap-mod-access-launch-btn-acc";
    public static final String START_LAB_VM = "#np-ap-mod-access-launch-btn-vm";
    public static final String LAUNCH_START =
            "#np-ap-mod-access-launch-btn-acc, #np-ap-mod-access-launch-btn-vm";
    public static final String STOP_LAB = ".np-ap-pl-playground-end";
    public static final String REFRESH_LAB = "#np-ap-mod-btn-tb-refresh";
    public static final String SPEED_TEST = "#np-ap-mod-btn-tb-speedTest";
    public static final String FULLSCREEN =
            "#np-ap-mod-btn-tb-fullscreen, #np-ap-mod-btn-tb-fullScreen, "
                    + "[id*='fullscreen' i], [title*='Full screen' i], [aria-label*='Full screen' i]";
    public static final String OPEN_NEW_TAB =
            "#np-ap-mod-btn-tb-newtab, #np-ap-mod-btn-tb-newTab, "
                    + "[id*='newtab' i], [title*='new tab' i], [aria-label*='new tab' i], "
                    + "a:has-text('Open in new'), button:has-text('Open in new')";
    public static final String VM_IFRAME = "#np-ap-mod-lab-container-iframe iframe, "
            + "#np-ap-mod-lab-container-iframe, iframe[id*='lab']";
    public static final String INSTRUCTION_FAB = "#openMissionBtn";
    public static final String USAGE_FAB = "#openLabUsageBtn";
    public static final String LAB_INFO_FAB = "#openLabInfoBtn";
    public static final String NOTES_TAB = "#notes-tab";
    public static final String NOTES_TEXTAREA = "#np-ap-pl-notes";
    public static final String NOTES_SAVE = "#np-ap-pl-note-save-btn";
    public static final String ACCESS_LAB_MODAL = "#launchAccModal";
    public static final String BACK_BTN =
            "#pl-back-btn, #np-ap-pl-back-btn, #np-ap-mod-btn-tb-back, "
                    + ".np-ap-pl-template-master-container button:has-text('Back'), "
                    + ".np-ap-pl-template-master-container a:has-text('Back')";
    public static final String SUPPORT_TAB =
            "a[href='#np-ap-cl-tab-support'], a[href='#np-ap-pl-tab-support'], "
                    + ".np-ap-cl-tab-title:has-text('Support'), a:has-text('Support')";
    public static final String SUPPORT_MAILTO =
            "#np-ap-lab-tab-support-mailto, a[href^='mailto:'], "
                    + "a:has-text('Contact our support'), a:has-text('Need help')";
    public static final String H_LAB_STATUS = "#np-ap-mod-loadTimeStatus";
    public static final String PROGRESS_LOADER = "#np-ap-mod-lab-loader";
    public static final String PROGRESS_STEPS =
            "#np-ap-mod-lab-loader #step1, #np-ap-mod-lab-loader #step2, "
                    + "#np-ap-mod-lab-loader #step3, #np-ap-mod-lab-loader #step4";
    public static final String PROGRESS_DESCRIPTION = "#np-ap-mod-lab-loader #step-description";
    /** Console + landing variants — sandbox may populate any of these after Start Lab. */
    public static final String H_LAB_ID =
            "#np-ap-mod-labId, #np-ap-pl-labid, #np-ap-cl-labId, input[name='labId'], input[id*='labId'], input[id*='labid']";

    public PlaygroundControlPanel(Page page) {
        super(page);
    }

    public void open(int cmid) {
        navigate("/mod/cloudlabs/view.php?id=" + cmid);
    }

    public void openInstructions() {
        page.locator(INSTRUCTION_FAB).click();
    }

    public void saveNote(String text) {
        page.locator(NOTES_TAB).click();
        page.locator(NOTES_TEXTAREA).fill(text);
        page.locator(NOTES_SAVE).click();
    }

    /** Click Stop Lab and confirm; no-op if already stopped (button disabled). */
    public void stopLab() {
        Locator btn = page.locator(STOP_LAB).first();
        if (!btn.isEnabled()) return;
        btn.click();
        confirmModal();
    }

    public boolean isStopLabEnabled() {
        Locator btn = page.locator(STOP_LAB);
        try {
            return btn.count() > 0 && btn.first().isVisible() && btn.first().isEnabled();
        } catch (RuntimeException e) {
            return false;
        }
    }

    /** True when control panel chrome is visible (Stop Lab and/or Start Lab). */
    public boolean isShown() {
        Locator controls = page.locator(STOP_LAB + ", " + LAUNCH_START + ", " + MASTER);
        try {
            return controls.count() > 0 && controls.first().isVisible();
        } catch (RuntimeException e) {
            return false;
        }
    }

    /**
     * After Stop Lab: wait until Stop is disabled, landing returns, or timeout.
     * Prefer pairing with CloudLabs API when lab id is known.
     */
    public boolean waitUntilStopSettled(int maxSeconds) {
        long deadline = System.currentTimeMillis() + maxSeconds * 1000L;
        while (System.currentTimeMillis() < deadline) {
            if (!isStopLabEnabled()) return true;
            if (page.locator(PlaygroundLanding.CONTAINER).count() > 0) {
                try {
                    if (page.locator(PlaygroundLanding.CONTAINER).first().isVisible()) return true;
                } catch (RuntimeException ignored) {}
            }
            page.waitForTimeout(2_000);
        }
        return !isStopLabEnabled();
    }

    public String labStatus() {
        return hiddenInputValue(H_LAB_STATUS);
    }

    public String labId() {
        Locator h = page.locator(H_LAB_ID);
        for (int i = 0; i < h.count(); i++) {
            try {
                String v = h.nth(i).inputValue();
                if (v != null && !v.trim().isEmpty() && !"0".equals(v.trim())) return v.trim();
            } catch (RuntimeException ignored) {}
            try {
                String attr = h.nth(i).getAttribute("value");
                if (attr != null && !attr.trim().isEmpty() && !"0".equals(attr.trim())) return attr.trim();
            } catch (RuntimeException ignored) {}
        }
        try {
            Object js = page.evaluate("""
                    () => {
                      const sels = ['#np-ap-mod-labId','#np-ap-pl-labid','#np-ap-cl-labId',
                        "input[name='labId']","input[id*='labId']","input[id*='labid']"];
                      for (const s of sels) {
                        for (const el of document.querySelectorAll(s)) {
                          const v = (el.value || el.getAttribute('value') || '').trim();
                          if (v && v !== '0') return v;
                        }
                      }
                      return '';
                    }
                    """);
            if (js != null) {
                String v = String.valueOf(js).trim();
                if (!v.isEmpty() && !"null".equals(v)) return v;
            }
        } catch (RuntimeException ignored) {}
        return "";
    }

    /** After Start Sandbox + confirm, control panel content loads in-place. */
    public void expectControlPanelLoaded(double timeoutMs) {
        PlaywrightAssertions.assertThat(page.locator(STOP_LAB + ", " + LAUNCH_START).first())
                .isVisible(new LocatorAssertions.IsVisibleOptions().setTimeout(timeoutMs));
    }

    public void expectControlPanelLoaded() {
        expectControlPanelLoaded(60_000);
    }

    /** Click Start Lab (Account or VM launch). Returns false if the control is not shown. */
    public boolean clickStartLab() {
        Locator btn = page.locator(LAUNCH_START);
        if (btn.count() == 0) return false;
        try {
            btn.first().click(new Locator.ClickOptions().setForce(true));
        } catch (RuntimeException ignored) {}
        jsClick("#np-ap-mod-access-launch-btn-acc");
        jsClick("#np-ap-mod-access-launch-btn-vm");
        page.waitForTimeout(1_500);
        return true;
    }

    /** Verify the action loader is visibly exercising the plugin's four-step tracker. */
    public boolean waitForFourStepProgress(int maxSeconds) {
        long deadline = System.currentTimeMillis() + maxSeconds * 1000L;
        while (System.currentTimeMillis() < deadline) {
            try {
                Locator loader = page.locator(PROGRESS_LOADER);
                int steps = page.locator(PROGRESS_STEPS).count();
                if (loader.count() > 0 && loader.first().isVisible() && steps >= 4) {
                    return true;
                }
                if (steps >= 4) {
                    String description = page.locator(PROGRESS_DESCRIPTION).count() == 0
                            ? "" : page.locator(PROGRESS_DESCRIPTION).first().textContent();
                    if (description != null && !description.isBlank()) return true;
                }
                if (loader.count() > 0 && loader.first().isVisible()) return true;
            } catch (RuntimeException ignored) {}
            page.waitForTimeout(200);
        }
        return false;
    }

    /**
     * After Start Lab: wait until the lab surface is shown.
     * Account labs never get a guacamole iframe and Stop Lab stays disabled until much later;
     * lab.js treats ready as {@code #np-ap-mod-lab-container-lablinks} / topbar losing {@code d-none}.
     */
    public boolean waitUntilLabLoaded(int maxSeconds) {
        long deadline = System.currentTimeMillis() + maxSeconds * 1000L;
        int poll = 0;
        while (System.currentTimeMillis() < deadline) {
            poll++;
            String why = labSurfaceReadyReason();
            if (!why.isBlank()) {
                System.out.println("[SB-user] lab loaded (" + why + ") poll=" + poll);
                return true;
            }
            page.waitForTimeout(2_000);
        }
        System.out.println("[SB-user] lab NOT loaded after " + maxSeconds + "s url=" + page.url()
                + " ready=" + labSurfaceReadyReason());
        return false;
    }

    /**
     * @return a short reason when the lab UI is ready, else empty.
     */
    public String labSurfaceReadyReason() {
        try {
            Object r = page.evaluate("""
                    () => {
                      const shown = (sel) => {
                        const el = document.querySelector(sel);
                        return !!(el && !el.classList.contains('d-none'));
                      };
                      const stop = document.querySelector('.np-ap-pl-playground-end');
                      if (stop && !stop.disabled && stop.offsetParent) return 'Stop Lab enabled';
                      if (shown('#np-ap-mod-lab-container-lablinks')) return 'account Access Lab panel';
                      const acc = document.querySelectorAll(
                          '#np-ap-mod-lab-acc-launch-buttons a, #np-ap-mod-lab-acc-launch-buttons button');
                      if (acc.length > 0) return 'Access Lab buttons';
                      const iframe = document.querySelector(
                          '#np-ap-mod-lab-container-iframe iframe, #np-ap-mod-lab-container iframe, '
                          + 'iframe[src*="guacamole"], iframe[id*="lab_iframe"]');
                      if (iframe) {
                        const src = iframe.getAttribute('src') || iframe.src || '';
                        const hidden = iframe.closest('.d-none');
                        if (src && src !== 'about:blank' && !hidden) return 'lab iframe present';
                      }
                      if (shown('#np-ap-mod-lab-topbar')) return 'lab toolbar';
                      const start = document.querySelector(
                          '#np-ap-mod-access-launch-btn-acc, #np-ap-mod-access-launch-btn-vm');
                      if (start && start.classList.contains('d-none') && shown('.np-ap-pl-template-master-container'))
                        return 'Start Lab hidden (launched)';
                      const jump = Array.from(document.querySelectorAll('a,button'))
                          .find(el => (el.textContent || '').includes('Jump to Console'));
                      if (jump && jump.offsetParent) return 'Jump to Console';
                      return '';
                    }
                    """);
            return r == null ? "" : String.valueOf(r).trim();
        } catch (RuntimeException e) {
            return "";
        }
    }

    /** True when lab.js has shown the iframe/account toolbar (removed {@code d-none}). */
    public boolean waitUntilToolbarReady(int maxSeconds) {
        long deadline = System.currentTimeMillis() + maxSeconds * 1000L;
        while (System.currentTimeMillis() < deadline) {
            try {
                Object shown = page.evaluate("""
                        () => {
                          const bar = document.querySelector('#np-ap-mod-lab-topbar');
                          if (bar && !bar.classList.contains('d-none')) return true;
                          const iframe = document.querySelector(
                            '#np-ap-mod-lab-container-iframe iframe, iframe[src*="guacamole"]');
                          const src = iframe && (iframe.getAttribute('src') || iframe.src || '');
                          return !!(src && src !== 'about:blank' && !iframe.closest('.d-none'));
                        }
                        """);
                if (Boolean.TRUE.equals(shown)) return true;
            } catch (RuntimeException ignored) {}
            page.waitForTimeout(1_000);
        }
        return page.locator("#np-ap-mod-btn-tb-fullscreen, #np-ap-mod-btn-tb-newtab, "
                + "#np-ap-mod-btn-tb-speedTest").count() > 0;
    }

    /** DOM click — works when the control is in a {@code d-none} ancestor (Playwright visibility fails). */
    public void jsClick(String selector) {
        page.evaluate("""
                (sel) => {
                  const el = document.querySelector(sel);
                  if (el) el.click();
                }
                """, selector);
    }

    public boolean clickJumpToConsole() {
        Locator jump = page.getByText("Jump to Console");
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

    public boolean accessLabModalVisible() {
        Locator modal = page.locator(ACCESS_LAB_MODAL);
        for (int i = 0; i < modal.count(); i++) {
            try {
                if (modal.nth(i).isVisible()) return true;
            } catch (RuntimeException ignored) {}
        }
        return false;
    }

    public static final String ACCESS_LAB_LINKS =
            "#launchAccBtnContainer a, #launchAccModal a[href], "
                    + "#np-ap-mod-lab-acc-launch-buttons a, #np-ap-mod-lab-acc-launch-buttons button, "
                    + "a:has-text('Access Lab'), button:has-text('Access Lab'), a:has-text('Access lab')";

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

    /** Open Instructions FAB and return #printArea text (empty if unavailable). */
    public String openInstructionsAndRead() {
        Locator fab = page.locator(INSTRUCTION_FAB);
        if (!waitVisible(fab, 45_000)) return "";
        fab.first().click();
        page.waitForTimeout(1_200);
        Locator area = page.locator("#printArea");
        String text = area.count() > 0 ? area.first().innerText().trim() : "";
        if (text.isBlank()) {
            Locator popup = page.locator("#missionPopup");
            if (popup.count() > 0) text = popup.first().innerText().trim();
        }
        closeIfPresent("#closeMission");
        return text;
    }

    /** Open Lab Usage (duration) and return panel text. Polls FAB visibility after lab ready. */
    public String openUsageAndRead() {
        Locator fab = page.locator(USAGE_FAB);
        if (!waitVisible(fab, 60_000)) return "";
        // Remove d-none by waiting until visible
        fab.first().click();
        page.waitForTimeout(1_500);
        Locator popup = page.locator("#labUsagePopup, #showCostUsage");
        String text = popup.count() > 0 ? popup.first().innerText().trim() : "";
        Locator getCost = page.locator("#getCost");
        if (getCost.count() > 0 && getCost.first().isVisible()) {
            try {
                getCost.first().click();
                page.waitForTimeout(2_000);
                text = popup.count() > 0 ? popup.first().innerText().trim() : text;
            } catch (RuntimeException ignored) {}
        }
        closeIfPresent("#closeLabUsage");
        return text;
    }

    /** Open Lab Credentials and return panel text. */
    public String openCredentialsAndRead() {
        Locator fab = page.locator("#openAuthBtn");
        if (!waitVisible(fab, 60_000)) return "";
        fab.first().click();
        page.waitForTimeout(1_500);
        Locator panel = page.locator("#authPopup, #np-ap-pl-tab-access-content");
        String text = panel.count() > 0 ? panel.first().innerText().trim() : "";
        closeIfPresent("#closeAuth");
        return text;
    }

    /** Open Lab Info and return panel text. */
    public String openLabInfoAndRead() {
        Locator fab = page.locator(LAB_INFO_FAB);
        if (!waitVisible(fab, 45_000)) return "";
        fab.first().click();
        page.waitForTimeout(1_200);
        Locator popup = page.locator("#labInfoPopup");
        String text = popup.count() > 0 ? popup.first().innerText().trim() : "";
        closeIfPresent("#closeLabInfo");
        return text;
    }

    /** Click the console Back control if present. Returns false when the control is missing. */
    public boolean clickBackToLanding() {
        Locator back = page.locator(BACK_BTN);
        for (int i = 0; i < back.count(); i++) {
            try {
                if (!back.nth(i).isVisible()) continue;
                back.nth(i).click(new Locator.ClickOptions().setForce(true));
                page.waitForTimeout(1_500);
                return true;
            } catch (RuntimeException ignored) {}
        }
        Locator crumb = page.locator(".breadcrumb a[href*='view.php'], "
                + ".navbar a[href*='/mod/cloudlabs/view.php']");
        for (int i = 0; i < crumb.count(); i++) {
            try {
                if (!crumb.nth(i).isVisible()) continue;
                crumb.nth(i).click(new Locator.ClickOptions().setForce(true));
                page.waitForTimeout(1_500);
                return true;
            } catch (RuntimeException ignored) {}
        }
        return false;
    }

    /**
     * Open Support tab if needed, click the contact-support link, return mailto URL
     * (window.open stub — mailto popups are unreliable on Windows).
     */
    public String openSupportMailtoUrl() {
        Locator tab = page.locator(SUPPORT_TAB);
        if (tab.count() > 0) {
            try {
                if (tab.first().isVisible()) tab.first().click();
                page.waitForTimeout(600);
            } catch (RuntimeException ignored) {}
        }
        Locator link = page.locator(SUPPORT_MAILTO);
        String href = "";
        if (link.count() > 0) {
            try {
                String h = link.first().getAttribute("href");
                if (h != null) href = h.trim();
            } catch (RuntimeException ignored) {}
        }
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
            if (link.count() > 0) {
                try {
                    if (link.first().isVisible()) link.first().click();
                } catch (RuntimeException ignored) {}
            }
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
        if (href.toLowerCase().startsWith("mailto:")) return href;
        Object supportEmail = page.evaluate(
                "() => (document.querySelector('#np-ap-pl-supportEmail') || {}).value || ''");
        String email = supportEmail == null ? "" : String.valueOf(supportEmail).trim();
        if (!email.isBlank() && email.contains("@")) return "mailto:" + email;
        return href;
    }

    private boolean waitVisible(Locator loc, double timeoutMs) {
        long deadline = System.currentTimeMillis() + (long) timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            try {
                if (loc.count() > 0 && loc.first().isVisible()) return true;
            } catch (RuntimeException ignored) {}
            page.waitForTimeout(1_000);
        }
        return false;
    }

    private void closeIfPresent(String selector) {
        Locator close = page.locator(selector);
        if (close.count() > 0) {
            try { close.first().click(); } catch (RuntimeException ignored) {}
            page.waitForTimeout(400);
        }
    }

    public boolean credentialsFabVisible() {
        Locator b = page.locator("#openAuthBtn");
        return b.count() > 0 && b.first().isVisible();
    }

    /**
     * After {@code #np-ap-mod-set-password-btn} opens the set-credentials dialog, fill the real
     * password input(s). {@code #np-ap-mod-setCredentials-input} is the modal body div, not an input.
     */
    public boolean fillSetCredentialsPassword(String newPassword) {
        Locator fields = page.locator(
                "#setCredentials input:not([type='hidden']):not([type='checkbox']):not([type='radio']), "
                + "#np-ap-mod-setCredentials-input input:not([type='hidden']):not([type='checkbox']):not([type='radio']), "
                + "[data-region='modal-container'] input[type='password']");
        try {
            fields.first().waitFor(new Locator.WaitForOptions()
                    .setState(com.microsoft.playwright.options.WaitForSelectorState.VISIBLE)
                    .setTimeout(20_000));
        } catch (RuntimeException e) {
            page.waitForTimeout(2_000);
        }
        boolean filled = false;
        for (int i = 0; i < fields.count(); i++) {
            try {
                Locator f = fields.nth(i);
                if (!f.isVisible()) continue;
                String type = String.valueOf(f.getAttribute("type")).toLowerCase();
                String id = String.valueOf(f.getAttribute("id")).toLowerCase();
                String name = String.valueOf(f.getAttribute("name")).toLowerCase();
                if (type.contains("password") || id.contains("pass") || name.contains("pass")
                        || fields.count() <= 2) {
                    f.fill(newPassword);
                    filled = true;
                }
            } catch (RuntimeException ignored) {}
        }
        if (!filled && fields.count() > 0) {
            try {
                fields.last().fill(newPassword);
                filled = true;
            } catch (RuntimeException ignored) {}
        }
        if (!filled) return false;
        Locator save = page.locator("#setCredentials, [data-region='modal-container'] [data-action='save'], "
                + ".modal.show [data-action='save'], button:has-text('Save'), input[value='Save']");
        for (int i = 0; i < save.count(); i++) {
            try {
                if (save.nth(i).isVisible()) {
                    save.nth(i).click();
                    break;
                }
            } catch (RuntimeException ignored) {}
        }
        page.waitForTimeout(2_500);
        return true;
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
}
