package io.fastfilelink.ffl;

import apebind.generated.ffl.ProcessSession;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * An interactive {@code ffl login --mode cli} run. The core mails a one-time code and waits on
 * stdin for it: {@link #otpRequested()} completes at that point, {@link #submitOtp} answers,
 * {@link #onOtpRejected} reports a code the core did not accept (it then waits again), and
 * {@link #result()} completes with the process.
 *
 * <p>The core is detected waiting by its unterminated stdout prompt rather than by the prompt's
 * wording, so this works in every UI language; the {@code [CLI-AUTH]} tag the core prints before
 * the first prompt is used as an earlier, secondary signal. A prompt that appears while a submitted
 * code is being verified and nothing was printed in between is the core echoing its own prompt
 * before reading the queued code, not a rejection.
 */
public final class LoginSession implements AutoCloseable {
    public enum Phase { CONNECTING, OTP_REQUIRED, VERIFYING, DONE }

    static final String OTP_REQUEST_TAG = "[CLI-AUTH]";

    private final ProcessSession session;
    private final StdinFeed feed;
    private final CompletableFuture<Void> otpRequested = new CompletableFuture<>();
    private final CompletableFuture<LoginResult> result = new CompletableFuture<>();
    private final List<Consumer<String>> rejectionListeners = new CopyOnWriteArrayList<>();
    private final List<String> linesSinceSubmit = new ArrayList<>();
    private volatile Phase phase = Phase.CONNECTING;
    private volatile String lastLine = "";
    private volatile String currentPrompt = "";

    LoginSession(ProcessSession session, StdinFeed feed) {
        this.session = Objects.requireNonNull(session, "session");
        this.feed = Objects.requireNonNull(feed, "feed");
        session.onExit(this::finish);
        session.onStdoutLine(this::onLine);
        session.onStdoutPrompt(this::onPrompt);
    }

    public Phase phase() {
        return phase;
    }

    /** Completes when the core asks for the code; fails if the run ends before that. */
    public CompletableFuture<Void> otpRequested() {
        return otpRequested;
    }

    public CompletableFuture<LoginResult> result() {
        return result;
    }

    /** Called with what the core printed about a code it did not accept; a new code may be submitted. */
    public LoginSession onOtpRejected(Consumer<String> listener) {
        rejectionListeners.add(Objects.requireNonNull(listener, "listener"));
        return this;
    }

    public LoginSession onStdoutLine(Consumer<String> listener) {
        session.onStdoutLine(listener);
        return this;
    }

    /** Every stderr line (the core's logger and tracebacks go there), replayed then live. */
    public LoginSession onStderrLine(Consumer<String> listener) {
        session.onStderrLine(listener);
        return this;
    }

    /** The core's most recent non-blank stdout line, in its UI language. */
    public String lastLine() {
        return lastLine;
    }

    public long pid() {
        return session.pid();
    }

    public boolean running() {
        return session.running();
    }

    /** Sends the one-time code; may be called again after a rejection, not after the run ended. */
    public void submitOtp(String code) {
        Objects.requireNonNull(code, "code");
        if (code.isBlank()) {
            throw new IllegalArgumentException("code must not be blank");
        }
        synchronized (this) {
            if (phase == Phase.DONE) {
                throw new IllegalStateException("login already finished: " + lastLine);
            }
            linesSinceSubmit.clear();
            phase = Phase.VERIFYING;
        }
        feed.writeLine(code.strip());
    }

    @Override
    public void close() {
        feed.close();
        try {
            if (session.running()) {
                session.stop();
            }
        } catch (RuntimeException ignored) {
            // Best effort: the run is being abandoned.
        }
    }

    private void onLine(String rawLine) {
        String line = withoutPrompt(rawLine, currentPrompt);
        if (!line.isBlank()) {
            lastLine = line.strip();
        }
        synchronized (this) {
            if (phase == Phase.VERIFYING) {
                linesSinceSubmit.add(line);
            } else if (phase == Phase.CONNECTING && line.contains(OTP_REQUEST_TAG)) {
                markOtpRequired();
            }
        }
    }

    private void onPrompt(String prompt) {
        currentPrompt = prompt;
        String rejection = null;
        synchronized (this) {
            if (phase == Phase.CONNECTING) {
                markOtpRequired();
            } else if (phase == Phase.VERIFYING && !linesSinceSubmit.isEmpty()) {
                StringBuilder message = new StringBuilder();
                for (String line : linesSinceSubmit) {
                    message.append(withoutPrompt(line, prompt)).append('\n');
                }
                rejection = message.toString().strip();
                linesSinceSubmit.clear();
                phase = Phase.OTP_REQUIRED;
            }
        }
        if (rejection != null) {
            for (Consumer<String> listener : rejectionListeners) {
                listener.accept(rejection);
            }
        }
    }

    /** The core's prompt has no newline, so the line printed after an answer starts with it. */
    private static String withoutPrompt(String line, String prompt) {
        return !prompt.isEmpty() && line.startsWith(prompt) ? line.substring(prompt.length()) : line;
    }

    private void markOtpRequired() {
        phase = Phase.OTP_REQUIRED;
        otpRequested.complete(null);
    }

    private void finish() {
        synchronized (this) {
            phase = Phase.DONE;
        }
        feed.close();
        Integer returnCode = session.returnCode();
        int exitCode = returnCode == null ? -1 : returnCode;
        otpRequested.completeExceptionally(
            new IllegalStateException("login ended before asking for a code: " + lastLine)
        );
        result.complete(new LoginResult(exitCode == 0, exitCode, lastLine));
    }
}
