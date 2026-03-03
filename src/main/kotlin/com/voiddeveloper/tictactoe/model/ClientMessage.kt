package com.voiddeveloper.tictactoe.model

import kotlinx.serialization.Serializable

@Serializable
data class ClientMessage(val move: GridPosition? = null, val clearGame: Boolean? = null)

@Serializable
data class GridPosition(val row: Int? = null, val col: Int? = null)
