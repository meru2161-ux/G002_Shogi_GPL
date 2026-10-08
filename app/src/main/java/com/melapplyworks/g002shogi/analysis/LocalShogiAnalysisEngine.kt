package com.melapplyworks.g002shogi.analysis

import com.melapplyworks.g002shogi.model.*
import com.melapplyworks.g002shogi.rules.ShogiRules

/**
 * G002's own small, offline engine.  It deliberately favors a short and stable
 * search over maximum strength: all moves come from [ShogiRules], so a candidate
 * can never bypass the app's legal-move layer.
 */
class LocalShogiAnalysisEngine : ShogiAnalysisEngine {
    override fun analyze(position: ShogiPosition, request: AnalysisRequest): AnalysisResult {
        val search = Search(request)
        val legal = ShogiRules.legalMoves(position)
        if (legal.isEmpty() || request.isCancelled()) {
            return AnalysisResult(position, emptyList(), isSample = false, positionId = request.positionId, requestId = request.requestId)
        }

        var completed = search.rootLines(position, depth = 1).lines
        // Do not lock every deeper iteration to the three moves selected by a
        // one-ply evaluation.  A quiet defensive or tactical move can be just
        // outside that first shortlist and still become best once replies are
        // searched.  Keep a small mobile-friendly frontier, while the UI still
        // receives only the final top three.
        var principalMoves = completed.take(ROOT_FRONTIER_SIZE).mapNotNull { it.moves.firstOrNull() }
        for (depth in 2..request.maxDepth.coerceAtLeast(1)) {
            if (search.shouldStop()) break
            val next = search.rootLines(position, depth, principalMoves)
            if (next.lines.isEmpty()) break
            if (!next.completed) break
            completed = next.lines
            principalMoves = completed.take(ROOT_FRONTIER_SIZE).mapNotNull { it.moves.firstOrNull() }
        }
        val candidates = completed.take(3).mapIndexed { index, line -> candidate(position, line, index + 1, request.positionId) }
        return AnalysisResult(
            position,
            candidates,
            isSample = false,
            positionId = request.positionId,
            requestId = request.requestId,
            reachedSearchLimit = search.reachedSearchLimit()
        )
    }

    override fun assessMove(position: ShogiPosition, move: Move, request: AnalysisRequest): MoveAssessment? {
        if (!ShogiRules.isLegal(position, move) || request.isCancelled()) return null
        val search = Search(request)
        val line = search.lineForMove(position, move, request.maxDepth.coerceAtLeast(1)) ?: return null
        return MoveAssessment(line.score, line.moves.map(::withNotation), reachedSearchLimit = search.reachedSearchLimit())
    }

    private fun candidate(position: ShogiPosition, line: Line, rank: Int, positionId: String): CandidateMove {
        val move = withNotation(line.moves.first())
        val after = ShogiRules.applyKnownLegal(position, line.moves.first())
        val givesCheck = ShogiRules.isInCheck(after, after.activePlayer)
        val captured = position.board[move.to]
        val strategicPurpose = ShogiStrategy.teachingPurpose(position, move)
        val strategicIntent = ShogiStrategy.teachingIntent(position, move)
        val strategicCaution = ShogiStrategy.teachingCaution(position, move)
        // Explain the plan produced by this candidate, not only the plan before it.
        val strategicProfile = ShogiStrategy.profile(after, move.player)
        val purpose = when {
            givesCheck -> "王に迫り、相手の応手を限定する"
            captured != null -> "${captured.type.unpromoted.label}を取り、持ち駒を増やす"
            move.promote -> "成って駒の働きを強める"
            move.isDrop -> "持ち駒を使って要所を押さえる"
            strategicPurpose != null -> strategicPurpose
            move.piece == PieceType.PAWN -> "歩を進めて駒組みと攻めの余地を作る"
            else -> "${move.piece.label}の働きを高め、次の狙いを作る"
        }
        val pv = line.moves.map(::withNotation)
        val opponent = pv.getOrNull(1)?.let { "有力な応手の一例は ${it.notation} です。" } ?: "相手の応手を見ながら、駒の利きを確認します。"
        val continuation = pv.getOrNull(2)?.let { "続きの目安は ${it.notation}。相手の形に応じて調整しましょう。" }
            ?: "次は相手の狙いに対応できる駒の配置を考えましょう。"
        val assessment = when {
            line.score >= 180 -> "局面を少し有利にしやすい候補です。"
            line.score >= -80 -> "大きな無理のない、自然な候補です。"
            else -> "相手の反撃もあるため、読み筋をよく確認したい候補です。"
        }
        return CandidateMove(
            id = "$positionId-$rank-${move.from}-${move.to}-${move.promote}",
            move = move,
            purpose = purpose,
            explanation = Explanation(
                title = "候補$rank：${move.notation}",
                body = "$assessment\n\n【現在の方針】${strategicProfile.summary}\n$purpose",
                nextStep = continuation,
                meaning = "$assessment ${move.notation}は、$purpose。",
                intent = strategicIntent ?: "この手から、相手より先に駒を働かせる形を目指します。",
                opponentResponse = opponent,
                continuation = continuation,
                caution = if (givesCheck) "王手後は、相手の逃げ道や反撃の利きを必ず確認しましょう。" else strategicCaution
                    ?: "自分の玉まわりが薄くならないか、相手の次の一手も確認しましょう。"
            ),
            evaluationForFuture = line.score / 100.0,
            variation = VariationLine(pv, pv.mapIndexed { index, pvMove -> if (index == 0) "まず ${pvMove.notation} を考えます。" else "読み筋の${index + 1}手目：${pvMove.notation}" }),
            rankForFuture = rank,
            positionId = positionId,
            comparisonSummary = "候補$rank は、${strategicProfile.summary}を踏まえて、${purpose}という方針です。"
        )
    }

    private fun withNotation(move: Move): Move = move.copy(notation = move.notation.ifBlank { ShogiMoveText.display(move) })

    private companion object {
        const val ROOT_FRONTIER_SIZE = 12
    }

    private data class Line(val score: Int, val moves: List<Move>)
    private data class RootSearch(val lines: List<Line>, val completed: Boolean)
    private enum class TranspositionBound { EXACT, LOWER, UPPER }
    private data class TranspositionKey(val position: ShogiPosition, val ply: Int, val checkExtensionsRemaining: Int)
    private data class TranspositionEntry(val depth: Int, val line: Line, val bound: TranspositionBound, val bestMove: Move?)

    private class Search(private val request: AnalysisRequest) {
        private val deadlineNanos = System.nanoTime() + request.timeLimitMillis.coerceAtLeast(1) * 1_000_000L
        // Mate scores include the current ply, so the ply is part of the key.
        // Depth stays in the entry to let deeper iterative passes reuse shallower
        // work without mixing incompatible mate distances.
        private val transpositions = HashMap<TranspositionKey, TranspositionEntry>()
        private val killers = HashMap<Int, ArrayDeque<Move>>()
        private val history = HashMap<MoveHistoryKey, Int>()
        private var nodes = 0
        private var reachedLimit = false

        private data class MoveHistoryKey(val player: Player, val piece: PieceType, val from: Square?, val to: Square, val promote: Boolean)

        fun shouldStop(): Boolean {
            val stop = request.isCancelled() || nodes >= request.nodeLimit || System.nanoTime() >= deadlineNanos
            if (stop) reachedLimit = true
            return stop
        }

        fun reachedSearchLimit(): Boolean = reachedLimit

        fun rootLines(position: ShogiPosition, depth: Int, selectedRootMoves: List<Move>? = null): RootSearch {
            val lines = mutableListOf<Line>()
            val rootMoves = selectedRootMoves ?: ordered(
                position,
                ShogiRules.legalMoves(position),
                0,
                transpositions[TranspositionKey(position, 0, CHECK_EXTENSIONS)]?.bestMove
            )
            var searchedMoves = 0
            for (move in rootMoves) {
                if (shouldStop()) break
                lineForMove(position, move, depth)?.let(lines::add)
                searchedMoves++
            }
            // A slow device must still be able to teach from several legal choices.
            // The fallback does not invent moves: it evaluates only legal root moves
            // that the full search could not reach before its short deadline.
            rootMoves.asSequence()
                .filter { move -> lines.none { it.moves.firstOrNull() == move } }
                .take((3 - lines.size).coerceAtLeast(0))
                .forEach { move ->
                    val after = ShogiRules.applyKnownLegal(position, move)
                    lines += Line(-evaluate(after), listOf(move))
                }
            return RootSearch(lines.sortedByDescending { it.score }, searchedMoves == rootMoves.size && !shouldStop())
        }

        fun lineForMove(position: ShogiPosition, move: Move, depth: Int): Line? {
            if (shouldStop()) return null
            val child = ShogiRules.applyKnownLegal(position, move)
            val tactical = position.board[move.to] != null || move.promote
            val reply = negamax(child, depth - 1, -MATE, MATE, 1, tactical, CHECK_EXTENSIONS)
            // Opening principles previously affected only move ordering, so a
            // strategically poor move could still return to the final top three
            // after the search. Apply the root preference once to the actual
            // result; deeper nodes remain governed by the normal evaluation.
            // Ordering values are intentionally strong; only a restrained
            // fraction belongs in evaluation so principle never outweighs a
            // clear material tactic.
            val rootStrategy = ShogiStrategy.openingMoveBonus(position, move) / 3
            val repetitionPenalty = immediateQuietReversalPenalty(position, move)
            return Line(-reply.score + rootStrategy + repetitionPenalty, listOf(move) + reply.moves)
        }

        /**
         * A stateless position evaluator cannot see that a king or gold just
         * returned to the square it left two plies ago.  Pass a short real-game
         * history into root search and discourage that quiet reversal, while
         * leaving captures, promotions, checks and forced single replies alone.
         */
        private fun immediateQuietReversalPenalty(position: ShogiPosition, move: Move): Int {
            val from = move.from ?: return 0
            val previous = request.recentMoves.lastOrNull {
                it.player == move.player && it.from == move.to && it.to == from &&
                    it.piece.unpromoted == move.piece.unpromoted
            } ?: return 0
            if (previous.from == null) return 0
            if (position.board[move.to] != null || move.promote) return 0
            val after = ShogiRules.applyKnownLegal(position, move)
            if (ShogiRules.isInCheck(after, after.activePlayer)) return 0
            if (ShogiRules.legalMoves(position).size <= 1) return 0
            return -240
        }

        private fun negamax(
            position: ShogiPosition,
            depth: Int,
            alphaStart: Int,
            beta: Int,
            ply: Int,
            lastMoveWasTactical: Boolean,
            checkExtensionsRemaining: Int
        ): Line {
            nodes++
            if (shouldStop()) return Line(evaluate(position), emptyList())
            if (depth <= 0) {
                val inCheck = ShogiRules.isInCheck(position, position.activePlayer)
                return if (lastMoveWasTactical || inCheck) {
                    quiescence(position, alphaStart, beta, ply, 0)
                } else Line(evaluate(position), emptyList())
            }
            val moves = ShogiRules.legalMoves(position)
            if (moves.isEmpty()) return Line(if (ShogiRules.isInCheck(position, position.activePlayer)) -MATE + ply else 0, emptyList())
            val transpositionKey = TranspositionKey(position, ply, checkExtensionsRemaining)
            val cached = transpositions[transpositionKey]
            if (cached != null && cached.depth >= depth) {
                when (cached.bound) {
                    TranspositionBound.EXACT -> return cached.line
                    TranspositionBound.LOWER -> if (cached.line.score >= beta) return cached.line
                    TranspositionBound.UPPER -> if (cached.line.score <= alphaStart) return cached.line
                }
            }
            var alpha = alphaStart
            var best = Line(-MATE, emptyList())
            for ((moveIndex, move) in ordered(position, moves, ply, cached?.bestMove).withIndex()) {
                if (shouldStop()) break
                val tactical = position.board[move.to] != null || move.promote
                val child = ShogiRules.applyKnownLegal(position, move)
                val givesCheck = ShogiRules.isInCheck(child, child.activePlayer)
                val reduction = if (depth >= 3 && moveIndex >= 4 && !tactical && !givesCheck) 1 else 0
                val checkExtension = if (givesCheck && checkExtensionsRemaining > 0) 1 else 0
                val nextExtensionsRemaining = checkExtensionsRemaining - checkExtension
                val searchDepth = depth - 1 - reduction + checkExtension
                // Principal-variation search: after the first ordered move has
                // established alpha, probe later moves with a null window. Only
                // a move that can improve the line pays for a full-window search.
                // This preserves the exact PV while spending the mobile budget
                // on promising shogi moves instead of repeatedly proving that a
                // late quiet move is worse.
                var reply = if (moveIndex == 0) {
                    negamax(child, searchDepth, -beta, -alpha, ply + 1, tactical, nextExtensionsRemaining)
                } else {
                    negamax(child, searchDepth, -alpha - 1, -alpha, ply + 1, tactical, nextExtensionsRemaining)
                }
                var score = -reply.score
                if (moveIndex > 0 && score > alpha && (reduction > 0 || score < beta) && !shouldStop()) {
                    reply = negamax(child, depth - 1 + checkExtension, -beta, -alpha, ply + 1, tactical, nextExtensionsRemaining)
                    score = -reply.score
                }
                if (score > best.score) best = Line(score, listOf(move) + reply.moves)
                if (score > alpha) alpha = score
                if (alpha >= beta) {
                    if (!tactical) recordCutoff(move, ply, depth)
                    break
                }
            }
            val result = if (best.moves.isEmpty()) Line(evaluate(position), emptyList()) else best
            if (!shouldStop()) {
                val bound = when {
                    result.score <= alphaStart -> TranspositionBound.UPPER
                    result.score >= beta -> TranspositionBound.LOWER
                    else -> TranspositionBound.EXACT
                }
                if (cached == null || depth >= cached.depth) {
                    transpositions[transpositionKey] = TranspositionEntry(depth, result, bound, result.moves.firstOrNull())
                }
            }
            return result
        }

        private fun quiescence(position: ShogiPosition, alphaStart: Int, beta: Int, ply: Int, qDepth: Int): Line {
            nodes++
            if (shouldStop()) return Line(evaluate(position), emptyList())
            val legal = ShogiRules.legalMoves(position)
            if (legal.isEmpty()) return Line(if (ShogiRules.isInCheck(position, position.activePlayer)) -MATE + ply else 0, emptyList())
            val inCheck = ShogiRules.isInCheck(position, position.activePlayer)
            var alpha = alphaStart
            var best = if (inCheck) Line(-MATE, emptyList()) else Line(evaluate(position), emptyList())
            if (!inCheck) {
                if (best.score >= beta) return best
                if (best.score > alpha) alpha = best.score
            }
            if (qDepth >= 4) return if (best.score == -MATE) Line(evaluate(position), emptyList()) else best
            val tactical = if (inCheck) legal else legal.filter { position.board[it.to] != null || it.promote }
            for (move in ordered(position, tactical, ply, transpositions[TranspositionKey(position, ply, 0)]?.bestMove)) {
                if (shouldStop()) break
                val reply = quiescence(ShogiRules.applyKnownLegal(position, move), -beta, -alpha, ply + 1, qDepth + 1)
                val score = -reply.score
                if (score > best.score) best = Line(score, listOf(move) + reply.moves)
                if (score > alpha) alpha = score
                if (alpha >= beta) break
            }
            return if (best.score == -MATE) Line(evaluate(position), emptyList()) else best
        }

        private fun ordered(position: ShogiPosition, moves: List<Move>, ply: Int, hashMove: Move?): List<Move> =
            moves.sortedByDescending { move ->
                val captured = position.board[move.to]?.type?.let(::value) ?: 0
                val attacker = value(move.piece)
                val promotion = if (move.promote) 260 else 0
                val tactical = captured > 0 || move.promote
                val child = if (tactical || move == hashMove) ShogiRules.applyKnownLegal(position, move) else null
                val givesCheck = if (child != null && ShogiRules.isInCheck(child, child.activePlayer)) 520 else 0
                val killerIndex = killers[ply]?.indexOf(move) ?: -1
                val killer = if (killerIndex >= 0) 380 - killerIndex * 80 else 0
                val historical = history[move.historyKey()] ?: 0
                val hash = if (move == hashMove) 1_000_000 else 0
                hash + captured * 16 - (if (captured > 0) attacker else 0) +
                    promotion + givesCheck + killer + historical + ShogiStrategy.openingMoveBonus(position, move)
            }

        private fun recordCutoff(move: Move, ply: Int, depth: Int) {
            val queue = killers.getOrPut(ply) { ArrayDeque(2) }
            queue.remove(move)
            queue.addFirst(move)
            while (queue.size > 2) queue.removeLast()
            val key = move.historyKey()
            history[key] = ((history[key] ?: 0) + depth * depth).coerceAtMost(20_000)
        }

        private fun Move.historyKey() = MoveHistoryKey(player, piece, from, to, promote)

        /** Score from the side-to-move's perspective. Values are intentionally centralised for easy v1 tuning. */
        private fun evaluate(position: ShogiPosition): Int {
            val perspective = position.activePlayer
            var score = 0
            position.board.forEach { (square, piece) ->
                val sign = if (piece.owner == perspective) 1 else -1
                score += sign * (value(piece.type) + activity(piece, square))
            }
            Player.entries.forEach { owner ->
                val sign = if (owner == perspective) 1 else -1
                position.hands[owner].orEmpty().forEach { score += sign * (value(it) * 9 / 10) }
                score += sign * ShogiStrategy.positionScore(position, owner)
            }
            if (ShogiRules.isInCheck(position, perspective)) score -= 140
            if (ShogiRules.isInCheck(position, perspective.opponent())) score += 80
            return score
        }

        private fun activity(piece: Piece, square: Square): Int = when (piece.type.unpromoted) {
            PieceType.PAWN -> (if (piece.owner == Player.SENTE) 9 - square.rank else square.rank - 1) * 5
            PieceType.ROOK, PieceType.BISHOP -> if (square.file in 3..7 && square.rank in 3..7) 16 else 4
            PieceType.SILVER, PieceType.GOLD -> if (square.file in 3..7) 7 else 0
            else -> 0
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
            const val MATE = 100_000
            const val CHECK_EXTENSIONS = 1
        }
    }
}
