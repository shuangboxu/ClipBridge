import { AUTH_ROUTE, NAV_ITEMS, PROTECTED_ROUTES, getRouteMeta } from "../config/app.js";
import { state, isPending } from "../state/store.js";
import { createDefaultDeviceName, isMobileViewport } from "../utils/browser.js";
import { escapeAttribute, escapeHTML } from "../utils/format.js";
import { renderErrorMessage, renderLoadingState, renderToast } from "./common.js";
import { renderIcon } from "./icons.js";
import { renderCurrentPage } from "./pages.js";

export function renderApp(appRoot) {
    if (!appRoot) {
        return;
    }

    appRoot.innerHTML = state.session && PROTECTED_ROUTES.has(state.route)
        ? renderProtectedLayout()
        : renderAuthLayout();
}

function renderAuthLayout() {
    const submitLabel = "登录";
    const submitLoadingLabel = "正在登录...";
    const authToast = renderToast(state.pageMessage);
    const authError = renderErrorMessage(state.pageError);

    return `
        <main class="page-shell auth-stage">
            ${authToast}
            <section class="auth-panel auth-panel-single">
                <div class="auth-brand">
                    <img src="./assets/brand/app-icon.png" alt="ClipBridge">
                    <div>
                        <p class="brand-title brand-title-dark">ClipBridge</p>
                        <p class="brand-subtitle brand-subtitle-dark">Web 登录</p>
                    </div>
                </div>

                <div class="panel-card auth-card">
                    <div class="auth-card-intro">
                        <h1>登录</h1>
                        <p class="panel-lead">设备自动识别：<code>${escapeHTML(createDefaultDeviceName())}</code></p>
                    </div>

                    <form id="auth-form" class="form-grid">
                        <div class="field">
                            <label for="username">用户名</label>
                            <input id="username" name="username" type="text" minlength="3" maxlength="64" autocomplete="username" value="${escapeAttribute(state.authForm.username)}" required>
                        </div>

                        <div class="field">
                            <label for="password">密码</label>
                            <input id="password" name="password" type="password" minlength="8" maxlength="128" autocomplete="current-password" value="${escapeAttribute(state.authForm.password)}" required>
                        </div>

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

function renderProtectedLayout() {
    const routeMeta = getRouteMeta(state.route);
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
                    ${NAV_ITEMS.map((item) => renderNavButton(item)).join("")}
                </nav>

                <div class="sidebar-footer">
                    <button type="button" class="sidebar-logout" data-action="logout" ${isPending("logout") ? "disabled" : ""}>
                        ${renderIcon("logout")}
                        <span class="sidebar-nav-text">${isPending("logout") ? "正在退出..." : "退出登录"}</span>
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
        </div>
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
