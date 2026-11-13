import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import models.FetchResult;
import models.Finding;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * SENTRIVA - ScanHandler
 * -------------------------
 * Handles POST /scan requests from the frontend.
 */
public class ScanHandler implements HttpHandler {

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
        exchange.getResponseHeaders().add("Access-Control-Allow-Methods", "POST, OPTIONS");
        exchange.getResponseHeaders().add("Access-Control-Allow-Headers", "Content-Type");
        exchange.getResponseHeaders().add("Content-Type", "application/json; charset=UTF-8");

        if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
            exchange.sendResponseHeaders(204, -1);
            exchange.close();
            return;
        }

        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendResponse(exchange, 405, "{\"error\":\"Only POST is allowed\"}");
            return;
        }

        try {
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            String url = extractUrl(body);
            boolean[] enabled = extractModules(body);

            if (url == null || url.trim().isEmpty()) {
                sendResponse(exchange, 400, "{\"error\":\"Missing url in request body\"}");
                return;
            }

            System.out.println("[ScanHandler] Scanning: " + url);

            FetchResult page = WebFetcher.fetchPage(url);

            CompletableFuture<List<Finding>> f0 = enabled[0]
                    ? CompletableFuture.supplyAsync(() -> PatternEngine.inspect(page))
                    : CompletableFuture.completedFuture(List.of());
            CompletableFuture<List<Finding>> f1 = enabled[1]
                    ? CompletableFuture.supplyAsync(() -> SecurityEngine.inspect(page))
                    : CompletableFuture.completedFuture(List.of());
            CompletableFuture<List<Finding>> f2 = enabled[2]
                    ? CompletableFuture.supplyAsync(() -> PerformanceEngine.inspect(page))
                    : CompletableFuture.completedFuture(List.of());
            CompletableFuture<List<Finding>> f3 = enabled[3]
                    ? CompletableFuture.supplyAsync(() -> AccessibilityEngine.inspect(page))
                    : CompletableFuture.completedFuture(List.of());
            CompletableFuture<List<Finding>> f4 = enabled[4]
                    ? CompletableFuture.supplyAsync(() -> PrivacyEngine.inspect(page))
                    : CompletableFuture.completedFuture(List.of());

            CompletableFuture.allOf(f0, f1, f2, f3, f4).join();

            List<Finding> findings = new ArrayList<>();
            findings.addAll(f0.join());
            findings.addAll(f1.join());
            findings.addAll(f2.join());
            findings.addAll(f3.join());
            findings.addAll(f4.join());
            findings.sort(Comparator.comparingInt(f -> severityOrder(f.getSeverity())));

            int rulesChecked = (enabled[0] ? PatternEngine.ruleCount() : 0)
                    + (enabled[1] ? SecurityEngine.ruleCount() : 0)
                    + (enabled[2] ? PerformanceEngine.ruleCount() : 0)
                    + (enabled[3] ? AccessibilityEngine.ruleCount() : 0)
                    + (enabled[4] ? PrivacyEngine.ruleCount() : 0);

            int score = calculateScore(findings);
            String json = buildJson(page, score, rulesChecked, findings);
            sendResponse(exchange, 200, json);

            System.out.println("[ScanHandler] Done. Score: " + score
                    + " | Issues found: " + findings.size());

        } catch (IllegalArgumentException e) {
            sendResponse(exchange, 400, "{\"error\":\"" + escapeJson(e.getMessage()) + "\"}");
        } catch (Exception e) {
            String msg = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            System.err.println("[ScanHandler] Error: " + msg);
            sendResponse(exchange, 502, "{\"error\":\"" + escapeJson(msg) + "\"}");
        }
    }

    private int calculateScore(List<Finding> findings) {
        int score = 100;
        for (Finding f : findings) {
            switch (f.getSeverity()) {
                case "CRITICAL": score -= 16; break;
                case "HIGH":     score -= 9;  break;
                case "MEDIUM":   score -= 4;  break;
                case "LOW":      score -= 1;  break;
            }
        }
        return Math.max(0, score);
    }

    private String buildJson(FetchResult page, int score, int rulesChecked, List<Finding> findings) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\n");
        sb.append("  \"score\": ").append(score).append(",\n");
        sb.append("  \"url\": \"").append(escapeJson(page.getUrl())).append("\",\n");
        sb.append("  \"statusCode\": ").append(page.getStatusCode()).append(",\n");
        sb.append("  \"fetchTimeMs\": ").append(page.getFetchTimeMs()).append(",\n");
        sb.append("  \"htmlSizeBytes\": ").append(page.getHtmlSizeBytes()).append(",\n");
        sb.append("  \"rulesChecked\": ").append(rulesChecked).append(",\n");
        sb.append("  \"findings\": [\n");

        for (int i = 0; i < findings.size(); i++) {
            Finding f = findings.get(i);
            sb.append("    {\n");
            sb.append("      \"module\": ").append(f.getModule()).append(",\n");
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

    private String extractUrl(String json) {
        String key = "\"url\"";
        int keyIdx = json.indexOf(key);
        if (keyIdx == -1) return null;
        int colonIdx = json.indexOf(":", keyIdx + key.length());
        if (colonIdx == -1) return null;
        int start = json.indexOf("\"", colonIdx + 1);
        if (start == -1) return null;
        StringBuilder sb = new StringBuilder();
        for (int i = start + 1; i < json.length(); i++) {
            char c = json.charAt(i);
            if (c == '\\' && i + 1 < json.length()) {
                char next = json.charAt(++i);
                switch (next) {
                    case '"': sb.append('"'); break;
                    case '\\': sb.append('\\'); break;
                    case '/': sb.append('/'); break;
                    default: sb.append(next); break;
                }
            } else if (c == '"') {
                return sb.toString();
            } else {
                sb.append(c);
            }
        }
        return null;
    }

    private boolean[] extractModules(String json) {
        boolean[] enabled = new boolean[5];
        Arrays.fill(enabled, true);
        int keyIdx = json.indexOf("\"modules\"");
        if (keyIdx == -1) return enabled;
        int arrStart = json.indexOf("[", keyIdx);
        int arrEnd = json.indexOf("]", arrStart);
        if (arrStart == -1 || arrEnd == -1) return enabled;
        String[] parts = json.substring(arrStart + 1, arrEnd).split(",");
        if (parts.length == 5) {
            for (int i = 0; i < 5; i++) {
                enabled[i] = !"false".equalsIgnoreCase(parts[i].trim());
            }
        }
        return enabled;
    }

    private int severityOrder(String severity) {
        switch (severity) {
            case "CRITICAL": return 0;
            case "HIGH":     return 1;
            case "MEDIUM":   return 2;
            case "LOW":      return 3;
            default:         return 4;
        }
    }

    private String escapeJson(String s) {
        if (s == null) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '\\': sb.append("\\\\"); break;
                case '"':  sb.append("\\\""); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                default:
                    if (c < 0x20) sb.append(String.format("\\u%04x", (int) c));
                    else sb.append(c);
            }
        }
        return sb.toString();
    }

    private void sendResponse(HttpExchange exchange, int code, String json) throws IOException {
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(code, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
        exchange.close();
    }
}
