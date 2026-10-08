# Phase 4: 将棋解析エンジン候補の事前評価

調査日: 2026-09-27

## 結論

現時点では、アプリへのエンジン組み込みは行わない。`ShogiAnalysisEngine` の抽象化を維持し、ライセンス・評価関数・ARM64の実測容量を固定した後に判断する。

## 候補と確認済み事項

|候補|Android / ARM64|USI / 複数候補|ライセンス / Play公開への影響|判断|
|---|---|---|---|---|
|やねうら王 (公式)|C++のためNDK/CMakeでのARM64ビルドは技術的に可能。ただし本プロジェクトでは未検証。|公式説明はUSI準拠。MultiPVは実機ビルド後に`usi`のoption出力で確認が必要。|GPL v3。静的リンクを伴うAndroid統合例もGPL v3対応ソース公開を明記している。|導入保留。公開方針への重大なライセンス判断が必要。|
|やねうら王 + SuishoPetite WASM派生|Androidアプリ内オフライン実装としては未検証。Web向けにSIMD/非SIMD配布あり。|USIメッセージの送受信例あり。MultiPVの確認は未実施。|GPL-3.0。Webサーバ要件も記載され、ネイティブAndroid組込み候補には適さない。|不採用。|
|自作の軽量解析器|KotlinのみでARM64依存なし。|本アプリ側で候補手を生成できるが、実力・MultiPV品質は限定的。|G002独自コードなら公開方針との整合を保てる。|Phase 3の教育用ヒューリスティックとして検討可。エンジン代替とは扱わない。|

## アーキテクチャ要件

- UIは `ShogiAnalysisEngine` だけに依存し、USI・JNI・モデルファイルを直接参照しない。
- 実エンジンは `position` / `go` / `info` / `bestmove` を橋渡しする専用アダプタに隔離する。
- MultiPV、深さ、ノード数、探索時間、評価の単位、読み筋を `AnalysisResult` へ正規化する。
- ARM64実機で、初回起動、メモリ、熱、応答時間、APK増分サイズを測定してから採用する。
- 評価関数・定跡・エンジン本体は別々にライセンスと配布可否を確認する。

## 一次資料

- やねうら王公式GitHub: https://github.com/yaneurao/YaneuraOu
- やねうら王公式USI説明: https://github.com/yaneurao/YaneuraOu/wiki/USI%E6%8B%A1%E5%BC%B5%E3%82%B3%E3%83%9E%E3%83%B3%E3%83%89
- Android NDK/CMake統合とGPL v3対応の実例: https://github.com/irep-takeshi-maya/shogi-vault-gpl-source
- WASM派生のライセンス・実行要件: https://github.com/usumerican/yaneuraou-suisho-petite

この文書は候補の採用承認ではない。GPL導入、評価関数取得、NDK追加、モデル・System Imageの大容量取得は未実施。
