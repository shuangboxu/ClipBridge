package com.xushuangbo.clipbridge.windows.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ServiceAddressFormatterTest {
    @Test
    void normalize_removesTrailingSlash() {
        assertEquals("https://clipbridge.example.com", ServiceAddressFormatter.normalize("https://clipbridge.example.com/"));
    }

    @Test
    void validate_rejectsMissingScheme() {
        assertEquals("服务地址必须以 http:// 或 https:// 开头", ServiceAddressFormatter.validate("clipbridge.example.com"));
    }

    @Test
    void validate_acceptsNormalHttpsAddress() {
        assertTrue(ServiceAddressFormatter.validate("https://clipbridge.example.com:18444").isBlank());
    }
}
