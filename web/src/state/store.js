import { STORAGE_KEYS, AUTH_ROUTE } from "../config/app.js";
import { detectDefaultServerBaseUrl } from "../utils/browser.js";

export const state = {
    route: AUTH_ROUTE,
    authMode: "login",
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
    devicePanel: {
        mode: "",
        deviceId: "",
        draftName: "",
        feedback: ""
    },
    pageMessage: null,
    pageError: null,
    pendingKey: "",
    isBootstrapping: true
};

export function setSession(session) {
    state.session = session;
    state.profile = null;
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

export function clearSession() {
    state.session = null;
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
        draftName: device?.device_name || "",
        feedback: ""
    };
}

export function closeDevicePanel() {
    state.devicePanel = {
        mode: "",
        deviceId: "",
        draftName: "",
        feedback: ""
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
