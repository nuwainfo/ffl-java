package io.fastfilelink.ffl;

import apebind.generated.ffl.ProcessSession;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public final class ShareSession implements AutoCloseable {
    private final ProcessSession session;
    private final FFLHookEventChannel eventChannel;
    private final List<Path> cleanupPaths = new ArrayList<>();

    ShareSession(ProcessSession session) {
        this(session, null);
    }

    ShareSession(ProcessSession session, FFLHookEventChannel eventChannel) {
        this.session = session;
        this.eventChannel = eventChannel;
        session.onExit(this::cleanup);
    }

    ShareSession attachCleanupPath(Path path) {
        cleanupPaths.add(path);
        return this;
    }

    public List<String> argv() {
        return session.argv();
    }

    /** Every stdout line of the FFL process, replayed from the start and then live; see {@link ProcessSession#onStdoutLine}. */
    public ShareSession onStdoutLine(Consumer<String> listener) {
        session.onStdoutLine(listener);
        return this;
    }

    /** Every stderr line (the core's logger and tracebacks go there), replayed then live; see {@link ProcessSession#onStderrLine}. */
    public ShareSession onStderrLine(Consumer<String> listener) {
        session.onStderrLine(listener);
        return this;
    }

    /**
     * The share link from the core's {@code --json} result. The first call waits for that file
     * (up to the schema's 20 seconds; a timeout throws {@link apebind.generated.ffl.APETimeoutException}
     * and leaves the share running). In upload mode the core writes it only after the upload, so
     * listen to the {@code link} event instead of calling this early.
     */
    public String link() {
        Object result = session.result();
        if (!(result instanceof String link) || link.isBlank()) {
            throw new IllegalStateException("FFL share session does not have a valid link result");
        }
        return link;
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

    public String stdout() {
        return session.stdout();
    }

    public String stderr() {
        return session.stderr();
    }

    public List<FFLHookEvent> eventHistory() {
        return eventChannel == null ? List.of() : eventChannel.history();
    }

    public ShareSession on(String name, Consumer<FFLHookEvent> listener) {
        if (eventChannel == null) {
            throw new IllegalStateException("FFL hook events are not available for this session");
        }

        eventChannel.onSemantic(name, listener);
        return this;
    }

    public ShareSession onRaw(String name, Consumer<FFLHookEvent> listener) {
        if (eventChannel == null) {
            throw new IllegalStateException("FFL hook events are not available for this session");
        }

        eventChannel.on(name, listener);
        return this;
    }

    /**
     * Receives every hook event regardless of name (for a single dispatcher that keys on
     * {@link FFLHookEvent#name()}); the events seen so far are replayed first, in order.
     */
    public ShareSession onAny(Consumer<FFLHookEvent> listener) {
        if (eventChannel == null) {
            throw new IllegalStateException("FFL hook events are not available for this session");
        }

        eventChannel.onAny(listener);
        return this;
    }

    public int waitFor() {
        try {
            int returnCode = session.waitFor();
            raiseEventError();
            return returnCode;
        } finally {
            if (!session.running()) {
                cleanup();
            }
        }
    }

    public int waitFor(double timeoutSeconds) {
        try {
            int returnCode = session.waitFor(timeoutSeconds);
            raiseEventError();
            return returnCode;
        } finally {
            if (!session.running()) {
                cleanup();
            }
        }
    }

    public void stop() {
        try {
            session.stop();
        } finally {
            cleanup();
        }
    }

    public void stop(double timeoutSeconds) {
        try {
            session.stop(timeoutSeconds);
        } finally {
            cleanup();
        }
    }

    @Override
    public void close() {
        try {
            session.close();
        } finally {
            cleanup();
        }
    }

    private synchronized void cleanup() {
        List<Path> owned = List.copyOf(cleanupPaths);
        cleanupPaths.clear();
        for (Path path : owned) {
            TemporaryFiles.deleteRecursively(path);
        }
        if (eventChannel != null) {
            eventChannel.close();
        }
    }

    private void raiseEventError() {
        if (eventChannel != null) {
            eventChannel.raiseIfError();
        }
    }
}
