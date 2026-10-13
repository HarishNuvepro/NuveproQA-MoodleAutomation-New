package com.nuvepro.moodle.steps;

import com.nuvepro.moodle.helpers.ApiClient;
import com.nuvepro.moodle.helpers.AssessmentActivityRegistry;
import com.nuvepro.moodle.helpers.AssessmentSuiteCourse;
import com.nuvepro.moodle.helpers.AutomationManifest;
import com.nuvepro.moodle.helpers.CleanupPolicy;
import com.nuvepro.moodle.helpers.CleanupRunner;
import com.nuvepro.moodle.helpers.PracticeSuiteCourse;
import com.nuvepro.moodle.helpers.SandboxActivityRegistry;
import com.nuvepro.moodle.helpers.SandboxCourse;
import com.nuvepro.moodle.helpers.SandboxUserRegistry;
import com.nuvepro.moodle.helpers.SettingsBaseline;
import com.nuvepro.moodle.pages.AdminUsers;
import com.nuvepro.moodle.pages.CourseAdmin;
import com.nuvepro.moodle.pages.LabTemplatesPage;
import com.nuvepro.moodle.pages.MoodleSettings;
import io.cucumber.java.en.When;
import org.testng.SkipException;

/**
 * On-demand cleanup only ({@code @module-cleanup}). Never wired into default {@code mvn test}.
 * Removes artefacts recorded in {@code target/automation-manifest.properties} and suite-course files.
 */
public class ModuleCleanupSteps {
    private final TestContext ctx;

    public ModuleCleanupSteps(TestContext ctx) {
        this.ctx = ctx;
    }

    @When("admin deletes all automation-created users")
    public void adminDeletesAllAutomationCreatedUsers() {
        if (ApiClient.wsUnavailable()) {
            throw new SkipException("MOODLE_WS_TOKEN not set — cannot delete users via WS");
        }
        if (CleanupPolicy.cleanupDryRun()) {
            System.out.println("[dry-run] would delete " + AutomationManifest.userIds().size()
                    + " user(s) from manifest: " + AutomationManifest.userIds());
            return;
        }
        AdminUsers admin = new AdminUsers(ctx.page);
        int ok = 0;
        for (Long id : AutomationManifest.userIds()) {
            try {
                if (admin.userProfilePresent(String.valueOf(id))) {
                    admin.deleteUserViaUi(String.valueOf(id));
                }
            } catch (RuntimeException ignored) {}
            try {
                CleanupRunner.deleteUserWs(id, "[cleanup-users]");
                ok++;
            } catch (RuntimeException e) {
                System.out.println("[cleanup-users] WS delete failed id=" + id + ": " + e.getMessage());
            }
        }
        AutomationManifest.clearUsers();
        System.out.println("[cleanup-users] finished (" + ok + " user id(s) processed)");
    }

    @When("admin deletes the sandbox suite course")
    public void adminDeletesTheSandboxSuiteCourse() {
        String courseId = SandboxCourse.idOrEmpty();
        if (courseId.isBlank()) {
            System.out.println("[SB-cleanup] no registered sandbox suite course");
            if (!CleanupPolicy.cleanupDryRun()) {
                SandboxActivityRegistry.clear();
                SandboxUserRegistry.clear();
            }
            return;
        }
        if (CleanupPolicy.cleanupDryRun()) {
            System.out.println("[dry-run] would delete sandbox suite course id=" + courseId);
            return;
        }
        System.out.println("[SB-cleanup] deleting suite course id=" + courseId
                + " (labs / activities / enrolments go with the course — no per-lab Delete)");
        CleanupRunner.deleteCourse(new CourseAdmin(ctx.page), courseId, "[SB-cleanup]");
        SandboxCourse.clear();
        SandboxActivityRegistry.clear();
        SandboxUserRegistry.clear();
    }

    @When("admin deletes all automation-created All Courses fixtures")
    public void adminDeletesAllAutomationCreatedAllCoursesFixtures() {
        CourseAdmin courseAdmin = new CourseAdmin(ctx.page);
        for (String id : AutomationManifest.allCoursesIds()) {
            try {
                CleanupRunner.deleteCourse(courseAdmin, id, "[cleanup-all-courses]");
            } catch (RuntimeException e) {
                System.out.println("[cleanup-all-courses] delete failed id=" + id + ": " + e.getMessage());
            }
        }
        if (!CleanupPolicy.cleanupDryRun()) {
            AutomationManifest.clearAllCourses();
        }
    }

    @When("admin deletes all automation-created lab templates")
    public void adminDeletesAllAutomationCreatedLabTemplates() {
        LabTemplatesPage templates = new LabTemplatesPage(ctx.page);
        for (String name : AutomationManifest.labTemplateNames()) {
            try {
                CleanupRunner.deleteLabTemplate(templates, name, "[cleanup-lab-templates]");
            } catch (RuntimeException e) {
                System.out.println("[cleanup-lab-templates] delete failed " + name + ": " + e.getMessage());
            }
        }
        if (!CleanupPolicy.cleanupDryRun()) {
            AutomationManifest.clearLabTemplates();
        }
    }

    @When("admin restores the automation Moodle settings baseline")
    public void adminRestoresTheAutomationMoodleSettingsBaseline() {
        if (!SettingsBaseline.hasBaseline()) {
            System.out.println("[settings-cleanup] no baseline file — nothing to restore");
            return;
        }
        if (CleanupPolicy.cleanupDryRun()) {
            System.out.println("[dry-run] would restore Moodle settings from "
                    + SettingsBaseline.baselinePath());
            return;
        }
        SettingsBaseline.restoreAll(new MoodleSettings(ctx.page));
        SettingsBaseline.clear();
    }

    @When("admin deletes the assessment suite course")
    public void adminDeletesTheAssessmentSuiteCourse() {
        String courseId = AssessmentSuiteCourse.idOrEmpty();
        if (courseId.isBlank()) {
            System.out.println("[cleanup-assessment] no assessment suite course registered "
                    + "(fixed COURSE_ID from .env is never deleted here)");
            return;
        }
        if (CleanupPolicy.cleanupDryRun()) {
            System.out.println("[dry-run] would delete assessment suite course id=" + courseId);
            return;
        }
        System.out.println("[cleanup-assessment] deleting suite course id=" + courseId
                + " (activities and enrolments cascade)");
        new CourseAdmin(ctx.page).deleteCourse(courseId);
        AssessmentSuiteCourse.clear();
        AssessmentActivityRegistry.clear();
        System.out.println("[cleanup-assessment] done");
    }

    @When("admin deletes the practice suite course")
    public void adminDeletesThePracticeSuiteCourse() {
        String courseId = PracticeSuiteCourse.idOrEmpty();
        if (courseId.isBlank()) {
            System.out.println("[cleanup-practice] no practice suite course registered "
                    + "(GUIDED_CMID fixture in .env is never deleted here)");
            return;
        }
        if (CleanupPolicy.cleanupDryRun()) {
            System.out.println("[dry-run] would delete practice suite course id=" + courseId);
            return;
        }
        System.out.println("[cleanup-practice] deleting suite course id=" + courseId);
        new CourseAdmin(ctx.page).deleteCourse(courseId);
        PracticeSuiteCourse.clear();
        System.out.println("[cleanup-practice] done");
    }
}
