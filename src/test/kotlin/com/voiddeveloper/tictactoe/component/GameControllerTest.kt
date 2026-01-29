package com.voiddeveloper.tictactoe.component

import com.voiddeveloper.tictactoe.model.GameResult
import com.voiddeveloper.tictactoe.model.GameStatus
import org.springframework.boot.test.context.SpringBootTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@SpringBootTest
class GameControllerTest {

    @Test
    fun `should return DRAW when board is completely filled without winner`() {

        val game = GameController()

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

        var result: GameResult? = null

        moves.forEach { (pos, player) ->
            val (row, col) = pos
            result = game.mark(row, col, player)
        }

        assertTrue(result != null)
        assertEquals(GameStatus.Draw, result!!.status)

        // Board should be fully filled
        assertTrue(result!!.board.flatten().all { it != null })
    }

    @Test
    fun `should return WIN when a player completes a line`() {

        val game = GameController()

        game.mark(0, 0, 'X')
        game.mark(0, 1, 'O')
        game.mark(1, 1, 'X')
        game.mark(0, 2, 'O')

        val result = game.mark(2, 2, 'X')

        assertTrue(result.status is GameStatus.Win)

        val winStatus = result.status

        assertEquals('X', winStatus.coin)

    }

    @Test
    fun `should return ACCEPTED for a normal valid move`() {

        val game = GameController()

        val result = game.mark(1, 1, 'X')

        assertEquals(GameStatus.Accepted, result.status)

        assertEquals('X', result.board[1][1])
    }



}