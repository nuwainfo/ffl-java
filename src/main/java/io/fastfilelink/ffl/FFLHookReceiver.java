package io.fastfilelink.ffl;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;

/**
 * Loopback HTTP/1.1 endpoint that receives FFL hook callbacks.
 *
 * <p>Built on plain {@code java.net} sockets so the same code runs on desktop JVMs and on Android,
 * where {@code com.sun.net.httpserver} does not exist. It serves exactly what the FFL core sends:
 * one POST with a Content-Length body per connection, answered with {@code Connection: close}.
 * Every connection gets its own thread, so a handler that blocks on one event (for example while
 * waiting for the user) does not stall the others.
 */
final class FFLHookReceiver implements AutoCloseable {
    /** Turns one hook request body into the JSON response body; a RuntimeException answers 500. */
    interface Handler {
        String handle(String body);
    }

    static final int MAX_HEAD_BYTES = 64 * 1024;
    static final int MAX_BODY_BYTES = 16 * 1024 * 1024;
    static final int DEFAULT_READ_TIMEOUT_MILLIS = 30_000;

    private final Handler handler;
    private final int readTimeoutMillis;
    private final String endpointProxy;
    private final Set<Socket> connections = ConcurrentHashMap.newKeySet();
    private final ExecutorService workers = Executors.newCachedThreadPool(
        daemonThreads("ffl-java-hook-connection")
    );
    private final ServerSocket serverSocket;
    private volatile boolean closed;

    FFLHookReceiver(Handler handler) {
        this(handler, DEFAULT_READ_TIMEOUT_MILLIS);
    }

    FFLHookReceiver(Handler handler, int readTimeoutMillis) {
        this(handler, readTimeoutMillis, null);
    }

    /**
     * @param endpointProxy HTTP base URL that answers every request which is not a hook event POST to
     *     the hook path (the routes registered through {@code /hook/server/endpoints/register}), or
     *     {@code null} to reject them as before
     */
    FFLHookReceiver(Handler handler, int readTimeoutMillis, String endpointProxy) {
        if (readTimeoutMillis < 1) {
            throw new IllegalArgumentException("readTimeoutMillis must be positive");
        }

        this.handler = Objects.requireNonNull(handler, "handler");
        this.readTimeoutMillis = readTimeoutMillis;
        this.endpointProxy = endpointProxy;
        try {
            serverSocket = new ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"));
        } catch (IOException caught) {
            workers.shutdownNow();
            throw new IllegalStateException("Failed to start FFL hook server", caught);
        }
        daemonThreads("ffl-java-hook-accept").newThread(this::acceptLoop).start();
    }

    int port() {
        return serverSocket.getLocalPort();
    }

    String url() {
        return "http://127.0.0.1:" + port();
    }

    @Override
    public void close() {
        closed = true;
        try {
            serverSocket.close();
        } catch (IOException ignored) {
            // Already unusable; nothing more to release.
        }
        for (Socket connection : connections) {
            discard(connection);
        }
        workers.shutdownNow();
    }

    static ThreadFactory daemonThreads(String name) {
        return runnable -> {
            Thread thread = new Thread(runnable, name);
            thread.setDaemon(true);
            return thread;
        };
    }

    private void acceptLoop() {
        while (!closed) {
            Socket connection;
            try {
                connection = serverSocket.accept();
            } catch (IOException caught) {
                if (closed) {
                    return;
                }
                continue;
            }
            connections.add(connection);
            if (closed) {
                discard(connection);
                return;
            }
            try {
                workers.execute(() -> serve(connection));
            } catch (RejectedExecutionException caught) {
                discard(connection);
            }
        }
    }

    private void serve(Socket connection) {
        try {
            connection.setSoTimeout(readTimeoutMillis);
            InputStream input = new BufferedInputStream(connection.getInputStream());
            OutputStream output = new BufferedOutputStream(connection.getOutputStream());
            Request request;
            try {
                List<String> head = readHead(input);
                if (head == null) {
                    return;
                }
                request = Request.parse(head);
            } catch (Rejection rejection) {
                write(output, Response.text(rejection.status, rejection.getMessage()));
                return;
            }

            if (endpointProxy != null && !request.isHookEvent()) {
                proxy(request, input, output);
            } else {
                write(output, respond(request, input));
            }
        } catch (IOException ignored) {
            // The peer went away or stopped sending; there is nobody left to answer.
        } finally {
            discard(connection);
        }
    }

    private void discard(Socket connection) {
        connections.remove(connection);
        try {
            connection.close();
        } catch (IOException ignored) {
            // Already closed by the peer.
        }
    }

    /** Answers one hook event with the handler's JSON; anything the core cannot have sent is rejected first. */
    private Response respond(Request request, InputStream input) throws IOException {
        String body;
        try {
            body = new String(readBody(input, request.hookBodyLength()), StandardCharsets.UTF_8);
        } catch (Rejection rejection) {
            return Response.text(rejection.status, rejection.getMessage());
        }

        try {
            return Response.json(Objects.requireNonNull(handler.handle(body), "hook handler returned null"));
        } catch (RuntimeException caught) {
            return Response.text(500, String.valueOf(caught.getMessage()));
        }
    }

    /** Request and response headers that describe this connection, not the message; never relayed. */
    private static final Set<String> HOP_BY_HOP = Set.of(
        "host", "connection", "keep-alive", "proxy-connection", "transfer-encoding", "te", "trailer", "upgrade",
        "content-length"
    );

    /**
     * Relays a registered-route request to the endpoint proxy. The FFL core fetches {@code /manifest},
     * {@code /thumb} and the other routes a hook server registered from the hook URL's host and port,
     * so they arrive here instead of at the server that really serves them; method, path, query,
     * headers and body go upstream, and the upstream status, headers and body come back.
     */
    private void proxy(Request request, InputStream input, OutputStream output) throws IOException {
        byte[] body;
        try {
            body = readBody(input, request.proxiedBodyLength());
        } catch (Rejection rejection) {
            write(output, Response.text(rejection.status, rejection.getMessage()));
            return;
        }
        if (!request.target().startsWith("/")) {
            write(output, Response.text(400, "Proxied requests must use an origin-form target"));
            return;
        }

        java.net.HttpURLConnection upstream;
        int status;
        try {
            upstream = (java.net.HttpURLConnection) new java.net.URL(endpointProxy + request.target()).openConnection();
            upstream.setRequestMethod(request.method());
            upstream.setInstanceFollowRedirects(false);
            upstream.setUseCaches(false);
            upstream.setConnectTimeout(readTimeoutMillis);
            upstream.setReadTimeout(readTimeoutMillis);
            for (String header : request.headers()) {
                int colon = header.indexOf(':');
                String name = header.substring(0, colon).trim();
                if (!HOP_BY_HOP.contains(name.toLowerCase(Locale.ROOT))) {
                    upstream.addRequestProperty(name, header.substring(colon + 1).trim());
                }
            }
            // Only send a body where one was framed: HttpURLConnection turns a GET with output into a POST.
            if (request.contentLength() >= 0 && !request.method().equals("GET") && !request.method().equals("HEAD")) {
                upstream.setDoOutput(true);
                upstream.setFixedLengthStreamingMode(body.length);
                try (OutputStream upstreamBody = upstream.getOutputStream()) {
                    upstreamBody.write(body);
                }
            }
            status = upstream.getResponseCode();
            if (status < 0) {
                throw new IOException("upstream sent no HTTP status line");
            }
        } catch (IOException caught) {
            write(output, Response.text(502, "Hook endpoint upstream failed: " + caught.getMessage()));
            return;
        }

        relay(request, upstream, status, output);
    }

    private static void relay(
        Request request,
        java.net.HttpURLConnection upstream,
        int status,
        OutputStream output
    ) throws IOException {
        boolean head = request.method().equals("HEAD");
        boolean withBody = !head && status != 204 && status != 304;
        long length = upstream.getContentLengthLong();
        String message = upstream.getResponseMessage();
        StringBuilder lines = new StringBuilder("HTTP/1.1 ").append(status).append(' ')
            .append(message == null ? reason(status) : message).append("\r\n");
        for (java.util.Map.Entry<String, List<String>> header : upstream.getHeaderFields().entrySet()) {
            String name = header.getKey();
            if (name == null || HOP_BY_HOP.contains(name.toLowerCase(Locale.ROOT))) {
                continue;
            }
            for (String value : header.getValue()) {
                lines.append(name).append(": ").append(value).append("\r\n");
            }
        }
        if ((head || withBody) && length >= 0) {
            lines.append("Content-Length: ").append(length).append("\r\n");
        }
        lines.append("Connection: close\r\n\r\n");
        output.write(lines.toString().getBytes(StandardCharsets.ISO_8859_1));

        if (withBody) {
            InputStream upstreamBody = status >= 400 ? upstream.getErrorStream() : upstream.getInputStream();
            if (upstreamBody != null) {
                try (upstreamBody) {
                    byte[] buffer = new byte[64 * 1024];
                    int count;
                    while ((count = upstreamBody.read(buffer)) >= 0) {
                        output.write(buffer, 0, count);
                    }
                }
            }
        }
        output.flush();
    }

    /** Request line plus header lines, read up to the blank line that ends the head. */
    private static List<String> readHead(InputStream input) throws IOException, Rejection {
        List<String> lines = new ArrayList<>();
        ByteArrayOutputStream line = new ByteArrayOutputStream();
        int total = 0;
        while (true) {
            int next = input.read();
            if (next < 0) {
                if (lines.isEmpty() && line.size() == 0) {
                    return null;
                }
                throw new EOFException("Connection closed inside the request head");
            }
            if (++total > MAX_HEAD_BYTES) {
                throw new Rejection(431, "Request head exceeds " + MAX_HEAD_BYTES + " bytes");
            }
            if (next != '\n') {
                line.write(next);
                continue;
            }

            String text = new String(line.toByteArray(), StandardCharsets.ISO_8859_1);
            line.reset();
            if (text.endsWith("\r")) {
                text = text.substring(0, text.length() - 1);
            }
            if (!text.isEmpty()) {
                lines.add(text);
            } else if (!lines.isEmpty()) {
                return lines;
            }
        }
    }

    /** One parsed request head: method, origin-form target, raw header lines and the body framing. */
    private record Request(String method, String target, List<String> headers, long contentLength, boolean transferEncoding) {
        static Request parse(List<String> head) throws Rejection {
            String[] requestLine = head.get(0).split(" ", -1);
            if (requestLine.length != 3 || !requestLine[2].startsWith("HTTP/1.")) {
                throw new Rejection(400, "Malformed request line");
            }

            long length = -1;
            boolean transferEncoding = false;
            List<String> headers = head.subList(1, head.size());
            for (String header : headers) {
                int colon = header.indexOf(':');
                if (colon <= 0) {
                    throw new Rejection(400, "Malformed header line");
                }
                String name = header.substring(0, colon).trim().toLowerCase(Locale.ROOT);
                String value = header.substring(colon + 1).trim();
                if (name.equals("transfer-encoding")) {
                    transferEncoding = true;
                } else if (name.equals("content-length")) {
                    long parsed = parseContentLength(value);
                    if (length >= 0 && length != parsed) {
                        throw new Rejection(400, "Conflicting Content-Length headers");
                    }
                    length = parsed;
                }
            }
            return new Request(requestLine[0], requestLine[1], List.copyOf(headers), length, transferEncoding);
        }

        /** Hook events are what the core POSTs to the hook URL itself, whose path is {@code /}. */
        boolean isHookEvent() {
            return method.equals("POST") && path().equals("/");
        }

        String path() {
            int query = target.indexOf('?');
            String path = query < 0 ? target : target.substring(0, query);
            return path.isEmpty() ? "/" : path;
        }

        /** Validates the framing of a hook event and returns the body length the FFL core announced. */
        int hookBodyLength() throws Rejection {
            if (!method.equals("POST")) {
                throw new Rejection(405, "FFL hook events must be POSTed");
            }
            if (transferEncoding) {
                throw new Rejection(501, "Transfer-Encoding is not supported; send Content-Length");
            }
            if (contentLength < 0) {
                throw new Rejection(411, "Content-Length is required");
            }
            if (contentLength > MAX_BODY_BYTES) {
                throw new Rejection(413, "Hook body exceeds " + MAX_BODY_BYTES + " bytes");
            }
            return (int) contentLength;
        }

        /** Validates the framing of a proxied request and returns its body length, 0 when it has none. */
        int proxiedBodyLength() throws Rejection {
            if (transferEncoding) {
                throw new Rejection(501, "Transfer-Encoding is not supported; send Content-Length");
            }
            if (contentLength > MAX_BODY_BYTES) {
                throw new Rejection(413, "Request body exceeds " + MAX_BODY_BYTES + " bytes");
            }
            return contentLength < 0 ? 0 : (int) contentLength;
        }
    }

    private static long parseContentLength(String value) throws Rejection {
        if (value.isEmpty() || value.length() > 18 || !value.chars().allMatch(Character::isDigit)) {
            throw new Rejection(400, "Malformed Content-Length");
        }
        return Long.parseLong(value);
    }

    private static byte[] readBody(InputStream input, int length) throws IOException {
        byte[] body = new byte[length];
        int filled = 0;
        while (filled < length) {
            int count = input.read(body, filled, length - filled);
            if (count < 0) {
                throw new EOFException("Connection closed after " + filled + " of " + length + " body bytes");
            }
            filled += count;
        }
        return body;
    }

    private static void write(OutputStream output, Response response) throws IOException {
        String head = "HTTP/1.1 " + response.status() + " " + reason(response.status()) + "\r\n"
            + "Content-Type: " + response.contentType() + "\r\n"
            + "Content-Length: " + response.body().length + "\r\n"
            + (response.status() == 405 ? "Allow: POST\r\n" : "")
            + "Connection: close\r\n"
            + "\r\n";
        output.write(head.getBytes(StandardCharsets.ISO_8859_1));
        output.write(response.body());
        output.flush();
    }

    private static String reason(int status) {
        return switch (status) {
            case 200 -> "OK";
            case 204 -> "No Content";
            case 206 -> "Partial Content";
            case 304 -> "Not Modified";
            case 400 -> "Bad Request";
            case 404 -> "Not Found";
            case 405 -> "Method Not Allowed";
            case 411 -> "Length Required";
            case 413 -> "Content Too Large";
            case 416 -> "Range Not Satisfiable";
            case 431 -> "Request Header Fields Too Large";
            case 500 -> "Internal Server Error";
            case 501 -> "Not Implemented";
            case 502 -> "Bad Gateway";
            default -> "Status";
        };
    }

    private record Response(int status, String contentType, byte[] body) {
        static Response json(String body) {
            return new Response(200, "application/json", body.getBytes(StandardCharsets.UTF_8));
        }

        static Response text(int status, String message) {
            return new Response(status, "text/plain; charset=utf-8", message.getBytes(StandardCharsets.UTF_8));
        }
    }

    /** A request the receiver answers itself, before the handler ever sees it. */
    private static final class Rejection extends Exception {
        private static final long serialVersionUID = 1L;

        private final int status;

        Rejection(int status, String message) {
            super(message);
            this.status = status;
        }
    }
}
