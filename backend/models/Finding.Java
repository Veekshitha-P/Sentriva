package models;

/**
 * SENTRIVA - Finding
 * --------------------
 * Represents one issue found during the scan.
 * PatternEngine produces a List<Finding> from the HTML.
 */
public class Finding {

    private final String name;         // Short title of the issue
    private final String category;     // Category (e.g. "Urgency Faking")
    private final String severity;     // CRITICAL / HIGH / MEDIUM / LOW
    private final String description;  // Full explanation shown to the user
    private final String snippet;      // The HTML text that triggered this finding

    public Finding(String name, String category, String severity,
                   String description, String snippet) {
        this.name        = name;
        this.category    = category;
        this.severity    = severity;
        this.description = description;
        this.snippet     = snippet;
    }

    public String getName()        { return name; }
    public String getCategory()    { return category; }
    public String getSeverity()    { return severity; }
    public String getDescription() { return description; }
    public String getSnippet()     { return snippet; }
}
