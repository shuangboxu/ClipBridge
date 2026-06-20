package com.xushuangbo.clipbridge.windows;

import com.xushuangbo.clipbridge.windows.api.ApiClient;
import com.xushuangbo.clipbridge.windows.api.ApiException;
import com.xushuangbo.clipbridge.windows.api.ApiModels;
import com.xushuangbo.clipbridge.windows.state.AppState;
import com.xushuangbo.clipbridge.windows.state.AppStateStore;
import com.xushuangbo.clipbridge.windows.state.ShareRules;
import com.xushuangbo.clipbridge.windows.sync.ClipboardSyncService;
import com.xushuangbo.clipbridge.windows.util.BandwidthUnitUtils;
import com.xushuangbo.clipbridge.windows.util.PublicShareLinkBuilder;
import com.xushuangbo.clipbridge.windows.util.ServiceAddressFormatter;
import com.xushuangbo.clipbridge.windows.util.WindowsStartupManager;
import javafx.animation.PauseTransition;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.PasswordField;
import javafx.scene.control.RadioButton;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Separator;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.control.TitledPane;
import javafx.scene.control.ToggleGroup;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.Dragboard;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.SVGPath;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.util.Duration;

import javax.imageio.ImageIO;
import java.awt.Desktop;
import java.awt.MenuItem;
import java.awt.PopupMenu;
import java.awt.SystemTray;
import java.awt.TrayIcon;
import java.awt.image.BufferedImage;
import java.io.File;
import java.net.URI;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.function.Consumer;

import static com.xushuangbo.clipbridge.windows.api.ApiModels.AccountProfile;
import static com.xushuangbo.clipbridge.windows.api.ApiModels.AdminRequest;
import static com.xushuangbo.clipbridge.windows.api.ApiModels.AdminSettings;
import static com.xushuangbo.clipbridge.windows.api.ApiModels.AdminUser;
import static com.xushuangbo.clipbridge.windows.api.ApiModels.BandwidthRequest;
import static com.xushuangbo.clipbridge.windows.api.ApiModels.ClipboardItem;
import static com.xushuangbo.clipbridge.windows.api.ApiModels.DeviceInfo;
import static com.xushuangbo.clipbridge.windows.api.ApiModels.FileItem;
import static com.xushuangbo.clipbridge.windows.api.ApiModels.PagedDevices;
import static com.xushuangbo.clipbridge.windows.api.ApiModels.PagedFiles;
import static com.xushuangbo.clipbridge.windows.api.ApiModels.QuotaRequest;
import static com.xushuangbo.clipbridge.windows.api.ApiModels.ShareItem;
import static com.xushuangbo.clipbridge.windows.api.ApiModels.ShareListResult;

public class MainApp extends Application {
    private static final int WINDOW_WIDTH = 1320;
    private static final int WINDOW_HEIGHT = 900;
    private static final int HISTORY_PAGE_SIZE = 20;
    private static final int FILE_PAGE_SIZE = 20;
    private static final int DEVICE_PAGE_SIZE = 10;
    private static final int SHARE_PAGE_SIZE = 10;
    private static final long BYTES_PER_MB = 1024L * 1024L;
    private static final String PROJECT_GITHUB_URL = "https://github.com/shuangboxu/ClipBridge";

    private final AppStateStore stateStore = new AppStateStore();
    private final ApiClient apiClient = new ApiClient(stateStore);
    private final ExecutorService ioExecutor = Executors.newFixedThreadPool(4, new IoThreadFactory());

    private ClipboardSyncService syncService;
    private Stage primaryStage;
    private Scene loginScene;
    private Scene mainScene;
    private BorderPane mainRoot;
    private StackPane pageContainer;

    private final PauseTransition toastClearTimer = new PauseTransition(Duration.seconds(5));
    private final Map<NavPage, Node> pageNodes = new EnumMap<>(NavPage.class);
    private final Map<NavPage, Button> navButtons = new EnumMap<>(NavPage.class);
    private final Map<NavPage, Label> pageNoticeLabels = new EnumMap<>(NavPage.class);
    private final EnumSet<NavPage> loadedPages = EnumSet.noneOf(NavPage.class);
    private NavPage currentPage = NavPage.HISTORY;

    private Image appIconImage;
    private Image authHeroImage;
    private Image dashboardHeroImage;
    private Image emptyHistoryImage;
    private Image emptyDevicesImage;
    private Image emptyFilesImage;
    private Image emptySharesImage;
    private Image emptyRequestsImage;
    private Image offlineStateImage;

    private Label topTitleLabel;
    private Label topUserLabel;
    private Label topRoleLabel;
    private Label topServerLabel;
    private Button topSyncToggleButton;
    private Label globalStatusLabel;
    private Label statusSyncLabel;
    private Button statusSyncToggleButton;
    private Button adminNavButton;
    private Stage settingsStage;
    private Label settingsDialogTitleLabel;
    private Label settingsDialogNoticeLabel;
    private VBox settingsDialogContentBox;
    private final Map<SettingsModule, Button> settingsModuleButtons = new EnumMap<>(SettingsModule.class);
    private SettingsModule activeSettingsModule = SettingsModule.GENERAL;

    private TextField loginBaseUrlField;
    private TextField loginUsernameField;
    private PasswordField loginPasswordField;
    private TextField loginDeviceNameField;
    private Label loginModeLabel;
    private Label loginHintLabel;
    private Button loginSubmitButton;
    private Button loginSwitchModeButton;
    private boolean registerMode = false;

    private Label overviewAccountValueLabel;
    private Label overviewDeviceValueLabel;
    private Label overviewSyncValueLabel;
    private Label overviewQuotaValueLabel;
    private Label overviewBandwidthValueLabel;
    private Label overviewAckValueLabel;
    private Button overviewSyncToggleButton;

    private final ObservableList<ClipboardItem> historyItems = FXCollections.observableArrayList();
    private ListView<ClipboardItem> historyListView;
    private TextArea historyManualUploadArea;
    private TextArea historyDetailTextArea;
    private Label historyDetailMetaLabel;
    private Button historyDeleteButton;
    private Label historyPageLabel;
    private Button historyPrevButton;
    private Button historyNextButton;
    private final List<Long> historyPageBeforeSeqs = new ArrayList<>();
    private int historyPageIndex = 0;
    private boolean historyHasNext = false;

    private final ObservableList<DeviceInfo> deviceItems = FXCollections.observableArrayList();
    private ListView<DeviceInfo> deviceListView;
    private Label devicePageLabel;
    private Button devicePrevButton;
    private Button deviceNextButton;
    private Label deviceDetailTitleLabel;
    private Label deviceDetailMetaLabel;
    private TextField deviceRenameField;
    private Button deviceSaveButton;
    private Button deviceOfflineButton;
    private int devicePage = 1;
    private int deviceTotalPages = 1;

    private final ObservableList<FileItem> fileItems = FXCollections.observableArrayList();
    private ListView<FileItem> fileListView;
    private Label filePageLabel;
    private Button filePrevButton;
    private Button fileNextButton;
    private Label fileSummaryFilesLabel;
    private Label fileSummaryBytesLabel;
    private Label fileSummaryLimitLabel;
    private Label fileDetailTitleLabel;
    private Label fileDetailMetaLabel;
    private TextField fileRenameField;
    private Button fileDownloadButton;
    private Button fileRenameButton;
    private Button fileDeleteButton;
    private Label fileDropHintLabel;
    private int filePage = 1;
    private int fileTotalPages = 1;

    private final ObservableList<ShareItem> shareItems = FXCollections.observableArrayList();
    private ListView<ShareItem> shareListView;
    private Label sharePageLabel;
    private Button sharePrevButton;
    private Button shareNextButton;
    private ComboBox<ShareRules.StatusFilter> shareStatusFilterBox;
    private Stage shareComposeStage;
    private Label shareComposeNoticeLabel;
    private RadioButton shareTextModeRadio;
    private RadioButton shareFileModeRadio;
    private RadioButton shareNeverStrategyRadio;
    private RadioButton shareExpireStrategyRadio;
    private RadioButton shareOnceStrategyRadio;
    private HBox shareTextContentFieldRow;
    private HBox shareFileFieldRow;
    private TextArea shareTextContentArea;
    private Label shareSelectedFileLabel;
    private Label shareStrategySummaryLabel;
    private Label shareLatestLinkLabel;
    private Button shareComposeToggleButton;
    private Label shareDetailTitleLabel;
    private Label shareDetailMetaLabel;
    private TextArea shareDetailPreviewArea;
    private Button shareCopyLinkButton;
    private Button shareOpenLinkButton;
    private Button shareRevokeButton;
    private ComboBox<ShareRules.ExpirePreset> shareExpirePresetBox;
    private ComboBox<ShareRules.CountdownPreset> shareCountdownPresetBox;
    private CheckBox shareNeverAllowCopyCheck;
    private CheckBox shareExpireAllowCopyCheck;
    private CheckBox shareOnceAllowCopyCheck;
    private CheckBox shareOnceShowCountdownCheck;
    private VBox shareNeverRuleBox;
    private VBox shareExpireRuleBox;
    private VBox shareOnceRuleBox;
    private ShareRules.ComposeMode selectedShareComposeMode = ShareRules.ComposeMode.TEXT;
    private ShareRules.StrategyKey selectedShareStrategy = ShareRules.StrategyKey.EXPIRE;
    private Path selectedShareFilePath;
    private int sharePage = 1;
    private int shareTotalPages = 1;
    private int shareTotalCount = 0;

    private ComboBox<String> requestStatusFilterBox;
    private final ObservableList<QuotaRequest> quotaRequestItems = FXCollections.observableArrayList();
    private final ObservableList<BandwidthRequest> bandwidthRequestItems = FXCollections.observableArrayList();
    private final ObservableList<AdminRequest> adminRequestItems = FXCollections.observableArrayList();
    private ListView<QuotaRequest> quotaRequestListView;
    private ListView<BandwidthRequest> bandwidthRequestListView;
    private ListView<AdminRequest> adminRequestListView;
    private Label requestOverviewLabel;
    private TextField requestQuotaField;
    private TextArea requestQuotaReasonArea;
    private TextField requestUploadField;
    private TextField requestDownloadField;
    private TextArea requestBandwidthReasonArea;
    private TextArea requestAdminReasonArea;

    private TextField adminMaxUsersField;
    private TextField adminDefaultQuotaField;
    private TextField adminDefaultUploadField;
    private TextField adminDefaultDownloadField;
    private TextField adminMaxUserUploadField;
    private TextField adminMaxUserDownloadField;
    private TextField adminMaxUploadFileField;
    private CheckBox adminAllowRegistrationCheck;
    private Label adminSettingsMetaLabel;
    private final ObservableList<AdminUser> adminUserItems = FXCollections.observableArrayList();
    private ListView<AdminUser> adminUserListView;
    private Label adminUserDetailTitleLabel;
    private Label adminUserDetailMetaLabel;
    private TextField adminUserQuotaField;
    private TextField adminUserUploadField;
    private TextField adminUserDownloadField;
    private CheckBox adminUserAdminCheck;
    private Button adminUserSaveButton;
    private Button adminUserDeleteButton;
    private TitledPane adminSettingsSectionPane;
    private TitledPane adminUsersSectionPane;
    private TitledPane adminQuotaSectionPane;
    private TitledPane adminBandwidthSectionPane;
    private TitledPane adminPrivilegeSectionPane;
    private final ObservableList<QuotaRequest> pendingQuotaRequestItems = FXCollections.observableArrayList();
    private final ObservableList<BandwidthRequest> pendingBandwidthRequestItems = FXCollections.observableArrayList();
    private final ObservableList<AdminRequest> pendingAdminRequestItems = FXCollections.observableArrayList();

    private Label settingsAccountInfoLabel;
    private TextField settingsBaseUrlField;
    private TextField settingsDeviceNameField;
    private TextField settingsHistoryRetentionDaysField;
    private TextField settingsHistoryLimitField;
    private PasswordField settingsCurrentPasswordField;
    private PasswordField settingsNewPasswordField;
    private PasswordField settingsConfirmPasswordField;
    private CheckBox settingsSyncEnabledCheck;
    private CheckBox settingsAutoUploadClipboardFilesCheck;
    private CheckBox settingsAutoStartCheck;
    private CheckBox settingsStartInTrayCheck;

    private TrayIcon trayIcon;
    private MenuItem trayToggleSyncMenuItem;
    private boolean forceStartInTrayArg = false;

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage stage) {
        this.primaryStage = stage;
        this.forceStartInTrayArg = getParameters().getRaw().stream()
            .anyMatch(value -> "--start-in-tray".equalsIgnoreCase(value));

        loadImages();
        buildScenes();

        apiClient.setSessionExpiredListener(() -> Platform.runLater(() -> handleSessionExpired("登录已失效，请重新登录")));
        syncService = new ClipboardSyncService(
            apiClient,
            stateStore,
            message -> Platform.runLater(() -> showToast(message)),
            () -> Platform.runLater(this::handleSyncDataChanged)
        );
        syncService.start();

        if (appIconImage != null) {
            primaryStage.getIcons().add(appIconImage);
        }
        primaryStage.setTitle("ClipBridge");
        primaryStage.setMinWidth(1180);
        primaryStage.setMinHeight(760);

        setupTray();
        Platform.setImplicitExit(trayIcon == null);
        primaryStage.setOnCloseRequest(event -> {
            if (trayIcon != null) {
                event.consume();
                primaryStage.hide();
                showToast("已最小化到系统托盘");
            }
        });

        AppState state = stateStore.getState();
        if (state.isLoggedIn()) {
            showMainScene();
            showPage(NavPage.HISTORY, true);
            refreshTopBarFromState();
            if (forceStartInTrayArg && trayIcon != null) {
                primaryStage.hide();
                showToast("应用正在托盘中运行");
            }
        } else {
            showLoginScene();
        }
    }

    @Override
    public void stop() {
        if (trayIcon != null) {
            SystemTray.getSystemTray().remove(trayIcon);
            trayIcon = null;
        }
        if (syncService != null) {
            syncService.shutdown();
        }
        ioExecutor.shutdownNow();
    }

    private void buildScenes() {
        loginScene = new Scene(buildLoginRoot(), 1180, 760);
        mainRoot = buildMainRoot();
        mainScene = new Scene(mainRoot, WINDOW_WIDTH, WINDOW_HEIGHT);
        String stylesheet = Objects.requireNonNull(getClass().getResource("/css/app.css")).toExternalForm();
        loginScene.getStylesheets().add(stylesheet);
        mainScene.getStylesheets().add(stylesheet);
        buildSettingsStage(stylesheet);
        buildShareComposeStage(stylesheet);
    }

    private BorderPane buildLoginRoot() {
        BorderPane root = new BorderPane();
        root.getStyleClass().addAll("login-root", "login-stage");

        VBox formCard = new VBox(16);
        formCard.getStyleClass().addAll("card", "auth-card");
        formCard.setPadding(new Insets(28));
        formCard.setMaxWidth(520);

        loginModeLabel = new Label("登录");
        loginModeLabel.getStyleClass().add("section-title");

        loginHintLabel = new Label("输入账号后进入控制台。");
        loginHintLabel.getStyleClass().add("field-help");

        loginBaseUrlField = new TextField();
        loginBaseUrlField.setPromptText("服务地址，例如 https://clipbridge.example.com");

        loginUsernameField = new TextField();
        loginUsernameField.setPromptText("用户名");

        loginPasswordField = new PasswordField();
        loginPasswordField.setPromptText("密码");

        loginDeviceNameField = new TextField();
        loginDeviceNameField.setPromptText("设备名");
        loginDeviceNameField.setText(stateStore.getState().getDeviceName());

        loginSubmitButton = createPrimaryButton("登录");
        loginSubmitButton.setMaxWidth(Double.MAX_VALUE);
        loginSubmitButton.setOnAction(event -> submitAuth());

        loginSwitchModeButton = createGhostButton("切换到注册");
        loginSwitchModeButton.setMaxWidth(Double.MAX_VALUE);
        loginSwitchModeButton.setOnAction(event -> {
            registerMode = !registerMode;
            updateLoginModeText();
        });

        Button loginTestButton = createGhostButton("测试服务地址");
        loginTestButton.setOnAction(event -> runTask(
            () -> {
                apiClient.testConnection(loginBaseUrlField.getText());
                return null;
            },
            ignored -> showToast("服务连接正常"),
            error -> setLoginHint("服务测试失败: " + error.getMessage(), true)
        ));

        HBox authSecondaryActions = new HBox(10, loginTestButton, loginSwitchModeButton);
        authSecondaryActions.getStyleClass().add("auth-inline-actions");
        authSecondaryActions.setAlignment(Pos.CENTER_LEFT);

        formCard.getChildren().addAll(
            loginModeLabel,
            loginHintLabel,
            createLabeledField("服务地址", loginBaseUrlField),
            createLabeledField("用户名", loginUsernameField),
            createLabeledField("密码", loginPasswordField),
            createLabeledField("设备名", loginDeviceNameField),
            authSecondaryActions,
            loginSubmitButton
        );

        VBox authShell = new VBox(18);
        authShell.getStyleClass().add("auth-shell");
        authShell.setMaxWidth(520);
        authShell.setPadding(new Insets(36));

        HBox authBrandRow = new HBox(14);
        authBrandRow.getStyleClass().add("auth-brand-row");
        authBrandRow.setAlignment(Pos.CENTER_LEFT);
        if (appIconImage != null) {
            ImageView imageView = new ImageView(appIconImage);
            imageView.setFitHeight(44);
            imageView.setPreserveRatio(true);
            authBrandRow.getChildren().add(imageView);
        }
        VBox authBrandCopy = new VBox(2);
        authBrandCopy.getStyleClass().add("auth-brand-copy");
        Label authBrandTitle = new Label("ClipBridge");
        authBrandTitle.getStyleClass().add("auth-brand-title");
        Label authBrandSubtitle = new Label("Windows 登录");
        authBrandSubtitle.getStyleClass().add("auth-brand-subtitle");
        authBrandCopy.getChildren().addAll(authBrandTitle, authBrandSubtitle);
        authBrandRow.getChildren().add(authBrandCopy);

        authShell.getChildren().addAll(authBrandRow, formCard);
        StackPane centerPane = new StackPane(authShell);
        centerPane.setAlignment(Pos.CENTER);
        root.setCenter(centerPane);

        updateLoginModeText();
        applyLoginStateToForm();
        return root;
    }

    private BorderPane buildMainRoot() {
        BorderPane root = new BorderPane();
        root.getStyleClass().add("app-shell");

        // 主界面结构向 Web 端靠拢：左侧固定导航，右侧再承载顶部栏和页面内容。
        HBox shell = new HBox();
        shell.getStyleClass().add("app-frame");

        BorderPane appMain = new BorderPane();
        appMain.getStyleClass().add("app-main");
        appMain.setTop(buildTopBar());

        pageContainer = new StackPane();
        pageContainer.getStyleClass().add("page-container");
        pageContainer.setPadding(new Insets(24, 28, 32, 28));
        appMain.setCenter(pageContainer);

        Node sidebar = buildSidebar();
        shell.getChildren().addAll(sidebar, appMain);
        HBox.setHgrow(appMain, Priority.ALWAYS);
        root.setCenter(shell);
        root.setBottom(buildStatusBar());

        buildPages();
        return root;
    }

    private Node buildSidebar() {
        VBox sidebar = new VBox(10);
        sidebar.getStyleClass().add("sidebar");
        sidebar.setPadding(new Insets(24));
        sidebar.setPrefWidth(232);

        HBox brandRow = new HBox(12);
        brandRow.setAlignment(Pos.CENTER_LEFT);
        if (appIconImage != null) {
            ImageView imageView = new ImageView(appIconImage);
            imageView.setFitHeight(36);
            imageView.setPreserveRatio(true);
            brandRow.getChildren().add(imageView);
        }
        VBox brandCopy = new VBox(2);
        Label brandTitle = new Label("ClipBridge");
        brandTitle.getStyleClass().add("sidebar-brand");
        Label brandHint = new Label("Windows 控制台");
        brandHint.getStyleClass().add("sidebar-hint");
        brandCopy.getChildren().addAll(brandTitle, brandHint);
        brandRow.getChildren().add(brandCopy);

        sidebar.getChildren().addAll(brandRow, new Separator());

        for (NavPage page : NavPage.values()) {
            if (page == NavPage.OVERVIEW || page == NavPage.SETTINGS) {
                continue;
            }
            Button button = createNavButton(page);
            navButtons.put(page, button);
            if (page == NavPage.ADMIN) {
                adminNavButton = button;
            }
            sidebar.getChildren().add(button);
        }

        Region spacer = new Region();
        VBox.setVgrow(spacer, Priority.ALWAYS);
        Button settingsButton = createNavButton(NavPage.SETTINGS);
        settingsButton.getStyleClass().add("nav-button-settings");
        navButtons.put(NavPage.SETTINGS, settingsButton);
        sidebar.getChildren().addAll(spacer, new Separator(), settingsButton);
        return sidebar;
    }

    private Node buildTopBar() {
        BorderPane topBar = new BorderPane();
        topBar.getStyleClass().add("topbar");
        topBar.setPadding(new Insets(16, 24, 16, 24));

        topTitleLabel = new Label(currentPage.getLabel());
        topTitleLabel.getStyleClass().add("topbar-title");

        topUserLabel = createMetaValueLabel("未登录");
        topSyncToggleButton = createGhostButton("关闭同步");
        topSyncToggleButton.setOnAction(event -> toggleSyncEnabled(!stateStore.getState().isSyncEnabled()));

        HBox left = new HBox(topTitleLabel);
        left.setAlignment(Pos.CENTER_LEFT);

        HBox right = new HBox(12, topSyncToggleButton, topUserLabel);
        right.getStyleClass().add("topbar-user-group");
        right.setAlignment(Pos.CENTER_RIGHT);

        topBar.setLeft(left);
        topBar.setRight(right);
        return topBar;
    }

    private Node buildStatusBar() {
        HBox bar = new HBox(12);
        bar.getStyleClass().add("statusbar");
        bar.setPadding(new Insets(10, 24, 10, 24));
        globalStatusLabel = new Label("就绪");
        globalStatusLabel.getStyleClass().add("statusbar-text");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        statusSyncLabel = new Label("同步状态加载中");
        statusSyncLabel.getStyleClass().add("statusbar-meta");
        statusSyncToggleButton = createGhostButton("关闭同步");
        statusSyncToggleButton.setOnAction(event -> toggleSyncEnabled(!stateStore.getState().isSyncEnabled()));

        bar.getChildren().addAll(globalStatusLabel, spacer, statusSyncLabel, statusSyncToggleButton);
        return bar;
    }

    private void buildPages() {
        pageNodes.put(NavPage.HISTORY, buildHistoryPage());
        pageNodes.put(NavPage.DEVICES, buildDevicesPage());
        pageNodes.put(NavPage.FILES, buildFilesPage());
        pageNodes.put(NavPage.SHARES, buildSharesPage());
        pageNodes.put(NavPage.REQUESTS, buildRequestsPage());
        pageNodes.put(NavPage.ADMIN, buildAdminPage());
    }

    private Node buildOverviewPage() {
        VBox page = new VBox(20);
        page.getChildren().add(createPageHeader(NavPage.OVERVIEW, ""));

        overviewAccountValueLabel = new Label("--");
        overviewDeviceValueLabel = new Label("--");
        overviewSyncValueLabel = new Label("--");
        overviewQuotaValueLabel = new Label("--");
        overviewBandwidthValueLabel = new Label("--");
        overviewAckValueLabel = new Label("--");

        overviewSyncToggleButton = createPrimaryButton("关闭同步");
        overviewSyncToggleButton.setOnAction(event -> toggleSyncEnabled(!stateStore.getState().isSyncEnabled()));

        VBox accountCard = createSummaryCard("当前账号");
        accountCard.getChildren().addAll(
            createSummaryRow("用户", overviewAccountValueLabel),
            createSummaryRow("配额", overviewQuotaValueLabel),
            createSummaryRow("带宽", overviewBandwidthValueLabel)
        );

        VBox deviceCard = createSummaryCard("当前设备");
        deviceCard.getChildren().addAll(
            createSummaryRow("设备", overviewDeviceValueLabel),
            createSummaryRow("同步", overviewSyncValueLabel),
            createSummaryRow("ACK", overviewAckValueLabel)
        );

        HBox summaryGrid = new HBox(20, accountCard, deviceCard);
        HBox.setHgrow(accountCard, Priority.ALWAYS);
        HBox.setHgrow(deviceCard, Priority.ALWAYS);

        FlowPane moduleGrid = new FlowPane(16, 16);
        moduleGrid.getStyleClass().add("module-grid");

        Button openHistoryButton = createModuleTile(NavPage.HISTORY, "文本记录");
        openHistoryButton.setOnAction(event -> showPage(NavPage.HISTORY, true));

        Button openFilesButton = createModuleTile(NavPage.FILES, "文件列表");
        openFilesButton.setOnAction(event -> showPage(NavPage.FILES, true));

        Button openSharesButton = createModuleTile(NavPage.SHARES, "分享列表");
        openSharesButton.setOnAction(event -> showPage(NavPage.SHARES, true));

        Button openDevicesButton = createModuleTile(NavPage.DEVICES, "设备中心");
        openDevicesButton.setOnAction(event -> showPage(NavPage.DEVICES, true));

        Button openRequestsButton = createModuleTile(NavPage.REQUESTS, "申请记录");
        openRequestsButton.setOnAction(event -> showPage(NavPage.REQUESTS, true));

        Button openSettingsButton = createModuleTile(NavPage.SETTINGS, "桌面设置");
        openSettingsButton.setOnAction(event -> showPage(NavPage.SETTINGS, true));

        moduleGrid.getChildren().addAll(
            openHistoryButton,
            openDevicesButton,
            openFilesButton,
            openSharesButton,
            openRequestsButton,
            openSettingsButton
        );

        VBox moduleCard = createCardBox();
        Label moduleTitle = new Label("功能入口");
        moduleTitle.getStyleClass().add("section-title");
        moduleCard.getChildren().addAll(moduleTitle, moduleGrid);

        HBox actionRow = new HBox(12);
        actionRow.setAlignment(Pos.CENTER_LEFT);
        Button syncNowButton = createGhostButton("立即同步");
        syncNowButton.setOnAction(event -> {
            syncService.requestImmediateSync();
            showToast("已触发立即同步");
        });
        actionRow.getChildren().addAll(overviewSyncToggleButton, syncNowButton);

        page.getChildren().addAll(summaryGrid, moduleCard, actionRow);
        return wrapScrollablePage(page);
    }

    private Node buildHistoryPage() {
        BorderPane page = new BorderPane();
        VBox topSection = new VBox(16);
        topSection.getChildren().add(createPageHeader(NavPage.HISTORY, "历史同步记录"));

        HBox toolbar = new HBox(10);
        toolbar.setAlignment(Pos.CENTER_LEFT);
        Button refreshButton = createGhostButton("刷新");
        refreshButton.setOnAction(event -> refreshHistoryPageAsync(true));
        historyPrevButton = createGhostButton("上一页");
        historyPrevButton.setOnAction(event -> {
            if (historyPageIndex > 0) {
                historyPageIndex -= 1;
                refreshHistoryPageAsync(false);
            }
        });
        historyNextButton = createGhostButton("下一页");
        historyNextButton.setOnAction(event -> {
            if (historyHasNext) {
                historyPageIndex += 1;
                refreshHistoryPageAsync(false);
            }
        });
        historyPageLabel = createSecondaryValueLabel("第 1 页");
        toolbar.getChildren().addAll(refreshButton, historyPrevButton, historyNextButton, historyPageLabel);

        topSection.getChildren().add(toolbar);
        page.setTop(topSection);

        historyListView = new ListView<>(historyItems);
        historyListView.getStyleClass().add("data-list");
        historyListView.setCellFactory(listView -> new ListCell<>() {
            @Override
            protected void updateItem(ClipboardItem item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setGraphic(null);
                    return;
                }
                Label title = new Label(trimPreview(item.textContent(), 68));
                title.getStyleClass().add("list-item-title");
                Label meta = createSmallMutedLabel(
                    "seq " + item.seq() +
                        " · " + formatTime(item.createdAt()) +
                        " · 来源 " + nonBlank(item.originDeviceId(), "未知设备")
                );
                VBox box = new VBox(6, title, meta);
                box.getStyleClass().add("list-item-card");
                setGraphic(box);
            }
        });
        historyListView.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, newValue) -> updateHistoryDetail(newValue));

        VBox manualCard = createCardBox();
        Label manualTitle = new Label("手动上传文本");
        manualTitle.getStyleClass().add("section-title");
        historyManualUploadArea = new TextArea();
        historyManualUploadArea.setPromptText("粘贴或输入一段文本，点击按钮后会直接上传到历史记录。");
        historyManualUploadArea.setPrefRowCount(5);
        Button manualUploadButton = createPrimaryButton("上传文本");
        manualUploadButton.setOnAction(event -> submitManualHistoryText());
        manualCard.getChildren().addAll(manualTitle, historyManualUploadArea, manualUploadButton);

        StackPane listPane = createListPane(historyListView, historyItems, createEmptyState(
            emptyHistoryImage,
            "还没有历史记录",
            "你可以先复制一段文本，或者在上方手动上传一条记录。",
            "立即同步",
            () -> syncService.requestImmediateSync()
        ));

        VBox listColumn = new VBox(16, manualCard, listPane);
        VBox.setVgrow(listPane, Priority.ALWAYS);

        VBox detailColumn = createCardBox();
        Label detailTitle = new Label("详情");
        detailTitle.getStyleClass().add("section-title");
        historyDetailMetaLabel = createSecondaryValueLabel("选择左侧记录后显示完整内容。");
        historyDetailTextArea = new TextArea();
        historyDetailTextArea.setEditable(false);
        historyDetailTextArea.setWrapText(true);
        historyDetailTextArea.setPrefRowCount(18);

        Button copyButton = createGhostButton("复制当前记录");
        copyButton.setOnAction(event -> {
            ClipboardItem selected = historyListView.getSelectionModel().getSelectedItem();
            if (selected != null) {
                writeTextToClipboard(selected.textContent());
                showToast("历史文本已复制到系统剪贴板");
            }
        });
        historyDeleteButton = createDangerButton("删除当前记录");
        historyDeleteButton.setDisable(true);
        historyDeleteButton.setOnAction(event -> deleteSelectedHistoryItem());
        HBox detailActions = new HBox(10, copyButton, historyDeleteButton);
        detailColumn.getChildren().addAll(detailTitle, historyDetailMetaLabel, historyDetailTextArea, detailActions);

        SplitPane splitPane = new SplitPane(listColumn, detailColumn);
        splitPane.setDividerPositions(0.62);
        splitPane.setOrientation(Orientation.HORIZONTAL);
        page.setCenter(splitPane);

        historyPageBeforeSeqs.clear();
        historyPageBeforeSeqs.add(0L);
        return page;
    }

    private Node buildDevicesPage() {
        BorderPane page = new BorderPane();
        VBox topSection = new VBox(16);
        topSection.getChildren().add(createPageHeader(NavPage.DEVICES, "设备列表"));

        HBox toolbar = new HBox(10);
        toolbar.setAlignment(Pos.CENTER_LEFT);
        Button refreshButton = createGhostButton("刷新");
        refreshButton.setOnAction(event -> refreshDevicesPageAsync(devicePage));
        devicePrevButton = createGhostButton("上一页");
        devicePrevButton.setOnAction(event -> refreshDevicesPageAsync(Math.max(1, devicePage - 1)));
        deviceNextButton = createGhostButton("下一页");
        deviceNextButton.setOnAction(event -> refreshDevicesPageAsync(Math.min(deviceTotalPages, devicePage + 1)));
        devicePageLabel = createSecondaryValueLabel("第 1 / 1 页");
        toolbar.getChildren().addAll(refreshButton, devicePrevButton, deviceNextButton, devicePageLabel);
        topSection.getChildren().add(toolbar);
        page.setTop(topSection);

        deviceListView = new ListView<>(deviceItems);
        deviceListView.getStyleClass().add("data-list");
        deviceListView.setCellFactory(listView -> new ListCell<>() {
            @Override
            protected void updateItem(DeviceInfo item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setGraphic(null);
                    return;
                }
                boolean isCurrent = item.isCurrent(stateStore.getState().getCurrentDeviceId());
                Label title = new Label(item.deviceName() + (isCurrent ? " · 当前设备" : ""));
                title.getStyleClass().add("list-item-title");
                Label meta = createSmallMutedLabel(item.platform() + " · " + (item.isActive() ? "在线" : "已下线") + " · 最近在线 " + formatTime(item.lastSeenAt()));
                VBox box = new VBox(6, title, meta);
                box.getStyleClass().add("list-item-card");
                if (isCurrent) {
                    box.getStyleClass().add("is-current-device");
                }
                setGraphic(box);
            }
        });
        deviceListView.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, newValue) -> updateDeviceDetail(newValue));

        StackPane listPane = createListPane(deviceListView, deviceItems, createEmptyState(
            emptyDevicesImage,
            "暂无设备记录",
            "登录后会自动登记当前设备。",
            "刷新设备列表",
            () -> refreshDevicesPageAsync(1)
        ));

        VBox detailColumn = createCardBox();
        Label detailTitle = new Label("设备详情");
        detailTitle.getStyleClass().add("section-title");
        deviceDetailTitleLabel = createSecondaryValueLabel("请选择一个设备");
        deviceDetailMetaLabel = createSmallMutedLabel("显示设备信息。");
        deviceRenameField = new TextField();
        deviceRenameField.setPromptText("新的设备名");
        deviceSaveButton = createPrimaryButton("保存设备名");
        deviceSaveButton.setOnAction(event -> renameSelectedDevice());
        deviceOfflineButton = createDangerButton("强制下线");
        deviceOfflineButton.setOnAction(event -> forceOfflineSelectedDevice());
        detailColumn.getChildren().addAll(detailTitle, deviceDetailTitleLabel, deviceDetailMetaLabel, createLabeledField("设备名", deviceRenameField), deviceSaveButton, deviceOfflineButton);

        SplitPane splitPane = new SplitPane(listPane, detailColumn);
        splitPane.setDividerPositions(0.62);
        page.setCenter(splitPane);
        return page;
    }

    private Node buildFilesPage() {
        BorderPane page = new BorderPane();
        VBox topSection = new VBox(16);
        topSection.getChildren().add(createPageHeader(NavPage.FILES, "文件列表"));
        fileSummaryFilesLabel = new Label("--");
        fileSummaryBytesLabel = new Label("--");
        fileSummaryLimitLabel = new Label("--");

        HBox toolbar = new HBox(10);
        toolbar.setAlignment(Pos.CENTER_LEFT);
        Button chooseFilesButton = createPrimaryButton("选择文件上传");
        chooseFilesButton.setOnAction(event -> chooseFilesForUpload());
        Button refreshButton = createGhostButton("刷新");
        refreshButton.setOnAction(event -> refreshFilesPageAsync(filePage));
        filePrevButton = createGhostButton("上一页");
        filePrevButton.setOnAction(event -> refreshFilesPageAsync(Math.max(1, filePage - 1)));
        fileNextButton = createGhostButton("下一页");
        fileNextButton.setOnAction(event -> refreshFilesPageAsync(Math.min(fileTotalPages, filePage + 1)));
        filePageLabel = createSecondaryValueLabel("第 1 / 1 页");
        toolbar.getChildren().addAll(chooseFilesButton, refreshButton, filePrevButton, fileNextButton, filePageLabel);

        VBox uploadDropCard = createCardBox();
        Label dropTitle = new Label("拖拽上传");
        dropTitle.getStyleClass().add("section-title");
        fileDropHintLabel = createSecondaryValueLabel("拖到这里，或点击上方按钮选择文件。");
        uploadDropCard.getChildren().addAll(dropTitle, fileDropHintLabel);
        uploadDropCard.getStyleClass().add("dropzone-card");
        configureFileDropZone(uploadDropCard, paths -> uploadFilesAsync(paths, true));

        topSection.getChildren().addAll(toolbar, uploadDropCard);
        page.setTop(topSection);

        fileListView = new ListView<>(fileItems);
        fileListView.getStyleClass().add("data-list");
        fileListView.setCellFactory(listView -> new ListCell<>() {
            @Override
            protected void updateItem(FileItem item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setGraphic(null);
                    return;
                }
                Label title = new Label(item.originalName());
                title.getStyleClass().add("list-item-title");
                Label meta = createSmallMutedLabel(formatBytes(item.sizeBytes()) + " · " + item.contentType() + " · " + formatTime(item.createdAt()));
                VBox box = new VBox(6, title, meta);
                box.getStyleClass().add("list-item-card");
                setGraphic(box);
            }
        });
        fileListView.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, newValue) -> updateFileDetail(newValue));

        StackPane listPane = createListPane(fileListView, fileItems, createEmptyState(
            emptyFilesImage,
            "文件列表还是空的",
            "上传后会显示在这里。",
            "选择文件上传",
            this::chooseFilesForUpload
        ));

        VBox detailColumn = createCardBox();
        Label detailTitle = new Label("文件详情");
        detailTitle.getStyleClass().add("section-title");
        fileDetailTitleLabel = createSecondaryValueLabel("请选择一个文件");
        fileDetailMetaLabel = createSmallMutedLabel("右侧可以下载、重命名或删除文件。");
        fileRenameField = new TextField();
        fileRenameField.setPromptText("新的文件显示名");
        fileDownloadButton = createGhostButton("下载文件");
        fileDownloadButton.setOnAction(event -> downloadSelectedFile());
        fileRenameButton = createPrimaryButton("保存新文件名");
        fileRenameButton.setOnAction(event -> renameSelectedFile());
        fileDeleteButton = createDangerButton("删除文件");
        fileDeleteButton.setOnAction(event -> deleteSelectedFile());
        detailColumn.getChildren().addAll(
            detailTitle,
            fileDetailTitleLabel,
            fileDetailMetaLabel,
            createLabeledField("文件名", fileRenameField),
            fileDownloadButton,
            fileRenameButton,
            fileDeleteButton
        );

        SplitPane splitPane = new SplitPane(listPane, detailColumn);
        splitPane.setDividerPositions(0.62);
        page.setCenter(splitPane);
        return page;
    }

    private Node buildSharesPage() {
        BorderPane page = new BorderPane();
        VBox topSection = new VBox(16);
        topSection.getChildren().add(createPageHeader(NavPage.SHARES, "分享列表"));

        shareStatusFilterBox = new ComboBox<>(FXCollections.observableArrayList(ShareRules.StatusFilter.values()));
        shareStatusFilterBox.setValue(ShareRules.StatusFilter.ALL);
        shareStatusFilterBox.setOnAction(event -> refreshSharesPageAsync(1));

        HBox listToolbar = new HBox(10);
        listToolbar.setAlignment(Pos.CENTER_LEFT);
        Button shareRefreshButton = createGhostButton("刷新");
        shareRefreshButton.setOnAction(event -> refreshSharesPageAsync(sharePage));
        sharePrevButton = createGhostButton("上一页");
        sharePrevButton.setOnAction(event -> refreshSharesPageAsync(Math.max(1, sharePage - 1)));
        shareNextButton = createGhostButton("下一页");
        shareNextButton.setOnAction(event -> refreshSharesPageAsync(Math.min(shareTotalPages, sharePage + 1)));
        sharePageLabel = createSecondaryValueLabel("第 1 / 1 页");
        shareComposeToggleButton = createIconButton("创建分享");
        shareComposeToggleButton.setGraphic(createShareIconGraphic());
        shareComposeToggleButton.setOnAction(event -> openShareComposeModal());
        Region shareToolbarSpacer = new Region();
        HBox.setHgrow(shareToolbarSpacer, Priority.ALWAYS);
        listToolbar.getChildren().addAll(
            shareStatusFilterBox,
            shareRefreshButton,
            sharePrevButton,
            shareNextButton,
            sharePageLabel,
            shareToolbarSpacer,
            shareComposeToggleButton
        );

        topSection.getChildren().add(listToolbar);
        page.setTop(topSection);

        shareListView = new ListView<>(shareItems);
        shareListView.getStyleClass().add("data-list");
        shareListView.setCellFactory(listView -> new ListCell<>() {
            @Override
            protected void updateItem(ShareItem item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setGraphic(null);
                    return;
                }
                String titleText = item.hasFileContent() && item.file() != null
                    ? item.file().originalName()
                    : trimPreview(item.textPreview(), 56);
                Label title = new Label(nonBlank(titleText, "未命名分享"));
                title.getStyleClass().add("list-item-title");
                Label meta = createSmallMutedLabel(
                    statusText(item.status()) + " · " +
                        ("countdown".equalsIgnoreCase(item.burnMode()) ? "倒计时焚毁" : burnModeText(item.burnMode())) +
                        " · 创建于 " + formatTime(item.createdAt())
                );
                VBox box = new VBox(6, title, meta);
                box.getStyleClass().add("list-item-card");
                setGraphic(box);
            }
        });
        shareListView.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, newValue) -> updateShareDetail(newValue));

        StackPane listPane = createListPane(shareListView, shareItems, createEmptyState(
            emptySharesImage,
            "还没有分享记录",
            "创建后会显示在这里。",
            "创建分享",
            this::openShareComposeModal
        ));

        VBox detailColumn = createCardBox();
        Label detailTitle = new Label("分享详情");
        detailTitle.getStyleClass().add("section-title");
        shareDetailTitleLabel = createSecondaryValueLabel("请选择一个分享");
        shareDetailMetaLabel = createSmallMutedLabel("显示分享信息和预览。");
        shareDetailPreviewArea = new TextArea();
        shareDetailPreviewArea.setEditable(false);
        shareDetailPreviewArea.setWrapText(true);
        shareDetailPreviewArea.setPrefRowCount(12);
        shareCopyLinkButton = createGhostButton("复制公开链接");
        shareCopyLinkButton.setOnAction(event -> {
            ShareItem selected = shareListView.getSelectionModel().getSelectedItem();
            if (selected != null) {
                String link = PublicShareLinkBuilder.build(stateStore.getState().getBaseUrl(), selected.token());
                copyShareLink(link);
            }
        });
        shareOpenLinkButton = createGhostButton("在浏览器中打开");
        shareOpenLinkButton.setOnAction(event -> {
            ShareItem selected = shareListView.getSelectionModel().getSelectedItem();
            if (selected != null) {
                String link = PublicShareLinkBuilder.build(stateStore.getState().getBaseUrl(), selected.token());
                openShareLink(link);
            }
        });
        shareRevokeButton = createDangerButton("撤销分享");
        shareRevokeButton.setOnAction(event -> revokeSelectedShare());
        detailColumn.getChildren().addAll(detailTitle, shareDetailTitleLabel, shareDetailMetaLabel, shareDetailPreviewArea, shareCopyLinkButton, shareOpenLinkButton, shareRevokeButton);

        SplitPane splitPane = new SplitPane(listPane, detailColumn);
        splitPane.setDividerPositions(0.62);
        page.setCenter(splitPane);
        refreshShareStrategySummary();
        refreshShareComposeMode();
        refreshShareComposeModalState();
        return page;
    }

    private Node buildRequestsPage() {
        VBox page = new VBox(18);
        page.getChildren().add(createPageHeader(NavPage.REQUESTS, "申请记录"));

        requestStatusFilterBox = new ComboBox<>(FXCollections.observableArrayList("all", "pending", "approved", "rejected"));
        requestStatusFilterBox.setValue("all");
        Button refreshButton = createGhostButton("刷新申请记录");
        refreshButton.setOnAction(event -> refreshRequestsPageAsync());
        HBox toolbar = new HBox(10, requestStatusFilterBox, refreshButton);
        toolbar.setAlignment(Pos.CENTER_LEFT);

        requestOverviewLabel = createSecondaryValueLabel("加载后会显示各类申请的当前数量。");

        requestQuotaField = new TextField();
        requestQuotaField.setPromptText("配额 MB");
        requestQuotaReasonArea = new TextArea();
        requestQuotaReasonArea.setPromptText("为什么需要更多存储");
        requestQuotaReasonArea.setPrefRowCount(3);
        Button submitQuotaButton = createPrimaryButton("提交配额申请");
        submitQuotaButton.setOnAction(event -> submitQuotaRequest());

        requestUploadField = new TextField();
        requestUploadField.setPromptText("上传带宽 MB/s");
        requestDownloadField = new TextField();
        requestDownloadField.setPromptText("下载带宽 MB/s");
        requestBandwidthReasonArea = new TextArea();
        requestBandwidthReasonArea.setPromptText("为什么需要调整带宽");
        requestBandwidthReasonArea.setPrefRowCount(3);
        Button submitBandwidthButton = createPrimaryButton("提交带宽申请");
        submitBandwidthButton.setOnAction(event -> submitBandwidthRequest());

        requestAdminReasonArea = new TextArea();
        requestAdminReasonArea.setPromptText("为什么申请管理员权限");
        requestAdminReasonArea.setPrefRowCount(3);
        Button submitAdminButton = createPrimaryButton("提交管理员申请");
        submitAdminButton.setOnAction(event -> submitAdminRequest());

        quotaRequestListView = new ListView<>(quotaRequestItems);
        quotaRequestListView.setCellFactory(listView -> buildQuotaRequestCell());
        bandwidthRequestListView = new ListView<>(bandwidthRequestItems);
        bandwidthRequestListView.setCellFactory(listView -> buildBandwidthRequestCell());
        adminRequestListView = new ListView<>(adminRequestItems);
        adminRequestListView.setCellFactory(listView -> buildAdminRequestCell());

        page.getChildren().addAll(
            toolbar,
            requestOverviewLabel,
            createRequestCard("配额申请", List.of(createLabeledField("申请配额", requestQuotaField), requestQuotaReasonArea, submitQuotaButton)),
            createRequestCard("带宽申请", List.of(createLabeledField("申请上传 / 下载带宽", new HBox(10, requestUploadField, requestDownloadField)), requestBandwidthReasonArea, submitBandwidthButton)),
            createRequestCard("管理员申请", List.of(requestAdminReasonArea, submitAdminButton)),
            createRequestCard("我的配额申请记录", List.of(createListPane(quotaRequestListView, quotaRequestItems, createEmptyState(emptyRequestsImage, "暂无配额申请", "提交申请后会显示在这里。", "刷新", this::refreshRequestsPageAsync)))),
            createRequestCard("我的带宽申请记录", List.of(createListPane(bandwidthRequestListView, bandwidthRequestItems, createEmptyState(emptyRequestsImage, "暂无带宽申请", "提交申请后会显示在这里。", "刷新", this::refreshRequestsPageAsync)))),
            createRequestCard("我的管理员申请记录", List.of(createListPane(adminRequestListView, adminRequestItems, createEmptyState(emptyRequestsImage, "暂无管理员申请", "提交申请后会显示在这里。", "刷新", this::refreshRequestsPageAsync))))
        );
        return wrapScrollablePage(page);
    }

    private Node buildAdminPage() {
        VBox page = new VBox(18);
        page.getChildren().add(createPageHeader(NavPage.ADMIN, "管理员操作"));

        Button refreshButton = createGhostButton("刷新管理员数据");
        refreshButton.setOnAction(event -> refreshAdminPageAsync());

        AccordionWithContent adminAccordion = buildAdminAccordion();
        page.getChildren().addAll(refreshButton, adminAccordion.root());
        return wrapScrollablePage(page);
    }

    private AccordionWithContent buildAdminAccordion() {
        VBox accordion = new VBox(14);

        adminMaxUsersField = new TextField();
        adminDefaultQuotaField = new TextField();
        adminDefaultUploadField = new TextField();
        adminDefaultDownloadField = new TextField();
        adminMaxUserUploadField = new TextField();
        adminMaxUserDownloadField = new TextField();
        adminMaxUploadFileField = new TextField();
        adminAllowRegistrationCheck = new CheckBox("允许公开注册");
        adminSettingsMetaLabel = createSecondaryValueLabel("显示当前系统设置。");
        Button saveSettingsButton = createPrimaryButton("保存系统设置");
        saveSettingsButton.setOnAction(event -> saveAdminSettings());

        VBox settingsContent = createCardBox();
        settingsContent.getChildren().addAll(
            adminSettingsMetaLabel,
            createLabeledField("最大用户数", adminMaxUsersField),
            createLabeledField("默认存储配额 MB", adminDefaultQuotaField),
            createLabeledField("默认上传带宽 MB/s", adminDefaultUploadField),
            createLabeledField("默认下载带宽 MB/s", adminDefaultDownloadField),
            createLabeledField("用户上传上限 MB/s", adminMaxUserUploadField),
            createLabeledField("用户下载上限 MB/s", adminMaxUserDownloadField),
            createLabeledField("单文件上传上限 MB", adminMaxUploadFileField),
            adminAllowRegistrationCheck,
            saveSettingsButton
        );

        adminUserListView = new ListView<>(adminUserItems);
        adminUserListView.setCellFactory(listView -> new ListCell<>() {
            @Override
            protected void updateItem(AdminUser item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setGraphic(null);
                    return;
                }
                Label title = new Label(item.username() + (item.isAdmin() ? " · 管理员" : ""));
                title.getStyleClass().add("list-item-title");
                Label meta = createSmallMutedLabel(
                    "配额 " + formatBytes(item.storageQuotaBytes()) +
                        " · 带宽 " + BandwidthUnitUtils.formatBandwidth(item.uploadBandwidthKbps()) + " / " + BandwidthUnitUtils.formatBandwidth(item.downloadBandwidthKbps())
                );
                VBox box = new VBox(6, title, meta);
                box.getStyleClass().add("list-item-card");
                setGraphic(box);
            }
        });
        adminUserListView.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, newValue) -> updateAdminUserDetail(newValue));

        adminUserDetailTitleLabel = createSecondaryValueLabel("请选择一个用户");
        adminUserDetailMetaLabel = createSmallMutedLabel("右侧可修改额度、带宽或管理员标记。");
        adminUserQuotaField = new TextField();
        adminUserUploadField = new TextField();
        adminUserDownloadField = new TextField();
        adminUserAdminCheck = new CheckBox("设为管理员");
        adminUserSaveButton = createPrimaryButton("保存用户设置");
        adminUserSaveButton.setOnAction(event -> saveSelectedAdminUser());
        adminUserDeleteButton = createDangerButton("删除用户");
        adminUserDeleteButton.setOnAction(event -> deleteSelectedAdminUser());

        VBox adminUserDetail = createCardBox();
        adminUserDetail.getChildren().addAll(
            adminUserDetailTitleLabel,
            adminUserDetailMetaLabel,
            createLabeledField("存储配额 MB", adminUserQuotaField),
            createLabeledField("上传带宽 MB/s", adminUserUploadField),
            createLabeledField("下载带宽 MB/s", adminUserDownloadField),
            adminUserAdminCheck,
            adminUserSaveButton,
            adminUserDeleteButton
        );

        SplitPane userSplit = new SplitPane(
            createListPane(adminUserListView, adminUserItems, createEmptyState(emptyRequestsImage, "暂无用户数据", "刷新后加载用户列表。", "刷新", this::refreshAdminPageAsync)),
            adminUserDetail
        );
        userSplit.setDividerPositions(0.58);

        ListView<QuotaRequest> quotaReviewListView = new ListView<>(pendingQuotaRequestItems);
        quotaReviewListView.setCellFactory(listView -> new QuotaReviewCell());
        ListView<BandwidthRequest> bandwidthReviewListView = new ListView<>(pendingBandwidthRequestItems);
        bandwidthReviewListView.setCellFactory(listView -> new BandwidthReviewCell());
        ListView<AdminRequest> adminReviewListView = new ListView<>(pendingAdminRequestItems);
        adminReviewListView.setCellFactory(listView -> new AdminReviewCell());

        adminSettingsSectionPane = createSection("系统设置", "全局默认值、上限和注册开关", settingsContent, false);
        adminUsersSectionPane = createSection("用户管理", "更新用户额度、带宽和管理员身份", userSplit, false);
        adminQuotaSectionPane = createSection("配额审批", "批准或拒绝用户的存储配额申请", createListPane(quotaReviewListView, pendingQuotaRequestItems, createEmptyState(emptyRequestsImage, "当前没有待审批配额申请", "待处理记录为空时会显示此状态。", "刷新", this::refreshAdminPageAsync)), false);
        adminBandwidthSectionPane = createSection("带宽审批", "批准或拒绝用户的上传 / 下载带宽申请", createListPane(bandwidthReviewListView, pendingBandwidthRequestItems, createEmptyState(emptyRequestsImage, "当前没有待审批带宽申请", "待处理记录为空时会显示此状态。", "刷新", this::refreshAdminPageAsync)), false);
        adminPrivilegeSectionPane = createSection("管理员审批", "批准或拒绝管理员权限申请", createListPane(adminReviewListView, pendingAdminRequestItems, createEmptyState(emptyRequestsImage, "当前没有待审批管理员申请", "待处理记录为空时会显示此状态。", "刷新", this::refreshAdminPageAsync)), false);

        accordion.getChildren().addAll(
            adminSettingsSectionPane,
            adminUsersSectionPane,
            adminQuotaSectionPane,
            adminBandwidthSectionPane,
            adminPrivilegeSectionPane
        );
        updateAdminSectionTitles();

        return new AccordionWithContent(accordion);
    }

    private void buildSettingsStage(String stylesheet) {
        initializeSettingsControls();

        VBox navPane = new VBox(8);
        navPane.getStyleClass().add("settings-dialog-nav");

        for (SettingsModule module : SettingsModule.values()) {
            Button button = new Button(module.getTitle());
            button.getStyleClass().add("settings-dialog-nav-button");
            button.setMaxWidth(Double.MAX_VALUE);
            button.setAlignment(Pos.CENTER_LEFT);
            button.setOnAction(event -> showSettingsModule(module));
            settingsModuleButtons.put(module, button);
            navPane.getChildren().add(button);
        }

        settingsDialogTitleLabel = new Label("设置");
        settingsDialogTitleLabel.getStyleClass().add("settings-dialog-title");

        Button closeButton = createGhostButton("关闭");
        closeButton.setOnAction(event -> closeSettingsModal());

        HBox headerRow = new HBox(12, settingsDialogTitleLabel, new Region(), closeButton);
        headerRow.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(headerRow.getChildren().get(1), Priority.ALWAYS);

        settingsDialogNoticeLabel = new Label();
        settingsDialogNoticeLabel.getStyleClass().add("page-notice");
        settingsDialogNoticeLabel.setVisible(false);
        settingsDialogNoticeLabel.setManaged(false);

        settingsDialogContentBox = new VBox(16);

        ScrollPane contentScroll = new ScrollPane(settingsDialogContentBox);
        contentScroll.setFitToWidth(true);
        contentScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        contentScroll.getStyleClass().addAll("page-scroll", "settings-dialog-scroll");

        VBox contentPane = new VBox(16, headerRow, settingsDialogNoticeLabel, contentScroll);
        contentPane.getStyleClass().add("settings-dialog-content");
        VBox.setVgrow(contentScroll, Priority.ALWAYS);

        HBox body = new HBox(navPane, contentPane);
        body.getStyleClass().add("settings-dialog-body");
        HBox.setHgrow(contentPane, Priority.ALWAYS);

        StackPane root = new StackPane(body);
        root.getStyleClass().add("settings-dialog-root");
        root.setPadding(new Insets(20));

        Scene scene = new Scene(root, 920, 680);
        scene.getStylesheets().add(stylesheet);

        settingsStage = new Stage();
        settingsStage.initOwner(primaryStage);
        settingsStage.initModality(Modality.WINDOW_MODAL);
        settingsStage.setTitle("设置");
        settingsStage.setMinWidth(860);
        settingsStage.setMinHeight(620);
        if (appIconImage != null) {
            settingsStage.getIcons().add(appIconImage);
        }
        settingsStage.setScene(scene);
        settingsStage.setOnHidden(event -> clearSettingsNotice());
        showSettingsModule(activeSettingsModule);
    }

    private void buildShareComposeStage(String stylesheet) {
        initializeShareComposeControls();

        Label titleLabel = new Label("创建分享");
        titleLabel.getStyleClass().add("settings-dialog-title");

        Button closeButton = createGhostButton("关闭");
        closeButton.setOnAction(event -> closeShareComposeModal());

        HBox headerRow = new HBox(12, titleLabel, new Region(), closeButton);
        headerRow.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(headerRow.getChildren().get(1), Priority.ALWAYS);

        shareComposeNoticeLabel = new Label();
        shareComposeNoticeLabel.getStyleClass().add("page-notice");
        shareComposeNoticeLabel.setVisible(false);
        shareComposeNoticeLabel.setManaged(false);

        ScrollPane contentScroll = new ScrollPane(buildShareComposeDialogContent());
        contentScroll.setFitToWidth(true);
        contentScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        contentScroll.getStyleClass().addAll("page-scroll", "settings-dialog-scroll");

        VBox contentPane = new VBox(16, headerRow, shareComposeNoticeLabel, contentScroll);
        contentPane.getStyleClass().add("settings-dialog-content");
        VBox.setVgrow(contentScroll, Priority.ALWAYS);

        VBox body = new VBox(contentPane);
        body.getStyleClass().add("settings-dialog-body");
        body.setMaxWidth(760);
        body.setMaxHeight(620);

        StackPane root = new StackPane(body);
        root.getStyleClass().add("settings-dialog-root");
        root.setPadding(new Insets(20));

        Scene scene = new Scene(root, 760, 620);
        scene.getStylesheets().add(stylesheet);

        shareComposeStage = new Stage();
        shareComposeStage.initOwner(primaryStage);
        shareComposeStage.initModality(Modality.WINDOW_MODAL);
        shareComposeStage.setTitle("创建分享");
        shareComposeStage.setMinWidth(720);
        shareComposeStage.setMinHeight(580);
        if (appIconImage != null) {
            shareComposeStage.getIcons().add(appIconImage);
        }
        shareComposeStage.setScene(scene);
        shareComposeStage.setOnHidden(event -> clearShareComposeNotice());
    }

    private void initializeShareComposeControls() {
        ToggleGroup composeModeGroup = new ToggleGroup();
        shareTextModeRadio = createChoiceButton("文本分享", composeModeGroup, selectedShareComposeMode == ShareRules.ComposeMode.TEXT);
        shareFileModeRadio = createChoiceButton("文件分享", composeModeGroup, selectedShareComposeMode == ShareRules.ComposeMode.FILE);
        composeModeGroup.selectedToggleProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue == shareFileModeRadio) {
                selectedShareComposeMode = ShareRules.ComposeMode.FILE;
            } else {
                selectedShareComposeMode = ShareRules.ComposeMode.TEXT;
            }
            refreshShareComposeMode();
        });

        ToggleGroup strategyGroup = new ToggleGroup();
        shareNeverStrategyRadio = createChoiceButton(
            ShareRules.StrategyKey.NEVER.getLabel(),
            strategyGroup,
            selectedShareStrategy == ShareRules.StrategyKey.NEVER
        );
        shareExpireStrategyRadio = createChoiceButton(
            ShareRules.StrategyKey.EXPIRE.getLabel(),
            strategyGroup,
            selectedShareStrategy == ShareRules.StrategyKey.EXPIRE
        );
        shareOnceStrategyRadio = createChoiceButton(
            ShareRules.StrategyKey.ONCE.getLabel(),
            strategyGroup,
            selectedShareStrategy == ShareRules.StrategyKey.ONCE
        );
        strategyGroup.selectedToggleProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue == shareNeverStrategyRadio) {
                selectedShareStrategy = ShareRules.StrategyKey.NEVER;
            } else if (newValue == shareOnceStrategyRadio) {
                selectedShareStrategy = ShareRules.StrategyKey.ONCE;
            } else {
                selectedShareStrategy = ShareRules.StrategyKey.EXPIRE;
            }
            refreshShareStrategySummary();
        });

        shareTextContentArea = new TextArea();
        shareTextContentArea.setPromptText("输入要分享的文字内容");
        shareTextContentArea.setPrefRowCount(6);

        shareSelectedFileLabel = createSecondaryValueLabel("未选择文件");
        shareStrategySummaryLabel = createSecondaryValueLabel("--");
        shareLatestLinkLabel = createSmallMutedLabel("最近生成的链接会显示在这里。");
    }

    private Node buildShareComposeDialogContent() {
        Button chooseFileButton = createGhostButton("选择文件");
        chooseFileButton.setOnAction(event -> chooseShareFile());
        Button clearFileButton = createGhostButton("清空文件");
        clearFileButton.setOnAction(event -> applySelectedShareFile(null));

        Button createShareButton = createPrimaryButton("生成分享");
        createShareButton.setOnAction(event -> createShareAsync());

        Button copyLatestLinkButton = createGhostButton("复制最近链接");
        copyLatestLinkButton.setOnAction(event -> copyShareLink(shareLatestLinkLabel.getText()));
        Button openLatestLinkButton = createGhostButton("打开最近链接");
        openLatestLinkButton.setOnAction(event -> openShareLink(shareLatestLinkLabel.getText()));

        HBox composeModeRow = new HBox(10, shareTextModeRadio, shareFileModeRadio);
        HBox strategyRow = new HBox(10, shareNeverStrategyRadio, shareExpireStrategyRadio, shareOnceStrategyRadio);
        HBox fileRow = new HBox(10, chooseFileButton, clearFileButton, shareSelectedFileLabel);
        fileRow.setAlignment(Pos.CENTER_LEFT);
        shareTextContentFieldRow = createLabeledField("文本内容", shareTextContentArea);
        shareFileFieldRow = createLabeledField("文件", fileRow);

        VBox composeCard = createCardBox();
        composeCard.getChildren().addAll(
            createLabeledField("分享类型", composeModeRow),
            createLabeledField("分享策略", strategyRow),
            shareStrategySummaryLabel,
            shareTextContentFieldRow,
            shareFileFieldRow,
            createShareButton,
            new HBox(10, copyLatestLinkButton, openLatestLinkButton),
            shareLatestLinkLabel
        );
        configureFileDropZone(composeCard, paths -> {
            if (!paths.isEmpty()) {
                // 中文注释：拖入文件时直接切到文件分享模式，减少一次额外点击。
                applySelectedShareFile(paths.get(0));
            }
        });
        return composeCard;
    }

    private void initializeSettingsControls() {
        settingsAccountInfoLabel = createSecondaryValueLabel("--");
        settingsBaseUrlField = new TextField();
        settingsBaseUrlField.setPromptText("服务地址");
        settingsDeviceNameField = new TextField();
        settingsDeviceNameField.setPromptText("设备名");
        settingsHistoryRetentionDaysField = new TextField("0");
        settingsHistoryRetentionDaysField.setPromptText("0 表示不限时间");
        settingsHistoryLimitField = new TextField("1000");
        settingsHistoryLimitField.setPromptText("最多保留条数");

        settingsCurrentPasswordField = new PasswordField();
        settingsCurrentPasswordField.setPromptText("当前密码");
        settingsNewPasswordField = new PasswordField();
        settingsNewPasswordField.setPromptText("新密码");
        settingsConfirmPasswordField = new PasswordField();
        settingsConfirmPasswordField.setPromptText("确认新密码");

        settingsSyncEnabledCheck = new CheckBox("启用文本同步");
        settingsAutoUploadClipboardFilesCheck = new CheckBox("复制文件也自动上传");
        settingsAutoStartCheck = new CheckBox("开机自启");
        settingsStartInTrayCheck = new CheckBox("启动时进入托盘");

        shareNeverAllowCopyCheck = new CheckBox("允许公开页复制文字");
        shareExpireAllowCopyCheck = new CheckBox("允许公开页复制文字");
        shareOnceAllowCopyCheck = new CheckBox("允许公开页复制文字");
        shareOnceShowCountdownCheck = new CheckBox("首次打开后展示倒计时");
        shareExpirePresetBox = new ComboBox<>(FXCollections.observableArrayList(ShareRules.ExpirePreset.values()));
        shareCountdownPresetBox = new ComboBox<>(FXCollections.observableArrayList(ShareRules.CountdownPreset.values()));

        // 中文注释：卡片标题已经说明了策略类型，这里只保留必要配置，避免重复文案。
        shareNeverRuleBox = new VBox(8, shareNeverAllowCopyCheck);
        shareExpireRuleBox = new VBox(8, createFieldLabel("过期时长"), shareExpirePresetBox, shareExpireAllowCopyCheck);
        shareOnceRuleBox = new VBox(8, shareOnceShowCountdownCheck, createFieldLabel("倒计时"), shareCountdownPresetBox, shareOnceAllowCopyCheck);

        shareNeverAllowCopyCheck.setOnAction(event -> persistShareRulesFromControls());
        shareExpireAllowCopyCheck.setOnAction(event -> persistShareRulesFromControls());
        shareOnceAllowCopyCheck.setOnAction(event -> persistShareRulesFromControls());
        shareOnceShowCountdownCheck.setOnAction(event -> {
            persistShareRulesFromControls();
            refreshShareSettingsControls();
        });
        shareExpirePresetBox.setOnAction(event -> persistShareRulesFromControls());
        shareCountdownPresetBox.setOnAction(event -> {
            persistShareRulesFromControls();
            refreshShareSettingsControls();
        });
    }

    private void openSettingsModal() {
        refreshSettingsModal();
        refreshHistorySettingsAsync();
        showSettingsModule(activeSettingsModule);
        settingsStage.show();
        settingsStage.toFront();
    }

    private void closeSettingsModal() {
        if (settingsStage != null) {
            settingsStage.hide();
        }
    }

    private void openShareComposeModal() {
        refreshShareComposeModalState();
        clearShareComposeNotice();
        if (shareComposeStage != null) {
            shareComposeStage.show();
            shareComposeStage.toFront();
        }
    }

    private void closeShareComposeModal() {
        if (shareComposeStage != null) {
            shareComposeStage.hide();
        }
    }

    private void showSettingsModule(SettingsModule module) {
        activeSettingsModule = module;
        settingsDialogTitleLabel.setText(module.getTitle());
        clearSettingsNotice();
        settingsDialogContentBox.getChildren().setAll(buildSettingsModuleContent(module));
        settingsModuleButtons.forEach((key, button) ->
            button.pseudoClassStateChanged(javafx.css.PseudoClass.getPseudoClass("active"), key == module)
        );
    }

    private Node buildSettingsModuleContent(SettingsModule module) {
        return switch (module) {
            case SHARES -> buildShareSettingsModule();
            case SECURITY -> buildSecuritySettingsModule();
            case SESSION -> buildSessionSettingsModule();
            case ABOUT -> buildAboutSettingsModule();
            case GENERAL -> buildGeneralSettingsModule();
        };
    }

    private Node buildGeneralSettingsModule() {
        Button saveBaseUrlButton = createPrimaryButton("保存服务地址");
        saveBaseUrlButton.setOnAction(event -> saveSettingsServerAddress());
        Button saveDeviceNameButton = createGhostButton("保存设备名");
        saveDeviceNameButton.setOnAction(event -> saveCurrentDeviceName());
        Button saveRuntimeButton = createPrimaryButton("保存运行设置");
        saveRuntimeButton.setOnAction(event -> saveRuntimeSettings());
        Button saveHistorySettingsButton = createPrimaryButton("保存历史设置");
        saveHistorySettingsButton.setOnAction(event -> saveHistorySettings());
        Button cleanupHistoryButton = createGhostButton("按保留天数立即清理");
        cleanupHistoryButton.setOnAction(event -> cleanupHistoryByRetentionDays());
        Button clearHistoryButton = createDangerButton("清空全部历史");
        clearHistoryButton.setOnAction(event -> clearClipboardHistory());

        VBox content = new VBox(16);
        content.getChildren().addAll(
            createRequestCard("账号信息", List.of(settingsAccountInfoLabel)),
            createRequestCard("服务地址与设备名", List.of(
                createLabeledField("服务地址", settingsBaseUrlField),
                createLabeledField("设备名", settingsDeviceNameField),
                new HBox(10, saveBaseUrlButton, saveDeviceNameButton)
            )),
            createRequestCard("运行设置", List.of(
                settingsSyncEnabledCheck,
                settingsAutoUploadClipboardFilesCheck,
                createSmallMutedLabel("关闭“复制文件也自动上传”后，复制文件时会直接忽略文件，只同步文本。"),
                settingsAutoStartCheck,
                settingsStartInTrayCheck,
                saveRuntimeButton
            )),
            createRequestCard("历史记录", List.of(
                createSmallMutedLabel("保留天数填 0 表示不限时间；最大记录数默认 1000，Web / Android / Windows 共用同一套服务端设置。"),
                createLabeledField("保留天数", settingsHistoryRetentionDaysField),
                createLabeledField("最大记录数", settingsHistoryLimitField),
                new HBox(10, saveHistorySettingsButton, cleanupHistoryButton, clearHistoryButton)
            ))
        );
        return content;
    }

    private Node buildShareSettingsModule() {
        VBox content = new VBox(16);
        Label hint = createSmallMutedLabel("分享页只保留策略选择，详细规则统一在这里维护。");
        content.getChildren().addAll(
            hint,
            createRequestCard("不过期", List.of(shareNeverRuleBox)),
            createRequestCard("过期", List.of(shareExpireRuleBox)),
            createRequestCard("阅后即焚", List.of(shareOnceRuleBox))
        );
        return content;
    }

    private Node buildSecuritySettingsModule() {
        Button changePasswordButton = createPrimaryButton("修改密码");
        changePasswordButton.setOnAction(event -> changePassword());

        VBox content = new VBox(16);
        content.getChildren().add(
            createRequestCard("修改密码", List.of(
                createLabeledField("当前密码", settingsCurrentPasswordField),
                createLabeledField("新密码", settingsNewPasswordField),
                createLabeledField("确认新密码", settingsConfirmPasswordField),
                changePasswordButton
            ))
        );
        return content;
    }

    private Node buildSessionSettingsModule() {
        Button logoutButton = createDangerButton("退出登录");
        logoutButton.setOnAction(event -> handleLogout(true));

        VBox content = new VBox(16);
        content.getChildren().add(
            createRequestCard("会话", List.of(logoutButton))
        );
        return content;
    }

    private Node buildAboutSettingsModule() {
        Label githubUrlLabel = createSecondaryValueLabel(PROJECT_GITHUB_URL);
        Button openGithubButton = createGhostButton("打开 GitHub");
        openGithubButton.setOnAction(event -> openProjectGithub());

        VBox content = new VBox(16);
        content.getChildren().add(
            createRequestCard("关于", List.of(
                createLabeledField("GitHub 仓库", githubUrlLabel),
                openGithubButton,
                createLabeledField("Windows 下载", createSecondaryValueLabel("即将开放")),
                createLabeledField("Android 下载", createSecondaryValueLabel("即将开放"))
            ))
        );
        return content;
    }

    private Node createPageHeader(NavPage page, String description) {
        VBox header = new VBox(8);
        header.getStyleClass().add("page-header");
        if (description != null && !description.isBlank()) {
            Label body = new Label(description);
            body.getStyleClass().add("page-caption");
            body.setWrapText(true);
            header.getChildren().add(body);
        }
        Label noticeLabel = new Label();
        noticeLabel.getStyleClass().add("page-notice");
        noticeLabel.setVisible(false);
        noticeLabel.setManaged(false);
        pageNoticeLabels.put(page, noticeLabel);
        header.getChildren().add(noticeLabel);
        return header;
    }

    private void showPage(NavPage page, boolean forceRefresh) {
        if (page == NavPage.SETTINGS) {
            openSettingsModal();
            return;
        }
        Node pageNode = pageNodes.get(page);
        if (pageNode == null) {
            return;
        }
        currentPage = page;
        topTitleLabel.setText(page.getLabel());
        pageContainer.getChildren().setAll(pageNode);
        navButtons.forEach((key, button) -> button.pseudoClassStateChanged(javafx.css.PseudoClass.getPseudoClass("active"), key == page));
        refreshTopBarFromState();
        if (forceRefresh || !loadedPages.contains(page)) {
            switch (page) {
                case OVERVIEW -> refreshOverviewPageAsync();
                case HISTORY -> refreshHistoryPageAsync(true);
                case DEVICES -> refreshDevicesPageAsync(devicePage);
                case FILES -> refreshFilesPageAsync(filePage);
                case SHARES -> refreshSharesPageAsync(sharePage);
                case REQUESTS -> refreshRequestsPageAsync();
                case ADMIN -> refreshAdminPageAsync();
                case SETTINGS -> {
                }
            }
        }
    }

    private void refreshOverviewPageAsync() {
        runTask(
            apiClient::getCurrentAccount,
            profile -> {
                loadedPages.add(NavPage.OVERVIEW);
                applyProfileToShell(profile);
                overviewAccountValueLabel.setText(profile.username() + (profile.isAdmin() ? " · 管理员" : " · 普通用户"));
                overviewDeviceValueLabel.setText(nonBlank(stateStore.getState().getDeviceName(), "未命名设备") + " · " + trimMiddle(profile.currentDeviceId(), 22));
                overviewSyncValueLabel.setText(buildSyncSummary());
                overviewQuotaValueLabel.setText(formatBytes(profile.storageUsedBytes()) + " / " + formatBytes(profile.storageQuotaBytes()));
                overviewBandwidthValueLabel.setText(
                    BandwidthUnitUtils.formatBandwidth(profile.uploadBandwidthKbps()) + " / " +
                        BandwidthUnitUtils.formatBandwidth(profile.downloadBandwidthKbps())
                );
                overviewAckValueLabel.setText("已确认到 seq " + stateStore.getState().getLastAckSeq());
                updateSyncControls();
                clearPageNotice(NavPage.OVERVIEW);
            },
            error -> showPageNotice(NavPage.OVERVIEW, "加载总览失败: " + error.getMessage())
        );
    }

    private void refreshHistoryPageAsync(boolean resetPage) {
        if (resetPage) {
            historyPageIndex = 0;
            historyPageBeforeSeqs.clear();
            historyPageBeforeSeqs.add(0L);
        }
        long beforeSeq = historyPageBeforeSeqs.size() > historyPageIndex ? historyPageBeforeSeqs.get(historyPageIndex) : 0L;
        runTask(
            () -> apiClient.listClipboardItems(HISTORY_PAGE_SIZE + 1, beforeSeq <= 0 ? null : beforeSeq),
            result -> {
                loadedPages.add(NavPage.HISTORY);
                List<ClipboardItem> items = new ArrayList<>(result.items());
                historyHasNext = items.size() > HISTORY_PAGE_SIZE;
                if (historyHasNext) {
                    items = new ArrayList<>(items.subList(0, HISTORY_PAGE_SIZE));
                }
                historyItems.setAll(items);
                if (historyHasNext && !items.isEmpty()) {
                    long nextBeforeSeq = items.get(items.size() - 1).seq();
                    if (historyPageBeforeSeqs.size() <= historyPageIndex + 1) {
                        historyPageBeforeSeqs.add(nextBeforeSeq);
                    } else {
                        historyPageBeforeSeqs.set(historyPageIndex + 1, nextBeforeSeq);
                    }
                }
                historyPrevButton.setDisable(historyPageIndex <= 0);
                historyNextButton.setDisable(!historyHasNext);
                historyPageLabel.setText("第 " + (historyPageIndex + 1) + " 页 · 已确认 seq " + stateStore.getState().getLastAckSeq());
                if (historyItems.isEmpty()) {
                    updateHistoryDetail(null);
                } else {
                    historyListView.getSelectionModel().select(historyItems.get(0));
                }
                clearPageNotice(NavPage.HISTORY);
            },
            error -> showPageNotice(NavPage.HISTORY, "加载历史失败: " + error.getMessage())
        );
    }

    private void refreshDevicesPageAsync(int targetPage) {
        runTask(
            () -> apiClient.listDevicesPaged(targetPage, DEVICE_PAGE_SIZE),
            result -> {
                loadedPages.add(NavPage.DEVICES);
                applyDevicesPage(result);
                clearPageNotice(NavPage.DEVICES);
            },
            error -> showPageNotice(NavPage.DEVICES, "加载设备失败: " + error.getMessage())
        );
    }

    private void refreshFilesPageAsync(int targetPage) {
        runTask(
            () -> apiClient.listFiles(targetPage, FILE_PAGE_SIZE),
            result -> {
                loadedPages.add(NavPage.FILES);
                applyFilesPage(result);
                clearPageNotice(NavPage.FILES);
            },
            error -> showPageNotice(NavPage.FILES, "加载文件失败: " + error.getMessage())
        );
    }

    private void refreshSharesPageAsync(int targetPage) {
        runTask(
            () -> apiClient.listShares(targetPage, SHARE_PAGE_SIZE, shareStatusFilterBox.getValue()),
            result -> {
                loadedPages.add(NavPage.SHARES);
                applySharesPage(result);
                clearPageNotice(NavPage.SHARES);
            },
            error -> showPageNotice(NavPage.SHARES, "加载分享失败: " + error.getMessage())
        );
    }

    private void refreshRequestsPageAsync() {
        String status = requestStatusFilterBox.getValue() == null ? "all" : requestStatusFilterBox.getValue();
        runTask(
            () -> new RequestBundle(
                apiClient.listMyQuotaRequests(status),
                apiClient.listMyBandwidthRequests(status),
                apiClient.listMyAdminRequests(status)
            ),
            bundle -> {
                loadedPages.add(NavPage.REQUESTS);
                quotaRequestItems.setAll(bundle.quota().requests());
                bandwidthRequestItems.setAll(bundle.bandwidth().requests());
                adminRequestItems.setAll(bundle.admin().requests());
                requestOverviewLabel.setText(
                    "配额 " + quotaRequestItems.size() + " 条 · 带宽 " + bandwidthRequestItems.size() + " 条 · 管理员 " + adminRequestItems.size() + " 条"
                );
                clearPageNotice(NavPage.REQUESTS);
            },
            error -> showPageNotice(NavPage.REQUESTS, "加载申请记录失败: " + error.getMessage())
        );
    }

    private void refreshAdminPageAsync() {
        if (!stateStore.getState().isAdmin()) {
            showPageNotice(NavPage.ADMIN, "当前账号不是管理员，无法访问此页面。");
            loadedPages.add(NavPage.ADMIN);
            return;
        }
        runTask(
            () -> new AdminBundle(
                apiClient.getAdminSettings(),
                apiClient.listAdminUsers(),
                apiClient.listPendingQuotaRequestsForAdmin().requests(),
                apiClient.listPendingBandwidthRequestsForAdmin().requests(),
                apiClient.listPendingAdminRequestsForAdmin().requests()
            ),
            bundle -> {
                loadedPages.add(NavPage.ADMIN);
                applyAdminBundle(bundle);
                clearPageNotice(NavPage.ADMIN);
            },
            error -> showPageNotice(NavPage.ADMIN, "加载管理员数据失败: " + error.getMessage())
        );
    }

    private void refreshSettingsModal() {
        AppState state = stateStore.getState();
        settingsAccountInfoLabel.setText(
            state.getUsername() + (state.isAdmin() ? " · 管理员" : " · 普通用户") +
                "\n当前设备: " + nonBlank(state.getDeviceName(), "未命名设备") +
                "\n设备 ID: " + trimMiddle(state.getCurrentDeviceId(), 24)
        );
        settingsBaseUrlField.setText(state.getBaseUrl());
        settingsDeviceNameField.setText(state.getDeviceName());
        settingsSyncEnabledCheck.setSelected(state.isSyncEnabled());
        settingsAutoUploadClipboardFilesCheck.setSelected(state.isAutoUploadClipboardFiles());
        settingsAutoStartCheck.setSelected(state.isAutoStartEnabled());
        settingsStartInTrayCheck.setSelected(state.isStartInTray());
        ShareRules.Config rules = state.getShareRules();
        shareNeverAllowCopyCheck.setSelected(rules.never().allowCopyText());
        shareExpireAllowCopyCheck.setSelected(rules.expire().allowCopyText());
        shareOnceAllowCopyCheck.setSelected(rules.once().allowCopyText());
        shareOnceShowCountdownCheck.setSelected(rules.once().showCountdown());
        shareExpirePresetBox.setValue(rules.expire().preset());
        shareCountdownPresetBox.setValue(rules.once().countdownPreset());
        refreshShareSettingsControls();
        clearSettingsNotice();
    }

    private void refreshHistorySettingsAsync() {
        if (settingsHistoryRetentionDaysField == null || settingsHistoryLimitField == null) {
            return;
        }
        runTask(
            apiClient::getClipboardHistorySettings,
            this::applyClipboardHistorySettings,
            error -> {
                if (settingsStage != null && settingsStage.isShowing()) {
                    showSettingsNotice("加载历史设置失败: " + error.getMessage());
                }
            }
        );
    }

    private void applyClipboardHistorySettings(ApiModels.ClipboardHistorySettings settings) {
        if (settings == null) {
            return;
        }
        settingsHistoryRetentionDaysField.setText(String.valueOf(settings.retentionDays()));
        settingsHistoryLimitField.setText(String.valueOf(settings.historyLimit()));
    }

    private void applyProfileToShell(AccountProfile profile) {
        topUserLabel.setText(nonBlank(profile.username(), "未登录"));
        AppState state = stateStore.getState();
        stateStore.update(next -> {
            next.setUsername(profile.username());
            next.setCurrentDeviceId(profile.currentDeviceId());
            next.setAdmin(profile.isAdmin());
            next.setStorageQuotaBytes(profile.storageQuotaBytes());
            next.setUploadBandwidthKbps(profile.uploadBandwidthKbps());
            next.setDownloadBandwidthKbps(profile.downloadBandwidthKbps());
        });
        if (adminNavButton != null) {
            adminNavButton.setManaged(profile.isAdmin());
            adminNavButton.setVisible(profile.isAdmin());
        }
        refreshSettingsModal();
    }

    private void applyDevicesPage(PagedDevices result) {
        deviceItems.setAll(result.items());
        devicePage = result.page();
        deviceTotalPages = result.totalPages();
        devicePageLabel.setText("第 " + devicePage + " / " + deviceTotalPages + " 页 · 共 " + result.total() + " 台设备");
        devicePrevButton.setDisable(devicePage <= 1);
        deviceNextButton.setDisable(devicePage >= deviceTotalPages);
        if (deviceItems.isEmpty()) {
            updateDeviceDetail(null);
            return;
        }

        DeviceInfo preferred = deviceItems.stream()
            .filter(item -> item.isCurrent(stateStore.getState().getCurrentDeviceId()))
            .findFirst()
            .orElse(deviceItems.get(0));
        deviceListView.getSelectionModel().select(preferred);
    }

    private void applyFilesPage(PagedFiles result) {
        fileItems.setAll(result.items());
        filePage = result.page();
        fileTotalPages = result.totalPages();
        filePageLabel.setText("第 " + filePage + " / " + fileTotalPages + " 页 · 共 " + result.total() + " 个文件");
        filePrevButton.setDisable(filePage <= 1);
        fileNextButton.setDisable(filePage >= fileTotalPages);
        fileSummaryFilesLabel.setText(String.valueOf(result.summary().totalFiles()));
        fileSummaryBytesLabel.setText(formatBytes(result.summary().totalBytes()));
        fileSummaryLimitLabel.setText(formatBytes(result.summary().maxUploadBytes()));
        if (fileItems.isEmpty()) {
            updateFileDetail(null);
        } else {
            fileListView.getSelectionModel().select(fileItems.get(0));
        }
    }

    private void applySharesPage(ShareListResult result) {
        shareItems.setAll(result.shares());
        sharePage = result.pagination().page();
        shareTotalPages = result.pagination().totalPages();
        shareTotalCount = result.pagination().total();
        sharePageLabel.setText("第 " + sharePage + " / " + shareTotalPages + " 页 · 共 " + shareTotalCount + " 条");
        sharePrevButton.setDisable(sharePage <= 1);
        shareNextButton.setDisable(sharePage >= shareTotalPages);
        if (shareItems.isEmpty()) {
            updateShareDetail(null);
        } else {
            shareListView.getSelectionModel().select(shareItems.get(0));
        }
    }

    private void applyAdminBundle(AdminBundle bundle) {
        AdminSettings settings = bundle.settings();
        adminSettingsMetaLabel.setText(
            "当前用户数 " + settings.currentUserCount() + " / " + settings.maxUserCount() +
                " · 最近更新时间 " + formatTime(settings.updatedAt())
        );
        adminMaxUsersField.setText(String.valueOf(settings.maxUserCount()));
        adminDefaultQuotaField.setText(String.valueOf(settings.defaultStorageQuotaBytes() / (1024L * 1024L)));
        adminDefaultUploadField.setText(BandwidthUnitUtils.toBandwidthInput(settings.defaultUploadBandwidthKbps()));
        adminDefaultDownloadField.setText(BandwidthUnitUtils.toBandwidthInput(settings.defaultDownloadBandwidthKbps()));
        adminMaxUserUploadField.setText(BandwidthUnitUtils.toBandwidthInput(settings.maxUserUploadBandwidthKbps()));
        adminMaxUserDownloadField.setText(BandwidthUnitUtils.toBandwidthInput(settings.maxUserDownloadBandwidthKbps()));
        adminMaxUploadFileField.setText(String.valueOf(settings.maxUploadFileBytes() / (1024L * 1024L)));
        adminAllowRegistrationCheck.setSelected(settings.allowRegistration());

        adminUserItems.setAll(bundle.users());
        pendingQuotaRequestItems.setAll(bundle.quotaRequests());
        pendingBandwidthRequestItems.setAll(bundle.bandwidthRequests());
        pendingAdminRequestItems.setAll(bundle.adminRequests());
        updateAdminSectionTitles();

        if (adminUserItems.isEmpty()) {
            updateAdminUserDetail(null);
        } else {
            adminUserListView.getSelectionModel().select(adminUserItems.get(0));
        }
    }

    private void updateAdminSectionTitles() {
        if (adminSettingsSectionPane != null) {
            adminSettingsSectionPane.setText("系统设置");
        }
        if (adminUsersSectionPane != null) {
            adminUsersSectionPane.setText("用户管理");
        }
        if (adminQuotaSectionPane != null) {
            adminQuotaSectionPane.setText(buildPendingSectionTitle("配额审批", !pendingQuotaRequestItems.isEmpty()));
        }
        if (adminBandwidthSectionPane != null) {
            adminBandwidthSectionPane.setText(buildPendingSectionTitle("带宽审批", !pendingBandwidthRequestItems.isEmpty()));
        }
        if (adminPrivilegeSectionPane != null) {
            adminPrivilegeSectionPane.setText(buildPendingSectionTitle("管理员审批", !pendingAdminRequestItems.isEmpty()));
        }
    }

    private String buildPendingSectionTitle(String title, boolean hasPending) {
        return hasPending ? title + "（待审批）" : title;
    }

    private void updateHistoryDetail(ClipboardItem item) {
        if (item == null) {
            historyDetailMetaLabel.setText("选择左侧记录后显示完整内容。");
            historyDetailTextArea.setText("");
            historyDeleteButton.setDisable(true);
            return;
        }
        historyDetailMetaLabel.setText(
            "seq " + item.seq() +
                " · " + formatTime(item.createdAt()) +
                " · 来源 " + nonBlank(item.originDeviceId(), "未知设备")
        );
        historyDetailTextArea.setText(item.textContent());
        historyDeleteButton.setDisable(false);
    }

    private void updateDeviceDetail(DeviceInfo device) {
        if (device == null) {
            deviceDetailTitleLabel.setText("请选择一个设备");
            deviceDetailMetaLabel.setText("显示设备信息。");
            deviceRenameField.setText("");
            deviceSaveButton.setDisable(true);
            deviceOfflineButton.setDisable(true);
            return;
        }
        boolean isCurrent = device.isCurrent(stateStore.getState().getCurrentDeviceId());
        deviceDetailTitleLabel.setText(device.deviceName() + (isCurrent ? " · 当前设备" : ""));
        deviceDetailMetaLabel.setText(
            "平台: " + device.platform() +
                "\n设备 ID: " + device.id() +
                "\n状态: " + (device.isActive() ? "在线" : "已下线") +
                "\n最近在线: " + formatTime(device.lastSeenAt())
        );
        deviceRenameField.setText(device.deviceName());
        deviceSaveButton.setDisable(false);
        deviceOfflineButton.setDisable(false);
    }

    private void updateFileDetail(FileItem file) {
        if (file == null) {
            fileDetailTitleLabel.setText("请选择一个文件");
            fileDetailMetaLabel.setText("右侧可以下载、重命名或删除文件。");
            fileRenameField.setText("");
            fileDownloadButton.setDisable(true);
            fileRenameButton.setDisable(true);
            fileDeleteButton.setDisable(true);
            return;
        }
        fileDetailTitleLabel.setText(file.originalName());
        fileDetailMetaLabel.setText(
            "大小: " + formatBytes(file.sizeBytes()) +
                "\n类型: " + file.contentType() +
                "\n来源设备: " + nonBlank(file.originDeviceName(), file.originDeviceId()) +
                "\n创建时间: " + formatTime(file.createdAt())
        );
        fileRenameField.setText(file.originalName());
        fileDownloadButton.setDisable(false);
        fileRenameButton.setDisable(false);
        fileDeleteButton.setDisable(false);
    }

    private void updateShareDetail(ShareItem share) {
        if (share == null) {
            shareDetailTitleLabel.setText("请选择一个分享");
            shareDetailMetaLabel.setText("显示分享信息和预览。");
            shareDetailPreviewArea.setText("");
            shareCopyLinkButton.setDisable(true);
            shareOpenLinkButton.setDisable(true);
            shareRevokeButton.setDisable(true);
            return;
        }
        shareDetailTitleLabel.setText(buildShareTitle(share));
        shareDetailMetaLabel.setText(
            "状态: " + statusText(share.status()) +
                "\n公开链接: " + share.token() +
                "\n策略: " + burnModeText(share.burnMode()) +
                "\n创建时间: " + formatTime(share.createdAt()) +
                "\n到期时间: " + nonBlank(formatTime(share.expiresAt()), "不过期")
        );
        shareDetailPreviewArea.setText(share.textPreview());
        shareCopyLinkButton.setDisable(false);
        shareOpenLinkButton.setDisable(false);
        shareRevokeButton.setDisable(!share.isActive());
    }

    private void updateAdminUserDetail(AdminUser user) {
        if (user == null) {
            adminUserDetailTitleLabel.setText("请选择一个用户");
            adminUserDetailMetaLabel.setText("右侧可修改额度、带宽或管理员标记。");
            adminUserQuotaField.setText("");
            adminUserUploadField.setText("");
            adminUserDownloadField.setText("");
            adminUserAdminCheck.setSelected(false);
            adminUserSaveButton.setDisable(true);
            adminUserDeleteButton.setDisable(true);
            return;
        }
        adminUserDetailTitleLabel.setText(user.username() + (user.isAdmin() ? " · 管理员" : ""));
        adminUserDetailMetaLabel.setText(
            "已用 / 配额: " + formatBytes(user.storageUsedBytes()) + " / " + formatBytes(user.storageQuotaBytes()) +
                "\n待处理申请: " + buildPendingFlags(user) +
                "\n最近活跃: " + nonBlank(formatTime(user.lastActiveAt()), "未知")
        );
        adminUserQuotaField.setText(String.valueOf(user.storageQuotaBytes() / (1024L * 1024L)));
        adminUserUploadField.setText(BandwidthUnitUtils.toBandwidthInput(user.uploadBandwidthKbps()));
        adminUserDownloadField.setText(BandwidthUnitUtils.toBandwidthInput(user.downloadBandwidthKbps()));
        adminUserAdminCheck.setSelected(user.isAdmin());
        adminUserSaveButton.setDisable(false);
        adminUserDeleteButton.setDisable(false);
    }

    private void submitAuth() {
        String baseUrl = loginBaseUrlField.getText();
        String username = loginUsernameField.getText();
        String password = loginPasswordField.getText();
        String deviceName = loginDeviceNameField.getText();
        String validationError = ServiceAddressFormatter.validate(baseUrl);
        if (!validationError.isBlank()) {
            setLoginHint(validationError, true);
            return;
        }

        loginSubmitButton.setDisable(true);
        loginSwitchModeButton.setDisable(true);
        setLoginHint(registerMode ? "正在创建账号..." : "正在登录...", false);

        runTask(
            () -> registerMode
                ? apiClient.register(baseUrl, username, password, deviceName)
                : apiClient.login(baseUrl, username, password, deviceName),
            session -> {
                showToast(registerMode ? "注册成功，已进入主界面" : "登录成功");
                registerMode = false;
                loadedPages.clear();
                showMainScene();
                showPage(NavPage.HISTORY, true);
                loginPasswordField.clear();
            },
            error -> {
                loginSubmitButton.setDisable(false);
                loginSwitchModeButton.setDisable(false);
                setLoginHint("认证失败: " + error.getMessage(), true);
            }
        );
    }

    private void submitManualHistoryText() {
        String text = historyManualUploadArea.getText();
        if (text == null || text.isBlank()) {
            showPageNotice(NavPage.HISTORY, "请先输入要上传的文本。");
            return;
        }
        runTask(
            () -> apiClient.createClipboardText(text),
            result -> {
                historyManualUploadArea.clear();
                showToast(result.deduplicated() ? "文本已去重" : "文本上传成功");
                loadedPages.remove(NavPage.HISTORY);
                loadedPages.remove(NavPage.OVERVIEW);
                refreshHistoryPageAsync(true);
            },
            error -> showPageNotice(NavPage.HISTORY, "上传文本失败: " + error.getMessage())
        );
    }

    private void deleteSelectedHistoryItem() {
        ClipboardItem selected = historyListView.getSelectionModel().getSelectedItem();
        if (selected == null) {
            return;
        }
        if (!confirmDanger("确认删除这条历史记录吗？删除后各端历史列表都不会再显示它。")) {
            return;
        }

        boolean fallbackToPreviousPage = historyItems.size() <= 1 && historyPageIndex > 0;
        runTask(
            () -> apiClient.deleteClipboardItem(selected.id()),
            result -> {
                if (fallbackToPreviousPage) {
                    historyPageIndex -= 1;
                }
                showToast(result.deletedCount() > 0 ? "历史记录已删除" : "这条历史记录已经不存在");
                invalidateHistoryPages(false);
            },
            error -> showPageNotice(NavPage.HISTORY, "删除历史记录失败: " + error.getMessage())
        );
    }

    private void renameSelectedDevice() {
        DeviceInfo selected = deviceListView.getSelectionModel().getSelectedItem();
        if (selected == null) {
            return;
        }
        runTask(
            () -> apiClient.renameDevice(selected.id(), deviceRenameField.getText()),
            updated -> {
                showToast("设备名已更新");
                loadedPages.remove(NavPage.DEVICES);
                loadedPages.remove(NavPage.OVERVIEW);
                refreshDevicesPageAsync(devicePage);
                refreshSettingsModal();
            },
            error -> showPageNotice(NavPage.DEVICES, "修改设备名失败: " + error.getMessage())
        );
    }

    private void forceOfflineSelectedDevice() {
        DeviceInfo selected = deviceListView.getSelectionModel().getSelectedItem();
        if (selected == null) {
            return;
        }
        String message = selected.isCurrent(stateStore.getState().getCurrentDeviceId())
            ? "确认强制下线当前设备吗？这会立即清理本地登录态。"
            : "确认强制下线设备“" + selected.deviceName() + "”吗？";
        if (!confirmDanger(message)) {
            return;
        }

        runTask(
            () -> apiClient.forceDeviceOffline(selected.id()),
            result -> {
                showToast("设备已强制下线");
                if (result.currentDeviceForcedOffline()) {
                    handleSessionExpired("当前设备已被强制下线，请重新登录");
                    return;
                }
                loadedPages.remove(NavPage.DEVICES);
                refreshDevicesPageAsync(devicePage);
            },
            error -> showPageNotice(NavPage.DEVICES, "强制下线失败: " + error.getMessage())
        );
    }

    private void chooseFilesForUpload() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("选择要上传的文件");
        List<File> files = chooser.showOpenMultipleDialog(primaryStage);
        if (files == null || files.isEmpty()) {
            return;
        }
        List<Path> paths = files.stream().map(File::toPath).toList();
        uploadFilesAsync(paths, false);
    }

    private void uploadFilesAsync(List<Path> paths, boolean fromDrop) {
        if (paths == null || paths.isEmpty()) {
            return;
        }
        runTask(
            () -> {
                int uploaded = 0;
                for (Path path : paths) {
                    apiClient.uploadFile(path);
                    uploaded++;
                }
                return uploaded;
            },
            uploaded -> {
                showToast((fromDrop ? "拖拽" : "选择") + "上传成功，共 " + uploaded + " 个文件");
                loadedPages.remove(NavPage.FILES);
                refreshFilesPageAsync(1);
            },
            error -> showPageNotice(NavPage.FILES, "上传文件失败: " + error.getMessage())
        );
    }

    private void downloadSelectedFile() {
        FileItem selected = fileListView.getSelectionModel().getSelectedItem();
        if (selected == null) {
            return;
        }
        FileChooser chooser = new FileChooser();
        chooser.setTitle("保存文件");
        chooser.setInitialFileName(selected.originalName());
        File target = chooser.showSaveDialog(primaryStage);
        if (target == null) {
            return;
        }
        runTask(
            () -> {
                apiClient.downloadFile(selected.id(), target.toPath());
                return target.toPath();
            },
            path -> showToast("文件已保存到 " + path),
            error -> showPageNotice(NavPage.FILES, "下载文件失败: " + error.getMessage())
        );
    }

    private void renameSelectedFile() {
        FileItem selected = fileListView.getSelectionModel().getSelectedItem();
        if (selected == null) {
            return;
        }
        runTask(
            () -> apiClient.renameFile(selected.id(), fileRenameField.getText()),
            file -> {
                showToast("文件名已更新");
                loadedPages.remove(NavPage.FILES);
                refreshFilesPageAsync(filePage);
            },
            error -> showPageNotice(NavPage.FILES, "重命名文件失败: " + error.getMessage())
        );
    }

    private void deleteSelectedFile() {
        FileItem selected = fileListView.getSelectionModel().getSelectedItem();
        if (selected == null || !confirmDanger("确认删除文件“" + selected.originalName() + "”吗？")) {
            return;
        }
        runTask(
            () -> apiClient.deleteFile(selected.id()),
            result -> {
                showToast(result.diskRemoved() ? "文件已删除" : "文件记录已删除，磁盘文件删除状态请检查服务端");
                loadedPages.remove(NavPage.FILES);
                refreshFilesPageAsync(Math.max(1, filePage));
            },
            error -> showPageNotice(NavPage.FILES, "删除文件失败: " + error.getMessage())
        );
    }

    private void chooseShareFile() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("选择分享文件");
        Stage owner = shareComposeStage != null && shareComposeStage.isShowing() ? shareComposeStage : primaryStage;
        File file = chooser.showOpenDialog(owner);
        if (file == null) {
            return;
        }
        applySelectedShareFile(file.toPath());
    }

    private void createShareAsync() {
        clearShareComposeNotice();
        ShareRules.Config rules = stateStore.getState().getShareRules();
        ShareRules.PolicyPayload policy = rules.buildPolicyPayload(selectedShareStrategy, true);
        if (selectedShareComposeMode == ShareRules.ComposeMode.TEXT) {
            String text = shareTextContentArea.getText();
            if (text == null || text.isBlank()) {
                showShareComposeNotice("请输入要分享的文字内容。");
                return;
            }
            runTask(
                () -> apiClient.createTextShare(text, policy),
                share -> handleShareCreated(share, true),
                error -> showShareComposeNotice("创建文本分享失败: " + error.getMessage())
            );
            return;
        }

        if (selectedShareFilePath == null) {
            showShareComposeNotice("请先选择一个要分享的文件。");
            return;
        }
        runTask(
            () -> apiClient.createFileShare(selectedShareFilePath, policy),
            share -> handleShareCreated(share, false),
            error -> showShareComposeNotice("创建文件分享失败: " + error.getMessage())
        );
    }

    private void handleShareCreated(ShareItem share, boolean clearText) {
        String latestLink = PublicShareLinkBuilder.build(stateStore.getState().getBaseUrl(), share.token());
        shareLatestLinkLabel.setText(nonBlank(latestLink, "分享已生成，但无法拼出公开链接，请检查服务地址"));
        if (clearText) {
            shareTextContentArea.clear();
        } else {
            applySelectedShareFile(null);
        }
        clearShareComposeNotice();
        refreshShareComposeModalState();
        showToast("分享创建成功");
        loadedPages.remove(NavPage.SHARES);
        refreshSharesPageAsync(1);
    }

    private void revokeSelectedShare() {
        ShareItem selected = shareListView.getSelectionModel().getSelectedItem();
        if (selected == null || !confirmDanger("确认撤销这个分享吗？")) {
            return;
        }
        runTask(
            () -> apiClient.revokeShare(selected.id()),
            share -> {
                showToast("分享已撤销");
                loadedPages.remove(NavPage.SHARES);
                refreshSharesPageAsync(sharePage);
            },
            error -> showPageNotice(NavPage.SHARES, "撤销分享失败: " + error.getMessage())
        );
    }

    private void submitQuotaRequest() {
        clearPageNotice(NavPage.REQUESTS);
        long requestedMb = parseLong(requestQuotaField.getText(), 0L);
        String validationMessage = validateQuotaRequestInput(requestedMb, requestQuotaReasonArea.getText());
        if (!validationMessage.isBlank()) {
            showToast(validationMessage);
            return;
        }
        runTask(
            () -> {
                apiClient.createQuotaRequest(requestedMb, requestQuotaReasonArea.getText());
                return null;
            },
            ignored -> {
                requestQuotaField.clear();
                requestQuotaReasonArea.clear();
                showToast("配额申请已提交");
                loadedPages.remove(NavPage.REQUESTS);
                refreshRequestsPageAsync();
            },
            this::handleQuotaRequestSubmitError
        );
    }

    private void submitBandwidthRequest() {
        int upload = BandwidthUnitUtils.parseBandwidthMbOrFallback(requestUploadField.getText(), 0);
        int download = BandwidthUnitUtils.parseBandwidthMbOrFallback(requestDownloadField.getText(), 0);
        if (upload <= 0 || download <= 0) {
            showPageNotice(NavPage.REQUESTS, "上传和下载带宽都必须是大于 0 的数字 MB/s。");
            return;
        }
        runTask(
            () -> {
                apiClient.createBandwidthRequest(upload, download, requestBandwidthReasonArea.getText());
                return null;
            },
            ignored -> {
                requestUploadField.clear();
                requestDownloadField.clear();
                requestBandwidthReasonArea.clear();
                showToast("带宽申请已提交");
                loadedPages.remove(NavPage.REQUESTS);
                refreshRequestsPageAsync();
            },
            error -> showPageNotice(NavPage.REQUESTS, "提交带宽申请失败: " + error.getMessage())
        );
    }

    private void submitAdminRequest() {
        runTask(
            () -> {
                apiClient.createAdminRequest(requestAdminReasonArea.getText());
                return null;
            },
            ignored -> {
                requestAdminReasonArea.clear();
                showToast("管理员申请已提交");
                loadedPages.remove(NavPage.REQUESTS);
                refreshRequestsPageAsync();
            },
            error -> showPageNotice(NavPage.REQUESTS, "提交管理员申请失败: " + error.getMessage())
        );
    }

    private void saveAdminSettings() {
        Integer maxUsers = parseOptionalInt(adminMaxUsersField.getText());
        Long defaultQuotaMb = parseOptionalLong(adminDefaultQuotaField.getText());
        Integer defaultUpload = BandwidthUnitUtils.parseBandwidthMbOptional(adminDefaultUploadField.getText());
        Integer defaultDownload = BandwidthUnitUtils.parseBandwidthMbOptional(adminDefaultDownloadField.getText());
        Integer maxUserUpload = BandwidthUnitUtils.parseBandwidthMbOptional(adminMaxUserUploadField.getText());
        Integer maxUserDownload = BandwidthUnitUtils.parseBandwidthMbOptional(adminMaxUserDownloadField.getText());
        Long maxUploadFileMb = parseOptionalLong(adminMaxUploadFileField.getText());
        runTask(
            () -> apiClient.updateAdminSettings(
                maxUsers,
                defaultQuotaMb,
                defaultUpload,
                defaultDownload,
                maxUserUpload,
                maxUserDownload,
                maxUploadFileMb,
                adminAllowRegistrationCheck.isSelected()
            ),
            settings -> {
                showToast("系统设置已保存");
                loadedPages.remove(NavPage.ADMIN);
                refreshAdminPageAsync();
            },
            error -> showPageNotice(NavPage.ADMIN, "保存系统设置失败: " + error.getMessage())
        );
    }

    private void saveSelectedAdminUser() {
        AdminUser selected = adminUserListView.getSelectionModel().getSelectedItem();
        if (selected == null) {
            return;
        }
        runTask(
            () -> apiClient.updateAdminUser(
                selected.id(),
                parseOptionalLong(adminUserQuotaField.getText()),
                BandwidthUnitUtils.parseBandwidthMbOptional(adminUserUploadField.getText()),
                BandwidthUnitUtils.parseBandwidthMbOptional(adminUserDownloadField.getText()),
                adminUserAdminCheck.isSelected()
            ),
            user -> {
                showToast("用户设置已更新");
                loadedPages.remove(NavPage.ADMIN);
                refreshAdminPageAsync();
            },
            error -> showPageNotice(NavPage.ADMIN, "更新用户失败: " + error.getMessage())
        );
    }

    private void deleteSelectedAdminUser() {
        AdminUser selected = adminUserListView.getSelectionModel().getSelectedItem();
        if (selected == null || !confirmDanger("确认删除用户“" + selected.username() + "”吗？该用户的文件、分享和申请记录会一并清理。")) {
            return;
        }
        runTask(
            () -> {
                apiClient.deleteAdminUser(selected.id());
                return null;
            },
            ignored -> {
                showToast("用户已删除");
                loadedPages.remove(NavPage.ADMIN);
                refreshAdminPageAsync();
            },
            error -> showPageNotice(NavPage.ADMIN, "删除用户失败: " + error.getMessage())
        );
    }

    private void saveSettingsServerAddress() {
        String newBaseUrl = settingsBaseUrlField.getText();
        String validationError = ServiceAddressFormatter.validate(newBaseUrl);
        if (!validationError.isBlank()) {
            showSettingsNotice(validationError);
            return;
        }
        String normalized = ServiceAddressFormatter.normalize(newBaseUrl);
        if (normalized.equals(stateStore.getState().getBaseUrl())) {
            showSettingsNotice("服务地址没有变化。");
            return;
        }
        if (!confirmDanger("修改服务地址后会清理当前登录态并返回登录页，确认继续吗？")) {
            return;
        }
        stateStore.update(state -> {
            state.setBaseUrl(normalized);
            state.clearSession();
        });
        loadedPages.clear();
        showLoginScene();
        showToast("服务地址已更新，请重新登录");
    }

    private void saveCurrentDeviceName() {
        String deviceName = settingsDeviceNameField.getText();
        if (deviceName == null || deviceName.isBlank()) {
            showSettingsNotice("设备名不能为空。");
            return;
        }
        runTask(
            () -> apiClient.renameDevice(stateStore.getState().getCurrentDeviceId(), deviceName),
            device -> {
                showToast("设备名已保存");
                loadedPages.remove(NavPage.OVERVIEW);
                loadedPages.remove(NavPage.DEVICES);
                refreshSettingsModal();
                refreshDevicesPageAsync(devicePage);
            },
            error -> showSettingsNotice("保存设备名失败: " + error.getMessage())
        );
    }

    private void changePassword() {
        String currentPassword = settingsCurrentPasswordField.getText();
        String newPassword = settingsNewPasswordField.getText();
        String confirmPassword = settingsConfirmPasswordField.getText();
        if (!Objects.equals(newPassword, confirmPassword)) {
            showSettingsNotice("两次输入的新密码不一致。");
            return;
        }
        runTask(
            () -> {
                apiClient.changePassword(currentPassword, newPassword);
                return null;
            },
            ignored -> {
                settingsCurrentPasswordField.clear();
                settingsNewPasswordField.clear();
                settingsConfirmPasswordField.clear();
                showToast("密码修改成功");
            },
            error -> showSettingsNotice("修改密码失败: " + error.getMessage())
        );
    }

    private void saveRuntimeSettings() {
        AppState currentState = stateStore.getState();
        boolean syncEnabled = settingsSyncEnabledCheck.isSelected();
        boolean autoUploadClipboardFiles = settingsAutoUploadClipboardFilesCheck.isSelected();
        boolean autoStartEnabled = settingsAutoStartCheck.isSelected();
        boolean startInTray = settingsStartInTrayCheck.isSelected();
        boolean startupRegistryChanged = autoStartEnabled != currentState.isAutoStartEnabled()
            || (autoStartEnabled && startInTray != currentState.isStartInTray());
        try {
            // 中文注释：只有开机自启相关设置真的发生变化时，才去触碰系统注册表，避免“只是点了保存”也报错。
            if (startupRegistryChanged) {
                if (autoStartEnabled) {
                    WindowsStartupManager.setEnabled(true, startInTray);
                } else if (WindowsStartupManager.isSupported()) {
                    WindowsStartupManager.remove();
                }
            }
            stateStore.update(state -> {
                state.setSyncEnabled(syncEnabled);
                state.setAutoUploadClipboardFiles(autoUploadClipboardFiles);
                state.setAutoStartEnabled(autoStartEnabled);
                state.setStartInTray(startInTray);
            });
            clearSettingsNotice();
            syncService.notifySettingsChanged();
            updateSyncControls();
            updateTrayMenuLabel();
            showToast("运行设置已保存");
        } catch (Exception error) {
            showSettingsNotice("保存运行设置失败: " + error.getMessage());
        }
    }

    private void toggleSyncEnabled(boolean enabled) {
        stateStore.update(state -> state.setSyncEnabled(enabled));
        syncService.notifySettingsChanged();
        updateSyncControls();
        if (settingsSyncEnabledCheck != null) {
            settingsSyncEnabledCheck.setSelected(enabled);
        }
        showToast(enabled ? "已开启文本同步" : "已关闭文本同步");
    }

    private void saveHistorySettings() {
        int retentionDays;
        int historyLimit;
        try {
            retentionDays = Integer.parseInt(nonBlank(settingsHistoryRetentionDaysField.getText(), "0").trim());
            historyLimit = Integer.parseInt(nonBlank(settingsHistoryLimitField.getText(), "").trim());
        } catch (Exception error) {
            showSettingsNotice("保留天数和最大记录数都必须是整数。");
            return;
        }
        if (retentionDays < 0) {
            showSettingsNotice("保留天数不能小于 0。");
            return;
        }
        if (historyLimit <= 0) {
            showSettingsNotice("最大记录数必须大于 0。");
            return;
        }

        runTask(
            () -> apiClient.updateClipboardHistorySettings(retentionDays, historyLimit),
            result -> {
                applyClipboardHistorySettings(result.settings());
                clearSettingsNotice();
                showToast(
                    result.deletedCount() > 0
                        ? "历史设置已保存，并清理了 " + result.deletedCount() + " 条记录"
                        : "历史设置已保存"
                );
                if (result.deletedCount() > 0) {
                    invalidateHistoryPages(true);
                }
            },
            error -> showSettingsNotice("保存历史设置失败: " + error.getMessage())
        );
    }

    private void cleanupHistoryByRetentionDays() {
        int retentionDays;
        try {
            retentionDays = Integer.parseInt(nonBlank(settingsHistoryRetentionDaysField.getText(), "").trim());
        } catch (Exception error) {
            showSettingsNotice("请先填写一个有效的保留天数。");
            return;
        }
        if (retentionDays <= 0) {
            showSettingsNotice("按天清理时，保留天数必须大于 0。");
            return;
        }
        if (!confirmDanger("会删除早于 " + retentionDays + " 天的历史记录，确认继续吗？")) {
            return;
        }

        runTask(
            () -> apiClient.cleanupClipboardHistory(retentionDays),
            result -> {
                applyClipboardHistorySettings(result.settings());
                clearSettingsNotice();
                showToast("已清理 " + result.deletedCount() + " 条历史记录");
                invalidateHistoryPages(true);
            },
            error -> showSettingsNotice("清理历史记录失败: " + error.getMessage())
        );
    }

    private void clearClipboardHistory() {
        if (!confirmDanger("会清空当前账号下全部文本历史记录，确认继续吗？")) {
            return;
        }

        runTask(
            apiClient::clearClipboardHistory,
            result -> {
                applyClipboardHistorySettings(result.settings());
                clearSettingsNotice();
                if (result.deletedCount() > 0) {
                    showToast("已清空 " + result.deletedCount() + " 条历史记录");
                } else {
                    showToast("当前没有可清空的历史记录");
                }
                invalidateHistoryPages(true);
            },
            error -> showSettingsNotice("清空历史记录失败: " + error.getMessage())
        );
    }

    private void invalidateHistoryPages(boolean resetPage) {
        // 中文注释：历史被删除或批量清理后，分页游标和本地详情区都可能已经过期。
        // 这里统一标记历史页失效；如果用户当前就在历史页，就立刻按新状态重新加载。
        loadedPages.remove(NavPage.HISTORY);
        loadedPages.remove(NavPage.OVERVIEW);
        if (currentPage == NavPage.HISTORY) {
            refreshHistoryPageAsync(resetPage);
        }
    }

    private void refreshTopBarFromState() {
        AppState state = stateStore.getState();
        topUserLabel.setText(nonBlank(state.getUsername(), "未登录"));
        updateSyncControls();
        updateTrayMenuLabel();
        if (adminNavButton != null) {
            adminNavButton.setManaged(state.isAdmin());
            adminNavButton.setVisible(state.isAdmin());
        }
    }

    private void refreshShareComposeMode() {
        if (shareTextContentFieldRow == null || shareFileFieldRow == null) {
            return;
        }
        boolean textMode = selectedShareComposeMode == ShareRules.ComposeMode.TEXT;
        shareTextContentFieldRow.setManaged(textMode);
        shareTextContentFieldRow.setVisible(textMode);
        shareFileFieldRow.setManaged(!textMode);
        shareFileFieldRow.setVisible(!textMode);
    }

    private void refreshShareComposeModalState() {
        if (shareTextModeRadio == null || shareFileModeRadio == null) {
            return;
        }
        if (shareComposeToggleButton != null) {
            shareComposeToggleButton.setTooltip(new Tooltip("创建分享"));
        }
        shareTextModeRadio.setSelected(selectedShareComposeMode == ShareRules.ComposeMode.TEXT);
        shareFileModeRadio.setSelected(selectedShareComposeMode == ShareRules.ComposeMode.FILE);
        shareNeverStrategyRadio.setSelected(selectedShareStrategy == ShareRules.StrategyKey.NEVER);
        shareExpireStrategyRadio.setSelected(selectedShareStrategy == ShareRules.StrategyKey.EXPIRE);
        shareOnceStrategyRadio.setSelected(selectedShareStrategy == ShareRules.StrategyKey.ONCE);
        if (shareSelectedFileLabel != null) {
            shareSelectedFileLabel.setText(
                selectedShareFilePath == null ? "未选择文件" : nonBlank(selectedShareFilePath.getFileName().toString(), selectedShareFilePath.toString())
            );
        }
        refreshShareComposeMode();
        refreshShareStrategySummary();
    }

    private void applySelectedShareFile(Path filePath) {
        // 中文注释：统一处理文件分享的选中状态，避免按钮选择、拖拽和创建成功后的清理逻辑分散。
        selectedShareFilePath = filePath;
        if (filePath != null) {
            selectedShareComposeMode = ShareRules.ComposeMode.FILE;
        }
        refreshShareComposeModalState();
    }

    private void showShareComposeNotice(String message) {
        if (shareComposeNoticeLabel == null) {
            showToast(message);
            return;
        }
        shareComposeNoticeLabel.setText(message);
        shareComposeNoticeLabel.setVisible(true);
        shareComposeNoticeLabel.setManaged(true);
    }

    private void clearShareComposeNotice() {
        if (shareComposeNoticeLabel == null) {
            return;
        }
        shareComposeNoticeLabel.setText("");
        shareComposeNoticeLabel.setVisible(false);
        shareComposeNoticeLabel.setManaged(false);
    }

    private void refreshShareSettingsControls() {
        shareCountdownPresetBox.setDisable(!shareOnceShowCountdownCheck.isSelected());
    }

    private void refreshShareStrategySummary() {
        ShareRules.Config rules = stateStore.getState().getShareRules();
        if (shareStrategySummaryLabel != null) {
            ShareRules.StrategySummary summary = rules.buildStrategySummary(selectedShareStrategy);
            shareStrategySummaryLabel.setText(
                summary.title() + " · " + summary.description() + " · " + summary.copyLabel()
            );
        }
    }

    private void persistShareRulesFromControls() {
        ShareRules.Config updatedRules = new ShareRules.Config(
            new ShareRules.NeverRule(shareNeverAllowCopyCheck.isSelected()),
            new ShareRules.ExpireRule(
                shareExpirePresetBox.getValue() == null ? ShareRules.ExpirePreset.ONE_DAY : shareExpirePresetBox.getValue(),
                shareExpireAllowCopyCheck.isSelected()
            ),
            new ShareRules.OnceRule(
                shareOnceShowCountdownCheck.isSelected(),
                shareCountdownPresetBox.getValue() == null ? ShareRules.CountdownPreset.TEN_SECONDS : shareCountdownPresetBox.getValue(),
                shareOnceAllowCopyCheck.isSelected()
            )
        );
        stateStore.update(state -> state.setShareRules(updatedRules));
        refreshShareSettingsControls();
        refreshShareStrategySummary();
    }

    private void handleSyncDataChanged() {
        // 中文注释：文本同步和文件自动上传都会走这里，所以历史页和文件页都要标记为需要刷新。
        loadedPages.remove(NavPage.HISTORY);
        loadedPages.remove(NavPage.FILES);
        if (currentPage == NavPage.HISTORY) {
            refreshHistoryPageAsync(true);
        } else if (currentPage == NavPage.FILES) {
            refreshFilesPageAsync(filePage);
        } else {
            refreshTopBarFromState();
        }
    }

    private void handleLogout(boolean callServer) {
        if (callServer) {
            runTask(
                () -> {
                    apiClient.logout();
                    return null;
                },
                ignored -> {
                    loadedPages.clear();
                    showLoginScene();
                    showToast("已退出登录");
                },
                error -> {
                    // 中文注释：即使服务端退出失败，本地仍然应该立刻清理登录态。
                    stateStore.update(AppState::clearSession);
                    loadedPages.clear();
                    showLoginScene();
                    showToast("已退出登录（服务端清理失败: " + error.getMessage() + "）");
                }
            );
        } else {
            stateStore.update(AppState::clearSession);
            loadedPages.clear();
            showLoginScene();
        }
    }

    private void handleSessionExpired(String message) {
        loadedPages.clear();
        showLoginScene();
        setLoginHint(message, true);
        showToast(message);
    }

    private void showMainScene() {
        refreshTopBarFromState();
        primaryStage.setScene(mainScene);
        primaryStage.centerOnScreen();
        primaryStage.show();
    }

    private void showLoginScene() {
        applyLoginStateToForm();
        closeSettingsModal();
        closeShareComposeModal();
        primaryStage.setScene(loginScene);
        primaryStage.centerOnScreen();
        primaryStage.show();
        loginSubmitButton.setDisable(false);
        loginSwitchModeButton.setDisable(false);
    }

    private void applyLoginStateToForm() {
        AppState state = stateStore.getState();
        if (loginBaseUrlField != null) {
            loginBaseUrlField.setText(state.getBaseUrl());
        }
        if (loginDeviceNameField != null) {
            loginDeviceNameField.setText(state.getDeviceName());
        }
        if (loginUsernameField != null) {
            loginUsernameField.setText(state.getUsername());
        }
        if (loginHintLabel != null) {
            setLoginHint("输入账号后进入控制台。", false);
        }
    }

    private void updateLoginModeText() {
        if (loginModeLabel == null) {
            return;
        }
        loginModeLabel.setText(registerMode ? "注册" : "登录");
        loginSubmitButton.setText(registerMode ? "注册并进入" : "登录");
        loginSwitchModeButton.setText(registerMode ? "返回登录" : "创建账号");
    }

    private void setLoginHint(String message, boolean error) {
        loginHintLabel.setText(message);
        loginHintLabel.getStyleClass().removeAll("status-error", "status-ok");
        loginHintLabel.getStyleClass().add(error ? "status-error" : "status-ok");
    }

    private void loadImages() {
        appIconImage = loadImage("/icons/icon.png");
        authHeroImage = loadImage("/assets/illustrations/p0/auth-hero.png");
        dashboardHeroImage = loadImage("/assets/illustrations/p1/dashboard-hero.png");
        emptyHistoryImage = loadImage("/assets/illustrations/p0/empty-history.png");
        emptyDevicesImage = loadImage("/assets/illustrations/p0/empty-devices.png");
        emptyFilesImage = loadImage("/assets/illustrations/p1/empty-files.png");
        emptySharesImage = loadImage("/assets/illustrations/p1/empty-shares.png");
        emptyRequestsImage = loadImage("/assets/illustrations/p1/empty-requests.png");
        offlineStateImage = loadImage("/assets/illustrations/p0/state-server-offline.png");
    }

    private Image loadImage(String resourcePath) {
        try {
            return new Image(Objects.requireNonNull(getClass().getResourceAsStream(resourcePath)));
        } catch (Exception error) {
            return null;
        }
    }

    private void setupTray() {
        if (!SystemTray.isSupported()) {
            return;
        }
        try {
            BufferedImage image = ImageIO.read(Objects.requireNonNull(getClass().getResourceAsStream("/icons/icon.png")));
            trayIcon = new TrayIcon(image, "ClipBridge");
            trayIcon.setImageAutoSize(true);
            trayIcon.addActionListener(event -> Platform.runLater(this::showMainWindowFromTray));

            PopupMenu menu = new PopupMenu();
            MenuItem showWindowItem = new MenuItem("Open Window");
            showWindowItem.addActionListener(event -> Platform.runLater(this::showMainWindowFromTray));

            MenuItem syncNowItem = new MenuItem("Sync Now");
            syncNowItem.addActionListener(event -> Platform.runLater(() -> {
                syncService.requestImmediateSync();
                showToast("已触发立即同步");
            }));

            trayToggleSyncMenuItem = new MenuItem("Disable Sync");
            trayToggleSyncMenuItem.addActionListener(event -> Platform.runLater(() -> toggleSyncEnabled(!stateStore.getState().isSyncEnabled())));

            MenuItem exitItem = new MenuItem("Exit");
            exitItem.addActionListener(event -> Platform.runLater(() -> {
                if (callBestEffortLogoutOnExit()) {
                    Platform.exit();
                }
            }));

            menu.add(showWindowItem);
            menu.add(syncNowItem);
            menu.add(trayToggleSyncMenuItem);
            menu.addSeparator();
            menu.add(exitItem);
            trayIcon.setPopupMenu(menu);

            SystemTray.getSystemTray().add(trayIcon);
            updateTrayMenuLabel();
        } catch (Exception error) {
            trayIcon = null;
        }
    }

    private boolean callBestEffortLogoutOnExit() {
        try {
            if (stateStore.getState().isLoggedIn()) {
                apiClient.logout();
            }
        } catch (Exception ignore) {
        }
        return true;
    }

    private void showMainWindowFromTray() {
        primaryStage.show();
        primaryStage.toFront();
        primaryStage.requestFocus();
    }

    private void updateTrayMenuLabel() {
        if (trayToggleSyncMenuItem != null) {
            trayToggleSyncMenuItem.setLabel(stateStore.getState().isSyncEnabled() ? "Disable Sync" : "Enable Sync");
        }
    }

    private void showToast(String message) {
        if (message == null || message.isBlank()) {
            return;
        }
        globalStatusLabel.setText(message);
        toastClearTimer.stop();
        toastClearTimer.setOnFinished(event -> {
            if (globalStatusLabel.getText().equals(message)) {
                globalStatusLabel.setText("就绪");
            }
        });
        toastClearTimer.playFromStart();
    }

    private void showPageNotice(NavPage page, String message) {
        Label label = pageNoticeLabels.get(page);
        if (label == null) {
            showToast(message);
            return;
        }
        label.setText(message);
        label.setVisible(true);
        label.setManaged(true);
    }

    private void clearPageNotice(NavPage page) {
        Label label = pageNoticeLabels.get(page);
        if (label == null) {
            return;
        }
        label.setText("");
        label.setVisible(false);
        label.setManaged(false);
    }

    private void showSettingsNotice(String message) {
        if (settingsDialogNoticeLabel == null) {
            showToast(message);
            return;
        }
        settingsDialogNoticeLabel.setText(message);
        settingsDialogNoticeLabel.setVisible(true);
        settingsDialogNoticeLabel.setManaged(true);
    }

    private void clearSettingsNotice() {
        if (settingsDialogNoticeLabel == null) {
            return;
        }
        settingsDialogNoticeLabel.setText("");
        settingsDialogNoticeLabel.setVisible(false);
        settingsDialogNoticeLabel.setManaged(false);
    }

    private String validateQuotaRequestInput(long requestedMb, String reason) {
        if (requestedMb <= 0) {
            return "配额申请值必须是正整数 MB。";
        }

        long currentQuotaBytes = stateStore.getState().getStorageQuotaBytes();
        long requestedQuotaBytes = toQuotaBytes(requestedMb);
        if (currentQuotaBytes > 0 && requestedQuotaBytes <= currentQuotaBytes) {
            return "申请配额需要大于当前配额。";
        }

        if (nonBlank(reason, "").trim().length() > 500) {
            return "申请理由不能超过 500 个字。";
        }
        return "";
    }

    private void handleQuotaRequestSubmitError(Throwable error) {
        String message = resolveQuotaRequestSubmitErrorMessage(error);
        if (shouldUseToastForQuotaRequestError(error, message)) {
            showToast(message);
            return;
        }
        showPageNotice(NavPage.REQUESTS, message);
    }

    private String resolveQuotaRequestSubmitErrorMessage(Throwable error) {
        String rawMessage = error == null ? "" : nonBlank(error.getMessage(), "");
        String normalized = rawMessage.toLowerCase(Locale.ROOT);

        // 中文注释：后端当前对“申请值不大于当前配额”会返回通用 invalid payload，
        // 这里改成用户能直接看懂的提示，避免出现生硬英文。
        if (normalized.contains("request payload is invalid")
            || (normalized.contains("quota") && normalized.contains("invalid"))) {
            return "申请配额需要大于当前配额。";
        }
        if (normalized.contains("reason") && normalized.contains("500")) {
            return "申请理由不能超过 500 个字。";
        }
        if (rawMessage.contains("网络异常") || rawMessage.contains("请先配置服务地址") || rawMessage.contains("登录已失效")) {
            return rawMessage;
        }
        return "提交配额申请失败，请稍后重试。";
    }

    private boolean shouldUseToastForQuotaRequestError(Throwable error, String message) {
        if (message.contains("当前配额") || message.contains("500 个字")) {
            return true;
        }
        if (error instanceof ApiException apiException) {
            return apiException.getStatusCode() > 0 && apiException.getStatusCode() < 500;
        }
        return false;
    }

    private long toQuotaBytes(long requestedMb) {
        try {
            return Math.multiplyExact(requestedMb, BYTES_PER_MB);
        } catch (ArithmeticException ignore) {
            return Long.MAX_VALUE;
        }
    }

    private void runTask(
        CheckedRunnable action,
        Runnable onSuccess,
        Consumer<Exception> onError
    ) {
        runTask(() -> {
            action.run();
            return null;
        }, ignored -> onSuccess.run(), onError);
    }

    private <T> void runTask(
        CheckedSupplier<T> action,
        Consumer<T> onSuccess,
        Consumer<Exception> onError
    ) {
        ioExecutor.submit(() -> {
            try {
                T result = action.get();
                Platform.runLater(() -> onSuccess.accept(result));
            } catch (Exception error) {
                Platform.runLater(() -> {
                    if (error instanceof ApiException apiError && apiError.isUnauthorized()) {
                        handleSessionExpired("登录已失效，请重新登录");
                        return;
                    }
                    onError.accept(error);
                });
            }
        });
    }

    private VBox createCardBox() {
        VBox box = new VBox(14);
        box.getStyleClass().add("card");
        box.setPadding(new Insets(20));
        return box;
    }

    private VBox createStatCard(String titleText, Label valueLabel, String hint) {
        VBox card = createCardBox();
        card.getStyleClass().add("stat-card");
        Label title = new Label(titleText);
        title.getStyleClass().add("field-label");
        valueLabel.getStyleClass().add("stat-value");
        Label hintLabel = createSmallMutedLabel(hint);
        card.getChildren().addAll(title, valueLabel, hintLabel);
        card.setPrefWidth(220);
        return card;
    }

    private VBox createInlineInfoCard(String titleText, Label bodyLabel) {
        VBox card = createCardBox();
        card.getStyleClass().add("inline-card");
        Label title = new Label(titleText);
        title.getStyleClass().add("field-label");
        card.getChildren().addAll(title, bodyLabel);
        card.setPrefWidth(250);
        return card;
    }

    private VBox createSummaryCard(String titleText) {
        VBox card = createCardBox();
        card.getStyleClass().add("summary-card");
        Label title = new Label(titleText);
        title.getStyleClass().add("section-title");
        card.getChildren().add(title);
        HBox.setHgrow(card, Priority.ALWAYS);
        return card;
    }

    private HBox createSummaryRow(String labelText, Label valueLabel) {
        HBox row = new HBox(12);
        row.getStyleClass().add("summary-row");
        row.setAlignment(Pos.CENTER_LEFT);

        Label label = new Label(labelText);
        label.getStyleClass().add("summary-row-label");

        valueLabel.getStyleClass().add("summary-row-value");
        valueLabel.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(valueLabel, Priority.ALWAYS);

        row.getChildren().addAll(label, valueLabel);
        return row;
    }

    private Button createModuleTile(NavPage page, String description) {
        Button button = new Button();
        button.getStyleClass().add("module-tile");
        button.setMaxWidth(Double.MAX_VALUE);
        button.setAlignment(Pos.TOP_LEFT);

        VBox copy = new VBox(6);
        copy.getStyleClass().add("module-tile-copy");
        Label title = new Label(page.getLabel());
        title.getStyleClass().add("module-tile-title");
        Label hint = new Label(description);
        hint.getStyleClass().add("module-tile-hint");
        hint.setWrapText(true);
        copy.getChildren().addAll(title, hint);

        button.setGraphic(copy);
        return button;
    }

    private VBox createRequestCard(String titleText, List<Node> children) {
        VBox box = createCardBox();
        Label title = new Label(titleText);
        title.getStyleClass().add("section-title");
        box.getChildren().add(title);
        box.getChildren().addAll(children);
        return box;
    }

    private TitledPane createSection(String titleText, String description, Node content, boolean expanded) {
        VBox wrapper = createCardBox();
        wrapper.getChildren().addAll(createBodyLabel(description), content);
        TitledPane pane = new TitledPane(titleText, wrapper);
        pane.setExpanded(expanded);
        pane.getStyleClass().add("section-pane");
        return pane;
    }

    private HBox createLabeledField(String labelText, Node field) {
        VBox box = new VBox(8);
        box.getChildren().addAll(createFieldLabel(labelText), field);
        HBox row = new HBox(box);
        HBox.setHgrow(box, Priority.ALWAYS);
        return row;
    }

    private Label createFieldLabel(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("field-label");
        return label;
    }

    private Label createHeadlineLabel(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("hero-title");
        label.setWrapText(true);
        return label;
    }

    private Label createBodyLabel(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("hero-body");
        label.setWrapText(true);
        return label;
    }

    private Label createKickerLabel(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("hero-kicker");
        return label;
    }

    private Label createSmallMutedLabel(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("small-muted");
        label.setWrapText(true);
        return label;
    }

    private Label createSecondaryValueLabel(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("secondary-value");
        label.setWrapText(true);
        return label;
    }

    private VBox createMetaBlock(String titleText, Label valueLabel) {
        VBox box = new VBox(2);
        box.getStyleClass().add("topbar-meta-block");
        Label title = new Label(titleText);
        title.getStyleClass().add("topbar-meta-title");
        box.getChildren().addAll(title, valueLabel);
        return box;
    }

    private Label createMetaValueLabel(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("topbar-meta-value");
        return label;
    }

    private Button createPrimaryButton(String text) {
        Button button = new Button(text);
        button.getStyleClass().addAll("app-button", "button-primary");
        return button;
    }

    private Button createGhostButton(String text) {
        Button button = new Button(text);
        button.getStyleClass().addAll("app-button", "button-ghost");
        return button;
    }

    private Button createDangerButton(String text) {
        Button button = new Button(text);
        button.getStyleClass().addAll("app-button", "button-danger");
        return button;
    }

    private Button createIconButton(String tooltipText) {
        Button button = new Button();
        button.getStyleClass().addAll("app-button", "button-ghost", "icon-button");
        if (tooltipText != null && !tooltipText.isBlank()) {
            button.setTooltip(new Tooltip(tooltipText));
        }
        return button;
    }

    private SVGPath createShareIconGraphic() {
        SVGPath path = new SVGPath();
        // 中文注释：这里用简单矢量路径画一个“向外分享”的图标，避免额外依赖图片资源。
        path.setContent("M4 13 L4 6 L11 6 M7 4 L14 4 L14 11 M5 13 L14 4");
        path.getStyleClass().add("icon-shape");
        path.setScaleX(1.2);
        path.setScaleY(1.2);
        return path;
    }

    private Button createNavButton(NavPage page) {
        Button button = new Button(page.getLabel());
        button.getStyleClass().add("nav-button");
        button.setMaxWidth(Double.MAX_VALUE);
        button.setAlignment(Pos.CENTER_LEFT);
        button.setOnAction(event -> showPage(page, false));
        return button;
    }

    private RadioButton createChoiceButton(String text, ToggleGroup group, boolean selected) {
        RadioButton button = new RadioButton(text);
        button.setToggleGroup(group);
        button.setSelected(selected);
        button.getStyleClass().add("choice-button");
        return button;
    }

    private Node createBrandMark() {
        HBox box = new HBox(12);
        box.setAlignment(Pos.CENTER_LEFT);
        if (appIconImage != null) {
            ImageView imageView = new ImageView(appIconImage);
            imageView.setFitHeight(48);
            imageView.setPreserveRatio(true);
            box.getChildren().add(imageView);
        }
        box.getChildren().add(createHeadlineLabel("ClipBridge"));
        return box;
    }

    private Node createHeroImageView(Image image, double fitWidth) {
        if (image == null) {
            Label fallback = new Label("插画资源缺失");
            fallback.getStyleClass().add("hero-image-fallback");
            return fallback;
        }
        ImageView imageView = new ImageView(image);
        imageView.setPreserveRatio(true);
        imageView.setFitWidth(fitWidth);
        imageView.getStyleClass().add("hero-image");
        return imageView;
    }

    private Node createEmptyState(Image image, String title, String description, String actionText, Runnable action) {
        VBox box = new VBox(14);
        box.getStyleClass().add("empty-state");
        box.setAlignment(Pos.CENTER);
        box.setPadding(new Insets(28));
        box.getChildren().add(createHeroImageView(image == null ? offlineStateImage : image, 180));
        Label titleLabel = new Label(title);
        titleLabel.getStyleClass().add("empty-title");
        Label descLabel = createBodyLabel(description);
        descLabel.setMaxWidth(320);
        Button actionButton = createGhostButton(actionText);
        actionButton.setOnAction(event -> action.run());
        box.getChildren().addAll(titleLabel, descLabel, actionButton);
        return box;
    }

    private StackPane createListPane(ListView<?> listView, ObservableList<?> items, Node emptyState) {
        StackPane stackPane = new StackPane(listView, emptyState);
        stackPane.getStyleClass().add("list-pane");
        VBox.setVgrow(stackPane, Priority.ALWAYS);
        emptyState.visibleProperty().bind(Bindings.isEmpty(items));
        emptyState.managedProperty().bind(emptyState.visibleProperty());
        listView.visibleProperty().bind(Bindings.isNotEmpty(items));
        listView.managedProperty().bind(listView.visibleProperty());
        return stackPane;
    }

    private Node wrapScrollablePage(Node content) {
        ScrollPane scrollPane = new ScrollPane(content);
        scrollPane.setFitToWidth(true);
        scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        scrollPane.getStyleClass().add("page-scroll");
        return scrollPane;
    }

    private void configureFileDropZone(Node node, Consumer<List<Path>> onDropped) {
        node.setOnDragOver(event -> {
            Dragboard dragboard = event.getDragboard();
            if (dragboard.hasFiles()) {
                event.acceptTransferModes(TransferMode.COPY);
            }
            event.consume();
        });
        node.setOnDragEntered(event -> node.getStyleClass().add("is-dragover"));
        node.setOnDragExited(event -> node.getStyleClass().remove("is-dragover"));
        node.setOnDragDropped(event -> {
            List<Path> paths = event.getDragboard().getFiles().stream().map(File::toPath).toList();
            onDropped.accept(paths);
            event.setDropCompleted(!paths.isEmpty());
            event.consume();
            node.getStyleClass().remove("is-dragover");
        });
    }

    private ListCell<QuotaRequest> buildQuotaRequestCell() {
        return new ListCell<>() {
            @Override
            protected void updateItem(QuotaRequest item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setGraphic(null);
                    return;
                }
                VBox box = createRequestRecordBox(
                    item.username() + " · " + statusText(item.status()),
                    "当前 " + formatBytes(item.currentQuotaBytes()) + " · 申请 " + formatBytes(item.requestedQuotaBytes()),
                    item.reason(),
                    item.reviewNote(),
                    item.createdAt(),
                    item.reviewedAt()
                );
                setGraphic(box);
            }
        };
    }

    private ListCell<BandwidthRequest> buildBandwidthRequestCell() {
        return new ListCell<>() {
            @Override
            protected void updateItem(BandwidthRequest item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setGraphic(null);
                    return;
                }
                VBox box = createRequestRecordBox(
                    item.username() + " · " + statusText(item.status()),
                    "当前 " + BandwidthUnitUtils.formatBandwidth(item.currentUploadKbps()) + " / " + BandwidthUnitUtils.formatBandwidth(item.currentDownloadKbps()) +
                        " · 申请 " + BandwidthUnitUtils.formatBandwidth(item.requestedUploadKbps()) + " / " + BandwidthUnitUtils.formatBandwidth(item.requestedDownloadKbps()),
                    item.reason(),
                    item.reviewNote(),
                    item.createdAt(),
                    item.reviewedAt()
                );
                setGraphic(box);
            }
        };
    }

    private ListCell<AdminRequest> buildAdminRequestCell() {
        return new ListCell<>() {
            @Override
            protected void updateItem(AdminRequest item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setGraphic(null);
                    return;
                }
                VBox box = createRequestRecordBox(
                    item.username() + " · " + statusText(item.status()),
                    "管理员权限申请",
                    item.reason(),
                    item.reviewNote(),
                    item.createdAt(),
                    item.reviewedAt()
                );
                setGraphic(box);
            }
        };
    }

    private VBox createRequestRecordBox(
        String title,
        String summary,
        String reason,
        String reviewNote,
        String createdAt,
        String reviewedAt
    ) {
        VBox box = new VBox(8);
        box.getStyleClass().add("request-record-card");
        Label titleLabel = new Label(title);
        titleLabel.getStyleClass().add("list-item-title");
        Label summaryLabel = createSmallMutedLabel(summary);
        Label reasonLabel = createBodyLabel("申请说明： " + nonBlank(reason, "-"));
        Label reviewLabel = createSmallMutedLabel("审核备注： " + nonBlank(reviewNote, "-"));
        Label metaLabel = createSmallMutedLabel("创建于 " + formatTime(createdAt) + " · 审核于 " + nonBlank(formatTime(reviewedAt), "未审核"));
        box.getChildren().addAll(titleLabel, summaryLabel, reasonLabel, reviewLabel, metaLabel);
        return box;
    }

    private void writeTextToClipboard(String text) {
        javafx.scene.input.Clipboard clipboard = javafx.scene.input.Clipboard.getSystemClipboard();
        ClipboardContent content = new ClipboardContent();
        content.putString(text == null ? "" : text);
        clipboard.setContent(content);
    }

    private void copyShareLink(String link) {
        if (link == null || link.isBlank()) {
            showToast("当前没有可复制的分享链接");
            return;
        }
        writeTextToClipboard(link);
        showToast("分享链接已复制");
    }

    private void openShareLink(String link) {
        if (link == null || link.isBlank()) {
            showToast("当前没有可打开的分享链接");
            return;
        }
        try {
            Desktop.getDesktop().browse(URI.create(link));
            showToast("已在系统浏览器中打开分享链接");
        } catch (Exception error) {
            showToast("打开分享链接失败: " + error.getMessage());
        }
    }

    private void openProjectGithub() {
        try {
            Desktop.getDesktop().browse(URI.create(PROJECT_GITHUB_URL));
            showToast("已在系统浏览器中打开 GitHub 仓库");
        } catch (Exception error) {
            showToast("打开 GitHub 仓库失败: " + error.getMessage());
        }
    }

    private boolean confirmDanger(String message) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("请确认");
        alert.setHeaderText("这是一个危险操作");
        alert.setContentText(message);
        Optional<ButtonType> result = alert.showAndWait();
        return result.isPresent() && result.get() == ButtonType.OK;
    }

    private String buildSyncSummary() {
        AppState state = stateStore.getState();
        String enabledText = state.isSyncEnabled() ? "已开启" : "已关闭";
        String transportText = syncService != null && syncService.isWebSocketConnected() ? "WebSocket 在线" : "pull 兜底";
        return enabledText + " · " + transportText + " · ack=" + state.getLastAckSeq();
    }

    private void updateSyncControls() {
        AppState state = stateStore.getState();
        if (overviewSyncToggleButton != null) {
            overviewSyncToggleButton.setText(state.isSyncEnabled() ? "关闭同步" : "开启同步");
        }
        if (topSyncToggleButton != null) {
            topSyncToggleButton.setText(state.isSyncEnabled() ? "关闭同步" : "开启同步");
        }
        if (statusSyncToggleButton != null) {
            statusSyncToggleButton.setText(state.isSyncEnabled() ? "关闭同步" : "开启同步");
        }
        if (overviewSyncValueLabel != null) {
            overviewSyncValueLabel.setText(buildSyncSummary());
        }
        if (statusSyncLabel != null) {
            statusSyncLabel.setText("同步: " + buildSyncSummary());
        }
        refreshTopBarFromStateWithoutRecursion();
    }

    private void refreshTopBarFromStateWithoutRecursion() {
        AppState state = stateStore.getState();
        if (topUserLabel != null) {
            topUserLabel.setText(nonBlank(state.getUsername(), "未登录"));
        }
    }

    private String buildPendingFlags(AdminUser user) {
        List<String> flags = new ArrayList<>();
        if (user.hasPendingQuotaRequest()) {
            flags.add("配额");
        }
        if (user.hasPendingBandwidthRequest()) {
            flags.add("带宽");
        }
        if (user.hasPendingAdminRequest()) {
            flags.add("管理员");
        }
        return flags.isEmpty() ? "无" : String.join(" / ", flags);
    }

    private String buildShareTitle(ShareItem share) {
        if (share.hasFileContent() && share.file() != null) {
            return share.file().originalName();
        }
        return nonBlank(trimPreview(share.textPreview(), 40), "未命名文本分享");
    }

    private String burnModeText(String burnMode) {
        String normalized = nonBlank(burnMode, "none").toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "once" -> "成功打开一次后失效";
            case "countdown" -> "首次打开后倒计时失效";
            default -> "不焚毁";
        };
    }

    private String statusText(String status) {
        String normalized = nonBlank(status, "unknown").toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "pending" -> "待处理";
            case "approved" -> "已通过";
            case "rejected" -> "已拒绝";
            case "active" -> "可访问";
            case "expired" -> "已过期";
            case "consumed" -> "已焚毁";
            case "revoked" -> "已撤销";
            default -> status;
        };
    }

    private String formatBytes(long bytes) {
        if (bytes < 1024L) {
            return bytes + " B";
        }
        double kb = bytes / 1024.0;
        if (kb < 1024.0) {
            return String.format(Locale.ROOT, "%.1f KB", kb);
        }
        double mb = kb / 1024.0;
        if (mb < 1024.0) {
            return String.format(Locale.ROOT, "%.1f MB", mb);
        }
        double gb = mb / 1024.0;
        return String.format(Locale.ROOT, "%.2f GB", gb);
    }

    private String formatTime(String raw) {
        if (raw == null || raw.isBlank()) {
            return "";
        }
        try {
            return OffsetDateTime.parse(raw).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
        } catch (Exception ignore) {
            return raw;
        }
    }

    private String trimPreview(String text, int maxLength) {
        String normalized = nonBlank(text, "").replaceAll("\\s+", " ").trim();
        if (normalized.length() <= maxLength) {
            return normalized;
        }
        return normalized.substring(0, maxLength - 1) + "…";
    }

    private String trimMiddle(String text, int maxLength) {
        String normalized = nonBlank(text, "");
        if (normalized.length() <= maxLength) {
            return normalized;
        }
        int prefix = Math.max(4, maxLength / 2 - 2);
        int suffix = Math.max(4, maxLength - prefix - 1);
        return normalized.substring(0, prefix) + "…" + normalized.substring(normalized.length() - suffix);
    }

    private String nonBlank(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private int parseInt(String value, int fallback) {
        try {
            return Integer.parseInt(value == null ? "" : value.trim());
        } catch (Exception ignore) {
            return fallback;
        }
    }

    private long parseLong(String value, long fallback) {
        try {
            return Long.parseLong(value == null ? "" : value.trim());
        } catch (Exception ignore) {
            return fallback;
        }
    }

    private Integer parseOptionalInt(String value) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isBlank()) {
            return null;
        }
        return Integer.parseInt(normalized);
    }

    private Long parseOptionalLong(String value) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isBlank()) {
            return null;
        }
        return Long.parseLong(normalized);
    }

    private record RequestBundle(
        ApiModels.RequestListResult<QuotaRequest> quota,
        ApiModels.RequestListResult<BandwidthRequest> bandwidth,
        ApiModels.RequestListResult<AdminRequest> admin
    ) {
    }

    private record AdminBundle(
        AdminSettings settings,
        List<AdminUser> users,
        List<QuotaRequest> quotaRequests,
        List<BandwidthRequest> bandwidthRequests,
        List<AdminRequest> adminRequests
    ) {
    }

    private record AccordionWithContent(VBox root) {
    }

    private enum NavPage {
        OVERVIEW("总览"),
        HISTORY("历史"),
        FILES("文件"),
        DEVICES("设备"),
        SHARES("分享"),
        REQUESTS("申请"),
        ADMIN("管理"),
        SETTINGS("设置");

        private final String label;

        NavPage(String label) {
            this.label = label;
        }

        public String getLabel() {
            return label;
        }
    }

    private enum SettingsModule {
        GENERAL("常规"),
        SHARES("分享"),
        SECURITY("安全"),
        SESSION("会话"),
        ABOUT("关于");

        private final String title;

        SettingsModule(String title) {
            this.title = title;
        }

        public String getTitle() {
            return title;
        }
    }

    @FunctionalInterface
    private interface CheckedRunnable {
        void run() throws Exception;
    }

    @FunctionalInterface
    private interface CheckedSupplier<T> {
        T get() throws Exception;
    }

    private final class QuotaReviewCell extends ListCell<QuotaRequest> {
        @Override
        protected void updateItem(QuotaRequest item, boolean empty) {
            super.updateItem(item, empty);
            if (empty || item == null) {
                setGraphic(null);
                return;
            }
            VBox card = createRequestRecordBox(
                item.username() + " · " + formatBytes(item.currentQuotaBytes()) + " -> " + formatBytes(item.requestedQuotaBytes()),
                "待审批配额申请",
                item.reason(),
                item.reviewNote(),
                item.createdAt(),
                item.reviewedAt()
            );
            Button approveButton = createPrimaryButton("批准");
            approveButton.setOnAction(event -> approveQuotaRequest(item));
            Button rejectButton = createDangerButton("拒绝");
            rejectButton.setOnAction(event -> rejectQuotaRequest(item));
            HBox actions = new HBox(10, approveButton, rejectButton);
            VBox wrapper = new VBox(10, card, actions);
            wrapper.getStyleClass().add("review-card");
            setGraphic(wrapper);
        }
    }

    private final class BandwidthReviewCell extends ListCell<BandwidthRequest> {
        @Override
        protected void updateItem(BandwidthRequest item, boolean empty) {
            super.updateItem(item, empty);
            if (empty || item == null) {
                setGraphic(null);
                return;
            }
            VBox card = createRequestRecordBox(
                item.username() + " · " +
                    BandwidthUnitUtils.formatBandwidth(item.currentUploadKbps()) + " / " + BandwidthUnitUtils.formatBandwidth(item.currentDownloadKbps()) +
                    " -> " + BandwidthUnitUtils.formatBandwidth(item.requestedUploadKbps()) + " / " + BandwidthUnitUtils.formatBandwidth(item.requestedDownloadKbps()),
                "待审批带宽申请",
                item.reason(),
                item.reviewNote(),
                item.createdAt(),
                item.reviewedAt()
            );
            Button approveButton = createPrimaryButton("批准");
            approveButton.setOnAction(event -> approveBandwidthRequest(item));
            Button rejectButton = createDangerButton("拒绝");
            rejectButton.setOnAction(event -> rejectBandwidthRequest(item));
            HBox actions = new HBox(10, approveButton, rejectButton);
            VBox wrapper = new VBox(10, card, actions);
            wrapper.getStyleClass().add("review-card");
            setGraphic(wrapper);
        }
    }

    private final class AdminReviewCell extends ListCell<AdminRequest> {
        @Override
        protected void updateItem(AdminRequest item, boolean empty) {
            super.updateItem(item, empty);
            if (empty || item == null) {
                setGraphic(null);
                return;
            }
            VBox card = createRequestRecordBox(
                item.username() + " · 管理员申请",
                "待审批管理员申请",
                item.reason(),
                item.reviewNote(),
                item.createdAt(),
                item.reviewedAt()
            );
            Button approveButton = createPrimaryButton("批准");
            approveButton.setOnAction(event -> approveAdminRequest(item));
            Button rejectButton = createDangerButton("拒绝");
            rejectButton.setOnAction(event -> rejectAdminRequest(item));
            HBox actions = new HBox(10, approveButton, rejectButton);
            VBox wrapper = new VBox(10, card, actions);
            wrapper.getStyleClass().add("review-card");
            setGraphic(wrapper);
        }
    }

    private void approveQuotaRequest(QuotaRequest request) {
        ReviewDialogResult result = showReviewDialog(
            "批准配额申请",
            "批准配额 MB",
            String.valueOf(request.requestedQuotaBytes() / (1024L * 1024L)),
            true
        );
        if (result == null) {
            return;
        }
        runTask(
            () -> apiClient.approveQuotaRequest(request.id(), parseOptionalLong(result.primaryValue()), result.note()),
            updated -> {
                showToast("配额申请已批准");
                loadedPages.remove(NavPage.ADMIN);
                refreshAdminPageAsync();
            },
            error -> showPageNotice(NavPage.ADMIN, "批准配额申请失败: " + error.getMessage())
        );
    }

    private void rejectQuotaRequest(QuotaRequest request) {
        ReviewDialogResult result = showReviewDialog("拒绝配额申请", "", "", false);
        if (result == null) {
            return;
        }
        runTask(
            () -> apiClient.rejectQuotaRequest(request.id(), result.note()),
            updated -> {
                showToast("配额申请已拒绝");
                loadedPages.remove(NavPage.ADMIN);
                refreshAdminPageAsync();
            },
            error -> showPageNotice(NavPage.ADMIN, "拒绝配额申请失败: " + error.getMessage())
        );
    }

    private void approveBandwidthRequest(BandwidthRequest request) {
        ReviewDialogResult result = showDualReviewDialog(
            "批准带宽申请",
            "批准上传 MB/s",
            BandwidthUnitUtils.toBandwidthInput(request.requestedUploadKbps()),
            "批准下载 MB/s",
            BandwidthUnitUtils.toBandwidthInput(request.requestedDownloadKbps())
        );
        if (result == null) {
            return;
        }
        runTask(
            () -> apiClient.approveBandwidthRequest(
                request.id(),
                BandwidthUnitUtils.parseBandwidthMbOptional(result.primaryValue()),
                BandwidthUnitUtils.parseBandwidthMbOptional(result.secondaryValue()),
                result.note()
            ),
            updated -> {
                showToast("带宽申请已批准");
                loadedPages.remove(NavPage.ADMIN);
                refreshAdminPageAsync();
            },
            error -> showPageNotice(NavPage.ADMIN, "批准带宽申请失败: " + error.getMessage())
        );
    }

    private void rejectBandwidthRequest(BandwidthRequest request) {
        ReviewDialogResult result = showReviewDialog("拒绝带宽申请", "", "", false);
        if (result == null) {
            return;
        }
        runTask(
            () -> apiClient.rejectBandwidthRequest(request.id(), result.note()),
            updated -> {
                showToast("带宽申请已拒绝");
                loadedPages.remove(NavPage.ADMIN);
                refreshAdminPageAsync();
            },
            error -> showPageNotice(NavPage.ADMIN, "拒绝带宽申请失败: " + error.getMessage())
        );
    }

    private void approveAdminRequest(AdminRequest request) {
        ReviewDialogResult result = showReviewDialog("批准管理员申请", "", "", false);
        if (result == null) {
            return;
        }
        runTask(
            () -> apiClient.approveAdminRequest(request.id(), result.note()),
            updated -> {
                showToast("管理员申请已批准");
                loadedPages.remove(NavPage.ADMIN);
                refreshAdminPageAsync();
            },
            error -> showPageNotice(NavPage.ADMIN, "批准管理员申请失败: " + error.getMessage())
        );
    }

    private void rejectAdminRequest(AdminRequest request) {
        ReviewDialogResult result = showReviewDialog("拒绝管理员申请", "", "", false);
        if (result == null) {
            return;
        }
        runTask(
            () -> apiClient.rejectAdminRequest(request.id(), result.note()),
            updated -> {
                showToast("管理员申请已拒绝");
                loadedPages.remove(NavPage.ADMIN);
                refreshAdminPageAsync();
            },
            error -> showPageNotice(NavPage.ADMIN, "拒绝管理员申请失败: " + error.getMessage())
        );
    }

    private ReviewDialogResult showReviewDialog(String title, String primaryLabel, String primaryValue, boolean showPrimaryField) {
        return showDualReviewDialog(title, primaryLabel, primaryValue, "", "", showPrimaryField, false);
    }

    private ReviewDialogResult showDualReviewDialog(String title, String firstLabel, String firstValue, String secondLabel, String secondValue) {
        return showDualReviewDialog(title, firstLabel, firstValue, secondLabel, secondValue, true, true);
    }

    private ReviewDialogResult showDualReviewDialog(
        String title,
        String firstLabel,
        String firstValue,
        String secondLabel,
        String secondValue,
        boolean showFirstField,
        boolean showSecondField
    ) {
        javafx.scene.control.Dialog<ReviewDialogResult> dialog = new javafx.scene.control.Dialog<>();
        dialog.setTitle(title);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        TextField firstField = new TextField(firstValue);
        TextField secondField = new TextField(secondValue);
        TextArea noteArea = new TextArea();
        noteArea.setPromptText("审核备注（可选）");
        noteArea.setPrefRowCount(4);

        VBox content = new VBox(12);
        content.setPadding(new Insets(16));
        if (showFirstField) {
            content.getChildren().add(createLabeledField(firstLabel, firstField));
        }
        if (showSecondField) {
            content.getChildren().add(createLabeledField(secondLabel, secondField));
        }
        content.getChildren().add(createLabeledField("审核备注", noteArea));
        dialog.getDialogPane().setContent(content);
        dialog.setResultConverter(buttonType -> {
            if (buttonType == ButtonType.OK) {
                return new ReviewDialogResult(firstField.getText(), secondField.getText(), noteArea.getText());
            }
            return null;
        });
        return dialog.showAndWait().orElse(null);
    }

    private record ReviewDialogResult(String primaryValue, String secondaryValue, String note) {
    }

    private static final class IoThreadFactory implements ThreadFactory {
        private int index = 0;

        @Override
        public synchronized Thread newThread(Runnable runnable) {
            Thread thread = new Thread(runnable, "clipbridge-io-" + (++index));
            thread.setDaemon(true);
            return thread;
        }
    }
}
