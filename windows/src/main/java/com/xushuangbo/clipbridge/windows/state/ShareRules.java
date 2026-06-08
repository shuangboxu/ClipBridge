package com.xushuangbo.clipbridge.windows.state;

public final class ShareRules {
    private ShareRules() {
    }

    public enum ComposeMode {
        TEXT("文本分享"),
        FILE("文件分享");

        private final String label;

        ComposeMode(String label) {
            this.label = label;
        }

        public String getLabel() {
            return label;
        }
    }

    public enum StrategyKey {
        NEVER("never", "不过期"),
        EXPIRE("expire", "过期"),
        ONCE("once", "打开一次失效");

        private final String storageKey;
        private final String label;

        StrategyKey(String storageKey, String label) {
            this.storageKey = storageKey;
            this.label = label;
        }

        public String getStorageKey() {
            return storageKey;
        }

        public String getLabel() {
            return label;
        }
    }

    public enum StatusFilter {
        ALL("all", "全部"),
        ACTIVE("active", "可访问"),
        EXPIRED("expired", "已过期"),
        CONSUMED("consumed", "已焚毁"),
        REVOKED("revoked", "已撤销");

        private final String apiValue;
        private final String label;

        StatusFilter(String apiValue, String label) {
            this.apiValue = apiValue;
            this.label = label;
        }

        public String getApiValue() {
            return apiValue;
        }

        public String getLabel() {
            return label;
        }

        public static StatusFilter fromApiValue(String rawValue) {
            String normalized = rawValue == null ? "" : rawValue.trim();
            for (StatusFilter item : values()) {
                if (item.apiValue.equalsIgnoreCase(normalized)) {
                    return item;
                }
            }
            return ALL;
        }
    }

    public enum ExpirePreset {
        ONE_HOUR("1h", "1 小时", 1),
        ONE_DAY("24h", "24 小时", 24),
        SEVEN_DAYS("7d", "7 天", 24 * 7);

        private final String storageKey;
        private final String label;
        private final int hours;

        ExpirePreset(String storageKey, String label, int hours) {
            this.storageKey = storageKey;
            this.label = label;
            this.hours = hours;
        }

        public String getStorageKey() {
            return storageKey;
        }

        public String getLabel() {
            return label;
        }

        public int getHours() {
            return hours;
        }
    }

    public enum CountdownPreset {
        TEN_SECONDS("10s", "10 秒", 10),
        THIRTY_SECONDS("30s", "30 秒", 30),
        ONE_MINUTE("60s", "1 分钟", 60),
        FIVE_MINUTES("300s", "5 分钟", 300);

        private final String storageKey;
        private final String label;
        private final int seconds;

        CountdownPreset(String storageKey, String label, int seconds) {
            this.storageKey = storageKey;
            this.label = label;
            this.seconds = seconds;
        }

        public String getStorageKey() {
            return storageKey;
        }

        public String getLabel() {
            return label;
        }

        public int getSeconds() {
            return seconds;
        }
    }

    public record NeverRule(boolean allowCopyText) {
        public NeverRule() {
            this(false);
        }
    }

    public record ExpireRule(ExpirePreset preset, boolean allowCopyText) {
        public ExpireRule() {
            this(ExpirePreset.ONE_DAY, false);
        }
    }

    public record OnceRule(boolean showCountdown, CountdownPreset countdownPreset, boolean allowCopyText) {
        public OnceRule() {
            this(true, CountdownPreset.TEN_SECONDS, false);
        }
    }

    public record Config(NeverRule never, ExpireRule expire, OnceRule once) {
        public Config() {
            this(new NeverRule(), new ExpireRule(), new OnceRule());
        }

        public PolicyPayload buildPolicyPayload(StrategyKey strategyKey, boolean allowTextCopy) {
            return switch (strategyKey) {
                case NEVER -> new PolicyPayload(
                    true,
                    0,
                    "none",
                    0,
                    allowTextCopy && never.allowCopyText()
                );
                case EXPIRE -> new PolicyPayload(
                    false,
                    expire.preset().hours * 60 * 60,
                    "none",
                    0,
                    allowTextCopy && expire.allowCopyText()
                );
                case ONCE -> new PolicyPayload(
                    true,
                    0,
                    once.showCountdown() ? "countdown" : "once",
                    once.showCountdown() ? once.countdownPreset().seconds : 0,
                    allowTextCopy && once.allowCopyText()
                );
            };
        }

        public StrategySummary buildStrategySummary(StrategyKey strategyKey) {
            return switch (strategyKey) {
                case NEVER -> new StrategySummary(
                    StrategyKey.NEVER.label,
                    "公开链接不会自动过期，需要你手动撤销。",
                    never.allowCopyText() ? "文字可复制" : "文字禁止复制"
                );
                case EXPIRE -> new StrategySummary(
                    StrategyKey.EXPIRE.label,
                    expire.preset().label + " 后自动失效。",
                    expire.allowCopyText() ? "文字可复制" : "文字禁止复制"
                );
                case ONCE -> new StrategySummary(
                    StrategyKey.ONCE.label,
                    once.showCountdown()
                        ? "首次打开后开始 " + once.countdownPreset().label + " 倒计时，时间到后自动失效。"
                        : "公开内容只允许成功打开一次。",
                    once.allowCopyText() ? "文字可复制" : "文字禁止复制"
                );
            };
        }
    }

    public record PolicyPayload(
        boolean neverExpires,
        int expireSeconds,
        String burnMode,
        int burnAfterSeconds,
        boolean allowCopyContent
    ) {
    }

    public record StrategySummary(String title, String description, String copyLabel) {
    }

    public static Config defaultConfig() {
        return new Config();
    }
}
