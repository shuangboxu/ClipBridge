import { AUTH_ROUTE, DEFAULT_ROUTE, PROTECTED_ROUTES } from "./config/app.js";
import { renderApp } from "./render/layout.js";
import { ensureValidAccessToken, request, requestRaw } from "./services/api.js";
import {
    clearPending,
    closeFilePanel,
    closeSharePanel,
    clearSettingsPasswordForm,
    clearSession,
    closeSettingsModal,
    closeClipboardPanel,
    closeDevicePanel,
    isPending,
    openClipboardPanel,
    openFilePanel,
    openSharePanel,
    openSettingsModal,
    openDevicePanel,
    saveSidebarCollapsed,
    selectShareStrategy,
    selectSettingsCategory,
    setAdminPanelOpen,
    setPending,
    setSession,
    state,
    toggleShareRulePanel,
    updateSessionUser,
    updateShareRules,
    updateSettingsPasswordForm,
    updateSessionDevice
} from "./state/store.js";
import {
    decryptFileWithPassword,
    decryptTextWithPassword,
    encryptFileWithPassword,
    encryptTextWithPassword
} from "./utils/crypto.js";
import {
    buildWebSocketURL,
    createDefaultDeviceName,
    isMobileViewport,
    readTextFromClipboard,
    writeTextToClipboard
} from "./utils/browser.js";
import { toUserMessage } from "./utils/format.js";

const appRoot = document.getElementById("app");
let toastTimerID = 0;
let preservePageErrorOnNextRouteChange = false;
let realtimeSocket = null;
let realtimeReconnectTimerID = 0;
let realtimePingTimerID = 0;
let clipboardAutoAckTimerID = 0;
let clipboardAutoAckInFlight = false;
let clipboardAutoPullPromise = null;
let publicShareCountdownTimerID = 0;

export function bootstrap() {
    registerEventListeners();

    startApplication().catch((error) => {
        console.error("start application failed", error);
        state.isBootstrapping = false;
        state.pageError = "页面初始化失败，请刷新后重试。";
        render();
    });
}

export async function startApplication() {
    applyParsedRoute(parseRoute(window.location.hash));
    render();
    await handleRouteChange();
}

function registerEventListeners() {
    window.addEventListener("hashchange", () => {
        void handleRouteChange();
    });

    window.addEventListener("resize", () => {
        if (!isMobileViewport() && state.mobileSidebarOpen) {
            state.mobileSidebarOpen = false;
            render();
        }
    });

    document.addEventListener("submit", (event) => {
        const form = event.target;
        if (!(form instanceof HTMLFormElement)) {
            return;
        }

        if (form.id === "quota-request-form") {
            event.preventDefault();
            void handleQuotaRequestSubmit(form);
            return;
        }
        if (form.id === "bandwidth-request-form") {
            event.preventDefault();
            void handleBandwidthRequestSubmit(form);
            return;
        }
        if (form.id === "admin-request-form") {
            event.preventDefault();
            void handleAdminRequestSubmit(form);
            return;
        }
        if (form.id === "admin-settings-form") {
            event.preventDefault();
            void handleAdminSettingsSubmit(form);
            return;
        }
        if (form.classList.contains("admin-user-form")) {
            event.preventDefault();
            void handleAdminUserSubmit(form);
            return;
        }
        if (form.dataset.adminReviewType === "quota") {
            event.preventDefault();
            void handleAdminQuotaApprove(form);
            return;
        }
        if (form.dataset.adminReviewType === "bandwidth") {
            event.preventDefault();
            void handleAdminBandwidthApprove(form);
            return;
        }
        if (form.dataset.adminReviewType === "admin") {
            event.preventDefault();
            void handleAdminPrivilegeApprove(form);
            return;
        }

        switch (form.id) {
            case "auth-form":
                event.preventDefault();
                void handleAuthSubmit(form);
                return;
            case "device-edit-form":
                event.preventDefault();
                void handleDeviceEditSubmit(form);
                return;
            case "clipboard-upload-form":
                event.preventDefault();
                void handleClipboardUpload(form);
                return;
            case "file-upload-form":
                event.preventDefault();
                void handleFileUpload(form);
                return;
            case "file-rename-form":
                event.preventDefault();
                void handleFileRenameSubmit(form);
                return;
            case "password-change-form":
                event.preventDefault();
                void handlePasswordChangeSubmit(form);
                return;
            case "history-settings-form":
                event.preventDefault();
                void handleHistorySettingsSubmit(form);
                return;
            case "share-compose-form":
                event.preventDefault();
                void handleShareComposeSubmit(form);
                return;
            default:
                return;
        }
    });

    document.addEventListener("click", (event) => {
        const target = event.target instanceof Element ? event.target.closest("[data-action]") : null;
        if (!target) {
            return;
        }

        const action = target.getAttribute("data-action");
        if (!action) {
            return;
        }

        switch (action) {
            case "navigate":
                if (isMobileViewport()) {
                    state.mobileSidebarOpen = false;
                }
                navigate(target.getAttribute("data-route") || AUTH_ROUTE);
                break;
            case "toggle-sidebar":
                if (isMobileViewport()) {
                    state.mobileSidebarOpen = !state.mobileSidebarOpen;
                } else {
                    state.sidebarCollapsed = !state.sidebarCollapsed;
                    saveSidebarCollapsed(state.sidebarCollapsed);
                }
                render();
                break;
            case "close-sidebar":
                state.mobileSidebarOpen = false;
                render();
                break;
            case "open-settings":
                if (isMobileViewport()) {
                    state.mobileSidebarOpen = false;
                }
                state.pageError = null;
                openSettingsModal(target.getAttribute("data-category") || "general");
                render();
                if (state.settingsModal.activeCategory === "history") {
                    void loadClipboardHistorySettings({ silent: true });
                }
                break;
            case "close-settings":
                state.pageError = null;
                closeSettingsModal();
                render();
                break;
            case "select-settings-category":
                state.pageError = null;
                selectSettingsCategory(target.getAttribute("data-category") || "general");
                render();
                if (state.settingsModal.activeCategory === "history") {
                    void loadClipboardHistorySettings({ silent: true });
                }
                break;
            case "open-project-link":
                window.open("https://github.com/shuangboxu/ClipBridge", "_blank", "noopener");
                break;
            case "reload-devices":
                void loadDevices({ silent: false });
                break;
            case "open-device-details":
                handleOpenDevicePanel("details", target.getAttribute("data-device-id") || "");
                break;
            case "open-device-editor":
                handleOpenDevicePanel("edit", target.getAttribute("data-device-id") || "");
                break;
            case "close-device-panel":
                closeDevicePanel();
                render();
                break;
            case "open-clipboard-upload":
                openClipboardPanel("upload");
                render();
                break;
            case "open-clipboard-details":
                handleOpenClipboardPanel(target.getAttribute("data-item-id") || "");
                break;
            case "close-clipboard-panel":
                closeClipboardPanel();
                render();
                break;
            case "reload-files":
                void loadFiles({ silent: false });
                break;
            case "open-file-upload":
                openFilePanel("upload");
                render();
                break;
            case "open-file-details":
                handleOpenFilePanel("details", target.getAttribute("data-file-id") || "");
                break;
            case "open-file-rename":
                handleOpenFilePanel("rename", target.getAttribute("data-file-id") || "");
                break;
            case "close-file-panel":
                closeFilePanel();
                render();
                break;
            case "open-share-panel":
                openSharePanel();
                render();
                break;
            case "close-share-panel":
                closeSharePanel();
                render();
                break;
            case "select-share-strategy":
                selectShareStrategy(target.getAttribute("data-strategy") || "expire");
                render();
                break;
            case "toggle-share-rule-panel":
                toggleShareRulePanel(target.getAttribute("data-rule-key") || "");
                render();
                break;
            case "clear-share-file":
                setSelectedShareFiles([]);
                render();
                break;
            case "download-file":
                void handleFileDownload(target.getAttribute("data-file-id") || "");
                break;
            case "delete-file":
                void handleFileDelete(target.getAttribute("data-file-id") || "");
                break;
            case "files-prev":
                void handleFilesPrev();
                break;
            case "files-next":
                void handleFilesNext();
                break;
            case "shares-prev":
                void handleSharesPrev();
                break;
            case "shares-next":
                void handleSharesNext();
                break;
            case "force-device-offline":
                void handleForceDeviceOffline(target.getAttribute("data-device-id") || "");
                break;
            case "reload-history":
                void loadClipboardHistory({ silent: false });
                break;
            case "reload-shares":
                void loadShares({ silent: false });
                break;
            case "reload-requests":
                void loadRequests({ silent: false });
                break;
            case "reload-admin":
                void loadAdminData({ silent: false });
                break;
            case "history-prev":
                void handleHistoryPrev();
                break;
            case "history-next":
                void handleHistoryNext();
                break;
            case "read-system-clipboard":
                void handleReadSystemClipboard();
                break;
            case "copy-clipboard-item":
                void handleCopyClipboardItem(target.getAttribute("data-item-id") || "");
                break;
            case "delete-clipboard-item":
                void handleClipboardItemDelete(target.getAttribute("data-item-id") || "");
                break;
            case "clear-clipboard-history":
                void handleClipboardHistoryClear();
                break;
            case "cleanup-clipboard-history":
                void handleClipboardHistoryCleanup();
                break;
            case "apply-share-filter":
                void handleApplyShareFilter();
                break;
            case "copy-share-link":
                void handleCopyShareLink(target.getAttribute("data-share-token") || "");
                break;
            case "open-share-link":
                handleOpenShareLink(target.getAttribute("data-share-token") || "");
                break;
            case "revoke-share":
                void handleRevokeShare(target.getAttribute("data-share-id") || "");
                break;
            case "open-public-share":
                void handleOpenPublicShare();
                break;
            case "download-public-share-file":
                void handleDownloadPublicShareFile(target.getAttribute("data-file-id") || "");
                break;
            case "copy-public-share-text":
                void handleCopyPublicShareText();
                break;
            case "delete-admin-user":
                void handleAdminUserDelete(
                    target.getAttribute("data-user-id") || "",
                    target.getAttribute("data-username") || ""
                );
                break;
            case "reject-quota-request":
                void handleAdminReviewReject("quota", target.getAttribute("data-request-id") || "", target.closest("form"));
                break;
            case "reject-bandwidth-request":
                void handleAdminReviewReject("bandwidth", target.getAttribute("data-request-id") || "", target.closest("form"));
                break;
            case "reject-admin-request":
                void handleAdminReviewReject("admin", target.getAttribute("data-request-id") || "", target.closest("form"));
                break;
            case "logout":
                void handleLogout();
                break;
            default:
                break;
        }
    });

    document.addEventListener("keydown", (event) => {
        if (event.key === "Escape" && state.settingsModal.isOpen) {
            state.pageError = null;
            closeSettingsModal();
            render();
            return;
        }

        if (event.key === "Escape" && state.shares.panelOpen) {
            closeSharePanel();
            render();
        }
    });

    document.addEventListener("input", (event) => {
        const target = event.target;
        if (!(target instanceof HTMLInputElement) && !(target instanceof HTMLTextAreaElement) && !(target instanceof HTMLSelectElement)) {
            return;
        }

        if (syncRequestsFormDraft(target) || syncAdminSettingsDraft(target) || syncAdminUserDraft(target)) {
            return;
        }

        switch (target.id) {
            case "share-text-content":
                state.shares.textDraft = target.value;
                break;
            case "share-password":
                state.shares.password = target.value;
                break;
            case "share-status-filter":
                state.shares.status = target.value || "all";
                break;
            case "public-share-password":
                state.publicShare.password = target.value;
                break;
            case "history-retention-days":
                state.clipboard.retentionDays = Number(target.value || 0);
                break;
            case "history-limit":
                state.clipboard.maxStoredItems = Number(target.value || 1000);
                break;
            case "history-cleanup-days":
                state.clipboard.cleanupDaysDraft = target.value;
                break;
            default:
                break;
        }
    });

    document.addEventListener("change", (event) => {
        const target = event.target;
        if (!(target instanceof HTMLInputElement) && !(target instanceof HTMLSelectElement)) {
            return;
        }

        if (syncRequestsFormDraft(target) || syncAdminSettingsDraft(target) || syncAdminUserDraft(target)) {
            render();
            return;
        }

        switch (target.id) {
            case "share-rule-never-allow-copy":
                updateShareRules("never", {
                    allowCopyText: target.checked
                });
                render();
                break;
            case "share-rule-expire-allow-copy":
                updateShareRules("expire", {
                    allowCopyText: target.checked
                });
                render();
                break;
            case "share-rule-expire-hours":
                updateShareRules("expire", {
                    expireHours: Number(target.value || 24)
                });
                render();
                break;
            case "share-rule-once-show-countdown":
                updateShareRules("once", {
                    showCountdown: target.checked
                });
                render();
                break;
            case "share-rule-once-countdown-seconds":
                updateShareRules("once", {
                    countdownSeconds: Number(target.value || 10)
                });
                render();
                break;
            case "share-rule-once-allow-copy":
                updateShareRules("once", {
                    allowCopyText: target.checked
                });
                render();
                break;
            case "share-file-input": {
                setSelectedShareFiles(Array.from(target.files || []));
                render();
                break;
            }
            case "share-status-filter":
                state.shares.status = target.value || "all";
                break;
            default:
                break;
        }
    });

    document.addEventListener("toggle", (event) => {
        const target = event.target;
        if (!(target instanceof HTMLDetailsElement)) {
            return;
        }

        const panelKey = target.dataset.adminPanel;
        if (!panelKey) {
            return;
        }

        setAdminPanelOpen(panelKey, target.open);
    });

    document.addEventListener("dragenter", (event) => {
        const target = event.target instanceof Element ? event.target.closest("[data-share-dropzone]") : null;
        if (!target || !state.shares.panelOpen) {
            return;
        }
        event.preventDefault();
        state.shares.dragActive = true;
        render();
    });

    document.addEventListener("dragover", (event) => {
        const target = event.target instanceof Element ? event.target.closest("[data-share-dropzone]") : null;
        if (!target || !state.shares.panelOpen) {
            return;
        }
        event.preventDefault();
        if (!state.shares.dragActive) {
            state.shares.dragActive = true;
            render();
        }
    });

    document.addEventListener("dragleave", (event) => {
        const target = event.target instanceof Element ? event.target.closest("[data-share-dropzone]") : null;
        if (!target || !state.shares.panelOpen) {
            return;
        }
        const nextTarget = event.relatedTarget instanceof Element ? event.relatedTarget.closest("[data-share-dropzone]") : null;
        if (nextTarget === target) {
            return;
        }
        state.shares.dragActive = false;
        render();
    });

    document.addEventListener("drop", (event) => {
        const target = event.target instanceof Element ? event.target.closest("[data-share-dropzone]") : null;
        if (!target || !state.shares.panelOpen) {
            return;
        }
        event.preventDefault();
        state.shares.dragActive = false;
        setSelectedShareFiles(Array.from(event.dataTransfer?.files || []));
        render();
    });
}

async function handleRouteChange() {
    applyParsedRoute(parseRoute(window.location.hash));
    if (preservePageErrorOnNextRouteChange) {
        preservePageErrorOnNextRouteChange = false;
    } else {
        state.pageError = null;
    }
    if (state.route !== "devices") {
        closeDevicePanel();
    }
    if (state.route !== "history") {
        closeClipboardPanel();
    }
    if (state.route !== "files") {
        closeFilePanel();
    }
    if (state.route !== "shares") {
        closeSharePanel();
    }
    if (state.route !== "public-share") {
        clearPublicShareCountdown();
        resetPublicShareOpenedContent();
    }

    if (state.route === "public-share") {
        disconnectRealtime();
        state.isBootstrapping = true;
        render();
        await loadPublicShareMeta({ silent: true });
        state.isBootstrapping = false;
        render();
        return;
    }

    if (PROTECTED_ROUTES.has(state.route) && !state.session) {
        disconnectRealtime();
        state.isBootstrapping = false;
        render();
        navigate(AUTH_ROUTE);
        return;
    }

    if (state.session) {
        if (
            state.route === "devices" ||
            state.route === "history" ||
            state.route === "files" ||
            state.route === "shares" ||
            state.route === "requests" ||
            state.route === "admin"
        ) {
            state.isBootstrapping = true;
            render();
        }

        const authenticated = await ensureAuthenticated({ silent: state.route === AUTH_ROUTE });
        if (!authenticated) {
            return;
        }

        await ensureRealtimeConnection();

        if (state.route === AUTH_ROUTE) {
            navigate(DEFAULT_ROUTE);
            return;
        }

        if (state.route === "admin" && !isCurrentUserAdmin()) {
            state.isBootstrapping = false;
            state.pageError = "当前账号没有管理员权限。";
            render();
            navigate(DEFAULT_ROUTE, { preserveError: true });
            return;
        }

        if (state.route === "devices") {
            await loadDevices({ silent: true });
        }
        if (state.route === "history") {
            await Promise.all([
                loadClipboardHistory({ silent: true }),
                loadClipboardHistorySettings({ silent: true })
            ]);
        }
        if (state.route === "files") {
            await loadFiles({ silent: true });
        }
        if (state.route === "shares") {
            await loadShares({ silent: true });
        }
        if (state.route === "requests") {
            await loadRequests({ silent: true });
        }
        if (state.route === "admin") {
            await loadAdminData({ silent: true });
        }
    } else if (state.route !== AUTH_ROUTE) {
        navigate(AUTH_ROUTE);
        return;
    }

    state.isBootstrapping = false;
    render();
}

async function handleAuthSubmit(form) {
    const formData = new FormData(form);
    const username = String(formData.get("username") || "").trim();
    const password = String(formData.get("password") || "");

    state.authForm = {
        username,
        password
    };

    setPending("auth");
    state.pageError = null;
    clearToast();
    render();

    try {
        const data = await request("/v1/auth/login", {
            method: "POST",
            body: {
                username,
                password,
                platform: "web",
                device_name: createDefaultDeviceName()
            },
            withAuth: false
        });

        setSession({
            user: data.user,
            device: data.device,
            tokens: data.tokens
        });
        state.authForm.password = "";
        state.profile = {
            user: data.user,
            current_device_id: data.device?.id || ""
        };
        navigate(DEFAULT_ROUTE);
    } catch (error) {
        state.pageError = toUserMessage(error);
        render();
    } finally {
        clearPending();
    }
}

async function handleLogout() {
    if (!window.confirm("确认退出当前账号吗？")) {
        return;
    }

    setPending("logout");
    state.pageError = null;
    clearToast();
    render();

    try {
        if (state.session?.tokens?.refresh_token) {
            await request("/v1/auth/logout", {
                method: "POST",
                body: {
                    refresh_token: state.session.tokens.refresh_token
                }
            });
        }
    } catch (error) {
        console.warn("logout request failed", error);
    } finally {
        disconnectRealtime();
        clearSession();
        state.profile = null;
        state.devices = [];
        clearPending();
        showToast("已退出登录");
        navigate(AUTH_ROUTE);
    }
}

async function handlePasswordChangeSubmit(form) {
    const formData = new FormData(form);
    const currentPassword = String(formData.get("current_password") || "");
    const newPassword = String(formData.get("new_password") || "");
    const confirmPassword = String(formData.get("confirm_password") || "");

    updateSettingsPasswordForm({
        currentPassword,
        newPassword,
        confirmPassword
    });

    if (currentPassword.trim() === "") {
        state.pageError = "当前密码不能为空。";
        render();
        return;
    }
    if (newPassword.length < 8) {
        state.pageError = "新密码至少需要 8 位。";
        render();
        return;
    }
    if (newPassword.length > 128) {
        state.pageError = "新密码不能超过 128 位。";
        render();
        return;
    }
    if (newPassword !== confirmPassword) {
        state.pageError = "两次输入的新密码不一致。";
        render();
        return;
    }
    if (currentPassword === newPassword) {
        state.pageError = "新密码不能与当前密码相同。";
        render();
        return;
    }

    setPending("change-password");
    state.pageError = null;
    clearToast();
    render();

    try {
        const data = await request("/v1/account/password", {
            method: "POST",
            body: {
                current_password: currentPassword,
                new_password: newPassword
            }
        });

        if (data.user) {
            updateSessionUser(data.user);
            if (state.profile?.user) {
                state.profile = {
                    ...state.profile,
                    user: {
                        ...state.profile.user,
                        ...data.user
                    }
                };
            }
        }

        clearSettingsPasswordForm();
        showToast("密码已更新，其他设备需要重新登录。");
    } catch (error) {
        state.pageError = toUserMessage(error);
    } finally {
        clearPending();
        render();
    }
}

async function ensureAuthenticated(options = {}) {
    if (!state.session) {
        state.isBootstrapping = false;
        render();
        if (PROTECTED_ROUTES.has(state.route)) {
            navigate(AUTH_ROUTE);
        }
        return false;
    }

    if (state.profile) {
        return true;
    }

    if (!options.silent) {
        state.isBootstrapping = true;
        render();
    }

    return refreshCurrentProfile({ handleAuthFailure: true });
}

async function refreshCurrentProfile(options = {}) {
    try {
        const data = await request("/v1/account/me");
        state.profile = data;
        if (data.user) {
            updateSessionUser(data.user);
        }
        return true;
    } catch (error) {
        if (options.handleAuthFailure) {
            console.warn("load current account failed", error);
            handleAuthExpired("登录已失效，请重新登录。");
            return false;
        }
        throw error;
    }
}

async function loadDevices(options = {}) {
    if (!state.session) {
        return;
    }

    if (!options.silent) {
        setPending("devices");
        state.pageError = null;
        render();
    }

    try {
        const data = await request("/v1/devices");
        state.devices = Array.isArray(data.devices) ? data.devices : [];
    } catch (error) {
        state.pageError = toUserMessage(error);
    } finally {
        if (!options.silent && isPending("devices")) {
            clearPending();
        }
        render();
    }
}

async function loadFiles(options = {}) {
    if (!state.session) {
        return;
    }

    if (!options.silent) {
        setPending("files");
        state.pageError = null;
        clearToast();
        render();
    }

    try {
        const query = new URLSearchParams({
            page: String(state.files.page),
            page_size: String(state.files.pageSize)
        });
        const data = await request(`/v1/files?${query.toString()}`);
        const pagination = data.pagination || {};
        const summary = data.summary || {};
        const items = Array.isArray(data.files) ? data.files : [];

        state.files.items = items;
        state.files.page = Number(pagination.page || state.files.page || 1);
        state.files.pageSize = Number(pagination.page_size || state.files.pageSize || 20);
        state.files.total = Number(pagination.total || 0);
        state.files.totalPages = Number(pagination.total_pages || 0);
        state.files.totalBytes = Number(summary.total_bytes || 0);
        state.files.maxUploadBytes = Number(summary.max_upload_bytes || 0);

        if (state.files.page > 1 && state.files.totalPages > 0 && state.files.page > state.files.totalPages) {
            state.files.page = state.files.totalPages;
            await loadFiles({ silent: true });
            return;
        }

        if (state.filePanel.mode && !findFileItem(state.filePanel.fileId)) {
            closeFilePanel();
        }
    } catch (error) {
        state.pageError = toUserMessage(error);
    } finally {
        if (!options.silent && isPending("files")) {
            clearPending();
        }
        render();
    }
}

async function loadShares(options = {}) {
    if (!state.session) {
        return;
    }

    if (!options.silent) {
        setPending("shares");
        state.pageError = null;
        clearToast();
        render();
    }

    try {
        const query = new URLSearchParams({
            page: String(state.shares.page),
            page_size: String(state.shares.pageSize),
            status: state.shares.status || "all"
        });
        const data = await request(`/v1/shares?${query.toString()}`);
        const pagination = data.pagination || {};
        const summary = data.summary || {};
        const items = Array.isArray(data.shares) ? data.shares : [];

        state.shares.items = items;
        state.shares.page = Number(pagination.page || state.shares.page || 1);
        state.shares.pageSize = Number(pagination.page_size || state.shares.pageSize || 20);
        state.shares.total = Number(pagination.total || 0);
        state.shares.totalPages = Number(pagination.total_pages || 0);
        state.shares.status = String(pagination.status || state.shares.status || "all");
        state.shares.maxUploadBytes = Number(summary.max_upload_bytes || 0);

        if (state.shares.page > 1 && state.shares.totalPages > 0 && state.shares.page > state.shares.totalPages) {
            state.shares.page = state.shares.totalPages;
            await loadShares({ silent: true });
            return;
        }
    } catch (error) {
        state.pageError = toUserMessage(error);
    } finally {
        if (!options.silent && isPending("shares")) {
            clearPending();
        }
        render();
    }
}

async function loadRequests(options = {}) {
    if (!state.session) {
        return;
    }

    if (!options.silent) {
        setPending("requests");
        state.pageError = null;
        clearToast();
        render();
    }

    try {
        const [quotaData, bandwidthData, adminData] = await Promise.all([
            request("/v1/account/quota-requests?status=all"),
            request("/v1/account/bandwidth-requests?status=all"),
            request("/v1/account/admin-requests?status=all")
        ]);

        state.requests.quotaRequests = Array.isArray(quotaData.requests) ? quotaData.requests : [];
        state.requests.bandwidthRequests = Array.isArray(bandwidthData.requests) ? bandwidthData.requests : [];
        state.requests.adminRequests = Array.isArray(adminData.requests) ? adminData.requests : [];
    } catch (error) {
        state.pageError = toUserMessage(error);
    } finally {
        if (!options.silent && isPending("requests")) {
            clearPending();
        }
        render();
    }
}

async function loadAdminData(options = {}) {
    if (!state.session) {
        return;
    }

    if (!options.silent) {
        setPending("admin");
        state.pageError = null;
        clearToast();
        render();
    }

    try {
        const [settingsData, usersData, quotaData, bandwidthData, adminData] = await Promise.all([
            request("/v1/admin/settings"),
            request("/v1/admin/users"),
            request("/v1/admin/quota-requests?status=pending"),
            request("/v1/admin/bandwidth-requests?status=pending"),
            request("/v1/admin/admin-requests?status=pending")
        ]);

        state.admin.settings = settingsData.settings || null;
        state.admin.currentUserCount = Number(settingsData.current_user_count || 0);
        if (state.admin.settings) {
            state.admin.settingsForm = createAdminSettingsDraft(state.admin.settings);
        }

        state.admin.users = Array.isArray(usersData.users) ? usersData.users : [];
        state.admin.userDrafts = createAdminUserDrafts(state.admin.users);
        state.admin.quotaRequests = Array.isArray(quotaData.requests) ? quotaData.requests : [];
        state.admin.bandwidthRequests = Array.isArray(bandwidthData.requests) ? bandwidthData.requests : [];
        state.admin.adminRequests = Array.isArray(adminData.requests) ? adminData.requests : [];
    } catch (error) {
        if (error?.status === 403) {
            state.pageError = "当前账号没有管理员权限。";
            render();
            navigate(DEFAULT_ROUTE, { preserveError: true });
            return;
        }
        state.pageError = toUserMessage(error);
    } finally {
        if (!options.silent && isPending("admin")) {
            clearPending();
        }
        render();
    }
}

async function loadPublicShareMeta(options = {}) {
    const token = String(state.publicShare.token || "").trim();
    if (!token) {
        state.pageError = "缺少分享 token，请检查链接是否完整。";
        state.publicShare.meta = null;
        resetPublicShareOpenedContent();
        clearPublicShareCountdown();
        return;
    }

    if (!options.silent) {
        setPending("public-share-meta");
        state.pageError = null;
        clearToast();
        render();
    }

    try {
        const data = await request(`/v1/public/shares/${encodeURIComponent(token)}/meta`, {
            withAuth: false
        });
        resetPublicShareOpenedContent();
        state.publicShare.meta = data.share || null;
        syncPublicShareCountdown(state.publicShare.meta);
        if (canAutoOpenPublicShare(state.publicShare.meta)) {
            await openPublicShareContent({ silent: true });
        }
    } catch (error) {
        clearPublicShareCountdown();
        state.publicShare.meta = null;
        resetPublicShareOpenedContent();
        state.pageError = toUserMessage(error);
    } finally {
        if (!options.silent && isPending("public-share-meta")) {
            clearPending();
        }
        render();
    }
}

async function loadClipboardHistory(options = {}) {
    if (!state.session) {
        return;
    }

    if (!options.silent) {
        setPending("clipboard-history");
        state.pageError = null;
        clearToast();
        render();
    }

    try {
        const beforeSeq = state.clipboard.historyCursors[state.clipboard.historyPageIndex] ?? null;
        const query = new URLSearchParams({
            limit: String(state.clipboard.historyLimit)
        });
        if (beforeSeq !== null) {
            query.set("before_seq", String(beforeSeq));
        }

        const data = await request(`/v1/clipboard/items?${query.toString()}`);
        applyClipboardServerState(data);

        const items = Array.isArray(data.items) ? data.items : [];
        state.clipboard.items = items;
        state.clipboard.historyHasMore = Boolean(data.has_more);
        if (state.clipboardPanel.mode === "details" && !items.some((item) => item.id === state.clipboardPanel.itemId)) {
            closeClipboardPanel();
        }

        if (state.clipboard.historyHasMore && items.length > 0) {
            state.clipboard.historyCursors[state.clipboard.historyPageIndex + 1] = items[items.length - 1].seq;
        } else {
            state.clipboard.historyCursors = state.clipboard.historyCursors.slice(0, state.clipboard.historyPageIndex + 1);
        }
    } catch (error) {
        state.pageError = toUserMessage(error);
    } finally {
        if (!options.silent && isPending("clipboard-history")) {
            clearPending();
        }
        render();
    }
}

async function loadClipboardHistorySettings(options = {}) {
    if (!state.session) {
        return;
    }

    if (!options.silent) {
        setPending("history-settings-load");
        state.pageError = null;
        clearToast();
        render();
    }

    try {
        const data = await request("/v1/clipboard/history/settings");
        applyClipboardHistorySettings(data.settings || {});
    } catch (error) {
        state.pageError = toUserMessage(error);
    } finally {
        if (!options.silent && isPending("history-settings-load")) {
            clearPending();
        }
        render();
    }
}

function handleOpenDevicePanel(mode, deviceID) {
    const device = state.devices.find((item) => item.id === deviceID);
    if (!device) {
        state.pageError = "设备不存在或已被移除。";
        render();
        return;
    }

    openDevicePanel(mode, device);
    render();
}

function handleOpenClipboardPanel(itemID) {
    const item = state.clipboard.items.find((nextItem) => nextItem.id === itemID);
    if (!item) {
        state.pageError = "历史记录不存在或已被移除。";
        render();
        return;
    }

    openClipboardPanel("details", item);
    render();
}

function handleOpenFilePanel(mode, fileID) {
    const file = findFileItem(fileID);
    if (!file) {
        state.pageError = "文件不存在或已被移除。";
        render();
        return;
    }

    openFilePanel(mode, file);
    render();
}

async function handleDeviceEditSubmit(form) {
    const formData = new FormData(form);
    const deviceName = String(formData.get("device_name") || "").trim();
    const deviceID = state.devicePanel.deviceId;
    if (!deviceID) {
        state.pageError = "设备不存在或已被移除。";
        render();
        return;
    }

    state.devicePanel.draftName = deviceName;
    setPending("device-rename");
    state.pageError = null;
    clearToast();
    render();

    try {
        const data = await request("/v1/devices", {
            method: "PATCH",
            body: {
                device_id: deviceID,
                device_name: deviceName
            }
        });

        const updatedDevice = data.device || null;
        if (updatedDevice && state.session?.device?.id === updatedDevice.id) {
            updateSessionDevice({
                device_name: updatedDevice.device_name
            });
        }

        await loadDevices({ silent: true });
        showToast("设备名称已更新");
    } catch (error) {
        state.pageError = toUserMessage(error);
    } finally {
        clearPending();
        render();
    }
}

async function handleForceDeviceOffline(deviceID) {
    const device = state.devices.find((item) => item.id === deviceID);
    if (!device) {
        state.pageError = "设备不存在或已被移除。";
        render();
        return;
    }

    if (!window.confirm(`确认强制下线设备“${device.device_name || "unnamed-device"}”吗？`)) {
        return;
    }

    setPending("device-offline");
    state.pageError = null;
    clearToast();
    render();

    try {
        const data = await request("/v1/devices/offline", {
            method: "POST",
            body: {
                device_id: deviceID
            }
        });

        const currentDeviceForcedOffline = Boolean(data.current_device_forced_offline);
        if (currentDeviceForcedOffline) {
            disconnectRealtime();
            clearSession();
            state.profile = null;
            state.devices = [];
            closeDevicePanel();
            clearPending();
            showToast("当前设备已被强制下线，请重新登录。");
            navigate(AUTH_ROUTE);
            return;
        }

        await loadDevices({ silent: true });
        closeDevicePanel();
        showToast("设备已强制下线");
    } catch (error) {
        state.pageError = toUserMessage(error);
    } finally {
        if (isPending("device-offline")) {
            clearPending();
        }
        render();
    }
}

async function handleQuotaRequestSubmit(form) {
    const formData = new FormData(form);
    const requestedQuotaMB = String(formData.get("requested_quota_mb") || "").trim();
    const reason = String(formData.get("reason") || "").trim();

    state.requests.quotaForm = {
        requestedQuotaMB,
        reason
    };

    setPending("quota-request-create");
    state.pageError = null;
    clearToast();
    render();

    try {
        await request("/v1/account/quota-requests", {
            method: "POST",
            body: {
                requested_quota_mb: Number(requestedQuotaMB || 0),
                reason
            }
        });

        state.requests.quotaForm = {
            requestedQuotaMB: "",
            reason: ""
        };
        await loadRequests({ silent: true });
        showToast("存储配额申请已提交。");
    } catch (error) {
        state.pageError = toUserMessage(error);
    } finally {
        if (isPending("quota-request-create")) {
            clearPending();
        }
        render();
    }
}

async function handleBandwidthRequestSubmit(form) {
    const formData = new FormData(form);
    const requestedUploadKbps = String(formData.get("requested_upload_kbps") || "").trim();
    const requestedDownloadKbps = String(formData.get("requested_download_kbps") || "").trim();
    const reason = String(formData.get("reason") || "").trim();

    state.requests.bandwidthForm = {
        requestedUploadKbps,
        requestedDownloadKbps,
        reason
    };

    setPending("bandwidth-request-create");
    state.pageError = null;
    clearToast();
    render();

    try {
        await request("/v1/account/bandwidth-requests", {
            method: "POST",
            body: {
                requested_upload_kbps: Number(requestedUploadKbps || 0),
                requested_download_kbps: Number(requestedDownloadKbps || 0),
                reason
            }
        });

        state.requests.bandwidthForm = {
            requestedUploadKbps: "",
            requestedDownloadKbps: "",
            reason: ""
        };
        await loadRequests({ silent: true });
        showToast("带宽申请已提交。");
    } catch (error) {
        state.pageError = toUserMessage(error);
    } finally {
        if (isPending("bandwidth-request-create")) {
            clearPending();
        }
        render();
    }
}

async function handleAdminRequestSubmit(form) {
    if (isCurrentUserAdmin()) {
        state.pageError = "当前账号已经是管理员，无需再次申请。";
        render();
        return;
    }

    const formData = new FormData(form);
    const reason = String(formData.get("reason") || "").trim();
    state.requests.adminForm = { reason };

    setPending("admin-request-create");
    state.pageError = null;
    clearToast();
    render();

    try {
        await request("/v1/account/admin-requests", {
            method: "POST",
            body: { reason }
        });

        state.requests.adminForm = { reason: "" };
        await loadRequests({ silent: true });
        showToast("管理员申请已提交。");
    } catch (error) {
        state.pageError = toUserMessage(error);
    } finally {
        if (isPending("admin-request-create")) {
            clearPending();
        }
        render();
    }
}

async function handleAdminSettingsSubmit(form) {
    const draft = syncAdminSettingsDraftFromForm(form);

    setPending("admin-settings-save");
    state.pageError = null;
    clearToast();
    render();

    try {
        const data = await request("/v1/admin/settings", {
            method: "PUT",
            body: {
                max_user_count: Number(draft.maxUserCount || 0),
                default_storage_quota_mb: Number(draft.defaultStorageQuotaMB || 0),
                default_upload_bandwidth_kbps: Number(draft.defaultUploadBandwidthKbps || 0),
                default_download_bandwidth_kbps: Number(draft.defaultDownloadBandwidthKbps || 0),
                max_user_upload_bandwidth_kbps: Number(draft.maxUserUploadBandwidthKbps || 0),
                max_user_download_bandwidth_kbps: Number(draft.maxUserDownloadBandwidthKbps || 0),
                max_upload_file_mb: Number(draft.maxUploadFileMB || 0),
                allow_registration: Boolean(draft.allowRegistration)
            }
        });

        state.admin.settings = data.settings || null;
        state.admin.currentUserCount = Number(data.current_user_count || state.admin.currentUserCount || 0);
        if (state.admin.settings) {
            state.admin.settingsForm = createAdminSettingsDraft(state.admin.settings);
        }
        await refreshCurrentProfile({ handleAuthFailure: true });
        showToast("系统设置已保存。");
    } catch (error) {
        state.pageError = toUserMessage(error);
    } finally {
        if (isPending("admin-settings-save")) {
            clearPending();
        }
        render();
    }
}

async function handleAdminUserSubmit(form) {
    const userID = String(form.dataset.adminUserId || "").trim();
    if (!userID) {
        state.pageError = "用户不存在或已被移除。";
        render();
        return;
    }

    const draft = syncAdminUserDraftFromForm(form);
    const pendingKey = `admin-user-save:${userID}`;

    setPending(pendingKey);
    state.pageError = null;
    clearToast();
    render();

    try {
        const data = await request(`/v1/admin/users/${encodeURIComponent(userID)}`, {
            method: "PATCH",
            body: {
                storage_quota_mb: Number(draft.storageQuotaMB || 0),
                upload_bandwidth_kbps: Number(draft.uploadBandwidthKbps || 0),
                download_bandwidth_kbps: Number(draft.downloadBandwidthKbps || 0),
                is_admin: Boolean(draft.isAdmin)
            }
        });

        const updatedUser = data.user || null;
        const isCurrentUser = updatedUser?.id && updatedUser.id === state.session?.user?.id;
        if (isCurrentUser && !updatedUser.is_admin) {
            disconnectRealtime();
            clearSession();
            state.profile = null;
            if (isPending(pendingKey)) {
                clearPending();
            }
            showToast("当前账号的管理员权限已被取消，请重新登录。");
            navigate(AUTH_ROUTE);
            return;
        }

        await loadAdminData({ silent: true });
        if (isCurrentUser) {
            await refreshCurrentProfile({ handleAuthFailure: true });
        }
        showToast("用户信息已更新。");
    } catch (error) {
        state.pageError = toUserMessage(error);
    } finally {
        if (isPending(pendingKey)) {
            clearPending();
        }
        render();
    }
}

async function handleAdminUserDelete(userID, username) {
    if (!userID) {
        state.pageError = "用户不存在或已被移除。";
        render();
        return;
    }

    if (!window.confirm(`确认删除用户“${username || userID}”吗？该用户的文件、分享和申请记录会一并清理。`)) {
        return;
    }

    const pendingKey = `admin-user-delete:${userID}`;
    setPending(pendingKey);
    state.pageError = null;
    clearToast();
    render();

    try {
        await request(`/v1/admin/users/${encodeURIComponent(userID)}`, {
            method: "DELETE"
        });

        if (userID === state.session?.user?.id) {
            disconnectRealtime();
            clearSession();
            state.profile = null;
            if (isPending(pendingKey)) {
                clearPending();
            }
            showToast("当前账号已删除，请重新登录。");
            navigate(AUTH_ROUTE);
            return;
        }

        await loadAdminData({ silent: true });
        showToast("用户已删除。");
    } catch (error) {
        state.pageError = toUserMessage(error);
    } finally {
        if (isPending(pendingKey)) {
            clearPending();
        }
        render();
    }
}

async function handleAdminQuotaApprove(form) {
    const requestID = String(form.dataset.requestId || "").trim();
    if (!requestID) {
        state.pageError = "申请不存在或已被处理。";
        render();
        return;
    }

    const formData = new FormData(form);
    const approvedQuotaMB = String(formData.get("approved_quota_mb") || "").trim();
    const reviewNote = String(formData.get("review_note") || "").trim();
    const pendingKey = `admin-review:quota:${requestID}`;

    setPending(pendingKey);
    state.pageError = null;
    clearToast();
    render();

    try {
        const body = { review_note: reviewNote };
        if (approvedQuotaMB !== "") {
            body.approved_quota_mb = Number(approvedQuotaMB);
        }

        await request(`/v1/admin/quota-requests/${encodeURIComponent(requestID)}/approve`, {
            method: "POST",
            body
        });

        await Promise.all([
            loadAdminData({ silent: true }),
            refreshCurrentProfile({ handleAuthFailure: true })
        ]);
        showToast("配额申请已批准。");
    } catch (error) {
        state.pageError = toUserMessage(error);
    } finally {
        if (isPending(pendingKey)) {
            clearPending();
        }
        render();
    }
}

async function handleAdminBandwidthApprove(form) {
    const requestID = String(form.dataset.requestId || "").trim();
    if (!requestID) {
        state.pageError = "申请不存在或已被处理。";
        render();
        return;
    }

    const formData = new FormData(form);
    const approvedUploadKbps = String(formData.get("approved_upload_kbps") || "").trim();
    const approvedDownloadKbps = String(formData.get("approved_download_kbps") || "").trim();
    const reviewNote = String(formData.get("review_note") || "").trim();
    const pendingKey = `admin-review:bandwidth:${requestID}`;

    setPending(pendingKey);
    state.pageError = null;
    clearToast();
    render();

    try {
        const body = { review_note: reviewNote };
        if (approvedUploadKbps !== "") {
            body.approved_upload_kbps = Number(approvedUploadKbps);
        }
        if (approvedDownloadKbps !== "") {
            body.approved_download_kbps = Number(approvedDownloadKbps);
        }

        await request(`/v1/admin/bandwidth-requests/${encodeURIComponent(requestID)}/approve`, {
            method: "POST",
            body
        });

        await Promise.all([
            loadAdminData({ silent: true }),
            refreshCurrentProfile({ handleAuthFailure: true })
        ]);
        showToast("带宽申请已批准。");
    } catch (error) {
        state.pageError = toUserMessage(error);
    } finally {
        if (isPending(pendingKey)) {
            clearPending();
        }
        render();
    }
}

async function handleAdminPrivilegeApprove(form) {
    const requestID = String(form.dataset.requestId || "").trim();
    if (!requestID) {
        state.pageError = "申请不存在或已被处理。";
        render();
        return;
    }

    const formData = new FormData(form);
    const reviewNote = String(formData.get("review_note") || "").trim();
    const pendingKey = `admin-review:admin:${requestID}`;

    setPending(pendingKey);
    state.pageError = null;
    clearToast();
    render();

    try {
        await request(`/v1/admin/admin-requests/${encodeURIComponent(requestID)}/approve`, {
            method: "POST",
            body: {
                review_note: reviewNote
            }
        });

        await Promise.all([
            loadAdminData({ silent: true }),
            refreshCurrentProfile({ handleAuthFailure: true })
        ]);
        showToast("管理员申请已批准。");
    } catch (error) {
        state.pageError = toUserMessage(error);
    } finally {
        if (isPending(pendingKey)) {
            clearPending();
        }
        render();
    }
}

async function handleAdminReviewReject(type, requestID, form) {
    if (!requestID) {
        state.pageError = "申请不存在或已被处理。";
        render();
        return;
    }

    const pendingKey = `admin-review:${type}:${requestID}`;
    const reviewNote = form instanceof HTMLFormElement
        ? String(new FormData(form).get("review_note") || "").trim()
        : "";

    setPending(pendingKey);
    state.pageError = null;
    clearToast();
    render();

    try {
        await request(buildAdminReviewEndpoint(type, requestID, "reject"), {
            method: "POST",
            body: {
                review_note: reviewNote
            }
        });

        await loadAdminData({ silent: true });
        showToast("申请已拒绝。");
    } catch (error) {
        state.pageError = toUserMessage(error);
    } finally {
        if (isPending(pendingKey)) {
            clearPending();
        }
        render();
    }
}

async function handleClipboardUpload(form) {
    const formData = new FormData(form);
    const textContent = String(formData.get("text_content") || "");
    state.clipboard.draftText = textContent;

    setPending("clipboard-upload");
    state.pageError = null;
    clearToast();
    render();

    try {
        const data = await request("/v1/clipboard/items", {
            method: "POST",
            body: {
                content_type: "text",
                text_content: textContent
            }
        });

        const item = data.item || null;
        state.clipboard.draftText = "";
        if (item?.seq) {
            state.clipboard.latestSeq = Math.max(state.clipboard.latestSeq, Number(item.seq));
            tryAdvancePendingAck(Number(item.seq));
            scheduleClipboardAck();
        }

        resetClipboardPager();
        mergeClipboardItems(item ? [item] : []);
        closeClipboardPanel();
        showToast(data.deduplicated ? "检测到短时间重复文本，已复用最近一条记录" : "文本已上传");
    } catch (error) {
        state.pageError = toUserMessage(error);
    } finally {
        clearPending();
        render();
    }
}

async function handleFileUpload(form) {
    const fileInput = form.querySelector("input[name='file']");
    const selectedFile = fileInput?.files?.[0] || null;
    if (!selectedFile) {
        state.pageError = "请先选择要上传的文件。";
        render();
        return;
    }

    state.files.selectedUploadName = selectedFile.name;
    setPending("file-upload");
    state.pageError = null;
    clearToast();
    render();

    try {
        const formData = new FormData();
        formData.append("file", selectedFile);

        const data = await request("/v1/files", {
            method: "POST",
            body: formData
        });

        state.files.page = 1;
        state.files.selectedUploadName = "";
        if (fileInput) {
            fileInput.value = "";
        }

        const uploadedFileID = data.file?.id || "";
        await loadFiles({ silent: true });
        if (uploadedFileID) {
            const nextFile = findFileItem(uploadedFileID);
            if (nextFile) {
                openFilePanel("details", nextFile);
            }
        }
        showToast("文件已上传");
    } catch (error) {
        state.pageError = toUserMessage(error);
    } finally {
        clearPending();
        render();
    }
}

async function handleFileRenameSubmit(form) {
    const fileID = state.filePanel.fileId;
    if (!fileID) {
        state.pageError = "文件不存在或已被移除。";
        render();
        return;
    }

    const formData = new FormData(form);
    const originalName = String(formData.get("original_name") || "").trim();
    state.filePanel.renameDraftName = originalName;

    setPending("file-rename");
    state.pageError = null;
    clearToast();
    render();

    try {
        await request(`/v1/files/${encodeURIComponent(fileID)}`, {
            method: "PATCH",
            body: {
                original_name: originalName
            }
        });

        await loadFiles({ silent: true });
        const nextFile = findFileItem(fileID);
        if (nextFile) {
            openFilePanel("details", nextFile);
        } else {
            closeFilePanel();
        }
        showToast("文件名称已更新");
    } catch (error) {
        state.pageError = toUserMessage(error);
    } finally {
        clearPending();
        render();
    }
}

async function handleFileDownload(fileID) {
    const file = findFileItem(fileID);
    if (!file) {
        state.pageError = "文件不存在或已被移除。";
        render();
        return;
    }

    setPending("file-download");
    state.pageError = null;
    clearToast();
    render();

    try {
        const response = await requestRaw(`/v1/files/${encodeURIComponent(fileID)}/download`);
        const blob = await response.blob();
        const fileName = parseFilenameFromContentDisposition(response.headers.get("Content-Disposition")) || file.original_name || "download.bin";
        const objectURL = URL.createObjectURL(blob);
        const link = document.createElement("a");
        link.href = objectURL;
        link.download = fileName;
        document.body.appendChild(link);
        link.click();
        link.remove();
        URL.revokeObjectURL(objectURL);
        showToast(`开始下载：${fileName}`);
    } catch (error) {
        state.pageError = toUserMessage(error);
    } finally {
        clearPending();
        render();
    }
}

async function handleFileDelete(fileID) {
    const file = findFileItem(fileID);
    if (!file) {
        state.pageError = "文件不存在或已被移除。";
        render();
        return;
    }

    if (!window.confirm(`确认删除文件“${file.original_name || "未命名文件"}”吗？`)) {
        return;
    }

    setPending("file-delete");
    state.pageError = null;
    clearToast();
    render();

    try {
        const data = await request(`/v1/files/${encodeURIComponent(fileID)}`, {
            method: "DELETE"
        });
        await loadFiles({ silent: true });
        if (state.filePanel.fileId === fileID) {
            closeFilePanel();
        }
        showToast(data.disk_removed === false ? "文件记录已删除，但磁盘文件清理失败" : "文件已删除");
    } catch (error) {
        state.pageError = toUserMessage(error);
    } finally {
        clearPending();
        render();
    }
}

async function handleShareComposeSubmit(form) {
    const formData = new FormData(form);
    const textContent = String(formData.get("text_content") || "");
    const selectedFiles = Array.isArray(state.shares.selectedFiles) ? state.shares.selectedFiles : [];
    state.shares.textDraft = textContent;

    if (textContent === "" && selectedFiles.length === 0) {
        state.pageError = "请至少输入文字，或拖入一个文件。";
        render();
        return;
    }

    setPending("share-create");
    state.pageError = null;
    clearToast();
    render();

    try {
        const payload = await buildShareComposePayload(textContent, selectedFiles);
        const data = await request("/v1/shares", {
            method: "POST",
            body: payload
        });

        state.shares.textDraft = "";
        state.shares.password = "";
        state.shares.selectedFiles = [];
        state.shares.dragActive = false;
        state.shares.panelOpen = false;
        state.shares.page = 1;
        state.shares.latestShareToken = data.share?.token || "";

        const fileInput = form.querySelector("input[name='files']");
        if (fileInput instanceof HTMLInputElement) {
            fileInput.value = "";
        }

        await loadShares({ silent: true });
        showToast(resolveShareCreatedMessage(textContent, selectedFiles));
    } catch (error) {
        state.pageError = toUserMessage(error);
    } finally {
        clearPending();
        render();
    }
}

async function handleApplyShareFilter() {
    if (isPending("shares")) {
        return;
    }

    state.shares.page = 1;
    await loadShares({ silent: false });
}

async function handleSharesPrev() {
    if (state.shares.page <= 1 || isPending("shares")) {
        return;
    }

    state.shares.page -= 1;
    await loadShares({ silent: false });
}

async function handleSharesNext() {
    if ((state.shares.totalPages > 0 && state.shares.page >= state.shares.totalPages) || isPending("shares")) {
        return;
    }

    state.shares.page += 1;
    await loadShares({ silent: false });
}

async function handleCopyShareLink(token) {
    const publicLink = buildPublicShareLink(token);
    if (!publicLink) {
        state.pageError = "分享链接无效，无法复制。";
        render();
        return;
    }

    try {
        await writeTextToClipboard(publicLink);
        showToast("分享链接已复制");
    } catch (error) {
        state.pageError = toUserMessage(error);
        render();
    }
}

function handleOpenShareLink(token) {
    const publicLink = buildPublicShareLink(token);
    if (!publicLink) {
        state.pageError = "分享链接无效，无法打开。";
        render();
        return;
    }

    window.open(publicLink, "_blank", "noopener");
}

async function handleRevokeShare(shareID) {
    const share = state.shares.items.find((item) => item.id === shareID);
    if (!share) {
        state.pageError = "分享不存在或已被移除。";
        render();
        return;
    }

    const shareLabel = resolveShareListLabel(share);
    if (!window.confirm(`确认撤销分享“${shareLabel}”吗？`)) {
        return;
    }

    setPending("share-revoke");
    state.pageError = null;
    clearToast();
    render();

    try {
        await request(`/v1/shares/${encodeURIComponent(shareID)}/revoke`, {
            method: "POST"
        });
        await loadShares({ silent: true });
        showToast("分享已撤销");
    } catch (error) {
        state.pageError = toUserMessage(error);
    } finally {
        clearPending();
        render();
    }
}

async function handleOpenPublicShare() {
    await openPublicShareContent({ silent: false });
}

async function openPublicShareContent(options = {}) {
    const token = String(state.publicShare.token || "").trim();
    const share = state.publicShare.meta;
    if (!token || !share) {
        state.pageError = "分享信息尚未准备好，请刷新后重试。";
        render();
        return;
    }

    if (!share.has_text_content && !share.has_file_content) {
        state.pageError = "当前分享没有可打开的内容。";
        render();
        return;
    }

    if (!options.silent) {
        setPending("public-share-open");
        state.pageError = null;
        clearToast();
        render();
    }

    try {
        const hadBurnDeadline = Boolean(share.burn_deadline);
        const data = await request(`/v1/public/shares/${encodeURIComponent(token)}/open`, {
            method: "POST",
            withAuth: false,
            body: buildPublicShareOpenBody()
        });

        const nextShare = data.share || null;
        const accessToken = String(data.access_token || "").trim();
        if (!nextShare || !accessToken) {
            throw new Error("服务端没有返回完整的分享内容。");
        }

        resetPublicShareOpenedContent();
        state.publicShare.meta = {
            ...state.publicShare.meta,
            ...nextShare
        };
        state.publicShare.accessToken = accessToken;
        state.publicShare.accessTokenExpiresAt = String(data.access_token_expires_at || "");
        state.publicShare.textContent = nextShare.has_text_content
            ? (nextShare.is_encrypted
                ? await decryptTextWithPassword(nextShare.encrypted_payload || "", nextShare.encryption || {}, state.publicShare.password || "")
                : String(nextShare.text_content || ""))
            : "";
        state.publicShare.contentOpen = true;

        // 首次打开倒计时分享时，用户真正看到内容往往会晚于服务端记录的 first_opened_at。
        // 这里把“展示给用户的倒计时起点”单独记下来，避免请求/解密耗时把 10 秒直接吃掉几秒。
        state.publicShare.countdownDisplayDeadlineAt = resolvePublicShareDisplayDeadline(nextShare, hadBurnDeadline);
        syncPublicShareCountdown(state.publicShare.meta);
        await preparePublicShareFilePreviews(token, accessToken, nextShare, state.publicShare.password || "");
        if (!options.silent) {
            showToast("分享内容已打开");
        }
    } catch (error) {
        state.pageError = toUserMessage(error);
    } finally {
        if (!options.silent) {
            clearPending();
        }
        render();
    }
}

async function handleDownloadPublicShareFile(fileID) {
    const token = String(state.publicShare.token || "").trim();
    const share = state.publicShare.meta;
    const accessToken = String(state.publicShare.accessToken || "").trim();
    const file = findPublicShareFile(fileID);
    if (!token || !share || !accessToken || !file) {
        state.pageError = "分享文件尚未准备好，请重新打开分享。";
        render();
        return;
    }

    const pendingKey = `public-share-download:${fileID}`;
    setPending(pendingKey);
    state.pageError = null;
    clearToast();
    render();

    try {
        const filePath = buildPublicShareFilePath(token, fileID, accessToken, { download: true });
        if (!share.is_encrypted) {
            triggerBrowserDownload(buildPublicShareFileURL(token, fileID, accessToken, { download: true }));
            showToast(`开始下载：${resolvePublicShareFileName(file)}`);
            return;
        }

        const response = await requestRaw(filePath, {
            withAuth: false
        });
        const encryptedBlob = await response.blob();
        const fileEncryption = buildFileEncryptionFromHeaders(response.headers);
        const fileName = resolvePublicShareFileName(file, response.headers);
        const contentType = response.headers.get("X-Share-File-Content-Type") || file.content_type || "application/octet-stream";
        const decryptedBytes = await decryptFileWithPassword(encryptedBlob, fileEncryption, state.publicShare.password || "");
        downloadBlobToFile(new Blob([decryptedBytes], { type: contentType }), fileName);
        showToast(`开始下载：${fileName}`);
    } catch (error) {
        state.pageError = toUserMessage(error);
    } finally {
        clearPending();
        render();
    }
}

async function handleCopyPublicShareText() {
    if (!state.publicShare.meta?.allow_copy_content) {
        state.pageError = "当前分享不允许复制文本。";
        render();
        return;
    }

    if (!state.publicShare.contentOpen || !state.publicShare.textContent) {
        state.pageError = "请先打开分享内容。";
        render();
        return;
    }

    try {
        await writeTextToClipboard(state.publicShare.textContent);
        showToast("分享文本已复制");
    } catch (error) {
        state.pageError = toUserMessage(error);
        render();
    }
}

async function handleReadSystemClipboard() {
    setPending("clipboard-read");
    state.pageError = null;
    clearToast();
    render();

    try {
        state.clipboard.draftText = await readTextFromClipboard();
        showToast("已读取系统剪切板");
    } catch (error) {
        state.pageError = toUserMessage(error);
    } finally {
        clearPending();
        render();
    }
}

async function handleCopyClipboardItem(itemID) {
    const item = state.clipboard.items.find((nextItem) => nextItem.id === itemID);
    if (!item) {
        state.pageError = "这条记录不存在，可能已经被刷新覆盖。";
        render();
        return;
    }

    try {
        await writeTextToClipboard(item.text_content || "");
        showToast("文本已复制到系统剪切板");
    } catch (error) {
        state.pageError = toUserMessage(error);
        render();
    }
}

async function handleClipboardItemDelete(itemID) {
    const item = state.clipboard.items.find((nextItem) => nextItem.id === itemID);
    if (!item) {
        state.pageError = "这条记录不存在，可能已经被刷新覆盖。";
        render();
        return;
    }
    if (!window.confirm(`确认删除 SEQ #${item.seq} 这条文本历史吗？`)) {
        return;
    }

    const pendingKey = `clipboard-delete:${itemID}`;
    setPending(pendingKey);
    state.pageError = null;
    clearToast();
    render();

    try {
        const data = await request(`/v1/clipboard/items/${encodeURIComponent(itemID)}`, {
            method: "DELETE"
        });
        applyClipboardServerState(data);
        state.clipboard.items = state.clipboard.items.filter((nextItem) => nextItem.id !== itemID);
        if (state.clipboardPanel.itemId === itemID) {
            closeClipboardPanel();
        }
        if (state.clipboard.items.length === 0 && state.clipboard.historyPageIndex > 0) {
            state.clipboard.historyPageIndex -= 1;
        }
        await loadClipboardHistory({ silent: true });
        showToast("历史记录已删除");
    } catch (error) {
        state.pageError = toUserMessage(error);
    } finally {
        if (isPending(pendingKey)) {
            clearPending();
        }
        render();
    }
}

async function handleHistorySettingsSubmit(form) {
    const formData = new FormData(form);
    const retentionDays = Number(String(formData.get("retention_days") || "0").trim());
    const historyLimit = Number(String(formData.get("history_limit") || "1000").trim());

    state.clipboard.retentionDays = retentionDays;
    state.clipboard.maxStoredItems = historyLimit;
    if (!Number.isInteger(retentionDays) || retentionDays < 0) {
        state.pageError = "保留天数必须是大于等于 0 的整数。";
        render();
        return;
    }
    if (!Number.isInteger(historyLimit) || historyLimit <= 0) {
        state.pageError = "最大记录数必须是正整数。";
        render();
        return;
    }

    setPending("history-settings");
    state.pageError = null;
    clearToast();
    render();

    try {
        const data = await request("/v1/clipboard/history/settings", {
            method: "PUT",
            body: {
                retention_days: retentionDays,
                history_limit: historyLimit
            }
        });
        applyClipboardServerState(data);
        applyClipboardHistorySettings(data.settings || {});
        resetClipboardPager();
        closeClipboardPanel();
        await loadClipboardHistory({ silent: true });
        showToast(`历史设置已保存，清理 ${Number(data.deleted_count || 0)} 条记录`);
    } catch (error) {
        state.pageError = toUserMessage(error);
    } finally {
        if (isPending("history-settings")) {
            clearPending();
        }
        render();
    }
}

async function handleClipboardHistoryCleanup() {
    const input = document.getElementById("history-cleanup-days");
    const days = Number(input instanceof HTMLInputElement ? input.value : state.clipboard.cleanupDaysDraft);
    state.clipboard.cleanupDaysDraft = String(days || "");
    if (!Number.isInteger(days) || days <= 0) {
        state.pageError = "清理天数必须是正整数。";
        render();
        return;
    }
    if (!window.confirm(`确认删除 ${days} 天前的文本历史吗？`)) {
        return;
    }

    setPending("history-cleanup");
    state.pageError = null;
    clearToast();
    render();

    try {
        const data = await request("/v1/clipboard/history/cleanup", {
            method: "POST",
            body: {
                days
            }
        });
        applyClipboardServerState(data);
        applyClipboardHistorySettings(data.settings || {});
        resetClipboardPager();
        closeClipboardPanel();
        await loadClipboardHistory({ silent: true });
        showToast(`已清理 ${Number(data.deleted_count || 0)} 条历史记录`);
    } catch (error) {
        state.pageError = toUserMessage(error);
    } finally {
        if (isPending("history-cleanup")) {
            clearPending();
        }
        render();
    }
}

async function handleClipboardHistoryClear() {
    if (!window.confirm("确认清空当前账号的全部文本历史吗？")) {
        return;
    }

    setPending("history-clear");
    state.pageError = null;
    clearToast();
    render();

    try {
        const data = await request("/v1/clipboard/history/clear", {
            method: "POST"
        });
        applyClipboardServerState(data);
        applyClipboardHistorySettings(data.settings || {});
        resetClipboardPager();
        closeClipboardPanel();
        state.clipboard.items = [];
        showToast(`已清空 ${Number(data.deleted_count || 0)} 条历史记录`);
    } catch (error) {
        state.pageError = toUserMessage(error);
    } finally {
        if (isPending("history-clear")) {
            clearPending();
        }
        render();
    }
}

async function handleHistoryPrev() {
    if (state.clipboard.historyPageIndex <= 0 || isPending("clipboard-history")) {
        return;
    }

    closeClipboardPanel();
    state.clipboard.historyPageIndex -= 1;
    await loadClipboardHistory({ silent: false });
}

async function handleHistoryNext() {
    if (!state.clipboard.historyHasMore || isPending("clipboard-history")) {
        return;
    }

    closeClipboardPanel();
    state.clipboard.historyPageIndex += 1;
    await loadClipboardHistory({ silent: false });
}

async function handleFilesPrev() {
    if (state.files.page <= 1 || isPending("files")) {
        return;
    }

    closeFilePanel();
    state.files.page -= 1;
    await loadFiles({ silent: false });
}

async function handleFilesNext() {
    if ((state.files.totalPages > 0 && state.files.page >= state.files.totalPages) || isPending("files")) {
        return;
    }

    closeFilePanel();
    state.files.page += 1;
    await loadFiles({ silent: false });
}

function isCurrentUserAdmin() {
    return Boolean(state.profile?.user?.is_admin || state.session?.user?.is_admin);
}

function syncRequestsFormDraft(target) {
    if (!(target instanceof HTMLInputElement) && !(target instanceof HTMLTextAreaElement) && !(target instanceof HTMLSelectElement)) {
        return false;
    }

    if (target.closest("#quota-request-form")) {
        state.requests.quotaForm = {
            ...state.requests.quotaForm,
            requestedQuotaMB: target.name === "requested_quota_mb" ? target.value : state.requests.quotaForm.requestedQuotaMB,
            reason: target.name === "reason" ? target.value : state.requests.quotaForm.reason
        };
        return true;
    }

    if (target.closest("#bandwidth-request-form")) {
        state.requests.bandwidthForm = {
            ...state.requests.bandwidthForm,
            requestedUploadKbps: target.name === "requested_upload_kbps" ? target.value : state.requests.bandwidthForm.requestedUploadKbps,
            requestedDownloadKbps: target.name === "requested_download_kbps" ? target.value : state.requests.bandwidthForm.requestedDownloadKbps,
            reason: target.name === "reason" ? target.value : state.requests.bandwidthForm.reason
        };
        return true;
    }

    if (target.closest("#admin-request-form")) {
        state.requests.adminForm = {
            ...state.requests.adminForm,
            [target.name]: target.value
        };
        return true;
    }

    return false;
}

function syncAdminSettingsDraft(target) {
    if (!(target instanceof HTMLInputElement) && !(target instanceof HTMLSelectElement)) {
        return false;
    }

    if (!target.closest("#admin-settings-form")) {
        return false;
    }

    state.admin.settingsForm = {
        ...state.admin.settingsForm,
        [normalizeAdminSettingsFieldName(target.name || target.id)]: target instanceof HTMLInputElement && target.type === "checkbox"
            ? target.checked
            : target.value
    };
    return true;
}

function syncAdminSettingsDraftFromForm(form) {
    const formData = new FormData(form);
    state.admin.settingsForm = {
        maxUserCount: String(formData.get("max_user_count") || "").trim(),
        defaultStorageQuotaMB: String(formData.get("default_storage_quota_mb") || "").trim(),
        defaultUploadBandwidthKbps: String(formData.get("default_upload_bandwidth_kbps") || "").trim(),
        defaultDownloadBandwidthKbps: String(formData.get("default_download_bandwidth_kbps") || "").trim(),
        maxUserUploadBandwidthKbps: String(formData.get("max_user_upload_bandwidth_kbps") || "").trim(),
        maxUserDownloadBandwidthKbps: String(formData.get("max_user_download_bandwidth_kbps") || "").trim(),
        maxUploadFileMB: String(formData.get("max_upload_file_mb") || "").trim(),
        allowRegistration: formData.get("allow_registration") !== null
    };
    return state.admin.settingsForm;
}

function syncAdminUserDraft(target) {
    if (!(target instanceof HTMLInputElement) && !(target instanceof HTMLSelectElement)) {
        return false;
    }

    const form = target.closest(".admin-user-form");
    if (!(form instanceof HTMLFormElement)) {
        return false;
    }

    syncAdminUserDraftFromForm(form);
    return true;
}

function syncAdminUserDraftFromForm(form) {
    const userID = String(form.dataset.adminUserId || "").trim();
    const formData = new FormData(form);
    const nextDraft = {
        storageQuotaMB: String(formData.get("storage_quota_mb") || "").trim(),
        uploadBandwidthKbps: String(formData.get("upload_bandwidth_kbps") || "").trim(),
        downloadBandwidthKbps: String(formData.get("download_bandwidth_kbps") || "").trim(),
        isAdmin: formData.get("is_admin") !== null
    };

    state.admin.userDrafts = {
        ...state.admin.userDrafts,
        [userID]: nextDraft
    };
    return nextDraft;
}

function createAdminSettingsDraft(settings) {
    return {
        maxUserCount: String(settings.max_user_count || ""),
        defaultStorageQuotaMB: String(bytesToMegabytes(settings.default_storage_quota_bytes)),
        defaultUploadBandwidthKbps: String(settings.default_upload_bandwidth_kbps || ""),
        defaultDownloadBandwidthKbps: String(settings.default_download_bandwidth_kbps || ""),
        maxUserUploadBandwidthKbps: String(settings.max_user_upload_bandwidth_kbps || ""),
        maxUserDownloadBandwidthKbps: String(settings.max_user_download_bandwidth_kbps || ""),
        maxUploadFileMB: String(bytesToMegabytes(settings.max_upload_file_bytes)),
        allowRegistration: Boolean(settings.allow_registration)
    };
}

function createAdminUserDrafts(users) {
    const drafts = {};
    for (const user of Array.isArray(users) ? users : []) {
        drafts[user.id] = {
            storageQuotaMB: String(bytesToMegabytes(user.storage_quota_bytes)),
            uploadBandwidthKbps: String(user.upload_bandwidth_kbps || ""),
            downloadBandwidthKbps: String(user.download_bandwidth_kbps || ""),
            isAdmin: Boolean(user.is_admin)
        };
    }
    return drafts;
}

function normalizeAdminSettingsFieldName(name) {
    switch (String(name || "").trim()) {
        case "max_user_count":
            return "maxUserCount";
        case "default_storage_quota_mb":
            return "defaultStorageQuotaMB";
        case "default_upload_bandwidth_kbps":
            return "defaultUploadBandwidthKbps";
        case "default_download_bandwidth_kbps":
            return "defaultDownloadBandwidthKbps";
        case "max_user_upload_bandwidth_kbps":
            return "maxUserUploadBandwidthKbps";
        case "max_user_download_bandwidth_kbps":
            return "maxUserDownloadBandwidthKbps";
        case "max_upload_file_mb":
            return "maxUploadFileMB";
        case "allow_registration":
            return "allowRegistration";
        default:
            return String(name || "");
    }
}

function buildAdminReviewEndpoint(type, requestID, action) {
    const normalizedType = String(type || "").trim();
    const normalizedID = encodeURIComponent(String(requestID || "").trim());
    const normalizedAction = String(action || "").trim();

    switch (normalizedType) {
        case "quota":
            return `/v1/admin/quota-requests/${normalizedID}/${normalizedAction}`;
        case "bandwidth":
            return `/v1/admin/bandwidth-requests/${normalizedID}/${normalizedAction}`;
        case "admin":
            return `/v1/admin/admin-requests/${normalizedID}/${normalizedAction}`;
        default:
            return "";
    }
}

function bytesToMegabytes(value) {
    const bytes = Number(value || 0);
    if (!Number.isFinite(bytes) || bytes <= 0) {
        return 0;
    }
    return Math.max(Math.round(bytes / (1024 * 1024)), 1);
}

async function ensureRealtimeConnection(options = {}) {
    if (!state.session) {
        disconnectRealtime();
        return;
    }

    if (!options.force && realtimeSocket && (state.clipboard.wsStatus === "connected" || state.clipboard.wsStatus === "connecting" || state.clipboard.wsStatus === "reconnecting")) {
        return;
    }

    clearRealtimeReconnectTimer();
    stopRealtimePingLoop();

    const tokenReady = await ensureValidAccessToken(30);
    if (!tokenReady) {
        handleAuthExpired("登录已失效，请重新登录。");
        return;
    }

    const accessToken = state.session?.tokens?.access_token || "";
    const wsURL = buildWebSocketURL(state.serverBaseUrl, "/v1/ws", {
        access_token: accessToken
    });
    if (!wsURL) {
        return;
    }

    state.clipboard.wsStatus = options.force ? "reconnecting" : "connecting";
    render();

    const socket = new WebSocket(wsURL);
    realtimeSocket = socket;

    socket.addEventListener("open", () => {
        if (realtimeSocket !== socket) {
            return;
        }
        state.clipboard.wsStatus = "connected";
        state.clipboard.wsReconnectAttempt = 0;
        startRealtimePingLoop(socket);
        render();
    });

    socket.addEventListener("message", (event) => {
        if (realtimeSocket !== socket) {
            return;
        }
        handleRealtimeMessage(event.data);
    });

    socket.addEventListener("error", () => {
        if (realtimeSocket !== socket) {
            return;
        }
        state.clipboard.wsStatus = "disconnected";
        render();
    });

    socket.addEventListener("close", () => {
        if (realtimeSocket !== socket) {
            return;
        }

        realtimeSocket = null;
        stopRealtimePingLoop();
        if (!state.session) {
            state.clipboard.wsStatus = "disconnected";
            render();
            return;
        }

        state.clipboard.wsStatus = "reconnecting";
        scheduleRealtimeReconnect();
        render();
    });
}

function handleRealtimeMessage(rawMessage) {
    let payload;
    try {
        payload = JSON.parse(rawMessage);
    } catch (error) {
        return;
    }

    const messageType = String(payload?.type || "");
    switch (messageType) {
        case "sync.hello":
            applyClipboardServerState(payload);
            state.clipboard.wsStatus = "connected";
            state.clipboard.wsLastHeartbeatAt = new Date().toISOString();
            // hello 只告诉我们“服务器最新到哪了”，如果本机 ACK 还落后，就自动补齐缺的事件。
            if (state.clipboard.latestSeq > Math.max(state.clipboard.lastAckSeq, state.clipboard.pendingAckSeq)) {
                void ensureClipboardCaughtUp({ reason: "hello" });
            }
            render();
            return;
        case "sync.heartbeat":
        case "sync.pong":
            state.clipboard.wsLastHeartbeatAt = new Date().toISOString();
            if (state.route === "history") {
                render();
            }
            return;
        case "sync.acknowledged":
            applyClipboardServerState(payload);
            if (state.route === "history") {
                render();
            }
            return;
        case "clipboard.new":
            handleRealtimeClipboardItem(payload.item || null);
            return;
        default:
            return;
    }
}

function handleRealtimeClipboardItem(item) {
    if (!item || !item.id) {
        return;
    }

    const nextSeq = Number(item.seq || 0);
    state.clipboard.wsLastEventAt = new Date().toISOString();
    state.clipboard.latestSeq = Math.max(state.clipboard.latestSeq, nextSeq);

    if (!tryAdvancePendingAck(nextSeq)) {
        const seenSeq = Math.max(state.clipboard.lastAckSeq, state.clipboard.pendingAckSeq);
        if (nextSeq > seenSeq + 1) {
            showToast("检测到实时序号缺口，正在自动补拉");
            void ensureClipboardCaughtUp({ reason: "gap" });
        }
    }

    if (state.clipboard.historyPageIndex === 0) {
        mergeClipboardItems([item]);
    } else {
        showToast("收到新记录，请返回第一页查看");
    }

    if (state.route !== "history") {
        showToast("收到来自其他设备的新文本");
    }

    scheduleClipboardAck();
    render();
}

function startRealtimePingLoop(socket) {
    stopRealtimePingLoop();
    realtimePingTimerID = window.setInterval(() => {
        if (!socket || socket.readyState !== WebSocket.OPEN) {
            return;
        }
        socket.send(JSON.stringify({ type: "sync.ping" }));
    }, 20000);
}

function stopRealtimePingLoop() {
    if (realtimePingTimerID) {
        window.clearInterval(realtimePingTimerID);
        realtimePingTimerID = 0;
    }
}

function scheduleRealtimeReconnect() {
    if (realtimeReconnectTimerID || !state.session) {
        return;
    }

    state.clipboard.wsReconnectAttempt += 1;
    const retryDelay = Math.min(1000 * 2 ** Math.min(state.clipboard.wsReconnectAttempt - 1, 4), 10000);
    realtimeReconnectTimerID = window.setTimeout(() => {
        realtimeReconnectTimerID = 0;
        void ensureRealtimeConnection();
    }, retryDelay);
}

function clearRealtimeReconnectTimer() {
    if (realtimeReconnectTimerID) {
        window.clearTimeout(realtimeReconnectTimerID);
        realtimeReconnectTimerID = 0;
    }
}

function scheduleClipboardAck(delay = 800) {
    if (!state.session || state.clipboard.pendingAckSeq <= state.clipboard.lastAckSeq) {
        return;
    }

    if (clipboardAutoAckTimerID) {
        window.clearTimeout(clipboardAutoAckTimerID);
    }

    // ACK 改成自动提交，避免页面只剩“待 ACK”但用户没有手动入口。
    clipboardAutoAckTimerID = window.setTimeout(() => {
        clipboardAutoAckTimerID = 0;
        void flushClipboardAck();
    }, delay);
}

async function flushClipboardAck() {
    if (clipboardAutoAckInFlight || !state.session) {
        return;
    }

    const ackSeq = state.clipboard.pendingAckSeq;
    if (ackSeq <= state.clipboard.lastAckSeq) {
        return;
    }

    clipboardAutoAckInFlight = true;
    try {
        const data = await request("/v1/sync/ack", {
            method: "POST",
            body: {
                seq: ackSeq
            }
        });

        applyClipboardServerState(data);
        if (state.route === "history") {
            render();
        }
    } catch (error) {
        console.warn("auto ack failed", error);
        scheduleClipboardAck(2000);
    } finally {
        clipboardAutoAckInFlight = false;
    }
}

async function ensureClipboardCaughtUp(options = {}) {
    if (!state.session) {
        return 0;
    }
    if (clipboardAutoPullPromise) {
        return clipboardAutoPullPromise;
    }

    clipboardAutoPullPromise = (async () => {
        let totalPulled = 0;

        try {
            // 这里用循环把断线期间积压的文本批量补齐，避免一次只拉 20 条导致仍然落后。
            for (let batchIndex = 0; batchIndex < 10; batchIndex += 1) {
                const sinceSeq = Math.max(state.clipboard.lastAckSeq, state.clipboard.pendingAckSeq);
                const query = new URLSearchParams({
                    since_seq: String(sinceSeq),
                    limit: "50"
                });
                const data = await request(`/v1/sync/pull?${query.toString()}`);
                applyClipboardServerState(data);

                const items = Array.isArray(data.items) ? data.items : [];
                if (state.clipboard.historyPageIndex !== 0 && items.length > 0) {
                    resetClipboardPager();
                    closeClipboardPanel();
                }
                mergeClipboardItems(items);

                const nextSinceSeq = Number(data.next_since_seq || sinceSeq);
                if (nextSinceSeq > sinceSeq) {
                    // 清理历史会让 seq 中间出现缺口；补拉接口返回的 next_since_seq
                    // 代表服务端允许客户端推进到的位置，因此这里直接推进待 ACK 游标。
                    state.clipboard.pendingAckSeq = Math.max(state.clipboard.pendingAckSeq, nextSinceSeq);
                }
                if (items.length > 0) {
                    totalPulled += items.length;
                }
                if (!data.has_more || items.length === 0) {
                    break;
                }
            }

            if (totalPulled > 0) {
                scheduleClipboardAck();
                if (options.reason === "gap") {
                    showToast(`已自动补拉 ${totalPulled} 条记录`);
                }
            }
        } catch (error) {
            console.warn("auto pull failed", error);
        } finally {
            clipboardAutoPullPromise = null;
            if (state.route === "history") {
                render();
            }
        }

        return totalPulled;
    })();

    return clipboardAutoPullPromise;
}

function disconnectRealtime() {
    clearRealtimeReconnectTimer();
    stopRealtimePingLoop();
    if (clipboardAutoAckTimerID) {
        window.clearTimeout(clipboardAutoAckTimerID);
        clipboardAutoAckTimerID = 0;
    }

    if (realtimeSocket) {
        const socket = realtimeSocket;
        realtimeSocket = null;
        if (socket.readyState === WebSocket.OPEN || socket.readyState === WebSocket.CONNECTING) {
            socket.close();
        }
    }

    state.clipboard.wsStatus = "disconnected";
}

function applyClipboardServerState(data) {
    const latestSeq = Math.max(Number(data?.latest_seq || 0), 0);
    const ackSeq = Math.max(Number(data?.current_device_ack_seq || 0), 0);

    if (latestSeq > state.clipboard.latestSeq) {
        state.clipboard.latestSeq = latestSeq;
    }
    if (ackSeq > state.clipboard.lastAckSeq) {
        state.clipboard.lastAckSeq = ackSeq;
    }
    if (state.clipboard.pendingAckSeq < state.clipboard.lastAckSeq) {
        state.clipboard.pendingAckSeq = state.clipboard.lastAckSeq;
    }
}

function applyClipboardHistorySettings(settings) {
    if (!settings || typeof settings !== "object") {
        return;
    }
    state.clipboard.retentionDays = Math.max(Number(settings.retention_days || 0), 0);
    state.clipboard.maxStoredItems = Math.max(Number(settings.history_limit || 1000), 1);
    state.clipboard.settingsUpdatedAt = String(settings.updated_at || "");
}

function tryAdvancePendingAck(seq) {
    const nextSeq = Math.max(Number(seq || 0), 0);
    const currentSeenSeq = Math.max(state.clipboard.lastAckSeq, state.clipboard.pendingAckSeq);
    if (nextSeq === currentSeenSeq + 1) {
        state.clipboard.pendingAckSeq = nextSeq;
        return true;
    }
    return nextSeq <= currentSeenSeq;
}

function mergeClipboardItems(items) {
    if (!Array.isArray(items) || items.length === 0 || state.clipboard.historyPageIndex !== 0) {
        return;
    }

    const mergedMap = new Map();
    for (const item of state.clipboard.items) {
        if (item?.id) {
            mergedMap.set(item.id, item);
        }
    }
    for (const item of items) {
        if (item?.id) {
            mergedMap.set(item.id, item);
        }
    }

    state.clipboard.items = [...mergedMap.values()]
        .sort((left, right) => Number(right.seq || 0) - Number(left.seq || 0))
        .slice(0, state.clipboard.historyLimit);
}

function resetClipboardPager() {
    state.clipboard.historyPageIndex = 0;
    state.clipboard.historyCursors = [null];
    state.clipboard.historyHasMore = false;
}

function findFileItem(fileID) {
    return state.files.items.find((item) => item.id === fileID) || null;
}

function handleAuthExpired(message) {
    disconnectRealtime();
    clearPublicShareCountdown();
    clearSession();
    state.profile = null;
    state.devices = [];
    state.pageError = message;
    state.isBootstrapping = false;
    render();
    navigate(AUTH_ROUTE, { preserveError: true });
}

function showToast(message) {
    state.pageMessage = message;
    scheduleToastDismiss();
    render();
}

function clearToast() {
    if (toastTimerID) {
        window.clearTimeout(toastTimerID);
        toastTimerID = 0;
    }
    state.pageMessage = null;
}

function scheduleToastDismiss() {
    if (toastTimerID) {
        window.clearTimeout(toastTimerID);
    }

    if (!state.pageMessage) {
        toastTimerID = 0;
        return;
    }

    toastTimerID = window.setTimeout(() => {
        state.pageMessage = null;
        toastTimerID = 0;
        render();
    }, 3200);
}

function buildPublicShareOpenBody() {
    const password = String(state.publicShare.password || "").trim();
    const payload = {};
    if (password) {
        payload.password = password;
    }
    return payload;
}

async function buildShareComposePayload(textContent, files) {
    const shareStrategy = buildShareStrategyPayload();
    const password = String(state.shares.password || "").trim();
    const isEncrypted = password !== "";
    const normalizedFiles = Array.isArray(files) ? files.filter((file) => file instanceof File) : [];
    if (isEncrypted && password.length < 4) {
        throw new Error("分享密码至少需要 4 位。");
    }

    const payload = new FormData();
    payload.append("is_encrypted", String(isEncrypted));
    payload.append("never_expires", String(shareStrategy.never_expires));
    payload.append("burn_mode", shareStrategy.burn_mode);
    payload.append("allow_copy_content", String(shareStrategy.allow_copy_content));
    if (!shareStrategy.never_expires) {
        payload.append("expire_seconds", String(shareStrategy.expire_seconds));
    }
    if (shareStrategy.burn_mode === "countdown") {
        payload.append("burn_after_seconds", String(shareStrategy.burn_after_seconds));
    }

    if (isEncrypted) {
        payload.append("password", password);
    }

    if (textContent !== "") {
        if (isEncrypted) {
            const encryptedText = await encryptTextWithPassword(textContent, password);
            payload.append("text_encrypted_payload", encryptedText.encryptedPayload);
            payload.append("text_encryption", JSON.stringify(encryptedText.encryption));
        } else {
            payload.append("text_content", textContent);
        }
    }

    if (normalizedFiles.length > 0) {
        const manifest = [];
        for (const file of normalizedFiles) {
            let uploadFile = file;
            let fileEncryption = null;
            if (isEncrypted) {
                const encryptedFile = await encryptFileWithPassword(file, password);
                uploadFile = encryptedFile.encryptedFile;
                fileEncryption = encryptedFile.encryption;
            }

            payload.append("files", uploadFile, uploadFile.name);
            manifest.push({
                original_name: file.name || "share.bin",
                original_content_type: file.type || "application/octet-stream",
                encryption: fileEncryption
            });
        }
        payload.append("files_manifest", JSON.stringify(manifest));
    }

    return payload;
}

function buildShareStrategyPayload() {
    const strategyKey = String(state.shares.strategyKey || "expire");
    const rules = state.shareRules || {};

    if (strategyKey === "never") {
        const rule = rules.never || {};
        return {
            never_expires: true,
            expire_seconds: 0,
            burn_mode: "none",
            burn_after_seconds: 0,
            allow_copy_content: Boolean(rule.allowCopyText)
        };
    }

    if (strategyKey === "once") {
        const rule = rules.once || {};
        const countdownSecondsValue = Number(rule.countdownSeconds || 10);
        const countdownSeconds = Number.isFinite(countdownSecondsValue) && countdownSecondsValue > 0 ? countdownSecondsValue : 10;
        return {
            never_expires: true,
            expire_seconds: 0,
            burn_mode: rule.showCountdown ? "countdown" : "once",
            burn_after_seconds: rule.showCountdown ? countdownSeconds : 0,
            allow_copy_content: Boolean(rule.allowCopyText)
        };
    }

    const rule = rules.expire || {};
    const expireHoursValue = Number(rule.expireHours || 24);
    const expireHours = Number.isFinite(expireHoursValue) && expireHoursValue > 0 ? expireHoursValue : 24;
    return {
        never_expires: false,
        expire_seconds: Math.round(expireHours * 3600),
        burn_mode: "none",
        burn_after_seconds: 0,
        allow_copy_content: Boolean(rule.allowCopyText)
    };
}

function resolveShareCreatedMessage(textContent, files) {
    const fileCount = Array.isArray(files) ? files.length : 0;
    if (textContent !== "" && fileCount > 0) {
        return fileCount === 1 ? "文件和文字分享已生成" : `${fileCount} 个文件和文字分享已生成`;
    }
    if (fileCount > 0) {
        return fileCount === 1 ? "文件分享已生成" : `${fileCount} 个文件分享已生成`;
    }
    return "文字分享已生成";
}

function resolveShareListLabel(share) {
    if (share?.has_file_content) {
        const files = Array.isArray(share.files) ? share.files : [];
        if (files.length > 1) {
            return `${files[0]?.original_name || "文件分享"} 等 ${files.length} 个文件`;
        }
        return share.file?.original_name || files[0]?.original_name || (share.has_text_content ? "文件 + 文字分享" : "文件分享");
    }
    return share?.text_preview || "文字分享";
}

function parseRoute(hashValue) {
    const rawRoute = (hashValue || "").replace(/^#\/?/, "").trim();
    if (!rawRoute) {
        return {
            route: state.session ? DEFAULT_ROUTE : AUTH_ROUTE,
            publicShareToken: ""
        };
    }

    if (rawRoute.toLowerCase().startsWith("public/")) {
        const rawToken = rawRoute.slice("public/".length).trim();
        return {
            route: "public-share",
            publicShareToken: decodeHeaderValue(rawToken)
        };
    }

    const route = rawRoute.toLowerCase();
    if (PROTECTED_ROUTES.has(route) || route === AUTH_ROUTE) {
        return {
            route,
            publicShareToken: ""
        };
    }
    return {
        route: state.session ? DEFAULT_ROUTE : AUTH_ROUTE,
        publicShareToken: ""
    };
}

function applyParsedRoute(parsedRoute) {
    state.route = parsedRoute?.route || (state.session ? DEFAULT_ROUTE : AUTH_ROUTE);
    if (state.route === "public-share") {
        const nextToken = String(parsedRoute?.publicShareToken || "").trim();
        if (state.publicShare.token !== nextToken) {
            resetPublicShareOpenedContent();
            state.publicShare.token = nextToken;
            state.publicShare.meta = null;
            state.publicShare.password = "";
        }
        return;
    }

    resetPublicShareOpenedContent();
    state.publicShare.token = "";
    state.publicShare.meta = null;
    state.publicShare.password = "";
}

function buildPublicShareLink(token) {
    const normalizedToken = String(token || "").trim();
    if (!normalizedToken) {
        return "";
    }
    return `${window.location.origin}${window.location.pathname}#/public/${encodeURIComponent(normalizedToken)}`;
}

function syncPublicShareCountdown(share) {
    clearPublicShareCountdown();
    if (!share) {
        return;
    }

    const deadlineAt = resolvePublicShareCountdownDeadline(share);
    if (!Number.isFinite(deadlineAt) || deadlineAt <= Date.now()) {
        return;
    }

    updatePublicShareRemaining(deadlineAt);
    publicShareCountdownTimerID = window.setInterval(() => {
        updatePublicShareRemaining(deadlineAt);
    }, 1000);
}

function clearPublicShareCountdown() {
    if (publicShareCountdownTimerID) {
        window.clearInterval(publicShareCountdownTimerID);
        publicShareCountdownTimerID = 0;
    }
}

function updatePublicShareRemaining(deadlineAt) {
    if (!state.publicShare.meta) {
        clearPublicShareCountdown();
        return;
    }

    const remainingSeconds = Math.max(Math.ceil((deadlineAt - Date.now()) / 1000), 0);
    state.publicShare.meta = {
        ...state.publicShare.meta,
        remaining_seconds: remainingSeconds
    };

    if (remainingSeconds > 0) {
        if (state.route === "public-share") {
            render();
        }
        return;
    }

    clearPublicShareCountdown();
    state.publicShare.meta = {
        ...state.publicShare.meta,
        status: state.publicShare.meta.burn_mode === "countdown" ? "consumed" : "expired",
        remaining_seconds: 0
    };
    if (state.publicShare.contentOpen) {
        resetPublicShareOpenedContent();
        state.pageError = "分享倒计时已结束，内容已自动隐藏。";
    }
    if (state.route === "public-share") {
        render();
    }
}

function resolveShareDeadline(share) {
    const expiresAt = Date.parse(share?.expires_at || "");
    const burnDeadline = Date.parse(share?.burn_deadline || "");
    if (Number.isFinite(expiresAt) && Number.isFinite(burnDeadline)) {
        return Math.min(expiresAt, burnDeadline);
    }
    if (Number.isFinite(expiresAt)) {
        return expiresAt;
    }
    if (Number.isFinite(burnDeadline)) {
        return burnDeadline;
    }
    return Number.NaN;
}

function resolvePublicShareDisplayDeadline(share, hadBurnDeadline) {
    if ((share?.burn_mode || "").toLowerCase() !== "countdown") {
        return 0;
    }

    const configuredSeconds = Math.max(Number(share?.burn_after_seconds || 0), 0);
    const remainingSeconds = Math.max(Number(share?.remaining_seconds || 0), 0);
    const visibleSeconds = hadBurnDeadline ? remainingSeconds : Math.max(configuredSeconds, remainingSeconds);
    if (visibleSeconds <= 0) {
        return 0;
    }

    return Date.now() + visibleSeconds * 1000;
}

function resolvePublicShareCountdownDeadline(share) {
    if (
        state.publicShare.contentOpen &&
        (share?.burn_mode || "").toLowerCase() === "countdown" &&
        Number.isFinite(state.publicShare.countdownDisplayDeadlineAt) &&
        state.publicShare.countdownDisplayDeadlineAt > Date.now()
    ) {
        return state.publicShare.countdownDisplayDeadlineAt;
    }

    return resolveShareDeadline(share);
}

function decodeHeaderValue(value) {
    const normalized = String(value || "");
    if (!normalized) {
        return "";
    }

    try {
        return decodeURIComponent(normalized);
    } catch (error) {
        return normalized;
    }
}

function downloadBlobToFile(blob, fileName) {
    const objectURL = URL.createObjectURL(blob);
    const link = document.createElement("a");
    link.href = objectURL;
    link.download = fileName || "download.bin";
    document.body.appendChild(link);
    link.click();
    link.remove();
    URL.revokeObjectURL(objectURL);
}

function triggerBrowserDownload(url) {
    const link = document.createElement("a");
    link.href = url;
    link.rel = "noopener";
    document.body.appendChild(link);
    link.click();
    link.remove();
}

function setSelectedShareFiles(files) {
    state.shares.selectedFiles = Array.isArray(files)
        ? files.filter((file) => file instanceof File)
        : [];
}

function resetPublicShareOpenedContent() {
    revokePublicSharePreviewURLs();
    state.publicShare.accessToken = "";
    state.publicShare.accessTokenExpiresAt = "";
    state.publicShare.textContent = "";
    state.publicShare.contentOpen = false;
    state.publicShare.filePreviews = {};
    state.publicShare.previewLoadingMap = {};
    state.publicShare.previewErrorMap = {};
    state.publicShare.countdownDisplayDeadlineAt = 0;
}

function revokePublicSharePreviewURLs() {
    const previews = state.publicShare.filePreviews || {};
    for (const previewURL of Object.values(previews)) {
        if (typeof previewURL === "string" && previewURL.startsWith("blob:")) {
            URL.revokeObjectURL(previewURL);
        }
    }
}

function canAutoOpenPublicShare(share) {
    return Boolean(share) &&
        share.status === "active" &&
        !share.requires_password &&
        (share.has_text_content || share.has_file_content);
}

function findPublicShareFile(fileID) {
    const files = Array.isArray(state.publicShare.meta?.files) ? state.publicShare.meta.files : [];
    return files.find((file) => file.id === fileID) || null;
}

function isPreviewablePublicShareFile(file) {
    return Boolean(file?.is_image || file?.is_video);
}

async function preparePublicShareFilePreviews(publicToken, accessToken, share, password) {
    const files = Array.isArray(share?.files) ? share.files : [];
    if (!share?.is_encrypted) {
        return;
    }

    const previewableFiles = files.filter(isPreviewablePublicShareFile);
    if (previewableFiles.length === 0) {
        return;
    }

    const loadingMap = { ...state.publicShare.previewLoadingMap };
    for (const file of previewableFiles) {
        loadingMap[file.id] = true;
    }
    state.publicShare.previewLoadingMap = loadingMap;
    render();

    await Promise.all(previewableFiles.map((file) => prepareEncryptedPublicSharePreview(publicToken, accessToken, file, password)));
}

async function prepareEncryptedPublicSharePreview(publicToken, accessToken, file, password) {
    try {
        const response = await requestRaw(buildPublicShareFilePath(publicToken, file.id, accessToken), {
            withAuth: false
        });
        const encryptedBlob = await response.blob();
        const encryption = buildFileEncryptionFromHeaders(response.headers);
        const contentType = response.headers.get("X-Share-File-Content-Type") || file.content_type || "application/octet-stream";
        const decryptedBytes = await decryptFileWithPassword(encryptedBlob, encryption, password);
        const previewURL = URL.createObjectURL(new Blob([decryptedBytes], { type: contentType }));

        if (state.publicShare.token !== publicToken || state.publicShare.accessToken !== accessToken || !state.publicShare.contentOpen) {
            URL.revokeObjectURL(previewURL);
            return;
        }

        state.publicShare.filePreviews = {
            ...state.publicShare.filePreviews,
            [file.id]: previewURL
        };
        const nextErrors = { ...state.publicShare.previewErrorMap };
        delete nextErrors[file.id];
        state.publicShare.previewErrorMap = nextErrors;
    } catch (error) {
        if (state.publicShare.token === publicToken && state.publicShare.accessToken === accessToken) {
            state.publicShare.previewErrorMap = {
                ...state.publicShare.previewErrorMap,
                [file.id]: toUserMessage(error)
            };
        }
    } finally {
        if (state.publicShare.token === publicToken && state.publicShare.accessToken === accessToken) {
            const nextLoadingMap = { ...state.publicShare.previewLoadingMap };
            delete nextLoadingMap[file.id];
            state.publicShare.previewLoadingMap = nextLoadingMap;
            if (state.route === "public-share") {
                render();
            }
        }
    }
}

function buildPublicShareFilePath(token, fileID, accessToken, options = {}) {
    const query = new URLSearchParams({
        access_token: accessToken
    });
    if (options.download) {
        query.set("download", "1");
    }
    return `/v1/public/shares/${encodeURIComponent(token)}/files/${encodeURIComponent(fileID)}?${query.toString()}`;
}

function buildPublicShareFileURL(token, fileID, accessToken, options = {}) {
    return `${state.serverBaseUrl}${buildPublicShareFilePath(token, fileID, accessToken, options)}`;
}

function buildFileEncryptionFromHeaders(headers) {
    return {
        version: headers.get("X-Share-Encryption-Version") || "",
        kdf: headers.get("X-Share-Encryption-KDF") || "",
        iterations: Number(headers.get("X-Share-Encryption-Iterations") || 0),
        salt: headers.get("X-Share-Encryption-Salt") || "",
        nonce: headers.get("X-Share-Encryption-Nonce") || "",
        cipher: headers.get("X-Share-Encryption-Cipher") || ""
    };
}

function resolvePublicShareFileName(file, headers = null) {
    const headerFileName = headers instanceof Headers
        ? decodeHeaderValue(headers.get("X-Share-File-Original-Name")) || parseFilenameFromContentDisposition(headers.get("Content-Disposition"))
        : "";
    return headerFileName || file?.original_name || "share.bin";
}

function navigate(route, options = {}) {
    const nextRoute = route || AUTH_ROUTE;
    const nextHash = `#/${nextRoute}`;
    preservePageErrorOnNextRouteChange = Boolean(options.preserveError);
    if (window.location.hash === nextHash) {
        void handleRouteChange();
        return;
    }
    window.location.hash = nextHash;
}

function parseFilenameFromContentDisposition(contentDisposition) {
    const headerValue = String(contentDisposition || "");
    if (!headerValue) {
        return "";
    }

    const utf8Match = headerValue.match(/filename\*=UTF-8''([^;]+)/i);
    if (utf8Match?.[1]) {
        try {
            return decodeURIComponent(utf8Match[1]);
        } catch (error) {
            return utf8Match[1];
        }
    }

    const plainMatch = headerValue.match(/filename="?([^";]+)"?/i);
    return plainMatch?.[1] || "";
}

function render() {
    renderApp(appRoot);
}

bootstrap();
