package com.xushuangbo.clipbridge.windows.state;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AppStateStoreTest {
    @TempDir
    Path tempDir;

    @Test
    void update_persistsStateAcrossReload() {
        Path stateFile = tempDir.resolve("windows-state.json");
        AppStateStore store = new AppStateStore(stateFile);

        store.update(state -> {
            state.setBaseUrl("https://clipbridge.example.com");
            state.setUsername("alice");
            state.setCurrentDeviceId("device-1");
            state.setDeviceName("Windows on Test-PC");
            state.setAccessToken("access-token");
            state.setRefreshToken("refresh-token");
            state.setLastAckSeq(12L);
            state.setSyncEnabled(false);
            state.setStartInTray(false);
            state.setShareRules(new ShareRules.Config(
                new ShareRules.NeverRule(true),
                new ShareRules.ExpireRule(ShareRules.ExpirePreset.SEVEN_DAYS, false),
                new ShareRules.OnceRule(false, ShareRules.CountdownPreset.TEN_SECONDS, false)
            ));
        });

        AppState reloaded = new AppStateStore(stateFile).getState();
        assertEquals("https://clipbridge.example.com", reloaded.getBaseUrl());
        assertEquals("alice", reloaded.getUsername());
        assertEquals("device-1", reloaded.getCurrentDeviceId());
        assertEquals(12L, reloaded.getLastAckSeq());
        assertFalse(reloaded.isSyncEnabled());
        assertFalse(reloaded.isStartInTray());
        assertTrue(reloaded.getShareRules().never().allowCopyText());
        assertEquals(ShareRules.ExpirePreset.SEVEN_DAYS, reloaded.getShareRules().expire().preset());
    }

    @Test
    void clearSession_keepsServerAddressAndDeviceName() {
        Path stateFile = tempDir.resolve("session-state.json");
        AppStateStore store = new AppStateStore(stateFile);

        store.update(state -> {
            state.setBaseUrl("https://clipbridge.example.com");
            state.setDeviceName("Windows on Demo");
            state.setAccessToken("access-token");
            state.setRefreshToken("refresh-token");
            state.setCurrentDeviceId("device-1");
            state.setUsername("alice");
            state.clearSession();
        });

        AppState state = store.getState();
        assertEquals("https://clipbridge.example.com", state.getBaseUrl());
        assertEquals("Windows on Demo", state.getDeviceName());
        assertFalse(state.isLoggedIn());
        assertEquals(0L, state.getLastAckSeq());
    }
}
