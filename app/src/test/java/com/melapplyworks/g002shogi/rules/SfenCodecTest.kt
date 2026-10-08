package com.melapplyworks.g002shogi.rules

import com.melapplyworks.g002shogi.model.Move
import com.melapplyworks.g002shogi.model.Piece
import com.melapplyworks.g002shogi.model.PieceType
import com.melapplyworks.g002shogi.model.Player
import com.melapplyworks.g002shogi.model.ShogiPosition
import com.melapplyworks.g002shogi.model.ShogiPositions
import com.melapplyworks.g002shogi.model.Square
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SfenCodecTest {
    @Test fun initialPositionMatchesTheStandardSfenAndRoundTrips() {
        val expected = "lnsgkgsnl/1r5b1/ppppppppp/9/9/9/PPPPPPPPP/1B5R1/LNSGKGSNL b - 1"
        assertEquals(expected, SfenCodec.format(ShogiPositions.initial()))
        assertEquals(ShogiPositions.initial(), SfenCodec.parse(expected))
    }

    @Test fun promotedPiecesHandsAndGoteTurnRoundTrip() {
        val position = ShogiPosition(
            board = mapOf(
                Square(5, 9) to Piece(PieceType.KING, Player.SENTE),
                Square(5, 1) to Piece(PieceType.KING, Player.GOTE),
                Square(8, 2) to Piece(PieceType.PROMOTED_ROOK, Player.SENTE),
                Square(2, 8) to Piece(PieceType.PROMOTED_BISHOP, Player.GOTE),
                Square(3, 3) to Piece(PieceType.PROMOTED_PAWN, Player.GOTE)
            ),
            hands = mapOf(
                Player.SENTE to listOf(PieceType.ROOK, PieceType.PAWN, PieceType.PAWN),
                Player.GOTE to listOf(PieceType.SILVER, PieceType.KNIGHT, PieceType.KNIGHT)
            ),
            activePlayer = Player.GOTE
        )
        val encoded = SfenCodec.format(position, moveNumber = 42)
        assertTrue(encoded.endsWith(" w R2Ps2n 42"))
        assertEquals(position, SfenCodec.parse(encoded))
    }

    @Test fun normalPromotionAndDropUsiMovesUseTheLegalMoveLayer() {
        val initial = ShogiPositions.initial()
        val pawn = SfenCodec.parseUsiMove(initial, "7g7f")
        assertEquals(Move(Square(7, 7), Square(7, 6), PieceType.PAWN, Player.SENTE), pawn?.copy(notation = ""))
        assertEquals("7g7f", SfenCodec.formatUsiMove(requireNotNull(pawn)))

        val promotionPosition = SfenCodec.parse("4k4/3P5/9/9/9/9/9/9/4K4 b - 1")
        val promoted = SfenCodec.parseUsiMove(promotionPosition, "6b6a+")
        assertEquals(true, promoted?.promote)
        assertEquals("6b6a+", SfenCodec.formatUsiMove(requireNotNull(promoted)))

        val dropPosition = SfenCodec.parse("4k4/9/9/9/9/9/9/9/4K4 b G2P 1")
        val drop = SfenCodec.parseUsiMove(dropPosition, "G*5e")
        assertTrue(requireNotNull(drop).isDrop)
        assertEquals("G*5e", SfenCodec.formatUsiMove(drop))
    }

    @Test fun illegalOrMalformedUsiMovesAreRejected() {
        val position = ShogiPositions.initial()
        assertNull(SfenCodec.parseUsiMove(position, "7g7e"))
        assertNull(SfenCodec.parseUsiMove(position, "P*5e+"))
        assertNull(SfenCodec.parseUsiMove(position, "invalid"))
    }
}
