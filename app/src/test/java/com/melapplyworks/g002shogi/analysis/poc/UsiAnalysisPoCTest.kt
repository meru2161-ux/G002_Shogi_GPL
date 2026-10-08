package com.melapplyworks.g002shogi.analysis.poc

import com.melapplyworks.g002shogi.analysis.MockShogiAnalysisEngine
import com.melapplyworks.g002shogi.model.PieceType
import com.melapplyworks.g002shogi.model.Player
import com.melapplyworks.g002shogi.model.Square
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class UsiAnalysisPoCTest {
    private val parser = UsiAnalysisParser()

    @Test fun mockInitialPositionUsesStandardRookAndBishopSquares() {
        val board = MockShogiAnalysisEngine().analyzeInitialPosition().position.board
        assertEquals(PieceType.BISHOP, board[Square(2, 2)]?.type)
        assertEquals(Player.GOTE, board[Square(2, 2)]?.owner)
        assertEquals(PieceType.ROOK, board[Square(8, 2)]?.type)
        assertEquals(Player.GOTE, board[Square(8, 2)]?.owner)
        assertEquals(PieceType.ROOK, board[Square(2, 8)]?.type)
        assertEquals(Player.SENTE, board[Square(2, 8)]?.owner)
        assertEquals(PieceType.BISHOP, board[Square(8, 8)]?.type)
        assertEquals(Player.SENTE, board[Square(8, 8)]?.owner)
    }

    @Test fun parsesCentipawnInfoAndPv() {
        val event = parser.parseLine("info depth 12 nodes 1234 time 56 score cp 34 multipv 2 pv 2g2f 3c3d") as UsiEngineEvent.Info
        assertEquals(2, event.value.multiPv); assertEquals(12, event.value.depth); assertEquals(1234L, event.value.nodes)
        assertEquals(56L, event.value.timeMillis); assertEquals(34, (event.value.score as UsiScore.Centipawn).value)
        assertEquals(listOf("2g2f", "3c3d"), event.value.pv)
    }

    @Test fun parsesMateScoreAndBestMove() {
        val info = parser.parseLine("info score mate -3 multipv 1 pv 7g7f") as UsiEngineEvent.Info
        val best = parser.parseLine("bestmove 7g7f ponder 3c3d") as UsiEngineEvent.BestMove
        assertEquals(-3, (info.value.score as UsiScore.Mate).moves); assertEquals("7g7f", best.move); assertEquals("3c3d", best.ponder)
    }

    @Test fun keepsMultiPvOrderedAndLatest() {
        val session = UsiAnalysisSession()
        session.accept(parser.parseLine("info multipv 3 score cp 5 pv 5g5f")!!)
        session.accept(parser.parseLine("info multipv 1 score cp 20 pv 7g7f")!!)
        session.accept(parser.parseLine("info multipv 3 score cp 7 pv 5g5f 3c3d")!!)
        session.accept(parser.parseLine("bestmove 7g7f")!!)
        val snapshot = session.snapshot()
        assertEquals(listOf(1, 3), snapshot.infos.map { it.multiPv })
        assertEquals(7, (snapshot.infos[1].score as UsiScore.Centipawn).value); assertEquals("7g7f", snapshot.bestMove)
    }

    @Test fun malformedInfoNeverThrowsAndUnknownLinesAreIgnored() {
        val malformed = parser.parseLine("info depth nope score cp ??? multipv 0 pv") as UsiEngineEvent.Info
        assertEquals(null, malformed.value.depth); assertEquals(null, malformed.value.score); assertEquals(emptyList<String>(), malformed.value.pv)
        assertNull(parser.parseLine("option name unexpected value text")); assertNull(parser.parseLine("   "))
    }

    @Test fun normalizesValidMultiPvToExistingAnalysisModels() {
        val position = MockShogiAnalysisEngine().analyzeInitialPosition().position
        val session = UsiAnalysisSession().apply {
            accept(parser.parseLine("info depth 8 score cp 28 multipv 2 pv 2g2f 3c3d")!!)
            accept(parser.parseLine("info depth 8 score cp 31 multipv 1 pv 7g7f 3c3d")!!)
            accept(parser.parseLine("info score mate 5 multipv 3 pv 5g5f 3c3d")!!)
        }
        val result = UsiAnalysisAdapter().toAnalysisResult(UsiAnalysisEnvelope("position-a", session.snapshot()), "position-a", position)
        assertNotNull(result); assertEquals(listOf(1, 2, 3), result!!.candidates.map { it.rankForFuture })
        assertEquals("7g7f", result.candidates.first().move.notation); assertEquals(0.31, result.candidates.first().evaluationForFuture!!, 0.0001)
        assertEquals(2, result.candidates.first().variation.moves.size)
    }

    @Test fun stalePositionResultIsRejectedBeforeUiConversion() {
        val position = MockShogiAnalysisEngine().analyzeInitialPosition().position
        val session = UsiAnalysisSession().apply { accept(parser.parseLine("info score cp 10 multipv 1 pv 7g7f")!!) }
        assertNull(UsiAnalysisAdapter().toAnalysisResult(UsiAnalysisEnvelope("old-position", session.snapshot()), "current-position", position))
    }

    @Test fun invalidPvSuffixIsDiscardedWithoutCrashing() {
        val position = MockShogiAnalysisEngine().analyzeInitialPosition().position
        val session = UsiAnalysisSession().apply { accept(parser.parseLine("info score cp 10 multipv 1 pv 7g7f invalid 3c3d")!!) }
        val result = UsiAnalysisAdapter().toAnalysisResult(UsiAnalysisEnvelope("position-a", session.snapshot()), "position-a", position)
        assertEquals(1, result!!.candidates.single().variation.moves.size)
    }

    @Test fun convertsYaneuraOuMaterialMultiPvFixtureToExistingModels() {
        val position = MockShogiAnalysisEngine().analyzeInitialPosition().position
        val session = UsiAnalysisSession().apply {
            listOf(
                "info depth 14 seldepth 19 multipv 1 score cp 6 nodes 581119 nps 1729520 hashfull 233 time 336 pv 1g1f 8b5b",
                "info depth 14 seldepth 17 multipv 2 score cp 5 nodes 581119 nps 1729520 hashfull 233 time 336 pv 2g2f 6a5b 1g1f",
                "info depth 13 seldepth 15 multipv 3 score cp -20 nodes 581119 nps 1729520 hashfull 233 time 336 pv 9g9f 4a4b 1g1f 9c9d",
                "bestmove 1g1f ponder 8b5b"
            ).forEach { accept(parser.parseLine(it)!!) }
        }

        val result = UsiAnalysisAdapter().toAnalysisResult(
            UsiAnalysisEnvelope("yaneuraou-material", session.snapshot()),
            "yaneuraou-material",
            position
        )

        assertNotNull(result)
        assertEquals(listOf(1, 2, 3), result!!.candidates.map { it.rankForFuture })
        assertEquals(listOf("1g1f", "2g2f", "9g9f"), result.candidates.map { it.move.notation })
        assertEquals(0.06, result.candidates.first().evaluationForFuture!!, 0.0001)
        assertEquals(2, result.candidates.first().variation.moves.size)
    }
}
