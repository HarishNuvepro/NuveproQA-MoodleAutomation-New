package com.nuvepro.moodle;

import io.cucumber.testng.AbstractTestNGCucumberTests;
import io.cucumber.testng.CucumberOptions;

/**
 * Cucumber runner scoped to assessment create + read-back flows.
 *
 *   CLI:  .\mvnw.cmd test -Pwatch "-Dcucumber.features=src/test/resources/features/assessment_create.feature"
 *   One:  .\mvnw.cmd test -Pwatch "-Dcucumber.tags=@A-create-full"
 */
@CucumberOptions(
        features = "src/test/resources/features/assessment_create.feature",
        glue = "com.nuvepro.moodle.steps",
        tags = "not @skip",
        plugin = {"pretty", "html:target/cucumber-report.html", "json:target/cucumber.json"}
)
public class RunAssessmentCreateTest extends AbstractTestNGCucumberTests {
}
