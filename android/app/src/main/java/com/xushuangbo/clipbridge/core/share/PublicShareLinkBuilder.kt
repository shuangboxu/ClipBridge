package com.xushuangbo.clipbridge.core.share

import com.xushuangbo.clipbridge.core.network.ServiceAddressFormatter
import java.net.URI
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

object PublicShareLinkBuilder {
    /**
     * Android 这边拿到的是“服务地址”，不是浏览器当前地址，
     * 所以这里需要主动把 token 拼成 Web 公开取件页的 hash 路由。
     */
    fun build(
        baseUrl: String,
        publicToken: String,
    ): String? {
        val normalizedBaseUrl = ServiceAddressFormatter.normalize(baseUrl)
        val normalizedToken = publicToken.trim()
        if (normalizedBaseUrl.isBlank() || normalizedToken.isBlank()) {
            return null
        }

        val parsedUri = runCatching { URI(normalizedBaseUrl) }.getOrNull() ?: return null
        val scheme = parsedUri.scheme ?: return null
        val authority = parsedUri.rawAuthority ?: return null
        val path = parsedUri.rawPath.orEmpty().removeSuffix("/")
        val routeBase = buildString {
            append("$scheme://$authority")
            append(path)
        }

        return "${routeBase.ifBlank { "$scheme://$authority" }}/#/public/${encodePathSegment(normalizedToken)}"
    }

    private fun encodePathSegment(value: String): String {
        return URLEncoder.encode(value, StandardCharsets.UTF_8)
            .replace("+", "%20")
    }
}
