package com.melapplyworks.g002shogi.ui

import com.melapplyworks.g002shogi.model.Move
import com.melapplyworks.g002shogi.model.PieceType
import com.melapplyworks.g002shogi.model.Square

/** Keeps board moves and hand drops on separate input paths. */
object ShogiMoveSelection {
    fun choicesForTarget(
        legalMoves: List<Move>,
        selectedSquare: Square?,
        selectedDrop: PieceType?,
        target: Square
    ): List<Move> = when {
        selectedDrop != null -> legalMoves.filter {
            it.isDrop && !it.promote && it.piece == selectedDrop && it.to == target
        }
        selectedSquare != null -> legalMoves.filter {
            !it.isDrop && it.from == selectedSquare && it.to == target
        }
        else -> emptyList()
    }

    fun legalTargets(
        legalMoves: List<Move>,
        selectedSquare: Square?,
        selectedDrop: PieceType?
    ): Set<Square> = when {
        selectedDrop != null -> legalMoves.filter {
            it.isDrop && !it.promote && it.piece == selectedDrop
        }.mapTo(linkedSetOf()) { it.to }
        selectedSquare != null -> legalMoves.filter {
            !it.isDrop && it.from == selectedSquare
        }.mapTo(linkedSetOf()) { it.to }
        else -> emptySet()
    }

    fun isOptionalPromotionChoice(choices: List<Move>): Boolean {
        if (choices.size != 2 || choices.any { it.isDrop }) return false
        val first = choices.first()
        return choices.all {
            it.from == first.from && it.to == first.to && it.piece == first.piece && it.player == first.player
        } && choices.map { it.promote }.toSet() == setOf(false, true)
    }
}
