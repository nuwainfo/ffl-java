package io.fastfilelink.ffl;

import apebind.generated.ffl.APERuntime;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Environment variables the FFL core reads at start-up, as entries for
 * {@link APERuntime.Builder#environment(Map)}:
 * {@code APERuntime.builder().environment(FFLEnvironment.language("zh_Hant")).build()}.
 */
public final class FFLEnvironment {
    /** UI and message language: {@code en}, {@code zh_Hant}, {@code zh_Hans}; the core also accepts BCP-47 spellings such as {@code zh-TW}. */
    public static final String LANGUAGE = "FFL_LANGUAGE";
    /** Console log level of the core process. */
    public static final String LOGGING_LEVEL = "FFL_LOGGING_LEVEL";

    private static final Set<String> LOGGING_LEVELS = Set.of("DEBUG", "INFO", "WARNING", "ERROR");

    private FFLEnvironment() {}

    public static Map<String, String> language(String code) {
        Objects.requireNonNull(code, "code");
        if (code.isBlank()) {
            throw new IllegalArgumentException("language code must not be blank");
        }
        return Map.of(LANGUAGE, code);
    }

    /**
     * One of {@code DEBUG}, {@code INFO}, {@code WARNING}, {@code ERROR} (any case). The variable
     * only takes a level name: a logging config path, which {@code --log-level} accepts, makes the
     * core crash on start-up when given here.
     */
    public static Map<String, String> loggingLevel(String level) {
        Objects.requireNonNull(level, "level");
        String name = level.trim().toUpperCase(Locale.ROOT);
        if (!LOGGING_LEVELS.contains(name)) {
            throw new IllegalArgumentException(
                "FFL_LOGGING_LEVEL takes DEBUG, INFO, WARNING or ERROR, not: " + level
            );
        }
        return Map.of(LOGGING_LEVEL, name);
    }
}
