package com.nuvepro.moodle.helpers;

/**
 * Named datasets for user cases (same pattern as {@link AssessmentTestData}).
 * To add a create-form variant:
 * 1. Add a {@code case} in {@link #createForm(String, ApiClient.SeededUser)}
 * 2. Add a tagged scenario / Examples row in {@code moodle_users.feature}
 *    with the next sequential {@code @U-user-n} / {@code TC-n} and the Kiwi tag
 *    ({@code @U4}, {@code @CGAP-U-1}, …).
 */
public final class UserTestData {
    private UserTestData() {}

    public static final String WEAK_PASSWORD = "abc";
    public static final String INVALID_EMAIL = "notanemail";
    public static final String BAD_LOGIN_PASSWORD = "WrongPass#123";
    public static final String PROFILE_CITY_PREFIX = "AutoCity";
    public static final String COHORT_PREFIX = "AutoCohort";
    public static final String COHORT_ASSIGN_PREFIX = "AutoCohAssign";

    public static String uniqueLabel(String prefix) {
        return prefix + System.currentTimeMillis();
    }

    public static String uniqueEmail(String prefix) {
        return prefix + System.currentTimeMillis() + "@example.com";
    }

    /**
     * @param dataset  name from the feature Examples table
     * @param existing registered user when the case needs a duplicate; ignored otherwise
     * @return payload to fill, or {@code null} for the empty-form case
     */
    public static UserSpec createForm(String dataset, ApiClient.SeededUser existing) {
        switch (dataset) {
            case "empty":
                return null;
            case "valid":
                return UserSpec.unique("autoui");
            case "weak_password":
                return UserSpec.unique("autoweak").withPassword(WEAK_PASSWORD).withName("Weak", "Pass");
            case "invalid_email":
                return UserSpec.unique("autobad").withEmail(INVALID_EMAIL).withName("Bad", "Email");
            case "duplicate_username":
                requireExisting(dataset, existing);
                return UserSpec.unique("autodup").withUsername(existing.username).withName("Dup", "User");
            case "duplicate_email":
                requireExisting(dataset, existing);
                return UserSpec.unique("autodupe").withEmail(existing.email).withName("Dup", "Email");
            case "force_password_change":
                return UserSpec.unique("autofpc").withName("Force", "Pwd");
            case "uppercase_username":
                return UserSpec.unique("autoupper")
                        .withUsername("AutoUser_" + System.currentTimeMillis())
                        .withName("Upper", "Case");
            case "spaces_username":
                return UserSpec.unique("autospaces")
                        .withUsername("auto user " + System.currentTimeMillis())
                        .withName("Space", "Name");
            default:
                throw new IllegalArgumentException(
                        "Unknown user dataset '" + dataset + "'. Add it in UserTestData.createForm().");
        }
    }

    private static void requireExisting(String dataset, ApiClient.SeededUser existing) {
        if (existing == null) {
            throw new IllegalStateException("dataset '" + dataset + "' needs Given a registered user first");
        }
    }
}
