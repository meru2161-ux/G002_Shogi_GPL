package com.melapplyworks.g002shogi.game

import com.melapplyworks.g002shogi.analysis.AnalysisRequest
import com.melapplyworks.g002shogi.analysis.LocalShogiAnalysisEngine
import com.melapplyworks.g002shogi.analysis.MoveAssessment
import com.melapplyworks.g002shogi.model.*
import com.melapplyworks.g002shogi.rules.ShogiRules
import org.junit.Assert.*
import org.junit.Test

class ShogiGameSessionTest {
    private fun request() = AnalysisRequest(maxDepth = 1, nodeLimit = 20_000, timeLimitMillis = 2_000)
    private fun install(session: ShogiGameSession) {
        assertTrue(session.installAnalysis(session.analyzeCurrent(request())))
    }

    @Test fun restoredGameReplaysOnlyLegalConfirmedMovesAndResumesCorrectTurn() {
        val moves = listOf(
            Move(Square(7, 7), Square(7, 6), PieceType.PAWN, Player.SENTE),
            Move(Square(3, 3), Square(3, 4), PieceType.PAWN, Player.GOTE)
        )
        val session = ShogiGameSession(
            LocalShogiAnalysisEngine(),
            mode = GameMode.AI_MATCH,
            humanPlayer = Player.SENTE,
            restoredMoves = moves
        )

        assertEquals(2, session.state.records.size)
        assertEquals(Player.SENTE, session.state.position.activePlayer)
        assertEquals(SessionPhase.ANALYZING, session.state.phase)
        assertEquals(Piece(PieceType.PAWN, Player.SENTE), session.state.position.board[Square(7, 6)])
        assertEquals(Piece(PieceType.PAWN, Player.GOTE), session.state.position.board[Square(3, 4)])
    }

    @Test(expected = IllegalArgumentException::class)
    fun restoredGameRejectsIllegalMoveInsteadOfCorruptingPosition() {
        ShogiGameSession(
            LocalShogiAnalysisEngine(),
            restoredMoves = listOf(Move(Square(7, 7), Square(7, 5), PieceType.PAWN, Player.SENTE))
        )
    }

    @Test fun coachingLoopStaysLegalForTenPlies() {
        val session = ShogiGameSession(LocalShogiAnalysisEngine(), mode = GameMode.COACHING)
        repeat(5) {
            install(session)
            val userMove = session.state.analysis!!.candidates.first().move
            assertTrue(session.submitHumanMove(userMove))
            assertEquals(SessionPhase.REVIEWING_HUMAN, session.state.phase)
            assertNotNull(session.state.records.last().review)
            assertTrue(session.continueAfterReview())
            assertTrue(session.playAiMove(session.analyzeCurrent(request())))
            assertEquals(SessionPhase.ANALYZING, session.state.phase)
            assertNotNull(session.state.records.last().analysisCandidate)
        }
        assertEquals(10, session.state.records.size)
        assertTrue(session.state.records.all { it.after.activePlayer == it.move.player.opponent() })
    }

    @Test fun aiMatchSkipsReviewAndReturnsToAnalysis() {
        val session = ShogiGameSession(LocalShogiAnalysisEngine(), mode = GameMode.AI_MATCH)
        install(session)
        assertTrue(session.submitHumanMove(session.state.analysis!!.candidates.first().move))
        assertEquals(SessionPhase.AI_THINKING, session.state.phase)
        assertTrue(session.playAiMove(session.analyzeCurrent(request())))
        assertEquals(SessionPhase.ANALYZING, session.state.phase)
        assertEquals(2, session.state.records.size)
    }

    @Test fun resultFromBeforeRestartIsRejectedAsStale() {
        val session = ShogiGameSession(LocalShogiAnalysisEngine())
        val stale = session.analyzeCurrent(request())
        session.restart()
        assertFalse(session.installAnalysis(stale))
    }

    @Test fun olderRequestForTheSamePositionIsRejectedAfterANewerRequestStarts() {
        val session = ShogiGameSession(LocalShogiAnalysisEngine(), mode = GameMode.COACHING)
        val older = session.analyzeCurrent(request())
        val newer = session.analyzeCurrent(request())

        assertFalse(session.installAnalysis(older))
        assertTrue(session.installAnalysis(newer))
    }

    @Test fun undoRebuildsTheInitialPositionWithoutKeepingFutureAnalysis() {
        val session = ShogiGameSession(LocalShogiAnalysisEngine())
        install(session)
        assertTrue(session.submitHumanMove(session.state.analysis!!.candidates.first().move))
        assertTrue(session.undoLastMove())
        assertEquals(0, session.state.records.size)
        assertEquals(ShogiPositions.initial(), session.state.position)
        assertEquals(SessionPhase.ANALYZING, session.state.phase)
        assertNull(session.state.analysis)
    }

    @Test fun checkmateMoveFinishesTheSession() {
        val mateInOne = ShogiPosition(mapOf(
            Square(9, 9) to Piece(PieceType.KING, Player.SENTE),
            Square(5, 1) to Piece(PieceType.KING, Player.GOTE),
            Square(5, 3) to Piece(PieceType.ROOK, Player.SENTE),
            Square(4, 2) to Piece(PieceType.GOLD, Player.SENTE),
            Square(6, 2) to Piece(PieceType.GOLD, Player.SENTE)
        ))
        val session = ShogiGameSession(LocalShogiAnalysisEngine(), initialPosition = mateInOne)
        install(session)
        assertTrue(session.submitHumanMove(session.state.analysis!!.candidates.first().move))
        assertEquals(SessionPhase.FINISHED, session.state.phase)
        assertEquals(SessionOutcome.SENTE_WIN, session.state.outcome)
        assertFalse(session.playAiMove(session.analyzeCurrent(request())))

        session.restart()
        assertEquals(mateInOne, session.state.position)
        assertEquals(SessionPhase.ANALYZING, session.state.phase)
        assertTrue(session.state.records.isEmpty())
        assertNull(session.state.analysis)
        assertNull(session.state.outcome)
    }

    @Test fun sessionAppliesTheChosenOptionalPromotion() {
        val promotionPosition = ShogiPosition(mapOf(
            Square(9, 9) to Piece(PieceType.KING, Player.SENTE),
            Square(1, 1) to Piece(PieceType.KING, Player.GOTE),
            Square(3, 4) to Piece(PieceType.PAWN, Player.SENTE)
        ))
        val session = ShogiGameSession(LocalShogiAnalysisEngine(), initialPosition = promotionPosition, mode = GameMode.AI_MATCH)
        install(session)
        val promote = ShogiRules.legalMoves(session.state.position).single {
            it.from == Square(3, 4) && it.to == Square(3, 3) && it.promote
        }

        assertTrue(session.submitHumanMove(promote))
        assertEquals(Piece(PieceType.PROMOTED_PAWN, Player.SENTE), session.state.position.board[Square(3, 3)])
        assertEquals(SessionPhase.AI_THINKING, session.state.phase)
    }

    @Test fun sessionAcceptsLegalHandDropAndRejectsNifuThroughTheSameMovePath() {
        val handPosition = ShogiPosition(
            board = mapOf(
                Square(9, 9) to Piece(PieceType.KING, Player.SENTE),
                Square(1, 1) to Piece(PieceType.KING, Player.GOTE),
                Square(4, 6) to Piece(PieceType.PAWN, Player.SENTE)
            ),
            hands = mapOf(Player.SENTE to listOf(PieceType.PAWN))
        )
        val session = ShogiGameSession(LocalShogiAnalysisEngine(), initialPosition = handPosition, mode = GameMode.AI_MATCH)
        install(session)
        val legalDrop = ShogiRules.legalMoves(session.state.position).single {
            it.isDrop && it.piece == PieceType.PAWN && it.to == Square(3, 5)
        }

        assertTrue(session.submitHumanMove(legalDrop))
        assertEquals(Piece(PieceType.PAWN, Player.SENTE), session.state.position.board[Square(3, 5)])
        assertTrue(session.state.position.hands[Player.SENTE].orEmpty().isEmpty())

        val nifuSession = ShogiGameSession(LocalShogiAnalysisEngine(), initialPosition = handPosition, mode = GameMode.AI_MATCH)
        install(nifuSession)
        val illegalDrop = Move(null, Square(4, 5), PieceType.PAWN, Player.SENTE)
        assertFalse(nifuSession.submitHumanMove(illegalDrop))
        assertEquals(handPosition, nifuSession.state.position)
    }

    @Test fun sessionAcceptsUnpromotedHandDropInBothModesForBothPlayers() {
        GameMode.entries.forEach { mode ->
            Player.entries.forEach { player ->
                val handPosition = ShogiPosition(
                    board = mapOf(
                        Square(9, 9) to Piece(PieceType.KING, Player.SENTE),
                        Square(1, 1) to Piece(PieceType.KING, Player.GOTE)
                    ),
                    hands = mapOf(player to listOf(PieceType.SILVER)),
                    activePlayer = player
                )
                val session = ShogiGameSession(LocalShogiAnalysisEngine(), initialPosition = handPosition, mode = mode, humanPlayer = player)
                install(session)
                val drop = ShogiRules.legalMoves(session.state.position).single {
                    it.isDrop && it.piece == PieceType.SILVER && it.to == Square(5, 5)
                }

                assertFalse(drop.promote)
                assertTrue(session.submitHumanMove(drop))
                assertEquals(Piece(PieceType.SILVER, player), session.state.position.board[Square(5, 5)])
                assertTrue(session.state.position.hands[player].orEmpty().isEmpty())
                assertEquals(1, session.state.records.size)
                assertTrue(session.state.records.single().move.isDrop)
                assertEquals(if (mode == GameMode.COACHING) SessionPhase.REVIEWING_HUMAN else SessionPhase.AI_THINKING, session.state.phase)
            }
        }
    }

    @Test fun sessionRejectsIllegalDropsWithoutStartingReviewOrAiResponse() {
        GameMode.entries.forEach { mode ->
            Player.entries.forEach { player ->
                val handPosition = ShogiPosition(
                    board = mapOf(
                        Square(9, 9) to Piece(PieceType.KING, Player.SENTE),
                        Square(1, 1) to Piece(PieceType.KING, Player.GOTE)
                    ),
                    hands = mapOf(player to listOf(PieceType.SILVER, PieceType.PAWN)),
                    activePlayer = player
                )
                val session = ShogiGameSession(LocalShogiAnalysisEngine(), initialPosition = handPosition, mode = mode, humanPlayer = player)
                install(session)
                val before = session.state
                val deadRank = if (player == Player.SENTE) 1 else 9
                val illegalDrops = listOf(
                    Move(null, Square(5, 3), PieceType.SILVER, player, promote = true),
                    Move(null, Square(5, deadRank), PieceType.PAWN, player)
                )

                illegalDrops.forEach { move ->
                    assertFalse(session.submitHumanMove(move))
                    assertEquals(before.position, session.state.position)
                    assertEquals(before.phase, session.state.phase)
                    assertEquals(before.positionId, session.state.positionId)
                    assertTrue(session.state.records.isEmpty())
                }
            }
        }
    }

    @Test fun userAssessmentIsStoredOnlyForTheCurrentReview() {
        val session = ShogiGameSession(LocalShogiAnalysisEngine())
        install(session)
        assertTrue(session.submitHumanMove(session.state.analysis!!.candidates.first().move))
        val id = session.state.positionId
        val assessment = MoveAssessment(15, listOf(session.state.records.last().move))
        val result = session.assessLastHumanMove(request())
        assertNotNull(result)
        assertTrue(session.installUserAssessment(result!!.copy(assessment = assessment)))
        assertEquals(assessment, session.state.records.last().assessment)
        assertFalse(session.installUserAssessment(result.copy(assessment = assessment)))
    }

    @Test fun olderReviewForTheSameMoveIsRejectedAfterANewerReviewStarts() {
        val session = ShogiGameSession(LocalShogiAnalysisEngine(), mode = GameMode.COACHING)
        install(session)
        val move = ShogiRules.legalMoves(session.state.position).first()
        assertTrue(session.submitHumanMove(move))
        val older = session.assessLastHumanMove(request())!!
        val newer = session.assessLastHumanMove(request())!!

        assertFalse(session.installUserAssessment(older))
        assertTrue(session.installUserAssessment(newer))
    }
}
