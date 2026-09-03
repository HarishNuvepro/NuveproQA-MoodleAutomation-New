package com.nuvepro.moodle;

import io.cucumber.testng.AbstractTestNGCucumberTests;
import io.cucumber.testng.CucumberOptions;

/**
 * Cucumber runner scoped to assessment user-functionality flows.
 *
 *   CLI:  .\mvnw.cmd test -Pwatch "-Dcucumber.features=src/test/resources/features/assessment_user_functionality.feature"
 *   One:  .\mvnw.cmd test -Pwatch "-Dcucumber.tags=@A-user-1"
 */
@CucumberOptions(
        features = "src/test/resources/features/assessment_user_functionality.feature",
        glue = "com.nuvepro.moodle.steps",
        tags = "not @skip",
        plugin = {"pretty", "html:target/cucumber-report.html", "json:target/cucumber.json"}
)
public class RunAssessmentUserFunctionalityTest extends AbstractTestNGCucumberTests {
}
