package com.melapplyworks.g002shogi.analysis

import com.melapplyworks.g002shogi.model.*

class MockShogiAnalysisEngine : ShogiAnalysisEngine {
    override fun analyze(position: ShogiPosition, request: AnalysisRequest): AnalysisResult {
        fun move(fromFile: Int, fromRank: Int, toFile: Int, toRank: Int, player: Player, notation: String) = Move(Square(fromFile, fromRank), Square(toFile, toRank), PieceType.PAWN, player, notation)
        val sevenSix = move(7, 7, 7, 6, Player.SENTE, "▲７六歩")
        val twoSix = move(2, 7, 2, 6, Player.SENTE, "▲２六歩")
        val fiveSix = move(5, 7, 5, 6, Player.SENTE, "▲５六歩")
        val goteThreeFour = move(3, 3, 3, 4, Player.GOTE, "△３四歩")
        return AnalysisResult(position, listOf(
            CandidateMove("open-bishop", sevenSix, "角道を開く", Explanation("角の働きを準備", "▲７六歩は角道を開く一手です。角が使いやすくなり、今後の攻めや駒組みの選択肢を増やします。\n\nただし、この一手だけで戦法が決まるわけではありません。相手の応手を見ながら次の方針を考えます。", "相手が△３四歩なら、互いに角道が開きます。"), null, VariationLine(listOf(sevenSix, goteThreeFour, twoSix), listOf("まず角道を開きます。", "相手も角道を開く応手の例です。", "飛車先も伸ばして選択肢を広げます。"))),
            CandidateMove("rook-pawn", twoSix, "飛車先の歩を進める", Explanation("飛車の通り道を作る", "▲２六歩は飛車先を少しずつ前へ進める基本的な構想です。飛車を使う準備をしながら、相手の出方を待てます。", "次は相手の駒組みを見て方針を選びます。"), null, VariationLine(listOf(twoSix, goteThreeFour), listOf("飛車先の歩を進めます。", "相手の角道を開く応手の例です。"))),
            CandidateMove("center-pawn", fiveSix, "中央の歩を進める", Explanation("中央の選択肢を残す", "▲５六歩は中央に余地を作るサンプルです。早く結論を出しすぎず、駒組みの方向を保留できます。", "相手の応手に応じて、駒組みを具体化します。"), null, VariationLine(listOf(fiveSix, goteThreeFour), listOf("中央の歩を一歩進めます。", "相手の応手を確認します。")))
        ), true, request.positionId, request.requestId)
    }
}
