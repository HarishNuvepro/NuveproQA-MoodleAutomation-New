package com.nuvepro.moodle.helpers;

import com.nuvepro.moodle.config.Settings;

/**
 * On-demand cleanup controls (Maven {@code -D} flags). Scenario {@code @After} hooks consult
 * {@link #deferScenarioCleanup()} so artefacts stay on the site until {@code -Pcleanup}.
 */
public final class CleanupPolicy {
    private CleanupPolicy() {}

    /** When true, module {@code @After} hooks skip deletes/restores; use {@code @module-cleanup} instead. */
    public static boolean deferScenarioCleanup() {
        return Settings.DEFER_SCENARIO_CLEANUP;
    }

    /** When true, {@code @module-cleanup} only logs planned actions (manifest/registry unchanged). */
    public static boolean cleanupDryRun() {
        return Settings.CLEANUP_DRY_RUN;
    }

    public static boolean runScenarioCleanup() {
        return !deferScenarioCleanup();
    }
}
