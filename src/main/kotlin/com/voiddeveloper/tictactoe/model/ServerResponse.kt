package com.voiddeveloper.tictactoe.model

import kotlinx.serialization.Serializable

@Serializable
data class ServerResponse(
    val userId: String? = null,
    val roomId: String? = null,
    val message: GameStatePayload? = null,
    val board: Board? = null,
)

@Serializable
data class GameStatePayload(
    val content: String? = null,
    val command: GameActionType? = null,
)

@Serializable
enum class GameActionType {
    GAME_WON,
    GAME_LOST,
    GAME_TIED,
    ERROR,
    PLAYER_CONNECTED,
    PLAYER_DISCONNECTED,
    ROOM_FULL,
    INVALID_ACTION,
    INVALID_CREDENTIALS,
}

@Serializable
data class Board(val board: List<List<Char?>>)