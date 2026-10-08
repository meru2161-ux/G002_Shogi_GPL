# G002 将棋: Sunfish4 Engine Gate Resolution v1

実施日: 2026-09-28
対象: `C:\Users\Mel7Dev\Documents\MelMiniGameFactory\G002_Shogi`
対象engine: Sunfish4 `v0.1.3` / `91cff975e68ccfcb89c13d4433bf0620a83f3adb`
最終Gate分類: **D — SUNFISH4 NOT SUITABLE（G002 Production/Google Play向け）**

## 1. 結論

Sunfish4 v0.1.3 はG002のProduction engineとして採用しない。理由は、USIの`isready`に必要な`eval.bin`について、作者または公式配布元の一次資料から作成者・ライセンス・商用利用・再配布・APK/AAB同梱・Google Play配布条件を確認できなかったためである。

`book.bin`は任意のため同梱しない構成を検討できるが、評価データのGateは解消しない。Windows native実行は、この環境で有効なApp Control強制ポリシーにより阻害されており、回避や保護設定変更は行わない。

したがって **STEP 7 READYではない**。Step 7～10の予約実行、Production JNI、実engine接続、native/runtime dataのAPK/AAB同梱は行わない。

## 2. Gate A — runtime data出所・ライセンス

### 一次資料として確認した範囲

- Sunfish4公式repositoryのREADME、LICENSE、release一覧、v0.1.3 release note
- 固定sourceのUSI初期化、Evaluator、Book、learning tool、設定ファイル
- 公式Windows release assetの展開内容
- repository内の`eval.bin`、`book.bin`、`eval-ex.bin`関連検索
- 公式repository/作者を対象とするWeb検索

公式repositoryはSunfish4 **source code** をMIT Licenseとして公開し、公式releaseはWindows用`sunfish4_v0.1.3_win64.zip`を案内する。しかし、v0.1.3 release noteはUSI PV順序修正とVisual Studio 2019更新だけであり、評価関数・定跡データの権利条件を示さない。

一次資料:

- <https://github.com/sunfish-shogi/sunfish4>
- <https://github.com/sunfish-shogi/sunfish4/blob/master/LICENSE.txt>
- <https://github.com/sunfish-shogi/sunfish4/releases/tag/v0.1.3>

### release assetの確認結果

調査専用の公式assetには、次のruntime dataが含まれていた。これらはProduction APK/AABおよびGitへ含めていない。

|ファイル|配布元|サイズ|asset内の個別LICENSE/README/COPYING/NOTICE/CREDITS|判定|
|---|---|---:|---|---|
|`eval.bin`|公式v0.1.3 Windows release|47,124,355 bytes|なし|C: 不明|
|`book.bin`|公式v0.1.3 Windows release|2,881,571 bytes|なし|C: 不明|

archive: `sunfish4_v0.1.3_win64.zip`、33,474,725 bytes、SHA-256 `7C03F2313FE70FFCE79D38E43F5E71F217C6FF64AE34DB77F89FC64D52303DBF`。

source treeには`eval.bin`/`book.bin`は含まれない。sourceのMIT Licenseを、別release dataの利用許諾として扱う根拠はない。作者、生成元、第三者データ由来か、改変・商用利用・再配布・クレジット義務、APK/AAB/Google Play同梱可否はいずれも**未確認**である。

### `book.bin` の必要性

固定sourceでは`UseBook`が既定`true`のUSI optionだが、`setoption name UseBook value false`でopening-book照会を無効化できる。`Book::load()`が`book.bin`を開けない場合はfalseを返すが、USI `ready()`は戻り値を検査せず`readyok`を返す。bookが空なら通常探索へ進む。

結論: **book.binは通常探索、MultiPV、bestmoveに必須ではない（source確認）。** G002が別engineを選ぶ場合も、定跡データはライセンス一次資料が得られるまで同梱しない。

### `eval.bin` の必要性

`Evaluator::sharedEvaluator()`は`eval.bin`を読み、失敗時にはzero evaluatorを初期化する。しかしUSI `ready()`はdata sourceが`EvalBin`ではないと`Failed to read eval.bin.`を出して終了する。したがって、Sunfish4 USIとしての`isready`/通常探索には`eval.bin`が必須である。

learning targetには`eval-ex.bin`を`eval.bin`へ変換する機能があるが、`eval-ex.bin`、学習済み値、学習入力データはsourceに同梱されない。sourceのみからG002固有の同等評価データを安全・現実的に生成できることは確認できない。大規模学習は本工程で実施しない。

結論: **eval.binは必須であり、個別ライセンスがC（不明）のままでは代替不能なGoogle Play blockerである。**

## 3. Gate B — Windows実行阻害の特定

### 対象と観測結果

|対象|Mark-of-the-Web (`Zone.Identifier`)|署名|直接PowerShell Process起動|
|---|---|---|---|
|公式v0.1.3 `sunfish_usi.exe`|なし|署名なし|block|
|固定sourceからMSVCで生成した`sunfish_usi.exe`|なし|署名なし|block|

PowerShellの`System.Diagnostics.Process.Start`による直接起動時のWindowsエラー:

> An error occurred trying to start process ... アプリケーション制御ポリシーによってこのファイルがブロックされました。

CodeIntegrity Operational logには両ファイルについて、次の組み合わせが記録された。

|Event ID|結果|
|---:|---|
|3033|Enterprise signing level requirementsを満たさない|
|3077|Policy ID `{0283ac0f-fff1-49ae-ada1-8a933130cad6}` のCode Integrity/App Control強制block|

`Microsoft-Windows-AppLocker/EXE and DLL`およびDefender Operationalには、対象パスを含むイベントを確認できなかった。したがって原因はPowerShellのExecutionPolicy、Mark-of-the-Web、Defender検知、DLL不足、ABI不一致ではなく、**Code Integrity/App Controlが未署名native exeのロードをblockしたこと**である。

Microsoft公式資料では3033をApp Control要件不適合、3077を強制モードの主要block eventとしている。

- <https://learn.microsoft.com/en-us/windows/security/application-security/application-control/app-control-for-business/operations/event-id-explanations>
- <https://learn.microsoft.com/en-us/windows/security/application-security/application-control/app-control-for-business/operations/appcontrol-debugging-and-troubleshooting>

### cmd.exe切り分け

`cmd.exe`からの非対話起動はexit code 0を返したが、Codex Sandboxが標準入力リダイレクトを拒否したため、USIコマンドをengineへ渡せなかった。

> ERROR: Input redirection is not supported, exiting the process immediately.

> The process tried to write to a nonexistent pipe.

これはSunfishのUSI応答ではなくSandboxのI/O制限である。別shellをセキュリティ回避に使わず、`usi`/`isready`/MultiPV/stop/連続解析は**未実測**のままとする。

### 正規の対処

本件はEnterprise/App Control policyの管理範囲である。Windows Defender全体、SmartScreen、AppLocker/WDAC、ExecutionPolicy、registry、ホワイトリストを変更していない。個別解除も実施しない。

正規に進めるには、端末管理者またはセキュリティ管理者が、Policy IDと3033/3077の相関イベント（必要なら3089署名情報）を確認し、Sunfish4を許可できるかを判断する必要がある。G002作業としてその変更を依頼・実施・回避しない。

## 4. Gate C — CMake / Android NDK

|項目|状態|
|---|---|
|CMake|未導入|
|Android side-by-side NDK|未導入|
|Android side-by-side CMake|未導入|
|arm64-v8a build|未実施|

eval.binのGoogle Play利用条件が未解決で、Sunfish4をProduction候補として継続する合理性がない。よって公式SDK ManagerからのNDK/CMake追加、Android cross build、JNIを実施しない。既存SDK、Visual Studio、PATH、他プロジェクト設定は変更していない。

## 5. 作者への問い合わせ文（送信はしていない）

一次資料だけでは解決できない場合に、ユーザーが作者へ確認するための文面である。Codexは送信しない。

```text
件名: Sunfish4 v0.1.3 の eval.bin / book.bin のライセンスとAndroid配布について

Kubo Ryosuke 様

Sunfish4 v0.1.3 の公式Windows releaseに含まれる eval.bin と book.bin を、商用のオフラインAndroid将棋学習アプリで利用することを検討しています。

MIT LicenseがSunfish4のsource codeに適用されることは確認できましたが、上記2つのruntime dataについて、次の点をご教示いただけますでしょうか。

1. eval.bin と book.bin の作成者・生成元・適用ライセンス
2. 改変、商用利用、再配布の可否
3. Google Play配布するAPK/AABへの同梱可否
4. MIT Licenseと同条件で扱えるか
5. 必要な著作権表示・NOTICE・クレジット表記

許諾条件が確認できるまで、これらのファイルを配布物へ含めることはありません。
よろしくお願いいたします。
```

## 6. Step 7 Gate

|Step 7条件|結果|
|---|---|
|実binary起動|未達（App Control block）|
|USI handshake|未達|
|MultiPV=3実測|未達|
|必要runtime data利用条件|未達（`eval.bin` C: 不明）|
|重大Google Play license blockerなし|未達|

**Gate分類: D — SUNFISH4 NOT SUITABLE。Step 7への進行: 不可。**

この結論はSunfish4のsource code品質を否定するものではなく、G002のGoogle Play向けProduction採用条件を満たせない、というスコープ限定の判定である。

## 7. 追加調査: 作者の公式Issueと問い合わせ経路

2026-09-28に公式GitHub APIで、作者アカウント`sunfish-shogi`（公開名: Kubo, Ryosuke）、repositoryの全公開Issue、Discussion設定、関連履歴を確認した。

- GitHub Discussions: 無効
- 公開email/blog: なし
- 公式問い合わせ経路: GitHub Issue

### Issue #1から確定した技術的事実

作者本人はIssue #1で、`eval.bin`を評価関数のparameters、`book.bin`をopening-book dataと説明している。また、`eval.bin`は`sunfish_ln`、`book.bin`は`sunfish_tools --gen-book`で生成できると説明し、最初の両ファイル入りreleaseとしてv0.1.0を公開した。

スマートフォン利用の質問に対し、作者はAndroid NDK/C++での利用、`USI_Hash`によるメモリ調整、3.5MB程度の軽量`eval.bin`を持つ`v0.1.0-light`/`light-eval` branchを案内している。これはSunfish4がモバイル利用を想定していた一次資料であるが、**商用利用、再配布、APK/AAB同梱、Google Play、MITと同条件か、クレジット義務についての回答は含まれない。**

一次資料:

- <https://github.com/sunfish-shogi/sunfish4/issues/1>
- <https://github.com/sunfish-shogi/sunfish4/releases/tag/v0.1.0-light>
- <https://github.com/sunfish-shogi/sunfish4/tree/light-eval>

### 問い合わせ送信の可否

作者はIssue #1で、追加の質問はnew issueまたはreopen issueで受け付けると案内している。ユーザーは問い合わせを許可しているため、公式GitHubのnew issue画面まで進んだ。

しかし、GitHubは投稿前にユーザーアカウントでのsign-inを要求した。この環境にログイン済みアカウントはなく、認証情報の入力、OAuth、アカウント作成、公開投稿は行っていない。**問い合わせは未送信**である。

投稿可能になった場合は、本資料の第5節の文面をそのままGitHub Issueへ送信する。回答が得られても、各項目（商用、再配布、APK/AAB、Google Play、MIT同条件、クレジット）を個別に満たすまで採用条件を満たしたとは扱わない。

## 8. ログインなし公開情報監査の完了

2026-09-28に、GitHubログインや投稿を行わず、次の公式公開情報を確認した。

|確認対象|確認結果|
|---|---|
|Repository / LICENSE / README|MIT Licenseはsource codeに明記されるが、`eval.bin` / `book.bin`固有のライセンス、再配布条件、クレジット条件はない。|
|全Release本文・asset一覧|v0.1.0、v0.1.0-light、v0.1.1、v0.1.2、v0.1.3の本文・asset名にruntime dataの利用許諾はない。|
|v0.1.0-light Windows assetの内容|`eval.bin`（3,546,199 bytes）と`book.bin`（2,881,571 bytes）を含むが、LICENSE、README、NOTICE、CREDITS等の文書は同梱されない。|
|Repository history / source comments|`eval.bin`と`book.bin`をsource repository内にコミットした履歴、またはdata固有のlicense/noticeは確認できない。|
|公開Issue / Discussions|Issue #1は技術的な生成元・モバイル利用の説明のみ。Discussionsは無効で、利用許諾を定める公開回答はない。|
|現在のWASM README / engine manifest /設計資料|`eval.bin`と`book.bin`を外部配置する技術仕様はあるが、配布、商用利用、クレジットの条件はない。|

### 結論

`book.bin`はsource上、`UseBook=false`で探索を継続できるため任意dataである。一方、通常のUSI探索には`eval.bin`が必要である。しかし、**どちらのdataについても商用利用、再配布、APK/AAB同梱、Google Play配布、適用ライセンス、クレジット要件を許可する公開一次資料は確認できなかった。**

したがって、Sunfish4のruntime dataをG002の配布物へ採用するには、作者本人への個別確認が必要である。これはGitHubへのログイン不足ではなく、公開資料に利用許諾そのものが存在しないためである。現在はユーザー方針に従い、ログイン、認証、投稿を行わない。

将来、ユーザーが投稿を指示しGitHubへログイン済みの場合のみ、公式のIssue作成画面 <https://github.com/sunfish-shogi/sunfish4/issues/new/choose> から第5節の質問を送る。回答では、少なくとも「商用利用」「再配布」「APK/AAB同梱」「Google Play配布」「MITと同条件か」「必要クレジット」を個別に明確化する必要がある。
