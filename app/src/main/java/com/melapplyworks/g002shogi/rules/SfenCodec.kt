package com.melapplyworks.g002shogi.rules

import com.melapplyworks.g002shogi.model.Move
import com.melapplyworks.g002shogi.model.Piece
import com.melapplyworks.g002shogi.model.PieceType
import com.melapplyworks.g002shogi.model.Player
import com.melapplyworks.g002shogi.model.ShogiPosition
import com.melapplyworks.g002shogi.model.Square

/** Strict SFEN/USI notation shared by reproducible engine benchmarks and import/export. */
object SfenCodec {
    fun parse(value: String): ShogiPosition {
        val fields = value.trim().split(Regex("\\s+"))
        require(fields.size >= 3) { "SFEN needs board, side and hands" }
        val board = parseBoard(fields[0])
        val activePlayer = when (fields[1]) {
            "b" -> Player.SENTE
            "w" -> Player.GOTE
            else -> error("Invalid SFEN side: ${fields[1]}")
        }
        return ShogiPosition(board, parseHands(fields[2]), activePlayer)
    }

    fun format(position: ShogiPosition, moveNumber: Int = 1): String {
        require(moveNumber >= 1)
        val board = (1..9).joinToString("/") { rank ->
            buildString {
                var empty = 0
                for (file in 9 downTo 1) {
                    val piece = position.board[Square(file, rank)]
                    if (piece == null) {
                        empty++
                    } else {
                        if (empty > 0) append(empty).also { empty = 0 }
                        append(pieceToken(piece))
                    }
                }
                if (empty > 0) append(empty)
            }
        }
        val side = if (position.activePlayer == Player.SENTE) "b" else "w"
        return "$board $side ${formatHands(position)} $moveNumber"
    }

    fun parseUsiMove(position: ShogiPosition, token: String): Move? {
        val promoted = token.endsWith('+')
        val core = token.removeSuffix("+")
        val candidate = when {
            core.length == 4 && core[1] == '*' -> {
                if (promoted) return null
                val type = pieceType(core[0])?.unpromoted ?: return null
                Move(null, parseSquare(core.substring(2)) ?: return null, type, position.activePlayer)
            }
            core.length == 4 -> {
                val from = parseSquare(core.substring(0, 2)) ?: return null
                val to = parseSquare(core.substring(2, 4)) ?: return null
                val piece = position.board[from]?.takeIf { it.owner == position.activePlayer } ?: return null
                Move(from, to, piece.type, position.activePlayer, promote = promoted)
            }
            else -> return null
        }
        return ShogiRules.legalMoves(position).firstOrNull { it.sameAction(candidate) }
            ?.copy(notation = token)
    }

    fun parseUsiVariation(position: ShogiPosition, tokens: List<String>): List<Move> {
        var current = position
        return buildList {
            for (token in tokens) {
                val move = parseUsiMove(current, token) ?: break
                add(move)
                current = ShogiRules.applyKnownLegal(current, move)
            }
        }
    }

    fun formatUsiMove(move: Move): String {
        val destination = formatSquare(move.to)
        return if (move.isDrop) {
            "${pieceLetter(move.piece.unpromoted)}*$destination"
        } else {
            "${formatSquare(requireNotNull(move.from))}$destination${if (move.promote) "+" else ""}"
        }
    }

    private fun parseBoard(value: String): Map<Square, Piece> {
        val ranks = value.split('/')
        require(ranks.size == 9) { "SFEN board must have 9 ranks" }
        val board = mutableMapOf<Square, Piece>()
        ranks.forEachIndexed { rankIndex, encoded ->
            var file = 9
            var promoted = false
            encoded.forEach { symbol ->
                when {
                    symbol == '+' -> {
                        require(!promoted) { "Duplicate SFEN promotion marker" }
                        promoted = true
                    }
                    symbol.isDigit() -> {
                        require(!promoted) { "Promotion marker before empty squares" }
                        file -= symbol.digitToInt().also { require(it in 1..9) }
                    }
                    else -> {
                        require(file in 1..9) { "Too many SFEN squares in rank ${rankIndex + 1}" }
                        val base = pieceType(symbol) ?: error("Unknown SFEN piece: $symbol")
                        require(!promoted || base.canPromote) { "Piece cannot be promoted: $symbol" }
                        val owner = if (symbol.isUpperCase()) Player.SENTE else Player.GOTE
                        board[Square(file, rankIndex + 1)] = Piece(if (promoted) base.promoted else base, owner)
                        file--
                        promoted = false
                    }
                }
            }
            require(!promoted && file == 0) { "SFEN rank ${rankIndex + 1} does not contain 9 squares" }
        }
        return board
    }

    private fun parseHands(value: String): Map<Player, List<PieceType>> {
        if (value == "-") return emptyMap()
        val hands = mutableMapOf<Player, MutableList<PieceType>>()
        var count = 0
        value.forEach { symbol ->
            if (symbol.isDigit()) {
                count = count * 10 + symbol.digitToInt()
            } else {
                val type = pieceType(symbol)?.unpromoted ?: error("Unknown SFEN hand piece: $symbol")
                require(type != PieceType.KING) { "A king cannot be in hand" }
                val owner = if (symbol.isUpperCase()) Player.SENTE else Player.GOTE
                repeat(if (count == 0) 1 else count) { hands.getOrPut(owner) { mutableListOf() }.add(type) }
                count = 0
            }
        }
        require(count == 0) { "SFEN hand count is missing a piece" }
        return hands
    }

    private fun formatHands(position: ShogiPosition): String {
        val order = listOf(PieceType.ROOK, PieceType.BISHOP, PieceType.GOLD, PieceType.SILVER, PieceType.KNIGHT, PieceType.LANCE, PieceType.PAWN)
        val encoded = buildString {
            listOf(Player.SENTE, Player.GOTE).forEach { owner ->
                order.forEach { type ->
                    val count = position.hands[owner].orEmpty().count { it.unpromoted == type }
                    if (count > 1) append(count)
                    if (count > 0) {
                        val letter = pieceLetter(type)
                        append(if (owner == Player.SENTE) letter else letter.lowercaseChar())
                    }
                }
            }
        }
        return encoded.ifEmpty { "-" }
    }

    private fun pieceToken(piece: Piece): String {
        val base = piece.type.unpromoted
        val letter = pieceLetter(base).let { if (piece.owner == Player.SENTE) it else it.lowercaseChar() }
        return if (piece.type != base) "+$letter" else letter.toString()
    }

    private fun pieceLetter(type: PieceType): Char = when (type.unpromoted) {
        PieceType.ROOK -> 'R'
        PieceType.BISHOP -> 'B'
        PieceType.GOLD -> 'G'
        PieceType.SILVER -> 'S'
        PieceType.KNIGHT -> 'N'
        PieceType.LANCE -> 'L'
        PieceType.PAWN -> 'P'
        PieceType.KING -> 'K'
        else -> error("Unexpected promoted type")
    }

    private fun pieceType(symbol: Char): PieceType? = when (symbol.uppercaseChar()) {
        'R' -> PieceType.ROOK
        'B' -> PieceType.BISHOP
        'G' -> PieceType.GOLD
        'S' -> PieceType.SILVER
        'N' -> PieceType.KNIGHT
        'L' -> PieceType.LANCE
        'P' -> PieceType.PAWN
        'K' -> PieceType.KING
        else -> null
    }

    private fun parseSquare(value: String): Square? {
        if (value.length != 2) return null
        val file = value[0].digitToIntOrNull()?.takeIf { it in 1..9 } ?: return null
        val rank = (value[1].lowercaseChar() - 'a' + 1).takeIf { it in 1..9 } ?: return null
        return Square(file, rank)
    }

    private fun formatSquare(square: Square): String = "${square.file}${('a'.code + square.rank - 1).toChar()}"

    private fun Move.sameAction(other: Move): Boolean =
        from == other.from && to == other.to && piece == other.piece && player == other.player && promote == other.promote
}
