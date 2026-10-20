package io.fastfilelink.ffl;

import apebind.generated.ffl.APEProcessException;
import apebind.generated.ffl.APEProcessResult;
import apebind.generated.ffl.APERuntime;
import apebind.generated.ffl.FFLBinding;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Objects;

public final class FFLClient {
    private final FFLBinding binding;
    private volatile FFLBinding englishBinding;

    /** A client that launches the bundled {@code ffl.com} through the default runtime. */
    public FFLClient() {
        this(FFLBinding.defaults());
    }

    /** A client that launches {@code ffl.com} as described by {@code runtime}. */
    public FFLClient(APERuntime runtime) {
        this(FFLBinding.create(runtime));
    }

    public FFLClient(FFLBinding binding) {
        this.binding = Objects.requireNonNull(binding, "binding");
    }

    FFLBinding binding() {
        return binding;
    }

    public String version() {
        var parameters = FFLBinding.VersionParameters.builder()
            .version(true)
            .build();
        return binding.version(parameters);
    }

    public ShareSession share(Path path) {
        return share(path, ShareOptions.defaults());
    }

    public ShareSession share(Path path, ShareOptions options) {
        Objects.requireNonNull(path, "path");
        return share(List.of(path), options);
    }

    public ShareSession share(List<Path> paths) {
        return share(paths, ShareOptions.defaults());
    }

    public ShareSession share(List<Path> paths, ShareOptions options) {
        validateSharePaths(paths);
        Objects.requireNonNull(options, "options");
        FFLHookEventChannel eventChannel = createEventChannel(options);
        try {
            var parameters = buildShareParameters(
                paths,
                options,
                null,
                effectiveHookUrl(options, eventChannel)
            );
            return new ShareSession(binding.share(parameters), eventChannel);
        } catch (RuntimeException error) {
            if (eventChannel != null) {
                eventChannel.close();
            }
            throw error;
        }
    }

    public ShareSession shareText(String text) {
        return shareText(text, "shared.txt", ShareOptions.defaults());
    }

    public ShareSession shareText(String text, String name) {
        return shareText(text, name, ShareOptions.defaults());
    }

    public ShareSession shareText(String text, String name, ShareOptions options) {
        Objects.requireNonNull(text, "text");
        return shareTemporaryBytes(text.getBytes(StandardCharsets.UTF_8), name, options);
    }

    public ShareSession shareBytes(byte[] data) {
        return shareBytes(data, "data.bin", ShareOptions.defaults());
    }

    public ShareSession shareBytes(byte[] data, String name) {
        return shareBytes(data, name, ShareOptions.defaults());
    }

    public ShareSession shareBytes(byte[] data, String name, ShareOptions options) {
        Objects.requireNonNull(data, "data");
        return shareTemporaryBytes(data, name, options);
    }

    public ShareSession share(InputStream source, String name) {
        return share(source, name, ShareOptions.defaults());
    }

    public ShareSession share(InputStream source, String name, ShareOptions options) {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(options, "options");
        FFLHookEventChannel eventChannel = createEventChannel(options);
        try {
            var parameters = buildShareParameters(
                List.of("-"),
                options,
                name,
                effectiveHookUrl(options, eventChannel),
                options.vfs()
            );
            return new ShareSession(binding.share(parameters, source), eventChannel);
        } catch (RuntimeException error) {
            if (eventChannel != null) {
                eventChannel.close();
            }
            throw error;
        }
    }

    public ShareSession shareVfs(String url) {
        return shareVfs(url, ShareOptions.defaults());
    }

    /**
     * Shares the tree served by a VFS endpoint: a {@code vfs://host:port} URI the FFL core reads
     * instead of the local file system, as an app does when its files live behind content URIs.
     * The URI is the share target itself; {@code --vfs} (which makes the core serve its own VFS
     * from a local path) is never sent, whatever {@link ShareOptions#vfs} says.
     */
    public ShareSession shareVfs(String url, ShareOptions options) {
        validateVfsUrl(url);
        Objects.requireNonNull(options, "options");
        FFLHookEventChannel eventChannel = createEventChannel(options);
        try {
            var parameters = buildShareParameters(
                List.of(url),
                options,
                null,
                effectiveHookUrl(options, eventChannel),
                false
            );
            return new ShareSession(binding.share(parameters), eventChannel);
        } catch (RuntimeException error) {
            if (eventChannel != null) {
                eventChannel.close();
            }
            throw error;
        }
    }

    /** Starts {@code ffl login --mode cli --email <email>}; the core mails a one-time code and waits for it. */
    public LoginSession login(String email) {
        FFLBinding.LoginParameters parameters = buildLoginParameters(email);
        StdinFeed feed = new StdinFeed();
        try {
            return new LoginSession(binding.login(parameters, feed), feed);
        } catch (RuntimeException error) {
            feed.close();
            throw error;
        }
    }

    public AuthStatus status() {
        return AuthStatus.parse(englishBinding().status(FFLBinding.StatusParameters.builder().build()));
    }

    /** Clears the stored credentials; {@code false} when the core reports failure. */
    public boolean logout() {
        try {
            return binding.logout(FFLBinding.LogoutParameters.builder().build()).returnCode() == 0;
        } catch (APEProcessException failed) {
            return false;
        }
    }

    FFLBinding.LoginParameters buildLoginParameters(String email) {
        Objects.requireNonNull(email, "email");
        if (email.isBlank()) {
            throw new IllegalArgumentException("email must not be blank");
        }
        return FFLBinding.LoginParameters.builder()
            .email(email.strip())
            .mode("cli")
            .build();
    }

    /**
     * {@code ffl status} only prints localized text, so it runs with {@code FFL_LANGUAGE=en} for
     * parsing; the core reads that variable without storing it. Login keeps the user's language
     * because it also picks the language of the one-time-code mail.
     */
    FFLBinding englishBinding() {
        FFLBinding current = englishBinding;
        if (current == null) {
            synchronized (this) {
                current = englishBinding;
                if (current == null) {
                    current = FFLBinding.create(
                        binding.runtime().toBuilder().environment(FFLEnvironment.LANGUAGE, "en").build()
                    );
                    englishBinding = current;
                }
            }
        }
        return current;
    }

    public DownloadResult download(String url) {
        return download(url, DownloadOptions.defaults());
    }

    public DownloadResult download(String url, DownloadOptions options) {
        return startDownload(url, options).waitFor();
    }

    public DownloadSession startDownload(String url) {
        return startDownload(url, DownloadOptions.defaults());
    }

    public DownloadSession startDownload(String url, DownloadOptions options) {
        return startDownload(url, options, false);
    }

    public DownloadSession downloadStream(String url) {
        return downloadStream(url, DownloadOptions.defaults());
    }

    public DownloadSession downloadStream(String url, DownloadOptions options) {
        if (options.outputPath() != null) {
            throw new IllegalArgumentException(
                "downloadStream writes to stdout and does not accept outputPath"
            );
        }

        return startDownload(url, options, true);
    }

    private DownloadSession startDownload(
        String url,
        DownloadOptions options,
        boolean stdout
    ) {
        Objects.requireNonNull(url, "url");
        Objects.requireNonNull(options, "options");
        Path cwd = Paths.get("").toAbsolutePath().normalize();
        return new DownloadSession(
            binding.download(buildDownloadParameters(url, options, stdout), !stdout),
            options.outputPath(),
            cwd
        );
    }

    FFLBinding.DownloadParameters buildDownloadParameters(
        String url,
        DownloadOptions options,
        boolean stdout
    ) {
        var builder = FFLBinding.DownloadParameters.builder()
            .url(url)
            .resume(options.resume())
            .follow(options.follow())
            .enableReporting(options.enableReporting())
            .stdout(stdout);
        if (options.outputPath() != null) {
            builder.outputPath(options.outputPath());
        }
        if (options.authUser() != null) {
            builder.authUser(options.authUser());
        }
        if (options.authPassword() != null) {
            builder.authPassword(options.authPassword());
        }
        if (options.proxy() != null) {
            builder.proxy(options.proxy());
        }
        if (options.recipientAuth() != null) {
            builder.recipientAuth(options.recipientAuth());
        }
        if (options.pickupCode() != null) {
            builder.pickupCode(options.pickupCode());
        }
        if (options.recipientPrivateKey() != null) {
            builder.recipientPrivateKey(options.recipientPrivateKey());
        }
        if (options.hookUrl() != null) {
            builder.hook(options.hookUrl());
        }
        if (options.logLevel() != null) {
            builder.logLevel(options.logLevel());
        }
        return builder.build();
    }

    public KeygenResult keygen() {
        return keygen(null, KeygenOptions.defaults());
    }

    public KeygenResult keygen(String name) {
        return keygen(name, KeygenOptions.defaults());
    }

    public KeygenResult keygen(String name, KeygenOptions options) {
        Objects.requireNonNull(options, "options");
        Path cwd = Paths.get("").toAbsolutePath().normalize();
        var builder = FFLBinding.KeygenParameters.builder()
            .enableReporting(options.enableReporting());
        if (name != null) {
            builder.name(name);
        }
        if (options.logLevel() != null) {
            builder.logLevel(options.logLevel());
        }
        return FFLResultParser.parseKeygen(binding.keygen(builder.build()), cwd);
    }

    public APEProcessResult raw(List<?> arguments) {
        return binding.raw(arguments);
    }

    public APEProcessResult raw(List<?> arguments, double timeoutSeconds) {
        return binding.raw(arguments, timeoutSeconds);
    }

    FFLBinding.ShareParameters buildShareParameters(
        List<Path> paths,
        ShareOptions options,
        String forcedName
    ) {
        return buildShareParameters(paths, options, forcedName, options.hookUrl());
    }

    FFLBinding.ShareParameters buildVfsShareParameters(String url, ShareOptions options) {
        validateVfsUrl(url);
        return buildShareParameters(List.of(url), options, null, options.hookUrl(), false);
    }

    private static void validateVfsUrl(String url) {
        Objects.requireNonNull(url, "url");
        String lower = url.toLowerCase(java.util.Locale.ROOT);
        if (!lower.startsWith("vfs://") || lower.length() == "vfs://".length()) {
            throw new IllegalArgumentException("VFS share target must be a vfs://host:port URI: " + url);
        }
    }

    static FFLHookEventChannel createEventChannel(ShareOptions options) {
        return options.captureHookEvents()
            ? new FFLHookEventChannel(options.hookUrl(), options.eventHistoryLimit(), options.responders(), options.hookEndpointProxy())
            : null;
    }

    static String effectiveHookUrl(ShareOptions options, FFLHookEventChannel eventChannel) {
        return eventChannel == null ? options.hookUrl() : eventChannel.url();
    }

    private FFLBinding.ShareParameters buildShareParameters(
        List<Path> paths,
        ShareOptions options,
        String forcedName,
        String hookUrl
    ) {
        List<String> targets = new java.util.ArrayList<>(paths.size());
        for (Path path : paths) {
            targets.add(path.toString());
        }
        return buildShareParameters(targets, options, forcedName, hookUrl, options.vfs());
    }

    /** {@code targets} are what the core's FILE_OR_FOLDER positional takes: paths, "-", "@list" or a VFS URL. */
    private FFLBinding.ShareParameters buildShareParameters(
        List<String> targets,
        ShareOptions options,
        String forcedName,
        String hookUrl,
        boolean vfs
    ) {
        var builder = FFLBinding.ShareParameters.builder()
            .paths(targets)
            .e2ee(options.e2ee())
            .resume(options.resumeUpload())
            .vfs(vfs)
            .watch(options.watch())
            .forceRelay(options.forceRelay())
            .invite(options.invite())
            .enableReporting(options.enableReporting())
            .disableClipboard(true)
            .foreground(true);
        String name = forcedName != null ? forcedName : options.name();
        if (name != null) {
            builder.name(name);
        }
        if (options.authUser() != null) {
            builder.authUser(options.authUser());
        }
        if (options.authPassword() != null) {
            builder.authPassword(options.authPassword());
        }
        if (options.maxDownloads() != null) {
            builder.maxDownloads(options.maxDownloads());
        }
        if (options.timeoutSeconds() != null) {
            builder.timeout(options.timeoutSeconds());
        }
        if (options.watchSettleSeconds() != null) {
            builder.watchSettle(options.watchSettleSeconds());
        }
        if (hookUrl != null) {
            builder.hook(hookUrl);
        }
        if (options.proxy() != null) {
            builder.proxy(options.proxy());
        }
        if (options.exclude() != null) {
            builder.exclude(options.exclude());
        }
        if (options.recipientAuth() != null) {
            builder.recipientAuth(options.recipientAuth());
        }
        if (options.pickupCode() != null) {
            builder.pickupCode(options.pickupCode());
        }
        if (options.recipientPublicKey() != null) {
            builder.recipientPublicKey(options.recipientPublicKey());
        }
        if (options.recipientEmail() != null) {
            builder.recipientEmail(options.recipientEmail());
        }
        if (options.recipientOtpApiBase() != null) {
            builder.recipientOtpApiBase(options.recipientOtpApiBase());
        }
        if (options.alias() != null) {
            builder.alias(options.alias());
        }
        applyOptionalString(options.receipt(), builder::receipt, builder::receipt);
        applyOptionalString(
            options.receiptConfirm(),
            builder::receiptConfirm,
            builder::receiptConfirm
        );
        applyOptionalString(options.upload(), builder::upload, builder::upload);
        applyOptionalPath(options.qr(), builder::qr, builder::qr);
        if (options.preferredTunnel() != null) {
            builder.preferredTunnel(options.preferredTunnel());
        }
        if (options.port() != null) {
            builder.port(options.port());
        }
        if (options.pause() != null) {
            builder.pause(options.pause());
        }
        if (options.stdinCache() != null) {
            builder.stdinCache(options.stdinCache());
        }
        if (options.logLevel() != null) {
            builder.logLevel(options.logLevel());
        }
        return builder.build();
    }

    private ShareSession shareTemporaryBytes(byte[] data, String name, ShareOptions options) {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(options, "options");
        Path directory;
        try {
            directory = Files.createTempDirectory("ffl-java-");
        } catch (IOException error) {
            throw new IllegalStateException("Failed to create FFL temporary directory", error);
        }
        try {
            Path source = directory.resolve("source" + extension(name));
            Files.write(source, data);
            FFLHookEventChannel eventChannel = createEventChannel(options);
            var parameters = buildShareParameters(
                List.of(source),
                options,
                name,
                effectiveHookUrl(options, eventChannel)
            );
            try {
                return new ShareSession(binding.share(parameters), eventChannel)
                    .attachCleanupPath(directory);
            } catch (RuntimeException error) {
                if (eventChannel != null) {
                    eventChannel.close();
                }
                throw error;
            }
        } catch (IOException error) {
            IllegalStateException failure = new IllegalStateException(
                "Failed to write temporary FFL share source",
                error
            );
            cleanupAfterFailure(directory, failure);
            throw failure;
        } catch (RuntimeException error) {
            cleanupAfterFailure(directory, error);
            throw error;
        }
    }

    private static void cleanupAfterFailure(Path path, RuntimeException failure) {
        try {
            TemporaryFiles.deleteRecursively(path);
        } catch (RuntimeException cleanupError) {
            failure.addSuppressed(cleanupError);
        }
    }

    private static void validateSharePaths(List<Path> paths) {
        Objects.requireNonNull(paths, "paths");
        if (paths.isEmpty()) {
            throw new IllegalArgumentException("paths must contain at least one file or folder");
        }
        for (Path path : paths) {
            Objects.requireNonNull(path, "share path");
            if (!Files.exists(path)) {
                throw new IllegalArgumentException("Share path does not exist: " + path);
            }
        }
    }

    private static String extension(String name) {
        String fileName = Paths.get(name).getFileName().toString();
        int dot = fileName.lastIndexOf('.');
        return dot <= 0 ? "" : fileName.substring(dot);
    }

    private static void applyOptionalString(
        OptionalArgument<String> argument,
        Runnable flagSetter,
        java.util.function.Consumer<String> valueSetter
    ) {
        if (!argument.present()) {
            return;
        }
        if (argument.hasValue()) {
            valueSetter.accept(argument.value());
            return;
        }
        flagSetter.run();
    }

    private static void applyOptionalPath(
        OptionalArgument<Path> argument,
        Runnable flagSetter,
        java.util.function.Consumer<Path> valueSetter
    ) {
        if (!argument.present()) {
            return;
        }
        if (argument.hasValue()) {
            valueSetter.accept(argument.value());
            return;
        }
        flagSetter.run();
    }
}
