package com.nuvepro.moodle;

import io.cucumber.testng.AbstractTestNGCucumberTests;
import io.cucumber.testng.CucumberOptions;
import org.testng.annotations.DataProvider;

/**
 * Complete Sandbox runner with five concurrent scenario workers.
 *
 * <p>Each worker owns its Playwright/Chromium process. {@code sandbox.feature} only —
 * create registry + Manage Labs. Isolated WS users per scenario; {@code sbmanage:manage_active}
 * serializes {@code @SB-manage-10} and {@code @SB-manage-16}. {@code @fresh_activity} scenarios
 * use their own activity cmid.</p>
 *
 * <p>Run: {@code .\mvnw test -Psandbox-parallel}</p>
 */
@CucumberOptions(
        features = "src/test/resources/features/sandbox.feature",
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
