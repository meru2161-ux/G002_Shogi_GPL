package com.melapplyworks.g002shogi.rules

import com.melapplyworks.g002shogi.model.*
import org.junit.Assert.*
import org.junit.Test

class ShogiRulesTest {
    private fun position(vararg pieces: Pair<Square, Piece>, hands: Map<Player, List<PieceType>> = emptyMap()) =
        ShogiPosition(mapOf(*pieces), hands, Player.SENTE)
    private fun sente(type: PieceType) = Piece(type, Player.SENTE)
    private fun gote(type: PieceType) = Piece(type, Player.GOTE)
    private val senteKing = Square(5, 9) to sente(PieceType.KING)
    private val goteKing = Square(5, 1) to gote(PieceType.KING)

    @Test fun knownLegalFastPathMatchesCheckedApplyForGeneratedMoves() {
        val positions = listOf(
            ShogiPositions.initial(),
            position(
                senteKing,
                goteKing,
                Square(5, 5) to sente(PieceType.ROOK),
                Square(4, 4) to gote(PieceType.SILVER),
                Square(3, 4) to sente(PieceType.PAWN),
                hands = mapOf(Player.SENTE to listOf(PieceType.GOLD, PieceType.PAWN))
            ),
            ShogiPosition(
                board = mapOf(
                    Square(9, 9) to sente(PieceType.KING),
                    Square(1, 1) to gote(PieceType.KING),
                    Square(7, 6) to gote(PieceType.BISHOP),
                    Square(6, 7) to sente(PieceType.PROMOTED_PAWN)
                ),
                hands = mapOf(Player.GOTE to listOf(PieceType.KNIGHT, PieceType.LANCE)),
                activePlayer = Player.GOTE
            )
        )

        positions.forEach { current ->
            val legal = ShogiRules.legalMoves(current)
            assertTrue(legal.isNotEmpty())
            legal.forEach { move ->
                assertEquals(
                    "fast path differs for $move",
                    ShogiRules.apply(current, move),
                    ShogiRules.applyKnownLegal(current, move)
                )
            }
        }
    }

    @Test fun pawnMovesForwardButNotBackward() {
        val p = position(senteKing, goteKing, Square(7, 7) to sente(PieceType.PAWN))
        val moves = ShogiRules.legalMoves(p)
        assertTrue(moves.any { it.from == Square(7, 7) && it.to == Square(7, 6) })
        assertFalse(moves.any { it.from == Square(7, 7) && it.to == Square(7, 8) })
    }

    @Test fun capturedPieceBecomesUnpromotedHandPiece() {
        val p = position(senteKing, goteKing, Square(5, 5) to sente(PieceType.ROOK), Square(5, 4) to gote(PieceType.PROMOTED_PAWN))
        val after = ShogiRules.apply(p, Move(Square(5, 5), Square(5, 4), PieceType.ROOK, Player.SENTE))
        assertEquals(Piece(PieceType.ROOK, Player.SENTE), after.board[Square(5, 4)])
        assertEquals(listOf(PieceType.PAWN), after.hands[Player.SENTE])
        assertEquals(Player.GOTE, after.activePlayer)
    }

    @Test fun dropRejectsNifuAndDeadEndPawn() {
        val p = position(senteKing, goteKing, Square(4, 6) to sente(PieceType.PAWN), hands = mapOf(Player.SENTE to listOf(PieceType.PAWN)))
        val drops = ShogiRules.legalMoves(p).filter { it.isDrop && it.piece == PieceType.PAWN }
        assertFalse(drops.any { it.to.file == 4 })
        assertFalse(drops.any { it.to.rank == 1 })
        assertTrue(drops.any { it.to == Square(3, 5) })
    }

    @Test fun lastRankPawnRequiresPromotion() {
        val p = position(senteKing, goteKing, Square(3, 2) to sente(PieceType.PAWN))
        val moves = ShogiRules.legalMoves(p).filter { it.from == Square(3, 2) && it.to == Square(3, 1) }
        assertEquals(1, moves.size)
        assertTrue(moves.single().promote)
    }

    @Test fun movingPinnedPieceIsRejected() {
        val p = position(senteKing, goteKing, Square(5, 8) to sente(PieceType.GOLD), Square(5, 3) to gote(PieceType.ROOK))
        assertTrue(ShogiRules.isInCheck(p, Player.SENTE).not())
        assertFalse(ShogiRules.legalMoves(p).any { it.from == Square(5, 8) && it.to == Square(4, 8) })
    }

    @Test fun promotionOffersBothChoicesWhenOptional() {
        val p = position(senteKing, goteKing, Square(3, 4) to sente(PieceType.PAWN))
        val moves = ShogiRules.legalMoves(p).filter { it.from == Square(3, 4) && it.to == Square(3, 3) }
        assertEquals(setOf(false, true), moves.map { it.promote }.toSet())
    }

    @Test fun kingCannotMoveIntoAnAttackedSquare() {
        val p = position(senteKing, goteKing, Square(5, 5) to gote(PieceType.ROOK))
        assertFalse(ShogiRules.legalMoves(p).any { it.from == Square(5, 9) && it.to == Square(5, 8) })
    }

    @Test fun checkedSideOnlyGetsEvasions() {
        val p = position(senteKing, goteKing, Square(5, 5) to gote(PieceType.ROOK))
        val evasions = ShogiRules.legalMoves(p)
        assertTrue(ShogiRules.isInCheck(p, Player.SENTE))
        assertTrue(evasions.isNotEmpty())
        assertTrue(evasions.all { it.from == Square(5, 9) })
    }

    @Test fun lanceAndKnightDropsRejectDeadRanks() {
        val p = position(senteKing, goteKing, hands = mapOf(Player.SENTE to listOf(PieceType.LANCE, PieceType.KNIGHT)))
        val drops = ShogiRules.legalMoves(p).filter { it.isDrop }
        assertFalse(drops.any { it.piece == PieceType.LANCE && it.to.rank == 1 })
        assertFalse(drops.any { it.piece == PieceType.KNIGHT && it.to.rank <= 2 })
    }

    @Test fun promotedDropRequestIsRejectedWithoutChangingPosition() {
        Player.entries.forEach { player ->
            val p = ShogiPosition(
                board = mapOf(
                    Square(9, 9) to sente(PieceType.KING),
                    Square(1, 1) to gote(PieceType.KING)
                ),
                hands = mapOf(player to listOf(PieceType.SILVER)),
                activePlayer = player
            )
            val illegal = Move(null, Square(5, 5), PieceType.SILVER, player, promote = true)
            assertFalse(ShogiRules.isLegal(p, illegal))
            assertThrows(IllegalArgumentException::class.java) { ShogiRules.apply(p, illegal) }
            assertEquals(listOf(PieceType.SILVER), p.hands[player])
            assertFalse(p.board.containsKey(Square(5, 5)))
            assertEquals(player, p.activePlayer)
        }
    }

    @Test fun droppedPawnCanPromoteNormallyOnALaterBoardMove() {
        data class Case(val player: Player, val drop: Square, val replyFrom: Square, val replyTo: Square, val moveTo: Square)
        listOf(
            Case(Player.SENTE, Square(4, 4), Square(1, 1), Square(1, 2), Square(4, 3)),
            Case(Player.GOTE, Square(4, 6), Square(9, 9), Square(9, 8), Square(4, 7))
        ).forEach { case ->
            val start = ShogiPosition(
                board = mapOf(
                    Square(9, 9) to sente(PieceType.KING),
                    Square(1, 1) to gote(PieceType.KING)
                ),
                hands = mapOf(case.player to listOf(PieceType.PAWN)),
                activePlayer = case.player
            )
            val afterDrop = ShogiRules.apply(start, Move(null, case.drop, PieceType.PAWN, case.player))
            val afterReply = ShogiRules.apply(afterDrop, Move(case.replyFrom, case.replyTo, PieceType.KING, case.player.opponent()))
            val laterMoves = ShogiRules.legalMoves(afterReply).filter { it.from == case.drop && it.to == case.moveTo }

            assertEquals(case.player.toString(), setOf(false, true), laterMoves.map { it.promote }.toSet())
        }
    }

    @Test fun deadEndDropsAreRejectedForBothPlayersWithoutChangingPosition() {
        Player.entries.forEach { player ->
            val p = ShogiPosition(
                board = mapOf(
                    Square(9, 9) to sente(PieceType.KING),
                    Square(1, 1) to gote(PieceType.KING)
                ),
                hands = mapOf(player to listOf(PieceType.PAWN, PieceType.LANCE, PieceType.KNIGHT)),
                activePlayer = player
            )
            val lastRank = if (player == Player.SENTE) 1 else 9
            val nextToLastRank = if (player == Player.SENTE) 2 else 8
            val illegalDrops = listOf(
                Move(null, Square(5, lastRank), PieceType.PAWN, player),
                Move(null, Square(6, lastRank), PieceType.LANCE, player),
                Move(null, Square(7, lastRank), PieceType.KNIGHT, player),
                Move(null, Square(7, nextToLastRank), PieceType.KNIGHT, player)
            )

            illegalDrops.forEach { move ->
                assertFalse("$player $move", ShogiRules.isLegal(p, move))
                assertThrows(IllegalArgumentException::class.java) { ShogiRules.apply(p, move) }
                assertEquals(p, p.copy())
            }
        }
    }

    @Test fun fourfoldSamePositionIsDrawWhenThereIsNoContinuousCheck() {
        val p = position(senteKing, goteKing)
        val tracker = RepetitionTracker()
        assertEquals(RepetitionResult.NONE, tracker.record(p, null, false))
        assertEquals(RepetitionResult.NONE, tracker.record(p, Player.SENTE, false))
        assertEquals(RepetitionResult.NONE, tracker.record(p, Player.GOTE, false))
        assertEquals(RepetitionResult.DRAW, tracker.record(p, Player.SENTE, false))
    }

    @Test fun pawnDropMateIsIllegal() {
        val p = position(
            Square(9, 9) to sente(PieceType.KING), Square(5, 1) to gote(PieceType.KING),
            Square(4, 9) to sente(PieceType.ROOK), Square(6, 9) to sente(PieceType.ROOK), Square(5, 3) to sente(PieceType.GOLD),
            hands = mapOf(Player.SENTE to listOf(PieceType.PAWN))
        )
        assertFalse(ShogiRules.legalMoves(p).any { it.isDrop && it.piece == PieceType.PAWN && it.to == Square(5, 2) })
    }

    @Test fun checkingPawnDropIsLegalWhenKingCanEscape() {
        val p = position(Square(9, 9) to sente(PieceType.KING), Square(5, 1) to gote(PieceType.KING), hands = mapOf(Player.SENTE to listOf(PieceType.PAWN)))
        assertTrue(ShogiRules.legalMoves(p).any { it.isDrop && it.piece == PieceType.PAWN && it.to == Square(5, 2) })
    }

    @Test fun checkingPawnDropIsLegalWhenKingCanCapturePawn() {
        val p = position(Square(9, 9) to sente(PieceType.KING), Square(5, 1) to gote(PieceType.KING), hands = mapOf(Player.SENTE to listOf(PieceType.PAWN)))
        val drop = ShogiRules.legalMoves(p).single { it.isDrop && it.piece == PieceType.PAWN && it.to == Square(5, 2) }
        val after = ShogiRules.apply(p, drop)
        assertTrue(ShogiRules.legalMoves(after).any { it.from == Square(5, 1) && it.to == Square(5, 2) })
    }

    @Test fun nifuStillRejectsCheckingPawnDrop() {
        val p = position(Square(9, 9) to sente(PieceType.KING), Square(5, 1) to gote(PieceType.KING), Square(5, 6) to sente(PieceType.PAWN), hands = mapOf(Player.SENTE to listOf(PieceType.PAWN)))
        assertFalse(ShogiRules.legalMoves(p).any { it.isDrop && it.piece == PieceType.PAWN && it.to == Square(5, 2) })
    }

    @Test fun boxedKingInCheckIsCheckmate() {
        val p = ShogiPosition(mapOf(
            Square(9, 9) to sente(PieceType.KING), Square(5, 1) to gote(PieceType.KING), Square(5, 2) to sente(PieceType.PAWN),
            Square(4, 9) to sente(PieceType.ROOK), Square(6, 9) to sente(PieceType.ROOK), Square(5, 3) to sente(PieceType.GOLD)
        ), activePlayer = Player.GOTE)
        assertTrue(ShogiRules.isCheckmate(p))
    }

    @Test fun checkIsNotMateWhenKingCanEscape() {
        val p = ShogiPosition(mapOf(Square(9, 9) to sente(PieceType.KING), Square(5, 1) to gote(PieceType.KING), Square(5, 2) to sente(PieceType.PAWN)), activePlayer = Player.GOTE)
        assertTrue(ShogiRules.isInCheck(p, Player.GOTE))
        assertFalse(ShogiRules.isCheckmate(p))
    }

    @Test fun checkIsNotMateWhenCheckingPieceCanBeCaptured() {
        val p = ShogiPosition(mapOf(Square(9, 9) to sente(PieceType.KING), Square(5, 1) to gote(PieceType.KING), Square(5, 2) to sente(PieceType.ROOK)), activePlayer = Player.GOTE)
        assertFalse(ShogiRules.isCheckmate(p))
        assertTrue(ShogiRules.legalMoves(p).any { it.from == Square(5, 1) && it.to == Square(5, 2) })
    }

    @Test fun checkIsNotMateWhenInterpositionIsPossible() {
        val p = ShogiPosition(mapOf(Square(9, 9) to sente(PieceType.KING), Square(5, 1) to gote(PieceType.KING), Square(5, 5) to sente(PieceType.ROOK), Square(4, 2) to gote(PieceType.GOLD)), activePlayer = Player.GOTE)
        assertFalse(ShogiRules.isCheckmate(p))
        assertTrue(ShogiRules.legalMoves(p).any { it.from == Square(4, 2) && it.to == Square(5, 2) })
    }

    @Test fun fourfoldContinuousSenteChecksLoseForSente() {
        val p = position(senteKing, goteKing)
        val tracker = RepetitionTracker()
        assertEquals(RepetitionResult.NONE, tracker.record(p, null, false))
        assertEquals(RepetitionResult.NONE, tracker.record(p, Player.SENTE, true))
        assertEquals(RepetitionResult.NONE, tracker.record(p, Player.GOTE, false))
        assertEquals(RepetitionResult.SENTE_LOSES_BY_PERPETUAL_CHECK, tracker.record(p, Player.SENTE, true))
    }

    @Test fun repetitionWithInterruptedChecksIsDraw() {
        val p = position(senteKing, goteKing)
        val tracker = RepetitionTracker()
        tracker.record(p, null, false)
        tracker.record(p, Player.SENTE, true)
        tracker.record(p, Player.GOTE, false)
        assertEquals(RepetitionResult.DRAW, tracker.record(p, Player.SENTE, false))
    }

    @Test fun lanceAndKnightMustPromoteOnDeadRanks() {
        val lance = position(senteKing, goteKing, Square(4, 2) to sente(PieceType.LANCE))
        val knight = position(senteKing, goteKing, Square(4, 3) to sente(PieceType.KNIGHT))
        assertEquals(listOf(true), ShogiRules.legalMoves(lance).filter { it.from == Square(4, 2) && it.to == Square(4, 1) }.map { it.promote })
        assertTrue(ShogiRules.legalMoves(knight).filter { it.from == Square(4, 3) }.all { it.promote })
    }

    @Test fun silverCanChoosePromotionAfterEnteringZone() {
        val p = position(senteKing, goteKing, Square(4, 4) to sente(PieceType.SILVER))
        val moves = ShogiRules.legalMoves(p).filter { it.from == Square(4, 4) && it.to == Square(4, 3) }
        assertEquals(setOf(false, true), moves.map { it.promote }.toSet())
    }
}
