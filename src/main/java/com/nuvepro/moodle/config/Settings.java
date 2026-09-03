package com.nuvepro.moodle.config;

import io.github.cdimascio.dotenv.Dotenv;

/**
 * Central configuration, loaded from the .env file (or environment).
 * Mirrors config/settings.py from the Python suite.
 */
public final class Settings {
    private static final Dotenv ENV = Dotenv.configure()
            .directory(".")
            .ignoreIfMissing()
            .load();

    /** Dedicated file for assessment-automation input values (activity cmids, role accounts, …).
     *  Configure it once; assessment.* keys read from here first, then fall back to .env / environment. */
    private static final Dotenv ASSESS = Dotenv.configure()
            .directory(".")
            .filename("assessment.env")
            .ignoreIfMissing()
            .load();

    private Settings() {}

    private static String get(String key, String def) {
        String v = ENV.get(key);
        if (v == null || v.isBlank()) v = System.getenv(key);
        return (v == null || v.isBlank()) ? def : v.trim();
    }

    /** Read an assessment input value: assessment.env first, then the main .env / environment. */
    private static String getAssess(String key, String def) {
        String v = ASSESS.get(key);
        if (v == null || v.isBlank()) v = get(key, "");
        return (v == null || v.isBlank()) ? def : v.trim();
    }

    public static final String BASE_URL = stripTrailingSlash(get("MOODLE_BASE_URL", "http://localhost"));
    public static final String ADMIN_USER = get("MOODLE_ADMIN_USER", "admin");
    public static final String ADMIN_PASS = get("MOODLE_ADMIN_PASS", "");
    public static final String STUDENT_USER = get("MOODLE_STUDENT_USER", "student1");
    public static final String STUDENT_PASS = get("MOODLE_STUDENT_PASS", "");
    public static final String STUDENT2_USER = get("MOODLE_STUDENT2_USER", "");
    public static final String STUDENT2_PASS = get("MOODLE_STUDENT2_PASS", "");
    /** Course full name that student2 has completed (All Courses 100% tile). */
    public static final String ALLCOURSES_COMPLETED_NAME = get("ALLCOURSES_COMPLETED_NAME", "");
    public static final String WS_TOKEN = get("MOODLE_WS_TOKEN", "");
    // CloudLabs (provider) API — separate system from Moodle; used to extend a lab's duration so
    // existing-lab tests don't expire mid-run. Auth = admin login/pwd against the CloudLabs login API.
    public static final String CLOUDLABS_GATEWAY_URL = get("CLOUDLABS_GATEWAY_URL", "");
    public static final String CLOUDLABS_LOGIN_URL = get("CLOUDLABS_LOGIN_URL", "");
    public static final String CLOUDLABS_ADMIN_USER = get("CLOUDLABS_ADMIN_USER", "");
    public static final String CLOUDLABS_ADMIN_PASS = get("CLOUDLABS_ADMIN_PASS", "");
    public static final String PROVISIONING_MODE = get("PROVISIONING_MODE", "stub");
    public static final String COURSE_ID = get("COURSE_ID", "");
    /** Safe catalog identifiers used only by stamped, throwaway Lab Template tests. */
    public static final String LAB_TEMPLATE_PLAN_ID = get("LAB_TEMPLATE_PLAN_ID", "");
    public static final String LAB_TEMPLATE_TEAM_ID = get("LAB_TEMPLATE_TEAM_ID", "");
    /** Disposable secondary site admin used only to verify Upload users cannot delete admins. */
    public static final String UPLOAD_USERS_PROTECTED_ADMIN_USERNAME =
            get("UPLOAD_USERS_PROTECTED_ADMIN_USERNAME", "");
    /** Course with self-enrolment enabled so a learner can Enrol me (default 70). */
    public static final String SELF_ENROL_COURSE_ID = get("SELF_ENROL_COURSE_ID", "70");
    public static final String PLAYGROUND_CMID = get("PLAYGROUND_CMID", "");
    public static final String GUIDED_CMID = get("GUIDED_CMID", "");
    /** Optional Practice Project fixtures used by config-gated conversion scenarios. */
    public static final String GUIDED_WITHOUT_PROGRESS_CMID = get("GUIDED_WITHOUT_PROGRESS_CMID", "");
    public static final String GUIDED_NO_STOP_CMID = get("GUIDED_NO_STOP_CMID", "");
    public static final String GUIDED_PARALLEL_CMID = get("GUIDED_PARALLEL_CMID", "");
    public static final String GUIDED_TIMED_CMID = get("GUIDED_TIMED_CMID", "");
    public static final String PRACTICE_DETAILED_REPORT_URL = get("PRACTICE_DETAILED_REPORT_URL", "");
    public static final String PRACTICE_SUMMARY_REPORT_URL = get("PRACTICE_SUMMARY_REPORT_URL", "");
    // Assessment activity cmids — configured in assessment.env (see that file for what each is for).
    public static final String ASSESSMENT_CMID = getAssess("ASSESSMENT_CMID", "");
    public static final String ASSESSMENT_RANDOM_CMID = getAssess("ASSESSMENT_RANDOM_CMID", "");
    public static final String ASSESSMENT_TESTCASE_CMID = getAssess("ASSESSMENT_TESTCASE_CMID", "");
    public static final String ASSESSMENT_LIFECYCLE_CMID = getAssess("ASSESSMENT_LIFECYCLE_CMID", "");
    public static final String STATES_FAILED_CMID = getAssess("STATES_FAILED_CMID", "");
    // Optional role accounts for the manager/teacher visibility cases (Wave D). Leave blank to have the
    // suite seed throwaway users via the WS token instead.
    public static final String MANAGER_USER = getAssess("MANAGER_USER", "");
    public static final String MANAGER_PASS = getAssess("MANAGER_PASS", "");
    public static final String TEACHER_USER = getAssess("TEACHER_USER", "");
    public static final String TEACHER_PASS = getAssess("TEACHER_PASS", "");
    public static final String CRON_URL = getAssess("CRON_URL", "");
    // Confirms the suite may drive real assessments to COMPLETION on ASSESSMENT_LIFECYCLE_CMID.
    public static final boolean LIFECYCLE_COMPLETION_OK =
            getAssess("LIFECYCLE_COMPLETION_OK", "").toLowerCase().contains("yes");
    public static final String PLAYGROUND_VM_CMID = get("PLAYGROUND_VM_CMID", "");
    public static final String PLAYGROUND_CATALOG_CMID = get("PLAYGROUND_CATALOG_CMID", "");
    public static final String PLAYGROUND_BADTEMPLATE_CMID = get("PLAYGROUND_BADTEMPLATE_CMID", "");
    public static final String PLAYGROUND_UNLIMITED_CMID = get("PLAYGROUND_UNLIMITED_CMID", "");
    // Headed/headless and slow-motion can be overridden per-run from the command line:
    //   mvn test -Dheadless=false            -> watch the browser
    //   mvn test -Dheadless=false -Dslowmo=500 -> watch, slowed to 500ms/action
    public static final boolean HEADLESS = resolveBool("headless", "HEADLESS", "true");
    public static final double SLOWMO_MS = resolveNum("slowmo", "SLOWMO_MS", "0");
    public static final double DEFAULT_TIMEOUT_MS = Double.parseDouble(get("DEFAULT_TIMEOUT_MS", "15000"));

    private static boolean resolveBool(String sysProp, String envKey, String def) {
        String sys = System.getProperty(sysProp);
        if (sys != null && !sys.isBlank()) return Boolean.parseBoolean(sys.trim());
        return Boolean.parseBoolean(get(envKey, def));
    }

    private static double resolveNum(String sysProp, String envKey, String def) {
        String sys = System.getProperty(sysProp);
        if (sys != null && !sys.isBlank()) return Double.parseDouble(sys.trim());
        return Double.parseDouble(get(envKey, def));
    }

    private static String stripTrailingSlash(String s) {
        return s.endsWith("/") ? s.substring(0, s.length() - 1) : s;
    }
}
