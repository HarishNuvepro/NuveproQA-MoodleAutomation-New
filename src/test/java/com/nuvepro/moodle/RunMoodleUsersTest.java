package com.nuvepro.moodle;

import io.cucumber.testng.AbstractTestNGCucumberTests;
import io.cucumber.testng.CucumberOptions;

/**
 * Cucumber runner scoped to Moodle user-administration flows.
 *
 *   Users file:  .\mvnw test -Pwatch "-Dcucumber.features=src/test/resources/features/moodle_users.feature"
 *   One user:    .\mvnw test -Pwatch "-Dcucumber.tags=@U-user-1"
 */
@CucumberOptions(
        features = {
                "src/test/resources/features/moodle_users.feature",
                "src/test/resources/features/upload_users.feature"
        },
        glue = "com.nuvepro.moodle.steps",
        tags = "not @skip",
        plugin = {"pretty", "html:target/cucumber-report.html", "json:target/cucumber.json"}
)
public class RunMoodleUsersTest extends AbstractTestNGCucumberTests {
}
