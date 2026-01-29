package com.voiddeveloper.tictactoe.model

import kotlinx.serialization.Serializable

@Serializable
data class ServerResponse(
    val userId: String? = null,
    val roomId: String? = null,
    val message: GameStatePayload? = null,
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
    PLAYER_CONNECTED,
    PLAYER_DISCONNECTED,
    ROOM_JOINED,
    ROOM_LEFT,
    ERROR,
    ROOM_FULL,
    INVALID_ACTION,
    INVALID_CREDENTIALS,
}
