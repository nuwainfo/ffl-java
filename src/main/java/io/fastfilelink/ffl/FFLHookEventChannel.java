package io.fastfilelink.ffl;

import apebind.generated.ffl.Json;
import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

final class FFLHookEventChannel implements AutoCloseable {
    private static final int FORWARD_TIMEOUT_MILLIS = 3000;

    private final URL forwardTarget;
    private final int eventHistoryLimit;
    private final List<FFLHookEvent> history = new CopyOnWriteArrayList<>();
    private final Map<String, List<Consumer<FFLHookEvent>>> listeners = new java.util.concurrent.ConcurrentHashMap<>();
    private final Map<String, List<Consumer<FFLHookEvent>>> semanticListeners = new java.util.concurrent.ConcurrentHashMap<>();
    private final List<Consumer<FFLHookEvent>> anyListeners = new CopyOnWriteArrayList<>();
    private final AtomicReference<RuntimeException> error = new AtomicReference<>();
    private final ExecutorService listenerExecutor = Executors.newSingleThreadExecutor(
        FFLHookReceiver.daemonThreads("ffl-java-hook-listeners")
    );
    private final ExecutorService forwardExecutor = Executors.newSingleThreadExecutor(
        FFLHookReceiver.daemonThreads("ffl-java-hook-forward")
    );
    private final Map<String, FFLHookResponder> responders;
    private final FFLHookReceiver receiver;

    FFLHookEventChannel(String forwardUrl) {
        this(forwardUrl, 1000);
    }

    FFLHookEventChannel(String forwardUrl, int eventHistoryLimit) {
        this(forwardUrl, eventHistoryLimit, Map.of());
    }

    FFLHookEventChannel(String forwardUrl, int eventHistoryLimit, Map<String, FFLHookResponder> responders) {
        this(forwardUrl, eventHistoryLimit, responders, null);
    }

    /** @param endpointProxy see {@link ShareOptions.Builder#hookEndpointProxy(String)}; {@code null} for none */
    FFLHookEventChannel(
        String forwardUrl,
        int eventHistoryLimit,
        Map<String, FFLHookResponder> responders,
        String endpointProxy
    ) {
        if (eventHistoryLimit < 1) {
            throw new IllegalArgumentException("eventHistoryLimit must be positive");
        }

        this.forwardTarget = forwardTarget(forwardUrl);
        this.eventHistoryLimit = eventHistoryLimit;
        this.responders = new java.util.LinkedHashMap<>(Objects.requireNonNull(responders, "responders"));
        this.receiver = new FFLHookReceiver(this::handle, FFLHookReceiver.DEFAULT_READ_TIMEOUT_MILLIS, endpointProxy);
    }

    String url() {
        return receiver.url();
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

    /** Receives every event whatever its name, after the named listeners; the history is replayed first. */
    void onAny(Consumer<FFLHookEvent> listener) {
        Objects.requireNonNull(listener, "listener");
        anyListeners.add(listener);
        for (FFLHookEvent event : history) {
            callListener(listener, event);
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
        receiver.close();
        listenerExecutor.shutdownNow();
        forwardExecutor.shutdown();
    }

    private String handle(String body) {
        try {
            FFLHookEvent event = parse(body);
            publish(event);
            forward(body);
            return reply(event);
        } catch (RuntimeException caught) {
            error.compareAndSet(null, caught);
            throw caught;
        }
    }

    /** Responders answer on the delivering connection, so a slow one only delays its own event. */
    private String reply(FFLHookEvent event) {
        FFLHookResponder responder = responders.get(event.name());
        if (responder == null) {
            return "{}";
        }
        Map<String, Object> answer = responder.respond(event);
        return answer == null ? "{}" : Json.stringify(answer);
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
        for (Consumer<FFLHookEvent> listener : anyListeners) {
            dispatchListener(listener, event);
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
        if (forwardTarget == null) {
            return;
        }
        try {
            forwardExecutor.execute(() -> post(forwardTarget, body));
        } catch (RejectedExecutionException caught) {
            // The channel is closing; late events are no longer forwarded.
        }
    }

    private static URL forwardTarget(String forwardUrl) {
        if (forwardUrl == null) {
            return null;
        }
        try {
            return URI.create(forwardUrl).toURL();
        } catch (MalformedURLException | IllegalArgumentException caught) {
            throw new IllegalArgumentException("Invalid FFL hook forward URL: " + forwardUrl, caught);
        }
    }

    /** Best-effort delivery to the caller's own hook; its failures never reach the FFL core. */
    private static void post(URL target, String body) {
        HttpURLConnection connection = null;
        try {
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            connection = (HttpURLConnection) target.openConnection();
            connection.setConnectTimeout(FORWARD_TIMEOUT_MILLIS);
            connection.setReadTimeout(FORWARD_TIMEOUT_MILLIS);
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setDoOutput(true);
            connection.setFixedLengthStreamingMode(bytes.length);
            try (OutputStream output = connection.getOutputStream()) {
                output.write(bytes);
            }
            connection.getResponseCode();
        } catch (IOException | RuntimeException caught) {
            // The caller's hook is down, slow or rejected the event; forwarding stays best effort.
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }
}
