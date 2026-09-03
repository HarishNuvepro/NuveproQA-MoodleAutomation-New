package com.nuvepro.moodle.helpers;

import com.nuvepro.moodle.config.Settings;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Minimal Moodle Web Services (REST) client for TEST DATA SEEDING only.
 * Uses the "Automation Seeding" token (Settings.WS_TOKEN). Available functions typically include:
 * core_user_create_users, enrol_manual_enrol_users, enrol_manual_unenrol_users (optional),
 * core_user_delete_users (optional — delete is best-effort).
 */
public final class ApiClient {
    private ApiClient() {}

    private static final HttpClient HTTP = trustAll();

    private static HttpClient trustAll() {
        try {
            SSLContext ssl = SSLContext.getInstance("TLS");
            ssl.init(null, new TrustManager[]{new X509TrustManager() {
                public void checkClientTrusted(X509Certificate[] c, String a) {}
                public void checkServerTrusted(X509Certificate[] c, String a) {}
                public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
            }}, new SecureRandom());
            return HttpClient.newBuilder().sslContext(ssl).build();
        } catch (Exception e) {
            return HttpClient.newHttpClient();
        }
    }

    private static String call(String function, Map<String, String> params) {
        Map<String, String> all = new LinkedHashMap<>();
        all.put("wstoken", Settings.WS_TOKEN);
        all.put("wsfunction", function);
        all.put("moodlewsrestformat", "json");
        all.putAll(params);
        String body = all.entrySet().stream()
                .map(e -> enc(e.getKey()) + "=" + enc(e.getValue()))
                .collect(Collectors.joining("&"));
        HttpRequest req = HttpRequest.newBuilder(URI.create(Settings.BASE_URL + "/webservice/rest/server.php"))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        try {
            HttpResponse<String> resp = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
            String out = resp.body();
            if (out != null && out.contains("\"exception\"")) {
                throw new RuntimeException("WS " + function + " error: " + out);
            }
            return out;
        } catch (RuntimeException re) {
            throw re;
        } catch (Exception e) {
            throw new RuntimeException("WS call failed: " + function + " - " + e.getMessage(), e);
        }
    }

    private static String enc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }

    /** A freshly-seeded user. */
    public static final class SeededUser {
        public final long id;
        public final String username;
        public final String email;
        public final String password;
        public SeededUser(long id, String username, String email, String password) {
            this.id = id; this.username = username; this.email = email; this.password = password;
        }
    }

    /** Create a fresh test user (unique via the supplied stamp) and return its details. */
    public static SeededUser createUser(long stamp) {
        String username = "autotest_" + stamp;
        String email = username + "@example.com";
        String password = "AutoT@" + stamp + "xZ";
        Map<String, String> p = new LinkedHashMap<>();
        p.put("users[0][username]", username);
        p.put("users[0][password]", password);
        p.put("users[0][firstname]", "Auto");
        p.put("users[0][lastname]", "Test" + stamp);
        p.put("users[0][email]", email);
        String json = call("core_user_create_users", p);
        Matcher m = Pattern.compile("\"id\"\\s*:\\s*(\\d+)").matcher(json);
        if (!m.find()) throw new RuntimeException("createUser: no id in response: " + json);
        return new SeededUser(Long.parseLong(m.group(1)), username, email, password);
    }

    /** Enrol a user into a course with the given role (5 = student by default). */
    public static void enrolUser(long userId, long courseId, int roleId) {
        Map<String, String> p = new LinkedHashMap<>();
        p.put("enrolments[0][roleid]", String.valueOf(roleId));
        p.put("enrolments[0][userid]", String.valueOf(userId));
        p.put("enrolments[0][courseid]", String.valueOf(courseId));
        call("enrol_manual_enrol_users", p);   // returns null/empty on success
    }

    /**
     * Unenrol a user from a course (manual enrol plugin). Best-effort — requires
     * {@code enrol_manual_unenrol_users} on the seeding token.
     */
    public static void unenrolUser(long userId, long courseId) {
        Map<String, String> p = new LinkedHashMap<>();
        p.put("enrolments[0][userid]", String.valueOf(userId));
        p.put("enrolments[0][courseid]", String.valueOf(courseId));
        call("enrol_manual_unenrol_users", p);
    }

    /** Read the plugin Practice Project results API through Moodle Web Services. */
    public static String practiceProjectResult(String practiceProjectId, String userEmail,
                                               boolean showTestCases) {
        Map<String, String> p = new LinkedHashMap<>();
        p.put("practiceProjectId", practiceProjectId);
        p.put("practiceProjectType", "internal");
        p.put("showTestCases", String.valueOf(showTestCases));
        if (userEmail != null && !userEmail.isBlank()) p.put("userEmail", userEmail);
        return call("mod_cloudlabs_get_guided_project_result", p);
    }

    /**
     * Exact-match user id for an email, or null when no such account exists. Used by cleanup so it
     * never deletes an account it merely happened to find on screen. Throws when the seeding token
     * lacks {@code core_user_get_users}, so callers can fall back to a UI lookup.
     */
    public static Long findUserIdByEmail(String email) {
        Map<String, String> p = new LinkedHashMap<>();
        p.put("criteria[0][key]", "email");
        p.put("criteria[0][value]", email);
        String json = call("core_user_get_users", p);
        if (json == null || !json.toLowerCase().contains(email.toLowerCase())) return null;
        Matcher m = Pattern.compile("\"id\"\\s*:\\s*(\\d+)").matcher(json);
        return m.find() ? Long.parseLong(m.group(1)) : null;
    }

    /** Delete a seeded user (best-effort cleanup; needs core_user_delete_users in the service). */
    public static void deleteUser(long userId) {
        try {
            Map<String, String> p = new LinkedHashMap<>();
            p.put("userids[0]", String.valueOf(userId));
            call("core_user_delete_users", p);
        } catch (RuntimeException e) {
            System.out.println("[ApiClient] deleteUser " + userId + " failed (non-fatal): " + e.getMessage());
        }
    }
}
