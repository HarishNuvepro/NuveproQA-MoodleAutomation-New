package com.nuvepro.moodle.helpers;

import com.nuvepro.moodle.pages.CourseAdmin;
import com.nuvepro.moodle.pages.LabTemplatesPage;

/** Shared delete helpers for {@code @module-cleanup} (respects {@link CleanupPolicy#cleanupDryRun()}). */
public final class CleanupRunner {
    private CleanupRunner() {}

    public static void deleteCourse(CourseAdmin admin, String courseId, String logPrefix) {
        if (courseId == null || courseId.isBlank()) return;
        if (CleanupPolicy.cleanupDryRun()) {
            System.out.println("[dry-run] " + logPrefix + " would delete course id=" + courseId);
            return;
        }
        admin.deleteCourse(courseId);
        System.out.println(logPrefix + " deleted course id=" + courseId);
    }

    public static void deleteLabTemplate(LabTemplatesPage templates, String name, String logPrefix) {
        if (name == null || name.isBlank()) return;
        if (CleanupPolicy.cleanupDryRun()) {
            System.out.println("[dry-run] " + logPrefix + " would delete lab template " + name);
            return;
        }
        templates.openList();
        if (templates.templateExists(name)) {
            templates.delete(name);
            System.out.println(logPrefix + " deleted " + name);
        }
    }

    public static void deleteUserWs(long userId, String logPrefix) {
        if (userId <= 0) return;
        if (CleanupPolicy.cleanupDryRun()) {
            System.out.println("[dry-run] " + logPrefix + " would WS-delete user id=" + userId);
            return;
        }
        ApiClient.deleteUser(userId);
        System.out.println(logPrefix + " deleted user id=" + userId);
    }
}
