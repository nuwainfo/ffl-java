package io.fastfilelink.ffl;

import apebind.generated.ffl.APEProcessResult;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.zip.ZipFile;

public final class FFLIntegrationTests {
    private FFLIntegrationTests() {}

    public static void main(String[] args) throws Exception {
        testVersionAndRaw();
        testBinaryTransfer();
        testStreamedDownload();
        testTextTransferAndCleanup();
        testBytesTransfer();
        testMultipleFileTransfer();
        testKeygen();
        testE2eeTransfer();
        testBasicAuthTransfer();
        testPickupCodeTransfer();
        testFolderExclude();
        testForcedRelayTransfer();
        testRequestedPort();
        testQrImage();
        testRecipientPublicKeyAuth();
        testShareHook();
        testSessionStop();
        System.out.println("ffl integration: ok");
    }

    private static void testVersionAndRaw() {
        require(FFL.version().contains("FastFileLink"), "version output");
        APEProcessResult result = FFL.raw(List.of("download", "--version"));
        require(result.returnCode() == 0, "raw version return code");
        require(result.stdout().contains("FastFileLink"), "raw version output");
    }

    private static void testBinaryTransfer() throws IOException {
        Path directory = Files.createTempDirectory("ffl-java-test-");
        Path source = directory.resolve("source.bin");
        Path received = directory.resolve("received.bin");
        byte[] payload = new byte[4096];
        for (int index = 0; index < payload.length; index += 1) {
            payload[index] = (byte) index;
        }
        Files.write(source, payload);
        downloadSharedFile(source, received, shareOptions("received.bin"), DownloadOptions.defaults());
        require(java.util.Arrays.equals(payload, Files.readAllBytes(received)), "binary transfer");
    }

    private static void testStreamedDownload() throws IOException {
        Path directory = Files.createTempDirectory("ffl-java-stream-");
        Path source = directory.resolve("source.bin");
        byte[] payload = new byte[1024 * 1024];
        for (int index = 0; index < payload.length; index += 1) {
            payload[index] = (byte) index;
        }
        Files.write(source, payload);

        try (ShareSession shared = FFL.share(source, shareOptions("stream.bin"));
             DownloadSession downloaded = FFL.downloadStream(shared.link());
             InputStream stream = downloaded.stdoutStream()) {
            byte[] received = stream.readAllBytes();
            downloaded.waitFor();
            require(java.util.Arrays.equals(payload, received), "streamed download");
            require(downloaded.stdoutBytes().length == 0, "streamed output is not retained");
        }
    }

    private static void testTextTransferAndCleanup() throws IOException {
        Path directory = Files.createTempDirectory("ffl-java-test-");
        Path received = directory.resolve("message.txt");
        String text = "FFL Java: portable APE.\n";
        Set<String> before = temporaryFFLDirectories();
        try (ShareSession session = FFL.shareText(text, "message.txt", shareOptions("message.txt"))) {
            DownloadResult result = FFL.download(
                session.link(),
                DownloadOptions.builder().outputPath(received).build()
            );
            require(result.returnCode() == 0, "text transfer return code");
        }
        require(text.equals(Files.readString(received)), "text transfer");
        require(before.equals(temporaryFFLDirectories()), "temporary source cleanup");
    }

    private static void testBytesTransfer() throws IOException {
        Path directory = Files.createTempDirectory("ffl-java-test-");
        Path received = directory.resolve("payload.dat");
        byte[] payload = new byte[] {0, -1, 16, 102, 102, 108, 0};
        try (ShareSession session = FFL.shareBytes(payload, "payload.dat", shareOptions("payload.dat"))) {
            DownloadResult result = FFL.download(
                session.link(),
                DownloadOptions.builder().outputPath(received).build()
            );
            require(result.returnCode() == 0, "bytes transfer return code");
        }
        require(java.util.Arrays.equals(payload, Files.readAllBytes(received)), "bytes transfer");
    }

    private static void testMultipleFileTransfer() throws IOException {
        Path directory = Files.createTempDirectory("ffl-java-test-");
        Path first = directory.resolve("first.txt");
        Path second = directory.resolve("second.txt");
        Path archive = directory.resolve("bundle.zip");
        Files.writeString(first, "first file");
        Files.writeString(second, "second file");
        downloadSharedFiles(List.of(first, second), archive, shareOptions("bundle.zip"));
        try (ZipFile zip = new ZipFile(archive.toFile())) {
            require("first file".equals(readZipEntry(zip, "first.txt")), "first archive entry");
            require("second file".equals(readZipEntry(zip, "second.txt")), "second archive entry");
        }
    }

    private static void testKeygen() throws IOException {
        KeygenResult result = FFL.keygen("java-integration-recipient");
        require(result.returnCode() == 0, "keygen return code");
        require(Files.isRegularFile(result.privateKeyPath()), "private key exists");
        require(Files.isRegularFile(result.publicKeyPath()), "public key exists");
        require(!Files.readString(result.privateKeyPath()).isBlank(), "private key contents");
        require(!Files.readString(result.publicKeyPath()).isBlank(), "public key contents");
    }

    private static void testE2eeTransfer() throws IOException {
        Path directory = Files.createTempDirectory("ffl-java-test-");
        Path source = directory.resolve("encrypted.bin");
        Path received = directory.resolve("received.bin");
        byte[] payload = "e2ee transfer payload".getBytes(StandardCharsets.UTF_8);
        Files.write(source, payload);
        downloadSharedFile(
            source,
            received,
            ShareOptions.builder().name("received.bin").e2ee(true).maxDownloads(1).timeoutSeconds(90).build(),
            DownloadOptions.defaults()
        );
        require(java.util.Arrays.equals(payload, Files.readAllBytes(received)), "E2EE transfer");
    }

    private static void testBasicAuthTransfer() throws IOException {
        Path directory = Files.createTempDirectory("ffl-java-test-");
        Path source = directory.resolve("protected.txt");
        Path received = directory.resolve("received.txt");
        Files.writeString(source, "HTTP Basic Auth protected payload");
        ShareOptions share = ShareOptions.builder()
            .name("received.txt").authUser("ffl-java-test")
            .authPassword("correct-horse-battery-staple").maxDownloads(1).timeoutSeconds(90).build();
        DownloadOptions download = DownloadOptions.builder().outputPath(received)
            .authUser("ffl-java-test").authPassword("correct-horse-battery-staple").build();
        downloadSharedFile(source, received, share, download);
        require("HTTP Basic Auth protected payload".equals(Files.readString(received)), "basic auth transfer");
    }

    private static void testPickupCodeTransfer() throws IOException {
        Path directory = Files.createTempDirectory("ffl-java-test-");
        Path source = directory.resolve("pickup.txt");
        Path received = directory.resolve("received.txt");
        Files.writeString(source, "Pickup code protected payload");
        ShareOptions share = ShareOptions.builder().name("received.txt").recipientAuth("pickup")
            .pickupCode("654321").maxDownloads(1).timeoutSeconds(90).build();
        DownloadOptions download = DownloadOptions.builder().outputPath(received)
            .recipientAuth("pickup").pickupCode("654321").build();
        downloadSharedFile(source, received, share, download);
        require("Pickup code protected payload".equals(Files.readString(received)), "pickup transfer");
    }

    private static void testFolderExclude() throws IOException {
        Path directory = Files.createTempDirectory("ffl-java-test-");
        Path source = directory.resolve("source-folder");
        Path archive = directory.resolve("folder.zip");
        Files.createDirectory(source);
        Files.writeString(source.resolve("included.txt"), "included");
        Files.writeString(source.resolve("ignored.txt"), "ignored");
        ShareOptions options = ShareOptions.builder().name("folder.zip").exclude("ignored.txt")
            .maxDownloads(1).timeoutSeconds(90).build();
        downloadSharedFile(source, archive, options, DownloadOptions.defaults());
        try (ZipFile zip = new ZipFile(archive.toFile())) {
            require("included".equals(readZipEntry(zip, "source-folder/included.txt")), "included entry");
            require(zip.getEntry("source-folder/ignored.txt") == null, "excluded entry");
        }
    }

    private static void testForcedRelayTransfer() throws IOException {
        Path directory = Files.createTempDirectory("ffl-java-test-");
        Path source = directory.resolve("relay.txt");
        Path received = directory.resolve("received.txt");
        Files.writeString(source, "Forced relay payload");
        ShareOptions options = ShareOptions.builder().name("received.txt").forceRelay(true)
            .maxDownloads(1).timeoutSeconds(90).build();
        downloadSharedFile(source, received, options, DownloadOptions.defaults());
        require("Forced relay payload".equals(Files.readString(received)), "forced relay transfer");
    }

    private static void testRequestedPort() throws IOException {
        Path directory = Files.createTempDirectory("ffl-java-test-");
        Path source = directory.resolve("port.txt");
        Path received = directory.resolve("received.txt");
        Files.writeString(source, "Explicit port payload");
        ShareOptions options = ShareOptions.builder().name("received.txt").port(availablePort())
            .maxDownloads(1).timeoutSeconds(90).build();
        downloadSharedFile(source, received, options, DownloadOptions.defaults());
        require("Explicit port payload".equals(Files.readString(received)), "requested port transfer");
    }

    private static void testQrImage() throws IOException {
        Path directory = Files.createTempDirectory("ffl-java-test-");
        Path source = directory.resolve("qr.txt");
        Path received = directory.resolve("received.txt");
        Path qr = directory.resolve("share.png");
        Files.writeString(source, "QR share payload");
        ShareOptions options = ShareOptions.builder().name("received.txt").qr(qr)
            .maxDownloads(1).timeoutSeconds(90).build();
        downloadSharedFile(source, received, options, DownloadOptions.defaults());
        require(Files.size(qr) > 0, "QR image");
    }

    private static void testRecipientPublicKeyAuth() throws IOException {
        KeygenResult keys = FFL.keygen("java-integration-public-key");
        Path directory = Files.createTempDirectory("ffl-java-test-");
        Path source = directory.resolve("recipient.txt");
        Path received = directory.resolve("received.txt");
        Files.writeString(source, "Public key protected payload");
        ShareOptions share = ShareOptions.builder().name("received.txt").recipientAuth("pubkey")
            .recipientPublicKey(keys.publicKeyPath()).maxDownloads(1).timeoutSeconds(90).build();
        DownloadOptions download = DownloadOptions.builder().outputPath(received).recipientAuth("pubkey")
            .recipientPrivateKey(keys.privateKeyPath()).build();
        downloadSharedFile(source, received, share, download);
        require("Public key protected payload".equals(Files.readString(received)), "public key transfer");
    }

    private static void testShareHook() throws Exception {
        try (ServerSocket server = new ServerSocket(0)) {
            CountDownLatch received = new CountDownLatch(1);
            Thread thread = new Thread(() -> receiveHook(server, received), "ffl-java-hook");
            thread.setDaemon(true);
            thread.start();
            Path directory = Files.createTempDirectory("ffl-java-test-");
            Path source = directory.resolve("hook.txt");
            Path output = directory.resolve("received.txt");
            Files.writeString(source, "Hook payload");
            ShareOptions options = ShareOptions.builder().name("received.txt")
                .hookUrl("http://127.0.0.1:" + server.getLocalPort() + "/events")
                .maxDownloads(1).timeoutSeconds(90).build();
            try (ShareSession session = FFL.share(source, options)) {
                DownloadResult result = FFL.download(
                    session.link(),
                    DownloadOptions.builder().outputPath(output).build()
                );
                require(result.returnCode() == 0, "hook download return code");
                require(!session.eventHistory().isEmpty(), "semantic hook event history");
                List<FFLHookEvent> endpoints = session.eventHistory().stream()
                    .filter(event -> "/hook/server/endpoints/register".equals(event.name()))
                    .toList();
                require(!endpoints.isEmpty(), "endpoint registration event");
                List<FFLHookEvent> completed = session.eventHistory().stream()
                    .filter(event -> "completed".equals(event.semanticName()))
                    .toList();
                require(
                    List.of("/transfer/complete").equals(
                        completed.stream().map(FFLHookEvent::name).toList()
                    ),
                    "integrated transfer complete"
                );
                List<FFLHookEvent> replayed = new ArrayList<>();
                session.onRaw("/hook/server/endpoints/register", replayed::add);
                require(replayed.equals(endpoints), "raw hook event replay");
            }
            require(received.await(5, TimeUnit.SECONDS), "hook event");
        }
    }

    private static void testSessionStop() throws IOException {
        Path directory = Files.createTempDirectory("ffl-java-test-");
        Path source = directory.resolve("session.txt");
        Files.writeString(source, "Session lifecycle payload");
        ShareOptions options = ShareOptions.builder().maxDownloads(0).timeoutSeconds(90).build();
        try (ShareSession session = FFL.share(source, options)) {
            require(session.running(), "session starts running");
            session.stop();
            require(!session.running(), "session stops");
            require(session.returnCode() != null, "session return code");
        }
    }

    private static ShareOptions shareOptions(String name) {
        return ShareOptions.builder().name(name).maxDownloads(1).timeoutSeconds(90).build();
    }

    private static void downloadSharedFile(
        Path source,
        Path received,
        ShareOptions options,
        DownloadOptions downloadOptions
    ) {
        try (ShareSession session = FFL.share(source, options)) {
            DownloadOptions optionsWithOutput = DownloadOptions.builder().outputPath(received)
                .resume(downloadOptions.resume()).authUser(downloadOptions.authUser())
                .authPassword(downloadOptions.authPassword()).recipientAuth(downloadOptions.recipientAuth())
                .pickupCode(downloadOptions.pickupCode()).recipientPrivateKey(downloadOptions.recipientPrivateKey())
                .build();
            DownloadResult result = FFL.download(session.link(), optionsWithOutput);
            require(result.returnCode() == 0, "download return code");
            require(result.transferMode() != TransferMode.UNKNOWN, "download transfer mode");
            require(Files.isRegularFile(received), "downloaded file");
        }
    }

    private static void downloadSharedFiles(List<Path> sources, Path received, ShareOptions options) {
        try (ShareSession session = FFL.share(sources, options)) {
            DownloadResult result = FFL.download(
                session.link(),
                DownloadOptions.builder().outputPath(received).build()
            );
            require(result.returnCode() == 0, "archive download return code");
        }
    }

    private static String readZipEntry(ZipFile zip, String name) throws IOException {
        var entry = zip.getEntry(name);
        if (entry == null) {
            return null;
        }
        try (var stream = zip.getInputStream(entry)) {
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static long availablePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }

    private static Set<String> temporaryFFLDirectories() throws IOException {
        try (var paths = Files.list(Path.of(System.getProperty("java.io.tmpdir")))) {
            return paths.map(path -> path.getFileName().toString())
                .filter(name -> name.startsWith("ffl-java-"))
                .collect(java.util.stream.Collectors.toSet());
        }
    }

    private static void receiveHook(ServerSocket server, CountDownLatch received) {
        while (!server.isClosed()) {
            try (Socket socket = server.accept()) {
                ByteArrayOutputStream request = new ByteArrayOutputStream();
                byte[] buffer = new byte[1024];
                int count;
                while ((count = socket.getInputStream().read(buffer)) > 0) {
                    request.write(buffer, 0, count);
                    if (new String(request.toByteArray(), StandardCharsets.ISO_8859_1)
                        .contains("\r\n\r\n")) {
                        break;
                    }
                }
                socket.getOutputStream().write(
                    ("HTTP/1.1 200 OK\r\nContent-Type: application/json\r\n"
                        + "Content-Length: 2\r\nConnection: close\r\n\r\n{}")
                        .getBytes(StandardCharsets.US_ASCII)
                );
                socket.getOutputStream().flush();
                if (request.size() > 0) {
                    received.countDown();
                }
            } catch (IOException error) {
                if (server.isClosed()) {
                    return;
                }
                throw new IllegalStateException("Failed to receive FFL hook", error);
            }
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
