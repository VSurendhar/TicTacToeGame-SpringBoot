package com.voiddeveloper.tictactoe.config

import com.voiddeveloper.tictactoe.component.QueryParamHandshakeInterceptor
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Configuration
import org.springframework.web.socket.WebSocketHandler
import org.springframework.web.socket.config.annotation.EnableWebSocket
import org.springframework.web.socket.config.annotation.WebSocketConfigurer
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry

@EnableWebSocket
@Configuration
class WebSocketConfig : WebSocketConfigurer {

    @Autowired
    private lateinit var webSocketHandler: WebSocketHandler

    override fun registerWebSocketHandlers(registry: WebSocketHandlerRegistry) {
        registry.addHandler(webSocketHandler, "/ticTacToe")
            .addInterceptors(QueryParamHandshakeInterceptor())
            .setAllowedOrigins("*")
    }

}