package com.xushuangbo.clipbridge.core.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ServiceAddressFormatterTest {
    @Test
    fun normalize_removesWhitespaceAndTrailingSlash() {
        assertEquals(
            "http://127.0.0.1:18080",
            ServiceAddressFormatter.normalize("  http://127.0.0.1:18080/  "),
        )
    }

    @Test
    fun validate_rejectsAddressWithoutScheme() {
        assertEquals(
            "服务地址必须以 http:// 或 https:// 开头",
            ServiceAddressFormatter.validate("127.0.0.1:18080"),
        )
    }

    @Test
    fun validate_acceptsStandardHttpAddress() {
        assertNull(ServiceAddressFormatter.validate("http://10.0.2.2:18080"))
    }
}
