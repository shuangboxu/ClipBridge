package com.xushuangbo.clipbridge.windows.ui;

import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.util.Callback;

import static com.xushuangbo.clipbridge.windows.api.ApiModels.AdminRequest;
import static com.xushuangbo.clipbridge.windows.api.ApiModels.BandwidthRequest;
import static com.xushuangbo.clipbridge.windows.api.ApiModels.QuotaRequest;

public final class ResourceRequestCells {
    private ResourceRequestCells() {
    }

    public static Callback<ListView<QuotaRequest>, ListCell<QuotaRequest>> quotaRequestCellFactory() {
        return lv -> new ListCell<>() {
            @Override
            protected void updateItem(QuotaRequest item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    return;
                }
                setText(
                    "状态=" + statusText(item.status()) +
                        " | 当前=" + formatBytes(item.currentQuotaBytes()) +
                        " | 申请=" + formatBytes(item.requestedQuotaBytes()) +
                        " | 说明=" + nonBlank(item.reason(), "-")
                );
            }
        };
    }

    public static Callback<ListView<BandwidthRequest>, ListCell<BandwidthRequest>> bandwidthRequestCellFactory() {
        return lv -> new ListCell<>() {
            @Override
            protected void updateItem(BandwidthRequest item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    return;
                }
                setText(
                    "状态=" + statusText(item.status()) +
                        " | 当前(上/下)=" + item.currentUploadKbps() + "/" + item.currentDownloadKbps() +
                        " KB/s | 申请(上/下)=" + item.requestedUploadKbps() + "/" + item.requestedDownloadKbps() +
                        " KB/s | 说明=" + nonBlank(item.reason(), "-")
                );
            }
        };
    }

    public static Callback<ListView<AdminRequest>, ListCell<AdminRequest>> adminRequestCellFactory() {
        return lv -> new ListCell<>() {
            @Override
            protected void updateItem(AdminRequest item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    return;
                }
                setText(
                    "状态=" + statusText(item.status()) +
                        " | 说明=" + nonBlank(item.reason(), "-") +
                        " | 备注=" + nonBlank(item.reviewNote(), "-")
                );
            }
        };
    }

    private static String nonBlank(String value, String fallback) {
        String text = safeTrim(value);
        return text.isBlank() ? fallback : text;
    }

    private static String statusText(String status) {
        String normalized = safeTrim(status).toLowerCase();
        return switch (normalized) {
            case "pending" -> "待审批";
            case "approved" -> "已通过";
            case "rejected" -> "已拒绝";
            default -> nonBlank(status, "未知");
        };
    }

    private static String formatBytes(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        }
        double kb = bytes / 1024.0;
        if (kb < 1024) {
            return String.format("%.2f KB", kb);
        }
        double mb = kb / 1024.0;
        if (mb < 1024) {
            return String.format("%.2f MB", mb);
        }
        double gb = mb / 1024.0;
        return String.format("%.2f GB", gb);
    }

    private static String safeTrim(String val) {
        return val == null ? "" : val.trim();
    }
}

