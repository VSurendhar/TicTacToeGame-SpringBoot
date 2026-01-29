package com.voiddeveloper.tictactoe.model

import kotlinx.serialization.Serializable
import org.springframework.web.socket.WebSocketSession

@Serializable
class Room(
    val socketList: MutableList<WebSocketSession> = mutableListOf(),
    val availableCoins: MutableList<Char> = mutableListOf('X', 'O'),
    val totalCoins: MutableList<Char> = mutableListOf('X', 'O'),
)
