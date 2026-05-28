import { AUTH_ROUTE, DEFAULT_ROUTE, PROTECTED_ROUTES } from "./config/app.js";
import { renderApp } from "./render/layout.js";
import { ensureValidAccessToken, request } from "./services/api.js";
import {
    clearPending,
    clearSettingsPasswordForm,
    clearSession,
    closeSettingsModal,
    closeClipboardPanel,
    closeDevicePanel,
    isPending,
    openClipboardPanel,
    openSettingsModal,
    openDevicePanel,
    saveSidebarCollapsed,
    selectSettingsCategory,
    setPending,
    setSession,
    state,
    updateSessionUser,
    updateSettingsPasswordForm,
    updateSessionDevice
} from "./state/store.js";
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
    state.route = normalizeRoute(window.location.hash);
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
            case "password-change-form":
                event.preventDefault();
                void handlePasswordChangeSubmit(form);
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
            case "force-device-offline":
                void handleForceDeviceOffline(target.getAttribute("data-device-id") || "");
                break;
            case "reload-history":
                void loadClipboardHistory({ silent: false });
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
        }
    });
}

async function handleRouteChange() {
    state.route = normalizeRoute(window.location.hash);
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

    if (PROTECTED_ROUTES.has(state.route) && !state.session) {
        disconnectRealtime();
        state.isBootstrapping = false;
        render();
        navigate(AUTH_ROUTE);
        return;
    }

    if (state.session) {
        if (state.route === "devices" || state.route === "history") {
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

        if (state.route === "devices") {
            await loadDevices({ silent: true });
        }
        if (state.route === "history") {
            await loadClipboardHistory({ silent: true });
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

    try {
        const data = await request("/v1/account/me");
        state.profile = data;
        return true;
    } catch (error) {
        console.warn("load current account failed", error);
        handleAuthExpired("登录已失效，请重新登录。");
        return false;
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
                if (items.length > 0) {
                    tryAdvancePendingAck(nextSinceSeq);
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

function handleAuthExpired(message) {
    disconnectRealtime();
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

function normalizeRoute(hashValue) {
    const route = (hashValue || "").replace(/^#\/?/, "").trim().toLowerCase();
    if (!route) {
        return state.session ? DEFAULT_ROUTE : AUTH_ROUTE;
    }

    if (PROTECTED_ROUTES.has(route) || route === AUTH_ROUTE) {
        return route;
    }
    return state.session ? DEFAULT_ROUTE : AUTH_ROUTE;
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

function render() {
    renderApp(appRoot);
}

bootstrap();
