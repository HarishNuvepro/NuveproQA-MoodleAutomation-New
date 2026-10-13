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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * CloudLabs (provider) API client — SEPARATE system from Moodle. Authenticates with an admin
 * login (username/password → JSON {"token": ...}) and uses that token as the X-CSRF-Token header.
 * Used to EXTEND an existing lab's duration so long/existing-lab tests don't expire mid-run.
 * The Manage Labs lab id == the provider subscriptionId.
 *
 *   POST {gateway}/v1/users/login              (form: username, password) -> {"token": "..."}
 *   POST {gateway}/v1/subscriptions/extendDuration  (form: subscriptionId, duration, durationType)
 *        header X-CSRF-Token: {token}          -> {"ResponseStatus":"SUCCESS", ...}
 */
public final class CloudLabsClient {
    private CloudLabsClient() {}

    static {
        // strictSSL=false in the Postman collection: the CloudLabs gateway cert can mismatch its hostname
        // (no SAN for qalb.cloudloka.com). A trust-all TrustManager does NOT disable HttpClient hostname
        // verification, so disable it explicitly (must be set before the HttpClient is built).
        System.setProperty("jdk.internal.httpclient.disableHostnameVerification", "true");
    }

    private static final HttpClient HTTP = trustAll();   // strictSSL=false in the Postman collection
    private static volatile String token;
    private static volatile boolean accountBlocked;
    private static volatile long lastLoginMs;

    /** CloudLabs rejected login (HTTP 406 "temporarily blocked") — do not keep retrying. */
    public static final class UnavailableException extends RuntimeException {
        public UnavailableException(String message) { super(message); }
    }

    public static boolean isConfigured() {
        return !Settings.CLOUDLABS_GATEWAY_URL.isEmpty()
                && !Settings.CLOUDLABS_ADMIN_USER.isEmpty()
                && !Settings.CLOUDLABS_ADMIN_PASS.isEmpty();
    }

    private static String gateway() {
        return Settings.CLOUDLABS_GATEWAY_URL.replaceAll("/+$", "");
    }

    private static String loginUrl() {
        // Always derive from the gateway base (validated: {gateway}/v1/users/login returns the token).
        // Ignore CLOUDLABS_LOGIN_URL to avoid pointing it at the UI host by mistake.
        return gateway() + "/v1/users/login";
    }

    public static boolean isBlocked() {
        return accountBlocked;
    }

    /** Reuse a cached token; never throw when the account is 406-blocked (callers fall back to UI). */
    private static synchronized String ensureToken() {
        if (token != null) return token;
        if (accountBlocked) return null;
        try {
            return login();
        } catch (UnavailableException e) {
            return null;
        } catch (RuntimeException e) {
            System.out.println("[CloudLabs] login unavailable: " + e.getMessage());
            return null;
        }
    }

    /** Authenticate and cache the token. Reuses a live token so a long suite does not 406-block the account. */
    public static synchronized String login() {
        if (token != null) return token;
        if (accountBlocked) {
            throw new UnavailableException("CloudLabs account is temporarily blocked (HTTP 406)");
        }
        if (lastLoginMs > 0 && System.currentTimeMillis() - lastLoginMs < 30_000) {
            return token;
        }
        String body = "username=" + enc(Settings.CLOUDLABS_ADMIN_USER)
                + "&password=" + enc(Settings.CLOUDLABS_ADMIN_PASS);
        HttpResponse<String> resp = post(loginUrl(), body, null);
        lastLoginMs = System.currentTimeMillis();
        String respBody = resp.body() == null ? "" : resp.body();
        if (resp.statusCode() == 406 || respBody.toLowerCase().contains("temporarily blocked")) {
            accountBlocked = true;
            throw new UnavailableException("CloudLabs login failed: HTTP " + resp.statusCode()
                    + " " + snippet(respBody));
        }
        if (resp.statusCode() == 401 || resp.statusCode() == 403) {
            throw new UnavailableException("CloudLabs login failed: HTTP " + resp.statusCode()
                    + " " + snippet(respBody));
        }
        if (resp.statusCode() != 200) {
            throw new RuntimeException("CloudLabs login failed: HTTP " + resp.statusCode() + " " + snippet(respBody));
        }
        Matcher m = Pattern.compile("\"token\"\\s*:\\s*\"([^\"]+)\"").matcher(respBody);
        if (!m.find()) throw new RuntimeException("CloudLabs login: no token in response: " + snippet(respBody));
        token = m.group(1);
        return token;
    }

    /** Extend a lab's duration. Returns true on ResponseStatus SUCCESS. Re-logs-in once on 401/403. */
    public static boolean extendDuration(String subscriptionId, int duration, String durationType) {
        if (subscriptionId == null || subscriptionId.isEmpty()) return false;
        if (ensureToken() == null) return false;
        String body = "subscriptionId=" + enc(subscriptionId)
                + "&duration=" + duration + "&durationType=" + enc(durationType);
        String url = gateway() + "/v1/subscriptions/extendDuration";
        HttpResponse<String> resp = post(url, body, token);
        if (resp.statusCode() == 401 || resp.statusCode() == 403) {   // token expired -> re-login once
            token = null;
            if (ensureToken() == null) return false;
            resp = post(url, body, token);
        }
        boolean ok = resp.statusCode() == 200 && resp.body() != null && resp.body().contains("SUCCESS");
        System.out.println("[CloudLabs] extendDuration sub=" + subscriptionId + " +" + duration + durationType
                + " -> " + (ok ? "SUCCESS" : "HTTP " + resp.statusCode() + " " + snippet(resp.body())));
        return ok;
    }

    /** Convenience: extend by N hours. */
    public static boolean extendHours(String subscriptionId, int hours) {
        return extendDuration(subscriptionId, hours, "Hours");
    }

    /** Reduce a lab's duration by N hours (negative extend). */
    public static boolean reduceHours(String subscriptionId, int hours) {
        return extendDuration(subscriptionId, -Math.abs(hours), "Hours");
    }

    /** Force a lab to EXPIRE by pushing its end time well into the past. */
    public static boolean expireLab(String subscriptionId) {
        return reduceHours(subscriptionId, 240);   // -10 days: end time is now in the past
    }

    // ---- authoritative status (replaces UI scraping) ----

    /** A subscription's current action/status from CloudLabs (nl_action / nl_status). */
    public static final class SubStatus {
        public final String action;
        public final String status;
        public SubStatus(String action, String status) { this.action = action; this.status = status; }
    }

    /** Live status of a lab from CloudLabs. Returns null if the id isn't found or API is blocked. */
    public static SubStatus getStatus(String subscriptionId) {
        if (ensureToken() == null) return null;
        String url = gateway() + "/v1/subscriptions/getSubscriptionsStatus";
        HttpResponse<String> resp = post(url, "subscriptionIds=" + enc(subscriptionId), token);
        if (resp.statusCode() == 401 || resp.statusCode() == 403) {
            String previous = token;
            token = null;
            if (ensureToken() == null) {
                token = previous;
                return null;
            }
            resp = post(url, "subscriptionIds=" + enc(subscriptionId), token);
        }
        String b = resp.body();
        if (b == null || b.isBlank() || b.contains("[]")) {
            return null;
        }
        if (subscriptionId != null && !subscriptionId.isBlank() && !b.contains(subscriptionId)) {
            System.out.println("[CloudLabs] getStatus: id " + subscriptionId
                    + " not in body — still parsing snippet=" + snippet(b));
        }
        Matcher paired = Pattern.compile(
                "\"nl_action\"\\s*:\\s*\"([^\"]*)\"\\s*,\\s*\"nl_status\"\\s*:\\s*\"([^\"]*)\"").matcher(b);
        if (paired.find()) {
            return new SubStatus(paired.group(1), paired.group(2));
        }
        Matcher action = Pattern.compile("\"nl_action\"\\s*:\\s*\"([^\"]*)\"").matcher(b);
        Matcher status = Pattern.compile("\"nl_status\"\\s*:\\s*\"([^\"]*)\"").matcher(b);
        if (action.find() && status.find()) {
            return new SubStatus(action.group(1), status.group(1));
        }
        return null;
    }

    /** Raw getSubscriptionsStatus JSON for duration / field cross-checks. */
    public static String getSubscriptionsStatusBody(String subscriptionId) {
        if (subscriptionId == null || subscriptionId.isBlank()) return "";
        if (ensureToken() == null) return "";
        String url = gateway() + "/v1/subscriptions/getSubscriptionsStatus";
        HttpResponse<String> resp = post(url, "subscriptionIds=" + enc(subscriptionId), token);
        if (resp.statusCode() == 401 || resp.statusCode() == 403) {
            String previous = token;
            token = null;
            if (ensureToken() == null) {
                token = previous;
                return "";
            }
            resp = post(url, "subscriptionIds=" + enc(subscriptionId), token);
        }
        return resp.body() == null ? "" : resp.body();
    }

    public static final class DurationInfo {
        public final Integer allottedMinutes;
        public final Integer consumedMinutes;
        public final Integer remainingMinutes;
        public final boolean unlimited;
        public final String raw;
        public DurationInfo(Integer allotted, Integer consumed, Integer remaining, boolean unlimited, String raw) {
            this.allottedMinutes = allotted;
            this.consumedMinutes = consumed;
            this.remainingMinutes = remaining;
            this.unlimited = unlimited;
            this.raw = raw;
        }
    }

    public static DurationInfo parseDuration(String json) {
        if (json == null) json = "";
        String lower = json.toLowerCase(java.util.Locale.ROOT);
        boolean unlimited = lower.contains("unlimited")
                || lower.contains("\"duration\":-1")
                || lower.contains("\"isunlimited\":true");
        Integer allotted = firstInt(json, "allotted", "alloted", "totalduration", "durationhours", "labduration");
        Integer remaining = firstInt(json, "remainingduration", "remaining", "timeleft");
        Integer consumed = firstInt(json, "consumedduration", "consumed", "usedduration");
        return new DurationInfo(allotted, consumed, remaining, unlimited, json);
    }

    private static Integer firstInt(String json, String... keys) {
        for (String key : keys) {
            Matcher m = Pattern.compile("\"" + key + "\"\\s*:\\s*\"?(-?\\d+(?:\\.\\d+)?)\"?",
                    Pattern.CASE_INSENSITIVE).matcher(json);
            if (m.find()) {
                try {
                    double v = Double.parseDouble(m.group(1));
                    if (v < 0) continue;
                    // hours vs minutes heuristic: values <= 48 treated as hours.
                    if (v <= 48 && !key.toLowerCase(java.util.Locale.ROOT).contains("minute")) {
                        return (int) Math.round(v * 60);
                    }
                    return (int) Math.round(v);
                } catch (NumberFormatException ignored) {}
            }
        }
        return null;
    }

    /**
     * Poll CloudLabs (fast, logged) until the lab reaches one of the target states, or timeout.
     * Replaces the slow/opaque Manage-Labs UI polling.
     */
    public static com.nuvepro.moodle.pages.ManageLabs.LabState waitForState(
            String subscriptionId, java.util.Set<com.nuvepro.moodle.pages.ManageLabs.LabState> targets,
            int maxSeconds, int intervalSeconds) {
        if (accountBlocked && token == null) {
            System.out.println("[CloudLabs] waitForState skipped — account blocked (use console UI)");
            return com.nuvepro.moodle.pages.ManageLabs.LabState.NOTCREATED;
        }
        long deadline = System.currentTimeMillis() + maxSeconds * 1000L;
        com.nuvepro.moodle.pages.ManageLabs.LabState st;
        int poll = 0;
        do {
            SubStatus s = getStatus(subscriptionId);
            st = (s == null) ? com.nuvepro.moodle.pages.ManageLabs.LabState.NOTCREATED
                    : com.nuvepro.moodle.pages.ManageLabs.classifyActionStatus(s.action, s.status);
            System.out.println("[CloudLabs] poll " + (++poll) + " lab=" + subscriptionId + " -> "
                    + (s == null ? "(not found)" : s.action + "/" + s.status) + " = " + st);
            if (targets.contains(st)) return st;
            try { Thread.sleep(intervalSeconds * 1000L); } catch (InterruptedException ignored) {}
        } while (System.currentTimeMillis() < deadline);
        return st;
    }

    /**
     * Poll many labs in ONE wait window (one interval per round), so N labs acted on together
     * cost about one operation instead of N sequential waits.
     *
     * @param label short tag for the poll log line (e.g. "delete", "create")
     */
    public static void waitForAll(java.util.List<String> subscriptionIds,
                                  java.util.Set<com.nuvepro.moodle.pages.ManageLabs.LabState> targets,
                                  int maxSeconds, int intervalSeconds, String label) {
        if (subscriptionIds == null || subscriptionIds.isEmpty()) {
            throw new IllegalArgumentException("no lab ids to wait for");
        }
        if (ensureToken() == null) {
            throw new UnavailableException("CloudLabs API token not available (login failed or blocked)");
        }
        boolean deleteWait = targets.contains(com.nuvepro.moodle.pages.ManageLabs.LabState.DELETED);
        long deadline = System.currentTimeMillis() + maxSeconds * 1000L;
        int poll = 0;
        java.util.Map<String, String> last = new java.util.LinkedHashMap<>();
        while (System.currentTimeMillis() < deadline) {
            poll++;
            boolean allDone = true;
            for (String id : subscriptionIds) {
                if (id == null || id.isBlank()) continue;
                SubStatus s = getStatus(id);
                // A missing subscription means deleted; for any other target it is not yet ready.
                com.nuvepro.moodle.pages.ManageLabs.LabState st = (s == null)
                        ? (deleteWait ? com.nuvepro.moodle.pages.ManageLabs.LabState.DELETED
                                      : com.nuvepro.moodle.pages.ManageLabs.LabState.NOTCREATED)
                        : com.nuvepro.moodle.pages.ManageLabs.classifyActionStatus(s.action, s.status);
                last.put(id, s == null ? "gone=" + st : s.action + "/" + s.status + "=" + st);
                if (!targets.contains(st)) allDone = false;
            }
            System.out.println("[CloudLabs] parallel-" + label + " poll " + poll + " " + last);
            if (allDone) return;
            try { Thread.sleep(intervalSeconds * 1000L); } catch (InterruptedException ignored) {}
        }
        throw new AssertionError("labs did not all reach " + targets + " within "
                + maxSeconds + "s: " + last);
    }

    public static void waitForAllDeleted(java.util.List<String> subscriptionIds,
                                         int maxSeconds, int intervalSeconds) {
        waitForAll(subscriptionIds,
                java.util.EnumSet.of(com.nuvepro.moodle.pages.ManageLabs.LabState.DELETED),
                maxSeconds, intervalSeconds, "delete");
    }

    // ---- plumbing ----

    private static HttpResponse<String> post(String url, String formBody, String csrf) {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(url))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(formBody));
        if (csrf != null) b.header("X-CSRF-Token", csrf);
        try {
            return HTTP.send(b.build(), HttpResponse.BodyHandlers.ofString());
        } catch (Exception e) {
            throw new RuntimeException("CloudLabs POST " + url + " failed: " + e.getMessage(), e);
        }
    }

    private static String enc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }

    private static String snippet(String s) {
        if (s == null) return "";
        return s.length() > 160 ? s.substring(0, 160) : s;
    }

    private static HttpClient trustAll() {
        // A CookieManager is REQUIRED: login sets a session cookie (SSESS...) that must accompany
        // the X-CSRF-Token on extendDuration, else the API sees the user as "anonymous" (403).
        java.net.CookieManager cookies = new java.net.CookieManager();
        cookies.setCookiePolicy(java.net.CookiePolicy.ACCEPT_ALL);
        try {
            SSLContext ssl = SSLContext.getInstance("TLS");
            ssl.init(null, new TrustManager[]{new X509TrustManager() {
                public void checkClientTrusted(X509Certificate[] c, String a) {}
                public void checkServerTrusted(X509Certificate[] c, String a) {}
                public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
            }}, new SecureRandom());
            return HttpClient.newBuilder().sslContext(ssl).cookieHandler(cookies).build();
        } catch (Exception e) {
            return HttpClient.newBuilder().cookieHandler(cookies).build();
        }
    }
}
