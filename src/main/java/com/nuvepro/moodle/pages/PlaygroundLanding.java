package com.nuvepro.moodle.pages;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.assertions.PlaywrightAssertions;
import com.microsoft.playwright.options.LoadState;

/**
 * Playground (sandbox) LANDING page — first screen of .../mod/cloudlabs/view.php?id=&lt;cmid&gt;.
 * Selectors from Activity\cloudlabs\templates\playground-landing.mustache.
 * Mirrors pages/playground_landing.py.
 */
public class PlaygroundLanding extends BasePage {
    public static final String CONTAINER = ".playground-landing-panel";
    public static final String START_SANDBOX = "#np-ap-pl-playground-start";
    public static final String CONTINUE_LAB = ".np-ap-pl-continue";
    public static final String LAB_STATUS = "#lab-status-pl";
    public static final String MANAGE_LABS = "a[href*='managelabs.php']";
    public static final String H_SECTION_ID = "#np-ap-pl-sectionId";
    public static final String H_LAB_ID = "#np-ap-pl-labid";
    public static final String H_COURSE_ID = "#np-ap-pl-course-id";

    public PlaygroundLanding(Page page) {
        super(page);
    }

    public void open(int cmid) {
        // The test instance intermittently stalls on view.php; a fresh load recovers it.
        Throwable last = null;
        for (int attempt = 1; attempt <= 3; attempt++) {
            try {
                navigate("/mod/cloudlabs/view.php?id=" + cmid);
                page.waitForTimeout(800);
                if (landingReady()) return;
                page.locator(CONTAINER + ", " + START_SANDBOX + ", " + CONTINUE_LAB + ", " + DETAILS)
                        .first()
                        .waitFor(new Locator.WaitForOptions()
                                .setState(com.microsoft.playwright.options.WaitForSelectorState.VISIBLE)
                                .setTimeout(20_000));
                if (landingReady()) return;
            } catch (Throwable e) {
                last = e;
                System.out.println("[SB] landing open attempt " + attempt + " cmid=" + cmid
                        + " url=" + page.url() + " " + e.getMessage());
            }
        }
        if (last instanceof RuntimeException re) throw re;
        throw new RuntimeException("sandbox landing did not load for cmid=" + cmid + " url=" + page.url(), last);
    }

    public boolean isLoaded() {
        return landingReady();
    }

    private boolean landingReady() {
        if (page.locator(CONTAINER).count() > 0) return true;
        if (controlPanelVisible()) return true;
        try {
            if (page.locator(START_SANDBOX).count() > 0 && page.locator(START_SANDBOX).first().isVisible()) {
                return true;
            }
        } catch (RuntimeException ignored) {}
        try {
            if (page.locator(CONTINUE_LAB).count() > 0 && page.locator(CONTINUE_LAB).first().isVisible()) {
                return true;
            }
        } catch (RuntimeException ignored) {}
        return page.locator(DETAILS).count() > 0;
    }

    public boolean hasActiveLab() {
        return page.locator(CONTINUE_LAB).count() > 0;
    }

    public String labStatusText() {
        return page.locator(LAB_STATUS).innerText().trim();
    }

    /** Click 'Start Sandbox' and confirm when a dialog appears. WARNING: provisions a real cloud lab. */
    public void startSandbox(boolean confirm) {
        Locator startBtn = page.locator(START_SANDBOX).first();
        startBtn.waitFor(new Locator.WaitForOptions()
                .setState(com.microsoft.playwright.options.WaitForSelectorState.VISIBLE)
                .setTimeout(15_000));
        startBtn.click();
        if (!confirm) return;

        long deadline = System.currentTimeMillis() + 45_000;
        while (System.currentTimeMillis() < deadline) {
            if (tryConfirmModal(1)) {
                page.waitForTimeout(1_000);
                if (controlPanelVisible()) return;
                // confirm clicked — keep waiting briefly for console
                long after = System.currentTimeMillis() + 20_000;
                while (System.currentTimeMillis() < after) {
                    if (controlPanelVisible()) return;
                    if (tryConfirmModal(1)) page.waitForTimeout(800);
                    page.waitForTimeout(500);
                }
                if (controlPanelVisible()) return;
            }
            if (controlPanelVisible()) {
                System.out.println("[SB] Start Sandbox: console present (modal optional)");
                return;
            }
            // Fallback: Playwright Yes/Start on any visible dialog
            if (clickVisibleConfirmFallback()) {
                page.waitForTimeout(1_200);
                continue;
            }
            page.waitForTimeout(400);
        }
        if (controlPanelVisible()) return;
        throw new AssertionError("Start Sandbox: neither confirm dialog nor lab console appeared (url="
                + page.url() + ")");
    }

    /** Confirm Start Sandbox, then observe the transient four-step Create tracker before it disappears. */
    public boolean startSandboxAndObserveFourStepProgress() {
        Locator startBtn = page.locator(START_SANDBOX).first();
        startBtn.waitFor(new Locator.WaitForOptions()
                .setState(com.microsoft.playwright.options.WaitForSelectorState.VISIBLE)
                .setTimeout(15_000));
        page.evaluate("""
                () => {
                  window.__sbFourStep = false;
                  const mark = () => {
                    const loader = document.querySelector('#np-ap-mod-lab-loader');
                    const steps = document.querySelectorAll(
                        '#np-ap-mod-lab-loader #step1, #np-ap-mod-lab-loader #step2, '
                        + '#np-ap-mod-lab-loader #step3, #np-ap-mod-lab-loader #step4');
                    if (loader || steps.length >= 4) window.__sbFourStep = true;
                  };
                  mark();
                  const obs = new MutationObserver(mark);
                  obs.observe(document.body, {subtree: true, childList: true, attributes: true});
                }
                """);
        startBtn.click();
        long deadline = System.currentTimeMillis() + 45_000;
        while (System.currentTimeMillis() < deadline) {
            if (tryConfirmModal(1) || clickVisibleConfirmFallback()) {
                boolean seen = new PlaygroundControlPanel(page).waitForFourStepProgress(20);
                return seen || fourStepHooked();
            }
            if (fourStepHooked()) return true;
            if (controlPanelVisible() && fourStepHooked()) {
                return true;
            }
            page.waitForTimeout(200);
        }
        return fourStepHooked();
    }

    private boolean fourStepHooked() {
        return Boolean.TRUE.equals(page.evaluate("() => window.__sbFourStep === true"));
    }

    private boolean clickVisibleConfirmFallback() {
        String[] sels = {
                "[data-region='modal-container']:not([aria-hidden='true']) [data-action='save']",
                ".modal.show [data-action='save']",
                ".modal.show .btn-primary",
                "button:has-text('Yes')",
                "button:has-text('Start')",
                "input[value='Yes']"
        };
        for (String sel : sels) {
            Locator b = page.locator(sel);
            for (int i = 0; i < Math.min(b.count(), 3); i++) {
                try {
                    if (b.nth(i).isVisible()) {
                        b.nth(i).click();
                        return true;
                    }
                } catch (RuntimeException ignored) {}
            }
        }
        return false;
    }

    private boolean controlPanelVisible() {
        String[] sels = {
                PlaygroundControlPanel.MASTER,
                PlaygroundControlPanel.STOP_LAB,
                PlaygroundControlPanel.LAUNCH_START,
                "#np-ap-mod-lab-ui-schema",
                "#subCatalogDataSubmitBtn",
                "#np-ap-mod-lab-container-home",
                "#np-ap-mod-lab-loader"
        };
        for (String sel : sels) {
            Locator cp = page.locator(sel);
            for (int i = 0; i < cp.count(); i++) {
                try {
                    if (cp.nth(i).isVisible()) return true;
                } catch (RuntimeException ignored) {}
            }
        }
        return false;
    }

    public void continueLab() {
        Locator cont = page.locator(CONTINUE_LAB);
        try {
            cont.first().click(new Locator.ClickOptions().setForce(true));
        } catch (RuntimeException e) {
            page.evaluate("() => document.querySelector('.np-ap-pl-continue')?.click()");
        }
        page.waitForTimeout(1_000);
        tryConfirmModal(2);
        clickVisibleConfirmFallback();
    }

    /** Reach the control panel regardless of lab state (Start Sandbox or Continue). */
    public void openLabConsole() {
        if (controlPanelVisible()) {
            System.out.println("[SB] lab console already open");
            return;
        }
        Locator start = page.locator(START_SANDBOX);
        Locator cont = page.locator(CONTINUE_LAB);
        boolean hasStart = false;
        boolean hasContinue = false;
        try {
            hasStart = start.count() > 0 && start.first().isVisible();
        } catch (RuntimeException ignored) {}
        try {
            hasContinue = cont.count() > 0 && cont.first().isVisible();
        } catch (RuntimeException ignored) {}

        if (hasStart) {
            startSandbox(true);
        } else if (hasContinue) {
            continueLab();
            long deadline = System.currentTimeMillis() + 120_000;
            while (System.currentTimeMillis() < deadline && !controlPanelVisible()) {
                tryConfirmModal(1);
                clickVisibleConfirmFallback();
                page.waitForTimeout(1_000);
            }
            if (!controlPanelVisible()) {
                // Continue is AJAX (loadPlaygroundLabcpContent). One retry click.
                continueLab();
                long retry = System.currentTimeMillis() + 30_000;
                while (System.currentTimeMillis() < retry && !controlPanelVisible()) {
                    page.waitForTimeout(1_000);
                }
            }
            if (!controlPanelVisible()) {
                throw new AssertionError("Continue did not open the lab console (url=" + page.url() + ")");
            }
        } else {
            throw new AssertionError("Neither 'Start Sandbox' nor 'Continue' control is present");
        }
    }

    public void openManageLabs() {
        page.locator(MANAGE_LABS).click();
    }

    public String hiddenSectionId() {
        return hiddenInputValue(H_SECTION_ID);
    }

    public String hiddenLabId() {
        return hiddenInputValue(H_LAB_ID);
    }

    public String hiddenCourseId() {
        return hiddenInputValue(H_COURSE_ID);
    }

    public void expectLoaded() {
        PlaywrightAssertions.assertThat(page.locator(CONTAINER)).isVisible();
    }

    public static final String DETAILS = "#np-ap-cl-challenge-details";

    public boolean hasStartSandbox() {
        Locator s = page.locator(START_SANDBOX);
        return s.count() > 0 && s.first().isVisible();
    }

    /** Click Start Sandbox and then Cancel the confirmation (no lab is created). */
    public void startSandboxThenCancel() {
        page.locator(START_SANDBOX).click();
        cancelModal();
    }
}
