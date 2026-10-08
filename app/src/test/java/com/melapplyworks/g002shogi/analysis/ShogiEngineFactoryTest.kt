package com.melapplyworks.g002shogi.analysis

import com.melapplyworks.g002shogi.analysis.poc.UsiAnalysisSnapshot
import com.melapplyworks.g002shogi.analysis.poc.UsiInfo
import com.melapplyworks.g002shogi.analysis.poc.UsiScore
import com.melapplyworks.g002shogi.model.ShogiPositions
import com.melapplyworks.g002shogi.rules.SfenCodec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ShogiEngineFactoryTest {
    @Test fun defaultFactoryPreservesExistingLocalProductEngine() {
        val result = ShogiEngineFactory.create().analyze(
            ShogiPositions.initial(),
            AnalysisRequest(positionId = "default-position", requestId = "default-request", maxDepth = 1)
        )
        assertFalse(result.isSample)
        assertEquals("default-position", result.positionId)
        assertEquals("default-request", result.requestId)
        assertTrue(result.candidates.isNotEmpty())
    }

    @Test fun approvedBackendFeedsRealCandidatesThroughProductFactory() {
        val backend = RecordingBackend(
            UsiAnalysisSnapshot(
                listOf(UsiInfo(1, UsiScore.Centipawn(60), pv = listOf("7g7f", "3c3d"))),
                "7g7f"
            )
        )
        val engine = ShogiEngineFactory.create(backend)
        val result = engine.analyze(
            ShogiPositions.initial(),
            AnalysisRequest(positionId = "usi-position", requestId = "usi-request")
        )
        assertEquals(listOf("7g7f"), result.candidates.map { SfenCodec.formatUsiMove(it.move) })
        assertEquals("usi-position", result.positionId)
        assertEquals("usi-request", result.requestId)
        engine.close()
        assertTrue(backend.closed)
    }

    @Test fun emptyApprovedBackendFallsBackToExistingLocalEngine() {
        val backend = RecordingBackend(UsiAnalysisSnapshot(emptyList()))
        val result = ShogiEngineFactory.create(backend).analyze(
            ShogiPositions.initial(),
            AnalysisRequest(positionId = "fallback-position", requestId = "fallback-request", maxDepth = 1)
        )
        assertTrue(result.candidates.isNotEmpty())
        assertEquals("fallback-position", result.positionId)
        assertEquals("fallback-request", result.requestId)
    }

    private class RecordingBackend(private val result: UsiAnalysisSnapshot) : UsiEngineBackend {
        var closed = false
        override fun analyze(query: UsiEngineQuery) = result
        override fun close() {
            closed = true
        }
    }
}
