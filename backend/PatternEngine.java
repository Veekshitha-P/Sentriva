import models.FetchResult;
import models.Finding;
import models.Rule;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * SENTRIVA - PatternEngine (Module 0: Dark Patterns)
 * ---------------------------------------------------
 * Scans fetched HTML for deceptive UX / dark pattern rules from patterns.json.
 */
public class PatternEngine {

    private static final List<Rule> RULES = RuleLoader.loadRules("patterns.json");

    public static int ruleCount() {
        int count = 0;
        for (Rule r : RULES) {
            if (!isPrivacyRule(r)) count++;
        }
        return count;
    }

    public static List<Finding> inspect(FetchResult page) {
        return scan(page.getHtml());
    }

    public static List<Finding> scan(String html) {
        List<Finding> findings = new ArrayList<>();
        if (html == null || html.isEmpty()) return findings;

        String htmlLower = html.toLowerCase(Locale.ROOT);

        for (Rule rule : RULES) {
            if (isPrivacyRule(rule)) continue;

            for (String keyword : rule.getKeywords()) {
                int index = htmlLower.indexOf(keyword.toLowerCase(Locale.ROOT));
                if (index != -1) {
                    String snippet = extractSnippet(html, index);
                    findings.add(new Finding(
                        0,
                        rule.getName(),
                        rule.getCategory(),
                        rule.getSeverity(),
                        rule.getDescription(),
                        snippet
                    ));
                    break;
                }
            }
        }

        findings.sort(Comparator.comparingInt(f -> severityOrder(f.getSeverity())));
        System.out.println("[PatternEngine] Checked " + ruleCount()
                + " dark pattern rules -> found " + findings.size() + " issue(s)");
        return findings;
    }

    private static boolean isPrivacyRule(Rule r) {
        String cat = r.getCategory() == null ? "" : r.getCategory().toLowerCase(Locale.ROOT);
        return cat.contains("privacy tracking") || cat.contains("gdpr");
    }

    private static String extractSnippet(String html, int index) {
        int start = Math.max(0, index - 30);
        int end   = Math.min(html.length(), index + 120);
        return html.substring(start, end).replaceAll("\\s+", " ").trim();
    }

    private static int severityOrder(String severity) {
        switch (severity) {
            case "CRITICAL": return 0;
            case "HIGH":     return 1;
            case "MEDIUM":   return 2;
            case "LOW":      return 3;
            default:         return 4;
        }
    }
}
