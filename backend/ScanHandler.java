import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import models.Finding;

import java.io.IOException;
import java.io.OutputStream;
import java.util.List;

/**
 * SENTRIVA - ScanHandler
 * -------------------------
 * Handles POST /scan requests from the frontend.
 *
 * Request JSON:
 *   { "url": "https://example.com" }
 *
 * Response JSON:
 *   {
 *     "score": 68,
 *     "url": "https://example.com",
 *     "findings": [
 *       { "name":"...", "category":"...", "severity":"...", "description":"...", "snippet":"..." },
 *       ...
 *     ]
 *   }
 */
public class ScanHandler implements HttpHandler {

    @Override
    public void handle(HttpExchange exchange) throws IOException {

        // --- CORS headers so the browser can call from index.html ---
        exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
        exchange.getResponseHeaders().add("Access-Control-Allow-Methods", "POST, OPTIONS");
        exchange.getResponseHeaders().add("Access-Control-Allow-Headers", "Content-Type");
        exchange.getResponseHeaders().add("Content-Type", "application/json");

        // Handle browser preflight request
        if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
            exchange.sendResponseHeaders(204, -1);
            exchange.close();
            return;
        }

        // Only accept POST
        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendResponse(exchange, 405, "{\"error\":\"Only POST is allowed\"}");
            return;
        }

        try {
            // Step 1: Read the request body and extract the URL
            String body = new String(exchange.getRequestBody().readAllBytes());
            String url  = extractUrl(body);

            if (url == null || url.trim().isEmpty()) {
                sendResponse(exchange, 400, "{\"error\":\"Missing url in request body\"}");
                return;
            }

            System.out.println("[ScanHandler] Scanning: " + url);

            // Step 2: Fetch the HTML from the target website
            String html = WebFetcher.fetch(url);

            // Step 3: Run the pattern detection engine
            List<Finding> findings = PatternEngine.scan(html);

            // Step 4: Calculate trust score
            int score = calculateScore(findings);

            // Step 5: Build JSON response and send it
            String json = buildJson(url, score, findings);
            sendResponse(exchange, 200, json);

            System.out.println("[ScanHandler] Done. Score: " + score
                    + " | Issues found: " + findings.size());

        } catch (Exception e) {
            System.err.println("[ScanHandler] Error: " + e.getMessage());
            sendResponse(exchange, 500, "{\"error\":\"" + e.getMessage() + "\"}");
        }
    }

    /**
     * Calculates trust score from 0 to 100.
     * CRITICAL = -16 pts, HIGH = -9 pts, MEDIUM = -4 pts
     */
    private int calculateScore(List<Finding> findings) {
        int score = 100;
        for (Finding f : findings) {
            switch (f.getSeverity()) {
                case "CRITICAL": score -= 16; break;
                case "HIGH":     score -= 9;  break;
                case "MEDIUM":   score -= 4;  break;
            }
        }
        return Math.max(0, score);
    }

    /**
     * Builds the full JSON response string.
     */
    private String buildJson(String url, int score, List<Finding> findings) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\n");
        sb.append("  \"score\": ").append(score).append(",\n");
        sb.append("  \"url\": \"").append(escapeJson(url)).append("\",\n");
        sb.append("  \"findings\": [\n");

        for (int i = 0; i < findings.size(); i++) {
            Finding f = findings.get(i);
            sb.append("    {\n");
            sb.append("      \"name\": \""       ).append(escapeJson(f.getName())       ).append("\",\n");
            sb.append("      \"category\": \""   ).append(escapeJson(f.getCategory())   ).append("\",\n");
            sb.append("      \"severity\": \""   ).append(escapeJson(f.getSeverity())   ).append("\",\n");
            sb.append("      \"description\": \"").append(escapeJson(f.getDescription())).append("\",\n");
            sb.append("      \"snippet\": \""    ).append(escapeJson(f.getSnippet())    ).append("\"\n");
            sb.append("    }");
            if (i < findings.size() - 1) sb.append(",");
            sb.append("\n");
        }

        sb.append("  ]\n");
        sb.append("}");
        return sb.toString();
    }

    /**
     * Extracts the "url" value from a simple JSON body.
     * E.g. {"url":"https://example.com"} → https://example.com
     */
    private String extractUrl(String json) {
        String key = "\"url\"";
        int keyIdx = json.indexOf(key);
        if (keyIdx == -1) return null;
        int colonIdx = json.indexOf(":", keyIdx);
        if (colonIdx == -1) return null;
        int start = json.indexOf("\"", colonIdx + 1);
        if (start == -1) return null;
        int end = json.indexOf("\"", start + 1);
        if (end == -1) return null;
        return json.substring(start + 1, end);
    }

    /** Escapes special characters for safe JSON output */
    private String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    /** Sends a JSON response with the given status code */
    private void sendResponse(HttpExchange exchange, int code, String json) throws IOException {
        byte[] bytes = json.getBytes("UTF-8");
        exchange.sendResponseHeaders(code, bytes.length);
        OutputStream out = exchange.getResponseBody();
        out.write(bytes);
        out.close();
        exchange.close();
    }
}
