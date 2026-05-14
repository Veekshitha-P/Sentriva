package models;

import java.util.List;

/**
 * SENTRIVA - Rule
 * -----------------
 * Represents one detection rule loaded from patterns.json.
 *
 * Each rule has:
 *  - name:        short label shown in the report
 *  - category:    type of dark pattern (e.g. "Urgency Faking")
 *  - severity:    CRITICAL / HIGH / MEDIUM / LOW
 *  - description: explanation shown to the user
 *  - keywords:    list of strings to search for in the HTML
 */
public class Rule {

    private final String       name;
    private final String       category;
    private final String       severity;
    private final String       description;
    private final List<String> keywords;

    public Rule(String name, String category, String severity,
                String description, List<String> keywords) {
        this.name        = name;
        this.category    = category;
        this.severity    = severity;
        this.description = description;
        this.keywords    = keywords;
    }

    public String       getName()        { return name; }
    public String       getCategory()    { return category; }
    public String       getSeverity()    { return severity; }
    public String       getDescription() { return description; }
    public List<String> getKeywords()    { return keywords; }
}
