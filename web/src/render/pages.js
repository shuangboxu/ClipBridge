import { NAV_ITEMS } from "../config/app.js";
import { state, isPending } from "../state/store.js";
import { escapeHTML, formatDateTime } from "../utils/format.js";
import { renderDataRow, renderModuleTile } from "./common.js";
import { renderIcon } from "./icons.js";

export function renderCurrentPage(route) {
    switch (route) {
        case "dashboard":
            return renderDashboardPage();
        case "history":
            return renderPlaceholderPage("历史记录");
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
        case "settings":
            return renderSettingsPage();
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

function renderSettingsPage() {
    const tokens = state.session?.tokens || {};

    return `
        <section class="card-grid">
            <article class="card">
                <div class="card-header">
                    <h2>当前会话</h2>
                    <span class="badge badge-primary">会话</span>
                </div>

                <div class="data-list">
                    ${renderDataRow("当前设备", state.session?.device?.device_name)}
                    ${renderDataRow("平台", state.session?.device?.platform || "web")}
                    ${renderDataRow("当前设备 ID", state.profile?.current_device_id || state.session?.device?.id, true)}
                </div>
            </article>

            <article class="card">
                <div class="card-header">
                    <h2>令牌</h2>
                </div>

                <div class="data-list">
                    ${renderDataRow("Access Token 过期", formatDateTime(tokens.access_token_expires_at))}
                    ${renderDataRow("Refresh Token 过期", formatDateTime(tokens.refresh_token_expires_at))}
                    ${renderDataRow("当前设备 ID", state.profile?.current_device_id || state.session?.device?.id, true)}
                </div>

                <div class="actions">
                    <button type="button" class="button-danger" data-action="logout" ${isPending("logout") ? "disabled" : ""}>
                        ${isPending("logout") ? "正在退出..." : "退出登录"}
                    </button>
                </div>
            </article>
        </section>

        <section class="card">
            <div class="card-header">
                <h3>规则</h3>
            </div>

            <div class="data-list">
                ${renderDataRow("Access Token 失效", "先尝试 refresh，失败则回登录页")}
                ${renderDataRow("服务地址", "当前 Web 固定使用本站后端，不提供手动切换")}
                ${renderDataRow("退出登录", "调用 /v1/auth/logout 后清理本地 token")}
            </div>
        </section>
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
            <div class="device-stats-grid">
                <div class="device-stat">
                    <span>总设备</span>
                    <strong>${summary.total}</strong>
                </div>
                <div class="device-stat">
                    <span>在线</span>
                    <strong>${summary.online}</strong>
                </div>
                <div class="device-stat">
                    <span>已下线</span>
                    <strong>${summary.offline}</strong>
                </div>
                <div class="device-stat">
                    <span>当前设备</span>
                    <strong>${summary.current}</strong>
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
    const feedback = panelState.feedback
        ? `<p class="device-panel-note">${escapeHTML(panelState.feedback)}</p>`
        : "";

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

            ${feedback}

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
    const currentDeviceID = state.profile?.current_device_id || state.session?.device?.id;
    const total = devices.length;
    const online = devices.filter((device) => device.is_active).length;
    const current = devices.filter((device) => device.id === currentDeviceID).length;
    const web = devices.filter((device) => (device.platform || "").toLowerCase() === "web").length;
    const latestLastSeen = [...devices]
        .sort((left, right) => new Date(right.last_seen_at || 0).getTime() - new Date(left.last_seen_at || 0).getTime())[0]?.last_seen_at;

    return {
        total,
        online,
        offline: Math.max(total - online, 0),
        current,
        web,
        lastSeenText: latestLastSeen ? formatDateTime(latestLastSeen) : "-"
    };
}
