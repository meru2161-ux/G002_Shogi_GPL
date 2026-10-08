package com.melapplyworks.g002shogi.analysis.local

import com.melapplyworks.g002shogi.analysis.LocalShogiAnalysisEngine
import com.melapplyworks.g002shogi.game.GameDifficulty
import com.melapplyworks.g002shogi.model.CandidateMove
import com.melapplyworks.g002shogi.rules.SfenCodec
import com.melapplyworks.g002shogi.rules.ShogiRules
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Fixed strategy acceptance set.
 *
 * Reference moves were produced once with YaneuraOu commit
 * c1b80eaa09fe13d5f12b1599d1ae4d53c224de30, MATERIAL_LEVEL=9,
 * Threads=1, Hash=64 MB, own book off, MultiPV=10 and 2,000 ms per position.
 * YaneuraOu is a development-only GPL reference and is not packaged in G002.
 * Agreement is evidence for regression control, not a rank or Elo claim.
 */
class EngineStrategyAcceptanceTest {
    private data class StrategyCase(val id: String, val sfen: String, val acceptedUsi: Set<String>)

    @Test fun fixedStrategyPositionsMeetReferenceOverlapAndExplanationRequirements() {
        val engine = LocalShogiAnalysisEngine()
        val misses = mutableListOf<String>()
        var acceptedPositions = 0

        cases.forEach { case ->
            val position = SfenCodec.parse(case.sfen)
            val legal = ShogiRules.legalMoves(position)
            val result = engine.analyze(position, GameDifficulty.STRONG.analysisRequest(case.id))

            assertFalse("${case.id}: candidates must not be empty", result.candidates.isEmpty())
            assertTrue("${case.id}: at most three candidates", result.candidates.size <= 3)
            assertEquals(
                "${case.id}: candidates must be distinct",
                result.candidates.size,
                result.candidates.map { SfenCodec.formatUsiMove(it.move) }.distinct().size
            )
            result.candidates.forEach { candidate ->
                assertTrue("${case.id}: candidate must be legal", legal.any { it.sameAction(candidate.move) })
                assertExplanationFacts(case.id, position, candidate)
            }

            val actual = result.candidates.map { SfenCodec.formatUsiMove(it.move) }
            if (actual.any(case.acceptedUsi::contains)) acceptedPositions++
            else misses += "${case.id}=${actual.joinToString()}"
        }

        println("strategy-reference-overlap=$acceptedPositions/${cases.size}; misses=${misses.joinToString("; ")}")

        assertTrue(
            "reference top10 overlap=$acceptedPositions/${cases.size}, misses=${misses.joinToString("; ")}",
            acceptedPositions >= 27
        )
    }

    private fun assertExplanationFacts(id: String, position: com.melapplyworks.g002shogi.model.ShogiPosition, candidate: CandidateMove) {
        assertTrue("$id: purpose", candidate.purpose.isNotBlank())
        assertTrue("$id: title", candidate.explanation.title.isNotBlank())
        assertTrue("$id: body", candidate.explanation.body.isNotBlank())
        assertTrue("$id: meaning", candidate.explanation.meaning.isNotBlank())
        assertTrue("$id: intent", candidate.explanation.intent.isNotBlank())
        assertTrue("$id: opponent response", candidate.explanation.opponentResponse.isNotBlank())
        assertTrue("$id: continuation", candidate.explanation.continuation.isNotBlank())
        assertTrue("$id: caution", candidate.explanation.caution.isNotBlank())
        assertFalse("$id: PV", candidate.variation.moves.isEmpty())
        assertTrue("$id: PV must start with candidate", candidate.variation.moves.first().sameAction(candidate.move))

        if ("取り" in candidate.purpose) {
            val captured = position.board[candidate.move.to]
            assertTrue("$id: capture explanation needs an enemy piece", captured != null && captured.owner != candidate.move.player)
        }
        if ("成って" in candidate.purpose) assertTrue("$id: promotion explanation needs promotion", candidate.move.promote)
        if ("持ち駒を使って" in candidate.purpose) assertTrue("$id: hand-piece explanation needs a drop", candidate.move.isDrop)
    }

    private fun com.melapplyworks.g002shogi.model.Move.sameAction(other: com.melapplyworks.g002shogi.model.Move): Boolean =
        from == other.from && to == other.to && piece == other.piece && player == other.player && promote == other.promote

    private companion object {
        val cases = listOf(
            StrategyCase("start-0", "lnsgkgsnl/1r5b1/ppppppppp/9/9/9/PPPPPPPPP/1B5R1/LNSGKGSNL b - 1", setOf("2g2f","5i5h","3i3h","7g7f","6i6h","3i4h","5i6h","4i4h","6i7h","2h7h")),
            StrategyCase("start-1", "lnsgkgsnl/1r5b1/ppppppppp/9/9/2P6/PP1PPPPPP/1B5R1/LNSGKGSNL w - 1", setOf("5a5b","3c3d","8c8d","9c9d","7a7b","4a4b","7a6b","6a6b","4a3b","1c1d")),
            StrategyCase("start-2", "lnsgkgsnl/1r5b1/p1ppppppp/1p7/9/2P6/PP1PPPPPP/1B5R1/LNSGKGSNL b - 1", setOf("7i7h","8h7g","3i3h","2g2f","5i6h","6i6h","5i5h","4i4h","3i4h","8h6f")),
            StrategyCase("start-3", "lnsgkgsnl/1r5b1/p1ppppppp/1p7/9/2P4P1/PP1PPPP1P/1B5R1/LNSGKGSNL w - 1", setOf("3c3d","5a4b","5a5b","6a6b","4a3b","7a7b","7a6b","8d8e","5a6b","1c1d")),
            StrategyCase("bishop-pawn-0", "lnsgkgsnl/1r5b1/ppppppppp/9/9/2P6/PP1PPPPPP/1B5R1/LNSGKGSNL w - 1", setOf("3c3d","8c8d","5a5b","5a4b","6a6b","7a6b","4a3b","5a6b","4a4b","1c1d")),
            StrategyCase("bishop-pawn-1", "lnsgkgsnl/1r5b1/p1ppppppp/1p7/9/2P6/PP1PPPPPP/1B5R1/LNSGKGSNL b - 1", setOf("8h7g","2g2f","7i7h","5i5h","7i6h","3i3h","6i6h","5i6h","4i4h","8h6f")),
            StrategyCase("bishop-pawn-2", "lnsgkgsnl/1r5b1/p1ppppppp/1p7/9/2P4P1/PP1PPPP1P/1B5R1/LNSGKGSNL w - 1", setOf("3c3d","7a6b","7a7b","5a5b","8d8e","6a6b","4a3b","5a6b","5a4b","1c1d")),
            StrategyCase("bishop-pawn-3", "lnsg1gsnl/1r3k1b1/p1ppppppp/1p7/9/2P4P1/PP1PPPP1P/1B5R1/LNSGKGSNL b - 1", setOf("2f2e","5i5h","3i3h","7i6h","5i4h","6i6h","7i7h","8h7g","5i6h","6i7h")),
            StrategyCase("double-bishop-pawn-0", "lnsgkgsnl/1r5b1/pppppp1pp/6p2/9/2P6/PP1PPPPPP/1B5R1/LNSGKGSNL b - 1", setOf("2g2f","5i5h","6i6h","5i6h","6i7h","3i3h","5i4h","3i4h","4i3h","4i4h")),
            StrategyCase("double-bishop-pawn-1", "lnsgkgsnl/1r5b1/pppppp1pp/6p2/9/2P4P1/PP1PPPP1P/1B5R1/LNSGKGSNL w - 1", setOf("5a4b","8c8d","6a6b","4a3b","4a4b","2b3c","5a5b","2b8h+","7a6b","7a7b")),
            StrategyCase("double-bishop-pawn-2", "lnsgkgsnl/1r5b1/p1pppp1pp/1p4p2/9/2P4P1/PP1PPPP1P/1B5R1/LNSGKGSNL b - 1", setOf("5i5h","6i6h","2f2e","3i3h","5i6h","6i7h","4i3h","5i4h","1g1f","3i4h")),
            StrategyCase("double-bishop-pawn-3", "lnsgkgsnl/1r5b1/p1pppp1pp/1p4p2/9/2P1P2P1/PP1P1PP1P/1B5R1/LNSGKGSNL w - 1", setOf("8d8e","5a5b","4a3b","6a6b","5a4b","7a7b","5a6b","9c9d","4a4b","7c7d")),
            StrategyCase("double-wing-0", "lnsgkgsnl/1r5b1/p1ppppppp/9/1p5P1/9/PPPPPPP1P/1B5R1/LNSGKGSNL b - 1", setOf("7i7h","7g7f","5i5h","9g9f","3i4h","3i3h","2h2f","5i6h","1g1f","5i4h")),
            StrategyCase("double-wing-1", "lnsgkgsnl/1r5b1/p1ppppppp/9/1p5P1/2P6/PP1PPPP1P/1B5R1/LNSGKGSNL w - 1", setOf("3c3d","4a3b","8b8d","1c1d","8e8f","3a3b","5a5b","5a6b","4a4b","5c5d")),
            StrategyCase("double-wing-2", "lnsg1gsnl/1r3k1b1/p1ppppppp/9/1p5P1/2P6/PP1PPPP1P/1B5R1/LNSGKGSNL b - 1", setOf("2e2d","8h6f","5i5h","3i4h","7i7h","3i3h","8h7g","6i7h","4i4h","6i6h")),
            StrategyCase("double-wing-3", "lnsg1gsnl/1r3k1b1/p1ppppppp/9/1p5P1/2P6/PP1PPPP1P/1B1K3R1/LNSG1GSNL w - 1", setOf("4b3b","8b8d","3c3d","1c1d","8e8f","4a3b","3a3b","4b5a","1a1b","4b5b")),
            StrategyCase("yagura-0", "lns1kgsnl/1r2g2b1/p1pppp1pp/1p4p2/9/2PP5/PP2PPPPP/1BGS3R1/LN2KGSNL w - 1", setOf("5a4b","6c6d","5c5d","8d8e","2b4d","1c1d","4c4d","9c9d","7a7b","2b3c")),
            StrategyCase("yagura-1", "lns2gsnl/1r2gk1b1/p1pppp1pp/1p4p2/9/2PP5/PP2PPPPP/1BGS3R1/LN2KGSNL b - 1", setOf("7h6g","5i6i","9g9f","2g2f","5i5h","2h4h","8h7g","1g1f","6h6g","7h7i")),
            StrategyCase("yagura-2", "lns2gsnl/1r2gk1b1/p1pppp1pp/1p4p2/9/2PP3P1/PP2PPP1P/1BGS3R1/LN2KGSNL w - 1", setOf("8d8e","4b3b","5c5d","2b3c","6c6d","9c9d","2b4d","7a6b","1c1d","7a7b")),
            StrategyCase("yagura-3", "lns2gsnl/1r2g2b1/p1ppppkpp/1p4p2/9/2PP3P1/PP2PPP1P/1BGS3R1/LN2KGSNL b - 1", setOf("7h6g","5i6i","2f2e","1g1f","8h7g","3i3h","6h6g","9g9f","5i5h","6h7g")),
            StrategyCase("fourth-file-0", "lnsgkgsnl/1r5b1/p1pppp1pp/1p4p2/9/2PP5/PP2PPPPP/1B1R5/LNSGKGSNL w - 1", setOf("7a7b","5a4b","8d8e","7a6b","6a6b","4a3b","5a5b","6a5b","4a4b","2b3c")),
            StrategyCase("fourth-file-1", "lnsg1gsnl/1r3k1b1/p1pppp1pp/1p4p2/9/2PP5/PP2PPPPP/1B1R5/LNSGKGSNL b - 1", setOf("8h7g","6i7h","5i5h","1g1f","6i5h","3i3h","9g9f","3i2h","5i4h","6f6e")),
            StrategyCase("fourth-file-2", "lnsg1gsnl/1r3k1b1/p1pppp1pp/1p4p2/9/2PP3P1/PP2PPP1P/1B1R5/LNSGKGSNL w - 1", setOf("8d8e","4b3b","7a7b","7a6b","6a7b","1c1d","6a6b","6a5a","6a5b","5c5d")),
            StrategyCase("fourth-file-3", "lnsg1gsnl/1r4kb1/p1pppp1pp/1p4p2/9/2PP3P1/PP2PPP1P/1B1R5/LNSGKGSNL b - 1", setOf("6i7h","3i3h","1g1f","9g9f","6i5h","3i2h","6f6e","7i7h","5i5h","8h7g")),
            StrategyCase("central-0", "lnsgkgsnl/1r5b1/pppp1p1pp/4p1p2/4P4/9/PPPP1PPPP/1B5R1/LNSGKGSNL b - 1", setOf("5e5d","6i5h","2h5h","6i6h","7g7f","2g2f","9g9f","5i6h","5i5h","7i7h")),
            StrategyCase("central-1", "lnsgkgsnl/1r5b1/pppp1p1pp/4P1p2/9/9/PPPP1PPPP/1B5R1/LNSGKGSNL w P 1", setOf("8b5b","4a4b","7a6b","5a5b","5a4b","6a5b","6a6b","4a5b","3a4b","2b4d")),
            StrategyCase("central-2", "ln1gkgsnl/1r1s3b1/pppp1p1pp/4P1p2/9/9/PPPP1PPPP/1B5R1/LNSGKGSNL b P 1", setOf("2h5h","5i6h","2h6h","6i6h","9g9f","5i5h","2h4h","2h1h","2g2f","2h3h")),
            StrategyCase("central-3", "ln1gkgsnl/1r1s3b1/pppp1p1pp/4P1p2/9/7P1/PPPP1PP1P/1B5R1/LNSGKGSNL w P 1", setOf("5a5b","4a4b","6c6d","9c9d","8c8d","2b4d","1c1d","5a4b","7c7d","6a7b")),
            StrategyCase("bishop-exchange-0", "lnsgkg1nl/1r5s1/pppppp1pp/6p2/9/2P6/PP1PPPPPP/7R1/LNSGKGSNL b Bb 1", setOf("6i7h","B*4e","7i8h","7i6h","3i4h","7i7h","6i6h","5i5h","B*5f","5i6h")),
            StrategyCase("bishop-exchange-1", "lnsgkg1nl/1r5s1/pppppp1pp/6p2/9/2P4P1/PP1PPPP1P/7R1/LNSGKGSNL w Bb 1", setOf("6a6b","2b3c","5a4b","6a5b","B*4d","4a3b","4a4b","4a3a","1c1d","B*5d"))
        )
    }
}
