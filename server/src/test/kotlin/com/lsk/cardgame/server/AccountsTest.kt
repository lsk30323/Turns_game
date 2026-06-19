package com.lsk.cardgame.server

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class AccountsTest {

    @Test fun getOrCreateIsIdempotentAndStartsAtBaseRating() = runTest {
        val repo = InMemoryUserRepository()
        val first = repo.getOrCreate("sub1", "Alice")
        val second = repo.getOrCreate("sub1", "Alice")
        assertEquals(first.sub, second.sub)
        assertEquals(0, first.wins)
        assertEquals(UserAccount.START_RATING, first.rating)
    }

    @Test fun recordResultUpdatesWinsLossesAndRating() = runTest {
        val repo = InMemoryUserRepository()
        repo.getOrCreate("w", "Winner")
        repo.getOrCreate("l", "Loser")
        val (winner, loser) = repo.recordResult("w", "l")
        assertEquals(1, winner.wins)
        assertEquals(UserAccount.START_RATING + UserAccount.RATING_DELTA, winner.rating)
        assertEquals(1, loser.losses)
        assertEquals(UserAccount.START_RATING - UserAccount.RATING_DELTA, loser.rating)
    }

    @Test fun leaderboardOrdersByRatingDescending() = runTest {
        val repo = InMemoryUserRepository()
        repo.getOrCreate("a", "A")
        repo.getOrCreate("b", "B")
        repo.getOrCreate("c", "C")
        repo.recordResult("b", "a") // B 1025, A 975
        repo.recordResult("b", "c") // B 1050, C 975
        val board = repo.leaderboard()
        assertEquals(3, board.size)
        assertEquals("B", board[0].displayName, "최고 레이팅이 1위")
        assertEquals(1, board[0].rank)
        // 레이팅 내림차순 정렬 보장
        val ratings = board.map { it.rating }
        assertEquals(ratings.sortedDescending(), ratings)
    }
}
