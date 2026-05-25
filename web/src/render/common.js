import { escapeHTML } from "../utils/format.js";

export function renderDataRow(label, value, isMono = false) {
    return `
        <div class="data-row">
            <span class="data-key">${escapeHTML(label)}</span>
            <span class="data-value ${isMono ? "mono" : ""}">${escapeHTML(value || "-")}</span>
        </div>
    `;
}

export function renderLoadingState() {
    return `
        <section class="loading-card hero-card">
            <div>
                <div class="skeleton is-medium"></div>
                <div class="skeleton"></div>
                <div class="skeleton"></div>
            </div>
            <div>
                <div class="skeleton is-short"></div>
                <div class="skeleton"></div>
                <div class="skeleton"></div>
            </div>
        </section>
    `;
}

export function renderErrorMessage(errorMessage) {
    if (errorMessage) {
        return `<div class="message message-error">${escapeHTML(errorMessage)}</div>`;
    }
    return "";
}

export function renderToast(message) {
    if (!message) {
        return "";
    }

    return `
        <div class="toast-stack" aria-live="polite" aria-atomic="true">
            <div class="toast toast-success">${escapeHTML(message)}</div>
        </div>
    `;
}

export function renderModuleTile(item) {
    return `
        <button
            type="button"
            class="module-tile ${item.ready ? "" : "is-pending"}"
            data-action="navigate"
            data-route="${item.route}"
        >
            <strong>${item.title}</strong>
            <span class="badge ${item.ready ? "badge-primary" : "badge-warning"}">${item.ready ? "可用" : "待接入"}</span>
        </button>
    `;
}
