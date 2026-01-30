package com.voiddeveloper.tictactoe.component

import com.voiddeveloper.tictactoe.model.GameEvent
import org.junit.jupiter.api.Assertions.assertNotNull
import org.springframework.boot.test.context.SpringBootTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@SpringBootTest
class GameControllerTest {

    @Test
    fun `should return TIE when board is completely filled without winner`() {

        val game = GameController()
        val board = List(3) { MutableList<Char?>(3) { null } }

        // Sequence of moves filling the board without a winner
        val moves = listOf(
            0 to 0 to 'X',
            0 to 1 to 'O',
            0 to 2 to 'X',
            1 to 0 to 'X',
            1 to 1 to 'O',
            1 to 2 to 'O',
            2 to 0 to 'O',
            2 to 1 to 'X',
            2 to 2 to 'X'
        )

        var event: GameEvent? = null

        moves.forEach { (pos, player) ->
            val (row, col) = pos
            event = game.mark(row, col, player, board)
        }

        assertNotNull(event)

        // Assert that the last move triggered a Tie
        assertTrue(event is GameEvent.Tie)

        // Check that the board is fully filled
        val flattenedBoard = board.flatten()
        assertTrue(flattenedBoard.all { it != null })
    }

    @Test
    fun `should return WIN when a player completes a line`() {

        val game = GameController()
        val board = List(3) { MutableList<Char?>(3) { null } }

        // Fill board to trigger a win for 'X'
        game.mark(0, 0, 'X', board)
        game.mark(0, 1, 'O', board)
        game.mark(1, 1, 'X', board)
        game.mark(0, 2, 'O', board)

        // Last move that should win
        val event = game.mark(2, 2, 'X', board)

        // Assert that the event is a Win
        assertTrue(event is GameEvent.Win)

        // Cast to access coin
        val winEvent = event

        // Check the winning player
        assertEquals('X', winEvent.coin)

    }

    @Test
    fun `should return MoveAccepted for a normal valid move`() {

        val game = GameController()
        val board = List(3) { MutableList<Char?>(3) { null } }

        val event = game.mark(1, 1, 'X', board)

        // Check that the returned event is MoveAccepted
        assertTrue(event is GameEvent.MoveAccepted)

        val moveAccepted = event

        // Check that the board is updated
        assertEquals('X', moveAccepted.board[1][1])
    }


}