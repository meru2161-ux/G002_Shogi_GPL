package com.melapplyworks.g002shogi.analysis

import com.melapplyworks.g002shogi.model.AnalysisResult
import com.melapplyworks.g002shogi.model.Move
import com.melapplyworks.g002shogi.model.ShogiPosition
import com.melapplyworks.g002shogi.model.ShogiPositions

interface ShogiAnalysisEngine : AutoCloseable {
    fun analyze(position: ShogiPosition, request: AnalysisRequest = AnalysisRequest()): AnalysisResult

    /** Optional exact-position review for a move selected by the learner. */
    fun assessMove(position: ShogiPosition, move: Move, request: AnalysisRequest = AnalysisRequest()): MoveAssessment? = null

    fun analyzeInitialPosition(): AnalysisResult = analyze(ShogiPositions.initial())

    override fun close() = Unit
}

/** A bounded mobile budget keeps the stronger local search responsive and cancellable. */
data class AnalysisRequest(
    val positionId: String = "initial",
    val requestId: String = "",
    val maxDepth: Int = 4,
    val nodeLimit: Int = 160_000,
    val timeLimitMillis: Long = 2_000,
    /** Recent real-game moves, oldest first. Used only to avoid aimless immediate reversals. */
    val recentMoves: List<Move> = emptyList(),
    val isCancelled: () -> Boolean = { false }
)

data class MoveAssessment(
    val scoreForMover: Int,
    val principalVariation: List<Move>,
    /** The score is useful guidance, but the requested local search did not finish. */
    val reachedSearchLimit: Boolean = false
)
