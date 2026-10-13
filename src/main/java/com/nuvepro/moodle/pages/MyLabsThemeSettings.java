package com.nuvepro.moodle.pages;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * NuveTheme admin settings that control the My Labs page ({@code /theme/nuvetheme/mylabs.php}).
 * Field ids are resolved from live form labels (not hard-coded) because theme setting names vary by build.
 */
public class MyLabsThemeSettings extends BasePage {
    public static final String SECTION = "themesettingnuvetheme";
    public static final Pattern ACTIVITY_TYPES = Pattern.compile("(?i)activity type");

    private final Map<String, String> fieldIds = new LinkedHashMap<>();

    public MyLabsThemeSettings(Page page) {
        super(page);
    }

    public void open() {
        navigate("/admin/settings.php?section=" + SECTION);
        page.waitForTimeout(800);
    }

    public boolean formLoaded() {
        return page.locator("#adminsettings .form-item, .form-item").count() > 0;
    }

    /** Resolve {@code id_s_theme_nuvetheme_*} inputs whose label matches the pattern (cached). */
    public String fieldId(Pattern labelPattern) {
        String key = labelPattern.pattern();
        if (fieldIds.containsKey(key)) {
            return fieldIds.get(key);
        }
        refreshFieldMap();
        for (Map.Entry<String, String> e : fieldIds.entrySet()) {
            if (labelPattern.matcher(e.getKey()).find()) {
                return e.getValue();
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private void refreshFieldMap() {
        fieldIds.clear();
        Object raw = page.evaluate("""
                () => {
                  const out = [];
                  for (const el of document.querySelectorAll('.form-item')) {
                    const inp = el.querySelector('[id^="id_s_theme_nuvetheme"]');
                    const lab = el.querySelector('label, .form-label, .form-shortname');
                    const label = lab ? lab.innerText.trim() : '';
                    if (inp && label) out.push({ label, id: inp.id });
                  }
                  return out;
                }
                """);
        if (raw instanceof Iterable<?> items) {
            for (Object item : items) {
                if (item instanceof Map<?, ?> m) {
                    String label = String.valueOf(m.get("label"));
                    String id = String.valueOf(m.get("id"));
                    if (!label.isBlank() && id.startsWith("id_s_theme_nuvetheme")) {
                        fieldIds.put(label, id);
                    }
                }
            }
        }
    }

    public void setFieldByLabelPattern(Pattern labelPattern, String value) {
        String id = requireFieldId(labelPattern);
        MoodleSettings settings = new MoodleSettings(page);
        settings.setValue(id, value);
        settings.save();
    }

    public String getFieldByLabelPattern(Pattern labelPattern) {
        String id = requireFieldId(labelPattern);
        return new MoodleSettings(page).getValue(id);
    }

    private String requireFieldId(Pattern labelPattern) {
        String id = fieldId(labelPattern);
        if (id == null) {
            throw new AssertionError("No NuveTheme field matched " + labelPattern.pattern()
                    + "; known labels=" + fieldIds.keySet());
        }
        return id;
    }

    public String fieldIdForRestore(Pattern labelPattern) {
        return fieldId(labelPattern);
    }

    /** Primary nav / drawer link to mylabs.php (visible to the current user). */
    public boolean navLinkVisible() {
        Locator link = page.locator("a[href*='mylabs.php']:visible");
        return link.count() > 0 && link.first().isVisible();
    }

    /** Moodle multiselect for Sandbox / Assessment / Practice Project on My Labs. */
    public void setActivityTypesSelected(String... optionLabels) {
        open();
        String id = requireFieldId(ACTIVITY_TYPES);
        Locator sel = page.locator("#" + id);
        if (sel.count() == 0) {
            throw new AssertionError("My Labs activity types field not found: " + id);
        }
        if (optionLabels.length == 0) {
            sel.evaluate("el => { if (el.multiple) { for (const o of el.options) o.selected = false; } }");
        } else {
            sel.selectOption(optionLabels);
        }
        new MoodleSettings(page).save();
        page.waitForTimeout(800);
    }
}
