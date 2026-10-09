package com.melapplyworks.g002shogi.coaching

import com.melapplyworks.g002shogi.analysis.MoveAssessment
import com.melapplyworks.g002shogi.model.CandidateMove

/**
 * Keeps a post-move lesson short enough to show over the board.
 * Advancing a page is presentation only; it never changes the game position.
 */
data class TeacherReviewPage(
    val title: String,
    val body: String
)

object TeacherReviewPages {
    fun from(
        comparison: MoveComparison,
        assessment: MoveAssessment?,
        bestCandidate: CandidateMove?
    ): List<TeacherReviewPage> {
        val teaching = assessment?.let { MoveTeachingSummaryFactory.from(it, bestCandidate) }
        val evaluation = teaching?.let {
            "${it.quality.label}：${it.verdict}\n${it.scorePresentation}"
        } ?: comparison.teacherMessage
        val plan = teaching?.winningPlan ?: comparison.continuation
        val danger = teaching?.dangerLine ?: comparison.caution

        return listOf(
            TeacherReviewPage("この手の評価", evaluation),
            TeacherReviewPage("比較と相手の応手", "${comparison.difference}\n相手の有力応手：${comparison.opponentResponse}"),
            TeacherReviewPage("次の方針", "$plan\n注意：$danger")
        )
    }

    fun nextIndex(current: Int, pageCount: Int): Int {
        if (pageCount <= 1) return 0
        return (current.coerceIn(0, pageCount - 1) + 1) % pageCount
    }
}
