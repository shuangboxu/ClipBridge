import { NAV_ITEMS } from "../config/app.js";
import { state, isPending } from "../state/store.js";
import { escapeAttribute, escapeHTML, formatDateTime } from "../utils/format.js";
import { renderDataRow, renderErrorMessage, renderModuleTile } from "./common.js";
import { renderIcon } from "./icons.js";

const SETTINGS_CATEGORIES = [
    {
        key: "general",
        title: "常规",
        icon: "settings"
    },
    {
        key: "security",
        title: "安全",
        icon: "security"
    },
    {
        key: "session",
        title: "会话",
        icon: "session"
    }
];

export function renderCurrentPage(route) {
    switch (route) {
        case "dashboard":
            return renderDashboardPage();
        case "history":
            return renderHistoryPage();
        case "devices":
            return renderDevicesPage();
        case "files":
            return renderPlaceholderPage("文件中心");
        case "shares":
            return renderPlaceholderPage("分享管理");
        case "requests":
            return renderPlaceholderPage("申请记录");
        case "admin":
            return renderPlaceholderPage("管理员");
        case "ai":
            return renderPlaceholderPage("AI 工具");
        default:
            return renderDashboardPage();
    }
}

function renderDashboardPage() {
    const user = state.profile?.user || state.session?.user || {};
    const currentDevice = state.session?.device || {};

    return `
        <section class="card-grid">
            <article class="card">
                <div class="card-header">
                    <div>
                        <h3>当前账号</h3>
                    </div>
                    <span class="badge badge-primary">已登录</span>
                </div>

                <div class="data-list">
                    ${renderDataRow("用户 ID", user.id, true)}
                    ${renderDataRow("用户名", user.username)}
                    ${renderDataRow("创建时间", formatDateTime(user.created_at))}
                    ${renderDataRow("更新时间", formatDateTime(user.updated_at))}
                </div>
            </article>

            <article class="card">
                <div class="card-header">
                    <div>
                        <h3>当前设备</h3>
                    </div>
                    <span class="badge badge-accent">当前设备</span>
                </div>

                <div class="data-list">
                    ${renderDataRow("设备 ID", currentDevice.id, true)}
                    ${renderDataRow("平台", currentDevice.platform || "web")}
                    ${renderDataRow("设备名", currentDevice.device_name)}
                    ${renderDataRow("最近在线", formatDateTime(currentDevice.last_seen_at))}
                </div>
            </article>
        </section>

        <section class="card">
            <div class="card-header">
                <h3>功能入口</h3>
            </div>

            <div class="module-grid">
                ${NAV_ITEMS.filter((item) => item.route !== "dashboard").map((item) => renderModuleTile(item)).join("")}
            </div>
        </section>
    `;
}

function renderHistoryPage() {
    const clipboard = state.clipboard;
    const wsMeta = buildWSMeta();
    const pendingAckCount = Math.max(clipboard.pendingAckSeq - clipboard.lastAckSeq, 0);
    const unseenCount = Math.max(clipboard.latestSeq - Math.max(clipboard.lastAckSeq, clipboard.pendingAckSeq), 0);

    return `
        ${renderHistoryOverview(wsMeta, pendingAckCount, unseenCount)}

        <section class="card device-list-shell sync-history-card">
            <div class="device-list-toolbar">
                <p>第 ${clipboard.historyPageIndex + 1} 页 · 每页 ${clipboard.historyLimit} 条</p>
                <div class="history-toolbar-actions">
                    <button type="button" class="button-secondary button-compact" data-action="reload-history" ${isPending("clipboard-history") ? "disabled" : ""}>
                        ${isPending("clipboard-history") ? "正在刷新..." : "刷新"}
                    </button>
                    <button
                        type="button"
                        class="button-ghost"
                        data-action="history-prev"
                        ${clipboard.historyPageIndex <= 0 || isPending("clipboard-history") ? "disabled" : ""}
                    >
                        上一页
                    </button>
                    <button
                        type="button"
                        class="button-ghost"
                        data-action="history-next"
                        ${!clipboard.historyHasMore || isPending("clipboard-history") ? "disabled" : ""}
                    >
                        下一页
                    </button>
                </div>
            </div>

            ${renderClipboardList()}
        </section>

        ${renderClipboardPanel()}
    `;
}

function renderHistoryOverview(wsMeta, pendingAckCount, unseenCount) {
    return `
        <section class="card device-stats-card sync-summary-card">
            <div class="sync-summary-shell">
                <div class="device-stats-grid sync-summary-grid">
                    <div class="device-stat">
                        <span>服务端最新序号</span>
                        <strong>${state.clipboard.latestSeq}</strong>
                    </div>
                    <div class="device-stat">
                        <span>当前设备 ACK</span>
                        <strong>${state.clipboard.lastAckSeq}</strong>
                    </div>
                    <div class="device-stat">
                        <span>待提交 ACK</span>
                        <strong>${pendingAckCount}</strong>
                    </div>
                    <div class="device-stat">
                        <span>待补拉事件</span>
                        <strong>${unseenCount}</strong>
                    </div>
                    <div class="device-stat">
                        <span>实时链路</span>
                        <strong class="sync-summary-text">${wsMeta.label}</strong>
                    </div>
                    <div class="device-stat">
                        <span>重连次数</span>
                        <strong>${state.clipboard.wsReconnectAttempt || 0}</strong>
                    </div>
                </div>

                <button
                    type="button"
                    class="icon-button history-upload-button"
                    data-action="open-clipboard-upload"
                    aria-label="手动上传文本"
                    title="手动上传"
                >
                    ${renderIcon("upload")}
                </button>
            </div>
        </section>
    `;
}

function renderClipboardList() {
    const items = Array.isArray(state.clipboard.items) ? state.clipboard.items : [];
    if (!items.length) {
        return `
            <div class="empty-state compact-empty-state">
                <h3>暂无历史记录</h3>
            </div>
        `;
    }

    return `
        <div class="device-compact-list clipboard-compact-list">
            ${items.map((item) => renderClipboardItem(item)).join("")}
        </div>
    `;
}

function renderClipboardItem(item) {
    const previewText = buildClipboardPreview(item.text_content);
    const isActive = state.clipboardPanel.mode === "details" && state.clipboardPanel.itemId === item.id;

    return `
        <article class="device-compact-item clipboard-compact-item ${isActive ? "is-active" : ""}">
            <div class="device-compact-main clipboard-compact-main">
                <button
                    type="button"
                    class="device-compact-copy clipboard-compact-copy clipboard-compact-trigger"
                    data-action="open-clipboard-details"
                    data-item-id="${escapeHTML(item.id)}"
                    aria-label="在右侧查看完整文本"
                    aria-pressed="${isActive ? "true" : "false"}"
                >
                    <strong class="clipboard-compact-preview">${escapeHTML(previewText)}</strong>
                    <div class="device-compact-status-row clipboard-compact-meta-row">
                        <span class="clipboard-item-seq">SEQ #${item.seq}</span>
                        <span class="device-compact-status">${formatDateTime(item.created_at)}</span>
                    </div>
                </button>

                <div class="device-compact-actions">
                    <!-- 列表按钮只负责复制；查看详情改成点击整条内容区域。 -->
                    <button
                        type="button"
                        class="icon-button"
                        data-action="copy-clipboard-item"
                        data-item-id="${escapeHTML(item.id)}"
                        aria-label="复制文本"
                        title="复制文本"
                    >
                        ${renderIcon("copy")}
                    </button>
                </div>
            </div>
        </article>
    `;
}

function renderPlaceholderPage(title) {
    return `
        <section class="card placeholder-card">
            <div class="placeholder-inner">
                <h2>${title}</h2>
                <span class="badge badge-warning">开发中</span>
            </div>
        </section>
    `;
}

function renderDevicesPage() {
    const summary = buildDeviceSummary();

    if (!state.devices.length) {
        return `
            ${renderDeviceStats(summary)}

            <section class="card">
                <div class="empty-state">
                    <img src="./assets/illustrations/p0/empty-devices.webp" alt="暂无设备插画">
                    <h3>暂无设备</h3>
                    <button type="button" class="button-secondary" data-action="reload-devices" ${isPending("devices") ? "disabled" : ""}>
                        ${isPending("devices") ? "正在刷新..." : "刷新"}
                    </button>
                </div>
            </section>
        `;
    }

    return `
        ${renderDeviceStats(summary)}

        <section class="card device-list-shell">
            <div class="device-list-toolbar">
                <p>共 ${summary.total} 台设备</p>
                <button type="button" class="button-secondary button-compact" data-action="reload-devices" ${isPending("devices") ? "disabled" : ""}>
                    ${isPending("devices") ? "正在刷新..." : "刷新"}
                </button>
            </div>

            <div class="device-compact-list">
                ${state.devices.map((device) => renderDeviceItem(device)).join("")}
            </div>
        </section>

        ${renderDevicePanel()}
    `;
}

function renderDeviceItem(device) {
    const isCurrentDevice = device.id === state.profile?.current_device_id;
    const statusLabel = device.is_active ? "在线" : "已下线";
    const deviceRoleLabel = isCurrentDevice ? "当前设备" : "已登记";

    return `
        <article class="device-compact-item">
            <div class="device-compact-main">
                <div class="device-compact-copy">
                    <strong>${escapeHTML(device.device_name || "unnamed-device")}</strong>
                    <div class="device-compact-status-row">
                        <span class="status-dot ${device.is_active ? "is-online" : "is-offline"}"></span>
                        <span class="device-compact-status">${statusLabel}</span>
                        <span class="device-compact-role">${deviceRoleLabel}</span>
                    </div>
                </div>

                <div class="device-compact-actions">
                    <button
                        type="button"
                        class="icon-button"
                        data-action="open-device-details"
                        data-device-id="${escapeHTML(device.id)}"
                        aria-label="查看设备详情"
                        title="查看详情"
                    >
                        ${renderIcon("view")}
                    </button>
                    <button
                        type="button"
                        class="icon-button"
                        data-action="open-device-editor"
                        data-device-id="${escapeHTML(device.id)}"
                        aria-label="编辑设备"
                        title="编辑"
                    >
                        ${renderIcon("edit")}
                    </button>
                </div>
            </div>
        </article>
    `;
}

function renderDeviceStats(summary) {
    return `
        <section class="card device-stats-card">
            <div class="device-stats-grid device-overview-grid">
                <div class="device-stat">
                    <span>总设备</span>
                    <strong>${summary.total}</strong>
                </div>
                <div class="device-stat">
                    <span>Web 设备</span>
                    <strong>${summary.web}</strong>
                </div>
                <div class="device-stat">
                    <span>最近活动</span>
                    <strong class="device-stat-time">${summary.lastSeenText}</strong>
                </div>
            </div>
        </section>
    `;
}

function renderDevicePanel() {
    const panelState = state.devicePanel;
    if (!panelState.mode || !panelState.deviceId) {
        return "";
    }

    const device = state.devices.find((item) => item.id === panelState.deviceId);
    if (!device) {
        return "";
    }

    return panelState.mode === "edit"
        ? renderDeviceEditPanel(device, panelState)
        : renderDeviceDetailPanel(device);
}

function renderClipboardPanel() {
    const panelState = state.clipboardPanel;
    if (!panelState.mode) {
        return "";
    }

    return panelState.mode === "upload"
        ? renderClipboardUploadPanel()
        : renderClipboardDetailPanel(panelState.itemId);
}

function renderClipboardUploadPanel() {
    return `
        <div class="device-panel-backdrop" data-action="close-clipboard-panel"></div>
        <aside class="device-panel clipboard-panel">
            <div class="device-panel-header">
                <div>
                    <h2>手动上传</h2>
                </div>
                <button type="button" class="icon-button" data-action="close-clipboard-panel" aria-label="关闭上传面板">
                    ${renderIcon("close")}
                </button>
            </div>

            <form id="clipboard-upload-form" class="form-grid">
                <div class="field">
                    <label for="clipboard-text">文本内容</label>
                    <textarea
                        id="clipboard-text"
                        name="text_content"
                        rows="8"
                        maxlength="65535"
                        placeholder="输入要上传的文本"
                        required>${escapeHTML(state.clipboard.draftText || "")}</textarea>
                </div>

                <div class="device-panel-actions">
                    <button type="submit" class="button-primary" ${isPending("clipboard-upload") ? "disabled" : ""}>
                        ${isPending("clipboard-upload") ? "正在上传..." : "上传文本"}
                    </button>
                    <button type="button" class="button-secondary" data-action="read-system-clipboard" ${isPending("clipboard-read") ? "disabled" : ""}>
                        ${isPending("clipboard-read") ? "正在读取..." : "读取剪切板"}
                    </button>
                </div>
            </form>
        </aside>
    `;
}

function renderClipboardDetailPanel(itemID) {
    const item = state.clipboard.items.find((nextItem) => nextItem.id === itemID);
    if (!item) {
        return "";
    }

    return `
        <div class="device-panel-backdrop" data-action="close-clipboard-panel"></div>
        <aside class="device-panel clipboard-panel">
            <div class="device-panel-header">
                <div>
                    <h2>SEQ #${item.seq}</h2>
                    <p>${escapeHTML(formatDateTime(item.created_at))}</p>
                </div>
                <button type="button" class="icon-button" data-action="close-clipboard-panel" aria-label="关闭详情">
                    ${renderIcon("close")}
                </button>
            </div>

            <div class="clipboard-item-badges">
                <span class="badge badge-primary">${item.is_current_device_origin ? "本机" : "远端"}</span>
            </div>

            <!-- 详情面板展示完整文本内容，长文本也允许在面板里自动换行。 -->
            <pre class="clipboard-item-text clipboard-panel-text">${escapeHTML(item.text_content || "")}</pre>

            <div class="data-list">
                ${renderDataRow("来源设备", item.origin_device_id, true)}
                ${renderDataRow("内容哈希", formatHash(item.content_hash), true)}
                ${renderDataRow("创建时间", formatDateTime(item.created_at))}
            </div>

            <div class="device-panel-actions">
                <button type="button" class="button-primary" data-action="copy-clipboard-item" data-item-id="${escapeHTML(item.id)}">
                    复制文本
                </button>
            </div>
        </aside>
    `;
}

function renderDeviceDetailPanel(device) {
    return `
        <div class="device-panel-backdrop" data-action="close-device-panel"></div>
        <aside class="device-panel">
            <div class="device-panel-header">
                <div>
                    <h2>${escapeHTML(device.device_name || "unnamed-device")}</h2>
                    <p>设备详情</p>
                </div>
                <button type="button" class="icon-button" data-action="close-device-panel" aria-label="关闭详情">
                    ${renderIcon("close")}
                </button>
            </div>

            <div class="data-list">
                ${renderDataRow("设备 ID", device.id, true)}
                ${renderDataRow("平台", device.platform || "unknown")}
                ${renderDataRow("状态", device.is_active ? "在线" : "已下线")}
                ${renderDataRow("创建时间", formatDateTime(device.created_at))}
                ${renderDataRow("最近在线", formatDateTime(device.last_seen_at))}
            </div>
        </aside>
    `;
}

function renderDeviceEditPanel(device, panelState) {
    return `
        <div class="device-panel-backdrop" data-action="close-device-panel"></div>
        <aside class="device-panel">
            <div class="device-panel-header">
                <div>
                    <h2>${escapeHTML(device.device_name || "unnamed-device")}</h2>
                    <p>编辑设备</p>
                </div>
                <button type="button" class="icon-button" data-action="close-device-panel" aria-label="关闭编辑">
                    ${renderIcon("close")}
                </button>
            </div>

            <form id="device-edit-form" class="form-grid">
                <div class="field">
                    <label for="device-name">设备名称</label>
                    <input
                        id="device-name"
                        name="device_name"
                        type="text"
                        minlength="1"
                        maxlength="128"
                        value="${escapeHTML(panelState.draftName || device.device_name || "")}"
                        required
                    >
                </div>

                <div class="device-panel-actions">
                    <button type="submit" class="button-primary" ${isPending("device-rename") || isPending("device-offline") ? "disabled" : ""}>
                        ${isPending("device-rename") ? "正在保存..." : "保存名称"}
                    </button>
                    <button
                        type="button"
                        class="button-danger"
                        data-action="force-device-offline"
                        data-device-id="${escapeHTML(device.id)}"
                        ${isPending("device-rename") || isPending("device-offline") ? "disabled" : ""}
                    >
                        ${isPending("device-offline") ? "正在下线..." : "强制下线"}
                    </button>
                </div>
            </form>

            <div class="data-list">
                ${renderDataRow("设备 ID", device.id, true)}
                ${renderDataRow("平台", device.platform || "unknown")}
                ${renderDataRow("状态", device.is_active ? "在线" : "已下线")}
                ${renderDataRow("最近在线", formatDateTime(device.last_seen_at))}
            </div>
        </aside>
    `;
}

function buildDeviceSummary() {
    const devices = Array.isArray(state.devices) ? state.devices : [];
    const total = devices.length;
    const web = devices.filter((device) => (device.platform || "").toLowerCase() === "web").length;
    const latestLastSeen = [...devices]
        .sort((left, right) => new Date(right.last_seen_at || 0).getTime() - new Date(left.last_seen_at || 0).getTime())[0]?.last_seen_at;

    return {
        total,
        web,
        lastSeenText: latestLastSeen ? formatDateTime(latestLastSeen) : "-"
    };
}

export function renderSettingsModal() {
    if (!state.session || !state.settingsModal.isOpen) {
        return "";
    }

    const activeCategory = SETTINGS_CATEGORIES.find((item) => item.key === state.settingsModal.activeCategory) || SETTINGS_CATEGORIES[0];

    return `
        <div class="settings-modal-backdrop" data-action="close-settings"></div>

        <section class="settings-modal" role="dialog" aria-modal="true" aria-labelledby="settings-modal-title">
            <div class="settings-modal-body">
                <aside class="settings-modal-nav" aria-label="设置分类">
                    <div class="settings-modal-nav-top">
                        <button
                            type="button"
                            class="settings-modal-close"
                            data-action="close-settings"
                            aria-label="关闭设置窗口"
                        >
                            ${renderIcon("close")}
                        </button>
                    </div>

                    ${SETTINGS_CATEGORIES.map((item) => renderSettingsCategoryButton(item, activeCategory.key)).join("")}
                </aside>

                <div class="settings-modal-content">
                    <div class="settings-pane">
                        <div class="settings-pane-title-row">
                            <h2 id="settings-modal-title">${activeCategory.title}</h2>
                        </div>
                        ${renderErrorMessage(state.pageError)}
                        ${renderSettingsCategoryContent(activeCategory.key)}
                    </div>
                </div>
            </div>
        </section>
    `;
}

function renderSettingsCategoryButton(item, activeCategory) {
    return `
        <button
            type="button"
            class="settings-category-button ${item.key === activeCategory ? "is-active" : ""}"
            data-action="select-settings-category"
            data-category="${item.key}"
        >
            <span class="settings-category-icon">${renderIcon(item.icon)}</span>
            <span class="settings-category-copy">
                <strong>${item.title}</strong>
            </span>
        </button>
    `;
}

function renderSettingsCategoryContent(categoryKey) {
    switch (categoryKey) {
        case "security":
            return renderSecuritySettings();
        case "session":
            return renderSessionSettings();
        case "general":
        default:
            return renderGeneralSettings();
    }
}

function renderGeneralSettings() {
    const user = state.profile?.user || state.session?.user || {};
    const currentDevice = state.session?.device || {};

    return `
        <section class="settings-pane-section">
            <div class="settings-compact-list">
                ${renderSettingsCompactRow("用户名", user.username)}
                ${renderSettingsCompactRow("当前设备", currentDevice.device_name)}
                ${renderSettingsCompactRow("平台", currentDevice.platform || "web")}
            </div>
        </section>
    `;
}

function renderSecuritySettings() {
    const form = state.settingsModal.passwordForm;

    return `
        <section class="settings-pane-section">
            <div class="settings-section-label">修改密码</div>

            <form id="password-change-form" class="settings-password-form">
                <div class="field">
                    <label for="current-password">当前密码</label>
                    <input
                        id="current-password"
                        name="current_password"
                        type="password"
                        minlength="8"
                        maxlength="128"
                        autocomplete="current-password"
                        value="${escapeAttribute(form.currentPassword)}"
                        required
                    >
                </div>

                <div class="field">
                    <label for="new-password">新密码</label>
                    <input
                        id="new-password"
                        name="new_password"
                        type="password"
                        minlength="8"
                        maxlength="128"
                        autocomplete="new-password"
                        value="${escapeAttribute(form.newPassword)}"
                        required
                    >
                </div>

                <div class="field">
                    <label for="confirm-password">确认新密码</label>
                    <input
                        id="confirm-password"
                        name="confirm_password"
                        type="password"
                        minlength="8"
                        maxlength="128"
                        autocomplete="new-password"
                        value="${escapeAttribute(form.confirmPassword)}"
                        required
                    >
                </div>

                <div class="settings-submit-row">
                    <button type="submit" class="button-primary" ${isPending("change-password") ? "disabled" : ""}>
                        ${isPending("change-password") ? "正在修改..." : "更新密码"}
                    </button>
                </div>
            </form>
        </section>
    `;
}

function renderSessionSettings() {
    return `
        <section class="settings-pane-section">
            <div class="settings-action-row">
                <div class="settings-action-copy">
                    <strong>退出登录</strong>
                    <span>退出当前浏览器账号</span>
                </div>

                <button type="button" class="button-danger" data-action="logout" ${isPending("logout") ? "disabled" : ""}>
                    ${isPending("logout") ? "正在退出..." : "退出登录"}
                </button>
            </div>
        </section>
    `;
}

function renderSettingsCompactRow(title, value, isMono = false) {
    return `
        <div class="settings-compact-row">
            <div class="settings-compact-label">
                <strong>${escapeHTML(title)}</strong>
            </div>

            <div class="settings-compact-value ${isMono ? "is-mono" : ""}">
                ${escapeHTML(value || "-")}
            </div>
        </div>
    `;
}

function buildAckMeta(seq) {
    if (seq <= state.clipboard.lastAckSeq) {
        return { label: "已 ACK", badgeClass: "badge-accent" };
    }
    if (seq <= state.clipboard.pendingAckSeq) {
        return { label: "待 ACK", badgeClass: "badge-warning" };
    }
    return { label: "仅历史", badgeClass: "badge-primary" };
}

function buildWSMeta() {
    switch (state.clipboard.wsStatus) {
        case "connected":
            return { label: "实时已连接", badgeClass: "badge-accent" };
        case "connecting":
            return { label: "正在连接", badgeClass: "badge-warning" };
        case "reconnecting":
            return { label: "重连中", badgeClass: "badge-warning" };
        default:
            return { label: "实时未连接", badgeClass: "badge-primary" };
    }
}

function buildClipboardPreview(value) {
    const text = String(value || "").replace(/\s+/g, " ").trim();
    if (!text) {
        return "(空文本)";
    }
    return text;
}

function formatHash(value) {
    const text = String(value || "");
    if (text.length <= 18) {
        return text || "-";
    }
    return `${text.slice(0, 10)}...${text.slice(-6)}`;
}
