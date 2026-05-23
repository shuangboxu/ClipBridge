export function isMobileViewport() {
    return window.matchMedia("(max-width: 760px)").matches;
}

export function createDefaultDeviceName() {
    const platform = navigator.platform || "Unknown Platform";
    const browserName = detectBrowserName();
    return `${browserName} on ${platform}`;
}

export function detectDefaultServerBaseUrl() {
    const locationOrigin = window.location.origin || "";
    const hostname = window.location.hostname || "";

    // 本地静态开发通常跑在 4173 之类的端口上，后端还是单独监听 18080。
    // 部署到服务器后，前端和 API 会一起挂在 nginx 下，此时默认直接走当前 origin。
    if (!locationOrigin || hostname === "127.0.0.1" || hostname === "localhost") {
        return "http://127.0.0.1:18080";
    }

    return trimServerBaseUrl(locationOrigin);
}

function detectBrowserName() {
    const userAgent = navigator.userAgent.toLowerCase();
    if (userAgent.includes("edg/")) {
        return "Edge";
    }
    if (userAgent.includes("chrome/")) {
        return "Chrome";
    }
    if (userAgent.includes("safari/") && !userAgent.includes("chrome/")) {
        return "Safari";
    }
    if (userAgent.includes("firefox/")) {
        return "Firefox";
    }
    return "Browser";
}

function trimServerBaseUrl(value) {
    return String(value || "").trim().replace(/\/+$/, "");
}
