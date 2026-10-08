# Engine difficulty benchmark

This benchmark is intentionally kept outside `app/src/test` because a full run can take many minutes. It compares two product difficulty budgets from the same fixed SFEN starts, swaps sides, validates every selected move, and records mate, repetition, sustained-material adjudication, and ply-cap separately.

To run one comparison, temporarily copy `EngineDifficultyBenchmarkTest.kt` to:

`app/src/test/java/com/melapplyworks/g002shogi/analysis/local/EngineDifficultyBenchmarkTest.kt`

Then run, for example:

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'
$env:G002_BENCHMARK_CASES='10'
$env:G002_BENCHMARK_MAX_PLIES='36'
$env:G002_BENCHMARK_HIGH='STANDARD'
$env:G002_BENCHMARK_LOW='BEGINNER'
$env:G002_BENCHMARK_REPORT='C:\\absolute\\path\\outside-the-repository\\difficulty-report.tsv'
.\gradlew.bat :app:testDebugUnitTest --tests "com.melapplyworks.g002shogi.analysis.local.EngineDifficultyBenchmarkTest" --rerun-tasks
```

`G002_BENCHMARK_REPORT` is optional. When set to an absolute path outside this repository, the benchmark appends one line after each completed game. An interrupted run can therefore be reported as preliminary evidence for the completed games only; it is not a complete comparison. Use `STRONG` and `STANDARD` for the other adjacent comparison. Remove the temporary test-source copy after recording the XML result. A ply cap is not a draw or a normal game conclusion, and an adjudicated result is not a checkmate.

## External reference diagnostic

`ExternalEngineCalibrationBenchmarkTest.kt` is also kept outside the normal test source set. It accepts an already-audited local USI executable through `G002_REFERENCE_ENGINE`; the executable is not copied, linked, or bundled. The diagnostic uses no opening book, one thread, a 64 MB hash, equal per-move time, and swaps G002's side. Its result is a relative engineering diagnostic only; it is not a human dan/kyu conversion.
