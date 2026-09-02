package app.stepsapp.ui.setup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SetupModelTest {

    @Test
    fun `最初はようこそ、最後は目標`() {
        assertEquals(SetupStep.WELCOME, SetupStep.ALL.first())
        assertEquals(SetupStep.GOAL, SetupStep.ALL.last())
        assertEquals(5, SetupStep.COUNT)
    }

    @Test
    fun `権限を求める画面には必ず逃げ道がある`() {
        // 強制すると拒否したときに行き止まりになる
        for (step in listOf(SetupStep.ACTIVITY, SetupStep.HEALTH_CONNECT, SetupStep.BATTERY)) {
            assertNotNull("${step.name} に逃げ道が無い", step.secondary)
        }
    }

    @Test
    fun `文言に否定形の言い回しを使わない`() {
        // 「〜が無いと〜できません」は英語の直訳型で、日本語アプリではまず見ない。
        // 「許可すると〜できます」のように利益を先に言う
        val banned = listOf("できません", "必要です", "しなければ")
        for (step in SetupStep.ALL) {
            for (word in banned) {
                assertFalse(
                    "${step.name} に「$word」が残っている: ${step.body}",
                    step.body.contains(word),
                )
            }
        }
    }

    @Test
    fun `本文は2行に収める`() {
        // 1画面1メッセージ。長いと読まれない
        for (step in SetupStep.ALL) {
            assertTrue(
                "${step.name} が3行以上ある",
                step.body.count { it == '\n' } <= 1,
            )
        }
    }

    @Test
    fun `すべての画面に文言がそろっている`() {
        for (step in SetupStep.ALL) {
            assertTrue(step.name, step.title.isNotBlank())
            assertTrue(step.name, step.body.isNotBlank())
            assertTrue(step.name, step.primary.isNotBlank())
        }
    }
}
