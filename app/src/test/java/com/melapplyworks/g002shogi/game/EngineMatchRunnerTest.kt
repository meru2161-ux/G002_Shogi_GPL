package com.melapplyworks.g002shogi.game

import com.melapplyworks.g002shogi.analysis.AnalysisRequest
import com.melapplyworks.g002shogi.analysis.ShogiAnalysisEngine
import com.melapplyworks.g002shogi.model.*
import com.melapplyworks.g002shogi.rules.ShogiRules
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EngineMatchRunnerTest {
    @Test fun boundedMatchUsesOnlyLegalMovesAndRecordsThePlyLimit() {
        val engine = LegalFirstEngine()
        val result = EngineMatchRunner(engine, engine).play(ShogiPositions.initial(), 8) { _, ply, recent ->
            AnalysisRequest(positionId = "calibration-$ply", maxDepth = 1, nodeLimit = 100, timeLimitMillis = 1, recentMoves = recent)
        }

        assertEquals(EngineMatchRunner.Termination.PLY_LIMIT, result.termination)
        assertEquals(8, result.moves.size)
        var position = result.initial
        result.moves.forEach { move ->
            assertTrue(ShogiRules.isLegal(position, move))
            position = ShogiRules.apply(position, move)
        }
        assertEquals(result.finalPosition, position)
    }

    @Test fun invalidEngineCandidateEndsMatchWithoutChangingThePosition() {
        val invalid = object : ShogiAnalysisEngine {
            override fun analyze(position: ShogiPosition, request: AnalysisRequest) = AnalysisResult(
                position, listOf(CandidateMove("bad", Move(Square(1, 1), Square(1, 9), PieceType.KING, position.activePlayer), "", Explanation("", "", ""), variation = VariationLine(emptyList(), emptyList()))), false
            )
        }
        val initial = ShogiPositions.initial()
        val result = EngineMatchRunner(invalid, invalid).play(initial, 2) { _, _, _ -> AnalysisRequest() }
        assertEquals(EngineMatchRunner.Termination.INVALID_ENGINE_MOVE, result.termination)
        assertTrue(result.moves.isEmpty())
        assertEquals(initial, result.finalPosition)
    }

    private class LegalFirstEngine : ShogiAnalysisEngine {
        override fun analyze(position: ShogiPosition, request: AnalysisRequest): AnalysisResult {
            val move = ShogiRules.legalMoves(position).first()
            return AnalysisResult(position, listOf(CandidateMove("legal", move, "", Explanation("", "", ""), variation = VariationLine(listOf(move), emptyList()))), false)
        }
    }
}
