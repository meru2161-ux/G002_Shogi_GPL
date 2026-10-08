package com.melapplyworks.g002shogi.game

import com.melapplyworks.g002shogi.analysis.AnalysisRequest
import com.melapplyworks.g002shogi.analysis.MoveAssessment
import com.melapplyworks.g002shogi.analysis.ShogiAnalysisEngine
import com.melapplyworks.g002shogi.coaching.PlayerMoveCoach
import com.melapplyworks.g002shogi.coaching.UserMoveReview
import com.melapplyworks.g002shogi.model.*
import com.melapplyworks.g002shogi.rules.RepetitionResult
import com.melapplyworks.g002shogi.rules.RepetitionTracker
import com.melapplyworks.g002shogi.rules.ShogiRules

enum class GameMode { COACHING, AI_MATCH }
enum class SessionPhase { ANALYZING, WAITING_FOR_HUMAN, REVIEWING_HUMAN, AI_THINKING, FINISHED }
enum class SessionOutcome { SENTE_WIN, GOTE_WIN, DRAW_REPETITION, SENTE_LOSES_PERPETUAL_CHECK, GOTE_LOSES_PERPETUAL_CHECK, NO_LEGAL_MOVE, RESIGNED }

data class GameRecord(
    val move: Move,
    val before: ShogiPosition,
    val after: ShogiPosition,
    val isHuman: Boolean,
    val review: UserMoveReview? = null,
    val analysisCandidate: CandidateMove? = null,
    val assessment: MoveAssessment? = null
)

data class GameSessionState(
    val mode: GameMode,
    val humanPlayer: Player,
    val position: ShogiPosition,
    val positionId: String,
    val phase: SessionPhase,
    val analysis: AnalysisResult? = null,
    val records: List<GameRecord> = emptyList(),
    val outcome: SessionOutcome? = null,
    val statusMessage: String = "局面を準備しています。"
)

data class SessionMoveAssessment(
    val requestId: String,
    val positionId: String,
    val assessment: MoveAssessment?
)

/** One authoritative transition path for both coaching and ordinary AI matches. */
class ShogiGameSession(
    private val engine: ShogiAnalysisEngine,
    private val initialPosition: ShogiPosition = ShogiPositions.initial(),
    private val mode: GameMode = GameMode.COACHING,
    private val humanPlayer: Player = Player.SENTE,
    restoredMoves: List<Move> = emptyList(),
    restoredOutcome: SessionOutcome? = null
) {
    private var repetition = RepetitionTracker()
    private var generation = 0
    private var requestSequence = 0L
    private var pendingAnalysisRequestId: String? = null
    private var pendingReviewRequestId: String? = null
    var state: GameSessionState = freshState()
        private set

    init {
        restoredMoves.forEach { move ->
            require(state.phase != SessionPhase.FINISHED && move.player == state.position.activePlayer && ShogiRules.isLegal(state.position, move)) {
                "Saved game contains an illegal move"
            }
            val before = state.position
            val after = ShogiRules.apply(before, move)
            appendMove(move, before, after, isHuman = move.player == humanPlayer, review = null)
        }
        if (restoredOutcome != null) {
            state = state.copy(phase = SessionPhase.FINISHED, outcome = restoredOutcome, analysis = null, statusMessage = "保存した終局局面を開きました。")
        } else if (state.phase != SessionPhase.FINISHED) {
            state = state.copy(
                phase = if (state.position.activePlayer == humanPlayer) SessionPhase.ANALYZING else SessionPhase.AI_THINKING,
                analysis = null,
                statusMessage = "保存した対局を再開します。"
            )
        }
    }

    fun restart() {
        repetition = RepetitionTracker()
        generation++
        pendingAnalysisRequestId = null
        pendingReviewRequestId = null
        state = freshState()
    }

    fun installAnalysis(result: AnalysisResult): Boolean {
        if (state.phase != SessionPhase.ANALYZING || result.requestId != pendingAnalysisRequestId || result.positionId != state.positionId || result.position != state.position) return false
        pendingAnalysisRequestId = null
        state = state.copy(analysis = result, phase = SessionPhase.WAITING_FOR_HUMAN, statusMessage = "あなたの番です。候補と盤上の利きを見て、一手を選んでください。")
        return true
    }

    fun analyzeCurrent(request: AnalysisRequest = AnalysisRequest(positionId = state.positionId)): AnalysisResult {
        val requestId = "g$generation-r${state.records.size}-q${++requestSequence}"
        pendingAnalysisRequestId = requestId
        return engine.analyze(
            state.position,
            request.copy(
                positionId = state.positionId,
                requestId = requestId,
                recentMoves = state.records.takeLast(8).map { it.move }
            )
        )
    }

    fun submitHumanMove(move: Move): Boolean {
        if (state.phase != SessionPhase.WAITING_FOR_HUMAN || state.position.activePlayer != state.humanPlayer || !ShogiRules.isLegal(state.position, move)) return false
        val before = state.position
        val after = ShogiRules.apply(before, move)
        val review = state.analysis?.let { PlayerMoveCoach.record(state.positionId, before, move, after, it) }
        appendMove(move, before, after, isHuman = true, review = review)
        if (state.phase == SessionPhase.FINISHED) return true
        state = if (mode == GameMode.COACHING) state.copy(phase = SessionPhase.REVIEWING_HUMAN, statusMessage = "先生のレビューを確認してから、相手の応手を見ましょう。")
        else state.copy(phase = SessionPhase.AI_THINKING, statusMessage = "AIが応手を考えています。")
        return true
    }

    fun continueAfterReview(): Boolean {
        if (state.phase != SessionPhase.REVIEWING_HUMAN) return false
        state = state.copy(phase = SessionPhase.AI_THINKING, statusMessage = "相手AIが応手を考えています。")
        return true
    }

    fun assessLastHumanMove(request: AnalysisRequest = AnalysisRequest()): SessionMoveAssessment? {
        val last = state.records.lastOrNull() ?: return null
        if (state.phase != SessionPhase.REVIEWING_HUMAN || !last.isHuman || last.assessment != null) return null
        val requestId = "g$generation-r${state.records.size}-review${++requestSequence}"
        pendingReviewRequestId = requestId
        val assessment = engine.assessMove(last.before, last.move, request.copy(positionId = state.positionId, requestId = requestId))
        return SessionMoveAssessment(requestId, state.positionId, assessment)
    }

    fun installUserAssessment(result: SessionMoveAssessment): Boolean {
        val last = state.records.lastOrNull() ?: return false
        val assessment = result.assessment ?: return false
        if (state.phase != SessionPhase.REVIEWING_HUMAN || result.requestId != pendingReviewRequestId || state.positionId != result.positionId || !last.isHuman || last.assessment != null) return false
        pendingReviewRequestId = null
        state = state.copy(records = state.records.dropLast(1) + last.copy(assessment = assessment))
        return true
    }

    /** Rebuilds repetition state from the retained record, so an undo cannot leak a future result. */
    fun undoLastMove(): Boolean {
        if (state.records.isEmpty()) return false
        val kept = state.records.dropLast(1)
        repetition = RepetitionTracker()
        repetition.record(initialPosition, null, false)
        kept.forEach { record -> repetition.record(record.after, record.move.player, ShogiRules.isInCheck(record.after, record.after.activePlayer)) }
        val position = kept.lastOrNull()?.after ?: initialPosition
        state = state.copy(
            position = position,
            positionId = "g$generation-p${kept.size}",
            phase = if (position.activePlayer == humanPlayer) SessionPhase.ANALYZING else SessionPhase.AI_THINKING,
            analysis = null,
            records = kept,
            outcome = null,
            statusMessage = "一手戻しました。局面をあらためて解析します。"
        )
        pendingAnalysisRequestId = null
        pendingReviewRequestId = null
        return true
    }

    fun playAiMove(result: AnalysisResult): Boolean {
        if (state.phase != SessionPhase.AI_THINKING || result.requestId != pendingAnalysisRequestId || result.positionId != state.positionId || result.position != state.position) return false
        pendingAnalysisRequestId = null
        pendingReviewRequestId = null
        val candidate = result.candidates.firstOrNull() ?: return finishNoLegalMove()
        val move = candidate.move
        if (!ShogiRules.isLegal(state.position, move)) return false
        val before = state.position
        val after = ShogiRules.apply(before, move)
        appendMove(move, before, after, isHuman = false, review = null, analysisCandidate = candidate)
        if (state.phase != SessionPhase.FINISHED) {
            state = state.copy(
                phase = if (after.activePlayer == humanPlayer) SessionPhase.ANALYZING else SessionPhase.AI_THINKING,
                analysis = null,
                statusMessage = if (after.activePlayer == humanPlayer) "相手の狙いを確認し、新しい候補から次の一手を考えましょう。" else "AIが続けて考えています。"
            )
        }
        return true
    }

    fun resign(): Boolean {
        if (state.phase == SessionPhase.FINISHED) return false
        val outcome = if (humanPlayer == Player.SENTE) SessionOutcome.GOTE_WIN else SessionOutcome.SENTE_WIN
        state = state.copy(phase = SessionPhase.FINISHED, outcome = outcome, statusMessage = "投了しました。対局を振り返って、次の一局へ進みましょう。")
        return true
    }

    private fun appendMove(move: Move, before: ShogiPosition, after: ShogiPosition, isHuman: Boolean, review: UserMoveReview?, analysisCandidate: CandidateMove? = null) {
        pendingAnalysisRequestId = null
        val repetitionResult = repetition.record(after, move.player, ShogiRules.isInCheck(after, after.activePlayer))
        state = state.copy(position = after, positionId = "g$generation-p${state.records.size + 1}", records = state.records + GameRecord(move, before, after, isHuman, review, analysisCandidate), analysis = null)
        when {
            ShogiRules.isCheckmate(after) -> {
                val outcome = if (move.player == Player.SENTE) SessionOutcome.SENTE_WIN else SessionOutcome.GOTE_WIN
                state = state.copy(phase = SessionPhase.FINISHED, outcome = outcome, statusMessage = "詰みです。${if (move.player == Player.SENTE) "先手" else "後手"}の勝ちです。")
            }
            repetitionResult != RepetitionResult.NONE -> {
                val outcome = when (repetitionResult) {
                    RepetitionResult.DRAW -> SessionOutcome.DRAW_REPETITION
                    RepetitionResult.SENTE_LOSES_BY_PERPETUAL_CHECK -> SessionOutcome.SENTE_LOSES_PERPETUAL_CHECK
                    RepetitionResult.GOTE_LOSES_BY_PERPETUAL_CHECK -> SessionOutcome.GOTE_LOSES_PERPETUAL_CHECK
                    RepetitionResult.NONE -> error("handled above")
                }
                state = state.copy(phase = SessionPhase.FINISHED, outcome = outcome, statusMessage = "同一局面が4回現れたため、対局を終了します。")
            }
        }
    }

    private fun finishNoLegalMove(): Boolean {
        if (ShogiRules.isCheckmate(state.position)) {
            val winner = state.position.activePlayer.opponent()
            state = state.copy(phase = SessionPhase.FINISHED, outcome = if (winner == Player.SENTE) SessionOutcome.SENTE_WIN else SessionOutcome.GOTE_WIN, statusMessage = "詰みです。")
        } else {
            state = state.copy(phase = SessionPhase.FINISHED, outcome = SessionOutcome.NO_LEGAL_MOVE, statusMessage = "合法手がない局面のため、対局を終了します。")
        }
        return true
    }

    private fun freshState(): GameSessionState {
        repetition.record(initialPosition, null, false)
        return GameSessionState(mode, humanPlayer, initialPosition, "g$generation-p0", if (initialPosition.activePlayer == humanPlayer) SessionPhase.ANALYZING else SessionPhase.AI_THINKING)
    }
}
