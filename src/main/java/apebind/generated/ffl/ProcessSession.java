package apebind.generated.ffl;

import java.io.ByteArrayOutputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

public final class ProcessSession implements AutoCloseable {
    private final Process process;
    private final List<String> argv;
    private APEClient.ResultFile resultFile;
    private final StreamCollector stdoutCollector;
    private final StreamCollector stderrCollector;
    private volatile boolean stoppedByClient;
    private volatile Object result;
    private final List<Runnable> exitListeners = new java.util.ArrayList<>();
    private final AtomicBoolean exitNotified = new AtomicBoolean();
    private volatile InputStream stdinSource;
    private CompletableFuture<Void> stdinCopy;
    private volatile IOException stdinError;
    private final boolean captureStdout;
    private final AtomicBoolean streamedStdoutOpened = new AtomicBoolean();
    private volatile boolean streamedStdoutConsumed;

    ProcessSession(Process process, List<String> argv, APEClient.ResultFile resultFile) {
        this(process, argv, resultFile, true);
    }

    ProcessSession(
        Process process,
        List<String> argv,
        APEClient.ResultFile resultFile,
        boolean captureStdout
    ) {
        this.process = process;
        this.argv = List.copyOf(argv);
        this.resultFile = resultFile;
        this.captureStdout = captureStdout;
        this.streamedStdoutConsumed = captureStdout;
        this.stdoutCollector = captureStdout
            ? new StreamCollector(process.getInputStream())
            : null;
        this.stderrCollector = new StreamCollector(process.getErrorStream());

        CompletableFuture.runAsync(() -> {
            try {
                process.waitFor();
                closeStdinSource();
                notifyExit();
            } catch (InterruptedException error) {
                Thread.currentThread().interrupt();
            }
        });
    }

    public List<String> argv() {
        return argv;
    }

    public long pid() {
        return process.pid();
    }

    public boolean running() {
        return process.isAlive();
    }

    public Integer returnCode() {
        if (process.isAlive()) {
            return null;
        }

        if (stoppedByClient) {
            return 0;
        }

        return APEClient.normalizeAPEReturnCode(process.exitValue());
    }

    public String stdout() {
        return stdoutCollector == null ? "" : stdoutCollector.text();
    }

    public byte[] stdoutBytes() {
        return stdoutCollector == null ? new byte[0] : stdoutCollector.bytes();
    }

    public InputStream stdoutStream() {
        if (captureStdout) {
            return process.getInputStream();
        }

        if (!streamedStdoutOpened.compareAndSet(false, true)) {
            throw new IllegalStateException("streamed stdout can only be consumed once");
        }

        return new FilterInputStream(process.getInputStream()) {
            @Override
            public int read() throws IOException {
                int value = super.read();
                markStreamedStdoutConsumed(value < 0);
                return value;
            }

            @Override
            public int read(byte[] buffer, int offset, int length) throws IOException {
                int count = super.read(buffer, offset, length);
                markStreamedStdoutConsumed(count < 0);
                return count;
            }
        };
    }

    private void markStreamedStdoutConsumed(boolean reachedEnd) {
        if (reachedEnd) {
            streamedStdoutConsumed = true;
        }
    }

    private void closeStreamedStdout() {
        if (captureStdout) {
            return;
        }

        try {
            process.getInputStream().close();
        } catch (IOException ignored) {
            // Closing an unconsumed stream is best-effort during teardown.
        }
    }

    private void requireStreamedStdout(boolean required) {
        if (captureStdout || streamedStdoutConsumed) {
            return;
        }

        if (required) {
            throw new IllegalStateException(
                "streamed stdout must be consumed before waiting for the process"
            );
        }

        closeStreamedStdout();
    }

    void attachStdin(InputStream source) {
        stdinSource = source;
        stdinCopy = CompletableFuture.runAsync(() -> copyStdin(source));
    }

    private void copyStdin(InputStream source) {
        try (source; OutputStream destination = process.getOutputStream()) {
            byte[] buffer = new byte[8192];

            while (true) {
                int count;
                try {
                    count = source.read(buffer);
                } catch (IOException caught) {
                    recordStdinError(caught);
                    return;
                }

                if (count < 0) {
                    return;
                }

                try {
                    destination.write(buffer, 0, count);
                } catch (IOException ignored) {
                    // FFL can close stdin before it exits. That EPIPE-like
                    // condition must not leak into the host application.
                    return;
                }
            }
        } catch (IOException caught) {
            if (process.isAlive() && !stoppedByClient) {
                recordStdinError(caught);
            }
        }
    }

    private void recordStdinError(IOException error) {
        if (stoppedByClient || !process.isAlive()) {
            return;
        }

        stdinError = error;
        destroyProcessTree(true);
    }

    private void closeStdinSource() {
        InputStream source = stdinSource;
        stdinSource = null;

        if (source == null) {
            return;
        }

        try {
            source.close();
        } catch (IOException ignored) {
            // Closing a caller-owned source is best-effort during teardown.
        }
    }

    private void waitForStdin() {
        if (stdinCopy != null) {
            stdinCopy.join();
        }

        if (stdinError != null && !stoppedByClient) {
            throw new IllegalStateException("Failed to read APE stdin", stdinError);
        }
    }

    public String stderr() {
        return stderrCollector.text();
    }

    public Object result() {
        return result;
    }

    void setResult(Object result) {
        this.result = result;
    }

    public ProcessSession onExit(Runnable listener) {
        boolean runNow;

        synchronized (exitListeners) {
            runNow = exitNotified.get();

            if (!runNow) {
                exitListeners.add(listener);
            }
        }

        if (runNow) {
            listener.run();
        }

        return this;
    }

    private void notifyExit() {
        if (!exitNotified.compareAndSet(false, true)) {
            return;
        }

        List<Runnable> listeners;

        synchronized (exitListeners) {
            listeners = List.copyOf(exitListeners);
            exitListeners.clear();
        }

        for (Runnable listener : listeners) {
            listener.run();
        }
    }

    void cleanupResultFile() {
        if (resultFile == null) {
            return;
        }

        APEClient.ResultFile owned = resultFile;
        resultFile = null;
        owned.cleanup();
    }

    public int waitFor() {
        return waitFor(null, true);
    }

    public int waitFor(Double timeoutSeconds) {
        return waitFor(timeoutSeconds, true);
    }

    private int waitFor(Double timeoutSeconds, boolean requireStreamedStdout) {
        requireStreamedStdout(requireStreamedStdout);

        try {
            boolean exited;
            if (timeoutSeconds == null) {
                process.waitFor();
                exited = true;
            } else {
                exited = process.waitFor(
                    Math.max(1L, Math.round(timeoutSeconds * 1000.0)),
                    TimeUnit.MILLISECONDS
                );
            }

            if (!exited) {
                destroyProcessTree(true);
                process.waitFor();
                awaitStdout(true);
                stderrCollector.await(true);
                throw new APETimeoutException(argv, timeoutSeconds);
            }

            waitForStdin();
            awaitStdout(stoppedByClient);
            stderrCollector.await(stoppedByClient);
            int returnCode = stoppedByClient ? 0 : APEClient.normalizeAPEReturnCode(process.exitValue());
            if (returnCode != 0 && !stoppedByClient) {
                throw new APEProcessException(
                    new APEProcessResult(argv, returnCode, stdout(), stderr())
                );
            }

            return returnCode;
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for APE", error);
        }
    }

    public void stop() {
        stop(5.0);
    }

    public void stop(double timeoutSeconds) {
        if (!process.isAlive()) {
            try {
                waitFor(null, false);
            } finally {
                cleanupResultFile();
            }

            return;
        }

        stoppedByClient = true;
        closeStdinSource();
        closeStreamedStdout();
        destroyProcessTree(false);
        try {
            if (!process.waitFor(
                Math.max(1L, Math.round(timeoutSeconds * 1000.0)),
                TimeUnit.MILLISECONDS
            )) {
                destroyProcessTree(true);
                process.waitFor();
            }

            waitForStdin();
            awaitStdout(true);
            stderrCollector.await(true);
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            destroyProcessTree(true);
            throw new IllegalStateException("Interrupted while stopping APE", error);
        } finally {
            cleanupResultFile();
        }
    }

    @Override
    public void close() {
        if (process.isAlive()) {
            stop();
            return;
        }

        try {
            waitFor(null, false);
        } finally {
            cleanupResultFile();
        }
    }

    private void destroyProcessTree(boolean forcibly) {
        try (var descendants = process.toHandle().descendants()) {
            for (ProcessHandle descendant : descendants.toList()) {
                if (forcibly) {
                    descendant.destroyForcibly();
                } else {
                    descendant.destroy();
                }
            }
        }

        if (forcibly) {
            process.destroyForcibly();
        } else {
            process.destroy();
        }
    }

    private void awaitStdout(boolean ignoreReadError) {
        if (stdoutCollector != null) {
            stdoutCollector.await(ignoreReadError);
        }
    }

    private static final class StreamCollector {
        private final ByteArrayOutputStream output = new ByteArrayOutputStream();
        private final Thread thread;
        private volatile IOException error;

        private StreamCollector(InputStream input) {
            thread = new Thread(() -> collect(input), "apebind-stream-collector");
            thread.setDaemon(true);
            thread.start();
        }

        private void collect(InputStream input) {
            byte[] buffer = new byte[8192];
            try (input) {
                while (true) {
                    int count = input.read(buffer);
                    if (count < 0) {
                        return;
                    }

                    synchronized (output) {
                        output.write(buffer, 0, count);
                    }
                }
            } catch (IOException caught) {
                error = caught;
            }
        }

        private void await() {
            await(false);
        }

        private void await(boolean ignoreReadError) {
            try {
                thread.join();
            } catch (InterruptedException caught) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Interrupted while collecting APE output", caught);
            }

            if (error != null && !ignoreReadError) {
                throw new IllegalStateException("Failed to read APE output", error);
            }
        }

        private String text() {
            synchronized (output) {
                return output.toString(StandardCharsets.UTF_8);
            }
        }

        private byte[] bytes() {
            synchronized (output) {
                return output.toByteArray();
            }
        }
    }
}
