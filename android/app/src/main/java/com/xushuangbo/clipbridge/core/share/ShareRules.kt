package com.xushuangbo.clipbridge.core.share

/**
 * 分享创建区固定只允许在“文本分享”和“文件分享”之间切换，
 * 这样页面逻辑更直观，也更符合当前后端的两条创建接口。
 */
enum class ShareComposeMode(
    val label: String,
) {
    Text("文本分享"),
    File("文件分享"),
}

enum class ShareStrategyKey(
    val storageKey: String,
    val label: String,
) {
    Never("never", "不过期"),
    Expire("expire", "过期"),
    Once("once", "打开一次失效");

    companion object {
        fun fromStorageKey(rawValue: String): ShareStrategyKey {
            return entries.find { it.storageKey == rawValue } ?: Expire
        }
    }
}

enum class ShareStatusFilter(
    val apiValue: String,
    val label: String,
) {
    All("all", "全部"),
    Active("active", "可访问"),
    Expired("expired", "已过期"),
    Consumed("consumed", "已焚毁"),
    Revoked("revoked", "已撤销");

    companion object {
        fun fromApiValue(rawValue: String): ShareStatusFilter {
            return entries.find { it.apiValue == rawValue } ?: All
        }
    }
}

enum class ShareExpirePreset(
    val storageKey: String,
    val label: String,
    val hours: Int,
) {
    OneHour("1h", "1 小时", 1),
    OneDay("24h", "24 小时", 24),
    SevenDays("7d", "7 天", 24 * 7);

    companion object {
        fun fromStorageKey(rawValue: String): ShareExpirePreset {
            return entries.find { it.storageKey == rawValue } ?: OneDay
        }
    }
}

enum class ShareCountdownPreset(
    val storageKey: String,
    val label: String,
    val seconds: Int,
) {
    TenSeconds("10s", "10 秒", 10),
    ThirtySeconds("30s", "30 秒", 30),
    OneMinute("60s", "60 秒", 60),
    FiveMinutes("300s", "5 分钟", 300);

    companion object {
        fun fromStorageKey(rawValue: String): ShareCountdownPreset {
            return entries.find { it.storageKey == rawValue } ?: TenSeconds
        }
    }
}

data class ShareNeverRule(
    val allowCopyText: Boolean = false,
)

data class ShareExpireRule(
    val preset: ShareExpirePreset = ShareExpirePreset.OneDay,
    val allowCopyText: Boolean = false,
)

data class ShareOnceRule(
    val showCountdown: Boolean = true,
    val countdownPreset: ShareCountdownPreset = ShareCountdownPreset.TenSeconds,
    val allowCopyText: Boolean = false,
)

data class ShareRuleConfig(
    val never: ShareNeverRule = ShareNeverRule(),
    val expire: ShareExpireRule = ShareExpireRule(),
    val once: ShareOnceRule = ShareOnceRule(),
)

data class ShareStrategySummary(
    val title: String,
    val description: String,
    val copyLabel: String,
)

data class SharePolicyPayload(
    val neverExpires: Boolean,
    val expireSeconds: Int,
    val burnMode: String,
    val burnAfterSeconds: Int,
    val allowCopyContent: Boolean,
)

/**
 * 这里把“页面上选中的策略”统一映射成后端真正需要的字段，
 * 这样 ViewModel 不需要到处写 if/else 拼接口参数。
 */
fun ShareRuleConfig.buildPolicyPayload(
    strategyKey: ShareStrategyKey,
    allowTextCopy: Boolean,
): SharePolicyPayload {
    return when (strategyKey) {
        ShareStrategyKey.Never -> SharePolicyPayload(
            neverExpires = true,
            expireSeconds = 0,
            burnMode = "none",
            burnAfterSeconds = 0,
            allowCopyContent = allowTextCopy && never.allowCopyText,
        )

        ShareStrategyKey.Expire -> SharePolicyPayload(
            neverExpires = false,
            expireSeconds = expire.preset.hours * 60 * 60,
            burnMode = "none",
            burnAfterSeconds = 0,
            allowCopyContent = allowTextCopy && expire.allowCopyText,
        )

        ShareStrategyKey.Once -> SharePolicyPayload(
            neverExpires = true,
            expireSeconds = 0,
            burnMode = if (once.showCountdown) "countdown" else "once",
            burnAfterSeconds = if (once.showCountdown) once.countdownPreset.seconds else 0,
            allowCopyContent = allowTextCopy && once.allowCopyText,
        )
    }
}

fun ShareRuleConfig.buildStrategySummary(strategyKey: ShareStrategyKey): ShareStrategySummary {
    return when (strategyKey) {
        ShareStrategyKey.Never -> ShareStrategySummary(
            title = ShareStrategyKey.Never.label,
            description = "公开链接不会自动过期，需要你手动撤销。",
            copyLabel = if (never.allowCopyText) "文字可复制" else "文字禁止复制",
        )

        ShareStrategyKey.Expire -> ShareStrategySummary(
            title = ShareStrategyKey.Expire.label,
            description = "${expire.preset.label} 后自动失效。",
            copyLabel = if (expire.allowCopyText) "文字可复制" else "文字禁止复制",
        )

        ShareStrategyKey.Once -> ShareStrategySummary(
            title = ShareStrategyKey.Once.label,
            description = if (once.showCountdown) {
                "首次打开后开始 ${once.countdownPreset.label} 倒计时，时间到后自动失效。"
            } else {
                "公开内容只允许成功打开一次。"
            },
            copyLabel = if (once.allowCopyText) "文字可复制" else "文字禁止复制",
        )
    }
}

fun buildDefaultShareRules(): ShareRuleConfig = ShareRuleConfig()
