package com.nuvepro.moodle.helpers;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.LoadState;
import com.microsoft.playwright.options.SelectOption;
import com.nuvepro.moodle.config.Settings;
import com.nuvepro.moodle.pages.ChallengeActivityForm;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Practice Project activity cmids: read {@code .env} / {@code practice.env} first; otherwise create once
 * in {@link PracticeSuiteCourse} and persist to {@code practice.env} for reuse.
 */
public final class PracticeProjectFixtures {
    public enum Variant {
        PRIMARY("GUIDED_CMID", "AUTO PP Primary"),
        WITHOUT_PROGRESS("GUIDED_WITHOUT_PROGRESS_CMID", "AUTO PP NoProgress"),
        NO_STOP("GUIDED_NO_STOP_CMID", "AUTO PP NoStop"),
        PARALLEL("GUIDED_PARALLEL_CMID", "AUTO PP Parallel"),
        TIMED("GUIDED_TIMED_CMID", "AUTO PP Timed");

        final String envKey;
        final String namePrefix;

        Variant(String envKey, String namePrefix) {
            this.envKey = envKey;
            this.namePrefix = namePrefix;
        }
    }

    private static final Path FILE = Paths.get("target", "practice-fixtures.properties");
    private static final Path LOCK = Paths.get("target", "practice-fixtures.lock");
    private static final Path PRACTICE_ENV = Paths.get("practice.env");

    private PracticeProjectFixtures() {}

    public static String primaryCmid(Page page) {
        return resolve(page, Variant.PRIMARY);
    }

    public static String variantCmid(Page page, Variant variant) {
        return resolve(page, variant);
    }

    public static String resolve(Page page, Variant variant) {
        String configured = fromPracticeEnvFile(variant.envKey);
        if (configured.isBlank()) {
            configured = Settings.practiceKey(variant.envKey);
        }
        if (!configured.isBlank()) {
            return configured.trim();
        }
        String cached = readProperty(variant.name());
        if (!cached.isBlank()) {
            return cached;
        }
        if (page == null) {
            throw new IllegalStateException(variant.envKey + " not set and no page to create fixture");
        }
        return ensureCreated(page, variant);
    }

    private static String ensureCreated(Page page, Variant variant) {
        try {
            Files.createDirectories(LOCK.getParent());
            try (FileChannel ch = FileChannel.open(LOCK,
                    StandardOpenOption.CREATE, StandardOpenOption.WRITE);
                 FileLock ignored = ch.lock()) {
                String cached = readProperty(variant.name());
                if (!cached.isBlank()) {
                    return cached;
                }
                String courseId = PracticeSuiteCourse.ensure(page);
                String cmid = createGuidedActivity(page, courseId, variant);
                writeProperty(variant.name(), cmid);
                appendPracticeEnv(variant.envKey, cmid);
                System.out.println("[PP-fixture] " + variant.envKey + "=" + cmid + " course=" + courseId);
                return cmid;
            }
        } catch (IOException e) {
            throw new RuntimeException("practice fixture lock failed: " + e.getMessage(), e);
        }
    }

    private static String createGuidedActivity(Page page, String courseId, Variant variant) {
        ChallengeActivityForm form = new ChallengeActivityForm(page);
        if (!form.openAddFormDirect(courseId)) {
            throw new RuntimeException("could not open guided add-activity form on course " + courseId);
        }
        String stamp = String.valueOf(System.currentTimeMillis() % 1_000_000_000L);
        String name = variant.namePrefix + " " + stamp;
        page.locator("#id_name").fill(name);
        pickRadio(page, "labtype", "guided");
        selectFirstLabTemplate(page);
        applyVariantOptions(page, variant);
        FormSubmit.andAwaitNewPage(page, page.locator("#id_submitbutton"));
        if (page.url().contains("modedit.php")) {
            throw new RuntimeException("guided activity form did not save: " + page.url());
        }
        String cmid = form.activityCmidFromUrl();
        if (cmid == null || cmid.isBlank()) {
            Matcher m = Pattern.compile("id=(\\d+)").matcher(page.url());
            if (m.find()) {
                cmid = m.group(1);
            }
        }
        if (cmid == null || cmid.isBlank()) {
            throw new RuntimeException("guided activity saved but cmid not found for " + name);
        }
        return cmid;
    }

    private static void applyVariantOptions(Page page, Variant variant) {
        switch (variant) {
            case PRIMARY -> pickRadio(page, "practiceprojecttype", "withprogresstracking");
            case WITHOUT_PROGRESS -> pickRadio(page, "practiceprojecttype", "withoutprogresstracking");
            case NO_STOP -> {
                pickRadio(page, "practiceprojecttype", "withprogresstracking");
                setYesNo(page, "show_stop_lab_button", false);
                Locator stopSelect = page.locator("#id_show_stop_lab_button, select[name='show_stop_lab_button']").first();
                if (stopSelect.count() > 0) {
                    try {
                        stopSelect.selectOption("0");
                    } catch (RuntimeException e) {
                        try {
                            stopSelect.selectOption("No");
                        } catch (RuntimeException ignored) {}
                    }
                }
            }
            case PARALLEL -> {
                pickRadio(page, "practiceprojecttype", "withprogresstracking");
                setYesNo(page, "parallelview", true);
            }
            case TIMED -> {
                pickRadio(page, "practiceprojecttype", "withprogresstracking");
                enableTimeLimitMinutes(page, 3);
                selectIfPresent(page, "overduehandling", "autosubmit");
                selectIfPresent(page, "actiontobeperformed", "Stop");
            }
        }
        page.waitForTimeout(500);
    }

    private static void selectFirstLabTemplate(Page page) {
        Locator sel = page.locator("#id_lab_template_id");
        if (sel.count() == 0) return;
        @SuppressWarnings("unchecked")
        java.util.List<Object> values = (java.util.List<Object>) sel.locator("option")
                .evaluateAll("els => els.map(o => o.value)");
        for (Object v : values) {
            if (v != null && !v.toString().isBlank() && !"0".equals(v.toString())) {
                sel.selectOption(v.toString());
                return;
            }
        }
    }

    private static void pickRadio(Page page, String name, String value) {
        Locator radio = page.locator("input[type=radio][name='" + name + "'][value='" + value + "']").first();
        if (radio.count() > 0) {
            radio.check(new Locator.CheckOptions().setForce(true));
            page.waitForTimeout(400);
        }
    }

    private static void setYesNo(Page page, String name, boolean yes) {
        String val = yes ? "1" : "0";
        Locator radio = page.locator("input[type=radio][name='" + name + "'][value='" + val + "']").first();
        if (radio.count() > 0) {
            radio.check(new Locator.CheckOptions().setForce(true));
            return;
        }
        Locator sel = page.locator("select[name='" + name + "'], #" + "id_" + name).first();
        if (sel.count() > 0) {
            sel.selectOption(yes ? "Yes" : "No");
        }
    }

    private static void enableTimeLimitMinutes(Page page, int minutes) {
        Locator enabled = page.locator("#id_timelimit_enabled");
        if (enabled.count() > 0 && !enabled.first().isChecked()) {
            enabled.first().check(new Locator.CheckOptions().setForce(true));
        }
        Locator num = page.locator("#id_timelimit_number, input[name='timelimit[number]']").first();
        if (num.count() > 0) {
            num.fill(String.valueOf(minutes));
        }
        selectIfPresent(page, "timelimit[timeunit]", "1");
    }

    private static void selectIfPresent(Page page, String name, String value) {
        Locator sel = page.locator("select[name='" + name + "'], #" + "id_" + name.replace("[", "_").replace("]", ""))
                .first();
        if (sel.count() == 0) {
            sel = page.locator("select[name='" + name + "']").first();
        }
        if (sel.count() > 0) {
            try {
                sel.selectOption(value);
            } catch (RuntimeException ignored) {
                try {
                    sel.selectOption(new SelectOption().setLabel(value));
                } catch (RuntimeException ignored2) {}
            }
        }
    }

    private static String readProperty(String variantName) {
        Properties p = loadProps();
        return p.getProperty("cmid." + variantName, "").trim();
    }

    private static void writeProperty(String variantName, String cmid) {
        Properties p = loadProps();
        p.setProperty("cmid." + variantName, cmid);
        p.setProperty("courseId", PracticeSuiteCourse.idOrEmpty());
        try {
            Files.createDirectories(FILE.getParent());
            try (OutputStream out = Files.newOutputStream(FILE)) {
                p.store(out, "Auto-created Practice Project fixtures");
            }
        } catch (IOException e) {
            System.err.println("[PP-fixture] could not persist properties: " + e.getMessage());
        }
    }

    private static Properties loadProps() {
        Properties p = new Properties();
        if (!Files.exists(FILE)) return p;
        try (InputStream in = Files.newInputStream(FILE)) {
            p.load(in);
        } catch (IOException ignored) {}
        return p;
    }

    private static String fromPracticeEnvFile(String key) {
        if (!Files.exists(PRACTICE_ENV)) return "";
        try {
            for (String line : Files.readAllLines(PRACTICE_ENV)) {
                String t = line.trim();
                if (t.isEmpty() || t.startsWith("#")) continue;
                if (t.startsWith(key + "=")) {
                    return t.substring(key.length() + 1).trim();
                }
            }
        } catch (IOException ignored) {}
        return "";
    }

    private static void appendPracticeEnv(String key, String cmid) {
        try {
            String line = key + "=" + cmid + System.lineSeparator();
            if (Files.exists(PRACTICE_ENV)) {
                String existing = Files.readString(PRACTICE_ENV);
                if (existing.contains(key + "=")) {
                    Files.writeString(PRACTICE_ENV,
                            existing.replaceAll("(?m)^" + key + "=.*$", key + "=" + cmid));
                    return;
                }
            }
            Files.writeString(PRACTICE_ENV, line, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            System.err.println("[PP-fixture] could not update practice.env: " + e.getMessage());
        }
    }
}
