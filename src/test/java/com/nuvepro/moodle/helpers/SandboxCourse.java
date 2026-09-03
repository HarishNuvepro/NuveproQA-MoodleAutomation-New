package com.nuvepro.moodle.helpers;

import com.microsoft.playwright.Page;
import com.nuvepro.moodle.config.Settings;
import com.nuvepro.moodle.pages.CourseAdmin;

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

/**
 * One throwaway Moodle course for the whole Sandbox suite ({@code @SB-*}).
 * Created on first use, reused across create / user / Manage Labs, and deleted
 * only by the explicit {@code @sandbox-cleanup} scenario — never automatically.
 */
public final class SandboxCourse {
    private static final Path FILE = Paths.get("target", "sandbox-course.properties");
    private static final Path LOCK = Paths.get("target", "sandbox-course.lock");

    private SandboxCourse() {}

    /** Create the suite course if missing; reuse the registered id when it still exists. */
    public static String ensure(Page page) {
        if (page == null) {
            String id = idOrEmpty();
            if (id.isBlank()) {
                throw new IllegalStateException("sandbox course needs a page to create the suite course");
            }
            return id;
        }
        try {
            Files.createDirectories(LOCK.getParent());
            try (FileChannel ch = FileChannel.open(LOCK,
                    StandardOpenOption.CREATE, StandardOpenOption.WRITE);
                 FileLock ignored = ch.lock()) {
                String existing = readId();
                if (!existing.isBlank() && courseExists(page, existing)) {
                    return existing;
                }
                if (!existing.isBlank()) {
                    System.out.println("[SB-course] registered course " + existing
                            + " is gone — creating a new suite course");
                    SandboxActivityRegistry.clear();
                    SandboxUserRegistry.clear();
                }
                return createLocked(page);
            }
        } catch (IOException e) {
            throw new RuntimeException("sandbox course lock failed: " + e.getMessage(), e);
        }
    }

    public static String idOrEmpty() {
        return readId();
    }

    public static String id() {
        String id = readId();
        if (id.isBlank()) {
            throw new IllegalStateException(
                    "no sandbox suite course — run any @SB-* scenario first, or it was already cleaned up");
        }
        return id;
    }

    public static long idLong() {
        return Long.parseLong(id());
    }

    public static long idLongOrZero() {
        String id = readId();
        if (id.isBlank()) return 0L;
        try {
            return Long.parseLong(id);
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    public static void clear() {
        try {
            Files.deleteIfExists(FILE);
        } catch (IOException ignored) {}
    }

    private static String createLocked(Page page) {
        String stamp = String.valueOf(System.currentTimeMillis() % 1_000_000_000L);
        String fullName = "AUTO Sandbox Suite " + stamp;
        String shortName = "sbsuite" + stamp;
        String id = new CourseAdmin(page).createCourse(fullName, shortName);
        write(id, fullName);
        System.out.println("[SB-course] created suite course id=" + id + " name=" + fullName);
        return id;
    }

    private static boolean courseExists(Page page, String courseId) {
        try {
            page.navigate(Settings.BASE_URL + "/course/view.php?id=" + courseId,
                    new Page.NavigateOptions()
                            .setWaitUntil(com.microsoft.playwright.options.WaitUntilState.DOMCONTENTLOADED)
                            .setTimeout(45_000));
            String url = page.url() == null ? "" : page.url();
            if (!url.contains("course/view.php") || !url.contains("id=" + courseId)) return false;
            String body = page.locator("body").innerText();
            String lower = body == null ? "" : body.toLowerCase();
            return !lower.contains("can't find data record")
                    && !lower.contains("invalidcourseid")
                    && !lower.contains("course not found");
        } catch (RuntimeException e) {
            System.out.println("[SB-course] existence check failed for " + courseId + ": " + e.getMessage());
            return false;
        }
    }

    private static String readId() {
        Properties p = new Properties();
        if (!Files.exists(FILE)) return "";
        try (InputStream in = Files.newInputStream(FILE)) {
            p.load(in);
        } catch (IOException ignored) {
            return "";
        }
        return p.getProperty("courseId", "").trim();
    }

    private static void write(String courseId, String name) {
        Properties p = new Properties();
        p.setProperty("courseId", courseId);
        p.setProperty("name", name == null ? "" : name);
        try {
            Files.createDirectories(FILE.getParent());
            try (OutputStream out = Files.newOutputStream(FILE)) {
                p.store(out, "Sandbox suite course — delete only via @sandbox-cleanup");
            }
        } catch (IOException e) {
            throw new RuntimeException("could not persist sandbox course: " + e.getMessage(), e);
        }
    }
}
