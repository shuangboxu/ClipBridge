package com.xushuangbo.clipbridge.core.share

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PublicShareLinkBuilderTest {
    @Test
    fun build_withRootAddressReturnsPublicRoute() {
        val link = PublicShareLinkBuilder.build(
            baseUrl = "https://clipbridge.example.com",
            publicToken = "token-1",
        )

        assertEquals("https://clipbridge.example.com/#/public/token-1", link)
    }

    @Test
    fun build_withSubPathPreservesExistingPath() {
        val link = PublicShareLinkBuilder.build(
            baseUrl = "https://clipbridge.example.com/app",
            publicToken = "token-1",
        )

        assertEquals("https://clipbridge.example.com/app/#/public/token-1", link)
    }

    @Test
    fun build_withTrailingSlashRemovesDuplicateSlash() {
        val link = PublicShareLinkBuilder.build(
            baseUrl = "https://clipbridge.example.com/app/",
            publicToken = "token 1",
        )

        assertEquals("https://clipbridge.example.com/app/#/public/token%201", link)
    }

    @Test
    fun build_withInvalidInputReturnsNull() {
        assertNull(PublicShareLinkBuilder.build("", "token-1"))
        assertNull(PublicShareLinkBuilder.build("not a url", "token-1"))
        assertNull(PublicShareLinkBuilder.build("https://clipbridge.example.com", ""))
    }
}
