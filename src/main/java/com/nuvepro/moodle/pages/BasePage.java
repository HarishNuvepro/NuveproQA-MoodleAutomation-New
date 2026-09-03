package com.nuvepro.moodle.pages;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.LoadState;
import com.nuvepro.moodle.config.Settings;

/**
 * Shared Page Object helpers. Locator discipline lives here:
 * prefer stable author id/class -> role -> label -> text.
 * Mirrors pages/base_page.py.
 */
public abstract class BasePage {
    protected final Page page;

    protected BasePage(Page page) {
        this.page = page;
        // PicoContainer constructs every step class that has an untagged @After, including
        // after sandbox student flows have already closed the admin page (ctx.page == null).
        if (page != null) {
            page.setDefaultTimeout(Settings.DEFAULT_TIMEOUT_MS);
        }
    }

    public Page page() {
        return page;
    }

    protected void navigate(String path) {
        // Use DOMCONTENTLOADED (not default LOAD): cloudlabs activity pages run lab-status/countdown
        // web-workers that poll forever, so the page never reaches 'load'/networkidle.
        // Retry on transient network blips (ERR_CONNECTION_TIMED_OUT / reset) — the site occasionally
        // drops a single request under load.
        com.microsoft.playwright.Page.NavigateOptions opts = new com.microsoft.playwright.Page.NavigateOptions()
                .setTimeout(45_000)
                .setWaitUntil(com.microsoft.playwright.options.WaitUntilState.DOMCONTENTLOADED);
        RuntimeException last = null;
        for (int attempt = 1; attempt <= 3; attempt++) {
            try {
                page.navigate(Settings.BASE_URL + path, opts);
                return;
            } catch (RuntimeException e) {
                last = e;
                page.waitForTimeout(3_000);   // brief backoff, then retry
            }
        }
        throw last;
    }

    /**
     * Click {@code #id_submitbutton} without waiting on the in-flight navigation, then poll until
     * the URL no longer contains {@code urlFragmentToLeave}, a Moodle field error appears, or a
     * same-URL reload finished. Moodle POSTs (create user, save profile) often exceed the 15s
     * default click-navigation wait; querying the DOM mid-navigation throws
     * "Execution context was destroyed".
     */
    protected void clickSubmitLeaving(String urlFragmentToLeave) {
        String before = page.url();
        page.locator("#id_submitbutton").click(new Locator.ClickOptions().setNoWaitAfter(true));
        long deadline = System.currentTimeMillis() + 60_000;
        boolean navigationStarted = false;
        while (System.currentTimeMillis() < deadline) {
            try {
                page.waitForLoadState(LoadState.DOMCONTENTLOADED,
                        new Page.WaitForLoadStateOptions().setTimeout(2_000));
                String url = page.url();
                if (!url.equals(before)) navigationStarted = true;
                if (!url.contains(urlFragmentToLeave)) return;
                // Moodle always renders empty #id_error_* feedback divs — only stop early on
                // visible, non-blank validation text (otherwise we abort mid-save).
                if (hasVisibleFormValidationError()) return;
                // Some saves stay on the same path but show a success banner / toast.
                if (safeCount(".alert-success, .notifysuccess, .toast-success, [data-region='toast'] .close") > 0
                        || pageHasVisibleSuccessNotice()) {
                    page.waitForTimeout(500);
                    return;
                }
                if (navigationStarted) {
                    page.waitForTimeout(400);
                    try {
                        page.waitForLoadState(LoadState.DOMCONTENTLOADED,
                                new Page.WaitForLoadStateOptions().setTimeout(10_000));
                    } catch (RuntimeException ignored) { }
                    return;
                }
            } catch (RuntimeException e) {
                String msg = String.valueOf(e.getMessage());
                if (msg.contains("Execution context was destroyed") || msg.contains("navigation")) {
                    navigationStarted = true;
                }
            }
            page.waitForTimeout(400);
        }
    }

    /** True when a Moodle mform shows a visible, non-empty field/form validation message. */
    protected boolean hasVisibleFormValidationError() {
        try {
            Object res = page.evaluate("() => {"
                    + "  const nodes = [...document.querySelectorAll("
                    + "    '[id^=\"id_error_\"], .form-control-feedback, .invalid-feedback, .error, .is-invalid');"
                    + "  return nodes.some(el => {"
                    + "    if (!el || el.getClientRects().length === 0) return false;"
                    + "    const t = (el.innerText || el.textContent || '').trim();"
                    + "    return t.length > 0;"
                    + "  });"
                    + "}");
            return Boolean.TRUE.equals(res);
        } catch (RuntimeException e) {
            return false;
        }
    }

    protected boolean pageHasVisibleSuccessNotice() {
        try {
            Object res = page.evaluate("() => {"
                    + "  const nodes = [...document.querySelectorAll("
                    + "    '.alert-success, .notifysuccess, .toast-success, [role=\"status\"], [role=\"alert\"]');"
                    + "  return nodes.some(el => {"
                    + "    if (!el || el.getClientRects().length === 0) return false;"
                    + "    const t = (el.innerText || '').toLowerCase();"
                    + "    return t.includes('chang') || t.includes('saved') || t.includes('success') || t.includes('updat');"
                    + "  });"
                    + "}");
            return Boolean.TRUE.equals(res);
        } catch (RuntimeException e) {
            return false;
        }
    }

    protected int safeCount(String selector) {
        try {
            return page.locator(selector).count();
        } catch (RuntimeException e) {
            return 0;
        }
    }

    protected Locator byCss(String selector) {
        return page.locator(selector);
    }

    protected String hiddenInputValue(String cssId) {
        Locator l = page.locator(cssId);
        if (l.count() == 0) return "";
        // Moodle/plugin pages can render duplicate hidden inputs with the same id.
        return l.first().inputValue();
    }

    /**
     * Click the primary/confirm button of Moodle's *visible* confirm modal, regardless of
     * its label (Start="Yes", Stop="Stop", ...). Targets the live dialog by visibility because
     * CSS data-action selectors also match hidden modal duplicates in Moodle 4.5.
     */
    protected void confirmModal() {
        if (tryConfirmModal(50)) return;
        throw new AssertionError("confirmModal: no visible confirm dialog appeared in time");
    }

    /**
     * Best-effort confirm: returns true if a visible modal was confirmed.
     * Returns false if no modal appeared within {@code maxAttempts} × 500ms.
     */
    protected boolean tryConfirmModal(int maxAttempts) {
        for (int i = 0; i < maxAttempts; i++) {
            Object res = page.evaluate(CONFIRM_JS);
            if ("ok".equals(res)) return true;
            page.waitForTimeout(500);
        }
        return false;
    }

    /** Click the Cancel/secondary button of the visible confirm modal. */
    protected void cancelModal() {
        for (int i = 0; i < 20; i++) {
            Object res = page.evaluate(CANCEL_JS);
            if ("ok".equals(res)) return;
            page.waitForTimeout(300);
        }
        throw new AssertionError("cancelModal: no visible confirm dialog appeared");
    }

    private static final String CANCEL_JS =
        "() => {" +
        "  const cont = [...document.querySelectorAll('[data-region=\"modal-container\"]')]" +
        "     .find(c => c.getClientRects().length > 0 && c.getAttribute('aria-hidden') !== 'true');" +
        "  if (!cont) return 'no-modal';" +
        "  const btn = cont.querySelector('[data-action=\"cancel\"]') || cont.querySelector('.btn-secondary');" +
        "  if (!btn) return 'no-btn';" +
        "  btn.click();" +
        "  return 'ok';" +
        "}";

    private static final String CONFIRM_JS =
        "() => {" +
        "  const visible = (el) => el && el.getClientRects().length > 0"
        + " && el.getAttribute('aria-hidden') !== 'true';" +
        "  const conts = ["
        + "...document.querySelectorAll('[data-region=\"modal-container\"]'),"
        + "...document.querySelectorAll('.modal.show'),"
        + "...document.querySelectorAll('.moodle-dialogue-confirm')"
        + "].filter(visible);" +
        "  if (!conts.length) return 'no-modal';" +
        "  const cont = conts[0];" +
        "  const btn = cont.querySelector('[data-action=\"save\"]')"
        + " || [...cont.querySelectorAll('button, input[type=button], input[type=submit], a.btn')]"
        + ".find(b => /\\b(yes|start|confirm|ok|proceed)\\b/i.test((b.innerText||b.value||'').trim()))"
        + " || cont.querySelector('.btn-primary');" +
        "  if (!btn || !visible(btn)) return 'no-btn';" +
        "  btn.click();" +
        "  return 'ok';" +
        "}";
}
