package io.fastfilelink.ffl;

import java.nio.file.Path;

public final class ShareOptions {
    private final String name;
    private final boolean e2ee;
    private final String authUser;
    private final String authPassword;
    private final Long maxDownloads;
    private final Long timeoutSeconds;
    private final String hookUrl;
    private final boolean captureHookEvents;
    private final int eventHistoryLimit;
    private final String proxy;
    private final String exclude;
    private final String recipientAuth;
    private final String pickupCode;
    private final Path recipientPublicKey;
    private final String recipientEmail;
    private final String recipientOtpApiBase;
    private final String alias;
    private final OptionalArgument<String> receipt;
    private final OptionalArgument<String> receiptConfirm;
    private final boolean forceRelay;
    private final OptionalArgument<String> upload;
    private final boolean resumeUpload;
    private final boolean vfs;
    private final String preferredTunnel;
    private final Long port;
    private final boolean invite;
    private final Long pause;
    private final boolean enableReporting;
    private final OptionalArgument<Path> qr;
    private final String stdinCache;
    private final String logLevel;

    private ShareOptions(Builder builder) {
        name = builder.name;
        e2ee = builder.e2ee;
        authUser = builder.authUser;
        authPassword = builder.authPassword;
        maxDownloads = builder.maxDownloads;
        timeoutSeconds = builder.timeoutSeconds;
        hookUrl = builder.hookUrl;
        captureHookEvents = builder.captureHookEvents;
        eventHistoryLimit = builder.eventHistoryLimit;
        proxy = builder.proxy;
        exclude = builder.exclude;
        recipientAuth = builder.recipientAuth;
        pickupCode = builder.pickupCode;
        recipientPublicKey = builder.recipientPublicKey;
        recipientEmail = builder.recipientEmail;
        recipientOtpApiBase = builder.recipientOtpApiBase;
        alias = builder.alias;
        receipt = builder.receipt;
        receiptConfirm = builder.receiptConfirm;
        forceRelay = builder.forceRelay;
        upload = builder.upload;
        resumeUpload = builder.resumeUpload;
        vfs = builder.vfs;
        preferredTunnel = builder.preferredTunnel;
        port = builder.port;
        invite = builder.invite;
        pause = builder.pause;
        enableReporting = builder.enableReporting;
        qr = builder.qr;
        stdinCache = builder.stdinCache;
        logLevel = builder.logLevel;
    }

    public static ShareOptions defaults() {
        return builder().build();
    }

    public static Builder builder() {
        return new Builder();
    }

    String name() { return name; }
    boolean e2ee() { return e2ee; }
    String authUser() { return authUser; }
    String authPassword() { return authPassword; }
    Long maxDownloads() { return maxDownloads; }
    Long timeoutSeconds() { return timeoutSeconds; }
    String hookUrl() { return hookUrl; }
    boolean captureHookEvents() { return captureHookEvents; }
    int eventHistoryLimit() { return eventHistoryLimit; }
    String proxy() { return proxy; }
    String exclude() { return exclude; }
    String recipientAuth() { return recipientAuth; }
    String pickupCode() { return pickupCode; }
    Path recipientPublicKey() { return recipientPublicKey; }
    String recipientEmail() { return recipientEmail; }
    String recipientOtpApiBase() { return recipientOtpApiBase; }
    String alias() { return alias; }
    OptionalArgument<String> receipt() { return receipt; }
    OptionalArgument<String> receiptConfirm() { return receiptConfirm; }
    boolean forceRelay() { return forceRelay; }
    OptionalArgument<String> upload() { return upload; }
    boolean resumeUpload() { return resumeUpload; }
    boolean vfs() { return vfs; }
    String preferredTunnel() { return preferredTunnel; }
    Long port() { return port; }
    boolean invite() { return invite; }
    Long pause() { return pause; }
    boolean enableReporting() { return enableReporting; }
    OptionalArgument<Path> qr() { return qr; }
    String stdinCache() { return stdinCache; }
    String logLevel() { return logLevel; }

    public static final class Builder {
        private String name;
        private boolean e2ee;
        private String authUser;
        private String authPassword;
        private Long maxDownloads;
        private Long timeoutSeconds;
        private String hookUrl;
        private boolean captureHookEvents = true;
        private int eventHistoryLimit = 1000;
        private String proxy;
        private String exclude;
        private String recipientAuth;
        private String pickupCode;
        private Path recipientPublicKey;
        private String recipientEmail;
        private String recipientOtpApiBase;
        private String alias;
        private OptionalArgument<String> receipt = OptionalArgument.absent();
        private OptionalArgument<String> receiptConfirm = OptionalArgument.absent();
        private boolean forceRelay;
        private OptionalArgument<String> upload = OptionalArgument.absent();
        private boolean resumeUpload;
        private boolean vfs;
        private String preferredTunnel;
        private Long port;
        private boolean invite;
        private Long pause;
        private boolean enableReporting;
        private OptionalArgument<Path> qr = OptionalArgument.absent();
        private String stdinCache;
        private String logLevel;

        private Builder() {}

        public Builder name(String value) { name = value; return this; }
        public Builder e2ee(boolean value) { e2ee = value; return this; }
        public Builder authUser(String value) { authUser = value; return this; }
        public Builder authPassword(String value) { authPassword = value; return this; }
        public Builder maxDownloads(long value) { maxDownloads = value; return this; }
        public Builder timeoutSeconds(long value) { timeoutSeconds = value; return this; }
        public Builder hookUrl(String value) { hookUrl = value; return this; }
        public Builder captureHookEvents(boolean value) { captureHookEvents = value; return this; }
        public Builder eventHistoryLimit(int value) {
            if (value < 1) {
                throw new IllegalArgumentException("eventHistoryLimit must be positive");
            }
            eventHistoryLimit = value;
            return this;
        }
        public Builder proxy(String value) { proxy = value; return this; }
        public Builder exclude(String value) { exclude = value; return this; }
        public Builder recipientAuth(String value) { recipientAuth = value; return this; }
        public Builder pickupCode(String value) { pickupCode = value; return this; }
        public Builder recipientPublicKey(Path value) { recipientPublicKey = value; return this; }
        public Builder recipientEmail(String value) { recipientEmail = value; return this; }
        public Builder recipientOtpApiBase(String value) { recipientOtpApiBase = value; return this; }
        public Builder alias(String value) { alias = value; return this; }
        public Builder receipt() { receipt = OptionalArgument.flag(); return this; }
        public Builder receipt(String value) { receipt = OptionalArgument.value(value); return this; }
        public Builder receiptConfirm() { receiptConfirm = OptionalArgument.flag(); return this; }
        public Builder receiptConfirm(String value) { receiptConfirm = OptionalArgument.value(value); return this; }
        public Builder forceRelay(boolean value) { forceRelay = value; return this; }
        public Builder upload() { upload = OptionalArgument.flag(); return this; }
        public Builder upload(String value) { upload = OptionalArgument.value(value); return this; }
        public Builder resumeUpload(boolean value) { resumeUpload = value; return this; }
        public Builder vfs(boolean value) { vfs = value; return this; }
        public Builder preferredTunnel(String value) { preferredTunnel = value; return this; }
        public Builder port(long value) { port = value; return this; }
        public Builder invite(boolean value) { invite = value; return this; }
        public Builder pause(long value) { pause = value; return this; }
        public Builder enableReporting(boolean value) { enableReporting = value; return this; }
        public Builder qr() { qr = OptionalArgument.flag(); return this; }
        public Builder qr(Path value) { qr = OptionalArgument.value(value); return this; }
        public Builder stdinCache(String value) { stdinCache = value; return this; }
        public Builder logLevel(String value) { logLevel = value; return this; }

        public ShareOptions build() {
            return new ShareOptions(this);
        }
    }
}
