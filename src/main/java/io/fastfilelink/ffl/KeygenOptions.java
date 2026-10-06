package io.fastfilelink.ffl;

public final class KeygenOptions {
    private final boolean enableReporting;
    private final String logLevel;

    private KeygenOptions(Builder builder) {
        enableReporting = builder.enableReporting;
        logLevel = builder.logLevel;
    }

    public static KeygenOptions defaults() {
        return builder().build();
    }

    public static Builder builder() {
        return new Builder();
    }

    boolean enableReporting() { return enableReporting; }
    String logLevel() { return logLevel; }

    public static final class Builder {
        private boolean enableReporting;
        private String logLevel;

        private Builder() {}

        public Builder enableReporting(boolean value) { enableReporting = value; return this; }
        public Builder logLevel(String value) { logLevel = value; return this; }

        public KeygenOptions build() {
            return new KeygenOptions(this);
        }
    }
}
