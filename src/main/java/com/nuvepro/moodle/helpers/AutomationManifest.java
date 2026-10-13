package com.nuvepro.moodle.helpers;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;

/**
 * Tracks automation-created artefacts for on-demand cleanup ({@code @module-cleanup}).
 * Does not run automatically — only records what the suite created.
 */
public final class AutomationManifest {
    private static final Path FILE = Paths.get("target", "automation-manifest.properties");

    private AutomationManifest() {}

    public static synchronized void registerUser(long userId, String username) {
        if (userId <= 0) return;
        Properties p = load();
        p.setProperty("user." + userId + ".username", username == null ? "" : username.trim());
        store(p);
    }

    public static synchronized List<Long> userIds() {
        Properties p = load();
        List<Long> ids = new ArrayList<>();
        for (String key : p.stringPropertyNames()) {
            if (!key.startsWith("user.") || !key.endsWith(".username")) continue;
            String idPart = key.substring("user.".length(), key.length() - ".username".length());
            try {
                ids.add(Long.parseLong(idPart));
            } catch (NumberFormatException ignored) {}
        }
        ids.sort(Long::compareTo);
        return ids;
    }

    public static synchronized void registerLabTemplate(String name) {
        if (name == null || name.isBlank()) return;
        Properties p = load();
        Set<String> names = labTemplateNames(p);
        names.add(name.trim());
        p.setProperty("labTemplates", String.join("\n", names));
        store(p);
    }

    public static synchronized List<String> labTemplateNames() {
        return new ArrayList<>(labTemplateNames(load()));
    }

    public static synchronized void registerAllCoursesCourse(String courseId) {
        registerCourseList("allCourses", courseId);
    }

    private static void registerCourseList(String key, String courseId) {
        if (courseId == null || courseId.isBlank()) return;
        Properties p = load();
        Set<String> ids = splitLines(p.getProperty(key, ""));
        ids.add(courseId.trim());
        p.setProperty(key, String.join("\n", ids));
        store(p);
    }

    public static synchronized List<String> allCoursesIds() {
        return new ArrayList<>(splitLines(load().getProperty("allCourses", "")));
    }

    public static synchronized void clearUsers() {
        Properties p = load();
        p.stringPropertyNames().stream()
                .filter(k -> k.startsWith("user."))
                .toList()
                .forEach(p::remove);
        store(p);
    }

    public static synchronized void clearLabTemplates() {
        Properties p = load();
        p.remove("labTemplates");
        store(p);
    }

    public static synchronized void clearAllCourses() {
        Properties p = load();
        p.remove("allCourses");
        store(p);
    }

    private static Set<String> labTemplateNames(Properties p) {
        return splitLines(p.getProperty("labTemplates", ""));
    }

    private static Set<String> splitLines(String raw) {
        Set<String> out = new LinkedHashSet<>();
        if (raw == null || raw.isBlank()) return out;
        for (String line : raw.split("\\R")) {
            if (!line.isBlank()) out.add(line.trim());
        }
        return out;
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
                p.store(out, "Automation manifest — cleanup via @module-cleanup only");
            }
        } catch (IOException e) {
            System.err.println("[manifest] could not persist: " + e.getMessage());
        }
    }
}
