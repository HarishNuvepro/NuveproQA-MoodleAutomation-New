package com.nuvepro.moodle.pages;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Response;
import com.microsoft.playwright.options.LoadState;
import com.microsoft.playwright.options.WaitForSelectorState;
import com.nuvepro.moodle.config.Settings;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.regex.Pattern;

/**
 * Nuvepro theme All Courses / All Labs catalog.
 *
 * The page has changed names between releases, so discovery starts from the authenticated
 * navigation and selectors prefer stable input/link semantics over one theme-specific CSS class.
 */
public final class AllCoursesPage extends BasePage {
    /** Live theme_nuvetheme/allcourses_pagination contract (not the stale Kiwi example of 16). */
    public static final int BATCH_SIZE = 24;
    private static final String COURSE_CARD =
            "[data-courseid], [data-course-id], .course-card, .coursebox, .course-card-container";
    private static final String SEARCH =
            "form[action*='course_search.php'] input[name='search'], "
                    + "input[aria-label='Search Course'], input[placeholder='Search Course']";
    private static final String CATEGORY =
            ".nuve-all-cat-btn, select[id*='categor' i], button:has-text('All Categories'), "
                    + "[role='combobox'][aria-label*='categor' i]";
    private static final String LOADER =
            ".spinner-border, .loading, [data-region*='loading'], [aria-label*='Loading' i]";

    private String catalogUrl;

    public AllCoursesPage(Page page) {
        super(page);
    }

    public void open() {
        if (catalogUrl == null) catalogUrl = discoverCatalogUrl();
        navigateToCatalog();
        waitForDocument();
        waitForFirstCards();
        waitForCatalogSettled();
    }

    /**
     * Late in a long suite the catalog can take well over a minute to respond, and waiting for
     * DOMCONTENTLOADED inside navigate() then aborts the whole scenario. Commit as soon as the
     * response starts, wait for the DOM separately, and retry a genuinely failed navigation.
     */
    private void navigateToCatalog() {
        RuntimeException last = null;
        for (int attempt = 1; attempt <= 3; attempt++) {
            try {
                page.navigate(catalogUrl, new Page.NavigateOptions()
                        .setTimeout(60_000)
                        .setWaitUntil(com.microsoft.playwright.options.WaitUntilState.COMMIT));
                return;
            } catch (RuntimeException e) {
                last = e;
                System.out.println("[AllCourses] catalog navigation attempt " + attempt
                        + " failed (" + catalogUrl + "); retrying");
                page.waitForTimeout(3_000);
            }
        }
        throw last;
    }

    /** Cards arrive from an async theme request; an empty catalog stays a valid outcome. */
    private void waitForFirstCards() {
        try {
            page.locator(COURSE_CARD).first().waitFor(new Locator.WaitForOptions()
                    .setState(WaitForSelectorState.VISIBLE).setTimeout(45_000));
        } catch (RuntimeException ignored) {}
    }

    public String url() {
        return catalogUrl == null ? page.url() : catalogUrl;
    }

    private String discoverCatalogUrl() {
        navigate("/my/");
        Locator links = page.locator("a[href]").filter(new Locator.FilterOptions()
                .setHasText(Pattern.compile(
                        "^\\s*(all\\s+(courses|labs)|course\\s+library)\\s*$",
                        Pattern.CASE_INSENSITIVE)));
        for (int i = 0; i < links.count(); i++) {
            String href = links.nth(i).getAttribute("href");
            if (href != null && !href.isBlank()) return absolute(href);
        }
        // Theme navigation sometimes renders the label in a nested span and adds a badge.
        Object found = page.evaluate("""
                () => {
                  const re = /^(all\\s+(courses|labs)|course\\s+library)$/i;
                  for (const a of document.querySelectorAll('a[href]')) {
                    if (re.test((a.innerText || '').trim())) return a.href;
                  }
                  return '';
                }
                """);
        if (found != null && !found.toString().isBlank()) return found.toString();
        throw new IllegalStateException("All Courses/All Labs navigation link not found on " + page.url());
    }

    private String absolute(String href) {
        try {
            return URI.create(Settings.BASE_URL + "/").resolve(href).toString();
        } catch (RuntimeException e) {
            return href;
        }
    }

    public boolean searchEnabled() {
        Locator input = catalogSearchInput();
        return input.count() > 0 && input.isVisible() && input.isEnabled();
    }

    /** Prefer the theme catalog box; Moodle's header course_search.php form is a different page. */
    private Locator catalogSearchInput() {
        Locator theme = page.locator(
                "input[aria-label='Search Course'], input[placeholder='Search Course']").first();
        if (theme.count() > 0) return theme;
        return page.locator(SEARCH).first();
    }

    private boolean onAllCoursesCatalog() {
        String url = page.url().toLowerCase(Locale.ROOT);
        return !url.contains("course_search.php")
                && (url.contains("allcourse") || url.contains("alllab")
                || url.contains("nuvetheme") || catalogUrl != null && page.url().startsWith(catalogUrl.split("\\?")[0]));
    }

    public boolean manageCoursesEnabled() {
        return manageCoursesLink().count() > 0 && manageCoursesLink().isVisible();
    }

    public boolean addCourseEnabled() {
        return addCourseLink().count() > 0 && addCourseLink().isVisible();
    }

    /** Live theme often comments out the More-actions chrome; prefer href selectors when present. */
    public Locator manageCoursesLink() {
        return page.locator("a[href*='course/management.php'], a:has-text('Manage courses')").first();
    }

    public Locator addCourseLink() {
        return page.locator(
                "a[href*='/course/edit.php']:has-text('Add'), a:has-text('Add a new course')").first();
    }

    public String clickControlAndReturnUrl(String text) {
        Locator c;
        if (text.toLowerCase(Locale.ROOT).contains("manage")) c = manageCoursesLink();
        else if (text.toLowerCase(Locale.ROOT).contains("add")) c = addCourseLink();
        else c = page.getByText(text, new Page.GetByTextOptions().setExact(false)).first();
        if (c.count() == 0) throw new AssertionError("control not found: " + text);
        c.click();
        waitForDocument();
        return page.url();
    }

    /** Admin destinations used when the theme omits on-page Manage/Add chrome. */
    public String openCourseManagementDirect() {
        navigate("/course/management.php");
        waitForDocument();
        return page.url();
    }

    public String openAddCourseDirect() {
        navigate("/course/edit.php?category=1");
        waitForDocument();
        return page.url();
    }

    public void search(String query) {
        closeDescriptionModal();
        Locator input = catalogSearchInput();
        input.waitFor(new Locator.WaitForOptions()
                .setState(WaitForSelectorState.VISIBLE).setTimeout(20_000));
        input.fill(query);
        input.dispatchEvent("input");
        input.dispatchEvent("change");
        input.press("Enter");
        page.waitForTimeout(500);
        waitForCatalogSettled();
    }

    /** Fresh catalog load, then search until that course tile is present. */
    public void showCourse(String courseName) {
        closeDescriptionModal();
        open();
        search(courseName);
        waitFor(() -> courseVisible(courseName), "course is missing from All Courses: " + courseName);
    }

    private void closeDescriptionModal() {
        Locator close = page.locator(
                "#courseDescModalBS button.close, #courseDescModalBS [data-dismiss='modal'], "
                        + "#courseDescModalBS .btn-close, #courseDescModalBS button:has-text('Close'), "
                        + ".modal.show button.close, .modal.show .btn-close, "
                        + ".modal.show [data-dismiss='modal'], .modal.show button:has-text('Close')").first();
        if (close.count() > 0 && close.isVisible()) {
            try { close.click(); } catch (RuntimeException ignored) {}
            page.waitForTimeout(300);
        } else if (page.locator("#courseDescModalBS.show, #courseDescModalBS, .modal.show").count() > 0
                && page.locator("#courseDescModalBS.show, .modal.show").first().isVisible()) {
            page.keyboard().press("Escape");
            page.waitForTimeout(300);
        }
    }

    public void clearSearch() {
        Locator input = catalogSearchInput();
        if (input.count() > 0 && input.isVisible()) {
            input.fill("");
            input.dispatchEvent("input");
            input.dispatchEvent("change");
            Locator clear = page.locator(
                    "[aria-label*='clear' i], button:has-text('Clear'), .search-clear, "
                            + ".fa-times, .fa-close, [class*='search'] [class*='clear']").first();
            if (clear.count() > 0 && clear.isVisible()) {
                try { clear.click(); } catch (RuntimeException ignored) {}
            }
            page.waitForTimeout(500);
            waitForCatalogSettled();
        }
        // Empty Enter / Moodle core course_search.php leaves a filtered or empty page.
        if (!onAllCoursesCatalog() || visibleCardCount() == 0 || emptyStateVisible()
                || visibleCardCount() < BATCH_SIZE) {
            open();
        }
    }

    public List<String> visibleCourseNames() {
        Object raw = page.locator(COURSE_CARD).evaluateAll("""
                els => els.filter(e => {
                    const s = getComputedStyle(e);
                    return s.display !== 'none' && s.visibility !== 'hidden' && e.getClientRects().length;
                  }).map(e => {
                    const id = e.getAttribute('data-course-id') || e.getAttribute('data-courseid') || '';
                    const n = (id && document.getElementById('course-title-' + id))
                      || e.querySelector('.course_tail_course_name, [id^="course-title-"], [data-region="course-name"], .course-name, .coursename, h3, h4, h5');
                    return ((n && (n.innerText || n.textContent)) || e.getAttribute('data-coursename') || '').trim();
                  }).filter(Boolean)
                """);
        List<String> out = new ArrayList<>();
        if (raw instanceof List<?> list) {
            for (Object o : list) if (o != null && !o.toString().isBlank()) out.add(o.toString().trim());
        }
        return out;
    }

    /** Stable card identity for uniqueness checks (names can collide / be blank on partial renders). */
    public List<String> visibleCourseIds() {
        Object raw = page.locator(COURSE_CARD).evaluateAll("""
                els => els.filter(e => {
                    const s = getComputedStyle(e);
                    return s.display !== 'none' && s.visibility !== 'hidden' && e.getClientRects().length;
                  }).map(e => {
                    const explicit = e.getAttribute('data-course-id') || e.getAttribute('data-courseid');
                    if (explicit) return explicit;
                    const a = e.querySelector("a[href*='course/view.php']");
                    const m = a ? (a.getAttribute('href') || '').match(/[?&]id=(\\d+)/) : null;
                    return m ? m[1] : '';
                  }).filter(Boolean)
                """);
        List<String> out = new ArrayList<>();
        if (raw instanceof List<?> list) {
            for (Object o : list) if (o != null && !o.toString().isBlank()) out.add(o.toString().trim());
        }
        return out;
    }

    public int visibleCardCount() {
        List<String> ids = visibleCourseIds();
        return ids.isEmpty() ? visibleCourseNames().size() : ids.size();
    }

    public boolean courseVisible(String name) {
        return visibleCourseNames().stream().anyMatch(n -> n.equalsIgnoreCase(name)
                || n.toLowerCase(Locale.ROOT).contains(name.toLowerCase(Locale.ROOT)));
    }

    public String cardText(String courseName) {
        Locator card = matchingCard(courseName);
        String visible = card.innerText();
        Object hidden = card.evaluate("""
                e => {
                  const id = e.getAttribute('data-course-id') || e.getAttribute('data-courseid') || '';
                  const desc = document.getElementById('course-desc-' + id)
                    || e.querySelector('[id^="course-desc"], .course-desc, .course_description');
                  return desc ? (desc.innerText || desc.textContent || '') : '';
                }
                """);
        return (visible == null ? "" : visible) + "\n" + (hidden == null ? "" : hidden);
    }

    /** True when the All Courses tile reports the given completion percent (e.g. 100% Completed). */
    public boolean cardShowsPercentComplete(String courseName, int percent) {
        String text = cardText(courseName).replaceAll("\\s+", " ");
        java.util.regex.Pattern p = java.util.regex.Pattern.compile(
                "\\b" + percent + "\\s*%\\s*completed\\b",
                java.util.regex.Pattern.CASE_INSENSITIVE);
        if (p.matcher(text).find()) return true;
        Object fromDom = matchingCard(courseName).evaluate("""
                (e, pct) => {
                  const t = (e.innerText || '').replace(/\\s+/g, ' ');
                  const re = new RegExp('\\\\b' + pct + '\\\\s*%\\\\s*completed\\\\b', 'i');
                  if (re.test(t)) return true;
                  const bar = e.querySelector('[aria-valuenow], .progress-bar, [role="progressbar"]');
                  if (bar) {
                    const now = bar.getAttribute('aria-valuenow') || bar.getAttribute('style') || '';
                    return String(now).includes(String(pct));
                  }
                  return false;
                }
                """, percent);
        return Boolean.TRUE.equals(fromDom);
    }

    /** First completion percent shown on the tile, or -1 if none. */
    public int cardProgressPercent(String courseName) {
        String text = cardText(courseName).replaceAll("\\s+", " ");
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("(\\d+)\\s*%")
                .matcher(text);
        if (m.find()) return Integer.parseInt(m.group(1));
        Object fromBar = matchingCard(courseName).evaluate("""
                e => {
                  const bar = e.querySelector('[aria-valuenow], .progress-bar, [role="progressbar"]');
                  if (!bar) return null;
                  const now = bar.getAttribute('aria-valuenow');
                  if (now && /\\d+/.test(now)) return parseInt(now, 10);
                  return null;
                }
                """);
        if (fromBar instanceof Number) return ((Number) fromBar).intValue();
        return -1;
    }

    /** Open Read More when present so long summaries are exercised (ACP-9/10). */
    public String openReadMoreDescription(String courseName) {
        Locator card = matchingCard(courseName);
        Locator more = card.locator(".view_desc_txt, a:has-text('Read More'), button:has-text('Read More')").first();
        if (more.count() > 0 && more.isVisible()) {
            more.click();
            page.waitForTimeout(400);
            Locator modal = page.locator("#courseDescModalBS, #courseDescContentBS, .modal.show .modal-body").first();
            if (modal.count() > 0) {
                String text = modal.innerText();
                closeDescriptionModal();
                return text;
            }
        }
        return cardText(courseName);
    }

    public boolean readMoreVisible(String courseName) {
        Locator more = matchingCard(courseName).locator(
                ".view_desc_txt, a:has-text('Read More'), button:has-text('Read More')").first();
        return more.count() > 0 && more.isVisible();
    }

    public void openCatalogAt(String url) {
        catalogUrl = url;
        navigateToCatalog();
        waitForDocument();
        waitForFirstCards();
        waitForCatalogSettled();
    }

    public void openCourseCard(String courseName) {
        Locator card = matchingCard(courseName);
        // Theme uses Bootstrap stretched-link (often visibility:hidden) — navigate by href.
        Object href = card.evaluate("""
                e => {
                  const a = e.querySelector("a[href*='/course/view.php']")
                    || e.querySelector('a.stretched-link[href]')
                    || e.querySelector('a[href]');
                  return a ? a.href : '';
                }
                """);
        if (href == null || href.toString().isBlank()) {
            throw new AssertionError("no course/view link in card: " + courseName);
        }
        page.navigate(href.toString(), new Page.NavigateOptions()
                .setTimeout(45_000)
                .setWaitUntil(com.microsoft.playwright.options.WaitUntilState.DOMCONTENTLOADED));
        waitForDocument();
    }

    public boolean unavailableToStudents(String pageText) {
        if (pageText == null) return false;
        String lower = pageText.toLowerCase(Locale.ROOT);
        return lower.contains("this course is currently unavailable to students")
                || lower.contains("currently unavailable to students");
    }

    private Locator matchingCard(String courseName) {
        String needle = courseName.toLowerCase(Locale.ROOT);
        Locator byText = page.locator(COURSE_CARD)
                .filter(new Locator.FilterOptions().setHasText(courseName));
        if (byText.count() > 0) return byText.first();
        Locator cards = page.locator(COURSE_CARD);
        for (int i = 0; i < cards.count(); i++) {
            Object raw = cards.nth(i).evaluate("""
                    (e, n) => {
                      const id = e.getAttribute('data-course-id') || e.getAttribute('data-courseid') || '';
                      const title = (id && document.getElementById('course-title-' + id))
                        || e.querySelector('.course_tail_course_name, [id^="course-title-"], [data-region="course-name"], .course-name, .coursename, h3, h4, h5');
                      const parts = [
                        e.innerText || '',
                        e.getAttribute('data-coursename') || '',
                        title ? (title.innerText || title.textContent || '') : '',
                        title ? (title.getAttribute('title') || '') : ''
                      ];
                      return parts.join(' ').toLowerCase().includes(n);
                    }
                    """, needle);
            if (Boolean.TRUE.equals(raw)) return cards.nth(i);
        }
        throw new AssertionError("course card not found: " + courseName
                + " (visible=" + visibleCourseNames() + ")");
    }

    /** The tile renders a course image (configured or theme default) with a real loaded bitmap. */
    public boolean cardShowsImage(String courseName) {
        Locator card = matchingCard(courseName);
        Object ok = card.evaluate("""
                e => {
                  const img = e.querySelector('img');
                  if (img) return (img.naturalWidth > 0 && img.naturalHeight > 0);
                  const bg = [...e.querySelectorAll('*')].some(n => {
                    const b = getComputedStyle(n).backgroundImage;
                    return b && b !== 'none' && b.includes('url(');
                  });
                  return bg;
                }
                """);
        return Boolean.TRUE.equals(ok);
    }

    public boolean categoryFilterVisible() {
        Locator c = page.locator(CATEGORY).first();
        return c.count() > 0 && c.isVisible();
    }

    public void selectCategory(String category) {
        selectCategory(category, null);
    }

    /**
     * Pick a category from the theme dropdown. Items carry {@code data-categoryid} but not a
     * {@code title} attribute, and long lists scroll, so match by id (when known) or name in the DOM
     * and click there instead of relying on an attribute/exact-text locator.
     */
    public void selectCategory(String category, String categoryId) {
        Locator select = page.locator("select[id*='categor' i]").first();
        if (select.count() > 0) {
            select.selectOption(new com.microsoft.playwright.options.SelectOption().setLabel(category));
        } else {
            openCategoryDropdown();
            Object clicked = page.evaluate("""
                    ([name, id]) => {
                      const items = [...document.querySelectorAll(
                        '.nuve-dropdown-menu a, .nuve-dropdown-content a, [role="menu"] a')];
                      const wanted = String(name || '').trim().toLowerCase();
                      const match = items.find(a => id && a.getAttribute('data-categoryid') === String(id))
                        || items.find(a => (a.innerText || a.textContent || '').trim().toLowerCase() === wanted)
                        || items.find(a => (a.innerText || a.textContent || '').toLowerCase().includes(wanted));
                      if (!match) {
                        return 'not-found: ' + items.map(a => (a.innerText || '').trim()).join(' | ');
                      }
                      match.scrollIntoView({block: 'center'});
                      match.click();
                      return 'ok';
                    }
                    """, java.util.Arrays.asList(category, categoryId));
            if (!"ok".equals(String.valueOf(clicked))) {
                throw new AssertionError("category not selectable: " + category + " (" + clicked + ")");
            }
        }
        page.waitForTimeout(700);
        waitForCatalogSettled();
    }

    private void openCategoryDropdown() {
        Locator trigger = page.locator(".nuve-all-cat-btn, #menu-btn").first();
        trigger.waitFor(new Locator.WaitForOptions()
                .setState(WaitForSelectorState.VISIBLE).setTimeout(20_000));
        trigger.click();
        page.waitForTimeout(400);
    }

    public List<String> categoryOptions() {
        Locator select = page.locator("select[id*='categor' i]").first();
        if (select.count() > 0) {
            Object raw = select.locator("option").allTextContents();
            List<String> out = new ArrayList<>();
            if (raw instanceof List<?> list) for (Object o : list) out.add(String.valueOf(o).trim());
            return out;
        }
        openCategoryDropdown();
        List<String> out = page.locator(
                ".nuve-dropdown-menu a, .nuve-dropdown-content a, [role='menu'] a").allTextContents();
        page.keyboard().press("Escape");
        List<String> trimmed = new ArrayList<>();
        for (String s : out) if (s != null && !s.isBlank()) trimmed.add(s.trim());
        return trimmed;
    }

    public boolean hasPaginationControls() {
        Locator pager = page.locator(".pagination, [data-region='paging-control-container'], "
                + "a:has-text('Next'), a:has-text('Previous')");
        return pager.count() > 0 && pager.first().isVisible();
    }

    public boolean loaderVisible() {
        Locator l = page.locator(LOADER);
        for (int i = 0; i < l.count(); i++) {
            try { if (l.nth(i).isVisible()) return true; } catch (RuntimeException ignored) {}
        }
        return false;
    }

    public void scrollToEnd() {
        int unchanged = 0;
        int before = visibleCardCount();
        for (int i = 0; i < 30 && unchanged < 3; i++) {
            page.evaluate("window.scrollTo(0, document.body.scrollHeight)");
            page.waitForTimeout(650);
            waitForCatalogSettled();
            int now = visibleCardCount();
            if (now == before) unchanged++; else unchanged = 0;
            before = now;
        }
    }

    public void rapidScroll() {
        for (int i = 0; i < 8; i++) {
            page.evaluate("window.scrollTo(0, document.body.scrollHeight)");
            page.waitForTimeout(80);
        }
        waitForCatalogSettled();
    }

    public boolean emptyStateVisible() {
        String body = page.locator("body").innerText().toLowerCase(Locale.ROOT);
        return visibleCardCount() == 0
                && (body.contains("no course") || body.contains("no data") || body.contains("nothing to display"));
    }

    /** End-of-list marker after infinite scroll: cards remain, and "No more courses" is shown. */
    public boolean noMoreCoursesVisible() {
        String body = page.locator("body").innerText().toLowerCase(Locale.ROOT);
        return body.contains("no more courses");
    }

    public boolean cardLayoutStable(String courseName) {
        Locator card = matchingCard(courseName);
        Object raw = card.evaluate("""
                e => {
                  const r = e.getBoundingClientRect();
                  const n = e.querySelector('.course_tail_course_name, [data-region="course-name"], .course-name, .coursename, h3, h4, h5');
                  if (!n) return false;
                  const nr = n.getBoundingClientRect();
                  const cs = getComputedStyle(n);
                  return r.width > 100 && r.height > 40 && nr.width <= r.width + 2
                    && cs.overflowX !== 'scroll' && document.documentElement.scrollWidth <= window.innerWidth + 2;
                }
                """);
        return Boolean.TRUE.equals(raw);
    }

    public boolean pageHasTechnicalError() {
        String body = page.locator("body").innerText().toLowerCase(Locale.ROOT);
        return body.contains("sql syntax") || body.contains("database error")
                || body.contains("exception -") || body.contains("error reading from database");
    }

    public Set<String> uniqueNames() {
        return new HashSet<>(visibleCourseNames());
    }

    public List<Response> observeCatalogResponses(Runnable action) {
        List<Response> responses = new ArrayList<>();
        java.util.function.Consumer<Response> listener = r -> {
            String u = r.url().toLowerCase(Locale.ROOT);
            if ((r.request().resourceType().equals("xhr") || r.request().resourceType().equals("fetch"))
                    && u.contains("/theme/nuvetheme/controllers/courseservices.php")) {
                responses.add(r);
            }
        };
        page.onResponse(listener);
        action.run();
        page.offResponse(listener);
        return responses;
    }

    public void waitFor(BooleanSupplier condition, String message) {
        long deadline = System.currentTimeMillis() + 20_000;
        while (System.currentTimeMillis() < deadline) {
            if (condition.getAsBoolean()) return;
            page.waitForTimeout(300);
        }
        throw new AssertionError(message);
    }

    private void waitForCatalogSettled() {
        long deadline = System.currentTimeMillis() + 15_000;
        int stable = 0;
        int previous = -1;
        while (System.currentTimeMillis() < deadline) {
            int count;
            try { count = visibleCardCount(); } catch (RuntimeException e) {
                page.waitForTimeout(250);
                continue;
            }
            if (!loaderVisible() && count == previous) stable++; else stable = 0;
            if (stable >= 2) return;
            previous = count;
            page.waitForTimeout(300);
        }
    }

    private void waitForDocument() {
        try {
            page.waitForLoadState(LoadState.DOMCONTENTLOADED,
                    new Page.WaitForLoadStateOptions().setTimeout(60_000));
        } catch (RuntimeException ignored) {}
        page.waitForTimeout(500);
    }
}
