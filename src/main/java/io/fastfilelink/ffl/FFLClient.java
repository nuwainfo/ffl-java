package io.fastfilelink.ffl;

import apebind.generated.ffl.APEProcessResult;
import apebind.generated.ffl.FFLBinding;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

public final class FFLClient {
    public String version() {
        var parameters = FFLBinding.VersionParameters.builder()
            .version(true)
            .build();
        return FFLBinding.version(parameters);
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
            return new ShareSession(FFLBinding.share(parameters), eventChannel);
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
                List.of(Path.of("-")),
                options,
                name,
                effectiveHookUrl(options, eventChannel)
            );
            return new ShareSession(FFLBinding.share(parameters, source), eventChannel);
        } catch (RuntimeException error) {
            if (eventChannel != null) {
                eventChannel.close();
            }
            throw error;
        }
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
        Path cwd = Path.of("").toAbsolutePath().normalize();
        var builder = FFLBinding.DownloadParameters.builder()
            .url(url)
            .resume(options.resume())
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
        return new DownloadSession(
            FFLBinding.download(builder.build(), !stdout),
            options.outputPath(),
            cwd
        );
    }

    public KeygenResult keygen() {
        return keygen(null, KeygenOptions.defaults());
    }

    public KeygenResult keygen(String name) {
        return keygen(name, KeygenOptions.defaults());
    }

    public KeygenResult keygen(String name, KeygenOptions options) {
        Objects.requireNonNull(options, "options");
        Path cwd = Path.of("").toAbsolutePath().normalize();
        var builder = FFLBinding.KeygenParameters.builder()
            .enableReporting(options.enableReporting());
        if (name != null) {
            builder.name(name);
        }
        if (options.logLevel() != null) {
            builder.logLevel(options.logLevel());
        }
        return FFLResultParser.parseKeygen(FFLBinding.keygen(builder.build()), cwd);
    }

    public APEProcessResult raw(List<?> arguments) {
        return FFLBinding.raw(arguments);
    }

    public APEProcessResult raw(List<?> arguments, double timeoutSeconds) {
        return FFLBinding.raw(arguments, timeoutSeconds);
    }

    FFLBinding.ShareParameters buildShareParameters(
        List<Path> paths,
        ShareOptions options,
        String forcedName
    ) {
        return buildShareParameters(paths, options, forcedName, options.hookUrl());
    }

    static FFLHookEventChannel createEventChannel(ShareOptions options) {
        return options.captureHookEvents()
            ? new FFLHookEventChannel(options.hookUrl(), options.eventHistoryLimit())
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
        var builder = FFLBinding.ShareParameters.builder()
            .paths(paths)
            .e2ee(options.e2ee())
            .resume(options.resumeUpload())
            .vfs(options.vfs())
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
                return new ShareSession(FFLBinding.share(parameters), eventChannel)
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
            deleteRecursively(path);
        } catch (RuntimeException cleanupError) {
            failure.addSuppressed(cleanupError);
        }
    }

    private static void deleteRecursively(Path path) {
        if (!Files.exists(path)) {
            return;
        }
        try (var stream = Files.walk(path)) {
            for (Path item : stream.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(item);
            }
        } catch (IOException error) {
            throw new IllegalStateException("Failed to clean FFL temporary path: " + path, error);
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
        String fileName = Path.of(name).getFileName().toString();
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
