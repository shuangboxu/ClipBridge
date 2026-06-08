package com.xushuangbo.clipbridge.windows.ui;

import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.util.Callback;

import java.util.function.BiConsumer;
import java.util.function.Consumer;

import static com.xushuangbo.clipbridge.windows.api.ApiModels.AdminRequest;
import static com.xushuangbo.clipbridge.windows.api.ApiModels.AdminUser;
import static com.xushuangbo.clipbridge.windows.api.ApiModels.BandwidthRequest;
import static com.xushuangbo.clipbridge.windows.api.ApiModels.QuotaRequest;

public final class AdminPanelCells {
    private AdminPanelCells() {
    }

    @FunctionalInterface
    public interface QuotaApproveAction {
        void apply(QuotaRequest req, Long approvedMb, String reviewNote);
    }

    @FunctionalInterface
    public interface BandwidthApproveAction {
        void apply(BandwidthRequest req, Integer upload, Integer download, String reviewNote);
    }

    @FunctionalInterface
    public interface AdminUserUpdateAction {
        void apply(AdminUser user, long storageQuotaMB, int uploadKbps, int downloadKbps);
    }

    public static Callback<ListView<QuotaRequest>, ListCell<QuotaRequest>> pendingQuotaCellFactory(
        QuotaApproveAction approveAction,
        BiConsumer<QuotaRequest, String> rejectAction
    ) {
        return lv -> new ListCell<>() {
            @Override
            protected void updateItem(QuotaRequest item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                    return;
                }

                Label info = new Label(
                    item.username()
                        + " | 当前=" + formatBytes(item.currentQuotaBytes())
                        + " | 申请=" + formatBytes(item.requestedQuotaBytes())
                        + " | 说明=" + nonBlank(item.reason(), "-")
                );
                info.setWrapText(true);
                info.setMaxWidth(Double.MAX_VALUE);
                HBox.setHgrow(info, Priority.ALWAYS);

                TextField approvedMbField = new TextField();
                approvedMbField.setPromptText("通过配额MB（可空）");
                approvedMbField.setPrefWidth(160);

                TextField noteField = new TextField();
                noteField.setPromptText("审核备注");
                noteField.setPrefWidth(180);

                Button approveButton = new Button("通过");
                approveButton.getStyleClass().add("btn-primary");
                approveButton.setOnAction(e -> {
                    long val = parseLongValue(approvedMbField.getText(), 0L);
                    Long approvedMb = val > 0 ? val : null;
                    approveAction.apply(item, approvedMb, noteField.getText());
                });

                Button rejectButton = new Button("拒绝");
                rejectButton.getStyleClass().add("btn-ghost");
                rejectButton.setOnAction(e -> rejectAction.accept(item, noteField.getText()));

                HBox inputs = new HBox(8, approvedMbField, noteField, approveButton, rejectButton);
                inputs.setAlignment(Pos.CENTER_LEFT);

                VBox wrap = new VBox(8, info, inputs);
                wrap.getStyleClass().add("item-box");
                setGraphic(wrap);
            }
        };
    }

    public static Callback<ListView<BandwidthRequest>, ListCell<BandwidthRequest>> pendingBandwidthCellFactory(
        BandwidthApproveAction approveAction,
        BiConsumer<BandwidthRequest, String> rejectAction
    ) {
        return lv -> new ListCell<>() {
            @Override
            protected void updateItem(BandwidthRequest item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                    return;
                }

                Label info = new Label(
                    item.username()
                        + " | 当前上/下=" + item.currentUploadKbps() + "/" + item.currentDownloadKbps()
                        + " KB/s | 申请上/下=" + item.requestedUploadKbps() + "/" + item.requestedDownloadKbps()
                        + " KB/s | 说明=" + nonBlank(item.reason(), "-")
                );
                info.setWrapText(true);
                info.setMaxWidth(Double.MAX_VALUE);
                HBox.setHgrow(info, Priority.ALWAYS);

                TextField approvedUploadField = new TextField();
                approvedUploadField.setPromptText("通过上传KB/s");
                approvedUploadField.setPrefWidth(130);

                TextField approvedDownloadField = new TextField();
                approvedDownloadField.setPromptText("通过下载KB/s");
                approvedDownloadField.setPrefWidth(130);

                TextField noteField = new TextField();
                noteField.setPromptText("审核备注");
                noteField.setPrefWidth(160);

                Button approveButton = new Button("通过");
                approveButton.getStyleClass().add("btn-primary");
                approveButton.setOnAction(e -> {
                    Integer upload = parseIntValue(approvedUploadField.getText(), 0);
                    Integer download = parseIntValue(approvedDownloadField.getText(), 0);
                    upload = upload > 0 ? upload : null;
                    download = download > 0 ? download : null;
                    approveAction.apply(item, upload, download, noteField.getText());
                });

                Button rejectButton = new Button("拒绝");
                rejectButton.getStyleClass().add("btn-ghost");
                rejectButton.setOnAction(e -> rejectAction.accept(item, noteField.getText()));

                HBox inputs = new HBox(8, approvedUploadField, approvedDownloadField, noteField, approveButton, rejectButton);
                inputs.setAlignment(Pos.CENTER_LEFT);

                VBox wrap = new VBox(8, info, inputs);
                wrap.getStyleClass().add("item-box");
                setGraphic(wrap);
            }
        };
    }

    public static Callback<ListView<AdminRequest>, ListCell<AdminRequest>> pendingAdminCellFactory(
        BiConsumer<AdminRequest, String> approveAction,
        BiConsumer<AdminRequest, String> rejectAction
    ) {
        return lv -> new ListCell<>() {
            @Override
            protected void updateItem(AdminRequest item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                    return;
                }

                Label info = new Label(
                    item.username()
                        + " | 状态=" + statusText(item.status())
                        + " | 说明=" + nonBlank(item.reason(), "-")
                );
                info.setWrapText(true);
                info.setMaxWidth(Double.MAX_VALUE);
                HBox.setHgrow(info, Priority.ALWAYS);

                TextField noteField = new TextField();
                noteField.setPromptText("审核备注");
                noteField.setPrefWidth(220);

                Button approveButton = new Button("通过");
                approveButton.getStyleClass().add("btn-primary");
                approveButton.setOnAction(e -> approveAction.accept(item, noteField.getText()));

                Button rejectButton = new Button("拒绝");
                rejectButton.getStyleClass().add("btn-ghost");
                rejectButton.setOnAction(e -> rejectAction.accept(item, noteField.getText()));

                HBox inputs = new HBox(8, noteField, approveButton, rejectButton);
                inputs.setAlignment(Pos.CENTER_LEFT);

                VBox wrap = new VBox(8, info, inputs);
                wrap.getStyleClass().add("item-box");
                setGraphic(wrap);
            }
        };
    }

    public static Callback<ListView<AdminUser>, ListCell<AdminUser>> adminUserCellFactory(
        AdminUserUpdateAction updateAction,
        Consumer<AdminUser> deleteAction,
        Consumer<String> statusSink
    ) {
        return lv -> new ListCell<>() {
            @Override
            protected void updateItem(AdminUser item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                    return;
                }

                Label info = new Label(
                    item.username()
                        + " | " + (item.isAdmin() ? "管理员" : "普通用户")
                        + " | 配额=" + formatBytes(item.storageQuotaBytes())
                        + " | 已用=" + formatBytes(item.storageUsedBytes())
                        + " | 上/下行=" + item.uploadBandwidthKbps() + "/" + item.downloadBandwidthKbps() + " KB/s"
                );
                info.setWrapText(true);
                info.setMaxWidth(Double.MAX_VALUE);
                HBox.setHgrow(info, Priority.ALWAYS);

                long defaultQuotaMb = Math.max(1L, (item.storageQuotaBytes() + (1024L * 1024L) - 1L) / (1024L * 1024L));
                TextField quotaMbField = new TextField(String.valueOf(defaultQuotaMb));
                quotaMbField.setPromptText("配额MB");
                quotaMbField.setPrefWidth(100);

                TextField uploadField = new TextField(String.valueOf(Math.max(1, item.uploadBandwidthKbps())));
                uploadField.setPromptText("上传KB/s");
                uploadField.setPrefWidth(100);

                TextField downloadField = new TextField(String.valueOf(Math.max(1, item.downloadBandwidthKbps())));
                downloadField.setPromptText("下载KB/s");
                downloadField.setPrefWidth(100);

                Button updateButton = new Button("更新");
                updateButton.getStyleClass().add("btn-primary");
                updateButton.setOnAction(e -> {
                    long quotaMb = parseLongValue(quotaMbField.getText(), 0L);
                    int upload = parseIntValue(uploadField.getText(), 0);
                    int download = parseIntValue(downloadField.getText(), 0);
                    if (quotaMb <= 0 || upload <= 0 || download <= 0) {
                        statusSink.accept("用户配置必须是正整数");
                        return;
                    }
                    updateAction.apply(item, quotaMb, upload, download);
                });

                Button deleteButton = new Button("删除");
                deleteButton.getStyleClass().add("btn-ghost");
                deleteButton.setOnAction(e -> deleteAction.accept(item));

                HBox inputs = new HBox(8, quotaMbField, uploadField, downloadField, updateButton, deleteButton);
                inputs.setAlignment(Pos.CENTER_LEFT);

                VBox wrap = new VBox(8, info, inputs);
                wrap.getStyleClass().add("item-box");
                setGraphic(wrap);
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

    private static int parseIntValue(String raw, int fallback) {
        try {
            return Integer.parseInt(safeTrim(raw));
        } catch (Exception ignore) {
            return fallback;
        }
    }

    private static long parseLongValue(String raw, long fallback) {
        try {
            return Long.parseLong(safeTrim(raw));
        } catch (Exception ignore) {
            return fallback;
        }
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

