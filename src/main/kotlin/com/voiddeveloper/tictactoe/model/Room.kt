package com.voiddeveloper.tictactoe.model

import org.springframework.web.socket.WebSocketSession


class Room(
    val socketList: MutableList<WebSocketSession> = mutableListOf(),
    val playerQueue: MutableList<String> = ArrayDeque(),
    val availableCoins: MutableList<Char> = mutableListOf('X', 'O'),
)
