package com.melapplyworks.g002shogi.analysis.local

import com.melapplyworks.g002shogi.analysis.LocalShogiAnalysisEngine
import com.melapplyworks.g002shogi.game.GameDifficulty
import com.melapplyworks.g002shogi.model.PieceType
import com.melapplyworks.g002shogi.model.Move
import com.melapplyworks.g002shogi.model.Player
import com.melapplyworks.g002shogi.model.ShogiPosition
import com.melapplyworks.g002shogi.rules.RepetitionResult
import com.melapplyworks.g002shogi.rules.RepetitionTracker
import com.melapplyworks.g002shogi.rules.SfenCodec
import com.melapplyworks.g002shogi.rules.ShogiRules
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption

/**
 * Long-running, manually invoked benchmark. This file is kept under tools/benchmarks
 * and temporarily copied into the test source set only while measuring a candidate.
 */
class EngineDifficultyBenchmarkTest {
    private enum class End { MATE, REPETITION, PERPETUAL_CHECK, ADJUDICATED, PLY_CAP }
    private data class Result(val id: String, val strong: Player, val winner: Player?, val end: End, val plies: Int, val materialSente: Int, val moves: List<String>)

    @Test fun runPairedDifficultyGames() {
        val caseCount = (System.getenv("G002_BENCHMARK_CASES") ?: "2").toInt().coerceIn(1, starts.size)
        val maxPlies = (System.getenv("G002_BENCHMARK_MAX_PLIES") ?: "24").toInt().coerceIn(4, 200)
        val higher = GameDifficulty.valueOf(System.getenv("G002_BENCHMARK_HIGH") ?: "STRONG")
        val lower = GameDifficulty.valueOf(System.getenv("G002_BENCHMARK_LOW") ?: "BEGINNER")
        require(higher.ordinal > lower.ordinal) { "higher difficulty must be above lower difficulty" }
        val report = System.getenv("G002_BENCHMARK_REPORT")?.takeIf(String::isNotBlank)?.let(Path::of)
        report?.let { path ->
            path.parent?.let { parent -> Files.createDirectories(parent) }
            Files.newBufferedWriter(
                path,
                Charsets.UTF_8,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.WRITE
            ).use { writer ->
                writer.write("difficulty-benchmark\thigher=$higher\tlower=$lower\tcases=$caseCount\tmaxPlies=$maxPlies")
                writer.newLine()
            }
        }
        val results = buildList {
            starts.take(caseCount).forEach { (id, sfen) ->
                listOf(
                    play("$id-S", sfen, Player.SENTE, maxPlies, higher, lower),
                    play("$id-G", sfen, Player.GOTE, maxPlies, higher, lower)
                ).forEach { result ->
                    add(result)
                    checkpoint(report, formatGame(result))
                }
            }
        }
        val decisive = results.filter { it.winner != null }
        val strongWins = decisive.count { it.winner == it.strong }
        val lowerWins = decisive.count { it.winner != it.strong }
        checkpoint(report, "difficulty-summary\thigher=$higher\tlower=$lower\tgames=${results.size}\thigherWins=$strongWins\tlowerWins=$lowerWins\tdrawOrCap=${results.size - decisive.size}")
        assertTrue("benchmark must produce every requested paired game", results.size == caseCount * 2)
    }

    private fun formatGame(result: Result): String =
        "difficulty-game\t${result.id}\tstrong=${result.strong}\twinner=${result.winner ?: "NONE"}\tend=${result.end}\tplies=${result.plies}\tmaterialSente=${result.materialSente}\tmoves=${result.moves.joinToString(" ")}"

    /**
     * Checkpoint after every finished game so an interrupted long run leaves
     * explicit preliminary evidence instead of an uncounted partial result.
     */
    private fun checkpoint(report: Path?, line: String) {
        println(line)
        report?.let { path ->
            Files.newBufferedWriter(path, Charsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND).use { writer ->
                writer.write(line)
                writer.newLine()
            }
        }
    }

    private fun play(
        id: String,
        sfen: String,
        strongPlayer: Player,
        maxPlies: Int,
        higher: GameDifficulty,
        lower: GameDifficulty
    ): Result {
        val engine = LocalShogiAnalysisEngine()
        var position = SfenCodec.parse(sfen)
        val repetition = RepetitionTracker().also { it.record(position, null, false) }
        val moves = mutableListOf<String>()
        val moveHistory = mutableListOf<Move>()
        var adjudicationLeader: Player? = null
        var adjudicationStreak = 0
        repeat(maxPlies) { ply ->
            val difficulty = if (position.activePlayer == strongPlayer) higher else lower
            val result = engine.analyze(
                position,
                difficulty.analysisRequest("$id-p$ply").copy(recentMoves = moveHistory.takeLast(8))
            )
            val move = result.candidates.firstOrNull()?.move
            if (move == null) {
                val winner = if (ShogiRules.isCheckmate(position)) position.activePlayer.opponent() else null
                return Result(id, strongPlayer, winner, End.MATE, ply, materialSente(position), moves)
            }
            assertTrue("$id ply=$ply must remain legal", ShogiRules.isLegal(position, move))
            val mover = position.activePlayer
            moves += SfenCodec.formatUsiMove(move)
            moveHistory += move
            position = ShogiRules.applyKnownLegal(position, move)
            if (ShogiRules.isCheckmate(position)) return Result(id, strongPlayer, mover, End.MATE, ply + 1, materialSente(position), moves)
            when (repetition.record(position, mover, ShogiRules.isInCheck(position, position.activePlayer))) {
                RepetitionResult.DRAW -> return Result(id, strongPlayer, null, End.REPETITION, ply + 1, materialSente(position), moves)
                RepetitionResult.SENTE_LOSES_BY_PERPETUAL_CHECK -> return Result(id, strongPlayer, Player.GOTE, End.PERPETUAL_CHECK, ply + 1, materialSente(position), moves)
                RepetitionResult.GOTE_LOSES_BY_PERPETUAL_CHECK -> return Result(id, strongPlayer, Player.SENTE, End.PERPETUAL_CHECK, ply + 1, materialSente(position), moves)
                RepetitionResult.NONE -> Unit
            }
            val material = materialSente(position)
            val leader = when {
                material >= 1_500 -> Player.SENTE
                material <= -1_500 -> Player.GOTE
                else -> null
            }
            if (leader != null && leader == adjudicationLeader) adjudicationStreak++
            else {
                adjudicationLeader = leader
                adjudicationStreak = if (leader == null) 0 else 1
            }
            if (ply >= 15 && adjudicationStreak >= 6) {
                return Result(id, strongPlayer, leader, End.ADJUDICATED, ply + 1, material, moves)
            }
        }
        return Result(id, strongPlayer, null, End.PLY_CAP, maxPlies, materialSente(position), moves)
    }

    private fun materialSente(position: ShogiPosition): Int {
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
            PieceType.PROMOTED_SILVER, PieceType.PROMOTED_KNIGHT, PieceType.PROMOTED_LANCE, PieceType.PROMOTED_PAWN -> 600
        }
        var score = position.board.values.sumOf { (if (it.owner == Player.SENTE) 1 else -1) * value(it.type) }
        Player.entries.forEach { owner ->
            val sign = if (owner == Player.SENTE) 1 else -1
            score += sign * position.hands[owner].orEmpty().sumOf { value(it.unpromoted) }
        }
        return score
    }

    private companion object {
        val starts = listOf(
            "start-0" to "lnsgkgsnl/1r5b1/ppppppppp/9/9/9/PPPPPPPPP/1B5R1/LNSGKGSNL b - 1",
            "start-3" to "lnsgkgsnl/1r5b1/p1ppppppp/1p7/9/2P4P1/PP1PPPP1P/1B5R1/LNSGKGSNL w - 1",
            "bishop-pawn-3" to "lnsg1gsnl/1r3k1b1/p1ppppppp/1p7/9/2P4P1/PP1PPPP1P/1B5R1/LNSGKGSNL b - 1",
            "double-bishop-3" to "lnsgkgsnl/1r5b1/p1pppp1pp/1p4p2/9/2P1P2P1/PP1P1PP1P/1B5R1/LNSGKGSNL w - 1",
            "double-wing-3" to "lnsg1gsnl/1r3k1b1/p1ppppppp/9/1p5P1/2P6/PP1PPPP1P/1B1K3R1/LNSG1GSNL w - 1",
            "yagura-0" to "lns1kgsnl/1r2g2b1/p1pppp1pp/1p4p2/9/2PP5/PP2PPPPP/1BGS3R1/LN2KGSNL w - 1",
            "yagura-3" to "lns2gsnl/1r2g2b1/p1ppppkpp/1p4p2/9/2PP3P1/PP2PPP1P/1BGS3R1/LN2KGSNL b - 1",
            "fourth-file-0" to "lnsgkgsnl/1r5b1/p1pppp1pp/1p4p2/9/2PP5/PP2PPPPP/1B1R5/LNSGKGSNL w - 1",
            "fourth-file-3" to "lnsg1gsnl/1r4kb1/p1pppp1pp/1p4p2/9/2PP3P1/PP2PPP1P/1B1R5/LNSGKGSNL b - 1",
            "central-0" to "lnsgkgsnl/1r5b1/pppp1p1pp/4p1p2/4P4/9/PPPP1PPPP/1B5R1/LNSGKGSNL b - 1",
            "central-3" to "ln1gkgsnl/1r1s3b1/pppp1p1pp/4P1p2/9/7P1/PPPP1PP1P/1B5R1/LNSGKGSNL w P 1",
            "bishop-exchange-0" to "lnsgkg1nl/1r5s1/pppppp1pp/6p2/9/2P6/PP1PPPPPP/7R1/LNSGKGSNL b Bb 1",
            "bishop-exchange-3" to "lnsgkg1nl/1r5s1/p1pppp1pp/1p4p2/9/2P3BP1/PP1PPPP1P/7R1/LNSGKGSNL w b 1",
            "start-1" to "lnsgkgsnl/1r5b1/ppppppppp/9/9/2P6/PP1PPPPPP/1B5R1/LNSGKGSNL w - 1",
            "double-wing-0" to "lnsgkgsnl/1r5b1/p1ppppppp/9/1p5P1/9/PPPPPPP1P/1B5R1/LNSGKGSNL b - 1",
            "yagura-1" to "lns2gsnl/1r2gk1b1/p1pppp1pp/1p4p2/9/2PP5/PP2PPPPP/1BGS3R1/LN2KGSNL b - 1",
            "fourth-file-1" to "lnsg1gsnl/1r3k1b1/p1pppp1pp/1p4p2/9/2PP5/PP2PPPPP/1B1R5/LNSGKGSNL b - 1",
            "central-1" to "lnsgkgsnl/1r5b1/pppp1p1pp/4P1p2/9/9/PPPP1PPPP/1B5R1/LNSGKGSNL w P 1",
            "bishop-exchange-1" to "lnsgkg1nl/1r5s1/pppppp1pp/6p2/9/2P4P1/PP1PPPP1P/7R1/LNSGKGSNL w Bb 1",
            "bishop-exchange-2" to "lnsgkg1nl/1r5s1/p1pppp1pp/1p4p2/9/2P4P1/PP1PPPP1P/7R1/LNSGKGSNL b Bb 1"
        )
    }
}
