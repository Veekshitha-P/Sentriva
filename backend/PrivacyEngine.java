import models.FetchResult;
import models.Finding;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * SENTRIVA - PrivacyEngine (Module 4: Privacy)
 * --------------------------------------------
 * Detects third-party trackers, cookie security gaps, and missing GDPR consent banners.
 */
public class PrivacyEngine {

    private static final String[][] TRACKERS = {
        {
            "Facebook tracking pixel",
            "3rd-party Tracking",
            "CRITICAL",
            "Meta Pixel records page visits and user events and links them to Facebook profiles. Requires explicit opt-in consent under GDPR.",
            "connect.facebook.net|fbevents.js|fbq('init'|fbq(\"init\""
        },
        {
            "Google Analytics / Tag Manager",
            "Analytics",
            "HIGH",
            "Google Analytics / GTM transmits behavioural telemetry and IP metadata to Google servers. Requires a valid Consent Management Platform in regulated regions.",
            "googletagmanager.com|google-analytics.com|gtag('config'|gtag(\"config\"|ga('create'"
        },
        {
            "Hotjar session recording",
            "Privacy Tracking",
            "HIGH",
            "Hotjar captures clicks, scrolls, and session replays. Explicit user consent is required before activating session recording.",
            "hotjar.com|_hjsettings|hj('trigger'|hjid"
        },
        {
            "Ad-tech / data broker scripts",
            "Data Sharing",
            "HIGH",
            "Third-party advertising or audience-measurement scripts detected. User browsing data may be shared across ad networks.",
            "quantserve.com|doubleclick.net|scorecardresearch.com|criteo.com|taboola.com|outbrain.com"
        }
    };

    private static final String[] CONSENT_MARKERS = {
        "cookieconsent", "cookie-consent", "cookiebot", "onetrust", "trustarc",
        "usercentrics", "gdpr-consent", "cookie-banner", "cookie_banner",
        "accept cookies", "manage cookies", "cookie preferences", "consent-banner"
    };

    public static int ruleCount() {
        return TRACKERS.length + 2;
    }

    public static List<Finding> inspect(FetchResult page) {
        List<Finding> out = new ArrayList<>();
        String html = page.getHtml();
        String lower = html.toLowerCase(Locale.ROOT);
        int trackerCount = 0;

        for (String[] tracker : TRACKERS) {
            String[] keywords = tracker[4].split("\\|");
            for (String kw : keywords) {
                int idx = lower.indexOf(kw);
                if (idx != -1) {
                    trackerCount++;
                    out.add(new Finding(
                        4,
                        tracker[0],
                        tracker[1],
                        tracker[2],
                        tracker[3],
                        extractSnippet(html, idx)
                    ));
                    break;
                }
            }
        }

        // Check for ABSENCE of cookie consent banner / CMP
        boolean hasConsentBanner = false;
        for (String marker : CONSENT_MARKERS) {
            if (lower.contains(marker)) {
                hasConsentBanner = true;
                break;
            }
        }

        List<String> setCookies = page.getHeaderValues("set-cookie");
        if (!hasConsentBanner && (trackerCount > 0 || !setCookies.isEmpty())) {
            out.add(new Finding(
                4,
                "Missing cookie consent banner",
                "GDPR Compliance",
                "CRITICAL",
                "Third-party tracking or cookies were detected without a visible Consent Management Platform (CMP) or cookie consent mechanism.",
                trackerCount > 0
                    ? "Detected " + trackerCount + " tracker(s) with no consent banner/CMP found in HTML"
                    : "Server set " + setCookies.size() + " cookie(s) with no consent banner/CMP found in HTML"
            ));
        }

        // Check Set-Cookie security flags
        for (String cookie : setCookies) {
            String lc = cookie.toLowerCase(Locale.ROOT);
            if (!lc.contains("httponly") || !lc.contains("samesite") || (page.getUrl().toLowerCase(Locale.ROOT).startsWith("https://") && !lc.contains("secure"))) {
                String cookieName = cookie.contains("=") ? cookie.substring(0, cookie.indexOf('=')) : "cookie";
                out.add(new Finding(
                    4,
                    "Cookie missing Secure/HttpOnly/SameSite flags",
                    "Cookie Security",
                    "MEDIUM",
                    "Response Set-Cookie header omits recommended security attributes (Secure, HttpOnly, SameSite), increasing session hijacking or CSRF risk.",
                    "Set-Cookie: " + cookieName + "=... (missing protective flags)"
                ));
                break;
            }
        }

        System.out.println("[PrivacyEngine] Checked " + ruleCount() + " privacy rules -> found " + out.size() + " issue(s)");
        return out;
    }

    private static String extractSnippet(String html, int index) {
        int start = Math.max(0, index - 30);
        int end   = Math.min(html.length(), index + 120);
        return html.substring(start, end).replaceAll("\\s+", " ").trim();
    }
}

