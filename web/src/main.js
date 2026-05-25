import { AUTH_ROUTE, DEFAULT_ROUTE, PROTECTED_ROUTES } from "./config/app.js";
import { renderApp } from "./render/layout.js";
import { request } from "./services/api.js";
import {
    clearPending,
    clearSession,
    closeDevicePanel,
    isPending,
    openDevicePanel,
    saveSidebarCollapsed,
    setPending,
    setSession,
    state,
    updateSessionDevice
} from "./state/store.js";
import { createDefaultDeviceName, isMobileViewport } from "./utils/browser.js";
import { toUserMessage } from "./utils/format.js";

const appRoot = document.getElementById("app");
// 成功类提示统一走短时 toast，避免页面里同时出现多套反馈。
let toastTimerID = 0;
let preservePageErrorOnNextRouteChange = false;

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

        if (form.id === "auth-form") {
            event.preventDefault();
            void handleAuthSubmit(form);
            return;
        }

        if (form.id === "device-edit-form") {
            event.preventDefault();
            void handleDeviceEditSubmit(form);
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
                // 手机端点完菜单项后，立即把抽屉收起来，避免内容区被遮住。
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
            case "force-device-offline":
                handleForceDeviceOffline(target.getAttribute("data-device-id") || "");
                break;
            case "logout":
                void handleLogout();
                break;
            default:
                break;
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

    if (PROTECTED_ROUTES.has(state.route) && !state.session) {
        state.isBootstrapping = false;
        render();
        navigate(AUTH_ROUTE);
        return;
    }

    if (state.session) {
        const authenticated = await ensureAuthenticated({ silent: state.route === AUTH_ROUTE });
        if (!authenticated) {
            return;
        }

        if (state.route === AUTH_ROUTE) {
            navigate(DEFAULT_ROUTE);
            return;
        }

        if (state.route === "devices") {
            await loadDevices({ silent: true });
        }
    } else if (state.route !== AUTH_ROUTE && !PROTECTED_ROUTES.has(state.route)) {
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
        // 退出登录时即使远端失败，也要把本地会话清掉，避免卡在半登录状态。
        console.warn("logout request failed", error);
    } finally {
        clearSession();
        state.profile = null;
        state.devices = [];
        clearPending();
        showToast("已退出登录");
        navigate(AUTH_ROUTE);
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
        clearSession();
        state.profile = null;
        state.devices = [];
        state.pageError = "登录已失效，请重新登录。";
        state.isBootstrapping = false;
        render();
        navigate(AUTH_ROUTE, { preserveError: true });
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

function showToast(message) {
    state.pageMessage = message;
    scheduleToastDismiss();
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

    // 成功类提醒保持短暂可见，避免影响主流程。
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
