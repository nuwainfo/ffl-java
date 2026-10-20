package io.fastfilelink.ffl;

import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Predicate;

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
    private final boolean watch;
    private final Long watchSettleSeconds;
    private final Map<String, FFLHookResponder> responders;
    private final String hookEndpointProxy;

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
        watch = builder.watch;
        watchSettleSeconds = builder.watchSettleSeconds;
        responders = Collections.unmodifiableMap(new LinkedHashMap<>(builder.responders));
        hookEndpointProxy = builder.hookEndpointProxy;
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
    boolean watch() { return watch; }
    Long watchSettleSeconds() { return watchSettleSeconds; }
    Map<String, FFLHookResponder> responders() { return responders; }
    String hookEndpointProxy() { return hookEndpointProxy; }

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
        private boolean watch;
        private Long watchSettleSeconds;
        private final Map<String, FFLHookResponder> responders = new LinkedHashMap<>();
        private String hookEndpointProxy;

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
        /** Watches a folder and publishes each new direct child folder as an immutable delivery. */
        public Builder watch(boolean value) { watch = value; return this; }
        /** Seconds a new delivery folder must stay unchanged before it is published (core default: 5). */
        public Builder watchSettleSeconds(long value) { watchSettleSeconds = value; return this; }

        /**
         * Answers every {@code eventName} hook event with the responder's JSON while the share runs.
         * Registering the same event again replaces the earlier responder. Requires ffl-java's own
         * hook channel, i.e. {@link #captureHookEvents(boolean)} left enabled.
         */
        public Builder respondTo(String eventName, FFLHookResponder responder) {
            Objects.requireNonNull(eventName, "eventName");
            Objects.requireNonNull(responder, "responder");
            if (eventName.isBlank()) {
                throw new IllegalArgumentException("eventName must not be blank");
            }
            responders.put(eventName, responder);
            return this;
        }

        /**
         * Decides cloud uploads that cost points: the FFL core asks before spending and waits for the
         * answer, so the decision may block while a user is consulted.
         */
        public Builder uploadConfirmation(Predicate<FFLHookEvent> decision) {
            Objects.requireNonNull(decision, "decision");
            return respondTo(FFLHookEvents.UPLOAD_CONFIRM, event -> Map.of("confirmed", decision.test(event)));
        }

        /**
         * Serves the routes an {@link FFLHookEvents#ENDPOINTS_REGISTER} responder registers. The FFL core
         * proxies every request for such a route (for example the share page's {@code /manifest} and
         * {@code /thumb}) to the hook URL's host and port, which is ffl-java's own receiver; with a base
         * URL set here the receiver forwards method, path, query, headers and body to
         * {@code baseUrl + path} and relays the upstream status, headers and body back. Requires
         * {@link #captureHookEvents(boolean)} left enabled.
         */
        public Builder hookEndpointProxy(String baseUrl) {
            Objects.requireNonNull(baseUrl, "baseUrl");
            String normalized = baseUrl.strip();
            java.net.URI uri;
            try {
                uri = java.net.URI.create(normalized);
            } catch (IllegalArgumentException malformed) {
                throw new IllegalArgumentException("hookEndpointProxy must be an http(s)://host[:port] base URL: " + baseUrl, malformed);
            }
            String scheme = uri.getScheme();
            if (scheme == null || uri.getHost() == null
                || !(scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"))) {
                throw new IllegalArgumentException("hookEndpointProxy must be an http(s)://host[:port] base URL: " + baseUrl);
            }
            while (normalized.endsWith("/")) {
                normalized = normalized.substring(0, normalized.length() - 1);
            }
            hookEndpointProxy = normalized;
            return this;
        }

        public ShareOptions build() {
            if (!captureHookEvents && hookEndpointProxy != null) {
                throw new IllegalStateException(
                    "The hook endpoint proxy needs ffl-java's hook channel; keep captureHookEvents enabled or drop it"
                );
            }
            if (!captureHookEvents && !responders.isEmpty()) {
                throw new IllegalStateException(
                    "Hook responders need ffl-java's hook channel; keep captureHookEvents enabled or drop the responders"
                );
            }
            return new ShareOptions(this);
        }
    }
}
