package com.nuvepro.moodle.steps;

import com.nuvepro.moodle.config.Settings;
import com.nuvepro.moodle.helpers.AssessmentActivityRegistry;
import com.nuvepro.moodle.pages.ChallengeActivityForm;
import io.cucumber.java.en.When;
import org.testng.SkipException;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Explicit cleanup for created assessment activities. Runs only when the user asks for it. */
public class AssessmentCleanupSteps {
    private final TestContext ctx;
    private final ChallengeActivityForm form;

    public AssessmentCleanupSteps(TestContext ctx) {
        this.ctx = ctx;
        this.form = new ChallengeActivityForm(ctx.page);
    }

    @When("admin deletes all registered assessment activities")
    public void adminDeletesAllRegisteredAssessmentActivities() {
        if (Settings.COURSE_ID.isEmpty()) throw new SkipException("COURSE_ID not set");
        List<AssessmentActivityRegistry.Entry> entries = AssessmentActivityRegistry.all();
        if (entries.isEmpty()) {
            System.out.println("[assessment-cleanup] no registered activities");
            return;
        }
        Set<String> deleted = new HashSet<>();
        for (AssessmentActivityRegistry.Entry entry : entries) {
            if (entry == null || entry.cmid() == null || entry.cmid().isBlank()) continue;
            if (!deleted.add(entry.cmid())) continue;
            try {
                ctx.page.navigate(Settings.BASE_URL + "/course/view.php?id=" + Settings.COURSE_ID,
                        new com.microsoft.playwright.Page.NavigateOptions()
                                .setWaitUntil(com.microsoft.playwright.options.WaitUntilState.DOMCONTENTLOADED)
                                .setTimeout(45_000));
                form.deleteActivity(entry.cmid());
                System.out.println("[assessment-cleanup] deleted dataset=" + entry.dataset()
                        + " cmid=" + entry.cmid() + " name=" + entry.name());
            } catch (Throwable t) {
                System.out.println("[assessment-cleanup] delete failed for dataset=" + entry.dataset()
                        + " cmid=" + entry.cmid() + ": " + t.getMessage());
            }
        }
        AssessmentActivityRegistry.clear();
    }
}
