package com.nuvepro.moodle.helpers;

import com.microsoft.playwright.Page;
import com.nuvepro.moodle.config.Settings;

/** Resolves throwaway suite courses vs fixed .env course ids (only suite courses are cleanup-deletable). */
public final class AutomationCourses {
    private AutomationCourses() {}

    /**
     * Assessment activities: prefer the registered suite course; otherwise {@code COURSE_ID} from .env
     * (not deleted by cleanup); if both empty, create a suite course on first use.
     */
    public static String assessmentCourseId(Page page) {
        String suite = AssessmentSuiteCourse.idOrEmpty();
        if (!suite.isBlank()) return suite;
        if (!Settings.COURSE_ID.isBlank()) return Settings.COURSE_ID;
        if (page == null) throw new IllegalStateException("COURSE_ID not set and no assessment suite course");
        return AssessmentSuiteCourse.ensure(page);
    }

    public static boolean assessmentSuiteCourseRegistered() {
        return !AssessmentSuiteCourse.idOrEmpty().isBlank();
    }

    /**
     * Practice activity create/form flows: suite course when registered or on first use;
     * does not replace {@code GUIDED_CMID} fixture activities in .env.
     */
    public static String practiceCourseId(Page page) {
        String suite = PracticeSuiteCourse.idOrEmpty();
        if (!suite.isBlank()) return suite;
        if (page == null) return "";
        return PracticeSuiteCourse.ensure(page);
    }

    public static boolean practiceSuiteCourseRegistered() {
        return !PracticeSuiteCourse.idOrEmpty().isBlank();
    }
}
