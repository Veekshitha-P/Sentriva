import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * SENTRIVA - WebFetcher
 * -----------------------
 * Fetches raw HTML from a target URL using Java 11's built-in HttpClient.
 * No external libraries needed.
 *
 * Sends a real browser User-Agent so websites respond normally
 * instead of blocking the request.
 */
public class WebFetcher {

    // Pretend to be a real browser so sites don't block us
    private static final String USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) " +
        "AppleWebKit/537.36 (KHTML, like Gecko) " +
        "Chrome/124.0.0.0 Safari/537.36";

    /**
     * Fetches the HTML content of the given URL.
     *
     * @param url  the full URL (must start with http:// or https://)
     * @return     the raw HTML string of the page
     * @throws Exception if the URL is unreachable or returns an error status
     */
    public static String fetch(String url) throws Exception {

        System.out.println("[WebFetcher] Fetching: " + url);

        // Build the HTTP client
        HttpClient client = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NORMAL)   // follow 301/302 redirects
                .connectTimeout(Duration.ofSeconds(10))
                .build();

        // Build the GET request with browser-like headers
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(15))
                .header("User-Agent", USER_AGENT)
                .header("Accept", "text/html,application/xhtml+xml;q=0.9,*/*;q=0.8")
                .header("Accept-Language", "en-US,en;q=0.9")
                .header("Accept-Encoding", "identity")   // get plain text, not gzip
                .GET()
                .build();

        // Send request and receive HTML as a string
        HttpResponse<String> response = client.send(request,
                HttpResponse.BodyHandlers.ofString());

        int statusCode = response.statusCode();
        String html    = response.body();

        System.out.println("[WebFetcher] Status: " + statusCode
                + " | Size: " + html.length() + " chars");

        if (statusCode >= 400) {
            throw new Exception("Website returned HTTP error: " + statusCode);
        }

        return html;
    }
}
