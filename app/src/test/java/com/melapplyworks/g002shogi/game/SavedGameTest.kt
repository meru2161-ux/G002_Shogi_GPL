package com.melapplyworks.g002shogi.game

import com.melapplyworks.g002shogi.model.Move
import com.melapplyworks.g002shogi.model.PieceType
import com.melapplyworks.g002shogi.model.Player
import com.melapplyworks.g002shogi.model.Square
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SavedGameTest {
    @Test fun versionedSaveRoundTripsBoardMovePromotionAndDrop() {
        val saved = SavedGame(
            configuration = GameConfiguration(GameMode.COACHING, Player.GOTE, GameDifficulty.STRONG),
            moves = listOf(
                Move(Square(7, 7), Square(7, 6), PieceType.PAWN, Player.SENTE),
                Move(Square(3, 3), Square(3, 4), PieceType.PAWN, Player.GOTE),
                Move(Square(8, 8), Square(2, 2), PieceType.BISHOP, Player.SENTE, promote = true),
                Move(null, Square(5, 5), PieceType.SILVER, Player.GOTE)
            ),
            outcome = SessionOutcome.GOTE_WIN,
            savedAtEpochMillis = 123456L
        )

        assertEquals(saved, SavedGameCodec.decode(SavedGameCodec.encode(saved)))
    }

    @Test fun corruptOrUnknownSaveIsRejectedWithoutInventingAGame() {
        assertNull(SavedGameCodec.decode(null))
        assertNull(SavedGameCodec.decode("G002_SAVE_V99|AI_MATCH|SENTE|STANDARD||0"))
        assertNull(SavedGameCodec.decode("G002_SAVE_V1|AI_MATCH|SENTE|STANDARD||0\n0,0,0,0,PAWN,SENTE,0"))
    }

    @Test fun semanticValidationRejectsSyntacticallyValidIllegalHistory() {
        val illegal = SavedGame(
            GameConfiguration(GameMode.AI_MATCH),
            listOf(Move(Square(7, 7), Square(7, 5), PieceType.PAWN, Player.SENTE))
        )
        val legal = SavedGame(
            GameConfiguration(GameMode.AI_MATCH),
            listOf(Move(Square(7, 7), Square(7, 6), PieceType.PAWN, Player.SENTE))
        )
        assertFalse(SavedGameValidator.isValid(illegal))
        assertTrue(SavedGameValidator.isValid(legal))
    }
}
