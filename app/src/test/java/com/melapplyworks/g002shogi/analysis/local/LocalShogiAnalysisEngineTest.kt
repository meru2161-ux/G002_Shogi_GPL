package com.melapplyworks.g002shogi.analysis.local

import com.melapplyworks.g002shogi.analysis.AnalysisRequest
import com.melapplyworks.g002shogi.analysis.LocalShogiAnalysisEngine
import com.melapplyworks.g002shogi.model.*
import com.melapplyworks.g002shogi.rules.ShogiRules
import org.junit.Assert.*
import org.junit.Test

class LocalShogiAnalysisEngineTest {
    private val engine = LocalShogiAnalysisEngine()
    private fun request(id: String = "test") = AnalysisRequest(id, maxDepth = 1, nodeLimit = 20_000, timeLimitMillis = 2_000)

    @Test fun initialPositionReturnsThreeDistinctLegalCandidates() {
        val position = ShogiPositions.initial()
        val result = engine.analyze(position, request())
        assertFalse(result.isSample)
        assertEquals("test", result.positionId)
        assertEquals(3, result.candidates.size)
        assertTrue(result.candidates.all { ShogiRules.isLegal(position, it.move) })
        assertEquals(3, result.candidates.map { listOf(it.move.from, it.move.to, it.move.piece, it.move.promote) }.toSet().size)
        assertTrue(result.candidates.all { it.variation.moves.first() == it.move })
    }

    @Test fun immediateMateIsPreferredWhenAWinningMoveExists() {
        val position = ShogiPosition(mapOf(
            Square(9, 9) to Piece(PieceType.KING, Player.SENTE),
            Square(5, 1) to Piece(PieceType.KING, Player.GOTE),
            Square(5, 3) to Piece(PieceType.ROOK, Player.SENTE),
            Square(4, 2) to Piece(PieceType.GOLD, Player.SENTE),
            Square(6, 2) to Piece(PieceType.GOLD, Player.SENTE)
        ))
        val result = engine.analyze(position, request("mate"))
        val bestAfter = ShogiRules.apply(position, result.candidates.first().move)
        assertTrue("the top line should choose an immediate mate", ShogiRules.isCheckmate(bestAfter))
    }

    @Test fun cancelledRequestDoesNotReturnStaleCandidates() {
        val result = engine.analyze(ShogiPositions.initial(), request().copy(isCancelled = { true }))
        assertTrue(result.candidates.isEmpty())
    }

    @Test fun shortTimeBudgetStillReturnsThreeLegalTeachingChoices() {
        val position = ShogiPositions.initial()
        val result = engine.analyze(position, request("short").copy(timeLimitMillis = 1))
        assertEquals(3, result.candidates.size)
        assertTrue(result.candidates.all { ShogiRules.isLegal(position, it.move) })
    }

    @Test fun legalLearnerMoveReceivesAnOfflineAssessmentAndPv() {
        val position = ShogiPositions.initial()
        val move = ShogiRules.legalMoves(position).first()
        val assessment = engine.assessMove(position, move, request("review"))
        assertNotNull(assessment)
        val first = assessment!!.principalVariation.first()
        assertEquals(move.from, first.from)
        assertEquals(move.to, first.to)
        assertEquals(move.piece, first.piece)
    }

    @Test fun openingPrefersPrincipledPawnDevelopmentOverAnEarlyLanceMove() {
        val position = ShogiPositions.initial()
        val result = engine.analyze(position, request("opening"))

        assertEquals(Move(Square(7, 7), Square(7, 6), PieceType.PAWN, Player.SENTE), result.candidates.first().move.copy(notation = ""))
        assertTrue(result.candidates.first().purpose.contains("角道"))
        assertTrue(result.candidates.none { it.move.piece == PieceType.LANCE })
        val staticRookCandidate = result.candidates.firstOrNull { it.move.from == Square(2, 7) && it.move.to == Square(2, 6) }
        assertNotNull("the three teaching choices should include the standard static-rook plan", staticRookCandidate)
        assertTrue(staticRookCandidate!!.explanation.body.contains("居飛車"))
    }

    @Test fun goteAnswersBishopPawnOpeningWithARecognizableDevelopmentMove() {
        val initial = ShogiPositions.initial()
        val senteOpening = ShogiRules.legalMoves(initial).single { it.from == Square(7, 7) && it.to == Square(7, 6) }
        val position = ShogiRules.apply(initial, senteOpening)
        val result = engine.analyze(position, request("reply"))

        assertEquals(Move(Square(3, 3), Square(3, 4), PieceType.PAWN, Player.GOTE), result.candidates.first().move.copy(notation = ""))
        assertTrue(result.candidates.first().purpose.contains("角道"))
        assertTrue(result.candidates.none { it.move.piece == PieceType.LANCE })
    }

    @Test fun earlyOpeningCandidatesDoNotWalkTheKingForwardIntoTheFight() {
        var position = ShogiPositions.initial()
        listOf(Square(7, 7) to Square(7, 6), Square(3, 3) to Square(3, 4)).forEach { (from, to) ->
            position = ShogiRules.apply(position, ShogiRules.legalMoves(position).single {
                it.from == from && it.to == to && !it.promote
            })
        }

        val result = engine.analyze(position, request("king-safety"))

        assertTrue(result.candidates.none {
            it.move.piece == PieceType.KING && it.move.from == Square(5, 9) && it.move.to == Square(5, 8)
        })
    }

    @Test fun tenPlyOpeningSelfPlayAvoidsPrematureLanceAndForwardKingMoves() {
        var position = ShogiPositions.initial()
        val played = mutableListOf<Move>()

        repeat(10) { ply ->
            val result = engine.analyze(position, request("opening-self-play-$ply"))
            val move = result.candidates.first().move
            val from = move.from

            assertTrue("ply=$ply must be legal", ShogiRules.isLegal(position, move))
            assertNotEquals("ply=$ply must not waste time on an early lance move", PieceType.LANCE, move.piece)
            if (move.piece == PieceType.KING && from != null) {
                // A diagonal 5九→6八 / 5一→4二 step is a normal route into a castle.
                // Reject only walking straight toward the fight while remaining an exposed central king.
                val walksForward = move.to.file == from.file &&
                    if (move.player == Player.SENTE) move.to.rank < from.rank else move.to.rank > from.rank
                assertFalse("ply=$ply move=$move must castle sideways instead of walking toward the fight", walksForward)
            }
            played += move
            position = ShogiRules.apply(position, move)
        }

        assertTrue("the opening should include an actual king-safety move: $played", played.any { it.piece == PieceType.KING })
    }

    @Test fun quiescenceDoesNotRecommendAOnePlyRookForPawnBlunder() {
        val position = ShogiPosition(mapOf(
            Square(9, 9) to Piece(PieceType.KING, Player.SENTE),
            Square(1, 1) to Piece(PieceType.KING, Player.GOTE),
            Square(5, 5) to Piece(PieceType.ROOK, Player.SENTE),
            Square(5, 4) to Piece(PieceType.PAWN, Player.GOTE),
            Square(4, 3) to Piece(PieceType.GOLD, Player.GOTE)
        ))
        val poisonedCapture = Move(Square(5, 5), Square(5, 4), PieceType.ROOK, Player.SENTE)
        val result = engine.analyze(position, request("qsearch"))

        assertNotEquals(poisonedCapture, result.candidates.first().move.copy(notation = ""))
    }

    @Test fun recentGameHistoryDiscouragesAQuietReturnToThePreviousSquare() {
        var position = ShogiPositions.initial()
        val played = mutableListOf<Move>()
        val usi = listOf("7g7f", "3c3d", "5i6h", "5a4b", "5g5f", "5c5d")
        usi.forEach { token ->
            val move = com.melapplyworks.g002shogi.rules.SfenCodec.parseUsiMove(position, token)!!
            played += move
            position = ShogiRules.applyKnownLegal(position, move)
        }
        val immediateKingReturn = Move(Square(6, 8), Square(5, 9), PieceType.KING, Player.SENTE)
        assertTrue(ShogiRules.isLegal(position, immediateKingReturn))

        val result = engine.analyze(
            position,
            AnalysisRequest("anti-shuffle", maxDepth = 3, nodeLimit = 80_000, timeLimitMillis = 10_000, recentMoves = played)
        )

        assertNotEquals(immediateKingReturn, result.candidates.first().move.copy(notation = ""))
    }

    @Test fun aFreeMajorPieceCaptureIsPreferred() {
        val position = ShogiPosition(mapOf(
            Square(9, 9) to Piece(PieceType.KING, Player.SENTE),
            Square(1, 1) to Piece(PieceType.KING, Player.GOTE),
            Square(5, 5) to Piece(PieceType.BISHOP, Player.SENTE),
            Square(4, 4) to Piece(PieceType.ROOK, Player.GOTE)
        ))
        val result = engine.analyze(position, request("free-rook"))

        assertEquals(Square(5, 5), result.candidates.first().move.from)
        assertEquals(Square(4, 4), result.candidates.first().move.to)
        assertEquals(PieceType.BISHOP, result.candidates.first().move.piece)
    }

    @Test fun defaultMobileBudgetIsLargeEnoughForARealMultiPlySearch() {
        val request = AnalysisRequest()
        assertTrue(request.maxDepth >= 4)
        assertTrue(request.nodeLimit >= 160_000)
        assertTrue(request.timeLimitMillis >= 2_000)
    }

    @Test fun defaultBudgetReturnsThreeLegalCandidatesWithinABoundedTime() {
        val position = ShogiPositions.initial()
        val started = System.nanoTime()
        val result = engine.analyze(position, AnalysisRequest(positionId = "default-budget"))
        val elapsedMillis = (System.nanoTime() - started) / 1_000_000

        assertEquals(3, result.candidates.size)
        assertTrue(result.candidates.all { ShogiRules.isLegal(position, it.move) })
        assertTrue("every candidate PV must include an opponent reply", result.candidates.all { it.variation.moves.size >= 2 })
        assertTrue("elapsed=${elapsedMillis}ms", elapsedMillis < 5_000)
    }

    @Test fun resourceLimitedSearchMarksCandidatesAsGuidanceInsteadOfACompletedSearch() {
        val result = engine.analyze(
            ShogiPositions.initial(),
            AnalysisRequest(positionId = "limited", maxDepth = 4, nodeLimit = 1, timeLimitMillis = 10_000)
        )

        assertTrue(result.reachedSearchLimit)
        assertEquals(3, result.candidates.size)
        assertTrue(result.candidates.all { ShogiRules.isLegal(ShogiPositions.initial(), it.move) })
    }
}
