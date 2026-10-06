package io.fastfilelink.ffl;

import apebind.generated.ffl.Json;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

final class FFLHookEventChannel implements AutoCloseable {
    private final HttpServer server;
    private final String forwardUrl;
    private final int eventHistoryLimit;
    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final List<FFLHookEvent> history = new CopyOnWriteArrayList<>();
    private final Map<String, List<Consumer<FFLHookEvent>>> listeners = new java.util.concurrent.ConcurrentHashMap<>();
    private final Map<String, List<Consumer<FFLHookEvent>>> semanticListeners = new java.util.concurrent.ConcurrentHashMap<>();
    private final AtomicReference<RuntimeException> error = new AtomicReference<>();
    private final ExecutorService listenerExecutor = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "ffl-java-hook-listeners");
        thread.setDaemon(true);
        return thread;
    });

    FFLHookEventChannel(String forwardUrl) {
        this(forwardUrl, 1000);
    }

    FFLHookEventChannel(String forwardUrl, int eventHistoryLimit) {
        if (eventHistoryLimit < 1) {
            throw new IllegalArgumentException("eventHistoryLimit must be positive");
        }

        this.forwardUrl = forwardUrl;
        this.eventHistoryLimit = eventHistoryLimit;
        try {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        } catch (IOException caught) {
            throw new IllegalStateException("Failed to start FFL hook server", caught);
        }
        server.createContext("/", this::handle);
        server.start();
    }

    String url() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    List<FFLHookEvent> history() {
        return List.copyOf(history);
    }

    void on(String name, Consumer<FFLHookEvent> listener) {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(listener, "listener");
        listeners.computeIfAbsent(name, ignored -> new CopyOnWriteArrayList<>()).add(listener);
        for (FFLHookEvent event : history) {
            if (name.equals(event.name())) {
                callListener(listener, event);
            }
        }
        raiseIfError();
    }

    void onSemantic(String name, Consumer<FFLHookEvent> listener) {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(listener, "listener");
        semanticListeners.computeIfAbsent(name, ignored -> new CopyOnWriteArrayList<>()).add(listener);
        for (FFLHookEvent event : history) {
            if (name.equals(event.semanticName())) {
                callListener(listener, event);
            }
        }
        raiseIfError();
    }

    void raiseIfError() {
        RuntimeException failure = error.get();
        if (failure != null) {
            throw failure;
        }
    }

    @Override
    public void close() {
        server.stop(0);
        listenerExecutor.shutdownNow();
    }

    private void handle(HttpExchange exchange) throws IOException {
        try (exchange) {
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            FFLHookEvent event = parse(body);
            publish(event);
            byte[] responseBody = "{}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, responseBody.length);
            exchange.getResponseBody().write(responseBody);
            forward(body);
        } catch (RuntimeException caught) {
            error.compareAndSet(null, caught);
            byte[] response = caught.getMessage().getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(500, response.length);
            exchange.getResponseBody().write(response);
        }
    }

    private FFLHookEvent parse(String body) {
        Map<String, Object> raw = Json.object(Json.parse(body));
        Object event = raw.get("event");
        if (!(event instanceof String name)) {
            throw new IllegalArgumentException("FFL hook payload must include an event name");
        }
        Object timestamp = raw.get("timestamp");
        if (timestamp != null && !(timestamp instanceof String)) {
            throw new IllegalArgumentException("FFL hook timestamp must be a string");
        }
        Object data = raw.getOrDefault("data", Map.of());
        return new FFLHookEvent(name, (String) timestamp, Json.object(data), raw);
    }

    private void publish(FFLHookEvent event) {
        history.add(event);
        if (history.size() > eventHistoryLimit) {
            history.remove(0);
        }
        for (Consumer<FFLHookEvent> listener : listeners.getOrDefault(event.name(), List.of())) {
            dispatchListener(listener, event);
        }
        String semanticName = event.semanticName();
        if (semanticName != null) {
            for (Consumer<FFLHookEvent> listener : semanticListeners.getOrDefault(
                semanticName,
                List.of()
            )) {
                dispatchListener(listener, event);
            }
        }
    }

    private void dispatchListener(Consumer<FFLHookEvent> listener, FFLHookEvent event) {
        listenerExecutor.execute(() -> callListener(listener, event));
    }

    private void callListener(Consumer<FFLHookEvent> listener, FFLHookEvent event) {
        try {
            listener.accept(event);
        } catch (RuntimeException caught) {
            error.compareAndSet(null, caught);
        }
    }

    private void forward(String body) {
        if (forwardUrl == null) {
            return;
        }
        HttpRequest request = HttpRequest.newBuilder(URI.create(forwardUrl))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
            .build();
        httpClient.sendAsync(request, HttpResponse.BodyHandlers.discarding())
            .orTimeout(3, TimeUnit.SECONDS)
            .exceptionally(ignored -> null);
    }
}
