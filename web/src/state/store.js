import { STORAGE_KEYS, AUTH_ROUTE } from "../config/app.js";
import { detectDefaultServerBaseUrl } from "../utils/browser.js";

export const state = {
    route: AUTH_ROUTE,
    serverBaseUrl: detectDefaultServerBaseUrl(),
    session: loadSession(),
    sidebarCollapsed: loadSidebarCollapsed(),
    mobileSidebarOpen: false,
    authForm: {
        username: "",
        password: ""
    },
    profile: null,
    devices: [],
    clipboard: createInitialClipboardState(),
    clipboardPanel: {
        mode: "",
        itemId: ""
    },
    settingsModal: createInitialSettingsModalState(),
    devicePanel: {
        mode: "",
        deviceId: "",
        draftName: ""
    },
    // pageMessage 只用于短时 toast，页面级错误统一走 pageError。
    pageMessage: null,
    pageError: null,
    pendingKey: "",
    isBootstrapping: true
};

export function setSession(session) {
    state.session = session;
    state.profile = null;
    state.clipboard = createInitialClipboardState();
    closeClipboardPanel();
    closeSettingsModal();
    localStorage.setItem(STORAGE_KEYS.session, JSON.stringify(session));
}

export function updateSessionTokens(tokens) {
	if (!state.session) {
		return;
	}

    state.session = {
        ...state.session,
        tokens
	};
	localStorage.setItem(STORAGE_KEYS.session, JSON.stringify(state.session));
}

export function updateSessionDevice(device) {
	if (!state.session) {
		return;
	}

	state.session = {
		...state.session,
		device: {
			...state.session.device,
			...device
		}
	};
	localStorage.setItem(STORAGE_KEYS.session, JSON.stringify(state.session));
}

export function updateSessionUser(user) {
    if (!state.session) {
        return;
    }

    state.session = {
        ...state.session,
        user: {
            ...state.session.user,
            ...user
        }
    };
    localStorage.setItem(STORAGE_KEYS.session, JSON.stringify(state.session));
}

export function clearSession() {
    state.session = null;
    state.clipboard = createInitialClipboardState();
    closeClipboardPanel();
    closeSettingsModal();
    localStorage.removeItem(STORAGE_KEYS.session);
}

export function saveSidebarCollapsed(value) {
    localStorage.setItem(STORAGE_KEYS.sidebarCollapsed, value ? "1" : "0");
}

export function isPending(key) {
    return state.pendingKey === key;
}

export function setPending(key) {
    state.pendingKey = key;
}

export function clearPending() {
    state.pendingKey = "";
}

export function openDevicePanel(mode, device) {
    state.devicePanel = {
        mode,
        deviceId: device?.id || "",
        draftName: device?.device_name || ""
    };
}

export function closeDevicePanel() {
    state.devicePanel = {
        mode: "",
        deviceId: "",
        draftName: ""
    };
}

export function openClipboardPanel(mode, item) {
    state.clipboardPanel = {
        mode,
        itemId: item?.id || ""
    };
}

export function closeClipboardPanel() {
    state.clipboardPanel = {
        mode: "",
        itemId: ""
    };
}

export function openSettingsModal(category = "general") {
    state.settingsModal = {
        ...state.settingsModal,
        isOpen: true,
        activeCategory: category || state.settingsModal.activeCategory || "general"
    };
}

export function selectSettingsCategory(category) {
    state.settingsModal = {
        ...state.settingsModal,
        activeCategory: category || "general"
    };
}

export function updateSettingsPasswordForm(fields) {
    state.settingsModal = {
        ...state.settingsModal,
        passwordForm: {
            ...state.settingsModal.passwordForm,
            ...fields
        }
    };
}

export function clearSettingsPasswordForm() {
    state.settingsModal = {
        ...state.settingsModal,
        passwordForm: createEmptyPasswordForm()
    };
}

export function closeSettingsModal() {
    state.settingsModal = {
        ...state.settingsModal,
        isOpen: false,
        passwordForm: createEmptyPasswordForm()
    };
}

function loadSession() {
    const rawValue = localStorage.getItem(STORAGE_KEYS.session);
    if (!rawValue) {
        return null;
    }

    try {
        return JSON.parse(rawValue);
    } catch (error) {
        console.warn("parse session failed", error);
        localStorage.removeItem(STORAGE_KEYS.session);
        return null;
    }
}

function loadSidebarCollapsed() {
    return localStorage.getItem(STORAGE_KEYS.sidebarCollapsed) === "1";
}

function createInitialClipboardState() {
    return {
        draftText: "",
        items: [],
        historyLimit: 20,
        historyPageIndex: 0,
        historyCursors: [null],
        historyHasMore: false,
        latestSeq: 0,
        lastAckSeq: 0,
        pendingAckSeq: 0,
        wsStatus: "disconnected",
        wsLastEventAt: "",
        wsLastHeartbeatAt: "",
        wsReconnectAttempt: 0
    };
}

function createInitialSettingsModalState() {
    return {
        isOpen: false,
        activeCategory: "general",
        passwordForm: createEmptyPasswordForm()
    };
}

function createEmptyPasswordForm() {
    return {
        currentPassword: "",
        newPassword: "",
        confirmPassword: ""
    };
}
