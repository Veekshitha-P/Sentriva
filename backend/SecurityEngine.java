import models.FetchResult;
import models.Finding;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * SENTRIVA - SecurityEngine (Module 1: Security)
 * -----------------------------------------------
 * Inspects HTTP response headers and HTML for transport and client security gaps.
 */
public class SecurityEngine {

    private static final Pattern MIXED_CONTENT = Pattern.compile(
        "<(?:script|iframe|form|object|embed)\\b[^>]*(?:src|action)=[\"']http://[^\"']+[\"'][^>]*>",
        Pattern.CASE_INSENSITIVE
    );

    private static final Pattern UNSAFE_BLANK = Pattern.compile(
        "<a\\b[^>]*target=[\"']_blank[\"'][^>]*>",
        Pattern.CASE_INSENSITIVE
    );

    public static int ruleCount() {
        return 9;
    }

    public static List<Finding> inspect(FetchResult page) {
        List<Finding> out = new ArrayList<>();
        String url = page.getUrl() == null ? "" : page.getUrl();
        boolean isHttps = url.toLowerCase(Locale.ROOT).startsWith("https://");

        if (!isHttps) {
            out.add(new Finding(
                1,
                "Insecure HTTP transport",
                "Transport Security",
                "CRITICAL",
                "Page is served over unencrypted HTTP. Traffic, credentials, and cookies can be intercepted or tampered with on the network.",
                "Target URL: " + url
            ));
        }

        if (!page.hasHeader("content-security-policy")) {
            out.add(new Finding(
                1,
                "Missing Content-Security-Policy",
                "Security Headers",
                "CRITICAL",
                "No Content-Security-Policy (CSP) header present. Without CSP, browsers cannot restrict unauthorized inline scripts or third-party code execution (XSS).",
                "HTTP Response: no Content-Security-Policy header present"
            ));
        }

        if (isHttps && !page.hasHeader("strict-transport-security")) {
            out.add(new Finding(
                1,
                "Missing Strict-Transport-Security",
                "Security Headers",
                "CRITICAL",
                "No HSTS header found on HTTPS response. Browsers may allow downgrade attacks from HTTPS to HTTP on initial visits.",
                "HTTP Response: no Strict-Transport-Security header present"
            ));
        }

        String csp = page.getHeaderFirst("content-security-policy");
        boolean hasFrameAncestors = csp != null && csp.toLowerCase(Locale.ROOT).contains("frame-ancestors");
        if (!page.hasHeader("x-frame-options") && !hasFrameAncestors) {
            out.add(new Finding(
                1,
                "Missing X-Frame-Options",
                "Clickjacking",
                "HIGH",
                "No X-Frame-Options header or CSP frame-ancestors directive detected. Attackers can embed this page inside a hidden iframe for clickjacking.",
                "HTTP Response: no X-Frame-Options or CSP frame-ancestors"
            ));
        }

        String xcto = page.getHeaderFirst("x-content-type-options");
        if (xcto == null || !xcto.toLowerCase(Locale.ROOT).contains("nosniff")) {
            out.add(new Finding(
                1,
                "Missing X-Content-Type-Options",
                "MIME Sniffing",
                "HIGH",
                "Response lacks X-Content-Type-Options: nosniff. Browsers may MIME-sniff responses away from the declared Content-Type.",
                xcto == null ? "HTTP Response: no X-Content-Type-Options header present" : "X-Content-Type-Options: " + xcto
            ));
        }

        if (!page.hasHeader("referrer-policy")) {
            out.add(new Finding(
                1,
                "Missing Referrer-Policy",
                "Info Leakage",
                "MEDIUM",
                "No Referrer-Policy header. Full URLs and query parameters may be leaked to external origins in the Referer header.",
                "HTTP Response: no Referrer-Policy header present"
            ));
        }

        if (!page.hasHeader("permissions-policy")) {
            out.add(new Finding(
                1,
                "Missing Permissions-Policy",
                "Security Headers",
                "LOW",
                "No Permissions-Policy header restricts powerful browser APIs (camera, microphone, geolocation) from embedded third-party frames.",
                "HTTP Response: no Permissions-Policy header present"
            ));
        }

        String html = page.getHtml();
        if (isHttps) {
            Matcher mixed = MIXED_CONTENT.matcher(html);
            if (mixed.find()) {
                out.add(new Finding(
                    1,
                    "Mixed active HTTP content",
                    "Transport Security",
                    "HIGH",
                    "An HTTPS page loads active scripts, iframes, or forms over unencrypted http://, weakening transport security.",
                    truncate(mixed.group(), 160)
                ));
            }
        }

        Matcher blankMatcher = UNSAFE_BLANK.matcher(html);
        int unsafeBlankCount = 0;
        String firstUnsafeBlank = null;
        while (blankMatcher.find()) {
            String tag = blankMatcher.group();
            String lowerTag = tag.toLowerCase(Locale.ROOT);
            if (!lowerTag.contains("noopener") && !lowerTag.contains("noreferrer")) {
                unsafeBlankCount++;
                if (firstUnsafeBlank == null) firstUnsafeBlank = tag;
            }
        }
        if (unsafeBlankCount > 0) {
            out.add(new Finding(
                1,
                "Links missing rel=\"noopener\" (" + unsafeBlankCount + ")",
                "Tabnabbing",
                "LOW",
                "External target=\"_blank\" links without rel=\"noopener\" or \"noreferrer\" expose window.opener to opened tabs in older browsers.",
                truncate(firstUnsafeBlank, 160)
            ));
        }

        System.out.println("[SecurityEngine] Checked " + ruleCount() + " security rules -> found " + out.size() + " issue(s)");
        return out;
    }

    private static String truncate(String s, int max) {
        if (s == null) return "";
        String clean = s.replaceAll("\\s+", " ").trim();
        return clean.length() <= max ? clean : clean.substring(0, max) + "...";
    }
}

