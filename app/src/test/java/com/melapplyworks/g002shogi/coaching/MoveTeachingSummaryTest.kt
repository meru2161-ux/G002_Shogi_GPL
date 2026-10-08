package com.melapplyworks.g002shogi.coaching

import com.melapplyworks.g002shogi.analysis.MoveAssessment
import com.melapplyworks.g002shogi.model.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MoveTeachingSummaryTest {
    private fun candidate(score: Double) = CandidateMove(
        id = "best",
        move = Move(Square(7, 7), Square(7, 6), PieceType.PAWN, Player.SENTE),
        purpose = "歩を進める",
        explanation = Explanation("候補", "意味", "狙い"),
        evaluationForFuture = score,
        variation = VariationLine(emptyList(), emptyList())
    )

    @Test fun noCandidateScoreIsExplicitlyUnratedInsteadOfAutomaticallyGood() {
        val summary = MoveTeachingSummaryFactory.from(MoveAssessment(25, emptyList()), null)
        assertEquals(MoveQuality.UNRATED, summary.quality)
        assertTrue(summary.verdict.contains("断定しません"))
        assertTrue(summary.winningPlan.isNotBlank())
    }

    @Test fun scoreGapUsesNonDismissiveCautionAndDangerExplanation() {
        val summary = MoveTeachingSummaryFactory.from(MoveAssessment(-20, emptyList()), candidate(score = 2.0))
        assertEquals(MoveQuality.CAUTION, summary.quality)
        assertTrue(summary.dangerLine.contains("苦しい"))
    }

    @Test fun forcedMateForTheLearnerIsNotShownAsAnOrdinaryLargeScore() {
        val summary = MoveTeachingSummaryFactory.from(MoveAssessment(999_997, emptyList()), candidate(score = 2.0))

        assertEquals(MoveQuality.GOOD, summary.quality)
        assertTrue(summary.verdict.contains("詰み"))
        assertTrue(summary.scorePresentation.contains("詰み"))
        assertFalse(summary.scorePresentation.contains("9999"))
    }

    @Test fun missingTheCandidateMateIsAConcreteCautionNotACentipawnComparison() {
        val summary = MoveTeachingSummaryFactory.from(MoveAssessment(25, emptyList()), candidate(score = 9_999.97))

        assertEquals(MoveQuality.CAUTION, summary.quality)
        assertTrue(summary.verdict.contains("詰み"))
        assertTrue(summary.scorePresentation.contains("詰み"))
    }

    @Test fun forcedMateAgainstTheLearnerUsesAMateWarning() {
        val summary = MoveTeachingSummaryFactory.from(MoveAssessment(-999_997, emptyList()), candidate(score = 2.0))

        assertEquals(MoveQuality.DIFFICULT, summary.quality)
        assertTrue(summary.verdict.contains("詰み"))
        assertTrue(summary.scorePresentation.contains("詰み"))
    }

    @Test fun limitedSearchScoreIsMarkedAsGuidance() {
        val summary = MoveTeachingSummaryFactory.from(
            MoveAssessment(25, emptyList(), reachedSearchLimit = true),
            candidate(score = 0.3)
        )

        assertEquals(MoveQuality.GOOD, summary.quality)
        assertTrue(summary.scorePresentation.contains("探索上限"))
    }
}
