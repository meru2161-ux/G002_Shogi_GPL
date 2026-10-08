# G002 将棋: AIエンジン候補の正式比較・選定 v1

調査日: 2026-09-27
対象: `C:\Users\Mel7Dev\Documents\MelMiniGameFactory\G002_Shogi`
対象外: エンジンの取得、NDK/CMake/JNI/Gradle変更、評価関数ダウンロード、production code変更

## 1. 結論

### 技術的第一候補（採用確定ではない）

**やねうら王 (YaneuraOu) を、GPLv3および選択する評価関数の配布条件を個別に確認することを前提とした技術的第一候補とする。**

根拠は、公式資料でUSI、MultiPV、ARM対応、複数種の評価関数、継続的な更新が確認でき、G002が必要とする「候補3手程度 + 評価 + PV」を最も直接的に満たすためである。一方でGPLv3の導入形態、JNI bridge、アプリ本体、評価関数を一体として配布する際の義務は、この調査だけで確定できない。したがって、これは**技術選定**であり、法的な採用承認ではない。

### ライセンス面で最も扱いやすい候補

**Sunfish4 (MIT)**。公式READMEでMITライセンスとUSIビルドは確認できた。しかし、G002の必須要件であるMultiPV、Android/ARM64実機、評価関数ファイルの配布条件・サイズは未実証である。そのため「安全な代替候補」であって、現時点の実装第一候補ではない。

### 方針

1. G002の `ShogiAnalysisEngine` 抽象化と先生層は維持する。
2. 次段階は、採用前に限定した**実証実験**を行う。評価関数を含む配布物のライセンス確認が完了するまで、GPLエンジンをアプリへ組み込まない。
3. ネイティブ統合する場合の第一案は、アプリ内 `lib*.so` をCMakeでビルドしてJNIから呼ぶ方式である。外部実行形式・OEX依存・ローカルソケットは第一案にしない。

## 2. G002固有の要求

G002は対局CPUではなく、局面を見て「この駒をここへ」「なぜ」「相手の応手」「その先」を示す研究・学習アプリである。エンジンが担当する範囲と、G002が担当する範囲を分離する。

|層|責務|今回の判断|
|---|---|---|
|将棋エンジン|局面評価、候補手、PV、詰み情報、探索進捗|USI `info` / `score` / `pv` / `multipv` / `bestmove` を取得する|
|G002解析アダプタ|USI文字列を `AnalysisResult` / `CandidateMove` / `VariationLine` に正規化|既存 `ShogiAnalysisEngine` の背後だけに置く|
|先生層|目的、注意点、相手の狙いを日本語へ変換|エンジンの数値やPVを説明用の根拠にする。説明生成AIは今回導入しない|

エンジンは人間向けの「なぜ」を直接返すものではない。候補手・評価・PVと、G002固有の説明テンプレート／局面特徴抽出を混同しない。

## 3. 調査の範囲と読み方

- 一次資料を優先し、各リポジトリのREADME/LICENSE/公式更新履歴を確認した。
- `○` は公式資料で確認、`△` は技術的にはあり得るがAndroid実機または当該バイナリのUSI option出力で未確認、`未確認` は資料で根拠を得られなかった項目である。
- サイズ、RAM、熱、初回解析時間、数秒でのMultiPV提示は実機計測なしには断定しない。
- 「別プロセスならアプリ本体を非GPLに保てる」とは結論づけない。結合・配布態様の評価は法的確認が必要である。

## 4. 候補比較表

|候補|ライセンス|USI|MultiPV / PV|ARM64 / Android|完全オフライン|棋力の研究適性|サイズ・評価関数|統合難易度|ライセンス難易度|G002適合性|重大リスク|
|---|---|---|---|---|---|---|---|---|---|---|---|
|やねうら王|GPL-3.0|○|○ / ○|○ ARM、△ Android|○（ローカル配布物）|○|評価関数は別途選定。実サイズ未確認|△|高|○（条件付き）|GPLv3と評価関数の配布条件|
|Apery Rust|GPL-3.0|○|△ / △|△ / 未確認|△（評価関数submoduleが必要）|△|評価関数バイナリ必須。サイズ未確認|高|高|△|Android移植、MultiPV、評価関数条件が未実証|
|Sunfish4|MIT|○|未確認 / △|△ / 未確認|△（`eval.bin`/`book.bin`を別管理）|△|サイズ・データ条件未確認|△|低|△|MultiPVとAndroid実機が未実証|
|nshogi-engine|MIT|○|未確認 / 未確認|△ / 未確認|未確認|未確認|TensorRT executorはCUDA/TensorRT必須。モデル条件未確認|高|低（本体）|×（現時点）|スマホ向けCPU実行と候補手出力が未実証|
|DeepLearningShogi (dlshogi)|GPL-3.0|○|未確認 / △|×に近い / 未確認|△（モデル同梱なら可能性はある）|○（研究用）|モデルは外部提供先あり。サイズ・利用条件未確認|非常に高|高|×（現時点）|CUDA/cuDNN/TensorRT前提とモデル配布条件|

### 比較の補足

**やねうら王**

- 正式名称／公式URL: YaneuraOu / <https://github.com/yaneurao/YaneuraOu>
- 言語: C++。公式READMEはUSI、MultiPV、ARM、KPPT/KPP_KKPT/NNUE/SFNNの評価関数対応を明記する。
- 更新状況: 公式コミット履歴で2026-08-05まで更新を確認。2026年更新履歴にはAndroid用make/path修正の記録もある。
- 解析: `MultiPV` は確認済み。USIの `info` 行から各順位の `score` と `pv` を蓄積し、`bestmove` を完了通知として扱う設計が可能。実際の選択edition／評価関数で `usi` のoption出力、`go depth`、`go nodes`、`go movetime`、詰み応答を確認する必要がある。
- Android: ARM対応とAndroid用make更新は確認したが、G002のNDK、`arm64-v8a`、`x86_64` emulatorでのビルド／実行／NEON要件は未実測。
- 評価関数: 本体と別物として固定する。公式READMEの「リゼロ評価関数ファイル」は権利主張なしと記載されるが、採用するネットワークがそれである保証はない。他の評価関数・定跡・学習済みネットワークの利用条件は各配布元で確認する。
- 資源: 本体、評価関数、hash、threads、定跡を含む実サイズ・RAM・発熱は未確認。`Threads`、`USI_Hash`、`MultiPV` を低い初期値にし、実機で段階的に測定する。

**Apery Rust**

- 正式名称／公式URL: Apery (Rust) / <https://github.com/HiraokaTakuya/apery_rust>
- 言語: Rust。公式READMEはStockfishおよびApery C++版由来のUSIエンジンと説明する。
- 更新状況: 公開リポジトリは確認できたが、この調査で取得できた公式画面には最終コミット日時が表示されなかった。活動中／停止中を断定しない。
- 解析: USIは確認済み。公式READMEでMultiPV、PV、score/mateの出力までは確認できないため、実バイナリの `usi` と探索出力で検証が必要。
- 評価関数: 既定のKPPTには評価関数バイナリsubmoduleが必要。`material` featureなら評価関数なしでビルドできるが、研究用の棋力を満たすかは未検証。
- Android: Rustのクロスコンパイル可能性だけではAndroid対応の根拠にならない。NDK連携、ABI、C++依存、評価関数ロードを実証する必要がある。

**Sunfish4**

- 正式名称／公式URL: Sunfish4 / <https://github.com/sunfish-shogi/sunfish4>
- 言語: C++。MIT License。
- 更新状況: 公開リポジトリとrelease案内は確認できたが、この調査で取得できた公式画面には最終コミット日時が表示されなかった。継続開発中とは断定しない。
- 解析: `make usi` によるUSIバイナリのビルドは確認済み。MultiPVは公式READMEで確認できず、候補3手要件を満たすかは実証前。
- 評価関数: WebAssemblyの公式手順では `eval.bin` と `book.bin` を別途配置する。ネイティブAndroidで必要なデータ、サイズ、各データのライセンスは未確認。
- Android: CMake/C++なのでNDK対象化は技術的に検討できるが、ARM64、NEON、Android実行、x86_64 emulatorの公式実績は未確認。

**nshogi-engine**

- 正式名称／公式URL: nshogi-engine / <https://github.com/nyashiki/nshogi-engine>
- 言語: C++。MIT License。nshogi library上のAlphaZero型MCTS／マルチスレッドUSIエンジン。
- 更新状況: 2025年著作権表記のMIT LICENSEと公開リポジトリは確認したが、最終コミット日時は今回の公式取得結果に表示されなかった。
- 解析: USIバイナリの生成は確認済み。MultiPV、PV、score/mate、時間・node・depth制御の対応はREADMEから確定できない。
- 評価関数／実行環境: TensorRT executorにはCUDAとTensorRTが必要。`random` executorは存在するが、研究用解析の代替にはならない。スマートフォン向けCPU executor・モデル配布条件・ARM64は未確認。

**DeepLearningShogi (dlshogi)**

- 正式名称／公式URL: DeepLearningShogi / <https://github.com/TadaoYamaoka/DeepLearningShogi>
- 言語: C++（USI／自己対局）とPython（学習）。GPL-3.0。
- 更新状況: 公式コミット履歴で2026-09-05まで更新を確認。
- 解析: READMEは `usi` と `usi_onnxruntime` プロジェクトを列挙する。MultiPV、PV、score/mateのG002利用可否は実バイナリで未検証。
- 評価関数: 最新モデルの案内先は棋神アナリティクスであり、モデルそのものの配布許諾・サイズ・APK/AAB同梱可否は別途確認が必要。
- Android: 公式ビルド環境はCUDA、cuDNN、TensorRTを明記する。現時点で一般的なAndroid ARM64スマートフォンのCPUオンリー解析用として選ぶ根拠は不足する。

## 5. USIからG002モデルへの変換

USIの `info` 行は途中経過で複数回到着する。G002は最終または安定した行だけを採用し、局面IDと探索要求IDに結び付ける。

|USI要素|G002側の格納先|注意点|
|---|---|---|
|`multipv N`|`CandidateMove.rankForFuture`|同一要求ID内で1始まりの順位を保持する|
|`pv <move...>`|`VariationLine`|先頭手を候補手とし、以後を相手応手・継続として一手ずつ盤へ反映する|
|`score cp X`|将来の評価値フィールド|手番視点・符号・単位をアダプタで明示的に正規化する|
|`score mate X`|将来の詰み情報フィールド|cpと混同しない。正負の解釈はエンジン実機で確認する|
|`depth` / `nodes` / `time` / `nps`|解析メタデータ|教師画面には必要なものだけを簡潔に表示する|
|`bestmove`|要求の完了|古い局面／cancel済み要求の結果は破棄する|

USI情報を説明文へ直接連結しない。例えば「候補AはPVで駒損を避ける」「候補Bは相手の王への線を維持する」のような説明は、G002側がPV・駒得・王手・利き・定跡外の情報を使って生成する。

## 6. Android統合方式の比較

|方式|概要|長所|問題点|G002での判断|
|---|---|---|---|---|
|NDK + CMake + `.so` + JNI|エンジンと狭いbridgeを `lib*.so` としてアプリに同梱し、KotlinからJNIで呼ぶ|Androidの標準的なネイティブライブラリ経路。UIとエンジンを明確に分離できる|NDK/CMake導入、ABI別ビルド、JNI例外・cancel・メモリ管理が必要。GPLならbridgeも含め法的確認が必要|**第一案**|
|アプリ内native worker + USI parser|`.so`内で専用workerを動かし、USI形式の要求／応答をbridge内部で処理する|既存エンジンのUSI境界を尊重し、G002へは構造化結果だけ返せる|文字列プロトコル、停止、タイムアウト、スレッドを設計する必要|第一案の内部構成として採用候補|
|別native process + local socket|engine workerを別プロセスにし、loopback socketでUSI通信する|クラッシュ隔離や既存CLIの再利用を検討しやすい|Androidでの実行形態、IPC、ライフサイクル、資産パス、デバッグが複雑。GPL境界の法的結論にもならない|将来の実験案。第一案にはしない|
|OEX外部エンジン|別アプリにパッケージしたUSIエンジンを利用する方式|ShogiDroid2に利用実績がある|ユーザーの別インストール、エンジン版差、Play配布、説明品質の再現性をG002で制御しにくい|G002内蔵解析には不採用|

Android公式資料は、C/C++をCMakeで共有ライブラリへビルドしGradleでパッケージ化、Kotlin/JavaからJNIで呼ぶ経路を案内している。これは「特定エンジンがそのままビルドできる」保証ではないが、G002の統合基盤として妥当である。

OEXについてはShogiDroid2公式マニュアルが、Androidのセキュリティ強化により従来のエンジンをそのまま使えず、外部アプリとしてパッケージしたOEXエンジンを利用すると説明している。これは外部連携の参考にはなるが、G002のオフライン一体型研究体験には適さない。

## 7. Android / Google Play /配布の論点

1. ネイティブコードを追加する時点で `arm64-v8a` を必須とし、必要なら `x86_64` をemulator検証用に別途ビルドする。Google Playの64-bit要件では、32-bit ABIを入れる場合は対応する64-bit ABIも必要となる。
2. AABは端末ABIに応じた配信でサイズ増を抑えられる。ただしエンジン本体、評価関数、定跡のサイズは実計測まで未確認である。
3. APK Analyzerで `lib/arm64-v8a/*.so` と必要なデータ資産を確認し、arm64実機で初回解析、連続解析、発熱、RAM、バックグラウンド復帰を測る。
4. GPL候補を配布するなら、ソース提供義務、改変・bridgeの扱い、ライセンス表示、オファー方法、評価関数の別条件をリリース前に専門家または権利者へ確認する。Google Playでの配布可能性をここで保証しない。
5. MIT候補でも著作権表示・ライセンス文の同梱、第三者データ／モデル／定跡の個別条件確認が必要である。

## 8. 評価関数と資源

|候補|評価関数の状態|APK/AAB同梱判断|サイズ・RAM・熱|
|---|---|---|---|
|やねうら王|KPPT/KPP_KKPT/NNUE/SFNN対応。採用ネットワークごとに条件が異なり得る|未決定。ライセンスと実測後に決める|未確認|
|Apery Rust|既定KPPTはsubmodule評価関数バイナリが必要。material modeあり|未決定|未確認|
|Sunfish4|`eval.bin` と `book.bin` を別管理する資料あり|未決定|未確認|
|nshogi-engine|TensorRT executorはモデル・CUDA/TensorRTに依存。CPU向けモデル条件未確認|現時点で判断不可|未確認|
|dlshogi|外部モデル案内あり。モデルの個別条件要確認|現時点で判断不可|未確認|

「候補3手を数秒程度」は目標であり保証ではない。最初の実証では、`Threads=1`、小さなhash、`MultiPV=3`、短い `movetime` または `nodes` 上限から始め、同一局面で再現可能な測定を行う。強さを落とす手段は、探索時間・node数・depth・threads・hash・MultiPVを調整することであり、初期値はエンジンの `usi` 応答を確認してから決める。

## 9. 最終候補（3つ以内）

### 1. やねうら王 — 技術的第一候補（条件付き）

- メリット: G002必須のUSI、MultiPV、PV、ARM、強い探索基盤を公式資料で確認できる。継続更新も確認できる。
- デメリット: C++/NDK統合、評価関数選定、端末性能調整が必要。
- 最大リスク: GPLv3および評価関数の配布条件。技術的に動いても、公開方針に適合するとは限らない。
- 統合方法: CMakeで `arm64-v8a` のshared libraryと狭いbridgeを作り、JNIから局面・設定・cancelを渡す。USI parser/adapterをG002 UIの外側に隔離する。
- 将来性: 高い。ただし上流変更をpinしたrevisionで検証し、更新は再計測する。

### 2. Sunfish4 — ライセンス軽量の代替候補

- メリット: MIT、C++、USI、CMakeビルドの資料がある。
- デメリット: MultiPV、Android実機、評価関数の配布条件が未確認で、現時点ではG002必須能力を満たす保証がない。
- 最大リスク: 実証後に候補3手／PVの要求を満たせず、採用し直しになること。
- 統合方法: YaneuraOuと同じくCMake/JNI。ただし最初に `usi` optionとMultiPVの有無を確認してから工数をかける。
- 将来性: ライセンス面は扱いやすいが、研究用の棋力と活動状況は実証が必要。

### 3. Apery Rust — GPL比較対象

- メリット: USIエンジンであり、評価関数なしのmaterial build経路もある。
- デメリット: GPLv3、評価関数submodule、Android/Rust/NDK連携、MultiPVの実証が残る。
- 最大リスク: やねうら王と同等のライセンス判断を要する一方で、G002に必要なAndroid実績・MultiPVの根拠が少ないこと。
- 統合方法: Rust Android targetまたはC ABI bridgeを要する可能性があるため、C++直接統合より不確実性が高い。
- 将来性: 保留。やねうら王のライセンス承認が得られず、Sunfish4がMultiPV不成立の場合の比較対象に留める。

`nshogi-engine` と `dlshogi` は、今回のスマートフォン・完全オフライン・候補3手・数秒目標には必要な実証不足またはGPU依存が大きく、最終候補には残さない。

## 10. Step 5で行う実証実験（実装は今回しない）

実証を開始する前に、GPL候補についてはライセンス判断と評価関数の出所・条件を確定する。未確定ならMIT候補のみを対象に限定する。

1. **配布物監査**: engine revision、LICENSE、評価関数、定跡、bridgeの各ライセンスを台帳化する。GPL候補は法的確認を完了させる。
2. **最小USI能力確認**: 各候補を隔離環境で起動し、`usi`、`isready`、`position sfen`、`setoption name MultiPV value 3`、`go movetime`、`stop` を確認する。`info multipv/score/pv` と `bestmove` を保存する。
3. **Android ABI実証**: 承認済み候補だけを `arm64-v8a`、必要なら `x86_64` で最小ビルドし、APK Analyzer、実機起動、JNI往復、cancelを検証する。
4. **資源測定**: 同一局面セットで初回時間、3候補応答時間、native増分サイズ、PSS/RSS、CPU、温度、連続解析時の電池消費を測定する。数値目標はこの測定後に決める。
5. **G002変換確認**: 1局面から3候補の `CandidateMove`、PVの一手送り、評価／詰み表現、先生説明の根拠を作り、UIを変更せず差し替えられることをテストする。

## 11. 未解決事項

- GPLv3エンジンをG002へどの配布形態で組み込むか、その際のアプリ／bridge／ソース提供義務の範囲。これは法的確認が必要。
- 採用する評価関数・定跡の正確なライセンス、サイズ、再配布可否。
- 各候補のAndroid ARM64、NEON、x86_64 emulator、実機での動作。
- Sunfish4、Apery Rust、nshogi-engine、dlshogiのMultiPVとUSI解析出力の実機確認。
- 本体・評価関数・hash設定を含む実アプリサイズ、RAM、発熱、解析時間。
- G002の教育向け評価値表現（手番・符号・駒割換算・詰み表示）のUX仕様。

## 12. 一次資料

- YaneuraOu README / LICENSE: <https://github.com/yaneurao/YaneuraOu>
- YaneuraOu 更新履歴 2026: <https://github.com/yaneurao/YaneuraOu/wiki/やねうら王の更新履歴2026>
- Apery Rust README / LICENSE: <https://github.com/HiraokaTakuya/apery_rust>
- Sunfish4 README / LICENSE: <https://github.com/sunfish-shogi/sunfish4>
- nshogi-engine README / LICENSE: <https://github.com/nyashiki/nshogi-engine>
- nshogi library / third-party data note: <https://github.com/nyashiki/nshogi>
- DeepLearningShogi README / LICENSE: <https://github.com/TadaoYamaoka/DeepLearningShogi>
- Android: Add C and C++ code to your project: <https://developer.android.com/studio/projects/add-native-code>
- Android: JNI tips: <https://developer.android.com/ndk/guides/jni-tips>
- Android: Android ABIs: <https://developer.android.com/ndk/guides/abis>
- Android: Support 64-bit architectures: <https://developer.android.com/games/optimize/64-bit>
- Google Play: create and set up app / App Bundle: <https://support.google.com/googleplay/android-developer/answer/9859152>
- ShogiDroid2 外部エンジン（OEXの実装可能性の参考）: <https://sdroid2.siganus.com/manuals/ext_engine.html>

この文書は技術調査記録であり、法律上の助言でも、いずれかのエンジンや評価関数の採用承認でもない。
