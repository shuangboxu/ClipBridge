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

export function buildWebSocketURL(baseUrl, path, query = {}) {
    const normalizedBaseUrl = trimServerBaseUrl(baseUrl || detectDefaultServerBaseUrl());
    if (!normalizedBaseUrl) {
        return "";
    }

    let url;
    try {
        url = new URL(path, normalizedBaseUrl);
    } catch (error) {
        return "";
    }

    url.protocol = url.protocol === "https:" ? "wss:" : "ws:";

    Object.entries(query).forEach(([key, value]) => {
        if (value === undefined || value === null || value === "") {
            return;
        }
        url.searchParams.set(key, String(value));
    });

    return url.toString();
}

export async function readTextFromClipboard() {
    if (!window.isSecureContext || !navigator.clipboard?.readText) {
        throw new Error("当前浏览器不支持读取系统剪切板。");
    }

    try {
        return await navigator.clipboard.readText();
    } catch (error) {
        throw new Error("读取系统剪切板失败，请检查浏览器权限。");
    }
}

export async function writeTextToClipboard(value) {
    const text = String(value || "");

    if (window.isSecureContext && navigator.clipboard?.writeText) {
        try {
            await navigator.clipboard.writeText(text);
            return;
        } catch (error) {
            // 权限被拒绝时继续走降级方案，避免直接失败。
        }
    }

    const textarea = document.createElement("textarea");
    textarea.value = text;
    textarea.setAttribute("readonly", "readonly");
    textarea.style.position = "fixed";
    textarea.style.top = "0";
    textarea.style.left = "-9999px";
    document.body.appendChild(textarea);

    textarea.focus();
    textarea.select();
    textarea.setSelectionRange(0, textarea.value.length);

    const copied = document.execCommand("copy");
    document.body.removeChild(textarea);

    if (!copied) {
        throw new Error("复制文本失败，请检查浏览器剪切板权限。");
    }
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
