import models.Rule;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * SENTRIVA - RuleLoader
 * -----------------------
 * Reads the patterns.json file from the resources folder
 * and converts it into a List<Rule> for PatternEngine to use.
 *
 * Uses only Java standard library — no external JSON libraries needed.
 * Parses JSON manually using simple string operations.
 *
 * patterns.json location: backend/resources/patterns.json
 * It is loaded from the classpath at runtime.
 */
public class RuleLoader {

    /**
     * Loads all rules from the given JSON file in the resources folder.
     *
     * @param filename  name of the JSON file (e.g. "patterns.json")
     * @return          list of Rule objects ready for PatternEngine
     */
    public static List<Rule> loadRules(String filename) {

        List<Rule> rules = new ArrayList<>();

        try {
            // Load the file from the classpath (resources/ folder)
            InputStream stream = RuleLoader.class.getClassLoader()
                    .getResourceAsStream(filename);

            if (stream == null) {
                System.err.println("[RuleLoader] ERROR: Could not find " + filename
                        + " in resources/ folder.");
                return rules;
            }

            // Read entire file content into a string
            String json = new String(stream.readAllBytes());
            stream.close();

            // Parse the JSON array of rule objects
            rules = parseRules(json);

            System.out.println("[RuleLoader] Loaded " + rules.size()
                    + " rules from " + filename);

        } catch (Exception e) {
            System.err.println("[RuleLoader] Failed to load rules: " + e.getMessage());
        }

        return rules;
    }

    /**
     * Parses the JSON string into a list of Rule objects.
     *
     * Expected JSON format:
     * {
     *   "rules": [
     *     {
     *       "name": "...",
     *       "category": "...",
     *       "severity": "...",
     *       "description": "...",
     *       "keywords": ["kw1", "kw2", ...]
     *     },
     *     ...
     *   ]
     * }
     */
    private static List<Rule> parseRules(String json) {
        List<Rule> rules = new ArrayList<>();

        // Find the "rules" array — split on each rule object
        int rulesArrayStart = json.indexOf("\"rules\"");
        if (rulesArrayStart == -1) {
            System.err.println("[RuleLoader] No 'rules' key found in JSON.");
            return rules;
        }

        // Split the JSON into individual rule blocks using "{ }" boundaries
        // Simple approach: find each { } block inside the rules array
        int arrayStart = json.indexOf("[", rulesArrayStart);
        int arrayEnd   = json.lastIndexOf("]");

        if (arrayStart == -1 || arrayEnd == -1) return rules;

        String arrayContent = json.substring(arrayStart + 1, arrayEnd);

        // Split into individual rule objects by finding { ... } pairs
        int depth = 0;
        int blockStart = -1;

        for (int i = 0; i < arrayContent.length(); i++) {
            char c = arrayContent.charAt(i);
            if (c == '{') {
                if (depth == 0) blockStart = i;
                depth++;
            } else if (c == '}') {
                depth--;
                if (depth == 0 && blockStart != -1) {
                    String block = arrayContent.substring(blockStart, i + 1);
                    Rule rule = parseOneRule(block);
                    if (rule != null) rules.add(rule);
                    blockStart = -1;
                }
            }
        }

        return rules;
    }

    /** Parses a single rule JSON object into a Rule instance */
    private static Rule parseOneRule(String block) {
        try {
            String name        = extractString(block, "name");
            String category    = extractString(block, "category");
            String severity    = extractString(block, "severity");
            String description = extractString(block, "description");
            List<String> keywords = extractKeywords(block);

            if (name == null || severity == null || keywords.isEmpty()) return null;

            return new Rule(name, category, severity, description, keywords);

        } catch (Exception e) {
            System.err.println("[RuleLoader] Failed to parse rule: " + e.getMessage());
            return null;
        }
    }

    /** Extracts a string value from a JSON object block */
    private static String extractString(String json, String key) {
        String search = "\"" + key + "\"";
        int keyIdx = json.indexOf(search);
        if (keyIdx == -1) return null;
        int colonIdx = json.indexOf(":", keyIdx + search.length());
        if (colonIdx == -1) return null;
        int start = json.indexOf("\"", colonIdx + 1);
        if (start == -1) return null;
        int end = start + 1;
        while (end < json.length()) {
            if (json.charAt(end) == '"' && json.charAt(end - 1) != '\\') break;
            end++;
        }
        return json.substring(start + 1, end);
    }

    /** Extracts the keywords array from a rule JSON block */
    private static List<String> extractKeywords(String block) {
        List<String> keywords = new ArrayList<>();
        int arrStart = block.indexOf("\"keywords\"");
        if (arrStart == -1) return keywords;
        int start = block.indexOf("[", arrStart);
        int end   = block.indexOf("]", start);
        if (start == -1 || end == -1) return keywords;

        String arrContent = block.substring(start + 1, end);

        // Extract each quoted string in the array
        int i = 0;
        while (i < arrContent.length()) {
            int q1 = arrContent.indexOf("\"", i);
            if (q1 == -1) break;
            int q2 = arrContent.indexOf("\"", q1 + 1);
            if (q2 == -1) break;
            keywords.add(arrContent.substring(q1 + 1, q2));
            i = q2 + 1;
        }

        return keywords;
    }
}
