package com.nuvepro.moodle.helpers;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;

/** Persistent registry for reusable seeded users, keyed by course id. */
public final class AssessmentUserRegistry {
    private static final Path FILE = Paths.get("target", "assessment-user-registry.properties");

    private AssessmentUserRegistry() {}

    public static synchronized ApiClient.SeededUser getForCourse(long courseId) {
        Properties p = load();
        String prefix = courseId + ".";
        String id = p.getProperty(prefix + "id", "").trim();
        String username = p.getProperty(prefix + "username", "").trim();
        String email = p.getProperty(prefix + "email", "").trim();
        String password = p.getProperty(prefix + "password", "").trim();
        if (id.isBlank() || username.isBlank() || password.isBlank()) return null;
        return new ApiClient.SeededUser(Long.parseLong(id), username, email, password);
    }

    public static synchronized void putForCourse(long courseId, ApiClient.SeededUser user) {
        if (user == null) return;
        Properties p = load();
        String prefix = courseId + ".";
        p.setProperty(prefix + "id", String.valueOf(user.id));
        p.setProperty(prefix + "username", user.username);
        p.setProperty(prefix + "email", user.email);
        p.setProperty(prefix + "password", user.password);
        store(p);
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
                p.store(out, "Assessment reusable users");
            }
        } catch (IOException ignored) {}
    }
}
