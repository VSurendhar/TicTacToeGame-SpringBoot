package com.voiddeveloper.tictactoe.component

import com.voiddeveloper.tictactoe.FakeWebSocketSession
import com.voiddeveloper.tictactoe.model.ClientMessage
import com.voiddeveloper.tictactoe.model.GameEvent
import com.voiddeveloper.tictactoe.model.GameServerResponse
import com.voiddeveloper.tictactoe.model.GridPosition
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.web.socket.TextMessage
import kotlin.test.Test

@SpringBootTest
class SimulateRealGame {

    @OptIn(ExperimentalSerializationApi::class)
    val json = Json { explicitNulls = false }

    @Autowired
    private lateinit var gameWsHandler: GameWsHandler

    fun FakeWebSocketSession.lastGameResponse(): GameServerResponse {
        return json.decodeFromString(
            sentMessages.last().payload
        )
    }


    fun playMove(
        session: FakeWebSocketSession,
        x: Int,
        y: Int,
    ) {
        val msg = ClientMessage(move = GridPosition(x, y))
        val payload = json.encodeToString(ClientMessage.serializer(), msg)

        gameWsHandler.handleTextMessage(
            session,
            TextMessage(payload)
        )
    }

    @Test
    fun `full game simulation should end with win or draw`() {

        // -------- Create players --------
        val player1 = FakeWebSocketSession().apply {
            attributes["action"] = "create_room"
        }

        gameWsHandler.afterConnectionEstablished(player1)

        val roomId = json.decodeFromString<GameServerResponse>(
            player1.sentMessages.first().payload
        ).roomId!!

        val player2 = FakeWebSocketSession().apply {
            attributes["action"] = "join_room"
            attributes["roomId"] = roomId
        }

        gameWsHandler.afterConnectionEstablished(player2)

        val players = listOf(player1, player2)

        // -------- Predefined move list (guaranteed outcome) --------
        val moves = listOf(
            0 to 0,
            1 to 0,
            0 to 1,
            1 to 1,
            0 to 2   // <-- win
        )

        var moveIndex = 0
        var gameOver = false

        // -------- Turn-based simulation --------
        while (!gameOver && moveIndex < moves.size) {

            val currentPlayer = players.first {
                it.lastGameResponse().message == GameEvent.YourTurn
            }

            println("$currentPlayer is playing")

            val (x, y) = moves[moveIndex++]

            playMove(currentPlayer, x, y)

            val response = currentPlayer.lastGameResponse()

            when (response.message) {

                is GameEvent.Win -> {
                    gameOver = true
                    println("${response.userId} ${response.assignedChar} WON the Game")
                }

                GameEvent.Tie -> {
                    gameOver = true
                    println("DRAW detected")
                }

                else -> Unit
            }

        }

    }

}


