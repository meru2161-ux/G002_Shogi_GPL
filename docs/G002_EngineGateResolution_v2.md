# G002 将棋 — Step 6 Engine Blocker Resolution v2

開始: 2026-09-28 00:48 JST
対象: `C:\Users\Mel7Dev\Documents\MelMiniGameFactory\G002_Shogi` のみ
目的: Google Play向けに安全・合法・現実的なローカル将棋解析エンジン候補を実証する。G002アプリへのProduction統合、JNI、Gradle native設定、APK/AABへのengine同梱は行わない。

開始時見積り: 短い場合60分、標準1〜3時間、長い場合約4時間、標準終了目安03:48 JST。

## 1. 結論

|候補|分類|結論|
|---|---|---|
|Sunfish4 v0.1.3|D — ENGINE NOT SUITABLE|Windows App Controlで実行不能。必須`eval.bin`の配布ライセンスも公開一次資料で確認不能。|
|YaneuraOu MATERIAL Lv9|B — TECHNICALLY READY / LICENSE BLOCKED|Windows x64参照実行に加え、Android x86_64 runtimeとARM64 cross build、USI、定跡なし、MultiPV=3、score/PV/bestmove、stopを実測成功。外部評価データ不要。ただし一般アマ五段級の独立較正は未達で、GPLv3のProduction配布形態も未決定のため統合不可。|
|gameboy-shogi|D — ENGINE NOT SUITABLE|MITかつhost USIは確認できるが、MultiPV、Android ARM64、研究用棋力の一次根拠がない。G002の候補3手提示には採用しない。|

**五段級目標に対する正式な技術PoC候補はYaneuraOu MATERIAL Lv9である。ただしProduction採用でも段位達成判定でもない。Lv1は外部データ不要とAndroid実行経路を確認する基準実装として記録を残す。**

## 2. YaneuraOu固定対象と取得範囲

|項目|確認結果|
|---|---|
|公式repository|`https://github.com/yaneurao/YaneuraOu`|
|固定commit|`c1b80eaa09fe13d5f12b1599d1ae4d53c224de30`|
|commit日時|2026-09-14 18:12:13 +09:00|
|ライセンス|GPL-3.0（repository LICENSEおよびREADME）|
|外部source|`poc/yaneuraou/` に隔離。`.gitignore`対象でGitへ含めない。|

公式READMEはUSI、MultiPV、ARMを機能として明記する。公式Android workflowはNDK r23bと`script/android_build.sh`を用いる。workflowの現在の実行matrixではNNUEだけが有効で、MATERIALはコメントアウトされているため、上流CIのMATERIAL成功は主張しない。一方、このG002隔離PoCでは下記のARM64 cross buildを実測した。

一次資料:

- <https://github.com/yaneurao/YaneuraOu>
- <https://github.com/yaneurao/YaneuraOu/blob/master/.github/workflows/ndk.yml>
- <https://github.com/yaneurao/YaneuraOu/wiki/%E3%82%84%E3%81%AD%E3%81%86%E3%82%89%E7%8E%8B%E3%81%AE%E3%83%93%E3%83%AB%E3%83%89%E6%89%8B%E9%A0%86>

## 3. MATERIALが外部評価データを必要としない根拠

【確認済み事実】

- 公式Visual Studio構成`Release-Material|x64`は`YANEURAOU_ENGINE_MATERIAL`と`EVAL_EXP=999`をdefineする。
- `source/eval/material/evaluate_material.cpp`の`load_eval()`はMATERIAL全レベル共通の空実装で、Lv1～Lv9の評価処理はsourceへ実装されている。
- 実行に使用した`build/Material`には`YaneuraOu-Material.exe`とPDBのみで、`eval.bin`、`nn.bin`、定跡dataは置かなかった。
- 生成binary: 1,140,736 bytes、SHA-256 `A79EBB1D797E8E18A5033F39BC9DC52FF64777EC6CC7743D2F0C3FDC7FBFC99E`。

従って、MATERIAL Lv1とLv9はSunfish4の`eval.bin`と同種の外部評価dataを配布しない。Lv9についても評価fileを置かないAndroid runtimeで`readyok`と探索を実測した。これは評価の強さやGPL義務を軽くするものではない。

## 4. Windows x64 PoC

### build

既存のVisual Studio 2022 MSBuild 17.14.60で、公式`YaneuraOu.vcxproj`の既存構成を変更せず実行した。

```text
MSBuild YaneuraOu.vcxproj /m /t:Build /p:Configuration=Release-Material /p:Platform=x64
```

結果: 成功。G002本体、Gradle、SDK、JNI、source codeには変更なし。

### USI handshake と定跡なし構成

実行時に次を設定した。

```text
setoption name USI_Hash value 16
setoption name Threads value 1
setoption name MultiPV value 3
setoption name USI_OwnBook value false
setoption name BookFile value no_book
setoption name MinimumThinkingTime value 1
```

【Machine結果】`usiok`、`readyok`を確認。USI optionは`MultiPV` min 1 / max 600、`Threads`、`USI_Hash`、`USI_OwnBook`、`BookFile`を出力した。`USI_OwnBook=false`および`BookFile=no_book`を`isready`前に設定した実行では、book read/errorを出さず`readyok`になった。

定跡を既定設定のままにした対照試験では、存在しない`book/standard_book.db`についてerrorを出した後でも`readyok`を返した。よって、**定跡dataは解析に必須ではなく、G002 PoCでは明示的に使わない。**

### MultiPV=3 / score / PV / bestmove

```text
position startpos
go depth 3
```

【Machine結果】以下を実測した。

```text
info depth 3 ... multipv 1 score cp 0 ... pv 1g1f 1c1d 2g2f
info depth 3 ... multipv 2 score cp 0 ... pv 2g2f 1c1d 1g1f
info depth 3 ... multipv 3 score cp 0 ... pv 3g3f 1c1d 1g1f
bestmove 1g1f ponder 1c1d
```

この深さ3・1msの結果はUSI機能確認用であり、棋力・解析速度・発熱の評価ではない。

### stop

`go movetime 3000`開始後500msで`stop`を送った。標準出力を同時に回収してパイプ詰まりを防いだ結果、MultiPV 1〜3の`info`（depth 12〜14、score/PV付き）後に`bestmove 1g1f ponder 8b5b`を確認した。`stop`応答成功である。

## 5. Windows実行Blockの切り分け

|binary|結果|根拠|
|---|---|---|
|Sunfish4公式v0.1.3とローカルMSVC build|実行不可|Windows Code Integrity Operationalの3033/3077。Enterprise/App Control policy ID `{0283ac0f-fff1-49ae-ada1-8a933130cad6}`。|
|今回のローカルYaneuraOu MATERIAL build|実行成功|exit 0、`usiok`、`readyok`、探索、`bestmove`を実測。|

これはSunfish4のUSI実装・DLL・architecture・Mark-of-the-Webが唯一の原因ではなく、このPCのApp Control policyがbinaryごとに判定していることを示す。Defender、SmartScreen、AppLocker/WDAC、ExecutionPolicy、registry、除外登録、ポリシーは変更していない。

## 6. G002 parser / 初期局面の修正

実YaneuraOu MATERIAL出力を既存`UsiAnalysisParser`と`UsiAnalysisAdapter`へそのまま通すfixtureを追加した。

検証中に、Mock初期局面の飛車・角が標準将棋初期配置と左右逆であることを発見した。実PV `1g1f 8b5b`の2手目をG002が不正手として捨てていた原因はこの初期配置誤りだった。

最小修正:

- 後手: 2二角、8二飛
- 先手: 2八飛、8八角

回帰テストは正しい4駒の位置と、実USI fixtureの3候補、score、PV変換を確認する。UI・engine abstraction・Production native codeは変更していない。

## 7. GPLv3の配布Gate

【確認済み事実】YaneuraOu repositoryはGPLv3である。GPLv3はbinaryを配布する場合、license/noticeを維持し、Corresponding Sourceを提供する条件を定める。改変版または結合された著作物として配布する形態では、GPLv3 Section 5/6の条件が問題になる。

|統合方式|現時点の判断|
|---|---|
|JNIでG002 processへ`lib*.so`としてリンク|GPLの結合著作物に該当する可能性が高い。G002本体・bridgeを含む対応ソース公開が必要になる可能性があるため、法的確認なしに採用しない。|
|static / dynamic link|staticかdynamicかだけで義務が消えるとは扱わない。|
|APK内の別native executable + IPC / USI|実装上の分離はできても、GPL上G002本体を非GPLのまま配布できるとは本資料では結論づけない。法的確認が必要。|
|G002本体をGPLへ変更|実施しない。ユーザーの明示判断が必要。|

【法的確認推奨】GPLの適用範囲、Corresponding Sourceの具体的提供方法、Google Playでの表示・入手導線は、公開前にライセンス実務の確認が必要である。これは「GPLだから利用不能」という結論ではないが、G002の現行ライセンス方針を勝手に変更せずにProduction統合できる根拠も未確定である。

一次資料:

- <https://github.com/yaneurao/YaneuraOu/blob/master/LICENSE>
- <https://www.gnu.org/licenses/gpl-3.0.html>
- <https://www.gnu.org/licenses/gpl-faq.html>

## 8. Android ARM64 / NDK / CMake

【確認済み事実】上流`script/jni/Android.mk`には`YANEURAOU_ENGINE_MATERIAL`のbuild例があり、`arm64-v8a`に`IS_64BIT`と`USE_NEON`を指定する。`Application.mk`は`APP_ABI := all`である。したがってsource上はARM64 Androidを対象にしている。

【確認済み事実】ユーザー許可後、Google公式repository metadataで`ndk;23.1.7779620`（r23b Windows archive: 788,638,042 bytes / SHA-1 `6e3fb50022c611a2b13d02f5de5c21cc7206a298`）を照合した。SDK Manager CLIがこのPCに無いため、検証済みのGoogle公式archiveを`C:\Users\Mel7Dev\AppData\Local\Android\Sdk\ndk\23.1.7779620`へ配置した。展開済みサイズは2,594,612,807 bytesである。

【確認済み事実】G002のGit ignore対象`poc/yaneuraou/build/android-arm64-material/`だけを出力先にして、`ndk-build.cmd`へ`APP_ABI=arm64-v8a`および`YANEURAOU_EDITION=YANEURAOU_ENGINE_MATERIAL`を渡してcross buildした。生成物は`libs/arm64-v8a/YaneuraOu_MaterialLv1_arm64-v8a`（1,077,520 bytes、SHA-256 `4ECC9DE5ABEB4BF96BE0CCCF0996E260FBEA19843E296C1E0DC4F3F634697224`）である。NDK付属`llvm-readelf`で`ELF64`、`Machine: AArch64`、`Type: DYN`、`FLAGS_1: NOW PIE`、依存`libc.so`/`libm.so`/`libdl.so`を確認した。

【確認済み事実】同じ固定sourceとNDK r23bを使い、Git ignore対象`poc/yaneuraou/build/android-x86_64-material/`へ`APP_ABI=x86_64`でMATERIAL Lv1をcross buildした。生成物は`libs/x86_64/YaneuraOu_MaterialLv1_x86_64`（1,280,856 bytes、SHA-256 `CECF233AE3C7EC1C3AE59D9E885BF3A24FB0FB41297244433231682B29E65982`）である。`llvm-readelf`で`ELF64`、`Machine: Advanced Micro Devices X86-64`、`Type: DYN`、`FLAGS_1: NOW PIE`、依存`libc.so`/`libm.so`/`libdl.so`を確認した。

【確認済み事実】既存のG002専用AVD `G002_VisualGate_API35`（API 35、`sdk_gphone64_x86_64`）だけを`emulator-5580`として起動し、serialを明示したADBで上記binaryを`/data/local/tmp/g002_yaneuraou_material`へ置いて実行した。APK/AABやアプリsourceへは組み込んでいない。`usiok`、`readyok`、MultiPV=3の`score`/`pv`、`bestmove`を確認し、`go infinite`開始約1秒後の`stop`に応答した。停止時はdepth 15、1,366,132 nodes、1,924,129 nps、710msを記録した。定跡は`USI_OwnBook=false`、Threads=1、Hash=16MBである。

【確認済み事実】五段級目標の候補として`MATERIAL_LEVEL=9`も同じ固定sourceとNDK r23bで別出力へbuildした。x86_64生成物は`poc/build/android-x86_64-material-lv9/libs/x86_64/YaneuraOu_MaterialLv9_x86_64`（1,256,328 bytes、SHA-256 `0B46C10AAECC5446B4840EDDF1DECB795C660CD6EFB5F0206F8F4B616DCD5AAE`）、ARM64生成物は`poc/build/android-arm64-material-lv9/libs/arm64-v8a/YaneuraOu_MaterialLv9_arm64-v8a`（1,092,248 bytes、SHA-256 `9585B1F7E892B780464AC00B165B1B93B072127C4D4D05E51C869372A68A95B6`）である。両方とも`ELF64`、`DYN`、`NOW PIE`で、依存は`libc.so`/`libm.so`/`libdl.so`だけ。Machineはそれぞれx86-64とAArch64である。

【確認済み事実】Lv9 x86_64を同じ`emulator-5580`の`/data/local/tmp`だけで実行し、`id name YaneuraOu_MaterialLv9 MaterialLv9 9.80git 64SSE4.2`、`usiok`、`readyok`、MultiPV=3、score、PV、bestmove、stopを確認した。Threads=1、Hash=16MB、`USI_OwnBook=false`で約1秒探索し、depth 16、seldepth 32、1,182,211 nodes、1,152,252 nps、1,026ms、`bestmove 2g2f ponder 3c3d`を記録した。検証後は対象AVDだけを終了し、物理端末は使用していない。

【未確認】ARM64 binaryの実機/ARM64 emulator runtime、物理端末上の性能、RAM、発熱、電池消費は未測定であり、x86_64 emulator結果から推測しない。CMakeはこのPoCに不要であり導入していない。物理端末は使用していない。


## 9. Step 7 Gate

|条件|YaneuraOu MATERIAL Lv9|
|---|---|
|実binary起動|PASS（Windows x64参照 / Android x86_64）|
|USI handshake|PASS|
|MultiPV=3|PASS|
|score / PV / bestmove / stop|PASS|
|外部評価data|PASS（不要）|
|定跡なし|PASS|
|G002 parser変換|PASS|
|Android ARM64 Lv9 cross build / ELF検証|PASS（ARM64 runtimeは未確認）|
|Android x86_64 emulator runtime|PASS（USI / MultiPV=3 / score / PV / bestmove / stop）|
|Google Play配布条件|未達（GPL統合方式・対応ソース提供の法的Gate）|

**Gate分類: B — TECHNICALLY READY / LICENSE BLOCKED。Step 7へは進まない。**

## 10. 次の安全な工程

1. Production採用前に、GPLv3を受け入れる配布方針（G002本体/bridgeのライセンスとCorresponding Source提供）をユーザーと法的確認で決定する。
2. GPL方針を採用する場合は、統合境界とCorresponding Source提供範囲を決めてからProduction実装へ進み、その後にARM64実機またはARM64 emulatorでruntimeと端末性能を測定する。
3. GPL方針を採用しない場合は、Gokakuのmodel別商用再配布許諾、Android向け推論移植、独立棋力較正を満たせるかを次の研究Gateとする。Sunfish4データをProduction APK/AABへ入れない。

## 11. 非GPL候補 Sekirei v0.3.53 の確認（2026-09-29）

### 固定した対象

- 公式repository: <https://github.com/kent-tokyo/sekirei>
- tag: `v0.3.53`
- commit: `c3ac81d0f120ebd8a6368b1f59abf077e662ac76`
- 公式GitHub Releaseはsource archiveのみで、Windows / Android実行binaryは配布していない。

### ライセンス

【確認済み事実】`sekirei`と`sekirei-core`は`MIT OR Apache-2.0`。USI binaryの固定された実行時依存は、`rayon`、`lineprior`、`serde`、`serde_json`、`thiserror`とその推移依存であり、crates.ioの各固定versionを照合した結果、MIT / Apache-2.0 / Unlicense / Unicode-3.0の許容ライセンスだけだった。GPL依存は確認されなかった。

【確認済み事実】公開NNUE `weights/sekirei-nnue-v0.3.38.bin`（1,305,356 bytes、SHA-256 `154ad1e4c8335b5e51a87af946d50a6789fbaf2d2568af58e85c6431e9d3797a`）はCC BY 4.0。再配布時は `Sekirei project, Kentaro Tanabe, https://github.com/kent-tokyo/sekirei` の表示、ライセンスへのlink、変更の明示が必要。作者の`NNUE-LICENSE.md`は、権利のない第三者入力dataまで再許諾するものではないとも明記しているため、Production採用時はNOTICEとmodel cardを同梱し、由来の記録を維持する。

### 機能と棋力の根拠

【Source確認】USI、`MultiPV`（1～256）、`Hash`、`Threads`、`SpecTopN`、`EvalFile`、`UseBook`、`stop`、`bestmove`を実装している。評価fileなしでもmaterial評価で動く。

【重要な制限】公開NNUEのmodel cardは、現行material評価との局所比較で1秒設定1勝31敗、5秒設定2勝30敗と記録し、採用保留を推奨している。作者はFloodgate、人間、他engineとの一般的な棋力比較を主張していない。したがって、licenseが許容的であることだけを理由に、現在のG002より強い、学習指導に十分、YaneuraOu相当とは判定しない。

### Android統合

【公式資料】現在の正式interfaceはUSI binaryのみ。公式Android JNI / C ABI / FFIは無く、Android実機のRSS・電池・性能検証も無い。Androidでは、binary subprocessを独自に扱うか、`sekirei-core`用のRust FFI/JNI bridgeをG002側で新設する必要がある。

### Windows / ARM64実証結果

Rust公式stable 1.98.1最小toolchainをG002のGit ignore対象`poc/downloads/`へ隔離導入した。global PATH、Windows設定、Android SDK設定は変更していない。

しかし`rustc.exe`起動時、Windows Code Integrity Operational 3033 / 3077が、Rust標準library `std-44a584f44bc3dd65.dll`をEnterprise signing level未達として拒否した。Policy IDはSunfish4と同じ`{0283ac0f-fff1-49ae-ada1-8a933130cad6}`で、3118はSmart App Control block detailを記録した。待機したG002 PoCの`rustc` processだけを停止し、Defender、SmartScreen、Smart App Control、registry、除外、組織policyは変更していない。

このため、このPCではWindows USI runtime、MultiPV実出力、停止応答、G002 parser実fixture、Android ARM64 cross buildを実測できなかった。source上に明白なx86専用intrinsicは見つからず、既存NDK r23bのARM64 linkerも存在するが、cross-build成功を推測でPASSにはしない。

### Gate判定

|条件|Sekirei v0.3.53|
|---|---|
|source / 実行時依存license|PASS（許容ライセンス）|
|NNUE再配布条件|CONDITIONAL PASS（CC BY 4.0の表示等が必要）|
|USI / MultiPV / stop|SOURCE CONFIRMED、実binary未測定|
|Windows build / runtime|BLOCKED（Smart App Control / Code Integrity）|
|Android ARM64 cross build|未実証|
|Android JNI / FFI|上流提供なし|
|G002より強い根拠|未確認|

**Gate分類: C — LICENSE CANDIDATE / TECHNICAL AND STRENGTH UNVERIFIED。Production採用・製品AAB同梱は行わない。**

次工程は、Windows安全policyを弱めずに署名要件を満たすbuild環境を用意できた場合だけ、USI fixtureとARM64 PoCを再開する。別環境でbuildできても、G002採用前に現在のローカルengineとの同一局面・同一時間条件の比較と、実機性能測定が必要である。

## 12. 五段級目標に対する非GPL候補の再確認（2026-10-08）

一般アマ五段級を独立較正できる強さ、Android ARM64 CPU動作、候補3手・PV・score・stop、商用Google Play再配布条件の全てを満たす非GPL候補がないか、公開一次資料と固定sourceを再確認した。強さの主張が無いもの、model/dataの権利が不明なもの、Android実行経路が無いものは、licenseが許容的でもProduction候補へ昇格させない。

|候補|確認結果|Gate判定|
|---|---|---|
|Sekirei v0.3.5|本体はMIT OR Apache-2.0、USI/MultiPVあり。しかし公式releaseはproduction推奨NNUEなし、棋力主張なし、公式Android FFIなしと明記する。|C — license候補だが棋力・mobile未実証|
|Gokaku 2.3以降|sourceはMIT、USI/MultiPV、Linux ARM64 CPU Docker、9.7～72.3MBの公開modelを確認。固定source `2ae0b5551245c5d936b25ebf67f10ff4fba6bb33` はPython/Cython/libtorch前提で、Android/ExecuTorch経路は提供しない。公開model固有の商用再配布条件と、人間段位または独立engineへの棋力較正も明記されない。|C — 有望な研究候補だがmodel権利・Android・五段較正が未達|
|L-base-shogi|本体はMIT OR Apache-2.0、USI。Apple Silicon向けbinaryのみでsource非公開、評価関数・定跡は別licenseかつ非同梱。|D — Androidへ移植・再配布できない|
|Meteo-Shogi-AI|Apache-2.0だが公開checkpointはrandom/untrainedで、公式文書もcompetition strengthを未証明とする。MLX/Apple GPU中心。|D — 五段級・Android CPU要件を満たさない|
|16shiki-Iroha_kirameki|controllerはMITだが、実動作にYaneuraOu+Suisho5とdlshogi+modelを要求するWindows向け合議wrapper。|D — GPL/model条件を回避する独立engineではない|
|Hayanagi 1.5.0|USI/MultiPVと段級位目安を備えるが、作者が「対局による校正前」「簡易評価の最小構成」と明記する。独立repositoryにlicense grantも確認できない。|D — 五段根拠・商用同梱許諾とも不足|
|nshogi-engine|MITのAlphaZero型MCTSだが、実用executorはCUDA/TensorRT、CPU側はrandom executorのみ。強い公開model、Android CPU executor、MultiPVの根拠がない。|D — smartphone production要件を満たさない|

### 結論

現時点で、**許容licenseだけでG002へ安全に同梱でき、一般アマ五段級を根拠付きで満たす既製engineは確認できない。** Gokakuは新しい技術候補として残すが、modelの商用再配布許諾、Android向け推論移植、端末性能、独立棋力較正の4 Gateがあるため、直ちに製品統合しない。

五段級へ最短で到達する実績ある経路は引き続きYaneuraOu系である。ただしGPLv3のCorresponding Source提供と、選択する評価関数の個別条件を受け入れる製品方針が必要である。これはコード上の通常判断ではなく、G002本体の公開方法を左右するHuman Gateであり、許可なしにProduction APK/AABへ組み込まない。

## 13. MIT探索器と評価データを分離した追加確認（2026-10-08）

### rsshogi-nnue-mini

公式repository `nyoki-mtl/rsshogi-nnue-mini` はMITで、Rust製USI engineとして反復深化、PVS、qsearch、置換表、主要な枝刈り、Lazy SMP、standard `HalfKP256x32x32` NNUEを実装する。USIの`stop`、`ponderhit`、score、PVは公式文書で確認した。一方、通常buildは実行時に`eval/nn.bin`を必須とし、公式文書は利用者が水匠5公式releaseから取得するよう指定する。repository自体には評価fileを同梱せず、公開releaseも無い。

【重要】探索コードがMITであることは、水匠5 `nn.bin`の商用APK/AAB同梱・再配布条件を自動的にMITへ変更しない。水匠5公式release pageは入手方法と互換形式を説明するが、商用再配布、Google Play同梱、別engineとの組合せへ適用する明示的な許諾を掲載していない。したがって、G002では次を分離して判定する。

【Release asset実査】公式`Suisho5.7z`（24,062,512 bytes、SHA-256 `6734E3A3D28E67B9206C3442F6D10F16148138327DFF811CADEDFCF581F79809`）を一時領域へ取得して内容一覧を確認した。archive内は`nn.bin`の1件だけで、README、LICENSE、NOTICE、商用・再配布条件は同梱されていない。展開した`nn.bin`は64,217,066 bytes、SHA-256 `768068F0D534A0603A5D38BCD143DE6BBCA820D5F1C95A14D40863E5B7892D76`。よって「archive内の文書で許諾を確認できる」という可能性も否定された。これは利用禁止の断定ではなく、Production再配布を許可する根拠が公開一次資料から得られなかったという判定である。

|対象|確認結果|Gate判定|
|---|---|---|
|rsshogi-nnue-mini source|MIT。現代的な探索機能とUSIを確認。|コード単体は許容候補|
|水匠5 `nn.bin`|別配布物。公式releaseに商用再配布・APK同梱の明示条件なし。|Production同梱不可|
|MultiPV|公式USI option一覧に`MultiPV`を確認できない。候補3手要件は未実証。|未達|
|Android ARM64|Rust sourceはあるが公式Android build、JNI/FFI、端末実測なし。|未実証|
|五段級較正|公式の人間段位・独立engine比較を確認できない。|未達|

**Gate分類: C — SEARCH SOURCE CANDIDATE / MODEL RIGHTS AND PRODUCT REQUIREMENTS UNVERIFIED。** 水匠5評価fileをProduction APK/AABへ入れず、MIT表記だけで再配布しない。

### GPL engineを別processで扱う境界

GNU GPL FAQは、pipe、socket、command-line引数は通常、別program間の通信に使われるため、単純な`fork/exec`と疎な通信なら別programになり得ると説明する。一方、実質的に一体のsystemか、親密な内部dataを交換するかを含む法的判断であり、最終的には裁判所が判断するとも明記する。USIは標準化された文字列protocolで、JNIの共有address spaceより分離根拠は強いが、G002とengineを同一APKへ一体配布する構成を非GPL本体との単なる集合と断定できる一次根拠にはならない。

よって、YaneuraOu MATERIAL Lv9を採用する場合のProduction案は次のHuman Gateを維持する。

1. GPL engineを独立実行物・標準USI pipeとして維持し、G002本体へlinkしない。
2. engineのGPLv3本文、著作権表示、対応sourceの恒久的な取得導線を提供する。
3. G002本体までGPL対象となるか、Google Play同一AABでのaggregate判断をライセンス実務で確認する。
4. 結論が得られるまでengine binaryをProduction APK/AABへ同梱しない。

一次資料:

- <https://github.com/nyoki-mtl/rsshogi-nnue-mini>
- <https://nyoki-mtl.github.io/rsshogi-nnue-mini/nnue.html>
- <https://nyoki-mtl.github.io/rsshogi-nnue-mini/usi.html>
- <https://github.com/yaneurao/YaneuraOu/releases/tag/suisho5>
- <https://www.gnu.org/licenses/gpl-faq.html>

## 14. MATERIAL Lv9の五段プロキシ較正（2026-10-08）

### 基準の作り方

YaneuraOu公式ブログの2018年較正は、将棋倶楽部24換算で初段R1600を3,450 nodes、九段R3200を315,754 nodesとする。当初は、この2端点を対数補間した約33,003 nodesを五段R2400の開発用プロキシとしていた。その後、商用版の元となった公式公開GUI MyShogiのsource（commit `c3300bb1b8a92d22ed6549ac4179c1413911eb4f`）を再調査し、`EngineDefineSample.cs`に段位別の直接値が残っていることを確認した。そこでは将棋倶楽部24 R2400の**五段はNodesLimit 22,885**、Threads 4、持ち時間15分切れ負け相当としている。従って33,003 nodesの補間値は採用せず、今後の五段較正基準を22,885 nodesへ訂正する。

【重要】22,885 nodesは公式sourceに残る直接設定値であり、以前の補間推定より根拠が強い。ただし2018年当時のengine/eval、Threads 4、15分切れ負け相当という条件に依存する。別評価関数、Threads 1、Android実行環境、人間の実戦へ無条件に一般化せず、同条件再現とHuman較正を分けて扱う。

一次資料:

- <https://yaneuraou.yaneu.com/2018/08/07/%E6%9C%80%E6%96%B0%E3%81%AE%E5%B0%86%E6%A3%8B%E3%82%BD%E3%83%95%E3%83%88%E3%81%A0%E3%81%A8%E3%83%8E%E3%83%BC%E3%83%88%E3%83%91%E3%82%BD%E3%82%B3%E3%83%B3%E3%81%A7%E3%82%82%E4%B9%9D%E6%AE%B5%E3%81%8C/>
- <https://yaneuraou.yaneu.com/2018/08/07/nodeslimit%E5%9B%BA%E5%AE%9A%E3%81%A7%E3%80%81threads%E3%82%92%E5%A2%97%E3%82%84%E3%81%A6%E3%82%82%E5%BC%B1%E3%81%8F%E3%81%AA%E3%82%89%E3%81%AA%E3%81%84%E4%BB%B6/>

### 実測条件と結果

- 環境: G002専用AVD `G002_VisualGate_API35` / API 35 / x86_64。物理端末は未使用。
- 共通: YaneuraOu固定source、Threads 1、Hash 32MB、定跡なし、G002の`ShogiRules`で全着手の合法性を再検査。
- 旧・保守的プロキシ: Android NNUE版 + 水匠5`nn.bin`、`go nodes 33000`。この試験値は公式五段値22,885より多いが、Threads 1かつ別評価関数なので公式設定の直接再現ではない。
- 候補: Android MATERIAL Lv9版、`go movetime 1000`。
- 開始条件: 初期形、`7g7f 3c3d`、`2g2f 8c8d`、`7g7f 8c8d 2g2f 3c3d`。各条件でMATERIAL Lv9の先後を入替。

|開始条件|MATERIAL側|終局|総手数|終局時材料差（先手基準）|
|---|---:|---|---:|---:|
|初期形|先手|先手詰み|94|-4,400|
|初期形|後手|後手詰み|97|-600|
|角道|先手|先手詰み|82|-500|
|角道|後手|後手詰み|91|+4,500|
|飛車先|先手|先手詰み|134|-1,600|
|飛車先|後手|後手詰み|139|+3,700|
|両歩|先手|先手詰み|136|-1,800|
|両歩|後手|後手詰み|105|+7,000|

MATERIAL Lv9は0勝8敗で、全局が時間・手数上限ではなく詰み終局だった。色または単一序盤への偏りでは説明できず、五段プロキシに対して明確な棋力差がある。

### 判定

**MATERIAL Lv9を五段級Production候補として不採用とする。** Android ARM64対応、USI、MultiPV、外部評価file不要は成立するが、棋力要件を満たさない。水匠5NNUEは比較基準として十分強い一方、`nn.bin`の商用APK/AAB再配布条件が公式releaseで確認できないため、Production採用は引き続き不可。強いYaneuraOu系を採るには、再配布条件が明確な評価関数とGPLv3の製品方針を両方解決する必要がある。

一時較正test、AVD上のbinaryと`nn.bin`は結果採取後に除去した。製品コードへ変更はなく、全110/110 Unit Test、Debug APK build、`git diff --check`を再確認した。

## 15. 水匠5の権利者確認が必要な理由と問い合わせ文（2026-10-08）

やねうら王公式サイトは、水匠開発者のたややん氏と相談して水匠5を単体公開し、「自作の探索部と組み合わせるのも良し」と説明している。また、過去の公式系releaseにはYaneuraOu Android版と水匠5を組み合わせた配布物もある。したがって技術利用や既存組合せ配布の事実は確認できる。しかし、単体release本文と`Suisho5.7z`内には、広告付き商用Androidアプリ、APK/AAB同梱、Google Play再配布、適用license、クレジット条件の明示がない。既存配布の事実だけをG002の商用再配布許諾へ拡大解釈しない。

問い合わせ先は、公式repositoryのIssue作成画面 `<https://github.com/yaneurao/YaneuraOu/issues/new/choose>`、または公式READMEが案内する質問箱 `<https://yaneuraou.yaneu.com/2022/05/19/yaneuraou-question-box/>` とする。投稿前にGitHub等への本人loginが必要な場合はHuman Gateとし、認証を自動突破しない。

問い合わせ題名:

> 水匠5 nn.bin の商用Androidアプリ同梱・再配布条件について

問い合わせ本文:

> 水匠5の評価関数ファイル `nn.bin` の利用条件について確認させてください。無料・広告付きのAndroid将棋学習アプリで、YaneuraOuのAndroidビルドと組み合わせ、オフライン解析用として配布することを検討しています。YaneuraOu本体のGPLv3義務には別途従います。`nn.bin`について、(1) 広告付き商用アプリでの使用、(2) APK/AABへの同梱、(3) Google Playでの再配布、(4) 適用されるlicense（YaneuraOu本体と同じGPLv3か、別条件か）、(5) 必要な著作権表示・クレジット・配布文書、(6) Android向けにbuildしたYaneuraOuとの組合せ、が許可されるかをご教示いただけないでしょうか。許諾範囲を拡大解釈せず、回答で明示された条件に従います。

公式一次資料:

- <https://yaneuraou.yaneu.com/2024/06/23/suisho10-beta/>
- <https://github.com/yaneurao/YaneuraOu/releases/tag/suisho5>
- <https://github.com/mizar/YaneuraOu/releases/tag/v7.5.0>

## 16. 軽量KP256「魚沼産やねうら王」のAndroid・棋力PoC（2026-10-08）

水匠5の再配布条件確認を待つ間も五段級候補の技術検証を止めないため、YaneuraOu公式release `20190212_k-p-256-32-32` のKP256評価関数を開発用隔離領域だけで確認した。公式releaseは評価関数を「前回より約R50強い」「1MB未満で低メモリ環境向け」と説明し、公式blogは当該世代をelmoより強いはずと説明している。blogの作者回答には、open sourceなので利用したいゲーム会社は使用して構わない旨もある。一方、教師生成にtanuki- libraryを利用したためWCSC29で使う場合は申告が必要との注記がある。これらは利用可能性の強い一次根拠だが、G002の同一AAB配布におけるGPLv3義務を消すものではない。

### 配布物とbuild

|項目|結果|
|---|---|
|公式archive|`20190212_k-p-256-32-32.zip`、396,978 bytes、SHA-256 `E8D7359A8648ACFADFBA6EF87FF129E4466091643E6094A1B1A82C91E640EEBB`|
|評価file|`nn.bin`、893,917 bytes、SHA-256 `CF7645F64BF6BAA5C74612799CE562752F7985923B1F0FC2E6092C998ED867F9`|
|Android x86_64 engine|1,319,304 bytes、SHA-256 `419D3863E5C6692E0A4D1C8599566637509370EBE76C3B34545E9AEC89B00962`|
|Android ARM64 engine|1,100,992 bytes、SHA-256 `6D5919142670BEF40C89502D523FBD3C98A2031D73249AFD6AE4627CBC04CAF1`|
|ARM64形式|ELF64 / AArch64 / DYN / NOW PIE、依存は`libc.so`、`libm.so`、`libdl.so`|

固定YaneuraOu sourceと既存NDK r23bを使用し、`YANEURAOU_ENGINE_NNUE_KP256`としてx86_64・ARM64を別出力へcross buildした。G002専用AVD `G002_VisualGate_API35`でx86_64版を起動し、`id name YaneuraOu_NNUE_KP256 NNUE KP256 9.80git 64SSE4.2`、`usiok`、評価file読込、`readyok`、MultiPV=3、score、PV、bestmoveを確認した。33,000 nodesの4局面は32～35ms、約0.94～1.03M npsだった。物理端末は使用しておらず、ARM64 runtime・端末RAM・発熱・電池は未確認である。

### 五段プロキシ比較

- 魚沼産KP256: Threads 1、USI_Hash 32MB、定跡なし、`go nodes 33000`。
- 対照: MATERIAL Lv9、Threads 1、USI_Hash 32MB、定跡なし、`go movetime 1000`。
- 開始条件: 初期形、角道、飛車先、両歩の4種類を先後入替した8局。
- 結果: 魚沼産KP256 5勝3敗、手数57～167。手数上限・材料差判定はなく全局詰み。
- G002本番`ShogiRules`と`SfenCodec`で8局の全着手を再生し、違法手0件、8局すべて終局局面が`isCheckmate=true`であることを独立確認した。

MATERIAL Lv9は同じ4局面・先後入替で水匠5の33,000 nodes設定へ0勝8敗だったのに対し、魚沼産KP256は同対照へ勝ち越した。さらに公式資料では魚沼産KP256をelmoより強いと位置付けているため、MATERIALより五段級候補として明確に妥当である。ただし試験は公式五段値22,885より多い33,000 nodes、Threads 1、別評価関数であり、公式MyShogi五段設定の直接再現でも人間五段との直接対局でもない。従って、**技術候補A / 五段級Completion判定は同条件再較正とHuman較正前の条件付き**とする。

### 採否

魚沼産KP256を、水匠5の権利回答を待たずに進められる最有力の軽量Production候補とする。評価fileとARM64 engineの合計は約2MBで、約1GBのKPPTや約62MBの標準NNUEよりAndroid製品へ適する。ただしYaneuraOu engineはGPLv3であり、G002の配布license、Corresponding Source提供、Play上の入手導線をオーナーが受け入れるまではAPK/AABへ組み込まない。PoC binaryと評価fileはGit ignore対象・AVD一時領域だけに置き、製品コードへ変更していない。

公式一次資料:

- <https://github.com/yaneurao/YaneuraOu/releases/tag/20190212_k-p-256-32-32>
- <https://yaneuraou.yaneu.com/2019/02/12/%E9%AD%9A%E6%B2%BC%E7%94%A3%E3%82%84%E3%81%AD%E3%81%86%E3%82%89%E7%8E%8B%E3%81%A7%E3%81%8D%E3%81%BE%E3%81%97%E3%81%9F/>
- <https://github.com/yaneurao/YaneuraOu/wiki/%E3%82%84%E3%81%AD%E3%81%86%E3%82%89%E7%8E%8B%E3%81%AE%E3%82%A4%E3%83%B3%E3%82%B9%E3%83%88%E3%83%BC%E3%83%AB%E6%89%8B%E9%A0%86>
- <https://yaneuraou.yaneu.com/2018/08/07/%E6%9C%80%E6%96%B0%E3%81%AE%E5%B0%86%E6%A3%8B%E3%82%BD%E3%83%95%E3%83%88%E3%81%A0%E3%81%A8%E3%83%8E%E3%83%BC%E3%83%88%E3%83%91%E3%82%BD%E3%82%B3%E3%83%B3%E3%81%A7%E3%82%82%E4%B9%9D%E6%AE%B5%E3%81%8C/>
- <https://github.com/yaneurao/MyShogi/blob/c3300bb1b8a92d22ed6549ac4179c1413911eb4f/MyShogi/Model/Shogi/EngineDefine/Sample/EngineDefineSample.cs#L36-L69>

## 17. 公式五段presetとの同一engine自己較正（2026-10-08）

公式MyShogi sourceで確定した五段条件（将棋倶楽部24 R2400、Threads 4、NodesLimit 22,885）を、魚沼産KP256のAndroid x86_64版へそのまま適用した。G002専用AVD上で定跡なし、Hash 32MB、Threads 4、`go nodes 22885`を実行し、22,924 nodes、depth 10、10ms、bestmove/PVを確認した。MultiPV=3でも22,913 nodes、9msで候補3手、score、PV、bestmoveを返した。

評価関数差を排除して製品候補設定が五段preset以上かを確認するため、同じ魚沼産KP256 engine・同じ`nn.bin`・Threads 4・定跡なしで、次を直接対局させた。

- 製品候補: 160,000 nodes。
- 公式五段基準: 22,885 nodes。
- 開始条件: 初期形、角道、飛車先、両歩の4種類を先後入替した8局。
- 結果: 製品候補8勝0敗、81～138手、手数上限なし、全局resign。
- G002本番`SfenCodec`と`ShogiRules`で8局の全着手を再生し、違法手0件、8局すべてresign直前局面が`isCheckmate=true`であることを確認した。
- AVDでの要求からbestmoveまで: 製品候補436手の平均69.16ms・最大100.18ms、公式五段側440手の平均11.31ms・最大20.18ms。

この結果は、**同じengine/eval条件では160,000 nodesの製品候補が公式五段presetを明確に上回る**ことを示す。したがって魚沼産KP256 160,000 nodesを、G002の「五段級」を支える技術的な第一候補として採用可能と判定する。ただし、まだAPK/AABへ統合しておらず、物理ARM64端末でのruntime・発熱・電池・連続対局も未確認である。またGPLv3の配布方針はオーナー判断前なので、現行製品を五段級と表示してはならない。Production統合後に同一設定、実機安定性、Human対局較正を再確認して初めてG002全体の五段級Completion判定を行う。

## 18. Production接続前のlicense-neutral境界（2026-10-08）

GPLv3の製品方針を決定する前に、特定engine、native binary、評価fileを含まないUSI接続境界を追加した。`UsiEngineBackend`がprocess lifecycle、USI handshake、`stop`を担当し、`UsiShogiAnalysisEngine`はG002の`ShogiAnalysisEngine`へMultiPV結果を変換する。MainActivityのengine選択は変更せず、現行製品は引き続き`LocalShogiAnalysisEngine`を使用する。

接続境界では、SFEN、MultiPV=3、node/time budget、cancel callbackをbackendへ渡し、PVをG002本番ルール層で合法手へ変換する。不正PVと重複候補を除外し、最大3候補、score、PV、先生説明、positionId、requestIdを既存modelへ保持する。ユーザー手レビューは着手後局面を同じbackendで解析し、相手視点のUSI scoreを着手者視点へ反転して返す。外部engineの失敗時には現行local engineへ戻せるが、cancel済み要求ではprimary/fallbackの双方を開始しない。

偽backendによる7件の回帰テストを追加し、MultiPV順、合法性、重複排除、stale/cancel遮断、同一局面レビュー、先後score変換、違法手拒否、fallbackを確認した。全回帰は15 suites、117/117 PASS、Debug APKとRelease AABのbuildが成功した。これはProduction接続経路の準備完了を示すが、実process backend、魚沼産binary/`nn.bin`の同梱、MainActivityでの有効化は行っておらず、GPLv3方針の承認前に製品棋力が変わったとは扱わない。

続いてpersistentな`UsiProtocolBackend`と`ProcessUsiCommandChannel`を追加した。`usi`/`usiok`、Threads=4、Hash=32、`isready`、`usinewgame`、要求ごとのMultiPV、`position sfen`、`go nodes`、cancel/time limit時の`stop`、`bestmove`回収、異常channelの破棄と次要求での再生成を一箇所で管理する。特定engineのpathは保持せず、製品承認後にfactoryから供給する設計である。偽channelの4件に加え、ローカルの一時PowerShell USI processを実際に起動し、標準入力からhandshake/queryを送り、標準出力の160,000 nodes・PV・bestmoveを回収して終了する往復試験をPASSした。一時process/testは製品から除去した。恒久回帰は16 suites、121/121 PASS、Debug APK・Release AAB build成功。残る技術工程は、承認済みARM64実行物をfactoryへ与えるAndroid配置、魚沼産実processでの同じ往復、MainActivity有効化、実機安定性である。

最後に`ShogiEngineFactory`を製品画面の唯一のengine生成点として接続した。承認済みbackendが無い現在は従来の`LocalShogiAnalysisEngine`をそのまま返すため、対局・指導の現行挙動は変わらない。backendを与えた場合だけUSI engineをprimary、local engineをfallbackにする。`ShogiAnalysisEngine`へ既定no-opのlifecycle終了処理を追加し、画面離脱時はUSI backend/processを閉じる。factory既定動作、USI候補、空結果fallback、close伝播の3件を追加し、全17 suites、124/124 PASS、Debug APK・Release AAB build成功。これでGPL判断後の製品コード変更は、承認済みruntime backendの供給とAndroid assets/native配置に限定された。

## 19. GPLを避ける候補「Sekirei」の一次資料確認（2026-10-08、研究のみ）

GPLv3方針を未承認のまま製品棋力を上げる代替として、`kent-tokyo/sekirei` の公開資料を確認した。source codeは MIT または Apache-2.0 の選択式であり、READMEは NOTICE の保持を求める。配布されるNNUE重みはsourceとは別の CC BY 4.0 artifactで、versioned model card、SHA-256、帰属表示を個別に確認しなければならない。従って、sourceだけを根拠にweightsや学習データまで同じ権利条件とは扱わない。

技術面ではUSI、alpha-beta/PVS、quiescence、TT、MultiPV、NNUEを提供する。一方、一次資料は現時点で公式Android/iOS FFI/JNIがなく、唯一の正式統合面はstdin/stdoutのUSI binaryであること、Android上のproduction検証・RAM/発熱/電池の実測・アマ棋力への公開較正がまだ無いことを明記している。PCにはRust/Cargoが無く、既存のAndroid NDK r23bだけがある。そのため、Rust toolchainの追加導入とG002固有JNI/Android runtime実装を伴う実証なしに、商用Android用の強いengineや一般アマ五段級とは判断しない。

**採否:** license clarityは魚沼産KP256より良好な研究候補だが、現在の公開資料は五段級・Android production readinessを裏付けない。engine source、binary、weights、Rust toolchainは今回導入せず、APK/AAB、Git、Play設定、既存local engineを変更しない。次の候補工程は、依存ライセンスの固定監査と、必要なら別途Android FFIのPoCを隔離して実証することである。

既存の隔離済みsource、Rust 1.98.1 toolchain、Cargo cacheだけを使い、`cargo build --release -p sekirei --offline`を試行したが、workspaceの`sekirei-bench`が固定commitの`https://github.com/nyoki-mtl/rsshogi.git`を依存として解決しようとして停止した。ネットワークなしでは当該commitがcacheに無く、buildは開始していない。この結果はsource treeだけで第三者依存のlicense監査を省略できないことを示す。追加download、依存導入、製品codeやAPK/AABへの変更は行わなかった。

公式一次資料:

- <https://github.com/kent-tokyo/sekirei>
- <https://github.com/kent-tokyo/sekirei/blob/main/docs/nnue_weights.md>
- <https://github.com/kent-tokyo/sekirei/blob/main/docs/mobile_integration.md>

## 20. GPLv3製品統合（2026-10-09）

オーナーがG002全体のGPL-3.0-or-later化と、秘密情報を除くsource公開を明示承認した。これにより、従来の「LICENSE BLOCKED」Gateは製品方針について解消した。G002はroot `LICENSE`、`NOTICE`、`README.md`でGPLv3、YaneuraOu upstream commit、KP256 modelの公式provenance、対応sourceの場所を明記する。

実装はJNI linkではなく、APKに含めたARM64 executableをAndroidのnative-library directoryから独立USI processとして起動する。`nn.bin`はAPK assetからapp-private working directoryへSHA-256検証して配置する。上流対応sourceとAndroid build scriptsは`third_party/YaneuraOu/`に同梱し、ローカルbuild output、JKS、AAB、APK、passwordはGit管理外にする。engine/model/processのいずれかが失敗した場合は、既存のtested local engineへfallbackして対局画面を停止させない。

実装後のclean buildはUnit Test 135/135 PASS、Debug APK、unsigned Release AAB生成まで確認した。ARM64 product runtimeはG64へDebug APKを更新後、USB transportが消えたため未確認である。次の技術GateはUSB再接続後にG64上でUSI handshake、候補3手、AI応手、連続対局、要求から結果までの時間を確認すること。これらが完了するまで、一般アマ五段級・端末性能・Human Play合格とは表示しない。
