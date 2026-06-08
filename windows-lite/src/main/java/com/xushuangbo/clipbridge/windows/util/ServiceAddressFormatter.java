package com.xushuangbo.clipbridge.windows.util;

import java.net.URI;

public final class ServiceAddressFormatter {
    private ServiceAddressFormatter() {
    }

    public static String normalize(String rawAddress) {
        return safeTrim(rawAddress).replaceAll("/+$", "");
    }

    public static String validate(String rawAddress) {
        String normalized = normalize(rawAddress);
        if (normalized.isBlank()) {
            return "请先填写服务地址";
        }
        if (!normalized.startsWith("http://") && !normalized.startsWith("https://")) {
            return "服务地址必须以 http:// 或 https:// 开头";
        }

        URI uri;
        try {
            uri = URI.create(normalized);
        } catch (Exception error) {
            return "服务地址格式不正确";
        }

        if (safeTrim(uri.getHost()).isBlank()) {
            return "服务地址缺少主机名或 IP";
        }
        return "";
    }

    public static String safeTrim(String value) {
        return value == null ? "" : value.trim();
    }
}
