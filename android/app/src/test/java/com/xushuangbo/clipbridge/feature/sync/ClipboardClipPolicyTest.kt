package com.xushuangbo.clipbridge.feature.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ClipboardClipPolicyTest {
    @Test
    fun internalLabelPolicy_matchesClipBridgePrefix() {
        assertTrue(isInternalClipboardLabel(CLIPBRIDGE_HISTORY_LABEL))
        assertTrue(isInternalClipboardLabel(CLIPBRIDGE_REMOTE_LABEL))
    }

    @Test
    fun internalLabelPolicy_rejectsExternalLabel() {
        assertEquals(false, isInternalClipboardLabel("plain-text"))
        assertEquals(false, isInternalClipboardLabel(null))
    }
}
