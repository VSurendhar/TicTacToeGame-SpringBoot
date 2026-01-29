package com.voiddeveloper.tictactoe.model

data class ClientAction(val action: GridPosition)

data class GridPosition(val x: Int, val y: Int)

data class Player(val roomId: String, val assignedCoin: Char, val userId: String)

