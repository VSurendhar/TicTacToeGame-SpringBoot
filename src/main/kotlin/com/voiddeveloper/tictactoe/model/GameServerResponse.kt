package com.voiddeveloper.tictactoe.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable


@Serializable
data class GameServerResponse(
    val userId: String? = null,
    val roomId: String? = null,
    val assignedChar: Char? = null,
    val message: Payload,
)

@Serializable
sealed interface Payload

@Serializable
sealed interface ServerEvent : Payload {

    @Serializable
    @SerialName("PLAYER_CONNECTED")
    object PlayerConnected : ServerEvent {
        override fun toString(): String {
            return "PLAYER_CONNECTED"
        }
    }

    @Serializable
    @SerialName("PLAYER_DISCONNECTED")
    object PlayerDisconnected : ServerEvent {
        override fun toString(): String {
            return "PLAYER_DISCONNECTED"
        }
    }

    @Serializable
    @SerialName("ROOM_FULL")
    object RoomFull : ServerEvent {
        override fun toString(): String {
            return "ROOM_FULL"
        }
    }

    @Serializable
    @SerialName("INVALID_ACTION")
    object InvalidAction : ServerEvent {
        override fun toString(): String {
            return "INVALID_ACTION"
        }
    }

    @Serializable
    @SerialName("INVALID_CREDENTIALS")
    data class InvalidCredentials(
        val message: String,
    ) : ServerEvent

    @Serializable
        @SerialName("ROOM_CREATED")
    object RoomCreated : ServerEvent {
        override fun toString(): String {
            return "ROOM_CREATED"
        }
    }
}


@Serializable
sealed interface GameEvent : Payload {

    @Serializable
    @SerialName("GAME_STARTED")
    object GameStarted : GameEvent {
        override fun toString(): String {
            return "GAME_STARTED"
        }
    }

    @Serializable
    @SerialName("MOVE_ACCEPTED")
    data class MoveAccepted(
        val board: List<List<Char?>>,
    ) : GameEvent

    @Serializable
    @SerialName("ALREADY_FILLED")
    object AlreadyFilled : GameEvent {
        override fun toString(): String {
            return "ALREADY_FILLED"
        }
    }

    @Serializable
    @SerialName("YOUR_TURN")
    object YourTurn : GameEvent {
        override fun toString(): String {
            return "YOUR_TURN"
        }
    }

    @Serializable
    @SerialName("INVALID_MOVE")
    object InvalidMove : GameEvent {
        override fun toString(): String {
            return "INVALID_MOVE"
        }
    }

    @Serializable
    @SerialName("WIN")
    data class Win(
        val coin: Char,
    ) : GameEvent

    @Serializable
    @SerialName("TIE")
    object Tie : GameEvent {
        override fun toString(): String {
            return "TIE"
        }
    }

}
