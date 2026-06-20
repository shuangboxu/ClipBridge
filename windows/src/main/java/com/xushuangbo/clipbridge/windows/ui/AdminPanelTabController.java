package com.xushuangbo.clipbridge.windows.ui;

import com.xushuangbo.clipbridge.windows.util.BandwidthUnitUtils;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Separator;
import javafx.scene.control.Tab;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import static com.xushuangbo.clipbridge.windows.api.ApiModels.AdminRequest;
import static com.xushuangbo.clipbridge.windows.api.ApiModels.AdminSettings;
import static com.xushuangbo.clipbridge.windows.api.ApiModels.AdminUser;
import static com.xushuangbo.clipbridge.windows.api.ApiModels.BandwidthRequest;
import static com.xushuangbo.clipbridge.windows.api.ApiModels.QuotaRequest;

public final class AdminPanelTabController {
    private static final int ADMIN_USER_PAGE_SIZE = 10;

    private final ObservableList<QuotaRequest> pendingQuotaRequestItems;
    private final ObservableList<BandwidthRequest> pendingBandwidthRequestItems;
    private final ObservableList<AdminRequest> pendingAdminRequestItems;
    private final ObservableList<AdminUser> adminUserItems;
    private final ObservableList<AdminUser> adminUserPageItems;

    private final Consumer<String> statusSink;
    private final Runnable refreshAction;
    private final Consumer<AdminSettingsInput> saveSettingsAction;
    private final ApproveQuotaAction approveQuotaAction;
    private final BiConsumer<QuotaRequest, String> rejectQuotaAction;
    private final ApproveBandwidthAction approveBandwidthAction;
    private final BiConsumer<BandwidthRequest, String> rejectBandwidthAction;
    private final BiConsumer<AdminRequest, String> approveAdminAction;
    private final BiConsumer<AdminRequest, String> rejectAdminAction;
    private final UpdateAdminUserAction updateAdminUserAction;
    private final Consumer<AdminUser> deleteAdminUserAction;

    private final Label adminStatusLabel = new Label("仅管理员可用");
    private final TextField adminMaxUsersField = new TextField();
    private final TextField adminMaxUploadField = new TextField();
    private final TextField adminMaxDownloadField = new TextField();
    private final TextField adminMaxUploadFileField = new TextField();

    private final Button adminUserPrevButton = new Button("上一页");
    private final Button adminUserNextButton = new Button("下一页");
    private final Label adminUserPageLabel = new Label("第 1/1 页 · 每页 10 条 · 总计 0 条");

    private int adminUserPage = 1;
    private int adminUserTotalPages = 1;

    private final Tab tab;

    @FunctionalInterface
    public interface ApproveQuotaAction {
        void apply(QuotaRequest req, Long approvedMb, String reviewNote);
    }

    @FunctionalInterface
    public interface ApproveBandwidthAction {
        void apply(BandwidthRequest req, Integer upload, Integer download, String reviewNote);
    }

    @FunctionalInterface
    public interface UpdateAdminUserAction {
        void apply(AdminUser user, long storageQuotaMB, int uploadKbps, int downloadKbps);
    }

    public record AdminSettingsInput(int maxUsers, int maxUpload, int maxDownload, int maxUploadFile) {
    }

    public AdminPanelTabController(
        ObservableList<QuotaRequest> pendingQuotaRequestItems,
        ObservableList<BandwidthRequest> pendingBandwidthRequestItems,
        ObservableList<AdminRequest> pendingAdminRequestItems,
        ObservableList<AdminUser> adminUserItems,
        ObservableList<AdminUser> adminUserPageItems,
        boolean isAdmin,
        Runnable refreshAction,
        Consumer<AdminSettingsInput> saveSettingsAction,
        ApproveQuotaAction approveQuotaAction,
        BiConsumer<QuotaRequest, String> rejectQuotaAction,
        ApproveBandwidthAction approveBandwidthAction,
        BiConsumer<BandwidthRequest, String> rejectBandwidthAction,
        BiConsumer<AdminRequest, String> approveAdminAction,
        BiConsumer<AdminRequest, String> rejectAdminAction,
        UpdateAdminUserAction updateAdminUserAction,
        Consumer<AdminUser> deleteAdminUserAction,
        Consumer<String> statusSink
    ) {
        this.pendingQuotaRequestItems = pendingQuotaRequestItems;
        this.pendingBandwidthRequestItems = pendingBandwidthRequestItems;
        this.pendingAdminRequestItems = pendingAdminRequestItems;
        this.adminUserItems = adminUserItems;
        this.adminUserPageItems = adminUserPageItems;
        this.refreshAction = refreshAction;
        this.saveSettingsAction = saveSettingsAction;
        this.approveQuotaAction = approveQuotaAction;
        this.rejectQuotaAction = rejectQuotaAction;
        this.approveBandwidthAction = approveBandwidthAction;
        this.rejectBandwidthAction = rejectBandwidthAction;
        this.approveAdminAction = approveAdminAction;
        this.rejectAdminAction = rejectAdminAction;
        this.updateAdminUserAction = updateAdminUserAction;
        this.deleteAdminUserAction = deleteAdminUserAction;
        this.statusSink = statusSink;

        this.tab = buildTab(isAdmin);
    }

    public Tab getTab() {
        return tab;
    }

    public Label getAdminStatusLabel() {
        return adminStatusLabel;
    }

    public TextField getAdminMaxUsersField() {
        return adminMaxUsersField;
    }

    public TextField getAdminMaxUploadField() {
        return adminMaxUploadField;
    }

    public TextField getAdminMaxDownloadField() {
        return adminMaxDownloadField;
    }

    public TextField getAdminMaxUploadFileField() {
        return adminMaxUploadFileField;
    }

    public Button getAdminUserPrevButton() {
        return adminUserPrevButton;
    }

    public Button getAdminUserNextButton() {
        return adminUserNextButton;
    }

    public Label getAdminUserPageLabel() {
        return adminUserPageLabel;
    }

    public void setAdminEnabled(boolean isAdmin) {
        tab.setDisable(!isAdmin);
    }

    public void clearForNonAdmin(String statusText) {
        setAdminEnabled(false);
        adminStatusLabel.setText(statusText);
        pendingQuotaRequestItems.clear();
        pendingBandwidthRequestItems.clear();
        pendingAdminRequestItems.clear();
        adminUserItems.clear();
        applyAdminUserPaging(1);
    }

    public void applyAdminData(
        AdminSettings settings,
        List<AdminUser> users,
        List<QuotaRequest> quotaRequests,
        List<BandwidthRequest> bandwidthRequests,
        List<AdminRequest> adminRequests,
        String statusText
    ) {
        setAdminEnabled(true);
        adminStatusLabel.setText(statusText);
        adminMaxUsersField.setText(String.valueOf(settings.maxUserCount()));
        adminMaxUploadField.setText(BandwidthUnitUtils.toBandwidthInput(settings.maxUserUploadBandwidthKbps()));
        adminMaxDownloadField.setText(BandwidthUnitUtils.toBandwidthInput(settings.maxUserDownloadBandwidthKbps()));
        adminMaxUploadFileField.setText(String.valueOf(settings.maxUploadFileMB()));

        adminUserItems.setAll(users);
        applyAdminUserPaging(adminUserPage);
        pendingQuotaRequestItems.setAll(quotaRequests);
        pendingBandwidthRequestItems.setAll(bandwidthRequests);
        pendingAdminRequestItems.setAll(adminRequests);
    }

    private Tab buildTab(boolean isAdmin) {
        Label title = new Label("管理员面板");
        title.getStyleClass().add("section-title");

        adminStatusLabel.getStyleClass().add("small-muted");

        adminMaxUsersField.setPromptText("最大用户数");
        adminMaxUploadField.setPromptText("用户上传上限 MB/s");
        adminMaxDownloadField.setPromptText("用户下载上限 MB/s");
        adminMaxUploadFileField.setPromptText("单文件上传上限 MB");

        Button refreshButton = new Button("刷新管理员数据");
        refreshButton.getStyleClass().add("btn-ghost");
        refreshButton.setOnAction(e -> refreshAction.run());

        Button saveSettingsButton = new Button("保存全局设置");
        saveSettingsButton.getStyleClass().add("btn-primary");
        saveSettingsButton.setOnAction(e -> saveSettingsFromFields());

        ListView<QuotaRequest> pendingQuotaList = new ListView<>(pendingQuotaRequestItems);
        pendingQuotaList.setCellFactory(lv -> new PendingQuotaCell());

        ListView<BandwidthRequest> pendingBandwidthList = new ListView<>(pendingBandwidthRequestItems);
        pendingBandwidthList.setCellFactory(lv -> new PendingBandwidthCell());

        ListView<AdminRequest> pendingAdminList = new ListView<>(pendingAdminRequestItems);
        pendingAdminList.setCellFactory(lv -> new PendingAdminCell());

        ListView<AdminUser> adminUsersList = new ListView<>(adminUserPageItems);
        adminUsersList.setCellFactory(lv -> new AdminUserCell());

        adminUserPrevButton.getStyleClass().add("btn-ghost");
        adminUserNextButton.getStyleClass().add("btn-ghost");
        adminUserPageLabel.getStyleClass().add("small-muted");
        adminUserPrevButton.setOnAction(e -> applyAdminUserPaging(adminUserPage - 1));
        adminUserNextButton.setOnAction(e -> applyAdminUserPaging(adminUserPage + 1));

        Region adminUserPagerSpacer = new Region();
        HBox.setHgrow(adminUserPagerSpacer, Priority.ALWAYS);
        HBox adminUserPager = new HBox(10, adminUserPrevButton, adminUserNextButton, adminUserPagerSpacer, adminUserPageLabel);
        adminUserPager.setAlignment(Pos.CENTER_LEFT);

        VBox root = new VBox(12,
            title,
            adminStatusLabel,
            new HBox(10, refreshButton, saveSettingsButton),
            new Label("全局设置"),
            adminMaxUsersField,
            adminMaxUploadField,
            adminMaxDownloadField,
            adminMaxUploadFileField,
            new Separator(),
            new Label("用户管理"),
            adminUsersList,
            adminUserPager,
            new Separator(),
            new Label("待审批：配额申请"),
            pendingQuotaList,
            new Label("待审批：带宽申请"),
            pendingBandwidthList,
            new Label("待审批：管理员权限申请"),
            pendingAdminList
        );
        root.setPadding(new Insets(20));
        root.setFillWidth(true);

        // 中文注释：列表区域统一做高度自适应，避免面板固定高度导致的滚动体验问题。
        FxListViewHelper.bindAdaptiveHeight(adminUsersList, adminUserPageItems, 116, 1, ADMIN_USER_PAGE_SIZE);
        FxListViewHelper.bindAdaptiveHeight(pendingQuotaList, pendingQuotaRequestItems, 110, 1, 6);
        FxListViewHelper.bindAdaptiveHeight(pendingBandwidthList, pendingBandwidthRequestItems, 120, 1, 6);
        FxListViewHelper.bindAdaptiveHeight(pendingAdminList, pendingAdminRequestItems, 110, 1, 6);
        applyAdminUserPaging(1);

        ScrollPane scrollPane = new ScrollPane(root);
        scrollPane.setFitToWidth(true);
        scrollPane.setPannable(true);
        scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);

        Tab builtTab = new Tab("管理员面板", scrollPane);
        builtTab.setClosable(false);
        builtTab.setDisable(!isAdmin);
        return builtTab;
    }

    private void saveSettingsFromFields() {
        int maxUsers = parseIntValue(adminMaxUsersField.getText(), 0);
        int maxUpload = BandwidthUnitUtils.parseBandwidthMbOrFallback(adminMaxUploadField.getText(), 0);
        int maxDownload = BandwidthUnitUtils.parseBandwidthMbOrFallback(adminMaxDownloadField.getText(), 0);
        int maxUploadFile = parseIntValue(adminMaxUploadFileField.getText(), 0);

        if (maxUsers <= 0 || maxUpload <= 0 || maxDownload <= 0 || maxUploadFile <= 0) {
            statusSink.accept("全局设置必须是正整数");
            return;
        }
        saveSettingsAction.accept(new AdminSettingsInput(maxUsers, maxUpload, maxDownload, maxUploadFile));
    }

    private void applyAdminUserPaging(int targetPage) {
        int total = adminUserItems.size();
        adminUserTotalPages = Math.max(1, (total + ADMIN_USER_PAGE_SIZE - 1) / ADMIN_USER_PAGE_SIZE);
        adminUserPage = Math.max(1, Math.min(targetPage, adminUserTotalPages));

        int start = Math.max(0, (adminUserPage - 1) * ADMIN_USER_PAGE_SIZE);
        int end = Math.min(total, start + ADMIN_USER_PAGE_SIZE);
        if (start >= end) {
            adminUserPageItems.clear();
        } else {
            adminUserPageItems.setAll(adminUserItems.subList(start, end));
        }
        updateAdminUserPager();
    }

    private void updateAdminUserPager() {
        adminUserPrevButton.setDisable(adminUserPage <= 1);
        adminUserNextButton.setDisable(adminUserPage >= adminUserTotalPages);
        adminUserPageLabel.setText(
            "第 " + adminUserPage + "/" + adminUserTotalPages +
                " 页 · 每页 " + ADMIN_USER_PAGE_SIZE + " 条 · 总计 " + adminUserItems.size() + " 条"
        );
    }

    private final class PendingQuotaCell extends ListCell<QuotaRequest> {
        @Override
        protected void updateItem(QuotaRequest item, boolean empty) {
            super.updateItem(item, empty);
            if (empty || item == null) {
                setText(null);
                setGraphic(null);
                return;
            }

            Label info = new Label(
                item.username() +
                    " | 当前=" + formatBytes(item.currentQuotaBytes()) +
                    " | 申请=" + formatBytes(item.requestedQuotaBytes()) +
                    " | 说明=" + nonBlank(item.reason(), "-")
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
                approveQuotaAction.apply(item, approvedMb, noteField.getText());
            });

            Button rejectButton = new Button("拒绝");
            rejectButton.getStyleClass().add("btn-ghost");
            rejectButton.setOnAction(e -> rejectQuotaAction.accept(item, noteField.getText()));

            HBox inputs = new HBox(8, approvedMbField, noteField, approveButton, rejectButton);
            inputs.setAlignment(Pos.CENTER_LEFT);

            VBox wrap = new VBox(8, info, inputs);
            wrap.getStyleClass().add("item-box");
            setGraphic(wrap);
        }
    }

    private final class PendingBandwidthCell extends ListCell<BandwidthRequest> {
        @Override
        protected void updateItem(BandwidthRequest item, boolean empty) {
            super.updateItem(item, empty);
            if (empty || item == null) {
                setText(null);
                setGraphic(null);
                return;
            }

            Label info = new Label(
                item.username() +
                    " | 当前上/下=" + BandwidthUnitUtils.formatBandwidth(item.currentUploadKbps()) + "/" + BandwidthUnitUtils.formatBandwidth(item.currentDownloadKbps()) +
                    " | 申请上/下=" + BandwidthUnitUtils.formatBandwidth(item.requestedUploadKbps()) + "/" + BandwidthUnitUtils.formatBandwidth(item.requestedDownloadKbps()) +
                    " | 说明=" + nonBlank(item.reason(), "-")
            );
            info.setWrapText(true);
            info.setMaxWidth(Double.MAX_VALUE);
            HBox.setHgrow(info, Priority.ALWAYS);

            TextField approvedUploadField = new TextField();
            approvedUploadField.setPromptText("通过上传MB/s");
            approvedUploadField.setPrefWidth(130);

            TextField approvedDownloadField = new TextField();
            approvedDownloadField.setPromptText("通过下载MB/s");
            approvedDownloadField.setPrefWidth(130);

            TextField noteField = new TextField();
            noteField.setPromptText("审核备注");
            noteField.setPrefWidth(160);

            Button approveButton = new Button("通过");
            approveButton.getStyleClass().add("btn-primary");
            approveButton.setOnAction(e -> {
                Integer upload = BandwidthUnitUtils.parseBandwidthMbOptional(approvedUploadField.getText());
                Integer download = BandwidthUnitUtils.parseBandwidthMbOptional(approvedDownloadField.getText());
                upload = upload != null && upload > 0 ? upload : null;
                download = download != null && download > 0 ? download : null;
                approveBandwidthAction.apply(item, upload, download, noteField.getText());
            });

            Button rejectButton = new Button("拒绝");
            rejectButton.getStyleClass().add("btn-ghost");
            rejectButton.setOnAction(e -> rejectBandwidthAction.accept(item, noteField.getText()));

            HBox inputs = new HBox(8, approvedUploadField, approvedDownloadField, noteField, approveButton, rejectButton);
            inputs.setAlignment(Pos.CENTER_LEFT);

            VBox wrap = new VBox(8, info, inputs);
            wrap.getStyleClass().add("item-box");
            setGraphic(wrap);
        }
    }

    private final class PendingAdminCell extends ListCell<AdminRequest> {
        @Override
        protected void updateItem(AdminRequest item, boolean empty) {
            super.updateItem(item, empty);
            if (empty || item == null) {
                setText(null);
                setGraphic(null);
                return;
            }

            Label info = new Label(
                item.username() +
                    " | 状态=" + statusText(item.status()) +
                    " | 说明=" + nonBlank(item.reason(), "-")
            );
            info.setWrapText(true);
            info.setMaxWidth(Double.MAX_VALUE);
            HBox.setHgrow(info, Priority.ALWAYS);

            TextField noteField = new TextField();
            noteField.setPromptText("审核备注");
            noteField.setPrefWidth(220);

            Button approveButton = new Button("通过");
            approveButton.getStyleClass().add("btn-primary");
            approveButton.setOnAction(e -> approveAdminAction.accept(item, noteField.getText()));

            Button rejectButton = new Button("拒绝");
            rejectButton.getStyleClass().add("btn-ghost");
            rejectButton.setOnAction(e -> rejectAdminAction.accept(item, noteField.getText()));

            HBox inputs = new HBox(8, noteField, approveButton, rejectButton);
            inputs.setAlignment(Pos.CENTER_LEFT);

            VBox wrap = new VBox(8, info, inputs);
            wrap.getStyleClass().add("item-box");
            setGraphic(wrap);
        }
    }

    private final class AdminUserCell extends ListCell<AdminUser> {
        @Override
        protected void updateItem(AdminUser item, boolean empty) {
            super.updateItem(item, empty);
            if (empty || item == null) {
                setText(null);
                setGraphic(null);
                return;
            }

            Label info = new Label(
                item.username() +
                    " | " + (item.isAdmin() ? "管理员" : "普通用户") +
                    " | 配额=" + formatBytes(item.storageQuotaBytes()) +
                    " | 已用=" + formatBytes(item.storageUsedBytes()) +
                    " | 上/下行=" + BandwidthUnitUtils.formatBandwidth(item.uploadBandwidthKbps()) + "/" + BandwidthUnitUtils.formatBandwidth(item.downloadBandwidthKbps())
            );
            info.setWrapText(true);
            info.setMaxWidth(Double.MAX_VALUE);
            HBox.setHgrow(info, Priority.ALWAYS);

            long defaultQuotaMb = Math.max(1L, (item.storageQuotaBytes() + (1024L * 1024L) - 1L) / (1024L * 1024L));
            TextField quotaMbField = new TextField(String.valueOf(defaultQuotaMb));
            quotaMbField.setPromptText("配额MB");
            quotaMbField.setPrefWidth(100);

            TextField uploadField = new TextField(BandwidthUnitUtils.toBandwidthInput(item.uploadBandwidthKbps()));
            uploadField.setPromptText("上传MB/s");
            uploadField.setPrefWidth(100);

            TextField downloadField = new TextField(BandwidthUnitUtils.toBandwidthInput(item.downloadBandwidthKbps()));
            downloadField.setPromptText("下载MB/s");
            downloadField.setPrefWidth(100);

            Button updateButton = new Button("更新");
            updateButton.getStyleClass().add("btn-primary");
            updateButton.setOnAction(e -> {
                long quotaMb = parseLongValue(quotaMbField.getText(), 0L);
                int upload = BandwidthUnitUtils.parseBandwidthMbOrFallback(uploadField.getText(), 0);
                int download = BandwidthUnitUtils.parseBandwidthMbOrFallback(downloadField.getText(), 0);
                if (quotaMb <= 0 || upload <= 0 || download <= 0) {
                    statusSink.accept("用户配置必须是正整数");
                    return;
                }
                updateAdminUserAction.apply(item, quotaMb, upload, download);
            });

            Button deleteButton = new Button("删除");
            deleteButton.getStyleClass().add("btn-ghost");
            deleteButton.setOnAction(e -> deleteAdminUserAction.accept(item));

            HBox inputs = new HBox(8, quotaMbField, uploadField, downloadField, updateButton, deleteButton);
            inputs.setAlignment(Pos.CENTER_LEFT);

            VBox wrap = new VBox(8, info, inputs);
            wrap.getStyleClass().add("item-box");
            setGraphic(wrap);
        }
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

