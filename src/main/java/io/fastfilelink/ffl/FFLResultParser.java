package io.fastfilelink.ffl;

import apebind.generated.ffl.APEProcessResult;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class FFLResultParser {
    private static final Pattern DOWNLOADED_PATTERN = Pattern.compile(
        "^Downloaded:\\s+(.+)$",
        Pattern.MULTILINE
    );
    private static final Pattern DOWNLOADING_PATTERN = Pattern.compile(
        "Downloading\\s+(.+?)\\s+\\("
    );
    private static final Pattern PRIVATE_KEY_PATTERN = Pattern.compile(
        "^\\s*Private key\\s*:\\s*(.+?)\\s*$",
        Pattern.MULTILINE
    );
    private static final Pattern PUBLIC_KEY_PATTERN = Pattern.compile(
        "^\\s*Public key\\s*:\\s*(.+?)\\s*$",
        Pattern.MULTILINE
    );

    private FFLResultParser() {}

    static DownloadResult parseDownload(
        APEProcessResult process,
        Path requestedOutputPath,
        Path cwd
    ) {
        String output = combinedOutput(process);
        Path outputPath = requestedOutputPath == null
            ? detectDownloadPath(output, cwd)
            : resolve(cwd, requestedOutputPath);
        return new DownloadResult(process, outputPath, detectTransferMode(output));
    }

    static KeygenResult parseKeygen(APEProcessResult process, Path cwd) {
        String output = combinedOutput(process);
        Matcher privateMatch = PRIVATE_KEY_PATTERN.matcher(output);
        Matcher publicMatch = PUBLIC_KEY_PATTERN.matcher(output);
        if (!privateMatch.find() || !publicMatch.find()) {
            throw new FFLOutputException("FFL keygen output did not contain both key paths");
        }

        Path privateKey = resolve(cwd, Paths.get(cleanKeyPath(privateMatch.group(1))));
        Path publicKey = resolve(cwd, Paths.get(cleanKeyPath(publicMatch.group(1))));
        if (!Files.isRegularFile(privateKey) || !Files.isRegularFile(publicKey)) {
            throw new FFLOutputException("FFL keygen reported key files that do not exist");
        }
        return new KeygenResult(process, privateKey, publicKey);
    }

    private static String combinedOutput(APEProcessResult process) {
        if (process.stderr().isBlank()) {
            return process.stdout();
        }
        if (process.stdout().isBlank()) {
            return process.stderr();
        }
        return process.stdout() + "\n" + process.stderr();
    }

    private static TransferMode detectTransferMode(String output) {
        if (
            output.contains("P2P direct")
            || output.contains("P2P TCP")
            || output.contains("WebRTC P2P")
        ) {
            return TransferMode.WEBRTC_P2P;
        }
        if (output.contains("HTTP fallback")) {
            return TransferMode.HTTP_FALLBACK;
        }
        if (
            output.contains("HTTP download")
            || output.contains("downloading directly via HTTP")
            || output.contains("WebRTC not supported")
        ) {
            return TransferMode.HTTP_DIRECT;
        }
        return TransferMode.UNKNOWN;
    }

    private static Path detectDownloadPath(String output, Path cwd) {
        Matcher downloaded = DOWNLOADED_PATTERN.matcher(output);
        if (downloaded.find()) {
            return resolve(cwd, Paths.get(downloaded.group(1).trim()));
        }
        Matcher downloading = DOWNLOADING_PATTERN.matcher(output);
        if (downloading.find()) {
            return resolve(cwd, Paths.get(downloading.group(1).trim()));
        }
        return null;
    }

    private static Path resolve(Path cwd, Path value) {
        return value.isAbsolute() ? value.normalize() : cwd.resolve(value).normalize();
    }

    private static String cleanKeyPath(String value) {
        int arrowIndex = value.indexOf('←');
        return (arrowIndex >= 0 ? value.substring(0, arrowIndex) : value).trim();
    }
}
