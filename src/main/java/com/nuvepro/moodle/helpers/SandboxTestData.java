package com.nuvepro.moodle.helpers;

/**
 * Named sandbox-create datasets. Lab type is always Moodle {@code playground} (product: Sandbox).
 */
public final class SandboxTestData {
    private SandboxTestData() {}

    /** Minimum fields Moodle needs to save a sandbox. */
    private static AssessmentCreateSpec.Builder baseline(String suffix) {
        return new AssessmentCreateSpec.Builder()
                .name("AUTO Sandbox " + suffix + " " + System.currentTimeMillis())
                .labType("playground")
                    .templateContains("aws.account");
    }

    public static final String READMORE_HEAD = "SB-READMORE-HEAD";
    public static final String READMORE_TAIL = "SB-READMORE-TAIL";

    private static String longReadMoreDescription() {
        StringBuilder sb = new StringBuilder();
        sb.append(READMORE_HEAD).append(" Unique sandbox description for Read More. ");
        for (int i = 1; i <= 40; i++) {
            sb.append("Paragraph ").append(i)
                    .append(" of the long sandbox description used to trigger the Read More control. ");
        }
        sb.append(READMORE_TAIL);
        return sb.toString();
    }

    public static AssessmentCreateSpec create(String dataset) {
        long stamp = System.currentTimeMillis();
        return switch (dataset) {
            // ---- @SB-create shared activities (stable names so registry re-runs match) ----
            case "sandbox_create_yes" -> new AssessmentCreateSpec.Builder()
                    .name("AUTO Sandbox create-yes")
                    .adminName("AUTO Sandbox Admin Yes")
                    .description("SB-CREATE-YES description for create suite verification.")
                    .labImage(AssessmentTestData.JAVA_LAB_PNG)
                    .labType("playground")
                    .templateContains("aws.account")
                    .exportPdf(true)
                    .parallelView(true)
                    .showHelpVideo(true)
                    .helpVideoLink(AssessmentTestData.HELP_VIDEO_LINK)
                    .build();
            case "sandbox_create_no" -> new AssessmentCreateSpec.Builder()
                    .name("AUTO Sandbox create-no")
                    .description("SB-CREATE-NO PDF Parallel Help all No.")
                    .labType("playground")
                    .templateContains("aws.account")
                    .exportPdf(false)
                    .parallelView(false)
                    .showHelpVideo(false)
                    .build();
            case "sandbox_create_edit" -> new AssessmentCreateSpec.Builder()
                    .name("AUTO Sandbox create-edit")
                    .description("SB-CREATE-EDIT baseline before edit+save.")
                    .labType("playground")
                    .templateContains("aws.account")
                    .build();
            case "sandbox_edit_update" -> new AssessmentCreateSpec.Builder()
                    .name("AUTO Sandbox create-edited")
                    .adminName("AUTO Sandbox Edited Admin")
                    .description("Updated sandbox description after edit and save.")
                    .labType("playground")
                    .templateContains("aws.account")
                    .exportPdf(false)
                    .parallelView(true)
                    .showHelpVideo(false)
                    .build();
            case "sandbox_desc_git" -> new AssessmentCreateSpec.Builder()
                    .name("AUTO Sandbox create-git")
                    .labType("playground")
                    .templateContains("aws.account")
                    .descriptionSource("git")
                    .descriptionGitUrl(AssessmentTestData.GIT_INSTRUCTIONS_URL)
                    .build();
            case "sandbox_missing_name" -> baseline("missing-name").omitName(true).build();
            case "sandbox_missing_template" -> baseline("missing-tpl").omitTemplate(true).build();
            // Legacy aliases kept for any old runners / docs
            case "sandbox_basic" -> baseline("basic").build();
            case "sandbox_general" -> create("sandbox_create_yes");
            case "sandbox_pdf_no", "sandbox_parallel_no", "sandbox_help_video_no" -> create("sandbox_create_no");
            case "sandbox_admin_name" -> create("sandbox_create_yes");
            // Student user-flow datasets
            case "sandbox_user_details" -> baseline("user-details")
                    .adminName("AUTO Sandbox UserAdmin " + stamp)
                    .description("SB-USER-DESC-MARKER " + stamp
                            + " sandbox landing description for student detail verification.")
                    .build();
            case "sandbox_user_lab" -> baseline("user-lab")
                    .adminName("AUTO Sandbox UserLab Admin")
                    .description("SB-USER-LAB description for student console Instructions verification.")
                    .exportPdf(true)
                    .build();
            case "sandbox_user_desc_git" -> baseline("user-desc-git")
                    .descriptionSource("git")
                    .descriptionGitUrl(AssessmentTestData.GIT_INSTRUCTIONS_URL)
                    .build();
            case "sandbox_long_desc" -> baseline("long-desc")
                    .description(longReadMoreDescription())
                    .exportPdf(true)
                    .build();
            case "sandbox_invalid_gcp_org" -> baseline("invalid-gcp-org")
                    .description("SB-INVALID-GCP-ORG template for missing-required-argument error.")
                    .templateContains("gcp.organization.account")
                    .build();
            case "sandbox_vm_vpat" -> baseline("vm-vpat")
                    .description("SB-VM-VPAT Ubuntu Desktop iframe lab.")
                    .templateContains("Ubuntu.Desktop_VPAT")
                    .build();
            case "sandbox_catalog_unlimited" -> baseline("catalog-unlimited")
                    .description("SB-CATALOG unlimited duration catalog UI lab.")
                    .templateContains("unlimited duration")
                    .build();
            default -> throw new IllegalArgumentException("Unknown sandbox create dataset: " + dataset);
        };
    }

    /**
     * Preferred registry keys when a scenario asks for {@code dataset}.
     * First existing entry wins — so {@code @SB-user} can reuse activities already made by {@code @SB-create}.
     */
    public static String[] reuseCandidates(String dataset) {
        if (dataset == null || dataset.isBlank()) return new String[0];
        return switch (dataset.trim()) {
            case "sandbox_user_details" -> new String[]{
                    "sandbox_create_yes", "sandbox_user_details", "sandbox_general", "sandbox_admin_name"};
            case "sandbox_user_lab" -> new String[]{
                    "sandbox_create_yes", "sandbox_create_no", "sandbox_create_edit",
                    "sandbox_user_lab", "sandbox_basic"};
            case "sandbox_user_desc_git" -> new String[]{
                    "sandbox_desc_git", "sandbox_user_desc_git"};
            case "sandbox_long_desc", "sandbox_invalid_gcp_org",
                 "sandbox_vm_vpat", "sandbox_catalog_unlimited" -> new String[]{dataset.trim()};
            // create-suite keys prefer themselves only
            case "sandbox_create_yes", "sandbox_create_no", "sandbox_create_edit", "sandbox_desc_git",
                 "sandbox_basic", "sandbox_general", "sandbox_admin_name" -> new String[]{dataset.trim()};
            default -> new String[]{dataset.trim()};
        };
    }
}
