package com.nuvepro.moodle.pages;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.WaitForSelectorState;
import java.util.Locale;

/**
 * Moodle ↔ CloudLabs plugin integration smoke: Cloud Server Settings, Lab Templates admin,
 * activity chooser, and report plugin listing. Read-only admin smoke.
 */
public class MoodleCloudlabsIntegrationPage extends BasePage {

    public static final String CLOUD_SERVER_SETTINGS =
            "/admin/settings.php?section=modcloudlabsgeneralsettings";
    public static final String REPORTS_INDEX = "/report/index.php";
    public static final String SITE_ADMIN_HOME = "/admin/index.php";
    public static final String PLUGIN_OVERVIEW = "/admin/plugins.php";
    public static final String PLUGIN_OVERVIEW_ADDITIONAL = "/admin/plugins.php?filter=additional";

    public MoodleCloudlabsIntegrationPage(Page page) {
        super(page);
    }

    /**
     * Site administration → Plugins → Plugins overview → Additional plugins (admin UI path;
     * falls back to direct URLs if the tree is collapsed or restyled).
     */
    public void openSiteAdminPluginOverviewAdditionalPlugins() {
        navigate(SITE_ADMIN_HOME);
        page.waitForTimeout(1_500);
        if (!clickAdminTreeLinkToPluginOverview()) {
            navigate(PLUGIN_OVERVIEW);
        }
        waitForPluginOverviewMain();
        if (!selectAdditionalPluginsFilter()) {
            navigate(PLUGIN_OVERVIEW_ADDITIONAL);
        }
        waitForPluginOverviewMain();
        System.out.println("[MCI-1] Plugin overview (additional) url=" + page.url());
    }

    private void waitForPluginOverviewMain() {
        page.locator("#region-main, table, .pluginname, .plugintypeheader").first()
                .waitFor(new Locator.WaitForOptions()
                        .setState(WaitForSelectorState.VISIBLE)
                        .setTimeout(30_000));
        page.waitForTimeout(800);
    }

    private boolean clickAdminTreeLinkToPluginOverview() {
        Locator overview = page.locator(
                "a[href*='admin/plugins.php']:not([href*='installplugins']):not([href*='install.php'])");
        Locator overviewByLabel = page.locator("a[href*='admin/plugins.php']").filter(new Locator.FilterOptions()
                .setHasText(java.util.regex.Pattern.compile("Plugins overview|Plugin overview",
                        java.util.regex.Pattern.CASE_INSENSITIVE)));
        if (overviewByLabel.count() > 0) {
            try {
                overviewByLabel.first().click();
                page.waitForTimeout(1_500);
                return page.url().contains("plugins.php");
            } catch (RuntimeException ignored) {}
        }
        Locator pluginsSection = page.locator(
                "a:has-text('Plugins'), [data-key='plugins'], .categoryname:has-text('Plugins')");
        for (int i = 0; i < pluginsSection.count(); i++) {
            try {
                if (pluginsSection.nth(i).isVisible()) {
                    pluginsSection.nth(i).click();
                    page.waitForTimeout(600);
                }
            } catch (RuntimeException ignored) {}
        }
        if (overview.count() > 0) {
            try {
                overview.first().click();
                page.waitForTimeout(1_500);
                return page.url().contains("plugins.php");
            } catch (RuntimeException ignored) {}
        }
        return false;
    }

    private boolean selectAdditionalPluginsFilter() {
        Locator additional = page.locator(
                "a[href*='filter=additional'], a[href*='filter=additionalplugins'], "
                        + ".nav-link:has-text('Additional plugins'), "
                        + "a:has-text('Additional plugins'), button:has-text('Additional plugins')");
        for (int i = 0; i < additional.count(); i++) {
            try {
                if (additional.nth(i).isVisible()) {
                    additional.nth(i).click();
                    page.waitForTimeout(1_200);
                    return page.url().contains("filter=additional")
                            || page.locator("#region-main").innerText().toLowerCase(Locale.ROOT)
                            .contains("additional");
                }
            } catch (RuntimeException ignored) {}
        }
        Locator filterSelect = page.locator("select#filter, select[name='filter']");
        if (filterSelect.count() > 0) {
            try {
                filterSelect.first().selectOption("additional");
                page.waitForTimeout(1_200);
                return true;
            } catch (RuntimeException ignored) {}
        }
        return page.url().contains("filter=additional");
    }

    /** Result of scanning the plugin overview for {@code mod_cloudlabs} / Nuvepro Cloudlabs. */
    public static final class NuveproCloudlabsPluginStatus {
        public final boolean listed;
        public final boolean enabled;
        public final String detail;

        NuveproCloudlabsPluginStatus(boolean listed, boolean enabled, String detail) {
            this.listed = listed;
            this.enabled = enabled;
            this.detail = detail;
        }
    }

    public NuveproCloudlabsPluginStatus nuveproCloudlabsPluginOnCurrentOverviewPage() {
        Object raw = page.evaluate("""
                () => {
                  const norm = (s) => (s || '').toLowerCase().replace(/\\s+/g, ' ').trim();
                  const names = ['nuvepro cloudlabs', 'nuvepro cloud labs', 'cloudlabs activity'];
                  const matchesName = (text) => {
                    const t = norm(text);
                    if (t.includes('mod_cloudlabs')) return true;
                    return names.some(n => t.includes(n));
                  };
                  const rowFor = () => {
                    const byData = document.querySelector('[data-pluginname="mod_cloudlabs"]');
                    if (byData) return byData.closest('tr') || byData;
                    for (const a of document.querySelectorAll('a[href*="mod_cloudlabs"]')) {
                      const tr = a.closest('tr');
                      if (tr) return tr;
                    }
                    for (const tr of document.querySelectorAll('tr')) {
                      if (matchesName(tr.innerText)) return tr;
                    }
                    for (const el of document.querySelectorAll('li, .pluginentry, .pluginname')) {
                      if (matchesName(el.innerText)) return el;
                    }
                    return null;
                  };
                  const row = rowFor();
                  if (!row) {
                    return { listed: false, enabled: false,
                      detail: 'Nuvepro Cloudlabs / mod_cloudlabs not found on this overview page' };
                  }
                  const rowText = norm(row.innerText);
                  const disable = row.querySelector('a[href*="disable"], a[href*="disable.php"]');
                  const enable = row.querySelector('a[href*="enable"], a[href*="enable.php"]');
                  const disableHref = disable ? (disable.getAttribute('href') || '') : '';
                  const enableHref = enable ? (enable.getAttribute('href') || '') : '';
                  const forCloudlabs = (href) => href.includes('mod_cloudlabs') || href.includes('cloudlabs');
                  let enabled = false;
                  if (disable && (forCloudlabs(disableHref) || !enable)) enabled = true;
                  if (enable && forCloudlabs(enableHref) && !disable) enabled = false;
                  if (rowText.includes('enabled') && !rowText.includes('disabled')) enabled = true;
                  if (row.querySelector('.icon.text-success, .fa-check, img[alt*="enabled"]')) enabled = true;
                  return {
                    listed: true,
                    enabled,
                    detail: 'row=' + rowText.substring(0, Math.min(120, rowText.length))
                  };
                }
                """);
        if (!(raw instanceof java.util.Map<?, ?> map)) {
            return new NuveproCloudlabsPluginStatus(false, false, "could not parse plugin overview");
        }
        boolean listed = Boolean.TRUE.equals(map.get("listed"));
        boolean enabled = Boolean.TRUE.equals(map.get("enabled"));
        Object detail = map.get("detail");
        return new NuveproCloudlabsPluginStatus(listed, enabled,
                detail == null ? "" : String.valueOf(detail));
    }

    /**
     * Some sites list {@code mod_cloudlabs} under Activity modules rather than Additional plugins;
     * try that filter only when Additional is empty (still requires plugin overview navigation first).
     */
    public void openPluginOverviewActivityModulesIfNeeded() {
        if (nuveproCloudlabsPluginOnCurrentOverviewPage().listed) {
            return;
        }
        navigate("/admin/plugins.php?filter=activitymodules");
        waitForPluginOverviewMain();
        System.out.println("[MCI-1] Nuvepro Cloudlabs not on Additional — trying Activity modules filter");
    }

    public void openCloudServerSettings() {
        navigate(CLOUD_SERVER_SETTINGS);
        page.waitForTimeout(2_000);
    }

    public boolean onCloudServerSettings() {
        String url = page.url().toLowerCase(Locale.ROOT);
        return url.contains("modcloudlabsgeneralsettings")
                && page.locator("#id_s_cloudlabs_serverurl, [name='s_cloudlabs_serverurl']").count() > 0;
    }

    public String settingsRegionText() {
        Locator region = page.locator("#region-main, #adminsettings, form.mform").first();
        return region.innerText().toLowerCase(Locale.ROOT);
    }

    /** Moodle admin setting labels (col-form-label / fitem). */
    public boolean settingsLabelMentions(String... fragments) {
        String labels = page.locator(".form-item .col-form-label, .form-label, .fitem .col-form-label")
                .allInnerTexts().stream()
                .map(s -> s.toLowerCase(Locale.ROOT))
                .reduce("", (a, b) -> a + " " + b);
        String hay = labels + " " + settingsRegionText();
        for (String f : fragments) {
            if (!hay.contains(f.toLowerCase(Locale.ROOT))) return false;
        }
        return true;
    }

    public boolean hasCloudlabsNamedSettingInput() {
        return page.locator(
                "input[name*='cloudlabs'], select[name*='cloudlabs'], textarea[name*='cloudlabs']")
                .count() >= 4;
    }

    public boolean hasDebugLogControl() {
        return page.locator(
                "#id_s_cloudlabs_developerlogs, input[name='s_cloudlabs_developerlogs'][type='checkbox']")
                .count() > 0;
    }

    public boolean hasSupportEmailField() {
        return page.locator("#id_s_cloudlabs_support_email, [name='s_cloudlabs_support_email']").count() > 0;
    }

    public void openLabTemplatesList() {
        navigate(LabTemplatesPage.LIST_PATH);
        page.locator(LabTemplatesPage.TABLE).waitFor(new Locator.WaitForOptions()
                .setState(WaitForSelectorState.ATTACHED).setTimeout(30_000));
    }

    public boolean adminTreeLinksToLabTemplates() {
        navigate("/admin/search.php");
        page.waitForTimeout(1_500);
        Locator link = page.locator("a[href*='labtemplates.php']");
        if (link.count() > 0) return true;
        navigate("/admin/index.php");
        page.waitForTimeout(2_000);
        return page.locator("a[href*='labtemplates.php']").count() > 0;
    }

    public void openLabTemplateAddForm() {
        navigate(LabTemplatesPage.ADD_PATH);
        page.locator("#np-ap-pl-lab-right-tab, #name").first().waitFor(new Locator.WaitForOptions()
                .setState(WaitForSelectorState.VISIBLE).setTimeout(30_000));
    }

    public boolean addTemplateFormShowsPlanAndTeamFields() {
        return page.locator("#plan_id").count() > 0 && page.locator("#team_id").count() > 0;
    }

    public boolean addTemplateFormMentionsAccessOrKeys() {
        String body = page.locator("#region-main, main, body").first().innerText()
                .toLowerCase(Locale.ROOT);
        return body.contains("access") || body.contains("key") || body.contains("credential")
                || page.locator("[id*='access'], [id*='api_key'], [id*='secret']").count() > 0;
    }

    public void openReportsIndex() {
        navigate(REPORTS_INDEX);
        page.waitForTimeout(2_000);
    }

    public boolean reportsIndexListsCloudlabsReports() {
        if (page.locator("a[href*='cloudlabsreport']").count() > 0) return true;
        Locator main = page.locator("#region-main");
        if (main.count() == 0) return false;
        String body = main.first().innerText().toLowerCase(Locale.ROOT);
        return body.contains("cloudlabs") || body.contains("cloud labs");
    }

}
