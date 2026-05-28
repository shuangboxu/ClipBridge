export function renderIcon(name) {
    const icons = {
        dashboard: `
            <svg viewBox="0 0 24 24" aria-hidden="true">
                <rect x="3" y="3" width="7" height="7" rx="1.5"></rect>
                <rect x="14" y="3" width="7" height="11" rx="1.5"></rect>
                <rect x="3" y="14" width="7" height="7" rx="1.5"></rect>
                <rect x="14" y="17" width="7" height="4" rx="1.5"></rect>
            </svg>
        `,
        history: `
            <svg viewBox="0 0 24 24" aria-hidden="true">
                <path d="M4 12a8 8 0 1 0 2.4-5.7"></path>
                <path d="M4 4v5h5"></path>
                <path d="M12 8v5l3 2"></path>
            </svg>
        `,
        devices: `
            <svg viewBox="0 0 24 24" aria-hidden="true">
                <rect x="3" y="4" width="11" height="8" rx="1.5"></rect>
                <rect x="7" y="14" width="6" height="2" rx="1"></rect>
                <rect x="16" y="7" width="5" height="10" rx="1.5"></rect>
            </svg>
        `,
        files: `
            <svg viewBox="0 0 24 24" aria-hidden="true">
                <path d="M4 6.5A1.5 1.5 0 0 1 5.5 5H10l2 2h6.5A1.5 1.5 0 0 1 20 8.5v9A1.5 1.5 0 0 1 18.5 19h-13A1.5 1.5 0 0 1 4 17.5z"></path>
            </svg>
        `,
        shares: `
            <svg viewBox="0 0 24 24" aria-hidden="true">
                <circle cx="6" cy="12" r="2.5"></circle>
                <circle cx="18" cy="6" r="2.5"></circle>
                <circle cx="18" cy="18" r="2.5"></circle>
                <path d="M8.3 10.9l7-3.8"></path>
                <path d="M8.3 13.1l7 3.8"></path>
            </svg>
        `,
        requests: `
            <svg viewBox="0 0 24 24" aria-hidden="true">
                <path d="M7 3h7l4 4v14H7a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2z"></path>
                <path d="M14 3v5h5"></path>
                <path d="M9 12h6"></path>
                <path d="M9 16h6"></path>
            </svg>
        `,
        admin: `
            <svg viewBox="0 0 24 24" aria-hidden="true">
                <path d="M12 3l7 3v5c0 4.5-3 8.4-7 10-4-1.6-7-5.5-7-10V6z"></path>
                <path d="M9.5 12l1.8 1.8L15 10.2"></path>
            </svg>
        `,
        ai: `
            <svg viewBox="0 0 24 24" aria-hidden="true">
                <path d="M12 3l1.7 4.3L18 9l-4.3 1.7L12 15l-1.7-4.3L6 9l4.3-1.7z"></path>
                <path d="M19 14l.9 2.1L22 17l-2.1.9L19 20l-.9-2.1L16 17l2.1-.9z"></path>
                <path d="M5 14l.9 2.1L8 17l-2.1.9L5 20l-.9-2.1L2 17l2.1-.9z"></path>
            </svg>
        `,
        settings: `
            <svg viewBox="0 0 24 24" aria-hidden="true">
                <circle cx="12" cy="12" r="3"></circle>
                <path d="M19.4 15a1 1 0 0 0 .2 1.1l.1.1a2 2 0 1 1-2.8 2.8l-.1-.1a1 1 0 0 0-1.1-.2 1 1 0 0 0-.6.9V20a2 2 0 1 1-4 0v-.1a1 1 0 0 0-.6-.9 1 1 0 0 0-1.1.2l-.1.1a2 2 0 0 1-2.8-2.8l.1-.1a1 1 0 0 0 .2-1.1 1 1 0 0 0-.9-.6H4a2 2 0 1 1 0-4h.1a1 1 0 0 0 .9-.6 1 1 0 0 0-.2-1.1l-.1-.1a2 2 0 0 1 2.8-2.8l.1.1a1 1 0 0 0 1.1.2 1 1 0 0 0 .6-.9V4a2 2 0 1 1 4 0v.1a1 1 0 0 0 .6.9 1 1 0 0 0 1.1-.2l.1-.1a2 2 0 0 1 2.8 2.8l-.1.1a1 1 0 0 0-.2 1.1 1 1 0 0 0 .9.6H20a2 2 0 1 1 0 4h-.1a1 1 0 0 0-.9.6z"></path>
            </svg>
        `,
        security: `
            <svg viewBox="0 0 24 24" aria-hidden="true">
                <path d="M12 3l7 3v5c0 4.5-3 8.4-7 10-4-1.6-7-5.5-7-10V6z"></path>
                <path d="M10 12.5l1.3 1.3L14.8 10"></path>
            </svg>
        `,
        session: `
            <svg viewBox="0 0 24 24" aria-hidden="true">
                <rect x="3" y="4" width="18" height="14" rx="2"></rect>
                <path d="M8 20h8"></path>
                <path d="M12 18v2"></path>
                <path d="M8.5 10.5h7"></path>
            </svg>
        `,
        "sidebar-open": `
            <svg viewBox="0 0 24 24" aria-hidden="true">
                <rect x="3" y="4" width="18" height="16" rx="2"></rect>
                <path d="M9 4v16"></path>
                <path d="M13 9l4 3-4 3"></path>
            </svg>
        `,
        "sidebar-collapse": `
            <svg viewBox="0 0 24 24" aria-hidden="true">
                <rect x="3" y="4" width="18" height="16" rx="2"></rect>
                <path d="M9 4v16"></path>
                <path d="M16 9l-4 3 4 3"></path>
            </svg>
        `,
        menu: `
            <svg viewBox="0 0 24 24" aria-hidden="true">
                <path d="M4 7h16"></path>
                <path d="M4 12h16"></path>
                <path d="M4 17h16"></path>
            </svg>
        `,
        close: `
            <svg viewBox="0 0 24 24" aria-hidden="true">
                <path d="M6 6l12 12"></path>
                <path d="M18 6l-12 12"></path>
            </svg>
        `,
        view: `
            <svg viewBox="0 0 24 24" aria-hidden="true">
                <path d="M2.5 12s3.5-6 9.5-6 9.5 6 9.5 6-3.5 6-9.5 6-9.5-6-9.5-6z"></path>
                <circle cx="12" cy="12" r="2.8"></circle>
            </svg>
        `,
        edit: `
            <svg viewBox="0 0 24 24" aria-hidden="true">
                <path d="M3 17.25V21h3.75L18.8 8.95l-3.75-3.75z"></path>
                <path d="M14.95 5.2l3.75 3.75"></path>
            </svg>
        `,
        upload: `
            <svg viewBox="0 0 24 24" aria-hidden="true">
                <path d="M12 16V5"></path>
                <path d="M8 9l4-4 4 4"></path>
                <path d="M5 19h14"></path>
            </svg>
        `,
        copy: `
            <svg viewBox="0 0 24 24" aria-hidden="true">
                <rect x="9" y="9" width="11" height="11" rx="2"></rect>
                <path d="M6 15H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h8a2 2 0 0 1 2 2v1"></path>
            </svg>
        `,
        logout: `
            <svg viewBox="0 0 24 24" aria-hidden="true">
                <path d="M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4"></path>
                <path d="M16 17l5-5-5-5"></path>
                <path d="M21 12H9"></path>
            </svg>
        `
    };

    return icons[name] || icons.dashboard;
}
