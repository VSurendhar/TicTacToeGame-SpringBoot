package com.voiddeveloper.tictactoe.component

import com.voiddeveloper.tictactoe.model.GameResult
import com.voiddeveloper.tictactoe.model.GameStatus

class GameController(
    private val size: Int = 3,
) {

    private val board: List<MutableList<Char?>> = List(size) { MutableList(size) { null } }

    private var winner: Char? = null

    fun mark(row: Int, col: Int, player: Char): GameResult {

        // Invalid or already filled
        if (row !in 0 until size || col !in 0 until size || board[row][col] != null) {
            return GameResult(
                status = GameStatus.AlreadyFilled, board = snapshotBoard()
            )
        }

        // Mark the board
        board[row][col] = player

        // Check win
        if (isWin(player)) {
            winner = player
            return GameResult(
                status = GameStatus.Win(player), board = snapshotBoard()
            )
        }

        // Check draw
        if (isDraw()) {
            return GameResult(
                status = GameStatus.Draw, board = snapshotBoard()
            )
        }

        // Normal successful move (no win, no draw)
        return GameResult(
            status = GameStatus.Accepted, board = snapshotBoard()
        )

    }

    private fun isDraw(): Boolean {
        val allFilled = board.all { row -> row.all { it != null } }
        return allFilled && winner == null
    }

    private fun isWin(player: Char): Boolean {

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


    private fun snapshotBoard(): List<List<Char?>> = board.map { it.toList() }

}
