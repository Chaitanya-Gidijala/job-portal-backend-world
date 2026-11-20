package com.job.portal.monitoring;

import jakarta.servlet.http.HttpServletRequest;

/** Request-side helpers: client IP behind proxies and a small user-agent classifier. */
public final class ClientInfo {

    private ClientInfo() {
    }

    /** First hop of X-Forwarded-For (set by Render / nginx), else the socket address. */
    public static String ip(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return truncate(forwarded.split(",")[0].trim(), 64);
        }
        String real = request.getHeader("X-Real-IP");
        if (real != null && !real.isBlank()) {
            return truncate(real.trim(), 64);
        }
        return truncate(request.getRemoteAddr(), 64);
    }

    public static String browser(String ua) {
        if (ua == null) return "Unknown";
        if (ua.contains("Edg/")) return "Edge";
        if (ua.contains("OPR/") || ua.contains("Opera")) return "Opera";
        if (ua.contains("SamsungBrowser")) return "Samsung Internet";
        if (ua.contains("Chrome/") || ua.contains("CriOS")) return "Chrome";
        if (ua.contains("Firefox/") || ua.contains("FxiOS")) return "Firefox";
        if (ua.contains("Safari/")) return "Safari";
        if (isBot(ua)) return "Bot";
        if (ua.startsWith("curl") || ua.startsWith("Java") || ua.contains("python") || ua.contains("PostmanRuntime")) return "Script";
        return "Other";
    }

    public static String os(String ua) {
        if (ua == null) return "Unknown";
        if (ua.contains("Windows")) return "Windows";
        if (ua.contains("Android")) return "Android";
        if (ua.contains("iPhone") || ua.contains("iPad") || ua.contains("iOS")) return "iOS";
        if (ua.contains("Mac OS") || ua.contains("Macintosh")) return "macOS";
        if (ua.contains("CrOS")) return "ChromeOS";
        if (ua.contains("Linux")) return "Linux";
        return "Other";
    }

    public static String device(String ua) {
        if (ua == null) return "Unknown";
        if (isBot(ua)) return "Bot";
        if (ua.contains("iPad") || ua.contains("Tablet")) return "Tablet";
        if (ua.contains("Mobi") || ua.contains("Android") || ua.contains("iPhone")) return "Mobile";
        return "Desktop";
    }

    private static final java.util.List<java.util.Map.Entry<String, java.util.regex.Pattern>> BROWSER_VERSIONS = java.util.List.of(
            java.util.Map.entry("Edge", java.util.regex.Pattern.compile("Edg(?:A|iOS)?/(\\d+)")),
            java.util.Map.entry("Opera", java.util.regex.Pattern.compile("OPR/(\\d+)")),
            java.util.Map.entry("Samsung Internet", java.util.regex.Pattern.compile("SamsungBrowser/(\\d+)")),
            java.util.Map.entry("Chrome", java.util.regex.Pattern.compile("(?:Chrome|CriOS)/(\\d+)")),
            java.util.Map.entry("Firefox", java.util.regex.Pattern.compile("(?:Firefox|FxiOS)/(\\d+)")),
            java.util.Map.entry("Safari", java.util.regex.Pattern.compile("Version/(\\d+(?:\\.\\d+)?)")));

    /** Major browser version, e.g. "Chrome 131". */
    public static String browserWithVersion(String ua) {
        String b = browser(ua);
        if (ua == null) return b;
        for (var e : BROWSER_VERSIONS) {
            if (e.getKey().equals(b)) {
                var m = e.getValue().matcher(ua);
                if (m.find()) return b + " " + m.group(1);
            }
        }
        return b;
    }

    /** OS with version where the user agent exposes it, e.g. "Android 14", "iOS 17.4", "macOS 10.15". */
    public static String osWithVersion(String ua) {
        String os = os(ua);
        if (ua == null) return os;
        java.util.regex.Matcher m;
        switch (os) {
            case "Windows" -> {
                if (ua.contains("Windows NT 10.0")) return "Windows 10/11";
                m = java.util.regex.Pattern.compile("Windows NT (\\d+\\.\\d+)").matcher(ua);
                return m.find() ? "Windows NT " + m.group(1) : os;
            }
            case "Android" -> {
                m = java.util.regex.Pattern.compile("Android (\\d+(?:\\.\\d+)?)").matcher(ua);
                return m.find() ? "Android " + m.group(1) : os;
            }
            case "iOS" -> {
                m = java.util.regex.Pattern.compile("OS (\\d+)_(\\d+)").matcher(ua);
                return m.find() ? "iOS " + m.group(1) + "." + m.group(2) : os;
            }
            case "macOS" -> {
                m = java.util.regex.Pattern.compile("Mac OS X (\\d+)[._](\\d+)").matcher(ua);
                return m.find() ? "macOS " + m.group(1) + "." + m.group(2) : os;
            }
            default -> {
                return os;
            }
        }
    }

    public static boolean isBot(String ua) {
        if (ua == null) return false;
        String l = ua.toLowerCase();
        return l.contains("bot") || l.contains("crawl") || l.contains("spider") || l.contains("slurp") || l.contains("headless");
    }

    public static String truncate(String value, int max) {
        if (value == null) return null;
        return value.length() <= max ? value : value.substring(0, max);
    }
}
