package io.fastfilelink.ffl;

import apebind.generated.ffl.FFLBinding;
import java.nio.file.Files;
import java.nio.file.Path;

public final class RealTests {
    private RealTests() {}

    public static void main(String[] args) {
        testVersion();
        testHiddenCommands();
        testKeygen();
        System.out.println("real: ok");
    }

    private static void testVersion() {
        String version = FFL.version();
        require(version.contains("FastFileLink"), "version output");
    }

    private static void testHiddenCommands() {
        var download = FFLBinding.download(
            FFLBinding.DownloadParameters.builder()
                .url("https://example.invalid")
                .version(true)
                .build()
        );
        var keygen = FFLBinding.keygen(
            FFLBinding.KeygenParameters.builder()
                .version(true)
                .build()
        );
        require(download.returnCode() == 0, "download --version return code");
        require(download.stdout().contains("FastFileLink"), "download --version output");
        require(keygen.returnCode() == 0, "keygen --version return code");
        require(keygen.stdout().contains("FastFileLink"), "keygen --version output");
    }

    private static void testKeygen() {
        Path cwd = Path.of("").toAbsolutePath().normalize();
        KeygenResult result = FFL.keygen("java-test-recipient");
        Path privateKey = cwd.resolve("java-test-recipient.fflkey");
        Path publicKey = cwd.resolve("java-test-recipient.fflpub");
        require(privateKey.equals(result.privateKeyPath()), "private key result path");
        require(publicKey.equals(result.publicKeyPath()), "public key result path");
        require(Files.isRegularFile(privateKey), "private key file");
        require(Files.isRegularFile(publicKey), "public key file");
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
