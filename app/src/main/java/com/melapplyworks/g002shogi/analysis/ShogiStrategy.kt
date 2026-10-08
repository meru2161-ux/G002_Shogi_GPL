package com.melapplyworks.g002shogi.analysis

import com.melapplyworks.g002shogi.model.*
import kotlin.math.abs

/**
 * Original position-based strategy knowledge shared by play and coaching.
 * It recognizes plans from the board instead of depending on a fixed move order.
 */
internal object ShogiStrategy {
    internal enum class RookPlan(val label: String) {
        UNDECIDED("方針を選ぶ段階"), STATIC_ROOK("居飛車"), CENTRAL_ROOK("中飛車"),
        FOURTH_FILE_ROOK("四間飛車"), THIRD_FILE_ROOK("三間飛車"),
        OPPOSING_ROOK("向かい飛車"), OTHER("飛車の活用")
    }

    internal enum class CastlePlan(val label: String) {
        NONE("囲いを準備中"), YAGURA("矢倉囲い"), MINO("美濃囲い")
    }

    internal enum class AttackPlan(val label: String) {
        NONE("攻め形を準備中"), BOGIN("棒銀"), RANGING_ROOK_ATTACK("歩・銀・飛の攻め形")
    }

    internal data class StrategicProfile(
        val rookPlan: RookPlan,
        val castlePlan: CastlePlan,
        val attackPlan: AttackPlan,
        val summary: String,
        val intent: String,
        val caution: String
    )

    fun profile(position: ShogiPosition, owner: Player): StrategicProfile {
        val rookPlan = rookPlan(position, owner)
        val castlePlan = when {
            minoFormationScore(position, owner) >= 3 -> CastlePlan.MINO
            yaguraFormationScore(position, owner) >= 3 -> CastlePlan.YAGURA
            else -> CastlePlan.NONE
        }
        val attackPlan = attackPlan(position, owner, rookPlan)
        val summary = listOfNotNull(
            rookPlan.label,
            castlePlan.takeIf { it != CastlePlan.NONE }?.label,
            attackPlan.takeIf { it != AttackPlan.NONE }?.label
        ).joinToString("・")
        val intent = when {
            attackPlan == AttackPlan.BOGIN ->
                "飛車先へ銀を進め、歩交換の後に銀を敵陣へ進出させる棒銀の方針です。"
            attackPlan == AttackPlan.RANGING_ROOK_ATTACK ->
                "歩を先頭に銀、飛車の順で支え、飛車を振った筋から攻める方針です。"
            else -> when (rookPlan) {
            RookPlan.CENTRAL_ROOK -> "5筋へ飛車・銀・歩を集め、中央から主導権を取る方針です。"
            RookPlan.FOURTH_FILE_ROOK -> "飛車を振った筋へ歩と銀を重ね、玉は反対側の美濃囲いへ運ぶ方針です。"
            RookPlan.THIRD_FILE_ROOK -> "角道と飛車の筋を連動させ、歩交換からさばく方針です。"
            RookPlan.OPPOSING_ROOK -> "相手の飛車先を正面から受け、歩交換後の反撃を狙う方針です。"
            RookPlan.STATIC_ROOK -> "飛車を元の筋で使い、飛車先の歩と銀を攻めへ参加させる方針です。"
            else -> "角道・飛車先・玉の安全を見比べ、無理のない作戦を選ぶ段階です。"
            }
        }
        val caution = when {
            rookPlan in RANGING_ROOK_PLANS && castlePlan != CastlePlan.MINO ->
                "飛車を振った後は玉飛接近を避け、戦いの前に美濃囲い方向へ玉を移しましょう。"
            rookPlan == RookPlan.STATIC_ROOK && castlePlan == CastlePlan.NONE ->
                "飛車先だけを急がず、金銀で玉を守ってから仕掛ける必要があります。"
            castlePlan == CastlePlan.YAGURA ->
                "矢倉は上部に強い一方、相手の角筋と端攻めを確認して駒組みを進めましょう。"
            castlePlan == CastlePlan.MINO ->
                "美濃囲いは横からの攻めに強い一方、端と玉頭の攻めに注意しましょう。"
            else -> "攻め駒だけでなく、自玉を守る金銀の配置も同時に確認しましょう。"
        }
        return StrategicProfile(rookPlan, castlePlan, attackPlan, summary, intent, caution)
    }

    fun openingMoveBonus(position: ShogiPosition, move: Move): Int {
        if (!isOpening(position) || move.isDrop) return 0
        val currentProfile = profile(position, move.player)
        val base = when {
            matches(move, Player.SENTE, 7, 7, 7, 6) || matches(move, Player.GOTE, 3, 3, 3, 4) -> 360
            matches(move, Player.SENTE, 2, 7, 2, 6) || matches(move, Player.GOTE, 8, 3, 8, 4) -> 320
            matches(move, Player.SENTE, 5, 7, 5, 6) || matches(move, Player.GOTE, 5, 3, 5, 4) -> 190
            move.piece == PieceType.SILVER && movesTowardCenter(move) -> 150
            move.piece == PieceType.KING && movesAwayFromCenter(move) -> 90
            move.piece == PieceType.KING && movesTowardEnemy(move) -> -280
            move.piece == PieceType.LANCE -> -320
            move.piece == PieceType.KNIGHT -> -80
            else -> 0
        }
        return base + planCoherenceBonus(position, move, currentProfile)
    }

    fun positionScore(position: ShogiPosition, owner: Player): Int {
        val king = position.board.entries.firstOrNull { it.value.owner == owner && it.value.type == PieceType.KING }?.key ?: return -10_000
        var score = 0
        position.board.forEach { (square, piece) ->
            if (piece.owner != owner || piece.type == PieceType.KING) return@forEach
            val distance = maxOf(abs(square.file - king.file), abs(square.rank - king.rank))
            if (distance == 1) {
                score += when (piece.type.unpromoted) {
                    PieceType.GOLD -> 22
                    PieceType.SILVER -> 16
                    else -> 7
                }
            }
        }
        if (king.file in setOf(2, 3, 7, 8)) score += 22
        // A completed castle remains a real defensive asset after exchanges.
        // Keep this modest positional value beyond the opening so that the
        // evaluator does not suddenly forget king safety merely because a few
        // pieces have left the board.
        score += yaguraFormationScore(position, owner) * 12
        score += minoFormationScore(position, owner) * 12
        if (isOpening(position)) {
            val bishopPawn = if (owner == Player.SENTE) Square(7, 7) else Square(3, 3)
            if (position.board[bishopPawn]?.let { it.owner == owner && it.type == PieceType.PAWN } != true) score += 28
            val leftLance = if (owner == Player.SENTE) Square(9, 9) else Square(1, 1)
            val rightLance = if (owner == Player.SENTE) Square(1, 9) else Square(9, 1)
            score += listOf(leftLance, rightLance).count { position.board[it]?.let { p -> p.owner == owner && p.type == PieceType.LANCE } == true } * 10
            score += when (attackPlan(position, owner, rookPlan(position, owner))) {
                AttackPlan.BOGIN -> 36
                AttackPlan.RANGING_ROOK_ATTACK -> 30
                AttackPlan.NONE -> 0
            }
        }
        return score
    }

    fun teachingPurpose(position: ShogiPosition, move: Move): String? = when {
        matches(move, Player.SENTE, 7, 7, 7, 6) || matches(move, Player.GOTE, 3, 3, 3, 4) ->
            "角道を開き、角を攻めと守りの両方へ働かせる"
        matches(move, Player.SENTE, 2, 7, 2, 6) || matches(move, Player.GOTE, 8, 3, 8, 4) ->
            "飛車先の歩を伸ばし、居飛車の攻め筋を作る"
        matches(move, Player.SENTE, 5, 7, 5, 6) || matches(move, Player.GOTE, 5, 3, 5, 4) ->
            "中央の歩を進め、中飛車を含む中央作戦に備える"
        isRookShiftFromHome(move) ->
            "${rookPlanAfter(move).label}を選び、飛車と歩銀を同じ筋で働かせる"
        isOpening(position) && move.piece == PieceType.SILVER && movesTowardCenter(move) ->
            "銀を中央へ繰り出し、攻守に参加させる"
        isOpening(position) && move.piece == PieceType.KING && movesAwayFromCenter(move) ->
            "玉を中央から離し、囲いへ近づける"
        else -> null
    }

    fun teachingIntent(position: ShogiPosition, move: Move): String? {
        val direct = when {
            matches(move, Player.SENTE, 7, 7, 7, 6) || matches(move, Player.GOTE, 3, 3, 3, 4) ->
                "角交換の可能性と、交換後に角を打ち込まれる場所を確認しながら駒組みを進めます。"
            matches(move, Player.SENTE, 2, 7, 2, 6) || matches(move, Player.GOTE, 8, 3, 8, 4) ->
                "飛車先の歩交換を目標にしつつ、銀や金を攻めへ参加させます。"
            matches(move, Player.SENTE, 5, 7, 5, 6) || matches(move, Player.GOTE, 5, 3, 5, 4) ->
                "中央を支える銀の位置を決め、相手の中央突破にも備えます。"
            isRookShiftFromHome(move) -> profile(assumeApplied(position, move), move.player).intent
            isOpening(position) && move.piece == PieceType.SILVER && movesTowardCenter(move) ->
                "銀を歩の後ろで支え、攻めに使うか玉の守りに使うかを相手の形で決めます。"
            isOpening(position) && move.piece == PieceType.KING && movesAwayFromCenter(move) ->
                profile(position, move.player).let { strategy ->
                    if (strategy.rookPlan in RANGING_ROOK_PLANS) strategy.intent
                    else "金銀を玉の近くへ集め、戦いが始まる前に王手へ強い形を作ります。"
                }
            else -> null
        }
        return direct ?: if (isOpening(position)) profile(position, move.player).intent else null
    }

    fun teachingCaution(position: ShogiPosition, move: Move): String? {
        val direct = when {
            matches(move, Player.SENTE, 7, 7, 7, 6) || matches(move, Player.GOTE, 3, 3, 3, 4) ->
                "自分の角だけでなく相手の角道も通るため、角交換後の両取りに注意しましょう。"
            matches(move, Player.SENTE, 2, 7, 2, 6) || matches(move, Player.GOTE, 8, 3, 8, 4) ->
                "飛車先だけを急ぎすぎると玉が薄いまま戦いになるので、囲いとのバランスが必要です。"
            isRookShiftFromHome(move) ->
                "飛車を動かした後は、玉を反対側へ囲って飛車と玉を近づけすぎないようにします。"
            isOpening(position) && move.piece == PieceType.KING && movesAwayFromCenter(move) ->
                "玉を動かす方向と飛車の攻める側が近すぎないか確認しましょう。"
            else -> null
        }
        return direct ?: if (isOpening(position)) profile(position, move.player).caution else null
    }

    private fun planCoherenceBonus(position: ShogiPosition, move: Move, currentProfile: StrategicProfile): Int {
        var score = 0
        if (isRookShiftFromHome(move)) score += 95
        if (move.piece == PieceType.KING) {
            val from = move.from ?: return score
            val towardMino = towardFile(move.player, from.file, move.to.file, senteTarget = 2, goteTarget = 8)
            val towardYagura = towardFile(move.player, from.file, move.to.file, senteTarget = 8, goteTarget = 2)
            score += when {
                currentProfile.rookPlan in RANGING_ROOK_PLANS && towardMino -> 150
                currentProfile.rookPlan == RookPlan.STATIC_ROOK && towardYagura -> 105
                currentProfile.rookPlan in RANGING_ROOK_PLANS && towardYagura -> -120
                else -> 0
            }
        }
        val after = assumeApplied(position, move)
        if (minoFormationScore(after, move.player) > minoFormationScore(position, move.player) && currentProfile.rookPlan in RANGING_ROOK_PLANS) score += 115
        if (yaguraFormationScore(after, move.player) > yaguraFormationScore(position, move.player) && currentProfile.rookPlan == RookPlan.STATIC_ROOK) score += 90
        if (profile(after, move.player).attackPlan != AttackPlan.NONE && currentProfile.attackPlan == AttackPlan.NONE) score += 105
        return score
    }

    private fun attackPlan(position: ShogiPosition, owner: Player, rookPlan: RookPlan): AttackPlan {
        val silverSquares = position.board.filterValues {
            it.owner == owner && it.type.unpromoted == PieceType.SILVER
        }.keys
        if (rookPlan == RookPlan.STATIC_ROOK) {
            val hasAdvancedSilver = silverSquares.any { square ->
                if (owner == Player.SENTE) square.file in 1..3 && square.rank <= 7
                else square.file in 7..9 && square.rank >= 3
            }
            if (hasAdvancedSilver) return AttackPlan.BOGIN
        }
        if (rookPlan in RANGING_ROOK_PLANS) {
            val rook = position.board.entries.firstOrNull {
                it.value.owner == owner && it.value.type.unpromoted == PieceType.ROOK
            }?.key
            if (rook != null) {
                val hasSilverInFront = silverSquares.any { square ->
                    abs(square.file - rook.file) <= 1 &&
                        if (owner == Player.SENTE) square.rank in (rook.rank - 2)..(rook.rank - 1)
                        else square.rank in (rook.rank + 1)..(rook.rank + 2)
                }
                if (hasSilverInFront) return AttackPlan.RANGING_ROOK_ATTACK
            }
        }
        return AttackPlan.NONE
    }

    private fun rookPlan(position: ShogiPosition, owner: Player): RookPlan {
        val rook = position.board.entries.firstOrNull {
            it.value.owner == owner && it.value.type.unpromoted == PieceType.ROOK
        }?.key ?: return RookPlan.OTHER
        val homeFile = if (owner == Player.SENTE) 2 else 8
        if (rook.file == homeFile) {
            val rookPawn = if (owner == Player.SENTE) Square(2, 7) else Square(8, 3)
            val king = position.board.entries.firstOrNull { it.value.owner == owner && it.value.type == PieceType.KING }?.key
            val committed = position.board[rookPawn]?.let { it.owner == owner && it.type == PieceType.PAWN } != true ||
                king?.let { if (owner == Player.SENTE) it.file >= 6 else it.file <= 4 } == true
            return if (committed) RookPlan.STATIC_ROOK else RookPlan.UNDECIDED
        }
        return planForRookFile(owner, rook.file)
    }

    private fun rookPlanAfter(move: Move): RookPlan = planForRookFile(move.player, move.to.file)

    private fun planForRookFile(owner: Player, file: Int): RookPlan = when {
        file == 5 -> RookPlan.CENTRAL_ROOK
        file == if (owner == Player.SENTE) 6 else 4 -> RookPlan.FOURTH_FILE_ROOK
        file == if (owner == Player.SENTE) 7 else 3 -> RookPlan.THIRD_FILE_ROOK
        file == if (owner == Player.SENTE) 8 else 2 -> RookPlan.OPPOSING_ROOK
        else -> RookPlan.OTHER
    }

    private fun yaguraFormationScore(position: ShogiPosition, owner: Player): Int {
        val squares = if (owner == Player.SENTE) listOf(
            Square(8, 8) to PieceType.KING, Square(7, 8) to PieceType.GOLD,
            Square(6, 7) to PieceType.GOLD, Square(7, 7) to PieceType.SILVER
        ) else listOf(
            Square(2, 2) to PieceType.KING, Square(3, 2) to PieceType.GOLD,
            Square(4, 3) to PieceType.GOLD, Square(3, 3) to PieceType.SILVER
        )
        return squares.count { (square, type) -> position.board[square]?.let { it.owner == owner && it.type.unpromoted == type } == true }
    }

    private fun minoFormationScore(position: ShogiPosition, owner: Player): Int {
        val squares = if (owner == Player.SENTE) listOf(
            Square(2, 8) to PieceType.KING, Square(3, 8) to PieceType.SILVER,
            Square(4, 9) to PieceType.GOLD, Square(5, 8) to PieceType.GOLD
        ) else listOf(
            Square(8, 2) to PieceType.KING, Square(7, 2) to PieceType.SILVER,
            Square(6, 1) to PieceType.GOLD, Square(5, 2) to PieceType.GOLD
        )
        return squares.count { (square, type) -> position.board[square]?.let { it.owner == owner && it.type.unpromoted == type } == true }
    }

    private fun isRookShiftFromHome(move: Move): Boolean {
        val from = move.from ?: return false
        if (move.piece.unpromoted != PieceType.ROOK) return false
        val homeFile = if (move.player == Player.SENTE) 2 else 8
        return from.file == homeFile && move.to.file != homeFile && rookPlanAfter(move) in RANGING_ROOK_PLANS
    }

    private fun assumeApplied(position: ShogiPosition, move: Move): ShogiPosition {
        val board = position.board.toMutableMap()
        move.from?.let(board::remove)
        board[move.to] = Piece(if (move.promote) move.piece.promoted else move.piece, move.player)
        return position.copy(board = board)
    }

    private fun towardFile(owner: Player, from: Int, to: Int, senteTarget: Int, goteTarget: Int): Boolean {
        val target = if (owner == Player.SENTE) senteTarget else goteTarget
        return abs(to - target) < abs(from - target)
    }

    private fun isOpening(position: ShogiPosition): Boolean {
        val handCount = position.hands.values.sumOf { it.size }
        val promotedCount = position.board.values.count { it.type != it.type.unpromoted }
        return position.board.size >= 36 && handCount <= 4 && promotedCount <= 2
    }

    private fun movesTowardCenter(move: Move): Boolean {
        val from = move.from ?: return false
        return abs(move.to.file - 5) < abs(from.file - 5) || move.to.rank != from.rank
    }

    private fun movesAwayFromCenter(move: Move): Boolean {
        val from = move.from ?: return false
        return abs(move.to.file - 5) > abs(from.file - 5)
    }

    private fun movesTowardEnemy(move: Move): Boolean {
        val from = move.from ?: return false
        return if (move.player == Player.SENTE) move.to.rank < from.rank else move.to.rank > from.rank
    }

    private fun matches(move: Move, player: Player, fromFile: Int, fromRank: Int, toFile: Int, toRank: Int): Boolean =
        move.player == player && move.from == Square(fromFile, fromRank) && move.to == Square(toFile, toRank)

    private val RANGING_ROOK_PLANS = setOf(
        RookPlan.CENTRAL_ROOK, RookPlan.FOURTH_FILE_ROOK, RookPlan.THIRD_FILE_ROOK, RookPlan.OPPOSING_ROOK
    )
}
