package com.xushuangbo.clipbridge.core.share

import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

object PublicShareLinkParser {
    fun extractToken(rawValue: String): String? {
        val normalizedValue = rawValue.trim()
        if (normalizedValue.isBlank()) {
            return null
        }

        extractTokenFromRoute(normalizedValue)?.let { return it }

        val parsedUri = runCatching { URI(normalizedValue) }.getOrNull()
        if (parsedUri != null) {
            extractTokenFromRoute(parsedUri.rawFragment.orEmpty())?.let { return it }
            extractTokenFromRoute(parsedUri.rawPath.orEmpty())?.let { return it }
        }

        // 兼容“二维码里直接放 token”的最简场景，
        // 这样本地调试时不必一定先拼完整的公开链接。
        return normalizedValue.takeIf {
            it.isNotBlank() &&
                !it.contains("://") &&
                !it.contains("#") &&
                !it.contains("?") &&
                !it.contains("/")
        }
    }

    private fun extractTokenFromRoute(route: String): String? {
        val normalizedRoute = route
            .trim()
            .removePrefix("#")
            .removePrefix("/")
            .substringBefore("?")

        if (!normalizedRoute.startsWith("public/")) {
            return null
        }

        val encodedToken = normalizedRoute.removePrefix("public/").substringBefore("/")
        if (encodedToken.isBlank()) {
            return null
        }

        return runCatching {
            URLDecoder.decode(encodedToken, StandardCharsets.UTF_8)
        }.getOrDefault(encodedToken).trim().ifBlank { null }
    }
}
