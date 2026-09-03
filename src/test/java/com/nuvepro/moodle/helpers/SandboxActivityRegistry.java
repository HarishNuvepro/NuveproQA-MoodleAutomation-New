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
 * Persistent registry of created sandbox activities keyed by dataset name.
 * Lets {@code @SB-user} scenarios reuse 2–3 activities instead of creating one per case.
 */
public final class SandboxActivityRegistry {
    private static final Path FILE = Paths.get("target", "sandbox-activity-registry.properties");

    private SandboxActivityRegistry() {}

    public record Entry(String dataset, String name, String cmid, String sectionId, String courseId) {}

    public static synchronized Entry get(String dataset) {
        return get(dataset, SandboxCourse.idOrEmpty());
    }

    /**
     * Only returns an activity that belongs to {@code courseId}.
     * Entries with no course id (old COURSE_ID / PLAYGROUND_CMID runs) are ignored.
     */
    public static synchronized Entry get(String dataset, String courseId) {
        if (dataset == null || dataset.isBlank()) return null;
        if (courseId == null || courseId.isBlank()) return null;
        Properties p = load();
        String prefix = dataset.trim() + ".";
        String cmid = p.getProperty(prefix + "cmid", "").trim();
        if (cmid.isBlank()) return null;
        String storedCourse = p.getProperty(prefix + "courseId", "").trim();
        if (storedCourse.isBlank() || !storedCourse.equals(courseId.trim())) return null;
        return new Entry(dataset.trim(),
                sanitizeActivityName(p.getProperty(prefix + "name", "")),
                cmid,
                p.getProperty(prefix + "sectionId", "").trim(),
                storedCourse);
    }

    public static synchronized void put(String dataset, String name, String cmid, String sectionId) {
        put(dataset, name, cmid, sectionId, SandboxCourse.idOrEmpty());
    }

    public static synchronized void put(String dataset, String name, String cmid,
                                        String sectionId, String courseId) {
        if (dataset == null || dataset.isBlank() || cmid == null || cmid.isBlank()) return;
        if (courseId == null || courseId.isBlank()) return;
        Properties p = load();
        String prefix = dataset.trim() + ".";
        p.setProperty(prefix + "name", sanitizeActivityName(name));
        p.setProperty(prefix + "cmid", cmid.trim());
        p.setProperty(prefix + "sectionId", nz(sectionId));
        p.setProperty(prefix + "courseId", courseId.trim());
        store(p);
    }

    /** Poll until one of {@code datasets} is registered on this suite course. */
    public static Entry waitForAny(String courseId, String[] datasets, long timeoutMs) {
        if (courseId == null || courseId.isBlank() || datasets == null || datasets.length == 0) {
            return null;
        }
        long deadline = System.currentTimeMillis() + Math.max(0, timeoutMs);
        while (true) {
            for (String dataset : datasets) {
                Entry entry = get(dataset, courseId);
                if (entry != null) return entry;
            }
            if (System.currentTimeMillis() >= deadline) return null;
            try {
                Thread.sleep(2_000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return null;
            }
        }
    }

    public static synchronized List<Entry> all() {
        Properties p = load();
        Map<String, Entry> out = new LinkedHashMap<>();
        for (String key : p.stringPropertyNames()) {
            int dot = key.indexOf('.');
            if (dot <= 0) continue;
            String dataset = key.substring(0, dot);
            out.computeIfAbsent(dataset, SandboxActivityRegistry::get);
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
                p.store(out, "Sandbox activity registry");
            }
        } catch (IOException ignored) {}
    }

    private static String nz(String s) {
        return s == null ? "" : s.trim();
    }

    /**
     * Page-header scrapes sometimes include a "LAB" badge + newline before the real title.
     * Keep a single clean activity name line.
     */
    public static String sanitizeActivityName(String raw) {
        if (raw == null) return "";
        String n = raw.replace('\r', '\n').trim();
        if (n.isBlank()) return "";
        String[] lines = n.split("\n");
        for (int i = lines.length - 1; i >= 0; i--) {
            String line = lines[i].trim();
            if (line.isBlank()) continue;
            if (line.equalsIgnoreCase("LAB") || line.equalsIgnoreCase("Sandbox")
                    || line.equalsIgnoreCase("Playground")) continue;
            return line;
        }
        return n.replace('\n', ' ').trim();
    }
}
