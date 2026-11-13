import models.FetchResult;
import models.Finding;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * SENTRIVA - AccessibilityEngine (Module 3: Accessibility)
 * --------------------------------------------------------
 * Checks HTML markup for core WCAG 2.1 accessibility signals.
 */
public class AccessibilityEngine {

    private static final Pattern HTML_TAG = Pattern.compile("<html\\b([^>]*)>", Pattern.CASE_INSENSITIVE);
    private static final Pattern TITLE_TAG = Pattern.compile("<title\\b[^>]*>([\\s\\S]*?)</title>", Pattern.CASE_INSENSITIVE);
    private static final Pattern IMG_TAG = Pattern.compile("<img\\b[^>]*>", Pattern.CASE_INSENSITIVE);
    private static final Pattern LABEL_FOR = Pattern.compile("<label\\b[^>]*\\bfor=[\"']([^\"']+)[\"']", Pattern.CASE_INSENSITIVE);
    private static final Pattern LABEL_BLOCK = Pattern.compile("<label\\b[^>]*>[\\s\\S]*?</label>", Pattern.CASE_INSENSITIVE);
    private static final Pattern INPUT_TAG = Pattern.compile("<input\\b[^>]*>", Pattern.CASE_INSENSITIVE);
    private static final Pattern ATTR_ID = Pattern.compile("\\bid=[\"']([^\"']+)[\"']", Pattern.CASE_INSENSITIVE);
    private static final Pattern ATTR_TYPE = Pattern.compile("\\btype=[\"']([^\"']+)[\"']", Pattern.CASE_INSENSITIVE);
    private static final Pattern H1_TAG = Pattern.compile("<h1\\b[^>]*>", Pattern.CASE_INSENSITIVE);
    private static final Pattern MAIN_TAG = Pattern.compile("<main\\b[^>]*>|role=[\"']main[\"']", Pattern.CASE_INSENSITIVE);

    public static int ruleCount() {
        return 5;
    }

    public static List<Finding> inspect(FetchResult page) {
        List<Finding> out = new ArrayList<>();
        String html = page.getHtml();

        // 1. Missing lang attribute on <html>
        Matcher htmlMatcher = HTML_TAG.matcher(html);
        if (htmlMatcher.find()) {
            String attrs = htmlMatcher.group(1).toLowerCase(Locale.ROOT);
            if (!attrs.contains("lang=")) {
                out.add(new Finding(
                    3,
                    "Missing document language",
                    "WCAG 3.1.1",
                    "HIGH",
                    "<html> has no lang attribute. Screen readers need a language declaration to select the correct pronunciation engine.",
                    truncate(htmlMatcher.group(), 140)
                ));
            }
        }

        // 2. Missing or empty <title>
        Matcher titleMatcher = TITLE_TAG.matcher(html);
        if (!titleMatcher.find() || titleMatcher.group(1).trim().isEmpty()) {
            out.add(new Finding(
                3,
                "Missing or empty page <title>",
                "WCAG 2.4.2",
                "MEDIUM",
                "Page has no descriptive <title> element. Screen reader users rely on document titles to identify browser tabs and pages.",
                "<head> <!-- missing non-empty <title> --> </head>"
            ));
        }

        // 3. Images missing alt attribute
        Matcher imgMatcher = IMG_TAG.matcher(html);
        int missingAltCount = 0;
        String firstMissingAlt = null;
        while (imgMatcher.find()) {
            String tag = imgMatcher.group();
            String lower = tag.toLowerCase(Locale.ROOT);
            if (!lower.contains("alt=") && !lower.contains("aria-hidden=\"true\"") && !lower.contains("role=\"presentation\"")) {
                missingAltCount++;
                if (firstMissingAlt == null) firstMissingAlt = tag;
            }
        }
        if (missingAltCount > 0) {
            out.add(new Finding(
                3,
                "Images missing alt text (" + missingAltCount + ")",
                "WCAG 1.1.1",
                "CRITICAL",
                "Images without alt attributes cannot be interpreted by screen readers, failing WCAG 2.1 Level A non-text content requirements.",
                truncate(firstMissingAlt, 160)
            ));
        }

        // 4. Unlabelled form inputs
        Set<String> labelledIds = new HashSet<>();
        Matcher lfMatcher = LABEL_FOR.matcher(html);
        while (lfMatcher.find()) {
            labelledIds.add(lfMatcher.group(1));
        }
        String htmlWithoutNestedInputs = html;
        Set<String> wrappedInputs = new HashSet<>();
        Matcher lbMatcher = LABEL_BLOCK.matcher(htmlWithoutNestedInputs);
        while (lbMatcher.find()) {
            Matcher nested = INPUT_TAG.matcher(lbMatcher.group());
            while (nested.find()) {
                wrappedInputs.add(nested.group());
            }
        }

        Matcher inputMatcher = INPUT_TAG.matcher(html);
        int unlabelledCount = 0;
        String firstUnlabelled = null;
        while (inputMatcher.find()) {
            String tag = inputMatcher.group();
            String lower = tag.toLowerCase(Locale.ROOT);
            Matcher typeM = ATTR_TYPE.matcher(tag);
            String type = typeM.find() ? typeM.group(1).toLowerCase(Locale.ROOT) : "text";
            if (type.equals("hidden") || type.equals("submit") || type.equals("button") || type.equals("reset") || type.equals("image")) {
                continue;
            }
            if (lower.contains("aria-label=") || lower.contains("aria-labelledby=") || lower.contains("title=")) {
                continue;
            }
            if (wrappedInputs.contains(tag)) continue;

            Matcher idM = ATTR_ID.matcher(tag);
            if (idM.find() && labelledIds.contains(idM.group(1))) {
                continue;
            }
            unlabelledCount++;
            if (firstUnlabelled == null) firstUnlabelled = tag;
        }
        if (unlabelledCount > 0) {
            out.add(new Finding(
                3,
                "Unlabelled form inputs (" + unlabelledCount + ")",
                "WCAG 1.3.1",
                "HIGH",
                "Form inputs lack an associated <label>, aria-label, or aria-labelledby attribute, leaving assistive technology users unable to identify fields.",
                truncate(firstUnlabelled, 160)
            ));
        }

        // 5. Missing <main> landmark or <h1>
        if (!MAIN_TAG.matcher(html).find() || !H1_TAG.matcher(html).find()) {
            out.add(new Finding(
                3,
                "Missing landmark or <h1> heading",
                "WCAG 2.4.1",
                "LOW",
                "Page is missing a <main> landmark or primary <h1> heading, making keyboard and screen-reader landmark navigation harder.",
                "Document lacks <main> landmark and/or <h1> heading"
            ));
        }

        System.out.println("[AccessibilityEngine] Checked " + ruleCount() + " accessibility rules -> found " + out.size() + " issue(s)");
        return out;
    }

    private static String truncate(String s, int max) {
        if (s == null) return "";
        String clean = s.replaceAll("\\s+", " ").trim();
        return clean.length() <= max ? clean : clean.substring(0, max) + "...";
    }
}

