package com.melapplyworks.g002shogi.analysis.local

import com.melapplyworks.g002shogi.analysis.LocalShogiAnalysisEngine
import com.melapplyworks.g002shogi.game.GameDifficulty
import com.melapplyworks.g002shogi.model.Move
import com.melapplyworks.g002shogi.model.PieceType
import com.melapplyworks.g002shogi.model.Player
import com.melapplyworks.g002shogi.model.ShogiPosition
import com.melapplyworks.g002shogi.rules.RepetitionResult
import com.melapplyworks.g002shogi.rules.RepetitionTracker
import com.melapplyworks.g002shogi.rules.SfenCodec
import com.melapplyworks.g002shogi.rules.ShogiRules
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.File
import java.io.InputStreamReader
import java.io.OutputStreamWriter

/**
 * Development-only cross-engine diagnostic. The external GPL executable is
 * supplied by an environment variable and is never bundled with G002.
 */
class ExternalEngineCalibrationBenchmarkTest {
    private enum class End { MATE, RESIGN, REPETITION, PERPETUAL_CHECK, ADJUDICATED, PLY_CAP }
    private data class Result(
        val id: String,
        val g002: Player,
        val winner: Player?,
        val end: End,
        val plies: Int,
        val materialSente: Int,
        val moves: List<String>
    )

    @Test fun playPairedGamesAgainstExternalReference() {
        val executable = requireNotNull(System.getenv("G002_REFERENCE_ENGINE"))
        val moveTimeMillis = (System.getenv("G002_REFERENCE_MOVETIME_MS") ?: "1000").toLong().coerceIn(50, 10_000)
        val maxPlies = (System.getenv("G002_REFERENCE_MAX_PLIES") ?: "60").toInt().coerceIn(4, 300)
        ReferenceUsiEngine(File(executable), moveTimeMillis).use { reference ->
            val results = listOf(
                play("initial-S", STARTPOS, Player.SENTE, maxPlies, moveTimeMillis, reference),
                play("initial-G", STARTPOS, Player.GOTE, maxPlies, moveTimeMillis, reference)
            )
            results.forEach { r ->
                println("external-game\t${r.id}\tg002=${r.g002}\twinner=${r.winner ?: "NONE"}\tend=${r.end}\tplies=${r.plies}\tmaterialSente=${r.materialSente}\tmoves=${r.moves.joinToString(" ")}")
            }
            val g002Wins = results.count { it.winner == it.g002 }
            val referenceWins = results.count { it.winner != null && it.winner != it.g002 }
            println("external-summary\tgames=${results.size}\tg002Wins=$g002Wins\treferenceWins=$referenceWins\tdrawOrCap=${results.size - g002Wins - referenceWins}\tmovetimeMs=$moveTimeMillis")
        }
    }

    private fun play(
        id: String,
        sfen: String,
        g002Player: Player,
        maxPlies: Int,
        moveTimeMillis: Long,
        reference: ReferenceUsiEngine
    ): Result {
        val local = LocalShogiAnalysisEngine()
        var position = SfenCodec.parse(sfen)
        val repetition = RepetitionTracker().also { it.record(position, null, false) }
        val moves = mutableListOf<String>()
        val history = mutableListOf<Move>()
        var adjudicationLeader: Player? = null
        var adjudicationStreak = 0
        repeat(maxPlies) { ply ->
            val token = if (position.activePlayer == g002Player) {
                val request = GameDifficulty.STRONG.analysisRequest("$id-p$ply").copy(
                    timeLimitMillis = moveTimeMillis,
                    recentMoves = history.takeLast(8)
                )
                local.analyze(position, request).candidates.firstOrNull()?.move?.let(SfenCodec::formatUsiMove)
            } else {
                reference.bestMove(position)
            }
            if (token == null || token == "resign" || token == "win") {
                val winner = if (token == "resign") position.activePlayer.opponent() else null
                return Result(id, g002Player, winner, End.RESIGN, ply, materialSente(position), moves)
            }
            val move = SfenCodec.parseUsiMove(position, token)
            assertTrue("$id ply=$ply external/local move must be legal: $token", move != null && ShogiRules.isLegal(position, move))
            val legalMove = requireNotNull(move)
            val mover = position.activePlayer
            moves += token
            history += legalMove
            position = ShogiRules.applyKnownLegal(position, legalMove)
            if (ShogiRules.isCheckmate(position)) return Result(id, g002Player, mover, End.MATE, ply + 1, materialSente(position), moves)
            when (repetition.record(position, mover, ShogiRules.isInCheck(position, position.activePlayer))) {
                RepetitionResult.DRAW -> return Result(id, g002Player, null, End.REPETITION, ply + 1, materialSente(position), moves)
                RepetitionResult.SENTE_LOSES_BY_PERPETUAL_CHECK -> return Result(id, g002Player, Player.GOTE, End.PERPETUAL_CHECK, ply + 1, materialSente(position), moves)
                RepetitionResult.GOTE_LOSES_BY_PERPETUAL_CHECK -> return Result(id, g002Player, Player.SENTE, End.PERPETUAL_CHECK, ply + 1, materialSente(position), moves)
                RepetitionResult.NONE -> Unit
            }
            val material = materialSente(position)
            val leader = when {
                material >= 1_500 -> Player.SENTE
                material <= -1_500 -> Player.GOTE
                else -> null
            }
            if (ply + 1 >= 15 && leader != null) {
                if (leader == adjudicationLeader) adjudicationStreak++ else {
                    adjudicationLeader = leader
                    adjudicationStreak = 1
                }
                if (adjudicationStreak >= 6) return Result(id, g002Player, leader, End.ADJUDICATED, ply + 1, material, moves)
            } else {
                adjudicationLeader = null
                adjudicationStreak = 0
            }
        }
        return Result(id, g002Player, null, End.PLY_CAP, maxPlies, materialSente(position), moves)
    }

    private class ReferenceUsiEngine(executable: File, private val moveTimeMillis: Long) : AutoCloseable {
        private val process = ProcessBuilder(executable.absolutePath).directory(executable.parentFile).redirectErrorStream(true).start()
        private val input = BufferedReader(InputStreamReader(process.inputStream))
        private val output = BufferedWriter(OutputStreamWriter(process.outputStream))

        init {
            send("usi")
            readUntil { it == "usiok" }
            send("setoption name Threads value 1")
            send("setoption name USI_Hash value 64")
            send("setoption name USI_OwnBook value false")
            send("setoption name MinimumThinkingTime value 1")
            send("setoption name RoundUpToFullSecond value false")
            send("isready")
            readUntil { it == "readyok" }
            send("usinewgame")
        }

        fun bestMove(position: ShogiPosition): String {
            send("position sfen ${SfenCodec.format(position)}")
            send("go movetime $moveTimeMillis")
            return readUntil { it.startsWith("bestmove ") }.split(Regex("\\s+"))[1]
        }

        private fun send(command: String) {
            output.write(command)
            output.newLine()
            output.flush()
        }

        private fun readUntil(done: (String) -> Boolean): String {
            while (true) {
                val line = input.readLine() ?: error("Reference engine stopped before completing the command")
                if (done(line)) return line
            }
        }

        override fun close() {
            runCatching { send("quit") }
            if (!process.waitFor(3, java.util.concurrent.TimeUnit.SECONDS)) process.destroy()
        }
    }

    private fun materialSente(position: ShogiPosition): Int {
        var score = 0
        position.board.values.forEach { piece -> score += if (piece.owner == Player.SENTE) value(piece.type) else -value(piece.type) }
        position.hands.forEach { (owner, pieces) -> pieces.forEach { score += if (owner == Player.SENTE) value(it) else -value(it) } }
        return score
    }

    private fun value(type: PieceType): Int = when (type) {
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

    private companion object {
        const val STARTPOS = "lnsgkgsnl/1r5b1/ppppppppp/9/9/9/PPPPPPPPP/1B5R1/LNSGKGSNL b - 1"
    }
}
