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
    // /share/available. Transport-specific events stay on the raw channel, so one
    // transfer notifies each semantic listener once.
    public String semanticName() {
        return switch (name) {
            case "/share/available" -> "ready";
            case "/transfer/create" -> "started";
            case "/transfer/progress" -> "progress";
            case "/transfer/complete" -> "completed";
            case "/transfer/fail" -> "failed";
            default -> null;
        };
    }
}
