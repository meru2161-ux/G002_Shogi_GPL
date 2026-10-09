package com.melapplyworks.g002shogi.coaching

import com.melapplyworks.g002shogi.analysis.MoveAssessment
import com.melapplyworks.g002shogi.model.CandidateMove
import com.melapplyworks.g002shogi.model.Explanation
import com.melapplyworks.g002shogi.model.Move
import com.melapplyworks.g002shogi.model.PieceType
import com.melapplyworks.g002shogi.model.Player
import com.melapplyworks.g002shogi.model.Square
import com.melapplyworks.g002shogi.model.VariationLine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TeacherReviewPagesTest {
    private val move = Move(Square(7, 7), Square(7, 6), PieceType.PAWN, Player.SENTE)
    private val candidate = CandidateMove(
        id = "best",
        move = move,
        purpose = "角道を開く",
        explanation = Explanation("意味", "狙い", "相手の応手", continuation = "次の構想", caution = "急がない"),
        evaluationForFuture = 0.5,
        variation = VariationLine(emptyList(), emptyList())
    )
    private val comparison = MoveComparison(
        sourcePositionId = "p1",
        userMove = move,
        candidate = candidate,
        candidateIndex = 0,
        sameMove = true,
        userIntent = "歩を進める",
        candidateIntent = "角道を開く",
        difference = "先生候補と同じ指し手です。",
        caution = "駒損に注意します。",
        opponentResponse = "相手は角道を開く手を考えます。",
        continuation = "次は飛車先を伸ばします。",
        teacherMessage = "候補と一致しています。"
    )

    @Test fun reviewIsSplitIntoEvaluationResponseAndPlanPages() {
        val pages = TeacherReviewPages.from(comparison, MoveAssessment(20, emptyList()), candidate)

        assertEquals(listOf("この手の評価", "比較と相手の応手", "次の方針"), pages.map { it.title })
        assertTrue(pages[1].body.contains("相手の有力応手"))
        assertTrue(pages[2].body.contains("注意："))
    }

    @Test fun pageAdvanceCyclesWithoutChangingAnyGameData() {
        assertEquals(1, TeacherReviewPages.nextIndex(0, 3))
        assertEquals(0, TeacherReviewPages.nextIndex(2, 3))
        assertEquals(0, TeacherReviewPages.nextIndex(8, 1))
    }
}
