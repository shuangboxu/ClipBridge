export const STORAGE_KEYS = {
    session: "clipbridge.web.session",
    sidebarCollapsed: "clipbridge.web.sidebar-collapsed"
};

export const DEFAULT_ROUTE = "dashboard";
export const AUTH_ROUTE = "login";

export const NAV_ITEMS = [
    { route: "dashboard", title: "总览", icon: "dashboard", ready: true },
    { route: "history", title: "历史", icon: "history", ready: false },
    { route: "devices", title: "设备", icon: "devices", ready: true },
    { route: "files", title: "文件", icon: "files", ready: false },
    { route: "shares", title: "分享", icon: "shares", ready: false },
    { route: "requests", title: "申请", icon: "requests", ready: false },
    { route: "admin", title: "管理", icon: "admin", ready: false },
    { route: "ai", title: "AI", icon: "ai", ready: false },
    { route: "settings", title: "设置", icon: "settings", ready: true }
];

export const PROTECTED_ROUTES = new Set(NAV_ITEMS.map((item) => item.route));

export function getRouteMeta(route) {
    switch (route) {
        case "history":
            return { title: "历史记录" };
        case "devices":
            return { title: "设备中心" };
        case "files":
            return { title: "文件中心" };
        case "shares":
            return { title: "分享管理" };
        case "requests":
            return { title: "申请记录" };
        case "admin":
            return { title: "管理员" };
        case "ai":
            return { title: "AI 工具" };
        case "settings":
            return { title: "设置" };
        case "dashboard":
        default:
            return { title: "总览" };
    }
}
