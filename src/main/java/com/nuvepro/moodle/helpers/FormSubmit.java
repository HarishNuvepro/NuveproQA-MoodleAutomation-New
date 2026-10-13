package com.nuvepro.moodle.helpers;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.LoadState;

/**
 * Submitting Moodle forms whose POST can outlast the 15s default action timeout (activity/course
 * saves, bulk uploads, enrolment edits).
 *
 * A plain {@code click()} fails on "waiting for scheduled navigations" while the server is still
 * saving, and a following {@code waitForLoadState()} returns at once because the OLD document is
 * already loaded — the caller then reads the pre-submit URL and reports "did not save". Tag the
 * current document, click without waiting, and treat the tag disappearing as "the next page
 * arrived".
 */
public final class FormSubmit {
    public static final int DEFAULT_TIMEOUT_MS = 120_000;

    private static final String MARK = "() => { window.__npSubmitPending = true; }";
    private static final String STILL_OLD = "() => window.__npSubmitPending === true";
    private static final String VALIDATION_ERROR = "() => [...document.querySelectorAll("
            + "'[id^=\"id_error_\"], .form-control-feedback, .invalid-feedback, .is-invalid')]"
            + ".some(el => el.getClientRects().length > 0"
            + " && (el.innerText || el.textContent || '').trim().length > 0)";

    private FormSubmit() {
    }

    /**
     * @return true once a new document has loaded; false when the form stayed put with a visible
     *         validation error or nothing navigated within {@code timeoutMs}
     */
    public static boolean andAwaitNewPage(Page page, Locator control, int timeoutMs) {
        try {
            page.evaluate(MARK);
        } catch (RuntimeException ignored) {
            // page mid-navigation already; the poll below still detects the new document
        }
        control.click(new Locator.ClickOptions().setNoWaitAfter(true));
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            page.waitForTimeout(300);
            try {
                if (!Boolean.TRUE.equals(page.evaluate(STILL_OLD))) {
                    long left = Math.max(1_000, deadline - System.currentTimeMillis());
                    page.waitForLoadState(LoadState.DOMCONTENTLOADED,
                            new Page.WaitForLoadStateOptions().setTimeout(left));
                    return true;
                }
                if (Boolean.TRUE.equals(page.evaluate(VALIDATION_ERROR))) return false;
            } catch (RuntimeException navigating) {
                // "Execution context was destroyed" — the POST is committing; keep polling
            }
        }
        return false;
    }

    public static boolean andAwaitNewPage(Page page, Locator control) {
        return andAwaitNewPage(page, control, DEFAULT_TIMEOUT_MS);
    }
}
