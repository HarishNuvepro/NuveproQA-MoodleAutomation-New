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

/** One throwaway course for Assessment automation; deleted only via {@code @cleanup-assessment}. */
public final class AssessmentSuiteCourse {
    private static final Path FILE = Paths.get("target", "assessment-suite-course.properties");
    private static final Path LOCK = Paths.get("target", "assessment-suite-course.lock");

    private AssessmentSuiteCourse() {}

    public static String ensure(Page page) {
        if (page == null) {
            String id = idOrEmpty();
            if (id.isBlank()) {
                throw new IllegalStateException("assessment suite course needs a page to create");
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
                return createLocked(page);
            }
        } catch (IOException e) {
            throw new RuntimeException("assessment suite course lock failed: " + e.getMessage(), e);
        }
    }

    public static String idOrEmpty() {
        return readId();
    }

    public static void clear() {
        try {
            Files.deleteIfExists(FILE);
        } catch (IOException ignored) {}
    }

    private static String createLocked(Page page) {
        String stamp = String.valueOf(System.currentTimeMillis() % 1_000_000_000L);
        String fullName = "AUTO Assessment Suite " + stamp;
        String shortName = "asmtsuite" + stamp;
        String id = new CourseAdmin(page).createCourse(fullName, shortName);
        write(id, fullName);
        System.out.println("[ASMT-course] created suite course id=" + id + " name=" + fullName);
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
            String lower = page.locator("body").innerText().toLowerCase();
            return !lower.contains("can't find data record") && !lower.contains("invalidcourseid");
        } catch (RuntimeException e) {
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
                p.store(out, "Assessment suite course — delete only via @cleanup-assessment");
            }
        } catch (IOException e) {
            throw new RuntimeException("could not persist assessment suite course: " + e.getMessage(), e);
        }
    }
}
