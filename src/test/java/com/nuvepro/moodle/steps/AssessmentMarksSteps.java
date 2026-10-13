package com.nuvepro.moodle.steps;

import com.nuvepro.moodle.config.Settings;
import com.nuvepro.moodle.pages.AssessmentAdmin;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import org.testng.SkipException;

import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

/**
 * Test Case Marks weightage (A63/A64/A81) — admin-only challenge-form behaviour. The radio group
 * {@code testcaseweightage} (SAME / DIFFERENT) appears only for Auto (script) evaluation on a
 * challenge lab (mod_form.php hideIf). SAME shows the per-test-case marks text field
 * ({@code eachtestcasemarks}); DIFFERENT hides it (marks are defined in the eval script instead).
 * Nothing is saved — the form is exercised and discarded.
 */
public class AssessmentMarksSteps {
    private final TestContext ctx;
    private final AssessmentAdmin admin;

    public AssessmentMarksSteps(TestContext ctx) {
        this.ctx = ctx;
        this.admin = new AssessmentAdmin(ctx.page);
    }

    private String value(String label) {
        return label.toLowerCase().contains("different") ? "DIFFERENT" : "SAME";
    }

    @Given("a challenge form with auto evaluation")
    public void challengeFormAutoEval() {
        if (ctx.assessmentCourseMissing()) throw new SkipException("COURSE_ID not set and no assessment suite course");
        admin.openChallengeFormForAutoEval(ctx.assessmentCourseId());
        if (ctx.page.locator("input[type=radio][name='testcaseweightage']").count() == 0)
            throw new SkipException("Test Case Marks (testcaseweightage) radio not on this form/plugin build");
    }

    @When("I select the {string} test case marks option")
    public void selectWeightage(String label) {
        admin.selectTestCaseWeightage(value(label));
    }

    @Then("the {string} option is selected and the per-test-case marks field is hidden")
    public void selectedAndHidden(String label) {
        assertTrue(admin.testCaseWeightageChecked(value(label)),
                "the '" + label + "' test case marks option is not selected");
        assertFalse(admin.eachTestCaseMarksVisible(),
                "the per-test-case marks field is still visible when '" + label + "' is selected");
    }

    @Then("the {string} option is no longer selected and the per-test-case marks field is shown")
    public void deselectedAndShown(String label) {
        assertFalse(admin.testCaseWeightageChecked(value(label)),
                "the '" + label + "' option is still selected after choosing the other option");
        assertTrue(admin.eachTestCaseMarksVisible(),
                "the per-test-case marks field is not shown when 'Same for all' is selected");
    }

    @Then("the {string} option is selected by default and the per-test-case marks field is shown")
    public void defaultSelectedAndShown(String label) {
        assertTrue(admin.testCaseWeightageChecked(value(label)),
                "the '" + label + "' option is not selected by default");
        assertTrue(admin.eachTestCaseMarksVisible(),
                "the per-test-case marks field is not shown by default");
    }
}
