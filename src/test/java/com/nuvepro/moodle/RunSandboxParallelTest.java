package com.nuvepro.moodle;

import io.cucumber.testng.AbstractTestNGCucumberTests;
import io.cucumber.testng.CucumberOptions;
import org.testng.annotations.DataProvider;

/**
 * Complete Sandbox runner with five concurrent scenario workers.
 *
 * <p>Each worker owns its Playwright/Chromium process. Sandbox student scenarios are kept
 * in one ordered lane because they intentionally reuse Account/VM/catalog labs. Manage Labs
 * scenarios use isolated users and may run concurrently. The four-delete scenario reserves
 * all remaining browser slots before launching its four delete browsers.</p>
 *
 * <p>Run: {@code .\mvnw test -Psandbox-parallel}</p>
 */
@CucumberOptions(
        features = {
                "src/test/resources/features/sandbox_create.feature",
                "src/test/resources/features/sandbox_user_functionality.feature",
                "src/test/resources/features/sandbox_actions.feature"
        },
        glue = "com.nuvepro.moodle.steps",
        tags = "not @skip",
        plugin = {
                "pretty",
                "html:target/cucumber-sandbox-parallel.html",
                "json:target/cucumber-sandbox-parallel.json"
        }
)
public class RunSandboxParallelTest extends AbstractTestNGCucumberTests {
    @Override
    @DataProvider(parallel = true)
    public Object[][] scenarios() {
        return super.scenarios();
    }
}
