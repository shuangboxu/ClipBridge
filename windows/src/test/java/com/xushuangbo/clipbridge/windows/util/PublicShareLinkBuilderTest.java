package com.xushuangbo.clipbridge.windows.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PublicShareLinkBuilderTest {
    @Test
    void build_convertsServiceAddressToHashRoute() {
        String link = PublicShareLinkBuilder.build("https://clipbridge.example.com:18444", "token-123");
        assertEquals("https://clipbridge.example.com:18444/#/public/token-123", link);
    }

    @Test
    void build_returnsEmptyWhenTokenMissing() {
        assertEquals("", PublicShareLinkBuilder.build("https://clipbridge.example.com", ""));
    }
}
