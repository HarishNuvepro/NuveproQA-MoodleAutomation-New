package com.nuvepro.moodle.helpers;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

/**
 * Persistent registry of created assessment activities keyed by dataset name.
 * Lets user-flow scenarios reuse an existing activity instead of creating one every time.
 */
public final class AssessmentActivityRegistry {
    private static final Path FILE = Paths.get("target", "assessment-activity-registry.properties");

    private AssessmentActivityRegistry() {}

    public record Entry(String dataset, String name, String cmid, String sectionId) {}

    public static synchronized Entry get(String dataset) {
        if (dataset == null || dataset.isBlank()) return null;
        Properties p = load();
        String prefix = dataset.trim() + ".";
        String cmid = p.getProperty(prefix + "cmid", "").trim();
        if (cmid.isBlank()) return null;
        return new Entry(dataset.trim(),
                p.getProperty(prefix + "name", "").trim(),
                cmid,
                p.getProperty(prefix + "sectionId", "").trim());
    }

    public static synchronized void put(String dataset, String name, String cmid, String sectionId) {
        if (dataset == null || dataset.isBlank() || cmid == null || cmid.isBlank()) return;
        Properties p = load();
        String prefix = dataset.trim() + ".";
        p.setProperty(prefix + "name", nz(name));
        p.setProperty(prefix + "cmid", cmid.trim());
        p.setProperty(prefix + "sectionId", nz(sectionId));
        store(p);
    }

    public static synchronized List<Entry> all() {
        Properties p = load();
        Map<String, Entry> out = new LinkedHashMap<>();
        for (String key : p.stringPropertyNames()) {
            int dot = key.indexOf('.');
            if (dot <= 0) continue;
            String dataset = key.substring(0, dot);
            out.computeIfAbsent(dataset, d -> get(d));
        }
        return new ArrayList<>(out.values());
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
                p.store(out, "Assessment activity registry");
            }
        } catch (IOException ignored) {}
    }

    private static String nz(String s) {
        return s == null ? "" : s.trim();
    }
}
