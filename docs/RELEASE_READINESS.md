# G002 v1.1.0 公開準備チェック

最終更新: 2026-10-09

## 今回の提出ビルド

- versionCode: `5`
- versionName: `1.1.0`
- applicationId: `com.melapplyworks.g002shogi`
- 解析方式: オフラインのYaneuraOu KP256 USI engine（ARM64）をprimary、既存の純Kotlin合法手探索をruntime fallbackとして使用。通信・広告・ログイン・課金は含めない。

## Codexが準備済み

- ストア掲載文: `docs/PLAY_STORE_LISTING_ja.md`
- ストアアイコン: `store-assets/g002-play-icon-512.png`
- フィーチャー画像: `store-assets/g002-play-feature-1024x500.png`
- 実アプリ画面の候補キャプチャ: `store-assets/screenshots/`
- プライバシーポリシー原稿: `docs/PRIVACY_POLICY_ja.md`
- 開始画面からプライバシーポリシー全文を読めるようにした。
- `app/build/outputs/bundle/release/app-release.aab` はARM64 engineと`nn.bin`を含む未署名の最新ビルド。公開提出にはこのビルドを既存G002 upload keyで署名する必要がある。
- `app/release/app-release.aab`（SHA-256 `C32B4C762EFC822CBA215897E900558D36E118132D8B333F6057A121CA0653DA`）は既存アップロード証明書で署名済みだが、上記のアプリ内ポリシー表示を追加する前のビルドなので、今回の提出には使わない。

## 公開前に外部サービス上で必要なこと

- [ ] オーナーが管理するサポート用メールアドレスを、ストア掲載情報とプライバシーポリシーに設定する
- [x] プライバシーポリシーは公開GPLソース内にあり、HTTPSで匿名アクセスのHTTP 200を確認済み。URL: `https://raw.githubusercontent.com/meru2161-ux/G002_Shogi_GPL/main/docs/PRIVACY_POLICY_ja.md`。今回の文言修正を公開ソースへ反映してからConsoleへ入力する。
- [ ] Play Consoleでコンテンツレーティング、Data safety、アプリのアクセス、ターゲット年齢層などの質問票に事実どおり回答する
- [ ] G64で、KP256 engineが`usiok` / `readyok`を返し、候補3手とAI応手を返すことを確認する（USB再接続後に実施）
- [x] G64で、G002 app UIDのARM64 KP256 child process起動と、通常AI対局の相手応手後に人間手番へ戻ることを確認済み。候補3手を含むHuman Play全項目は引き続き内部テストで確認する
- [ ] G64で、成り・駒打ち・詰み終局の最終画面を確認する
- [ ] 既存G002 upload keyでversionCode 5のAABへ署名する（新しい鍵を作らない）
- [ ] Play Consoleの内部テストへ署名済みvc5 AABを提出する
- [x] GPLv3 source repositoryを公開済み: `https://github.com/meru2161-ux/G002_Shogi_GPL`。Play掲載時はこのURLとNOTICEをライセンス案内へ反映する

## 今回は含めないもの

- 広告SDK: 追加すると広告識別子・通信・Data safety・プライバシーポリシーが変わるため、別バージョンで安全に導入する
- Google Playへの公開送信は、最新版の再署名とConsoleの必須申告が完了した後に行う。
