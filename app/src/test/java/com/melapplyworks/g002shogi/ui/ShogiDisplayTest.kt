package com.melapplyworks.g002shogi.ui

import com.melapplyworks.g002shogi.model.PieceType
import com.melapplyworks.g002shogi.model.Player
import com.melapplyworks.g002shogi.model.Square
import org.junit.Assert.assertEquals
import org.junit.Test

class ShogiDisplayTest {
    @Test fun sentePiecePointsUpWithoutRotation() {
        assertEquals(0f, ShogiDisplay.rotationDegrees(Player.SENTE))
    }

    @Test fun gotePieceRotatesAsOnePiece() {
        assertEquals(180f, ShogiDisplay.rotationDegrees(Player.GOTE))
    }

    @Test fun boardCoordinatesFollowSenteBottomView() {
        assertEquals(listOf("9", "8", "7", "6", "5", "4", "3", "2", "1"), ShogiDisplay.fileLabels)
        assertEquals(listOf("一", "二", "三", "四", "五", "六", "七", "八", "九"), ShogiDisplay.rankLabels)
    }

    @Test fun handPiecesUseStableOrderAndKeepCounts() {
        val actual = ShogiDisplay.orderedHandCounts(
            listOf(PieceType.PAWN, PieceType.SILVER, PieceType.PAWN, PieceType.ROOK)
        )
        assertEquals(listOf(PieceType.ROOK to 1, PieceType.SILVER to 1, PieceType.PAWN to 2), actual)
    }

    @Test fun accessibilitySquareUsesJapaneseNotation() {
        assertEquals("盤 ７六", ShogiDisplay.squareDescription(Square(7, 6)))
    }
}
