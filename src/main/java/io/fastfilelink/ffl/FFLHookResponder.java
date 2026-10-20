package io.fastfilelink.ffl;

import java.util.Map;

/**
 * Answers one FFL hook event synchronously.
 *
 * <p>Some hook events are questions: the FFL core blocks until the reply arrives and acts on it
 * (for example {@code /upload/confirm} waits for {@code {"confirmed": true}} before spending points).
 * A responder runs on the connection that delivered the event, so it may take as long as the core
 * is willing to wait for that event; other events keep flowing meanwhile. Returning {@code null}
 * answers with an empty object, and throwing answers with HTTP 500, which the core reports as a
 * hook error.
 */
@FunctionalInterface
public interface FFLHookResponder {
    Map<String, Object> respond(FFLHookEvent event);
}
