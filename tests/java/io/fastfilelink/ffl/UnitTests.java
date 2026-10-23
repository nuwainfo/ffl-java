package io.fastfilelink.ffl;

import apebind.generated.ffl.APEClient;
import apebind.generated.ffl.APEProcessResult;
import apebind.generated.ffl.APEProcessException;
import apebind.generated.ffl.APERuntime;
import apebind.generated.ffl.APETimeoutException;
import apebind.generated.ffl.FFLBinding;
import apebind.generated.ffl.Json;
import apebind.generated.ffl.OptionalValue;
import apebind.generated.ffl.ProcessSession;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.net.ConnectException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public final class UnitTests {
    private UnitTests() {}

    public static void main(String[] args) throws Exception {
        testRuntimeDefaultsWrapBinaryInPlatformShell();
        testRuntimeWithoutShellWrapperExecsLauncherThenBinary();
        testRuntimeEnvironmentUnsetsPolicyVariables();
        testRuntimeEnvironmentOverridesWinOverPolicyUnset();
        testBindingCreatedWithRuntimeKeepsIt();
        testClientBoundToRuntimeUsesIt();
        testSessionPidMatchesChildProcess();
        testStdoutLinesAreDeliveredLiveAndReplayed();
        testStdoutLinesNeedCapturedStdout();
        testStderrLinesAreDeliveredLiveAndReplayed();
        testSessionsForwardStderrLines();
        testPersistentJsonResultIsAwaitedLazily();
        testLazyJsonResultTimeoutLeavesTheProcessRunning();
        testStdinFeedDeliversWritesAndEndsOnClose();
        testStdinCopyFlushesEachLine();
        testPromptIsAnnouncedWhenOutputStallsWithoutNewline();
        testTerminatedLinesAreNotPrompts();
        testRuntimeToBuilderCopiesEverything();
        testAuthStatusParsesLoggedInLayout();
        testAuthStatusParsesFreeLayout();
        testAuthStatusParsesFractionalPoints();
        testAuthStatusRejectsUnknownText();
        testLoginParametersUseCliMode();
        testEnglishBindingOnlyAddsTheLanguage();
        testLoginSessionRunsTheOtpDialogue();
        testLoginSessionFailsOtpRequestWhenRunEndsEarly();
        testLoginSessionDoesNotMistakeVerificationForRejection();
        testShareOptionMapping();
        testDownloadOptionMapping();
        testVfsShareOptionMapping();
        testVfsShareRejectsNonVfsTargets();
        testEnvironmentHelpersProduceCoreVariables();
        testStreamShareOptionMapping();
        testCallerManagedHook();
        testHookEvents();
        testIntegratedTransferEvents();
        testBoundedSemanticHookEvents();
        testOnAnyReceivesEveryEventAndReplaysHistory();
        testLiveHookListenersSurviveHistoryEviction();
        testHookResponseDoesNotWaitForLocalListeners();
        testHookReceiverListensOnLoopback();
        testHookReceiverAnswersPostWithHandlerJson();
        testHookReceiverRejectsUnservableRequestsBeforeHandler();
        testHookReceiverAnswers500WhenHandlerThrows();
        testHookReceiverServesRequestsConcurrently();
        testHookReceiverDropsIdleConnection();
        testHookReceiverRefusesConnectionsAfterClose();
        testHookReceiverProxiesEndpointRoutesToTheConfiguredBase();
        testHookReceiverProxiesPostRoutesWithTheirBody();
        testHookReceiverStillTakesHookEventsWhileProxying();
        testHookReceiverAnswers502WhenTheEndpointUpstreamIsDown();
        testShareOptionsDropsTheTrailingSlashOfTheEndpointProxy();
        testShareOptionsEndpointProxyNeedsTheHookChannel();
        testShareOptionsRejectsNonHttpEndpointProxies();
        testHookChannelForwardsEventToCallerHook();
        testHookChannelRecordsMalformedPayloadAsError();
        testJsonParseKeepsIntegersAsLongs();
        testJsonStringifyRoundTripsNestedValues();
        testJsonStringifyRejectsUnsupportedValues();
        testResponderReplyReachesCore();
        testResponderNullReplyMeansEmptyObject();
        testResponderFailureAnswers500AndRecordsError();
        testEventsWithoutResponderStillGetEmptyObject();
        testUploadConfirmationOptionAnswersConfirmed();
        testRespondToOptionAnswersWithResponderJson();
        testRespondersRequireCapturedHookEvents();
        testEventConstantsAreDistinctWireNames();
        testShareLinkEventHasSemanticLinkName();
        testProgressAccessorReadsTransferFields();
        testErrorAccessorReadsFailureText();
        testDownloadParsing();
        testDownloadSessionApi();
        testClosingRunningDownloadCancelsIt();
        testStreamedDownloadRequiresStdoutConsumptionBeforeWaiting();
        testExplicitDownloadPathWins();
        testKeygenParsing();
        testDeleteRecursivelyRemovesWholeTree();
        testDeleteRecursivelyIgnoresMissingPath();
        System.out.println("unit: ok");
    }

    private static void testRuntimeDefaultsWrapBinaryInPlatformShell() {
        APEClient client = new APEClient(
            "ffl.com",
            APERuntime.builder().binary(Path.of("ffl.com")).build(),
            Map.of(),
            Map.of()
        );

        List<String> command = client.commandLine(List.of("--version"));

        if (System.getProperty("os.name").startsWith("Windows")) {
            require(command.get(0).toLowerCase().endsWith("cmd.exe"), "windows shell wrapper");
            require(command.subList(1, 4).equals(List.of("/d", "/s", "/c")), "cmd.exe flags");
            require(command.get(4).contains("\"ffl.com\" \"--version\""), "cmd.exe command string");
        } else {
            require(
                command.equals(List.of("/bin/sh", "-c", "exec \"$0\" \"$@\"", "ffl.com", "--version")),
                "posix shell wrapper"
            );
        }
    }

    private static void testRuntimeWithoutShellWrapperExecsLauncherThenBinary() {
        APERuntime runtime = APERuntime.builder()
            .binary(Path.of("libffl.so"))
            .launcher("libape.so")
            .shellWrapper(false)
            .build();
        APEClient client = new APEClient("ffl.com", runtime, Map.of(), Map.of());

        List<String> command = client.commandLine(List.of("share", "file.txt"));

        require(
            command.equals(List.of("libape.so", "libffl.so", "share", "file.txt")),
            "launcher precedes the binary and no shell is involved"
        );
    }

    private static void testRuntimeEnvironmentUnsetsPolicyVariables() {
        String existing = System.getenv().keySet().iterator().next();
        APEClient client = new APEClient(
            "ffl.com",
            APERuntime.builder().binary(Path.of("ffl.com")).build(),
            Map.of(),
            Map.<String, Object>of("env_unset", List.of(existing))
        );

        Map<String, String> environment = client.environment();

        require(!environment.containsKey(existing), "policy env_unset removes " + existing);
        require(
            environment.size() == System.getenv().size() - 1,
            "only the unset variable is removed"
        );
    }

    private static void testRuntimeEnvironmentOverridesWinOverPolicyUnset() {
        APERuntime runtime = APERuntime.builder()
            .binary(Path.of("ffl.com"))
            .environment("APEBIND_UNIT_VAR", "override")
            .build();
        APEClient client = new APEClient(
            "ffl.com",
            runtime,
            Map.of(),
            Map.<String, Object>of("env_unset", List.of("APEBIND_UNIT_VAR"))
        );

        require(
            "override".equals(client.environment().get("APEBIND_UNIT_VAR")),
            "runtime environment overrides apply after policy env_unset"
        );
    }

    private static void testBindingCreatedWithRuntimeKeepsIt() {
        APERuntime runtime = APERuntime.builder().binary(Path.of("ffl.com")).build();

        require(FFLBinding.create(runtime).runtime() == runtime, "binding keeps its runtime");
    }

    private static void testClientBoundToRuntimeUsesIt() {
        APERuntime runtime = APERuntime.builder().binary(Path.of("ffl.com")).build();

        require(
            new FFLClient(runtime).binding().runtime() == runtime,
            "client uses the bound runtime"
        );
    }

    private static void testSessionPidMatchesChildProcess() throws Exception {
        Process child = startChild(Sleeper.class);
        try {
            ProcessSession session = newSession(child, true);

            require(session.pid() == child.pid(), "session pid comes from the child process");
            session.stop();
        } finally {
            child.destroyForcibly();
            child.waitFor();
        }
    }

    private static void testStdoutLinesAreDeliveredLiveAndReplayed() throws Exception {
        Process child = startChild(LineEmitter.class);
        ProcessSession session = newSession(child, true);
        List<String> live = new CopyOnWriteArrayList<>();
        CountDownLatch delivered = new CountDownLatch(4);
        session.onStdoutLine(line -> {
            live.add(line);
            delivered.countDown();
        });

        require(delivered.await(10, TimeUnit.SECONDS), "stdout lines arrive while the process runs");
        child.waitFor();
        List<String> replayed = new ArrayList<>();
        session.onStdoutLine(replayed::add);

        require(List.of("one", "two", "three", "tail").equals(live), "lines are split on \\n, \\r stripped, last partial line flushed: " + live);
        require(live.equals(replayed), "a late listener replays the history in order: " + replayed);
        require("one\ntwo\r\nthree\ntail".equals(session.stdout()), "captured stdout text is untouched");
    }

    private static void testStdoutLinesNeedCapturedStdout() throws Exception {
        Process child = startChild(Sleeper.class);
        try {
            ProcessSession session = newSession(child, false);
            try {
                session.onStdoutLine(line -> {});
            } catch (IllegalStateException expected) {
                return;
            }
            throw new AssertionError("streamed stdout cannot also be watched line by line");
        } finally {
            child.destroyForcibly();
        }
    }

    public static final class LineEmitter {
        private LineEmitter() {}

        public static void main(String[] args) throws InterruptedException {
            System.out.print("one\ntwo\r\n");
            System.out.flush();
            Thread.sleep(200);
            System.out.print("three\ntail");
            System.out.flush();
        }
    }

    private static void testStderrLinesAreDeliveredLiveAndReplayed() throws Exception {
        Process child = startChild(StderrEmitter.class);
        ProcessSession session = newSession(child, true);
        List<String> live = new CopyOnWriteArrayList<>();
        CountDownLatch delivered = new CountDownLatch(3);
        session.onStderrLine(line -> {
            live.add(line);
            delivered.countDown();
        });

        require(delivered.await(10, TimeUnit.SECONDS), "stderr lines arrive while the process runs");
        child.waitFor();
        List<String> replayed = new ArrayList<>();
        session.onStderrLine(replayed::add);

        require(List.of("INFO: hello", "WARNING: Failed to forward hook event", "Traceback").equals(live), "stderr is split like stdout: " + live);
        require(live.equals(replayed), "a late stderr listener replays the history in order: " + replayed);
        require(List.of("out").equals(stdoutLines(session)), "stdout is not mixed into stderr");
    }

    private static void testSessionsForwardStderrLines() throws Exception {
        Process shareChild = startChild(StderrEmitter.class);
        List<String> shareLines = new CopyOnWriteArrayList<>();
        new ShareSession(newSession(shareChild, true)).onStderrLine(shareLines::add);
        Process loginChild = startChild(StderrEmitter.class);
        List<String> loginLines = new CopyOnWriteArrayList<>();
        new LoginSession(newSession(loginChild, true), new StdinFeed()).onStderrLine(loginLines::add);

        shareChild.waitFor();
        loginChild.waitFor();
        Thread.sleep(200);

        require(shareLines.contains("Traceback"), "ShareSession hands stderr lines over: " + shareLines);
        require(loginLines.contains("Traceback"), "LoginSession hands stderr lines over: " + loginLines);
    }

    private static void testPersistentJsonResultIsAwaitedLazily() throws Exception {
        APEClient client = lateJsonClient(10.0);
        long started = System.nanoTime();
        ProcessSession session = (ProcessSession) client.invoke("share", lateJsonValues(1500));
        try {
            long invokeMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);
            require(invokeMillis < 1000, "invoke returns before the JSON result exists; took " + invokeMillis + " ms");
            require("https://example.test/late".equals(session.result()), "result() waits for the file on first use");
            require(session.running(), "the process keeps running after the result was read");
        } finally {
            session.stop();
        }
    }

    private static void testLazyJsonResultTimeoutLeavesTheProcessRunning() throws Exception {
        APEClient client = lateJsonClient(0.5);
        ProcessSession session = (ProcessSession) client.invoke("share", lateJsonValues(60_000));
        try {
            try {
                session.result();
                throw new AssertionError("result() must time out when the file never appears");
            } catch (APETimeoutException expected) {
                require(session.running(), "a result timeout does not stop the process");
            }
        } finally {
            session.stop();
        }
    }

    /** A persistent json_file operation that runs {@link LateJsonWriter}: {@code java -cp <cp> <class> --json <file> <delay>}. */
    private static APEClient lateJsonClient(double readyTimeoutSeconds) {
        Map<String, Object> result = Map.of(
            "format", "json_file",
            "field", "link",
            "option_flag", "--json",
            "ready_timeout_seconds", readyTimeoutSeconds,
            "strip", true
        );
        Map<String, Object> operation = Map.of(
            "execution", "persistent",
            "result", result,
            "process", Map.of(),
            "levels", List.of(
                Map.of("options", List.of(), "positionals", List.of()),
                Map.of(
                    "token", "-cp",
                    "options", List.of(),
                    "positionals", List.of(Map.of("api_name", "classpath", "required", true))
                ),
                Map.of(
                    "token", LateJsonWriter.class.getName(),
                    "options", List.of(Map.of("runtime_owned", true)),
                    "positionals", List.of(Map.of("api_name", "delay_millis", "required", true))
                )
            )
        );
        APERuntime runtime = APERuntime.builder().binary(javaExecutable()).shellWrapper(false).build();
        return new APEClient("unit", runtime, Map.of("share", operation), Map.of());
    }

    private static Map<String, Object> lateJsonValues(long delayMillis) {
        return Map.of("classpath", System.getProperty("java.class.path"), "delay_millis", delayMillis);
    }

    public static final class LateJsonWriter {
        private LateJsonWriter() {}

        public static void main(String[] args) throws Exception {
            Thread.sleep(Long.parseLong(args[2]));
            Files.writeString(Path.of(args[1]), "{\"link\": \"https://example.test/late\"}");
            Thread.sleep(Long.MAX_VALUE);
        }
    }

    private static List<String> stdoutLines(ProcessSession session) {
        List<String> lines = new ArrayList<>();
        session.onStdoutLine(lines::add);
        return lines;
    }

    public static final class StderrEmitter {
        private StderrEmitter() {}

        public static void main(String[] args) throws InterruptedException {
            System.err.print("INFO: hello\nWARNING: Failed to forward hook event\r\n");
            System.err.flush();
            Thread.sleep(200);
            System.out.println("out");
            System.out.flush();
            System.err.print("Traceback");
            System.err.flush();
        }
    }

    private static void testStdinFeedDeliversWritesAndEndsOnClose() throws Exception {
        StdinFeed feed = new StdinFeed();
        feed.writeLine("one");
        Thread writer = new Thread(() -> feed.writeLine("two"));
        writer.start();
        writer.join();
        feed.close();

        String text = new String(feed.readAllBytes(), StandardCharsets.UTF_8);

        require("one\ntwo\n".equals(text), "writes from any thread arrive in order, then end of stream: " + text);
        require(feed.read() == -1, "a closed feed stays at end of stream");
        try {
            feed.writeLine("late");
        } catch (IllegalStateException expected) {
            return;
        }
        throw new AssertionError("writing after close must fail");
    }

    private static void testStdinCopyFlushesEachLine() throws Exception {
        Process child = startChild(EchoLine.class);
        ProcessSession session = newSession(child, true);
        StdinFeed feed = new StdinFeed();
        attachStdin(session, feed);
        BlockingQueue<String> lines = new LinkedBlockingQueue<>();
        session.onStdoutLine(lines::add);

        feed.writeLine("hello");

        String echoed = lines.poll(5, TimeUnit.SECONDS);
        require("got:hello".equals(echoed), "a line written to stdin reaches the child before the feed closes: " + echoed);
        feed.close();
        require(child.waitFor() == 0, "child exits after stdin ends");
    }

    private static void testPromptIsAnnouncedWhenOutputStallsWithoutNewline() throws Exception {
        Process child = startChild(Prompter.class);
        ProcessSession session = newSession(child, true);
        StdinFeed feed = new StdinFeed();
        attachStdin(session, feed);
        BlockingQueue<String> prompts = new LinkedBlockingQueue<>();
        session.onStdoutPrompt(prompts::add);

        String prompt = prompts.poll(5, TimeUnit.SECONDS);

        require("Enter code: ".equals(prompt), "unterminated text becomes a prompt after the quiet period: " + prompt);
        List<String> late = new ArrayList<>();
        session.onStdoutPrompt(late::add);
        require(List.of("Enter code: ").equals(late), "a listener registered during the prompt gets it at once");
        feed.writeLine("42");
        require(child.waitFor() == 0, "child exits after the answer");
        feed.close();
        List<String> replay = new ArrayList<>();
        session.onStdoutLine(replay::add);
        require(List.of("Enter code: ok 42").equals(replay), "an answered prompt merges into the next line: " + replay);
        require(prompts.poll(500, TimeUnit.MILLISECONDS) == null, "each prompt is announced once");
    }

    private static void testTerminatedLinesAreNotPrompts() throws Exception {
        Process child = startChild(LineEmitter.class);
        ProcessSession session = newSession(child, true);
        List<String> prompts = new CopyOnWriteArrayList<>();
        session.onStdoutPrompt(prompts::add);

        child.waitFor();
        Thread.sleep(600);

        require(prompts.isEmpty(), "lines ending in newline or end of stream are never prompts: " + prompts);
    }

    private static void testRuntimeToBuilderCopiesEverything() {
        APERuntime base = APERuntime.builder()
            .binary(Path.of("/x/ffl"))
            .launcher("/x/ape")
            .shellWrapper(false)
            .workingDirectory(Path.of("/w"))
            .environment("A", "1")
            .build();

        APERuntime derived = base.toBuilder().environment("B", "2").build();

        require(
            base.binary().equals(derived.binary()) && List.of("/x/ape").equals(derived.launcher())
                && !derived.shellWrapper() && base.workingDirectory().equals(derived.workingDirectory()),
            "launch settings are copied"
        );
        require(Map.of("A", "1", "B", "2").equals(derived.environment()), "environment is extended");
        require(Map.of("A", "1").equals(base.environment()), "the original runtime is untouched");
    }

    private static void testAuthStatusParsesLoggedInLayout() {
        AuthStatus status = AuthStatus.parse(
            "Authentication Status:\n   User: Leo\n   Email: leo@example.com\n   Level: Plus\n   Serial: ABC-123\n   Points: 420\n   Registered: Yes\n"
        );

        require("Leo".equals(status.name()) && "leo@example.com".equals(status.email()), "identity fields");
        require(status.level() == AuthStatus.Level.PLUS && "ABC-123".equals(status.serialNumber()), "level and serial");
        require(status.points() == 420 && status.registered() && status.loggedIn(), "points, registration, loggedIn");
    }

    private static void testAuthStatusParsesFreeLayout() {
        AuthStatus status = AuthStatus.parse(
            "Authentication Status:\n   User: Not logged in\n   Email: Not set\n   Level: Free\n   Serial: None\n   Points: 0\n   Registered: No\n"
        );

        require(status.name() == null && status.email() == null && status.serialNumber() == null, "placeholders become null");
        require(status.level() == AuthStatus.Level.FREE && status.points() == 0 && !status.registered(), "free account");
        require(!status.loggedIn(), "not logged in");
    }

    private static void testAuthStatusParsesFractionalPoints() {
        AuthStatus status = AuthStatus.parse(
            "Authentication Status:\n   User: Leo\n   Email: leo@example.com\n   Level: Plus\n   Serial: ABC-123\n   Points: 233.9\n   Registered: Yes\n"
        );

        require(status.points() == 233.9, "fractional points are kept as reported");
    }

    private static void testAuthStatusRejectsUnknownText() {
        try {
            AuthStatus.parse("身份驗證狀態：\n   使用者：Leo\n");
        } catch (IllegalStateException expected) {
            require(expected.getMessage().contains("身份驗證狀態"), "raw text is reported");
            return;
        }
        throw new AssertionError("a localized or unknown layout must be rejected");
    }

    private static void testLoginParametersUseCliMode() {
        Map<String, Object> values = new FFLClient().buildLoginParameters(" leo@example.com ").toValues();

        require("leo@example.com".equals(values.get("email")), "email is trimmed");
        require("cli".equals(values.get("mode")), "login runs in cli mode");
        try {
            new FFLClient().buildLoginParameters(" ");
        } catch (IllegalArgumentException expected) {
            return;
        }
        throw new AssertionError("blank email must be rejected");
    }

    private static void testEnglishBindingOnlyAddsTheLanguage() {
        FFLClient client = new FFLClient(APERuntime.builder().environment("A", "1").build());

        FFLBinding english = client.englishBinding();

        require(Map.of("A", "1", "FFL_LANGUAGE", "en").equals(english.runtime().environment()), "status binding forces English");
        require(Map.of("A", "1").equals(client.binding().runtime().environment()), "the main binding keeps the user language");
        require(english == client.englishBinding(), "the English binding is created once");
    }

    private static void testLoginSessionRunsTheOtpDialogue() throws Exception {
        Process child = startChild(FakeLoginCore.class);
        StdinFeed feed = new StdinFeed();
        ProcessSession process = newSession(child, true);
        attachStdin(process, feed);
        BlockingQueue<String> rejections = new LinkedBlockingQueue<>();
        try (LoginSession login = new LoginSession(process, feed)) {
            login.onOtpRejected(rejections::add);

            login.otpRequested().get(5, TimeUnit.SECONDS);
            require(login.phase() == LoginSession.Phase.OTP_REQUIRED, "waiting for the code");
            login.submitOtp("000000");
            String rejection = rejections.poll(5, TimeUnit.SECONDS);
            require(rejection != null && rejection.startsWith("[ERROR] Invalid OTP code"), "rejection carries the core message without the prompt: " + rejection);
            require(login.phase() == LoginSession.Phase.OTP_REQUIRED, "a rejected code asks again");
            login.submitOtp("123456");
            LoginResult result = login.result().get(5, TimeUnit.SECONDS);

            require(result.success() && result.exitCode() == 0, "accepted code logs in: " + result);
            require("Login successful.".equals(result.lastLine()), "last line is the core's final message: " + result.lastLine());
            require(login.phase() == LoginSession.Phase.DONE, "finished");
            try {
                login.submitOtp("123456");
            } catch (IllegalStateException expected) {
                require(expected.getMessage().startsWith("login already finished"), "the session, not the closed feed, refuses: " + expected.getMessage());
                return;
            }
            throw new AssertionError("no code can be submitted after the run ended");
        }
    }

    private static void testLoginSessionFailsOtpRequestWhenRunEndsEarly() throws Exception {
        Process child = startChild(FailingLoginCore.class);
        StdinFeed feed = new StdinFeed();
        ProcessSession process = newSession(child, true);
        attachStdin(process, feed);
        try (LoginSession login = new LoginSession(process, feed)) {
            LoginResult result = login.result().get(5, TimeUnit.SECONDS);

            require(!result.success() && result.exitCode() == 1 && "Login failed!".equals(result.lastLine()), "failure result: " + result);
            try {
                login.otpRequested().get(1, TimeUnit.SECONDS);
            } catch (java.util.concurrent.ExecutionException expected) {
                require(expected.getCause().getMessage().contains("Login failed!"), "the failure explains why no code was asked for");
                return;
            }
            throw new AssertionError("otpRequested must fail when the run ends first");
        }
    }

    private static void testLoginSessionDoesNotMistakeVerificationForRejection() throws Exception {
        Process child = startChild(SlowVerifyLoginCore.class);
        StdinFeed feed = new StdinFeed();
        ProcessSession process = newSession(child, true);
        attachStdin(process, feed);
        List<String> rejections = new CopyOnWriteArrayList<>();
        try (LoginSession login = new LoginSession(process, feed)) {
            login.onOtpRejected(rejections::add);
            login.otpRequested().get(5, TimeUnit.SECONDS);

            login.submitOtp("123456");
            LoginResult result = login.result().get(5, TimeUnit.SECONDS);

            require(result.success(), "slow verification still logs in: " + result);
            require(rejections.isEmpty(), "the prompt echoed before the core read the code is not a rejection: " + rejections);
        }
    }

    public static final class FakeLoginCore {
        private FakeLoginCore() {}

        public static void main(String[] args) throws java.io.IOException {
            var reader = new java.io.BufferedReader(new java.io.InputStreamReader(System.in, StandardCharsets.UTF_8));
            System.out.println("[CLI-AUTH] Please check your email for OTP code");
            System.out.print("Enter OTP code: ");
            System.out.flush();
            while (true) {
                String code = reader.readLine();
                if (code == null) {
                    System.exit(1);
                }
                if ("123456".equals(code)) {
                    System.out.println("[OK] OTP verification successful");
                    System.out.println("Login successful.");
                    System.out.flush();
                    System.exit(0);
                }
                System.out.println("[ERROR] Invalid OTP code");
                System.out.println("   Please try again...");
                System.out.print("Enter OTP code: ");
                System.out.flush();
            }
        }
    }

    public static final class FailingLoginCore {
        private FailingLoginCore() {}

        public static void main(String[] args) {
            System.out.println("Login failed!");
            System.out.flush();
            System.exit(1);
        }
    }

    public static final class SlowVerifyLoginCore {
        private SlowVerifyLoginCore() {}

        public static void main(String[] args) throws Exception {
            var reader = new java.io.BufferedReader(new java.io.InputStreamReader(System.in, StandardCharsets.UTF_8));
            System.out.println("[CLI-AUTH] Please check your email for OTP code");
            System.out.print("Enter OTP code: ");
            System.out.flush();
            reader.readLine();
            Thread.sleep(800);
            System.out.println("[OK] OTP verification successful");
            System.out.println("Login successful.");
            System.out.flush();
            System.exit(0);
        }
    }

    public static final class EchoLine {
        private EchoLine() {}

        public static void main(String[] args) throws java.io.IOException {
            var reader = new java.io.BufferedReader(new java.io.InputStreamReader(System.in, StandardCharsets.UTF_8));
            System.out.println("got:" + reader.readLine());
            System.out.flush();
        }
    }

    public static final class Prompter {
        private Prompter() {}

        public static void main(String[] args) throws java.io.IOException {
            System.out.print("Enter code: ");
            System.out.flush();
            var reader = new java.io.BufferedReader(new java.io.InputStreamReader(System.in, StandardCharsets.UTF_8));
            System.out.println("ok " + reader.readLine());
            System.out.flush();
        }
    }

    private static void attachStdin(ProcessSession session, InputStream source) throws Exception {
        java.lang.reflect.Method method = ProcessSession.class.getDeclaredMethod("attachStdin", InputStream.class);
        method.setAccessible(true);
        method.invoke(session, source);
    }

    private static Path javaExecutable() {
        return Path.of(
            System.getProperty("java.home"),
            "bin",
            System.getProperty("os.name").startsWith("Windows") ? "java.exe" : "java"
        );
    }

    private static Process startChild(Class<?> mainClass) throws Exception {
        return new ProcessBuilder(
            javaExecutable().toString(),
            "-cp",
            System.getProperty("java.class.path"),
            mainClass.getName()
        ).start();
    }

    private static ProcessSession newSession(Process child, boolean captureStdout) throws Exception {
        Class<?> resultFile = Class.forName("apebind.generated.ffl.APEClient$RuntimeFiles");
        Constructor<ProcessSession> constructor = ProcessSession.class.getDeclaredConstructor(
            Process.class,
            List.class,
            resultFile,
            java.util.Map.class,
            boolean.class
        );
        constructor.setAccessible(true);
        return constructor.newInstance(child, List.of("download"), null, null, captureStdout);
    }

    private static void testCallerManagedHook() {
        String hookUrl = "http://127.0.0.1:9000/events";
        ShareOptions options = ShareOptions.builder()
            .hookUrl(hookUrl)
            .captureHookEvents(false)
            .build();

        require(FFLClient.createEventChannel(options) == null, "caller-managed hook channel");
        require(
            hookUrl.equals(FFLClient.effectiveHookUrl(options, null)),
            "caller-managed hook URL"
        );
    }

    private static void testHookEvents() throws Exception {
        try (FFLHookEventChannel channel = new FFLHookEventChannel(null)) {
            String payload = """
                {"event":"/hook/server/endpoints/register","timestamp":"2026-09-07T00:00:00Z","data":{"fileName":"file.txt","fileSize":5}}
                """;
            HttpRequest request = HttpRequest.newBuilder(URI.create(channel.url()))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(payload, StandardCharsets.UTF_8))
                .build();
            HttpResponse<String> response = HttpClient.newHttpClient().send(
                request,
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)
            );

            require(response.statusCode() == 200, "hook response");
            require("{}".equals(response.body()), "hook response body");
            require(channel.history().size() == 1, "hook history");
            FFLHookEvent event = channel.history().get(0);
            require("/hook/server/endpoints/register".equals(event.name()), "hook name");
            require(event.semanticName() == null, "registration has no semantic name");
            require("file.txt".equals(event.data().get("fileName")), "hook data");
            List<FFLHookEvent> replayed = new ArrayList<>();
            channel.on(event.name(), replayed::add);
            require(replayed.equals(channel.history()), "hook listener replay");
        }
    }

    private static void testIntegratedTransferEvents() throws Exception {
        try (FFLHookEventChannel channel = new FFLHookEventChannel(null)) {
            sendHookEvent(channel, "/download/complete", 1);
            sendHookEvent(channel, "/webrtc/transfer/complete", 2);
            sendHookEvent(channel, "/p2p/transfer/complete", 3);
            sendHookEvent(channel, "/hook/transfer/complete", 4);
            sendHookEvent(channel, "/transfer/complete", 5);
            sendHookEvent(channel, "/share/available", 6);
            sendHookEvent(channel, "/transfer/create", 7);
            sendHookEvent(channel, "/transfer/progress", 8);
            sendHookEvent(channel, "/download/progress", 9);
            sendHookEvent(channel, "/transfer/fail", 10);

            require(semanticNames(channel, "completed").equals(List.of("/transfer/complete")), "completed");
            require(semanticNames(channel, "progress").equals(List.of("/transfer/progress")), "progress");
            require(semanticNames(channel, "ready").equals(List.of("/share/available")), "ready");
            require(semanticNames(channel, "started").equals(List.of("/transfer/create")), "started");
            require(semanticNames(channel, "failed").equals(List.of("/transfer/fail")), "failed");

            List<FFLHookEvent> replayed = new ArrayList<>();
            channel.onSemantic("completed", replayed::add);
            require(replayed.size() == 1, "completed replays once");
            require("/transfer/complete".equals(replayed.get(0).name()), "replayed completed name");
        }
    }

    private static List<String> semanticNames(FFLHookEventChannel channel, String semanticName) {
        List<String> names = new ArrayList<>();
        for (FFLHookEvent event : channel.history()) {
            if (semanticName.equals(event.semanticName())) {
                names.add(event.name());
            }
        }
        return names;
    }

    private static void testBoundedSemanticHookEvents() throws Exception {
        try (FFLHookEventChannel channel = new FFLHookEventChannel(null, 2)) {
            sendHookEvent(channel, "/share/available", 1);
            sendHookEvent(channel, "/transfer/progress", 2);
            sendHookEvent(channel, "/transfer/complete", 3);

            require(channel.history().size() == 2, "bounded hook history");
            require(
                "completed".equals(channel.history().get(1).semanticName()),
                "semantic hook event name"
            );
            List<FFLHookEvent> replayed = new ArrayList<>();
            channel.onSemantic("completed", replayed::add);
            require(replayed.equals(List.of(channel.history().get(1))), "semantic listener replay");
        }
    }

    private static void testOnAnyReceivesEveryEventAndReplaysHistory() throws Exception {
        Process child = startChild(Sleeper.class);
        try (FFLHookEventChannel channel = new FFLHookEventChannel(null)) {
            ShareSession session = new ShareSession(newSession(child, true), channel);
            sendHookEvent(channel, "/share/available", 1);
            List<String> names = new CopyOnWriteArrayList<>();
            CountDownLatch delivered = new CountDownLatch(3);
            session.onAny(event -> {
                names.add(event.name());
                delivered.countDown();
            });
            sendHookEvent(channel, "/hook/server/endpoints/register", 2);
            sendHookEvent(channel, "/transfer/progress", 3);

            require(delivered.await(5, TimeUnit.SECONDS), "every event reaches the catch-all listener");
            require(
                List.of("/share/available", "/hook/server/endpoints/register", "/transfer/progress").equals(names),
                "history is replayed first, then live events in order: " + names
            );
        } finally {
            child.destroyForcibly();
            child.waitFor();
        }
    }

    private static void testLiveHookListenersSurviveHistoryEviction() throws Exception {
        try (FFLHookEventChannel channel = new FFLHookEventChannel(null, 2)) {
            List<Long> received = new ArrayList<>();
            CountDownLatch delivered = new CountDownLatch(4);
            channel.on("/transfer/progress", event -> received.add(
                ((Number) event.data().get("index")).longValue()
            ));
            channel.on("/transfer/progress", event -> {
                delivered.countDown();
            });
            sendHookEvent(channel, "/transfer/progress", 1);
            sendHookEvent(channel, "/transfer/progress", 2);
            sendHookEvent(channel, "/transfer/progress", 3);
            sendHookEvent(channel, "/transfer/progress", 4);

            require(delivered.await(1, TimeUnit.SECONDS), "lossless live hook delivery");
            require(channel.history().size() == 2, "bounded live hook history");
            require(received.equals(List.of(1L, 2L, 3L, 4L)), "lossless live hook listener");
        }
    }

    private static void testHookResponseDoesNotWaitForLocalListeners() throws Exception {
        try (FFLHookEventChannel channel = new FFLHookEventChannel(null)) {
            CountDownLatch listenerStarted = new CountDownLatch(1);
            CountDownLatch releaseListener = new CountDownLatch(1);
            channel.on("/transfer/progress", event -> {
                listenerStarted.countDown();
                try {
                    releaseListener.await(5, TimeUnit.SECONDS);
                } catch (InterruptedException caught) {
                    Thread.currentThread().interrupt();
                }
            });
            long started = System.nanoTime();
            sendHookEvent(channel, "/transfer/progress", 1);

            require(
                TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started) < 500,
                "hook response must not wait for local listener"
            );
            require(listenerStarted.await(1, TimeUnit.SECONDS), "local listener runs asynchronously");
            releaseListener.countDown();
        }
    }

    private static void testHookReceiverListensOnLoopback() {
        try (FFLHookReceiver receiver = new FFLHookReceiver(body -> "{}")) {
            require(receiver.port() > 0, "receiver port");
            require(
                receiver.url().equals("http://127.0.0.1:" + receiver.port()),
                "receiver URL is loopback"
            );
        }
    }

    private static void testHookReceiverAnswersPostWithHandlerJson() throws Exception {
        List<String> received = new CopyOnWriteArrayList<>();
        try (FFLHookReceiver receiver = new FFLHookReceiver(body -> {
            received.add(body);
            return "{\"confirmed\":true}";
        })) {
            String body = "{\"event\":\"/upload/confirm\",\"data\":{\"name\":\"Pixel 9 的 3 個項目\"}}";

            String response = rawPost(receiver, body);

            require(statusOf(response) == 200, "receiver status");
            require(response.contains("\r\nContent-Type: application/json\r\n"), "receiver content type");
            require("{\"confirmed\":true}".equals(bodyOf(response)), "receiver answers with handler JSON");
            require(received.equals(List.of(body)), "receiver passes the UTF-8 body through intact");
        }
    }

    private static void testHookReceiverRejectsUnservableRequestsBeforeHandler() throws Exception {
        requireRejected("GET / HTTP/1.1\r\nHost: 127.0.0.1\r\n\r\n", 405, "non-POST request");
        requireRejected("POST / HTTP/1.1\r\nHost: 127.0.0.1\r\n\r\n", 411, "missing Content-Length");
        requireRejected("POST / HTTP/1.1\r\nContent-Length: abc\r\n\r\n", 400, "non-numeric Content-Length");
        requireRejected(
            "POST / HTTP/1.1\r\nContent-Length: 1\r\nContent-Length: 2\r\n\r\n",
            400,
            "conflicting Content-Length"
        );
        requireRejected(
            "POST / HTTP/1.1\r\nContent-Length: " + (FFLHookReceiver.MAX_BODY_BYTES + 1) + "\r\n\r\n",
            413,
            "oversized body"
        );
        requireRejected("POST / HTTP/1.1\r\nTransfer-Encoding: chunked\r\n\r\n", 501, "chunked body");
        requireRejected("NOT-HTTP\r\n\r\n", 400, "malformed request line");
    }

    private static void testHookReceiverAnswers500WhenHandlerThrows() throws Exception {
        try (FFLHookReceiver receiver = new FFLHookReceiver(body -> {
            throw new IllegalStateException("handler failed");
        })) {
            String response = rawPost(receiver, "{}");

            require(statusOf(response) == 500, "handler failure status");
            require("handler failed".equals(bodyOf(response)), "handler failure message");
        }
    }

    private static void testHookReceiverServesRequestsConcurrently() throws Exception {
        CountDownLatch slowEntered = new CountDownLatch(1);
        CountDownLatch releaseSlow = new CountDownLatch(1);
        ExecutorService client = Executors.newSingleThreadExecutor();
        try (FFLHookReceiver receiver = new FFLHookReceiver(body -> {
            if (body.contains("slow")) {
                slowEntered.countDown();
                awaitQuietly(releaseSlow);
            }
            return "{}";
        })) {
            Future<String> slow = client.submit(() -> rawPost(receiver, "{\"slow\":true}"));
            require(slowEntered.await(2, TimeUnit.SECONDS), "slow hook request reached its handler");

            String fast = rawPost(receiver, "{\"fast\":true}");

            require(statusOf(fast) == 200, "a blocked handler must not stall other hook requests");
            releaseSlow.countDown();
            require(statusOf(slow.get(5, TimeUnit.SECONDS)) == 200, "blocked hook request completes once released");
        } finally {
            releaseSlow.countDown();
            client.shutdownNow();
        }
    }

    private static void testHookReceiverDropsIdleConnection() throws Exception {
        try (FFLHookReceiver receiver = new FFLHookReceiver(body -> "{}", 200)) {
            try (Socket idle = new Socket("127.0.0.1", receiver.port())) {
                idle.setSoTimeout(5000);
                long started = System.nanoTime();

                int read = idle.getInputStream().read();

                require(read == -1, "idle hook connection is closed without a response");
                require(
                    TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started) < 3000,
                    "idle hook connection is closed after the read timeout"
                );
            }
        }
    }

    private static void testHookReceiverRefusesConnectionsAfterClose() throws Exception {
        FFLHookReceiver receiver = new FFLHookReceiver(body -> "{}");
        int port = receiver.port();

        receiver.close();

        try {
            new Socket("127.0.0.1", port).close();
        } catch (ConnectException expected) {
            return;
        }
        throw new AssertionError("closed hook receiver still accepts connections");
    }

    private static void testHookChannelForwardsEventToCallerHook() throws Exception {
        BlockingQueue<String> forwarded = new LinkedBlockingQueue<>();
        try (FFLHookReceiver callerHook = new FFLHookReceiver(body -> {
            forwarded.add(body);
            return "{}";
        })) {
            try (FFLHookEventChannel channel = new FFLHookEventChannel(callerHook.url() + "/events")) {
                sendHookEvent(channel, "/transfer/progress", 7);

                require(
                    hookPayload("/transfer/progress", 7).equals(forwarded.poll(5, TimeUnit.SECONDS)),
                    "hook event forwarded verbatim to caller hook"
                );
            }
        }
    }

    private static void testHookChannelRecordsMalformedPayloadAsError() throws Exception {
        try (FFLHookEventChannel channel = new FFLHookEventChannel(null)) {
            String response = exchangeRaw(URI.create(channel.url()).getPort(), postRequest("{\"data\":{}}"));

            require(statusOf(response) == 500, "malformed hook payload answered with 500");
            try {
                channel.raiseIfError();
            } catch (IllegalArgumentException expected) {
                return;
            }
            throw new AssertionError("malformed hook payload must be recorded as a channel error");
        }
    }

    private static void testJsonParseKeepsIntegersAsLongs() {
        Map<String, Object> parsed = Json.object(Json.parse("{\"size\":20480,\"ratio\":0.5,\"big\":1e3}"));

        require(Long.valueOf(20480L).equals(parsed.get("size")), "integer literal parses as Long");
        require(Double.valueOf(0.5).equals(parsed.get("ratio")), "fraction parses as Double");
        require(Double.valueOf(1000.0).equals(parsed.get("big")), "exponent parses as Double");
    }

    private static void testJsonStringifyRoundTripsNestedValues() {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("confirmed", true);
        value.put("sidecar", Map.of("size", 20480L));
        value.put("routes", List.of(Map.of("method", "GET", "path", "/thumb", "encryptResponse", false)));
        value.put("name", "Pixel 9 的 3 個項目 \"quoted\" back\\slash\nnewline\ttab\u0001");
        value.put("ratio", 0.5);
        value.put("nothing", null);

        String text = Json.stringify(value);

        require(Json.parse(text).equals(value), "stringify output parses back to the same value");
        require(text.startsWith("{\"confirmed\":true,\"sidecar\":{\"size\":20480},"), "compact form keeps key order");
        require(text.contains("\\u0001"), "control characters are escaped");
        require(text.contains("的"), "non-ASCII text passes through unescaped");
        require("{\"size\":5}".equals(Json.stringify(Map.of("size", 5))), "integers serialize without a fraction");
        require("[]".equals(Json.stringify(List.of())), "top-level arrays serialize");
    }

    private static void testJsonStringifyRejectsUnsupportedValues() {
        for (Object unsupported : new Object[] {Double.NaN, new Object(), Map.of(1, "x")}) {
            try {
                Json.stringify(unsupported);
            } catch (IllegalArgumentException expected) {
                continue;
            }
            throw new AssertionError("stringify must reject " + unsupported);
        }
    }

    private static void testResponderReplyReachesCore() throws Exception {
        List<Object> seen = new CopyOnWriteArrayList<>();
        Map<String, FFLHookResponder> responders = Map.of("/upload/confirm", event -> {
            seen.add(event.data().get("fileName"));
            return Map.of("confirmed", true);
        });
        try (FFLHookEventChannel channel = new FFLHookEventChannel(null, 1000, responders)) {
            String response = rawPost(channel, "{\"event\":\"/upload/confirm\",\"data\":{\"fileName\":\"a.txt\"}}");

            require(statusOf(response) == 200, "responder status");
            require("{\"confirmed\":true}".equals(bodyOf(response)), "responder JSON is the hook reply");
            require(seen.equals(List.of("a.txt")), "responder receives the parsed event");
            require(channel.history().size() == 1, "responded events still enter history");
        }
    }

    private static void testResponderNullReplyMeansEmptyObject() throws Exception {
        Map<String, FFLHookResponder> responders = Map.of("/upload/tell", event -> null);
        try (FFLHookEventChannel channel = new FFLHookEventChannel(null, 1000, responders)) {
            String response = rawPost(channel, hookPayload("/upload/tell", 1));

            require("{}".equals(bodyOf(response)), "null responder reply becomes an empty object");
        }
    }

    private static void testResponderFailureAnswers500AndRecordsError() throws Exception {
        Map<String, FFLHookResponder> responders = Map.of("/upload/confirm", event -> {
            throw new IllegalStateException("no points");
        });
        try (FFLHookEventChannel channel = new FFLHookEventChannel(null, 1000, responders)) {
            String response = rawPost(channel, hookPayload("/upload/confirm", 1));

            require(statusOf(response) == 500, "responder failure status");
            require("no points".equals(bodyOf(response)), "responder failure message reaches the core");
            try {
                channel.raiseIfError();
            } catch (IllegalStateException expected) {
                return;
            }
            throw new AssertionError("responder failure must be recorded as a channel error");
        }
    }

    private static void testEventsWithoutResponderStillGetEmptyObject() throws Exception {
        Map<String, FFLHookResponder> responders = Map.of("/upload/confirm", event -> Map.of("confirmed", true));
        try (FFLHookEventChannel channel = new FFLHookEventChannel(null, 1000, responders)) {
            sendHookEvent(channel, "/transfer/progress", 1);
        }
    }

    private static void testUploadConfirmationOptionAnswersConfirmed() throws Exception {
        ShareOptions options = ShareOptions.builder()
            .uploadConfirmation(event -> false)
            .build();
        try (FFLHookEventChannel channel = FFLClient.createEventChannel(options)) {
            String response = rawPost(channel, hookPayload("/upload/confirm", 1));

            require("{\"confirmed\":false}".equals(bodyOf(response)), "upload confirmation decision is the reply");
        }
    }

    private static void testRespondToOptionAnswersWithResponderJson() throws Exception {
        ShareOptions options = ShareOptions.builder()
            .respondTo("/hook/server/endpoints/register", event -> Map.of("routes", List.of()))
            .build();
        try (FFLHookEventChannel channel = FFLClient.createEventChannel(options)) {
            String response = rawPost(channel, hookPayload("/hook/server/endpoints/register", 1));

            require("{\"routes\":[]}".equals(bodyOf(response)), "respondTo responder answers its event");
        }
    }

    private static void testRespondersRequireCapturedHookEvents() {
        ShareOptions.Builder builder = ShareOptions.builder()
            .captureHookEvents(false)
            .respondTo("/upload/confirm", event -> Map.of("confirmed", true));
        try {
            builder.build();
        } catch (IllegalStateException expected) {
            return;
        }
        throw new AssertionError("responders without ffl-java's hook channel can never run");
    }

    private static void testEventConstantsAreDistinctWireNames() throws Exception {
        List<String> names = new ArrayList<>();
        for (java.lang.reflect.Field field : FFLHookEvents.class.getDeclaredFields()) {
            if (field.getType() == String.class && java.lang.reflect.Modifier.isStatic(field.getModifiers())) {
                names.add((String) field.get(null));
            }
        }

        require(names.size() >= 40, "event vocabulary covers the 4.2.2 hook events: " + names.size());
        require(names.stream().allMatch(name -> name.startsWith("/")), "every constant is a wire name");
        require(names.stream().distinct().count() == names.size(), "wire names are distinct");
        require("/upload/confirm".equals(FFLHookEvents.UPLOAD_CONFIRM), "upload confirm wire name");
        require("/hook/server/endpoints/register".equals(FFLHookEvents.ENDPOINTS_REGISTER), "registration wire name");
    }

    private static void testShareLinkEventHasSemanticLinkName() {
        FFLHookEvent event = new FFLHookEvent(
            FFLHookEvents.SHARE_LINK_CREATE, null, Map.of("link", "https://fastfilelink.com/x", "upload", false), Map.of()
        );

        require("link".equals(event.semanticName()), "share link semantic name");
        require("https://fastfilelink.com/x".equals(event.link()), "link accessor");
        require(new FFLHookEvent(FFLHookEvents.UPLOAD_PROGRESS, null, Map.of(), Map.of()).link() == null, "no link elsewhere");
    }

    private static void testProgressAccessorReadsTransferFields() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("percentage", 42.5);
        data.put("bytesTransferred", 1024L);
        data.put("totalBytes", 2048L);
        data.put("speed", 300L);
        FFLHookEvent.Progress progress = new FFLHookEvent(FFLHookEvents.UPLOAD_PROGRESS, null, data, Map.of()).progress();

        require(progress != null && progress.percentage() == 42.5, "percentage");
        require(progress.bytesTransferred() == 1024L && progress.bytesPerSecond() == 300L, "byte counters");
        require(Long.valueOf(2048L).equals(progress.totalBytes()), "total bytes");
        FFLHookEvent.Progress partial = new FFLHookEvent(
            FFLHookEvents.WEBRTC_TRANSFER_PROGRESS, null, Map.of("percentage", 7L, "bytesTransferred", 1L), Map.of()
        ).progress();
        require(partial != null && partial.percentage() == 7.0 && partial.totalBytes() == null, "missing total stays null");
        require(new FFLHookEvent(FFLHookEvents.UPLOAD_CREATE, null, Map.of(), Map.of()).progress() == null, "no percentage, no progress");
    }

    private static void testErrorAccessorReadsFailureText() {
        require("boom".equals(new FFLHookEvent(FFLHookEvents.UPLOAD_FAIL, null, Map.of("error", "boom"), Map.of()).error()), "error text");
        require(new FFLHookEvent(FFLHookEvents.UPLOAD_PROGRESS, null, Map.of(), Map.of()).error() == null, "no error elsewhere");
    }

    // --- hook endpoint proxy: the core fetches registered routes from the hook host:port ---

    private static void testHookReceiverProxiesEndpointRoutesToTheConfiguredBase() throws Exception {
        AtomicInteger handled = new AtomicInteger();
        try (LoopbackUpstream upstream = new LoopbackUpstream(
                "HTTP/1.1 206 Partial Content\r\nContent-Type: image/jpeg\r\nContent-Range: bytes 0-2/10\r\n"
                    + "Server: vfs\r\nContent-Length: 3\r\nConnection: close\r\n\r\nabc"
            );
            FFLHookReceiver receiver = proxyingReceiver(upstream.url(), handled)) {
            String response = exchangeRaw(
                receiver.port(),
                "GET /thumb?hash=h1 HTTP/1.1\r\nHost: 127.0.0.1\r\nRange: bytes=0-2\r\nX-FFL-UID: 42\r\n\r\n"
            );

            require(statusOf(response) == 206, "upstream status is relayed: " + response);
            require(response.contains("\r\nContent-Type: image/jpeg\r\n"), "upstream content type is relayed");
            require(response.contains("\r\nContent-Range: bytes 0-2/10\r\n"), "upstream headers are relayed");
            require("abc".equals(bodyOf(response)), "upstream body is relayed");
            String seen = upstream.request();
            require(seen.startsWith("GET /thumb?hash=h1 HTTP/1.1\r\n"), "method, path and query reach the upstream: " + seen);
            require(
                seen.contains("\r\nRange: bytes=0-2\r\n") && seen.contains("\r\nX-FFL-UID: 42\r\n"),
                "request headers reach the upstream: " + seen
            );
            require(handled.get() == 0, "proxied requests never reach the hook handler");
        }
    }

    private static void testHookReceiverProxiesPostRoutesWithTheirBody() throws Exception {
        AtomicInteger handled = new AtomicInteger();
        try (LoopbackUpstream upstream = new LoopbackUpstream(
                "HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nContent-Length: 2\r\nConnection: close\r\n\r\n{}"
            );
            FFLHookReceiver receiver = proxyingReceiver(upstream.url(), handled)) {
            String body = "{\"question\":\"sidecar\"}";

            String response = exchangeRaw(
                receiver.port(),
                "POST /sidecar HTTP/1.1\r\nHost: 127.0.0.1\r\nContent-Type: application/json\r\nContent-Length: "
                    + body.getBytes(StandardCharsets.UTF_8).length + "\r\n\r\n" + body
            );

            require(statusOf(response) == 200 && "{}".equals(bodyOf(response)), "POST route answer is relayed");
            require(upstream.request().endsWith("\r\n\r\n" + body), "POST body reaches the upstream");
            require(handled.get() == 0, "POST routes off the hook path are not hook events");
        }
    }

    private static void testHookReceiverStillTakesHookEventsWhileProxying() throws Exception {
        List<String> received = new CopyOnWriteArrayList<>();
        try (LoopbackUpstream upstream = new LoopbackUpstream("HTTP/1.1 200 OK\r\nContent-Length: 0\r\nConnection: close\r\n\r\n");
            FFLHookReceiver receiver = new FFLHookReceiver(body -> {
                received.add(body);
                return "{\"routes\":[]}";
            }, FFLHookReceiver.DEFAULT_READ_TIMEOUT_MILLIS, upstream.url())) {
            String body = hookPayload(FFLHookEvents.ENDPOINTS_REGISTER, 1);

            String response = exchangeRaw(
                receiver.port(),
                "POST / HTTP/1.1\r\nHost: 127.0.0.1\r\nContent-Type: application/json\r\nContent-Length: "
                    + body.getBytes(StandardCharsets.UTF_8).length + "\r\n\r\n" + body
            );

            require(statusOf(response) == 200 && "{\"routes\":[]}".equals(bodyOf(response)), "hook POSTs are still answered by the handler");
            require(received.equals(List.of(body)), "hook body reaches the handler");
            require(upstream.requestCount() == 0, "hook events are not proxied");
        }
    }

    private static void testHookReceiverAnswers502WhenTheEndpointUpstreamIsDown() throws Exception {
        int closedPort;
        try (ServerSocket probe = new ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))) {
            closedPort = probe.getLocalPort();
        }
        AtomicInteger handled = new AtomicInteger();
        try (FFLHookReceiver receiver = proxyingReceiver("http://127.0.0.1:" + closedPort, handled)) {
            String response = exchangeRaw(receiver.port(), "GET /manifest HTTP/1.1\r\nHost: 127.0.0.1\r\n\r\n");

            require(statusOf(response) == 502, "unreachable upstream answers 502: " + response);
            require(handled.get() == 0, "failed proxy requests never reach the hook handler");
        }
    }

    private static void testShareOptionsDropsTheTrailingSlashOfTheEndpointProxy() {
        ShareOptions options = ShareOptions.builder().hookEndpointProxy("http://127.0.0.1:8080/").build();

        require("http://127.0.0.1:8080".equals(options.hookEndpointProxy()), "base URL keeps no trailing slash");
    }

    private static void testShareOptionsEndpointProxyNeedsTheHookChannel() {
        try {
            ShareOptions.builder().captureHookEvents(false).hookEndpointProxy("http://127.0.0.1:8080").build();
        } catch (IllegalStateException expected) {
            return;
        }
        throw new AssertionError("hookEndpointProxy without the hook channel must be rejected");
    }

    private static void testShareOptionsRejectsNonHttpEndpointProxies() {
        try {
            ShareOptions.builder().hookEndpointProxy("ftp://127.0.0.1:21");
        } catch (IllegalArgumentException expected) {
            return;
        }
        throw new AssertionError("a non-HTTP endpoint proxy must be rejected");
    }

    private static FFLHookReceiver proxyingReceiver(String base, AtomicInteger handled) {
        return new FFLHookReceiver(body -> {
            handled.incrementAndGet();
            return "{}";
        }, FFLHookReceiver.DEFAULT_READ_TIMEOUT_MILLIS, base);
    }

    /** Loopback HTTP/1.1 upstream for the endpoint proxy tests: records every request, answers canned bytes. */
    private static final class LoopbackUpstream implements AutoCloseable {
        private final ServerSocket server = new ServerSocket(0, 10, InetAddress.getByName("127.0.0.1"));
        private final List<String> requests = new CopyOnWriteArrayList<>();
        private final byte[] response;

        LoopbackUpstream(String response) throws IOException {
            this.response = response.getBytes(StandardCharsets.ISO_8859_1);
            Thread acceptor = new Thread(this::accept, "test-upstream");
            acceptor.setDaemon(true);
            acceptor.start();
        }

        String url() {
            return "http://127.0.0.1:" + server.getLocalPort();
        }

        String request() {
            require(!requests.isEmpty(), "upstream received a request");
            return requests.get(0);
        }

        int requestCount() {
            return requests.size();
        }

        private void accept() {
            while (!server.isClosed()) {
                try (Socket socket = server.accept()) {
                    socket.setSoTimeout(5000);
                    requests.add(readRequest(socket.getInputStream()));
                    socket.getOutputStream().write(response);
                    socket.getOutputStream().flush();
                } catch (IOException closed) {
                    return;
                }
            }
        }

        private static String readRequest(InputStream input) throws IOException {
            ByteArrayOutputStream head = new ByteArrayOutputStream();
            int next;
            while ((next = input.read()) >= 0) {
                head.write(next);
                byte[] bytes = head.toByteArray();
                int size = bytes.length;
                if (size >= 4 && bytes[size - 4] == '\r' && bytes[size - 3] == '\n' && bytes[size - 2] == '\r' && bytes[size - 1] == '\n') {
                    break;
                }
            }
            String text = head.toString(StandardCharsets.ISO_8859_1);
            int length = 0;
            for (String line : text.split("\r\n")) {
                if (line.regionMatches(true, 0, "Content-Length:", 0, 15)) {
                    length = Integer.parseInt(line.substring(15).trim());
                }
            }
            return text + new String(input.readNBytes(length), StandardCharsets.UTF_8);
        }

        @Override
        public void close() throws IOException {
            server.close();
        }
    }

    private static String rawPost(FFLHookEventChannel channel, String body) throws Exception {
        return exchangeRaw(URI.create(channel.url()).getPort(), postRequest(body));
    }

    private static void requireRejected(String request, int status, String label) throws Exception {
        AtomicInteger handled = new AtomicInteger();
        try (FFLHookReceiver receiver = new FFLHookReceiver(body -> {
            handled.incrementAndGet();
            return "{}";
        })) {
            String response = exchangeRaw(receiver.port(), request);

            require(statusOf(response) == status, label + " status");
            require(handled.get() == 0, label + " never reaches the handler");
        }
    }

    private static String hookPayload(String name, long index) {
        return "{\"event\":\"" + name + "\",\"data\":{\"index\":" + index + "}}";
    }

    private static String postRequest(String body) {
        return "POST /events HTTP/1.1\r\n"
            + "Host: 127.0.0.1\r\n"
            + "Content-Type: application/json\r\n"
            + "Content-Length: " + body.getBytes(StandardCharsets.UTF_8).length + "\r\n"
            + "\r\n"
            + body;
    }

    private static String rawPost(FFLHookReceiver receiver, String body) throws Exception {
        return exchangeRaw(receiver.port(), postRequest(body));
    }

    private static String exchangeRaw(int port, String request) throws Exception {
        try (Socket socket = new Socket("127.0.0.1", port)) {
            socket.setSoTimeout(5000);
            OutputStream output = socket.getOutputStream();
            output.write(request.getBytes(StandardCharsets.UTF_8));
            output.flush();
            return new String(socket.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static int statusOf(String response) {
        require(response.startsWith("HTTP/1.1 "), "HTTP/1.1 status line: " + response);
        return Integer.parseInt(response.substring(9, 12));
    }

    private static String bodyOf(String response) {
        int separator = response.indexOf("\r\n\r\n");
        require(separator >= 0, "HTTP response head terminator");
        return response.substring(separator + 4);
    }

    private static void awaitQuietly(CountDownLatch latch) {
        try {
            latch.await(5, TimeUnit.SECONDS);
        } catch (InterruptedException caught) {
            Thread.currentThread().interrupt();
        }
    }

    private static void sendHookEvent(
        FFLHookEventChannel channel,
        String name,
        long index
    ) throws Exception {
        String payload = hookPayload(name, index);
        HttpRequest request = HttpRequest.newBuilder(URI.create(channel.url()))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(payload, StandardCharsets.UTF_8))
            .build();
        HttpResponse<String> response = HttpClient.newHttpClient().send(
            request,
            HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)
        );
        require(response.statusCode() == 200, "hook response");
        require("{}".equals(response.body()), "hook response body");
    }

    private static void testShareOptionMapping() throws Exception {
        Path file = Files.createTempFile("ffl-java-unit-", ".txt");
        ShareOptions options = ShareOptions.builder()
            .timeoutSeconds(1800)
            .hookUrl("http://127.0.0.1/events")
            .upload()
            .receipt()
            .receiptConfirm("confirm")
            .qr()
            .resumeUpload(true)
            .watch(true)
            .watchSettleSeconds(9)
            .build();
        Map<String, Object> values = new FFLClient()
            .buildShareParameters(List.of(file), options, null)
            .toValues();

        require(values.get("paths") instanceof List<?>, "paths must be a list");
        require(Long.valueOf(1800).equals(values.get("timeout")), "timeout mapping");
        require("http://127.0.0.1/events".equals(values.get("hook")), "hook mapping");
        require(Boolean.TRUE.equals(values.get("resume")), "resume mapping");
        require(Boolean.TRUE.equals(values.get("watch")), "watch mapping");
        require(Long.valueOf(9).equals(values.get("watch_settle")), "watch settle mapping");
        require(
            ShareOptions.defaults().watchSettleSeconds() == null
                && new FFLClient().buildShareParameters(List.of(file), ShareOptions.defaults(), null)
                    .toValues().get("watch_settle") == null,
            "watch settle stays unset by default so the core keeps its own default"
        );
        require(Boolean.TRUE.equals(values.get("foreground")), "foreground must be forced");
        require(
            Boolean.TRUE.equals(values.get("disable_clipboard")),
            "clipboard must be disabled"
        );
        requireOptionalFlag(values.get("upload"), "upload");
        requireOptionalFlag(values.get("receipt"), "receipt");
        requireOptionalFlag(values.get("qr"), "qr");
        Object receiptConfirm = values.get("receipt_confirm");
        require(receiptConfirm instanceof OptionalValue<?>, "receiptConfirm optional value");
        OptionalValue<?> optional = (OptionalValue<?>) receiptConfirm;
        require(optional.present() && optional.hasValue(), "receiptConfirm value state");
        require("confirm".equals(optional.value()), "receiptConfirm value");
        Files.deleteIfExists(file);
    }

    private static void testDownloadOptionMapping() {
        DownloadOptions options = DownloadOptions.builder()
            .resume(true)
            .follow(true)
            .build();

        Map<String, Object> values = new FFLClient()
            .buildDownloadParameters("https://fastfilelink.com/x", options, false)
            .toValues();

        require("https://fastfilelink.com/x".equals(values.get("url")), "url mapping");
        require(Boolean.TRUE.equals(values.get("resume")), "resume mapping");
        require(Boolean.TRUE.equals(values.get("follow")), "follow mapping");
        require(Boolean.FALSE.equals(values.get("stdout")), "stdout mapping");
    }

    private static void testVfsShareOptionMapping() {
        ShareOptions options = ShareOptions.builder().name("Pixel 9 的 3 個項目").vfs(true).build();

        Map<String, Object> values = new FFLClient()
            .buildVfsShareParameters("vfs://127.0.0.1:4321", options)
            .toValues();

        require(List.of("vfs://127.0.0.1:4321").equals(values.get("paths")), "the vfs:// URI is the positional target");
        require(
            !Boolean.TRUE.equals(values.get("vfs")),
            "--vfs makes the core serve its own VFS, so a vfs:// target never sets it: " + values.get("vfs")
        );
        require("Pixel 9 的 3 個項目".equals(values.get("name")), "name mapping");
    }

    private static void testVfsShareRejectsNonVfsTargets() {
        for (String target : new String[] {"http://127.0.0.1:4321/", "ftp://127.0.0.1/", "/tmp/folder", "127.0.0.1:4321", "vfs://"}) {
            try {
                new FFLClient().buildVfsShareParameters(target, ShareOptions.defaults());
            } catch (IllegalArgumentException expected) {
                continue;
            }
            throw new AssertionError("VFS share must reject " + target);
        }
    }

    private static void testEnvironmentHelpersProduceCoreVariables() {
        require(Map.of("FFL_LANGUAGE", "zh-TW").equals(FFLEnvironment.language("zh-TW")), "language variable");
        require(Map.of("FFL_LOGGING_LEVEL", "INFO").equals(FFLEnvironment.loggingLevel("info")), "level is upper-cased");
        APERuntime runtime = APERuntime.builder()
            .environment(FFLEnvironment.language("zh_Hant"))
            .environment(FFLEnvironment.loggingLevel("DEBUG"))
            .build();
        require("zh_Hant".equals(runtime.environment().get("FFL_LANGUAGE")), "language reaches the runtime");
        require("DEBUG".equals(runtime.environment().get("FFL_LOGGING_LEVEL")), "level reaches the runtime");
        for (String bad : new String[] {"C:/ffl/logging.json", "TRACE", ""}) {
            try {
                FFLEnvironment.loggingLevel(bad);
            } catch (IllegalArgumentException expected) {
                continue;
            }
            throw new AssertionError("logging level must reject " + bad);
        }
    }

    private static void testStreamShareOptionMapping() {
        FFLClient client = new FFLClient();
        ShareOptions options = ShareOptions.builder().build();
        var parameters = client.buildShareParameters(
            List.of(Path.of("-")),
            options,
            "archive.tar"
        );
        Map<String, Object> values = parameters.toValues();

        require(
            List.of("-").equals(values.get("paths")),
            "stream share must use standard input"
        );
        require("archive.tar".equals(values.get("name")), "stream share name");
        try {
            require(
                FFL.class.getMethod("share", InputStream.class, String.class) != null,
                "FFL exposes stream sharing"
            );
        } catch (NoSuchMethodException error) {
            throw new AssertionError("FFL exposes stream sharing", error);
        }
    }

    private static void testDownloadParsing() {
        Path cwd = Path.of("/tmp/ffl-java-test");
        APEProcessResult process = new APEProcessResult(
            List.of("download", "https://example.test/file"),
            0,
            "WebRTC P2P\nDownloaded: package.zip\n",
            ""
        );
        DownloadResult result = FFLResultParser.parseDownload(process, null, cwd);
        require(result.process() == process, "download process identity");
        require(result.transferMode() == TransferMode.WEBRTC_P2P, "transfer mode");
        require(cwd.resolve("package.zip").equals(result.outputPath()), "download path");

        APEProcessResult tcpProcess = new APEProcessResult(
            List.of("download", "https://example.test/file"),
            0,
            "Using P2P TCP download\nDownloaded: package.zip\n",
            ""
        );
        DownloadResult tcpResult = FFLResultParser.parseDownload(tcpProcess, null, cwd);
        require(tcpResult.transferMode() == TransferMode.WEBRTC_P2P, "P2P TCP mode");
    }

    private static void testDownloadSessionApi() throws Exception {
        require(
            FFL.class.getMethod("startDownload", String.class) != null,
            "FFL exposes cancellable downloads"
        );
        require(
            FFL.class.getMethod("startDownload", String.class, DownloadOptions.class) != null,
            "FFL exposes configured cancellable downloads"
        );
        require(
            DownloadSession.class.getMethod("stdoutBytes") != null,
            "download session exposes binary stdout"
        );
        require(
            DownloadSession.class.getMethod("stdoutStream") != null,
            "download session exposes non-buffering stdout stream"
        );
        require(
            DownloadSession.class.getMethod("abort") != null,
            "download session exposes explicit abort"
        );
        require(
            DownloadSession.class.getMethod("cancelled") != null,
            "download session exposes cancellation state"
        );
        require(
            FFL.class.getMethod("downloadStream", String.class) != null,
            "FFL exposes binary download streaming"
        );

        Path completedPath = Files.createTempFile("ffl-java-completed-", ".bin");
        Files.writeString(completedPath, "completed");
        APEProcessException failure = new APEProcessException(new APEProcessResult(
            List.of("download", "https://example.test/file"),
            11,
            "Downloaded: file.bin",
            "Progress 100%\nTerminating on uncaught SIGSEGV"
        ));
        APEProcessResult completed = DownloadSession.completedDownloadProcess(
            failure,
            completedPath
        );
        require(completed.returnCode() == 0, "completed download process");
        Files.deleteIfExists(completedPath);
    }

    private static void testClosingRunningDownloadCancelsIt() throws Exception {
        Process child = startChild(Sleeper.class);

        try {
            DownloadSession session = new DownloadSession(
                newSession(child, true),
                null,
                Path.of(".")
            );

            require(session.running(), "download session starts running");
            session.close();
            require(session.cancelled(), "closing a running download cancels it");
            try {
                session.waitFor();
                throw new AssertionError("cancelled download must not resolve successfully");
            } catch (CancellationException expected) {
                // Expected: closing an active semantic download is an abort.
            }
        } finally {
            child.destroyForcibly();
            child.waitFor();
        }
    }

    private static void testStreamedDownloadRequiresStdoutConsumptionBeforeWaiting()
        throws Exception {
        Process child = startChild(LargeStdout.class);

        try {
            ProcessSession session = newSession(child, false);

            try {
                session.waitFor(1.0);
                throw new AssertionError("unconsumed streamed stdout must not wait");
            } catch (IllegalStateException expected) {
                require(
                    expected.getMessage().contains("streamed stdout must be consumed"),
                    "streamed stdout wait error"
                );
            } finally {
                session.stop();
            }
        } finally {
            child.destroyForcibly();
            child.waitFor();
        }
    }

    public static final class Sleeper {
        private Sleeper() {}

        public static void main(String[] args) throws InterruptedException {
            Thread.sleep(Long.MAX_VALUE);
        }
    }

    public static final class LargeStdout {
        private LargeStdout() {}

        public static void main(String[] args) {
            System.out.writeBytes(new byte[2 * 1024 * 1024]);
            System.out.flush();
        }
    }

    private static void testExplicitDownloadPathWins() {
        Path cwd = Path.of("/tmp/ffl-java-test");
        APEProcessResult process = new APEProcessResult(
            List.of("download", "https://example.test/file"),
            0,
            "HTTP fallback\nDownloaded: ignored.zip\n",
            ""
        );
        DownloadResult result = FFLResultParser.parseDownload(
            process,
            Path.of("chosen.bin"),
            cwd
        );
        require(result.transferMode() == TransferMode.HTTP_FALLBACK, "fallback mode");
        require(cwd.resolve("chosen.bin").equals(result.outputPath()), "explicit output path");
    }

    private static void testKeygenParsing() throws Exception {
        Path directory = Files.createTempDirectory("ffl-java-keygen-unit-");
        Path privateKey = directory.resolve("alice.fflkey");
        Path publicKey = directory.resolve("alice.fflpub");
        Files.writeString(privateKey, "private");
        Files.writeString(publicKey, "public");
        APEProcessResult process = new APEProcessResult(
            List.of("keygen", "--name", "alice"),
            0,
            "Generated RSA-2048 keypair:\n"
                + "  Private key : " + privateKey + "\n"
                + "  Public key  : " + publicKey + "  ← share with sender\n",
            ""
        );
        KeygenResult result = FFLResultParser.parseKeygen(process, directory);
        require(privateKey.equals(result.privateKeyPath()), "private key path");
        require(publicKey.equals(result.publicKeyPath()), "public key path");
        Files.deleteIfExists(privateKey);
        Files.deleteIfExists(publicKey);
        Files.deleteIfExists(directory);
    }

    private static void testDeleteRecursivelyRemovesWholeTree() throws Exception {
        Path root = Files.createTempDirectory("ffl-java-cleanup");
        Path nested = Files.createDirectories(root.resolve("a").resolve("b"));
        Files.writeString(nested.resolve("file.txt"), "x");
        Files.writeString(root.resolve("top.txt"), "y");

        TemporaryFiles.deleteRecursively(root);

        require(!Files.exists(root), "temporary tree removed including nested files");
    }

    private static void testDeleteRecursivelyIgnoresMissingPath() throws Exception {
        Path missing = Files.createTempDirectory("ffl-java-cleanup").resolve("never-created");

        TemporaryFiles.deleteRecursively(missing);

        require(!Files.exists(missing), "missing path stays missing without error");
    }

    private static void requireOptionalFlag(Object value, String label) {
        require(value instanceof OptionalValue<?>, label + " optional value");
        OptionalValue<?> optional = (OptionalValue<?>) value;
        require(optional.present() && !optional.hasValue(), label + " flag state");
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
