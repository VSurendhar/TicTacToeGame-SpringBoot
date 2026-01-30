package com.voiddeveloper.tictactoe.model

import kotlinx.serialization.Serializable
import org.springframework.web.socket.WebSocketSession

@Serializable
class Room(
    var socketList: MutableList<WebSocketSession> = mutableListOf(),
    val availableCoins: MutableList<Char> = mutableListOf('X', 'O'),
    val board: List<MutableList<Char?>> = List(3) { MutableList(3) { null } },
) {
    fun toggleSocketList() {
        socketList = (socketList.drop(1) + socketList.first()).toMutableList()
    }
}
