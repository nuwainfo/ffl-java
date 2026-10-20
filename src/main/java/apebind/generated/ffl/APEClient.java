package apebind.generated.ffl;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Array;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public final class APEClient {
    private final String binaryName;
    private final APERuntime runtime;
    private final Map<String, Object> operations;
    private final Map<String, Object> policy;
    private volatile Path resolvedBinary;

    public APEClient(
        String binaryName,
        APERuntime runtime,
        Map<String, Object> operations,
        Map<String, Object> policy
    ) {
        this.binaryName = binaryName;
        this.runtime = Objects.requireNonNull(runtime, "runtime");
        this.operations = Map.copyOf(operations);
        this.policy = Map.copyOf(policy);
    }

    public APEClient(Path binaryPath, Map<String, Object> operations, Map<String, Object> policy) {
        this(null, APERuntime.builder().binary(binaryPath).build(), operations, policy);
    }

    public APERuntime runtime() {
        return runtime;
    }

    private Path binaryPath() {
        Path explicit = runtime.binary();
        if (explicit != null) {
            return explicit;
        }

        Path resolved = resolvedBinary;
        if (resolved == null) {
            if (binaryName == null) {
                throw new IllegalStateException("APE binary path is not configured");
            }

            resolved = BinaryResource.resolve(binaryName);
            resolvedBinary = resolved;
        }

        return resolved;
    }

    static int normalizeAPEReturnCode(int returnCode) {
        if (isWindows() && returnCode > 0 && returnCode % 256 == 0) {
            return returnCode / 256;
        }

        return returnCode;
    }

    static String wslInteropGuidance(String stderr) {
        if (!stderr.toLowerCase(Locale.ROOT).contains("tlserror([0x6300])") || !isWSL2()) {
            return "";
        }

        return "\n\nWSL2 may be executing this .com APE through Windows interop. "
            + "Run the following command in WSL, then restart the WSL session:\n\n"
            + "    sudo sh -c 'echo -1 > /proc/sys/fs/binfmt_misc/WSLInterop'";
    }

    private static boolean isWSL2() {
        try {
            String version = new String(
                Files.readAllBytes(Paths.get("/proc/version")),
                StandardCharsets.UTF_8
            ).toLowerCase(Locale.ROOT);
            return version.contains("microsoft") || version.contains("wsl");
        } catch (IOException caught) {
            return false;
        }
    }

    static Map<String, Object> parseObject(String json) {
        return Json.object(Json.parse(json));
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase().startsWith("windows");
    }

    private static String stringify(Object value) {
        return value instanceof Path path ? path.toString() : String.valueOf(value);
    }

    private static Map<String, Object> object(Object value) {
        return Json.object(value);
    }

    private static List<Object> array(Object value) {
        return Json.array(value);
    }

    private static String string(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static boolean bool(Object value) {
        return Boolean.TRUE.equals(value);
    }

    private static Double number(Object value) {
        return value instanceof Number number ? number.doubleValue() : null;
    }

    private ResultFile createResultFile(Map<String, Object> resultConfig) {
        if (!"json_file".equals(string(resultConfig.get("format")))) {
            return null;
        }

        try {
            Path directory = Files.createTempDirectory("apebind-");
            return new ResultFile(directory, directory.resolve("result.json"));
        } catch (IOException error) {
            throw new IllegalStateException("Failed to create APE result file", error);
        }
    }

    private static Object extractField(Object value, String fieldPath) {
        if (fieldPath == null || fieldPath.isBlank()) {
            return value;
        }

        Object current = value;

        for (String part : fieldPath.split("\\.")) {
            current = object(current).get(part);
        }

        return current;
    }

    private static Iterable<?> multipleValues(Object value) {
        if (value instanceof Iterable<?> iterable) {
            return iterable;
        }

        if (value != null && value.getClass().isArray()) {
            int length = Array.getLength(value);
            List<Object> values = new ArrayList<>(length);
            for (int index = 0; index < length; index += 1) {
                values.add(Array.get(value, index));
            }

            return values;
        }

        return Collections.singletonList(value);
    }

    private static boolean appendOptionalValue(
        List<String> argv,
        Map<String, Object> option,
        Object value
    ) {
        if (!bool(option.get("value_optional"))) {
            return false;
        }

        if (value == null) {
            return true;
        }

        String flag = string(option.get("primary_flag"));

        if (value instanceof OptionalValue<?> optionalValue) {
            if (!optionalValue.present()) {
                return true;
            }

            argv.add(flag);
            if (optionalValue.hasValue()) {
                argv.add(stringify(optionalValue.value()));
            }

            return true;
        }

        if (Boolean.FALSE.equals(value)) {
            return true;
        }

        argv.add(flag);

        if (!Boolean.TRUE.equals(value)) {
            argv.add(stringify(value));
        }

        return true;
    }

    private static void appendOptions(
        List<String> argv,
        List<Object> options,
        Map<String, Object> values,
        ResultFile resultFile,
        Map<String, Object> resultConfig
    ) {
        for (Object rawOption : options) {
            Map<String, Object> option = object(rawOption);
            if (bool(option.get("runtime_owned"))) {
                if (resultFile == null) {
                    throw new IllegalStateException("Runtime-owned JSON option has no result path");
                }

                argv.add(string(resultConfig.get("option_flag")));
                argv.add(resultFile.path().toString());
                continue;
            }

            String apiName = string(option.get("api_name"));
            Object value = values.get(apiName);

            if (bool(option.get("boolean"))) {
                if (Boolean.TRUE.equals(value)) {
                    argv.add(string(option.get("primary_flag")));
                }

                continue;
            }

            if (appendOptionalValue(argv, option, value)) {
                continue;
            }

            if (value == null) {
                if (bool(option.get("required"))) {
                    throw new IllegalArgumentException("Missing required argument: " + apiName);
                }

                continue;
            }

            if (bool(option.get("multiple"))) {
                for (Object item : multipleValues(value)) {
                    argv.add(string(option.get("primary_flag")));
                    argv.add(stringify(item));
                }

                continue;
            }

            argv.add(string(option.get("primary_flag")));
            argv.add(stringify(value));
        }
    }

    private static void appendPositionals(
        List<String> argv,
        List<Object> positionals,
        Map<String, Object> values
    ) {
        for (Object rawPositional : positionals) {
            Map<String, Object> positional = object(rawPositional);
            String apiName = string(positional.get("api_name"));
            Object value = values.get(apiName);

            if (value == null) {
                if (bool(positional.get("required"))) {
                    throw new IllegalArgumentException("Missing required argument: " + apiName);
                }

                continue;
            }

            if (bool(positional.get("multiple"))) {
                for (Object item : multipleValues(value)) {
                    argv.add(stringify(item));
                }

                continue;
            }

            argv.add(stringify(value));
        }
    }

    private static List<String> buildArgv(
        Map<String, Object> operation,
        Map<String, Object> values,
        ResultFile resultFile
    ) {
        List<String> argv = new ArrayList<>();
        Map<String, Object> resultConfig = object(operation.get("result"));
        List<Object> levels = array(operation.get("levels"));
        for (int levelIndex = 0; levelIndex < levels.size(); levelIndex += 1) {
            Map<String, Object> level = object(levels.get(levelIndex));
            if (levelIndex > 0) {
                argv.add(string(level.get("token")));
            }

            appendOptions(
                argv,
                array(level.get("options")),
                values,
                resultFile,
                resultConfig
            );
            appendPositionals(argv, array(level.get("positionals")), values);
        }

        return argv;
    }

    /**
     * The full command line used to start the APE with {@code argv}: the runtime's
     * launcher prefix, the binary, then the arguments, wrapped in the platform shell
     * when the runtime asks for it.
     */
    public List<String> commandLine(List<String> argv) {
        List<String> target = new ArrayList<>(runtime.launcher());
        target.add(binaryPath().toString());
        target.addAll(argv);
        if (!runtime.shellWrapper()) {
            return target;
        }

        List<String> command = new ArrayList<>();
        if (isWindows()) {
            // Java starts a direct child without a console on Windows. That prevents
            // Cosmopolitan APEs from decrypting themselves, so let cmd.exe create it.
            command.add(System.getenv().getOrDefault("ComSpec", "cmd.exe"));
            command.add("/d");
            command.add("/s");
            command.add("/c");
            command.add(windowsCommand(target));
            return command;
        }

        command.add("/bin/sh");
        command.add("-c");
        command.add("exec \"$0\" \"$@\"");
        command.addAll(target);
        return command;
    }

    /**
     * The environment handed to the APE: the caller's environment minus the schema's
     * {@code env_unset} policy, then the runtime's overrides.
     */
    public Map<String, String> environment() {
        Map<String, String> environment = new LinkedHashMap<>(System.getenv());
        applyEnvironment(environment);
        return environment;
    }

    private void applyEnvironment(Map<String, String> environment) {
        Object envUnset = policy.get("env_unset");
        if (envUnset instanceof List<?> variables) {
            for (Object variable : variables) {
                environment.remove(String.valueOf(variable));
            }
        }

        environment.putAll(runtime.environment());
    }

    private static String windowsCommand(List<String> target) {
        StringBuilder command = new StringBuilder("\"call");
        for (String argument : target) {
            command.append(' ').append(quoteWindowsArgument(argument));
        }

        return command.append('\"').toString();
    }

    private static String quoteWindowsArgument(String value) {
        StringBuilder quoted = new StringBuilder("\"");
        int backslashCount = 0;
        for (int index = 0; index < value.length(); index += 1) {
            char character = value.charAt(index);
            if (character == '\\') {
                backslashCount += 1;
                continue;
            }

            if (character == '\"') {
                quoted.append("\\".repeat(backslashCount * 2 + 1));
            } else {
                quoted.append("\\".repeat(backslashCount));
            }

            quoted.append(character);
            backslashCount = 0;
        }

        quoted.append("\\".repeat(backslashCount * 2));
        return quoted.append('\"').toString();
    }

    private Process spawn(List<String> argv) {
        ProcessBuilder builder = new ProcessBuilder(commandLine(argv));
        applyEnvironment(builder.environment());
        if (runtime.workingDirectory() != null) {
            builder.directory(runtime.workingDirectory().toFile());
        }

        try {
            return builder.start();
        } catch (IOException error) {
            throw new IllegalStateException("Failed to start APE", error);
        }
    }

    static byte[] readFully(InputStream stream) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int count;
        while ((count = stream.read(buffer)) >= 0) {
            output.write(buffer, 0, count);
        }

        return output.toByteArray();
    }

    private static CompletableFuture<String> collect(InputStream stream) {
        return CompletableFuture.supplyAsync(() -> {
            try (stream) {
                return new String(readFully(stream), StandardCharsets.UTF_8);
            } catch (IOException error) {
                throw new IllegalStateException("Failed to read APE output", error);
            }
        });
    }

    private APEProcessResult runCommand(List<String> argv, Double timeoutSeconds) {
        Process process = spawn(argv);
        CompletableFuture<String> stdout = collect(process.getInputStream());
        CompletableFuture<String> stderr = collect(process.getErrorStream());
        try {
            boolean exited;
            if (timeoutSeconds == null) {
                process.waitFor();
                exited = true;
            } else {
                exited = process.waitFor(
                    Math.max(1L, Math.round(timeoutSeconds * 1000.0)),
                    TimeUnit.MILLISECONDS
                );
            }

            if (!exited) {
                Processes.destroyTree(process, true);
                process.waitFor();
                stdout.join();
                stderr.join();
                throw new APETimeoutException(argv, timeoutSeconds);
            }
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            Processes.destroyTree(process, true);
            throw new IllegalStateException("Interrupted while waiting for APE", error);
        }

        APEProcessResult result = new APEProcessResult(
            argv,
            normalizeAPEReturnCode(process.exitValue()),
            stdout.join(),
            stderr.join()
        );
        if (result.returnCode() != 0) {
            throw new APEProcessException(result);
        }

        return result;
    }

    private ProcessSession startPersistent(
        List<String> argv,
        ResultFile resultFile,
        InputStream stdinSource,
        boolean captureStdout
    ) {
        Process process = spawn(argv);
        ProcessSession session = new ProcessSession(
            process,
            argv,
            resultFile,
            captureStdout
        );

        if (stdinSource != null) {
            session.attachStdin(stdinSource);
        }

        return session;
    }

    private static Object decodeResult(
        APEProcessResult result,
        ResultFile resultFile,
        Map<String, Object> resultConfig
    ) {
        String format = string(resultConfig.get("format"));
        if ("command".equals(format)) {
            return result;
        }

        if ("text".equals(format)) {
            return bool(resultConfig.get("strip")) ? result.stdout().strip() : result.stdout();
        }

        if ("json".equals(format)) {
            return extractField(Json.parse(result.stdout()), string(resultConfig.get("field")));
        }

        if ("json_file".equals(format)) {
            if (resultFile == null) {
                throw new IllegalStateException("APE did not create the configured JSON result file");
            }

            return readJsonResult(resultFile, resultConfig);
        }

        throw new IllegalStateException("Unsupported result format: " + format);
    }

    private static Object readJsonResult(ResultFile resultFile, Map<String, Object> resultConfig) {
        try {
            String text = new String(Files.readAllBytes(resultFile.path()), StandardCharsets.UTF_8);
            return extractField(Json.parse(text), string(resultConfig.get("field")));
        } catch (IOException error) {
            throw new IllegalStateException("Failed to read APE JSON result", error);
        }
    }

    private static Object waitForJsonResult(
        ProcessSession session,
        ResultFile resultFile,
        Map<String, Object> resultConfig
    ) {
        if (resultFile == null) {
            throw new IllegalStateException("JSON result path was not created");
        }

        Double timeoutSeconds = number(resultConfig.get("ready_timeout_seconds"));
        if (timeoutSeconds == null) {
            return null;
        }

        long deadline = System.nanoTime() + Math.round(timeoutSeconds * 1_000_000_000.0);
        long lastSize = -1;
        int stableCount = 0;
        while (System.nanoTime() < deadline) {
            try {
                if (Files.exists(resultFile.path())) {
                    long size = Files.size(resultFile.path());
                    if (size > 0 && size == lastSize) {
                        stableCount += 1;
                        if (stableCount >= 2) {
                            return readJsonResult(resultFile, resultConfig);
                        }
                    } else {
                        stableCount = 0;
                    }

                    lastSize = size;
                }
            } catch (IOException error) {
                throw new IllegalStateException("Failed to inspect APE JSON result", error);
            }

            if (!session.running()) {
                session.waitFor();
                if (!Files.exists(resultFile.path())) {
                    throw new IllegalStateException("APE exited before producing the JSON result file");
                }

                return readJsonResult(resultFile, resultConfig);
            }

            try {
                Thread.sleep(20);
            } catch (InterruptedException error) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Interrupted while waiting for APE JSON result", error);
            }
        }

        throw new APETimeoutException(session.argv(), timeoutSeconds);
    }

    public Object invoke(String operationName, Map<String, Object> values) {
        return invoke(operationName, values, null, true);
    }

    public Object invoke(
        String operationName,
        Map<String, Object> values,
        InputStream stdinSource
    ) {
        return invoke(operationName, values, stdinSource, true);
    }

    public Object invoke(
        String operationName,
        Map<String, Object> values,
        InputStream stdinSource,
        boolean captureStdout
    ) {
        Object rawOperation = operations.get(operationName);
        if (rawOperation == null) {
            throw new IllegalArgumentException("Unknown operation: " + operationName);
        }

        Map<String, Object> operation = object(rawOperation);
        Map<String, Object> resultConfig = object(operation.get("result"));
        ResultFile resultFile = createResultFile(resultConfig);
        List<String> argv;
        try {
            argv = buildArgv(operation, values, resultFile);
        } catch (RuntimeException error) {
            if (resultFile != null) {
                resultFile.cleanup();
            }

            throw error;
        }

        if ("persistent".equals(string(operation.get("execution")))) {
            ProcessSession session = startPersistent(
                argv,
                resultFile,
                stdinSource,
                captureStdout
            );
            if (
                "json_file".equals(string(resultConfig.get("format")))
                && resultConfig.get("ready_timeout_seconds") != null
            ) {
                // Awaited on the first ProcessSession.result() call, not here: the core may
                // write the file only late in the run (an upload's link after the upload).
                session.deferResult(() -> waitForJsonResult(session, resultFile, resultConfig));
            }

            return session;
        }

        try {
            Map<String, Object> processConfig = object(operation.get("process"));
            APEProcessResult result = runCommand(argv, number(processConfig.get("timeout_seconds")));
            return decodeResult(result, resultFile, resultConfig);
        } finally {
            if (resultFile != null) {
                resultFile.cleanup();
            }
        }
    }

    public APEProcessResult raw(List<?> arguments) {
        return raw(arguments, null);
    }

    public APEProcessResult raw(List<?> arguments, Double timeoutSeconds) {
        List<String> argv = arguments.stream().map(APEClient::stringify).collect(Collectors.toList());
        return runCommand(argv, timeoutSeconds);
    }

    public ProcessSession rawPersistent(List<?> arguments) {
        List<String> argv = arguments.stream().map(APEClient::stringify).collect(Collectors.toList());
        return startPersistent(argv, null, null, true);
    }

    static final class ResultFile {
        private final Path directory;
        private final Path path;

        private ResultFile(Path directory, Path path) {
            this.directory = directory;
            this.path = path;
        }

        Path path() {
            return path;
        }

        void cleanup() {
            try {
                Files.deleteIfExists(path);
                Files.deleteIfExists(directory);
            } catch (IOException error) {
                throw new IllegalStateException("Failed to clean APE result file", error);
            }
        }
    }
}
