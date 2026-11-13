package models;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * SENTRIVA - FetchResult
 * ------------------------
 * Holds the fetched HTML, response headers, status code, and timing metadata.
 */
public class FetchResult {

    private final String url;
    private final int statusCode;
    private final long fetchTimeMs;
    private final int htmlSizeBytes;
    private final Map<String, List<String>> headers;
    private final String html;

    public FetchResult(String url, int statusCode, long fetchTimeMs, int htmlSizeBytes,
                       Map<String, List<String>> headers, String html) {
        this.url = url;
        this.statusCode = statusCode;
        this.fetchTimeMs = fetchTimeMs;
        this.htmlSizeBytes = htmlSizeBytes;
        this.headers = headers == null ? Collections.emptyMap() : headers;
        this.html = html == null ? "" : html;
    }

    public String getUrl() { return url; }
    public int getStatusCode() { return statusCode; }
    public long getFetchTimeMs() { return fetchTimeMs; }
    public int getHtmlSizeBytes() { return htmlSizeBytes; }
    public Map<String, List<String>> getHeaders() { return headers; }
    public String getHtml() { return html; }

    public boolean hasHeader(String name) {
        return headers.containsKey(name.toLowerCase());
    }

    public List<String> getHeaderValues(String name) {
        return headers.getOrDefault(name.toLowerCase(), Collections.emptyList());
    }

    public String getHeaderFirst(String name) {
        List<String> vals = getHeaderValues(name);
        return vals.isEmpty() ? null : vals.get(0);
    }
}

