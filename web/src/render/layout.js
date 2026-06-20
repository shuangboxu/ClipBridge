import { AUTH_ROUTE, NAV_ITEMS, PROTECTED_ROUTES, REGISTER_ROUTE, getRouteMeta } from "../config/app.js";
import { state, isPending } from "../state/store.js";
import { createDefaultDeviceName, isMobileViewport } from "../utils/browser.js";
import { escapeAttribute, escapeHTML } from "../utils/format.js";
import { renderErrorMessage, renderLoadingState, renderToast } from "./common.js";
import { renderIcon } from "./icons.js";
import { renderCurrentPage, renderPublicSharePage, renderSettingsModal } from "./pages.js";

let lastRenderedRoute = "";

export function renderApp(appRoot) {
    if (!appRoot) {
        return;
    }

    // 整个应用目前还是全量 innerHTML 重绘。
    // 这里把常见滚动容器的位置先记住，再在同一路由的下一次重绘后恢复，
    // 避免点击复制、筛选之类的小动作时页面突然跳回顶部。
    const shouldRestoreScroll = lastRenderedRoute === state.route;
    const scrollSnapshot = shouldRestoreScroll ? captureScrollSnapshot(appRoot) : null;

    appRoot.innerHTML = state.route === "public-share"
        ? renderPublicShareLayout()
        : state.session && PROTECTED_ROUTES.has(state.route)
        ? renderProtectedLayout()
        : renderAuthLayout();

    if (shouldRestoreScroll && scrollSnapshot) {
        restoreScrollSnapshot(appRoot, scrollSnapshot);
    }

    lastRenderedRoute = state.route;
}

function captureScrollSnapshot(appRoot) {
    return {
        windowX: window.scrollX,
        windowY: window.scrollY,
        content: readElementScroll(appRoot, ".content-scroll"),
        settingsNav: readElementScroll(appRoot, ".settings-modal-nav"),
        settingsContent: readElementScroll(appRoot, ".settings-modal-content"),
        devicePanel: readElementScroll(appRoot, ".device-panel")
    };
}

function restoreScrollSnapshot(appRoot, snapshot) {
    window.requestAnimationFrame(() => {
        writeElementScroll(appRoot, ".content-scroll", snapshot.content);
        writeElementScroll(appRoot, ".settings-modal-nav", snapshot.settingsNav);
        writeElementScroll(appRoot, ".settings-modal-content", snapshot.settingsContent);
        writeElementScroll(appRoot, ".device-panel", snapshot.devicePanel);
        window.scrollTo(snapshot.windowX || 0, snapshot.windowY || 0);
    });
}

function readElementScroll(appRoot, selector) {
    const element = appRoot.querySelector(selector);
    if (!(element instanceof HTMLElement)) {
        return null;
    }

    return {
        top: element.scrollTop,
        left: element.scrollLeft
    };
}

function writeElementScroll(appRoot, selector, value) {
    if (!value) {
        return;
    }

    const element = appRoot.querySelector(selector);
    if (!(element instanceof HTMLElement)) {
        return;
    }

    element.scrollTop = value.top || 0;
    element.scrollLeft = value.left || 0;
}

function renderAuthLayout() {
    const isRegisterMode = state.route === REGISTER_ROUTE;
    const submitLabel = isRegisterMode ? "注册并进入" : "登录";
    const submitLoadingLabel = isRegisterMode ? "正在创建账号..." : "正在登录...";
    const authToast = renderToast(state.pageMessage);
    const authError = renderErrorMessage(state.pageError);
    const authPolicyMessage = renderAuthPolicyMessage(isRegisterMode);

    return `
        <main class="page-shell auth-stage">
            ${authToast}
            <section class="auth-panel auth-panel-single">
                <div class="auth-brand">
                    <img src="./assets/brand/app-icon.png" alt="ClipBridge">
                    <div>
                        <p class="brand-title brand-title-dark">ClipBridge</p>
                        <p class="brand-subtitle brand-subtitle-dark">${isRegisterMode ? "Web 注册" : "Web 登录"}</p>
                    </div>
                </div>

                <div class="panel-card auth-card">
                    <div class="auth-card-intro">
                        <h1>${isRegisterMode ? "注册" : "登录"}</h1>
                        <p class="panel-lead">设备自动识别：<code>${escapeHTML(createDefaultDeviceName())}</code></p>
                    </div>

                    <div class="auth-tabs" role="tablist" aria-label="登录与注册">
                        <button
                            type="button"
                            class="tab-button ${!isRegisterMode ? "is-active" : ""}"
                            data-action="navigate"
                            data-route="${AUTH_ROUTE}"
                        >
                            登录
                        </button>
                        <button
                            type="button"
                            class="tab-button ${isRegisterMode ? "is-active" : ""}"
                            data-action="navigate"
                            data-route="${REGISTER_ROUTE}"
                        >
                            注册
                        </button>
                    </div>

                    ${authPolicyMessage}

                    <form id="auth-form" class="form-grid">
                        <div class="field">
                            <label for="username">用户名</label>
                            <input id="username" name="username" type="text" minlength="3" maxlength="64" autocomplete="username" value="${escapeAttribute(state.authForm.username)}" required>
                        </div>

                        <div class="field">
                            <label for="password">密码</label>
                            <input id="password" name="password" type="password" minlength="8" maxlength="128" autocomplete="${isRegisterMode ? "new-password" : "current-password"}" value="${escapeAttribute(state.authForm.password)}" required>
                        </div>

                        ${isRegisterMode ? `
                            <div class="field">
                                <label for="confirm-password">确认密码</label>
                                <input id="confirm-password" name="confirm_password" type="password" minlength="8" maxlength="128" autocomplete="new-password" value="${escapeAttribute(state.authForm.confirmPassword)}" required>
                            </div>
                        ` : ""}

                        <div class="actions">
                            <button type="submit" class="button-primary" ${isPending("auth") ? "disabled" : ""}>
                                ${isPending("auth") ? submitLoadingLabel : submitLabel}
                            </button>
                        </div>
                    </form>

                    ${authError}
                </div>
            </section>
        </main>
    `;
}

function renderAuthPolicyMessage(isRegisterMode) {
    const allowRegistration = state.authRegistrationPolicy?.allowRegistration;
    if (allowRegistration === true && !isRegisterMode) {
        return `<p class="panel-lead">当前服务已开放公开注册，可直接切换到“注册”创建账号。</p>`;
    }
    if (allowRegistration === false && isRegisterMode) {
        return `<p class="panel-lead">当前服务可能已关闭公开注册；如果提交后返回 403，需要由管理员开放注册。</p>`;
    }
    return "";
}

function renderProtectedLayout() {
    const routeMeta = getRouteMeta(state.route);
    const visibleNavItems = getVisibleNavItems();
    const topbarTitle = String(routeMeta.title || "").trim();
    const shellClassParts = ["page-shell", "app-frame"];
    const sidebarToggleLabel = getSidebarToggleLabel();
    const desktopSidebarToggleIcon = state.sidebarCollapsed ? renderIcon("sidebar-open") : renderIcon("sidebar-collapse");
    const mobileSidebarToggleIcon = state.mobileSidebarOpen ? renderIcon("close") : renderIcon("menu");
    if (state.sidebarCollapsed) {
        shellClassParts.push("is-sidebar-collapsed");
    }
    if (state.mobileSidebarOpen) {
        shellClassParts.push("is-mobile-sidebar-open");
    }

    return `
        <div class="${shellClassParts.join(" ")}">
            ${renderToast(state.pageMessage)}
            <div class="sidebar-backdrop" data-action="close-sidebar"></div>
            <aside class="sidebar-shell">
                <div class="sidebar-header">
                    <div class="sidebar-brand">
                        <img src="./assets/brand/app-icon.png" alt="ClipBridge">
                        <div class="sidebar-brand-copy">
                            <p class="brand-title brand-title-dark">ClipBridge</p>
                            <p class="brand-subtitle brand-subtitle-dark">Web 控制台</p>
                        </div>
                    </div>

                    <button
                        type="button"
                        class="sidebar-toggle"
                        data-action="toggle-sidebar"
                        aria-label="${sidebarToggleLabel}"
                    >
                        ${desktopSidebarToggleIcon}
                    </button>
                </div>

                <nav class="sidebar-nav" aria-label="主导航">
                    ${visibleNavItems.map((item) => renderNavButton(item)).join("")}
                </nav>

                <div class="sidebar-footer">
                    <button
                        type="button"
                        class="sidebar-footer-button ${state.settingsModal.isOpen ? "is-active" : ""}"
                        data-action="open-settings"
                    >
                        ${renderIcon("settings")}
                        <span class="sidebar-nav-text">设置</span>
                    </button>
                </div>
            </aside>

            <div class="app-main">
                <header class="topbar-shell">
                    <div class="topbar-main">
                        <div class="topbar-left">
                            <button
                                type="button"
                                class="sidebar-toggle sidebar-toggle-mobile"
                                data-action="toggle-sidebar"
                                aria-label="${sidebarToggleLabel}"
                            >
                                ${mobileSidebarToggleIcon}
                            </button>

                            ${topbarTitle ? `
                                <div class="topbar-title">
                                    <h1>${topbarTitle}</h1>
                                </div>
                            ` : ""}
                        </div>

                        <div class="topbar-meta">
                            <span class="meta-chip"><strong>用户</strong> ${escapeHTML(state.profile?.user?.username || state.session?.user?.username || "-")}</span>
                            <span class="meta-chip"><strong>角色</strong> ${resolveUserRoleLabel()}</span>
                        </div>
                    </div>
                </header>

                <main class="content-scroll app-content">
                    <div class="page-grid">
                        ${renderErrorMessage(state.pageError)}
                        ${state.isBootstrapping ? renderLoadingState() : renderCurrentPage(state.route)}
                    </div>
                </main>
            </div>

            ${renderSettingsModal()}
        </div>
    `;
}

function getVisibleNavItems() {
    const isAdmin = Boolean(state.profile?.user?.is_admin || state.session?.user?.is_admin);
    return NAV_ITEMS.filter((item) => !item.adminOnly || isAdmin);
}

function resolveUserRoleLabel() {
    return Boolean(state.profile?.user?.is_admin || state.session?.user?.is_admin)
        ? "管理员"
        : "普通用户";
}

function renderPublicShareLayout() {
    return `
        <main class="page-shell public-share-shell-page">
            ${renderToast(state.pageMessage)}
            <div class="page-grid">
                ${renderErrorMessage(state.pageError)}
                ${state.isBootstrapping ? renderLoadingState() : renderPublicSharePage()}
            </div>
        </main>
    `;
}

function renderNavButton(item) {
    return `
        <button
            type="button"
            class="sidebar-nav-button ${state.route === item.route ? "is-active" : ""}"
            data-action="navigate"
            data-route="${item.route}"
        >
            <span class="sidebar-nav-icon">${renderIcon(item.icon)}</span>
            <span class="sidebar-nav-text">${item.title}</span>
            ${item.ready ? "" : '<span class="sidebar-nav-status">待接入</span>'}
        </button>
    `;
}

function getSidebarToggleLabel() {
    // 移动端是抽屉开关，桌面端才是展开/收起固定侧边栏。
    if (isMobileViewport()) {
        return state.mobileSidebarOpen ? "收起功能区" : "展开功能区";
    }
    return state.sidebarCollapsed ? "展开侧边栏" : "收起侧边栏";
}
