import models.Rule;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
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
            // Load the file from the classpath (resources/ folder) or filesystem fallback
            InputStream stream = RuleLoader.class.getClassLoader()
                    .getResourceAsStream(filename);
            if (stream == null) {
                Path p1 = Path.of("resources", filename);
                Path p2 = Path.of("backend", "resources", filename);
                if (Files.exists(p1)) stream = Files.newInputStream(p1);
                else if (Files.exists(p2)) stream = Files.newInputStream(p2);
            }

            if (stream == null) {
                System.err.println("[RuleLoader] ERROR: Could not find " + filename
                        + " in resources/ folder.");
                return rules;
            }

            // Read entire file content into a string
            String json = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
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
        return readJsonString(json, start);
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

        int i = 0;
        while (i < arrContent.length()) {
            int q1 = arrContent.indexOf("\"", i);
            if (q1 == -1) break;
            String val = readJsonString(arrContent, q1);
            if (val == null) break;
            keywords.add(val);
            i = advancePastJsonString(arrContent, q1);
        }

        return keywords;
    }

    private static int advancePastJsonString(String s, int openQuoteIdx) {
        int idx = openQuoteIdx + 1;
        while (idx < s.length()) {
            char c = s.charAt(idx);
            if (c == '\\') {
                idx += 2;
                continue;
            }
            if (c == '"') {
                return idx + 1;
            }
            idx++;
        }
        return s.length();
    }

    private static String readJsonString(String s, int openQuoteIdx) {
        StringBuilder sb = new StringBuilder();
        int idx = openQuoteIdx + 1;
        while (idx < s.length()) {
            char c = s.charAt(idx);
            if (c == '\\' && idx + 1 < s.length()) {
                char next = s.charAt(idx + 1);
                switch (next) {
                    case '"':  sb.append('"'); break;
                    case '\\': sb.append('\\'); break;
                    case '/':  sb.append('/'); break;
                    case 'n':  sb.append('\n'); break;
                    case 'r':  sb.append('\r'); break;
                    case 't':  sb.append('\t'); break;
                    default:   sb.append(next); break;
                }
                idx += 2;
                continue;
            }
            if (c == '"') {
                return sb.toString();
            }
            sb.append(c);
            idx++;
        }
        return null;
    }
}
