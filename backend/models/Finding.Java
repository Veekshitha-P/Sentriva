package models;

/**
 * SENTRIVA - Finding
 * --------------------
 * Represents one issue found during a scan.
 */
public class Finding {

    private final int module;          // 0=Dark, 1=Security, 2=Performance, 3=Accessibility, 4=Privacy
    private final String name;         // Short title of the issue
    private final String category;     // Category (e.g. "Urgency Faking")
    private final String severity;     // CRITICAL / HIGH / MEDIUM / LOW
    private final String description;  // Full explanation shown to the user
    private final String snippet;      // The HTML/header text that triggered this finding

    public Finding(String name, String category, String severity,
                   String description, String snippet) {
        this(-1, name, category, severity, description, snippet);
    }

    public Finding(int module, String name, String category, String severity,
                   String description, String snippet) {
        this.module      = module;
        this.name        = name;
        this.category    = category;
        this.severity    = severity;
        this.description = description;
        this.snippet     = snippet;
    }

    public int getModule()         { return module; }
    public String getName()        { return name; }
    public String getCategory()    { return category; }
    public String getSeverity()    { return severity; }
    public String getDescription() { return description; }
    public String getSnippet()     { return snippet; }
}
