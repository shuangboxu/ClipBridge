import { state, updateSessionTokens } from "../state/store.js";

export async function request(path, options = {}) {
    const baseUrl = normalizeServerBaseUrl(options.baseUrl || state.serverBaseUrl);
    if (!baseUrl) {
        throw new Error("服务地址未初始化。");
    }

    const headers = new Headers({
        "Content-Type": "application/json"
    });

    if (options.withAuth !== false && state.session?.tokens?.access_token) {
        headers.set("Authorization", `Bearer ${state.session.tokens.access_token}`);
    }

    let response;
    try {
        response = await fetch(`${baseUrl}${path}`, {
            method: options.method || "GET",
            headers,
            body: options.body ? JSON.stringify(options.body) : undefined
        });
    } catch (error) {
        throw new Error("无法连接服务。");
    }

    // access token 过期后，统一先走 refresh，再透明重试一次原请求。
    if (response.status === 401 && options.withAuth !== false && !options._retry) {
        const refreshed = await refreshSession();
        if (refreshed) {
            return request(path, {
                ...options,
                _retry: true
            });
        }
    }

    const payload = await parseJSON(response);
    if (!response.ok || payload?.code !== 0) {
        throw buildAPIError(response.status, payload);
    }

    return payload.data || {};
}

export async function ensureValidAccessToken(minTTLSeconds = 60) {
    if (!state.session?.tokens?.access_token) {
        return false;
    }

    const expiresAt = Date.parse(state.session.tokens.access_token_expires_at || "");
    if (Number.isFinite(expiresAt) && expiresAt - Date.now() > minTTLSeconds * 1000) {
        return true;
    }

    return refreshSession();
}

async function refreshSession() {
    if (!state.session?.tokens?.refresh_token) {
        return false;
    }

    try {
        const data = await request("/v1/auth/refresh", {
            method: "POST",
            withAuth: false,
            body: {
                refresh_token: state.session.tokens.refresh_token
            },
            _retry: true
        });

        updateSessionTokens(data.tokens);
        return true;
    } catch (error) {
        console.warn("refresh session failed", error);
        return false;
    }
}

function normalizeServerBaseUrl(value) {
    return String(value || "").trim().replace(/\/+$/, "");
}

function parseJSON(response) {
    return response.text().then((text) => {
        if (!text) {
            return null;
        }
        try {
            return JSON.parse(text);
        } catch (error) {
            return null;
        }
    });
}

function buildAPIError(status, payload) {
    const message = payload?.message || `请求失败，HTTP ${status}`;
    const error = new Error(message);
    error.status = status;
    error.requestId = payload?.request_id || "";
    return error;
}
