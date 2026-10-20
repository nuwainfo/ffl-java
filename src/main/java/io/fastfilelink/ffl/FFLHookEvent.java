package io.fastfilelink.ffl;

import java.util.Map;
import java.util.Collections;
import java.util.LinkedHashMap;

public record FFLHookEvent(
    String name,
    String timestamp,
    Map<String, Object> data,
    Map<String, Object> raw
) {
    public FFLHookEvent {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("FFL hook payload must include an event name");
        }

        data = Collections.unmodifiableMap(new LinkedHashMap<>(data));
        raw = Collections.unmodifiableMap(new LinkedHashMap<>(raw));
    }

    // FFL posts one integrated /transfer/* event for every HTTP, WebRTC, and
    // direct P2P transfer. Semantic listeners bind to those names, plus
    // /share/available and /share/link/create. Transport-specific events stay
    // on the raw channel, so one transfer notifies each semantic listener once.
    public String semanticName() {
        return switch (name) {
            case FFLHookEvents.SHARE_AVAILABLE -> "ready";
            case FFLHookEvents.SHARE_LINK_CREATE -> "link";
            case FFLHookEvents.TRANSFER_CREATE -> "started";
            case FFLHookEvents.TRANSFER_PROGRESS -> "progress";
            case FFLHookEvents.TRANSFER_COMPLETE -> "completed";
            case FFLHookEvents.TRANSFER_FAIL -> "failed";
            default -> null;
        };
    }

    /**
     * The share link carried by {@link FFLHookEvents#SHARE_LINK_CREATE}, {@link FFLHookEvents#SHARE_AVAILABLE}
     * and {@link FFLHookEvents#UPLOAD_COMPLETE}; {@code null} for events without one.
     */
    public String link() {
        return text("link");
    }

    /** The failure text of the {@code /.../fail} events; {@code null} for events without one. */
    public String error() {
        return text("error");
    }

    /**
     * Transfer counters of the upload, download, WebRTC, P2P and integrated transfer progress events;
     * {@code null} for events that carry no {@code percentage}.
     */
    public Progress progress() {
        Number percentage = number("percentage");
        if (percentage == null) {
            return null;
        }
        Number totalBytes = number("totalBytes");
        return new Progress(
            percentage.doubleValue(),
            longValue(number("bytesTransferred")),
            totalBytes == null ? null : totalBytes.longValue(),
            longValue(number("speed"))
        );
    }

    /** Counters shared by every progress event; {@code totalBytes} is {@code null} when the size is unknown. */
    public record Progress(double percentage, long bytesTransferred, Long totalBytes, long bytesPerSecond) {}

    private String text(String key) {
        return data.get(key) instanceof String value && !value.isBlank() ? value : null;
    }

    private Number number(String key) {
        return data.get(key) instanceof Number value ? value : null;
    }

    private static long longValue(Number value) {
        return value == null ? 0L : value.longValue();
    }
}
