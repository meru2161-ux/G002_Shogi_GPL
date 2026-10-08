package com.melapplyworks.g002shogi.analysis.local

import com.melapplyworks.g002shogi.analysis.AnalysisRequest
import com.melapplyworks.g002shogi.analysis.LocalShogiAnalysisEngine
import com.melapplyworks.g002shogi.model.*
import com.melapplyworks.g002shogi.rules.SfenCodec
import com.melapplyworks.g002shogi.rules.ShogiRules
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Objective P0 positions: correctness is proved by the legal/checkmate layer, not by engine agreement. */
class EngineTacticalAcceptanceTest {
    private data class MateCase(val id: String, val sfen: String)

    @Test fun fixedMateInOneCasesAreFoundAndPlayed() {
        val cases = listOf(
            MateCase("supported-rook-move", "4k4/3G1G3/4R4/9/9/9/9/9/K8 b - 1"),
            MateCase("gold-drop", "4k4/9/4R4/9/9/9/9/9/K8 b G 1"),
            MateCase("silver-drop-with-blockers", "4k4/3p1p3/4R4/9/9/9/9/9/K8 b S 1"),
            MateCase("promoted-rook-move", "4k4/9/3+R1G3/9/9/9/9/9/K8 b - 1")
        )
        val engine = LocalShogiAnalysisEngine()

        cases.forEach { case ->
            val position = SfenCodec.parse(case.sfen)
            val objectiveMates = ShogiRules.legalMoves(position).filter { move ->
                ShogiRules.isCheckmate(ShogiRules.apply(position, move))
            }
            assertFalse("${case.id} must independently contain mate in one", objectiveMates.isEmpty())

            val result = engine.analyze(
                position,
                AnalysisRequest(case.id, maxDepth = 2, nodeLimit = 50_000, timeLimitMillis = 10_000)
            )
            val top = result.candidates.first().move
            assertTrue("${case.id}: top=$top must be an objectively verified mate", objectiveMates.any { sameAction(it, top) })
        }
    }

    @Test fun fixedUniqueCheckEvasionsAreRecognizedAndPlayed() {
        // Discovered by a deterministic 20261008 construction pass, then fixed
        // as SFEN. Every run re-proves check and exactly one legal reply.
        val positions = listOf(
            "3k4G/8K/9/5B3/N4s3/3ll4/8r/9/7r1 b - 1",
            "R2k5/9/1brG5/9/3ps1K1G/9/9/9/3P5 w - 1",
            "9/k1K6/5N3/9/LBb6/9/l6P1/9/8p w - 1",
            "9/1l1Gb4/3R5/6gkp/5R3/7L1/9/6K2/9 w - 1",
            "4R4/k1R4g1/bl7/6l1B/K8/9/9/9/5p3 w - 1",
            "9/2P6/1r7/9/K8/4k4/9/rN7/1r7 b - 1"
        )
        val engine = LocalShogiAnalysisEngine()

        positions.forEachIndexed { index, sfen ->
            val position = SfenCodec.parse(sfen)
            assertTrue("unique-evasion-$index must start in check", ShogiRules.isInCheck(position, position.activePlayer))
            val onlyLegalMove = ShogiRules.legalMoves(position).single()
            val result = engine.analyze(
                position,
                AnalysisRequest("unique-evasion-$index", maxDepth = 2, nodeLimit = 50_000, timeLimitMillis = 10_000)
            )
            val top = result.candidates.single().move
            assertTrue("unique-evasion-$index must play the only legal defense", sameAction(onlyLegalMove, top))
        }
    }

    @Test fun fixedMateInThreeCasesMeetTopOneAndTopThreeThresholds() {
        val positions = listOf(
            "1L3R3/9/2g6/9/9/6P2/2KR5/9/4k4 b - 1",
            "2S6/2s6/7p1/1K7/7S1/r8/1k7/2r6/5p3 w - 1",
            "3b5/8n/6N2/9/1kg4b1/9/5rg2/4K4/9 w - 1",
            "4g3b/g8/9/9/1k7/1s1r5/9/9/K8 w - 1",
            "9/9/N2R1p3/3R5/9/9/KR7/4k4/9 b - 1",
            "9/2lsL4/r1l6/6k2/9/9/1K7/8b/9 w - 1",
            "G1kN4B/9/2S6/9/9/3K5/9/9/9 b - 1",
            "7k1/n8/7K1/9/b8/9/5P1p1/1r7/3R5 b - 1"
        )
        val engine = LocalShogiAnalysisEngine()
        var topOneMatches = 0

        positions.forEachIndexed { index, sfen ->
            val position = SfenCodec.parse(sfen)
            val winning = forcedMateInThreeMoves(position)
            assertFalse("mate3-$index needs independently proven winning moves", winning.isEmpty())
            val result = engine.analyze(
                position,
                AnalysisRequest("mate3-$index", maxDepth = 3, nodeLimit = 100_000, timeLimitMillis = 10_000)
            )
            val accepted = result.candidates.map { candidate -> winning.any { sameAction(it, candidate.move) } }
            assertTrue("mate3-$index must include a proven line in the top three", accepted.any { it })
            if (accepted.firstOrNull() == true) topOneMatches++
        }
        assertTrue("mate-in-three top1=$topOneMatches/8 must remain at least 7/8", topOneMatches >= 7)
    }

    @Test fun fixedTwoPlyMaterialTacticsSurviveTheBestRecapture() {
        val positions = listOf(
            "9/G8/9/4K4/5n3/9/r2p5/5kB2/9 w - 1",
            "9/7k1/7b1/6G2/9/6s2/K5S2/4R4/9 b - 1",
            "8K/9/3n5/4s2Nk/l8/9/9/3G5/7N1 w - 1",
            "6k2/9/G8/2P6/2N1S2K1/8p/r4R3/9/9 b - 1",
            "5N3/9/9/9/3kNP3/5K3/7s1/g2G2R1s/6l2 b - 1",
            "G2l5/1l3L3/9/5k3/9/6KR1/9/7rN/9 b - 1",
            "2N6/1s1K5/3s5/6sBk/8r/9/9/9/9 w - 1",
            "8P/1kr6/9/6R2/9/5N2b/9/3SK2N1/9 b - 1",
            "9/2KN5/9/2n6/b5g2/4gn1G1/P3s4/9/5k3 w - 1",
            "3R4S/9/9/1L1P1pR2/9/3b4K/6b2/9/6k2 b - 1"
        )
        val engine = LocalShogiAnalysisEngine()

        positions.forEachIndexed { index, sfen ->
            val position = SfenCodec.parse(sfen)
            val mover = position.activePlayer
            val scored = ShogiRules.legalMoves(position).map { move ->
                move to worstMaterialAfterReply(position, move, mover)
            }.sortedByDescending { it.second }
            assertTrue("material-$index needs multiple legal alternatives", scored.size >= 2)
            assertTrue("material-$index needs an objective margin", scored[0].second - scored[1].second >= 300)

            val top = engine.analyze(
                position,
                AnalysisRequest("material-$index", maxDepth = 3, nodeLimit = 100_000, timeLimitMillis = 10_000)
            ).candidates.first().move
            assertTrue("material-$index top=$top must survive the best recapture", sameAction(scored[0].first, top))
        }
    }

    @Test fun fixedPromotionTacticsChooseTheProvenPromotion() {
        val positions = listOf(
            "9/1gr4P1/5s3/6r2/9/5r1P1/1N7/K7p/4r3k b - 1",
            "1S7/3S5/9/2P3k2/9/G3K4/6S2/9/3n5 b - 1",
            "9/2b3K2/9/P8/4g4/9/G3k4/G8/9 b - 1",
            "2L6/9/9/P8/2s6/6r2/9/9/1N2K1k1s b - 1",
            "5r1b1/3L5/1l6K/3p5/5p3/4R4/5L3/3p1k3/1G7 w - 1",
            "9/3k5/1K7/5P3/6n2/7S1/bn7/5n2n/G8 w - 1",
            "6p2/N6k1/L8/4p4/9/3S2p2/2R2B3/3K5/9 w - 1",
            "9/6Pp1/1K7/5S1n1/9/1B2k4/1NN6/9/4G4 b - 1",
            "6K2/3S5/9/9/k7N/2np5/P2L5/4P1B1G/7p1 w - 1",
            "7b1/2s1S4/9/9/7L1/8k/7l1/8B/8K b - 1"
        )
        val engine = LocalShogiAnalysisEngine()

        positions.forEachIndexed { index, sfen ->
            val position = SfenCodec.parse(sfen)
            val mover = position.activePlayer
            val scored = ShogiRules.legalMoves(position).map { move ->
                move to worstMaterialAfterReply(position, move, mover)
            }.sortedByDescending { it.second }
            val proven = scored.first()
            assertTrue("promotion-$index best move must be a promotion", proven.first.promote)
            assertTrue("promotion-$index needs an objective margin", proven.second - scored[1].second >= 300)

            val top = engine.analyze(
                position,
                AnalysisRequest("promotion-$index", maxDepth = 3, nodeLimit = 100_000, timeLimitMillis = 10_000)
            ).candidates.first().move
            assertTrue("promotion-$index top=$top must choose the proven promotion", sameAction(proven.first, top))
        }
    }

    @Test fun fixedMateThreatPositionsAvoidAllowingMateInOne() {
        val positions = listOf(
            "k4n3/5K3/9/9/2R6/4B3B/gsp3n2/9/R1B6 w - 1",
            "5kL2/5n3/7R1/2n6/3b5/7G1/7g1/6G2/6K2 b - 1",
            "5R2p/k8/6R2/4L4/l5GR1/7n1/3b1K3/9/4s4 w - 1",
            "9/9/9/2N1gR1K1/9/P6k1/2R6/9/1p7 w - 1",
            "R8/1k2L4/9/2B6/8G/8K/n7B/1B7/6n2 w - 1",
            "N8/4N4/9/9/1bb4RS/6K2/9/5R3/6sSk w - 1",
            "2S1k4/L2R5/G2K1B3/9/2S6/L8/2G1g4/9/9 w - 1",
            "5P2B/9/6l2/8k/2p6/1G2g3p/7g1/1L1g1K3/9 b - 1",
            "6p2/9/9/9/3r5/5RS2/s1s2R3/1Pl6/6K1k w - 1",
            "2n5b/9/6s2/5R3/9/g6b1/1nrk5/K8/n5p2 b - 1"
        )
        val engine = LocalShogiAnalysisEngine()

        positions.forEachIndexed { index, sfen ->
            val position = SfenCodec.parse(sfen)
            val legal = ShogiRules.legalMoves(position)
            val blunders = legal.filter { allowsImmediateMate(position, it) }
            assertFalse("avoid-mate-$index must contain a real one-move-mate blunder", blunders.isEmpty())
            assertTrue("avoid-mate-$index must also have a safe defense", blunders.size < legal.size)

            val top = engine.analyze(
                position,
                AnalysisRequest("avoid-mate-$index", maxDepth = 3, nodeLimit = 100_000, timeLimitMillis = 10_000)
            ).candidates.first().move
            assertFalse("avoid-mate-$index top=$top must not allow immediate mate", allowsImmediateMate(position, top))
        }
    }

    @Test fun fixedUniqueDropEvasionsUseTheOnlyLegalInterposition() {
        val positions = listOf(
            "7s1/9/2K6/9/9/1n1B4k/3R5/8L/1G5R1 w p 1",
            "9/2n1N4/NL4S2/9/1k5l1/9/8K/5g3/8r b 2L 1",
            "7P1/9/b5k1K/9/8r/9/9/9/1N7 b 3P 1",
            "1Pl6/9/9/8N/9/5k3/1R1K5/5g3/3r5 b B 1",
            "5n3/1r5B1/r1k6/9/K4L3/9/6l2/9/9 b B 1",
            "9/9/8r/6b2/6s1K/7B1/4l3N/2k6/9 b P 1",
            "1l7/9/9/9/9/4b4/9/2Ksk4/9 b N 1",
            "6l1r/5N3/9/3b3K1/9/7r1/9/9/3k5 b S 1",
            "2r1K4/2r6/9/9/5k2g/4s4/9/9/1g3B3 b R 1",
            "2S5k/6K2/S7R/L8/6N2/9/9/S3s4/9 w 2l 1",
            "g2GK1r2/6r2/8L/9/9/6s2/g7k/3g5/9 b RP 1",
            "9/P6l1/sr5N1/2k2N3/r8/9/K8/9/3N5 b B 1"
        )
        val engine = LocalShogiAnalysisEngine()

        positions.forEachIndexed { index, sfen ->
            val position = SfenCodec.parse(sfen)
            assertTrue("drop-evasion-$index must start in check", ShogiRules.isInCheck(position, position.activePlayer))
            val onlyLegalMove = ShogiRules.legalMoves(position).single()
            assertTrue("drop-evasion-$index only defense must be a drop", onlyLegalMove.isDrop)

            val result = engine.analyze(
                position,
                AnalysisRequest("drop-evasion-$index", maxDepth = 2, nodeLimit = 50_000, timeLimitMillis = 10_000)
            )
            val top = result.candidates.single().move
            assertTrue("drop-evasion-$index must play the only legal drop", sameAction(onlyLegalMove, top))
        }
    }

    private fun forcedMateInThreeMoves(position: com.melapplyworks.g002shogi.model.ShogiPosition): List<Move> {
        val legal = ShogiRules.legalMoves(position)
        assertFalse("mate-in-three fixtures must not already have mate in one", legal.any { move ->
            ShogiRules.isCheckmate(ShogiRules.apply(position, move))
        })
        return legal.filter { first ->
            val afterFirst = ShogiRules.apply(position, first)
            if (!ShogiRules.isInCheck(afterFirst, afterFirst.activePlayer)) return@filter false
            val replies = ShogiRules.legalMoves(afterFirst)
            replies.isNotEmpty() && replies.all { reply ->
                val afterReply = ShogiRules.apply(afterFirst, reply)
                ShogiRules.legalMoves(afterReply).any { finish ->
                    ShogiRules.isCheckmate(ShogiRules.apply(afterReply, finish))
                }
            }
        }
    }

    private fun worstMaterialAfterReply(position: ShogiPosition, move: Move, mover: Player): Int {
        val after = ShogiRules.apply(position, move)
        val replies = ShogiRules.legalMoves(after)
        return if (replies.isEmpty()) material(after, mover)
        else replies.minOf { reply -> material(ShogiRules.apply(after, reply), mover) }
    }

    private fun allowsImmediateMate(position: ShogiPosition, move: Move): Boolean {
        val after = ShogiRules.apply(position, move)
        return ShogiRules.legalMoves(after).any { reply ->
            ShogiRules.isCheckmate(ShogiRules.apply(after, reply))
        }
    }

    private fun material(position: ShogiPosition, perspective: Player): Int {
        fun value(type: PieceType): Int = when (type) {
            PieceType.KING -> 0
            PieceType.ROOK -> 1_000
            PieceType.BISHOP -> 850
            PieceType.GOLD -> 600
            PieceType.SILVER -> 520
            PieceType.KNIGHT -> 360
            PieceType.LANCE -> 320
            PieceType.PAWN -> 100
            PieceType.PROMOTED_ROOK -> 1_150
            PieceType.PROMOTED_BISHOP -> 1_000
            PieceType.PROMOTED_SILVER, PieceType.PROMOTED_KNIGHT,
            PieceType.PROMOTED_LANCE, PieceType.PROMOTED_PAWN -> 600
        }
        var score = position.board.values.sumOf { piece ->
            (if (piece.owner == perspective) 1 else -1) * value(piece.type)
        }
        position.hands.forEach { (owner, pieces) ->
            score += (if (owner == perspective) 1 else -1) * pieces.sumOf(::value)
        }
        return score
    }

    private fun sameAction(expected: Move, actual: Move): Boolean =
        expected.from == actual.from && expected.to == actual.to && expected.piece == actual.piece &&
            expected.player == actual.player && expected.promote == actual.promote
}
