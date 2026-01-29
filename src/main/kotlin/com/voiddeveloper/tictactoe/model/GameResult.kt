package com.voiddeveloper.tictactoe.model

sealed interface GameStatus {
    data class Win(val coin: Char) : GameStatus
    object Draw : GameStatus
    object AlreadyFilled : GameStatus
    object Accepted : GameStatus
}

data class GameResult(
    val status: GameStatus,
    val board: List<List<Char?>>,
)
