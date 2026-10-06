package io.fastfilelink.ffl;

import java.nio.file.Path;

public final class DownloadOptions {
    private final Path outputPath;
    private final boolean resume;
    private final String authUser;
    private final String authPassword;
    private final String proxy;
    private final String recipientAuth;
    private final String pickupCode;
    private final Path recipientPrivateKey;
    private final boolean enableReporting;
    private final String hookUrl;
    private final String logLevel;

    private DownloadOptions(Builder builder) {
        outputPath = builder.outputPath;
        resume = builder.resume;
        authUser = builder.authUser;
        authPassword = builder.authPassword;
        proxy = builder.proxy;
        recipientAuth = builder.recipientAuth;
        pickupCode = builder.pickupCode;
        recipientPrivateKey = builder.recipientPrivateKey;
        enableReporting = builder.enableReporting;
        hookUrl = builder.hookUrl;
        logLevel = builder.logLevel;
    }

    public static DownloadOptions defaults() {
        return builder().build();
    }

    public static Builder builder() {
        return new Builder();
    }

    Path outputPath() { return outputPath; }
    boolean resume() { return resume; }
    String authUser() { return authUser; }
    String authPassword() { return authPassword; }
    String proxy() { return proxy; }
    String recipientAuth() { return recipientAuth; }
    String pickupCode() { return pickupCode; }
    Path recipientPrivateKey() { return recipientPrivateKey; }
    boolean enableReporting() { return enableReporting; }
    String hookUrl() { return hookUrl; }
    String logLevel() { return logLevel; }

    public static final class Builder {
        private Path outputPath;
        private boolean resume;
        private String authUser;
        private String authPassword;
        private String proxy;
        private String recipientAuth;
        private String pickupCode;
        private Path recipientPrivateKey;
        private boolean enableReporting;
        private String hookUrl;
        private String logLevel;

        private Builder() {}

        public Builder outputPath(Path value) { outputPath = value; return this; }
        public Builder resume(boolean value) { resume = value; return this; }
        public Builder authUser(String value) { authUser = value; return this; }
        public Builder authPassword(String value) { authPassword = value; return this; }
        public Builder proxy(String value) { proxy = value; return this; }
        public Builder recipientAuth(String value) { recipientAuth = value; return this; }
        public Builder pickupCode(String value) { pickupCode = value; return this; }
        public Builder recipientPrivateKey(Path value) { recipientPrivateKey = value; return this; }
        public Builder enableReporting(boolean value) { enableReporting = value; return this; }
        public Builder hookUrl(String value) { hookUrl = value; return this; }
        public Builder logLevel(String value) { logLevel = value; return this; }

        public DownloadOptions build() {
            return new DownloadOptions(this);
        }
    }
}
