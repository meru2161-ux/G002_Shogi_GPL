package com.melapplyworks.g002shogi.analysis

import com.melapplyworks.g002shogi.analysis.poc.UsiAnalysisSnapshot
import com.melapplyworks.g002shogi.analysis.poc.UsiInfo
import com.melapplyworks.g002shogi.analysis.poc.UsiScore
import com.melapplyworks.g002shogi.model.AnalysisResult
import com.melapplyworks.g002shogi.model.ShogiPosition
import com.melapplyworks.g002shogi.model.ShogiPositions
import com.melapplyworks.g002shogi.rules.SfenCodec
import com.melapplyworks.g002shogi.rules.ShogiRules
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UsiShogiAnalysisEngineTest {
    @Test fun multiPvBecomesThreeLegalTeachingCandidatesAndPreservesRequestIdentity() {
        var query: UsiEngineQuery? = null
        val engine = UsiShogiAnalysisEngine { requested ->
            query = requested
            UsiAnalysisSnapshot(
                infos = listOf(
                    UsiInfo(3, UsiScore.Centipawn(8), pv = listOf("5g5f", "5c5d")),
                    UsiInfo(1, UsiScore.Centipawn(42), pv = listOf("7g7f", "3c3d", "2g2f")),
                    UsiInfo(2, UsiScore.Centipawn(21), pv = listOf("2g2f", "8c8d")),
                    UsiInfo(4, UsiScore.Centipawn(-5), pv = listOf("9g9f"))
                ),
                bestMove = "7g7f"
            )
        }
        val position = ShogiPositions.initial()
        val result = engine.analyze(position, AnalysisRequest("position-7", "request-9", nodeLimit = 160_000, timeLimitMillis = 900))

        assertEquals("position-7", result.positionId)
        assertEquals("request-9", result.requestId)
        assertFalse(result.isSample)
        assertEquals(listOf("7g7f", "2g2f", "5g5f"), result.candidates.map { SfenCodec.formatUsiMove(it.move) })
        assertTrue(result.candidates.all { ShogiRules.isLegal(position, it.move) })
        assertTrue(result.candidates.all { it.explanation.meaning.isNotBlank() && it.explanation.opponentResponse.isNotBlank() })
        assertEquals(3, query?.multiPv)
        assertEquals(160_000, query?.nodeLimit)
        assertEquals(900L, query?.timeLimitMillis)
        assertEquals(SfenCodec.format(position), query?.sfen)
    }

    @Test fun illegalAndDuplicatePvAreRejectedWithoutInventingMoves() {
        val engine = UsiShogiAnalysisEngine {
            UsiAnalysisSnapshot(
                listOf(
                    UsiInfo(1, UsiScore.Centipawn(10), pv = listOf("7g7e")),
                    UsiInfo(2, UsiScore.Centipawn(9), pv = listOf("7g7f")),
                    UsiInfo(3, UsiScore.Centipawn(8), pv = listOf("7g7f", "3c3d"))
                )
            )
        }
        val result = engine.analyze(ShogiPositions.initial(), AnalysisRequest("p", "r"))
        assertEquals(listOf("7g7f"), result.candidates.map { SfenCodec.formatUsiMove(it.move) })
    }

    @Test fun cancellationBeforeAndAfterBackendSuppressesStaleCandidates() {
        var called = false
        val cancelledBefore = UsiShogiAnalysisEngine {
            called = true
            UsiAnalysisSnapshot(emptyList())
        }.analyze(ShogiPositions.initial(), AnalysisRequest(isCancelled = { true }))
        assertFalse(called)
        assertTrue(cancelledBefore.candidates.isEmpty())

        var cancelled = false
        val cancelledAfter = UsiShogiAnalysisEngine {
            cancelled = true
            UsiAnalysisSnapshot(listOf(UsiInfo(1, UsiScore.Centipawn(10), pv = listOf("7g7f"))))
        }.analyze(ShogiPositions.initial(), AnalysisRequest("p", "r", isCancelled = { cancelled }))
        assertTrue(cancelledAfter.candidates.isEmpty())
        assertEquals("r", cancelledAfter.requestId)
    }

    @Test fun moveAssessmentUsesSamePreMovePositionAndFlipsReplyPerspective() {
        var analyzed: UsiEngineQuery? = null
        val engine = UsiShogiAnalysisEngine { query ->
            analyzed = query
            UsiAnalysisSnapshot(listOf(UsiInfo(1, UsiScore.Centipawn(-125), pv = listOf("3c3d", "2g2f"))))
        }
        val position = ShogiPositions.initial()
        val move = requireNotNull(SfenCodec.parseUsiMove(position, "7g7f"))
        val assessment = engine.assessMove(position, move, AnalysisRequest(nodeLimit = 80_000))

        assertNotNull(assessment)
        assertEquals(125, assessment?.scoreForMover)
        assertEquals(listOf("7g7f", "3c3d", "2g2f"), assessment?.principalVariation?.map(SfenCodec::formatUsiMove))
        assertEquals(SfenCodec.format(ShogiRules.applyKnownLegal(position, move)), analyzed?.sfen)
        assertEquals(1, analyzed?.multiPv)
    }

    @Test fun illegalMoveIsNeverSentForAssessment() {
        var called = false
        val engine = UsiShogiAnalysisEngine {
            called = true
            UsiAnalysisSnapshot(emptyList())
        }
        val position = ShogiPositions.initial()
        val illegal = com.melapplyworks.g002shogi.model.Move(
            from = com.melapplyworks.g002shogi.model.Square(7, 7),
            to = com.melapplyworks.g002shogi.model.Square(7, 5),
            piece = com.melapplyworks.g002shogi.model.PieceType.PAWN,
            player = position.activePlayer
        )
        assertNull(engine.assessMove(position, illegal))
        assertFalse(called)
    }

    @Test fun fallbackProtectsBothModesWhenPrimaryFails() {
        val position = ShogiPositions.initial()
        val fallbackResult = AnalysisResult(position, emptyList(), false, "fallback", "fallback-request")
        val primary = object : ShogiAnalysisEngine {
            override fun analyze(position: ShogiPosition, request: AnalysisRequest): AnalysisResult = error("engine unavailable")
        }
        val fallback = object : ShogiAnalysisEngine {
            override fun analyze(position: ShogiPosition, request: AnalysisRequest): AnalysisResult = fallbackResult
            override fun assessMove(position: ShogiPosition, move: com.melapplyworks.g002shogi.model.Move, request: AnalysisRequest) =
                MoveAssessment(77, listOf(move))
        }
        val engine = FallbackShogiAnalysisEngine(primary, fallback)
        assertEquals(fallbackResult, engine.analyze(position))
        val move = requireNotNull(SfenCodec.parseUsiMove(position, "7g7f"))
        assertEquals(77, engine.assessMove(position, move)?.scoreForMover)
    }

    @Test fun cancellationNeverStartsPrimaryOrFallbackWork() {
        var primaryCalls = 0
        var fallbackCalls = 0
        val primary = UsiShogiAnalysisEngine {
            primaryCalls++
            UsiAnalysisSnapshot(emptyList())
        }
        val fallback = object : ShogiAnalysisEngine {
            override fun analyze(position: ShogiPosition, request: AnalysisRequest): AnalysisResult {
                fallbackCalls++
                return AnalysisResult(position, emptyList(), false, request.positionId, request.requestId)
            }
        }
        val result = FallbackShogiAnalysisEngine(primary, fallback).analyze(
            ShogiPositions.initial(),
            AnalysisRequest(positionId = "cancelled-position", requestId = "cancelled-request", isCancelled = { true })
        )
        assertEquals(0, primaryCalls)
        assertEquals(0, fallbackCalls)
        assertEquals("cancelled-position", result.positionId)
        assertEquals("cancelled-request", result.requestId)
    }
}
