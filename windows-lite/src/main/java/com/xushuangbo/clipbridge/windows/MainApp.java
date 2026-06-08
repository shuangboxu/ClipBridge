package com.xushuangbo.clipbridge.windows;

import com.xushuangbo.clipbridge.windows.api.ApiClient;
import com.xushuangbo.clipbridge.windows.state.AppState;
import com.xushuangbo.clipbridge.windows.state.AppStateStore;
import com.xushuangbo.clipbridge.windows.sync.ClipboardSyncService;
import com.xushuangbo.clipbridge.windows.util.ServiceAddressFormatter;
import javafx.animation.PauseTransition;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.SVGPath;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.awt.Desktop;
import java.net.URI;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.function.Consumer;

public class MainApp extends Application {
    private static final int WINDOW_WIDTH = 420;
    private static final int WINDOW_HEIGHT = 760;
    private static final Duration TOAST_DURATION = Duration.seconds(4);

    private final AppStateStore stateStore = new AppStateStore();
    private final ApiClient apiClient = new ApiClient(stateStore);
    private final ExecutorService ioExecutor = Executors.newFixedThreadPool(3, new IoThreadFactory());
    private final PauseTransition toastClearTimer = new PauseTransition(TOAST_DURATION);

    private ClipboardSyncService syncService;
    private Stage primaryStage;
    private BorderPane root;
    private StackPane centerPane;
    private Label toastLabel;

    private Node loginView;
    private Node homeView;
    private Node homeTopBar;

    private TextField loginBaseUrlField;
    private TextField loginUsernameField;
    private PasswordField loginPasswordField;
    private Button loginButton;

    private Button logoutButton;
    private Button openWebButton;
    private Button syncButton;
    private ImageView syncImageView;
    private Label syncStateLabel;
    private CheckBox syncFilesCheck;

    private Image appIconImage;
    private Image syncOnImage;
    private Image syncOffImage;

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage stage) {
        primaryStage = stage;
        loadImages();
        buildScene();

        apiClient.setSessionExpiredListener(() -> Platform.runLater(() -> handleSessionExpired("登录已失效，请重新登录")));
        syncService = new ClipboardSyncService(
            apiClient,
            stateStore,
            message -> Platform.runLater(() -> handleSyncStatus(message)),
            () -> { }
        );
        syncService.start();

        if (appIconImage != null) {
            primaryStage.getIcons().add(appIconImage);
        }
        primaryStage.setTitle("ClipBridge Lite");
        primaryStage.setResizable(false);
        primaryStage.setWidth(WINDOW_WIDTH);
        primaryStage.setHeight(WINDOW_HEIGHT);
        primaryStage.setMinWidth(WINDOW_WIDTH);
        primaryStage.setMinHeight(WINDOW_HEIGHT);
        primaryStage.setMaxWidth(WINDOW_WIDTH);
        primaryStage.setMaxHeight(WINDOW_HEIGHT);

        if (stateStore.getState().isLoggedIn()) {
            showHomeView();
        } else {
            showLoginView();
        }

        primaryStage.centerOnScreen();
        primaryStage.show();
    }

    @Override
    public void stop() {
        if (syncService != null) {
            syncService.shutdown();
        }
        ioExecutor.shutdownNow();
    }

    private void buildScene() {
        root = new BorderPane();
        root.getStyleClass().add("app-root");

        centerPane = new StackPane();
        centerPane.setPadding(new Insets(24, 22, 12, 22));
        root.setCenter(centerPane);

        toastLabel = new Label();
        toastLabel.getStyleClass().add("toast-label");
        toastLabel.setMinHeight(28);
        toastLabel.setWrapText(true);
        BorderPane.setMargin(toastLabel, new Insets(0, 22, 18, 22));
        root.setBottom(toastLabel);

        Scene scene = new Scene(root, WINDOW_WIDTH, WINDOW_HEIGHT);
        scene.getStylesheets().add(Objects.requireNonNull(getClass().getResource("/css/app.css")).toExternalForm());
        primaryStage.setScene(scene);
    }

    private void loadImages() {
        appIconImage = loadImage("/icons/icon.png");
        syncOnImage = loadImage("/images/sync_toggle_on.png");
        syncOffImage = loadImage("/images/sync_toggle_off.png");
    }

    private Image loadImage(String resourcePath) {
        try {
            return new Image(Objects.requireNonNull(getClass().getResourceAsStream(resourcePath)));
        } catch (Exception error) {
            return null;
        }
    }

    private void showLoginView() {
        if (loginView == null) {
            loginView = buildLoginView();
        }
        AppState state = stateStore.getState();
        // 极简版和正式版共用同一份本地状态，这里直接把已有服务地址回填到登录框里。
        loginBaseUrlField.setText(state.getBaseUrl());
        loginUsernameField.setText(state.getUsername());
        loginPasswordField.clear();
        if (loginButton != null) {
            loginButton.setDisable(false);
        }
        root.setTop(null);
        centerPane.getChildren().setAll(loginView);
    }

    private void showHomeView() {
        if (homeView == null) {
            homeView = buildHomeView();
        }
        if (homeTopBar == null) {
            homeTopBar = buildHomeTopBar();
        }
        // 首页只有一个页面，所以每次回到首页时都直接按当前本地状态刷新按钮和开关。
        root.setTop(homeTopBar);
        centerPane.getChildren().setAll(homeView);
        refreshHomeState();
    }

    private Node buildLoginView() {
        VBox shell = new VBox(18);
        shell.getStyleClass().add("login-shell");
        shell.setAlignment(Pos.CENTER);

        VBox card = new VBox(14);
        card.getStyleClass().add("login-card");
        card.setMaxWidth(330);
        card.setAlignment(Pos.CENTER_LEFT);

        if (appIconImage != null) {
            ImageView logo = new ImageView(appIconImage);
            logo.setFitWidth(54);
            logo.setFitHeight(54);
            logo.setPreserveRatio(true);
            shell.getChildren().add(logo);
        }

        Label titleLabel = new Label("ClipBridge Lite");
        titleLabel.getStyleClass().add("login-title");

        loginBaseUrlField = new TextField();
        loginBaseUrlField.setPromptText("服务地址");

        loginUsernameField = new TextField();
        loginUsernameField.setPromptText("用户名");

        loginPasswordField = new PasswordField();
        loginPasswordField.setPromptText("密码");
        loginPasswordField.setOnAction(event -> submitLogin());

        loginButton = new Button("登录");
        loginButton.getStyleClass().add("primary-button");
        loginButton.setMaxWidth(Double.MAX_VALUE);
        loginButton.setOnAction(event -> submitLogin());

        card.getChildren().addAll(
            titleLabel,
            createFieldGroup("服务地址", loginBaseUrlField),
            createFieldGroup("用户名", loginUsernameField),
            createFieldGroup("密码", loginPasswordField),
            loginButton
        );
        shell.getChildren().add(card);
        return shell;
    }

    private Node buildHomeTopBar() {
        HBox bar = new HBox(10);
        bar.getStyleClass().add("top-bar");
        bar.setAlignment(Pos.CENTER);
        bar.setPadding(new Insets(18, 18, 0, 18));

        logoutButton = createIconButton("M10 4V8H4V20H10V24H0V4H10ZM14.4 8.6L17.8 12H8V16H17.8L14.4 19.4L17.2 22.2L25.4 14L17.2 5.8L14.4 8.6Z", "退出登录");
        logoutButton.setOnAction(event -> logout());

        Region spacerLeft = new Region();
        HBox.setHgrow(spacerLeft, Priority.ALWAYS);

        Label titleLabel = new Label("Lite");
        titleLabel.getStyleClass().add("top-title");

        Region spacerRight = new Region();
        HBox.setHgrow(spacerRight, Priority.ALWAYS);

        openWebButton = createIconButton("M9 7H21V11H17V13H21V21H9V9H13V7H9C7.9 7 7 7.9 7 9V21C7 22.1 7.9 23 9 23H21C22.1 23 23 22.1 23 21V13C23 11.9 22.1 11 21 11H19V9H21C22.1 9 23 8.1 23 7V3C23 1.9 22.1 1 21 1H17C15.9 1 15 1.9 15 3V5H17V3H21V7H17V9H15V7H9V7ZM10 10H20V20H10V10Z", "打开网页端");
        openWebButton.setOnAction(event -> openWebHistory());

        bar.getChildren().addAll(logoutButton, spacerLeft, titleLabel, spacerRight, openWebButton);
        return bar;
    }

    private Node buildHomeView() {
        VBox shell = new VBox(18);
        shell.getStyleClass().add("home-shell");
        shell.setAlignment(Pos.TOP_CENTER);

        VBox syncCard = new VBox(14);
        syncCard.getStyleClass().add("sync-card");
        syncCard.setAlignment(Pos.CENTER);

        syncImageView = new ImageView();
        syncImageView.setFitWidth(170);
        syncImageView.setFitHeight(170);
        syncImageView.setPreserveRatio(true);

        syncButton = new Button();
        syncButton.getStyleClass().add("sync-button");
        syncButton.setGraphic(syncImageView);
        syncButton.setOnAction(event -> toggleSync());

        syncStateLabel = new Label();
        syncStateLabel.getStyleClass().add("sync-state-label");

        syncCard.getChildren().addAll(syncButton, syncStateLabel);

        VBox settingsCard = new VBox(10);
        settingsCard.getStyleClass().add("settings-card");

        Label settingsTitle = new Label("同步文件");
        settingsTitle.getStyleClass().add("settings-title");

        syncFilesCheck = new CheckBox("开启");
        syncFilesCheck.getStyleClass().add("file-check");
        syncFilesCheck.setOnAction(event -> updateSyncFilesSetting(syncFilesCheck.isSelected()));

        settingsCard.getChildren().addAll(settingsTitle, syncFilesCheck);

        shell.getChildren().addAll(syncCard, settingsCard);
        return shell;
    }

    private VBox createFieldGroup(String title, TextField field) {
        VBox box = new VBox(6);
        Label label = new Label(title);
        label.getStyleClass().add("field-label");
        box.getChildren().addAll(label, field);
        return box;
    }

    private Button createIconButton(String svgContent, String tooltipText) {
        SVGPath icon = new SVGPath();
        icon.setContent(svgContent);
        icon.getStyleClass().add("toolbar-icon");

        StackPane graphic = new StackPane(icon);
        graphic.setPrefSize(18, 18);

        Button button = new Button();
        button.getStyleClass().add("icon-button");
        button.setGraphic(graphic);
        button.setTooltip(new Tooltip(tooltipText));
        return button;
    }

    private void submitLogin() {
        String baseUrl = ServiceAddressFormatter.safeTrim(loginBaseUrlField.getText());
        String username = ServiceAddressFormatter.safeTrim(loginUsernameField.getText());
        String password = loginPasswordField.getText() == null ? "" : loginPasswordField.getText();
        if (baseUrl.isBlank() || username.isBlank() || password.isBlank()) {
            showToast("请先填写完整登录信息");
            return;
        }

        loginButton.setDisable(true);
        runTask(
            // 设备名不再让用户输入，极简版统一走默认设备名，减少登录页复杂度。
            () -> apiClient.login(baseUrl, username, password, AppState.buildDefaultDeviceName()),
            ignored -> {
                loginButton.setDisable(false);
                showHomeView();
                showToast("登录成功");
            },
            error -> {
                loginButton.setDisable(false);
                showToast(nonBlank(error.getMessage(), "登录失败"));
            }
        );
    }

    private void toggleSync() {
        boolean enabled = !stateStore.getState().isSyncEnabled();
        // 中文注释：同步开关需要立刻落盘，并马上通知同步服务，确保重启后状态保持一致。
        stateStore.update(state -> state.setSyncEnabled(enabled));
        syncService.notifySettingsChanged();
        refreshHomeState();
        showToast(enabled ? "同步已开启" : "同步已关闭");
    }

    private void updateSyncFilesSetting(boolean enabled) {
        // 这里控制的是“复制文件时是否自动上传”，只保留一个最小设置项。
        stateStore.update(state -> state.setAutoUploadClipboardFiles(enabled));
        syncService.notifySettingsChanged();
        refreshHomeState();
        showToast(enabled ? "文件同步已开启" : "文件同步已关闭");
    }

    private void refreshHomeState() {
        if (syncButton == null || syncFilesCheck == null) {
            return;
        }
        AppState state = stateStore.getState();
        boolean enabled = state.isSyncEnabled();
        syncStateLabel.setText(enabled ? "同步中" : "未同步");
        syncImageView.setImage(enabled ? syncOnImage : syncOffImage);
        syncButton.getStyleClass().removeAll("sync-button-on", "sync-button-off");
        syncButton.getStyleClass().add(enabled ? "sync-button-on" : "sync-button-off");
        syncFilesCheck.setSelected(state.isAutoUploadClipboardFiles());
    }

    private void openWebHistory() {
        try {
            String baseUrl = ServiceAddressFormatter.normalize(stateStore.getState().getBaseUrl());
            if (baseUrl.isBlank()) {
                showToast("缺少服务地址");
                return;
            }
            if (!Desktop.isDesktopSupported()) {
                showToast("当前系统无法打开浏览器");
                return;
            }
            // 复杂操作统一交给网页端，所以这里固定跳到网页端历史页。
            Desktop.getDesktop().browse(URI.create(baseUrl + "/#/history"));
            showToast("已打开网页端");
        } catch (Exception error) {
            showToast("打开网页端失败");
        }
    }

    private void logout() {
        if (logoutButton != null) {
            logoutButton.setDisable(true);
        }
        runTask(
            // 退出登录沿用现有 best-effort 逻辑，优先保证本地状态被清掉并回到登录页。
            () -> {
                apiClient.logout();
                return null;
            },
            ignored -> {
                if (logoutButton != null) {
                    logoutButton.setDisable(false);
                }
                showLoginView();
                showToast("已退出登录");
            },
            error -> {
                stateStore.update(AppState::clearSession);
                if (logoutButton != null) {
                    logoutButton.setDisable(false);
                }
                showLoginView();
                showToast("已退出登录");
            }
        );
    }

    private void handleSessionExpired(String message) {
        showLoginView();
        showToast(nonBlank(message, "登录已失效"));
    }

    private void handleSyncStatus(String message) {
        String normalized = switch (nonBlank(message, "")) {
            case "实时同步已连接" -> "同步在线";
            case "实时连接不可用，继续使用补拉同步", "实时连接心跳超时，已切回补拉" -> "同步已开启";
            default -> message;
        };
        if (!nonBlank(normalized, "").isBlank()) {
            showToast(normalized);
        }
    }

    private void showToast(String message) {
        String normalized = nonBlank(message, "");
        toastLabel.setText(normalized);
        toastClearTimer.stop();
        if (normalized.isBlank()) {
            return;
        }
        toastClearTimer.setOnFinished(event -> {
            if (normalized.equals(toastLabel.getText())) {
                toastLabel.setText("");
            }
        });
        toastClearTimer.playFromStart();
    }

    private String nonBlank(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private <T> void runTask(CheckedSupplier<T> action, Consumer<T> onSuccess, Consumer<Exception> onError) {
        ioExecutor.submit(() -> {
            try {
                T result = action.get();
                Platform.runLater(() -> onSuccess.accept(result));
            } catch (Exception error) {
                Platform.runLater(() -> onError.accept(error));
            }
        });
    }

    @FunctionalInterface
    private interface CheckedSupplier<T> {
        T get() throws Exception;
    }

    private static final class IoThreadFactory implements ThreadFactory {
        private int nextIndex = 1;

        @Override
        public Thread newThread(Runnable task) {
            Thread thread = new Thread(task, "clipbridge-lite-io-" + nextIndex++);
            thread.setDaemon(true);
            return thread;
        }
    }
}
