package com.voiddeveloper.tictactoe.model

data class ServerResponse(
    val userId: String,
    val roomId: String,
    val message: GameStatePayload
)

data class GameStatePayload(
    val content: String,
    val command: GameActionType,
)

enum class GameActionType {
    GAME_WON,
    GAME_LOST,
    GAME_TIED,
    PLAYER_CONNECTED,
    PLAYER_DISCONNECTED,
    ROOM_JOINED,
    ROOM_LEFT,
    ERROR
}
