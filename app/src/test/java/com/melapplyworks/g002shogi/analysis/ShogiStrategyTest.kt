package com.melapplyworks.g002shogi.analysis

import com.melapplyworks.g002shogi.model.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ShogiStrategyTest {
    @Test fun openingDiscouragesWalkingEitherKingForwardBeforeCastling() {
        val position = ShogiPositions.initial()

        assertTrue(ShogiStrategy.openingMoveBonus(position, Move(Square(5, 9), Square(5, 8), PieceType.KING, Player.SENTE)) < 0)
        assertTrue(ShogiStrategy.openingMoveBonus(position, Move(Square(5, 1), Square(5, 2), PieceType.KING, Player.GOTE)) < 0)
        assertTrue(ShogiStrategy.openingMoveBonus(position, Move(Square(5, 9), Square(6, 9), PieceType.KING, Player.SENTE)) > 0)
        assertTrue(ShogiStrategy.openingMoveBonus(position, Move(Square(5, 1), Square(4, 1), PieceType.KING, Player.GOTE)) > 0)
    }

    @Test fun openingPenaltyDoesNotLeakIntoEndgame() {
        val lanceMove = Move(Square(9, 9), Square(9, 8), PieceType.LANCE, Player.SENTE)
        assertTrue(ShogiStrategy.openingMoveBonus(ShogiPositions.initial(), lanceMove) < 0)

        val endgame = ShogiPosition(mapOf(
            Square(5, 9) to Piece(PieceType.KING, Player.SENTE),
            Square(5, 1) to Piece(PieceType.KING, Player.GOTE),
            Square(9, 9) to Piece(PieceType.LANCE, Player.SENTE)
        ))
        assertEquals(0, ShogiStrategy.openingMoveBonus(endgame, lanceMove))
    }

    @Test fun recognizesStaticRookFromPositionRatherThanFixedMoveOrder() {
        val board = ShogiPositions.initial().board.toMutableMap().apply {
            remove(Square(2, 7))
            put(Square(2, 6), Piece(PieceType.PAWN, Player.SENTE))
        }

        val profile = ShogiStrategy.profile(ShogiPosition(board), Player.SENTE)

        assertEquals(ShogiStrategy.RookPlan.STATIC_ROOK, profile.rookPlan)
        assertTrue(profile.intent.contains("飛車先"))
    }

    @Test fun recognizesFourthFileRookAndMinoFromBoardShape() {
        val board = ShogiPositions.initial().board.toMutableMap().apply {
            remove(Square(2, 8)); put(Square(6, 8), Piece(PieceType.ROOK, Player.SENTE))
            remove(Square(5, 9)); put(Square(2, 8), Piece(PieceType.KING, Player.SENTE))
            remove(Square(3, 9)); put(Square(3, 8), Piece(PieceType.SILVER, Player.SENTE))
            remove(Square(6, 9)); put(Square(5, 8), Piece(PieceType.GOLD, Player.SENTE))
        }

        val profile = ShogiStrategy.profile(ShogiPosition(board), Player.SENTE)

        assertEquals(ShogiStrategy.RookPlan.FOURTH_FILE_ROOK, profile.rookPlan)
        assertEquals(ShogiStrategy.CastlePlan.MINO, profile.castlePlan)
        assertTrue(profile.summary.contains("四間飛車"))
        assertTrue(profile.summary.contains("美濃囲い"))
    }

    @Test fun recognizesGoteYaguraSymmetrically() {
        val board = ShogiPositions.initial().board.toMutableMap().apply {
            remove(Square(5, 1)); put(Square(2, 2), Piece(PieceType.KING, Player.GOTE))
            remove(Square(4, 1)); put(Square(3, 2), Piece(PieceType.GOLD, Player.GOTE))
            remove(Square(6, 1)); put(Square(4, 3), Piece(PieceType.GOLD, Player.GOTE))
            remove(Square(7, 1)); put(Square(3, 3), Piece(PieceType.SILVER, Player.GOTE))
            remove(Square(8, 3)); put(Square(8, 4), Piece(PieceType.PAWN, Player.GOTE))
        }

        val profile = ShogiStrategy.profile(ShogiPosition(board, activePlayer = Player.GOTE), Player.GOTE)

        assertEquals(ShogiStrategy.RookPlan.STATIC_ROOK, profile.rookPlan)
        assertEquals(ShogiStrategy.CastlePlan.YAGURA, profile.castlePlan)
    }

    @Test fun completedCastleKeepsItsModestSafetyValueAfterOpeningMaterialChanges() {
        val bareKings = ShogiPosition(mapOf(
            Square(5, 9) to Piece(PieceType.KING, Player.SENTE),
            Square(5, 1) to Piece(PieceType.KING, Player.GOTE)
        ))
        val mino = ShogiPosition(mapOf(
            Square(2, 8) to Piece(PieceType.KING, Player.SENTE),
            Square(3, 8) to Piece(PieceType.SILVER, Player.SENTE),
            Square(4, 9) to Piece(PieceType.GOLD, Player.SENTE),
            Square(5, 8) to Piece(PieceType.GOLD, Player.SENTE),
            Square(5, 1) to Piece(PieceType.KING, Player.GOTE)
        ))

        assertTrue("castle value must remain after the opening",
            ShogiStrategy.positionScore(mino, Player.SENTE) > ShogiStrategy.positionScore(bareKings, Player.SENTE))

        val goteMino = ShogiPosition(mapOf(
            Square(5, 9) to Piece(PieceType.KING, Player.SENTE),
            Square(8, 2) to Piece(PieceType.KING, Player.GOTE),
            Square(7, 2) to Piece(PieceType.SILVER, Player.GOTE),
            Square(6, 1) to Piece(PieceType.GOLD, Player.GOTE),
            Square(5, 2) to Piece(PieceType.GOLD, Player.GOTE)
        ))

        assertTrue("gote castle value must remain after the opening",
            ShogiStrategy.positionScore(goteMino, Player.GOTE) > ShogiStrategy.positionScore(bareKings, Player.GOTE))
    }

    @Test fun recognizesTheMainRangingRookPlansForBothPlayers() {
        val cases = listOf(
            Triple(Player.SENTE, 5, ShogiStrategy.RookPlan.CENTRAL_ROOK),
            Triple(Player.SENTE, 7, ShogiStrategy.RookPlan.THIRD_FILE_ROOK),
            Triple(Player.SENTE, 8, ShogiStrategy.RookPlan.OPPOSING_ROOK),
            Triple(Player.GOTE, 5, ShogiStrategy.RookPlan.CENTRAL_ROOK),
            Triple(Player.GOTE, 3, ShogiStrategy.RookPlan.THIRD_FILE_ROOK),
            Triple(Player.GOTE, 2, ShogiStrategy.RookPlan.OPPOSING_ROOK)
        )

        cases.forEach { (player, file, expected) ->
            val home = if (player == Player.SENTE) Square(2, 8) else Square(8, 2)
            val rank = if (player == Player.SENTE) 8 else 2
            val board = ShogiPositions.initial().board.toMutableMap().apply {
                remove(home)
                put(Square(file, rank), Piece(PieceType.ROOK, player))
            }
            assertEquals("player=$player file=$file", expected, ShogiStrategy.profile(ShogiPosition(board), player).rookPlan)
        }
    }

    @Test fun fourthFileRookPrefersMovingKingTowardMinoInsteadOfCentralExposure() {
        val board = ShogiPositions.initial().board.toMutableMap().apply {
            remove(Square(2, 8)); put(Square(6, 8), Piece(PieceType.ROOK, Player.SENTE))
        }
        val position = ShogiPosition(board)
        val towardMino = Move(Square(5, 9), Square(4, 8), PieceType.KING, Player.SENTE)
        val straightForward = Move(Square(5, 9), Square(5, 8), PieceType.KING, Player.SENTE)

        assertTrue(ShogiStrategy.openingMoveBonus(position, towardMino) > ShogiStrategy.openingMoveBonus(position, straightForward))
        assertTrue(ShogiStrategy.teachingIntent(position, towardMino).orEmpty().contains("飛車を振った筋"))
    }

    @Test fun recognizesBoginFromAdvancedSilverAndStaticRook() {
        val board = ShogiPositions.initial().board.toMutableMap().apply {
            remove(Square(2, 7)); put(Square(2, 6), Piece(PieceType.PAWN, Player.SENTE))
            remove(Square(3, 9)); put(Square(2, 7), Piece(PieceType.SILVER, Player.SENTE))
        }

        val profile = ShogiStrategy.profile(ShogiPosition(board), Player.SENTE)

        assertEquals(ShogiStrategy.AttackPlan.BOGIN, profile.attackPlan)
        assertTrue(profile.summary.contains("棒銀"))
        assertTrue(profile.intent.contains("飛車先"))
    }

    @Test fun recognizesSilverInFrontOfFourthFileRookAsAnAttackShape() {
        val board = ShogiPositions.initial().board.toMutableMap().apply {
            remove(Square(2, 8)); put(Square(6, 8), Piece(PieceType.ROOK, Player.SENTE))
            remove(Square(7, 9)); put(Square(6, 7), Piece(PieceType.SILVER, Player.SENTE))
        }

        val profile = ShogiStrategy.profile(ShogiPosition(board), Player.SENTE)

        assertEquals(ShogiStrategy.AttackPlan.RANGING_ROOK_ATTACK, profile.attackPlan)
        assertTrue(profile.intent.contains("歩を先頭"))
    }
}
