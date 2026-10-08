package com.melapplyworks.g002shogi.game

import android.content.Context
import com.melapplyworks.g002shogi.model.Move
import com.melapplyworks.g002shogi.model.PieceType
import com.melapplyworks.g002shogi.model.Player
import com.melapplyworks.g002shogi.model.Square
import com.melapplyworks.g002shogi.model.ShogiPositions
import com.melapplyworks.g002shogi.rules.ShogiRules

data class SavedGame(
    val configuration: GameConfiguration,
    val moves: List<Move>,
    val outcome: SessionOutcome? = null,
    val savedAtEpochMillis: Long = 0L,
    val schemaVersion: Int = 1
)

object SavedGameCodec {
    private const val Header = "G002_SAVE_V1"

    fun encode(saved: SavedGame): String = buildString {
        appendLine(listOf(Header, saved.configuration.mode.name, saved.configuration.humanPlayer.name, saved.configuration.difficulty.name, saved.outcome?.name.orEmpty(), saved.savedAtEpochMillis).joinToString("|"))
        saved.moves.forEach { move ->
            appendLine(listOf(
                move.from?.file ?: 0,
                move.from?.rank ?: 0,
                move.to.file,
                move.to.rank,
                move.piece.name,
                move.player.name,
                if (move.promote) 1 else 0
            ).joinToString(","))
        }
    }

    fun decode(raw: String?): SavedGame? = runCatching {
        val lines = raw?.lineSequence()?.filter { it.isNotBlank() }?.toList().orEmpty()
        if (lines.isEmpty()) return null
        val header = lines.first().split('|')
        require(header.size == 6 && header[0] == Header)
        val configuration = GameConfiguration(
            GameMode.valueOf(header[1]),
            Player.valueOf(header[2]),
            GameDifficulty.valueOf(header[3])
        )
        val outcome = header[4].takeIf(String::isNotBlank)?.let(SessionOutcome::valueOf)
        val savedAt = header[5].toLong()
        val moves = lines.drop(1).map { line ->
            val fields = line.split(',')
            require(fields.size == 7)
            val fromFile = fields[0].toInt()
            val fromRank = fields[1].toInt()
            val from = if (fromFile == 0 && fromRank == 0) null else Square(fromFile, fromRank)
            Move(
                from = from,
                to = Square(fields[2].toInt(), fields[3].toInt()),
                piece = PieceType.valueOf(fields[4]),
                player = Player.valueOf(fields[5]),
                promote = fields[6] == "1"
            )
        }
        SavedGame(configuration, moves, outcome, savedAt)
    }.getOrNull()
}

object SavedGameValidator {
    fun isValid(saved: SavedGame): Boolean = runCatching {
        var position = ShogiPositions.initial()
        var finished = false
        saved.moves.forEach { move ->
            require(!finished && move.player == position.activePlayer && ShogiRules.isLegal(position, move))
            position = ShogiRules.apply(position, move)
            finished = ShogiRules.isCheckmate(position)
        }
        require(saved.outcome == null || finished || saved.outcome in setOf(
            SessionOutcome.SENTE_WIN,
            SessionOutcome.GOTE_WIN,
            SessionOutcome.RESIGNED,
            SessionOutcome.DRAW_REPETITION,
            SessionOutcome.SENTE_LOSES_PERPETUAL_CHECK,
            SessionOutcome.GOTE_LOSES_PERPETUAL_CHECK,
            SessionOutcome.NO_LEGAL_MOVE
        ))
        true
    }.getOrDefault(false)
}

class LocalGameRepository(context: Context) {
    private val preferences = context.getSharedPreferences("g002_local_games_v1", Context.MODE_PRIVATE)

    fun loadActive(): SavedGame? {
        val raw = preferences.getString("active_game", null) ?: return null
        val saved = SavedGameCodec.decode(raw)
        if (saved != null && SavedGameValidator.isValid(saved)) return saved
        preferences.edit()
            .putString("corrupt_active_${System.currentTimeMillis()}", raw)
            .remove("active_game")
            .commit()
        return null
    }

    fun saveActive(saved: SavedGame): Boolean = preferences.edit()
        .putString("active_game", SavedGameCodec.encode(saved))
        .commit()

    fun clearActive(): Boolean = preferences.edit().remove("active_game").commit()
}
