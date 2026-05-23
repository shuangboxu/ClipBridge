export function formatDateTime(value) {
    if (!value) {
        return "-";
    }

    const date = new Date(value);
    if (Number.isNaN(date.getTime())) {
        return value;
    }

    const formatter = new Intl.DateTimeFormat("zh-CN", {
        year: "numeric",
        month: "2-digit",
        day: "2-digit",
        hour: "2-digit",
        minute: "2-digit",
        second: "2-digit",
        hour12: false
    });

    return formatter.format(date).replace(/\//g, "-");
}

export function escapeHTML(value) {
    return String(value ?? "")
        .replaceAll("&", "&amp;")
        .replaceAll("<", "&lt;")
        .replaceAll(">", "&gt;")
        .replaceAll("\"", "&quot;")
        .replaceAll("'", "&#39;");
}

export function escapeAttribute(value) {
    return escapeHTML(value);
}

export function toUserMessage(error) {
    if (!error) {
        return "请求失败，请稍后重试。";
    }

    const message = String(error.message || "请求失败，请稍后重试。");
    if (error.requestId) {
        return `${message}（request_id: ${error.requestId}）`;
    }
    return message;
}
