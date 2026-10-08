package com.melapplyworks.g002shogi.ui

import com.melapplyworks.g002shogi.model.*
import com.melapplyworks.g002shogi.rules.ShogiRules
import org.junit.Assert.*
import org.junit.Test

class ShogiMoveSelectionTest {
    private val handTypes = listOf(
        PieceType.ROOK, PieceType.BISHOP, PieceType.GOLD, PieceType.SILVER,
        PieceType.KNIGHT, PieceType.LANCE, PieceType.PAWN
    )

    @Test fun everyHandPieceUsesOnlyItsOwnUnpromotedDropForBothPlayers() {
        Player.entries.forEach { player ->
            val position = ShogiPosition(
                board = mapOf(
                    Square(9, 9) to Piece(PieceType.KING, Player.SENTE),
                    Square(1, 1) to Piece(PieceType.KING, Player.GOTE)
                ),
                hands = mapOf(player to handTypes),
                activePlayer = player
            )
            val legalMoves = ShogiRules.legalMoves(position)
            val enemyZoneTarget = if (player == Player.SENTE) Square(5, 3) else Square(5, 7)
            val outsideTarget = Square(5, 5)

            handTypes.forEach { type ->
                listOf(enemyZoneTarget, outsideTarget).forEach { target ->
                    val choices = ShogiMoveSelection.choicesForTarget(legalMoves, null, type, target)
                    assertEquals("$player $type to $target", 1, choices.size)
                    assertTrue(choices.single().isDrop)
                    assertFalse(choices.single().promote)
                    assertEquals(type, choices.single().piece)
                    assertFalse(ShogiMoveSelection.isOptionalPromotionChoice(choices))
                }
            }
        }
    }

    @Test fun handSelectionDoesNotMixOtherDropsAtTheSameTarget() {
        val target = Square(5, 5)
        val legalMoves = handTypes.map { Move(null, target, it, Player.SENTE) }

        val choices = ShogiMoveSelection.choicesForTarget(legalMoves, null, PieceType.PAWN, target)

        assertEquals(listOf(PieceType.PAWN), choices.map { it.piece })
        assertFalse(ShogiMoveSelection.isOptionalPromotionChoice(choices))
    }

    @Test fun onlyMatchingBoardMovePromotionPairOpensPromotionChoice() {
        val from = Square(4, 4)
        val to = Square(4, 3)
        val choices = listOf(
            Move(from, to, PieceType.SILVER, Player.SENTE, promote = false),
            Move(from, to, PieceType.SILVER, Player.SENTE, promote = true)
        )

        assertTrue(ShogiMoveSelection.isOptionalPromotionChoice(choices))
        assertEquals(choices, ShogiMoveSelection.choicesForTarget(choices, from, null, to))
        assertTrue(ShogiMoveSelection.choicesForTarget(choices, null, PieceType.SILVER, to).isEmpty())
    }

    @Test fun clearingSelectionLeavesNoTargetsOrChoices() {
        val move = Move(null, Square(5, 5), PieceType.PAWN, Player.SENTE)
        assertTrue(ShogiMoveSelection.legalTargets(listOf(move), null, null).isEmpty())
        assertTrue(ShogiMoveSelection.choicesForTarget(listOf(move), null, null, move.to).isEmpty())
    }
}
