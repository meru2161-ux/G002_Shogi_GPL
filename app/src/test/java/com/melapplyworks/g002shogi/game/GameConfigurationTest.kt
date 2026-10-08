package com.melapplyworks.g002shogi.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GameConfigurationTest {
    @Test fun threeDifficultiesUseGenuinelyIncreasingSearchBudgets() {
        val levels = GameDifficulty.entries
        assertEquals(3, levels.size)
        assertTrue(levels.zipWithNext().all { (lower, higher) ->
            lower.maxDepth < higher.maxDepth &&
                lower.nodeLimit < higher.nodeLimit &&
                lower.timeLimitMillis < higher.timeLimitMillis
        })
    }

    @Test fun teacherBudgetStaysStrongWhenOpponentIsBeginner() {
        val opponent = GameDifficulty.BEGINNER.analysisRequest("p1")
        val teacher = teacherAnalysisRequest("p1")
        assertTrue(teacher.maxDepth > opponent.maxDepth)
        assertTrue(teacher.nodeLimit > opponent.nodeLimit)
        assertTrue(teacher.timeLimitMillis > opponent.timeLimitMillis)
    }

    @Test fun normalAiMatchDefaultsToTheStrongestAvailableLocalBudget() {
        assertEquals(GameDifficulty.STRONG, GameConfiguration(GameMode.AI_MATCH).difficulty)
        assertEquals(GameDifficulty.STANDARD, GameConfiguration(GameMode.COACHING).difficulty)
    }
}

