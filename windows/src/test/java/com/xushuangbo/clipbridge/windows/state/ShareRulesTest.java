package com.xushuangbo.clipbridge.windows.state;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShareRulesTest {
    @Test
    void defaultExpireRule_buildsOneDayPolicy() {
        ShareRules.Config rules = ShareRules.defaultConfig();
        ShareRules.PolicyPayload payload = rules.buildPolicyPayload(ShareRules.StrategyKey.EXPIRE, true);

        assertFalse(payload.neverExpires());
        assertEquals(24 * 60 * 60, payload.expireSeconds());
        assertEquals("none", payload.burnMode());
        assertFalse(payload.allowCopyContent());
    }

    @Test
    void onceRule_withCountdown_buildsCountdownPayload() {
        ShareRules.Config rules = new ShareRules.Config(
            new ShareRules.NeverRule(false),
            new ShareRules.ExpireRule(ShareRules.ExpirePreset.ONE_DAY, false),
            new ShareRules.OnceRule(true, ShareRules.CountdownPreset.ONE_MINUTE, true)
        );

        ShareRules.PolicyPayload payload = rules.buildPolicyPayload(ShareRules.StrategyKey.ONCE, true);

        assertTrue(payload.neverExpires());
        assertEquals("countdown", payload.burnMode());
        assertEquals(60, payload.burnAfterSeconds());
        assertTrue(payload.allowCopyContent());
    }
}
