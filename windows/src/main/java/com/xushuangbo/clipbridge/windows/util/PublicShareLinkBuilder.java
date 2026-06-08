package com.xushuangbo.clipbridge.windows.util;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

public final class PublicShareLinkBuilder {
    private PublicShareLinkBuilder() {
    }

    public static String build(String baseUrl, String publicToken) {
        String normalizedBaseUrl = ServiceAddressFormatter.normalize(baseUrl);
        String normalizedToken = ServiceAddressFormatter.safeTrim(publicToken);
        if (normalizedBaseUrl.isBlank() || normalizedToken.isBlank()) {
            return "";
        }

        URI uri;
        try {
            uri = URI.create(normalizedBaseUrl);
        } catch (Exception error) {
            return "";
        }

        String scheme = ServiceAddressFormatter.safeTrim(uri.getScheme());
        String authority = ServiceAddressFormatter.safeTrim(uri.getRawAuthority());
        if (scheme.isBlank() || authority.isBlank()) {
            return "";
        }

        String basePath = uri.getRawPath() == null ? "" : uri.getRawPath().replaceAll("/+$", "");
        String routeBase = basePath.isBlank()
            ? scheme + "://" + authority
            : scheme + "://" + authority + basePath;
        return routeBase + "/#/public/" + encodePathSegment(normalizedToken);
    }

    private static String encodePathSegment(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }
}
