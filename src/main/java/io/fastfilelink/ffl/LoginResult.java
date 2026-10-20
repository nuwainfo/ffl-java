package io.fastfilelink.ffl;

/** How an {@code ffl login} run ended; {@code lastLine} is the core's final message in its UI language. */
public record LoginResult(boolean success, int exitCode, String lastLine) {}
