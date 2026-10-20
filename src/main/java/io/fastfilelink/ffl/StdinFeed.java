package io.fastfilelink.ffl;

import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * An stdin source the host writes to whenever it has input, for interactive FFL commands such as
 * {@code login}. Unlike {@link java.io.PipedInputStream} it does not care which thread writes
 * and never fails with "write end dead". Reads block until data or {@link #close()} arrives.
 */
final class StdinFeed extends InputStream {
    private static final byte[] END = new byte[0];

    private final BlockingQueue<byte[]> chunks = new LinkedBlockingQueue<>();
    private byte[] current = new byte[0];
    private int position;
    private volatile boolean closed;

    /** Queues {@code line} plus a newline, as a user pressing Enter would send it. */
    void writeLine(String line) {
        write((Objects.requireNonNull(line, "line") + "\n").getBytes(StandardCharsets.UTF_8));
    }

    void write(byte[] bytes) {
        if (closed) {
            throw new IllegalStateException("stdin feed is closed");
        }
        if (bytes.length > 0) {
            chunks.add(bytes.clone());
        }
    }

    @Override
    public int read() throws IOException {
        byte[] one = new byte[1];
        return read(one, 0, 1) < 0 ? -1 : one[0] & 0xff;
    }

    @Override
    public int read(byte[] buffer, int offset, int length) throws IOException {
        if (length == 0) {
            return 0;
        }
        while (position >= current.length) {
            if (current == END) {
                return -1;
            }
            try {
                current = chunks.take();
            } catch (InterruptedException caught) {
                Thread.currentThread().interrupt();
                throw new InterruptedIOException("Interrupted while waiting for stdin input");
            }
            position = 0;
        }
        int count = Math.min(length, current.length - position);
        System.arraycopy(current, position, buffer, offset, count);
        position += count;
        return count;
    }

    /** Ends the input: pending writes are still delivered, then the reader sees end of stream. */
    @Override
    public void close() {
        if (!closed) {
            closed = true;
            chunks.add(END);
        }
    }
}
