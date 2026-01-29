package com.voiddeveloper.tictactoe.model

data class ClientAction(val action: GridPosition, val player: Player)

data class GridPosition(val x: Int, val y: Int)

data class Player(val roomId: String, val coin: Coin)

enum class Coin {
    X,
    O
}

