package com.voiddeveloper.tictactoe.model

import org.springframework.web.socket.WebSocketSession

data class SessionMessage(
    val session: WebSocketSession,
    val message: String
)
