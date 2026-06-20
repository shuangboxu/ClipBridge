import { STORAGE_KEYS, AUTH_ROUTE } from "../config/app.js";
import { detectDefaultServerBaseUrl } from "../utils/browser.js";

export const state = {
    route: AUTH_ROUTE,
    serverBaseUrl: detectDefaultServerBaseUrl(),
    session: loadSession(),
    sidebarCollapsed: loadSidebarCollapsed(),
    mobileSidebarOpen: false,
    authForm: createInitialAuthForm(),
    authRegistrationPolicy: createInitialAuthRegistrationPolicy(),
    profile: null,
    devices: [],
    files: createInitialFilesState(),
    shareRules: loadShareRules(),
    shares: createInitialSharesState(),
    requests: createInitialRequestsState(),
    admin: createInitialAdminState(),
    clipboard: createInitialClipboardState(),
    publicShare: createInitialPublicShareState(),
    clipboardPanel: {
        mode: "",
        itemId: ""
    },
    filePanel: createInitialFilePanelState(),
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
    state.authRegistrationPolicy = createInitialAuthRegistrationPolicy();
    state.profile = null;
    state.files = createInitialFilesState();
    closeFilePanel();
    state.shares = createInitialSharesState();
    state.requests = createInitialRequestsState();
    state.admin = createInitialAdminState();
    state.clipboard = createInitialClipboardState();
    closeClipboardPanel();
    state.publicShare = createInitialPublicShareState();
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
    state.authRegistrationPolicy = createInitialAuthRegistrationPolicy();
    state.files = createInitialFilesState();
    closeFilePanel();
    state.shares = createInitialSharesState();
    state.requests = createInitialRequestsState();
    state.admin = createInitialAdminState();
    state.clipboard = createInitialClipboardState();
    closeClipboardPanel();
    state.publicShare = createInitialPublicShareState();
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

export function openFilePanel(mode, file) {
    state.filePanel = {
        mode,
        fileId: file?.id || "",
        renameDraftName: file?.original_name || ""
    };
}

export function openSharePanel() {
    state.shares = {
        ...state.shares,
        panelOpen: true
    };
}

export function closeSharePanel() {
    state.shares = {
        ...state.shares,
        panelOpen: false,
        dragActive: false
    };
}

export function openShareQRCodeDialog(shareToken) {
    const normalizedToken = String(shareToken || "").trim();
    if (!normalizedToken) {
        return;
    }

    state.shares = {
        ...state.shares,
        qrCodeDialogToken: normalizedToken
    };
}

export function closeShareQRCodeDialog() {
    state.shares = {
        ...state.shares,
        qrCodeDialogToken: ""
    };
}

export function selectShareStrategy(strategyKey) {
    state.shares = {
        ...state.shares,
        strategyKey
    };
}

export function updateShareRules(ruleKey, fields) {
    if (!ruleKey || !state.shareRules?.[ruleKey]) {
        return;
    }

    state.shareRules = {
        ...state.shareRules,
        [ruleKey]: {
            ...state.shareRules[ruleKey],
            ...fields
        }
    };
    localStorage.setItem(STORAGE_KEYS.shareRules, JSON.stringify(state.shareRules));
}

export function toggleShareRulePanel(ruleKey) {
    if (!ruleKey) {
        return;
    }

    const currentPanels = state.settingsModal.shareRulePanels || createDefaultShareRulePanels();
    state.settingsModal = {
        ...state.settingsModal,
        shareRulePanels: {
            ...currentPanels,
            [ruleKey]: !currentPanels[ruleKey]
        }
    };
}

export function setAdminPanelOpen(panelKey, isOpen) {
    if (!panelKey || !Object.prototype.hasOwnProperty.call(state.admin.panels || {}, panelKey)) {
        return;
    }

    // 管理页会频繁整页重绘，这里单独记住折叠状态，避免用户展开后又被自动收起。
    state.admin = {
        ...state.admin,
        panels: {
            ...state.admin.panels,
            [panelKey]: Boolean(isOpen)
        }
    };
}

export function closeClipboardPanel() {
    state.clipboardPanel = {
        mode: "",
        itemId: ""
    };
}

export function closeFilePanel() {
    state.filePanel = createInitialFilePanelState();
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
        passwordForm: createEmptyPasswordForm(),
        shareRulePanels: createDefaultShareRulePanels()
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

function createInitialAuthForm() {
    return {
        username: "",
        password: "",
        confirmPassword: ""
    };
}

function createInitialAuthRegistrationPolicy() {
    return {
        allowRegistration: null
    };
}

function createInitialClipboardState() {
    return {
        draftText: "",
        items: [],
        historyLimit: 20,
        historyPageIndex: 0,
        historyCursors: [null],
        historyHasMore: false,
        retentionDays: 0,
        maxStoredItems: 1000,
        cleanupDaysDraft: "30",
        settingsUpdatedAt: "",
        latestSeq: 0,
        lastAckSeq: 0,
        pendingAckSeq: 0,
        wsStatus: "disconnected",
        wsLastEventAt: "",
        wsLastHeartbeatAt: "",
        wsReconnectAttempt: 0
    };
}

function createInitialFilesState() {
    return {
        selectedUploadName: "",
        items: [],
        page: 1,
        pageSize: 20,
        total: 0,
        totalPages: 0,
        totalBytes: 0,
        maxUploadBytes: 0
    };
}

function createInitialSharesState() {
    return {
        textDraft: "",
        selectedFiles: [],
        dragActive: false,
        panelOpen: false,
        qrCodeDialogToken: "",
        strategyKey: "expire",
        password: "",
        items: [],
        page: 1,
        pageSize: 20,
        total: 0,
        totalPages: 0,
        status: "all",
        maxUploadBytes: 0,
        latestShareToken: ""
    };
}

function createInitialRequestsState() {
    return {
        quotaForm: {
            requestedQuotaMB: "",
            reason: ""
        },
        bandwidthForm: {
            // 中文注释：这里虽然沿用旧字段名，但表单里实际保存的是 MB/s 文本，提交前会再换回接口字段。
            requestedUploadKbps: "",
            requestedDownloadKbps: "",
            reason: ""
        },
        adminForm: {
            reason: ""
        },
        quotaRequests: [],
        bandwidthRequests: [],
        adminRequests: []
    };
}

function createInitialAdminState() {
    return {
        settings: null,
        currentUserCount: 0,
        panels: createDefaultAdminPanels(),
        settingsForm: {
            maxUserCount: "",
            defaultStorageQuotaMB: "",
            // 中文注释：这里继续保留旧键名，避免影响其它逻辑；值本身已经统一改成 MB/s。
            defaultUploadBandwidthKbps: "",
            defaultDownloadBandwidthKbps: "",
            maxUserUploadBandwidthKbps: "",
            maxUserDownloadBandwidthKbps: "",
            maxUploadFileMB: "",
            allowRegistration: false
        },
        users: [],
        userDrafts: {},
        quotaRequests: [],
        bandwidthRequests: [],
        adminRequests: []
    };
}

function createDefaultAdminPanels() {
    return {
        settings: false,
        users: false,
        reviews: false
    };
}

function createInitialPublicShareState() {
    return {
        token: "",
        meta: null,
        password: "",
        accessToken: "",
        accessTokenExpiresAt: "",
        textContent: "",
        contentOpen: false,
        filePreviews: {},
        previewLoadingMap: {},
        previewErrorMap: {},
        countdownDisplayDeadlineAt: 0
    };
}

function createInitialFilePanelState() {
    return {
        mode: "",
        fileId: "",
        renameDraftName: ""
    };
}

function createInitialSettingsModalState() {
    return {
        isOpen: false,
        activeCategory: "general",
        passwordForm: createEmptyPasswordForm(),
        shareRulePanels: createDefaultShareRulePanels()
    };
}

function createEmptyPasswordForm() {
    return {
        currentPassword: "",
        newPassword: "",
        confirmPassword: ""
    };
}

function createDefaultShareRulePanels() {
    return {
        never: false,
        expire: false,
        once: false
    };
}

function loadShareRules() {
    const fallback = createDefaultShareRules();
    const rawValue = localStorage.getItem(STORAGE_KEYS.shareRules);
    if (!rawValue) {
        return fallback;
    }

    try {
        const parsed = JSON.parse(rawValue);
        return {
            never: {
                ...fallback.never,
                ...(parsed?.never || {})
            },
            expire: {
                ...fallback.expire,
                ...(parsed?.expire || {})
            },
            once: {
                ...fallback.once,
                ...(parsed?.once || {})
            }
        };
    } catch (error) {
        console.warn("parse share rules failed", error);
        localStorage.removeItem(STORAGE_KEYS.shareRules);
        return fallback;
    }
}

function createDefaultShareRules() {
    return {
        never: {
            key: "never",
            title: "不过期",
            allowCopyText: false
        },
        expire: {
            key: "expire",
            title: "过期",
            expireHours: 24,
            allowCopyText: false
        },
        once: {
            key: "once",
            title: "打开一次失效",
            showCountdown: true,
            countdownSeconds: 10,
            allowCopyText: false
        }
    };
}
