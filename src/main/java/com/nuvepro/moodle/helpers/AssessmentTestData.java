package com.nuvepro.moodle.helpers;

import java.nio.file.Path;

/**
 * Named assessment-create datasets. To add another create case:
 * 1. Add a {@code case} here
 * 2. Add a tagged scenario in assessment_create.feature using that dataset name
 */
public final class AssessmentTestData {
    private AssessmentTestData() {}

    public static final Path JAVA_LAB_PNG = Path.of("src/test/resources/fixtures/java-lab.png");
    public static final String SCRIPT_URL =
            "https://cygnus.nuvepro.com/repos/Products/Droplets/Main/Dev/Challenges/AWS/aws.clab.testio/AWS_SAA/S3_Life_Cycle/scripts/newTemplate/scripts/";
    public static final String GIT_INSTRUCTIONS_URL =
            "https://github.com/Nuvepro-Technologies-Pvt-Ltd/Infosys-GCP-Trial-Assessment_instructions/blob/main/instructions.md";
    /** Help Video Link value when Show help video = Yes (Activity Help Settings). */
    public static final String HELP_VIDEO_LINK =
            "https://youtu.be/eIrMbAQSU34?si=FXPE3p3MhWNv-KJ1";
    /** Stable video id used for soft matching if Moodle normalizes the YouTube URL. */
    public static final String HELP_VIDEO_ID = "eIrMbAQSU34";
    /** Account VPAT sandbox used for student user-flow provisioning (@A-user-1). */
    public static final String LAB_TEMPLATE_VPAT = "aws.Account.Clab_ACCOUNT_VPAT Testing";

    /** Minimum fields Moodle needs to save an assessment. */
    private static AssessmentCreateSpec.Builder baseline(String suffix) {
        return new AssessmentCreateSpec.Builder()
                .name("AUTO Assess " + suffix + " " + System.currentTimeMillis())
                .labType("challenge")
                .templateContains("aws.account.clab ELB")
                .gradeToPass("30");
    }

    public static AssessmentCreateSpec create(String dataset) {
        long stamp = System.currentTimeMillis();
        switch (dataset) {
            case "full_config":
                return new AssessmentCreateSpec.Builder()
                        .name("AUTO Assess full " + stamp)
                        .adminName("AUTO Admin name " + stamp)
                        .description("Auto-created assessment with full configuration")
                        .instructions("Complete the AWS S3 lifecycle tasks. Follow every test case in the script.")
                        .labImage(JAVA_LAB_PNG)
                        .labType("challenge")
                        .exportPdf(true)
                        .difficulty("Advanced")
                        .evaluationType("AUTO")
                        .testScriptUrl(SCRIPT_URL)
                        .eachTestCaseMarks("10")
                        .templateContains("aws.account.clab ELB")
                        .parallelView(true)
                        .showStopLabButton(true)
                        .enableStartDate(true)
                        .due(2027, 6, 20)
                        .timeLimitHours(2)
                        .proctored(true)
                        .submissionsAllowed("2")
                        .attemptsAllowed("2")
                        .allowAttemptAfterPassing(true)
                        .cooldownDays("2")
                        .actionAfterAttemptCompletes("Stop")
                        .emailAfterCompletion(true)
                        .gradeToPass("30")
                        .build();
            case "general":
                return baseline("general")
                        .adminName("AUTO Admin name " + stamp)
                        .description("General details create")
                        .instructions("Assessment instructions for the general create case")
                        .labImage(JAVA_LAB_PNG)
                        .exportPdf(true)
                        .difficulty("Advanced")
                        .build();
            case "pdf_no":
                return baseline("pdf-no").exportPdf(false).build();
            case "difficulty_beginner":
                return baseline("diff-beginner").difficulty("Beginner").build();
            case "difficulty_intermediate":
                return baseline("diff-intermediate").difficulty("Intermediate").build();
            case "difficulty_advanced":
                return baseline("diff-advanced").difficulty("Advanced").build();
            case "difficulty_expert":
                return baseline("diff-expert").difficulty("Expert").build();
            case "eval_auto":
                return baseline("eval-auto")
                        .templateContains(LAB_TEMPLATE_VPAT)
                        .evaluationType("AUTO")
                        .testScriptUrl(SCRIPT_URL)
                        .eachTestCaseMarks("10")
                        .build();
            case "eval_auto_stop_lab":
                return baseline("eval-auto-stop")
                        .templateContains(LAB_TEMPLATE_VPAT)
                        .evaluationType("AUTO")
                        .testScriptUrl(SCRIPT_URL)
                        .eachTestCaseMarks("10")
                        .showStopLabButton(true)
                        .build();
            case "eval_auto_pdf":
                return baseline("eval-auto-pdf")
                        .templateContains(LAB_TEMPLATE_VPAT)
                        .evaluationType("AUTO")
                        .testScriptUrl(SCRIPT_URL)
                        .eachTestCaseMarks("10")
                        .exportPdf(true)
                        .instructions("Auto PDF instructions: download this content as a PDF from the lab console.")
                        .build();
            case "eval_answer":
                return baseline("eval-answer")
                        .evaluationType("ANSWER")
                        .challengeAnswer("Auto create answer text")
                        .challengeMarks("10")
                        .build();
            case "eval_manual":
                return baseline("eval-manual").evaluationType("MANUAL").build();
            case "lab_yes":
                return baseline("lab-yes").parallelView(true).showStopLabButton(true).build();
            case "lab_no":
                return baseline("lab-no").parallelView(false).showStopLabButton(false).build();
            case "dates":
                return baseline("dates").enableStartDate(true).due(2027, 6, 20).build();
            case "future_start":
                return baseline("future-start")
                        .templateContains(LAB_TEMPLATE_VPAT)
                        .evaluationType("AUTO")
                        .testScriptUrl(SCRIPT_URL)
                        .eachTestCaseMarks("10")
                        .start(2027, 6, 20)
                        .due(2027, 6, 21)
                        .build();
            case "timelimit":
                return baseline("timelimit").timeLimitHours(2).build();
            case "no_timelimit":
                return baseline("no-timelimit").timeLimitHours(-1).build();
            case "proctored_yes":
                // Moodle rejects proctoring unless start date, due date, and time limit are set.
                return baseline("proctored-yes")
                        .enableStartDate(true)
                        .due(2027, 6, 20)
                        .timeLimitHours(2)
                        .proctored(true)
                        .build();
            case "proctored_no":
                return baseline("proctored-no").proctored(false).build();
            case "attempts":
                return baseline("attempts")
                        .submissionsAllowed("2")
                        .attemptsAllowed("2")
                        .cooldownDays("2")
                        .build();
            case "eval_auto_attempts":
                return baseline("eval-auto-attempts")
                        .templateContains(LAB_TEMPLATE_VPAT)
                        .evaluationType("AUTO")
                        .testScriptUrl(SCRIPT_URL)
                        .eachTestCaseMarks("10")
                        .submissionsAllowed("2")
                        .attemptsAllowed("2")
                        .build();
            case "autosubmit_3min":
                return baseline("auto-3min")
                        .templateContains(LAB_TEMPLATE_VPAT)
                        .evaluationType("AUTO")
                        .testScriptUrl(SCRIPT_URL)
                        .eachTestCaseMarks("10")
                        .timeLimitMinutes(3)
                        .whenTimeExpires("autosubmit")
                        .build();
            case "autosubmit_3min_attempts":
                return baseline("auto-3min-multi")
                        .templateContains(LAB_TEMPLATE_VPAT)
                        .evaluationType("AUTO")
                        .testScriptUrl(SCRIPT_URL)
                        .eachTestCaseMarks("10")
                        .timeLimitMinutes(3)
                        .whenTimeExpires("autosubmit")
                        .submissionsAllowed("1")
                        .attemptsAllowed("2")
                        .build();
            case "userflow_full_config":
                return new AssessmentCreateSpec.Builder()
                        .name("AUTO Assess userflow-full " + stamp)
                        .adminName("AUTO Admin name " + stamp)
                        .description("Auto-created assessment with full configuration for user-flow checks")
                        .instructions("Complete every task in the assessment instructions panel.")
                        .labImage(JAVA_LAB_PNG)
                        .labType("challenge")
                        .exportPdf(true)
                        .difficulty("Advanced")
                        .evaluationType("AUTO")
                        .testScriptUrl(SCRIPT_URL)
                        .eachTestCaseMarks("10")
                        .templateContains(LAB_TEMPLATE_VPAT)
                        .parallelView(true)
                        .showStopLabButton(true)
                        .enableStartDate(true)
                        .due(2027, 6, 20)
                        .timeLimitHours(2)
                        .proctored(true)
                        .submissionsAllowed("2")
                        .attemptsAllowed("2")
                        .allowAttemptAfterPassing(true)
                        .cooldownDays("2")
                        .actionAfterAttemptCompletes("Stop")
                        .emailAfterCompletion(true)
                        .gradeToPass("30")
                        .build();
            case "rich_content": {
                String marker = "AUTO-RICH-CONTENT-" + stamp;
                StringBuilder body = new StringBuilder();
                body.append("<p><strong>").append(marker).append("</strong></p>");
                for (int i = 1; i <= 24; i++) {
                    body.append("<p>Auto rich-content paragraph ").append(i)
                            .append(": detailed assessment description filler for read-more and layout checks.</p>");
                }
                body.append("<p><img src=\"/mod/cloudlabs/pix/challenge.png\" alt=\"auto lab icon\" width=\"120\"/></p>");
                StringBuilder instr = new StringBuilder();
                instr.append("<p><strong>").append(marker).append("-INSTRUCTIONS</strong></p>");
                for (int i = 1; i <= 16; i++) {
                    instr.append("<p>Instruction block ").append(i)
                            .append(": follow each step carefully before submitting the lab attempt.</p>");
                }
                instr.append("<p><img src=\"/mod/cloudlabs/pix/challenge.png\" alt=\"instruction icon\" width=\"100\"/></p>");
                return baseline("rich-content")
                        .templateContains(LAB_TEMPLATE_VPAT)
                        .evaluationType("AUTO")
                        .testScriptUrl(SCRIPT_URL)
                        .eachTestCaseMarks("10")
                        .description(body.toString())
                        .instructions(instr.toString())
                        .richContentMarker(marker)
                        .build();
            }
            case "action_stop":
                return baseline("action-stop").actionAfterAttemptCompletes("Stop").build();
            case "action_park":
                return baseline("action-park").actionAfterAttemptCompletes("Park").build();
            case "action_delete":
                return baseline("action-delete").actionAfterAttemptCompletes("Delete").build();
            case "email_yes":
                // sendassessmentresult is hideIf when evaluationtype != AUTO (disabled fields do not save).
                return baseline("email-yes")
                        .evaluationType("AUTO")
                        .testScriptUrl(SCRIPT_URL)
                        .eachTestCaseMarks("10")
                        .emailAfterCompletion(true)
                        .build();
            case "email_no":
                return baseline("email-no")
                        .evaluationType("AUTO")
                        .testScriptUrl(SCRIPT_URL)
                        .eachTestCaseMarks("10")
                        .emailAfterCompletion(false)
                        .build();
            case "grade":
                return baseline("grade").gradeToPass("30").build();
            case "desc_git":
                return baseline("desc-git")
                        .descriptionSource("git")
                        .descriptionGitUrl(GIT_INSTRUCTIONS_URL)
                        .build();
            case "content_git":
                return baseline("content-git")
                        .randomization(false)
                        .contentSource("git")
                        .contentGitUrl(GIT_INSTRUCTIONS_URL)
                        .build();
            case "randomization_yes":
                return baseline("rand-yes")
                        .omitTemplate(true)
                        .randomization(true)
                        .build();
            case "overdue_autosubmit":
                return baseline("overdue-auto")
                        .timeLimitHours(2)
                        .whenTimeExpires("autosubmit")
                        .build();
            case "overdue_remove":
                return baseline("overdue-remove")
                        .timeLimitHours(2)
                        .whenTimeExpires("removelabaccess")
                        .build();
            case "attempts_after_cooldown":
                return baseline("after-cd")
                        .cooldownDays("2")
                        .attemptsAfterCooldown("2")
                        .build();
            case "eval_timeout":
                return baseline("eval-timeout")
                        .evaluationType("AUTO")
                        .testScriptUrl(SCRIPT_URL)
                        .eachTestCaseMarks("10")
                        .evaluationTimeoutHours(1)
                        .evaluationTimeoutAction("Park")
                        .build();
            case "missing_name":
                return baseline("missing-name").omitName(true).build();
            case "missing_template":
                return baseline("missing-tpl").omitTemplate(true).build();
            case "stop_lab_yes":
                return baseline("stop-lab-yes").showStopLabButton(true).build();
            case "stop_lab_no":
                return baseline("stop-lab-no").showStopLabButton(false).build();
            case "attempts_after_passing_yes":
                return baseline("after-pass-yes")
                        .submissionsAllowed("2")
                        .attemptsAllowed("2")
                        .allowAttemptAfterPassing(true)
                        .build();
            case "attempts_after_passing_no":
                return baseline("after-pass-no")
                        .submissionsAllowed("2")
                        .attemptsAllowed("2")
                        .allowAttemptAfterPassing(false)
                        .build();
            case "help_video_yes":
                return baseline("help-video-yes")
                        .showHelpVideo(true)
                        .helpVideoLink(HELP_VIDEO_LINK)
                        .build();
            case "help_video_no":
                return baseline("help-video-no").showHelpVideo(false).build();
            case "action_none":
                return baseline("action-none").actionAfterAttemptCompletes("none").build();
            case "marks_different":
                return baseline("marks-diff")
                        .evaluationType("AUTO")
                        .testScriptUrl(SCRIPT_URL)
                        .testCaseMarksMode("DIFFERENT")
                        .build();
            case "randomization_no":
                return baseline("rand-no").randomization(false).build();
            case "grade_1":
                // Boundary: lowest valid grade (plugin rejects <= 0)
                return baseline("grade-1").gradeToPass("1").build();
            case "grade_100":
                // Boundary: common max / upper typical value
                return baseline("grade-100").gradeToPass("100").build();
            case "grade_0":
                // Negative/boundary: plugin validation rejects gradepass <= 0
                return baseline("grade-0").gradeToPass("0").build();
            case "grade_negative":
                // Negative: below zero must be rejected
                return baseline("grade-neg").gradeToPass("-1").build();
            default:
                throw new IllegalArgumentException(
                        "Unknown assessment create dataset '" + dataset + "'. Add it in AssessmentTestData.create().");
        }
    }
}
