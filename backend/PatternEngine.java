import models.Finding;
import models.Rule;

import java.util.ArrayList;
import java.util.List;
import java.util.Comparator;

/**
 * SENTRIVA - PatternEngine
 * --------------------------
 * The core detection engine.
 *
 * How it works:
 *   1. RuleLoader reads all rules from resources/patterns.json
 *   2. For each rule, checks if any of its keywords appear in the HTML
 *   3. If found → creates a Finding with the matched snippet
 *   4. Returns all findings sorted by severity (CRITICAL first)
 *
 * Detection method: keyword matching (case-insensitive)
 * One finding per rule maximum — avoids duplicate reports.
 */
public class PatternEngine {

    // Load all rules once when the class is first used
    private static final List<Rule> RULES = RuleLoader.loadRules("patterns.json");

    /**
     * Scans the given HTML against all loaded rules.
     *
     * @param html  the raw HTML string fetched from the target website
     * @return      list of findings, sorted by severity (CRITICAL → HIGH → MEDIUM → LOW)
     */
    public static List<Finding> scan(String html) {

        List<Finding> findings = new ArrayList<>();

        // Lowercase once — reused for all keyword checks
        String htmlLower = html.toLowerCase();

        for (Rule rule : RULES) {

            // Check each keyword in the rule
            for (String keyword : rule.getKeywords()) {

                int index = htmlLower.indexOf(keyword.toLowerCase());

                if (index != -1) {
                    // Keyword found — extract surrounding HTML as snippet
                    String snippet = extractSnippet(html, index);

                    // Create a finding for this rule
                    findings.add(new Finding(
                        rule.getName(),
                        rule.getCategory(),
                        rule.getSeverity(),
                        rule.getDescription(),
                        snippet
                    ));

                    // Only one finding per rule — move to next rule
                    break;
                }
            }
        }

        // Sort: CRITICAL → HIGH → MEDIUM → LOW
        findings.sort(Comparator.comparingInt(f -> severityOrder(f.getSeverity())));

        System.out.println("[PatternEngine] Scanned " + RULES.size()
                + " rules → found " + findings.size() + " issue(s)");

        return findings;
    }

    /**
     * Extracts a readable snippet of HTML around the found keyword.
     * Shows 30 characters before and 120 after the match.
     */
    private static String extractSnippet(String html, int index) {
        int start = Math.max(0, index - 30);
        int end   = Math.min(html.length(), index + 120);
        return html.substring(start, end).trim();
    }

    /** Maps severity to sort order (lower = shown first) */
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
