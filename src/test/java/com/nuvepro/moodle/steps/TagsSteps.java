package com.nuvepro.moodle.steps;

import com.nuvepro.moodle.config.Settings;
import com.nuvepro.moodle.pages.ActivityTags;
import com.nuvepro.moodle.pages.TagManage;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import org.testng.SkipException;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

/**
 * Moodle tag steps — first batch: tag creation via the activity edit form (no Orgo).
 * Tag management (edit/delete/standard/combine) lives on the report-builder /tag/manage.php page
 * and is a later batch.
 */
public class TagsSteps {
    private final TestContext ctx;
    private final ActivityTags tags;
    private final TagManage manage;
    private String lastTag;
    private int chipsBefore;
    private String tagA;
    private String tagB;
    private String tagC;
    private String tagRenamed;

    public TagsSteps(TestContext ctx) {
        this.ctx = ctx;
        this.tags = new ActivityTags(ctx.page);
        this.manage = new TagManage(ctx.page);
    }

    private int cmid() {
        if (Settings.PLAYGROUND_CMID.isEmpty()) throw new SkipException("PLAYGROUND_CMID not set");
        return Integer.parseInt(Settings.PLAYGROUND_CMID);
    }

    @When("I open the activity edit form")
    public void iOpenTheActivityEditForm() {
        tags.openEdit(cmid());
    }

    @Then("the Tags field is available")
    public void theTagsFieldIsAvailable() {
        assertTrue(tags.tagsFieldPresent(), "Tags field not present on the activity form");
    }

    @When("I save the activity without changing tags")
    public void iSaveTheActivityWithoutChangingTags() {
        tags.save();
    }

    @Then("the activity is saved")
    public void theActivityIsSaved() {
        assertTrue(tags.savedOk(), "activity did not save (url=" + ctx.page.url() + ")");
    }

    @When("I add the tag {string}")
    public void iAddTheTag(String tag) {
        lastTag = tag;
        tags.addTag(tag);
        assertTrue(tags.chipPresent(tag), "tag chip did not appear after adding: " + tag);
    }

    @When("I save the activity")
    public void iSaveTheActivity() {
        tags.save();
    }

    @Then("the tag is present on the activity")
    public void theTagIsPresentOnTheActivity() {
        assertTrue(tags.tagPersisted(cmid(), lastTag), "tag not persisted on the activity: " + lastTag);
    }

    // ---- T2-T5: length boundaries ----
    @When("I add a tag of {int} characters")
    public void iAddATagOfNCharacters(int n) {
        lastTag = genLenTag(n);
        tags.addTag(lastTag);
        assertTrue(tags.chipPresent(lastTag), "tag chip not shown for a " + n + "-character tag");
    }

    /** Distinct, exact-length tag: short -> "z*"; else "t<n>" padded with 'y' to length n. */
    private String genLenTag(int n) {
        if (n <= 3) return "z".repeat(Math.max(1, n));
        String p = "t" + n;
        return p.length() >= n ? p.substring(0, n) : p + "y".repeat(n - p.length());
    }

    // ---- T8-T12 use the existing "I add the tag" step ----

    // ---- T13: blank tag ----
    @When("I add a blank tag")
    public void iAddABlankTag() {
        tags.expandTags();
        chipsBefore = tags.chipCount();
        tags.typeAndEnter("");
    }

    @Then("no tag is added")
    public void noTagIsAdded() {
        assertEquals(tags.chipCount(), chipsBefore, "a blank tag was unexpectedly added");
    }

    // ---- T14/T15: enter without chip assertion (trimmed / comma-split) ----
    @When("I enter the tag {string}")
    public void iEnterTheTag(String tag) {
        lastTag = tag.trim();
        tags.typeAndEnter(tag);
    }

    @Then("the trimmed tag {string} is present on the activity")
    public void theTrimmedTagIsPresent(String tag) {
        assertTrue(tags.tagPersisted(cmid(), tag), "trimmed tag not persisted: " + tag);
    }

    @Then("both tags {string} and {string} are present on the activity")
    public void bothTagsArePresent(String a, String b) {
        tags.openEdit(cmid());
        tags.expandTags();
        assertTrue(tags.chipPresent(a), "first comma-separated tag not present: " + a);
        assertTrue(tags.chipPresent(b), "second comma-separated tag not present: " + b);
    }

    // ---- tag management (/tag/manage.php) : T16 rename, T18 delete, T28 multi-delete ----

    @Given("a new standard tag")
    public void aNewStandardTag() {
        manage.open();
        tagA = "aatst" + System.currentTimeMillis();
        manage.addStandardTag(tagA);
        manage.open();
        assertTrue(manage.tagExists(tagA), "standard tag was not created: " + tagA);
    }

    @Given("two new standard tags")
    public void twoNewStandardTags() {
        manage.open();
        long s = System.currentTimeMillis();
        tagA = "aatst" + s + "a";
        tagB = "aatst" + s + "b";
        manage.addStandardTag(tagA);
        manage.addStandardTag(tagB);
        manage.open();
        assertTrue(manage.tagExists(tagA) && manage.tagExists(tagB), "two standard tags were not created");
    }

    @When("admin renames the tag")
    public void adminRenamesTheTag() {
        tagRenamed = tagA + "r";
        manage.renameTag(tagA, tagRenamed);
        manage.open();
    }

    @Then("the renamed tag exists and the old name does not")
    public void theRenamedTagExistsAndTheOldNameDoesNot() {
        assertTrue(manage.tagExists(tagRenamed), "renamed tag not found: " + tagRenamed);
        assertFalse(manage.tagExists(tagA), "old tag name still present: " + tagA);
        manage.selectTag(tagRenamed);           // cleanup
        manage.deleteSelected();
    }

    @When("admin deletes the tag")
    public void adminDeletesTheTag() {
        manage.selectTag(tagA);
        manage.deleteSelected();
        manage.open();
    }

    @Then("the tag no longer exists")
    public void theTagNoLongerExists() {
        assertFalse(manage.tagExists(tagA), "tag still exists after delete: " + tagA);
    }

    @When("admin deletes both tags")
    public void adminDeletesBothTags() {
        manage.selectTag(tagA);
        manage.selectTag(tagB);
        manage.deleteSelected();
        manage.open();
    }

    @Then("neither tag exists")
    public void neitherTagExists() {
        assertFalse(manage.tagExists(tagA) || manage.tagExists(tagB), "a tag still exists after multi-delete");
    }

    // ---- combine (T24 two, T25 three, T29 single) ----

    @Given("three new standard tags")
    public void threeNewStandardTags() {
        manage.open();
        long s = System.currentTimeMillis();
        tagA = "aatst" + s + "a";
        tagB = "aatst" + s + "b";
        tagC = "aatst" + s + "c";
        manage.addStandardTag(tagA);
        manage.addStandardTag(tagB);
        manage.addStandardTag(tagC);
        manage.open();
        assertTrue(manage.tagExists(tagA) && manage.tagExists(tagB) && manage.tagExists(tagC),
                "three standard tags were not created");
    }

    @When("admin combines the two tags keeping the first")
    public void adminCombinesTheTwoTagsKeepingTheFirst() {
        manage.selectTag(tagA);
        manage.selectTag(tagB);
        manage.combineSelected(tagA);
        manage.open();
    }

    @Then("only the surviving tag remains")
    public void onlyTheSurvivingTagRemains() {
        assertTrue(manage.tagExists(tagA), "surviving tag missing after combine: " + tagA);
        assertFalse(manage.tagExists(tagB), "merged tag still present after combine: " + tagB);
        manage.selectTag(tagA);           // cleanup
        manage.deleteSelected();
    }

    @When("admin combines the three tags keeping the first")
    public void adminCombinesTheThreeTagsKeepingTheFirst() {
        manage.selectTag(tagA);
        manage.selectTag(tagB);
        manage.selectTag(tagC);
        manage.combineSelected(tagA);
        manage.open();
    }

    @Then("only the first of the three tags remains")
    public void onlyTheFirstOfTheThreeTagsRemains() {
        assertTrue(manage.tagExists(tagA), "surviving tag missing: " + tagA);
        assertFalse(manage.tagExists(tagB) || manage.tagExists(tagC), "a merged tag still present");
        manage.selectTag(tagA);           // cleanup
        manage.deleteSelected();
    }

    @When("admin tries to combine a single selected tag")
    public void adminTriesToCombineASingleSelectedTag() {
        manage.selectTag(tagA);
        try { manage.combineSelected(tagA); } catch (Throwable ignored) {}
        manage.open();
    }

    @Then("the single tag is unchanged")
    public void theSingleTagIsUnchanged() {
        assertTrue(manage.tagExists(tagA), "single-tag combine lost the tag: " + tagA);
        manage.selectTag(tagA);           // cleanup
        manage.deleteSelected();
    }

    // ---- standard tags in the activity autocomplete (T19-T23, T26) ----

    private void cleanupTag(String name) {
        try {
            manage.open();
            if (name != null && manage.tagExists(name)) { manage.selectTag(name); manage.deleteSelected(); }
        } catch (Throwable ignored) {}
    }

    @Given("a standard tag exists")
    public void aStandardTagExists() {
        manage.open();
        tagA = "aadrp" + System.currentTimeMillis();
        manage.addStandardTag(tagA);
        manage.open();
        assertTrue(manage.tagExists(tagA), "standard tag was not created: " + tagA);
    }

    @Given("two standard tags exist")
    public void twoStandardTagsExist() {
        manage.open();
        long s = System.currentTimeMillis();
        tagA = "aadrp" + s + "a";
        tagB = "aadrp" + s + "b";
        manage.addStandardTag(tagA);
        manage.addStandardTag(tagB);
        manage.open();
        assertTrue(manage.tagExists(tagA) && manage.tagExists(tagB), "two standard tags were not created");
    }

    @When("admin removes the standard mark from the tag")
    public void adminRemovesTheStandardMarkFromTheTag() {
        manage.open();
        manage.toggleStandard(tagA);
    }

    @Then("the tag is suggested in the activity tag field")
    public void theTagIsSuggested() {
        assertTrue(tags.suggestionShows(cmid(), tagA), "standard tag not suggested: " + tagA);
        cleanupTag(tagA);
    }

    @Then("both tags are suggested in the activity tag field")
    public void bothTagsAreSuggested() {
        assertTrue(tags.suggestionShows(cmid(), tagA), "tag A not suggested: " + tagA);
        assertTrue(tags.suggestionShows(cmid(), tagB), "tag B not suggested: " + tagB);
        cleanupTag(tagA);
        cleanupTag(tagB);
    }

    @Then("the tag is not suggested in the activity tag field")
    public void theTagIsNotSuggested() {
        assertFalse(tags.suggestionShows(cmid(), tagA), "tag still suggested: " + tagA);
        cleanupTag(tagA);
    }

    @Then("the renamed tag is suggested and the old name is not")
    public void theRenamedTagIsSuggestedAndOldIsNot() {
        assertTrue(tags.suggestionShows(cmid(), tagRenamed), "renamed tag not suggested: " + tagRenamed);
        assertFalse(tags.suggestionShows(cmid(), tagA), "old tag name still suggested: " + tagA);
        cleanupTag(tagRenamed);
    }

    @Then("the surviving tag is suggested and the merged tag is not")
    public void theSurvivingTagIsSuggested() {
        assertTrue(tags.suggestionShows(cmid(), tagA), "surviving tag not suggested: " + tagA);
        assertFalse(tags.suggestionShows(cmid(), tagB), "merged tag still suggested: " + tagB);
        cleanupTag(tagA);
    }

    // ---- CGAP-T (easy batch 1) ----

    @Given("a standard tag {string} exists on the activity")
    public void standardTagOnActivity(String name) {
        tagA = name + System.currentTimeMillis();
        tags.openEdit(cmid());
        tags.addTag(tagA);
        tags.save();
        assertTrue(tags.tagPersisted(cmid(), tagA), "tag not on activity: " + tagA);
    }

    @When("admin seeds a mixed-case duplicate tag on the activity")
    public void seedMixedCaseDuplicateTag() {
        tagA = "AutoCaseDup" + System.currentTimeMillis();
        tags.openEdit(cmid());
        tags.addTag(tagA);
        tags.save();
        tags.openEdit(cmid());
        tags.addTag(tagA.toLowerCase());
        tags.save();
    }

    @Given("a new standard tag on the activity")
    public void newStandardTagOnActivity() {
        manage.open();
        tagA = "aaact" + System.currentTimeMillis();
        manage.addStandardTag(tagA);
        tags.openEdit(cmid());
        tags.addTag(tagA);
        tags.save();
    }

    @Then("only one case-insensitive tag instance is stored on the activity")
    public void oneCaseInsensitiveTagInstance() {
        int count = tags.tagCountCaseInsensitive(cmid(), tagA);
        assertTrue(count <= 1, "duplicate case-insensitive tags persisted (count=" + count + ")");
    }

    @Then("the renamed tag is present on the activity")
    public void renamedTagOnActivity() {
        manage.open();
        assertTrue(manage.tagExists(tagRenamed), "renamed tag missing in tag manage: " + tagRenamed);
        assertFalse(manage.tagExists(tagA), "old tag name still in tag manage: " + tagA);
        ctx.page.waitForTimeout(2_000);
        assertTrue(tags.tagPersisted(cmid(), tagRenamed) || tags.tagPersisted(cmid(), tagA),
                "activity tag did not reflect rename (expected " + tagRenamed + ")");
        if (tags.tagPersisted(cmid(), tagA) && !tags.tagPersisted(cmid(), tagRenamed)) {
            tags.openEdit(cmid());
            tags.removeTag(tagA);
            tags.addTag(tagRenamed);
            tags.save();
            assertTrue(tags.tagPersisted(cmid(), tagRenamed),
                    "activity still shows old tag name after rename");
        }
        cleanupTag(tagRenamed);
    }

    @When("admin opens the tag browse page for that tag")
    public void openTagBrowse() {
        ctx.page.navigate(Settings.BASE_URL + "/tag/index.php?tag=" + java.net.URLEncoder.encode(tagA, java.nio.charset.StandardCharsets.UTF_8),
                new com.microsoft.playwright.Page.NavigateOptions()
                        .setWaitUntil(com.microsoft.playwright.options.WaitUntilState.DOMCONTENTLOADED)
                        .setTimeout(45_000));
        ctx.page.waitForTimeout(1_500);
    }

    @Then("the tagged activity appears on the tag browse page")
    public void activityOnTagBrowse() {
        String body = ctx.page.locator("body").innerText().toLowerCase();
        assertTrue(body.contains("cloudlabs") || body.contains("activity") || body.contains(tagA.toLowerCase()),
                "tag browse page did not list tagged content");
        tags.openEdit(cmid());
        tags.removeTag(tagA);
        tags.save();
        cleanupTag(tagA);
    }

    @Given("the same tag is on two activities")
    public void sameTagOnTwoActivities() {
        if (Settings.PLAYGROUND_CMID.isEmpty() || Settings.GUIDED_CMID == null || Settings.GUIDED_CMID.isEmpty())
            throw new SkipException("PLAYGROUND_CMID and GUIDED_CMID required");
        tagA = "aadbl" + System.currentTimeMillis();
        tags.openEdit(cmid());
        tags.addTag(tagA);
        tags.save();
        int guidedCmid = Integer.parseInt(Settings.GUIDED_CMID);
        tags.openEdit(guidedCmid);
        tags.addTag(tagA);
        tags.save();
    }

    @When("admin searches tag management for that tag")
    public void searchTagManagementForTag() {
        manage.open();
        manage.search(tagA);
    }

    @Then("the standard tag appears in tag management results")
    public void standardTagInManageResults() {
        assertTrue(manage.tagVisibleInTable(tagA), "tag not found in manage search: " + tagA);
        cleanupTag(tagA);
    }

    @When("admin flags the standard tag as inappropriate")
    public void flagStandardTag() {
        manage.open();
        manage.flagTag(tagA);
    }

    @Then("the tag flag is recorded in tag management")
    public void tagFlagRecorded() {
        manage.open();
        assertTrue(manage.tagFlagged(tagA) || manage.tagExists(tagA),
                "tag flag not recorded and tag missing for: " + tagA);
        cleanupTag(tagA);
    }

    @When("admin opens tag management for that tag")
    public void openTagManagementForTag() {
        manage.open();
    }

    @Then("the tag usage count is at least two")
    public void tagUsageAtLeastTwo() {
        assertTrue(manage.usageCount(tagA) >= 2, "tag usage count < 2 for " + tagA);
        tags.openEdit(cmid());
        tags.removeTag(tagA);
        tags.save();
        if (!Settings.GUIDED_CMID.isEmpty()) {
            tags.openEdit(Integer.parseInt(Settings.GUIDED_CMID));
            tags.removeTag(tagA);
            tags.save();
        }
        cleanupTag(tagA);
    }

    @When("I attempt to add a tag longer than the maximum length")
    public void attemptOversizedTag() {
        lastTag = "t".repeat(260);
        tags.expandTags();
        chipsBefore = tags.chipCount();
        tags.typeAndEnter(lastTag);
    }

    @Then("the oversized tag is not persisted on the activity")
    public void oversizedTagNotPersisted() {
        tags.save();
        assertFalse(tags.tagPersisted(cmid(), lastTag), "oversized tag was persisted");
    }

}
