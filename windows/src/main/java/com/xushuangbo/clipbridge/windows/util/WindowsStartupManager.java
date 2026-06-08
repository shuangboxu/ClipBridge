package com.xushuangbo.clipbridge.windows.util;

import java.net.URI;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class WindowsStartupManager {
    private static final String RUN_KEY = "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Run";
    private static final String VALUE_NAME = "ClipBridge";

    private WindowsStartupManager() {
    }

    public static boolean isSupported() {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        return os.contains("windows");
    }

    public static boolean isEnabled() {
        ensureSupported();
        CommandResult result = exec("reg", "query", RUN_KEY, "/v", VALUE_NAME);
        return result.exitCode == 0;
    }

    public static void setEnabled(boolean enabled, boolean startInTray) {
        ensureSupported();
        if (!enabled) {
            remove();
            return;
        }

        String launcher = buildLauncherCommand(startInTray);
        CommandResult result = exec(
            "reg", "add", RUN_KEY,
            "/v", VALUE_NAME,
            "/t", "REG_SZ",
            "/d", launcher,
            "/f"
        );
        if (result.exitCode != 0) {
            throw new IllegalStateException("写入开机自启失败: " + safeOutput(result.output));
        }
    }

    public static void remove() {
        ensureSupported();
        // 中文注释：先判断一次是否已存在，避免因为系统语言或编码不同，把“本来就没配置”误判成失败。
        if (!isEnabled()) {
            return;
        }

        CommandResult result = exec("reg", "delete", RUN_KEY, "/v", VALUE_NAME, "/f");
        if (result.exitCode == 0) {
            return;
        }

        // 中文注释：有些机器上 reg delete 会返回非 0，但实际已经删掉了，这里再查一次最终状态。
        if (!isEnabled()) {
            return;
        }

        String lowerOut = result.output == null ? "" : result.output.toLowerCase(Locale.ROOT);
        if (lowerOut.contains("unable to find") || lowerOut.contains("找不到") || lowerOut.contains("cannot find")) {
            return;
        }
        throw new IllegalStateException("删除开机自启失败: " + safeOutput(result.output));
    }

    public static String buildLauncherCommand(boolean startInTray) {
        ensureSupported();

        ProcessHandle.Info info = ProcessHandle.current().info();
        String command = info.command().orElse("");
        if (command.isBlank()) {
            throw new IllegalStateException("无法识别当前启动命令");
        }

        List<String> args = new ArrayList<>();
        args.add(command);
        if (command.toLowerCase(Locale.ROOT).endsWith(".exe")) {
            // 安装版通常是 ClipBridge.exe，直接写入注册表即可。
        } else {
            // 开发态（java -jar）也尽量支持，便于本地调试自启逻辑。
            String jarPath = detectJarPath(info.arguments().orElse(new String[0]));
            if (jarPath == null || jarPath.isBlank()) {
                throw new IllegalStateException("仅安装版可自动配置开机自启（未检测到启动器 EXE/JAR）");
            }
            args.add("-jar");
            args.add(jarPath);
        }

        if (startInTray) {
            args.add("--start-in-tray");
        }
        return joinCommand(args);
    }

    private static String detectJarPath(String[] args) {
        if (args != null) {
            for (int i = 0; i < args.length - 1; i++) {
                if ("-jar".equals(args[i])) {
                    String raw = args[i + 1];
                    if (raw != null && !raw.isBlank()) {
                        return raw;
                    }
                }
            }
            for (String arg : args) {
                if (arg == null) {
                    continue;
                }
                String lower = arg.toLowerCase(Locale.ROOT);
                if (lower.endsWith(".jar")) {
                    return arg;
                }
            }
        }

        try {
            URI uri = WindowsStartupManager.class.getProtectionDomain().getCodeSource().getLocation().toURI();
            Path codePath = Paths.get(uri);
            if (Files.isRegularFile(codePath) && codePath.toString().toLowerCase(Locale.ROOT).endsWith(".jar")) {
                return codePath.toString();
            }
        } catch (Exception ignore) {
        }
        return null;
    }

    private static String joinCommand(List<String> args) {
        StringBuilder cmd = new StringBuilder();
        for (String arg : args) {
            if (arg == null) {
                continue;
            }
            if (cmd.length() > 0) {
                cmd.append(' ');
            }
            cmd.append(escapeWindowsArg(arg));
        }
        return cmd.toString();
    }

    // Follow CommandLineToArgvW-compatible escaping rules so spaces and trailing
    // backslashes in install paths are handled correctly.
    private static String escapeWindowsArg(String arg) {
        if (arg.isEmpty()) {
            return "\"\"";
        }
        boolean needQuotes = arg.chars().anyMatch(ch -> Character.isWhitespace(ch) || ch == '"');
        if (!needQuotes) {
            return arg;
        }

        StringBuilder sb = new StringBuilder(arg.length() + 8);
        sb.append('"');
        int backslashCount = 0;
        for (int i = 0; i < arg.length(); i++) {
            char c = arg.charAt(i);
            if (c == '\\') {
                backslashCount++;
                continue;
            }
            if (c == '"') {
                appendRepeat(sb, '\\', backslashCount * 2 + 1);
                sb.append('"');
                backslashCount = 0;
                continue;
            }
            appendRepeat(sb, '\\', backslashCount);
            backslashCount = 0;
            sb.append(c);
        }
        appendRepeat(sb, '\\', backslashCount * 2);
        sb.append('"');
        return sb.toString();
    }

    private static void appendRepeat(StringBuilder sb, char ch, int count) {
        for (int i = 0; i < count; i++) {
            sb.append(ch);
        }
    }

    private static void ensureSupported() {
        if (!isSupported()) {
            throw new UnsupportedOperationException("当前系统不是 Windows，无法配置开机自启");
        }
    }

    private static CommandResult exec(String... cmd) {
        try {
            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.redirectErrorStream(true);
            Process p = pb.start();
            byte[] bytes = p.getInputStream().readAllBytes();
            int exit = p.waitFor();
            String out = new String(bytes, Charset.defaultCharset()).trim();
            return new CommandResult(exit, out);
        } catch (Exception e) {
            throw new IllegalStateException("执行系统命令失败: " + String.join(" ", cmd) + "，" + e.getMessage(), e);
        }
    }

    private static String safeOutput(String out) {
        if (out == null || out.isBlank()) {
            return "无输出";
        }
        return out;
    }

    private record CommandResult(int exitCode, String output) {
    }
}

