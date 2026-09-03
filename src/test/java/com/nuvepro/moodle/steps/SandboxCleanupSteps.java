package com.nuvepro.moodle.steps;

import com.nuvepro.moodle.helpers.SandboxActivityRegistry;
import com.nuvepro.moodle.helpers.SandboxCourse;
import com.nuvepro.moodle.helpers.SandboxUserRegistry;
import com.nuvepro.moodle.pages.CourseAdmin;
import io.cucumber.java.en.When;

/** Explicit Sandbox cleanup. Runs only when the user asks for {@code @sandbox-cleanup}. */
public class SandboxCleanupSteps {
    private final TestContext ctx;

    public SandboxCleanupSteps(TestContext ctx) {
        this.ctx = ctx;
    }

    @When("admin deletes the sandbox suite course")
    public void adminDeletesTheSandboxSuiteCourse() {
        String courseId = SandboxCourse.idOrEmpty();
        if (courseId.isBlank()) {
            System.out.println("[SB-cleanup] no registered sandbox suite course");
            SandboxActivityRegistry.clear();
            SandboxUserRegistry.clear();
            return;
        }
        System.out.println("[SB-cleanup] deleting suite course id=" + courseId
                + " (labs / activities / enrolments go with the course — no per-lab Delete)");
        new CourseAdmin(ctx.page).deleteCourse(courseId);
        System.out.println("[SB-cleanup] deleted course id=" + courseId);
        SandboxCourse.clear();
        SandboxActivityRegistry.clear();
        SandboxUserRegistry.clear();
    }
}
