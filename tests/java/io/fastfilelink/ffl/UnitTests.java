package io.fastfilelink.ffl;

import apebind.generated.ffl.APEProcessResult;
import apebind.generated.ffl.APEProcessException;
import apebind.generated.ffl.OptionalValue;
import apebind.generated.ffl.ProcessSession;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public final class UnitTests {
    private UnitTests() {}

    public static void main(String[] args) throws Exception {
        testShareOptionMapping();
        testStreamShareOptionMapping();
        testCallerManagedHook();
        testHookEvents();
        testIntegratedTransferEvents();
        testBoundedSemanticHookEvents();
        testLiveHookListenersSurviveHistoryEviction();
        testHookResponseDoesNotWaitForLocalListeners();
        testDownloadParsing();
        testDownloadSessionApi();
        testClosingRunningDownloadCancelsIt();
        testStreamedDownloadRequiresStdoutConsumptionBeforeWaiting();
        testExplicitDownloadPathWins();
        testKeygenParsing();
        System.out.println("unit: ok");
    }

    private static void testCallerManagedHook() {
        String hookUrl = "http://127.0.0.1:9000/events";
        ShareOptions options = ShareOptions.builder()
            .hookUrl(hookUrl)
            .captureHookEvents(false)
            .build();

        require(FFLClient.createEventChannel(options) == null, "caller-managed hook channel");
        require(
            hookUrl.equals(FFLClient.effectiveHookUrl(options, null)),
            "caller-managed hook URL"
        );
    }

    private static void testHookEvents() throws Exception {
        try (FFLHookEventChannel channel = new FFLHookEventChannel(null)) {
            String payload = """
                {"event":"/hook/server/endpoints/register","timestamp":"2026-09-07T00:00:00Z","data":{"fileName":"file.txt","fileSize":5}}
                """;
            HttpRequest request = HttpRequest.newBuilder(URI.create(channel.url()))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(payload, StandardCharsets.UTF_8))
                .build();
            HttpResponse<String> response = HttpClient.newHttpClient().send(
                request,
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)
            );

            require(response.statusCode() == 200, "hook response");
            require("{}".equals(response.body()), "hook response body");
            require(channel.history().size() == 1, "hook history");
            FFLHookEvent event = channel.history().get(0);
            require("/hook/server/endpoints/register".equals(event.name()), "hook name");
            require(event.semanticName() == null, "registration has no semantic name");
            require("file.txt".equals(event.data().get("fileName")), "hook data");
            List<FFLHookEvent> replayed = new ArrayList<>();
            channel.on(event.name(), replayed::add);
            require(replayed.equals(channel.history()), "hook listener replay");
        }
    }

    private static void testIntegratedTransferEvents() throws Exception {
        try (FFLHookEventChannel channel = new FFLHookEventChannel(null)) {
            sendHookEvent(channel, "/download/complete", 1);
            sendHookEvent(channel, "/webrtc/transfer/complete", 2);
            sendHookEvent(channel, "/p2p/transfer/complete", 3);
            sendHookEvent(channel, "/hook/transfer/complete", 4);
            sendHookEvent(channel, "/transfer/complete", 5);
            sendHookEvent(channel, "/share/available", 6);
            sendHookEvent(channel, "/transfer/create", 7);
            sendHookEvent(channel, "/transfer/progress", 8);
            sendHookEvent(channel, "/download/progress", 9);
            sendHookEvent(channel, "/transfer/fail", 10);

            require(semanticNames(channel, "completed").equals(List.of("/transfer/complete")), "completed");
            require(semanticNames(channel, "progress").equals(List.of("/transfer/progress")), "progress");
            require(semanticNames(channel, "ready").equals(List.of("/share/available")), "ready");
            require(semanticNames(channel, "started").equals(List.of("/transfer/create")), "started");
            require(semanticNames(channel, "failed").equals(List.of("/transfer/fail")), "failed");

            List<FFLHookEvent> replayed = new ArrayList<>();
            channel.onSemantic("completed", replayed::add);
            require(replayed.size() == 1, "completed replays once");
            require("/transfer/complete".equals(replayed.get(0).name()), "replayed completed name");
        }
    }

    private static List<String> semanticNames(FFLHookEventChannel channel, String semanticName) {
        List<String> names = new ArrayList<>();
        for (FFLHookEvent event : channel.history()) {
            if (semanticName.equals(event.semanticName())) {
                names.add(event.name());
            }
        }
        return names;
    }

    private static void testBoundedSemanticHookEvents() throws Exception {
        try (FFLHookEventChannel channel = new FFLHookEventChannel(null, 2)) {
            sendHookEvent(channel, "/share/available", 1);
            sendHookEvent(channel, "/transfer/progress", 2);
            sendHookEvent(channel, "/transfer/complete", 3);

            require(channel.history().size() == 2, "bounded hook history");
            require(
                "completed".equals(channel.history().get(1).semanticName()),
                "semantic hook event name"
            );
            List<FFLHookEvent> replayed = new ArrayList<>();
            channel.onSemantic("completed", replayed::add);
            require(replayed.equals(List.of(channel.history().get(1))), "semantic listener replay");
        }
    }

    private static void testLiveHookListenersSurviveHistoryEviction() throws Exception {
        try (FFLHookEventChannel channel = new FFLHookEventChannel(null, 2)) {
            List<Long> received = new ArrayList<>();
            CountDownLatch delivered = new CountDownLatch(4);
            channel.on("/transfer/progress", event -> received.add(
                ((Number) event.data().get("index")).longValue()
            ));
            channel.on("/transfer/progress", event -> {
                delivered.countDown();
            });
            sendHookEvent(channel, "/transfer/progress", 1);
            sendHookEvent(channel, "/transfer/progress", 2);
            sendHookEvent(channel, "/transfer/progress", 3);
            sendHookEvent(channel, "/transfer/progress", 4);

            require(delivered.await(1, TimeUnit.SECONDS), "lossless live hook delivery");
            require(channel.history().size() == 2, "bounded live hook history");
            require(received.equals(List.of(1L, 2L, 3L, 4L)), "lossless live hook listener");
        }
    }

    private static void testHookResponseDoesNotWaitForLocalListeners() throws Exception {
        try (FFLHookEventChannel channel = new FFLHookEventChannel(null)) {
            CountDownLatch listenerStarted = new CountDownLatch(1);
            CountDownLatch releaseListener = new CountDownLatch(1);
            channel.on("/transfer/progress", event -> {
                listenerStarted.countDown();
                try {
                    releaseListener.await(5, TimeUnit.SECONDS);
                } catch (InterruptedException caught) {
                    Thread.currentThread().interrupt();
                }
            });
            long started = System.nanoTime();
            sendHookEvent(channel, "/transfer/progress", 1);

            require(
                TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started) < 500,
                "hook response must not wait for local listener"
            );
            require(listenerStarted.await(1, TimeUnit.SECONDS), "local listener runs asynchronously");
            releaseListener.countDown();
        }
    }

    private static void sendHookEvent(
        FFLHookEventChannel channel,
        String name,
        long index
    ) throws Exception {
        String payload = "{\"event\":\"" + name + "\",\"data\":{\"index\":" + index + "}}";
        HttpRequest request = HttpRequest.newBuilder(URI.create(channel.url()))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(payload, StandardCharsets.UTF_8))
            .build();
        HttpResponse<String> response = HttpClient.newHttpClient().send(
            request,
            HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)
        );
        require(response.statusCode() == 200, "hook response");
        require("{}".equals(response.body()), "hook response body");
    }

    private static void testShareOptionMapping() throws Exception {
        Path file = Files.createTempFile("ffl-java-unit-", ".txt");
        ShareOptions options = ShareOptions.builder()
            .timeoutSeconds(1800)
            .hookUrl("http://127.0.0.1/events")
            .upload()
            .receipt()
            .receiptConfirm("confirm")
            .qr()
            .resumeUpload(true)
            .build();
        Map<String, Object> values = new FFLClient()
            .buildShareParameters(List.of(file), options, null)
            .toValues();

        require(values.get("paths") instanceof List<?>, "paths must be a list");
        require(Long.valueOf(1800).equals(values.get("timeout")), "timeout mapping");
        require("http://127.0.0.1/events".equals(values.get("hook")), "hook mapping");
        require(Boolean.TRUE.equals(values.get("resume")), "resume mapping");
        require(Boolean.TRUE.equals(values.get("foreground")), "foreground must be forced");
        require(
            Boolean.TRUE.equals(values.get("disable_clipboard")),
            "clipboard must be disabled"
        );
        requireOptionalFlag(values.get("upload"), "upload");
        requireOptionalFlag(values.get("receipt"), "receipt");
        requireOptionalFlag(values.get("qr"), "qr");
        Object receiptConfirm = values.get("receipt_confirm");
        require(receiptConfirm instanceof OptionalValue<?>, "receiptConfirm optional value");
        OptionalValue<?> optional = (OptionalValue<?>) receiptConfirm;
        require(optional.present() && optional.hasValue(), "receiptConfirm value state");
        require("confirm".equals(optional.value()), "receiptConfirm value");
        Files.deleteIfExists(file);
    }

    private static void testStreamShareOptionMapping() {
        FFLClient client = new FFLClient();
        ShareOptions options = ShareOptions.builder().build();
        var parameters = client.buildShareParameters(
            List.of(Path.of("-")),
            options,
            "archive.tar"
        );
        Map<String, Object> values = parameters.toValues();

        require(
            List.of(Path.of("-")).equals(values.get("paths")),
            "stream share must use standard input"
        );
        require("archive.tar".equals(values.get("name")), "stream share name");
        try {
            require(
                FFL.class.getMethod("share", InputStream.class, String.class) != null,
                "FFL exposes stream sharing"
            );
        } catch (NoSuchMethodException error) {
            throw new AssertionError("FFL exposes stream sharing", error);
        }
    }

    private static void testDownloadParsing() {
        Path cwd = Path.of("/tmp/ffl-java-test");
        APEProcessResult process = new APEProcessResult(
            List.of("download", "https://example.test/file"),
            0,
            "WebRTC P2P\nDownloaded: package.zip\n",
            ""
        );
        DownloadResult result = FFLResultParser.parseDownload(process, null, cwd);
        require(result.process() == process, "download process identity");
        require(result.transferMode() == TransferMode.WEBRTC_P2P, "transfer mode");
        require(cwd.resolve("package.zip").equals(result.outputPath()), "download path");

        APEProcessResult tcpProcess = new APEProcessResult(
            List.of("download", "https://example.test/file"),
            0,
            "Using P2P TCP download\nDownloaded: package.zip\n",
            ""
        );
        DownloadResult tcpResult = FFLResultParser.parseDownload(tcpProcess, null, cwd);
        require(tcpResult.transferMode() == TransferMode.WEBRTC_P2P, "P2P TCP mode");
    }

    private static void testDownloadSessionApi() throws Exception {
        require(
            FFL.class.getMethod("startDownload", String.class) != null,
            "FFL exposes cancellable downloads"
        );
        require(
            FFL.class.getMethod("startDownload", String.class, DownloadOptions.class) != null,
            "FFL exposes configured cancellable downloads"
        );
        require(
            DownloadSession.class.getMethod("stdoutBytes") != null,
            "download session exposes binary stdout"
        );
        require(
            DownloadSession.class.getMethod("stdoutStream") != null,
            "download session exposes non-buffering stdout stream"
        );
        require(
            DownloadSession.class.getMethod("abort") != null,
            "download session exposes explicit abort"
        );
        require(
            DownloadSession.class.getMethod("cancelled") != null,
            "download session exposes cancellation state"
        );
        require(
            FFL.class.getMethod("downloadStream", String.class) != null,
            "FFL exposes binary download streaming"
        );

        Path completedPath = Files.createTempFile("ffl-java-completed-", ".bin");
        Files.writeString(completedPath, "completed");
        APEProcessException failure = new APEProcessException(new APEProcessResult(
            List.of("download", "https://example.test/file"),
            11,
            "Downloaded: file.bin",
            "Progress 100%\nTerminating on uncaught SIGSEGV"
        ));
        APEProcessResult completed = DownloadSession.completedDownloadProcess(
            failure,
            completedPath
        );
        require(completed.returnCode() == 0, "completed download process");
        Files.deleteIfExists(completedPath);
    }

    private static void testClosingRunningDownloadCancelsIt() throws Exception {
        String executable = Path.of(
            System.getProperty("java.home"),
            "bin",
            System.getProperty("os.name").startsWith("Windows") ? "java.exe" : "java"
        ).toString();
        Process child = new ProcessBuilder(
            executable,
            "-cp",
            System.getProperty("java.class.path"),
            Sleeper.class.getName()
        ).start();

        try {
            Class<?> resultFile = Class.forName("apebind.generated.ffl.APEClient$ResultFile");
            Constructor<ProcessSession> constructor = ProcessSession.class.getDeclaredConstructor(
                Process.class,
                List.class,
                resultFile
            );
            constructor.setAccessible(true);
            DownloadSession session = new DownloadSession(
                constructor.newInstance(child, List.of("download"), null),
                null,
                Path.of(".")
            );

            require(session.running(), "download session starts running");
            session.close();
            require(session.cancelled(), "closing a running download cancels it");
            try {
                session.waitFor();
                throw new AssertionError("cancelled download must not resolve successfully");
            } catch (CancellationException expected) {
                // Expected: closing an active semantic download is an abort.
            }
        } finally {
            child.destroyForcibly();
            child.waitFor();
        }
    }

    private static void testStreamedDownloadRequiresStdoutConsumptionBeforeWaiting()
        throws Exception {
        String executable = Path.of(
            System.getProperty("java.home"),
            "bin",
            System.getProperty("os.name").startsWith("Windows") ? "java.exe" : "java"
        ).toString();
        Process child = new ProcessBuilder(
            executable,
            "-cp",
            System.getProperty("java.class.path"),
            LargeStdout.class.getName()
        ).start();

        try {
            Class<?> resultFile = Class.forName("apebind.generated.ffl.APEClient$ResultFile");
            Constructor<ProcessSession> constructor = ProcessSession.class.getDeclaredConstructor(
                Process.class,
                List.class,
                resultFile,
                boolean.class
            );
            constructor.setAccessible(true);
            ProcessSession session = constructor.newInstance(
                child,
                List.of("download"),
                null,
                false
            );

            try {
                session.waitFor(1.0);
                throw new AssertionError("unconsumed streamed stdout must not wait");
            } catch (IllegalStateException expected) {
                require(
                    expected.getMessage().contains("streamed stdout must be consumed"),
                    "streamed stdout wait error"
                );
            } finally {
                session.stop();
            }
        } finally {
            child.destroyForcibly();
            child.waitFor();
        }
    }

    public static final class Sleeper {
        private Sleeper() {}

        public static void main(String[] args) throws InterruptedException {
            Thread.sleep(Long.MAX_VALUE);
        }
    }

    public static final class LargeStdout {
        private LargeStdout() {}

        public static void main(String[] args) {
            System.out.writeBytes(new byte[2 * 1024 * 1024]);
            System.out.flush();
        }
    }

    private static void testExplicitDownloadPathWins() {
        Path cwd = Path.of("/tmp/ffl-java-test");
        APEProcessResult process = new APEProcessResult(
            List.of("download", "https://example.test/file"),
            0,
            "HTTP fallback\nDownloaded: ignored.zip\n",
            ""
        );
        DownloadResult result = FFLResultParser.parseDownload(
            process,
            Path.of("chosen.bin"),
            cwd
        );
        require(result.transferMode() == TransferMode.HTTP_FALLBACK, "fallback mode");
        require(cwd.resolve("chosen.bin").equals(result.outputPath()), "explicit output path");
    }

    private static void testKeygenParsing() throws Exception {
        Path directory = Files.createTempDirectory("ffl-java-keygen-unit-");
        Path privateKey = directory.resolve("alice.fflkey");
        Path publicKey = directory.resolve("alice.fflpub");
        Files.writeString(privateKey, "private");
        Files.writeString(publicKey, "public");
        APEProcessResult process = new APEProcessResult(
            List.of("keygen", "--name", "alice"),
            0,
            "Generated RSA-2048 keypair:\n"
                + "  Private key : " + privateKey + "\n"
                + "  Public key  : " + publicKey + "  ← share with sender\n",
            ""
        );
        KeygenResult result = FFLResultParser.parseKeygen(process, directory);
        require(privateKey.equals(result.privateKeyPath()), "private key path");
        require(publicKey.equals(result.publicKeyPath()), "public key path");
        Files.deleteIfExists(privateKey);
        Files.deleteIfExists(publicKey);
        Files.deleteIfExists(directory);
    }

    private static void requireOptionalFlag(Object value, String label) {
        require(value instanceof OptionalValue<?>, label + " optional value");
        OptionalValue<?> optional = (OptionalValue<?>) value;
        require(optional.present() && !optional.hasValue(), label + " flag state");
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
