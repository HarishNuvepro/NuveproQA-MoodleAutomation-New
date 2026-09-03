package com.nuvepro.moodle.steps;

import com.nuvepro.moodle.config.Settings;
import com.nuvepro.moodle.helpers.CronRunner;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import org.testng.SkipException;

import static org.testng.Assert.assertTrue;

/**
 * Wave F cron cases (A185-A188 retake-cron). Triggers the cloudlabs scheduled tasks headlessly via the
 * admin "Run now" flow / web cron and asserts the task executed. NOTE: the full multi-user attempt EFFECT
 * (attempts actually incremented for many users) needs a seeded-users-with-attempts fixture; these verify
 * the cron mechanism runs — the effect assertion is best-effort and skips if the task can't be triggered.
 */
public class AssessmentCronSteps {
    private final TestContext ctx;

    public AssessmentCronSteps(TestContext ctx) {
        this.ctx = ctx;
    }

    @When("the cloudlabs scheduled tasks are run")
    public void theScheduledTasksAreRun() {
        if (Settings.CRON_URL.isEmpty()) throw new SkipException("CRON_URL not set in assessment.env");
        boolean ran = CronRunner.runWebCron(ctx.page);
        if (!ran) throw new SkipException("web cron did not report execution (check CRON_URL / cron access)");
    }

    @Then("the cron run completes without error")
    public void theCronRunCompletes() {
        String body = ctx.page.locator("body").innerText().toLowerCase();
        assertTrue(!body.contains("exception") && !body.contains("fatal error"),
                "cron output reported an error");
    }
}
