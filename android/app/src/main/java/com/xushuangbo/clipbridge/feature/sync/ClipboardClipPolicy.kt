package com.xushuangbo.clipbridge.feature.sync

import android.content.ClipData

const val CLIPBRIDGE_INTERNAL_LABEL_PREFIX = "clipbridge-"
const val CLIPBRIDGE_HISTORY_LABEL = "clipbridge-history-text"
const val CLIPBRIDGE_REMOTE_LABEL = "clipbridge-remote-text"

fun isInternalClipboardLabel(label: String?): Boolean {
    return label?.startsWith(CLIPBRIDGE_INTERNAL_LABEL_PREFIX, ignoreCase = true) == true
}

fun buildClipboardClipData(label: String, text: String): ClipData {
    return ClipData.newPlainText(label, text)
}
