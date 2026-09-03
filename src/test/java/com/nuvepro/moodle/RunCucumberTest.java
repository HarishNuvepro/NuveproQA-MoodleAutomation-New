package com.nuvepro.moodle;

import io.cucumber.testng.AbstractTestNGCucumberTests;
import io.cucumber.testng.CucumberOptions;

/**
 * Main Cucumber runner for all feature files. Tag filtering comes from Maven
 * {@code -Dcucumber.tags=...} (surefire system property {@code cucumber.filter.tags}).
 *
 *   CLI:  .\mvnw test -Pwatch "-Dcucumber.tags=@PG-1"
 *   Fast: .\mvnw test
 *   Slow: .\mvnw test -Pslow
 */
@CucumberOptions(
        features = "src/test/resources/features",
        glue = "com.nuvepro.moodle.steps",
        plugin = {"pretty", "html:target/cucumber-report.html", "json:target/cucumber.json"}
)
public class RunCucumberTest extends AbstractTestNGCucumberTests {
}
