package com.nuvepro.moodle.helpers;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.WaitForSelectorState;
import com.microsoft.playwright.options.WaitUntilState;
import com.nuvepro.moodle.config.Settings;
import com.nuvepro.moodle.pages.PlaygroundLanding;

/**
 * Resolves the course id and section id for the configured {@link Settings#PLAYGROUND_CMID} activity.
 * Manage User Labs and WS enrolment must use this course, not {@link Settings#COURSE_ID} alone.
 */
public final class PlaygroundAnchor {
    public final int courseId;
    public final int sectionId;

    private PlaygroundAnchor(int courseId, int sectionId) {
        this.courseId = courseId;
        this.sectionId = sectionId;
    }

    public static PlaygroundAnchor resolve(Page page) {
        if (Settings.PLAYGROUND_CMID.isEmpty()) {
            throw new IllegalStateException("PLAYGROUND_CMID not set");
        }
        page.navigate(Settings.BASE_URL + "/mod/cloudlabs/view.php?id=" + Settings.PLAYGROUND_CMID,
                new Page.NavigateOptions().setWaitUntil(WaitUntilState.DOMCONTENTLOADED).setTimeout(45_000));
        Locator sec = page.locator("#np-ap-pl-sectionId, #np-ap-manage-sectionId, input[id*='sectionId']");
        sec.first().waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.ATTACHED).setTimeout(30_000));
        int sectionId = Integer.parseInt(sec.first().inputValue().trim());
        PlaygroundLanding landing = new PlaygroundLanding(page);
        String courseRaw = landing.hiddenCourseId();
        int courseId = parseCourseId(page, courseRaw);
        return new PlaygroundAnchor(courseId, sectionId);
    }

    private static int parseCourseId(Page page, String hiddenCourse) {
        if (hiddenCourse != null && !hiddenCourse.isBlank()) {
            return Integer.parseInt(hiddenCourse.trim());
        }
        Locator crumb = page.locator("a[href*='course/view.php?id=']");
        for (int i = 0; i < crumb.count(); i++) {
            String href = crumb.nth(i).getAttribute("href");
            if (href == null) continue;
            java.util.regex.Matcher m = java.util.regex.Pattern.compile("[?&]id=(\\d+)").matcher(href);
            if (m.find()) {
                return Integer.parseInt(m.group(1));
            }
        }
        String bodyCtx = page.locator("body").getAttribute("data-course-id");
        if (bodyCtx != null && !bodyCtx.isBlank()) {
            return Integer.parseInt(bodyCtx.trim());
        }
        if (!Settings.COURSE_ID.isBlank()) {
            return Integer.parseInt(Settings.COURSE_ID.trim());
        }
        throw new IllegalStateException("could not resolve sandbox course id from playground activity");
    }
}
