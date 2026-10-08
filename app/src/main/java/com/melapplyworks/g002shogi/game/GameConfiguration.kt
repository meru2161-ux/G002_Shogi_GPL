package com.melapplyworks.g002shogi.game

import com.melapplyworks.g002shogi.analysis.AnalysisRequest
import com.melapplyworks.g002shogi.model.Player

enum class GameDifficulty(
    val label: String,
    val description: String,
    val maxDepth: Int,
    val nodeLimit: Int,
    val timeLimitMillis: Long
) {
    BEGINNER("入門", "短く考え、将棋を始めたばかりでも指しやすい強さ", 2, 20_000, 450),
    STANDARD("標準", "基本戦術と序盤方針をバランスよく読む標準設定", 3, 80_000, 1_200),
    STRONG("強め", "端末内で許す範囲まで深く読む設定", 5, 160_000, 2_000);

    fun analysisRequest(positionId: String, isCancelled: () -> Boolean = { false }) = AnalysisRequest(
        positionId = positionId,
        maxDepth = maxDepth,
        nodeLimit = nodeLimit,
        timeLimitMillis = timeLimitMillis,
        isCancelled = isCancelled
    )
}

data class GameConfiguration(
    val mode: GameMode,
    val humanPlayer: Player = Player.SENTE,
    val difficulty: GameDifficulty = defaultOpponentDifficulty(mode)
)

/**
 * A normal AI match should not silently start at the middle budget when the
 * user expects the strongest available local opponent. Coaching keeps a
 * moderate opponent by default because teacher analysis is strong either way.
 */
fun defaultOpponentDifficulty(mode: GameMode): GameDifficulty = when (mode) {
    GameMode.COACHING -> GameDifficulty.STANDARD
    GameMode.AI_MATCH -> GameDifficulty.STRONG
}

/** Teacher review is independent from the opponent difficulty. */
fun teacherAnalysisRequest(positionId: String, isCancelled: () -> Boolean = { false }) =
    GameDifficulty.STRONG.analysisRequest(positionId, isCancelled)

