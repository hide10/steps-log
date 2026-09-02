package app.stepsapp.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GoalNoticeTest {

    private val goal = Goal(6_000)

    @Test
    fun `目標に届いたら知らせる`() {
        assertEquals(
            GoalNotice.ACHIEVED,
            goalNoticeFor(6_000, goal, hourOfDay = 10, alreadyNotified = emptySet()),
        )
    }

    @Test
    fun `同じ知らせは1日1回だけ`() {
        assertNull(
            goalNoticeFor(8_000, goal, hourOfDay = 20, alreadyNotified = setOf(GoalNotice.ACHIEVED)),
        )
    }

    @Test
    fun `夕方に近づいていれば あと少し を出す`() {
        assertEquals(
            GoalNotice.ALMOST,
            goalNoticeFor(4_500, goal, hourOfDay = 18, alreadyNotified = emptySet()),
        )
    }

    @Test
    fun `昼のうちは あと少し を出さない`() {
        // 一日はまだ長い。急かす意味がない
        assertNull(goalNoticeFor(4_500, goal, hourOfDay = 12, alreadyNotified = emptySet()))
    }

    @Test
    fun `半分にも届いていない日は あと少し を出さない`() {
        // 白々しいので黙っている
        assertNull(goalNoticeFor(1_000, goal, hourOfDay = 20, alreadyNotified = emptySet()))
    }

    @Test
    fun `あと少しを出したあとに達成したら達成も知らせる`() {
        assertEquals(
            GoalNotice.ACHIEVED,
            goalNoticeFor(6_100, goal, hourOfDay = 21, alreadyNotified = setOf(GoalNotice.ALMOST)),
        )
    }

    @Test
    fun `文面に歩数が入る`() {
        val (title, _) = goalNoticeText(GoalNotice.ALMOST, 4_500, goal)

        assertEquals("あと 1,500 歩", title)
    }
}
