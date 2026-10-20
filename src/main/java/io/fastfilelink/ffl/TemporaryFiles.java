package io.fastfilelink.ffl;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/** Cleanup of the temporary paths ffl-java creates for streamed shares. */
final class TemporaryFiles {
    private TemporaryFiles() {}

    /** Removes {@code path} and everything below it; a path that does not exist is not an error. */
    static void deleteRecursively(Path path) {
        if (!Files.exists(path)) {
            return;
        }
        try (Stream<Path> stream = Files.walk(path)) {
            List<Path> deepestFirst = stream.sorted(Comparator.reverseOrder()).collect(Collectors.toList());
            for (Path item : deepestFirst) {
                Files.deleteIfExists(item);
            }
        } catch (IOException error) {
            throw new IllegalStateException("Failed to clean FFL temporary path: " + path, error);
        }
    }
}
