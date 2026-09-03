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
import java.util.Locale;
import java.util.Map;
import java.util.Properties;

/**
 * Pool of known sandbox students (with passwords) plus per-activity usage marks.
 * Lets {@code @SB-user} reuse enrolled participants instead of creating a user every run.
 */
public final class SandboxUserRegistry {
    private static final Path FILE = Paths.get("target", "sandbox-user-registry.properties");

    private SandboxUserRegistry() {}

    public static synchronized List<ApiClient.SeededUser> usersForCourse(long courseId) {
        Properties p = load();
        String prefix = "course." + courseId + ".user.";
        Map<String, ApiClient.SeededUser> byKey = new LinkedHashMap<>();
        for (String key : p.stringPropertyNames()) {
            if (!key.startsWith(prefix) || !key.endsWith(".id")) continue;
            String stem = key.substring(0, key.length() - ".id".length());
            String id = p.getProperty(stem + ".id", "").trim();
            String username = p.getProperty(stem + ".username", "").trim();
            String email = p.getProperty(stem + ".email", "").trim();
            String password = p.getProperty(stem + ".password", "").trim();
            if (id.isBlank() || username.isBlank() || password.isBlank()) continue;
            byKey.put(username.toLowerCase(Locale.ROOT),
                    new ApiClient.SeededUser(Long.parseLong(id), username, email, password));
        }
        return new ArrayList<>(byKey.values());
    }

    public static synchronized void clear() {
        try {
            Files.deleteIfExists(FILE);
        } catch (IOException ignored) {}
    }

    public static synchronized void putNamed(String key, ApiClient.SeededUser user) {
        if (key == null || key.isBlank() || user == null) return;
        Properties p = load();
        String stem = "named." + key.trim();
        p.setProperty(stem + ".id", String.valueOf(user.id));
        p.setProperty(stem + ".username", user.username);
        p.setProperty(stem + ".email", user.email == null ? "" : user.email);
        p.setProperty(stem + ".password", user.password);
        store(p);
    }

    public static synchronized ApiClient.SeededUser getNamed(String key) {
        if (key == null || key.isBlank()) return null;
        Properties p = load();
        String stem = "named." + key.trim();
        String id = p.getProperty(stem + ".id", "").trim();
        String username = p.getProperty(stem + ".username", "").trim();
        String email = p.getProperty(stem + ".email", "").trim();
        String password = p.getProperty(stem + ".password", "").trim();
        if (id.isBlank() || username.isBlank() || password.isBlank()) return null;
        return new ApiClient.SeededUser(Long.parseLong(id), username, email, password);
    }

    public static synchronized void put(long courseId, ApiClient.SeededUser user) {
        if (user == null || user.username == null || user.username.isBlank()) return;
        Properties p = load();
        String stem = "course." + courseId + ".user." + user.username.trim().toLowerCase(Locale.ROOT);
        p.setProperty(stem + ".id", String.valueOf(user.id));
        p.setProperty(stem + ".username", user.username);
        p.setProperty(stem + ".email", user.email == null ? "" : user.email);
        p.setProperty(stem + ".password", user.password);
        store(p);
    }

    /** True if this username already ran / took the given activity cmid. */
    public static synchronized boolean hasUsedActivity(long courseId, String cmid, String username) {
        if (cmid == null || cmid.isBlank() || username == null || username.isBlank()) return false;
        Properties p = load();
        return "1".equals(p.getProperty(usedKey(courseId, cmid, username), "").trim());
    }

    public static synchronized void markUsedActivity(long courseId, String cmid, String username) {
        if (cmid == null || cmid.isBlank() || username == null || username.isBlank()) return;
        Properties p = load();
        p.setProperty(usedKey(courseId, cmid, username), "1");
        store(p);
    }

    /**
     * Rebuild credentials for an {@code autotest_<stamp>} participant created by {@link ApiClient#createUser}.
     */
    public static ApiClient.SeededUser fromAutotestUsername(String username) {
        if (username == null) return null;
        String u = username.trim();
        if (!u.toLowerCase(Locale.ROOT).startsWith("autotest_")) return null;
        String stamp = u.substring("autotest_".length());
        if (stamp.isBlank() || !stamp.chars().allMatch(Character::isDigit)) return null;
        long id;
        try {
            id = Long.parseLong(stamp);
        } catch (NumberFormatException e) {
            return null;
        }
        String email = u + "@example.com";
        String password = "AutoT@" + stamp + "xZ";
        return new ApiClient.SeededUser(id, u, email, password);
    }

    private static String usedKey(long courseId, String cmid, String username) {
        return "course." + courseId + ".cmid." + cmid.trim()
                + ".used." + username.trim().toLowerCase(Locale.ROOT);
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
                p.store(out, "Sandbox reusable students + activity usage");
            }
        } catch (IOException ignored) {}
    }
}
