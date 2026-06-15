export const STORAGE_KEYS = {
    session: "clipbridge.web.session",
    sidebarCollapsed: "clipbridge.web.sidebar-collapsed",
    shareRules: "clipbridge.web.share-rules"
};

export const DEFAULT_ROUTE = "history";
export const AUTH_ROUTE = "login";

export const NAV_ITEMS = [
    { route: "history", title: "历史", icon: "history", ready: true },
    { route: "files", title: "文件", icon: "files", ready: true },
    { route: "devices", title: "设备", icon: "devices", ready: true },
    { route: "shares", title: "分享", icon: "shares", ready: true },
    { route: "requests", title: "申请", icon: "requests", ready: true },
    { route: "admin", title: "管理", icon: "admin", ready: true, adminOnly: true },
    { route: "ai", title: "AI", icon: "ai", ready: false }
];

export const PROTECTED_ROUTES = new Set(NAV_ITEMS.map((item) => item.route));

export function getRouteMeta(route) {
    switch (route) {
        case "history":
            return { title: "文本同步" };
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
        default:
            return { title: "文本同步" };
    }
}
