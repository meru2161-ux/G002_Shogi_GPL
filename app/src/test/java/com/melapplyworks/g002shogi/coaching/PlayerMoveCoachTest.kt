package com.melapplyworks.g002shogi.coaching

import com.melapplyworks.g002shogi.model.*
import org.junit.Assert.*
import org.junit.Test

class PlayerMoveCoachTest {
    private val source = ShogiPosition(mapOf(Square(5, 9) to Piece(PieceType.KING, Player.SENTE), Square(5, 1) to Piece(PieceType.KING, Player.GOTE)))
    private val candidateA = Move(Square(7, 7), Square(7, 6), PieceType.PAWN, Player.SENTE)
    private val candidateB = Move(Square(2, 7), Square(2, 6), PieceType.PAWN, Player.SENTE)
    private val candidateC = Move(Square(5, 7), Square(5, 6), PieceType.PAWN, Player.SENTE)

    private fun analysis(positionId: String = "A") = AnalysisResult(source, listOf(candidate(candidateA, "A"), candidate(candidateB, "B"), candidate(candidateC, "C")), true, positionId)
    private fun candidate(move: Move, id: String) = CandidateMove(id, move, "狙い$id", Explanation("見出し", "意味$id", "次$id", intent = "候補$id の狙い", opponentResponse = "応手$id", continuation = "展開$id", caution = "注意$id"), variation = VariationLine(listOf(move), listOf("読み$id")), rankForFuture = if (id == "A") 1 else if (id == "B") 2 else 3, positionId = "A", comparisonSummary = "比較$id")
    private fun record(move: Move, positionId: String = "A", result: AnalysisResult = analysis()) = PlayerMoveCoach.record(positionId, source, move, source.copy(activePlayer = Player.GOTE), result)

    @Test fun matchingCandidateIsRecognized() {
        val review = record(candidateA)
        val comparison = PlayerMoveCoach.compare(review, 0)
        assertTrue(review.isCandidateMatch)
        assertTrue(comparison.sameMove)
        assertTrue(comparison.teacherMessage.contains("一致"))
    }

    @Test fun nonCandidateMoveIsNotLabelledBad() {
        val review = record(Move(Square(4, 7), Square(4, 6), PieceType.PAWN, Player.SENTE))
        val comparison = PlayerMoveCoach.compare(review, 0)
        assertFalse(review.isCandidateMatch)
        assertFalse(comparison.teacherMessage.contains("悪手"))
        assertTrue(comparison.teacherMessage.contains("狙い"))
    }

    @Test fun userCaptureIsExplainedAsCaptureInsteadOfGenericMovement() {
        val tacticalSource = source.copy(board = source.board +
            (Square(5, 5) to Piece(PieceType.ROOK, Player.SENTE)) +
            (Square(5, 4) to Piece(PieceType.SILVER, Player.GOTE)))
        val move = Move(Square(5, 5), Square(5, 4), PieceType.ROOK, Player.SENTE)
        val after = tacticalSource.copy(board = tacticalSource.board - Square(5, 5) +
            (Square(5, 4) to Piece(PieceType.ROOK, Player.SENTE)), activePlayer = Player.GOTE)
        val review = PlayerMoveCoach.record("A", tacticalSource, move, after, analysis())

        assertTrue(PlayerMoveCoach.compare(review, 0).userIntent.contains("銀を取り"))
    }

    @Test fun userDropIsExplainedAsAHandPiecePlan() {
        val move = Move(null, Square(5, 5), PieceType.SILVER, Player.SENTE)
        val after = source.copy(board = source.board + (Square(5, 5) to Piece(PieceType.SILVER, Player.SENTE)), activePlayer = Player.GOTE)
        val review = PlayerMoveCoach.record("A", source, move, after, analysis())

        assertTrue(PlayerMoveCoach.compare(review, 0).userIntent.contains("持ち駒の銀"))
    }

    @Test fun immediatelyRecapturableMoveGetsASpecificWarning() {
        val tacticalSource = ShogiPosition(mapOf(
            Square(9, 9) to Piece(PieceType.KING, Player.SENTE),
            Square(1, 1) to Piece(PieceType.KING, Player.GOTE),
            Square(5, 5) to Piece(PieceType.ROOK, Player.SENTE),
            Square(5, 4) to Piece(PieceType.PAWN, Player.GOTE),
            Square(4, 3) to Piece(PieceType.GOLD, Player.GOTE)
        ))
        val move = Move(Square(5, 5), Square(5, 4), PieceType.ROOK, Player.SENTE)
        val after = ShogiPosition(tacticalSource.board - Square(5, 5) +
            (Square(5, 4) to Piece(PieceType.ROOK, Player.SENTE)), activePlayer = Player.GOTE)
        val review = PlayerMoveCoach.record("A", tacticalSource, move, after, analysis())

        assertTrue(PlayerMoveCoach.compare(review, 0).caution.contains("取り返される"))
    }

    @Test fun comparisonTargetSwitchesBetweenCandidates() {
        val review = record(Move(Square(4, 7), Square(4, 6), PieceType.PAWN, Player.SENTE))
        assertEquals(candidateB, PlayerMoveCoach.compare(review, 1).candidate?.move)
        assertEquals(candidateC, PlayerMoveCoach.compare(review, 2).candidate?.move)
    }

    @Test fun mismatchedPositionIdDoesNotUseCandidates() {
        val review = record(candidateA, positionId = "B", result = analysis("A"))
        assertTrue(review.candidatesAtSource.isEmpty())
        assertNull(PlayerMoveCoach.compare(review, 0).candidate)
    }

    @Test fun missingComparisonCandidateDoesNotCrash() {
        val comparison = PlayerMoveCoach.compare(record(candidateA), 9)
        assertNull(comparison.candidate)
        assertTrue(comparison.teacherMessage.isNotBlank())
    }

    @Test fun reviewRetainsPositionBeforeAndAfterUserMove() {
        val after = source.copy(activePlayer = Player.GOTE)
        val review = PlayerMoveCoach.record("A", source, candidateA, after, analysis())
        assertSame(source, review.sourcePosition)
        assertSame(after, review.resultingPosition)
        assertEquals("A", review.sourcePositionId)
    }

    @Test fun identicalMoveAtDifferentPositionIdsIsSeparateStudyData() {
        val history = StudyMoveHistory()
        history.add(record(candidateA, "A", analysis("A")))
        history.add(record(candidateA, "B", analysis("B")))
        assertEquals(2, history.reviews.size)
        assertNotEquals(history.reviews[0].sourcePositionId, history.reviews[1].sourcePositionId)
    }
}
