package io.fastfilelink.ffl;

import apebind.generated.ffl.APEProcessResult;
import apebind.generated.ffl.APEProcessException;
import apebind.generated.ffl.ProcessSession;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CancellationException;

public final class DownloadSession implements AutoCloseable {
    private final ProcessSession session;
    private final Path outputPath;
    private final Path cwd;
    private volatile boolean aborted;

    DownloadSession(ProcessSession session, Path outputPath, Path cwd) {
        this.session = session;
        this.outputPath = outputPath;
        this.cwd = cwd;
    }

    public List<String> argv() {
        return session.argv();
    }

    public long pid() {
        return session.pid();
    }

    public boolean running() {
        return session.running();
    }

    public Integer returnCode() {
        return session.returnCode();
    }

    public boolean cancelled() {
        return aborted;
    }

    public byte[] stdoutBytes() {
        return session.stdoutBytes();
    }

    public InputStream stdoutStream() {
        return session.stdoutStream();
    }

    public DownloadResult waitFor() {
        try {
            session.waitFor();
            throwIfAborted();
            return result();
        } catch (APEProcessException error) {
            throwIfAborted();
            return completedResult(error);
        }
    }

    public DownloadResult waitFor(double timeoutSeconds) {
        try {
            session.waitFor(timeoutSeconds);
            throwIfAborted();
            return result();
        } catch (APEProcessException error) {
            throwIfAborted();
            return completedResult(error);
        }
    }

    public void abort() {
        aborted = true;
        session.stop();
    }

    public void abort(double timeoutSeconds) {
        aborted = true;
        session.stop(timeoutSeconds);
    }

    public void stop() {
        abort();
    }

    public void stop(double timeoutSeconds) {
        abort(timeoutSeconds);
    }

    @Override
    public void close() {
        if (running()) {
            abort();
            return;
        }

        session.close();
    }

    private DownloadResult result() {
        Integer returnCode = session.returnCode();
        if (returnCode == null) {
            throw new IllegalStateException("FFL download session is still running");
        }

        return FFLResultParser.parseDownload(
            new APEProcessResult(session.argv(), returnCode, session.stdout(), session.stderr()),
            outputPath,
            cwd
        );
    }

    private void throwIfAborted() {
        if (aborted) {
            throw new CancellationException("FFL download was aborted");
        }
    }

    private DownloadResult completedResult(APEProcessException error) {
        APEProcessResult completed = completedDownloadProcess(error, outputPath);
        return FFLResultParser.parseDownload(completed, outputPath, cwd);
    }

    static APEProcessResult completedDownloadProcess(
        APEProcessException error,
        Path outputPath
    ) {
        if (outputPath == null || !isCompletedDownload(error.result(), outputPath)) {
            throw error;
        }

        APEProcessResult process = error.result();
        return new APEProcessResult(process.argv(), 0, process.stdout(), process.stderr());
    }

    private static boolean isCompletedDownload(APEProcessResult process, Path outputPath) {
        try {
            return Files.isRegularFile(outputPath)
                && Files.size(outputPath) > 0
                && (process.stdout() + "\n" + process.stderr()).contains("100%");
        } catch (IOException error) {
            return false;
        }
    }
}
