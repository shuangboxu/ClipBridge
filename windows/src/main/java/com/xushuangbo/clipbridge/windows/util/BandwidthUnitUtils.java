package com.xushuangbo.clipbridge.windows.util;

import java.util.Locale;

public final class BandwidthUnitUtils {
    private static final double BANDWIDTH_BASE = 1024.0;

    private BandwidthUnitUtils() {
    }

    public static String formatBandwidth(int legacyKbps) {
        if (legacyKbps <= 0) {
            return "0 MB/s";
        }
        return trimTrailingZeros(String.format(Locale.ROOT, "%.3f", legacyKbps / BANDWIDTH_BASE)) + " MB/s";
    }

    public static String toBandwidthInput(int legacyKbps) {
        if (legacyKbps <= 0) {
            return "";
        }
        return trimTrailingZeros(String.format(Locale.ROOT, "%.3f", legacyKbps / BANDWIDTH_BASE));
    }

    public static Integer parseBandwidthMbOptional(String raw) {
        String normalized = raw == null ? "" : raw.trim();
        if (normalized.isBlank()) {
            return null;
        }
        try {
            double parsed = Double.parseDouble(normalized);
            if (!Double.isFinite(parsed) || parsed <= 0.0) {
                return null;
            }

            // 中文注释：接口字段名仍然叫 kbps，但服务端历史实现一直按 KB/s 使用。
            // 这里统一把界面里的 MB/s 输入换算回旧字段，避免额外改动接口协议。
            return Math.max(1, (int) Math.round(parsed * BANDWIDTH_BASE));
        } catch (Exception ignore) {
            return null;
        }
    }

    public static int parseBandwidthMbOrFallback(String raw, int fallback) {
        Integer parsed = parseBandwidthMbOptional(raw);
        return parsed == null ? fallback : parsed;
    }

    private static String trimTrailingZeros(String value) {
        return value.replaceAll("\\.?0+$", "");
    }
}
