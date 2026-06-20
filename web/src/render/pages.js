import { NAV_ITEMS } from "../config/app.js";
import { state, isPending } from "../state/store.js";
import {
    bandwidthKbpsToMBpsInput,
    escapeAttribute,
    escapeHTML,
    formatBandwidthMBps,
    formatBytes,
    formatDateTime
} from "../utils/format.js";
import { renderDataRow, renderErrorMessage, renderModuleTile } from "./common.js";
import { renderIcon } from "./icons.js";

const SETTINGS_CATEGORIES = [
    {
        key: "general",
        title: "常规",
        icon: "settings"
    },
    {
        key: "shares",
        title: "分享",
        icon: "shares"
    },
    {
        key: "history",
        title: "历史",
        icon: "history"
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
    },
    {
        key: "about",
        title: "关于",
        icon: "about"
    }
];

export function renderCurrentPage(route) {
    switch (route) {
        case "history":
            return renderHistoryPage();
        case "devices":
            return renderDevicesPage();
        case "files":
            return renderFilesPage();
        case "shares":
            return renderSharesPage();
        case "requests":
            return renderRequestsPage();
        case "admin":
            return renderAdminPage();
        case "ai":
            return renderPlaceholderPage("AI 工具");
        default:
            return renderHistoryPage();
    }
}

export function renderPublicSharePage() {
    const share = state.publicShare.meta;
    const hasOpenContent = state.publicShare.contentOpen;
    const files = Array.isArray(share?.files) ? share.files : [];

    return `
        <section class="public-share-stage">
            <article class="card public-share-card">
                <div class="public-share-card-header">
                    <div>
                        <p class="public-share-kicker">ClipBridge</p>
                        <h1>公开取件</h1>
                    </div>
                </div>

                ${share ? `
                    <div class="public-share-body">
                        ${renderPublicShareStatusNote(share, hasOpenContent)}
                        ${renderPublicShareUnlockBlock(share, hasOpenContent)}

                        ${share.has_text_content && hasOpenContent ? `
                            <section class="public-share-text-card ${share.allow_copy_content ? "" : "is-copy-blocked"}">
                                <div class="public-share-text-card-header">
                                    <strong>分享文字</strong>
                                    ${share.allow_copy_content ? `
                                        <button type="button" class="button-secondary button-compact" data-action="copy-public-share-text">
                                            复制文本
                                        </button>
                                    ` : `
                                        <span class="public-share-muted-note">禁止复制</span>
                                    `}
                                </div>
                                <pre>${escapeHTML(state.publicShare.textContent || "")}</pre>
                            </section>
                        ` : ""}

                        ${files.length && hasOpenContent ? `
                            <section class="public-share-file-list">
                                ${files.map((file) => renderPublicShareFileCard(file, share)).join("")}
                            </section>
                        ` : ""}
                    </div>
                ` : `
                    <div class="empty-state compact-empty-state">
                        <h3>正在读取分享信息</h3>
                    </div>
                `}
            </article>
        </section>
    `;
}

function renderPublicShareStatusNote(share, hasOpenContent) {
    if (!share) {
        return "";
    }

    if (hasOpenContent && (share.burn_mode || "").toLowerCase() === "countdown" && Number(share.remaining_seconds || 0) > 0) {
        return `<div class="public-share-status-note is-warning">倒计时剩余 ${escapeHTML(formatRemainingSeconds(share.remaining_seconds))}</div>`;
    }

    if (hasOpenContent) {
        return "";
    }

    switch ((share.status || "").toLowerCase()) {
        case "expired":
            return `<div class="public-share-status-note is-danger">这个分享已经过期。</div>`;
        case "revoked":
            return `<div class="public-share-status-note is-danger">分享已被创建者撤销。</div>`;
        case "consumed":
            return `<div class="public-share-status-note is-danger">这个分享已经失效，不能再次打开。</div>`;
        default:
            if (share.requires_password) {
                return `<div class="public-share-status-note">输入密码后即可查看文字和文件内容。</div>`;
            }
            return `<div class="public-share-status-note">正在准备分享内容...</div>`;
    }
}

function renderPublicShareUnlockBlock(share, hasOpenContent) {
    if (!share) {
        return "";
    }

    const shareStatus = String(share.status || "").toLowerCase();
    if (shareStatus !== "active" || hasOpenContent) {
        return "";
    }

    if (share.requires_password || share.is_encrypted) {
        return `
            <section class="public-share-unlock-card">
                <div class="field">
                    <label for="public-share-password">分享密码</label>
                    <input
                        id="public-share-password"
                        name="public_share_password"
                        type="password"
                        minlength="4"
                        maxlength="128"
                        value="${escapeAttribute(state.publicShare.password || "")}"
                        placeholder="请输入创建分享时设置的密码"
                    >
                </div>

                <div class="public-share-actions">
                    <button
                        type="button"
                        class="button-primary"
                        data-action="open-public-share"
                        ${isPending("public-share-open") ? "disabled" : ""}
                    >
                        ${isPending("public-share-open") ? "正在解锁..." : "解锁内容"}
                    </button>
                </div>
            </section>
        `;
    }

    return `
        <div class="public-share-actions">
            <button
                type="button"
                class="button-primary"
                data-action="open-public-share"
                ${isPending("public-share-open") ? "disabled" : ""}
            >
                ${isPending("public-share-open") ? "正在打开..." : "重新打开内容"}
            </button>
        </div>
    `;
}

function renderPublicShareFileCard(file, share) {
    const previewURL = resolvePublicSharePreviewURL(file, share);
    const isLoading = Boolean(state.publicShare.previewLoadingMap?.[file.id]);
    const previewError = state.publicShare.previewErrorMap?.[file.id] || "";

    return `
        <article class="public-share-file-card">
            <div class="public-share-file-head">
                <strong>${escapeHTML(file.original_name || "share.bin")}</strong>
                <button
                    type="button"
                    class="button-secondary button-compact"
                    data-action="download-public-share-file"
                    data-file-id="${escapeAttribute(file.id || "")}"
                    ${isPending(`public-share-download:${file.id}`) ? "disabled" : ""}
                >
                    ${isPending(`public-share-download:${file.id}`) ? "正在下载..." : "下载文件"}
                </button>
            </div>

            ${isPreviewablePublicShareFile(file) ? `
                <div class="public-share-preview">
                    ${previewURL ? renderPublicSharePreviewMedia(file, previewURL) : ""}
                    ${!previewURL && isLoading ? `<div class="public-share-preview-note">正在准备预览...</div>` : ""}
                    ${!previewURL && !isLoading && previewError ? `<div class="public-share-preview-note is-danger">${escapeHTML(previewError)}</div>` : ""}
                    ${!previewURL && !isLoading && !previewError ? `<div class="public-share-preview-note">当前文件支持预览，内容准备完成后会显示在这里。</div>` : ""}
                </div>
            ` : `
                <p class="public-share-muted-note">这个文件不支持在线预览，请使用下载按钮获取。</p>
            `}
        </article>
    `;
}

function renderPublicSharePreviewMedia(file, previewURL) {
    if (file.is_video) {
        return `
            <video controls preload="metadata" src="${escapeAttribute(previewURL)}">
                当前浏览器不支持视频预览。
            </video>
        `;
    }

    return `<img src="${escapeAttribute(previewURL)}" alt="${escapeAttribute(file.original_name || "分享图片")}">`;
}

function resolvePublicSharePreviewURL(file, share) {
    if (!isPreviewablePublicShareFile(file) || !state.publicShare.contentOpen) {
        return "";
    }

    if (share?.is_encrypted) {
        return state.publicShare.filePreviews?.[file.id] || "";
    }

    if (!state.publicShare.token || !state.publicShare.accessToken) {
        return "";
    }

    const query = new URLSearchParams({
        access_token: state.publicShare.accessToken
    });
    return `${state.serverBaseUrl}/v1/public/shares/${encodeURIComponent(state.publicShare.token)}/files/${encodeURIComponent(file.id)}?${query.toString()}`;
}

function isPreviewablePublicShareFile(file) {
    return Boolean(file?.is_image || file?.is_video);
}

function renderRequestsPage() {
    const profile = state.profile || {};
    const user = profile.user || state.session?.user || {};
    const limits = profile.limits || {};
    const requestsState = state.requests || {};

    return `
        <section class="card request-overview-card">
            <div class="card-header">
                <div>
                    <h3>当前额度</h3>
                    <p class="muted">申请提交后会进入管理员审核队列；审核通过后，额度与权限会立即生效。</p>
                </div>
                <span class="badge ${user.is_admin ? "badge-accent" : "badge-primary"}">${user.is_admin ? "管理员" : "普通用户"}</span>
            </div>

            <div class="device-stats-grid request-overview-grid">
                <div class="device-stat">
                    <span>存储配额</span>
                    <strong>${formatBytes(user.storage_quota_bytes)}</strong>
                </div>
                <div class="device-stat">
                    <span>已用空间</span>
                    <strong>${formatBytes(profile.storage_used_bytes)}</strong>
                </div>
                <div class="device-stat">
                    <span>剩余空间</span>
                    <strong>${formatBytes(profile.storage_free_bytes)}</strong>
                </div>
                <div class="device-stat">
                    <span>上传带宽</span>
                    <strong>${formatBandwidthMBps(user.upload_bandwidth_kbps)}</strong>
                </div>
                <div class="device-stat">
                    <span>下载带宽</span>
                    <strong>${formatBandwidthMBps(user.download_bandwidth_kbps)}</strong>
                </div>
                <div class="device-stat">
                    <span>系统单文件上限</span>
                    <strong>${formatBytes(limits.max_upload_file_bytes)}</strong>
                </div>
            </div>

        </section>

        <section class="card-grid request-form-grid">
            <article class="card">
                <div class="card-header">
                    <div>
                        <h3>存储配额申请</h3>
                        <p class="muted">只接受大于当前配额的申请，单位为 MB。</p>
                    </div>
                </div>

                <form id="quota-request-form" class="form-grid">
                    <div class="field">
                        <label for="request-quota-mb">目标配额（MB）</label>
                        <input
                            id="request-quota-mb"
                            name="requested_quota_mb"
                            type="number"
                            min="1"
                            step="1"
                            value="${escapeAttribute(requestsState.quotaForm?.requestedQuotaMB || "")}"
                            required
                        >
                    </div>

                    <div class="field">
                        <label for="request-quota-reason">申请说明</label>
                        <textarea
                            id="request-quota-reason"
                            name="reason"
                            rows="4"
                            maxlength="500"
                            placeholder="说明用途、需要的空间规模和时长"
                        >${escapeHTML(requestsState.quotaForm?.reason || "")}</textarea>
                    </div>

                    <div class="actions">
                        <button type="submit" class="button-primary" ${isPending("quota-request-create") ? "disabled" : ""}>
                            ${isPending("quota-request-create") ? "正在提交..." : "提交申请"}
                        </button>
                    </div>
                </form>
            </article>

            <article class="card">
                <div class="card-header">
                    <div>
                        <h3>带宽申请</h3>
                        <p class="muted">上传和下载至少有一项需要大于当前值，单位为 MB/s。</p>
                    </div>
                </div>

                <form id="bandwidth-request-form" class="form-grid">
                    <div class="inline-fields">
                        <div class="field">
                            <label for="request-upload-kbps">上传带宽（MB/s）</label>
                            <input
                                id="request-upload-kbps"
                                name="requested_upload_kbps"
                                type="number"
                                min="0.1"
                                step="0.1"
                                value="${escapeAttribute(requestsState.bandwidthForm?.requestedUploadKbps || "")}"
                                required
                            >
                        </div>

                        <div class="field">
                            <label for="request-download-kbps">下载带宽（MB/s）</label>
                            <input
                                id="request-download-kbps"
                                name="requested_download_kbps"
                                type="number"
                                min="0.1"
                                step="0.1"
                                value="${escapeAttribute(requestsState.bandwidthForm?.requestedDownloadKbps || "")}"
                                required
                            >
                        </div>
                    </div>

                    <div class="field">
                        <label for="request-bandwidth-reason">申请说明</label>
                        <textarea
                            id="request-bandwidth-reason"
                            name="reason"
                            rows="4"
                            maxlength="500"
                            placeholder="说明上传/下载场景和需要提速的原因"
                        >${escapeHTML(requestsState.bandwidthForm?.reason || "")}</textarea>
                    </div>

                    <div class="actions">
                        <button type="submit" class="button-primary" ${isPending("bandwidth-request-create") ? "disabled" : ""}>
                            ${isPending("bandwidth-request-create") ? "正在提交..." : "提交申请"}
                        </button>
                    </div>
                </form>
            </article>

            <article class="card">
                <div class="card-header">
                    <div>
                        <h3>管理员申请</h3>
                        <p class="muted">说明需要管理员能力的原因和预期操作范围。</p>
                    </div>
                </div>

                <form id="admin-request-form" class="form-grid">
                    <div class="field">
                        <label for="request-admin-reason">申请说明</label>
                        <textarea
                            id="request-admin-reason"
                            name="reason"
                            rows="8"
                            maxlength="500"
                            placeholder="例如：需要审批团队成员的容量申请、维护账号权限等"
                            ${user.is_admin ? "disabled" : ""}
                        >${escapeHTML(requestsState.adminForm?.reason || "")}</textarea>
                    </div>

                    <div class="actions">
                        <button type="submit" class="button-primary" ${user.is_admin || isPending("admin-request-create") ? "disabled" : ""}>
                            ${user.is_admin ? "当前账号已是管理员" : isPending("admin-request-create") ? "正在提交..." : "提交申请"}
                        </button>
                    </div>
                </form>
            </article>
        </section>

        <section class="card request-record-card">
            <div class="card-header">
                <div>
                    <h3>我的申请记录</h3>
                    <p class="muted">按提交时间倒序展示，会保留审批人、审核时间和备注。</p>
                </div>
                <button type="button" class="button-secondary button-compact" data-action="reload-requests" ${isPending("requests") ? "disabled" : ""}>
                    ${isPending("requests") ? "正在刷新..." : "刷新"}
                </button>
            </div>

            <div class="request-record-sections">
                ${renderQuotaRecordSection("存储配额申请", requestsState.quotaRequests || [])}
                ${renderBandwidthRecordSection("带宽申请", requestsState.bandwidthRequests || [])}
                ${renderAdminRecordSection("管理员申请", requestsState.adminRequests || [])}
            </div>
        </section>
    `;
}

function renderAdminPage() {
    if (!Boolean(state.profile?.user?.is_admin || state.session?.user?.is_admin)) {
        return renderPlaceholderPage("管理员权限不足");
    }

    const totalPendingCount = (state.admin.quotaRequests || []).length
        + (state.admin.bandwidthRequests || []).length
        + (state.admin.adminRequests || []).length;

    return `
        ${renderAdminSectionCard({
            panelKey: "settings",
            title: "系统设置",
            description: "这里的默认值会作用到新注册账号；上传上限和注册开关会实时影响系统行为。",
            badgeLabel: `当前用户数 ${state.admin.currentUserCount || 0}`,
            badgeClass: "badge-accent",
            body: `
                <form id="admin-settings-form" class="form-grid">
                    <div class="inline-fields admin-settings-grid">
                        <div class="field">
                            <label for="admin-max-user-count">最大用户数</label>
                            <input id="admin-max-user-count" name="max_user_count" type="number" min="1" step="1" value="${escapeAttribute(state.admin.settingsForm?.maxUserCount || "")}" required>
                        </div>
                        <div class="field">
                            <label for="admin-default-storage-quota-mb">默认配额（MB）</label>
                            <input id="admin-default-storage-quota-mb" name="default_storage_quota_mb" type="number" min="1" step="1" value="${escapeAttribute(state.admin.settingsForm?.defaultStorageQuotaMB || "")}" required>
                        </div>
                        <div class="field">
                            <label for="admin-default-upload-bandwidth">默认上传带宽（MB/s）</label>
                            <input id="admin-default-upload-bandwidth" name="default_upload_bandwidth_kbps" type="number" min="0.1" step="0.1" value="${escapeAttribute(state.admin.settingsForm?.defaultUploadBandwidthKbps || "")}" required>
                        </div>
                        <div class="field">
                            <label for="admin-default-download-bandwidth">默认下载带宽（MB/s）</label>
                            <input id="admin-default-download-bandwidth" name="default_download_bandwidth_kbps" type="number" min="0.1" step="0.1" value="${escapeAttribute(state.admin.settingsForm?.defaultDownloadBandwidthKbps || "")}" required>
                        </div>
                        <div class="field">
                            <label for="admin-max-user-upload-bandwidth">用户上传上限（MB/s）</label>
                            <input id="admin-max-user-upload-bandwidth" name="max_user_upload_bandwidth_kbps" type="number" min="0.1" step="0.1" value="${escapeAttribute(state.admin.settingsForm?.maxUserUploadBandwidthKbps || "")}" required>
                        </div>
                        <div class="field">
                            <label for="admin-max-user-download-bandwidth">用户下载上限（MB/s）</label>
                            <input id="admin-max-user-download-bandwidth" name="max_user_download_bandwidth_kbps" type="number" min="0.1" step="0.1" value="${escapeAttribute(state.admin.settingsForm?.maxUserDownloadBandwidthKbps || "")}" required>
                        </div>
                        <div class="field">
                            <label for="admin-max-upload-file-mb">单文件上传上限（MB）</label>
                            <input id="admin-max-upload-file-mb" name="max_upload_file_mb" type="number" min="1" step="1" value="${escapeAttribute(state.admin.settingsForm?.maxUploadFileMB || "")}" required>
                        </div>
                        <div class="field field-inline field-inline-toggle">
                            <label class="checkbox-row" for="admin-allow-registration">
                                <input id="admin-allow-registration" name="allow_registration" type="checkbox" ${state.admin.settingsForm?.allowRegistration ? "checked" : ""}>
                                <span>允许公开注册</span>
                            </label>
                        </div>
                    </div>

                    <div class="actions">
                        <button type="submit" class="button-primary" ${isPending("admin-settings-save") ? "disabled" : ""}>
                            ${isPending("admin-settings-save") ? "正在保存..." : "保存设置"}
                        </button>
                        <span class="muted">上次更新时间：${formatDateTime(state.admin.settings?.updated_at)}</span>
                    </div>
                </form>
            `
        })}

        ${renderAdminSectionCard({
            panelKey: "users",
            title: "用户列表",
            description: "支持调整单个用户的配额、带宽和管理员标记，也可以直接删除用户。",
            badgeLabel: `${(state.admin.users || []).length} 个用户`,
            body: `
                <div class="admin-section-toolbar">
                    <button type="button" class="button-secondary button-compact" data-action="reload-admin" ${isPending("admin") ? "disabled" : ""}>
                        ${isPending("admin") ? "正在刷新..." : "刷新"}
                    </button>
                </div>

                <div class="admin-user-list">
                    ${(state.admin.users || []).length ? state.admin.users.map((user) => renderAdminUserItem(user)).join("") : `
                        <div class="empty-state compact-empty-state">
                            <h3>暂无用户</h3>
                        </div>
                    `}
                </div>
            `
        })}

        ${renderAdminSectionCard({
            panelKey: "reviews",
            title: "待审批",
            description: "配额、带宽和管理员申请统一放在这里，展开后逐项处理。",
            badgeLabel: `${totalPendingCount} 条`,
            body: `
                <div class="admin-review-stack">
                    <article class="card">
                        <div class="card-header">
                            <div>
                                <h3>待审批配额申请</h3>
                                <p class="muted">不填批准额度时，默认使用用户申请值。</p>
                            </div>
                            <span class="badge badge-primary">${(state.admin.quotaRequests || []).length} 条</span>
                        </div>
                        <div class="admin-review-list">
                            ${renderAdminQuotaReviewList()}
                        </div>
                    </article>

                    <article class="card">
                        <div class="card-header">
                            <div>
                                <h3>待审批带宽申请</h3>
                                <p class="muted">批准时可分别覆盖上传/下载值，也可以直接按申请值通过。</p>
                            </div>
                            <span class="badge badge-primary">${(state.admin.bandwidthRequests || []).length} 条</span>
                        </div>
                        <div class="admin-review-list">
                            ${renderAdminBandwidthReviewList()}
                        </div>
                    </article>

                    <article class="card">
                        <div class="card-header">
                            <div>
                                <h3>待审批管理员申请</h3>
                                <p class="muted">批准后目标账号会立刻获得管理员身份。</p>
                            </div>
                            <span class="badge badge-primary">${(state.admin.adminRequests || []).length} 条</span>
                        </div>
                        <div class="admin-review-list">
                            ${renderAdminPrivilegeReviewList()}
                        </div>
                    </article>
                </div>
            `
        })}
    `;
}

function renderAdminSectionCard({ panelKey, title, description, badgeLabel, badgeClass = "badge-primary", body }) {
    const isOpen = Boolean(state.admin.panels?.[panelKey]);

    // 管理页折叠区统一走这里，结构固定后样式和状态都更容易维护。
    return `
        <details class="card admin-section-card" data-admin-panel="${escapeAttribute(panelKey)}" ${isOpen ? "open" : ""}>
            <summary class="admin-section-summary">
                <div class="admin-section-summary-main">
                    <div class="admin-section-summary-copy">
                        <h3>${escapeHTML(title)}</h3>
                        <p class="muted">${escapeHTML(description)}</p>
                    </div>
                    <span class="badge ${escapeAttribute(badgeClass)}">${escapeHTML(badgeLabel)}</span>
                </div>
            </summary>
            <div class="admin-section-body">
                ${body}
            </div>
        </details>
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
                    <button
                        type="button"
                        class="icon-button"
                        data-action="delete-clipboard-item"
                        data-item-id="${escapeHTML(item.id)}"
                        aria-label="删除历史记录"
                        title="删除"
                        ${isPending(`clipboard-delete:${item.id}`) ? "disabled" : ""}
                    >
                        ${renderIcon("trash")}
                    </button>
                </div>
            </div>
        </article>
    `;
}

function renderFilesPage() {
    const files = Array.isArray(state.files.items) ? state.files.items : [];
    const totalPages = Math.max(state.files.totalPages, 1);

    return `
        ${renderFileOverview()}

        <section class="card device-list-shell file-list-shell">
            <div class="device-list-toolbar">
                <p>第 ${state.files.page} / ${totalPages} 页 · 共 ${state.files.total} 个文件</p>
                <div class="history-toolbar-actions">
                    <button type="button" class="button-secondary button-compact" data-action="reload-files" ${isPending("files") ? "disabled" : ""}>
                        ${isPending("files") ? "正在刷新..." : "刷新"}
                    </button>
                    <button type="button" class="button-ghost" data-action="files-prev" ${state.files.page <= 1 || isPending("files") ? "disabled" : ""}>
                        上一页
                    </button>
                    <button type="button" class="button-ghost" data-action="files-next" ${(state.files.totalPages > 0 && state.files.page >= state.files.totalPages) || isPending("files") ? "disabled" : ""}>
                        下一页
                    </button>
                </div>
            </div>

            ${files.length ? `
                <div class="device-compact-list file-compact-list">
                    ${files.map((item) => renderFileItem(item)).join("")}
                </div>
            ` : `
                <div class="empty-state compact-empty-state">
                    <img src="./assets/illustrations/p1/empty-files.webp" alt="暂无文件插画">
                    <h3>暂无文件</h3>
                    <p>上传一个文件后，就可以在这里统一查看、下载、重命名和删除。</p>
                </div>
            `}
        </section>

        ${renderFilePanel()}
    `;
}

function renderFileOverview() {
    return `
        <section class="card device-stats-card file-summary-card">
            <div class="sync-summary-shell">
                <div class="device-stats-grid file-summary-grid">
                    <div class="device-stat">
                        <span>文件总数</span>
                        <strong>${state.files.total}</strong>
                    </div>
                    <div class="device-stat">
                        <span>总占用</span>
                        <strong>${formatBytes(state.files.totalBytes)}</strong>
                    </div>
                    <div class="device-stat">
                        <span>单文件上限</span>
                        <strong>${formatBytes(state.files.maxUploadBytes)}</strong>
                    </div>
                </div>

                <button
                    type="button"
                    class="icon-button history-upload-button"
                    data-action="open-file-upload"
                    aria-label="上传文件"
                    title="上传文件"
                >
                    ${renderIcon("upload")}
                </button>
            </div>
        </section>
    `;
}

function renderFileItem(item) {
    const isActive = state.filePanel.fileId === item.id;
    const isCurrentOrigin = item.origin_device_id && item.origin_device_id === state.profile?.current_device_id;

    return `
        <article class="device-compact-item file-compact-item ${isActive ? "is-active" : ""}">
            <div class="device-compact-main file-compact-main">
                <button
                    type="button"
                    class="device-compact-copy file-compact-trigger"
                    data-action="open-file-details"
                    data-file-id="${escapeHTML(item.id)}"
                    aria-label="查看文件详情"
                    aria-pressed="${isActive ? "true" : "false"}"
                >
                    <strong class="file-compact-name">${escapeHTML(item.original_name || "未命名文件")}</strong>
                    <div class="device-compact-status-row file-compact-meta-row">
                        <span class="clipboard-item-seq">${formatBytes(item.size_bytes)}</span>
                        <span class="device-compact-status">${escapeHTML(item.content_type || "application/octet-stream")}</span>
                        <span class="device-compact-role">${escapeHTML(isCurrentOrigin ? "本机上传" : (item.origin_device_name || "未知设备"))}</span>
                    </div>
                </button>

                <div class="device-compact-actions">
                    <button
                        type="button"
                        class="icon-button"
                        data-action="download-file"
                        data-file-id="${escapeHTML(item.id)}"
                        aria-label="下载文件"
                        title="下载"
                    >
                        ${renderIcon("download")}
                    </button>
                    <button
                        type="button"
                        class="icon-button"
                        data-action="open-file-rename"
                        data-file-id="${escapeHTML(item.id)}"
                        aria-label="重命名文件"
                        title="重命名"
                    >
                        ${renderIcon("edit")}
                    </button>
                    <button
                        type="button"
                        class="icon-button"
                        data-action="delete-file"
                        data-file-id="${escapeHTML(item.id)}"
                        aria-label="删除文件"
                        title="删除"
                    >
                        ${renderIcon("trash")}
                    </button>
                </div>
            </div>
        </article>
    `;
}

function renderSharesPage() {
    const sharesState = state.shares;
    const items = Array.isArray(sharesState.items) ? sharesState.items : [];
    const totalPages = Math.max(sharesState.totalPages, 1);

    return `
        ${renderShareOverview()}

        ${renderLatestShareResult()}

        <section class="card device-list-shell share-list-shell">
            <div class="device-list-toolbar share-list-toolbar">
                <div class="share-filter-group">
                    <label for="share-status-filter">状态筛选</label>
                    <select id="share-status-filter" name="share_status">
                        ${renderShareStatusOption("all", "全部", sharesState.status)}
                        ${renderShareStatusOption("active", "可访问", sharesState.status)}
                        ${renderShareStatusOption("expired", "已过期", sharesState.status)}
                        ${renderShareStatusOption("consumed", "已焚毁", sharesState.status)}
                        ${renderShareStatusOption("revoked", "已撤销", sharesState.status)}
                    </select>
                    <button type="button" class="button-secondary button-compact" data-action="apply-share-filter" ${isPending("shares") ? "disabled" : ""}>
                        应用
                    </button>
                </div>

                <div class="history-toolbar-actions">
                    <button type="button" class="button-secondary button-compact" data-action="reload-shares" ${isPending("shares") ? "disabled" : ""}>
                        ${isPending("shares") ? "正在刷新..." : "刷新"}
                    </button>
                    <button type="button" class="button-ghost" data-action="shares-prev" ${sharesState.page <= 1 || isPending("shares") ? "disabled" : ""}>
                        上一页
                    </button>
                    <button type="button" class="button-ghost" data-action="shares-next" ${(sharesState.totalPages > 0 && sharesState.page >= sharesState.totalPages) || isPending("shares") ? "disabled" : ""}>
                        下一页
                    </button>
                </div>
            </div>

            <p class="muted share-list-meta">第 ${sharesState.page} / ${totalPages} 页 · 共 ${sharesState.total} 条分享</p>

            ${items.length ? `
                <div class="device-compact-list share-compact-list">
                    ${items.map((item) => renderShareItem(item)).join("")}
                </div>
            ` : `
                <div class="empty-state compact-empty-state">
                    <h3>暂无分享</h3>
                    <p>创建第一条分享后，这里会显示公开访问状态、链接和撤销入口。</p>
                </div>
            `}
        </section>

        ${renderSharePanel()}
        ${renderShareQRCodeDialog()}
    `;
}

function renderShareOverview() {
    return `
        <section class="card device-stats-card file-summary-card">
            <div class="sync-summary-shell">
                <div class="device-stats-grid share-summary-grid">
                    <div class="device-stat">
                        <span>分享总数</span>
                        <strong>${state.shares.total}</strong>
                    </div>
                    <div class="device-stat">
                        <span>当前筛选</span>
                        <strong>${resolveStatusLabel(state.shares.status || "all")}</strong>
                    </div>
                    <div class="device-stat">
                        <span>每个文件上限</span>
                        <strong>${formatBytes(state.shares.maxUploadBytes)}</strong>
                    </div>
                </div>

                <button
                    type="button"
                    class="icon-button history-upload-button"
                    data-action="open-share-panel"
                    aria-label="创建分享"
                    title="创建分享"
                >
                    ${renderIcon("shares")}
                </button>
            </div>
        </section>
    `;
}

function renderSharePanel() {
    if (!state.shares.panelOpen) {
        return "";
    }

    const activeStrategy = buildShareStrategySummary(state.shares.strategyKey);
    const passwordHint = state.shares.password.trim() ? "当前分享会在浏览器里先加密，再上传密文。" : "不输入密码就是普通公开分享。";
    const selectedFiles = Array.isArray(state.shares.selectedFiles) ? state.shares.selectedFiles : [];

    return `
        <div class="device-panel-backdrop" data-action="close-share-panel"></div>
        <aside class="device-panel share-panel">
            <div class="device-panel-header">
                <div>
                    <h2>创建分享</h2>
                    <p>文字和文件共用同一份分享规则，文件可选，文字也可单独发送。</p>
                </div>
                <button type="button" class="icon-button" data-action="close-share-panel" aria-label="关闭分享面板">
                    ${renderIcon("close")}
                </button>
            </div>

            <form id="share-compose-form" class="form-grid share-panel-form">
                <div class="field">
                    <label for="share-text-content">文字内容（可选）</label>
                    <textarea
                        id="share-text-content"
                        name="text_content"
                        rows="7"
                        maxlength="65535"
                        placeholder="可以只发文字，也可以和文件一起发"
                    >${escapeHTML(state.shares.textDraft || "")}</textarea>
                </div>

                <div class="field">
                    <label for="share-file-input">文件内容（可选）</label>
                    <div class="share-dropzone ${state.shares.dragActive ? "is-drag-active" : ""}" data-share-dropzone>
                        <input id="share-file-input" name="files" type="file" class="sr-only" multiple>
                        <label class="share-dropzone-trigger" for="share-file-input">
                            <strong>拖拽文件到这里，或点击选择多个文件</strong>
                            <span>每个文件上限 ${formatBytes(state.shares.maxUploadBytes || 0)}</span>
                        </label>

                        ${selectedFiles.length ? `
                            <div class="share-selected-files">
                                <div class="share-selected-file">
                                    <strong>已选择 ${selectedFiles.length} 个文件</strong>
                                    <button type="button" class="button-ghost button-compact" data-action="clear-share-file">清空</button>
                                </div>
                                <div class="share-selected-file-list">
                                    ${selectedFiles.map((file) => `
                                        <span class="share-selected-file-chip">${escapeHTML(file.name || "未命名文件")}</span>
                                    `).join("")}
                                </div>
                            </div>
                        ` : `
                            <p class="muted share-inline-note">未选择文件时，会创建纯文字分享。</p>
                        `}
                    </div>
                </div>

                <div class="field">
                    <label>分享策略</label>
                    <div class="share-strategy-group">
                        ${renderShareStrategyButton("never", "不过期", activeStrategy.key)}
                        ${renderShareStrategyButton("expire", "过期", activeStrategy.key)}
                        ${renderShareStrategyButton("once", "打开一次失效", activeStrategy.key)}
                    </div>
                </div>

                <section class="share-strategy-summary-card">
                    <div class="share-strategy-summary-head">
                        <strong>${escapeHTML(activeStrategy.title)}</strong>
                        <span>${escapeHTML(activeStrategy.copyLabel)}</span>
                    </div>
                    <p>${escapeHTML(activeStrategy.description)}</p>
                </section>

                <div class="field">
                    <label for="share-password">分享密码（可选）</label>
                    <input
                        id="share-password"
                        name="password"
                        type="password"
                        minlength="4"
                        maxlength="128"
                        value="${escapeAttribute(state.shares.password || "")}"
                        placeholder="留空就是不加密"
                    >
                    <p class="muted share-inline-note">${escapeHTML(passwordHint)}</p>
                </div>

                <div class="device-panel-actions">
                    <button type="submit" class="button-primary" ${isPending("share-create") ? "disabled" : ""}>
                        ${isPending("share-create") ? "正在生成..." : "生成分享链接"}
                    </button>
                </div>
            </form>
        </aside>
    `;
}

function renderLatestShareResult() {
    if (!state.shares.latestShareToken) {
        return "";
    }

    const publicLink = buildPublicShareURL(state.shares.latestShareToken);
    return `
        <section class="card share-result-card">
            <div class="card-header">
                <div>
                    <h3>最新分享链接</h3>
                    <p class="muted">生成后可以直接复制，或在新标签页打开公开取件页。</p>
                </div>
            </div>

            <div class="share-result-row">
                <input type="text" readonly value="${escapeAttribute(publicLink)}">
                <button
                    type="button"
                    class="icon-button"
                    data-action="open-share-qr"
                    data-share-token="${escapeHTML(state.shares.latestShareToken)}"
                    aria-label="查看分享二维码"
                    title="二维码"
                >
                    ${renderIcon("qr")}
                </button>
                <button type="button" class="button-secondary" data-action="copy-share-link" data-share-token="${escapeHTML(state.shares.latestShareToken)}">
                    复制链接
                </button>
                <button type="button" class="button-ghost" data-action="open-share-link" data-share-token="${escapeHTML(state.shares.latestShareToken)}">
                    打开
                </button>
            </div>
        </section>
    `;
}

function renderShareItem(item) {
    const badgeClass = resolveStatusBadgeClass(item.status);
    const files = Array.isArray(item.files) ? item.files : [];
    const title = item.has_file_content
        ? (files.length > 1
            ? `${files[0]?.original_name || item.file?.original_name || "文件分享"} 等 ${files.length} 个文件`
            : (item.file?.original_name || files[0]?.original_name || "文件分享"))
        : (item.text_preview || "文字分享");

    return `
        <article class="device-compact-item share-compact-item">
            <div class="device-compact-main share-compact-main">
                <div class="device-compact-copy share-compact-copy">
                    <div class="share-compact-title-row">
                        <strong>${escapeHTML(title)}</strong>
                        <span class="badge ${badgeClass}">${escapeHTML(resolveStatusLabel(item.status))}</span>
                    </div>

                    <div class="device-compact-status-row share-compact-meta-row">
                        <span class="clipboard-item-seq">${escapeHTML(resolveShareContentLabel(item))}</span>
                        <span class="device-compact-status">${escapeHTML(resolveShareAccessLabel(item))}</span>
                        <span class="device-compact-role">${escapeHTML(item.is_encrypted ? "已加密" : "明文访问")}</span>
                    </div>

                    <div class="share-compact-extra">
                        <span>创建于 ${escapeHTML(formatDateTime(item.created_at))}</span>
                        <span>剩余 ${escapeHTML(formatRemainingSeconds(item.remaining_seconds))}</span>
                        ${item.has_file_content ? `<span>${files.length > 1 ? `${files.length} 个文件` : escapeHTML(formatBytes(item.file?.size_bytes || 0))}</span>` : ""}
                        ${item.has_text_content ? `<span>${item.allow_copy_content ? "文字可复制" : "文字禁止复制"}</span>` : ""}
                    </div>
                </div>

                <div class="device-compact-actions">
                    <button
                        type="button"
                        class="icon-button"
                        data-action="copy-share-link"
                        data-share-token="${escapeHTML(item.token)}"
                        aria-label="复制分享链接"
                        title="复制链接"
                    >
                        ${renderIcon("copy")}
                    </button>
                    <button
                        type="button"
                        class="icon-button"
                        data-action="open-share-qr"
                        data-share-token="${escapeHTML(item.token)}"
                        aria-label="查看分享二维码"
                        title="二维码"
                    >
                        ${renderIcon("qr")}
                    </button>
                    <button
                        type="button"
                        class="icon-button"
                        data-action="open-share-link"
                        data-share-token="${escapeHTML(item.token)}"
                        aria-label="打开公开取件页"
                        title="打开"
                    >
                        ${renderIcon("view")}
                    </button>
                    <button
                        type="button"
                        class="icon-button"
                        data-action="revoke-share"
                        data-share-id="${escapeHTML(item.id)}"
                        aria-label="撤销分享"
                        title="撤销"
                        ${item.status === "revoked" ? "disabled" : ""}
                    >
                        ${renderIcon("trash")}
                    </button>
                </div>
            </div>
        </article>
    `;
}

function renderShareQRCodeDialog() {
    const shareToken = String(state.shares.qrCodeDialogToken || "").trim();
    if (!shareToken) {
        return "";
    }

    const publicLink = buildPublicShareURL(shareToken);
    const qrCodeURL = buildShareQRCodeURL(publicLink);

    return `
        <div class="device-panel-backdrop" data-action="close-share-qr"></div>
        <section class="share-qr-dialog" role="dialog" aria-modal="true" aria-labelledby="share-qr-title">
            <div class="share-qr-dialog-header">
                <div>
                    <h3 id="share-qr-title">分享二维码</h3>
                    <p class="muted">扫码后可以直接打开当前分享链接。</p>
                </div>
                <button
                    type="button"
                    class="icon-button"
                    data-action="close-share-qr"
                    aria-label="关闭二维码弹窗"
                >
                    ${renderIcon("close")}
                </button>
            </div>

            <div class="share-qr-dialog-body">
                <div class="share-qr-preview-shell">
                    ${qrCodeURL ? `
                        <img
                            class="share-qr-preview-image"
                            src="${escapeAttribute(qrCodeURL)}"
                            alt="分享链接二维码"
                        >
                    ` : `
                        <div class="empty-state compact-empty-state">
                            <h3>二维码暂不可用</h3>
                        </div>
                    `}
                </div>

                <div class="share-qr-link-block">
                    <strong>公开链接</strong>
                    <p>${escapeHTML(publicLink)}</p>
                </div>
            </div>
        </section>
    `;
}

function renderShareStatusOption(value, label, current) {
    return `<option value="${value}" ${value === current ? "selected" : ""}>${label}</option>`;
}

function renderShareStrategyButton(strategyKey, label, activeKey) {
    return `
        <button
            type="button"
            class="share-strategy-button ${strategyKey === activeKey ? "is-active" : ""}"
            data-action="select-share-strategy"
            data-strategy="${strategyKey}"
        >
            ${escapeHTML(label)}
        </button>
    `;
}

function buildShareStrategySummary(strategyKey) {
    const rules = state.shareRules || {};

    switch (strategyKey) {
        case "never":
            return {
                key: "never",
                title: "不过期",
                description: "链接不会自动过期，只能靠手动撤销结束访问。",
                copyLabel: rules.never?.allowCopyText ? "文字可复制" : "文字禁止复制"
            };
        case "once":
            return {
                key: "once",
                title: "打开一次失效",
                description: rules.once?.showCountdown
                    ? `首次打开后开始 ${rules.once?.countdownSeconds || 10} 秒倒计时，时间到后自动失效。`
                    : "内容首次打开后立刻失效。",
                copyLabel: rules.once?.allowCopyText ? "文字可复制" : "文字禁止复制"
            };
        case "expire":
        default:
            return {
                key: "expire",
                title: "过期",
                description: `${rules.expire?.expireHours || 24} 小时后自动失效。`,
                copyLabel: rules.expire?.allowCopyText ? "文字可复制" : "文字禁止复制"
            };
    }
}

function buildPublicShareURL(token) {
    const base = `${window.location.origin}${window.location.pathname}`;
    return `${base}#/public/${encodeURIComponent(token || "")}`;
}

function buildShareQRCodeURL(publicLink) {
    const normalizedLink = String(publicLink || "").trim();
    const normalizedServerBaseUrl = String(state.serverBaseUrl || "").trim().replace(/\/+$/, "");
    if (!normalizedLink || !normalizedServerBaseUrl) {
        return "";
    }

    const query = new URLSearchParams({
        content: normalizedLink,
        size: "280"
    });
    return `${normalizedServerBaseUrl}/v1/public/qrcode?${query.toString()}`;
}

function resolveStatusLabel(status) {
    switch ((status || "").toLowerCase()) {
        case "active":
            return "可访问";
        case "expired":
            return "已过期";
        case "consumed":
            return "已焚毁";
        case "revoked":
            return "已撤销";
        case "all":
            return "全部";
        default:
            return "未知";
    }
}

function resolveStatusBadgeClass(status) {
    switch ((status || "").toLowerCase()) {
        case "active":
            return "badge-primary";
        case "expired":
            return "badge-warning";
        case "consumed":
            return "badge-accent";
        case "revoked":
            return "badge-warning";
        default:
            return "badge-warning";
    }
}

function resolveShareContentLabel(item) {
    if (item?.has_file_content && item?.has_text_content) {
        return "文件 + 文字";
    }
    if (item?.has_file_content || (item?.content_kind || "").toLowerCase() === "file") {
        return "文件分享";
    }
    return "文字分享";
}

function resolveShareAccessLabel(share) {
    if (!share) {
        return "未知策略";
    }

    if ((share.burn_mode || "").toLowerCase() === "once") {
        return "打开一次失效";
    }
    if ((share.burn_mode || "").toLowerCase() === "countdown") {
        return `首次打开后 ${share.burn_after_seconds || 0} 秒倒计时`;
    }
    if (!share.expires_at) {
        return "不过期";
    }
    return "过期";
}

function formatRemainingSeconds(value) {
    const seconds = Number(value || 0);
    if (!Number.isFinite(seconds) || seconds <= 0) {
        return "不限 / 已结束";
    }
    if (seconds < 60) {
        return `${seconds} 秒`;
    }
    if (seconds < 3600) {
        return `${Math.ceil(seconds / 60)} 分钟`;
    }
    if (seconds < 86400) {
        return `${Math.ceil(seconds / 3600)} 小时`;
    }
    return `${Math.ceil(seconds / 86400)} 天`;
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

function renderQuotaRecordSection(title, items) {
    return renderRequestRecordBlock(
        title,
        items,
        (item) => `
            <article class="request-record-item">
                <div class="request-record-head">
                    <strong>${formatBytes(item.requested_quota_bytes)}</strong>
                    <span class="badge ${resolveRequestBadgeClass(item.status)}">${resolveRequestStatusLabel(item.status)}</span>
                </div>
                <div class="request-record-meta">
                    <span>当前配额 ${formatBytes(item.current_quota_bytes)}</span>
                    <span>${formatDateTime(item.created_at)}</span>
                </div>
                ${item.reason ? `<p class="request-record-note">${escapeHTML(item.reason)}</p>` : ""}
                ${renderReviewMeta(item)}
            </article>
        `
    );
}

function renderBandwidthRecordSection(title, items) {
    return renderRequestRecordBlock(
        title,
        items,
        (item) => `
            <article class="request-record-item">
                <div class="request-record-head">
                    <strong>${formatBandwidthMBps(item.requested_upload_kbps)} / ${formatBandwidthMBps(item.requested_download_kbps)}</strong>
                    <span class="badge ${resolveRequestBadgeClass(item.status)}">${resolveRequestStatusLabel(item.status)}</span>
                </div>
                <div class="request-record-meta">
                    <span>当前带宽 ${formatBandwidthMBps(item.current_upload_kbps)} / ${formatBandwidthMBps(item.current_download_kbps)}</span>
                    <span>${formatDateTime(item.created_at)}</span>
                </div>
                ${item.reason ? `<p class="request-record-note">${escapeHTML(item.reason)}</p>` : ""}
                ${renderReviewMeta(item)}
            </article>
        `
    );
}

function renderAdminRecordSection(title, items) {
    return renderRequestRecordBlock(
        title,
        items,
        (item) => `
            <article class="request-record-item">
                <div class="request-record-head">
                    <strong>${escapeHTML(item.username || state.profile?.user?.username || "-")}</strong>
                    <span class="badge ${resolveRequestBadgeClass(item.status)}">${resolveRequestStatusLabel(item.status)}</span>
                </div>
                <div class="request-record-meta">
                    <span>${formatDateTime(item.created_at)}</span>
                </div>
                ${item.reason ? `<p class="request-record-note">${escapeHTML(item.reason)}</p>` : ""}
                ${renderReviewMeta(item)}
            </article>
        `
    );
}

function renderRequestRecordBlock(title, items, renderItem) {
    return `
        <section class="request-record-block">
            <div class="request-record-title-row">
                <h4>${escapeHTML(title)}</h4>
                <span class="muted">${items.length} 条</span>
            </div>
            ${items.length ? `
                <div class="request-record-list">
                    ${items.map((item) => renderItem(item)).join("")}
                </div>
            ` : `
                <div class="empty-state compact-empty-state">
                    <h3>暂无记录</h3>
                </div>
            `}
        </section>
    `;
}

function renderReviewMeta(item) {
    if (!item.reviewed_at && !item.review_note && !item.reviewed_by_username) {
        return "";
    }

    return `
        <div class="request-review-meta">
            <span>审核人：${escapeHTML(item.reviewed_by_username || item.reviewed_by || "-")}</span>
            <span>审核时间：${escapeHTML(formatDateTime(item.reviewed_at))}</span>
            ${item.review_note ? `<span>备注：${escapeHTML(item.review_note)}</span>` : ""}
        </div>
    `;
}

function renderAdminUserItem(user) {
    const draft = state.admin.userDrafts?.[user.id] || {};
    const isCurrentUser = user.id === state.session?.user?.id;

    return `
        <article class="admin-user-item">
            <form class="admin-user-form" data-admin-user-id="${escapeAttribute(user.id)}">
                <div class="admin-user-head">
                    <div>
                        <div class="admin-user-title-row">
                            <strong>${escapeHTML(user.username || "-")}</strong>
                            ${isCurrentUser ? '<span class="badge badge-accent">当前账号</span>' : ""}
                            ${user.is_admin ? '<span class="badge badge-primary">管理员</span>' : '<span class="badge badge-warning">普通用户</span>'}
                        </div>
                        <div class="request-record-meta">
                            <span>ID ${escapeHTML(user.id)}</span>
                            <span>最后活跃 ${escapeHTML(formatDateTime(user.last_active_at))}</span>
                        </div>
                    </div>
                    <div class="admin-user-pending-flags">
                        ${user.has_pending_quota_request ? '<span class="badge badge-warning">待配额审批</span>' : ""}
                        ${user.has_pending_bandwidth_request ? '<span class="badge badge-warning">待带宽审批</span>' : ""}
                        ${user.has_pending_admin_request ? '<span class="badge badge-warning">待管理员审批</span>' : ""}
                    </div>
                </div>

                <div class="inline-fields admin-user-grid">
                    <div class="field">
                        <label for="admin-user-storage-${escapeAttribute(user.id)}">存储配额（MB）</label>
                        <input
                            id="admin-user-storage-${escapeAttribute(user.id)}"
                            name="storage_quota_mb"
                            type="number"
                            min="1"
                            step="1"
                            value="${escapeAttribute(draft.storageQuotaMB ?? bytesToMegabytes(user.storage_quota_bytes))}"
                            required
                        >
                    </div>
                    <div class="field">
                        <label for="admin-user-upload-${escapeAttribute(user.id)}">上传带宽（MB/s）</label>
                        <input
                            id="admin-user-upload-${escapeAttribute(user.id)}"
                            name="upload_bandwidth_kbps"
                            type="number"
                            min="0.1"
                            step="0.1"
                            value="${escapeAttribute(draft.uploadBandwidthKbps ?? bandwidthKbpsToMBpsInput(user.upload_bandwidth_kbps))}"
                            required
                        >
                    </div>
                    <div class="field">
                        <label for="admin-user-download-${escapeAttribute(user.id)}">下载带宽（MB/s）</label>
                        <input
                            id="admin-user-download-${escapeAttribute(user.id)}"
                            name="download_bandwidth_kbps"
                            type="number"
                            min="0.1"
                            step="0.1"
                            value="${escapeAttribute(draft.downloadBandwidthKbps ?? bandwidthKbpsToMBpsInput(user.download_bandwidth_kbps))}"
                            required
                        >
                    </div>
                    <div class="field field-inline field-inline-toggle">
                        <label class="checkbox-row" for="admin-user-admin-${escapeAttribute(user.id)}">
                            <input
                                id="admin-user-admin-${escapeAttribute(user.id)}"
                                name="is_admin"
                                type="checkbox"
                                ${Boolean(draft.isAdmin ?? user.is_admin) ? "checked" : ""}
                            >
                            <span>管理员</span>
                        </label>
                    </div>
                </div>

                <div class="request-record-meta admin-user-storage-meta">
                    <span>已用 ${formatBytes(user.storage_used_bytes)}</span>
                    <span>剩余 ${formatBytes(user.storage_free_bytes)}</span>
                    <span>创建于 ${formatDateTime(user.created_at)}</span>
                </div>

                <div class="actions admin-user-actions">
                    <button type="submit" class="button-primary" ${isPending(`admin-user-save:${user.id}`) ? "disabled" : ""}>
                        ${isPending(`admin-user-save:${user.id}`) ? "正在保存..." : "保存用户"}
                    </button>
                    <button
                        type="button"
                        class="button-danger"
                        data-action="delete-admin-user"
                        data-user-id="${escapeHTML(user.id)}"
                        data-username="${escapeHTML(user.username || "")}"
                        ${isPending(`admin-user-delete:${user.id}`) ? "disabled" : ""}
                    >
                        ${isPending(`admin-user-delete:${user.id}`) ? "正在删除..." : "删除用户"}
                    </button>
                </div>
            </form>
        </article>
    `;
}

function renderAdminQuotaReviewList() {
    const items = state.admin.quotaRequests || [];
    if (!items.length) {
        return `
            <div class="empty-state compact-empty-state">
                <h3>暂无待审批配额申请</h3>
            </div>
        `;
    }

    return items.map((item) => `
        <article class="admin-review-item">
            <form class="form-grid admin-review-form" data-admin-review-type="quota" data-request-id="${escapeAttribute(item.id)}">
                <div class="admin-review-head">
                    <div>
                        <strong>${escapeHTML(item.username || "-")}</strong>
                        <div class="request-record-meta">
                            <span>当前 ${formatBytes(item.current_quota_bytes)}</span>
                            <span>申请 ${formatBytes(item.requested_quota_bytes)}</span>
                            <span>${formatDateTime(item.created_at)}</span>
                        </div>
                    </div>
                    <span class="badge ${resolveRequestBadgeClass(item.status)}">${resolveRequestStatusLabel(item.status)}</span>
                </div>
                ${item.reason ? `<p class="request-record-note">${escapeHTML(item.reason)}</p>` : ""}
                <div class="field">
                    <label for="quota-approve-mb-${escapeAttribute(item.id)}">批准配额（MB，可留空）</label>
                    <input id="quota-approve-mb-${escapeAttribute(item.id)}" name="approved_quota_mb" type="number" min="1" step="1" placeholder="${bytesToMegabytes(item.requested_quota_bytes)}">
                </div>
                <div class="field">
                    <label for="quota-review-note-${escapeAttribute(item.id)}">审核备注</label>
                    <textarea id="quota-review-note-${escapeAttribute(item.id)}" name="review_note" rows="3" maxlength="500" placeholder="可选：记录审批原因"></textarea>
                </div>
                <div class="actions">
                    <button type="submit" class="button-primary" ${isPending(`admin-review:quota:${item.id}`) ? "disabled" : ""}>
                        ${isPending(`admin-review:quota:${item.id}`) ? "正在提交..." : "批准"}
                    </button>
                    <button type="button" class="button-danger" data-action="reject-quota-request" data-request-id="${escapeHTML(item.id)}" ${isPending(`admin-review:quota:${item.id}`) ? "disabled" : ""}>
                        拒绝
                    </button>
                </div>
            </form>
        </article>
    `).join("");
}

function renderAdminBandwidthReviewList() {
    const items = state.admin.bandwidthRequests || [];
    if (!items.length) {
        return `
            <div class="empty-state compact-empty-state">
                <h3>暂无待审批带宽申请</h3>
            </div>
        `;
    }

    return items.map((item) => `
        <article class="admin-review-item">
            <form class="form-grid admin-review-form" data-admin-review-type="bandwidth" data-request-id="${escapeAttribute(item.id)}">
                <div class="admin-review-head">
                    <div>
                        <strong>${escapeHTML(item.username || "-")}</strong>
                        <div class="request-record-meta">
                            <span>当前 ${formatBandwidthMBps(item.current_upload_kbps)} / ${formatBandwidthMBps(item.current_download_kbps)}</span>
                            <span>申请 ${formatBandwidthMBps(item.requested_upload_kbps)} / ${formatBandwidthMBps(item.requested_download_kbps)}</span>
                            <span>${formatDateTime(item.created_at)}</span>
                        </div>
                    </div>
                    <span class="badge ${resolveRequestBadgeClass(item.status)}">${resolveRequestStatusLabel(item.status)}</span>
                </div>
                ${item.reason ? `<p class="request-record-note">${escapeHTML(item.reason)}</p>` : ""}
                <div class="inline-fields">
                    <div class="field">
                        <label for="bandwidth-approve-upload-${escapeAttribute(item.id)}">批准上传（MB/s）</label>
                        <input id="bandwidth-approve-upload-${escapeAttribute(item.id)}" name="approved_upload_kbps" type="number" min="0.1" step="0.1" placeholder="${bandwidthKbpsToMBpsInput(item.requested_upload_kbps)}">
                    </div>
                    <div class="field">
                        <label for="bandwidth-approve-download-${escapeAttribute(item.id)}">批准下载（MB/s）</label>
                        <input id="bandwidth-approve-download-${escapeAttribute(item.id)}" name="approved_download_kbps" type="number" min="0.1" step="0.1" placeholder="${bandwidthKbpsToMBpsInput(item.requested_download_kbps)}">
                    </div>
                </div>
                <div class="field">
                    <label for="bandwidth-review-note-${escapeAttribute(item.id)}">审核备注</label>
                    <textarea id="bandwidth-review-note-${escapeAttribute(item.id)}" name="review_note" rows="3" maxlength="500" placeholder="可选：记录审批原因"></textarea>
                </div>
                <div class="actions">
                    <button type="submit" class="button-primary" ${isPending(`admin-review:bandwidth:${item.id}`) ? "disabled" : ""}>
                        ${isPending(`admin-review:bandwidth:${item.id}`) ? "正在提交..." : "批准"}
                    </button>
                    <button type="button" class="button-danger" data-action="reject-bandwidth-request" data-request-id="${escapeHTML(item.id)}" ${isPending(`admin-review:bandwidth:${item.id}`) ? "disabled" : ""}>
                        拒绝
                    </button>
                </div>
            </form>
        </article>
    `).join("");
}

function renderAdminPrivilegeReviewList() {
    const items = state.admin.adminRequests || [];
    if (!items.length) {
        return `
            <div class="empty-state compact-empty-state">
                <h3>暂无待审批管理员申请</h3>
            </div>
        `;
    }

    return items.map((item) => `
        <article class="admin-review-item">
            <form class="form-grid admin-review-form" data-admin-review-type="admin" data-request-id="${escapeAttribute(item.id)}">
                <div class="admin-review-head">
                    <div>
                        <strong>${escapeHTML(item.username || "-")}</strong>
                        <div class="request-record-meta">
                            <span>${formatDateTime(item.created_at)}</span>
                        </div>
                    </div>
                    <span class="badge ${resolveRequestBadgeClass(item.status)}">${resolveRequestStatusLabel(item.status)}</span>
                </div>
                ${item.reason ? `<p class="request-record-note">${escapeHTML(item.reason)}</p>` : ""}
                <div class="field">
                    <label for="admin-review-note-${escapeAttribute(item.id)}">审核备注</label>
                    <textarea id="admin-review-note-${escapeAttribute(item.id)}" name="review_note" rows="4" maxlength="500" placeholder="可选：记录审批原因"></textarea>
                </div>
                <div class="actions">
                    <button type="submit" class="button-primary" ${isPending(`admin-review:admin:${item.id}`) ? "disabled" : ""}>
                        ${isPending(`admin-review:admin:${item.id}`) ? "正在提交..." : "批准"}
                    </button>
                    <button type="button" class="button-danger" data-action="reject-admin-request" data-request-id="${escapeHTML(item.id)}" ${isPending(`admin-review:admin:${item.id}`) ? "disabled" : ""}>
                        拒绝
                    </button>
                </div>
            </form>
        </article>
    `).join("");
}

function getVisibleNavItems() {
    const isAdmin = Boolean(state.profile?.user?.is_admin || state.session?.user?.is_admin);
    return NAV_ITEMS.filter((item) => !item.adminOnly || isAdmin);
}

function resolveRequestStatusLabel(status) {
    switch ((status || "").toLowerCase()) {
        case "pending":
            return "待审批";
        case "approved":
            return "已批准";
        case "rejected":
            return "已拒绝";
        default:
            return "未知";
    }
}

function resolveRequestBadgeClass(status) {
    switch ((status || "").toLowerCase()) {
        case "pending":
            return "badge-warning";
        case "approved":
            return "badge-accent";
        case "rejected":
            return "badge-danger";
        default:
            return "badge-warning";
    }
}

function bytesToMegabytes(value) {
    const normalized = Number(value || 0);
    if (!Number.isFinite(normalized) || normalized <= 0) {
        return 0;
    }
    return Math.max(Math.round(normalized / (1024 * 1024)), 1);
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

function renderFilePanel() {
    const panelState = state.filePanel;
    if (!panelState.mode) {
        return "";
    }

    if (panelState.mode === "upload") {
        return renderFileUploadPanel();
    }

    if (!panelState.fileId) {
        return "";
    }

    const file = state.files.items.find((item) => item.id === panelState.fileId);
    if (!file) {
        return "";
    }

    return panelState.mode === "rename"
        ? renderFileRenamePanel(file, panelState)
        : renderFileDetailPanel(file);
}

function renderFileUploadPanel() {
    return `
        <div class="device-panel-backdrop" data-action="close-file-panel"></div>
        <aside class="device-panel file-panel">
            <div class="device-panel-header">
                <div>
                    <h2>上传文件</h2>
                    <p>单文件上限 ${formatBytes(state.files.maxUploadBytes || 0)}，会记录类型、哈希和来源设备。</p>
                </div>
                <button type="button" class="icon-button" data-action="close-file-panel" aria-label="关闭上传面板">
                    ${renderIcon("close")}
                </button>
            </div>

            <form id="file-upload-form" class="form-grid file-upload-form">
                <div class="field">
                    <label for="upload-file">选择文件</label>
                    <input id="upload-file" name="file" type="file" required>
                </div>

                <div class="device-panel-actions">
                    <button type="submit" class="button-primary" ${isPending("file-upload") ? "disabled" : ""}>
                        ${isPending("file-upload") ? "正在上传..." : "上传文件"}
                    </button>
                    <span class="muted file-upload-hint">${escapeHTML(state.files.selectedUploadName || "上传后可在右侧查看详情。")}</span>
                </div>
            </form>
        </aside>
    `;
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

function renderFileDetailPanel(file) {
    const isCurrentOrigin = file.origin_device_id && file.origin_device_id === state.profile?.current_device_id;

    return `
        <div class="device-panel-backdrop" data-action="close-file-panel"></div>
        <aside class="device-panel file-panel">
            <div class="device-panel-header">
                <div>
                    <h2>${escapeHTML(file.original_name || "未命名文件")}</h2>
                    <p>${escapeHTML(formatDateTime(file.created_at))}</p>
                </div>
                <button type="button" class="icon-button" data-action="close-file-panel" aria-label="关闭详情">
                    ${renderIcon("close")}
                </button>
            </div>

            <div class="clipboard-item-badges">
                <span class="badge badge-primary">${isCurrentOrigin ? "本机上传" : "远端文件"}</span>
                <span class="badge badge-accent">${formatBytes(file.size_bytes)}</span>
            </div>

            <div class="data-list">
                ${renderDataRow("文件 ID", file.id, true)}
                ${renderDataRow("文件类型", file.content_type || "application/octet-stream")}
                ${renderDataRow("文件大小", formatBytes(file.size_bytes))}
                ${renderDataRow("SHA256", formatHash(file.file_sha256), true)}
                ${renderDataRow("来源设备 ID", file.origin_device_id || "-", true)}
                ${renderDataRow("来源设备名", file.origin_device_name || "-")}
                ${renderDataRow("上传时间", formatDateTime(file.created_at))}
            </div>

            <div class="device-panel-actions">
                <button type="button" class="button-primary" data-action="download-file" data-file-id="${escapeHTML(file.id)}" ${isPending("file-download") ? "disabled" : ""}>
                    ${isPending("file-download") ? "正在下载..." : "下载文件"}
                </button>
                <button type="button" class="button-secondary" data-action="open-file-rename" data-file-id="${escapeHTML(file.id)}" ${isPending("file-download") || isPending("file-delete") ? "disabled" : ""}>
                    重命名
                </button>
                <button type="button" class="button-danger" data-action="delete-file" data-file-id="${escapeHTML(file.id)}" ${isPending("file-download") || isPending("file-delete") ? "disabled" : ""}>
                    ${isPending("file-delete") ? "正在删除..." : "删除文件"}
                </button>
            </div>
        </aside>
    `;
}

function renderFileRenamePanel(file, panelState) {
    return `
        <div class="device-panel-backdrop" data-action="close-file-panel"></div>
        <aside class="device-panel file-panel">
            <div class="device-panel-header">
                <div>
                    <h2>${escapeHTML(file.original_name || "未命名文件")}</h2>
                    <p>重命名文件</p>
                </div>
                <button type="button" class="icon-button" data-action="close-file-panel" aria-label="关闭重命名">
                    ${renderIcon("close")}
                </button>
            </div>

            <form id="file-rename-form" class="form-grid">
                <div class="field">
                    <label for="file-original-name">文件名称</label>
                    <input
                        id="file-original-name"
                        name="original_name"
                        type="text"
                        maxlength="255"
                        value="${escapeAttribute(panelState.renameDraftName || file.original_name || "")}"
                        required
                    >
                </div>

                <div class="device-panel-actions">
                    <button type="submit" class="button-primary" ${isPending("file-rename") ? "disabled" : ""}>
                        ${isPending("file-rename") ? "正在保存..." : "保存名称"}
                    </button>
                    <button type="button" class="button-secondary" data-action="open-file-details" data-file-id="${escapeHTML(file.id)}" ${isPending("file-rename") ? "disabled" : ""}>
                        返回详情
                    </button>
                </div>
            </form>

            <div class="data-list">
                ${renderDataRow("文件类型", file.content_type || "application/octet-stream")}
                ${renderDataRow("文件大小", formatBytes(file.size_bytes))}
                ${renderDataRow("来源设备名", file.origin_device_name || "-")}
            </div>
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
                <button type="button" class="button-danger" data-action="delete-clipboard-item" data-item-id="${escapeHTML(item.id)}" ${isPending(`clipboard-delete:${item.id}`) ? "disabled" : ""}>
                    ${isPending(`clipboard-delete:${item.id}`) ? "正在删除..." : "删除记录"}
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
        case "shares":
            return renderShareSettings();
        case "history":
            return renderHistorySettings();
        case "security":
            return renderSecuritySettings();
        case "session":
            return renderSessionSettings();
        case "about":
            return renderAboutSettings();
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

function renderHistorySettings() {
    const retentionDays = Number(state.clipboard.retentionDays || 0);
    const maxStoredItems = Number(state.clipboard.maxStoredItems || 1000);
    const retentionText = retentionDays <= 0 ? "全部时间段" : `${retentionDays} 天`;

    return `
        <section class="settings-pane-section">
            <div class="settings-section-label">历史保留</div>
            <p class="settings-inline-note">保留时间为 0 表示不过期；最大记录数只保留最新的可见历史。</p>

            <form id="history-settings-form" class="settings-password-form">
                <div class="field">
                    <label for="history-retention-days">保留天数</label>
                    <input
                        id="history-retention-days"
                        name="retention_days"
                        type="number"
                        min="0"
                        step="1"
                        value="${escapeAttribute(retentionDays)}"
                        required
                    >
                </div>

                <div class="field">
                    <label for="history-limit">最大记录数</label>
                    <input
                        id="history-limit"
                        name="history_limit"
                        type="number"
                        min="1"
                        step="1"
                        value="${escapeAttribute(maxStoredItems)}"
                        required
                    >
                </div>

                <div class="settings-submit-row">
                    <button type="submit" class="button-primary" ${isPending("history-settings") ? "disabled" : ""}>
                        ${isPending("history-settings") ? "正在保存..." : "保存历史设置"}
                    </button>
                </div>
            </form>

            <div class="settings-compact-list">
                ${renderSettingsCompactRow("当前保留时间", retentionText)}
                ${renderSettingsCompactRow("当前最大记录数", String(maxStoredItems))}
                ${renderSettingsCompactRow("最近更新", state.clipboard.settingsUpdatedAt ? formatDateTime(state.clipboard.settingsUpdatedAt) : "-")}
            </div>
        </section>

        <section class="settings-pane-section">
            <div class="settings-section-label">批量清理</div>
            <div class="settings-action-row">
                <div class="settings-action-copy">
                    <strong>删除 N 天前记录</strong>
                    <span>只清理当前账号的文本历史，文件和分享不会受影响。</span>
                </div>
                <div class="settings-inline-controls">
                    <input
                        id="history-cleanup-days"
                        type="number"
                        min="1"
                        step="1"
                        value="${escapeAttribute(state.clipboard.cleanupDaysDraft || "30")}"
                        aria-label="清理天数"
                    >
                    <button type="button" class="button-secondary" data-action="cleanup-clipboard-history" ${isPending("history-cleanup") ? "disabled" : ""}>
                        ${isPending("history-cleanup") ? "正在清理..." : "清理"}
                    </button>
                </div>
            </div>

            <div class="settings-action-row">
                <div class="settings-action-copy">
                    <strong>清空全部文本历史</strong>
                    <span>只清空当前账号可见的文本历史，操作会软删除记录。</span>
                </div>
                <button type="button" class="button-danger" data-action="clear-clipboard-history" ${isPending("history-clear") ? "disabled" : ""}>
                    ${isPending("history-clear") ? "正在清空..." : "清空历史"}
                </button>
            </div>
        </section>
    `;
}

function renderShareSettings() {
    const panels = state.settingsModal.shareRulePanels || {};
    const rules = state.shareRules || {};

    return `
        <section class="settings-pane-section">
            <div class="settings-section-label">分享规则</div>
            <p class="settings-inline-note">文本和文件会复用这里的过期、焚毁和文字复制设置。密码不会持久化保存，每次创建时单独输入。</p>

            <div class="share-rule-panel-list">
                ${renderShareRulePanel("never", rules.never, panels.never, `
                    <div class="field field-inline">
                        <label class="checkbox-row" for="share-rule-never-allow-copy">
                            <input id="share-rule-never-allow-copy" type="checkbox" ${rules.never?.allowCopyText ? "checked" : ""}>
                            <span>允许公开页复制文字</span>
                        </label>
                    </div>
                `)}

                ${renderShareRulePanel("expire", rules.expire, panels.expire, `
                    <div class="field">
                        <label for="share-rule-expire-hours">过期时间（小时）</label>
                        <input
                            id="share-rule-expire-hours"
                            type="number"
                            min="1"
                            step="1"
                            value="${escapeAttribute(rules.expire?.expireHours || 24)}"
                        >
                    </div>

                    <div class="field field-inline">
                        <label class="checkbox-row" for="share-rule-expire-allow-copy">
                            <input id="share-rule-expire-allow-copy" type="checkbox" ${rules.expire?.allowCopyText ? "checked" : ""}>
                            <span>允许公开页复制文字</span>
                        </label>
                    </div>
                `)}

                ${renderShareRulePanel("once", rules.once, panels.once, `
                    <div class="field field-inline">
                        <label class="checkbox-row" for="share-rule-once-show-countdown">
                            <input id="share-rule-once-show-countdown" type="checkbox" ${rules.once?.showCountdown ? "checked" : ""}>
                            <span>首次打开后显示倒计时</span>
                        </label>
                    </div>

                    <div class="field">
                        <label for="share-rule-once-countdown-seconds">倒计时秒数</label>
                        <input
                            id="share-rule-once-countdown-seconds"
                            type="number"
                            min="1"
                            step="1"
                            value="${escapeAttribute(rules.once?.countdownSeconds || 10)}"
                            ${rules.once?.showCountdown ? "" : "disabled"}
                        >
                    </div>

                    <div class="field field-inline">
                        <label class="checkbox-row" for="share-rule-once-allow-copy">
                            <input id="share-rule-once-allow-copy" type="checkbox" ${rules.once?.allowCopyText ? "checked" : ""}>
                            <span>允许公开页复制文字</span>
                        </label>
                    </div>
                `)}
            </div>
        </section>
    `;
}

function renderShareRulePanel(ruleKey, rule, isOpen, bodyContent) {
    const summary = buildShareStrategySummary(ruleKey);
    return `
        <section class="share-rule-panel ${isOpen ? "is-open" : ""}">
            <button
                type="button"
                class="share-rule-panel-toggle"
                data-action="toggle-share-rule-panel"
                data-rule-key="${ruleKey}"
            >
                <span class="share-rule-panel-copy">
                    <strong>${escapeHTML(rule?.title || summary.title)}</strong>
                    <span>${escapeHTML(summary.description)}</span>
                </span>
                <span class="share-rule-panel-state">${isOpen ? "收起" : "展开"}</span>
            </button>

            ${isOpen ? `
                <div class="share-rule-panel-body">
                    ${bodyContent}
                </div>
            ` : ""}
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

function renderAboutSettings() {
    return `
        <section class="settings-pane-section">
            <div class="settings-compact-list">
                <div class="settings-action-row">
                    <div class="settings-action-copy">
                        <strong>GitHub 仓库</strong>
                        <span class="settings-compact-value is-mono">https://github.com/shuangboxu/ClipBridge</span>
                    </div>

                    <button type="button" class="button-secondary" data-action="open-project-link">
                        打开
                    </button>
                </div>

                ${renderSettingsCompactRow("Windows 下载", "即将开放")}
                ${renderSettingsCompactRow("Android 下载", "即将开放")}
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
