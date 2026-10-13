package com.nuvepro.moodle.helpers;

import com.nuvepro.moodle.pages.MoodleSettings;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;

/** Baseline admin settings captured during {@code @settings} runs; restored on {@code @settings-cleanup}. */
public final class SettingsBaseline {
    private static final Path FILE = Paths.get("target", "settings-baseline.properties");

    private SettingsBaseline() {}

    public static synchronized void capture(String section, String fieldId, String value) {
        if (section == null || fieldId == null) return;
        Properties p = load();
        p.setProperty(section + "|" + fieldId, value == null ? "" : value);
        store(p);
    }

    public static synchronized boolean hasBaseline() {
        return Files.exists(FILE) && !load().stringPropertyNames().isEmpty();
    }

    public static Path baselinePath() {
        return FILE;
    }

    public static synchronized void restoreAll(MoodleSettings settings) {
        Properties p = load();
        Map<String, Map<String, String>> bySection = new LinkedHashMap<>();
        for (String key : p.stringPropertyNames()) {
            int sep = key.indexOf('|');
            if (sep <= 0) continue;
            String section = key.substring(0, sep);
            String field = key.substring(sep + 1);
            bySection.computeIfAbsent(section, s -> new LinkedHashMap<>()).put(field, p.getProperty(key));
        }
        for (Map.Entry<String, Map<String, String>> e : bySection.entrySet()) {
            settings.open(e.getKey());
            for (Map.Entry<String, String> field : e.getValue().entrySet()) {
                settings.setValue(field.getKey(), field.getValue());
            }
            settings.save();
        }
        System.out.println("[settings-cleanup] restored " + p.size() + " baseline field(s) across "
                + bySection.size() + " section(s)");
    }

    public static synchronized void clear() {
        try {
            Files.deleteIfExists(FILE);
        } catch (IOException ignored) {}
    }

    private static Properties load() {
        Properties p = new Properties();
        if (!Files.exists(FILE)) return p;
        try (InputStream in = Files.newInputStream(FILE)) {
            p.load(in);
        } catch (IOException ignored) {}
        return p;
    }

    private static void store(Properties p) {
        try {
            Files.createDirectories(FILE.getParent());
            try (OutputStream out = Files.newOutputStream(FILE)) {
                p.store(out, "Settings baseline for @settings-cleanup");
            }
        } catch (IOException e) {
            System.err.println("[settings-baseline] persist failed: " + e.getMessage());
        }
    }
}
