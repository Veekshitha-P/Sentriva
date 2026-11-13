import models.FetchResult;
import models.Finding;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * SENTRIVA - PerformanceEngine (Module 2: Performance)
 * ----------------------------------------------------
 * Inspects HTML payload and response timing for common web performance bottlenecks.
 */
public class PerformanceEngine {

    private static final Pattern HEAD_BLOCK = Pattern.compile("<head\\b[^>]*>([\\s\\S]*?)</head>", Pattern.CASE_INSENSITIVE);
    private static final Pattern SCRIPT_SRC = Pattern.compile("<script\\b[^>]*\\bsrc=[\"'][^\"']+[\"'][^>]*>", Pattern.CASE_INSENSITIVE);
    private static final Pattern VIEWPORT_META = Pattern.compile("<meta\\b[^>]*name=[\"']viewport[\"'][^>]*>", Pattern.CASE_INSENSITIVE);
    private static final Pattern STYLE_BLOCK = Pattern.compile("<style\\b[^>]*>([\\s\\S]*?)</style>", Pattern.CASE_INSENSITIVE);
    private static final Pattern IMG_TAG = Pattern.compile("<img\\b[^>]*>", Pattern.CASE_INSENSITIVE);

    public static int ruleCount() {
        return 6;
    }

    public static List<Finding> inspect(FetchResult page) {
        List<Finding> out = new ArrayList<>();
        String html = page.getHtml();

        // 1. Render-blocking scripts in <head>
        Matcher headMatcher = HEAD_BLOCK.matcher(html);
        if (headMatcher.find()) {
            String headContent = headMatcher.group(1);
            Matcher scriptMatcher = SCRIPT_SRC.matcher(headContent);
            int blockingCount = 0;
            String firstBlocking = null;
            while (scriptMatcher.find()) {
                String tag = scriptMatcher.group();
                String lower = tag.toLowerCase(Locale.ROOT);
                if (!lower.contains("defer") && !lower.contains("async") && !lower.contains("type=\"module\"") && !lower.contains("type='module'")) {
                    blockingCount++;
                    if (firstBlocking == null) firstBlocking = tag;
                }
            }
            if (blockingCount > 0) {
                out.add(new Finding(
                    2,
                    "Render-blocking scripts in <head> (" + blockingCount + ")",
                    "Load Performance",
                    "HIGH",
                    "Synchronous <script src> tags inside <head> block HTML parsing and delay First Contentful Paint. Add defer, async, or type=\"module\".",
                    truncate(firstBlocking, 160)
                ));
            }
        }

        // 2. Missing viewport meta tag
        if (!VIEWPORT_META.matcher(html).find()) {
            out.add(new Finding(
                2,
                "Missing viewport meta tag",
                "Mobile Perf",
                "HIGH",
                "No <meta name=\"viewport\"> tag detected. Mobile browsers fall back to desktop-width layout scaling, hurting responsiveness and CLS.",
                "<head> <!-- no <meta name=\"viewport\" content=\"width=device-width, initial-scale=1\"> --> </head>"
            ));
        }

        // 3. Large inline CSS
        Matcher styleMatcher = STYLE_BLOCK.matcher(html);
        int totalInlineCss = 0;
        while (styleMatcher.find()) {
            totalInlineCss += styleMatcher.group(1).length();
        }
        if (totalInlineCss > 8192) {
            out.add(new Finding(
                2,
                "Excessive inline CSS (" + (totalInlineCss / 1024) + " KB)",
                "Page Weight",
                "MEDIUM",
                "Large inline <style> blocks inflate HTML payload and cannot be cached independently across page navigations.",
                "<style> /* " + totalInlineCss + " chars of inline CSS detected */ </style>"
            ));
        }

        // 4. Images missing lazy loading / dimensions
        Matcher imgMatcher = IMG_TAG.matcher(html);
        int imgTotal = 0;
        int missingLazy = 0;
        int missingDims = 0;
        String firstNoLazy = null;
        String firstNoDims = null;
        while (imgMatcher.find()) {
            imgTotal++;
            String tag = imgMatcher.group();
            String lower = tag.toLowerCase(Locale.ROOT);
            if (!lower.contains("loading=")) {
                missingLazy++;
                if (firstNoLazy == null) firstNoLazy = tag;
            }
            if (!lower.contains("width=") || !lower.contains("height=")) {
                missingDims++;
                if (firstNoDims == null) firstNoDims = tag;
            }
        }
        if (missingLazy >= 2) {
            out.add(new Finding(
                2,
                "Images missing lazy-loading (" + missingLazy + " of " + imgTotal + ")",
                "Load Performance",
                "MEDIUM",
                "Multiple images omit loading=\"lazy\", causing off-screen images to compete for bandwidth during initial page load.",
                truncate(firstNoLazy, 160)
            ));
        }
        if (missingDims >= 1) {
            out.add(new Finding(
                2,
                "Images missing explicit width/height (" + missingDims + ")",
                "Page Weight",
                "LOW",
                "Images without explicit width and height attributes can trigger Cumulative Layout Shift (CLS) while loading.",
                truncate(firstNoDims, 160)
            ));
        }

        // 5. Large HTML payload or slow TTFB
        if (page.getHtmlSizeBytes() > 250_000 || page.getFetchTimeMs() > 2_000) {
            out.add(new Finding(
                2,
                "Heavy HTML payload or slow response",
                "Load Performance",
                "MEDIUM",
                "HTML transfer was " + (page.getHtmlSizeBytes() / 1024) + " KB in " + page.getFetchTimeMs() + " ms. Reducing DOM bloat and server latency improves LCP.",
                "Status " + page.getStatusCode() + " · " + (page.getHtmlSizeBytes() / 1024) + " KB · " + page.getFetchTimeMs() + " ms"
            ));
        }

        System.out.println("[PerformanceEngine] Checked " + ruleCount() + " performance rules -> found " + out.size() + " issue(s)");
        return out;
    }

    private static String truncate(String s, int max) {
        if (s == null) return "";
        String clean = s.replaceAll("\\s+", " ").trim();
        return clean.length() <= max ? clean : clean.substring(0, max) + "...";
    }
}

