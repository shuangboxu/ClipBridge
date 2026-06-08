package com.xushuangbo.clipbridge.core.share

import android.content.SharedPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ShareRulesStoreTest {
    @Test
    fun readRules_withoutSavedValueReturnsDefaults() {
        val store = PreferenceShareRulesStore(FakeSharedPreferences())

        val rules = store.readRules()

        assertEquals(ShareExpirePreset.OneDay, rules.expire.preset)
        assertTrue(rules.once.showCountdown)
        assertEquals(ShareCountdownPreset.TenSeconds, rules.once.countdownPreset)
        assertFalse(rules.never.allowCopyText)
    }

    @Test
    fun readRules_withBrokenJsonFallsBackToDefaults() {
        val preferences = FakeSharedPreferences().apply {
            edit().putString("share_rules", "{broken json").apply()
        }
        val store = PreferenceShareRulesStore(preferences)

        val rules = store.readRules()

        assertEquals(ShareExpirePreset.OneDay, rules.expire.preset)
        assertTrue(rules.once.showCountdown)
    }

    @Test
    fun saveRules_roundTripsCustomValues() {
        val preferences = FakeSharedPreferences()
        val store = PreferenceShareRulesStore(preferences)
        val expectedRules = ShareRuleConfig(
            never = ShareNeverRule(allowCopyText = true),
            expire = ShareExpireRule(
                preset = ShareExpirePreset.SevenDays,
                allowCopyText = true,
            ),
            once = ShareOnceRule(
                showCountdown = false,
                countdownPreset = ShareCountdownPreset.FiveMinutes,
                allowCopyText = true,
            ),
        )

        store.saveRules(expectedRules)
        val actualRules = store.readRules()

        assertEquals(expectedRules, actualRules)
    }
}

private class FakeSharedPreferences : SharedPreferences {
    private val values = mutableMapOf<String, Any?>()

    override fun getAll(): MutableMap<String, *> = values.toMutableMap()

    override fun getString(key: String?, defValue: String?): String? {
        return values[key] as? String ?: defValue
    }

    override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? {
        @Suppress("UNCHECKED_CAST")
        return values[key] as? MutableSet<String> ?: defValues
    }

    override fun getInt(key: String?, defValue: Int): Int {
        return values[key] as? Int ?: defValue
    }

    override fun getLong(key: String?, defValue: Long): Long {
        return values[key] as? Long ?: defValue
    }

    override fun getFloat(key: String?, defValue: Float): Float {
        return values[key] as? Float ?: defValue
    }

    override fun getBoolean(key: String?, defValue: Boolean): Boolean {
        return values[key] as? Boolean ?: defValue
    }

    override fun contains(key: String?): Boolean = values.containsKey(key)

    override fun edit(): SharedPreferences.Editor = Editor(values)

    override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit

    override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit

    private class Editor(
        private val values: MutableMap<String, Any?>,
    ) : SharedPreferences.Editor {
        private val updates = mutableMapOf<String, Any?>()
        private val removals = mutableSetOf<String>()
        private var shouldClear = false

        override fun putString(key: String?, value: String?): SharedPreferences.Editor = apply {
            if (key != null) {
                updates[key] = value
            }
        }

        override fun putStringSet(key: String?, values: MutableSet<String>?): SharedPreferences.Editor = apply {
            if (key != null) {
                updates[key] = values
            }
        }

        override fun putInt(key: String?, value: Int): SharedPreferences.Editor = apply {
            if (key != null) {
                updates[key] = value
            }
        }

        override fun putLong(key: String?, value: Long): SharedPreferences.Editor = apply {
            if (key != null) {
                updates[key] = value
            }
        }

        override fun putFloat(key: String?, value: Float): SharedPreferences.Editor = apply {
            if (key != null) {
                updates[key] = value
            }
        }

        override fun putBoolean(key: String?, value: Boolean): SharedPreferences.Editor = apply {
            if (key != null) {
                updates[key] = value
            }
        }

        override fun remove(key: String?): SharedPreferences.Editor = apply {
            if (key != null) {
                removals += key
            }
        }

        override fun clear(): SharedPreferences.Editor = apply {
            shouldClear = true
        }

        override fun commit(): Boolean {
            apply()
            return true
        }

        override fun apply() {
            if (shouldClear) {
                values.clear()
            }
            removals.forEach(values::remove)
            values.putAll(updates)
        }
    }
}
