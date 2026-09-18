package com.example

import android.graphics.Bitmap
import com.example.ai.ActionPlanner
import com.example.ai.AiActionPlan
import com.example.ai.AiProvider
import com.example.automation.AutomationStep
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ActionPlannerTest {

    private val fakeAiProvider = object : AiProvider {
        override suspend fun generateText(prompt: String, systemInstruction: String?): Result<String> =
            Result.success("{}")

        override suspend fun analyzeImage(bitmap: Bitmap, prompt: String): Result<String> =
            Result.success("Analysis")

        override suspend fun planAction(userQuery: String, screenContext: String?): Result<AiActionPlan> =
            Result.success(AiActionPlan("GENERAL", "", emptyMap(), emptyList(), false, 1.0f))
    }

    private val planner = ActionPlanner(fakeAiProvider)

    @Test
    fun testLockCommandFastPath() = runBlocking {
        val plan = planner.planUserCommand("Sigma phone lock karo")
        assertTrue(plan.steps.first() is AutomationStep.LockDevice)
    }

    @Test
    fun testScrollDownFastPath() = runBlocking {
        val plan = planner.planUserCommand("Sigma neeche scroll karo")
        val step = plan.steps.first() as AutomationStep.Scroll
        assertTrue(step.forward)
    }

    @Test
    fun testChainedYouTubeSearchAndPlay() = runBlocking {
        val plan = planner.planUserCommand("Open YouTube, search for lo-fi beats, and play it")
        assertEquals("PLAY_MEDIA", plan.intent)
        assertTrue(plan.query.contains("lo-fi beats", ignoreCase = true))
        assertTrue(plan.steps.any { it is AutomationStep.LaunchApp })
        assertTrue(plan.steps.any { it is AutomationStep.FindAndTapSearch })
        assertTrue(plan.steps.any { it is AutomationStep.TypeText })
        assertTrue(plan.steps.any { it is AutomationStep.SelectResult })
        assertTrue(plan.steps.any { it is AutomationStep.PlayMedia })
        assertTrue(plan.steps.any { it is AutomationStep.VerifyPlayback })
    }

    @Test
    fun testSearchAlanWalkerFadedAndPlayIt() = runBlocking {
        val plan = planner.planUserCommand("search Alan Walker Faded and play it")
        assertEquals("PLAY_MEDIA", plan.intent)
        assertEquals("Alan Walker Faded", plan.query)
        assertTrue(plan.steps.any { it is AutomationStep.SelectResult })
        assertTrue(plan.steps.any { it is AutomationStep.PlayMedia })
        assertTrue(plan.steps.any { it is AutomationStep.VerifyPlayback })
    }

    @Test
    fun testSearchQueryThenPlay() = runBlocking {
        val plan = planner.planUserCommand("search shape of you then play")
        assertEquals("PLAY_MEDIA", plan.intent)
        assertEquals("shape of you", plan.query)
        assertTrue(plan.steps.any { it is AutomationStep.SelectResult })
        assertTrue(plan.steps.any { it is AutomationStep.PlayMedia })
    }

    @Test
    fun testFindSongAndPlay() = runBlocking {
        val plan = planner.planUserCommand("find despacito and play")
        assertEquals("PLAY_MEDIA", plan.intent)
        assertEquals("despacito", plan.query)
        assertTrue(plan.steps.any { it is AutomationStep.SelectResult })
        assertTrue(plan.steps.any { it is AutomationStep.PlayMedia })
    }

    @Test
    fun testSearchYouTubeForQueryAndPlayFirstResult() = runBlocking {
        val plan = planner.planUserCommand("search YouTube for coldplay yellow and play the first result")
        assertEquals("PLAY_MEDIA", plan.intent)
        assertEquals("coldplay yellow", plan.query)
        val selectStep = plan.steps.filterIsInstance<AutomationStep.SelectResult>().firstOrNull()
        assertTrue(selectStep != null)
        assertEquals("coldplay yellow", selectStep?.query)
        assertTrue(plan.steps.any { it is AutomationStep.PlayMedia })
    }

    @Test
    fun testOpenYouTubeSearchAndPlayIt() = runBlocking {
        val plan = planner.planUserCommand("open YouTube, search Alan Walker Faded and play it")
        assertEquals("PLAY_MEDIA", plan.intent)
        assertEquals("Alan Walker Faded", plan.query)
        assertTrue(plan.steps.any { it is AutomationStep.SelectResult })
        assertTrue(plan.steps.any { it is AutomationStep.PlayMedia })
    }

    @Test
    fun testSearchXAndPlayTheFirstResult() = runBlocking {
        val plan = planner.planUserCommand("search interstellar theme and play the first result")
        assertEquals("PLAY_MEDIA", plan.intent)
        assertEquals("interstellar theme", plan.query)
        assertTrue(plan.steps.any { it is AutomationStep.SelectResult })
        assertTrue(plan.steps.any { it is AutomationStep.PlayMedia })
    }

    @Test
    fun testSearchXAloneDoesNotAttemptResultPlay() = runBlocking {
        val plan = planner.planUserCommand("search Alan Walker Faded")
        assertEquals("SEARCH_ONLY", plan.intent)
        assertEquals("Alan Walker Faded", plan.query)
        assertFalse("Search alone must not have SelectResult step", plan.steps.any { it is AutomationStep.SelectResult })
        assertFalse("Search alone must not have PlayMedia step", plan.steps.any { it is AutomationStep.PlayMedia })
        assertFalse("Search alone must not have VerifyPlayback step", plan.steps.any { it is AutomationStep.VerifyPlayback })
    }

    @Test
    fun testSettingsNavigation() = runBlocking {
        val plan = planner.planUserCommand("Go to settings and open Wi-Fi")
        assertTrue(plan.steps.isNotEmpty())
        assertTrue(plan.steps.any { it is AutomationStep.LaunchApp || it is AutomationStep.FindAndTap })
    }

    @Test
    fun testTapAndType() = runBlocking {
        val plan = planner.planUserCommand("Tap search and type Naruto")
        assertTrue(plan.steps.isNotEmpty())
        assertTrue(plan.steps.any { it is AutomationStep.FindAndTap || it is AutomationStep.FindAndTapSearch })
        assertTrue(plan.steps.any { it is AutomationStep.TypeText })
    }

    @Test
    fun testPlayMusicCommands() = runBlocking {
        val plan = planner.planUserCommand("play music")
        assertTrue(plan.steps.isNotEmpty())
        assertTrue(plan.steps.any { it is AutomationStep.LaunchApp || it is AutomationStep.MediaControl })
    }

    @Test
    fun testPlaySongDirectCommand() = runBlocking {
        val plan = planner.planUserCommand("play Bohemian Rhapsody")
        assertEquals("PLAY_MEDIA", plan.intent)
        assertTrue(plan.steps.any { it is AutomationStep.LaunchApp })
        assertTrue(plan.steps.any { it is AutomationStep.TypeText })
        assertTrue(plan.steps.any { it is AutomationStep.SelectResult })
        assertTrue(plan.steps.any { it is AutomationStep.PlayMedia })
        assertTrue(plan.steps.any { it is AutomationStep.VerifyPlayback })
    }
}
