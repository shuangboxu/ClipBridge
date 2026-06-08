package com.xushuangbo.clipbridge.windows.util;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public final class AppLogger {
    private static final DateTimeFormatter TS_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final Object LOCK = new Object();
    private static final Path LOG_FILE = resolveLogFile();

    private AppLogger() {
    }

    public static void log(String message) {
        if (message == null || message.isBlank()) {
            return;
        }
        String line = "[" + LocalDateTime.now().format(TS_FORMAT) + "] " + message + System.lineSeparator();
        synchronized (LOCK) {
            try {
                Path parent = LOG_FILE.getParent();
                if (parent != null) {
                    Files.createDirectories(parent);
                }
                Files.writeString(
                    LOG_FILE,
                    line,
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.APPEND
                );
            } catch (IOException ignore) {
                // 中文注释：日志写入失败不能影响主流程，保持客户端继续可用。
            }
        }
    }

    private static Path resolveLogFile() {
        try {
            return Path.of(System.getProperty("user.dir"), "log.txt");
        } catch (Exception e) {
            return Path.of(System.getProperty("user.home"), "ClipBridge", "log.txt");
        }
    }
}

