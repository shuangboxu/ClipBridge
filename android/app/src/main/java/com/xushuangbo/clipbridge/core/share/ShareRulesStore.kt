package com.xushuangbo.clipbridge.core.share

import android.content.SharedPreferences

interface ShareRulesStore {
    fun readRules(): ShareRuleConfig

    fun saveRules(rules: ShareRuleConfig)
}

class PreferenceShareRulesStore(
    private val preferences: SharedPreferences,
) : ShareRulesStore {
    override fun readRules(): ShareRuleConfig {
        val fallback = buildDefaultShareRules()
        val rawValue = preferences.getString(KEY_SHARE_RULES, "").orEmpty()
        if (rawValue.isBlank()) {
            return fallback
        }

        // 这里故意不用 JSONObject，避免 JVM 单测依赖 Android stub。
        // 同时保留对旧 JSON 字符串的兼容，防止已有本地配置丢失。
        return parseRules(rawValue, fallback) ?: fallback
    }

    override fun saveRules(rules: ShareRuleConfig) {
        val payload = buildString {
            appendLine("version=1")
            appendLine("never.allow_copy_text=${rules.never.allowCopyText}")
            appendLine("expire.preset=${rules.expire.preset.storageKey}")
            appendLine("expire.allow_copy_text=${rules.expire.allowCopyText}")
            appendLine("once.show_countdown=${rules.once.showCountdown}")
            appendLine("once.countdown_preset=${rules.once.countdownPreset.storageKey}")
            append("once.allow_copy_text=${rules.once.allowCopyText}")
        }

        preferences.edit()
            .putString(KEY_SHARE_RULES, payload.toString())
            .apply()
    }

    private fun parseRules(
        rawValue: String,
        fallback: ShareRuleConfig,
    ): ShareRuleConfig? {
        val values = when {
            rawValue.contains('=') -> parseKeyValuePayload(rawValue)
            rawValue.trimStart().startsWith("{") -> parseLegacyJsonPayload(rawValue)
            else -> emptyMap()
        }

        if (values.isEmpty()) {
            return null
        }

        return ShareRuleConfig(
            never = ShareNeverRule(
                allowCopyText = values.readBoolean(
                    key = "never.allow_copy_text",
                    fallback = fallback.never.allowCopyText,
                ),
            ),
            expire = ShareExpireRule(
                preset = ShareExpirePreset.fromStorageKey(
                    values["expire.preset"] ?: fallback.expire.preset.storageKey,
                ),
                allowCopyText = values.readBoolean(
                    key = "expire.allow_copy_text",
                    fallback = fallback.expire.allowCopyText,
                ),
            ),
            once = ShareOnceRule(
                showCountdown = values.readBoolean(
                    key = "once.show_countdown",
                    fallback = fallback.once.showCountdown,
                ),
                countdownPreset = ShareCountdownPreset.fromStorageKey(
                    values["once.countdown_preset"] ?: fallback.once.countdownPreset.storageKey,
                ),
                allowCopyText = values.readBoolean(
                    key = "once.allow_copy_text",
                    fallback = fallback.once.allowCopyText,
                ),
            ),
        )
    }

    private fun parseKeyValuePayload(rawValue: String): Map<String, String> {
        return rawValue.lineSequence()
            .mapNotNull { line ->
                val separatorIndex = line.indexOf('=')
                if (separatorIndex <= 0) {
                    return@mapNotNull null
                }

                val key = line.substring(0, separatorIndex).trim()
                val value = line.substring(separatorIndex + 1).trim()
                if (key.isBlank()) {
                    null
                } else {
                    key to value
                }
            }
            .toMap()
    }

    private fun parseLegacyJsonPayload(rawValue: String): Map<String, String> {
        val values = mutableMapOf<String, String>()
        val neverSection = rawValue.findJsonSection("never")
        val expireSection = rawValue.findJsonSection("expire")
        val onceSection = rawValue.findJsonSection("once")

        neverSection.extractJsonBoolean("allow_copy_text")?.let { value ->
            values["never.allow_copy_text"] = value.toString()
        }
        expireSection.extractJsonString("preset")?.let { value ->
            values["expire.preset"] = value
        }
        expireSection.extractJsonBoolean("allow_copy_text")?.let { value ->
            values["expire.allow_copy_text"] = value.toString()
        }
        onceSection.extractJsonBoolean("show_countdown")?.let { value ->
            values["once.show_countdown"] = value.toString()
        }
        onceSection.extractJsonString("countdown_preset")?.let { value ->
            values["once.countdown_preset"] = value
        }
        onceSection.extractJsonBoolean("allow_copy_text")?.let { value ->
            values["once.allow_copy_text"] = value.toString()
        }

        return values
    }

    private fun Map<String, String>.readBoolean(
        key: String,
        fallback: Boolean,
    ): Boolean {
        return this[key]?.toBooleanStrictOrNull() ?: fallback
    }

    private fun String.findJsonSection(name: String): String {
        val regex = Regex("\"$name\"\\s*:\\s*\\{([^}]*)}")
        return regex.find(this)?.groupValues?.getOrNull(1).orEmpty()
    }

    private fun String.extractJsonString(key: String): String? {
        val regex = Regex("\"$key\"\\s*:\\s*\"([^\"]*)\"")
        return regex.find(this)?.groupValues?.getOrNull(1)
    }

    private fun String.extractJsonBoolean(key: String): Boolean? {
        val regex = Regex("\"$key\"\\s*:\\s*(true|false)", RegexOption.IGNORE_CASE)
        return regex.find(this)?.groupValues?.getOrNull(1)?.lowercase()?.toBooleanStrictOrNull()
    }

    private companion object {
        const val KEY_SHARE_RULES = "share_rules"
    }
}
