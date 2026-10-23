# The generated layer is APEBind output

`src/main/java/apebind/generated/ffl/` is the unmodified output of APEBind's Java backend
(`nuwainfo/apebind`, main after the 2026-10-20 Java backend changes: `APERuntime`,
`Processes`, line/prompt listeners, lazy JSON-file results, `Json.stringify`, instance
bindings, Android-safe APIs). Do not edit it by hand; regenerate it.

## Regenerating

```powershell
cd C:\Users\leo\work2\github\apebind
python -m apebind generate C:\Users\leo\work2\github\ffl-java\binding\ffl.apebind.yaml `
    --ape C:\Users\leo\work2\github\ffl-java\src\main\resources\apebind\ffl.com `
    --lang java -o build\gen-ffl
Copy-Item build\gen-ffl\src\main\java\apebind\generated\ffl\*.java `
    C:\Users\leo\work2\github\ffl-java\src\main\java\apebind\generated\ffl\
```

`generate` only copies the APE into the output's resources; it never runs it. Take the
Java files only: the generated `pom.xml`, `README.md` and resources belong to the
generated project, not to this one. The generator writes CRLF on Windows; the files in
this repository are kept as LF.

Three generated classes are unused by FFL and stay for fidelity with the generator:
`APEEvent`, `APEEventException`, `JSONLFileEventChannel` (the JSONL event pipe; the FFL
schema declares no `events`, so no code path reaches them).

## What ffl-java adds on top

Everything FFL-specific lives in `src/main/java/io/fastfilelink/ffl/` and only uses the
generated layer's public API: `FFLBinding.defaults()` / `create(APERuntime)`, the
operation methods, `ProcessSession` (`onStdoutLine`, `onStderrLine`, `onStdoutPrompt`,
lazy `result()`, `stop`, `waitFor`), `Json.parse` / `Json.stringify`, `OptionalValue`.
The schema (`binding/ffl.apebind.yaml`, `binding/ffl.commands.yaml`) is the only input
that changes when the core gains flags or commands; `binding/ffl.hook-events.md`
documents the hook event wire contract the handwritten layer relies on.

## Updating the core

1. Replace `src/main/resources/apebind/ffl.com` with the new APE (the Android app ships
   the same file as `jniLibs/<abi>/libffl.so`).
2. Diff the core's `--help` output against `binding/ffl.apebind.yaml`; add new flags or
   commands to the yaml (and `ffl.commands.yaml` for hidden commands).
3. Regenerate as above, then `scripts/test.ps1 unit` and `scripts/test.ps1 real`
   (the real suite creates real shares; run it on Windows).
4. For Android, rebuild the slim JAR with `scripts/build-android.ps1`.
