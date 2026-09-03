package com.nuvepro.moodle.helpers;

import java.nio.file.Path;

/**
 * Payload for creating a cloudlabs assessment (challenge) activity.
 * Null / blank fields are left at Moodle defaults and are not asserted on read-back.
 * Add a new variant in {@link AssessmentTestData}.
 */
public final class AssessmentCreateSpec {
    public final String name;
    public final String adminName;
    public final String description;
    public final String instructions;
    public final Path labImage;
    public final String labType;
    public final Boolean exportPdf;
    public final String difficulty;
    public final String evaluationType;
    public final String testScriptUrl;
    /** SAME (default for Auto) or DIFFERENT — when DIFFERENT, per-test-case marks field is hidden. */
    public final String testCaseMarksMode;
    public final String eachTestCaseMarks;
    public final String challengeAnswer;
    public final String challengeMarks;
    public final String templateContains;
    public final Boolean parallelView;
    public final Boolean showStopLabButton;
    public final boolean enableStartDate;
    public final int startYear;
    public final int startMonth;
    public final int startDay;
    public final int dueYear;
    public final int dueMonth;
    public final int dueDay;
    /** &gt;0 set hours; 0 leave default (do not assert); -1 leave unlimited and assert off. */
    public final int timeLimitHours;
    /** When &gt;0, time limit is set in minutes (takes precedence over {@link #timeLimitHours}). */
    public final int timeLimitMinutes;
    /** Unique markers embedded in rich HTML description/instructions for student read-back tests. */
    public final String richContentMarker;
    public final Boolean proctored;
    public final String submissionsAllowed;
    public final String attemptsAllowed;
    public final Boolean allowAttemptAfterPassing;
    public final String cooldownDays;
    public final String actionAfterAttemptCompletes;
    public final Boolean emailAfterCompletion;
    public final String gradeToPass;
    public final String descriptionSource;
    public final String descriptionGitUrl;
    public final String contentSource;
    public final String contentGitUrl;
    public final Boolean randomization;
    public final Boolean allowLabCreateWhenDeleted;
    public final boolean skipAllowLabCreateWhenDeletedReadback;
    public final String whenTimeExpires;
    public final String durationBetweenAttempts;
    public final String attemptsAfterCooldown;
    /** &gt;0 enable and set hours; 0 leave default (do not assert). */
    public final int evaluationTimeoutHours;
    public final String evaluationTimeoutAction;
    /** Activity Help Settings: Show help video when activity opens. */
    public final Boolean showHelpVideo;
    /** Filled only when {@link #showHelpVideo} is true. */
    public final String helpVideoLink;
    public final boolean omitName;
    public final boolean omitTemplate;

    private AssessmentCreateSpec(Builder b) {
        this.name = b.name;
        this.adminName = b.adminName;
        this.description = b.description;
        this.instructions = b.instructions;
        this.labImage = b.labImage;
        this.labType = b.labType;
        this.exportPdf = b.exportPdf;
        this.difficulty = b.difficulty;
        this.evaluationType = b.evaluationType;
        this.testScriptUrl = b.testScriptUrl;
        this.testCaseMarksMode = b.testCaseMarksMode;
        this.eachTestCaseMarks = b.eachTestCaseMarks;
        this.challengeAnswer = b.challengeAnswer;
        this.challengeMarks = b.challengeMarks;
        this.templateContains = b.templateContains;
        this.parallelView = b.parallelView;
        this.showStopLabButton = b.showStopLabButton;
        this.enableStartDate = b.enableStartDate;
        this.startYear = b.startYear;
        this.startMonth = b.startMonth;
        this.startDay = b.startDay;
        this.dueYear = b.dueYear;
        this.dueMonth = b.dueMonth;
        this.dueDay = b.dueDay;
        this.timeLimitHours = b.timeLimitHours;
        this.timeLimitMinutes = b.timeLimitMinutes;
        this.richContentMarker = b.richContentMarker;
        this.proctored = b.proctored;
        this.submissionsAllowed = b.submissionsAllowed;
        this.attemptsAllowed = b.attemptsAllowed;
        this.allowAttemptAfterPassing = b.allowAttemptAfterPassing;
        this.cooldownDays = b.cooldownDays;
        this.actionAfterAttemptCompletes = b.actionAfterAttemptCompletes;
        this.emailAfterCompletion = b.emailAfterCompletion;
        this.gradeToPass = b.gradeToPass;
        this.descriptionSource = b.descriptionSource;
        this.descriptionGitUrl = b.descriptionGitUrl;
        this.contentSource = b.contentSource;
        this.contentGitUrl = b.contentGitUrl;
        this.randomization = b.randomization;
        this.allowLabCreateWhenDeleted = b.allowLabCreateWhenDeleted;
        this.skipAllowLabCreateWhenDeletedReadback = b.skipAllowLabCreateWhenDeletedReadback;
        this.whenTimeExpires = b.whenTimeExpires;
        this.durationBetweenAttempts = b.durationBetweenAttempts;
        this.attemptsAfterCooldown = b.attemptsAfterCooldown;
        this.evaluationTimeoutHours = b.evaluationTimeoutHours;
        this.evaluationTimeoutAction = b.evaluationTimeoutAction;
        this.showHelpVideo = b.showHelpVideo;
        this.helpVideoLink = b.helpVideoLink;
        this.omitName = b.omitName;
        this.omitTemplate = b.omitTemplate;
    }

    public static final class Builder {
        private String name;
        private String adminName = "";
        private String description = "";
        private String instructions = "";
        private Path labImage;
        private String labType = "challenge";
        private Boolean exportPdf;
        private String difficulty = "";
        private String evaluationType = "";
        private String testScriptUrl = "";
        private String testCaseMarksMode = "";
        private String eachTestCaseMarks = "";
        private String challengeAnswer = "";
        private String challengeMarks = "";
        private String templateContains = "";
        private Boolean parallelView;
        private Boolean showStopLabButton;
        private boolean enableStartDate;
        private int startYear;
        private int startMonth;
        private int startDay;
        private int dueYear;
        private int dueMonth;
        private int dueDay;
        private int timeLimitHours;
        private int timeLimitMinutes;
        private String richContentMarker = "";
        private Boolean proctored;
        private String submissionsAllowed = "";
        private String attemptsAllowed = "";
        private Boolean allowAttemptAfterPassing;
        private String cooldownDays = "";
        private String actionAfterAttemptCompletes = "";
        private Boolean emailAfterCompletion;
        private String gradeToPass = "";
        private String descriptionSource = "";
        private String descriptionGitUrl = "";
        private String contentSource = "";
        private String contentGitUrl = "";
        private Boolean randomization;
        private Boolean allowLabCreateWhenDeleted;
        private boolean skipAllowLabCreateWhenDeletedReadback;
        private String whenTimeExpires = "";
        private String durationBetweenAttempts = "";
        private String attemptsAfterCooldown = "";
        private int evaluationTimeoutHours;
        private String evaluationTimeoutAction = "";
        private Boolean showHelpVideo;
        private String helpVideoLink = "";
        private boolean omitName;
        private boolean omitTemplate;

        public Builder name(String v) { name = v; return this; }
        public Builder adminName(String v) { adminName = v; return this; }
        public Builder description(String v) { description = v; return this; }
        public Builder instructions(String v) { instructions = v; return this; }
        public Builder labImage(Path v) { labImage = v; return this; }
        public Builder labType(String v) { labType = v; return this; }
        public Builder exportPdf(boolean v) { exportPdf = v; return this; }
        public Builder difficulty(String v) { difficulty = v; return this; }
        public Builder evaluationType(String v) { evaluationType = v; return this; }
        public Builder testScriptUrl(String v) { testScriptUrl = v; return this; }
        public Builder testCaseMarksMode(String v) { testCaseMarksMode = v; return this; }
        public Builder eachTestCaseMarks(String v) { eachTestCaseMarks = v; return this; }
        public Builder challengeAnswer(String v) { challengeAnswer = v; return this; }
        public Builder challengeMarks(String v) { challengeMarks = v; return this; }
        public Builder templateContains(String v) { templateContains = v; return this; }
        public Builder parallelView(boolean v) { parallelView = v; return this; }
        public Builder showStopLabButton(boolean v) { showStopLabButton = v; return this; }
        public Builder enableStartDate(boolean v) { enableStartDate = v; return this; }
        public Builder start(int year, int month, int day) { startYear = year; startMonth = month; startDay = day; enableStartDate = true; return this; }
        public Builder due(int year, int month, int day) { dueYear = year; dueMonth = month; dueDay = day; return this; }
        public Builder timeLimitHours(int v) { timeLimitHours = v; return this; }
        public Builder timeLimitMinutes(int v) { timeLimitMinutes = v; return this; }
        public Builder richContentMarker(String v) { richContentMarker = v; return this; }
        public Builder proctored(boolean v) { proctored = v; return this; }
        public Builder submissionsAllowed(String v) { submissionsAllowed = v; return this; }
        public Builder attemptsAllowed(String v) { attemptsAllowed = v; return this; }
        public Builder allowAttemptAfterPassing(boolean v) { allowAttemptAfterPassing = v; return this; }
        public Builder cooldownDays(String v) { cooldownDays = v; return this; }
        public Builder actionAfterAttemptCompletes(String v) { actionAfterAttemptCompletes = v; return this; }
        public Builder emailAfterCompletion(boolean v) { emailAfterCompletion = v; return this; }
        public Builder gradeToPass(String v) { gradeToPass = v; return this; }
        public Builder descriptionSource(String v) { descriptionSource = v; return this; }
        public Builder descriptionGitUrl(String v) { descriptionGitUrl = v; return this; }
        public Builder contentSource(String v) { contentSource = v; return this; }
        public Builder contentGitUrl(String v) { contentGitUrl = v; return this; }
        public Builder randomization(boolean v) { randomization = v; return this; }
        public Builder allowLabCreateWhenDeleted(boolean v) { allowLabCreateWhenDeleted = v; return this; }
        public Builder skipAllowLabCreateWhenDeletedReadback(boolean v) { skipAllowLabCreateWhenDeletedReadback = v; return this; }
        public Builder whenTimeExpires(String v) { whenTimeExpires = v; return this; }
        public Builder durationBetweenAttempts(String v) { durationBetweenAttempts = v; return this; }
        public Builder attemptsAfterCooldown(String v) { attemptsAfterCooldown = v; return this; }
        public Builder evaluationTimeoutHours(int v) { evaluationTimeoutHours = v; return this; }
        public Builder evaluationTimeoutAction(String v) { evaluationTimeoutAction = v; return this; }
        public Builder showHelpVideo(boolean v) { showHelpVideo = v; return this; }
        public Builder helpVideoLink(String v) { helpVideoLink = v; return this; }
        public Builder omitName(boolean v) { omitName = v; return this; }
        public Builder omitTemplate(boolean v) { omitTemplate = v; return this; }

        public AssessmentCreateSpec build() { return new AssessmentCreateSpec(this); }
    }
}
