package com.xushuangbo.clipbridge.windows.ui;

import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Separator;
import javafx.scene.control.Tab;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;

import java.util.function.Consumer;

import static com.xushuangbo.clipbridge.windows.api.ApiModels.AdminRequest;
import static com.xushuangbo.clipbridge.windows.api.ApiModels.BandwidthRequest;
import static com.xushuangbo.clipbridge.windows.api.ApiModels.QuotaRequest;
import static com.xushuangbo.clipbridge.windows.api.ApiModels.RequestListResult;

public final class ResourceRequestTabController {
    private final ObservableList<QuotaRequest> quotaRequestItems;
    private final ObservableList<BandwidthRequest> bandwidthRequestItems;
    private final ObservableList<AdminRequest> adminRequestItems;

    private final Label resourceRetentionHintLabel = new Label(" ");
    private final Tab tab;

    @FunctionalInterface
    public interface QuotaSubmitHandler {
        void submit(long requestedMb, String reason, Runnable onSuccess);
    }

    @FunctionalInterface
    public interface BandwidthSubmitHandler {
        void submit(int uploadKbps, int downloadKbps, String reason, Runnable onSuccess);
    }

    @FunctionalInterface
    public interface AdminSubmitHandler {
        void submit(String reason, Runnable onSuccess);
    }

    public ResourceRequestTabController(
        ObservableList<QuotaRequest> quotaRequestItems,
        ObservableList<BandwidthRequest> bandwidthRequestItems,
        ObservableList<AdminRequest> adminRequestItems,
        Runnable refreshAction,
        QuotaSubmitHandler submitQuotaAction,
        BandwidthSubmitHandler submitBandwidthAction,
        AdminSubmitHandler submitAdminAction,
        Consumer<String> statusSink
    ) {
        this.quotaRequestItems = quotaRequestItems;
        this.bandwidthRequestItems = bandwidthRequestItems;
        this.adminRequestItems = adminRequestItems;
        this.tab = buildTab(refreshAction, submitQuotaAction, submitBandwidthAction, submitAdminAction, statusSink);
    }

    public Tab getTab() {
        return tab;
    }

    public Label getResourceRetentionHintLabel() {
        return resourceRetentionHintLabel;
    }

    public void updateRequestData(
        RequestListResult<QuotaRequest> quota,
        RequestListResult<BandwidthRequest> bandwidth,
        RequestListResult<AdminRequest> admin
    ) {
        quotaRequestItems.setAll(quota.requests());
        bandwidthRequestItems.setAll(bandwidth.requests());
        adminRequestItems.setAll(admin.requests());

        int retentionDays = Math.max(quota.retentionDays(), Math.max(bandwidth.retentionDays(), admin.retentionDays()));
        resourceRetentionHintLabel.setText(retentionDays > 0 ? "已处理申请记录会自动保留 " + retentionDays + " 天" : "");
    }

    private Tab buildTab(
        Runnable refreshAction,
        QuotaSubmitHandler submitQuotaAction,
        BandwidthSubmitHandler submitBandwidthAction,
        AdminSubmitHandler submitAdminAction,
        Consumer<String> statusSink
    ) {
        Label title = new Label("资源申请");
        title.getStyleClass().add("section-title");

        TextField quotaMbField = new TextField();
        quotaMbField.setPromptText("申请配额（MB）");
        TextArea quotaReasonField = new TextArea();
        quotaReasonField.setPromptText("配额申请原因（可选）");
        quotaReasonField.setPrefRowCount(2);

        Button submitQuotaButton = new Button("提交配额申请");
        submitQuotaButton.getStyleClass().add("btn-primary");
        submitQuotaButton.setOnAction(e -> {
            long requestedMb = parseLongValue(quotaMbField.getText(), 0L);
            if (requestedMb <= 0) {
                statusSink.accept("申请配额必须是正整数 MB");
                return;
            }
            submitQuotaAction.submit(requestedMb, quotaReasonField.getText(), quotaReasonField::clear);
        });

        TextField uploadKbpsField = new TextField();
        uploadKbpsField.setPromptText("申请上传带宽（KB/s）");
        TextField downloadKbpsField = new TextField();
        downloadKbpsField.setPromptText("申请下载带宽（KB/s）");
        TextArea bandwidthReasonField = new TextArea();
        bandwidthReasonField.setPromptText("带宽申请原因（可选）");
        bandwidthReasonField.setPrefRowCount(2);

        Button submitBandwidthButton = new Button("提交带宽申请");
        submitBandwidthButton.getStyleClass().add("btn-primary");
        submitBandwidthButton.setOnAction(e -> {
            int upload = parseIntValue(uploadKbpsField.getText(), 0);
            int download = parseIntValue(downloadKbpsField.getText(), 0);
            if (upload <= 0 || download <= 0) {
                statusSink.accept("申请带宽必须是正整数 KB/s");
                return;
            }
            submitBandwidthAction.submit(upload, download, bandwidthReasonField.getText(), bandwidthReasonField::clear);
        });

        TextArea adminReasonField = new TextArea();
        adminReasonField.setPromptText("申请管理员权限原因（可选）");
        adminReasonField.setPrefRowCount(2);

        Button submitAdminButton = new Button("提交管理员权限申请");
        submitAdminButton.getStyleClass().add("btn-primary");
        submitAdminButton.setOnAction(e -> submitAdminAction.submit(adminReasonField.getText(), adminReasonField::clear));

        Button refreshButton = new Button("刷新申请记录");
        refreshButton.getStyleClass().add("btn-ghost");
        refreshButton.setOnAction(e -> refreshAction.run());

        resourceRetentionHintLabel.getStyleClass().add("small-muted");

        ListView<QuotaRequest> quotaList = new ListView<>(quotaRequestItems);
        quotaList.setCellFactory(lv -> new ListCell<>() {
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
        });

        ListView<BandwidthRequest> bandwidthList = new ListView<>(bandwidthRequestItems);
        bandwidthList.setCellFactory(lv -> new ListCell<>() {
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
        });

        ListView<AdminRequest> adminList = new ListView<>(adminRequestItems);
        adminList.setCellFactory(lv -> new ListCell<>() {
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
        });

        VBox root = new VBox(12,
            title,
            refreshButton,
            resourceRetentionHintLabel,
            new Label("配额申请"),
            quotaMbField,
            quotaReasonField,
            submitQuotaButton,
            new Label("带宽申请"),
            uploadKbpsField,
            downloadKbpsField,
            bandwidthReasonField,
            submitBandwidthButton,
            new Label("管理员权限申请"),
            adminReasonField,
            submitAdminButton,
            new Separator(),
            new Label("我的配额申请记录"),
            quotaList,
            new Label("我的带宽申请记录"),
            bandwidthList,
            new Label("我的管理员权限申请记录"),
            adminList
        );
        root.setPadding(new Insets(20));
        root.setFillWidth(true);

        // 中文注释：按数据量自适应列表高度，结合外层滚动容器避免面板挤压。
        FxListViewHelper.bindAdaptiveHeight(quotaList, quotaRequestItems, 42, 1, 8);
        FxListViewHelper.bindAdaptiveHeight(bandwidthList, bandwidthRequestItems, 42, 1, 8);
        FxListViewHelper.bindAdaptiveHeight(adminList, adminRequestItems, 42, 1, 8);

        ScrollPane scrollPane = new ScrollPane(root);
        scrollPane.setFitToWidth(true);
        scrollPane.setPannable(true);
        scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);

        Tab builtTab = new Tab("资源申请", scrollPane);
        builtTab.setClosable(false);
        return builtTab;
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

