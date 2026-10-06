package io.fastfilelink.ffl;

import apebind.generated.ffl.APEProcessResult;
import java.nio.file.Path;
import java.util.List;

public record DownloadResult(
    APEProcessResult process,
    Path outputPath,
    TransferMode transferMode
) {
    public List<String> argv() {
        return process.argv();
    }

    public int returnCode() {
        return process.returnCode();
    }

    public String stdout() {
        return process.stdout();
    }

    public String stderr() {
        return process.stderr();
    }
}
