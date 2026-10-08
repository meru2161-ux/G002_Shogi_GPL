# G002 将棋: 実エンジン接続 PoC v1

実施日: 2026-09-27
対象: `C:\Users\Mel7Dev\Documents\MelMiniGameFactory\G002_Shogi`
結果: **PoC一部成功**（USI parser / G002モデル変換は成功、実バイナリ・Android native/JNIは環境不足のためPending）

## 1. スコープ

Step 5ではSunfish4を正式採用・アプリ同梱せず、G002がUSIの候補手・評価・PVを既存モデルへ安全に変換できるかを実証した。

- やねうら王、GPLコード、評価関数、定跡、JNI、NDK/CMake、Gradle native設定は導入していない。
- UIからPoCを呼び出していない。`ShogiAnalysisEngine` とMock解析は既存のままである。
- 外部ソースは `poc/sunfish4-v0.1.3/` にのみ隔離し、`.gitignore`で除外した。上流コード・生成物はG002のコミットに含めない。

## 2. 対象エンジンとライセンス監査

|項目|確認結果|
|---|---|
|対象|Sunfish4|
|公式repository|<https://github.com/sunfish-shogi/sunfish4>|
|固定tag / commit|`v0.1.3` / `91cff975e68ccfcb89c13d4433bf0620a83f3adb`|
|エンジン本体LICENSE|MIT License、Copyright (c) 2015 Ryosuke Kubo|
|submodule|なし（`.gitmodules` 不在、`git submodule status` は空）|
|CMake external dependency|`src/usi/CMakeLists.txt` に `find_package` / `FetchContent` / `ExternalProject` はなし|
|source同梱データ|`res/strings/*` と設定ファイルのみ。`eval.bin` / `book.bin` は含まれない|
|公式Windows release|v0.1.3 asset `sunfish4_v0.1.3_win64.zip`、公開API上のサイズ 33,474,725 bytes。**未ダウンロード**|

公式release assetの中に含まれる評価・定跡データの個別ライセンスを、このPoCでは確認できなかった。そのためassetは取得・展開・実行・同梱していない。MIT確認済みのsourceだけを浅いcloneで検査した。

一次資料:

- MIT LICENSE: <https://github.com/sunfish-shogi/sunfish4/blob/master/LICENSE.txt>
- Release v0.1.3: <https://github.com/sunfish-shogi/sunfish4/releases/tag/v0.1.3>

## 3. Sunfish4 USI能力

以下は固定source `91cff97` の `src/usi/client/UsiClient.cpp` を静的に確認した結果であり、今回実バイナリから受信した結果ではない。

|要求|結果|根拠|
|---|---|---|
|`usi` / `usiok`|source確認|idとoptionを出力し`usiok`を送信|
|`isready`|source確認|USI clientのcommand dispatcherに実装あり|
|`setoption`|source確認|`UseBook`、`Snappy`、`MarginMs`、`Threads`、`MaxDepth`、`MultiPV`を処理|
|`position` / `go` / `quit`|source確認|USI client command dispatcherに実装あり|
|`stop`|source確認のみ|`stop`分岐はあるが、実行時の中断タイミングは未検証|
|`info`|source確認|depth / nodes / time / score / multipv / pv を出力|
|`score cp`|source確認|通常評価で`score cp`を出力|
|`score mate`|source確認|mate域の評価で`score mate`を出力|
|`pv`|source確認|`pv.toStringSFEN()`を出力。v0.1.3はinfo内PVの位置を修正したrelease|
|`bestmove`|source確認|探索完了時に`bestmove`（必要ならponder）を出力|
|MultiPV|**source確認: 対応**|`MultiPV`はspin option、min 1 / max 10。G002の3候補要求を設定可能|

結論: Sunfish4 sourceはMultiPV=3に必要なUSI出力を実装している。これは実バイナリ通信の成功証明ではないため、runtime確認はPendingのままである。

## 4. 実装した隔離PoC

追加ファイル:

- `app/src/main/java/com/melapplyworks/g002shogi/analysis/poc/UsiAnalysisPoC.kt`
- `app/src/test/java/com/melapplyworks/g002shogi/analysis/poc/UsiAnalysisPoCTest.kt`

### parser / session

`UsiAnalysisParser` は、次のUSI行をUI層へ渡さずに解析する。

- `info multipv`
- `score cp` / `score mate`
- `depth` / `nodes` / `time`
- `pv`
- `bestmove` / `ponder`

不明行、空行、数値不正、欠落した`info`は例外にせず、認識できた安全な値だけを返す。`UsiAnalysisSession` はrankごとに最新のPVを保存し、MultiPV順へ整列する。

### G002モデル変換

`UsiAnalysisAdapter` は `UsiAnalysisEnvelope.positionId` が現在局面IDと一致しない場合、`AnalysisResult`を返さない。古い解析結果が別局面のUIへ混入することを防ぐ。

PV内のUSI手は既存 `ShogiRules.legalMoves` で検証してから `Move` に変換し、順に局面を進める。不正手を見つけた場合は後続PVを解釈せず打ち切る。変換後は既存の `CandidateMove`、`VariationLine`、`AnalysisResult` を用いるため、UIは特定エンジン名を知らない。

このPoCは `isSample = true` を設定し、説明文も「USI出力を変換した候補」であることを明示する。評価値から手の意味・狙いを生成していない。

## 5. テスト結果

新規 `UsiAnalysisPoCTest` は7件。

1. `score cp`、depth、nodes、time、PVの解析
2. `score mate`、`bestmove`、ponderの解析
3. MultiPVの順位整列と最新infoへの更新
4. 不完全info・未知行・空行での無例外処理
5. 有効な3候補PVから既存 `AnalysisResult` への変換
6. 古い局面IDの結果を拒否
7. 不正PV接尾辞を安全に打ち切る

`./gradlew.bat :app:testDebugUnitTest :app:assembleDebug --offline --no-daemon --console=plain` をAndroid Studio同梱JBRをそのプロセスだけに指定して実行した。

|suite|tests|failures|errors|
|---|---:|---:|---:|
|UsiAnalysisPoCTest|7|0|0|
|PlayerMoveCoachTest|7|0|0|
|ShogiRulesTest|22|0|0|
|合計|36|0|0|

結果: `testDebugUnitTest` PASS、`assembleDebug` PASS。

## 6. Native / Android実証状況

|項目|状態|事実|
|---|---|---|
|Windows x64 C++ compiler|利用可能|既存Visual Studio 2022のMSVC 14.44とNMakeを確認|
|CMake|**未導入**|PATH、Visual Studio、Android Studio、Android SDKを確認したが`cmake.exe`なし|
|Windows USIバイナリ|Pending|CMake未導入のためsourceビルド不可。評価データ条件不明のreleaseも未使用|
|Android NDK|未導入|Android SDKに`ndk` / `ndk-bundle`なし|
|Android CMake|未導入|Android SDKに`cmake`なし|
|arm64-v8a build|Pending|NDK/CMake未導入のため未実施|
|JNI往復|Pending|native libraryを追加していないため未実施|
|runtime stop/cancel|Pending|source上の`stop`分岐のみ確認。実バイナリ通信は未実施|
|連続解析|Pending|実バイナリ未生成のため未実施。sessionの順位更新はUnit Test済み|

追加SDK・CMake・System Image・評価関数のダウンロードは、今回の指示に従い行っていない。したがってAndroid実機性能、温度、電池、arm64 RAM、JNI native crashの有無は未測定である。

## 7. 性能とAPK

- Windows実バイナリ: 未生成のため起動時間、isready時間、解析時間、CPU、RAM、バイナリサイズは未測定。
- Android native: 未実施のため温度、電池、arm64性能は未測定。
- Debug APK: `app-debug.apk` は 14,439,018 bytes。native `.so`を追加していないため、今回のPoCによるnative APKサイズ増は **0 bytes**。

## 8. 正式採用の判断

Sunfish4は**正式採用していない**。今回確定したのは「G002はSunfish4が出す形式のUSI `info` / `score` / `pv` / `multipv` / `bestmove` を、UIへ具体名を漏らさず既存モデルへ変換できる」という純Kotlin PoCである。

正式採用の前に必要なもの:

1. CMake/NDKを追加することの承認と、固定source revisionでのWindows/arm64実バイナリ実証。
2. 使用する評価関数・定跡データの正確な出所、ライセンス、容量、再配布可否。
3. MultiPV=3、stop、局面A→B→C連続解析、JNI例外、ARM64実機性能の計測。
4. APK Analyzer、AAB、ライセンス表示を含む配布確認。

## 9. Step 6への推奨判断

次工程は「Sunfish4を正式採用する」ではなく、**CMake/NDKを追加してよいか、評価関数を何にするかを先に決める判断工程**とする。これらが未承認のままrelease binaryや評価データを取得し、Androidへ組み込むことはしない。
