package com.nuvepro.moodle.steps;

import io.cucumber.java.After;
import io.cucumber.java.Before;
import io.cucumber.java.Scenario;

/** Scenario lifecycle: launch a fresh browser/page before each scenario; on the way out, capture
 *  failure diagnostics (screenshot/HTML/console/trace) BEFORE closing the browser. */
public class Hooks {
    private final TestContext ctx;

    public Hooks(TestContext ctx) {
        this.ctx = ctx;
    }

    @Before
    public void setUp(Scenario scenario) {
        ctx.scenarioName = scenario.getName();
        ctx.scenarioTags.clear();
        ctx.scenarioTags.addAll(scenario.getSourceTagNames());
        try {
            ctx.start();
        } catch (Throwable t) {
            try { ctx.stop(); } catch (Throwable ignored) {}
            throw t;
        }
    }

    /** order = 1 (low) so this runs LAST among the @After hooks — any tag-scoped cleanup that still
     *  needs ctx.page (e.g. @settings restore) runs first while the page is alive; we capture + close
     *  the context here at the end. */
    @After(order = 1)
    public void tearDown(Scenario scenario) {
        try {
            try { FailureCapture.onScenarioEnd(scenario, ctx); } catch (Throwable ignored) {}
            ctx.stop();
        } finally {
            ctx.exitLane();
        }
    }
}
