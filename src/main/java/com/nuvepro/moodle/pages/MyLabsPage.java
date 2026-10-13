package com.nuvepro.moodle.pages;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;

import java.util.regex.Pattern;

/**
 * Student/admin My Labs listing ({@code /theme/nuvetheme/mylabs.php}).
 */
public class MyLabsPage extends BasePage {
    public static final String PATH = "/theme/nuvetheme/mylabs.php";
    public static final String CARD = ".course-card";

    private static final String[] LAUNCH_LABELS = {
            "Launch", "Start", "Continue", "Access Lab", "Access", "Open Lab", "Start Lab", "Start Sandbox"
    };
    private static final String[] STOP_LABELS = {"Stop", "Stop Lab"};
    private static final String[] DETAILS_LABELS = {"View Details", "Details", "Lab Details", "View lab"};

    public MyLabsPage(Page page) {
        super(page);
    }

    public void open() {
        navigate(PATH);
        waitForCardsReady();
    }

    /** Cards/actions often load after an AJAX pass on mylabs.php. */
    public void waitForCardsReady() {
        long deadline = System.currentTimeMillis() + 45_000;
        while (System.currentTimeMillis() < deadline) {
            if (cardCount() == 0) {
                page.waitForTimeout(1_000);
                continue;
            }
            if (cardHasLabSurface(preferredCard()) || page.locator(
                    "#region-main a[href*='mod/cloudlabs/view.php']").count() > 0) {
                return;
            }
            page.waitForTimeout(1_500);
            page.reload();
        }
    }

    public Locator cardForActivityCmid(int cmid) {
        String needle = "id=" + cmid;
        Locator cards = page.locator(CARD);
        for (int i = 0; i < cards.count(); i++) {
            Locator card = cards.nth(i);
            if (card.locator("a[href*='" + needle + "']").count() > 0) {
                return card;
            }
        }
        return preferredCard();
    }

    public int cardCount() {
        return page.locator(CARD).count();
    }

    public Locator firstCard() {
        return preferredCard();
    }

    /** Prefer a card that looks like a cloud lab (links/actions), not a bare course tile. */
    public Locator preferredCard() {
        Locator cards = page.locator(CARD);
        int n = cards.count();
        for (int i = 0; i < n; i++) {
            Locator card = cards.nth(i);
            if (cardHasLabSurface(card)) {
                return card;
            }
        }
        return cards.first();
    }

    private static boolean cardHasLabSurface(Locator card) {
        if (card.locator("a[href*='mod/cloudlabs'], a[href*='cloudlabs/view.php']").count() > 0) {
            return true;
        }
        for (String label : LAUNCH_LABELS) {
            if (cardContainsActionLabel(card, label)) {
                return true;
            }
        }
        for (String label : STOP_LABELS) {
            if (cardContainsActionLabel(card, label)) {
                return true;
            }
        }
        for (String label : DETAILS_LABELS) {
            if (cardContainsActionLabel(card, label)) {
                return true;
            }
        }
        String text = card.innerText().toLowerCase();
        return text.contains("sandbox") || text.contains("playground") || text.contains("cloud lab");
    }

    private static boolean cardContainsActionLabel(Locator card, String label) {
        Pattern p = Pattern.compile(Pattern.quote(label), Pattern.CASE_INSENSITIVE);
        Locator links = card.locator("a, button").filter(new Locator.FilterOptions().setHasText(p));
        return links.count() > 0;
    }

    public boolean clickActionOnAnyCard(String[] labels) {
        Locator cards = page.locator(CARD);
        for (int i = 0; i < cards.count(); i++) {
            if (clickActionOnCard(cards.nth(i), labels)) {
                return true;
            }
        }
        return false;
    }

    public boolean clickActionOnCard(Locator card, String[] labels) {
        for (String label : labels) {
            if (clickByLabelOnCard(card, label)) {
                return true;
            }
        }
        return false;
    }

    private boolean clickByLabelOnCard(Locator card, String label) {
        Pattern partial = Pattern.compile(label, Pattern.CASE_INSENSITIVE);
        Locator link = card.getByRole(AriaRole.LINK, new Locator.GetByRoleOptions().setName(partial));
        if (link.count() > 0 && link.first().isVisible()) {
            link.first().click();
            page.waitForTimeout(1_500);
            return true;
        }
        Locator btn = card.getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions().setName(partial));
        if (btn.count() > 0 && btn.first().isVisible()) {
            btn.first().click();
            page.waitForTimeout(1_500);
            return true;
        }
        Locator text = card.locator("a, button").filter(new Locator.FilterOptions().setHasText(partial));
        if (text.count() > 0 && text.first().isVisible()) {
            text.first().click();
            page.waitForTimeout(1_500);
            return true;
        }
        Locator candidates = card.locator("a, button");
        for (int k = 0; k < candidates.count(); k++) {
            Locator el = candidates.nth(k);
            String title = el.getAttribute("title");
            String aria = el.getAttribute("aria-label");
            if (labelMatchesAttr(label, title) || labelMatchesAttr(label, aria)) {
                if (el.isVisible()) {
                    el.click();
                    page.waitForTimeout(1_500);
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean labelMatchesAttr(String label, String attr) {
        if (attr == null || attr.isBlank()) {
            return false;
        }
        return attr.toLowerCase().contains(label.toLowerCase());
    }

    /** Opens mod/cloudlabs/view.php from any card (details or launch when buttons are absent). */
    public boolean clickCloudlabsViewLinkOnAnyCard() {
        Locator cards = page.locator(CARD);
        for (int i = 0; i < cards.count(); i++) {
            Locator links = cards.nth(i).locator(
                    "a[href*='mod/cloudlabs/view.php'], a[href*='cloudlabs/view.php']");
            for (int j = 0; j < links.count(); j++) {
                Locator link = links.nth(j);
                if (link.isVisible()) {
                    link.click();
                    page.waitForTimeout(1_500);
                    return true;
                }
            }
        }
        Locator global = page.locator(
                "#region-main a[href*='mod/cloudlabs/view.php'], #region-main a[href*='cloudlabs/view.php']");
        if (global.count() > 0 && global.first().isVisible()) {
            global.first().click();
            page.waitForTimeout(1_500);
            return true;
        }
        return false;
    }

    public void clickLaunchOnCardForCmid(int cmid) {
        clickLaunchOnCard(cardForActivityCmid(cmid));
    }

    public void clickStopOnCardForCmid(int cmid) {
        clickStopOnCard(cardForActivityCmid(cmid));
    }

    public void clickViewDetailsOnCardForCmid(int cmid) {
        clickViewDetailsOnCard(cardForActivityCmid(cmid));
    }

    public void clickLaunchOnFirstCard() {
        clickLaunchOnCard(preferredCard());
    }

    public void clickLaunchOnCard(Locator card) {
        if (clickActionOnCard(card, LAUNCH_LABELS) || clickJsActionInCard(card, LAUNCH_LABELS)) {
            return;
        }
        if (clickCloudlabsLinkInCard(card) || clickCloudlabsViewLinkOnAnyCard()) {
            return;
        }
        throw new AssertionError("no Launch/Start/Continue on My Labs card (sample="
                + snippetCardText(card) + ")");
    }

    public void clickStopOnFirstCard() {
        clickStopOnCard(preferredCard());
    }

    public void clickStopOnCard(Locator card) {
        if (!clickActionOnCard(card, STOP_LABELS) && !clickJsActionInCard(card, STOP_LABELS)) {
            throw new AssertionError("no Stop on My Labs card (sample=" + snippetCardText(card) + ")");
        }
        confirmModal();
        page.waitForTimeout(1_000);
    }

    public void clickViewDetailsOnFirstCard() {
        clickViewDetailsOnCard(preferredCard());
    }

    public void clickViewDetailsOnCard(Locator card) {
        if (clickActionOnCard(card, DETAILS_LABELS) || clickJsActionInCard(card, DETAILS_LABELS)) {
            return;
        }
        Locator title = card.locator("a[href*='cloudlabs'], a[href*='view.php']").first();
        if (title.count() > 0 && title.isVisible()) {
            title.click();
            page.waitForTimeout(1_500);
            return;
        }
        if (clickCloudlabsLinkInCard(card) || clickCloudlabsViewLinkOnAnyCard()) {
            return;
        }
        throw new AssertionError("no View Details on My Labs card (sample=" + snippetCardText(card) + ")");
    }

    private boolean clickCloudlabsLinkInCard(Locator card) {
        Locator links = card.locator("a[href*='mod/cloudlabs/view.php'], a[href*='cloudlabs/view.php']");
        for (int j = 0; j < links.count(); j++) {
            Locator link = links.nth(j);
            if (link.isVisible()) {
                link.click();
                page.waitForTimeout(1_500);
                return true;
            }
        }
        return false;
    }

    /** Theme sometimes renders actions as clickable div/span (not a/button). */
    private boolean clickJsActionInCard(Locator card, String[] labels) {
        Object clicked = card.evaluate("""
                (root, labels) => {
                  const norm = s => (s || '').toLowerCase().replace(/\\s+/g, ' ').trim();
                  const targets = labels.map(l => norm(l));
                  const nodes = root.querySelectorAll('a, button, [role="button"], .btn, [onclick], [data-action]');
                  for (const el of nodes) {
                    const text = norm(el.innerText || el.textContent);
                    const title = norm(el.getAttribute('title'));
                    const aria = norm(el.getAttribute('aria-label'));
                    for (const t of targets) {
                      if (text.includes(t) || title.includes(t) || aria.includes(t)) {
                        el.click();
                        return true;
                      }
                    }
                  }
                  return false;
                }
                """, labels);
        if (Boolean.TRUE.equals(clicked)) {
            page.waitForTimeout(1_500);
            return true;
        }
        return false;
    }

    private static String snippetCardText(Locator card) {
        try {
            String t = card.innerText().replaceAll("\\s+", " ").trim();
            return t.substring(0, Math.min(200, t.length()));
        } catch (RuntimeException e) {
            return "(unreadable)";
        }
    }
}
