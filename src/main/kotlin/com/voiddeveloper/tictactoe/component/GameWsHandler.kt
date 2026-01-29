package com.voiddeveloper.tictactoe.component

import com.voiddeveloper.tictactoe.model.GameActionType
import com.voiddeveloper.tictactoe.model.GameStatePayload
import com.voiddeveloper.tictactoe.model.ServerResponse
import com.voiddeveloper.tictactoe.utils.Utils.generateRandomCode
import com.voiddeveloper.tictactoe.utils.Utils.serialize
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import org.springframework.web.socket.CloseStatus
import org.springframework.web.socket.TextMessage
import org.springframework.web.socket.WebSocketSession
import org.springframework.web.socket.handler.TextWebSocketHandler

@Component
class GameWsHandler : TextWebSocketHandler() {

    val gameRooms: MutableMap<String, MutableList<WebSocketSession>> = HashMap()

    @Autowired
    private lateinit var tokenHandler: TokenHandler


    override fun afterConnectionEstablished(session: WebSocketSession) {
        super.afterConnectionEstablished(session)
        val action = session.attributes["action"]
        println(action)
        when (action) {
            "create_room" -> createRoom(session)
            "join_room" -> joinRoom(session)
            else -> {
                session.sendMessage(TextMessage("invalid action"))
            }
        }
    }

    private fun joinRoom(session: WebSocketSession) {

        val secureRoomId: String? = session.attributes["roomId"] as? String
        val validRoomID = secureRoomId?.let { tokenHandler.verifyRoomToken(it) } ?: false

        val roomId = secureRoomId?.split(".")?.get(0)

        if (roomId == null || !validRoomID || !gameRooms.containsKey(roomId)) {
            println("$roomId is not a valid room")
            println(gameRooms)
            session.sendMessage(TextMessage("Invalid Room Id or Room Id Missing"))
            return
        } else {

            val userId = generateRandomCode()
            val secureUserId = tokenHandler.createUserToken(userId = userId)

            println("user ID - $userId")
            println("room ID - $roomId")
            println("secure user ID - $secureUserId")

            val room = gameRooms[roomId] ?: run {
                println(gameRooms)
                session.sendMessage(TextMessage("Invalid Room Id or Room Id Missing"))
                return
            }

            room.add(session)
            println("$roomId is available and user added to room")


            val serverResponse = ServerResponse(
                userId = secureUserId, roomId = secureRoomId, message = GameStatePayload(
                    command = GameActionType.PLAYER_CONNECTED, content = "Room is Available and you are connected"
                )
            ).serialize()

            println(gameRooms)
            session.sendMessage(TextMessage(serverResponse))

        }
    }

    private fun createRoom(session: WebSocketSession) {

        val userId = generateRandomCode()
        val roomId = generateRandomCode()
        val secureUserId = tokenHandler.createUserToken(userId = userId)
        val secureRoomId = tokenHandler.createRoomToken(roomId = roomId)

        println("user ID - $userId")
        println("room ID - $roomId")
        println("secure user ID - $secureUserId")
        println("secure room ID - $secureRoomId")

        gameRooms.put(
            roomId, mutableListOf(session)
        )

        println("room created!!!!!")
        println(gameRooms)

        val serverResponse = ServerResponse(
            userId = secureUserId, roomId = secureRoomId, message = GameStatePayload(
                command = GameActionType.PLAYER_CONNECTED, content = "Room is Created and you are connected"
            )
        ).serialize()

        println("server response: $serverResponse")

        session.sendMessage(TextMessage(serverResponse))

    }

    override fun afterConnectionClosed(session: WebSocketSession, status: CloseStatus) {
        super.afterConnectionClosed(session, status)
        val room = gameRooms[session.attributes["roomId"]]
        room?.forEach { session ->
//            TODO()
            /*val serverResponse = ServerResponse(
                userId = session.attributes["userId"] as? String ?: "",
                roomId = session.attributes["roomId"] as? String ?: "",
                message = GameStatePayload(
                    content = "Player ${} Disconnected",
                    command = GameActionType.PLAYER_DISCONNECTED
                )
            )*/
            session.sendMessage(TextMessage("${session.attributes["roomId"]} closed"))
        }
    }

    override fun handleTextMessage(session: WebSocketSession, message: TextMessage) {
        super.handleTextMessage(session, message)
    }


}