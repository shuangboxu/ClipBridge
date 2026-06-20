package com.xushuangbo.clipbridge.core.share

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PublicShareLinkParserTest {
    @Test
    fun extractToken_fromFullPublicLinkReturnsDecodedToken() {
        val token = PublicShareLinkParser.extractToken(
            "https://clipbridge.example.com/app/#/public/token%201",
        )

        assertEquals("token 1", token)
    }

    @Test
    fun extractToken_fromRouteOnlyReturnsToken() {
        val token = PublicShareLinkParser.extractToken("#/public/demo-token")

        assertEquals("demo-token", token)
    }

    @Test
    fun extractToken_fromRawTokenReturnsSameValue() {
        val token = PublicShareLinkParser.extractToken("plain-token")

        assertEquals("plain-token", token)
    }

    @Test
    fun extractToken_fromInvalidLinkReturnsNull() {
        assertNull(PublicShareLinkParser.extractToken("https://clipbridge.example.com/history"))
        assertNull(PublicShareLinkParser.extractToken(""))
    }
}
