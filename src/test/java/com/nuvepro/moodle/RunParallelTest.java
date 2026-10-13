package com.nuvepro.moodle;

import io.cucumber.testng.AbstractTestNGCucumberTests;
import io.cucumber.testng.CucumberOptions;
import org.testng.annotations.DataProvider;

/**
 * Parallel Cucumber runner: scenarios run concurrently, each thread with its own Playwright/Chromium
 * (sandbox.parallel=true). Thread count comes from {@code testng-parallel.xml}; the browser cap from
 * {@code -Dparallel.browsers} (the profile sets 8). Scope with -Dcucumber.features / -Dcucumber.tags.
 *
 * <p>Run: {@code .\mvnw.cmd test -Pparallel "-Dcucumber.features=src/test/resources/features/upload_users.feature"}</p>
 */
@CucumberOptions(
        features = "src/test/resources/features",
        glue = "com.nuvepro.moodle.steps",
        plugin = {"pretty", "html:target/cucumber-report.html", "json:target/cucumber.json"}
)
public class RunParallelTest extends AbstractTestNGCucumberTests {
    @Override
    @DataProvider(parallel = true)
    public Object[][] scenarios() {
        return super.scenarios();
    }
}
