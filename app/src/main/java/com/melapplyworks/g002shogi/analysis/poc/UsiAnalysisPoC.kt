package com.melapplyworks.g002shogi.analysis.poc

import com.melapplyworks.g002shogi.model.AnalysisResult
import com.melapplyworks.g002shogi.model.CandidateMove
import com.melapplyworks.g002shogi.model.Explanation
import com.melapplyworks.g002shogi.model.ShogiPosition
import com.melapplyworks.g002shogi.model.VariationLine
import com.melapplyworks.g002shogi.rules.SfenCodec

/** Isolated Step 5 spike. It parses USI output but neither launches nor bundles an engine. */
sealed interface UsiEngineEvent {
    data class Info(val value: UsiInfo) : UsiEngineEvent
    data class BestMove(val move: String, val ponder: String? = null) : UsiEngineEvent
}

sealed interface UsiScore {
    data class Centipawn(val value: Int) : UsiScore
    data class Mate(val moves: Int) : UsiScore
}

data class UsiInfo(
    val multiPv: Int = 1,
    val score: UsiScore? = null,
    val depth: Int? = null,
    val nodes: Long? = null,
    val timeMillis: Long? = null,
    val pv: List<String> = emptyList()
)

data class UsiAnalysisSnapshot(val infos: List<UsiInfo>, val bestMove: String? = null)

/** Tolerant parser: unknown and malformed fields never escape into the UI. */
class UsiAnalysisParser {
    fun parseLine(line: String): UsiEngineEvent? {
        val tokens = line.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (tokens.isEmpty()) return null
        return when (tokens.first()) {
            "info" -> UsiEngineEvent.Info(parseInfo(tokens.drop(1)))
            "bestmove" -> tokens.getOrNull(1)?.let { move ->
                UsiEngineEvent.BestMove(move, tokens.drop(2).windowed(2).firstOrNull { it[0] == "ponder" }?.get(1))
            }
            else -> null
        }
    }

    private fun parseInfo(tokens: List<String>): UsiInfo {
        var multiPv = 1; var score: UsiScore? = null; var depth: Int? = null
        var nodes: Long? = null; var timeMillis: Long? = null; var pv = emptyList<String>(); var index = 0
        while (index < tokens.size) {
            when (tokens[index]) {
                "multipv" -> { tokens.getOrNull(index + 1)?.toIntOrNull()?.takeIf { it > 0 }?.let { multiPv = it }; index += 2 }
                "depth" -> { tokens.getOrNull(index + 1)?.toIntOrNull()?.let { depth = it }; index += 2 }
                "nodes" -> { tokens.getOrNull(index + 1)?.toLongOrNull()?.let { nodes = it }; index += 2 }
                "time" -> { tokens.getOrNull(index + 1)?.toLongOrNull()?.let { timeMillis = it }; index += 2 }
                "score" -> {
                    score = when (tokens.getOrNull(index + 1)) {
                        "cp" -> tokens.getOrNull(index + 2)?.toIntOrNull()?.let(UsiScore::Centipawn)
                        "mate" -> parseMate(tokens.getOrNull(index + 2))
                        else -> null
                    }
                    index += 3
                }
                "pv" -> { pv = tokens.drop(index + 1); index = tokens.size }
                else -> index++
            }
        }
        return UsiInfo(multiPv, score, depth, nodes, timeMillis, pv)
    }

    private fun parseMate(value: String?): UsiScore.Mate? = when (value) {
        "+" -> UsiScore.Mate(1); "-" -> UsiScore.Mate(-1)
        else -> value?.toIntOrNull()?.let(UsiScore::Mate)
    }
}

/** Accumulates the latest complete PV for each MultiPV rank of one analysis request. */
class UsiAnalysisSession {
    private val latestByRank = mutableMapOf<Int, UsiInfo>()
    private var bestMove: String? = null

    fun accept(event: UsiEngineEvent) {
        when (event) {
            is UsiEngineEvent.Info -> if (event.value.pv.isNotEmpty()) latestByRank[event.value.multiPv] = event.value
            is UsiEngineEvent.BestMove -> bestMove = event.move
        }
    }

    fun snapshot() = UsiAnalysisSnapshot(latestByRank.values.sortedBy { it.multiPv }, bestMove)
}

data class UsiAnalysisEnvelope(val positionId: String, val snapshot: UsiAnalysisSnapshot)

/** Converts a USI snapshot to G002 models and rejects a stale position before UI conversion. */
class UsiAnalysisAdapter {
    fun toAnalysisResult(envelope: UsiAnalysisEnvelope, currentPositionId: String, position: ShogiPosition, maxCandidates: Int = 3): AnalysisResult? {
        if (envelope.positionId != currentPositionId) return null
        val candidates = envelope.snapshot.infos.sortedBy { it.multiPv }.take(maxCandidates)
            .mapNotNull { toCandidate(position, currentPositionId, it) }
        return AnalysisResult(position, candidates, isSample = true, positionId = currentPositionId)
    }

    private fun toCandidate(position: ShogiPosition, positionId: String, info: UsiInfo): CandidateMove? {
        val moves = SfenCodec.parseUsiVariation(position, info.pv)
        val move = moves.firstOrNull() ?: return null
        val scoreText = when (val score = info.score) {
            is UsiScore.Centipawn -> "評価 ${score.value} cp"
            is UsiScore.Mate -> "詰み ${score.moves}"
            null -> "評価値は未出力"
        }
        return CandidateMove(
            id = "$positionId-multipv-${info.multiPv}", move = move,
            purpose = "USI候補 ${info.multiPv}（$scoreText）",
            explanation = Explanation("USI読み筋 ${info.multiPv}", "Step 5の隔離PoCでUSI出力を変換した候補です。人間向けの意味・狙いはまだ生成しません。", "読み筋を一手ずつ確認します。"),
            evaluationForFuture = (info.score as? UsiScore.Centipawn)?.value?.div(100.0),
            variation = VariationLine(moves, List(moves.size) { "USI読み筋の手順です。" }),
            rankForFuture = info.multiPv, positionId = positionId,
            comparisonSummary = "USI評価とPVのPoC結果です。正式エンジン採用ではありません。"
        )
    }
}
