package com.melapplyworks.g002shogi.game

import com.melapplyworks.g002shogi.analysis.AnalysisRequest
import com.melapplyworks.g002shogi.analysis.ShogiAnalysisEngine
import com.melapplyworks.g002shogi.model.Move
import com.melapplyworks.g002shogi.model.Player
import com.melapplyworks.g002shogi.model.ShogiPosition
import com.melapplyworks.g002shogi.rules.RepetitionResult
import com.melapplyworks.g002shogi.rules.RepetitionTracker
import com.melapplyworks.g002shogi.rules.ShogiRules

/** Pure, bounded runner for reproducible engine calibration. It is not a UI game loop. */
class EngineMatchRunner(
    private val sente: ShogiAnalysisEngine,
    private val gote: ShogiAnalysisEngine
) {
    enum class Termination { SENTE_MATE, GOTE_MATE, DRAW_REPETITION, SENTE_LOSES_PERPETUAL_CHECK, GOTE_LOSES_PERPETUAL_CHECK, NO_LEGAL_MOVE, INVALID_ENGINE_MOVE, PLY_LIMIT }

    data class Result(
        val initial: ShogiPosition,
        val moves: List<Move>,
        val finalPosition: ShogiPosition,
        val termination: Termination,
        val boundedSearches: Int
    )

    fun play(initial: ShogiPosition, maxPlies: Int, requestFor: (Player, Int, List<Move>) -> AnalysisRequest): Result {
        require(maxPlies > 0)
        val repetition = RepetitionTracker()
        repetition.record(initial, null, false)
        var position = initial
        val moves = mutableListOf<Move>()
        var boundedSearches = 0

        repeat(maxPlies) { ply ->
            val legal = ShogiRules.legalMoves(position)
            if (legal.isEmpty()) return terminal(initial, moves, position, if (ShogiRules.isInCheck(position, position.activePlayer)) mateFor(position.activePlayer.opponent()) else Termination.NO_LEGAL_MOVE, boundedSearches)
            val engine = if (position.activePlayer == Player.SENTE) sente else gote
            val result = engine.analyze(position, requestFor(position.activePlayer, ply, moves))
            if (result.reachedSearchLimit) boundedSearches++
            val move = result.candidates.firstOrNull()?.move
                ?: return terminal(initial, moves, position, Termination.INVALID_ENGINE_MOVE, boundedSearches)
            if (legal.none { it.sameAction(move) }) return terminal(initial, moves, position, Termination.INVALID_ENGINE_MOVE, boundedSearches)
            position = ShogiRules.applyKnownLegal(position, move)
            moves += move
            if (ShogiRules.isCheckmate(position)) return terminal(initial, moves, position, mateFor(move.player), boundedSearches)
            when (repetition.record(position, move.player, ShogiRules.isInCheck(position, position.activePlayer))) {
                RepetitionResult.DRAW -> return terminal(initial, moves, position, Termination.DRAW_REPETITION, boundedSearches)
                RepetitionResult.SENTE_LOSES_BY_PERPETUAL_CHECK -> return terminal(initial, moves, position, Termination.SENTE_LOSES_PERPETUAL_CHECK, boundedSearches)
                RepetitionResult.GOTE_LOSES_BY_PERPETUAL_CHECK -> return terminal(initial, moves, position, Termination.GOTE_LOSES_PERPETUAL_CHECK, boundedSearches)
                RepetitionResult.NONE -> Unit
            }
        }
        return terminal(initial, moves, position, Termination.PLY_LIMIT, boundedSearches)
    }

    private fun mateFor(winner: Player) = if (winner == Player.SENTE) Termination.SENTE_MATE else Termination.GOTE_MATE
    private fun terminal(initial: ShogiPosition, moves: List<Move>, position: ShogiPosition, termination: Termination, bounded: Int) =
        Result(initial, moves.toList(), position, termination, bounded)

    private fun Move.sameAction(other: Move) = from == other.from && to == other.to && piece == other.piece && player == other.player && promote == other.promote
}
