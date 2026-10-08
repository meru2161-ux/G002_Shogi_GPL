# G002 将棋: Native Engine Bring-up PoC v1

実施日: 2026-09-27
対象: `C:\Users\Mel7Dev\Documents\MelMiniGameFactory\G002_Shogi`
結果分類: **C — Windows x64のsource buildは成功、実行環境とruntime dataライセンスが未解決のため正式採用しない**

## 1. スコープと不変条件

Step 6はSunfish4をG002へ正式採用・UI統合・APK同梱する工程ではない。固定した上流sourceがnative buildできるか、実行可能ならUSIとMultiPVを実測できるか、評価関数・定跡データを再配布できるかを検証した。

- 対象source: Sunfish4 `v0.1.3` / `91cff975e68ccfcb89c13d4433bf0620a83f3adb`
- production source、`MainActivity`、`ShogiAnalysisEngine`、Mock解析、Gradle native設定、JNIは変更していない。
- 上流source、release archive、展開物、ローカルnative生成物はすべて `poc/` 下に隔離し、Gitへ含めない。
- Windowsのアプリケーション制御ポリシーは変更・解除・回避していない。

## 2. 開始時ベースラインと環境

開始時のG002はcommit `969a06c`、branch `main`、Git cleanだった。既存JUnitは36件すべてPASSで、Step 6でproduction Kotlinコードには差分を入れていない。

|項目|確認結果|
|---|---|
|Android SDK|`C:\\Users\\Mel7Dev\\AppData\\Local\\Android\\Sdk`|
|Gradle SDK設定|compile/target SDK 36、min SDK 26|
|JDK|Android Studio JBR OpenJDK 21.0.10|
|Windows C++|Visual Studio 2022 Developer Command Prompt 17.14.41、MSVC 14.44.35207 x64|
|CMake|未導入（Android SDK・Android関連パスに`cmake.exe`なし）|
|Android NDK|未導入（SDKに`ndk`/`ndk-bundle`なし）|
|Android SDK Manager CLI|未検出（`sdkmanager.bat`なし）|
|Cドライブ空き|64.51 GiB|
|追加toolchain|なし（追加容量0 bytes）|

Android Studioが提供するside-by-side NDK/CMakeの追加は許可範囲だったが、この環境にはCLI SDK Managerがなく、nativeアプリ画面にも自動操作経路を確認できなかった。既存SDK・Visual Studio・PATHを変更せず、未導入のままとした。

## 3. Sunfish4 sourceとPC build

上流sourceにはsubmoduleがなく、USI targetのCMake定義にも`find_package`、`FetchContent`、`ExternalProject`はない。CMakeが未導入のため公式CMake generator自体は実行できなかったが、`src/usi/CMakeLists.txt`が定義する同じUSI executableと依存static libraryのC++ sourceを、既存MSVCで直接コンパイルした。

|項目|結果|
|---|---|
|Windows x64 build|**成功**、exit code 0|
|compiler options|`/std:c++14 /utf-8 /EHsc /O2`|
|source変更|なし|
|生成物|隔離済み `poc/sunfish4-v0.1.3/out/g002-poc-win64-manual/sunfish_usi.exe`|
|build警告|上流sourceのMSVC narrowing/boolean comparison警告。build errorなし|

初回の直接buildはUTF-8入力指定を省いたため、日本語文字列を持つ上流`KifuWriter.cpp`がMSVC既定コードページで失敗した。`/utf-8`を追加した再実行で成功した。生成時に誤ってG002直下へ出た36個の`.obj`は時刻・場所を確認して削除済みであり、今後の混入防止として`*.obj`を`.gitignore`へ追加した。

## 4. 実USI、MultiPV、停止、連続解析

公式v0.1.3 Windows release assetとローカル生成binaryの両方を、同一環境で起動しようとした。いずれもWindowsが次の理由で拒否した。

> アプリケーション制御ポリシーによってこのファイルがブロックされました。

これはUSI parser、Sunfish source、評価データ、G002のコードからのエラーではなく、process開始前のOSポリシー拒否である。安全上、`Unblock-File`、ポリシー変更、代替実行回避は行っていない。

そのため、以下はすべて**未実測**である。

|検証|状態|理由|
|---|---|---|
|`usi` → `usiok`|未実測|binaryを起動できない|
|`isready` → `readyok`|未実測|同上|
|`setoption`|未実測|同上|
|`position` / `go`|未実測|同上|
|`info depth` / `score cp` / `score mate` / `pv` / `bestmove`|未実測|同上|
|`MultiPV=3`（rank 1–3）|未実測|同上|
|`stop` → `bestmove`|未実測|同上|
|局面A→B→C連続解析|未実測|同上|
|起動、ready、解析時間、RAM|未測定|同上|

固定sourceの静的確認（Step 5）では、`MultiPV` はmin 1 / max 10のUSI spin optionであり、`info`はdepth、score、multipv、pvを、探索完了時は`bestmove`を出力する。しかし、これはruntime成功の証明ではない。実出力が得られないため、実USI fixtureと追加のparser回帰テストも作成していない。既存の`UsiAnalysisPoCTest` 7件は維持する。

## 5. runtime dataとライセンスGate

公式release asset `sunfish4_v0.1.3_win64.zip` を調査専用として取得し、production APK・AAB・Gitには含めなかった。

|項目|確認結果|
|---|---|
|配布元|Sunfish4公式GitHub release v0.1.3|
|archive size|33,474,725 bytes|
|SHA-256|`7C03F2313FE70FFCE79D38E43F5E71F217C6FF64AE34DB77F89FC64D52303DBF`|
|engine source|MIT License、Copyright (c) 2015 Ryosuke Kubo|
|`eval.bin`|47,124,355 bytes。release asset内、個別LICENSE/作者/再配布条件を確認できず|
|`book.bin`|2,881,571 bytes。release asset内、個別LICENSE/作者/再配布条件を確認できず|
|asset内のLICENSE/README/COPYING/NOTICE/CREDITS|該当ファイルなし|
|source tree内の`eval.bin`/`book.bin`|なし|

MITはengine sourceに対する確認結果であり、別配布の評価関数・定跡データの利用条件を自動的に与えるものではない。一次資料（repository LICENSE、v0.1.3 release、asset内容）から、各データの作成者、改変可否、商用利用、APK/AAB同梱、クレジット要件を確認できなかった。

**結論: `eval.bin` と `book.bin` はGoogle Playを含む再配布可否が未確認である。G002への同梱・Git追加・正式採用は不可。** ローカル調査利用と配布判断は別である。

一次資料:

- <https://github.com/sunfish-shogi/sunfish4/blob/master/LICENSE.txt>
- <https://github.com/sunfish-shogi/sunfish4/releases/tag/v0.1.3>

## 6. Android ARM64 / JNI

|項目|状態|根拠|
|---|---|---|
|side-by-side NDK|未導入|SDKにNDKなし、SDK Manager CLIなし|
|side-by-side CMake|未導入|SDKにCMakeなし|
|`arm64-v8a` cross build|未実施|クロスtoolchain未導入|
|portability診断|未実施|ARM64 compilerなし|
|JNI bridge|未実施|ARM64 build成功後のみ行う方針|
|Android native APK増分|0 bytes|`.so`、native asset、Gradle native設定を追加していない|

Android公式が案内するCMake + shared library + JNIは将来の統合候補だが、NDK/CMake追加とruntime dataの配布許諾を解決する前に実装しない。

## 7. G002への影響と次判断

- `ShogiAnalysisEngine` 境界、Mock解析、既存USI parser/adapter、UIは未変更である。
- 実出力がないため、`AnalysisResult` / `CandidateMove` / `VariationLine`への「実Sunfish4データ」変換は**未実証**。Step 5の合成USI fixtureによる変換テストは有効なままである。
- Sunfish4の正式採用は行わない。

Step 7へ進む前に必要な判断材料:

1. Windows実行ポリシーを**正規の管理手段**で利用可能にできるか（回避策は使用しない）。
2. official Android SDK Managerから固定versionのside-by-side NDK/CMakeを追加できるか。導入前にpackage名、version、容量、保存先を記録する。
3. `eval.bin` と `book.bin`の権利者または公式配布元から、改変・商用・APK/AAB・Google Play再配布・表示義務を一次資料で得られるか。

上記のうちデータライセンスが未解決なら、Sunfish4は技術PoCのまま保持し、G002へ取り込まない。
