package com.xushuangbo.clipbridge.core.network

import java.net.URI

object ServiceAddressFormatter {
    fun normalize(rawAddress: String): String {
        return rawAddress.trim().removeSuffix("/")
    }

    fun validate(rawAddress: String): String? {
        val normalizedAddress = normalize(rawAddress)

        if (normalizedAddress.isBlank()) {
            return "请先填写服务地址"
        }
        if (!normalizedAddress.startsWith("http://") && !normalizedAddress.startsWith("https://")) {
            return "服务地址必须以 http:// 或 https:// 开头"
        }

        val parsedAddress = runCatching { URI(normalizedAddress) }.getOrNull()
            ?: return "服务地址格式不正确"

        if (parsedAddress.host.isNullOrBlank()) {
            return "服务地址缺少主机名或 IP"
        }

        return null
    }
}
