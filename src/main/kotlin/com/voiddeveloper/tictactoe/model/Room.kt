package com.voiddeveloper.tictactoe.model

import kotlinx.serialization.Serializable
import org.springframework.web.socket.WebSocketSession

@Serializable
class Room(
    val socketList: MutableList<WebSocketSession> = mutableListOf(),
    val playerQueue: MutableList<String> = ArrayDeque(),
    val availableCoins: MutableList<Char> = mutableListOf('X', 'O'),
)
