package com.xushuangbo.clipbridge.feature.sync

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ClipboardClipPolicyInstrumentedTest {
    @Test
    fun buildClipboardClipData_keepsLabelAndText() {
        val clip = buildClipboardClipData("clipbridge-test", "hello")

        assertEquals("clipbridge-test", clip.description.label.toString())
        assertEquals("hello", clip.getItemAt(0).text.toString())
    }
}
