package com.nuvepro.moodle.helpers;

/**
 * One add-user form payload. Build with {@link #unique(String)} then override the field
 * under test ({@link #withPassword}, {@link #withEmail}, …). Never put live credentials here.
 */
public final class UserSpec {
    public final String username;
    public final String password;
    public final String firstName;
    public final String lastName;
    public final String email;

    public UserSpec(String username, String password, String firstName, String lastName, String email) {
        this.username = username;
        this.password = password;
        this.firstName = firstName;
        this.lastName = lastName;
        this.email = email;
    }

    /** Unique, policy-compliant user. Prefix distinguishes the case in Moodle (e.g. autoui, autoweak). */
    public static UserSpec unique(String prefix) {
        long stamp = System.currentTimeMillis();
        String username = prefix + "_" + stamp;
        return new UserSpec(username, strongPassword(stamp), "Auto", "UiTest", username + "@example.com");
    }

    public static String strongPassword(long stamp) {
        return "AutoT@" + stamp + "xZ";
    }

    public static String strongPassword() {
        return strongPassword(System.currentTimeMillis());
    }

    public UserSpec withUsername(String value) {
        return new UserSpec(value, password, firstName, lastName, email);
    }

    public UserSpec withPassword(String value) {
        return new UserSpec(username, value, firstName, lastName, email);
    }

    public UserSpec withEmail(String value) {
        return new UserSpec(username, password, firstName, lastName, value);
    }

    public UserSpec withName(String first, String last) {
        return new UserSpec(username, password, first, last, email);
    }
}
