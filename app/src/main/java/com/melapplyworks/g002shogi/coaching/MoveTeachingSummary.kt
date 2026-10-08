package com.melapplyworks.g002shogi.coaching

import com.melapplyworks.g002shogi.analysis.MoveAssessment
import com.melapplyworks.g002shogi.model.CandidateMove

/** Short, non-dismissive teaching wording derived from the learner move re-search. */
enum class MoveQuality(val label: String) {
    UNRATED("評価保留"), GOOD("良い手"), NATURAL("自然な手"), CAUTION("注意が必要な手"), DIFFICULT("苦しくなりやすい手")
}

data class MoveTeachingSummary(
    val quality: MoveQuality,
    val verdict: String,
    val winningPlan: String,
    val dangerLine: String,
    /** Keeps forced mates separate from ordinary centipawn-like score guidance. */
    val scorePresentation: String = "解析の目安"
)

object MoveTeachingSummaryFactory {
    private const val MATE_SCORE_THRESHOLD = 900_000

    fun from(assessment: MoveAssessment, bestCandidate: CandidateMove?): MoveTeachingSummary {
        val bestScore = bestCandidate?.evaluationForFuture?.times(100)?.toInt()
        val difference = bestScore?.let { assessment.scoreForMover - it }
        val next = assessment.principalVariation.getOrNull(1)?.notation
        val moverHasMate = assessment.scoreForMover >= MATE_SCORE_THRESHOLD
        val moverFacesMate = assessment.scoreForMover <= -MATE_SCORE_THRESHOLD
        val candidateHasMate = bestScore != null && bestScore >= MATE_SCORE_THRESHOLD
        val quality = when {
            moverHasMate -> MoveQuality.GOOD
            candidateHasMate -> MoveQuality.CAUTION
            moverFacesMate -> MoveQuality.DIFFICULT
            difference == null -> MoveQuality.UNRATED
            difference >= -30 -> MoveQuality.GOOD
            difference >= -120 -> MoveQuality.NATURAL
            difference >= -260 -> MoveQuality.CAUTION
            else -> MoveQuality.DIFFICULT
        }
        val moveAhead = next?.let { "読み筋の $it を目安に" } ?: "自分の玉を守りながら"
        val scorePresentation = when {
            moverHasMate -> "この読み筋では詰みを確認"
            candidateHasMate -> "候補①には詰みを含む読みがあります"
            moverFacesMate -> "この読み筋には相手の詰み筋があります"
            assessment.reachedSearchLimit -> "探索上限に達したため、目安 ${String.format("%+.1f", assessment.scoreForMover / 100.0)}"
            else -> "目安 ${String.format("%+.1f", assessment.scoreForMover / 100.0)}"
        }
        return when (quality) {
            MoveQuality.UNRATED -> MoveTeachingSummary(
                quality,
                "同じ局面の候補評価がそろっていないため、この手の良し悪しは断定しません。",
                "$moveAhead、相手の応手を確認してから方針を決めましょう。",
                "解析根拠が不足している間は、駒損と自玉への王手を優先して確認します。",
                scorePresentation
            )
            MoveQuality.GOOD -> MoveTeachingSummary(
                quality,
                if (moverHasMate) "この読み筋では、あなたの手から詰みを確認しています。" else "候補手と比べても、局面の方針を大きく損ねていません。",
                "$moveAhead、働いていない駒を攻めに参加させて有利を広げましょう。",
                "急ぎすぎて玉まわりを薄くしないことが大切です。",
                scorePresentation
            )
            MoveQuality.NATURAL -> MoveTeachingSummary(
                quality,
                "すぐに悪くはありませんが、候補①より効率が下がる可能性があります。",
                "$moveAhead、相手より先に駒の働きをつなげる形を目指しましょう。",
                "相手に先手を取られると、こちらの駒組みが遅れて苦しくなる流れがあります。",
                scorePresentation
            )
            MoveQuality.CAUTION -> MoveTeachingSummary(
                quality,
                if (candidateHasMate) "候補①には詰みを含む読みがあります。この手では同じ詰みを確認できていません。" else "狙いは分かりますが、注意が必要な手です。候補①より相手の反撃を許しやすくなります。",
                "$moveAhead、まず相手の反撃を受け止められる駒の配置を作りましょう。",
                "備えが遅れると、相手に駒を先に働かせられ、受けに回る苦しい流れになります。",
                scorePresentation
            )
            MoveQuality.DIFFICULT -> MoveTeachingSummary(
                quality,
                if (moverFacesMate) "この読み筋には相手の詰み筋があります。まず王手と逃げ道を確認しましょう。" else "この手は苦しくなりやすい手です。ただし、次の備えを急げば立て直す余地はあります。",
                "$moveAhead、玉の安全を優先して、相手の狙いを一つずつ消していきましょう。",
                "このまま攻め急ぐと、相手の反撃で駒損や玉の薄さが表面化する負け筋があります。",
                scorePresentation
            )
        }
    }
}
