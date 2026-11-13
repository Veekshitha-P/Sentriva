import models.FetchResult;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.zip.GZIPInputStream;

/**
 * SENTRIVA - WebFetcher
 * -----------------------
 * Fetches HTML and HTTP response headers from a target URL using Java 11's HttpClient.
 */
public class WebFetcher {

    private static final String USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) " +
        "AppleWebKit/537.36 (KHTML, like Gecko) " +
        "Chrome/124.0.0.0 Safari/537.36";

    private static final HttpClient CLIENT = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .proxy(java.net.ProxySelector.getDefault())
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    public static FetchResult fetchPage(String url) throws Exception {
        URI uri = URI.create(url.trim());
        String scheme = uri.getScheme();
        if (scheme == null || (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme))) {
            throw new IllegalArgumentException("URL must start with http:// or https://");
        }
        if (uri.getHost() == null || uri.getHost().isBlank()) {
            throw new IllegalArgumentException("URL is missing a valid host");
        }

        System.out.println("[WebFetcher] Fetching: " + uri);
        long startNs = System.nanoTime();

        HttpRequest request = HttpRequest.newBuilder()
                .uri(uri)
                .timeout(Duration.ofSeconds(15))
                .header("User-Agent", USER_AGENT)
                .header("Accept", "text/html,application/xhtml+xml;q=0.9,*/*;q=0.8")
                .header("Accept-Language", "en-US,en;q=0.9")
                .header("Accept-Encoding", "gzip, identity")
                .GET()
                .build();

        HttpResponse<byte[]> response = CLIENT.send(request, HttpResponse.BodyHandlers.ofByteArray());
        long elapsedMs = Math.max(1L, (System.nanoTime() - startNs) / 1_000_000L);

        Map<String, List<String>> lowerHeaders = new HashMap<>();
        response.headers().map().forEach((k, v) -> {
            if (k != null) {
                lowerHeaders.put(k.toLowerCase(Locale.ROOT), v);
            }
        });

        byte[] rawBody = response.body() == null ? new byte[0] : response.body();
        String encoding = "";
        List<String> encHeaders = lowerHeaders.get("content-encoding");
        if (encHeaders != null && !encHeaders.isEmpty()) {
            encoding = encHeaders.get(0).toLowerCase(Locale.ROOT);
        }

        byte[] decodedBytes = encoding.contains("gzip") ? gunzip(rawBody) : rawBody;
        String html = new String(decodedBytes, StandardCharsets.UTF_8);

        int statusCode = response.statusCode();
        System.out.println("[WebFetcher] Status: " + statusCode
                + " | Time: " + elapsedMs + "ms"
                + " | Size: " + decodedBytes.length + " bytes");

        if (statusCode >= 400) {
            throw new IOException("Target website returned HTTP " + statusCode);
        }

        String finalUrl = response.uri() != null ? response.uri().toString() : url;
        return new FetchResult(finalUrl, statusCode, elapsedMs, decodedBytes.length, lowerHeaders, html);
    }

    /** Backwards-compatible helper returning HTML only. */
    public static String fetch(String url) throws Exception {
        return fetchPage(url).getHtml();
    }

    private static byte[] gunzip(byte[] compressed) throws IOException {
        try (GZIPInputStream gis = new GZIPInputStream(new ByteArrayInputStream(compressed))) {
            return gis.readAllBytes();
        }
    }
}
