package com.voiddeveloper.tictactoe.component

import com.voiddeveloper.tictactoe.model.GameEvent
import com.voiddeveloper.tictactoe.utils.Utils.snapShotList

class GameController(
    private val size: Int = 3,
) {

    fun mark(
        row: Int,
        col: Int,
        player: Char,
        board: List<MutableList<Char?>>
    ): GameEvent {

        // Invalid index or already filled
        if (row !in 0 until size || col !in 0 until size || board[row][col] != null) {
            return GameEvent.AlreadyFilled
        }

        // Mark the board
        board[row][col] = player

        // Check win
        if (isWin(player, board)) {
            return GameEvent.Win(player)
        }

        // Check draw
        if (isDraw(board)) {
            return GameEvent.Tie
        }

        // Valid move, game continues
        return GameEvent.MoveAccepted(
            board = board.snapShotList()
        )
    }

    private fun isDraw(board: List<MutableList<Char?>>): Boolean {
        val allFilled = board.all { row -> row.all { it != null } }
        val coins = board.flatten().distinct()
        coins.forEach { coin ->
            if (isWin(coin, board)) {
                return false
            }
        }
        return allFilled
    }

    private fun isWin(player: Char?, board: List<MutableList<Char?>>): Boolean {

        if (player == null) return false

        // Horizontal
        for (row in 0 until size) {
            if ((0 until size).all { col -> board[row][col] == player }) {
                return true
            }
        }

        // Vertical
        for (col in 0 until size) {
            if ((0 until size).all { row -> board[row][col] == player }) {
                return true
            }
        }

        // Main diagonal
        if ((0 until size).all { i -> board[i][i] == player }) {
            return true
        }

        // Anti-diagonal
        if ((0 until size).all { i -> board[i][size - 1 - i] == player }) {
            return true
        }

        return false
    }

}
